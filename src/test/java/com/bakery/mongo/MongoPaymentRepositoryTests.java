package com.bakery.mongo;

import com.bakery.common.persistence.MongoSequenceGenerator;
import com.bakery.order.model.OrderType;
import com.bakery.order.service.OrderService;
import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.Method;
import com.bakery.payment.model.Payment.Purpose;
import com.bakery.payment.model.Payment.Status;
import com.bakery.payment.model.Payment.TargetType;
import com.bakery.payment.repository.PaymentRepository;
import com.bakery.payment.repository.mongo.MongoPaymentRepository;
import com.bakery.payment.repository.mongo.PaymentDocumentRepository;
import com.bakery.payment.service.PaymentIntegration.Actor;
import com.bakery.payment.service.PaymentIntegration.PayableProvider;
import com.bakery.payment.service.PaymentService;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.security.BakeryUserPrincipal;
import com.bakery.user.service.UserService;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** MongoDB payment persistence against an isolated temporary database (never bakery_db or Atlas). */
@SpringBootTest
@AutoConfigureMockMvc
class MongoPaymentRepositoryTests {

    @BeforeAll
    static void requireMongo() {
        assumeTrue(MongoTestSupport.isAvailable(), "Local MongoDB not running: MongoDB tests skipped");
    }

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        MongoTestSupport.register(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate template;
    @Autowired PaymentRepository repository;
    @Autowired PaymentDocumentRepository documents;
    @Autowired PaymentService payments;
    @Autowired PayableProvider payables;
    @Autowired OrderService orders;
    @Autowired UserService userService;
    @Autowired UserRepository users;

    private String alice;
    private Actor aliceActor;
    private final Actor admin = new Actor("U900", true);

    @BeforeEach
    void clean() {
        MongoTestSupport.clear(template);
        alice = userService.register("Alice Perera", "alice@example.com", "077 111 1111", "1 Lake Road, Kandy",
                "Bakery2026", "Bakery2026").id();
        aliceActor = new Actor(alice, false);
    }

    private String order(int quantity, double price) {
        return orders.placeOrder(alice, "P001", "Sourdough Loaf", quantity, price, OrderType.PICKUP, null).getId();
    }

    private Payment pay(String orderId, Purpose purpose, String amount) {
        return payments.create(aliceActor, TargetType.ORDER, orderId, Method.CASH, purpose, new BigDecimal(amount),
                null, UUID.randomUUID().toString());
    }

    private Document stored(String id) {
        return template.getCollection(MongoPaymentRepository.COLLECTION).find(new Document("_id", id)).first();
    }

    @Test
    void mongoImplementationIsActiveWithIndexesAndValidator() {
        assertInstanceOf(MongoPaymentRepository.class, repository);
        Set<String> indexes = new HashSet<>();
        template.indexOps(MongoPaymentRepository.COLLECTION).getIndexInfo().forEach(i -> indexes.add(i.getName()));
        assertTrue(indexes.containsAll(Set.of("uk_payments_request_key", "uk_payments_reference",
                "idx_payments_customer_created", "idx_payments_target_status", "idx_payments_status_created")));
        // MongoDB's own validator refuses a malformed payment (wrong ID, money as a string, unknown status).
        assertThrows(com.mongodb.MongoWriteException.class, () -> template.getCollection(MongoPaymentRepository.COLLECTION)
                .insertOne(new Document("_id", "X1").append("amount", "100").append("status", "MAYBE")));
    }

    @Test
    void paymentsAreStoredWithDecimal128MoneyAndSurviveARestart() {
        String orderId = order(3, 1.10);                                   // 3.30, exact
        Payment p = pay(orderId, Purpose.FULL, "3.30");
        assertEquals("PAY001", p.getId());

        Document doc = stored("PAY001");
        assertInstanceOf(Decimal128.class, doc.get("amount"));
        assertEquals(new BigDecimal("3.30"), ((Decimal128) doc.get("total")).bigDecimalValue());
        assertEquals(alice, doc.getString("customerId"));
        assertEquals("CASH-PAY001", doc.getString("referenceKey"));
        assertEquals(0L, ((Number) doc.get("version")).longValue());
        assertTrue(doc.get("createdAt") instanceof java.util.Date);

        payments.transition(admin, "PAY001", Status.PAID);
        assertEquals("PAID", stored("PAY001").getString("status"));
        assertEquals(1L, ((Number) stored("PAY001").get("version")).longValue());

        // A fresh repository (as after a restart) reads the same records and continues the ID sequence.
        MongoPaymentRepository restarted = new MongoPaymentRepository(documents, template, new MongoSequenceGenerator(template));
        PaymentService again = new PaymentService(restarted, payables);
        assertEquals(Status.PAID, again.get(aliceActor, "PAY001").getStatus());
        String second = order(1, 200);
        assertEquals("PAY002", again.create(aliceActor, TargetType.ORDER, second, Method.CASH, Purpose.FULL,
                new BigDecimal("200"), null, "after-restart").getId());
    }

    @Test
    void concurrentPaymentsForOneOrderCannotOverpay() throws Exception {
        String orderId = order(2, 500);                                    // 1000
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            attempts.add(() -> {
                try {
                    pay(orderId, Purpose.FULL, "1000");
                    return true;
                } catch (IllegalArgumentException rejected) {
                    return false;
                }
            });
        }
        int succeeded = 0;
        for (Future<Boolean> f : pool.invokeAll(attempts)) if (f.get()) succeeded++;
        pool.shutdown();
        assertEquals(1, succeeded, "only one full payment fits the order total");
        assertEquals(1, documents.count());
        // The lease is released after every unit of work.
        assertNull(template.getCollection(MongoPaymentRepository.LOCK_COLLECTION).find().first().get("owner"));
    }

