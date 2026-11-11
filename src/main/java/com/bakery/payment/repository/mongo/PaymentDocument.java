package com.bakery.payment.repository.mongo;

import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.Method;
import com.bakery.payment.model.Payment.Purpose;
import com.bakery.payment.model.Payment.Status;
import com.bakery.payment.model.Payment.TargetType;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/**
 * MongoDB shape of a payment ({@code payments} collection), kept separate from the domain classes so
 * {@link Payment}, {@code CashPayment}, {@code CardPayment} and {@code OnlineTransfer} stay unchanged.
 *
 * <p>Inheritance strategy: one collection discriminated by {@code method}; {@link Payment#create}
 * rebuilds the matching subclass. Money is stored as Decimal128 (exact), never as a double.
 * {@code referenceKey} is the upper-case reference, used for the case-insensitive unique index.
 * The invoice ID is derived ({@code INV-<id>}) and not stored.</p>
 */
@Document(collection = MongoPaymentRepository.COLLECTION)
public class PaymentDocument {

    @Id
    private String id;
    private String targetType;
    private String targetId;
    private String customerId;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal total;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;
    private String method;
    private String purpose;
    private String status;
    private String reference;
    private String referenceKey;
    private String requestKey;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    public PaymentDocument() {
    }

    static PaymentDocument from(Payment p, Long version, Instant updatedAt) {
        PaymentDocument d = new PaymentDocument();
        d.id = p.getId();
        d.targetType = p.getTargetType().name();
        d.targetId = p.getTargetId();
        d.customerId = p.getCustomerId();
        d.total = p.getTotal();
        d.amount = p.getAmount();
        d.method = p.getMethod().name();
        d.purpose = p.getPurpose().name();
        d.status = p.getStatus().name();
        d.reference = p.getReference();
        d.referenceKey = referenceKey(p.getReference());
        d.requestKey = p.getRequestKey();
        d.createdAt = p.getCreatedAt();
        d.updatedAt = updatedAt;
        d.version = version;
        return d;
    }

    /** Rebuilds the domain object; throws if the stored values break a domain rule. */
    Payment toDomain() {
        return Payment.create(Method.valueOf(method), id, TargetType.valueOf(targetType), targetId, customerId,
                total, amount, Purpose.valueOf(purpose), Status.valueOf(status), reference, requestKey, createdAt);
    }

    /** True if the business fields equal the given payment (used to write only changed records). */
    boolean sameAs(Payment p) {
        return id.equals(p.getId()) && status.equals(p.getStatus().name())
                && amount.compareTo(p.getAmount()) == 0 && reference.equals(p.getReference());
    }

    static String referenceKey(String reference) {
        return reference.toUpperCase(Locale.ROOT);
    }

    public String getId() { return id; }
    public Long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
