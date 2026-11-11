package com.bakery.payment.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** Spring Data access to the {@code payments} collection (used by {@link MongoPaymentRepository}). */
public interface PaymentDocumentRepository extends MongoRepository<PaymentDocument, String> {

    List<PaymentDocument> findByTargetTypeAndTargetId(String targetType, String targetId);
}
