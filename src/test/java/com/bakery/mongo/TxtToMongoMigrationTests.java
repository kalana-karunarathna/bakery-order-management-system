package com.bakery.mongo;

import com.bakery.common.persistence.migration.TxtToMongoMigration;
import com.bakery.order.repository.mongo.MongoOrderRepository;
import com.bakery.user.model.AccountStatus;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.repository.mongo.MongoUserRepository;
import com.bakery.user.service.UserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** TXT → MongoDB migration: dry run, validation, safety rules and idempotence (temporary database). */
@SpringBootTest
class TxtToMongoMigrationTests {

    @BeforeAll
    static void requireMongo() {
        assumeTrue(MongoTestSupport.isAvailable(), "Local MongoDB not running: MongoDB tests skipped");
    }

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        MongoTestSupport.register(registry);
    }

    @TempDir Path source;
    @Autowired MongoUserRepository mongoUsers;
    @Autowired MongoOrderRepository mongoOrders;
    @Autowired UserRepository users;
    @Autowired UserService userService;
    @Autowired MongoTemplate template;
    @Autowired PasswordEncoder encoder;

    private TxtToMongoMigration migration;
    private List<String> usersFile;
    private List<String> ordersFile;

    @BeforeEach
    void setUp() throws Exception {
        MongoTestSupport.clear(template);
        migration = new TxtToMongoMigration(mongoUsers, mongoOrders, "dry-run", source.toString());
        String hash = encoder.encode("Bakery2026");
        usersFile = List.of(
                "﻿# id|role|customerType|fullName|email|phone|address|passwordHash|status|createdDate",
                "U001|CUSTOMER|REGULAR|Sample Customer|customer@example.test|||-|INACTIVE|2026-10-01",
                "U002|CUSTOMER|REGULAR|Nimal Perera|nimal@example.com|0771234567|25 Galle Road, Colombo|" + hash + "|ACTIVE|2026-11-10",
                "U003|ADMIN|NONE|Bakery Admin|admin@example.com|||" + hash + "|ACTIVE|2026-11-10",
                "U004|CUSTOMER|REGULAR|Duplicate Email|nimal@example.com|||" + hash + "|ACTIVE|2026-11-10",
                "U005|CUSTOMER|BROKEN|Broken Record|broken@example.com|||" + hash + "|ACTIVE|2026-11-10");
        ordersFile = List.of(
                "# id|customerId|productId|productName|quantity|unitPrice|orderType|deliveryAddress|status|orderDate",
                "O001|U002|P001|Real Order|2|500.00|PICKUP||PENDING|2026-11-12",
                "O002|U002|P003|Old Sample Order|5|180.00|PICKUP||PREPARING|2026-10-15",
                "O003|U999|P001|Orphan Order|1|100.00|PICKUP||PENDING|2026-11-12",
                "O004|U003|P001|Admin Order|1|100.00|PICKUP||PENDING|2026-11-12",
                "O005|U002|P001|Broken|abc|1.00|PICKUP||PENDING|2026-11-12");
        Files.write(source.resolve("users.txt"), usersFile);
        Files.write(source.resolve("orders.txt"), ordersFile);
    }

    @Test
    void dryRunValidatesAndReportsButWritesNothing() throws Exception {
        TxtToMongoMigration.Report report = migration.migrate(source, false);
        assertEquals(3, report.sourceUsers());          // U001, U002, U003 (duplicate email and broken type unreadable)
        assertEquals(2, report.unreadableUserLines());
        assertEquals(1, report.unreadableOrderLines());
        assertEquals(3, report.count("user", "import"));
        assertEquals(1, report.count("order", "import"));   // only O001
        assertEquals(3, report.count("order", "skipped"));  // sample-dated, orphan, admin-owned
        assertEquals(0, template.getCollection("users").countDocuments());
        assertEquals(0, template.getCollection("orders").countDocuments());
        assertEquals(usersFile, Files.readAllLines(source.resolve("users.txt")), "source files are never changed");
        assertEquals(ordersFile, Files.readAllLines(source.resolve("orders.txt")));
    }

    @Test
    void applyImportsSafelyAndIsIdempotent() throws Exception {
        migration.migrate(source, true);
        assertEquals(3, template.getCollection("users").countDocuments());
        assertEquals(1, template.getCollection("orders").countDocuments());
        assertEquals("Real Order", mongoOrders.findById("O001").orElseThrow().getProductName());

        // Legacy account without a password cannot sign in; real accounts keep their BCrypt hash.
        assertEquals(AccountStatus.INACTIVE, users.findById("U001").orElseThrow().getStatus());
        assertFalse(users.findById("U001").orElseThrow().hasUsablePassword());
        assertTrue(encoder.matches("Bakery2026", users.findById("U002").orElseThrow().getPasswordHash()));

        // Counters continue after the imported IDs.
        assertEquals("U004", userService.register("New Person", "new@example.com", "0771112222", "1 New Road, Kandy",
                "Bakery2026", "Bakery2026").id());

        // Second run: nothing new, nothing overwritten.
        TxtToMongoMigration.Report again = migration.migrate(source, true);
        assertEquals(0, again.count("user", "import"));
        assertEquals(0, again.count("order", "import"));
        assertEquals(4, template.getCollection("users").countDocuments());
        assertEquals(1, template.getCollection("orders").countDocuments());
    }

    @Test
    void conflictsWithExistingMongoAccountsAreNeverOverwritten() throws Exception {
        // MongoDB already has nimal@example.com under a different ID.
        String existing = userService.register("Nimal Mongo", "nimal@example.com", "0779998888", "9 Other Road, Galle",
                "Bakery2026", "Bakery2026").id();
        TxtToMongoMigration.Report report = migration.migrate(source, true);
        // TXT U001 clashes on ID (MongoDB U001 is Nimal), TXT U002 clashes on email: both are skipped.
        assertEquals(2, report.count("user", "conflict"));
        assertEquals("Nimal Mongo", users.findByEmail("nimal@example.com").orElseThrow().getFullName());
        assertEquals(existing, users.findByEmail("nimal@example.com").orElseThrow().getId());
        // Nimal's TXT orders are not attached to anyone, because his TXT account was not migrated.
        assertTrue(mongoOrders.findById("O001").isEmpty());
    }
}
