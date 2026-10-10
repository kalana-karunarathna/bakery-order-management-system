package com.bakery.order.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Landing page only. Add this module's workflows when its implementation begins. */
@Controller
public class OrderController {

    @GetMapping("/orders")
    public String index() {
        return "order/index";
    }
}