    @Test
    void duplicateSubmissionsAndReferencesAreRefused() {
        String orderId = order(4, 500);                                    // 2000
        payments.create(aliceActor, TargetType.ORDER, orderId, Method.BANK_TRANSFER, Purpose.PARTIAL,
                new BigDecimal("500"), "BANK-REF-1", "same-key");
        assertThrows(IllegalArgumentException.class, () -> payments.create(aliceActor, TargetType.ORDER, orderId,
                Method.BANK_TRANSFER, Purpose.PARTIAL, new BigDecimal("500"), "BANK-REF-2", "same-key"));
        assertThrows(IllegalArgumentException.class, () -> payments.create(aliceActor, TargetType.ORDER, orderId,
                Method.BANK_TRANSFER, Purpose.PARTIAL, new BigDecimal("500"), "bank-ref-1", "other-key"));
        // Even a raw insert that bypasses the service is stopped by the unique index.
        Document copy = new Document(stored("PAY001"));
        copy.put("_id", "PAY777");
        assertThrows(com.mongodb.MongoWriteException.class,
                () -> template.getCollection(MongoPaymentRepository.COLLECTION).insertOne(copy));
        assertEquals(1, documents.count());
    }

    @Test
    void hardDeleteRemovesTheMongoDocumentAndKeepsTheIdCounter() {
        String orderId = order(2, 500);                                    // 1000
        Payment paid = pay(orderId, Purpose.PARTIAL, "600");
        payments.transition(admin, paid.getId(), Status.PAID);
        assertEquals(com.bakery.order.service.OrderPaymentSummary.Status.PARTIALLY_PAID,
                orders.paymentSummary(orders.getOwnOrder(orderId, alice)).orElseThrow().status());

        // A separate service instance in coursework mode (the shared test context keeps the safe default).
        PaymentService coursework = new PaymentService(repository, payables);
        coursework.setHardDeleteEnabled(true);
        assertThrows(IllegalArgumentException.class, () -> payments.delete(admin, paid.getId()), "default refuses Paid");
        coursework.delete(admin, paid.getId());

        assertNull(stored(paid.getId()), "document removed from MongoDB");
        assertEquals(0, documents.count());
        assertEquals(com.bakery.order.service.OrderPaymentSummary.Status.UNPAID,
                orders.paymentSummary(orders.getOwnOrder(orderId, alice)).orElseThrow().status());
        assertEquals("PAY002", pay(orderId, Purpose.PARTIAL, "100").getId(), "deleted IDs are never reused");
    }

    @Test
    void invalidStoredPaymentsFailClosedAndProtectTheOrder() throws Exception {
        String orderId = order(1, 500);
        pay(orderId, Purpose.FULL, "500");
        // Schema-valid but domain-invalid: amount above the agreed total.
        template.getCollection(MongoPaymentRepository.COLLECTION).updateOne(new Document("_id", "PAY001"),
                new Document("$set", new Document("amount", new Decimal128(new BigDecimal("900.00")))));

        assertThrows(PaymentRepository.StorageException.class, () -> payments.history(aliceActor, null, null, null));
        mvc.perform(post("/orders/" + orderId + "/edit").with(user(new BakeryUserPrincipal(users.findById(alice).orElseThrow())))
                        .with(csrf()).param("quantity", "2"))
                .andExpect(flash().attribute("errorMessage", containsString("Payment information is unavailable")));
        assertEquals(1, orders.getOwnOrder(orderId, alice).getQuantity());
    }

    @Test
    void webFlowStoresTheCustomersPaymentInMongo() throws Exception {
        String orderId = order(2, 450);                                    // 900
        var principal = user(new BakeryUserPrincipal(users.findById(alice).orElseThrow()));
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/payments/new").param("orderId", orderId).session(session).with(principal));
        mvc.perform(post("/payments").session(session).with(principal).with(csrf())
                        .param("csrf", (String) session.getAttribute("paymentCsrf"))
                        .param("type", "ORDER").param("targetId", orderId).param("method", "CARD")
                        .param("purpose", "PARTIAL").param("amount", "400").param("reference", "POS-55")
                        .param("requestKey", "web-flow").param("customerId", "U999"))
                .andExpect(redirectedUrl("/payments/PAY001"));
        Document doc = stored("PAY001");
        assertEquals(alice, doc.getString("customerId"), "the customer comes from the signed-in user, not the form");
        assertEquals("CARD", doc.getString("method"));
        assertEquals("PENDING", doc.getString("status"));
        assertEquals(new BigDecimal("900.00"), ((Decimal128) doc.get("total")).bigDecimalValue());
    }
}
