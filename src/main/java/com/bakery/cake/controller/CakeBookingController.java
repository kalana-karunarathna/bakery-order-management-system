package com.bakery.cake.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Landing page only. Add this module's workflows when its implementation begins. */
@Controller
public class CakeBookingController {

    @GetMapping("/cake-bookings")
    public String index() {
        return "cake/index";
    }
}
