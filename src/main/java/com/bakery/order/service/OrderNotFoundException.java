package com.bakery.order.service;

/**
 * The order does not exist, or the requester is not allowed to see it.
 * Both cases use the same exception (and the same message) so customers cannot discover
 * which order IDs exist by trying other customers' IDs.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException() {
        super("Order not found.");
    }
}
