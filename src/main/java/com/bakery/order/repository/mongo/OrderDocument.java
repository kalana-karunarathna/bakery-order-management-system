package com.bakery.order.repository.mongo;

import com.bakery.order.model.Order;
import com.bakery.order.model.OrderStatus;
import com.bakery.order.model.OrderType;
import com.bakery.order.repository.OrderFactory;
import com.bakery.order.repository.OrderRepository;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * MongoDB shape of an order ({@code orders} collection). Separate from the domain classes so
 * {@link Order}, {@code PickupOrder} and {@code DeliveryOrder} stay unchanged.
 *
 * <p>Inheritance strategy: one collection discriminated by {@code orderType}; {@link OrderFactory}
 * rebuilds the matching subclass, so {@code calculateTotal()} keeps its polymorphic behaviour.
 * Totals are never stored; they are always calculated from quantity, unit price and type.</p>
 */
@Document(collection = MongoOrderRepository.COLLECTION)
public class OrderDocument {

    @Id
    private String id;
    private String customerId;
    private String productId;
    private String productName;
    private int quantity;
    private double unitPrice;
    private String orderType;
    private String deliveryAddress;
    private String status;
    /** ISO date (yyyy-MM-dd); sorts correctly as text and never shifts with time zones. */
    private String orderDate;
    @Version
    private Long version;

    protected OrderDocument() {
        // for Spring Data
    }

    /**
     * Converts a domain order for storage, refusing values that must never be stored.
     *
     * @throws IllegalStateException if a field is missing or unsafe
     */
    static OrderDocument from(Order order, String id, Long version) {
        String address = OrderFactory.deliveryAddressOf(order);
        for (String field : new String[]{id, order.getCustomerId(), order.getProductId(), order.getProductName(), address}) {
            if (field == null || OrderRepository.containsUnsafeCharacters(field)) {
                throw new IllegalStateException("Refusing to store an order with a missing or unsafe field.");
            }
        }
        if (order.getQuantity() < 1 || !(order.getUnitPrice() > 0) || order.getOrderType() == null
                || order.getStatus() == null || order.getOrderDate() == null) {
            throw new IllegalStateException("Refusing to store an order with invalid values.");
        }
        OrderDocument d = new OrderDocument();
        d.id = id;
        d.customerId = order.getCustomerId();
        d.productId = order.getProductId();
        d.productName = order.getProductName();
        d.quantity = order.getQuantity();
        d.unitPrice = order.getUnitPrice();
        d.orderType = order.getOrderType().name();
        d.deliveryAddress = address;
        d.status = order.getStatus().name();
        d.orderDate = order.getOrderDate().toString();
        d.version = version;
        return d;
    }

    /** @throws IllegalArgumentException if the stored document is invalid */
    Order toDomain() {
        return OrderFactory.create(id, customerId, productId, productName, quantity, unitPrice,
                OrderType.valueOf(orderType), deliveryAddress, OrderStatus.valueOf(status), LocalDate.parse(orderDate));
    }

    String getId() { return id; }
    String getCustomerId() { return customerId; }
    Long getVersion() { return version; }
}
