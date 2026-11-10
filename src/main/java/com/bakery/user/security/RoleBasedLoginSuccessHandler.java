package com.bakery.user.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;
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
 *       but only if the signed-in user is allowed to open it under the SecurityConfig URL rules.
 *       Otherwise the saved page is dropped instead of ending on a 403.</li>
 *   <li>Without a saved page: ADMIN goes to {@value #ADMIN_HOME}, CUSTOMER to {@value #CUSTOMER_HOME}
 *       (the premium homepage, which shows the customer's account navigation).</li>
 * </ul>
 */
@Component
public class RoleBasedLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public static final String ADMIN_HOME = "/users/admin";
    /** Customers land on the premium homepage, which shows their account navigation. */
    public static final String CUSTOMER_HOME = "/";

    private final RequestCache requestCache = new HttpSessionRequestCache();
    /** Resolved lazily: the evaluator is built from the security filter chain that uses this handler. */
    private final ObjectProvider<WebInvocationPrivilegeEvaluator> privilegeEvaluator;

    public RoleBasedLoginSuccessHandler(ObjectProvider<WebInvocationPrivilegeEvaluator> privilegeEvaluator) {
        this.privilegeEvaluator = privilegeEvaluator;
        setRequestCache(requestCache);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null && !isAllowed(saved, request, authentication)) {
            // e.g. a customer bounced from an admin page lands on the homepage, not a 403.
            requestCache.removeRequest(request, response);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {
        return homeFor(authentication);
    }

    /** Home page for a signed-in user's role (also used when a signed-in user opens /login or /register). */
    public static String homeFor(Authentication authentication) {
        return isAdmin(authentication) ? ADMIN_HOME : CUSTOMER_HOME;
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** True if the signed-in user may open the saved page according to SecurityConfig's URL rules. */
    private boolean isAllowed(SavedRequest saved, HttpServletRequest request, Authentication authentication) {
        String path = URI.create(saved.getRedirectUrl()).getPath();
        String contextPath = request.getContextPath();
        if (path == null || !path.startsWith(contextPath)) {
            return false;
        }
        String uri = path.substring(contextPath.length());
        WebInvocationPrivilegeEvaluator evaluator = privilegeEvaluator.getIfAvailable();
        // Fail closed: without an evaluator, fall back to the role's home page.
        return evaluator != null && evaluator.isAllowed(contextPath, uri, saved.getMethod(), authentication);
    }
}
