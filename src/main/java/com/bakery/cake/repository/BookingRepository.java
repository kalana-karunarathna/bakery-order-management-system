package com.bakery.cake.repository;

import com.bakery.cake.model.CakeBooking;

import java.util.List;
import java.util.Optional;

/**
 * OOP: Abstraction – BookingRepository is an interface that declares WHAT
 * the data layer must do, without specifying HOW it does it.
 *
 * <p>The service layer depends only on this interface (not on the file
 * implementation), so the storage mechanism could later be swapped to a
 * database without touching the service or controller code.
 *
 * <p>This demonstrates the Dependency Inversion Principle: high-level modules
 * (service) depend on an abstraction (this interface), not on a concrete class.
 */
public interface BookingRepository {

    /**
     * Returns all bookings stored in the system.
     *
     * @return a list of all bookings; never null, may be empty
     */
    List<CakeBooking> findAll();

    /**
     * Finds a single booking by its unique ID.
     *
     * @param bookingId the ID to look up (e.g. "CB001")
     * @return an Optional containing the booking, or empty if not found
     */
    Optional<CakeBooking> findById(String bookingId);

    /**
     * Returns all bookings belonging to a specific customer.
     *
     * @param customerId the customer's ID string
     * @return list of bookings for that customer, may be empty
     */
    List<CakeBooking> findByCustomerId(String customerId);

    /**
     * Saves a new booking, assigning it a generated ID.
     *
     * @param booking the booking to persist (bookingId will be set by this method)
     */
    void save(CakeBooking booking);

    /**
     * Updates an existing booking (matches by bookingId).
     *
     * @param booking the booking with updated fields; must have an existing bookingId
     */
    void update(CakeBooking booking);

    /**
     * Deletes a booking by its ID.
     *
     * @param bookingId the ID of the booking to remove
     */
    void delete(String bookingId);

    /**
     * Generates the next available booking ID in the format CB001, CB002, ...
     * This is called internally before saving, but exposed so tests can verify it.
     *
     * @return the next unused booking ID string
     */
    String generateNextId();
}

