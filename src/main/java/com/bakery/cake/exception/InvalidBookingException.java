package com.bakery.cake.exception;

/**
 * OOP: Custom Exception – Thrown when user-supplied data fails validation.
 *
 * <p>Setters in the model classes throw this exception when they detect
 * illegal values (empty strings, negative weight, past dates, etc.).
 * The service layer also throws it for business-rule violations such as
 * "delivery date must be at least 3 days from today".
 */
public class InvalidBookingException extends RuntimeException {

    /** The name of the field that failed validation, for helpful error messages. */
    private final String field;

    /**
     * Creates the exception for a specific field that has an invalid value.
     *
     * @param field   name of the field (e.g. "weight", "deliveryDate")
     * @param message explanation of what is wrong
     */
    public InvalidBookingException(String field, String message) {
        super(message);
        this.field = field;
    }

    /**
     * Creates the exception without a specific field (for general validation failures).
     *
     * @param message explanation of what is wrong
     */
    public InvalidBookingException(String message) {
        super(message);
        this.field = null;
    }

    /**
     * Returns the field name, or {@code null} if this is a general validation error.
     *
     * @return the field name string, possibly null
     */
    public String getField() {
        return field;
    }
}

