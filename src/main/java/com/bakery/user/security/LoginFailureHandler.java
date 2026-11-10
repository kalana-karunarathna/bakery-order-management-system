package com.bakery.user.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sends failed sign-ins back to the login page. The typed email is kept in the session (not the
 * URL) so the form can be refilled; the password is never kept.
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    public static final String LAST_EMAIL_ATTRIBUTE = "BAKERY_LOGIN_EMAIL";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String email = request.getParameter("email");
        if (email != null && email.length() <= 254) {
            request.getSession().setAttribute(LAST_EMAIL_ATTRIBUTE, email.strip());
        }
        // DisabledException is only raised after the password was verified (see SecurityConfig).
        String target = exception instanceof DisabledException ? "/login?disabled" : "/login?error";
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
