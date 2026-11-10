package com.bakery.order.model;

import java.time.LocalDate;

/**
 * An order the customer collects from the bakery in person.
 *
 * <p>No delivery fee is added — {@code calculateTotal()} returns
 * {@code quantity × unitPrice} exactly.</p>
 */
public class PickupOrder extends Order {

    public PickupOrder() {
        super();
    }

    public PickupOrder(String id, String customerId, String productId,
                       String productName, int quantity, double unitPrice,
                       OrderStatus status, LocalDate orderDate) {
        super(id, customerId, productId, productName, quantity, unitPrice,
              OrderType.PICKUP, status, orderDate);
    }

    /**
     * No delivery surcharge for self-collection.
     *
     * @return quantity × unitPrice
     */
    @Override
    public double calculateTotal() {
        return getQuantity() * getUnitPrice();
    }

    public String getDeliveryAddress() {
        return "";
    }

    public double getDeliveryFee() {
        return 0.0;
    }
}
