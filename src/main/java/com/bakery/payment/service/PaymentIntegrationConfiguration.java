package com.bakery.payment.service;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import java.util.List;

/** Fail-closed defaults until the owning modules supply their integration beans. */
@AutoConfiguration
public class PaymentIntegrationConfiguration {
    @Bean @ConditionalOnMissingBean(PaymentIntegration.IdentityProvider.class)
    PaymentIntegration.IdentityProvider paymentIdentity() { return session -> null; }
    @Bean @ConditionalOnMissingBean(PaymentIntegration.PayableProvider.class)
    PaymentIntegration.PayableProvider paymentTargets() { return actor -> List.of(); }
}