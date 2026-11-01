package com.bakery.cake.model;

import com.bakery.cake.exception.InvalidBookingException;
import java.math.BigDecimal;

/**
 * OOP: Inheritance – GeneralCustomCake IS-A CustomCake.
 * Covers other occasions like graduations, office parties, etc.
 */
public class GeneralCustomCake extends CustomCake {

    // OOP: Encapsulation – private field
    private String occasionDescription;

    // OOP: Inheritance – constructor calls super(...)
    public GeneralCustomCake(String flavour, double weightKg, int tiers,
                             String messageOnCake, String specialInstructions,
                             String occasionDescription) {
        super(flavour, weightKg, tiers, messageOnCake, specialInstructions);
        setOccasionDescription(occasionDescription);
    }

    // OOP: Polymorphism (Method Overriding) – base price with no multiplier
    @Override
    public BigDecimal calculatePrice() {
        return basePrice();
    }

    @Override
    public String getCakeType() {
        return "General Custom Cake";
    }

    @Override
    public String getExtraDetails() {
        return "Occasion: " + occasionDescription;
    }

    // OOP: Encapsulation – validation in setter
    public void setOccasionDescription(String occasionDescription) {
        if (occasionDescription == null || occasionDescription.trim().isEmpty()) {
            throw new InvalidBookingException("occasionDescription", "Occasion description must not be empty.");
        }
        this.occasionDescription = occasionDescription.trim();
    }

    public String getOccasionDescription() { return occasionDescription; }
}
