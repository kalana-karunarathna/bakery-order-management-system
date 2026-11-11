package com.bakery.payment;

import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.*;
import com.bakery.payment.service.*;
import com.bakery.payment.service.PaymentIntegration.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockHttpSession;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Thymeleaf renders with trusted test adapters; output is only in ignored target/. */
@SpringBootTest(properties="logging.level.root=WARN")
@AutoConfigureMockMvc
class PaymentUiRenderingTests {
    @TempDir static Path data;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry r) { r.add("bakery.data-directory",()->data.toString()); }
    @TestConfiguration static class Integrations {
        // @Primary: these test adapters replace the real Spring Security / Order Management adapters.
        @Bean @Primary IdentityProvider uiIdentity() { return s -> (Actor)s.getAttribute("testActor"); }
        @Bean @Primary CustomerDirectory uiCustomers() { return id -> true; }
        @Bean @Primary PayableProvider uiTargets() {
            return a -> java.util.stream.IntStream.range(0,12).mapToObj(i ->
                new Payable(TargetType.BOOKING,"CAKE-"+(101+i),"U001",new BigDecimal("5000"))).toList();
        }
    }
    @Autowired MockMvc mvc;
    @Autowired PaymentService service;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @BeforeEach void accounts() { PaymentTestUsers.ensure(userService, users); }
    void snapshot(String filename, MvcResult result) throws Exception {
        Path output=Path.of("target","payment-ui-preview");Files.createDirectories(output);
        Files.writeString(output.resolve(filename+".html"),result.getResponse().getContentAsString());
    }
    @Test void rendersCustomerAdminStatesAndSummaryUsingActualRecords() throws Exception {
        Actor customer=new Actor("U001",false), admin=new Actor("ADMIN",true);
        MockHttpSession session=new MockHttpSession();session.setAttribute("testActor",customer);
        // Visitors are sent to sign in; a signed-in account without a payment identity sees the welcome panel.
        mvc.perform(get("/payments")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        snapshot("anonymous",mvc.perform(get("/payments").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .user(new com.bakery.user.security.BakeryUserPrincipal(users.findById("U001").orElseThrow()))))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Sign in with an active account"))).andReturn());
        snapshot("empty",mvc.perform(get("/payments").session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk()).andReturn());
        List<Payment> records=new ArrayList<>();
        for(int i=0;i<12;i++){
            Payment p=service.create(customer,TargetType.BOOKING,"CAKE-"+(101+i),Method.CASH,Purpose.ADVANCE,
                new BigDecimal("1000"),null,"ui-test-"+i);records.add(p);
            switch(i%5){
                case 1 -> service.transition(admin,p.getId(),Status.PAID);
                case 2 -> service.transition(admin,p.getId(),Status.FAILED);
                case 3 -> service.transition(customer,p.getId(),Status.CANCELLED);
                case 4 -> {service.transition(admin,p.getId(),Status.PAID);service.transition(admin,p.getId(),Status.REFUNDED);}
                default -> {}
            }
        }
        snapshot("make",mvc.perform(get("/payments/new").session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk())
            .andExpect(content().string(containsString("data-paid=\"1000.00\"")))
            .andExpect(content().string(containsString("data-pending=\"1000.00\""))).andReturn());
        snapshot("history",mvc.perform(get("/payments").session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk())
            .andExpect(content().string(containsString("status-refunded"))).andReturn());
        snapshot("filtered",mvc.perform(get("/payments").session(session).with(PaymentTestUsers.signedIn(session, users)).param("query",records.get(1).getId())
            .param("method","CASH").param("status","PAID")).andExpect(status().isOk())
            .andExpect(content().string(containsString(records.get(1).getId())))
            .andExpect(content().string(not(containsString(records.get(0).getId())))).andReturn());
        snapshot("pending",mvc.perform(get("/payments/"+records.get(0).getId()).session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk()).andReturn());
        snapshot("invoice",mvc.perform(get("/payments/"+records.get(1).getId()).session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk()).andReturn());
        snapshot("download",mvc.perform(get("/payments/"+records.get(1).getId()+"/download").session(session).with(PaymentTestUsers.signedIn(session, users)))
            .andExpect(status().isOk()).andExpect(content().string(not(containsString("data-payment-form")))).andReturn());
        snapshot("invalid",mvc.perform(post("/payments").session(session).with(PaymentTestUsers.signedIn(session, users))
            .param("csrf",(String)session.getAttribute("paymentCsrf")).param("type","BOOKING").param("targetId","CAKE-101")
            .param("purpose","ADVANCE").param("method","CASH").param("amount","NaN").param("requestKey","invalid-ui"))
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("is-invalid"))).andReturn());
        session.setAttribute("testActor",admin);
        snapshot("admin",mvc.perform(get("/payments/admin").session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk())
            .andExpect(content().string(containsString("LKR 3,000.00"))).andReturn());
        snapshot("delete",mvc.perform(get("/payments/"+records.get(3).getId()).session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isOk())
            .andExpect(content().string(containsString("payment-delete-dialog"))).andReturn());
        snapshot("error",mvc.perform(get("/payments/NOT-FOUND").session(session).with(PaymentTestUsers.signedIn(session, users))).andExpect(status().isNotFound()).andReturn());
    }
}
