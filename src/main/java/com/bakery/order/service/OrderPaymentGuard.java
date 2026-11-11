package com.bakery.order.service;

/**
 * Port through which Order Management asks whether an order has payments, without depending on the
 * payment module. Payment Management provides the implementation; without one, {@link #NONE} applies.
 *
 * <p>OrderService uses it to stop unsafe changes: an order with Pending or Paid payments cannot be
 * edited, cancelled by the customer or cancelled by an admin, and an order with any payment record
 * cannot be deleted (the payment history must keep pointing at it). Order status and payment status
 * stay separate: this check never changes either of them.</p>
 */
@FunctionalInterface
public interface OrderPaymentGuard {

    enum PaymentState {
        /** No payment has been recorded for the order. */
        NONE,
        /** Only Failed, Cancelled or Refunded payments: the order may change, but must be kept. */
        CLOSED_ONLY,
        /** At least one Pending or Paid payment holds part of the order's total. */
        ACTIVE
    }

    /**
     * Payment state of an order.
     *
     * @throws IllegalStateException with a user-friendly message if payment data cannot be read
     *         (the change is then refused: fail closed)
     */
    PaymentState paymentState(String orderId);

    /** Used when no payment module is present. */
    OrderPaymentGuard NONE = orderId -> PaymentState.NONE;
}
