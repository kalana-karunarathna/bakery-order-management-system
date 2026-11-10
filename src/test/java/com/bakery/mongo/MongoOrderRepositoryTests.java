package com.bakery.mongo;

import com.bakery.common.persistence.MongoSequenceGenerator;
import com.bakery.order.model.DeliveryOrder;
import com.bakery.order.model.Order;
import com.bakery.order.model.OrderStatus;
import com.bakery.order.model.OrderType;
import com.bakery.order.model.PickupOrder;
import com.bakery.order.repository.OrderRepository;
import com.bakery.order.repository.mongo.MongoOrderRepository;
import com.bakery.order.repository.mongo.OrderDocumentRepository;
import com.bakery.order.service.OrderNotFoundException;
import com.bakery.order.service.OrderService;
import com.bakery.user.service.AuthenticatedUser;
import com.bakery.user.model.AccountStatus;
import com.bakery.user.model.CustomerType;
import com.bakery.user.model.UserRole;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** MongoDB order persistence against an isolated temporary database (never bakery_db). */
@SpringBootTest
class MongoOrderRepositoryTests {

    @BeforeAll
    static void requireMongo() {
        assumeTrue(MongoTestSupport.isAvailable(), "Local MongoDB not running: MongoDB tests skipped");
    }

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        MongoTestSupport.register(registry);
    }

    @Autowired OrderRepository repository;
    @Autowired OrderService orderService;
    @Autowired MongoTemplate template;
    @Autowired OrderDocumentRepository documents;

    private static final AuthenticatedUser ALICE = new AuthenticatedUser("U001", "alice@example.com", "Alice Perera",
            UserRole.CUSTOMER, CustomerType.REGULAR, AccountStatus.ACTIVE);
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser("U009", "admin@example.com", "Bakery Admin",
            UserRole.ADMIN, CustomerType.NONE, AccountStatus.ACTIVE);

    @BeforeEach
    void clean() {
        MongoTestSupport.clear(template);
    }

    @Test
    void mongoImplementationIsActive() {
        assertInstanceOf(MongoOrderRepository.class, repository);
    }

    @Test
    void ordersAreStoredWithServerCalculatedTotalsAndSubclassesSurviveTheRoundTrip() throws Exception {
        Order delivery = orderService.placeOrder("U001", "P001", "Sourdough", 2, 900, OrderType.DELIVERY, "1 Lake Road, Kandy");
        Order pickup = orderService.placeOrder("U001", "P002", "Croissant", 3, 180, OrderType.PICKUP, null);
        assertEquals("O001", delivery.getId());
        assertEquals("O002", pickup.getId());

        Document doc = template.getCollection("orders").find(new Document("_id", "O001")).first();
        assertEquals("U001", doc.getString("customerId"));
        assertEquals("DELIVERY", doc.getString("orderType"));
        assertEquals("PENDING", doc.getString("status"));
        assertFalse(doc.containsKey("total"), "totals are calculated, never stored");

        assertInstanceOf(DeliveryOrder.class, repository.findById("O001").orElseThrow());
        assertInstanceOf(PickupOrder.class, repository.findById("O002").orElseThrow());
        assertEquals(2050.0, repository.findById("O001").orElseThrow().calculateTotal(), 0.001);
    }

    @Test
    void customerQueriesOwnershipAndIndexes() throws Exception {
        orderService.placeOrder("U001", "P001", "Alice Cake", 1, 100, OrderType.PICKUP, null);
        String bobOrder = orderService.placeOrder("U002", "P002", "Bob Cake", 1, 100, OrderType.PICKUP, null).getId();

        assertEquals(List.of("Alice Cake"), orderService.getOrdersForCustomer("U001").stream().map(Order::getProductName).toList());
        assertThrows(OrderNotFoundException.class, () -> orderService.getOrderFor(bobOrder, ALICE));
        assertEquals("Bob Cake", orderService.getOrderFor(bobOrder, ADMIN).getProductName());
        assertThrows(OrderNotFoundException.class, () -> orderService.cancelOwnOrder(bobOrder, "U001"));
        assertEquals(OrderStatus.PENDING, repository.findById(bobOrder).orElseThrow().getStatus());

        Set<String> indexes = new java.util.HashSet<>();
        template.indexOps("orders").getIndexInfo().forEach(i -> indexes.add(i.getName()));
        assertTrue(indexes.containsAll(Set.of("idx_orders_customer_date", "idx_orders_status", "idx_orders_date")));
    }

    @Test
    void statusWorkflowEditCancelAndDelete() throws Exception {
        String id = orderService.placeOrder("U001", "P001", "Cake", 2, 500, OrderType.DELIVERY, "1 Lake Road, Kandy").getId();
        orderService.updateOwnOrder(id, "U001", 4, "9 New Road, Kandy");
        assertEquals(4, repository.findById(id).orElseThrow().getQuantity());

        orderService.updateStatus(id, OrderStatus.PREPARING);
        assertThrows(IllegalStateException.class, () -> orderService.updateStatus(id, OrderStatus.PENDING));
        assertThrows(IllegalStateException.class, () -> orderService.cancelOwnOrder(id, "U001"));
        orderService.updateStatus(id, OrderStatus.READY);
        orderService.updateStatus(id, OrderStatus.DELIVERED);
        assertEquals(OrderStatus.DELIVERED, repository.findById(id).orElseThrow().getStatus());

        String second = orderService.placeOrder("U001", "P002", "Bread", 1, 200, OrderType.PICKUP, null).getId();
        orderService.cancelOwnOrder(second, "U001");
        assertEquals(OrderStatus.CANCELLED, repository.findById(second).orElseThrow().getStatus());

        orderService.deleteOrder(second);
        assertTrue(repository.findById(second).isEmpty());
        assertThrows(OrderNotFoundException.class, () -> orderService.deleteOrder(second));
    }

    @Test
    void concurrentOrdersGetUniqueIds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            futures.add(pool.submit(() -> orderService.placeOrder("U001", "P001", "Cookies", 1, 100, OrderType.PICKUP, null).getId()));
        }
        Set<String> ids = ConcurrentHashMap.newKeySet();
        for (Future<String> f : futures) ids.add(f.get());
        pool.shutdown();
        assertEquals(25, ids.size());
        assertEquals(25, documents.count());
    }

    @Test
    void concurrentChangesToOneOrderAreNotLost() throws Exception {
        String id = orderService.placeOrder("U001", "P001", "Cake", 1, 100, OrderType.PICKUP, null).getId();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 2; i <= 9; i++) {
            int qty = i;
            futures.add(pool.submit(() -> {
                orderService.updateOwnOrder(id, "U001", qty, null);
                return null;
            }));
        }
        for (Future<?> f : futures) f.get();
        pool.shutdown();
        long version = ((Number) template.getCollection("orders").find(new Document("_id", id)).first().get("version")).longValue();
        assertEquals(8, version, "every one of the 8 updates was applied in turn (optimistic locking + retry)");
    }

    @Test
    void dataAndIdCounterSurviveARestart() throws Exception {
        orderService.placeOrder("U001", "P001", "Cake", 1, 100, OrderType.PICKUP, null);
        orderService.placeOrder("U001", "P002", "Bread", 1, 100, OrderType.PICKUP, null);
        orderService.deleteOrder("O002");
        MongoOrderRepository restarted = new MongoOrderRepository(documents, template, new MongoSequenceGenerator(template));
        assertEquals(1, restarted.findAll().size());
        Order next = restarted.save(new PickupOrder(null, "U001", "P003", "Rolls", 1, 50, OrderStatus.PENDING, java.time.LocalDate.now()));
        assertEquals("O003", next.getId(), "deleted IDs are never reused");
    }

    @Test
    void invalidInputAndMalformedDocumentsCannotCorruptStorage() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder("U001", "P001", "Bad | name", 1, 100, OrderType.PICKUP, null));
        assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder("U001", "P001", "Cake", 0, 100, OrderType.PICKUP, null));
        assertEquals(0, documents.count());

        // MongoDB's own validator refuses a malformed document...
        assertThrows(com.mongodb.MongoWriteException.class, () -> template.getCollection("orders")
                .insertOne(new Document("_id", "O900").append("customerId", "U001").append("quantity", -1)));
        // ...and a schema-valid but domain-invalid one (delivery without address) is skipped on read.
        orderService.placeOrder("U001", "P001", "Cake", 1, 100, OrderType.PICKUP, null);
        template.getCollection("orders").insertOne(new Document("_id", "O901").append("customerId", "U001")
                .append("productId", "P1").append("productName", "Ghost").append("quantity", 1).append("unitPrice", 10.0)
                .append("orderType", "DELIVERY").append("deliveryAddress", "").append("status", "PENDING")
                .append("orderDate", "2026-11-10"));
        assertEquals(1, repository.findAll().size());
    }
}
