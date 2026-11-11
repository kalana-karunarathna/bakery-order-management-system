package com.bakery.payment.integration;

import com.bakery.payment.repository.PaymentRepository.StorageException;
import com.bakery.payment.service.PaymentIntegration.CustomerDirectory;
import com.bakery.user.model.UserRole;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.repository.UserStorageException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Customer lookup from User Management: a payment can only belong to an existing CUSTOMER account.
 * Deactivated customers still count, so an admin can settle or refund their existing payments;
 * deactivated users cannot sign in, so they cannot act on payments themselves.
 */
@Component
@Profile("!payment-demo")
public class UserCustomerDirectory implements CustomerDirectory {

    private final UserRepository users;

    public UserCustomerDirectory(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean exists(String customerId) {
        try {
            return users.findById(customerId).map(user -> user.getRole() == UserRole.CUSTOMER).orElse(false);
        } catch (UserStorageException e) {
            throw new StorageException("Account information is unavailable right now. No payment was recorded.", e);
        }
    }
}
