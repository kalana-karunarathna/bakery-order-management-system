package com.bakery.cake.controller;

import com.bakery.cake.exception.AccessDeniedBookingException;
import com.bakery.cake.exception.BookingNotFoundException;
import com.bakery.cake.exception.InvalidBookingException;
import com.bakery.cake.model.BookingStatus;
import com.bakery.cake.model.CakeBooking;
import com.bakery.cake.service.CakeBookingService;
import com.bakery.cake.service.CurrentCustomerProvider;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * OOP: Controller layer – handles HTTP requests and delegates ALL business logic
 * to {@link CakeBookingService}.
 *
 * <p>Access control:
 * <ul>
 *   <li>ADMIN: Full CRUD over all customer cake bookings at {@code /cake-bookings/admin/**}</li>
 *   <li>CUSTOMER: Full CRUD over their own cake bookings at {@code /cake-bookings/**}</li>
 * </ul>
 */
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

    // ── GET /cake-bookings – Customer Bookings List (or Admin Dashboard) ─────

    /**
     * Lists bookings. If an administrator opens this page, directly renders the
     * full Admin Cake Orders Management dashboard (cake/admin) so they see Photo 3
     * immediately with HTTP 200. For regular customers, shows their own bookings.
     */
    @GetMapping
    public String listBookings(HttpSession session, Model model) {
        if (customerProvider.isAdmin()) {
            return adminListBookings(model);
        }

        // For authenticated customers, load only their own bookings
        List<CakeBooking> bookings = bookingService.getMyBookings(session);
        model.addAttribute("bookings", bookings);
        model.addAttribute("today", LocalDate.now());
        return "cake/index";
    }

    // ── GET /cake-bookings/new – Customer Create Form ─────────────────────────

    @GetMapping("/new")
    public String showNewForm(Model model) {
        model.addAttribute("minDeliveryDate", LocalDate.now().plusDays(3));
        return "cake/new";
    }

    // ── POST /cake-bookings – Customer Submit New Booking ─────────────────────

    @PostMapping
    public String createBooking(
            @RequestParam String cakeType,
            @RequestParam String flavour,
            @RequestParam double weightKg,
            @RequestParam int tiers,
            @RequestParam(defaultValue = "") String messageOnCake,
            @RequestParam(defaultValue = "") String specialInstructions,
            @RequestParam(defaultValue = "") String extra1,
            @RequestParam(defaultValue = "") String extra2,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam String contactPhone,
            @RequestParam(required = false) MultipartFile designImage,
            HttpSession session,
            RedirectAttributes redirectAttrs,
            Model model) {

        if (!customerProvider.isAuthenticated()) {
            return "redirect:/login";
        }

        try {
            String customerId = customerProvider.getCurrentCustomerId(session);
            CakeBooking booking = bookingService.createBooking(
                    customerId, cakeType, flavour, weightKg, tiers,
                    messageOnCake, specialInstructions, extra1, extra2,
                    deliveryDate, contactPhone, designImage);

            redirectAttrs.addFlashAttribute("successMessage",
                    "Custom cake booking " + booking.getBookingId() + " created successfully!");
            return "redirect:/cake-bookings";

        } catch (InvalidBookingException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("minDeliveryDate", LocalDate.now().plusDays(3));
            model.addAttribute("prevCakeType",  cakeType);
            model.addAttribute("prevFlavour",   flavour);
            model.addAttribute("prevWeightKg",  weightKg);
            model.addAttribute("prevTiers",     tiers);
            model.addAttribute("prevMessage",   messageOnCake);
            model.addAttribute("prevSpecial",   specialInstructions);
            model.addAttribute("prevExtra1",    extra1);
            model.addAttribute("prevExtra2",    extra2);
            model.addAttribute("prevPhone",     contactPhone);
            return "cake/new";
        }
    }

    // ── GET /cake-bookings/{id} – View Booking Details (Owner or Admin) ───────

    @GetMapping("/{id}")
    public String viewBooking(@PathVariable String id, HttpSession session, Model model) {
        try {
            boolean isAdmin = customerProvider.isAdmin();
            model.addAttribute("isAdmin", isAdmin);

            CakeBooking booking;
            if (isAdmin) {
                booking = bookingService.getBookingByIdForAdmin(id);
            } else {
                booking = bookingService.getBookingForCurrentCustomer(id, session);
            }
            model.addAttribute("booking", booking);
            return "cake/detail";
        } catch (BookingNotFoundException e) {
            model.addAttribute("errorMessage", "Booking not found: " + id);
            return "cake/error";
        } catch (AccessDeniedBookingException e) {
            model.addAttribute("errorMessage", "You do not have permission to view that booking.");
            return "cake/error";
        }
    }

    // ── GET /cake-bookings/{id}/edit – Customer Edit Form (PENDING only) ──────

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable String id, HttpSession session, Model model) {
        if (customerProvider.isAdmin()) {
            return "redirect:/cake-bookings/admin/" + id + "/edit";
        }

        try {
            CakeBooking booking = bookingService.getBookingForCurrentCustomer(id, session);

            if (!booking.isPending()) {
                model.addAttribute("errorMessage",
                        "Only PENDING bookings can be edited. Current status: "
                                + booking.getStatus().getLabel());
                return "cake/error";
            }

            model.addAttribute("booking", booking);
            model.addAttribute("minDeliveryDate", LocalDate.now().plusDays(3));
            return "cake/edit";

        } catch (BookingNotFoundException e) {
            model.addAttribute("errorMessage", "Booking not found: " + id);
            return "cake/error";
        } catch (AccessDeniedBookingException e) {
            model.addAttribute("errorMessage", "You do not have access to that booking.");
            return "cake/error";
        }
    }

    // ── POST /cake-bookings/{id}/edit – Customer Submit Edit ──────────────────

    @PostMapping("/{id}/edit")
    public String updateBooking(
            @PathVariable String id,
            @RequestParam String cakeType,
            @RequestParam String flavour,
            @RequestParam double weightKg,
            @RequestParam int tiers,
            @RequestParam(defaultValue = "") String messageOnCake,
            @RequestParam(defaultValue = "") String specialInstructions,
            @RequestParam(defaultValue = "") String extra1,
            @RequestParam(defaultValue = "") String extra2,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam String contactPhone,
            @RequestParam(required = false) MultipartFile designImage,
            HttpSession session,
            RedirectAttributes redirectAttrs,
            Model model) {

        try {
            bookingService.updateBooking(id, session, cakeType, flavour, weightKg, tiers,
                    messageOnCake, specialInstructions, extra1, extra2,
                    deliveryDate, contactPhone, designImage);

            redirectAttrs.addFlashAttribute("successMessage",
                    "Booking " + id + " updated successfully!");
            return "redirect:/cake-bookings/" + id;

        } catch (InvalidBookingException e) {
            model.addAttribute("errorMessage", e.getMessage());
            try {
                model.addAttribute("booking", bookingService.getBookingForCurrentCustomer(id, session));
            } catch (Exception ignored) { }
            model.addAttribute("minDeliveryDate", LocalDate.now().plusDays(3));
            return "cake/edit";

        } catch (BookingNotFoundException e) {
            model.addAttribute("errorMessage", "Booking not found: " + id);
            return "cake/error";
        } catch (AccessDeniedBookingException e) {
            model.addAttribute("errorMessage", "Access denied.");
            return "cake/error";
        }
    }

    // ── POST /cake-bookings/{id}/delete – Customer Cancel Booking ─────────────

    @PostMapping("/{id}/delete")
    public String cancelBooking(@PathVariable String id, HttpSession session,
                                RedirectAttributes redirectAttrs, Model model) {
        try {
            bookingService.cancelBooking(id, session);
            redirectAttrs.addFlashAttribute("successMessage",
                    "Booking " + id + " has been cancelled.");
            return "redirect:/cake-bookings";

        } catch (BookingNotFoundException e) {
            model.addAttribute("errorMessage", "Booking not found: " + id);
            return "cake/error";
        } catch (AccessDeniedBookingException | InvalidBookingException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "cake/error";
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Admin Full CRUD Operations (/cake-bookings/admin/**) ──────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Admin Dashboard: Displays ALL customer cake bookings with stats and management actions.
     */
    @GetMapping("/admin")
    public String adminListBookings(Model model) {
        if (!customerProvider.isAdmin()) {
            return "redirect:/access-denied";
        }

        List<CakeBooking> allBookings = bookingService.getAllBookings();
        model.addAttribute("bookings", allBookings);
        model.addAttribute("statuses", BookingStatus.values());

        // Calculate order counts for the admin statistics cards
        long totalCount     = allBookings.size();
        long pendingCount   = allBookings.stream().filter(b -> b.getStatus() == BookingStatus.PENDING).count();
        long confirmedCount = allBookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
        long completedCount = allBookings.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED).count();
        long cancelledCount = allBookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();

        model.addAttribute("totalCount", totalCount);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("confirmedCount", confirmedCount);
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("cancelledCount", cancelledCount);

        return "cake/admin";
    }

    /**
     * Admin View: Inspect full details of any customer cake order.
     */
    @GetMapping("/admin/{id}")
    public String adminViewBooking(@PathVariable String id, Model model) {
        if (!customerProvider.isAdmin()) {
            return "redirect:/access-denied";
        }

        try {
            CakeBooking booking = bookingService.getBookingByIdForAdmin(id);
            model.addAttribute("booking", booking);
            model.addAttribute("isAdmin", true);
            return "cake/detail";
        } catch (BookingNotFoundException e) {
            model.addAttribute("errorMessage", "Booking not found: " + id);
            return "cake/error";
        }
    }

    /**
     * Admin Status Update: Quick update of order fulfillment status.
     */
    @PostMapping("/admin/{id}/status")
    public String adminUpdateStatus(@PathVariable String id,
                                    @RequestParam BookingStatus status,
                                    RedirectAttributes redirectAttrs) {
        if (!customerProvider.isAdmin()) {
            return "redirect:/access-denied";
        }

        try {
            bookingService.updateBookingStatus(id, status);
            redirectAttrs.addFlashAttribute("successMessage",
                    "Booking " + id + " status successfully updated to " + status.getLabel() + ".");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cake-bookings/admin";
    }

    /**
     * Admin Edit Form: Allows bakery staff to modify any order.
     */
    @GetMapping("/admin/{id}/edit")
    public String adminShowEditForm(@PathVariable String id, Model model) {
        if (!customerProvider.isAdmin()) {
            return "redirect:/access-denied";
        }

        try {
            CakeBooking booking = bookingService.getBookingByIdForAdmin(id);
            model.addAttribute("booking", booking);
            model.addAttribute("statuses", BookingStatus.values());
            model.addAttribute("isAdmin", true);
            return "cake/admin-edit";
        } catch (BookingNotFoundException e) {
            model.addAttribute("errorMessage", "Booking not found: " + id);
            return "cake/error";
        }
    }

    /**
     * Admin Submit Edit: Saves updates to any order with status modification.
     */
    @PostMapping("/admin/{id}/edit")
    public String adminSubmitEdit(
            @PathVariable String id,
            @RequestParam String cakeType,
            @RequestParam String flavour,
            @RequestParam double weightKg,
            @RequestParam int tiers,
            @RequestParam(defaultValue = "") String messageOnCake,
            @RequestParam(defaultValue = "") String specialInstructions,
            @RequestParam(defaultValue = "") String extra1,
            @RequestParam(defaultValue = "") String extra2,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam String contactPhone,
            @RequestParam BookingStatus status,
            @RequestParam(required = false) java.math.BigDecimal price,
            @RequestParam(required = false) MultipartFile designImage,
            RedirectAttributes redirectAttrs) {

        if (!customerProvider.isAdmin()) {
            return "redirect:/access-denied";
        }

        try {
            bookingService.adminUpdateBooking(id, cakeType, flavour, weightKg, tiers,
                    messageOnCake, specialInstructions, extra1, extra2,
                    deliveryDate, contactPhone, status, price, designImage);

            redirectAttrs.addFlashAttribute("successMessage",
                    "Booking " + id + " updated successfully by Admin.");
            return "redirect:/cake-bookings/admin";

        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "Error updating booking: " + e.getMessage());
            return "redirect:/cake-bookings/admin/" + id + "/edit";
        }
    }

    /**
     * Admin Delete: Removes a customer cake order completely.
     */
    @PostMapping("/admin/{id}/delete")
    public String adminDeleteBooking(@PathVariable String id, RedirectAttributes redirectAttrs) {
        if (!customerProvider.isAdmin()) {
            return "redirect:/access-denied";
        }

        try {
            bookingService.adminDeleteBooking(id);
            redirectAttrs.addFlashAttribute("successMessage",
                    "Booking " + id + " has been permanently deleted by Admin.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cake-bookings/admin";
    }

    /**
     * Admin Create Form: Allows bakery staff to place an order for a customer.
     */
    @GetMapping("/admin/new")
    public String adminShowNewForm(Model model) {
        if (!customerProvider.isAdmin()) {
            return "redirect:/access-denied";
        }
        model.addAttribute("minDeliveryDate", LocalDate.now().plusDays(3));
        return "cake/admin-new";
    }

    /**
     * Admin Submit New: Places a cake order on behalf of a customer.
     */
    @PostMapping("/admin/new")
    public String adminCreateBooking(
            @RequestParam String customerId,
            @RequestParam String cakeType,
            @RequestParam String flavour,
            @RequestParam double weightKg,
            @RequestParam int tiers,
            @RequestParam(defaultValue = "") String messageOnCake,
            @RequestParam(defaultValue = "") String specialInstructions,
            @RequestParam(defaultValue = "") String extra1,
            @RequestParam(defaultValue = "") String extra2,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam String contactPhone,
            @RequestParam(required = false) java.math.BigDecimal price,
            @RequestParam(required = false) MultipartFile designImage,
            RedirectAttributes redirectAttrs,
            Model model) {

        if (!customerProvider.isAdmin()) {
            return "redirect:/access-denied";
        }

        try {
            CakeBooking booking = bookingService.createBooking(
                    customerId, cakeType, flavour, weightKg, tiers,
                    messageOnCake, specialInstructions, extra1, extra2,
                    deliveryDate, contactPhone, price, designImage);

            redirectAttrs.addFlashAttribute("successMessage",
                    "New booking " + booking.getBookingId() + " created for customer " + customerId + "!");
            return "redirect:/cake-bookings/admin";

        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("minDeliveryDate", LocalDate.now().plusDays(3));
            return "cake/admin-new";
        }
    }
}
