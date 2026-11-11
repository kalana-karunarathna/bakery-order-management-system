package com.bakery.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Isolated MongoDB for tests: a random {@code bakery_test_xxxxxxxx} database on the local server.
 * The main {@code bakery_db} database is never used. All MongoDB test classes share this database (and so one
 * Spring context); each test clears only its own collections, and the database is dropped when the JVM exits.
 * If no local MongoDB is running, the MongoDB tests are skipped (not failed).
 */
public final class MongoTestSupport {

    public static final String DATABASE = "bakery_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    public static final String URI = "mongodb://127.0.0.1:27017/" + DATABASE;
    private static final List<String> COLLECTIONS = List.of("users", "orders", "counters", "payments", "payment_locks");
    private static Boolean available;

    private MongoTestSupport() {
    }

    /** True if a local MongoDB answers within 2 seconds. */
    public static synchronized boolean isAvailable() {
        if (available == null) {
            try (MongoClient client = client()) {
                client.getDatabase("admin").runCommand(new Document("ping", 1));
                available = true;
                Runtime.getRuntime().addShutdownHook(new Thread(MongoTestSupport::dropTestDatabase));
            } catch (RuntimeException e) {
                available = false;
            }
        }
        return available;
    }

    /** Points a Spring test context at the temporary database in MongoDB storage mode. */
    public static void register(DynamicPropertyRegistry registry) {
        registry.add("bakery.storage.type", () -> "mongodb");
        registry.add("spring.data.mongodb.uri", () -> URI);
    }

    /** Empties the test collections (keeps their indexes and validators). */
    public static void clear(MongoTemplate template) {
        requireTestDatabase(template.getDb().getName());
        for (String collection : COLLECTIONS) {
            template.remove(new Query(), collection);
        }
    }

    private static void dropTestDatabase() {
        requireTestDatabase(DATABASE);
        try (MongoClient client = client()) {
            client.getDatabase(DATABASE).drop();
        } catch (RuntimeException ignored) {
            // best effort at shutdown
        }
    }

    private static void requireTestDatabase(String name) {
        if (!name.startsWith("bakery_test_") || name.equals("bakery_db")) {
            throw new IllegalStateException("Refusing to modify non-test database " + name);
        }
    }

    private static MongoClient client() {
        return MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(URI))
                .applyToClusterSettings(c -> c.serverSelectionTimeout(2, TimeUnit.SECONDS))
                .build());
    }
}
