package com.bakery.payment.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Abstract manual payment record. No card numbers, CVVs or bank credentials are collected. */
public abstract class Payment {
    public enum Method { CASH, CARD, BANK_TRANSFER }
    public enum Status { PENDING, PAID, FAILED, CANCELLED, REFUNDED }
    public enum TargetType { ORDER, BOOKING }
    public enum Purpose { FULL, ADVANCE, PARTIAL }
    private final String id, targetId, customerId, invoiceId, requestKey;
    private final TargetType targetType;
    private final Purpose purpose;
    private final BigDecimal total;
    private final Instant createdAt;
    private BigDecimal amount;
    private Status status;
    private String reference;

    protected Payment(String id, TargetType type, String targetId, String customerId,
            BigDecimal total, BigDecimal amount, Purpose purpose, Status status,
            String reference, String requestKey, Instant createdAt) {
        this.id = text(id); this.targetType = type; this.targetId = text(targetId);
        this.customerId = text(customerId); this.total = money(total);
        this.purpose = purpose; this.status = status; this.requestKey = text(requestKey);
        this.invoiceId = "INV-" + id; this.createdAt = createdAt;
        if (createdAt == null || createdAt.isAfter(Instant.now()))
            throw new IllegalArgumentException("Payment date must not be in the future");
        setAmount(amount); setReference(reference);
        if (type == null || purpose == null || status == null || createdAt == null)
            throw new IllegalArgumentException("Missing payment fields");
    }
    public static String text(String value) {
        if (value == null || value.isBlank() || value.length() > 150 ||
                value.contains("|") || value.contains("\n") || value.contains("\r"))
            throw new IllegalArgumentException("Invalid payment text field");
        return value.trim();
    }
    public static BigDecimal money(BigDecimal value) {
        if (value == null) throw new PaymentValidationException("amount", "Payment amount is required.");
        if (value.signum() <= 0)
            throw new PaymentValidationException("amount", "Payment amount must be greater than zero.");
        if (value.scale() > 2)
            throw new PaymentValidationException("amount", "Use a maximum of two decimal places.");
        if (value.compareTo(new BigDecimal("999999999999.99")) > 0)
            throw new PaymentValidationException("amount", "Payment amount is too large.");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
    public static String reference(String value) {
        if (value == null || value.isBlank())
            throw new PaymentValidationException("reference", "Transaction reference is required.");
        String normalized = value.trim();
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9 ./_-]{2,79}"))
            throw new PaymentValidationException("reference",
                "Use 3-80 characters: letters, numbers, spaces, dots, slashes, underscores or hyphens.");
        return normalized;
    }

    // ── References in the manual payment workflow ─────────────────────────────
    // CASH: paid at the counter or to the rider; no reference is needed.
    // CARD: paid on the bakery's own card machine; the optional reference is the one printed on the slip.
    // BANK_TRANSFER: paid into the bakery's bank account; the reference comes from the customer's bank.
    // Without a customer reference the server records a system reference (CASH-/CARD-/TRANSFER-<payment ID>),
    // and an administrator must still find the payment (cash drawer, terminal report, bank statement) before
    // confirming it. Card numbers, expiry dates and CVV codes are never accepted or stored.

    private static final Pattern RESERVED = Pattern.compile("(?i)(CASH|CARD|TRANSFER)-PAY[0-9]+");
    private static final Pattern DIGIT_RUN = Pattern.compile("[0-9]{13,}");
    private static final Pattern CARD_WORDS = Pattern.compile("(?i)\\b(cvv2?|cvc2?|cid|exp|expiry|expires)\\b");
    private static final Pattern EXPIRY = Pattern.compile("(?<![0-9])(0[1-9]|1[0-2]) ?/ ?([0-9]{2}|20[0-9]{2})(?![0-9])");

    /** Reference the server records when none was given, e.g. {@code CARD-PAY004}. Customers cannot enter this format. */
    public static String systemReference(Method method, String id) {
        String prefix = switch (method) { case CASH -> "CASH"; case CARD -> "CARD"; case BANK_TRANSFER -> "TRANSFER"; };
        return prefix + "-" + id;
    }

    /** True if the customer (or admin) supplied a reference; false for a server-generated system reference. */
    public boolean isReferenceProvided() {
        return !reference.equalsIgnoreCase(systemReference(getMethod(), id));
    }

