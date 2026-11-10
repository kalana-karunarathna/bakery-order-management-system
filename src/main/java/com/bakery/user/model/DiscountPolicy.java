package com.bakery.user.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Abstraction: the contract every account type uses to work out its discount.
 * Each subclass of {@link User} supplies its own implementation (polymorphism).
 *
 * <p>Integration note: Order Management does not call this yet. When it does, it should pass the
 * order subtotal and store the discount it applied, rather than recalculating old orders.</p>
 */
public interface DiscountPolicy {

    /** Discount rate as a fraction, e.g. {@code 0.05} for 5%. */
    BigDecimal getDiscountRate();

    /**
     * Discount amount for the given subtotal (in LKR), rounded to 2 decimal places.
     *
     * @throws IllegalArgumentException if the subtotal is null or negative
     */
    BigDecimal calculateDiscount(BigDecimal subtotal);

    /** Subtotal minus {@link #calculateDiscount(BigDecimal)}. */
    default BigDecimal applyDiscount(BigDecimal subtotal) {
        return subtotal.subtract(calculateDiscount(subtotal)).setScale(2, RoundingMode.HALF_UP);
    }
}
