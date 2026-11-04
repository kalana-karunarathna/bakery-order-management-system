package com.bakery.cake.service;

import jakarta.servlet.http.HttpSession;

/**
 * OOP: Abstraction (Interface) – Defines the contract for retrieving the
 * currently logged-in customer's ID from the HTTP session.
 *
 * <p>This interface deliberately keeps the cake module decoupled from the User
 * Management module. The cake service calls {@link #getCurrentCustomerId} and
 * gets back a plain String customer ID; it never imports any User class.
 *
 * <p>When the User module delivers its login mechanism, they just need to
 * provide a different implementation of this interface — the cake service does
 * not need to change at all.
 */
public interface CurrentCustomerProvider {

    /**
     * Returns the ID of the currently authenticated customer.
     *
     * <p>Implementations read the ID from the HTTP session, a JWT token,
     * or any other auth mechanism — the caller does not need to know which.
     *
     * @param session the current HTTP session
     * @return the customer ID string (never null; use a fallback or redirect if absent)
     */
    String getCurrentCustomerId(HttpSession session);

    /**
     * Checks if the currently active user has administrator privileges.
     * Defaults to false for backward compatibility and test mocks.
     *
     * @return true if the signed-in user is an admin
     */
    default boolean isAdmin() {
        return false;
    }

    /**
     * Checks if a user is actively authenticated (not anonymous).
     *
     * @return true if signed in
     */
    default boolean isAuthenticated() {
        return true;
    }
}

