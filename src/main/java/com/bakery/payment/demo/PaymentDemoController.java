package com.bakery.payment.demo;

import jakarta.servlet.http.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** A local demo selector, never a production authentication endpoint. */
@Controller
@Profile("payment-demo")
public class PaymentDemoController {
    @PostMapping("/payments/demo/session")
    public String change(HttpServletRequest request, @RequestParam String csrf, @RequestParam String persona) {
        HttpSession session = request.getSession(false);
        if (session == null || !csrf.equals(session.getAttribute("paymentCsrf")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Refresh the payment page before switching demo accounts.");
        var actor = PaymentDemoConfiguration.selected(persona);
        if (actor == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid demo account.");
        request.changeSessionId();
        session.setAttribute(PaymentDemoConfiguration.SESSION_ACTOR,actor);
        session.removeAttribute("paymentCsrf");
        return actor.admin() ? "redirect:/payments/admin" : "redirect:/payments";
    }
}