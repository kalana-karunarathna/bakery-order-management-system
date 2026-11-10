package com.bakery.user.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Bakery staff account with the ADMIN role. Created only through the development bootstrap
 * (see {@code AdminBootstrap}); there is no public admin registration.
 */
public class AdminUser extends User {

    public AdminUser(String id, String fullName, String email, String phone, String address,
                     String passwordHash, AccountStatus status, LocalDate createdDate) {
        super(id, fullName, email, phone, address, passwordHash, status, createdDate);
    }

    @Override
    public UserRole getRole() {
        return UserRole.ADMIN;
    }

    @Override
    public CustomerType getCustomerType() {
        return CustomerType.NONE;
    }

    @Override
    public BigDecimal getDiscountRate() {
        return BigDecimal.ZERO;
    }

    /** Staff accounts do not receive customer discounts. */
    @Override
    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        requireValidSubtotal(subtotal);
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
