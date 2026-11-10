package com.bakery.order.repository;

import com.bakery.common.TextFileStorage;
import com.bakery.order.model.*;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * File-backed repository for orders.
 *
 * <p>Record format (pipe-delimited, one order per line):</p>
 * <pre>
 * id | customerId | productId | productName | quantity | unitPrice
 *    | orderType  | deliveryAddress | status | orderDate
 * </pre>
 * <p>deliveryAddress is stored as an empty field for pickup orders.</p>
 */
@Repository
public class OrderRepository {

    private static final String FILE_NAME = "orders.txt";
    private static final String DELIMITER = "\\|";
    private static final String WRITE_DELIMITER = "|";

    private final TextFileStorage storage;

    public OrderRepository(TextFileStorage storage) {
        this.storage = storage;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns every order in the file, skipping blank lines and comments. */
    public List<Order> findAll() throws IOException {
        List<Order> orders = new ArrayList<>();
        List<String> lines;
        try {
            lines = storage.readLines(FILE_NAME);
        } catch (IOException e) {
            return orders; // file not yet created
        }
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) continue;
            Order o = parseLine(line);
            if (o != null) orders.add(o);
        }
        return orders;
    }

    /** Finds a single order by its ID, e.g. {@code O001}. */
    public Optional<Order> findById(String id) throws IOException {
        return findAll().stream().filter(o -> o.getId().equals(id)).findFirst();
    }

    /** Returns all orders placed by a given customer. */
    public List<Order> findByCustomerId(String customerId) throws IOException {
        return findAll().stream()
                .filter(o -> o.getCustomerId().equalsIgnoreCase(customerId))
                .toList();
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Persists a new order. Generates the next sequential ID automatically.
     *
     * @return the saved order with its assigned ID
     */
    public Order save(Order order) throws IOException {
        List<Order> all = findAll();
        order.setId(nextId(all));
        all.add(order);
        writeAll(all);
        return order;
    }

    /**
     * Replaces an existing order record identified by {@code order.getId()}.
     *
     * @throws IllegalArgumentException if the ID is not found
     */
    public void update(Order order) throws IOException {
        List<Order> all = findAll();
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(order.getId())) {
                all.set(i, order);
                found = true;
                break;
            }
        }
        if (!found) throw new IllegalArgumentException("Order not found: " + order.getId());
        writeAll(all);
    }

    /**
     * Removes an order from the file permanently.
     * Callers should cancel instead of deleting active orders — this method
     * is intended for admin use only.
     */
    public void delete(String id) throws IOException {
        List<Order> all = findAll();
        List<Order> filtered = all.stream().filter(o -> !o.getId().equals(id)).toList();
        if (filtered.size() == all.size()) {
            throw new IllegalArgumentException("Order not found: " + id);
        }
        writeAll(filtered);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Order parseLine(String line) {
        String[] parts = line.split(DELIMITER, -1);
        if (parts.length < 10) return null;

        String id            = parts[0].trim();
        String customerId    = parts[1].trim();
        String productId     = parts[2].trim();
        String productName   = parts[3].trim();
        int    quantity      = Integer.parseInt(parts[4].trim());
        double unitPrice     = Double.parseDouble(parts[5].trim());
        OrderType orderType  = OrderType.valueOf(parts[6].trim());
        String deliveryAddr  = parts[7].trim();
        OrderStatus status   = OrderStatus.valueOf(parts[8].trim());
        LocalDate orderDate  = LocalDate.parse(parts[9].trim());

        if (orderType == OrderType.DELIVERY) {
            return new DeliveryOrder(id, customerId, productId, productName,
                                     quantity, unitPrice, deliveryAddr, status, orderDate);
        } else {
            return new PickupOrder(id, customerId, productId, productName,
                                   quantity, unitPrice, status, orderDate);
        }
    }

    private String serialise(Order o) {
        String deliveryAddress = (o instanceof DeliveryOrder d) ? d.getDeliveryAddress() : "";
        return String.join(WRITE_DELIMITER,
                o.getId(),
                o.getCustomerId(),
                o.getProductId(),
                o.getProductName(),
                String.valueOf(o.getQuantity()),
                String.format("%.2f", o.getUnitPrice()),
                o.getOrderType().name(),
                deliveryAddress,
                o.getStatus().name(),
                o.getOrderDate().toString()
        );
    }

    private void writeAll(List<Order> orders) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# id|customerId|productId|productName|quantity|unitPrice|orderType|deliveryAddress|status|orderDate");
        for (Order o : orders) {
            lines.add(serialise(o));
        }
        storage.writeLines(FILE_NAME, lines);
    }

    /** Generates the next ID in the sequence O001, O002, … */
    private String nextId(List<Order> existing) {
        int max = existing.stream()
                .mapToInt(o -> {
                    try { return Integer.parseInt(o.getId().substring(1)); }
                    catch (NumberFormatException e) { return 0; }
                })
                .max().orElse(0);
        return String.format("O%03d", max + 1);
    }
}
