package com.bakery.user.service;

import com.bakery.user.model.AccountStatus;
import com.bakery.user.model.CustomerType;
import com.bakery.user.model.User;
import com.bakery.user.model.UserRole;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only account data for templates. Deliberately has no password hash field,
 * so templates cannot display it even by mistake.
 */
public record UserView(
        String id,
        String fullName,
        String email,
        String phone,
        String address,
        UserRole role,
        CustomerType customerType,
        AccountStatus status,
        LocalDate createdDate,
        BigDecimal discountRate,
        BigDecimal exampleSubtotal,
        BigDecimal exampleDiscount,
        boolean hasPassword) {

    /** Example order value used to show each account type's discount rule. */
    public static final BigDecimal EXAMPLE_SUBTOTAL = new BigDecimal("5000.00");

    static UserView of(User user) {
        return new UserView(
                user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getAddress(),
                user.getRole(), user.getCustomerType(), user.getStatus(), user.getCreatedDate(),
                user.getDiscountRate(),
                EXAMPLE_SUBTOTAL,
                user.calculateDiscount(EXAMPLE_SUBTOTAL), // polymorphic call
                user.hasUsablePassword());
    }

    public boolean admin() { return role == UserRole.ADMIN; }
    public boolean active() { return status == AccountStatus.ACTIVE; }
    public boolean deleted() { return status == AccountStatus.DELETED; }

    /** Whole-number percentage for display, e.g. 5. */
    public int discountPercent() {
        return discountRate.movePointRight(2).intValue();
    }

    /** One or two letters for the avatar circle. */
    public String initials() {
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }
}
