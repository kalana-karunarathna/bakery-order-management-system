package com.bakery.payment.integration;

import com.bakery.order.service.OrderPaymentGuard;
import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.TargetType;
import com.bakery.payment.repository.PaymentRecords;
import com.bakery.payment.repository.PaymentRepository;
import com.bakery.payment.repository.PaymentRepository.StorageException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tells Order Management whether an order has payments, so it can refuse unsafe edits, cancellations
 * and deletions. If payment data cannot be read, the order change is refused (fail closed).
 */
@Component
public class PaymentOrderGuard implements OrderPaymentGuard {

    private final PaymentRepository payments;

    public PaymentOrderGuard(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    public PaymentState paymentState(String orderId) {
        List<Payment> recorded;
        try {
            recorded = payments.findByTarget(TargetType.ORDER, orderId);
        } catch (StorageException e) {
            throw new IllegalStateException("Payment information is unavailable right now, so this order can't be"
                    + " changed. Please try again later.");
        }
        if (recorded.isEmpty()) return PaymentState.NONE;
        return recorded.stream().anyMatch(PaymentRecords::isActive) ? PaymentState.ACTIVE : PaymentState.CLOSED_ONLY;
    }
}
