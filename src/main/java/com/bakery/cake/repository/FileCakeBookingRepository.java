package com.bakery.cake.repository;

import com.bakery.cake.exception.InvalidBookingException;
import com.bakery.cake.model.BookingStatus;
import com.bakery.cake.model.CakeBooking;
import com.bakery.cake.model.CakeBookingFactory;
import com.bakery.cake.model.CustomCake;
import com.bakery.cake.model.BirthdayCake;
import com.bakery.cake.model.WeddingCake;
import com.bakery.cake.model.AnniversaryCake;
import com.bakery.cake.model.GeneralCustomCake;
import com.bakery.common.TextFileStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * OOP: Inheritance (implements BookingRepository) + Information Hiding.
 * This class is the ONLY place that handles cake-bookings.txt file parsing.
 * The rest of the application never touches raw text lines.
 */
@Repository
@ConditionalOnProperty(name = "bakery.storage.type", havingValue = "file", matchIfMissing = true)
public class FileCakeBookingRepository implements BookingRepository {

    private static final String FILE_NAME = "cake-bookings.txt";
    private static final int TOTAL_FIELDS = 15;

    // OOP: Constructor Injection
    private final TextFileStorage storage;

    public FileCakeBookingRepository(TextFileStorage storage) {
        this.storage = storage;
    }

    // OOP: Abstraction – reads all records from file, skipping comments and corrupt lines
    @Override
    public List<CakeBooking> findAll() {
        List<CakeBooking> bookings = new ArrayList<>();
        List<String> lines = readRawLines();

        for (String line : lines) {
            if (line.trim().isEmpty() || line.startsWith("#")) {
                continue; // Skip comment lines and blank lines
            }
            try {
                CakeBooking booking = deserialise(line);
                bookings.add(booking);
            } catch (Exception e) {
                // Skip corrupt lines safely without crashing the system
                System.err.println("Skipping invalid line: " + line + " | " + e.getMessage());
            }
        }
        return bookings;
    }

