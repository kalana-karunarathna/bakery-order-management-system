package com.bakery.order.service;

import com.bakery.order.model.*;
import com.bakery.order.repository.OrderRepository;
import com.bakery.user.service.AuthenticatedUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Business logic layer for order management.
 *
 * <p>Validates input, enforces ownership (a customer can only see and change their own orders),
 * enforces the order status workflow, and delegates persistence to {@link OrderRepository}.
 * The customer ID always comes from the signed-in user (see OrderController), never from a form.</p>
 *
 * <p>Orders with payments are protected through {@link OrderPaymentGuard}: Pending or Paid payments
 * block editing and cancelling, and any payment record blocks deletion. The payment check runs before
 * the order is locked for the update, so order and payment storage never wait for each other.</p>
 */
@Service
public class OrderService {

    public static final int MAX_QUANTITY = 100;
    /** Upper bound for the (still client-supplied) unit price, see {@link #placeOrder}. */
    public static final double MAX_UNIT_PRICE = 100_000.00;
    private static final Pattern PRODUCT_ID = Pattern.compile("^[A-Za-z0-9-]{1,40}$");

    /**
     * Allowed status changes (admin): forward through the kitchen workflow, or cancel any order
     * that has not been delivered. Delivered and cancelled orders are final.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.PENDING,   EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED),
            OrderStatus.PREPARING, EnumSet.of(OrderStatus.READY, OrderStatus.CANCELLED),
            OrderStatus.READY,     EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
            OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

    private final OrderRepository repo;
    private final OrderPaymentGuard paymentGuard;
    /** Optional: present when Payment Management is installed. */
    private final OrderPaymentSummaryProvider paymentSummaries;

    /** Without a payment module (e.g. unit tests): no payment protection is needed. */
    public OrderService(OrderRepository repo) {
        this(repo, OrderPaymentGuard.NONE);
    }

    public OrderService(OrderRepository repo, OrderPaymentGuard paymentGuard) {
        this(repo, paymentGuard, null);
    }

    public OrderService(OrderRepository repo, OrderPaymentGuard paymentGuard, OrderPaymentSummaryProvider paymentSummaries) {
        this.repo = repo;
        this.paymentGuard = paymentGuard == null ? OrderPaymentGuard.NONE : paymentGuard;
        this.paymentSummaries = paymentSummaries;
    }

