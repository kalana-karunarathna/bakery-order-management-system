package com.bakery.cake.model;

/**
 * OOP: Enum (special class) – Represents the lifecycle states of a cake booking.
 * Using an enum instead of a plain String means only valid states can ever exist;
 * the compiler catches typos at build time rather than at runtime.
 *
 * <p>PENDING   → booking just created, customer may still edit or cancel
 * CONFIRMED  → bakery has accepted; no more customer edits allowed
 * COMPLETED  → cake has been delivered / picked up
 * CANCELLED  → customer or bakery cancelled the booking
 */
public enum BookingStatus {

    /** Booking submitted but not yet reviewed by the bakery. */
    PENDING,

    /** Bakery has confirmed the booking; editing is locked. */
    CONFIRMED,

    /** Booking fulfilled – cake delivered or collected. */
    COMPLETED,

    /** Booking was cancelled before completion. */
    CANCELLED;

    /**
     * Returns a human-friendly label for display in the UI.
     * Example: PENDING → "Pending"
     *
     * @return capitalised first letter, rest lower-case
     */
    public String getLabel() {
        // Convert "PENDING" → "Pending" for nicer display in HTML
        String name = this.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    /**
     * Returns the Bootstrap badge CSS class that matches this status colour.
     * This keeps presentation logic out of the templates themselves.
     *
     * @return a Bootstrap bg-* class string
     */
    public String getBadgeClass() {
        // OOP: Polymorphism-like behaviour through switch – each status has its own badge colour
        return switch (this) {
            case PENDING   -> "bg-warning text-dark";
            case CONFIRMED -> "bg-primary";
            case COMPLETED -> "bg-success";
            case CANCELLED -> "bg-secondary";
        };
    }
}

