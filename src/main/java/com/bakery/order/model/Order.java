package com.bakery.order.model;

import java.time.LocalDate;

/**
 * Abstract base for all bakery orders — demonstrates encapsulation and abstraction.
 *
 * <p>Encapsulation: all fields are private; access is through getters/setters.</p>
 * <p>Abstraction: {@code calculateTotal()} is abstract so each concrete order type
 * decides how the total is computed (e.g. with or without a delivery fee).</p>
 */
public abstract class Order {

    private String id;
    private String customerId;
    private String productId;
    private String productName;
    private int quantity;
    private double unitPrice;
    private OrderType orderType;
    private OrderStatus status;
    private LocalDate orderDate;

    protected Order() {}

    protected Order(String id, String customerId, String productId,
                    String productName, int quantity, double unitPrice,
                    OrderType orderType, OrderStatus status, LocalDate orderDate) {
        this.id = id;
        this.customerId = customerId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.orderType = orderType;
        this.status = status;
        this.orderDate = orderDate;
    }

    /**
     * Polymorphic total calculation — each subclass applies its own pricing rules.
     *
     * @return total amount payable by the customer
     */
    public abstract double calculateTotal();

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public OrderType getOrderType() { return orderType; }
    public OrderStatus getStatus() { return status; }
    public LocalDate getOrderDate() { return orderDate; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public void setProductId(String productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public void setOrderType(OrderType orderType) { this.orderType = orderType; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }

    // ── Helpers used by templates ─────────────────────────────────────────────

    /** Convenience label for status badges in Thymeleaf templates. */
    public String getStatusLabel() {
        return switch (status) {
            case PENDING   -> "Pending";
            case PREPARING -> "Preparing";
            case READY     -> "Ready for Pickup / Dispatch";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Cancelled";
        };
    }

    /** CSS badge class keyed to the colour palette. */
    public String getStatusBadgeClass() {
        return switch (status) {
            case PENDING   -> "badge-pending";
            case PREPARING -> "badge-preparing";
            case READY     -> "badge-ready";
            case DELIVERED -> "badge-delivered";
            case CANCELLED -> "badge-cancelled";
        };
    }

    /** True while the order can still be edited or cancelled by the customer. */
    public boolean isEditable() {
        return status == OrderStatus.PENDING;
    }
}
