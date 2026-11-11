package com.bakery.payment;

import com.bakery.order.model.OrderType;
import com.bakery.order.service.OrderPaymentSummary;
import com.bakery.order.service.OrderService;
import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.Method;
import com.bakery.payment.model.Payment.Purpose;
import com.bakery.payment.model.Payment.Status;
import com.bakery.payment.model.Payment.TargetType;
import com.bakery.payment.service.PaymentIntegration.Actor;
import com.bakery.payment.service.PaymentService;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.security.BakeryUserPrincipal;
import com.bakery.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Admin DELETE in coursework mode ({@code bakery.payments.hard-delete.enabled=true}): any status can be deleted
 * from the Admin Payment Dashboard by an administrator (POST + CSRF + confirmation), the record disappears from
 * storage, history and the dashboard, IDs are never reused, and order balances are recalculated.
 * Temporary data folder only; real payment records are never touched.
 */
@SpringBootTest(properties = "bakery.payments.hard-delete.enabled=true")
@AutoConfigureMockMvc
class PaymentDeleteTests {

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

    private String alice, bob, admin, order;
    private Actor aliceActor, adminActor;
    private MockHttpSession adminSession;

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
        aliceActor = new Actor(alice, false);
        adminActor = new Actor(admin, true);
        order = orders.placeOrder(alice, "P001", "Butter Cake", 2, 500, OrderType.PICKUP, null).getId();   // LKR 1,000
        adminSession = new MockHttpSession();
        mvc.perform(get("/payments/admin").session(adminSession).with(as(admin))).andExpect(status().isOk());
    }

    private RequestPostProcessor as(String userId) {
        return user(new BakeryUserPrincipal(users.findById(userId).orElseThrow()));
    }

    /** A payment of LKR {@code amount} for the order, moved to {@code status} the normal way. */
    private String payment(String amount, Status status) {
        String id = payments.create(aliceActor, TargetType.ORDER, order, Method.CASH, Purpose.PARTIAL,
                new BigDecimal(amount), null, UUID.randomUUID().toString()).getId();
        switch (status) {
            case PAID -> payments.transition(adminActor, id, Status.PAID);
            case FAILED -> payments.transition(adminActor, id, Status.FAILED);
            case CANCELLED -> payments.transition(aliceActor, id, Status.CANCELLED);
            case REFUNDED -> { payments.transition(adminActor, id, Status.PAID); payments.transition(adminActor, id, Status.REFUNDED); }
            default -> { }
        }
        return id;
    }

    /** The dashboard's delete request: POST with Spring CSRF, the payment form token and the dialog's confirmation. */
    private MockHttpServletRequestBuilder deleteRequest(String id) {
        return post("/payments/" + id + "/delete").session(adminSession).with(as(admin)).with(csrf())
                .param("csrf", (String) adminSession.getAttribute("paymentCsrf")).param("confirmed", "true");
    }

    private ResultActions dashboard() throws Exception {
        return mvc.perform(get("/payments/admin").session(adminSession).with(as(admin)));
    }

    private OrderPaymentSummary summary() {
        return orders.paymentSummary(orders.getOwnOrder(order, alice)).orElseThrow();
    }

    // ── Delete every status ──────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(Status.class)
    void adminDeletesAPaymentOfAnyStatusFromTheDashboard(Status status) throws Exception {
        String id = payment("400", status);
        dashboard().andExpect(content().string(allOf(
                containsString("action=\"/payments/" + id + "/delete\""), containsString("data-payment-id=\"" + id + "\""),
                containsString("data-payment-amount=\"LKR 400.00\""), containsString("payment-row-delete-dialog"))));

        mvc.perform(deleteRequest(id))
                .andExpect(redirectedUrl("/payments/admin"))
                .andExpect(flash().attribute("success", allOf(containsString("Payment " + id), containsString("LKR 400.00"),
                        containsString("permanently deleted"))));

        assertTrue(payments.history(adminActor, null, null, null).isEmpty(), "removed from storage");
        assertFalse(Files.readString(data.resolve("payments.txt")).contains("|" + id + "|"));
        dashboard().andExpect(content().string(not(containsString("class=\"pay-record-id\">" + id))));
        mvc.perform(get("/payments").with(as(alice))).andExpect(content().string(not(containsString("class=\"pay-record-id\">" + id))));
        mvc.perform(get("/payments/" + id).with(as(admin))).andExpect(status().isNotFound());
        assertEquals(OrderPaymentSummary.Status.UNPAID, summary().status());
    }

    @Test
    void dashboardOffersDeleteForEveryStatusInCourseworkMode() throws Exception {
        for (Status s : Status.values()) payment("100", s);
        String page = dashboard().andReturn().getResponse().getContentAsString();
        assertEquals(5, page.split("data-row-delete", -1).length - 1, "one Delete button per payment");
        assertFalse(page.contains("Coursework mode"), "no coursework banner on the dashboard");
        assertTrue(page.contains("Yes, delete payment"));
        assertFalse(page.contains("data-hard-delete-off"));
    }

    // ── Safety ───────────────────────────────────────────────────────────────

    @Test
    void deletionNeedsConfirmationAndAnAdmin() throws Exception {
        String id = payment("400", Status.PAID);
        MockHttpSession aliceSession = new MockHttpSession();
        mvc.perform(get("/payments").session(aliceSession).with(as(alice)));

        // No confirmation from the dialog
        mvc.perform(post("/payments/" + id + "/delete").session(adminSession).with(as(admin)).with(csrf())
                .param("csrf", (String) adminSession.getAttribute("paymentCsrf"))).andExpect(status().isBadRequest());
        // No Spring CSRF token
        mvc.perform(post("/payments/" + id + "/delete").session(adminSession).with(as(admin))
                .param("csrf", (String) adminSession.getAttribute("paymentCsrf")).param("confirmed", "true"))
                .andExpect(status().isForbidden());
        // The owner, another customer, and a visitor
        mvc.perform(post("/payments/" + id + "/delete").session(aliceSession).with(as(alice)).with(csrf())
                .param("csrf", (String) aliceSession.getAttribute("paymentCsrf")).param("confirmed", "true"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/payments/" + id + "/delete").with(as(bob)).with(csrf()).param("confirmed", "true"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/payments/" + id + "/delete").with(csrf()).param("confirmed", "true"))
                .andExpect(redirectedUrlPattern("**/login"));
        // Customers never see a Delete button
        mvc.perform(get("/payments").with(as(alice))).andExpect(content().string(not(containsString("data-row-delete"))));

        assertEquals(Status.PAID, payments.get(adminActor, id).getStatus(), "still there");
    }

    @Test
    void deletedPaymentIdsAreNeverReused() throws Exception {
        payment("100", Status.PENDING);
        String second = payment("100", Status.PAID);
        mvc.perform(deleteRequest(second)).andExpect(redirectedUrl("/payments/admin"));
        assertEquals("PAY003", payment("100", Status.PENDING));
        mvc.perform(deleteRequest("PAY001")).andExpect(redirectedUrl("/payments/admin"));
        mvc.perform(deleteRequest("PAY003")).andExpect(redirectedUrl("/payments/admin"));
        assertEquals("PAY004", payment("100", Status.PENDING), "even after every payment was deleted");
    }

    // ── Order balance ────────────────────────────────────────────────────────

    @Test
    void orderBalanceAndProtectionAreRecalculatedFromTheRemainingPayments() throws Exception {
        String paid = payment("600", Status.PAID);
        String pending = payment("200", Status.PENDING);
        String refunded = payment("100", Status.REFUNDED);
        OrderPaymentSummary before = summary();
        assertEquals(OrderPaymentSummary.Status.PARTIALLY_PAID, before.status());
        assertEquals(new BigDecimal("400.00"), before.getOutstanding());

        mvc.perform(deleteRequest(paid)).andExpect(redirectedUrl("/payments/admin"));
        OrderPaymentSummary afterPaid = summary();
        assertEquals(OrderPaymentSummary.Status.PAYMENT_PENDING, afterPaid.status());
        assertEquals(new BigDecimal("0.00"), afterPaid.paid());
        assertEquals(new BigDecimal("1000.00"), afterPaid.getOutstanding());
        assertTrue(orders.isPaymentLocked(order), "the pending payment still protects the order");

        mvc.perform(deleteRequest(pending)).andExpect(redirectedUrl("/payments/admin"));
        assertEquals(OrderPaymentSummary.Status.UNPAID, summary().status());
        assertFalse(orders.isPaymentLocked(order));
        mvc.perform(get("/orders/" + order).with(as(alice))).andExpect(content().string(allOf(
                containsString("data-status=\"UNPAID\""), containsString("Pay for this order (LKR 1,000.00)"))));
        // The order can be edited again (no pending or paid payments)...
        mvc.perform(post("/orders/" + order + "/edit").with(as(alice)).with(csrf()).param("quantity", "3"))
                .andExpect(flash().attribute("successMessage", containsString("updated")));
        // ...but not deleted while the refunded record still exists; after deleting it, it can be.
        mvc.perform(post("/orders/" + order + "/delete").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", containsString("payment records")));
        mvc.perform(deleteRequest(refunded)).andExpect(redirectedUrl("/payments/admin"));
        mvc.perform(post("/orders/" + order + "/delete").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("successMessage", containsString("deleted")));
    }

    // ── Other CRUD still works ───────────────────────────────────────────────

    @Test
    void createReadUpdateStillWork() throws Exception {
        String id = payment("300", Status.PENDING);                                                  // Create
        dashboard().andExpect(content().string(containsString("class=\"pay-record-id\">" + id)));    // Read
        mvc.perform(post("/payments/" + id + "/edit").session(adminSession).with(as(admin)).with(csrf())  // Update (edit)
                .param("csrf", (String) adminSession.getAttribute("paymentCsrf")).param("amount", "350"))
                .andExpect(redirectedUrl("/payments/" + id));
        mvc.perform(post("/payments/" + id + "/status").session(adminSession).with(as(admin)).with(csrf())  // Update (status)
                .param("csrf", (String) adminSession.getAttribute("paymentCsrf")).param("status", "PAID").param("verified", "true"))
                .andExpect(redirectedUrl("/payments/" + id));
        Payment updated = payments.get(adminActor, id);
        assertEquals(new BigDecimal("350.00"), updated.getAmount());
        assertEquals(Status.PAID, updated.getStatus());
    }
}
