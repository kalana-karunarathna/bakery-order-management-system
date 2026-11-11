package com.bakery.payment;

import com.bakery.payment.demo.PaymentDemoConfiguration;
import com.bakery.payment.demo.PaymentDemoController;
import com.bakery.payment.service.PaymentIntegration.IdentityProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpSession;
import static org.junit.jupiter.api.Assertions.*;

/** The demo must not weaken the application's normal fail-closed behavior. */
@SpringBootTest
class PaymentDemoIsolationTests {
    @Autowired ApplicationContext context;
    @Autowired IdentityProvider identity;
    @Test void demoBeansDoNotExistWithoutExplicitProfile() {
        assertTrue(context.getBeansOfType(PaymentDemoConfiguration.class).isEmpty());
        assertTrue(context.getBeansOfType(PaymentDemoController.class).isEmpty());
        assertNull(identity.current(new MockHttpSession()));
    }
}