package com.bakery.order.service;

import com.bakery.order.model.Order;

/**
 * Port through which the order pages show an order's payment summary, without Order Management
 * depending on payment classes. Payment Management provides the implementation. Callers must have
 * checked that the viewer may see the order (owner or admin) before asking for its summary.
 */
@FunctionalInterface
public interface OrderPaymentSummaryProvider {

    /** Payment summary of the order, calculated from its payment records. */
    OrderPaymentSummary summarize(Order order);
}
