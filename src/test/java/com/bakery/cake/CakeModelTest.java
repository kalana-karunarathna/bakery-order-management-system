package com.bakery.cake;

import com.bakery.cake.exception.InvalidBookingException;
import com.bakery.cake.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OOP: Polymorphism – calculatePrice() per subclass,
 * and setter validation (Encapsulation).
 *
 * <p>These tests run without Spring context (plain JUnit), so they're fast.
 */
class CakeModelTest {

    // ── BirthdayCake price ────────────────────────────────────────────────────

    @Test
    @DisplayName("BirthdayCake price = (weight × 1500) + (extra tiers × 800) + (candles × 200)")
    void birthdayCakePriceCalculation() {
        // 2 kg × 1500 = 3000, 1 extra tier × 800 = 800, 10 candles × 200 = 2000  → 5800
        BirthdayCake cake = new BirthdayCake("Chocolate", 2.0, 2,
                "Happy Birthday", "", 25, 10);
        BigDecimal expected = new BigDecimal("5800.00");
        assertEquals(0, expected.compareTo(cake.calculatePrice()),
                "Birthday cake price should be 5800.00");
    }

    // ── WeddingCake price ─────────────────────────────────────────────────────

    @Test
    @DisplayName("WeddingCake price = basePrice × 1.5 + 2000 when floralDecoration=true")
    void weddingCakePriceWithFloral() {
        // 3 kg × 1500 = 4500, 1 extra tier × 800 = 800 → base = 5300 × 1.5 = 7950 + 2000 = 9950
        WeddingCake cake = new WeddingCake("Vanilla", 3.0, 2,
                "Together forever", "", true, "Hilton Colombo");
        BigDecimal expected = new BigDecimal("9950.00");
        assertEquals(0, expected.compareTo(cake.calculatePrice()),
                "Wedding cake with floral should be 9950.00");
    }

    @Test
    @DisplayName("WeddingCake price without floral = basePrice × 1.5")
    void weddingCakePriceWithoutFloral() {
        // 3 kg × 1500 = 4500, 1 extra tier × 800 = 800 → base = 5300 × 1.5 = 7950
        WeddingCake cake = new WeddingCake("Vanilla", 3.0, 2,
                "Together forever", "", false, "Hilton Colombo");
        BigDecimal expected = new BigDecimal("7950.00");
        assertEquals(0, expected.compareTo(cake.calculatePrice()),
                "Wedding cake without floral should be 7950.00");
    }

    // ── AnniversaryCake price ─────────────────────────────────────────────────

    @Test
    @DisplayName("AnniversaryCake price = basePrice × 1.2 + (decades × 500)")
    void anniversaryCakePriceCalculation() {
        // 2 kg × 1500 = 3000, 0 extra tiers → base = 3000 × 1.2 = 3600
        // 25 years = 2 decades → + 1000 → 4600
        AnniversaryCake cake = new AnniversaryCake("Strawberry", 2.0, 1,
                "25 Years!", "", 25, "Forever yours");
        BigDecimal expected = new BigDecimal("4600.00");
        assertEquals(0, expected.compareTo(cake.calculatePrice()),
                "Anniversary cake price (25 years) should be 4600.00");
    }

    // ── GeneralCustomCake price ───────────────────────────────────────────────

    @Test
    @DisplayName("GeneralCustomCake price = basePrice (no multiplier)")
    void generalCakePriceCalculation() {
        // 1.5 kg × 1500 = 2250, 0 extra tiers → 2250
        GeneralCustomCake cake = new GeneralCustomCake("Lemon", 1.5, 1,
                "Congrats!", "", "Graduation Party");
        BigDecimal expected = new BigDecimal("2250.00");
        assertEquals(0, expected.compareTo(cake.calculatePrice()),
                "General cake price should be 2250.00");
    }

    // ── Setter validation (Encapsulation) ─────────────────────────────────────

    @Test
    @DisplayName("CustomCake rejects empty flavour")
    void emptyFlavourThrowsException() {
        assertThrows(InvalidBookingException.class, () ->
                new BirthdayCake("", 1.0, 1, "", "", 25, 0),
                "Empty flavour should throw InvalidBookingException");
    }

