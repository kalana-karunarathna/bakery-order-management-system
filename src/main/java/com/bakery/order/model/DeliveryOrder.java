package com.bakery.order.model;

import java.time.LocalDate;

/**
 * An order delivered to the customer's address.
 *
 * <p>Demonstrates polymorphism: {@code calculateTotal()} adds a fixed delivery
 * fee on top of the product subtotal, unlike {@link PickupOrder}.</p>
 */
public class DeliveryOrder extends Order {

    /** Flat delivery charge applied to every home-delivery order. */
    public static final double DELIVERY_FEE = 250.0;

    private String deliveryAddress;

    public DeliveryOrder() {
        super();
    }

    public DeliveryOrder(String id, String customerId, String productId,
                         String productName, int quantity, double unitPrice,
                         String deliveryAddress, OrderStatus status, LocalDate orderDate) {
        super(id, customerId, productId, productName, quantity, unitPrice,
              OrderType.DELIVERY, status, orderDate);
        this.deliveryAddress = deliveryAddress;
    }

    /**
     * Adds the flat delivery surcharge to the product subtotal.
     *
     * @return (quantity × unitPrice) + DELIVERY_FEE
     */
    @Override
    public double calculateTotal() {
        return (getQuantity() * getUnitPrice()) + DELIVERY_FEE;
    }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public double getDeliveryFee() { return DELIVERY_FEE; }
}
