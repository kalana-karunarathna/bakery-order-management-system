package com.bakery.payment;

import com.bakery.order.model.OrderStatus;
import com.bakery.order.model.OrderType;
import com.bakery.order.model.PickupOrder;
import com.bakery.order.service.OrderPaymentSummary;
import com.bakery.order.service.OrderService;
import com.bakery.payment.integration.PaymentOrderSummaryProvider;
import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.Method;
import com.bakery.payment.model.Payment.Purpose;
import com.bakery.payment.model.Payment.Status;
import com.bakery.payment.model.Payment.TargetType;
import com.bakery.payment.repository.PaymentRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Payment summary on the order pages: calculated on the server from the payment records (Paid counts,
 * Pending is awaiting confirmation, Refunded is not counted), never changes the order status, and is only
 * shown to the order's owner or an admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderPaymentSummaryTests {

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

    private RequestPostProcessor as(String userId) {
        return user(new BakeryUserPrincipal(users.findById(userId).orElseThrow()));
    }

    /** The scenario from the bug report: 1 x 1,200 delivered (+ 250 fee) = LKR 1,450. */
    private String order1450() {
        return orders.placeOrder(alice, "P002", "Ribbon Cake", 1, 1200, OrderType.DELIVERY, "1 Lake Road, Kandy").getId();
    }

    private String record(String orderId, Purpose purpose, String amount) {
        return payments.create(new Actor(alice, false), TargetType.ORDER, orderId, Method.CASH, purpose,
                new BigDecimal(amount), null, UUID.randomUUID().toString()).getId();
    }

    private void confirm(String paymentId, Status status) {
        payments.transition(new Actor(admin, true), paymentId, status);
    }

    private OrderPaymentSummary summary(String orderId) {
        return orders.paymentSummary(orders.getOwnOrder(orderId, alice)).orElseThrow();
    }

    // ── Status rules (pure calculation) ──────────────────────────────────────

    @Test
    void statusIsDerivedFromConfirmedAndPendingAmounts() {
        BigDecimal total = new BigDecimal("1450");
        assertEquals(OrderPaymentSummary.Status.UNPAID, OrderPaymentSummary.of(total, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO).status());
        assertEquals(OrderPaymentSummary.Status.PAYMENT_PENDING, OrderPaymentSummary.of(total, BigDecimal.ZERO, new BigDecimal("1450"), BigDecimal.ZERO).status());
        OrderPaymentSummary partial = OrderPaymentSummary.of(total, new BigDecimal("1400"), new BigDecimal("30"), BigDecimal.ZERO);
        assertEquals(OrderPaymentSummary.Status.PARTIALLY_PAID, partial.status());
        assertEquals(new BigDecimal("50.00"), partial.getOutstanding());
        assertEquals(new BigDecimal("20.00"), partial.getPayable(), "pending amounts are reserved, not paid");
        OrderPaymentSummary paid = OrderPaymentSummary.of(total, new BigDecimal("1450"), BigDecimal.ZERO, new BigDecimal("100"));
        assertEquals(OrderPaymentSummary.Status.PAID, paid.status());
        assertEquals(new BigDecimal("0.00"), paid.getOutstanding());
    }

    @Test
    void unreadablePaymentRecordsShowAsUnavailableWithoutAPaymentAction() {
        PaymentRepository broken = new PaymentRepository() {
            public <T> T transaction(Function<List<Payment>, T> action, boolean write) { throw new StorageException("down", null); }
            public String nextId() { throw new IllegalStateException(); }
            public List<Payment> findByTarget(TargetType type, String targetId) { throw new StorageException("down", null); }
        };
        OrderPaymentSummary s = new PaymentOrderSummaryProvider(broken).summarize(
                new PickupOrder("O001", "U001", "P001", "Bread", 2, 100, OrderStatus.PENDING, LocalDate.now()));
        assertEquals(OrderPaymentSummary.Status.UNAVAILABLE, s.status());
        assertFalse(s.isAvailable());
        assertEquals(new BigDecimal("200.00"), s.total());
    }

    // ── Scenarios on the real pages ──────────────────────────────────────────

    @Test
    void unpaidOrderOffersToPayTheWholeTotal() throws Exception {
        String order = order1450();
        assertEquals(OrderPaymentSummary.Status.UNPAID, summary(order).status());
        mvc.perform(get("/orders/" + order).with(as(alice))).andExpect(content().string(allOf(
                containsString("Payment summary"), containsString("data-status=\"UNPAID\""),
                containsString("Pay for this order (LKR 1,450.00)"), containsString("/payments/new?orderId=" + order))));
    }

    @Test
    void partiallyPaidOrderShowsConfirmedPaidOutstandingAndPayRemainingBalance() throws Exception {
        String order = order1450();
        confirm(record(order, Purpose.PARTIAL, "1400"), Status.PAID);

        OrderPaymentSummary s = summary(order);
        assertEquals(OrderPaymentSummary.Status.PARTIALLY_PAID, s.status());
        assertEquals(new BigDecimal("1400.00"), s.paid());
        assertEquals(new BigDecimal("50.00"), s.getOutstanding());
        assertEquals(OrderStatus.PENDING, orders.getOwnOrder(order, alice).getStatus(), "payment never moves the order forward");

        mvc.perform(get("/orders/" + order).with(as(alice))).andExpect(content().string(allOf(
                containsString("data-status=\"PARTIALLY_PAID\""), containsString("Partially paid"),
                containsString("LKR 1,450.00"), containsString("LKR 1,400.00"),
                containsString("Pay remaining balance (LKR 50.00)"))));
        // The payment form opens with the remaining balance filled in.
        mvc.perform(get("/payments/new").param("orderId", order).with(as(alice))).andExpect(content().string(allOf(
                containsString("value=\"50.00\""), containsString("<option value=\"FULL\" selected=\"selected\">"))));
        mvc.perform(get("/orders/my-orders").with(as(alice))).andExpect(content().string(allOf(
                containsString("LKR 50.00 outstanding"), containsString("Pay balance"))));
    }

    @Test
    void pendingPaymentsAreAwaitingConfirmationNotPaid() throws Exception {
        String full = order1450();
        record(full, Purpose.FULL, "1450");
        OrderPaymentSummary s = summary(full);
        assertEquals(OrderPaymentSummary.Status.PAYMENT_PENDING, s.status());
        assertEquals(new BigDecimal("0.00"), s.paid());
        assertEquals(new BigDecimal("1450.00"), s.getOutstanding());
        assertEquals(new BigDecimal("0.00"), s.getPayable());
        mvc.perform(get("/orders/" + full).with(as(alice))).andExpect(content().string(allOf(
                containsString("Payment pending"), containsString("Awaiting confirmation"),
                containsString("will count as paid once the bakery confirms it"), not(containsString("data-pay-balance")))));

        String part = order1450();
        record(part, Purpose.PARTIAL, "1000");
        mvc.perform(get("/orders/" + part).with(as(alice)))
                .andExpect(content().string(containsString("Pay remaining balance (LKR 450.00)")));
    }

    @Test
    void fullyPaidOrderHasNoBalanceAndNoPayAction() throws Exception {
        String order = order1450();
        confirm(record(order, Purpose.FULL, "1450"), Status.PAID);
        assertEquals(OrderPaymentSummary.Status.PAID, summary(order).status());
        assertEquals(OrderStatus.PENDING, orders.getOwnOrder(order, alice).getStatus());
        mvc.perform(get("/orders/" + order).with(as(alice))).andExpect(content().string(allOf(
                containsString("data-status=\"PAID\""), containsString("LKR 0.00"), not(containsString("data-pay-balance")),
                // existing protection still applies
                containsString("A payment is recorded for this order"), not(containsString("/orders/" + order + "/edit\"")))));
        mvc.perform(get("/orders/my-orders").with(as(alice))).andExpect(content().string(allOf(
                containsString("data-status=\"PAID\""), not(containsString("data-pay-balance")))));
    }

    @Test
    void refundedPaymentsAreNotCountedAsPaid() throws Exception {
        String order = order1450();
        String payment = record(order, Purpose.FULL, "1450");
        confirm(payment, Status.PAID);
        confirm(payment, Status.REFUNDED);
        OrderPaymentSummary s = summary(order);
        assertEquals(OrderPaymentSummary.Status.UNPAID, s.status());
        assertEquals(new BigDecimal("1450.00"), s.refunded());
        assertEquals(new BigDecimal("1450.00"), s.getOutstanding());
        mvc.perform(get("/orders/" + order).with(as(alice))).andExpect(content().string(allOf(
                containsString("Refunded"), containsString("not counted as paid"),
                containsString("Pay for this order (LKR 1,450.00)"))));
    }

    @Test
    void cancelledOrdersShowTheirPaymentsButOfferNoPayment() throws Exception {
        String order = order1450();
        orders.cancelOwnOrder(order, alice);
        mvc.perform(get("/orders/" + order).with(as(alice))).andExpect(content().string(allOf(
                containsString("data-status=\"UNPAID\""), not(containsString("data-pay-balance")))));
    }

    // ── Ownership ────────────────────────────────────────────────────────────

    @Test
    void anotherCustomerNeverSeesTheOrderOrItsPayments() throws Exception {
        String order = order1450();
        confirm(record(order, Purpose.PARTIAL, "1400"), Status.PAID);

        mvc.perform(get("/orders/" + order).with(as(bob)))
                .andExpect(redirectedUrl("/orders/my-orders"))
                .andExpect(flash().attribute("errorMessage", "Order not found."));
        mvc.perform(get("/orders/my-orders").with(as(bob))).andExpect(content().string(allOf(
                not(containsString(order + "<")), not(containsString("LKR 1,400.00")), not(containsString("data-payment-status")))));

        // The admin sees the summary but is not offered a customer payment action.
        mvc.perform(get("/orders/" + order).with(as(admin))).andExpect(content().string(allOf(
                containsString("data-status=\"PARTIALLY_PAID\""), containsString("/payments/admin?query=" + order),
                not(containsString("data-pay-balance")))));
    }
}
