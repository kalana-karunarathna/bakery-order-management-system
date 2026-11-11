package com.bakery.cake.service;

import com.bakery.cake.exception.AccessDeniedBookingException;
import com.bakery.cake.exception.BookingNotFoundException;
import com.bakery.cake.exception.InvalidBookingException;
import com.bakery.cake.model.BookingStatus;
import com.bakery.cake.model.CakeBooking;
import com.bakery.cake.model.CakeBookingFactory;
import com.bakery.cake.model.CustomCake;
import com.bakery.cake.repository.BookingRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * OOP: Service layer – enforces all business rules.
 * Controllers delegate here and never implement business logic directly.
 */
@Service
public class CakeBookingService {

    // OOP: Constructor Injection
    private final BookingRepository repository;
    private final CurrentCustomerProvider customerProvider;
    private final Path uploadDir;

    public CakeBookingService(BookingRepository repository,
                              CurrentCustomerProvider customerProvider,
                              @Value("${bakery.cake.upload-dir:uploads/cake-designs}") String uploadDir) {
        this.repository = repository;
        this.customerProvider = customerProvider;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    // Returns all bookings belonging to the current customer
    public List<CakeBooking> getMyBookings(HttpSession session) {
        String customerId = customerProvider.getCurrentCustomerId(session);
        return repository.findByCustomerId(customerId);
    }

    // Finds a booking and checks ownership
    public CakeBooking getBookingForCurrentCustomer(String bookingId, HttpSession session) {
        CakeBooking booking = repository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        // Business Rule: Ownership check
        String customerId = customerProvider.getCurrentCustomerId(session);
        if (!booking.getCustomerId().equals(customerId)) {
            throw new AccessDeniedBookingException("Access denied: You do not own booking " + bookingId);
        }
        return booking;
    }

    // OOP: Polymorphism (Method Overloading) – create booking with image upload
    public CakeBooking createBooking(String customerId,
                                     String cakeType,
                                     String flavour,
                                     double weightKg,
                                     int tiers,
                                     String message,
                                     String special,
                                     String extra1,
                                     String extra2,
                                     LocalDate deliveryDate,
                                     String contactPhone,
                                     MultipartFile imageFile) {
        return createBooking(customerId, cakeType, flavour, weightKg, tiers,
                message, special, extra1, extra2, deliveryDate, contactPhone, null, imageFile);
    }

    // OOP: Polymorphism (Method Overloading) – create booking with custom price and image upload
    public CakeBooking createBooking(String customerId,
                                     String cakeType,
                                     String flavour,
                                     double weightKg,
                                     int tiers,
                                     String message,
                                     String special,
                                     String extra1,
                                     String extra2,
                                     LocalDate deliveryDate,
                                     String contactPhone,
                                     java.math.BigDecimal customPrice,
                                     MultipartFile imageFile) {
        // Business Rule 1: Delivery date must be at least 3 days ahead
        validateDeliveryDate(deliveryDate);

        // OOP: Polymorphism & Factory – create cake subclass polymorphically
        CustomCake cake = CakeBookingFactory.createFromForm(
                cakeType, flavour, weightKg, tiers, message, special, extra1, extra2);

        // OOP: Composition – CakeBooking HAS-A CustomCake
        CakeBooking booking = new CakeBooking(customerId, cake, deliveryDate, contactPhone);
        if (customPrice != null) {
            booking.setCustomPrice(customPrice);
        }

        // Handle image upload if provided
        if (imageFile != null && !imageFile.isEmpty()) {
            String savedFileName = saveImage(imageFile);
            booking.setImageFileName(savedFileName);
        }

        repository.save(booking);
        return booking;
    }

    // OOP: Polymorphism (Method Overloading) – create booking without image
    public CakeBooking createBooking(String customerId,
                                     String cakeType,
                                     String flavour,
                                     double weightKg,
                                     int tiers,
                                     String message,
                                     String special,
                                     String extra1,
                                     String extra2,
                                     LocalDate deliveryDate,
                                     String contactPhone) {
        return createBooking(customerId, cakeType, flavour, weightKg, tiers,
                message, special, extra1, extra2, deliveryDate, contactPhone, null);
    }

    // Update booking – allowed only if PENDING and owned by current customer.
    // Business Rule: Customers CANNOT change status (status is strictly read-only for customers).
    public void updateBooking(String bookingId,
                              HttpSession session,
                              String cakeType,
                              String flavour,
                              double weightKg,
                              int tiers,
                              String message,
                              String special,
                              String extra1,
                              String extra2,
                              LocalDate deliveryDate,
                              String contactPhone,
                              MultipartFile imageFile) {
        CakeBooking booking = getBookingForCurrentCustomer(bookingId, session);

        // Business Rule 2: Only PENDING bookings can be edited
        if (!booking.isPending()) {
            throw new InvalidBookingException("Only PENDING bookings can be edited.");
        }

        validateDeliveryDate(deliveryDate);

        CustomCake updatedCake = CakeBookingFactory.createFromForm(
                cakeType, flavour, weightKg, tiers, message, special, extra1, extra2);

        booking.setCake(updatedCake);
        booking.setDeliveryDate(deliveryDate);
        booking.setContactPhone(contactPhone);

        // Replace image if a new file is uploaded
        if (imageFile != null && !imageFile.isEmpty()) {
            deleteImageFile(booking.getImageFileName());
            String newFileName = saveImage(imageFile);
            booking.setImageFileName(newFileName);
        }

        repository.update(booking);
    }

    // Cancel (delete) booking – allowed only if PENDING and owned by current customer
    public void cancelBooking(String bookingId, HttpSession session) {
        CakeBooking booking = getBookingForCurrentCustomer(bookingId, session);

        // Business Rule: Only PENDING bookings can be cancelled
        if (!booking.isPending()) {
            throw new InvalidBookingException("Only PENDING bookings can be cancelled.");
        }

        // Delete associated design image file from storage
        deleteImageFile(booking.getImageFileName());
        repository.delete(bookingId);
    }

    // ── Admin / Staff Methods ────────────────────────────────────────────────

    // Returns ALL cake bookings across all customers for bakery staff/admin review
    public List<CakeBooking> getAllBookings() {
        return repository.findAll();
    }

    // Admin view of a single booking (no ownership check needed for staff)
    public CakeBooking getBookingByIdForAdmin(String bookingId) {
        return repository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    // Allows bakery staff/admin to update booking status (CONFIRMED, COMPLETED, CANCELLED)
    public void updateBookingStatus(String bookingId, BookingStatus newStatus) {
        CakeBooking booking = repository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (newStatus == null) {
            throw new InvalidBookingException("status", "Status cannot be null.");
        }

        booking.setStatus(newStatus);
        repository.update(booking);
    }

    // Admin hard-delete of any booking (e.g. spam or completed record cleanup)
    public void adminDeleteBooking(String bookingId) {
        CakeBooking booking = repository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        deleteImageFile(booking.getImageFileName());
        repository.delete(bookingId);
    }

    // Admin update of any booking details (Admin Full CRUD with Price Override)
    public CakeBooking adminUpdateBooking(String bookingId,
                                          String cakeType,
                                          String flavour,
                                          double weightKg,
                                          int tiers,
                                          String message,
                                          String special,
                                          String extra1,
                                          String extra2,
                                          LocalDate deliveryDate,
                                          String contactPhone,
                                          BookingStatus status,
                                          java.math.BigDecimal price,
                                          MultipartFile imageFile) {
        CakeBooking booking = repository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        CustomCake updatedCake = CakeBookingFactory.createFromForm(
                cakeType, flavour, weightKg, tiers, message, special, extra1, extra2);

        booking.setCake(updatedCake);
        if (deliveryDate != null) {
            booking.setDeliveryDate(deliveryDate);
        }
        booking.setContactPhone(contactPhone);
        if (status != null) {
            booking.setStatus(status);
        }
        // Set admin custom/overridden price if provided
        booking.setCustomPrice(price);

        if (imageFile != null && !imageFile.isEmpty()) {
            deleteImageFile(booking.getImageFileName());
            String newFileName = saveImage(imageFile);
            booking.setImageFileName(newFileName);
        }

        repository.update(booking);
        return booking;
    }

    // OOP: Method Overloading (Polymorphism) – backwards compatible without price parameter
    public CakeBooking adminUpdateBooking(String bookingId,
                                          String cakeType,
                                          String flavour,
                                          double weightKg,
                                          int tiers,
                                          String message,
                                          String special,
                                          String extra1,
                                          String extra2,
                                          LocalDate deliveryDate,
                                          String contactPhone,
                                          BookingStatus status,
                                          MultipartFile imageFile) {
        return adminUpdateBooking(bookingId, cakeType, flavour, weightKg, tiers,
                message, special, extra1, extra2,
                deliveryDate, contactPhone, status, null, imageFile);
    }

    public Path getUploadDir() {
        return uploadDir;
    }

    // --- Private Helper Methods (Encapsulation / Single Responsibility) ---

    private void validateDeliveryDate(LocalDate deliveryDate) {
        if (deliveryDate == null) {
            throw new InvalidBookingException("deliveryDate", "Delivery date is required.");
        }
        LocalDate minAllowed = LocalDate.now().plusDays(3);
        if (deliveryDate.isBefore(minAllowed)) {
            throw new InvalidBookingException("deliveryDate",
                    "Delivery date must be at least 3 days ahead (earliest: " + minAllowed + ").");
        }
    }

    private String saveImage(MultipartFile file) {
        // Size validation: max 5 MB
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new InvalidBookingException("imageFile", "Image size must not exceed 5 MB.");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase();
        }

        // Extension check
        if (!ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("png") && !ext.equals("webp")) {
            throw new InvalidBookingException("imageFile", "Only JPG, PNG, and WebP images are allowed.");
        }

        // Secure unique filename to prevent path traversal
        String savedFileName = UUID.randomUUID() + "." + (ext.equals("jpeg") ? "jpg" : ext);

        try {
            Files.createDirectories(uploadDir);
            File dest = uploadDir.resolve(savedFileName).toFile();
            file.transferTo(dest);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image file: " + e.getMessage(), e);
        }

        return savedFileName;
    }

    private void deleteImageFile(String fileName) {
        if (fileName != null && !fileName.trim().isEmpty()) {
            try {
                Files.deleteIfExists(uploadDir.resolve(fileName));
            } catch (IOException ignored) {
            }
        }
    }
}
