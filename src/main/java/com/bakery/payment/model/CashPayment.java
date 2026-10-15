package com.bakery.payment.model;
import java.math.BigDecimal;
import java.time.Instant;
/** Educational manual recording strategy. It never charges a payment instrument. */
public final class CashPayment extends Payment {
    public CashPayment(String id, TargetType type, String targetId, String customerId,
            BigDecimal total, BigDecimal amount, Purpose purpose, String reference, String key, Instant time) {
        super(id, type, targetId, customerId, total, amount, purpose, Status.PENDING, reference, key, time);
    }
    @Override public Method getMethod() { return Method.CASH; }
    @Override public String processPayment() { return "Cash record awaiting staff confirmation."; }
}