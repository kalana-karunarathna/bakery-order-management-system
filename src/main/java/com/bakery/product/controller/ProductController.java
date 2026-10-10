package com.bakery.product.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Landing page only. Add this module's workflows when its implementation begins. */
@Controller
public class ProductController {

    @GetMapping("/products")
    public String index() {
        return "product/index";
    }
}
