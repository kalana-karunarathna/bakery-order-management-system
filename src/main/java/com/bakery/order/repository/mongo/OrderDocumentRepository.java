package com.bakery.order.repository.mongo;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** Spring Data access to the {@code orders} collection. Used only by {@link MongoOrderRepository}. */
public interface OrderDocumentRepository extends MongoRepository<OrderDocument, String> {

    /** A customer's orders (backed by the customerId + orderDate index). */
    List<OrderDocument> findByCustomerId(String customerId, Sort sort);
}
