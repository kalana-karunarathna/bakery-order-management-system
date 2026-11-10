package com.bakery.user.service;

import com.bakery.user.model.AccountStatus;
import com.bakery.user.model.CustomerType;
import com.bakery.user.model.UserRole;

/**
 * Identity of the signed-in user, for other modules (see {@link CurrentUserService}).
 * Contains no password data.
 */
public record AuthenticatedUser(
        String id,
        String email,
        String fullName,
        UserRole role,
        CustomerType customerType,
        AccountStatus status) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isCustomer() {
        return role == UserRole.CUSTOMER;
    }
}
