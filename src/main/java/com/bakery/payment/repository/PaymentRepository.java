package com.bakery.payment.repository;

import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.TargetType;

import java.util.List;
import java.util.function.Function;

/**
 * Payment storage contract. Two implementations follow {@code bakery.storage.type}, like users and orders:
 * {@link FilePaymentRepository} ({@code file}) and
 * {@link com.bakery.payment.repository.mongo.MongoPaymentRepository} ({@code mongodb}).
 *
 * <p>{@link #transaction} is the unit of work used by PaymentService: it loads every payment, runs the
 * action while holding the payment lock, validates the result with {@link PaymentRecords#validate} and
 * (when {@code write} is true) stores the changes. Balance and duplicate rules are therefore checked
 * against a consistent set of records.</p>
 */
public interface PaymentRepository {

    /** Runs {@code action} on all payments under the payment lock; stores changes when {@code write} is true. */
    <T> T transaction(Function<List<Payment>, T> action, boolean write);

    /** Next payment ID (PAY001, PAY002...). Only valid inside a write {@link #transaction}; never reused. */
    String nextId();

    /**
     * Payments recorded for one order or booking, read without taking the payment lock.
     * Used by Order Management to protect orders that have payments.
     */
    List<Payment> findByTarget(TargetType type, String targetId);

    /** Storage failure with a message that is safe to show; technical details stay in the cause/log. */
    class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
