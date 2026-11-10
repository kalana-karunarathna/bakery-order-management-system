package com.bakery.user.repository;

import com.bakery.user.model.AccountStatus;
import com.bakery.user.model.AdminUser;
import com.bakery.user.model.CustomerType;
import com.bakery.user.model.LoyaltyCustomer;
import com.bakery.user.model.RegularCustomer;
import com.bakery.user.model.User;
import com.bakery.user.model.UserRole;

import java.time.LocalDate;

/**
 * Rebuilds the correct {@link User} subclass from stored values. Shared by the TXT and MongoDB
 * repositories so the inheritance mapping (role + customer type → subclass) lives in one place.
 *
 * <p>The domain constructors validate every field, so a stored record with invalid data is rejected
 * here (an {@link IllegalArgumentException}) instead of producing an invalid account.</p>
 */
public final class UserFactory {

    private UserFactory() {
    }

    public static User create(String id, UserRole role, CustomerType type, String fullName, String email,
                              String phone, String address, String passwordHash, AccountStatus status,
                              LocalDate createdDate) {
        if (role == null || type == null) {
            throw new IllegalArgumentException("Stored account is missing its role or customer type.");
        }
        return switch (role) {
            case ADMIN -> {
                if (type != CustomerType.NONE) throw new IllegalArgumentException("Admin with a customer type");
                yield new AdminUser(id, fullName, email, phone, address, passwordHash, status, createdDate);
            }
            case CUSTOMER -> switch (type) {
                case REGULAR -> new RegularCustomer(id, fullName, email, phone, address, passwordHash, status, createdDate);
                case LOYALTY -> new LoyaltyCustomer(id, fullName, email, phone, address, passwordHash, status, createdDate);
                case NONE -> throw new IllegalArgumentException("Customer without a customer type");
            };
        };
    }

    /** Same as {@link #create} but keeps the subclass of an existing user while copying all fields. */
    public static User copy(User user) {
        return create(user.getId(), user.getRole(), user.getCustomerType(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.getAddress(), user.getPasswordHash(), user.getStatus(), user.getCreatedDate());
    }
}
