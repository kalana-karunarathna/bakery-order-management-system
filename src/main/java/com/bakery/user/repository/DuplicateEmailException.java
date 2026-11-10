package com.bakery.user.repository;

/** Thrown when saving would give two accounts the same (normalised) email address. */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("An account with this email address already exists.");
    }
}
