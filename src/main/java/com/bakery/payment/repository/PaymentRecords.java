package com.bakery.payment.repository;

import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.Purpose;
import com.bakery.payment.model.Payment.Status;
import com.bakery.payment.model.Payment.TargetType;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Whole-collection integrity rules shared by both payment stores. Storage fails closed: a set of
 * records that breaks a rule is never written (and a stored set that breaks one is never trusted).
 */
public final class PaymentRecords {

    private PaymentRecords() {
    }

    /** True for payments that hold part of an order's balance (Pending reserves it, Paid uses it). */
    public static boolean isActive(Payment payment) {
        return payment.getStatus() == Status.PENDING || payment.getStatus() == Status.PAID;
    }

    /**
     * Checks unique IDs, request keys and references; one owner per target; one agreed total among the
     * <em>active</em> payments of a target (closed records keep the total that applied when they were made,
     * so an order changed after a cancelled payment can still be paid); advance rules; and that active
     * payments never exceed the agreed total.
     */
    public static void validate(List<Payment> records) throws IOException {
        Set<String> ids = new HashSet<>(), keys = new HashSet<>(), references = new HashSet<>();
        Map<String, BigDecimal> reserved = new HashMap<>(), activeTotals = new HashMap<>();
        Map<String, String> owners = new HashMap<>();
        for (Payment p : records) {
            if (!ids.add(p.getId()) || !keys.add(p.getRequestKey())
                    || !references.add(p.getReference().toUpperCase(Locale.ROOT))) {
                throw new IOException("Duplicate payment record");
            }
            String target = p.getTargetType() + ":" + p.getTargetId();
            String owner = owners.putIfAbsent(target, p.getCustomerId());
            if (owner != null && !owner.equals(p.getCustomerId())) {
                throw new IOException("Conflicting target owner");
            }
            if (p.getPurpose() == Purpose.ADVANCE && (p.getTargetType() != TargetType.BOOKING
                    || p.getAmount().compareTo(p.getTotal()) >= 0)) {
                throw new IOException("Invalid advance record");
            }
            if (isActive(p)) {
                BigDecimal total = activeTotals.putIfAbsent(target, p.getTotal());
                if (total != null && total.compareTo(p.getTotal()) != 0) {
                    throw new IOException("Conflicting target details");
                }
                reserved.merge(target, p.getAmount(), BigDecimal::add);
            }
        }
        for (var entry : reserved.entrySet()) {
            if (entry.getValue().compareTo(activeTotals.get(entry.getKey())) > 0) {
                throw new IOException("Payment totals exceed agreed total");
            }
        }
    }
}
