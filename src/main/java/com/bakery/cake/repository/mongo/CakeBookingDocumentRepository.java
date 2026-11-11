package com.bakery.cake.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data access interface for the {@code cake_bookings} collection.
 * Used internally by {@link MongoCakeBookingRepository}.
 */
public interface CakeBookingDocumentRepository extends MongoRepository<CakeBookingDocument, String> {

    List<CakeBookingDocument> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<CakeBookingDocument> findAllByOrderByCreatedAtDesc();
}

