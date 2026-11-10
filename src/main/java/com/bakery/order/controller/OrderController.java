package com.bakery.order.controller;

import com.bakery.order.model.Order;
import com.bakery.order.model.OrderStatus;
import com.bakery.order.model.OrderType;
import com.bakery.order.service.OrderNotFoundException;
import com.bakery.order.service.OrderService;
import com.bakery.order.service.OrderStorageException;
import com.bakery.user.service.AuthenticatedUser;
import com.bakery.user.service.CurrentUserService;
import com.bakery.user.service.UserService;
import com.bakery.user.service.UserView;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Handles all HTTP requests for the Order Management module.
 *
 * <p>Routes and who may use them (enforced by SecurityConfig <b>and</b> {@code @PreAuthorize}):</p>
 * <ul>
 *   <li>GET  /orders              → public landing page</li>
 *   <li>GET  /orders/place        → CUSTOMER: place-order form</li>
 *   <li>POST /orders/place        → CUSTOMER: submit new order (customer = signed-in user)</li>
 *   <li>GET  /orders/my-orders    → CUSTOMER: the signed-in customer's orders</li>
 *   <li>GET  /orders/admin        → ADMIN: all orders</li>
 *   <li>GET  /orders/{id}         → owner or ADMIN: order detail</li>
 *   <li>GET  /orders/{id}/edit    → owner: edit PENDING order form</li>
 *   <li>POST /orders/{id}/edit    → owner: submit edits</li>
 *   <li>POST /orders/{id}/cancel  → owner: cancel PENDING order</li>
 *   <li>POST /orders/{id}/status  → ADMIN: update order status (workflow enforced)</li>
 *   <li>POST /orders/{id}/delete  → ADMIN: hard-delete order</li>
 * </ul>
 *
 * <p>The customer ID is never read from the request. Orders belonging to another customer are
 * reported as "not found".</p>
 */
