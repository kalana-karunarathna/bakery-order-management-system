package com.bakery.payment.integration;

import com.bakery.payment.controller.PaymentController;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Chooses the page shell for payment pages: the admin dashboard layout (top bar + sidebar) for signed-in
 * administrators, the customer storefront layout for everyone else. Presentation only; access rules are
 * in SecurityConfig and PaymentService.
 */
@ControllerAdvice(assignableTypes = PaymentController.class)
public class PaymentLayoutAdvice {

    @ModelAttribute
    public void layout(Model model) {
        apply(model);
    }

    /** Also called by PaymentController's exception handlers, which do not receive advice model attributes. */
    public static void apply(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        model.addAttribute("adminShell", admin);
    }
}
