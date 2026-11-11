package com.bakery.cake.exception;

/**
 * OOP: Custom Exception – Thrown when a customer tries to view, edit, or
 * delete a booking that belongs to a different customer.
 *
 * <p>Ownership enforcement is a business rule that belongs in the service layer.
 * This dedicated exception lets the controller present a clear "forbidden"
 * message rather than leaking that the booking exists at all.
 */
public class AccessDeniedBookingException extends RuntimeException {

    /**
     * Creates the exception with an explanatory message.
     *
     * @param message description of the access violation
     */
    public AccessDeniedBookingException(String message) {
        super(message);
    }
}

