package com.bakery.feedback.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Landing page only. Add this module's workflows when its implementation begins. */
@Controller
public class FeedbackController {

    @GetMapping("/feedback")
    public String index() {
        return "feedback/index";
    }
}
