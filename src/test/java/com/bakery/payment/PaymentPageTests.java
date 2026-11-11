package com.bakery.payment;

import com.bakery.payment.model.Payment.*;
import com.bakery.payment.service.*;
import com.bakery.payment.service.PaymentIntegration.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentPageTests {
    @TempDir static Path data;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("bakery.data-directory",()->data.toString());
    }
    @TestConfiguration static class Integration {
        // @Primary: these test adapters replace the real Spring Security / Order Management adapters.
        @Bean @Primary IdentityProvider testIdentity() { return s -> (Actor)s.getAttribute("testActor"); }
        @Bean @Primary CustomerDirectory testCustomers() { return id -> true; }
        @Bean @Primary PayableProvider testTargets() { return a -> List.of(new Payable(TargetType.BOOKING,"C-TEST","U001",new BigDecimal("5000"))); }
    }
    @Autowired MockMvc mvc;
    @Autowired PaymentService service;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @BeforeEach void accounts() { PaymentTestUsers.ensure(userService, users); }
    @Test void rendersAllPagesAndPrintableInvoice() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("testActor",new Actor("U001",false));
        mvc.perform(get("/payments/new").session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk()).andExpect(content().string(containsString("C-TEST")));
        mvc.perform(post("/payments").session(session).with(PaymentTestUsers.signedIn(session, users))
            .param("csrf",(String)session.getAttribute("paymentCsrf"))
            .param("type","BOOKING").param("targetId","C-TEST").param("method","BANK_TRANSFER")
            .param("purpose","ADVANCE").param("amount","1000").param("reference","test-transfer")
            .param("requestKey",UUID.randomUUID().toString())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/payments").session(session).with(PaymentTestUsers.signedIn(session, users))
            .param("csrf",(String)session.getAttribute("paymentCsrf"))
            .param("type","BOOKING").param("targetId","C-TEST").param("method","BANK_TRANSFER")
            .param("purpose","ADVANCE").param("amount","NaN").param("reference","preserved-ref")
            .param("requestKey","invalid-test-key")).andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("value=\"NaN\"")))
            .andExpect(content().string(containsString("amount-error")))
            .andExpect(content().string(containsString("preserved-ref")));
        var p = service.history(new Actor("U001",false),null,null,null).getFirst();
        mvc.perform(get("/payments").session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk()).andExpect(content().string(containsString(p.getId())));
        mvc.perform(get("/payments/" + p.getId()).session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Print invoice"))).andExpect(content().string(containsString("5000.00")));
        mvc.perform(get("/payments/" + p.getId()+"/download").session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",containsString("attachment")))
            .andExpect(content().string(containsString("NOT A PAID RECEIPT")))
            .andExpect(content().string(containsString("LKR 0.00")));
        mvc.perform(post("/payments/"+p.getId()+"/edit").session(session).with(PaymentTestUsers.signedIn(session, users))
            .param("csrf",(String)session.getAttribute("paymentCsrf")).param("amount","9999").param("reference","preserved-edit"))
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("value=\"9999\"")));
        session.setAttribute("testActor",new Actor("ADMIN",true));
        mvc.perform(get("/payments/admin").session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk()).andExpect(content().string(containsString(p.getId())));
        mvc.perform(get("/payments/" + p.getId()).session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk());
        mvc.perform(post("/payments/" + p.getId() + "/status").session(session).with(PaymentTestUsers.signedIn(session, users))
            .param("csrf",(String)session.getAttribute("paymentCsrf")).param("status","PAID").param("verified","true")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/payments/" + p.getId()).session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk());
        mvc.perform(get("/payments/" + p.getId()+"/download").session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk())
            .andExpect(content().string(containsString("PAID RECEIPT")))
            .andExpect(content().string(containsString("LKR 1,000.00")))
            .andExpect(content().string(containsString("LKR 4,000.00")));
        session.setAttribute("testActor",new Actor("U002",false));
        mvc.perform(get("/payments/" + p.getId()).session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isNotFound());
        session.removeAttribute("testActor");
        // Not signed in: Spring Security sends the visitor to the sign-in page.
        mvc.perform(get("/payments/new").session(new MockHttpSession()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }
}