    // OOP: Abstraction – finds a booking by ID using a simple loop
    @Override
    public Optional<CakeBooking> findById(String bookingId) {
        for (CakeBooking b : findAll()) {
            if (b.getBookingId().equals(bookingId)) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    // OOP: Abstraction – filters bookings by customer ID
    @Override
    public List<CakeBooking> findByCustomerId(String customerId) {
        List<CakeBooking> list = new ArrayList<>();
        for (CakeBooking b : findAll()) {
            if (b.getCustomerId().equals(customerId)) {
                list.add(b);
            }
        }
        return list;
    }

    // OOP: Abstraction – saves new booking with an auto-generated sequential ID
    @Override
    public void save(CakeBooking booking) {
        String newId = generateNextId();
        booking.setBookingId(newId);

        List<String> lines = readRawLines();
        lines.add(serialise(booking));
        writeLines(lines);
    }

    // OOP: Abstraction – updates an existing booking by matching bookingId
    @Override
    public void update(CakeBooking booking) {
        List<String> lines = readRawLines();
        List<String> updated = new ArrayList<>();

        for (String line : lines) {
            if (line.trim().isEmpty() || line.startsWith("#")) {
                updated.add(line);
                continue;
            }
            String[] parts = line.split("\\|", 2);
            if (parts.length > 0 && parts[0].equals(booking.getBookingId())) {
                updated.add(serialise(booking)); // Replace with updated line
            } else {
                updated.add(line);
            }
        }
        writeLines(updated);
    }

    // OOP: Abstraction – removes booking by bookingId
    @Override
    public void delete(String bookingId) {
        List<String> lines = readRawLines();
        List<String> remaining = new ArrayList<>();

        for (String line : lines) {
            if (line.trim().isEmpty() || line.startsWith("#")) {
                remaining.add(line);
                continue;
            }
            String[] parts = line.split("\\|", 2);
            if (parts.length > 0 && parts[0].equals(bookingId)) {
                continue; // Skip this record to delete it
            }
            remaining.add(line);
        }
        writeLines(remaining);
    }

    // Generates next ID: CB001, CB002, CB003...
    @Override
    public String generateNextId() {
        int maxNumber = 0;
        for (CakeBooking b : findAll()) {
            String id = b.getBookingId();
            if (id != null && id.startsWith("CB")) {
                try {
                    int num = Integer.parseInt(id.substring(2));
                    if (num > maxNumber) {
                        maxNumber = num;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return String.format("CB%03d", maxNumber + 1);
    }

    // ── OOP: Information Hiding – private serialization helpers ──────────────

    // Converts CakeBooking into a pipe-delimited text line
    private String serialise(CakeBooking b) {
        CustomCake cake = b.getCake();
        String[] extras = extractExtras(cake);
        String customPriceStr = b.getCustomPrice() != null ? b.getCustomPrice().toString() : "";

        return String.join("|",
                b.getBookingId(),
                b.getCustomerId(),
                cakeTypeToken(cake),
                escape(cake.getFlavour()),
                String.valueOf(cake.getWeightKg()),
                String.valueOf(cake.getTiers()),
                escape(cake.getMessageOnCake()),
                escape(cake.getSpecialInstructions()),
                escape(extras[0]),
                escape(extras[1]),
                b.getDeliveryDate().toString(),
                b.getContactPhone(),
                b.getStatus().name(),
                b.getImageFileName() != null ? b.getImageFileName() : "",
                b.getCreatedAt().toString(),
                customPriceStr
        );
    }

    // Reconstructs CakeBooking from a pipe-delimited text line
    private CakeBooking deserialise(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < TOTAL_FIELDS) {
            throw new InvalidBookingException("Invalid line format: " + line);
        }

        String bookingId    = parts[0];
        String customerId   = parts[1];
        String cakeType     = parts[2];
        String flavour      = unescape(parts[3]);
        String weightKg     = parts[4];
        String tiers        = parts[5];
        String message      = unescape(parts[6]);
        String special      = unescape(parts[7]);
        String extra1       = unescape(parts[8]);
        String extra2       = unescape(parts[9]);
        LocalDate delDate   = LocalDate.parse(parts[10]);
        String phone        = parts[11];
        BookingStatus status = BookingStatus.valueOf(parts[12]);
        String imageFile    = parts[13].isEmpty() ? null : parts[13];
        LocalDateTime created = LocalDateTime.parse(parts[14]);

        java.math.BigDecimal customPrice = null;
        if (parts.length > 15 && !parts[15].isBlank()) {
            try {
                customPrice = new java.math.BigDecimal(parts[15].trim());
            } catch (Exception ignored) {
            }
        }

        CustomCake cake = CakeBookingFactory.createFromFileLine(
                cakeType, flavour, weightKg, tiers, message, special, extra1, extra2);

        return new CakeBooking(bookingId, customerId, cake, delDate, phone, status, imageFile, created, customPrice);
    }

    // OOP: Polymorphism – determines type token from subclass
    private String cakeTypeToken(CustomCake cake) {
        if (cake instanceof BirthdayCake) return CakeBookingFactory.TYPE_BIRTHDAY;
        if (cake instanceof WeddingCake) return CakeBookingFactory.TYPE_WEDDING;
        if (cake instanceof AnniversaryCake) return CakeBookingFactory.TYPE_ANNIVERSARY;
        return CakeBookingFactory.TYPE_GENERAL;
    }

    // Extracts extra subclass fields
    private String[] extractExtras(CustomCake cake) {
        if (cake instanceof BirthdayCake bc) {
            return new String[]{ String.valueOf(bc.getAge()), String.valueOf(bc.getCandles()) };
        } else if (cake instanceof WeddingCake wc) {
            return new String[]{ String.valueOf(wc.isFloralDecoration()), wc.getVenueName() };
        } else if (cake instanceof AnniversaryCake ac) {
            return new String[]{ String.valueOf(ac.getYearsOfAnniversary()), ac.getDedicationQuote() };
        } else if (cake instanceof GeneralCustomCake gc) {
            return new String[]{ gc.getOccasionDescription(), "" };
        }
        return new String[]{ "", "" };
    }

    // Escapes pipe and newline characters in user free-text
    private String escape(String text) {
        if (text == null) return "";
        return text.replace("|", "\\pipe").replace("\n", "\\n").replace("\r", "");
    }

    private String unescape(String text) {
        if (text == null) return "";
        return text.replace("\\pipe", "|").replace("\\n", "\n");
    }

    private List<String> readRawLines() {
        try {
            return new ArrayList<>(storage.readLines(FILE_NAME));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void writeLines(List<String> lines) {
        try {
            storage.writeLines(FILE_NAME, lines);
        } catch (IOException e) {
            throw new RuntimeException("Error writing to " + FILE_NAME, e);
        }
    }
}
