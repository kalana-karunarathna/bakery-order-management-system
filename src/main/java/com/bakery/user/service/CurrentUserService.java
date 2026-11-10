package com.bakery.user.service;

import com.bakery.user.repository.UserRepository;
import com.bakery.user.security.BakeryUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * <b>Integration contract for other modules.</b> Gives the signed-in user's identity, read fresh
 * from {@code users.txt} on every call (so a customer-type change or deactivation is seen at once).
 *
 * <pre>{@code
 * // e.g. in OrderController
 * AuthenticatedUser me = currentUserService.requireCurrentUser();
 * orderService.getOrdersByCustomerId(me.id());   // never trust a customerId request parameter
 * }</pre>
 */
@Service
public class CurrentUserService {

    private final UserRepository repository;

    public CurrentUserService(UserRepository repository) {
        this.repository = repository;
    }

    /** The signed-in, active user, or empty for anonymous visitors. */
    public Optional<AuthenticatedUser> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof BakeryUserPrincipal principal)) {
            return Optional.empty();
        }
        return repository.findById(principal.getId())
                .filter(u -> u.isActive())
                .map(u -> new AuthenticatedUser(u.getId(), u.getEmail(), u.getFullName(),
                        u.getRole(), u.getCustomerType(), u.getStatus()));
    }

    /** The signed-in user, or an {@link AccessDeniedException} (which Spring Security turns into sign-in / 403). */
    public AuthenticatedUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new AccessDeniedException("Sign in required."));
    }

    /** Convenience for ownership checks, e.g. {@code isCurrentUser(order.getCustomerId())}. */
    public boolean isCurrentUser(String userId) {
        return currentUser().map(u -> u.id().equals(userId)).orElse(false);
    }
}
