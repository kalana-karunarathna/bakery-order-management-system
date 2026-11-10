package com.bakery.user.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Customer upgraded by an administrator to the loyalty programme.
 *
 * <p>Rule used here: a flat {@value #DISCOUNT_PERCENT}% discount on the order subtotal.
 * This is a demonstration value for the assignment; replace it if the team agrees on
 * different rules. It is not applied to any orders until Order Management integrates it.</p>
 */
public class LoyaltyCustomer extends User {

    public static final int DISCOUNT_PERCENT = 5;
    private static final BigDecimal RATE = BigDecimal.valueOf(DISCOUNT_PERCENT, 2); // 0.05

    public LoyaltyCustomer(String id, String fullName, String email, String phone, String address,
                           String passwordHash, AccountStatus status, LocalDate createdDate) {
        super(id, fullName, email, phone, address, passwordHash, status, createdDate);
    }

    @Override
    public UserRole getRole() {
        return UserRole.CUSTOMER;
    }

    @Override
    public CustomerType getCustomerType() {
        return CustomerType.LOYALTY;
    }

    @Override
    public BigDecimal getDiscountRate() {
        return RATE;
    }

    /** {@value #DISCOUNT_PERCENT}% of the subtotal, rounded half-up to 2 decimal places. */
    @Override
    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        return requireValidSubtotal(subtotal).multiply(RATE).setScale(2, RoundingMode.HALF_UP);
    }
}
