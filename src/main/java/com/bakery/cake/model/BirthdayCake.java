package com.bakery.cake.model;

import com.bakery.cake.exception.InvalidBookingException;
import java.math.BigDecimal;

/**
 * OOP: Inheritance – BirthdayCake IS-A CustomCake.
 * Adds age and candle count, and overrides calculatePrice().
 */
public class BirthdayCake extends CustomCake {

    // OOP: Encapsulation – private fields
    private int age;
    private int candles;

    // OOP: Inheritance – constructor calls super(...) for shared fields
    public BirthdayCake(String flavour, double weightKg, int tiers,
                        String messageOnCake, String specialInstructions,
                        int age, int candles) {
        super(flavour, weightKg, tiers, messageOnCake, specialInstructions);
        setAge(age);
        setCandles(candles);
    }

    // OOP: Polymorphism (Method Overriding) – birthday formula: base + (candles * 200)
    @Override
    public BigDecimal calculatePrice() {
        BigDecimal candleCost = new BigDecimal("200.00").multiply(BigDecimal.valueOf(candles));
        return basePrice().add(candleCost);
    }

    // OOP: Polymorphism – returns type name
    @Override
    public String getCakeType() {
        return "Birthday Cake";
    }

    // OOP: Polymorphism – returns birthday details
    @Override
    public String getExtraDetails() {
        return "Age: " + age + " | Candles: " + candles;
    }

    // OOP: Encapsulation – validated setters
    public void setAge(int age) {
        if (age < 1) {
            throw new InvalidBookingException("age", "Age must be at least 1.");
        }
        this.age = age;
    }

    public void setCandles(int candles) {
        if (candles < 0) {
            throw new InvalidBookingException("candles", "Candles cannot be negative.");
        }
        this.candles = candles;
    }

    public int getAge() { return age; }
    public int getCandles() { return candles; }
}
