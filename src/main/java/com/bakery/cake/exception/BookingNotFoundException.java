package com.bakery.cake.exception;

/**
 * OOP: Custom Exception (Encapsulation of error state) –
 * Thrown when a booking record cannot be found in the data file.
 *
 * <p>Having a dedicated exception type lets the controller catch exactly this
 * error and show a 404-style page, rather than catching a generic Exception
 * and hiding other bugs.
 */
public class BookingNotFoundException extends RuntimeException {

    /** ID of the booking that was not found, so the caller can include it in a message. */
    private final String bookingId;

    /**
     * Creates the exception with a message and the missing booking ID.
     *
     * @param bookingId the ID that was looked up but not found
     */
    public BookingNotFoundException(String bookingId) {
        // Pass a helpful message to the parent RuntimeException
        super("Booking not found: " + bookingId);
        this.bookingId = bookingId;
    }

    /**
     * Returns the booking ID that was not found.
     *
     * @return the missing booking ID string
     */
    public String getBookingId() {
        return bookingId;
    }
}

