package com.bakery.payment;

import com.bakery.payment.model.Payment.*;
import com.bakery.payment.service.PaymentService;
import com.bakery.payment.service.PaymentIntegration.Actor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockHttpSession;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("payment-demo")
class PaymentDemoTests {
    @TempDir static Path directory;
    @DynamicPropertySource static void data(DynamicPropertyRegistry r) {r.add("bakery.data-directory",()->directory.toString());}
    @Autowired MockMvc mvc;
    @Autowired PaymentService service;
    MockHttpSession session;
    Actor customer=new Actor("DEMO-U001",false), admin=new Actor("DEMO-ADMIN",true);
    @BeforeEach void prepare() throws Exception {
        Files.deleteIfExists(directory.resolve("payments.txt"));
        session=new MockHttpSession();
        mvc.perform(get("/payments").session(session)).andExpect(status().isOk())
            .andExpect(content().string(containsString("LOCAL DEMO")))
            .andExpect(content().string(containsString("Make Payment")));
    }
    String csrf() {return (String)session.getAttribute("paymentCsrf");}
    void switchTo(String persona) throws Exception {
        mvc.perform(post("/payments/demo/session").session(session).param("csrf",csrf()).param("persona",persona))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/payments").session(session)).andExpect(status().isOk());
    }
    String record(String target, String type, String purpose, String amount) throws Exception {
        mvc.perform(post("/payments").session(session).param("csrf",csrf())
            .param("type",type).param("targetId",target).param("purpose",purpose)
            .param("amount",amount).param("method","CASH").param("requestKey",UUID.randomUUID().toString()))
            .andExpect(status().is3xxRedirection());
        return service.history(customer,null,null,null).getFirst().getId();
    }
    @Test void fullPaymentConfirmationRefundAndInvoiceAreClearlyDemo() throws Exception {
        mvc.perform(get("/payments/new").session(session)).andExpect(status().isOk())
            .andExpect(content().string(containsString("DEMO-O001")))
            .andExpect(content().string(not(containsString("DEMO-O002"))));
        String id=record("DEMO-O001","ORDER","FULL","5000");
        mvc.perform(post("/payments/"+id+"/status").session(session).param("csrf",csrf()).param("status","PAID"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/payments/admin").session(session)).andExpect(status().isForbidden());
        switchTo("admin");
        mvc.perform(get("/payments/admin").session(session)).andExpect(status().isOk())
            .andExpect(content().string(containsString(id)));
        mvc.perform(post("/payments/"+id+"/status").session(session).param("csrf",csrf()).param("status","PAID"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/payments/"+id+"/download").session(session)).andExpect(status().isOk())
            .andExpect(content().string(containsString("LOCAL DEMO - FICTIONAL PAYMENT RECORD")))
            .andExpect(content().string(containsString("LKR 5,000.00")));
        mvc.perform(post("/payments/"+id+"/delete").session(session).param("csrf",csrf()).param("confirmed","true"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/payments/"+id+"/status").session(session).param("csrf",csrf()).param("status","REFUNDED"))
            .andExpect(status().is3xxRedirection());
        assertEquals(Status.REFUNDED,service.get(admin,id).getStatus());
    }
    @Test void advanceEditingOwnershipCancellationAndDeletionWork() throws Exception {
        String id=record("DEMO-B001","BOOKING","ADVANCE","2000");
        mvc.perform(post("/payments/"+id+"/edit").session(session).param("csrf",csrf())
            .param("amount","2500").param("reference","DEMO-CASH-EDIT")).andExpect(status().is3xxRedirection());
        switchTo("customer2");
        mvc.perform(get("/payments").session(session)).andExpect(status().isOk())
            .andExpect(content().string(not(containsString(id))));
        mvc.perform(get("/payments/"+id).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/payments/new").session(session)).andExpect(status().isOk())
            .andExpect(content().string(containsString("DEMO-O002")))
            .andExpect(content().string(not(containsString("DEMO-O001"))));
        switchTo("customer1");
        mvc.perform(post("/payments/"+id+"/status").session(session).param("csrf",csrf()).param("status","CANCELLED"))
            .andExpect(status().is3xxRedirection());
        switchTo("admin");
        mvc.perform(get("/payments/"+id).session(session)).andExpect(status().isOk())
            .andExpect(content().string(containsString("payment-delete-dialog")));
        mvc.perform(post("/payments/"+id+"/delete").session(session).param("csrf",csrf()).param("confirmed","true"))
            .andExpect(status().is3xxRedirection());
        assertTrue(service.history(admin,null,null,null).isEmpty());
    }
    @Test void depositBalanceAndPersistentDemoFile() throws Exception {
        String id=record("DEMO-B001","BOOKING","ADVANCE","2000");
        switchTo("admin");
        mvc.perform(post("/payments/"+id+"/status").session(session).param("csrf",csrf()).param("status","PAID"))
            .andExpect(status().is3xxRedirection());
        assertEquals(new BigDecimal("6500.00"),service.remaining(customer,id));
        var fresh=new PaymentService(new com.bakery.payment.repository.PaymentRepository(directory.toString()),actor->java.util.List.of());
        assertEquals(new BigDecimal("2000.00"),fresh.get(customer,id).getAmount());
    }
    @Test void roleSwitchNeedsCsrfAndOnlyLocalRequestsAreAccepted() throws Exception {
        mvc.perform(post("/payments/demo/session").session(session).param("csrf","wrong").param("persona","admin"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/payments/demo/session").session(session).param("csrf",csrf()).param("persona","invented"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/payments").with(req->{req.setRemoteAddr("203.0.113.20");return req;}))
            .andExpect(status().isForbidden());
        mvc.perform(get("/payments").header("Host","attacker.example")
            .with(req->{req.setServerName("attacker.example");return req;})).andExpect(status().isForbidden());
    }
}