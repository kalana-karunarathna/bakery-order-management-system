package com.bakery.payment.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

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