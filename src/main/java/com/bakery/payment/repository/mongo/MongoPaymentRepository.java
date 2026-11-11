package com.bakery.payment.repository.mongo;

import com.bakery.common.persistence.MongoCollections;
import com.bakery.common.persistence.MongoSequenceGenerator;
import com.bakery.common.persistence.RetryBackoff;
import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.TargetType;
import com.bakery.payment.model.PaymentValidationException;
import com.bakery.payment.repository.PaymentRecords;
import com.bakery.payment.repository.PaymentRepository;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * MongoDB implementation of {@link PaymentRepository} ({@code payments} collection), active when
 * {@code bakery.storage.type=mongodb}.
 *
 * <ul>
 *   <li><b>Unit of work:</b> a write {@link #transaction} holds a short lease in {@code payment_locks}
 *       (so several application instances never check balances at the same time) plus a local lock,
 *       loads all payments, runs the action, validates the whole set and writes only the changed
 *       documents. A crashed instance's lease expires after {@link #LEASE}.</li>
 *   <li><b>Concurrency:</b> each document has a {@code version} (optimistic locking); unique indexes on
 *       {@code requestKey} and {@code referenceKey} stop duplicate submissions and references even if a
 *       lease expired.</li>
 *   <li><b>IDs:</b> PAY001, PAY002... from the shared atomic {@code counters} sequence, never reused.</li>
 *   <li><b>Money:</b> Decimal128. <b>Validation:</b> a {@code $jsonSchema} validator plus the domain
 *       rules; a stored record that breaks a rule makes the module fail closed (never trusted, never
 *       overwritten), and only its ID is logged.</li>
 *   <li><b>Errors:</b> driver details are logged; users see {@link StorageException} messages only.</li>
 * </ul>
 * Nothing here deletes data except an admin's explicit deletion of a Failed or Cancelled payment.
 */
@Repository
@ConditionalOnProperty(name = "bakery.storage.type", havingValue = "mongodb")
public class MongoPaymentRepository implements PaymentRepository {

    public static final String COLLECTION = "payments";
    public static final String SEQUENCE = "payments";
    public static final String LOCK_COLLECTION = "payment_locks";
    static final String LOCK_ID = "payments";
    static final Duration LEASE = Duration.ofSeconds(15);
    private static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(10);
    private static final String REFERENCE_INDEX = "uk_payments_reference";
    private static final String REQUEST_KEY_INDEX = "uk_payments_request_key";
    private static final String UNAVAILABLE = "Payment storage unavailable. No success was recorded.";

    private static final Logger log = LoggerFactory.getLogger(MongoPaymentRepository.class);

    private final PaymentDocumentRepository documents;
    private final MongoTemplate template;
    private final MongoSequenceGenerator sequences;
    private final ReentrantLock local = new ReentrantLock();
    private final ThreadLocal<Boolean> writing = ThreadLocal.withInitial(() -> false);

    public MongoPaymentRepository(PaymentDocumentRepository documents, MongoTemplate template,
                                  MongoSequenceGenerator sequences) {
        this.documents = documents;
        this.template = template;
        this.sequences = sequences;
    }

    /** Creates the collection, schema validator and indexes (idempotent) and aligns the ID counter. */
    @PostConstruct
    void initialise() {
        MongoCollections.ensureCollection(template, COLLECTION, schema(), List.of(
                new Index().on("requestKey", Sort.Direction.ASC).unique().named(REQUEST_KEY_INDEX),
                new Index().on("referenceKey", Sort.Direction.ASC).unique().named(REFERENCE_INDEX),
                new Index().on("customerId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC)
                        .named("idx_payments_customer_created"),
                new Index().on("targetType", Sort.Direction.ASC).on("targetId", Sort.Direction.ASC)
                        .on("status", Sort.Direction.ASC).named("idx_payments_target_status"),
                new Index().on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC)
                        .named("idx_payments_status_created")));
        syncSequence();
    }

    // ── Unit of work ──────────────────────────────────────────────────────────

    @Override
    public <T> T transaction(Function<List<Payment>, T> action, boolean write) {
        if (local.isHeldByCurrentThread()) {
            throw new IllegalStateException("Nested payment transactions are not supported.");
        }
        local.lock();
        String lease = null;
        try {
            if (write) lease = acquireLease();
            Map<String, PaymentDocument> stored = new LinkedHashMap<>();
            for (PaymentDocument doc : guard(() -> documents.findAll())) stored.put(doc.getId(), doc);
            List<Payment> records = toDomain(stored.values());
            validate(records);
            writing.set(write);
            T result = action.apply(records);
            if (write) persist(records, stored);
            return result;
        } finally {
            writing.set(false);
            if (lease != null) releaseLease(lease);
            local.unlock();
        }
    }

    @Override
    public String nextId() {
        if (!local.isHeldByCurrentThread() || !writing.get()) {
            throw new IllegalStateException("ID allocation requires transaction");
        }
        return String.format("PAY%03d", guard(() -> sequences.next(SEQUENCE)));
    }

    @Override
    public List<Payment> findByTarget(TargetType type, String targetId) {
        if (type == null || targetId == null) return List.of();
        return toDomain(guard(() -> documents.findByTargetTypeAndTargetId(type.name(), targetId)));
    }

    /** Moves the ID counter past the highest stored payment ID (never backwards). */
    public void syncSequence() {
        sequences.ensureAtLeast(SEQUENCE, MongoCollections.maxIdNumber(template, COLLECTION, "PAY"));
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    /** Inserts new payments, saves changed ones (version-checked) and removes deleted ones (version-checked). */
    private void persist(List<Payment> records, Map<String, PaymentDocument> stored) {
        validate(records);
        Instant now = Instant.now();
        Set<String> kept = new HashSet<>();
        try {
            for (Payment p : records) {
                kept.add(p.getId());
                PaymentDocument before = stored.get(p.getId());
                if (before == null) {
                    documents.insert(PaymentDocument.from(p, null, now));
                } else if (!before.sameAs(p)) {
                    documents.save(PaymentDocument.from(p, before.getVersion(), now));
                }
            }
            for (PaymentDocument before : stored.values()) {
                if (kept.contains(before.getId())) continue;
                Query exact = Query.query(Criteria.where("_id").is(before.getId()).and("version").is(before.getVersion()));
                if (template.remove(exact, COLLECTION).getDeletedCount() != 1) {
                    throw new OptimisticLockingFailureException("Payment " + before.getId() + " changed before deletion");
                }
            }
        } catch (DuplicateKeyException e) {
            String message = String.valueOf(e.getMessage());
            if (message.contains(REFERENCE_INDEX)) {
                throw new PaymentValidationException("reference", "Transaction reference already exists.");
            }
            if (message.contains(REQUEST_KEY_INDEX)) {
                throw new IllegalArgumentException("This form has already been submitted");
            }
            syncSequence();   // payment ID already taken (e.g. inserted outside the app): catch up, ask to retry
            throw new StorageException("Payment could not be saved. Please try again.", e);
        } catch (OptimisticLockingFailureException e) {
            throw new StorageException("Payment records changed at the same time. Please try again.", e);
        } catch (DataAccessException e) {
            log.error("MongoDB payment storage failure: {}", e.getClass().getSimpleName());
            throw new StorageException(UNAVAILABLE, e);
        }
    }

    // ── Cross-instance lease ──────────────────────────────────────────────────

    /** Takes the payments lease (a document in {@code payment_locks}), waiting up to ten seconds. */
    private String acquireLease() {
        String token = UUID.randomUUID().toString();
        Instant deadline = Instant.now().plus(ACQUIRE_TIMEOUT);
        for (int attempt = 1; ; attempt++) {
            Instant now = Instant.now();
            Query free = Query.query(Criteria.where("_id").is(LOCK_ID).orOperator(
                    Criteria.where("owner").is(null), Criteria.where("expiresAt").lt(Date.from(now))));
            Update take = new Update().set("owner", token).set("expiresAt", Date.from(now.plus(LEASE)));
            try {
                template.findAndModify(free, take, FindAndModifyOptions.options().upsert(true).returnNew(true),
                        Document.class, LOCK_COLLECTION);
                return token;
            } catch (DuplicateKeyException held) {
                // Another request holds the lease: wait and try again.
            } catch (DataAccessException e) {
                log.error("MongoDB payment lock failure: {}", e.getClass().getSimpleName());
                throw new StorageException(UNAVAILABLE, e);
            }
            if (Instant.now().isAfter(deadline)) {
                throw new StorageException("Payments are busy right now. Please try again in a moment.", null);
            }
            RetryBackoff.pause(Math.min(attempt, RetryBackoff.MAX_ATTEMPTS));
        }
    }

    private void releaseLease(String token) {
        try {
            template.updateFirst(Query.query(Criteria.where("_id").is(LOCK_ID).and("owner").is(token)),
                    new Update().unset("owner").set("expiresAt", new Date(0)), LOCK_COLLECTION);
        } catch (DataAccessException e) {
            log.warn("Could not release the payment lock; it expires automatically.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<Payment> toDomain(Iterable<PaymentDocument> docs) {
        List<Payment> payments = new ArrayList<>();
        for (PaymentDocument doc : docs) {
            try {
                payments.add(doc.toDomain());
            } catch (RuntimeException e) {
                log.error("payments document '{}' is invalid; payment changes are blocked until it is fixed.", doc.getId());
                throw new StorageException(UNAVAILABLE, null);
            }
        }
        return payments;
    }

    private static void validate(List<Payment> records) {
        try {
            PaymentRecords.validate(records);
        } catch (IOException e) {
            log.error("Stored payments break an integrity rule: {}", e.getMessage());
            throw new StorageException(UNAVAILABLE, e);
        }
    }

    private static <T> T guard(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException e) {
            log.error("MongoDB payment storage failure: {}", e.getClass().getSimpleName());
            throw new StorageException(UNAVAILABLE, e);
        }
    }

    /** {@code $jsonSchema} validator: MongoDB itself refuses malformed payment documents. */
    private static Document schema() {
        return new Document("bsonType", "object")
                .append("required", List.of("_id", "targetType", "targetId", "customerId", "total", "amount", "method",
                        "purpose", "status", "reference", "referenceKey", "requestKey", "createdAt"))
                .append("properties", new Document()
                        .append("_id", MongoCollections.string("^PAY[0-9]{3,}$"))
                        .append("targetType", MongoCollections.enumOf("ORDER", "BOOKING"))
                        .append("targetId", MongoCollections.string(null).append("minLength", 1).append("maxLength", 150))
                        .append("customerId", MongoCollections.string(null).append("minLength", 1).append("maxLength", 150))
                        .append("total", new Document("bsonType", "decimal"))
                        .append("amount", new Document("bsonType", "decimal"))
                        .append("method", MongoCollections.enumOf("CASH", "CARD", "BANK_TRANSFER"))
                        .append("purpose", MongoCollections.enumOf("FULL", "ADVANCE", "PARTIAL"))
                        .append("status", MongoCollections.enumOf("PENDING", "PAID", "FAILED", "CANCELLED", "REFUNDED"))
                        .append("reference", MongoCollections.string(null).append("minLength", 3).append("maxLength", 80))
                        .append("referenceKey", MongoCollections.string(null).append("minLength", 3).append("maxLength", 80))
                        .append("requestKey", MongoCollections.string(null).append("minLength", 1).append("maxLength", 150))
                        .append("createdAt", new Document("bsonType", "date"))
                        .append("updatedAt", new Document("bsonType", "date")));
    }
}
