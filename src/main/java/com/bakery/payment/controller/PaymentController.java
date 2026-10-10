package com.bakery.payment.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Landing page only. Add this module's workflows when its implementation begins. */
@Controller
public class PaymentController {

    @GetMapping("/payments")
    public String index() {
        return "payment/index";
    }
}
