package com.bakery.cake.controller;

import com.bakery.cake.model.CakeBooking;
import com.bakery.cake.service.CakeBookingService;
import com.bakery.cake.service.CurrentCustomerProvider;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/cake-bookings")
public class CakeBookingController {

    private final CakeBookingService bookingService;
    private final CurrentCustomerProvider customerProvider;

    public CakeBookingController(CakeBookingService bookingService,
                                 CurrentCustomerProvider customerProvider) {
        this.bookingService    = bookingService;
        this.customerProvider  = customerProvider;
    }

    @GetMapping
    public String listBookings(HttpSession session, Model model) {
        List<CakeBooking> bookings = bookingService.getMyBookings(session);
        model.addAttribute("bookings", bookings);
        model.addAttribute("today", LocalDate.now());
        return "cake/index";
    }
}
