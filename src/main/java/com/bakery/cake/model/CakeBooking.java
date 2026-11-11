package com.bakery.cake.model;

import com.bakery.cake.exception.InvalidBookingException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * OOP: Composition & Encapsulation.
 * CakeBooking HAS-A CustomCake (Composition), along with customer ID,
 * delivery date, contact phone, status, and design image filename.
 */
public class CakeBooking {

    // OOP: Encapsulation – private fields
    private String bookingId;
    private String customerId;
    private CustomCake cake;          // OOP: Composition (HAS-A relationship)
    private LocalDate deliveryDate;
    private String contactPhone;
    private BookingStatus status;
    private String imageFileName;
    private LocalDateTime createdAt;
    private java.math.BigDecimal customPrice; // Admin overridden/finalized price

    // Full constructor with customPrice (for loading records or admin pricing)
    public CakeBooking(String bookingId,
                       String customerId,
                       CustomCake cake,
                       LocalDate deliveryDate,
                       String contactPhone,
                       BookingStatus status,
                       String imageFileName,
                       LocalDateTime createdAt,
                       java.math.BigDecimal customPrice) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        setCake(cake);
        setDeliveryDate(deliveryDate);
        setContactPhone(contactPhone);
        this.status = status != null ? status : BookingStatus.PENDING;
        this.imageFileName = imageFileName;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        setCustomPrice(customPrice);
    }

    // OOP: Polymorphism (Constructor Overloading) – for backwards compatibility (8 params)
    public CakeBooking(String bookingId,
                       String customerId,
                       CustomCake cake,
                       LocalDate deliveryDate,
                       String contactPhone,
                       BookingStatus status,
                       String imageFileName,
                       LocalDateTime createdAt) {
        this(bookingId, customerId, cake, deliveryDate, contactPhone, status, imageFileName, createdAt, null);
    }

    // OOP: Polymorphism (Constructor Overloading) – for creating a new booking
    public CakeBooking(String customerId,
                       CustomCake cake,
                       LocalDate deliveryDate,
                       String contactPhone) {
        this("PENDING_ID", customerId, cake, deliveryDate, contactPhone,
                BookingStatus.PENDING, null, LocalDateTime.now(), null);
    }

    // Convenience method: check if booking is in PENDING status
    public boolean isPending() {
        return BookingStatus.PENDING.equals(status);
    }

    // OOP: Encapsulation – validated setters
    public void setCake(CustomCake cake) {
        if (cake == null) {
            throw new InvalidBookingException("cake", "Cake details must not be null.");
        }
        this.cake = cake;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        if (deliveryDate == null) {
            throw new InvalidBookingException("deliveryDate", "Delivery date must not be null.");
        }
        this.deliveryDate = deliveryDate;
    }

    public void setContactPhone(String contactPhone) {
        if (contactPhone == null || contactPhone.trim().isEmpty()) {
            throw new InvalidBookingException("contactPhone", "Contact phone must not be empty.");
        }
        String cleaned = contactPhone.trim();
        // Validates 07XXXXXXXX or international +XX... format
        if (!cleaned.matches("^(07\\d{8}|\\+\\d{7,15})$")) {
            throw new InvalidBookingException("contactPhone", "Invalid phone number format.");
        }
        this.contactPhone = cleaned;
    }

    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    // OOP: Encapsulation – Status is read-only for customers; only internal logic can modify it
    public void setStatus(BookingStatus status) {
        if (status == null) {
            throw new InvalidBookingException("status", "Status must not be null.");
        }
        this.status = status;
    }

    public void setImageFileName(String imageFileName) { this.imageFileName = imageFileName; }

    public void setCustomPrice(java.math.BigDecimal customPrice) {
        if (customPrice != null && customPrice.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new InvalidBookingException("price", "Price cannot be negative.");
        }
        this.customPrice = customPrice;
    }

    /**
     * OOP: Encapsulation & Polymorphism.
     * Returns the finalized/custom price set by bakery admin if available,
     * otherwise falls back to dynamic price calculation via {@link CustomCake#calculatePrice()}.
     */
    public java.math.BigDecimal getPrice() {
        if (customPrice != null) {
            return customPrice;
        }
        return cake != null ? cake.calculatePrice() : java.math.BigDecimal.ZERO;
    }

    public java.math.BigDecimal getCustomPrice() { return customPrice; }

    // Getters
    public String getBookingId() { return bookingId; }
    public String getCustomerId() { return customerId; }
    public CustomCake getCake() { return cake; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public String getContactPhone() { return contactPhone; }
    public BookingStatus getStatus() { return status; }
    public String getImageFileName() { return imageFileName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
