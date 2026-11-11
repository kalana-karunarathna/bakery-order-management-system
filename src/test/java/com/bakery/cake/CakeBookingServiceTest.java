package com.bakery.cake;

import com.bakery.cake.exception.AccessDeniedBookingException;
import com.bakery.cake.exception.BookingNotFoundException;
import com.bakery.cake.exception.InvalidBookingException;
import com.bakery.cake.model.*;
import com.bakery.cake.repository.BookingRepository;
import com.bakery.cake.service.CakeBookingService;
import com.bakery.cake.service.CurrentCustomerProvider;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CakeBookingService.
 * Uses Mockito to mock the repository and customer provider so we can test
 * the service's business rules in isolation, without touching the filesystem.
 *
 * <p>Tests cover:
 * - Ownership check (AccessDeniedBookingException)
 * - PENDING-only edit/delete (InvalidBookingException)
 * - Delivery date at least 3 days ahead (InvalidBookingException)
 */
class CakeBookingServiceTest {

    @TempDir
    Path tempDir;

    // Mocked dependencies
    private BookingRepository mockRepo;
    private CurrentCustomerProvider mockCustomerProvider;
    private HttpSession mockSession;
    private CakeBookingService service;

    @BeforeEach
    void setUp() {
        mockRepo             = Mockito.mock(BookingRepository.class);
        mockCustomerProvider = Mockito.mock(CurrentCustomerProvider.class);
        mockSession          = Mockito.mock(HttpSession.class);

        // Create the service with the temp upload dir
        service = new CakeBookingService(mockRepo, mockCustomerProvider,
                tempDir.toString());
    }

    // ── Helper to build a sample booking ─────────────────────────────────────

    private CakeBooking samplePendingBooking(String bookingId, String customerId) {
        BirthdayCake cake = new BirthdayCake("Chocolate", 2.0, 2, "HB!", "", 25, 25);
        return new CakeBooking(bookingId, customerId, cake,
                LocalDate.now().plusDays(10), "0771234567",
                BookingStatus.PENDING, null, LocalDateTime.now());
    }

    // ── Ownership check ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getBookingForCurrentCustomer() throws AccessDenied when customer IDs don't match")
    void ownershipCheckFails() {
        CakeBooking booking = samplePendingBooking("CB001", "U001");
        when(mockRepo.findById("CB001")).thenReturn(Optional.of(booking));
        // Logged-in user is U002, but the booking belongs to U001
        when(mockCustomerProvider.getCurrentCustomerId(mockSession)).thenReturn("U002");

        assertThrows(AccessDeniedBookingException.class, () ->
                service.getBookingForCurrentCustomer("CB001", mockSession),
                "Should throw AccessDenied when customer doesn't own the booking");
    }

    @Test
    @DisplayName("getBookingForCurrentCustomer() returns booking when owner matches")
    void ownershipCheckPasses() {
        CakeBooking booking = samplePendingBooking("CB001", "U001");
        when(mockRepo.findById("CB001")).thenReturn(Optional.of(booking));
        when(mockCustomerProvider.getCurrentCustomerId(mockSession)).thenReturn("U001");

        CakeBooking result = service.getBookingForCurrentCustomer("CB001", mockSession);
        assertNotNull(result);
        assertEquals("CB001", result.getBookingId());
    }

    // ── Booking not found ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getBookingForCurrentCustomer() throws BookingNotFound for unknown ID")
    void bookingNotFound() {
        when(mockRepo.findById("CB999")).thenReturn(Optional.empty());
        when(mockCustomerProvider.getCurrentCustomerId(mockSession)).thenReturn("U001");

        assertThrows(BookingNotFoundException.class, () ->
                service.getBookingForCurrentCustomer("CB999", mockSession));
    }

    // ── PENDING-only delete ───────────────────────────────────────────────────

    @Test
    @DisplayName("cancelBooking() throws InvalidBooking when status is not PENDING")
    void cancelNonPendingThrows() {
        // Booking is CONFIRMED, not PENDING
        BirthdayCake cake = new BirthdayCake("Choc", 1.0, 1, "", "", 25, 0);
        CakeBooking confirmed = new CakeBooking("CB001", "U001", cake,
                LocalDate.now().plusDays(10), "0771234567",
                BookingStatus.CONFIRMED, null, LocalDateTime.now());

        when(mockRepo.findById("CB001")).thenReturn(Optional.of(confirmed));
        when(mockCustomerProvider.getCurrentCustomerId(mockSession)).thenReturn("U001");

        assertThrows(InvalidBookingException.class, () ->
                service.cancelBooking("CB001", mockSession),
                "Should throw InvalidBooking when trying to cancel a CONFIRMED booking");
    }

    @Test
    @DisplayName("cancelBooking() succeeds for PENDING booking owned by the customer")
    void cancelPendingSucceeds() {
        CakeBooking pending = samplePendingBooking("CB001", "U001");
        when(mockRepo.findById("CB001")).thenReturn(Optional.of(pending));
        when(mockCustomerProvider.getCurrentCustomerId(mockSession)).thenReturn("U001");

        // Should NOT throw
        assertDoesNotThrow(() -> service.cancelBooking("CB001", mockSession));
        verify(mockRepo, times(1)).delete("CB001");
    }

    // ── Delivery date validation ──────────────────────────────────────────────

    @Test
    @DisplayName("createBooking() throws InvalidBooking when delivery date is tomorrow")
    void deliveryDateTooSoonThrows() {
        when(mockCustomerProvider.getCurrentCustomerId(mockSession)).thenReturn("U001");

        // Tomorrow is not 3 days ahead
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        assertThrows(InvalidBookingException.class, () ->
                service.createBooking("U001", "BIRTHDAY", "Chocolate",
                        2.0, 1, "", "", "25", "10",
                        tomorrow, "0771234567"),
                "Should reject delivery date that is not at least 3 days ahead");
    }

    @Test
    @DisplayName("createBooking() succeeds when delivery date is 3 days ahead")
    void deliveryDateExactlyThreeDaysAheadIsAccepted() {
        when(mockCustomerProvider.getCurrentCustomerId(mockSession)).thenReturn("U001");
        when(mockRepo.generateNextId()).thenReturn("CB001");

        LocalDate exactly3Days = LocalDate.now().plusDays(3);

        // Should NOT throw
        assertDoesNotThrow(() ->
                service.createBooking("U001", "BIRTHDAY", "Choc",
                        2.0, 1, "", "", "25", "10",
                        exactly3Days, "0771234567"));

        verify(mockRepo, times(1)).save(any(CakeBooking.class));
    }

    // ── getMyBookings ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMyBookings() returns only bookings for the current customer")
    void getMyBookingsFiltersCorrectly() {
        when(mockCustomerProvider.getCurrentCustomerId(mockSession)).thenReturn("U001");
        CakeBooking b1 = samplePendingBooking("CB001", "U001");
        CakeBooking b2 = samplePendingBooking("CB002", "U001");
        when(mockRepo.findByCustomerId("U001")).thenReturn(List.of(b1, b2));

        List<CakeBooking> results = service.getMyBookings(mockSession);
        assertEquals(2, results.size(), "Should return exactly 2 bookings for U001");
    }
}

