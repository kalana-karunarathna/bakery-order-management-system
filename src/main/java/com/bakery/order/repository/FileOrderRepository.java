package com.bakery.order.repository;

import com.bakery.common.TextFileStorage;
import com.bakery.order.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-backed repository for orders.
 *
 * <p>Record format (pipe-delimited, one order per line):</p>
 * <pre>
 * id | customerId | productId | productName | quantity | unitPrice
 *    | orderType  | deliveryAddress | status | orderDate
 * </pre>
 * <p>deliveryAddress is stored as an empty field for pickup orders.</p>
 *
 * <p>Safety: every read-modify-write runs under one lock, writes replace the file atomically,
 * lines that cannot be parsed are kept unchanged (and their IDs are never reused), and fields
 * containing {@code |} or line breaks are refused instead of corrupting the file.</p>
 *
 * <p>Active when {@code bakery.storage.type=file} (also the fallback if the property is missing).</p>
 */
@Repository
@ConditionalOnProperty(name = "bakery.storage.type", havingValue = "file", matchIfMissing = true)
public class FileOrderRepository implements OrderRepository {

    public static final String FILE_NAME = "orders.txt";
    private static final String HEADER =
            "# id|customerId|productId|productName|quantity|unitPrice|orderType|deliveryAddress|status|orderDate";
    private static final String UNREADABLE_NOTE = "# Unreadable records kept unchanged below; fix or remove them manually.";
    private static final String DELIMITER = "\\|";
    private static final String WRITE_DELIMITER = "|";
    private static final Pattern ID_NUMBER = Pattern.compile("^O(\\d+)$");

    private static final Logger log = LoggerFactory.getLogger(FileOrderRepository.class);

    private final TextFileStorage storage;
    private final Object lock = new Object();

    public FileOrderRepository(TextFileStorage storage) {
        this.storage = storage;
    }

    private record Snapshot(List<Order> orders, List<String> unreadable) {
    }

    /** Read-only result for the TXT → MongoDB migration: readable orders plus the unreadable line count. */
    public record ReadReport(List<Order> orders, int unreadableLines) {
    }

    /** Reads the file without changing it and reports how many lines could not be read. */
    public ReadReport readForMigration() throws IOException {
        synchronized (lock) {
            Snapshot snapshot = load();
            return new ReadReport(List.copyOf(snapshot.orders()), snapshot.unreadable().size());
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns every readable order in the file, skipping blank lines and comments. */
    @Override
    public List<Order> findAll() throws IOException {
        synchronized (lock) {
            return new ArrayList<>(load().orders());
        }
    }

    /** Finds a single order by its ID, e.g. {@code O001}. */
    @Override
    public Optional<Order> findById(String id) throws IOException {
        if (id == null) return Optional.empty();
        return findAll().stream().filter(o -> o.getId().equals(id)).findFirst();
    }

    /** Returns all orders placed by a given customer (exact ID match). */
    @Override
    public List<Order> findByCustomerId(String customerId) throws IOException {
        return findAll().stream()
                .filter(o -> o.getCustomerId().equals(customerId))
                .toList();
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Persists a new order. Generates the next sequential ID automatically.
     *
     * @return the saved order with its assigned ID
     */
    @Override
    public Order save(Order order) throws IOException {
        synchronized (lock) {
            Snapshot snapshot = load();
            order.setId(nextId(snapshot));
            List<Order> all = new ArrayList<>(snapshot.orders());
            all.add(order);
            writeAll(all, snapshot.unreadable());
            return order;
        }
    }

    /**
     * Replaces an existing order record identified by {@code order.getId()}.
     *
     * @throws java.util.NoSuchElementException if the ID is not found
     */
    @Override
    public void update(Order order) throws IOException {
        update(order.getId(), current -> order);
    }

    /**
     * Loads the latest copy of an order, applies {@code change} and saves it, all under the lock,
     * so a customer edit and an admin status change cannot overwrite each other.
     *
     * @throws java.util.NoSuchElementException if the ID is not found
     */
    @Override
    public Order update(String id, UnaryOperator<Order> change) throws IOException {
        synchronized (lock) {
            Snapshot snapshot = load();
            List<Order> all = new ArrayList<>(snapshot.orders());
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).getId().equals(id)) {
                    Order updated = change.apply(all.get(i));
                    if (updated == null || !id.equals(updated.getId())) {
                        throw new IllegalStateException("An update must keep the order ID.");
                    }
                    all.set(i, updated);
                    writeAll(all, snapshot.unreadable());
                    return updated;
                }
            }
            throw new java.util.NoSuchElementException("Order not found: " + id);
        }
    }

