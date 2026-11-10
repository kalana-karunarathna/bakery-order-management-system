package com.bakery.user.repository;

import com.bakery.user.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Storage contract for accounts. Two implementations, selected by {@code bakery.storage.type}:
 * <ul>
 *   <li>{@link FileUserRepository}: {@code users.txt} (TXT mode, also used by most tests)</li>
 *   <li>{@link com.bakery.user.repository.mongo.MongoUserRepository}: MongoDB {@code users} collection</li>
 * </ul>
 * Business rules stay in {@code UserService}; implementations only store and load.
 *
 * <p>Errors: {@link DuplicateEmailException} for a taken email, {@link UserStorageException} when the
 * store cannot be reached (message safe to show), {@link IllegalArgumentException} for a missing account.</p>
 */
public interface UserRepository {

    /** A change applied to the latest copy of one account. May return a new object (e.g. a type change). */
    @FunctionalInterface
    interface UserChange {
        User apply(User current, List<User> allUsers);
    }

    List<User> findAll();

    Optional<User> findById(String id);

    /** Look-up by email; the email is normalised (trimmed, lower-case) first. */
    Optional<User> findByEmail(String email);

    /**
     * Saves a new account (which must not have an ID yet) and assigns the next sequential ID
     * (U001, U002, ...). IDs are never reused.
     *
     * @throws DuplicateEmailException if the email is already used
     */
    User save(User newUser);

    /**
     * Loads the latest copy of an account, applies {@code change} and saves the result so that
     * concurrent changes cannot silently overwrite each other.
     *
     * @throws IllegalArgumentException if the account does not exist
     * @throws DuplicateEmailException  if the change gives it an email used by another account
     */
    User update(String id, UserChange change);
}
