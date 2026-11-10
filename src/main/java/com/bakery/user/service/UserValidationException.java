package com.bakery.user.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validation failure with one message per form field (field name to message).
 * A {@code null} field key means a general message for the whole form.
 */
public class UserValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public UserValidationException(Map<String, String> fieldErrors) {
        super("Please correct the highlighted fields.");
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public static UserValidationException of(String field, String message) {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(field, message);
        return new UserValidationException(errors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
