package com.bakery.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Landing page only. Add this module's workflows when its implementation begins. */
@Controller
public class UserController {

    @GetMapping("/users")
    public String index() {
        return "user/index";
    }
}
