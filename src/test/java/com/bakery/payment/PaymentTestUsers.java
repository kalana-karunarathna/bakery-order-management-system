package com.bakery.payment;

import com.bakery.payment.service.PaymentIntegration.Actor;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.security.BakeryUserPrincipal;
import com.bakery.user.service.UserService;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Real Spring Security sign-in for the payment UI tests that use test payment adapters. The payment
 * identity still comes from the session's {@code testActor}; the request is signed in as a matching real
 * account (customers U001/U002, admin U003 in the test's temporary data folder) so Spring Security, the
 * account-status filter and the storefront/admin headers behave exactly as in the application.
 */
final class PaymentTestUsers {

    static final String PASSWORD = "Bakery2026";

    private PaymentTestUsers() {
    }

    /** Creates U001, U002 (customers) and U003 (admin) once per temporary data folder. */
    static void ensure(UserService userService, UserRepository users) {
        if (!users.findAll().isEmpty()) return;
        userService.register("Payment Customer One", "pay.one@example.com", "077 111 1111", "1 Lake Road, Kandy", PASSWORD, PASSWORD);
        userService.register("Payment Customer Two", "pay.two@example.com", "077 222 2222", "2 Hill Street, Galle", PASSWORD, PASSWORD);
        userService.createAdmin("Payment Admin", "pay.admin@example.com", "Admin2026x");
    }

    /** Spring CSRF token plus a sign-in that matches the session's test actor (none: anonymous). */
    static RequestPostProcessor signedIn(MockHttpSession session, UserRepository users) {
        return request -> {
            request = csrf().postProcessRequest(request);
            Actor actor = (Actor) session.getAttribute("testActor");
            if (actor == null) return request;
            String id = actor.admin() ? "U003" : users.findById(actor.customerId()).isPresent() ? actor.customerId() : "U001";
            return user(new BakeryUserPrincipal(users.findById(id).orElseThrow())).postProcessRequest(request);
        };
    }
}