    @Autowired
    public OrderService(OrderRepository repo, ObjectProvider<OrderPaymentGuard> paymentGuard,
                        ObjectProvider<OrderPaymentSummaryProvider> paymentSummaries) {
        this(repo, paymentGuard.getIfAvailable(() -> OrderPaymentGuard.NONE), paymentSummaries.getIfAvailable());
    }

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Places a new order for the signed-in customer.
     *
     * <p><b>Known limitation:</b> product ID, name and unit price are still entered on the form
     * because Product Management has no catalogue service yet. The price is bounded and the total
     * is always calculated here (never accepted from the client), but the unit price itself must be
     * looked up from the product catalogue by {@code productId} once that module exists.</p>
     *
     * @param customerId the authenticated customer's ID (from the session, never from the form)
     */
    public Order placeOrder(String customerId, String productId, String productName,
                            int quantity, double unitPrice,
                            OrderType orderType, String deliveryAddress) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalStateException("Please sign in to place an order.");
        }
        String pid = trim(productId);
        String name = collapse(productName);
        if (pid.isEmpty()) throw new IllegalArgumentException("Please enter the product ID.");
        if (!PRODUCT_ID.matcher(pid).matches())
            throw new IllegalArgumentException("Product ID can only contain letters, numbers and hyphens.");
        if (name.length() < 2 || name.length() > 100)
            throw new IllegalArgumentException("Please enter a product name (2 to 100 characters).");
        if (OrderRepository.containsUnsafeCharacters(name))
            throw new IllegalArgumentException("Product name contains characters that are not allowed.");
        validateQuantity(quantity);
        if (!Double.isFinite(unitPrice) || unitPrice <= 0) {
            throw new IllegalArgumentException("Unit price must be greater than zero.");
        }
        if (unitPrice > MAX_UNIT_PRICE) {
            throw new IllegalArgumentException("Unit price looks too high. Please check it.");
        }
        double price = BigDecimal.valueOf(unitPrice).setScale(2, RoundingMode.HALF_UP).doubleValue();
        if (orderType == null) {
            throw new IllegalArgumentException("Please choose pickup or delivery.");
        }

        Order order;
        if (orderType == OrderType.DELIVERY) {
            String address = validateAddress(deliveryAddress);
            order = new DeliveryOrder(null, customerId, pid, name, quantity, price, address,
                                      OrderStatus.PENDING, LocalDate.now());
        } else {
            order = new PickupOrder(null, customerId, pid, name, quantity, price,
                                    OrderStatus.PENDING, LocalDate.now());
        }
        return io(() -> repo.save(order));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** All orders, newest first (admin). */
    public List<Order> getAllOrders() {
        return io(() -> repo.findAll().stream().sorted(NEWEST_FIRST).toList());
    }

    /** The signed-in customer's own orders, newest first. */
    public List<Order> getOrdersForCustomer(String customerId) {
        return io(() -> repo.findByCustomerId(customerId).stream().sorted(NEWEST_FIRST).toList());
    }

    /**
     * Returns an order if the viewer may see it: admins see every order, customers only their own.
     *
     * @throws OrderNotFoundException if it does not exist or belongs to someone else
     */
    public Order getOrderFor(String orderId, AuthenticatedUser viewer) {
        Order order = io(() -> repo.findById(orderId)).orElseThrow(OrderNotFoundException::new);
        if (viewer.isAdmin() || order.getCustomerId().equals(viewer.id())) {
            return order;
        }
        throw new OrderNotFoundException();
    }

    /** Returns one of the customer's own orders. */
    public Order getOwnOrder(String orderId, String customerId) {
        Order order = io(() -> repo.findById(orderId)).orElseThrow(OrderNotFoundException::new);
        requireOwner(order, customerId);
        return order;
    }

    // ── Update (customer) ─────────────────────────────────────────────────────

    /**
     * Lets a customer change the quantity and/or delivery address of their own order
     * before the bakery starts preparing it. Only PENDING orders may be edited.
     */
    public Order updateOwnOrder(String orderId, String customerId, int newQuantity, String newDeliveryAddress) {
        validateQuantity(newQuantity);
        getOwnOrder(orderId, customerId);   // ownership first, so another customer learns nothing
        requireNoActivePayments(orderId, CUSTOMER_PAYMENT_LOCK);
        return io(() -> repo.update(orderId, order -> {
            requireOwner(order, customerId);
            if (order.getStatus() != OrderStatus.PENDING) {
                throw new IllegalStateException("Only pending orders can be edited.");
            }
            order.setQuantity(newQuantity);
            if (order instanceof DeliveryOrder delivery) {
                delivery.setDeliveryAddress(validateAddress(newDeliveryAddress));
            }
            return order;
        }));
    }

    /** A customer may cancel their own order only while it is still PENDING. */
    public Order cancelOwnOrder(String orderId, String customerId) {
        getOwnOrder(orderId, customerId);
        requireNoActivePayments(orderId, CUSTOMER_PAYMENT_LOCK);
        return io(() -> repo.update(orderId, order -> {
            requireOwner(order, customerId);
            if (order.getStatus() != OrderStatus.PENDING) {
                throw new IllegalStateException("This order is already being prepared, so it can't be cancelled online. Please contact the bakery.");
            }
            order.setStatus(OrderStatus.CANCELLED);
            return order;
        }));
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    /** Updates the status of an order (admin), following the allowed workflow. */
    public Order updateStatus(String orderId, OrderStatus newStatus) {
        if (newStatus == null) throw new IllegalArgumentException("Choose a status.");
        if (newStatus == OrderStatus.CANCELLED) {
            requireNoActivePayments(orderId, "Order " + orderId + " has pending or paid payments. Cancel or fail the"
                    + " pending payments, or refund the paid ones, in Payments before cancelling the order.");
        }
        return io(() -> repo.update(orderId, order -> {
            if (!canTransition(order.getStatus(), newStatus)) {
                throw new IllegalStateException("An order can't move from " + label(order.getStatus())
                        + " to " + label(newStatus) + ".");
            }
            order.setStatus(newStatus);
            return order;
        }));
    }

    /**
     * True if Pending or Paid payments lock the order (used by the order pages to hide Edit / Cancel).
     * If payment data cannot be read, the order is shown as locked; the server checks again on every change.
     */
    public boolean isPaymentLocked(String orderId) {
        try {
            return paymentGuard.paymentState(orderId) == OrderPaymentGuard.PaymentState.ACTIVE;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /**
     * Payment summary of an order the caller has already been allowed to see (via {@link #getOrderFor} or
     * {@link #getOrdersForCustomer}). Empty when no payment module is installed. Read-only: it never
     * changes the order or its status.
     */
    public Optional<OrderPaymentSummary> paymentSummary(Order order) {
        return paymentSummaries == null ? Optional.empty() : Optional.of(paymentSummaries.summarize(order));
    }

    /** Payment summaries keyed by order ID (empty map without a payment module). */
    public Map<String, OrderPaymentSummary> paymentSummaries(List<Order> orders) {
        Map<String, OrderPaymentSummary> result = new java.util.LinkedHashMap<>();
        for (Order order : orders) paymentSummary(order).ifPresent(summary -> result.put(order.getId(), summary));
        return result;
    }

    /** True if an admin may move an order from {@code from} to {@code to}. */
    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** Statuses an admin may choose next for an order in {@code current} status. */
    public static Set<OrderStatus> nextStatuses(OrderStatus current) {
        return TRANSITIONS.getOrDefault(current, Set.of());
    }

    /** Permanently removes an order record (admin only). Orders with payment records are kept. */
    public void deleteOrder(String orderId) {
        if (paymentGuard.paymentState(orderId) != OrderPaymentGuard.PaymentState.NONE) {
            throw new IllegalStateException("Order " + orderId + " has payment records, so it can't be deleted."
                    + " Cancel the order instead to keep its payment history.");
        }
        io(() -> {
            repo.delete(orderId);
            return null;
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final String CUSTOMER_PAYMENT_LOCK = "This order has a payment recorded, so it can't be changed"
            + " or cancelled online. Please contact the bakery.";

    private void requireNoActivePayments(String orderId, String message) {
        if (paymentGuard.paymentState(orderId) == OrderPaymentGuard.PaymentState.ACTIVE) {
            throw new IllegalStateException(message);
        }
    }

    private static final Comparator<Order> NEWEST_FIRST =
            Comparator.comparing(Order::getOrderDate).thenComparing(Order::getId).reversed();

    private static void requireOwner(Order order, String customerId) {
        if (customerId == null || !order.getCustomerId().equals(customerId)) {
            throw new OrderNotFoundException();
        }
    }

    private static void validateQuantity(int quantity) {
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be at least 1.");
        if (quantity > MAX_QUANTITY)
            throw new IllegalArgumentException("For more than " + MAX_QUANTITY + " items, please contact the bakery.");
    }

    private static String validateAddress(String address) {
        String value = collapse(address);
        if (value.isEmpty()) throw new IllegalArgumentException("Delivery address is required for delivery orders.");
        if (value.length() < 5 || value.length() > 250)
            throw new IllegalArgumentException("Delivery address must be between 5 and 250 characters.");
        if (OrderRepository.containsUnsafeCharacters(value))
            throw new IllegalArgumentException("Delivery address contains characters that are not allowed (such as |).");
        return value;
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }

    /** Trims and collapses runs of spaces; line breaks are left so validation rejects them. */
    private static String collapse(String value) {
        return value == null ? "" : value.strip().replaceAll("\\p{Zs}+", " ");
    }

    private static String label(OrderStatus status) {
        String s = status.name().toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @FunctionalInterface
    private interface IoAction<T> {
        T run() throws IOException;
    }

    private static <T> T io(IoAction<T> action) {
        try {
            return action.run();
        } catch (java.util.NoSuchElementException e) {
            throw new OrderNotFoundException();
        } catch (IOException e) {
            throw new OrderStorageException(e);
        }
    }
}
