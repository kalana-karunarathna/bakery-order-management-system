package com.bakery.cake.model;

import com.bakery.cake.exception.InvalidBookingException;
import java.math.BigDecimal;

/**
 * OOP: Abstraction – Abstract base class representing a general custom cake.
 * Defines shared cake properties and declares abstract methods that subclasses must implement.
 */
public abstract class CustomCake {

    // Base pricing constants
    protected static final BigDecimal BASE_PRICE_PER_KG = new BigDecimal("1500.00");
    protected static final BigDecimal PRICE_PER_EXTRA_TIER = new BigDecimal("800.00");

    // OOP: Encapsulation – all fields are private
    private String flavour;
    private double weightKg;
    private int tiers;
    private String messageOnCake;
    private String specialInstructions;

    // OOP: Inheritance – base constructor called by subclasses
    protected CustomCake(String flavour, double weightKg, int tiers,
                         String messageOnCake, String specialInstructions) {
        setFlavour(flavour);
        setWeightKg(weightKg);
        setTiers(tiers);
        this.messageOnCake = messageOnCake != null ? messageOnCake.trim() : "";
        this.specialInstructions = specialInstructions != null ? specialInstructions.trim() : "";
    }

    // OOP: Abstraction & Polymorphism – each subclass calculates price differently
    public abstract BigDecimal calculatePrice();

    // OOP: Abstraction & Polymorphism – returns human-readable cake type
    public abstract String getCakeType();

    // OOP: Polymorphism – extra details overridden by subclasses
    public String getExtraDetails() {
        return "";
    }

    // Calculates base price: (weight * 1500) + ((tiers - 1) * 800)
    protected BigDecimal basePrice() {
        BigDecimal weightCost = BASE_PRICE_PER_KG.multiply(BigDecimal.valueOf(weightKg));
        int extraTiers = tiers > 1 ? tiers - 1 : 0;
        BigDecimal tierCost = PRICE_PER_EXTRA_TIER.multiply(BigDecimal.valueOf(extraTiers));
        return weightCost.add(tierCost);
    }

    // OOP: Encapsulation – validated setters
    public void setFlavour(String flavour) {
        if (flavour == null || flavour.trim().isEmpty()) {
            throw new InvalidBookingException("flavour", "Flavour must not be empty.");
        }
        this.flavour = flavour.trim();
    }

    public void setWeightKg(double weightKg) {
        if (weightKg <= 0) {
            throw new InvalidBookingException("weightKg", "Weight must be greater than 0 kg.");
        }
        this.weightKg = weightKg;
    }

    public void setTiers(int tiers) {
        if (tiers < 1) {
            throw new InvalidBookingException("tiers", "Number of tiers must be at least 1.");
        }
        this.tiers = tiers;
    }

    public void setMessageOnCake(String messageOnCake) {
        this.messageOnCake = messageOnCake != null ? messageOnCake.trim() : "";
    }

    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions != null ? specialInstructions.trim() : "";
    }

    // Getters
    public String getFlavour() { return flavour; }
    public double getWeightKg() { return weightKg; }
    public int getTiers() { return tiers; }
    public String getMessageOnCake() { return messageOnCake; }
    public String getSpecialInstructions() { return specialInstructions; }

    @Override
    public String toString() {
        return getCakeType() + " (" + flavour + ", " + weightKg + "kg, " + tiers + " tiers)";
    }
}
