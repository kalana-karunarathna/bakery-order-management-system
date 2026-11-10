package com.bakery.user.repository.mongo;

import com.bakery.common.persistence.MongoCollections;
import com.bakery.common.persistence.MongoSequenceGenerator;
import com.bakery.common.persistence.RetryBackoff;
import com.bakery.user.model.User;
import com.bakery.user.model.UserInputRules;
import com.bakery.user.repository.DuplicateEmailException;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.repository.UserStorageException;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * MongoDB implementation of {@link UserRepository} ({@code users} collection).
 *
 * <ul>
 *   <li><b>IDs:</b> U001, U002... from the atomic {@code counters} sequence, so concurrent registrations
 *       never share an ID and IDs are never reused (deleted accounts keep theirs).</li>
 *   <li><b>Emails:</b> stored normalised; the unique index {@value #EMAIL_INDEX} rejects duplicates even
 *       under concurrent registrations ({@link DuplicateEmailException}).</li>
 *   <li><b>Updates:</b> optimistic locking via the document version; a conflicting update is retried
 *       on the latest copy instead of overwriting it.</li>
 *   <li><b>Errors:</b> database failures become {@link UserStorageException} with a safe message;
 *       driver details are only logged.</li>
 * </ul>
 * Active when {@code bakery.storage.type=mongodb}.
 */
@Repository
@ConditionalOnProperty(name = "bakery.storage.type", havingValue = "mongodb")
public class MongoUserRepository implements UserRepository {

    public static final String COLLECTION = "users";
    public static final String SEQUENCE = "users";
    public static final String EMAIL_INDEX = "uk_users_email";
    private static final int MAX_ATTEMPTS = RetryBackoff.MAX_ATTEMPTS;
    private static final String UNAVAILABLE = "We couldn't reach the account records just now. Please try again.";

    private static final Logger log = LoggerFactory.getLogger(MongoUserRepository.class);

    private final UserDocumentRepository documents;
    private final MongoTemplate template;
    private final MongoSequenceGenerator sequences;

    public MongoUserRepository(UserDocumentRepository documents, MongoTemplate template, MongoSequenceGenerator sequences) {
        this.documents = documents;
        this.template = template;
        this.sequences = sequences;
    }

    /** Creates the collection, schema validator and indexes (idempotent) and aligns the ID counter. */
    @PostConstruct
    void initialise() {
        guard(() -> {
            MongoCollections.ensureCollection(template, COLLECTION, schema(), List.of(
                    new Index().on("email", Sort.Direction.ASC).unique().named(EMAIL_INDEX),
                    new Index().on("role", Sort.Direction.ASC).on("status", Sort.Direction.ASC).named("idx_users_role_status")));
            syncSequence();
            return null;
        });
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Override
    public List<User> findAll() {
        return guard(() -> {
            List<User> users = new ArrayList<>();
            for (UserDocument doc : documents.findAll(Sort.by("id"))) {
                toDomain(doc).ifPresent(users::add);
            }
            return List.copyOf(users);
        });
    }

    @Override
    public Optional<User> findById(String id) {
        if (id == null) return Optional.empty();
        return guard(() -> documents.findById(id).flatMap(MongoUserRepository::toDomain));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String normalized = UserInputRules.normalizeEmail(email);
        return guard(() -> documents.findByEmail(normalized).flatMap(MongoUserRepository::toDomain));
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Override
    public User save(User newUser) {
        if (newUser.getId() != null) {
            throw new IllegalArgumentException("New accounts must not have an ID yet.");
        }
        return guard(() -> {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                String id = String.format("U%03d", sequences.next(SEQUENCE));
                try {
                    documents.insert(UserDocument.from(newUser, id, null));
                    newUser.assignId(id);   // only after the record is safely stored
                    return newUser;
                } catch (DuplicateKeyException e) {
                    if (isEmailConflict(e)) throw new DuplicateEmailException();
                    // The ID already exists (e.g. records imported by hand): catch the counter up and retry.
                    syncSequence();
                }
            }
            throw new UserStorageException(UNAVAILABLE, null);
        });
    }

    @Override
    public User update(String id, UserChange change) {
        return guard(() -> {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                UserDocument stored = documents.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Account not found."));
                User current = toDomain(stored)
                        .orElseThrow(() -> new UserStorageException("This account record can't be read.", null));
                User updated = change.apply(current, findAll());
                if (updated == null || !id.equals(updated.getId())) {
                    throw new IllegalStateException("A change must keep the account's ID.");
                }
                try {
                    documents.save(UserDocument.from(updated, id, stored.getVersion()));
                    return updated;
                } catch (OptimisticLockingFailureException e) {
                    // Someone else changed this account meanwhile: apply the change again to the latest copy.
                    RetryBackoff.pause(attempt);
                } catch (DuplicateKeyException e) {
                    if (isEmailConflict(e)) throw new DuplicateEmailException();
                    throw e;
                }
            }
            throw new UserStorageException("The account was changed by someone else. Please try again.", null);
        });
    }

    // ── Migration support (not part of UserRepository) ────────────────────────

    /**
     * Inserts an account with its existing ID (TXT → MongoDB migration). Never overwrites.
     *
     * @return false if an account with that ID or email already exists
     */
    public boolean importUser(User user) {
        if (user.getId() == null) throw new IllegalArgumentException("Imported accounts must keep their ID.");
        return guard(() -> {
            try {
                documents.insert(UserDocument.from(user, user.getId(), null));
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

    private static Optional<User> toDomain(UserDocument doc) {
        try {
            return Optional.of(doc.toDomain());
        } catch (RuntimeException e) {
            // ID only: the document may contain personal data or a password hash.
            log.warn("users document '{}' is invalid and was skipped.", doc.getId());
            return Optional.empty();
        }
    }

    private static boolean isEmailConflict(DuplicateKeyException e) {
        String message = e.getMessage();
        return message != null && message.contains(EMAIL_INDEX);
    }

    /** Turns database failures into a safe, user-facing storage error; business exceptions pass through. */
    private static <T> T guard(Supplier<T> action) {
        try {
            return action.get();
        } catch (DuplicateKeyException e) {
            throw new UserStorageException(UNAVAILABLE, e);
        } catch (DataAccessException e) {
            log.error("MongoDB user storage failure: {}", e.getClass().getSimpleName());
            throw new UserStorageException(UNAVAILABLE, e);
        }
    }

    /** {@code $jsonSchema} validator: MongoDB itself refuses malformed account documents. */
    private static Document schema() {
        return new Document("bsonType", "object")
                .append("required", List.of("_id", "role", "customerType", "fullName", "email",
                        "passwordHash", "status", "createdDate"))
                .append("properties", new Document()
                        .append("_id", MongoCollections.string("^U[0-9]{3,}$"))
                        .append("role", MongoCollections.enumOf("CUSTOMER", "ADMIN"))
                        .append("customerType", MongoCollections.enumOf("REGULAR", "LOYALTY", "NONE"))
                        .append("fullName", MongoCollections.string(null).append("minLength", 1).append("maxLength", 100))
                        .append("email", MongoCollections.string("^[^@\\s]+@[^@\\s]+$").append("maxLength", 254))
                        .append("phone", MongoCollections.string(null))
                        .append("address", MongoCollections.string(null))
                        .append("passwordHash", MongoCollections.string(null).append("minLength", 1))
                        .append("status", MongoCollections.enumOf("ACTIVE", "INACTIVE", "DELETED"))
                        .append("createdDate", MongoCollections.string("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")));
    }
}