    @Test
    @DisplayName("CustomCake rejects zero weight")
    void zeroWeightThrowsException() {
        assertThrows(InvalidBookingException.class, () ->
                new BirthdayCake("Chocolate", 0.0, 1, "", "", 25, 0),
                "Zero weight should throw InvalidBookingException");
    }

    @Test
    @DisplayName("CustomCake rejects negative weight")
    void negativeWeightThrowsException() {
        assertThrows(InvalidBookingException.class, () ->
                new GeneralCustomCake("Vanilla", -1.0, 1, "", "", "Party"),
                "Negative weight should throw InvalidBookingException");
    }

    @Test
    @DisplayName("CustomCake rejects zero tiers")
    void zeroTiersThrowsException() {
        assertThrows(InvalidBookingException.class, () ->
                new BirthdayCake("Chocolate", 1.0, 0, "", "", 25, 0),
                "Zero tiers should throw InvalidBookingException");
    }

    @Test
    @DisplayName("BirthdayCake rejects age < 1")
    void birthdayAgeZeroThrowsException() {
        assertThrows(InvalidBookingException.class, () ->
                new BirthdayCake("Chocolate", 1.0, 1, "", "", 0, 0),
                "Age 0 should throw InvalidBookingException");
    }

    @Test
    @DisplayName("WeddingCake rejects blank venue name")
    void weddingCakeBlankVenueThrowsException() {
        assertThrows(InvalidBookingException.class, () ->
                new WeddingCake("Vanilla", 2.0, 2, "", "", false, ""),
                "Blank venue should throw InvalidBookingException");
    }

    @Test
    @DisplayName("AnniversaryCake rejects years < 1")
    void anniversaryZeroYearsThrowsException() {
        assertThrows(InvalidBookingException.class, () ->
                new AnniversaryCake("Chocolate", 2.0, 1, "", "", 0, ""),
                "Zero years should throw InvalidBookingException");
    }

    @Test
    @DisplayName("GeneralCustomCake rejects blank occasion description")
    void generalCakeBlankOccasionThrowsException() {
        assertThrows(InvalidBookingException.class, () ->
                new GeneralCustomCake("Vanilla", 1.0, 1, "", "", ""),
                "Blank occasion should throw InvalidBookingException");
    }

    // ── getCakeType() polymorphism ────────────────────────────────────────────

    @Test
    @DisplayName("Each subclass returns the correct type name via getCakeType() – polymorphism")
    void polymorphicGetCakeType() {
        // Store each in a CustomCake reference → runtime polymorphism
        CustomCake birthday    = new BirthdayCake("Choc", 1.0, 1, "", "", 10, 10);
        CustomCake wedding     = new WeddingCake("Van", 2.0, 2, "", "", false, "Hotel");
        CustomCake anniversary = new AnniversaryCake("Straw", 1.0, 1, "", "", 10, "");
        CustomCake general     = new GeneralCustomCake("Lemon", 1.0, 1, "", "", "Party");

        assertEquals("Birthday Cake",     birthday.getCakeType());
        assertEquals("Wedding Cake",      wedding.getCakeType());
        assertEquals("Anniversary Cake",  anniversary.getCakeType());
        assertEquals("General Custom Cake", general.getCakeType());
    }

    // ── Admin Price Override Tests ───────────────────────────────────────────

    @Test
    @DisplayName("CakeBooking getPrice returns calculated price by default and overridden price when set by admin")
    void cakeBookingPriceOverride() {
        CustomCake birthday = new BirthdayCake("Choc", 1.0, 1, "", "", 10, 10);
        CakeBooking booking = new CakeBooking("U001", birthday, java.time.LocalDate.now().plusDays(5), "0771234567");

        assertEquals(0, birthday.calculatePrice().compareTo(booking.getPrice()),
                "Default getPrice should match calculated price");

        // Admin overrides price to Rs. 2200.00
        booking.setCustomPrice(new BigDecimal("2200.00"));
        assertEquals(0, new BigDecimal("2200.00").compareTo(booking.getPrice()),
                "getPrice should return the admin custom price when set");
    }
}

