package com.bakery.order.model;

/**
 * Lifecycle states an order moves through from placement to delivery.
 */
public enum OrderStatus {
    PENDING,
    PREPARING,
    READY,
    DELIVERED,
    CANCELLED
}
