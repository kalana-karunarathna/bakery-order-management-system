package com.bakery.order.controller;

import com.bakery.order.model.Order;
import com.bakery.order.model.OrderStatus;
import com.bakery.order.model.OrderType;
import com.bakery.order.service.OrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Handles all HTTP requests for the Order Management module.
 *
 * <p>Routes:</p>
 * <ul>
 *   <li>GET  /orders              → module landing / customer search hub</li>
 *   <li>GET  /orders/place        → place-order form</li>
 *   <li>POST /orders/place        → submit new order</li>
 *   <li>GET  /orders/my-orders    → customer's order history</li>
 *   <li>GET  /orders/admin        → admin: all orders list</li>
 *   <li>GET  /orders/{id}         → order detail page</li>
 *   <li>GET  /orders/{id}/edit    → edit PENDING order form</li>
 *   <li>POST /orders/{id}/edit    → submit edits</li>
 *   <li>POST /orders/{id}/cancel  → customer cancels order</li>
 *   <li>POST /orders/{id}/status  → admin: update order status</li>
 *   <li>POST /orders/{id}/delete  → admin: hard-delete order</li>
 * </ul>
 */
@Controller
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ── Landing ───────────────────────────────────────────────────────────────

    @GetMapping
    public String index() {
        return "order/index";
    }

    // ── Place Order ───────────────────────────────────────────────────────────

    @GetMapping("/place")
    public String placeOrderForm(Model model) {
        model.addAttribute("orderTypes", OrderType.values());
        return "order/place";
    }

    @PostMapping("/place")
    public String placeOrder(@RequestParam String customerId,
                             @RequestParam String productId,
                             @RequestParam String productName,
                             @RequestParam int quantity,
                             @RequestParam double unitPrice,
                             @RequestParam OrderType orderType,
                             @RequestParam(required = false) String deliveryAddress,
                             RedirectAttributes redirectAttributes) {
        try {
            Order saved = orderService.placeOrder(customerId, productId, productName,
                                                  quantity, unitPrice, orderType, deliveryAddress);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Order " + saved.getId() + " placed successfully!");
            return "redirect:/orders/" + saved.getId();
        } catch (IllegalArgumentException | IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/orders/place";
        }
    }

    // ── My Orders ─────────────────────────────────────────────────────────────

    @GetMapping("/my-orders")
    public String myOrders(@RequestParam(required = false) String customerId, Model model) {
        if (customerId != null && !customerId.isBlank()) {
            try {
                List<Order> orders = orderService.getOrdersByCustomerId(customerId);
                model.addAttribute("orders", orders);
                model.addAttribute("customerId", customerId);
                if (orders.isEmpty()) {
                    model.addAttribute("infoMessage", "No orders found for customer: " + customerId);
                }
            } catch (IOException e) {
                model.addAttribute("errorMessage", "Could not load orders: " + e.getMessage());
            }
        }
        return "order/my-orders";
    }

    // ── Admin: All Orders ─────────────────────────────────────────────────────

    @GetMapping("/admin")
    public String adminOrders(Model model) {
        try {
            List<Order> orders = orderService.getAllOrders();
            model.addAttribute("orders", orders);
            model.addAttribute("statuses", OrderStatus.values());
            model.addAttribute("countPending",
                    orders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count());
            model.addAttribute("countPreparing",
                    orders.stream().filter(o -> o.getStatus() == OrderStatus.PREPARING).count());
            model.addAttribute("countDelivered",
                    orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count());
        } catch (IOException e) {
            model.addAttribute("errorMessage", "Could not load orders: " + e.getMessage());
        }
        return "order/admin";
    }

    // ── Order Detail ──────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public String orderDetail(@PathVariable String id, Model model,
                              RedirectAttributes redirectAttributes) {
        try {
            Optional<Order> order = orderService.getOrderById(id);
            if (order.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Order " + id + " not found.");
                return "redirect:/orders";
            }
            model.addAttribute("order", order.get());
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Could not load order: " + e.getMessage());
            return "redirect:/orders";
        }
        return "order/detail";
    }

    // ── Edit Order ────────────────────────────────────────────────────────────

    @GetMapping("/{id}/edit")
    public String editOrderForm(@PathVariable String id, Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            Optional<Order> order = orderService.getOrderById(id);
            if (order.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Order " + id + " not found.");
                return "redirect:/orders";
            }
            if (!order.get().isEditable()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Only PENDING orders can be edited.");
                return "redirect:/orders/" + id;
            }
            model.addAttribute("order", order.get());
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Could not load order: " + e.getMessage());
            return "redirect:/orders";
        }
        return "order/edit";
    }

    @PostMapping("/{id}/edit")
    public String editOrder(@PathVariable String id,
                            @RequestParam int quantity,
                            @RequestParam(required = false) String deliveryAddress,
                            RedirectAttributes redirectAttributes) {
        try {
            orderService.updateOrderItems(id, quantity, deliveryAddress);
            redirectAttributes.addFlashAttribute("successMessage", "Order updated successfully.");
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    // ── Cancel Order ──────────────────────────────────────────────────────────

    @PostMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            orderService.cancelOrder(id);
            redirectAttributes.addFlashAttribute("successMessage", "Order " + id + " has been cancelled.");
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    // ── Admin: Update Status ──────────────────────────────────────────────────

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable String id,
                               @RequestParam OrderStatus status,
                               RedirectAttributes redirectAttributes) {
        try {
            orderService.updateStatus(id, status);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Order " + id + " status updated to " + status + ".");
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/orders/admin";
    }

    // ── Admin: Delete ─────────────────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    public String deleteOrder(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            orderService.deleteOrder(id);
            redirectAttributes.addFlashAttribute("successMessage", "Order " + id + " deleted.");
        } catch (IllegalArgumentException | IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/orders/admin";
    }
}
