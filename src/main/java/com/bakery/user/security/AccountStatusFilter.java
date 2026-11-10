package com.bakery.user.security;

import com.bakery.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ends the session of a signed-in user whose account was deactivated or deleted after they
 * signed in, so an admin's change takes effect on the user's next request.
 *
 * <p>Not a Spring bean on purpose: it is added only to the security filter chain.</p>
 */
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserRepository repository;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AccountStatusFilter(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/vendor/")
                || path.startsWith("/images/") || path.startsWith("/fonts/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof BakeryUserPrincipal principal) {
            boolean stillActive = repository.findById(principal.getId())
                    .map(u -> u.isActive() && u.hasUsablePassword())
                    .orElse(false);
            if (!stillActive) {
                logoutHandler.logout(request, response, auth);
                response.sendRedirect(request.getContextPath() + "/login?disabled");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
