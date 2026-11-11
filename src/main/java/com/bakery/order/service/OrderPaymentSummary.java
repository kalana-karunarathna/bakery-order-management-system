package com.bakery.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Read-only payment picture of one order, calculated on the server from the actual payment records
 * every time it is shown (nothing is copied into the order data).
 *
 * <ul>
 *   <li>{@code paid}: Paid records only (confirmed by an administrator).</li>
 *   <li>{@code pending}: Pending records (recorded, awaiting confirmation). Not counted as paid, but they
 *       reserve part of the balance, so {@code payable} excludes them.</li>
 *   <li>{@code refunded}: Refunded records (money returned). Not counted as paid.</li>
 *   <li>Failed and Cancelled records are ignored.</li>
 * </ul>
 * This is separate from the order's lifecycle status: confirming a payment never moves an order forward.
 */
public record OrderPaymentSummary(BigDecimal total, BigDecimal paid, BigDecimal pending, BigDecimal refunded,
                                  Status status) {

    public enum Status {
        /** Nothing paid and nothing awaiting confirmation. */
        UNPAID("Unpaid", "cancelled"),
        /** Nothing confirmed yet, but a payment awaits confirmation. */
        PAYMENT_PENDING("Payment pending", "pending"),
        /** Part of the total is confirmed. */
        PARTIALLY_PAID("Partially paid", "preparing"),
        /** The whole total is confirmed. */
        PAID("Paid", "delivered"),
        /** Payment records could not be read right now. */
        UNAVAILABLE("Payment info unavailable", "cancelled");

        private final String label;
        private final String badge;

        Status(String label, String badge) {
            this.label = label;
            this.badge = badge;
        }

        public String getLabel() { return label; }

        /** Reuses the order status badge colours ({@code o-status--<badge>}). */
        public String getBadge() { return badge; }
    }

    public OrderPaymentSummary {
        total = money(total);
        paid = money(paid);
        pending = money(pending);
        refunded = money(refunded);
    }

    /** Builds the summary and derives the status from the amounts. */
    public static OrderPaymentSummary of(BigDecimal total, BigDecimal paid, BigDecimal pending, BigDecimal refunded) {
        Status status;
        if (total.signum() > 0 && paid.compareTo(total) >= 0) status = Status.PAID;
        else if (paid.signum() > 0) status = Status.PARTIALLY_PAID;
        else if (pending.signum() > 0) status = Status.PAYMENT_PENDING;
        else status = Status.UNPAID;
        return new OrderPaymentSummary(total, paid, pending, refunded, status);
    }

    /** Shown when payment records cannot be read: amounts are unknown, so no payment action is offered. */
    public static OrderPaymentSummary unavailable(BigDecimal total) {
        return new OrderPaymentSummary(total, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Status.UNAVAILABLE);
    }

    // Bean-style getters for templates (Thymeleaf/SpEL property access).
    public BigDecimal getTotal() { return total; }
    public BigDecimal getPaid() { return paid; }
    public BigDecimal getPending() { return pending; }
    public BigDecimal getRefunded() { return refunded; }
    public Status getStatus() { return status; }

    /** Total minus confirmed payments (never negative). */
    public BigDecimal getOutstanding() {
        return total.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** What can still be paid now: outstanding minus amounts already awaiting confirmation. */
    public BigDecimal getPayable() {
        return getOutstanding().subtract(pending).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isAvailable() {
        return status != Status.UNAVAILABLE;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