    /**
     * Removes an order from the file permanently.
     * Callers should cancel instead of deleting active orders — this method
     * is intended for admin use only.
     */
    @Override
    public void delete(String id) throws IOException {
        synchronized (lock) {
            Snapshot snapshot = load();
            List<Order> filtered = snapshot.orders().stream().filter(o -> !o.getId().equals(id)).toList();
            if (filtered.size() == snapshot.orders().size()) {
                throw new java.util.NoSuchElementException("Order not found: " + id);
            }
            writeAll(filtered, snapshot.unreadable());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Snapshot load() throws IOException {
        List<String> lines;
        try {
            lines = storage.readLines(FILE_NAME);
        } catch (NoSuchFileException e) {
            return new Snapshot(List.of(), List.of()); // file not yet created
        }
        List<Order> orders = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // Files saved by some Windows editors start with a byte-order mark; ignore it.
            if (i == 0 && line.startsWith("﻿")) line = line.substring(1);
            if (line.isBlank() || line.startsWith("#")) continue;
            Order o = parseLine(line);
            if (o == null || orders.stream().anyMatch(existing -> existing.getId().equals(o.getId()))) {
                // Position only: the line may contain customer data.
                log.warn("orders.txt line {} could not be read and is kept unchanged.", i + 1);
                unreadable.add(line);
            } else {
                orders.add(o);
            }
        }
        return new Snapshot(orders, unreadable);
    }

    private Order parseLine(String line) {
        String[] parts = line.split(DELIMITER, -1);
        if (parts.length != 10) return null;
        try {
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
            return OrderFactory.create(id, customerId, productId, productName, quantity, unitPrice,
                                       orderType, deliveryAddr, status, orderDate);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String serialise(Order o) {
        String deliveryAddress = (o instanceof DeliveryOrder d) ? d.getDeliveryAddress() : "";
        String[] fields = {
                o.getId(),
                o.getCustomerId(),
                o.getProductId(),
                o.getProductName(),
                String.valueOf(o.getQuantity()),
                String.format(java.util.Locale.ROOT, "%.2f", o.getUnitPrice()),
                o.getOrderType().name(),
                deliveryAddress == null ? "" : deliveryAddress,
                o.getStatus().name(),
                o.getOrderDate().toString()
        };
        for (String field : fields) {
            if (field == null || OrderRepository.containsUnsafeCharacters(field)) {
                // Defence in depth: OrderService validation should already have refused this.
                throw new IllegalStateException("Refusing to save an order field containing | or a line break.");
            }
        }
        return String.join(WRITE_DELIMITER, fields);
    }

    private void writeAll(List<Order> orders, List<String> unreadable) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Order o : orders) {
            lines.add(serialise(o));
        }
        if (!unreadable.isEmpty()) {
            lines.add(UNREADABLE_NOTE);
            lines.addAll(unreadable);
        }
        storage.writeLinesAtomically(FILE_NAME, lines);
    }

    /** Next ID after the highest ever seen (including unreadable lines): O001, O002, … */
    private static String nextId(Snapshot snapshot) {
        int max = 0;
        for (Order o : snapshot.orders()) max = Math.max(max, idNumber(o.getId()));
        for (String line : snapshot.unreadable()) max = Math.max(max, idNumber(line.split(DELIMITER, 2)[0].trim()));
        return String.format("O%03d", max + 1);
    }

    private static int idNumber(String id) {
        Matcher m = ID_NUMBER.matcher(id);
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }
}
