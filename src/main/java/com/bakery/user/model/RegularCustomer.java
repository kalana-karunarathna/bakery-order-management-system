package com.bakery.user.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Standard customer account. Every new registration is a {@code RegularCustomer}. No discount. */
public class RegularCustomer extends User {

    public RegularCustomer(String id, String fullName, String email, String phone, String address,
                           String passwordHash, AccountStatus status, LocalDate createdDate) {
        super(id, fullName, email, phone, address, passwordHash, status, createdDate);
    }

    @Override
    public UserRole getRole() {
        return UserRole.CUSTOMER;
    }

    @Override
    public CustomerType getCustomerType() {
        return CustomerType.REGULAR;
    }

    @Override
    public BigDecimal getDiscountRate() {
        return BigDecimal.ZERO;
    }

    /** Regular customers pay the full price. */
    @Override
    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        requireValidSubtotal(subtotal);
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
