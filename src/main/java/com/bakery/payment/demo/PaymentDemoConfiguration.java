package com.bakery.payment.demo;

import com.bakery.payment.model.Payment.TargetType;
import com.bakery.payment.service.PaymentIntegration.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import java.math.BigDecimal;
import java.util.*;

/**
 * Fictional integration adapters only for the explicitly selected local payment-demo profile.
 * The demo replaces the real sign-in for {@code /payments/**} only (Spring Security CSRF stays on),
 * accepts requests from this computer only, and must use file storage so its fictional records can
 * never be written to MongoDB.
 */
@Configuration
@Profile("payment-demo")
public class PaymentDemoConfiguration {
    public static final String SESSION_ACTOR = "paymentDemoActor";
    private static final Map<String,Actor> ACTORS = Map.of(
        "customer1",new Actor("DEMO-U001",false),
        "customer2",new Actor("DEMO-U002",false),
        "admin",new Actor("DEMO-ADMIN",true));
    public static Actor selected(String name) { return ACTORS.get(name); }
    public static Actor current(HttpSession session) {
        Object saved = session.getAttribute(SESSION_ACTOR);
        if (saved instanceof Actor actor && ACTORS.containsValue(actor)) return actor;
        Actor actor = ACTORS.get("customer1");
        session.setAttribute(SESSION_ACTOR,actor);
        return actor;
    }
    @Bean IdentityProvider demoPaymentIdentity() { return PaymentDemoConfiguration::current; }
    @Bean PayableProvider demoPaymentTargets() {
        // Quoted fictional amounts, not prices inferred from another member's data files.
        List<Payable> targets = List.of(
            new Payable(TargetType.ORDER,"DEMO-O001","DEMO-U001",new BigDecimal("5000.00")),
            new Payable(TargetType.BOOKING,"DEMO-B001","DEMO-U001",new BigDecimal("8500.00")),
            new Payable(TargetType.ORDER,"DEMO-O002","DEMO-U002",new BigDecimal("3000.00")),
            new Payable(TargetType.BOOKING,"DEMO-B002","DEMO-U002",new BigDecimal("7000.00")));
        return actor -> targets.stream().filter(t -> actor.admin() || t.customerId().equals(actor.customerId())).toList();
    }
    @Bean CustomerDirectory demoPaymentCustomers() {
        return id -> Set.of("DEMO-U001","DEMO-U002").contains(id);
    }
    /** Demo personas instead of a real sign-in, for the payment pages only. Other pages keep the normal rules. */
    @Bean @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain paymentDemoSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher("/payments", "/payments/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
    @Bean FilterRegistrationBean<jakarta.servlet.Filter> localDemoGuard(
            @Value("${server.address:}") String address, @Value("${bakery.storage.type:file}") String storage) {
        if (!"127.0.0.1".equals(address))
            throw new IllegalStateException("payment-demo requires server.address=127.0.0.1");
        if (!"file".equalsIgnoreCase(storage.trim()))
            throw new IllegalStateException("payment-demo requires bakery.storage.type=file (demo records never go to MongoDB)");
        FilterRegistrationBean<jakarta.servlet.Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter((request,response,chain) -> {
            HttpServletRequest req = (HttpServletRequest)request;
            boolean local = Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(req.getRemoteAddr());
            boolean host = Set.of("localhost","127.0.0.1","[::1]","::1").contains(req.getServerName().toLowerCase(Locale.ROOT));
            if (!local || !host) {
                ((HttpServletResponse)response).sendError(403,"Payment demo is available only on this computer.");
                return;
            }
            chain.doFilter(request,response);
        });
        registration.addUrlPatterns("/payments","/payments/*");
        registration.setOrder(-100);
        return registration;
    }
}