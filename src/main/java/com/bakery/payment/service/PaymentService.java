package com.bakery.payment.service;

import com.bakery.payment.model.Payment;
import com.bakery.payment.model.PaymentValidationException;
import com.bakery.payment.model.Payment.*;
import com.bakery.payment.repository.PaymentRepository;
import com.bakery.payment.service.PaymentIntegration.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class PaymentService {
    private final PaymentRepository repository;
    private final PayableProvider targets;
    private CustomerDirectory customers;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCustomerDirectory(CustomerDirectory customers) { this.customers = customers; }
    private void checkCustomer(String id) {
        if (id == null || id.isBlank())
            throw new PaymentValidationException("targetId", "Customer ID is required.");
        if (customers != null && !customers.exists(id))
            throw new PaymentValidationException("targetId", "Customer account is unavailable.");
    }
    private Payable payable(Actor actor, TargetType type, String id) {
        if (type == null || id == null || id.isBlank())
            throw new PaymentValidationException("targetId", "Please select a valid order or booking.");
        Payable result = targets(actor).stream().filter(t -> t.type() == type && t.id().equals(id.trim()))
            .findFirst().orElseThrow(() -> new PaymentValidationException("targetId",
                "Order or booking is unavailable, cancelled or does not belong to this customer."));
        checkCustomer(result.customerId());
        return result;
    }
    private void currentTarget(Actor actor, Payment payment) {
        Payable current = payable(actor, payment.getTargetType(), payment.getTargetId());
        if (!current.customerId().equals(payment.getCustomerId()) || current.total().compareTo(payment.getTotal()) != 0)
            throw new PaymentValidationException("targetId", "Order details changed. Contact an administrator before continuing.");
    }
    public record TargetOption(TargetType type, String id, BigDecimal total, BigDecimal remaining) {}
    public List<TargetOption> targetOptions(Actor actor) {
        List<Payable> available = targets(actor);
        return repository.transaction(list -> available.stream().map(t -> new TargetOption(
            t.type(), t.id(), t.total(), balance(list,t.type(),t.id(),t.total(),true,null))).toList(), false);
    }
    public BigDecimal availableForEdit(Actor actor, String id) {
        return repository.transaction(list -> {
            Payment p = owned(list,actor,id);
            return balance(list,p.getTargetType(),p.getTargetId(),p.getTotal(),true,id);
        }, false);
    }
    public PaymentService(PaymentRepository repository, PayableProvider targets) {
        this.repository = repository; this.targets = targets;
    }
    public List<Payable> targets(Actor actor) {
        requireActor(actor);
        return targets.availableTo(actor).stream()
            .filter(t -> actor.admin() || t.customerId().equals(actor.customerId())).toList();
    }
    public List<Payment> history(Actor actor, String query, Method method, Status status) {
        requireActor(actor);
        return repository.transaction(list -> list.stream()
            .filter(p -> actor.admin() || p.getCustomerId().equals(actor.customerId()))
            .filter(p -> query == null || query.isBlank() ||
                p.getId().equalsIgnoreCase(query.trim()) || p.getTargetId().equalsIgnoreCase(query.trim()))
            .filter(p -> method == null || p.getMethod() == method)
            .filter(p -> status == null || p.getStatus() == status)
            .sorted(Comparator.comparing(Payment::getCreatedAt).reversed()).toList(), false);
    }
    public Payment get(Actor actor, String id) {
        return repository.transaction(list -> owned(list, actor, id), false);
    }
    public Payment create(Actor actor, TargetType type, String targetId, Method method,
            Purpose purpose, BigDecimal amount, String reference, String key) {
        requireActor(actor);
        if (method == null) throw new PaymentValidationException("method", "Please select a payment method.");
        Payable target = payable(actor,type,targetId);
        return repository.transaction(list -> {
            String normalizedKey = Payment.text(key);
            if (list.stream().anyMatch(p -> p.getRequestKey().equals(normalizedKey)))
                throw new IllegalArgumentException("This form has already been submitted");
            BigDecimal remaining = balance(list, target.type(), target.id(), target.total(), true, null);
            validateAmount(type, purpose, amount, remaining);
            String id = repository.nextId();
            String normalizedReference = method == Method.CASH && (reference == null || reference.isBlank())
                ? "CASH-" + id : Payment.reference(reference);
            uniqueReference(list, method, normalizedReference, null);
            Payment p = Payment.create(method, id, type, target.id(),
                target.customerId(), target.total(), amount, purpose, Status.PENDING,
                normalizedReference, normalizedKey, Instant.now());
            p.processPayment(); // Dynamic dispatch; every strategy records a manual pending payment.
            list.add(p); return p;
        }, true);
    }
    private void referenceForEdit(Payment payment, String reference) {
        if (payment.getMethod() != Method.CASH || reference != null && !reference.isBlank()) Payment.reference(reference);
    }
    public void edit(Actor actor, String id, BigDecimal amount, String reference) {
        repository.transaction(list -> {
            Payment p = owned(list, actor, id);
            if (p.getStatus() != Status.PENDING) throw new IllegalArgumentException("Only pending payments can be edited");
            currentTarget(actor, p);
            validateAmount(p.getTargetType(), p.getPurpose(), amount,
                balance(list, p.getTargetType(), p.getTargetId(), p.getTotal(), true, id));
            referenceForEdit(p, reference);
            String normalizedReference = p.getMethod() == Method.CASH && (reference == null || reference.isBlank())
                ? p.getReference() : Payment.reference(reference);
            uniqueReference(list, p.getMethod(), normalizedReference, id);
            p.setAmount(amount); p.setReference(normalizedReference); return null;
        }, true);
    }
    public void transition(Actor actor, String id, Status next) {
        repository.transaction(list -> {
            Payment p = owned(list, actor, id);
            if (next == Status.PAID) currentTarget(actor, p);
            if (next == Status.PAID && p.getAmount().compareTo(
                balance(list, p.getTargetType(), p.getTargetId(), p.getTotal(), true, id)) > 0)
                throw new IllegalArgumentException("Payment exceeds remaining balance");
            p.transition(next, actor.admin()); return null;
        }, true);
    }
    public void delete(Actor actor, String id) {
        requireAdmin(actor);
        repository.transaction(list -> {
            Payment p = owned(list, actor, id);
            if (p.getStatus() != Status.FAILED && p.getStatus() != Status.CANCELLED)
                throw new IllegalArgumentException(p.getStatus() == Status.PAID ? "Paid payments cannot be deleted."
                    : p.getStatus() == Status.REFUNDED ? "Refunded payments cannot be deleted."
                    : "Only Failed or Cancelled payments can be deleted.");
            list.remove(p); return null;
        }, true);
    }
    public BigDecimal remaining(Actor actor, String id) {
        return repository.transaction(list -> {
            Payment p = owned(list, actor, id);
            return balance(list, p.getTargetType(), p.getTargetId(), p.getTotal(), false, null);
        }, false);
    }
    private BigDecimal balance(List<Payment> list, TargetType type, String id, BigDecimal total,
            boolean reservePending, String excluded) {
        return total.subtract(list.stream().filter(p -> p.getTargetType() == type && p.getTargetId().equals(id))
            .filter(p -> !p.getId().equals(excluded))
            .filter(p -> p.getStatus() == Status.PAID || reservePending && p.getStatus() == Status.PENDING)
            .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).max(BigDecimal.ZERO).setScale(2);
    }
    private void validateAmount(TargetType type, Purpose purpose, BigDecimal amount, BigDecimal remaining) {
        BigDecimal value = Payment.money(amount);
        if (purpose == null) throw new PaymentValidationException("purpose", "Please select a payment purpose.");
        if (value.compareTo(remaining) > 0)
            throw new PaymentValidationException("amount", "Amount cannot exceed the remaining balance (pending payments reserve funds).");
        if (purpose == Purpose.ADVANCE && (type != TargetType.BOOKING || value.compareTo(remaining) >= 0))
            throw new PaymentValidationException("amount", "An advance must be less than the remaining custom cake balance.");
        if (purpose == Purpose.FULL && value.compareTo(remaining) != 0)
            throw new PaymentValidationException("amount", "Full payment must equal the remaining balance. Select Partial Payment for a smaller amount.");
    }
    private void uniqueReference(List<Payment> list, Method method, String reference, String excluded) {
        String normalized = Payment.reference(reference);
        if (list.stream().anyMatch(p -> !p.getId().equals(excluded)
                && p.getReference().equalsIgnoreCase(normalized)))
            throw new PaymentValidationException("reference", "Transaction reference already exists.");
    }
    private Payment owned(List<Payment> list, Actor actor, String id) {
        requireActor(actor);
        return list.stream().filter(p -> p.getId().equals(id))
            .filter(p -> actor.admin() || p.getCustomerId().equals(actor.customerId()))
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found."));
    }
    public void requireActor(Actor actor) {
        if (actor != null && !actor.admin()) checkCustomer(actor.customerId());
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in through User Management");
    }
    public void requireAdmin(Actor actor) {
        requireActor(actor);
        if (!actor.admin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to perform this payment operation.");
    }
}