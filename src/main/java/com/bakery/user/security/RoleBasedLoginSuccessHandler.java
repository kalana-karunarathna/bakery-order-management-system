package com.bakery.user.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Where to go after a successful sign-in.
 * <ul>
 *   <li>If the user was sent to the login page from a protected page (saved by Spring Security's
 *       request cache, which only stores this application's own GET requests), go back there,
 *       unless it is an admin page and the user is not an admin.</li>
 *   <li>Otherwise ADMIN goes to {@value #ADMIN_HOME} and CUSTOMER to {@value #CUSTOMER_HOME}.</li>
 * </ul>
 */
@Component
public class RoleBasedLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public static final String ADMIN_HOME = "/users/admin";
    public static final String CUSTOMER_HOME = "/profile";

    private final RequestCache requestCache = new HttpSessionRequestCache();

    public RoleBasedLoginSuccessHandler() {
        setRequestCache(requestCache);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null && !isAdmin(authentication) && isAdminPath(saved.getRedirectUrl(), request)) {
            // A customer who was bounced from an admin page should land on their account, not a 403.
            requestCache.removeRequest(request, response);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {
        return isAdmin(authentication) ? ADMIN_HOME : CUSTOMER_HOME;
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private static boolean isAdminPath(String url, HttpServletRequest request) {
        String path = URI.create(url).getPath();
        String adminPath = request.getContextPath() + ADMIN_HOME;
        return path != null && (path.equals(adminPath) || path.startsWith(adminPath + "/"));
    }
}
