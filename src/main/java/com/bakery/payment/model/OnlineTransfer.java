package com.bakery.payment.model;
import java.math.BigDecimal;
import java.time.Instant;
/** Educational manual recording strategy. It never charges a payment instrument. */
public final class OnlineTransfer extends Payment {
    public OnlineTransfer(String id, TargetType type, String targetId, String customerId,
            BigDecimal total, BigDecimal amount, Purpose purpose, String reference, String key, Instant time) {
        super(id, type, targetId, customerId, total, amount, purpose, Status.PENDING, reference, key, time);
    }
    @Override public Method getMethod() { return Method.BANK_TRANSFER; }
    @Override public String processPayment() { return "Transfer reference recorded; staff must verify receipt independently."; }
}