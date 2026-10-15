package com.bakery.payment;

import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.*;
import com.bakery.payment.repository.PaymentRepository;
import com.bakery.payment.service.*;
import com.bakery.payment.service.PaymentIntegration.*;
import com.bakery.payment.controller.PaymentController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentTests {
    @TempDir Path directory;
    PaymentRepository repository;
    PaymentService service;
    Actor customer = new Actor("U001", false), other = new Actor("U002", false), admin = new Actor("ADMIN", true);
    PayableProvider targets = a -> List.of(new Payable(TargetType.ORDER,"O001","U001", money("3000")),
        new Payable(TargetType.BOOKING,"C001","U001",money("5000")));
    static BigDecimal money(String value) { return new BigDecimal(value); }
    @BeforeEach void setup() {
        repository = new PaymentRepository(directory.toString());
        service = new PaymentService(repository, targets);
    }
    Payment create(TargetType type, Purpose purpose, String amount, Method method, String reference) {
        return service.create(customer,type,type == TargetType.ORDER ? "O001" : "C001",method,purpose,
            money(amount),reference,UUID.randomUUID().toString());
    }
    @Test void createHistoryInvoiceAndRestartPersistence() {
        Payment p = create(TargetType.ORDER,Purpose.FULL,"3000",Method.CARD,"terminal-1");
        assertEquals(Status.PENDING,p.getStatus());
        assertEquals("INV-" + p.getId(),p.getInvoiceId());
        assertTrue(p.processPayment().contains("no gateway"));
        PaymentService restarted = new PaymentService(new PaymentRepository(directory.toString()),targets);
        assertEquals(p.getId(),restarted.history(customer,p.getTargetId(),Method.CARD,Status.PENDING).getFirst().getId());
        assertEquals(money("3000.00"),restarted.get(customer,p.getId()).getAmount());
    }
    @Test void statusesAndDeleteRules() {
        Payment p = create(TargetType.ORDER,Purpose.FULL,"3000",Method.CASH,"cash");
        assertThrows(IllegalArgumentException.class,()->service.transition(customer,p.getId(),Status.PAID));
        service.transition(admin,p.getId(),Status.PAID);
        assertThrows(IllegalArgumentException.class,()->service.delete(admin,p.getId()));
        assertThrows(IllegalArgumentException.class,()->service.transition(admin,p.getId(),Status.CANCELLED));
        service.transition(admin,p.getId(),Status.REFUNDED);
        assertEquals(money("3000.00"),service.remaining(customer,p.getId()));
        assertThrows(IllegalArgumentException.class,()->service.delete(admin,p.getId()));
        Payment next = create(TargetType.ORDER,Purpose.FULL,"3000",Method.CASH,"cash-next");
        service.transition(customer,next.getId(),Status.CANCELLED);
        assertThrows(ResponseStatusException.class,()->service.delete(customer,next.getId()));
        service.delete(admin,next.getId());
        assertEquals(1,service.history(admin,null,null,null).size());
    }
    @Test void advanceAndRemainingBalance() {
        Payment advance = create(TargetType.BOOKING,Purpose.ADVANCE,"1500",Method.BANK_TRANSFER,"bank-1");
        assertEquals(money("5000.00"),service.remaining(customer,advance.getId()));
        service.edit(customer,advance.getId(),money("2000"),"bank-1-edit");
        service.transition(admin,advance.getId(),Status.PAID);
        assertEquals(money("3000.00"),service.remaining(customer,advance.getId()));
        Payment finalPayment = create(TargetType.BOOKING,Purpose.FULL,"3000",Method.CASH,"cash-2");
        service.transition(admin,finalPayment.getId(),Status.PAID);
        assertEquals(money("0.00"),service.remaining(customer,finalPayment.getId()));
    }
    @Test void invalidAmountsReferencesAndDuplicateSubmission() {
        for (String amount : List.of("-1","0","3000.001","4000","1")) {
            assertThrows(RuntimeException.class,()->create(TargetType.ORDER,Purpose.FULL,amount,Method.CASH,"cash"));
        }
        assertThrows(IllegalArgumentException.class,()->create(TargetType.ORDER,Purpose.ADVANCE,"100",Method.CASH,"cash"));
        assertThrows(IllegalArgumentException.class,()->create(TargetType.BOOKING,Purpose.ADVANCE,"5000",Method.CASH,"cash"));
        assertThrows(IllegalArgumentException.class,()->create(TargetType.ORDER,Purpose.FULL,"3000",Method.CARD,"bad|ref"));
        Payment p = create(TargetType.ORDER,Purpose.FULL,"3000",Method.CARD,"same-ref");
        assertThrows(IllegalArgumentException.class,()->create(TargetType.ORDER,Purpose.FULL,"3000",Method.CASH,"cash"));
        service.transition(customer,p.getId(),Status.CANCELLED);
        assertThrows(IllegalArgumentException.class,()->create(TargetType.ORDER,Purpose.FULL,"3000",Method.CARD,"SAME-REF"));
        assertThrows(IllegalArgumentException.class,()->service.create(customer,TargetType.ORDER,"O001",Method.CASH,
            Purpose.FULL,money("3000"),"cash",p.getRequestKey()));
    }
    @Test void ownershipAppliesToEveryOperation() {
        Payment p = create(TargetType.ORDER,Purpose.FULL,"3000",Method.CASH,"cash");
        assertTrue(service.history(other,null,null,null).isEmpty());
        assertTrue(service.targets(other).isEmpty());
        assertThrows(ResponseStatusException.class,()->service.get(other,p.getId()));
        assertThrows(ResponseStatusException.class,()->service.remaining(other,p.getId()));
        assertThrows(ResponseStatusException.class,()->service.edit(other,p.getId(),money("3000"),"new"));
        assertThrows(ResponseStatusException.class,()->service.transition(other,p.getId(),Status.CANCELLED));
        assertThrows(IllegalArgumentException.class,()->service.create(other,TargetType.ORDER,"O001",Method.CASH,
            Purpose.FULL,money("3000"),"cash","key"));
        assertThrows(ResponseStatusException.class,()->service.history(null,null,null,null));
    }
    @Test void malformedStorageIsNotOverwritten() throws Exception {
        Files.writeString(directory.resolve("payments.txt"),"unparseable record");
        assertThrows(PaymentRepository.StorageException.class,()->create(TargetType.ORDER,Purpose.FULL,"3000",Method.CASH,"cash"));
        assertEquals("unparseable record",Files.readString(directory.resolve("payments.txt")));
    }
    @Test void concurrentRequestsCannotOverpay() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> action = () -> {
                try { create(TargetType.ORDER,Purpose.FULL,"3000",Method.CASH,"cash"); return true; }
                catch (IllegalArgumentException ex) { return false; }
            };
            var results = pool.invokeAll(List.of(action,action));
            assertEquals(1,results.stream().filter(f -> { try { return f.get(); } catch(Exception ex) { throw new RuntimeException(ex); } }).count());
        }
    }
    @Test void controllerEnforcesCsrfAdminAndOwnership() throws Exception {
        Payment p = create(TargetType.ORDER,Purpose.FULL,"3000",Method.CASH,"cash");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service,
            s -> (Actor)s.getAttribute("testActor"))).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("testActor",customer);
        mvc.perform(get("/payments/admin").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/payments/" + p.getId() + "/status").session(session)
            .param("csrf","wrong").param("status","CANCELLED")).andExpect(status().isForbidden());
        mvc.perform(get("/payments").session(session)).andExpect(status().isOk());
        String csrf = (String)session.getAttribute("paymentCsrf");
        mvc.perform(post("/payments/" + p.getId() + "/status").session(session)
            .param("csrf",csrf).param("status","PAID")).andExpect(status().isBadRequest());
        session.setAttribute("testActor",other);
        mvc.perform(get("/payments/" + p.getId()).session(session)).andExpect(status().isNotFound());
    }
}