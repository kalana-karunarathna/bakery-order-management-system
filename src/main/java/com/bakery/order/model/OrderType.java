package com.bakery.order.model;

/**
 * Distinguishes how the customer receives the order.
 * PickupOrder and DeliveryOrder subclasses use this to calculate totals differently.
 */
public enum OrderType {
    PICKUP,
    DELIVERY
}