    /**
     * Validates a reference typed into the form. Returns {@code null} when there is none to store (always for
     * cash, or a blank card/bank reference): the caller then records {@link #systemReference}. Anything that
     * looks like card details is refused and never echoed back.
     */
    public static String customerReference(Method method, String raw) {
        if (method == null) throw new PaymentValidationException("method", "Please select a payment method.");
        if (method == Method.CASH || raw == null || raw.isBlank()) return null;
        if (looksLikeCardDetails(method, raw))
            throw new PaymentValidationException("reference", method == Method.CARD
                ? "This looks like card details. Never enter a card number, expiry date or CVV. Use the reference printed on the card machine slip, or leave this blank."
                : "This looks like a card number. Enter the bank transfer reference from your banking app or deposit slip instead.");
        String normalized = reference(raw);
        if (RESERVED.matcher(normalized).matches())
            throw new PaymentValidationException("reference",
                "This reference format is reserved for references created by the bakery system. Enter the reference exactly as shown on your slip or banking app, or leave it blank.");
        return normalized;
    }

    /**
     * Card number protection. For card payments any 13+ digit run (spaces and hyphens ignored), an expiry date
     * (MM/YY) or CVV/expiry wording is refused. For bank transfers only 13-19 digit runs that pass the card
     * checksum (Luhn) are refused, because genuine bank references are often long numbers.
     */
    public static boolean looksLikeCardDetails(Method method, String raw) {
        if (raw == null) return false;
        Matcher digits = DIGIT_RUN.matcher(raw.replaceAll("[\\s-]", ""));
        while (digits.find()) {
            String run = digits.group();
            if (method == Method.CARD || run.length() <= 19 && luhn(run)) return true;
        }
        return method == Method.CARD && (CARD_WORDS.matcher(raw).find() || EXPIRY.matcher(raw).find());
    }

    private static boolean luhn(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int d = digits.charAt(digits.length() - 1 - i) - '0';
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
        }
        return sum % 10 == 0;
    }
    public void setAmount(BigDecimal amount) {
        if (status != Status.PENDING)
            throw new PaymentValidationException("amount", "Confirmed or closed payment amounts cannot be edited.");
        BigDecimal validated = money(amount);
        if (validated.compareTo(total) > 0)
            throw new PaymentValidationException("amount", "Amount cannot exceed the remaining balance.");
        this.amount = validated;
    }
    public void setReference(String reference) {
        if (status != Status.PENDING && this.reference != null)
            throw new PaymentValidationException("reference", "Only pending payments can be edited.");
        this.reference = reference(reference);
    }
    public void transition(Status next, boolean admin) {
        if (next == null) throw new PaymentValidationException("status", "Invalid payment status.");
        if (!admin && next == Status.PAID)
            throw new PaymentValidationException("status", "Only administrators can confirm payments.");
        if (!admin && next == Status.REFUNDED)
            throw new PaymentValidationException("status", "Only administrators can record refunds.");
        if (status == Status.REFUNDED)
            throw new PaymentValidationException("status", "This payment has already been refunded.");
        boolean permitted = status == Status.PENDING &&
                (next == Status.CANCELLED || admin && (next == Status.PAID || next == Status.FAILED))
                || admin && status == Status.PAID && next == Status.REFUNDED;
        if (!permitted) throw new PaymentValidationException("status", "Invalid payment status transition.");
        status = next;
    }    public abstract Method getMethod();
    public abstract String processPayment();
    public String getId() { return id; }
    public TargetType getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getAmount() { return amount; }
    public Purpose getPurpose() { return purpose; }
    public Status getStatus() { return status; }
    public String getReference() { return reference; }
    public String getRequestKey() { return requestKey; }
    public String getInvoiceId() { return invoiceId; }
    public Instant getCreatedAt() { return createdAt; }

    public static Payment create(Method method, String id, TargetType type, String targetId,
            String customerId, BigDecimal total, BigDecimal amount, Purpose purpose,
            Status status, String reference, String key, Instant time) {
        if (method == null) throw new PaymentValidationException("method", "Please select a payment method.");
        if (status == null) throw new PaymentValidationException("status", "Invalid payment status.");
        // Construct pending first so validated setters can restore terminal records safely.
        Payment p = switch (method) {
            case CASH -> new CashPayment(id, type, targetId, customerId, total, amount, purpose, reference, key, time);
            case CARD -> new CardPayment(id, type, targetId, customerId, total, amount, purpose, reference, key, time);
            case BANK_TRANSFER -> new OnlineTransfer(id, type, targetId, customerId, total, amount, purpose, reference, key, time);
        };
        p.status = status;
        return p;
    }
}