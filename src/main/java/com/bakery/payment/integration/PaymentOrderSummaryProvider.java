package com.bakery.payment.integration;

import com.bakery.order.model.Order;
import com.bakery.order.service.OrderPaymentSummary;
import com.bakery.order.service.OrderPaymentSummaryProvider;
import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.Status;
import com.bakery.payment.model.Payment.TargetType;
import com.bakery.payment.repository.PaymentRepository;
import com.bakery.payment.repository.PaymentRepository.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Builds an order's payment summary from its payment records (read on every request, never stored on
 * the order). The order total is the same server-side amount the payment module charges against
 * ({@link OrderPayableProvider#amountDue}). Paid records count as paid; Pending records are shown as
 * awaiting confirmation; Refunded records are shown separately and are not counted; Failed and Cancelled
 * records are ignored.
 */
@Component
public class PaymentOrderSummaryProvider implements OrderPaymentSummaryProvider {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderSummaryProvider.class);

    private final PaymentRepository payments;

    public PaymentOrderSummaryProvider(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    public OrderPaymentSummary summarize(Order order) {
        BigDecimal total = OrderPayableProvider.amountDue(order);
        List<Payment> records;
        try {
            records = payments.findByTarget(TargetType.ORDER, order.getId());
        } catch (StorageException e) {
            log.warn("Payment summary for order '{}' is unavailable: payment storage could not be read.", order.getId());
            return OrderPaymentSummary.unavailable(total);
        }
        return OrderPaymentSummary.of(total, sum(records, Status.PAID), sum(records, Status.PENDING),
                sum(records, Status.REFUNDED));
    }

    private static BigDecimal sum(List<Payment> records, Status status) {
        return records.stream().filter(p -> p.getStatus() == status)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
