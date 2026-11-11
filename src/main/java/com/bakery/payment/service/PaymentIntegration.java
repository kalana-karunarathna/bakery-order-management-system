package com.bakery.payment.service;

import com.bakery.payment.model.Payment.TargetType;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.List;

/** Ports to be implemented by the module owners; no other module's files are parsed here. */
public final class PaymentIntegration {
    private PaymentIntegration() {}
    public record Actor(String customerId, boolean admin) {
        public Actor { customerId = com.bakery.payment.model.Payment.text(customerId); }
    }
    public record Payable(TargetType type, String id, String customerId, BigDecimal total) {
        public Payable {
            if (type == null) throw new IllegalArgumentException("Missing target type");
            id = com.bakery.payment.model.Payment.text(id);
            customerId = com.bakery.payment.model.Payment.text(customerId);
            total = com.bakery.payment.model.Payment.money(total);
        }
    }
    /** Matheesha: resolve only a server-authenticated session, never request fields. */
    /** Optional live-user lookup. The user module supplies this when available. */
    public interface CustomerDirectory { boolean exists(String customerId); }
    public interface IdentityProvider { Actor current(HttpSession session); }
    /** Kalana/Salila: return authoritative IDs, owner and agreed total, excluding cancelled targets. */
    public interface PayableProvider { List<Payable> availableTo(Actor actor); }
}