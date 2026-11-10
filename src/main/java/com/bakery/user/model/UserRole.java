package com.bakery.user.model;

/**
 * Permission level of an account. Spring Security exposes it as the authority {@code ROLE_<name>}.
 * The role is decided by the account's class (see {@link User#getRole()}), never by form input.
 */
public enum UserRole {
    CUSTOMER,
    ADMIN;

    /** Authority string used by Spring Security, e.g. {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
