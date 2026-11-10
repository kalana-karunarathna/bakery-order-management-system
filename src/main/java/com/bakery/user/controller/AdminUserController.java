package com.bakery.user.controller;

import com.bakery.user.model.AccountStatus;
import com.bakery.user.model.CustomerType;
import com.bakery.user.service.UserService;
import com.bakery.user.service.UserValidationException;
import com.bakery.user.service.UserView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin customer management. Protected twice: by the URL rule in SecurityConfig and by
 * {@code @PreAuthorize} here. Only CUSTOMER accounts can be changed; administrator accounts are
 * not editable from this screen, so an admin cannot remove their own access here.
 */
@Controller
@RequestMapping("/users/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @InitBinder("form")
    void allowAdminFields(WebDataBinder binder) {
        binder.setAllowedFields(ProfileForm.ADMIN_FIELDS);
    }

    /** Admin dashboard: account statistics and the most recent registrations. */
    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", userService.customerStats());
        model.addAttribute("recentCustomers", userService.recentCustomers(5));
        return "user/admin/dashboard";
    }

    @GetMapping("/customers")
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "") String status, Model model) {
        model.addAttribute("customers", userService.listCustomers(q, status));
        model.addAttribute("stats", userService.customerStats());
        model.addAttribute("q", q);
        model.addAttribute("status", status.toUpperCase());
        model.addAttribute("statuses", AccountStatus.values());
        return "user/admin/customers";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model, RedirectAttributes redirect) {
        try {
            UserView customer = userService.getCustomerForAdmin(id);
            addDetailModel(model, customer, ProfileForm.from(customer));
            return "user/admin/customer-detail";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", "Customer not found.");
            return "redirect:/users/admin/customers";
        }
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable String id, @ModelAttribute("form") ProfileForm form, BindingResult result,
                       Model model, RedirectAttributes redirect) {
        try {
            userService.adminUpdateCustomer(id, form.getFullName(), form.getEmail(), form.getPhone(),
                    form.getAddress(), form.getCustomerType());
        } catch (UserValidationException e) {
            FormErrors.apply(e, result);
            return detailAfterError(id, form, model, redirect);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/users/admin/customers";
        }
        redirect.addFlashAttribute("successMessage", "Customer " + id + " updated.");
        return "redirect:/users/admin/" + id;
    }

    @PostMapping("/{id}/status")
    public String setStatus(@PathVariable String id, @RequestParam boolean active, RedirectAttributes redirect) {
        try {
            userService.setCustomerActive(id, active);
            redirect.addFlashAttribute("successMessage",
                    "Customer " + id + (active ? " activated." : " deactivated. They have been signed out."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/users/admin/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes redirect) {
        try {
            userService.adminDeleteCustomer(id);
            redirect.addFlashAttribute("successMessage",
                    "Customer " + id + " deleted. Their personal details were erased; the ID is kept for order history.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/users/admin/customers";
    }

    private String detailAfterError(String id, ProfileForm form, Model model, RedirectAttributes redirect) {
        try {
            addDetailModel(model, userService.getCustomerForAdmin(id), form);
            return "user/admin/customer-detail";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", "Customer not found.");
            return "redirect:/users/admin/customers";
        }
    }

    private static void addDetailModel(Model model, UserView customer, ProfileForm form) {
        model.addAttribute("customer", customer);
        model.addAttribute("form", form);
        model.addAttribute("customerTypes", new CustomerType[]{CustomerType.REGULAR, CustomerType.LOYALTY});
    }
}