@Controller
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private static final String NOT_FOUND = "Order not found.";

    private final OrderService orderService;
    private final CurrentUserService currentUserService;
    private final UserService userService;

    public OrderController(OrderService orderService, CurrentUserService currentUserService, UserService userService) {
        this.orderService = orderService;
        this.currentUserService = currentUserService;
        this.userService = userService;
    }

    // ── Landing ───────────────────────────────────────────────────────────────

    @GetMapping
    public String index() {
        return "order/index";
    }

    // ── Place Order ───────────────────────────────────────────────────────────

    @GetMapping("/place")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String placeOrderForm(Model model) {
        UserView me = userService.getView(currentUserService.requireCurrentUser().id());
        Map<String, String> form = new LinkedHashMap<>();
        form.put("productId", "");
        form.put("productName", "");
        form.put("quantity", "1");
        form.put("unitPrice", "");
        form.put("orderType", OrderType.PICKUP.name());
        form.put("deliveryAddress", me.address());   // pre-filled from the profile, still editable
        return placeForm(model, me, form);
    }

    @PostMapping("/place")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String placeOrder(@RequestParam(defaultValue = "") String productId,
                             @RequestParam(defaultValue = "") String productName,
                             @RequestParam(defaultValue = "") String quantity,
                             @RequestParam(defaultValue = "") String unitPrice,
                             @RequestParam(defaultValue = "") String orderType,
                             @RequestParam(defaultValue = "") String deliveryAddress,
                             Model model, RedirectAttributes redirectAttributes) {
        AuthenticatedUser me = currentUserService.requireCurrentUser();
        try {
            Order saved = orderService.placeOrder(me.id(), productId, productName,
                    parseQuantity(quantity), parsePrice(unitPrice), parseType(orderType), deliveryAddress);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Order " + saved.getId() + " placed successfully!");
            return "redirect:/orders/" + saved.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("productId", productId);
            form.put("productName", productName);
            form.put("quantity", quantity);
            form.put("unitPrice", unitPrice);
            form.put("orderType", orderType.isBlank() ? OrderType.PICKUP.name() : orderType);
            form.put("deliveryAddress", deliveryAddress);
            model.addAttribute("errorMessage", e.getMessage());
            return placeForm(model, userService.getView(me.id()), form);
        }
    }

    private String placeForm(Model model, UserView me, Map<String, String> form) {
        model.addAttribute("orderTypes", OrderType.values());
        model.addAttribute("customer", me);
        model.addAttribute("form", form);
        return "order/place";
    }

    // ── My Orders ─────────────────────────────────────────────────────────────

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String myOrders(Model model) {
        AuthenticatedUser me = currentUserService.requireCurrentUser();
        model.addAttribute("orders", orderService.getOrdersForCustomer(me.id()));
        model.addAttribute("customerName", me.fullName());
        model.addAttribute("customerId", me.id());
        return "order/my-orders";
    }

    // ── Admin: All Orders ─────────────────────────────────────────────────────

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOrders(Model model) {
        model.addAttribute("orders", orderService.getAllOrders());
        model.addAttribute("statuses", OrderStatus.values());
        return "order/admin";
    }

    // ── Order Detail ──────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public String orderDetail(@PathVariable String id, Model model) {
        AuthenticatedUser me = currentUserService.requireCurrentUser();
        Order order = orderService.getOrderFor(id, me);
        model.addAttribute("order", order);
        model.addAttribute("isOwner", order.getCustomerId().equals(me.id()));
        model.addAttribute("isAdmin", me.isAdmin());
        return "order/detail";
    }

    // ── Edit Order ────────────────────────────────────────────────────────────

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String editOrderForm(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        Order order = orderService.getOwnOrder(id, currentUserService.requireCurrentUser().id());
        if (!order.isEditable()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only pending orders can be edited.");
            return "redirect:/orders/" + id;
        }
        model.addAttribute("order", order);
        return "order/edit";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String editOrder(@PathVariable String id,
                            @RequestParam(defaultValue = "") String quantity,
                            @RequestParam(required = false) String deliveryAddress,
                            RedirectAttributes redirectAttributes) {
        String me = currentUserService.requireCurrentUser().id();
        try {
            orderService.updateOwnOrder(id, me, parseQuantity(quantity), deliveryAddress);
            redirectAttributes.addFlashAttribute("successMessage", "Order updated successfully.");
            return "redirect:/orders/" + id;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/orders/" + id + "/edit";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/orders/" + id;
        }
    }

    // ── Cancel Order ──────────────────────────────────────────────────────────

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String cancelOrder(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            orderService.cancelOwnOrder(id, currentUserService.requireCurrentUser().id());
            redirectAttributes.addFlashAttribute("successMessage", "Order " + id + " has been cancelled.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    // ── Admin: Update Status ──────────────────────────────────────────────────

    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateStatus(@PathVariable String id,
                               @RequestParam(defaultValue = "") String status,
                               RedirectAttributes redirectAttributes) {
        try {
            OrderStatus newStatus = OrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            orderService.updateStatus(id, newStatus);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Order " + id + " status updated to " + newStatus + ".");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Choose a valid status.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (OrderNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", NOT_FOUND);
        }
        return "redirect:/orders/admin";
    }

    // ── Admin: Delete ─────────────────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteOrder(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            orderService.deleteOrder(id);
            redirectAttributes.addFlashAttribute("successMessage", "Order " + id + " deleted.");
        } catch (OrderNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", NOT_FOUND);
        }
        return "redirect:/orders/admin";
    }

    // ── Error handling (no technical details shown to users) ──────────────────

    @ExceptionHandler(OrderNotFoundException.class)
    public String handleNotFound(HttpServletRequest request) {
        return redirectWithError(request, NOT_FOUND);
    }

    @ExceptionHandler(OrderStorageException.class)
    public String handleStorage(OrderStorageException e, HttpServletRequest request) {
        log.error("Order storage failure", e.getCause());
        return redirectWithError(request, e.getMessage());
    }

    private String redirectWithError(HttpServletRequest request, String message) {
        FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
        flash.put("errorMessage", message);
        boolean admin = currentUserService.currentUser().map(AuthenticatedUser::isAdmin).orElse(false);
        return admin ? "redirect:/orders/admin" : "redirect:/orders/my-orders";
    }

    // ── Input parsing with friendly messages ──────────────────────────────────

    private static int parseQuantity(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Quantity must be a whole number.");
        }
    }

    private static double parsePrice(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Please enter the unit price as a number, for example 1200.00.");
        }
    }

    private static OrderType parseType(String value) {
        try {
            return OrderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Please choose pickup or delivery.");
        }
    }
}
