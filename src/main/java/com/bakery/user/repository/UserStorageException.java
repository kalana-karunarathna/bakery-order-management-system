package com.bakery.user.repository;

/** Wraps file I/O failures so callers get one unchecked, user-safe error type. */
public class UserStorageException extends RuntimeException {

    public UserStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
