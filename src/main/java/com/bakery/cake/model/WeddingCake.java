package com.bakery.cake.model;

import com.bakery.cake.exception.InvalidBookingException;
import java.math.BigDecimal;

/**
 * OOP: Inheritance – WeddingCake IS-A CustomCake.
 * Adds floral decorations and venue name.
 */
public class WeddingCake extends CustomCake {

    // OOP: Encapsulation – private fields
    private boolean floralDecoration;
    private String venueName;

    // OOP: Inheritance – constructor calls super(...)
    public WeddingCake(String flavour, double weightKg, int tiers,
                       String messageOnCake, String specialInstructions,
                       boolean floralDecoration, String venueName) {
        super(flavour, weightKg, tiers, messageOnCake, specialInstructions);
        this.floralDecoration = floralDecoration;
        setVenueName(venueName);
    }

    // OOP: Polymorphism (Method Overriding) – base * 1.5 + Rs. 2000 for floral
    @Override
    public BigDecimal calculatePrice() {
        BigDecimal total = basePrice().multiply(new BigDecimal("1.5"));
        if (floralDecoration) {
            total = total.add(new BigDecimal("2000.00"));
        }
        return total;
    }

    // OOP: Polymorphism – returns type name
    @Override
    public String getCakeType() {
        return "Wedding Cake";
    }

    // OOP: Polymorphism – returns extra details
    @Override
    public String getExtraDetails() {
        return "Venue: " + venueName + " | Floral: " + (floralDecoration ? "Yes" : "No");
    }

    // OOP: Encapsulation – validation in setter
    public void setVenueName(String venueName) {
        if (venueName == null || venueName.trim().isEmpty()) {
            throw new InvalidBookingException("venueName", "Venue name must not be empty.");
        }
        this.venueName = venueName.trim();
    }

    public void setFloralDecoration(boolean floralDecoration) {
        this.floralDecoration = floralDecoration;
    }

    public boolean isFloralDecoration() { return floralDecoration; }
    public String getVenueName() { return venueName; }
}

// Wedding cake pricing: base price * 1.5 + floral decoration fee
