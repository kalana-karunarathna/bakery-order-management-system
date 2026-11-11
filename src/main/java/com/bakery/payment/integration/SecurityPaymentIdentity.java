package com.bakery.payment.integration;

import com.bakery.payment.service.PaymentIntegration.Actor;
import com.bakery.payment.service.PaymentIntegration.IdentityProvider;
import com.bakery.user.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Payment identity from Spring Security, through User Management's {@link CurrentUserService}: the
 * signed-in, active user (read fresh on every request). Request fields and session attributes are never
 * trusted for identity. ADMIN users act as administrators; everyone else as a customer.
 * (The {@code payment-demo} profile supplies its own fictional identities instead.)
 */
@Component
@Profile("!payment-demo")
public class SecurityPaymentIdentity implements IdentityProvider {

    private final CurrentUserService currentUserService;

    public SecurityPaymentIdentity(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    public Actor current(HttpSession ignored) {
        return currentUserService.currentUser()
                .map(user -> new Actor(user.id(), user.isAdmin()))
                .orElse(null);
    }
}
