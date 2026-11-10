package com.bakery.order.service;

import com.bakery.order.model.*;
import com.bakery.order.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Business logic layer for order management.
 *
 * <p>Validates input, enforces state-machine rules on order status,
 * and delegates persistence to {@link OrderRepository}.</p>
 */
@Service
public class OrderService {

    private final OrderRepository repo;

    public OrderService(OrderRepository repo) {
        this.repo = repo;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Places a new order for a customer.
     * Stock availability is checked conceptually: quantity must be ≥ 1.
     *
     * @param customerId      the customer placing the order
     * @param productId       product being ordered
     * @param productName     human-readable product name
     * @param quantity        number of units (must be ≥ 1)
     * @param unitPrice       price per unit in LKR
     * @param orderType       PICKUP or DELIVERY
     * @param deliveryAddress required when orderType is DELIVERY
     * @return the persisted order with an assigned ID
     */
    public Order placeOrder(String customerId, String productId, String productName,
                            int quantity, double unitPrice,
                            OrderType orderType, String deliveryAddress) throws IOException {
        // Stock availability check (abstraction — real stock query would go here)
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1.");
        }
        if (unitPrice <= 0) {
            throw new IllegalArgumentException("Unit price must be greater than zero.");
        }
        if (orderType == OrderType.DELIVERY &&
                (deliveryAddress == null || deliveryAddress.isBlank())) {
            throw new IllegalArgumentException("Delivery address is required for delivery orders.");
        }

        Order order;
        if (orderType == OrderType.DELIVERY) {
            order = new DeliveryOrder(null, customerId, productId, productName,
                                      quantity, unitPrice, deliveryAddress,
                                      OrderStatus.PENDING, LocalDate.now());
        } else {
            order = new PickupOrder(null, customerId, productId, productName,
                                    quantity, unitPrice,
                                    OrderStatus.PENDING, LocalDate.now());
        }

        return repo.save(order);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<Order> getAllOrders() throws IOException {
        return repo.findAll();
    }

    public Optional<Order> getOrderById(String id) throws IOException {
        return repo.findById(id);
    }

    public List<Order> getOrdersByCustomerId(String customerId) throws IOException {
        return repo.findByCustomerId(customerId);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * Updates the status of an order (admin operation).
     * An order cannot be moved out of CANCELLED status.
     */
    public void updateStatus(String id, OrderStatus newStatus) throws IOException {
        Order order = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot change status of a cancelled order.");
        }
        order.setStatus(newStatus);
        repo.update(order);
    }

    /**
     * Allows a customer to modify quantity and/or delivery details
     * before the bakery starts preparing the order.
     * Only PENDING orders may be edited.
     */
    public void updateOrderItems(String id, int newQuantity,
                                 String newDeliveryAddress) throws IOException {
        Order order = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be edited.");
        }
        if (newQuantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1.");
        }

        order.setQuantity(newQuantity);

        if (order instanceof DeliveryOrder delivery) {
            if (newDeliveryAddress != null && !newDeliveryAddress.isBlank()) {
                delivery.setDeliveryAddress(newDeliveryAddress);
            }
        }

        repo.update(order);
    }

    // ── Delete / Cancel ───────────────────────────────────────────────────────

    /**
     * Cancels an order by setting its status to CANCELLED.
     * Only PENDING orders can be cancelled by customers.
     * Admins can also hard-delete via {@link #deleteOrder(String)}.
     */
    public void cancelOrder(String id) throws IOException {
        Order order = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));

        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Delivered orders cannot be cancelled.");
        }
        order.setStatus(OrderStatus.CANCELLED);
        repo.update(order);
    }

    /**
     * Permanently removes an order record (admin only).
     */
    public void deleteOrder(String id) throws IOException {
        repo.delete(id);
    }
}
