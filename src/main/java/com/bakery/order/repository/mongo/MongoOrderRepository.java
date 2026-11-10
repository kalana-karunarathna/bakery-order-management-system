package com.bakery.order.repository.mongo;

import com.bakery.common.persistence.MongoCollections;
import com.bakery.common.persistence.MongoSequenceGenerator;
import com.bakery.common.persistence.RetryBackoff;
import com.bakery.order.model.Order;
import com.bakery.order.repository.OrderRepository;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * MongoDB implementation of {@link OrderRepository} ({@code orders} collection).
 *
 * <ul>
 *   <li><b>IDs:</b> O001, O002... from the atomic {@code counters} sequence: unique under concurrent
 *       requests, never reused.</li>
 *   <li><b>Indexes:</b> customerId + orderDate (My Orders), status (admin filters/counts), orderDate (sorting).</li>
 *   <li><b>Updates:</b> optimistic locking via the document version, retried on the latest copy, so a
 *       customer edit and an admin status change cannot overwrite each other.</li>
 *   <li><b>Errors:</b> database failures are reported as {@link IOException}, which OrderService turns into
 *       a friendly message; driver details are only logged.</li>
 * </ul>
 * Ownership, validation and the status workflow stay in OrderService. Active when
 * {@code bakery.storage.type=mongodb}.
 */
@Repository
@ConditionalOnProperty(name = "bakery.storage.type", havingValue = "mongodb")
public class MongoOrderRepository implements OrderRepository {

