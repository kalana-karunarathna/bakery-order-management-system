package com.bakery.payment.integration;

import com.bakery.order.model.Order;
import com.bakery.order.model.OrderStatus;
import com.bakery.order.service.OrderService;
import com.bakery.order.service.OrderStorageException;
import com.bakery.payment.model.Payment.TargetType;
import com.bakery.payment.repository.PaymentRepository.StorageException;
import com.bakery.payment.service.PaymentIntegration.Actor;
import com.bakery.payment.service.PaymentIntegration.Payable;
import com.bakery.payment.service.PaymentIntegration.PayableProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Payable orders from Order Management, read on the server every time (never from the form):
 * <ul>
 *   <li>customers get only their own orders, admins get every order;</li>
 *   <li>cancelled orders cannot be paid;</li>
 *   <li>the amount due is the order's own {@code calculateTotal()} (including the delivery fee),
 *       converted to an exact two-decimal {@link BigDecimal}.</li>
 * </ul>
 * Cake bookings are not offered yet: Custom Cake Booking has no service to integrate with.
 */
@Component
@Profile("!payment-demo")
public class OrderPayableProvider implements PayableProvider {

    private static final Logger log = LoggerFactory.getLogger(OrderPayableProvider.class);

    private final OrderService orderService;

    public OrderPayableProvider(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public List<Payable> availableTo(Actor actor) {
        List<Order> orders;
        try {
            orders = actor.admin() ? orderService.getAllOrders() : orderService.getOrdersForCustomer(actor.customerId());
        } catch (OrderStorageException e) {
            throw new StorageException("Order information is unavailable right now. No payment was recorded.", e);
        }
        List<Payable> payable = new ArrayList<>();
        for (Order order : orders) {
            if (order.getStatus() == OrderStatus.CANCELLED) continue;
            try {
                payable.add(new Payable(TargetType.ORDER, order.getId(), order.getCustomerId(), amountDue(order)));
            } catch (IllegalArgumentException e) {
                log.warn("Order '{}' cannot be offered for payment: its stored values are invalid.", order.getId());
            }
        }
        return payable;
    }

    /**
     * The order total as money. {@code calculateTotal()} works in {@code double}, which can produce values
     * such as 3.3000000000000003 for 3 x 1.10; {@link BigDecimal#valueOf(double)} plus HALF_UP rounding to
     * cents gives the exact amount (3.30).
     */
    public static BigDecimal amountDue(Order order) {
        return BigDecimal.valueOf(order.calculateTotal()).setScale(2, RoundingMode.HALF_UP);
    }
}
