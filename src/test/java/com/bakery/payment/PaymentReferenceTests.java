package com.bakery.payment;

import com.bakery.order.model.OrderType;
import com.bakery.order.service.OrderService;
import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.Method;
import com.bakery.payment.model.Payment.Status;
import com.bakery.payment.model.PaymentValidationException;
import com.bakery.payment.service.PaymentIntegration.Actor;
import com.bakery.payment.service.PaymentService;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.security.BakeryUserPrincipal;
import com.bakery.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payment methods and references in the manual payment workflow: cash needs no reference, card and bank
 * transfer references are optional, card details are never accepted or stored, every new payment stays
 * Pending, and only an administrator who ticks the verification box can mark a payment Paid.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentReferenceTests {

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
        order = orders.placeOrder(alice, "P001", "Butter Cake", 2, 500, OrderType.PICKUP, null).getId();   // LKR 1,000
    }

    private RequestPostProcessor as(String userId) {
        return user(new BakeryUserPrincipal(users.findById(userId).orElseThrow()));
    }

    private ResultActions submit(String userId, String path, String... params) throws Exception {
        MockHttpSession session = sessions.computeIfAbsent(userId, id -> new MockHttpSession());
        if (session.getAttribute("paymentCsrf") == null) {
            mvc.perform(get("/payments").session(session).with(as(userId))).andExpect(status().isOk());
        }
        var request = post(path).session(session).with(as(userId)).with(csrf())
                .param("csrf", (String) session.getAttribute("paymentCsrf"));
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return mvc.perform(request);
    }

    private ResultActions record(String method, String reference) throws Exception {
        return submit(alice, "/payments", "type", "ORDER", "targetId", order, "method", method, "purpose", "PARTIAL",
                "amount", "400", "reference", reference, "requestKey", UUID.randomUUID().toString());
    }

    private List<Payment> stored() {
        return payments.history(new Actor(admin, true), null, null, null);
    }

    private Payment only() {
        List<Payment> all = stored();
        assertEquals(1, all.size());
        return all.getFirst();
    }

    // ── Cash ─────────────────────────────────────────────────────────────────

    @Test
    void cashNeedsNoReferenceAndIgnoresOneIfSent() throws Exception {
        record("CASH", "").andExpect(redirectedUrlPattern("/payments/PAY*"));
        record("CASH", "MY-OWN-REF").andExpect(redirectedUrlPattern("/payments/PAY*"));
        List<Payment> all = stored();
        assertEquals(List.of("CASH-PAY001", "CASH-PAY002"), all.stream().map(Payment::getReference).sorted().toList());
        assertTrue(all.stream().allMatch(p -> p.getStatus() == Status.PENDING && !p.isReferenceProvided()));
        mvc.perform(get("/payments/PAY001").with(as(alice)))
                .andExpect(content().string(containsString("Cash - no reference needed (CASH-PAY001)")));
    }

    // ── Card ─────────────────────────────────────────────────────────────────

    @Test
    void cardReferenceIsOptionalAndMissingOnesNeedManualVerification() throws Exception {
        record("CARD", "").andExpect(redirectedUrl("/payments/PAY001"));
        Payment p = only();
        assertEquals("CARD-PAY001", p.getReference());
        assertFalse(p.isReferenceProvided());
        assertEquals(Status.PENDING, p.getStatus());
        mvc.perform(get("/payments/PAY001").with(as(alice))).andExpect(content().string(containsString("Not provided - verified manually")));
        mvc.perform(get("/payments/PAY001").with(as(admin))).andExpect(content().string(allOf(
                containsString("No slip reference was given"), containsString("name=\"verified\""))));

        record("CARD", "  Ref 004521 ").andExpect(redirectedUrl("/payments/PAY002"));
        assertEquals("Ref 004521", payments.get(new Actor(alice, false), "PAY002").getReference());
        mvc.perform(get("/payments/PAY002").with(as(admin))).andExpect(content().string(containsString("slip reference Ref 004521")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111111", "4111 1111 1111 1111", "4111-1111-1111-1111", "5500 0000 0000 0004 exp 12/27",
            "Exp 12/27", "CVV 123", "1234567890123456"})
    void cardDetailsAreNeverAcceptedStoredOrEchoed(String cardLike) throws Exception {
        record("CARD", cardLike).andExpect(status().isBadRequest())
                .andExpect(model().attribute("errors", hasEntry(org.hamcrest.Matchers.is("reference"),
                        containsString("Never enter a card number"))))
                .andExpect(model().attribute("form", hasEntry("reference", "")))
                .andExpect(content().string(not(containsString(cardLike))));
        assertTrue(stored().isEmpty());
        assertFalse(Files.exists(data.resolve("payments.txt")) && Files.readString(data.resolve("payments.txt")).contains("4111"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "bad|ref", "<script>", "CARD-PAY009", "transfer-pay1", "cash-pay3"})
    void invalidOrReservedReferencesAreRefused(String reference) throws Exception {
        record("CARD", reference).andExpect(status().isBadRequest())
                .andExpect(model().attribute("errors", org.hamcrest.Matchers.hasKey("reference")));
        assertTrue(stored().isEmpty());
    }

    // ── Bank transfer ────────────────────────────────────────────────────────

    @Test
    void bankTransferReferenceIsRecommendedButOptional() throws Exception {
        record("BANK_TRANSFER", "4111 1111 1111 1111").andExpect(status().isBadRequest())
                .andExpect(model().attribute("errors", hasEntry(org.hamcrest.Matchers.is("reference"),
                        containsString("looks like a card number"))));
        record("BANK_TRANSFER", "").andExpect(redirectedUrl("/payments/PAY001"));
        assertEquals("TRANSFER-PAY001", only().getReference());
        mvc.perform(get("/payments/PAY001").with(as(admin))).andExpect(content().string(allOf(
                containsString("No bank reference was given"), containsString("mark the payment Failed"))));

        record("BANK_TRANSFER", "TRF-2026-0001").andExpect(redirectedUrl("/payments/PAY002"));
        // Long numeric bank references are fine as long as they are not card numbers (checksum).
        submit(alice, "/payments", "type", "ORDER", "targetId", order, "method", "BANK_TRANSFER", "purpose", "PARTIAL",
                "amount", "100", "reference", "1234567890123456", "requestKey", "k-long")
                .andExpect(redirectedUrl("/payments/PAY003"));
        assertEquals(3, stored().size());
        assertTrue(stored().stream().allMatch(p -> p.getStatus() == Status.PENDING));
    }

    @Test
    void formExplainsEachMethodAndWarnsAboutCardDetails() throws Exception {
        mvc.perform(get("/payments/new").param("orderId", order).with(as(alice))).andExpect(content().string(allOf(
                containsString("data-method-guide=\"CASH\""), containsString("No reference is needed"),
                containsString("bakery's card machine"), containsString("Reference number"),
                containsString("Never enter a card number, expiry date or CVV"),
                containsString("Card machine or bank transfer reference (optional)"),
                containsString("autocomplete=\"off\""), not(containsString("Transaction reference is required")))));
    }

    // ── Approval ─────────────────────────────────────────────────────────────

    @Test
    void onlyAVerifyingAdminCanMarkAPaymentPaid() throws Exception {
        record("BANK_TRANSFER", "").andExpect(redirectedUrl("/payments/PAY001"));

        // A customer can never confirm, even when sending the verification flag.
        submit(alice, "/payments/PAY001/status", "status", "PAID", "verified", "true")
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("errors", hasEntry("status", "Only administrators can confirm payments.")));
        // Another customer cannot even see the payment.
        submit(bob, "/payments/PAY001/status", "status", "PAID", "verified", "true").andExpect(status().isNotFound());
        // An admin must tick the verification box; a missing reference does not skip it.
        submit(admin, "/payments/PAY001/status", "status", "PAID")
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("errors", org.hamcrest.Matchers.hasKey("verified")));
        submit(admin, "/payments/PAY001/status", "status", "PAID", "verified", "yes").andExpect(status().isBadRequest());
        assertEquals(Status.PENDING, only().getStatus());

        submit(admin, "/payments/PAY001/status", "status", "PAID", "verified", "true").andExpect(redirectedUrl("/payments/PAY001"));
        assertEquals(Status.PAID, only().getStatus());
    }

    @Test
    void postedStatusOnCreateIsIgnored() throws Exception {
        submit(alice, "/payments", "type", "ORDER", "targetId", order, "method", "CARD", "purpose", "PARTIAL",
                "amount", "400", "requestKey", "k-status", "status", "PAID", "verified", "true")
                .andExpect(redirectedUrl("/payments/PAY001"));
        assertEquals(Status.PENDING, only().getStatus());
    }

    // ── Editing ──────────────────────────────────────────────────────────────

    @Test
    void editingFollowsTheSameReferenceRules() throws Exception {
        record("CARD", "Ref 1").andExpect(redirectedUrl("/payments/PAY001"));
        submit(alice, "/payments/PAY001/edit", "amount", "400", "reference", "4111 1111 1111 1111")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("4111 1111 1111 1111"))));
        assertEquals("Ref 1", only().getReference());
        submit(alice, "/payments/PAY001/edit", "amount", "400", "reference", "").andExpect(redirectedUrl("/payments/PAY001"));
        assertEquals("CARD-PAY001", only().getReference());
        // The edit form shows a blank field (not the system reference) for a payment without a reference.
        mvc.perform(get("/payments/PAY001").with(as(alice))).andExpect(content().string(allOf(
                containsString("Terminal transaction reference (optional)"),
                containsString("name=\"reference\" maxlength=\"80\" autocomplete=\"off\" spellcheck=\"false\" value=\"\""))));
    }

    @Test
    void existingRecordsAreNotRewritten() throws Exception {
        // A payment stored before this change: cash with a typed receipt reference.
        Files.write(data.resolve("payments.txt"), List.of("# sequence=1",
                "v1|PAY001|ORDER|" + order + "|" + alice + "|1000.00|500.00|CASH|PARTIAL|PENDING|cash-receipt-7|legacy-key|2026-11-01T10:00:00Z"));
        String before = Files.readString(data.resolve("payments.txt"));
        mvc.perform(get("/payments/PAY001").with(as(alice))).andExpect(status().isOk());
        assertEquals(before, Files.readString(data.resolve("payments.txt")), "reading never rewrites records");
        assertTrue(only().isReferenceProvided());
        // Editing the amount keeps its reference.
        submit(alice, "/payments/PAY001/edit", "amount", "600").andExpect(redirectedUrl("/payments/PAY001"));
        assertEquals("cash-receipt-7", only().getReference());
    }

    // ── Rules on their own ───────────────────────────────────────────────────

    @Test
    void referenceRules() {
        assertNull(Payment.customerReference(Method.CASH, "anything"));
        assertNull(Payment.customerReference(Method.CARD, "   "));
        assertEquals("TRF 99", Payment.customerReference(Method.BANK_TRANSFER, " TRF 99 "));
        assertEquals("CARD-PAY007", Payment.systemReference(Method.CARD, "PAY007"));
        assertEquals("TRANSFER-PAY007", Payment.systemReference(Method.BANK_TRANSFER, "PAY007"));
        assertTrue(Payment.looksLikeCardDetails(Method.CARD, "4012 8888 8888 1881"));
        assertFalse(Payment.looksLikeCardDetails(Method.CARD, "RRN 123456789012"));
        assertFalse(Payment.looksLikeCardDetails(Method.BANK_TRANSFER, "Exp 12/27"));
        assertThrows(PaymentValidationException.class, () -> Payment.customerReference(Method.BANK_TRANSFER, "4012888888881881"));
    }
}