    public static final String COLLECTION = "orders";
    public static final String SEQUENCE = "orders";
    private static final int MAX_ATTEMPTS = RetryBackoff.MAX_ATTEMPTS;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("orderDate"), Sort.Order.desc("id"));

    private static final Logger log = LoggerFactory.getLogger(MongoOrderRepository.class);

    private final OrderDocumentRepository documents;
    private final MongoTemplate template;
    private final MongoSequenceGenerator sequences;

    public MongoOrderRepository(OrderDocumentRepository documents, MongoTemplate template, MongoSequenceGenerator sequences) {
        this.documents = documents;
        this.template = template;
        this.sequences = sequences;
    }

    /** Creates the collection, schema validator and indexes (idempotent) and aligns the ID counter. */
    @PostConstruct
    void initialise() {
        MongoCollections.ensureCollection(template, COLLECTION, schema(), List.of(
                new Index().on("customerId", Sort.Direction.ASC).on("orderDate", Sort.Direction.DESC).named("idx_orders_customer_date"),
                new Index().on("status", Sort.Direction.ASC).named("idx_orders_status"),
                new Index().on("orderDate", Sort.Direction.DESC).named("idx_orders_date")));
        syncSequence();
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Override
    public List<Order> findAll() throws IOException {
        return io(() -> toDomain(documents.findAll(NEWEST_FIRST)));
    }

    @Override
    public Optional<Order> findById(String id) throws IOException {
        if (id == null) return Optional.empty();
        return io(() -> documents.findById(id).flatMap(MongoOrderRepository::toDomain));
    }

    @Override
    public List<Order> findByCustomerId(String customerId) throws IOException {
        return io(() -> toDomain(documents.findByCustomerId(customerId, NEWEST_FIRST)));
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Override
    public Order save(Order order) throws IOException {
        return io(() -> {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                String id = String.format("O%03d", sequences.next(SEQUENCE));
                try {
                    documents.insert(OrderDocument.from(order, id, null));
                    order.setId(id);
                    return order;
                } catch (DuplicateKeyException e) {
                    syncSequence();   // ID taken (e.g. imported records): catch the counter up and retry
                }
            }
            throw new IOException("Could not allocate a new order ID.");
        });
    }

    @Override
    public void update(Order order) throws IOException {
        update(order.getId(), current -> order);
    }

    @Override
    public Order update(String id, UnaryOperator<Order> change) throws IOException {
        return io(() -> {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                OrderDocument stored = documents.findById(id)
                        .orElseThrow(() -> new NoSuchElementException("Order not found: " + id));
                Order current = toDomain(stored)
                        .orElseThrow(() -> new IOException("Stored order " + id + " is invalid."));
                Order updated = change.apply(current);
                if (updated == null || !id.equals(updated.getId())) {
                    throw new IllegalStateException("An update must keep the order ID.");
                }
                try {
                    documents.save(OrderDocument.from(updated, id, stored.getVersion()));
                    return updated;
                } catch (OptimisticLockingFailureException e) {
                    // Changed concurrently: re-apply the change to the latest copy (rules are re-checked).
                    RetryBackoff.pause(attempt);
                }
            }
            throw new IOException("Order " + id + " kept changing; please try again.");
        });
    }

    @Override
    public void delete(String id) throws IOException {
        io(() -> {
            if (!documents.existsById(id)) {
                throw new NoSuchElementException("Order not found: " + id);
            }
            documents.deleteById(id);
            return null;
        });
    }

    // ── Migration support (not part of OrderRepository) ───────────────────────

    /** Inserts an order with its existing ID. Never overwrites; returns false if the ID exists. */
    public boolean importOrder(Order order) throws IOException {
        return io(() -> {
            try {
                documents.insert(OrderDocument.from(order, order.getId(), null));
                return true;
            } catch (DuplicateKeyException e) {
                return false;
            }
        });
    }

    /** Moves the ID counter past the highest stored ID (never backwards). */
    public void syncSequence() {
        sequences.ensureAtLeast(SEQUENCE, MongoCollections.maxIdNumber(template, COLLECTION));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<Order> toDomain(List<OrderDocument> docs) {
        List<Order> orders = new ArrayList<>();
        for (OrderDocument doc : docs) toDomain(doc).ifPresent(orders::add);
        return orders;
    }

    private static Optional<Order> toDomain(OrderDocument doc) {
        try {
            return Optional.of(doc.toDomain());
        } catch (RuntimeException e) {
            log.warn("orders document '{}' is invalid and was skipped.", doc.getId());
            return Optional.empty();
        }
    }

    @FunctionalInterface
    private interface MongoAction<T> {
        T run() throws IOException;
    }

    /** Database failures become IOException (friendly message upstream); business exceptions pass through. */
    private static <T> T io(MongoAction<T> action) throws IOException {
        try {
            return action.run();
        } catch (DataAccessException e) {
            log.error("MongoDB order storage failure: {}", e.getClass().getSimpleName());
            throw new IOException("Order storage unavailable.", e);
        }
    }

    /** {@code $jsonSchema} validator: MongoDB itself refuses malformed order documents. */
    private static Document schema() {
        return new Document("bsonType", "object")
                .append("required", List.of("_id", "customerId", "productId", "productName", "quantity",
                        "unitPrice", "orderType", "status", "orderDate"))
                .append("properties", new Document()
                        .append("_id", MongoCollections.string("^O[0-9]{3,}$"))
                        .append("customerId", MongoCollections.string(null).append("minLength", 1))
                        .append("productId", MongoCollections.string(null).append("minLength", 1))
                        .append("productName", MongoCollections.string(null).append("minLength", 1).append("maxLength", 100))
                        .append("quantity", new Document("bsonType", List.of("int", "long")).append("minimum", 1))
                        .append("unitPrice", new Document("bsonType", List.of("double", "int", "long", "decimal"))
                                .append("minimum", 0).append("exclusiveMinimum", true))
                        .append("orderType", MongoCollections.enumOf("PICKUP", "DELIVERY"))
                        .append("deliveryAddress", MongoCollections.string(null))
                        .append("status", MongoCollections.enumOf("PENDING", "PREPARING", "READY", "DELIVERED", "CANCELLED"))
                        .append("orderDate", MongoCollections.string("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")));
    }
}
