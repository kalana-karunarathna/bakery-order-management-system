package com.bakery.cake.model;

import com.bakery.cake.exception.InvalidBookingException;
import java.math.BigDecimal;

/**
 * OOP: Inheritance – AnniversaryCake IS-A CustomCake.
 * Adds celebration years and an optional dedication quote.
 */
public class AnniversaryCake extends CustomCake {

    // OOP: Encapsulation – private fields
    private int yearsOfAnniversary;
    private String dedicationQuote;

    // OOP: Inheritance – constructor calls super(...)
    public AnniversaryCake(String flavour, double weightKg, int tiers,
                           String messageOnCake, String specialInstructions,
                           int yearsOfAnniversary, String dedicationQuote) {
        super(flavour, weightKg, tiers, messageOnCake, specialInstructions);
        setYearsOfAnniversary(yearsOfAnniversary);
        this.dedicationQuote = dedicationQuote != null ? dedicationQuote.trim() : "";
    }

    // OOP: Polymorphism (Method Overriding) – base * 1.2 + Rs. 500 per decade
    @Override
    public BigDecimal calculatePrice() {
        BigDecimal total = basePrice().multiply(new BigDecimal("1.2"));
        int decades = yearsOfAnniversary / 10;
        BigDecimal decadeBonus = new BigDecimal("500.00").multiply(BigDecimal.valueOf(decades));
        return total.add(decadeBonus);
    }

    @Override
    public String getCakeType() {
        return "Anniversary Cake";
    }

    @Override
    public String getExtraDetails() {
        String quote = dedicationQuote.isEmpty() ? "None" : dedicationQuote;
        return yearsOfAnniversary + " Years | Quote: " + quote;
    }

    // OOP: Encapsulation – validation in setter
    public void setYearsOfAnniversary(int years) {
        if (years < 1) {
            throw new InvalidBookingException("yearsOfAnniversary", "Years of anniversary must be at least 1.");
        }
        this.yearsOfAnniversary = years;
    }

    public void setDedicationQuote(String dedicationQuote) {
        this.dedicationQuote = dedicationQuote != null ? dedicationQuote.trim() : "";
    }

    public int getYearsOfAnniversary() { return yearsOfAnniversary; }
    public String getDedicationQuote() { return dedicationQuote; }
}

// Anniversary cake pricing: base price * 1.2 + Rs. 500 per decade of marriage
