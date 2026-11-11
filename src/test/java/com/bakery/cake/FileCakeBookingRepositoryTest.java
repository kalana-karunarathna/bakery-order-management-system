package com.bakery.cake;

import com.bakery.cake.exception.InvalidBookingException;
import com.bakery.cake.model.*;
import com.bakery.cake.repository.FileCakeBookingRepository;
import com.bakery.common.TextFileStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for FileCakeBookingRepository.
 * Uses a @TempDir so tests never touch the real data files.
 *
 * <p>Tests cover: save, findById, findByCustomerId, update, delete,
 * generateNextId, and corrupt-line skipping.
 */
class FileCakeBookingRepositoryTest {

    @TempDir
    Path tempDir;

    private FileCakeBookingRepository repository;

    /** Create a fresh repository pointing to the temp directory before each test. */
    @BeforeEach
    void setUp() {
        // Build a TextFileStorage that writes to tempDir
        TextFileStorage storage = new TextFileStorage(tempDir.toString());
        repository = new FileCakeBookingRepository(storage);
    }

    // ── Helper to create a minimal CakeBooking ────────────────────────────────

    private CakeBooking makeBirthday(String customerId, LocalDate deliveryDate) {
        BirthdayCake cake = new BirthdayCake("Chocolate", 2.0, 2,
                "Happy Birthday!", "", 25, 25);
        return new CakeBooking(customerId, cake, deliveryDate, "0771234567");
    }

    // ── save + findById ───────────────────────────────────────────────────────

    @Test
    @DisplayName("save() assigns a CB001 ID and findById() retrieves it")
    void saveAndFindById() {
        CakeBooking booking = makeBirthday("U001", LocalDate.now().plusDays(10));
        repository.save(booking);

        assertEquals("CB001", booking.getBookingId(), "First saved booking should get CB001");

        Optional<CakeBooking> found = repository.findById("CB001");
        assertTrue(found.isPresent(), "findById should find CB001");
        assertEquals("U001", found.get().getCustomerId());
        assertEquals("Birthday Cake", found.get().getCake().getCakeType());
    }

    // ── generateNextId ────────────────────────────────────────────────────────

    @Test
    @DisplayName("generateNextId() increments sequentially")
    void generateNextIdSequential() {
        // No records yet → next ID is CB001
        assertEquals("CB001", repository.generateNextId());

        repository.save(makeBirthday("U001", LocalDate.now().plusDays(5)));
        // After CB001 → next is CB002
        assertEquals("CB002", repository.generateNextId());
    }

    // ── findByCustomerId ──────────────────────────────────────────────────────

    @Test
    @DisplayName("findByCustomerId() returns only bookings for that customer")
    void findByCustomerId() {
        repository.save(makeBirthday("U001", LocalDate.now().plusDays(5)));
        repository.save(makeBirthday("U002", LocalDate.now().plusDays(7)));
        repository.save(makeBirthday("U001", LocalDate.now().plusDays(9)));

        List<CakeBooking> u1Bookings = repository.findByCustomerId("U001");
        assertEquals(2, u1Bookings.size(), "U001 should have 2 bookings");

        List<CakeBooking> u2Bookings = repository.findByCustomerId("U002");
        assertEquals(1, u2Bookings.size(), "U002 should have 1 booking");
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update() changes fields and persists them")
    void updateBooking() {
        CakeBooking booking = makeBirthday("U001", LocalDate.now().plusDays(10));
        repository.save(booking);

        // Change status and delivery date, then update
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setDeliveryDate(LocalDate.now().plusDays(20));
        repository.update(booking);

        CakeBooking updated = repository.findById(booking.getBookingId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, updated.getStatus(), "Status should be CONFIRMED after update");
        assertEquals(LocalDate.now().plusDays(20), updated.getDeliveryDate(), "Delivery date should be updated");
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete() removes the booking from the file")
    void deleteBooking() {
        CakeBooking booking = makeBirthday("U001", LocalDate.now().plusDays(5));
        repository.save(booking);
        String id = booking.getBookingId();

        repository.delete(id);

        assertTrue(repository.findById(id).isEmpty(), "Booking should be gone after delete");
        assertEquals(0, repository.findAll().size(), "No bookings should remain");
    }

    // ── Corrupt line skipping ─────────────────────────────────────────────────

    @Test
    @DisplayName("Corrupt lines in the file are skipped, valid ones are still read")
    void corruptLinesAreSkipped() throws IOException {
        // Write one valid record and one corrupt line manually
        String validLine = "CB001|U001|BIRTHDAY|Chocolate|2.0|2|Hi|none|25|25|"
                + LocalDate.now().plusDays(10) + "|0771234567|PENDING||"
                + LocalDateTime.now();
        String corruptLine = "THIS_IS_NOT_VALID";

        Files.writeString(tempDir.resolve("cake-bookings.txt"),
                "# header comment\n" + validLine + "\n" + corruptLine + "\n",
                StandardCharsets.UTF_8);

        List<CakeBooking> all = repository.findAll();
        // Only the valid line should be parsed; the corrupt line should be silently skipped
        assertEquals(1, all.size(), "Only 1 valid booking expected; corrupt line should be skipped");
        assertEquals("CB001", all.get(0).getBookingId());
    }

    // ── All four cake subclasses round-trip ───────────────────────────────────

    @Test
    @DisplayName("All four cake types serialise and deserialise correctly")
    void allCakeTypesRoundTrip() {
        LocalDate future = LocalDate.now().plusDays(14);

        // Birthday
        BirthdayCake bday = new BirthdayCake("Choc", 2.0, 2, "HB", "", 25, 25);
        repository.save(new CakeBooking("U001", bday, future, "0771111111"));

        // Wedding
        WeddingCake wed = new WeddingCake("Van", 6.0, 4, "Love", "", true, "Hilton");
        repository.save(new CakeBooking("U002", wed, future, "0772222222"));

        // Anniversary
        AnniversaryCake ann = new AnniversaryCake("Straw", 3.0, 2, "25!", "", 25, "Forever");
        repository.save(new CakeBooking("U003", ann, future, "0773333333"));

        // General
        GeneralCustomCake gen = new GeneralCustomCake("Lemon", 1.5, 1, "Congrats", "", "Graduation");
        repository.save(new CakeBooking("U004", gen, future, "0774444444"));

        List<CakeBooking> all = repository.findAll();
        assertEquals(4, all.size());
        assertEquals("Birthday Cake",       all.get(0).getCake().getCakeType());
        assertEquals("Wedding Cake",        all.get(1).getCake().getCakeType());
        assertEquals("Anniversary Cake",    all.get(2).getCake().getCakeType());
        assertEquals("General Custom Cake", all.get(3).getCake().getCakeType());
    }

    // ── Pipe characters in free-text are escaped ──────────────────────────────

    @Test
    @DisplayName("Pipe characters in free-text fields are escaped and round-trip correctly")
    void pipeEscapingRoundTrip() {
        // Message contains a | which must not break the line format
        BirthdayCake cake = new BirthdayCake("Choc", 1.0, 1,
                "Love|Peace|Joy", "No|nuts", 20, 0);
        CakeBooking booking = new CakeBooking("U001", cake, LocalDate.now().plusDays(5), "0771234567");
        repository.save(booking);

        CakeBooking loaded = repository.findById(booking.getBookingId()).orElseThrow();
        // Pipe characters should be preserved after round-trip
        assertEquals("Love|Peace|Joy", loaded.getCake().getMessageOnCake());
        assertEquals("No|nuts",        loaded.getCake().getSpecialInstructions());
    }
}

