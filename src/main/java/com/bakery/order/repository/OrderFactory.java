package com.bakery.order.repository;

import com.bakery.order.model.DeliveryOrder;
import com.bakery.order.model.Order;
import com.bakery.order.model.OrderStatus;
import com.bakery.order.model.OrderType;
import com.bakery.order.model.PickupOrder;

import java.time.LocalDate;

/**
 * Rebuilds the correct {@link Order} subclass from stored values (polymorphism: the stored order
 * type decides between {@link PickupOrder} and {@link DeliveryOrder}). Shared by the TXT and MongoDB
 * repositories, and validates the stored values so a corrupt record is rejected rather than loaded.
 */
public final class OrderFactory {

    private OrderFactory() {
    }

    public static Order create(String id, String customerId, String productId, String productName,
                               int quantity, double unitPrice, OrderType orderType, String deliveryAddress,
                               OrderStatus status, LocalDate orderDate) {
        if (isBlank(id) || isBlank(customerId) || isBlank(productId) || isBlank(productName)) {
            throw new IllegalArgumentException("Stored order is missing an ID, customer or product.");
        }
        if (quantity < 1 || !Double.isFinite(unitPrice) || unitPrice <= 0) {
            throw new IllegalArgumentException("Stored order has an invalid quantity or price.");
        }
        if (orderType == null || status == null || orderDate == null) {
            throw new IllegalArgumentException("Stored order is missing its type, status or date.");
        }
        if (orderType == OrderType.DELIVERY) {
            if (isBlank(deliveryAddress)) {
                throw new IllegalArgumentException("Stored delivery order has no address.");
            }
            return new DeliveryOrder(id, customerId, productId, productName, quantity, unitPrice,
                                     deliveryAddress, status, orderDate);
        }
        return new PickupOrder(id, customerId, productId, productName, quantity, unitPrice, status, orderDate);
    }

    /** Delivery address of an order, or an empty string for pickup orders. */
    public static String deliveryAddressOf(Order order) {
        return order instanceof DeliveryOrder d && d.getDeliveryAddress() != null ? d.getDeliveryAddress() : "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
