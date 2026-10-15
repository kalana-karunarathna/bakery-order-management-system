package com.bakery.payment.demo;

import com.bakery.payment.controller.PaymentController;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** Visible labeling for all demo pages, including printed/downloaded invoices. */
@ControllerAdvice(assignableTypes=PaymentController.class)
@Profile("payment-demo")
public class PaymentDemoAdvice {
    @ModelAttribute
    public void demo(Model model, HttpSession session) {
        var actor = PaymentDemoConfiguration.current(session);
        model.addAttribute("paymentDemo",true);
        model.addAttribute("demoPersona",actor.admin() ? "admin" :
            actor.customerId().equals("DEMO-U002") ? "customer2" : "customer1");
    }
}