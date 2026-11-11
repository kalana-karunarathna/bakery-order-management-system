package com.bakery.cake.service;

import com.bakery.user.security.BakeryUserPrincipal;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * OOP: Inheritance (implements CurrentCustomerProvider) + Encapsulation.
 * Seamlessly integrates with Spring Security (User Management Component 01)
 * while maintaining backward compatibility with HTTP session attributes and demo fallback.
 */
@Component
public class SessionCurrentCustomerProvider implements CurrentCustomerProvider {

    private static final Logger log = LoggerFactory.getLogger(SessionCurrentCustomerProvider.class);

    private static final String SESSION_KEY = "loggedInUserId";
    private static final String DEMO_CUSTOMER_ID = "U001";

    @Override
    public String getCurrentCustomerId(HttpSession session) {
        // 1. Try to read from Spring Security authenticated principal
        BakeryUserPrincipal principal = getPrincipal();
        if (principal != null) {
            return principal.getId();
        }

        // 2. Try to read from HTTP session attribute
        if (session != null) {
            Object userId = session.getAttribute(SESSION_KEY);
            if (userId != null && !userId.toString().isBlank()) {
                return userId.toString();
            }
        }

        // 3. Fallback for offline testing or pre-login development
        log.debug("No authenticated user found. Using demo customer '{}'.", DEMO_CUSTOMER_ID);
        return DEMO_CUSTOMER_ID;
    }

    @Override
    public boolean isAdmin() {
        BakeryUserPrincipal principal = getPrincipal();
        if (principal != null) {
            return principal.isAdmin();
        }
        // Also check Spring Security granted authorities for ROLE_ADMIN
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }

    @Override
    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && auth.getPrincipal().equals("anonymousUser"))) {
            return true;
        }
        return false;
    }

    private BakeryUserPrincipal getPrincipal() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof BakeryUserPrincipal principal) {
                return principal;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
