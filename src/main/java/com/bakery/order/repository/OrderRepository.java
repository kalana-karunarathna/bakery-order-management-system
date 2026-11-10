package com.bakery.order.repository;

import com.bakery.order.model.Order;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Storage contract for orders. Two implementations, selected by {@code bakery.storage.type}:
 * <ul>
 *   <li>{@link FileOrderRepository}: {@code orders.txt}</li>
 *   <li>{@link com.bakery.order.repository.mongo.MongoOrderRepository}: MongoDB {@code orders} collection</li>
 * </ul>
 * Ownership checks, validation and the status workflow live in {@code OrderService}, not here.
 *
 * <p>Errors: {@link IOException} when the store cannot be reached (OrderService turns it into a
 * friendly message), {@link java.util.NoSuchElementException} when an order to change does not exist.</p>
 */
public interface OrderRepository {

    List<Order> findAll() throws IOException;

    Optional<Order> findById(String id) throws IOException;

    /** All orders of one customer (exact customer ID match). */
    List<Order> findByCustomerId(String customerId) throws IOException;

    /** Persists a new order and assigns the next sequential ID (O001, O002, ...). IDs are never reused. */
    Order save(Order order) throws IOException;

    /** Replaces an existing order identified by {@code order.getId()}. */
    void update(Order order) throws IOException;

    /**
     * Loads the latest copy of an order, applies {@code change} and saves it, so concurrent changes
     * (e.g. a customer edit and an admin status change) cannot overwrite each other.
     */
    Order update(String id, UnaryOperator<Order> change) throws IOException;

    /** Permanently removes an order (admin only). */
    void delete(String id) throws IOException;

    /** True for the TXT delimiter, line breaks and other control characters (refused in every store). */
    static boolean containsUnsafeCharacters(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '|' || Character.isISOControl(c) || c == ' ' || c == ' ') return true;
        }
        return false;
    }
}
