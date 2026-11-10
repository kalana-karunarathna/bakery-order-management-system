package com.bakery.mongo;

import com.bakery.common.persistence.MongoSequenceGenerator;
import com.bakery.user.model.AccountStatus;
import com.bakery.user.model.AdminUser;
import com.bakery.user.model.CustomerType;
import com.bakery.user.model.LoyaltyCustomer;
import com.bakery.user.model.User;
import com.bakery.user.repository.DuplicateEmailException;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.repository.mongo.MongoUserRepository;
import com.bakery.user.repository.mongo.UserDocumentRepository;
import com.bakery.user.service.UserService;
import com.bakery.user.service.UserValidationException;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** MongoDB user persistence against an isolated temporary database (never bakery_db). */
@SpringBootTest
class MongoUserRepositoryTests {

    private static final String PASSWORD = "Bakery2026";

    @BeforeAll
    static void requireMongo() {
        assumeTrue(MongoTestSupport.isAvailable(), "Local MongoDB not running: MongoDB tests skipped");
    }

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        MongoTestSupport.register(registry);
    }

    @Autowired UserRepository repository;
    @Autowired UserService userService;
    @Autowired MongoTemplate template;
    @Autowired UserDocumentRepository documents;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void clean() {
        MongoTestSupport.clear(template);
    }

    private String register(String name, String email) {
        return userService.register(name, email, "077 123 4567", "12 Lake Road, Kandy", PASSWORD, PASSWORD).id();
    }

    @Test
    void mongoImplementationIsActiveAndUsesTheTestDatabase() {
        assertInstanceOf(MongoUserRepository.class, repository);
        assertTrue(template.getDb().getName().startsWith("bakery_test_"));
    }

    @Test
    void registrationIsStoredWithSequentialIdsAndBcryptHash() {
        assertEquals("U001", register("Nimal Perera", "Nimal@Example.com"));
        assertEquals("U002", register("Kamala Silva", "kamala@example.com"));

        Document doc = template.getCollection("users").find(new Document("_id", "U001")).first();
        assertEquals("nimal@example.com", doc.getString("email"));
        assertEquals("CUSTOMER", doc.getString("role"));
        assertEquals("REGULAR", doc.getString("customerType"));
        assertEquals("ACTIVE", doc.getString("status"));
        assertTrue(doc.getString("passwordHash").startsWith("$2a$"));
        assertFalse(doc.toJson().contains(PASSWORD), "plain password must never be stored");
        assertTrue(encoder.matches(PASSWORD, repository.findByEmail("NIMAL@example.com").orElseThrow().getPasswordHash()));
    }

    @Test
    void emailsAreUniqueIncludingAtDatabaseLevel() {
        register("Nimal Perera", "nimal@example.com");
        assertThrows(UserValidationException.class, () -> register("Other Person", "NIMAL@example.com"));
        // Bypassing the service: the unique index still refuses it.
        User twin = repository.findById("U001").orElseThrow();
        User copy = new LoyaltyCustomer(null, "Twin Person", twin.getEmail(), "", "", twin.getPasswordHash(),
                AccountStatus.ACTIVE, twin.getCreatedDate());
        assertThrows(DuplicateEmailException.class, () -> repository.save(copy));
        assertEquals(1, documents.count());
        assertTrue(template.indexOps("users").getIndexInfo().stream().anyMatch(i -> i.getName().equals("uk_users_email") && i.isUnique()));
    }

    @Test
    void concurrentRegistrationsGetUniqueIds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int n = i;
            futures.add(pool.submit(() -> register("Customer Number", "customer" + n + "@example.com")));
        }
        Set<String> ids = ConcurrentHashMap.newKeySet();
        for (Future<String> f : futures) ids.add(f.get());
        pool.shutdown();
        assertEquals(20, ids.size());
        assertEquals(20, documents.count());
    }

    @Test
    void concurrentRegistrationsWithTheSameEmailCreateOnlyOneAccount() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger ok = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                try {
                    register("Same Person", "same@example.com");
                    ok.incrementAndGet();
                } catch (UserValidationException expected) {
                    // duplicate email
                }
                return null;
            });
        }
        pool.invokeAll(tasks);
        pool.shutdown();
        assertEquals(1, ok.get());
        assertEquals(1, documents.count());
    }

    @Test
    void profilePasswordStatusAndTypeChangesPersist() {
        String id = register("Nimal Perera", "nimal@example.com");
        userService.updateOwnProfile(id, "Nimal K Perera", "nimal.k@example.com", "071 555 0000", "45 Temple Street, Kandy");
        userService.changePassword(id, PASSWORD, "NewBakery2027", "NewBakery2027");
        userService.setCustomerActive(id, false);
        userService.adminUpdateCustomer(id, "Nimal K Perera", "nimal.k@example.com", "071 555 0000",
                "45 Temple Street, Kandy", CustomerType.LOYALTY);

        User stored = repository.findById(id).orElseThrow();
        assertInstanceOf(LoyaltyCustomer.class, stored);
        assertEquals("nimal.k@example.com", stored.getEmail());
        assertEquals(AccountStatus.INACTIVE, stored.getStatus());
        assertTrue(encoder.matches("NewBakery2027", stored.getPasswordHash()));
        assertFalse(encoder.matches(PASSWORD, stored.getPasswordHash()));
        long version = ((Number) template.getCollection("users").find(new Document("_id", id)).first().get("version")).longValue();
        assertTrue(version >= 4, "each update increments the optimistic-locking version");

        userService.setCustomerActive(id, true);
        assertEquals(AccountStatus.ACTIVE, repository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void deletionAnonymisesAndIdsAreNeverReused() {
        String id = register("Nimal Perera", "nimal@example.com");
        userService.deleteOwnAccount(id, PASSWORD);
        User deleted = repository.findById(id).orElseThrow();
        assertEquals(AccountStatus.DELETED, deleted.getStatus());
        assertEquals("Deleted user", deleted.getFullName());
        assertEquals("U002", register("Nimal Perera", "nimal@example.com"));
    }

    @Test
    void lastAdminRuleAndAdminSubclassWork() {
        String admin = userService.createAdmin("Bakery Admin", "admin@example.com", "Admin2026x").id();
        assertInstanceOf(AdminUser.class, repository.findById(admin).orElseThrow());
        assertThrows(IllegalStateException.class, () -> userService.deleteOwnAccount(admin, "Admin2026x"));
    }

    @Test
    void dataAndIdCounterSurviveARestart() {
        register("Nimal Perera", "nimal@example.com");
        register("Kamala Silva", "kamala@example.com");
        // Simulate a restart: brand-new repository objects over the same database.
        MongoUserRepository restarted = new MongoUserRepository(documents, template, new MongoSequenceGenerator(template));
        assertEquals(2, restarted.findAll().size());
        User saved = restarted.save(new LoyaltyCustomer(null, "New Person", "new@example.com", "", "",
                repository.findById("U001").orElseThrow().getPasswordHash(), AccountStatus.ACTIVE, java.time.LocalDate.now()));
        assertEquals("U003", saved.getId());
    }

    @Test
    void databaseRejectsMalformedDocumentsAndInvalidDocumentsAreSkippedOnRead() {
        // Schema validator: missing required fields / wrong enum value.
        assertThrows(com.mongodb.MongoWriteException.class, () -> template.getCollection("users")
                .insertOne(new Document("_id", "U900").append("role", "SUPERUSER").append("email", "x@example.com")));
        // Passes the schema but breaks a domain rule (digits in the name): skipped, not fatal.
        register("Nimal Perera", "nimal@example.com");
        template.getCollection("users").insertOne(new Document("_id", "U901").append("role", "CUSTOMER")
                .append("customerType", "REGULAR").append("fullName", "R2D2 Robot").append("email", "r2@example.com")
                .append("phone", "").append("address", "").append("passwordHash", "-").append("status", "INACTIVE")
                .append("createdDate", "2026-11-10"));
        assertEquals(1, repository.findAll().size());
    }
}
