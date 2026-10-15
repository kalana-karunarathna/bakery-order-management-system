package com.bakery.payment.controller;

import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.*;
import com.bakery.payment.model.PaymentValidationException;
import com.bakery.payment.service.*;
import com.bakery.payment.service.PaymentIntegration.*;
import com.bakery.payment.repository.PaymentRepository.StorageException;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.*;

/** HTTP validation complements service rules; raw values are retained only for escaped form rendering. */
@Controller
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService service;
    private final IdentityProvider identity;
    public PaymentController(PaymentService service, IdentityProvider identity) {
        this.service = service; this.identity = identity;
    }
    private Actor actor(HttpSession session) { return identity.current(session); }
    private String token(HttpSession session) {
        if (session.getAttribute("paymentCsrf") == null)
            session.setAttribute("paymentCsrf", UUID.randomUUID().toString());
        return (String) session.getAttribute("paymentCsrf");
    }
    private void csrf(HttpSession session, String value) {
        if (!token(session).equals(value))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your form expired. Refresh the page and try again.");
    }
    @ModelAttribute
    void common(Model model, HttpSession session, HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        model.addAttribute("actor", actor(session));
        model.addAttribute("csrf", token(session));
        model.addAttribute("methods", Method.values());
        model.addAttribute("statuses", Status.values());
        model.addAttribute("form", Map.of());
        model.addAttribute("errors", Map.of());
        model.addAttribute("download", false);
    }
    private <E extends Enum<E>> E choice(Class<E> type, String raw, String field, boolean optional) {
        if (raw == null || raw.isBlank()) {
            if (optional) return null;
            throw new PaymentValidationException(field, "Please select a valid " + field + ".");
        }
        try { return Enum.valueOf(type,raw.trim()); }
        catch (IllegalArgumentException ex) {
            throw new PaymentValidationException(field, field.equals("status") ? "Invalid payment status."
                : "Please select a valid " + field + ".");
        }
    }
    private BigDecimal amount(String raw) {
        if (raw == null || raw.isBlank())
            throw new PaymentValidationException("amount", "Payment amount is required.");
        if (raw.length() > 30 || !raw.trim().matches("-?[0-9]+(\\.[0-9]{1,2})?"))
            throw new PaymentValidationException("amount", "Enter a numeric amount with a maximum of two decimal places.");
        try { return Payment.money(new BigDecimal(raw.trim())); }
        catch (NumberFormatException ex) {
            throw new PaymentValidationException("amount", "Enter a valid payment amount.");
        }
    }
    private void failure(Model model, Map<String,String> values, IllegalArgumentException ex,
                         HttpServletResponse response) {
        response.setStatus(400);
        model.addAttribute("form", values);
        model.addAttribute("errors", Map.of(ex instanceof PaymentValidationException v ? v.getField() : "_global", ex.getMessage()));
    }
    private String historyView(HttpSession session, Model model, Map<String,String> params, boolean admin,
                               HttpServletResponse response) {
        Actor actor = actor(session);
        if (admin) service.requireAdmin(actor);
        try {
            Method method = choice(Method.class,params.get("method"),"method",true);
            Status status = choice(Status.class,params.get("status"),"status",true);
            String query = params.getOrDefault("query","").trim();
            if (query.length() > 150 || query.chars().anyMatch(Character::isISOControl))
                throw new PaymentValidationException("query", "Search must be at most 150 characters without control characters.");
            model.addAttribute("payments",actor == null ? List.of() : service.history(actor,query,method,status));
        } catch (IllegalArgumentException ex) {
            failure(model,params,ex,response); model.addAttribute("payments",List.of());
        }
        return admin ? "payment/admin" : "payment/index";
    }
    @GetMapping
    String history(HttpSession session, Model model, @RequestParam Map<String,String> params, HttpServletResponse response) {
        return historyView(session,model,params,false,response);
    }
    @GetMapping("/admin")
    String admin(HttpSession session, Model model, @RequestParam Map<String,String> params, HttpServletResponse response) {
        return historyView(session,model,params,true,response);
    }
    /** Presentation-only projection; the service still owns every balance and authorization rule. */
    public record PaymentTargetView(TargetType type, String id, String customerId, BigDecimal total,
                                    BigDecimal remaining, BigDecimal paid, BigDecimal pending) {}
    private void paymentFormModel(HttpSession session, Model model) {
        Actor actor = actor(session);
        var records = service.history(actor,null,null,null);
        var payables = service.targets(actor);
        model.addAttribute("targets",service.targetOptions(actor).stream().map(t -> {
            String customerId = payables.stream().filter(p -> p.type() == t.type() && p.id().equals(t.id()))
                .map(Payable::customerId).findFirst().orElse("");
            BigDecimal paid = records.stream().filter(p -> p.getTargetType() == t.type() && p.getTargetId().equals(t.id())
                && p.getStatus() == Status.PAID).map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
            return new PaymentTargetView(t.type(),t.id(),customerId,t.total(),t.remaining(),paid,
                t.total().subtract(t.remaining()).subtract(paid).max(BigDecimal.ZERO));
        }).toList());
    }
    @GetMapping("/new")
    String make(HttpSession session, Model model) {
        service.requireActor(actor(session));
        paymentFormModel(session,model);
        model.addAttribute("requestKey", UUID.randomUUID().toString());
        return "payment/make";
    }
    @PostMapping
    String create(HttpSession session, @RequestParam Map<String,String> values, Model model,
                  HttpServletResponse response, RedirectAttributes flash) {
        csrf(session,values.get("csrf")); service.requireActor(actor(session));
        try {
            Payment p = service.create(actor(session),
                choice(TargetType.class,values.get("type"),"type",false), values.get("targetId"),
                choice(Method.class,values.get("method"),"method",false),
                choice(Purpose.class,values.get("purpose"),"purpose",false),
                amount(values.get("amount")),values.get("reference"),values.get("requestKey"));
            flash.addFlashAttribute("success","Payment successfully recorded. It remains Pending until verified by an administrator.");
            return "redirect:/payments/" + p.getId();
        } catch (IllegalArgumentException ex) {
            failure(model,values,ex,response);
            paymentFormModel(session,model);
            model.addAttribute("requestKey",values.getOrDefault("requestKey",UUID.randomUUID().toString()));
            return "payment/make";
        }
    }
    private void invoiceModel(HttpSession session, Model model, String id) {
        Payment p = service.get(actor(session),id);
        model.addAttribute("payment",p);
        model.addAttribute("remaining",service.remaining(actor(session),id));
        model.addAttribute("available",service.availableForEdit(actor(session),id));
        model.addAttribute("paidAmount",p.getStatus() == Status.PAID ? p.getAmount() : BigDecimal.ZERO.setScale(2));
    }
    @GetMapping("/{id}")
    String invoice(HttpSession session, Model model, @PathVariable String id) {
        invoiceModel(session,model,id); return "payment/invoice";
    }
    /** Download the same authorized, escaped invoice as HTML; browser Print can save it as PDF. */
    @GetMapping("/{id}/download")
    String download(HttpSession session, Model model, @PathVariable String id, HttpServletResponse response) {
        invoiceModel(session,model,id);
        response.setHeader("Content-Disposition","attachment; filename=\"payment-invoice.html\"");
        response.setHeader("Cache-Control","no-store");
        model.addAttribute("download",true); return "payment/invoice";
    }
    @PostMapping("/{id}/edit")
    String edit(HttpSession session, @PathVariable String id, @RequestParam Map<String,String> values,
                Model model, HttpServletResponse response, RedirectAttributes flash) {
        csrf(session,values.get("csrf")); service.get(actor(session),id);
        try {
            service.edit(actor(session),id,amount(values.get("amount")),values.get("reference"));
            flash.addFlashAttribute("success","Payment successfully updated.");
            return "redirect:/payments/" + id;
        } catch (IllegalArgumentException ex) {
            failure(model,values,ex,response); invoiceModel(session,model,id); return "payment/invoice";
        }
    }
    @PostMapping("/{id}/status")
    String status(HttpSession session, @PathVariable String id, @RequestParam Map<String,String> values,
                  Model model, HttpServletResponse response, RedirectAttributes flash) {
        csrf(session,values.get("csrf")); service.get(actor(session),id);
        try {
            service.transition(actor(session),id,choice(Status.class,values.get("status"),"status",false));
            flash.addFlashAttribute("success","Payment status successfully updated.");
            return "redirect:/payments/" + id;
        } catch (IllegalArgumentException ex) {
            failure(model,values,ex,response); invoiceModel(session,model,id); return "payment/invoice";
        }
    }
    @PostMapping("/{id}/delete")
    String delete(HttpSession session, @PathVariable String id, @RequestParam Map<String,String> values,
                  Model model, HttpServletResponse response, RedirectAttributes flash) {
        csrf(session,values.get("csrf")); service.requireAdmin(actor(session));
        try {
            if (!"true".equals(values.get("confirmed")))
                throw new PaymentValidationException("confirmed","Confirm deletion first.");
            service.delete(actor(session),id);
            flash.addFlashAttribute("success","Payment successfully deleted.");
            return "redirect:/payments/admin";
        } catch (IllegalArgumentException ex) {
            failure(model,values,ex,response); invoiceModel(session,model,id); return "payment/invoice";
        }
    }
    @ExceptionHandler(ResponseStatusException.class)
    String access(ResponseStatusException error, Model model, HttpServletResponse response) {
        response.setStatus(error.getStatusCode().value());
        model.addAttribute("error",error.getReason() == null ? "Payment operation is not permitted." : error.getReason());
        return "payment/error";
    }
    @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class})
    String invalid(RuntimeException error, Model model, HttpServletResponse response) {
        response.setStatus(400); model.addAttribute("error","Invalid payment information. Check the form and try again.");
        return "payment/error";
    }
    @ExceptionHandler(StorageException.class)
    String storage(StorageException error, Model model, HttpServletResponse response) {
        response.setStatus(503); model.addAttribute("error",error.getMessage()); return "payment/error";
    }
}
