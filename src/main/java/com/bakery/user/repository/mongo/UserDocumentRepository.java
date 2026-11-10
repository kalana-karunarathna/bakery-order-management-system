package com.bakery.user.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/** Spring Data access to the {@code users} collection. Used only by {@link MongoUserRepository}. */
public interface UserDocumentRepository extends MongoRepository<UserDocument, String> {

    /** Exact match on the normalised email (backed by the unique email index). */
    Optional<UserDocument> findByEmail(String email);
}
