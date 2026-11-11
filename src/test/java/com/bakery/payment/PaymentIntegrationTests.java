package com.bakery.payment;

import com.bakery.order.model.Order;
import com.bakery.order.model.OrderStatus;
import com.bakery.order.model.OrderType;
import com.bakery.order.service.OrderService;
import com.bakery.payment.integration.OrderPayableProvider;
import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.Status;
import com.bakery.payment.service.PaymentIntegration.Actor;
import com.bakery.payment.service.PaymentService;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.security.BakeryUserPrincipal;
import com.bakery.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payment Management integrated with the real User Management security and Order Management services
 * (TXT storage in a temporary folder): authentication, authorization, ownership, server-side order totals,
 * the payment lifecycle, duplicate submissions and order protection.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentIntegrationTests {

    private static final String PASSWORD = "Bakery2026";

    @TempDir static Path data;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("bakery.data-directory", () -> data.toString());
    }

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;

    private String alice, bob, admin;
    private final Map<String, MockHttpSession> sessions = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        Files.deleteIfExists(data.resolve("orders.txt"));
        Files.deleteIfExists(data.resolve("payments.txt"));
        if (users.findAll().isEmpty()) {
            userService.register("Alice Perera", "alice@example.com", "077 111 1111", "1 Lake Road, Kandy", PASSWORD, PASSWORD);
            userService.register("Bob Silva", "bob@example.com", "077 222 2222", "2 Hill Street, Galle", PASSWORD, PASSWORD);
            userService.createAdmin("Bakery Admin", "admin@example.com", "Admin2026x");
        }
        alice = users.findByEmail("alice@example.com").orElseThrow().getId();
        bob = users.findByEmail("bob@example.com").orElseThrow().getId();
        admin = users.findByEmail("admin@example.com").orElseThrow().getId();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RequestPostProcessor as(String userId) {
        return user(new BakeryUserPrincipal(users.findById(userId).orElseThrow()));
    }

    /** A signed-in browser session that has opened a payment page (so it has the payment form token). */
    private MockHttpSession session(String userId) throws Exception {
        MockHttpSession session = sessions.get(userId);
        if (session == null) {
            session = new MockHttpSession();
            mvc.perform(get("/payments").session(session).with(as(userId))).andExpect(status().isOk());
            sessions.put(userId, session);
        }
        return session;
    }

    private ResultActions submit(String userId, String path, String... params) throws Exception {
        MockHttpSession session = session(userId);
        var request = post(path).session(session).with(as(userId)).with(csrf())
                .param("csrf", (String) session.getAttribute("paymentCsrf"));
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return mvc.perform(request);
    }

    private ResultActions pay(String userId, String orderId, String purpose, String amount, String key) throws Exception {
        return submit(userId, "/payments", "type", "ORDER", "targetId", orderId, "method", "CASH",
                "purpose", purpose, "amount", amount, "requestKey", key);
    }

    private String pendingPayment(String userId, String orderId, String amount) throws Exception {
        pay(userId, orderId, "FULL", amount, UUID.randomUUID().toString()).andExpect(redirectedUrlPattern("/payments/PAY*"));
        return payments.history(new Actor(userId, false), orderId, null, null).getFirst().getId();
    }

    private String placeOrder(String customerId, int quantity, double unitPrice, OrderType type) {
        return orders.placeOrder(customerId, "P001", "Sourdough Loaf", quantity, unitPrice, type,
                type == OrderType.DELIVERY ? "1 Lake Road, Kandy" : null).getId();
    }

    // ── Authentication and authorization ──────────────────────────────────────

    @Test
    void paymentRoutesRequireSignInAndAdminPagesRequireAdmin() throws Exception {
        for (String path : new String[]{"/payments", "/payments/new", "/payments/admin", "/payments/PAY001"}) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        }
        mvc.perform(get("/payments/admin").with(as(alice))).andExpect(status().isForbidden());
        mvc.perform(post("/payments/PAY001/delete").with(as(alice)).with(csrf()).param("confirmed", "true"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/payments/admin").with(as(admin))).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Admin Payment Dashboard"),
                        containsString("a-sidebar"), containsString("href=\"/payments/admin\""))));
        mvc.perform(get("/payments").with(as(alice))).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("sf-header"), containsString("Alice Perera"))));
    }

    @Test
    void postsWithoutTheSpringCsrfTokenAreRejected() throws Exception {
        String order = placeOrder(alice, 1, 500, OrderType.PICKUP);
        MockHttpSession session = session(alice);
        mvc.perform(post("/payments").session(session).with(as(alice))
                        .param("csrf", (String) session.getAttribute("paymentCsrf"))
                        .param("type", "ORDER").param("targetId", order).param("method", "CASH")
                        .param("purpose", "FULL").param("amount", "500").param("requestKey", "no-csrf"))
                .andExpect(status().isForbidden());
        assertTrue(payments.history(new Actor(alice, false), null, null, null).isEmpty());
    }

    // ── Payments are linked to real orders, priced on the server ─────────────

    @Test
    void customerPaysOwnOrderUsingTheServerCalculatedTotal() throws Exception {
        String order = placeOrder(alice, 2, 900, OrderType.DELIVERY);       // 1800 + 250 delivery fee
        String bobOrder = placeOrder(bob, 1, 400, OrderType.PICKUP);

        mvc.perform(get("/payments/new").param("orderId", order).session(session(alice)).with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("data-total=\"2050.00\""),
                        containsString("data-customer=\"" + alice + "\" selected=\"selected\""), not(containsString(bobOrder)),
                        not(containsString("Custom cake booking")))));

        // Paying someone else's order, or more than the total, is refused and nothing is stored.
        pay(alice, bobOrder, "FULL", "400", "k-other").andExpect(status().isBadRequest());
        pay(alice, order, "PARTIAL", "2050.01", "k-over").andExpect(status().isBadRequest());
        pay(alice, order, "FULL", "2000", "k-not-full").andExpect(status().isBadRequest());
        assertTrue(payments.history(new Actor(alice, false), null, null, null).isEmpty());

        pay(alice, order, "FULL", "2050", "k-ok").andExpect(redirectedUrl("/payments/PAY001"));
        Payment recorded = payments.get(new Actor(alice, false), "PAY001");
        assertEquals(alice, recorded.getCustomerId());
        assertEquals(order, recorded.getTargetId());
        assertEquals(new BigDecimal("2050.00"), recorded.getTotal());
        assertEquals(Status.PENDING, recorded.getStatus());

        // Same form submitted twice: no second payment. Order fully reserved: no more payments.
        pay(alice, order, "FULL", "2050", "k-ok").andExpect(status().isBadRequest());
        pay(alice, order, "PARTIAL", "1", "k-more").andExpect(status().isBadRequest());
        assertEquals(1, payments.history(new Actor(alice, false), null, null, null).size());
    }

    @Test
    void doubleTotalsAreConvertedToExactMoneyAndCancelledOrdersCannotBePaid() throws Exception {
        String order = placeOrder(alice, 3, 1.10, OrderType.PICKUP);           // 3.3000000000000003 as a double
        Order stored = orders.getOwnOrder(order, alice);
        assertEquals(new BigDecimal("3.30"), OrderPayableProvider.amountDue(stored));
        pay(alice, order, "FULL", "3.30", "k-rounding").andExpect(redirectedUrlPattern("/payments/PAY*"));

        String cancelled = placeOrder(alice, 1, 500, OrderType.PICKUP);
        orders.cancelOwnOrder(cancelled, alice);
        mvc.perform(get("/payments/new").session(session(alice)).with(as(alice)))
                .andExpect(content().string(not(containsString("value=\"" + cancelled + "\""))));
        pay(alice, cancelled, "FULL", "500", "k-cancelled").andExpect(status().isBadRequest());
    }

    // ── Privacy ───────────────────────────────────────────────────────────────

    @Test
    void customersSeeOnlyTheirOwnPaymentsAndInvoices() throws Exception {
        String order = placeOrder(alice, 1, 750, OrderType.PICKUP);
        String payment = pendingPayment(alice, order, "750");

        mvc.perform(get("/payments/" + payment).with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/payments/" + payment + "/download").with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/payments").param("query", payment).with(as(bob)))     // the search box echoes Bob's own text only
                .andExpect(content().string(allOf(containsString("No payments found"),
                        not(containsString("class=\"pay-record-id\">" + payment)))));
        submit(bob, "/payments/" + payment + "/status", "status", "CANCELLED").andExpect(status().isNotFound());

        mvc.perform(get("/payments/" + payment).with(as(alice))).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("INV-" + payment), containsString("NOT A PAID RECEIPT"))));
        mvc.perform(get("/payments/admin").param("query", order).with(as(admin)))
                .andExpect(content().string(allOf(containsString(payment), containsString(alice))));
    }

    // ── Lifecycle: only admins confirm and refund; order status stays separate ─

    @Test
    void onlyAdminsConfirmAndRefundAndOrderStatusIsUnchanged() throws Exception {
        String order = placeOrder(alice, 2, 500, OrderType.PICKUP);
        String payment = pendingPayment(alice, order, "1000");

        submit(alice, "/payments/" + payment + "/status", "status", "PAID").andExpect(status().isBadRequest());
        // Even an admin must confirm that the money was found before marking it Paid.
        submit(admin, "/payments/" + payment + "/status", "status", "PAID").andExpect(status().isBadRequest());
        assertEquals(Status.PENDING, payments.get(new Actor(admin, true), payment).getStatus());
        submit(admin, "/payments/" + payment + "/status", "status", "PAID", "verified", "true").andExpect(redirectedUrl("/payments/" + payment));
        assertEquals(Status.PAID, payments.get(new Actor(admin, true), payment).getStatus());
        assertEquals(OrderStatus.PENDING, orders.getOwnOrder(order, alice).getStatus(), "payment status never changes the order");
        mvc.perform(get("/payments/" + payment + "/download").with(as(alice)))
                .andExpect(content().string(containsString("PAID RECEIPT")));

        submit(admin, "/payments/" + payment + "/delete", "confirmed", "true").andExpect(status().isBadRequest());
        submit(alice, "/payments/" + payment + "/status", "status", "REFUNDED").andExpect(status().isBadRequest());
        submit(admin, "/payments/" + payment + "/status", "status", "REFUNDED").andExpect(redirectedUrl("/payments/" + payment));
        assertEquals(Status.REFUNDED, payments.get(new Actor(alice, false), payment).getStatus());
    }

    @Test
    void hardDeleteIsOffByDefault() throws Exception {
        String order = placeOrder(alice, 2, 500, OrderType.PICKUP);
        String paid = pendingPayment(alice, order, "1000");
        submit(admin, "/payments/" + paid + "/status", "status", "PAID", "verified", "true");
        // Production-safe default: no Delete button for a Paid payment, no coursework notice, and the server refuses.
        mvc.perform(get("/payments/admin").with(as(admin))).andExpect(content().string(allOf(
                containsString("class=\"pay-record-id\">" + paid), not(containsString("data-row-delete")),
                not(containsString("Coursework mode")), containsString("Hard delete is off (production-safe setting)"))));
        submit(admin, "/payments/" + paid + "/delete", "confirmed", "true").andExpect(status().isBadRequest());
        assertEquals(Status.PAID, payments.get(new Actor(admin, true), paid).getStatus());
    }

    // ── Order protection ──────────────────────────────────────────────────────

    @Test
    void ordersWithPendingOrPaidPaymentsCannotBeEditedCancelledOrDeleted() throws Exception {
        String order = placeOrder(alice, 2, 500, OrderType.PICKUP);
        pendingPayment(alice, order, "1000");

        // The order pages no longer offer Edit / Cancel...
        mvc.perform(get("/orders/" + order).with(as(alice))).andExpect(content().string(allOf(
                containsString("A payment is recorded for this order"), not(containsString("/orders/" + order + "/edit\"")))));
        mvc.perform(get("/orders/my-orders").with(as(alice))).andExpect(content().string(allOf(
                containsString("Payment recorded: contact the bakery"), not(containsString("/orders/" + order + "/cancel\"")))));
        mvc.perform(get("/orders/" + order + "/edit").with(as(alice)))
                .andExpect(redirectedUrl("/orders/" + order))
                .andExpect(flash().attribute("errorMessage", containsString("payment recorded")));
        // ...and the server refuses them anyway.
        mvc.perform(post("/orders/" + order + "/edit").with(as(alice)).with(csrf()).param("quantity", "5"))
                .andExpect(flash().attribute("errorMessage", containsString("payment recorded")));
        mvc.perform(post("/orders/" + order + "/cancel").with(as(alice)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", containsString("payment recorded")));
        mvc.perform(post("/orders/" + order + "/status").with(as(admin)).with(csrf()).param("status", "CANCELLED"))
                .andExpect(flash().attribute("errorMessage", containsString("pending or paid payments")));
        mvc.perform(post("/orders/" + order + "/delete").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", containsString("payment records")));
        Order unchanged = orders.getOwnOrder(order, alice);
        assertEquals(2, unchanged.getQuantity());
        assertEquals(OrderStatus.PENDING, unchanged.getStatus());

        // The kitchen workflow is independent of payments.
        mvc.perform(post("/orders/" + order + "/status").with(as(admin)).with(csrf()).param("status", "PREPARING"))
                .andExpect(flash().attribute("successMessage", containsString("PREPARING")));

        // Another customer learns nothing about the order or its payments.
        mvc.perform(post("/orders/" + order + "/edit").with(as(bob)).with(csrf()).param("quantity", "1"))
                .andExpect(flash().attribute("errorMessage", "Order not found."));
    }

    @Test
    void afterAPaymentIsCancelledTheOrderCanChangeAndBePaidAtItsNewTotal() throws Exception {
        String order = placeOrder(alice, 2, 500, OrderType.PICKUP);
        String first = pendingPayment(alice, order, "1000");
        submit(alice, "/payments/" + first + "/status", "status", "CANCELLED").andExpect(redirectedUrl("/payments/" + first));

        mvc.perform(post("/orders/" + order + "/edit").with(as(alice)).with(csrf()).param("quantity", "3"))
                .andExpect(flash().attribute("successMessage", containsString("updated")));
        pay(alice, order, "FULL", "1500", "k-new-total").andExpect(redirectedUrlPattern("/payments/PAY*"));

        // Payment history is kept, so the order itself can no longer be deleted.
        mvc.perform(post("/orders/" + order + "/delete").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", containsString("payment records")));
    }

    @Test
    void orderPagesLinkToPayments() throws Exception {
        String order = placeOrder(alice, 1, 500, OrderType.PICKUP);
        mvc.perform(get("/orders/" + order).with(as(alice)))
                .andExpect(content().string(allOf(containsString("/payments/new?orderId=" + order), containsString("Pay for this order"))));
        mvc.perform(get("/orders/my-orders").with(as(alice)))
                .andExpect(content().string(containsString("/payments/new?orderId=" + order)));
        mvc.perform(get("/orders/admin").with(as(admin)))
                .andExpect(content().string(containsString("/payments/admin?query=" + order)));
    }
}
