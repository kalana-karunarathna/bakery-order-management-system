package com.bakery.payment;

import com.bakery.payment.controller.PaymentController;
import com.bakery.payment.model.*;
import com.bakery.payment.model.Payment.*;
import com.bakery.payment.repository.PaymentRepository;
import com.bakery.payment.service.*;
import com.bakery.payment.service.PaymentIntegration.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentValidationTests {
    @TempDir Path data;
    PaymentService service;
    MockMvc mvc;
    MockHttpSession session;
    Actor customer = new Actor("U001",false), admin = new Actor("ADMIN",true);
    @BeforeEach void setup() throws Exception {
        service = new PaymentService(new PaymentRepository(data.toString()), a -> List.of(
            new Payable(TargetType.ORDER,"O001","U001",new BigDecimal("5000")),
            new Payable(TargetType.BOOKING,"B001","U001",new BigDecimal("5000"))));
        mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service,s -> (Actor)s.getAttribute("testActor"))).build();
        session = new MockHttpSession(); session.setAttribute("testActor",customer);
        mvc.perform(get("/payments/new").session(session)).andExpect(status().isOk());
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path) {
        return post(path).session(session).param("csrf",(String)session.getAttribute("paymentCsrf"));
    }
    Payment create(String amount, String reference) {
        return service.create(customer,TargetType.ORDER,"O001",Method.CASH,Purpose.PARTIAL,
            new BigDecimal(amount),reference,UUID.randomUUID().toString());
    }
    @ParameterizedTest
    @ValueSource(strings={"", "-1", "0", "5000.01", "1.001", "1.000", "NaN", "Infinity", "hello", "1e3", "999999999999999999999999999999999999"})
    void invalidAmountsPreserveInputAndDoNotWrite(String amount) throws Exception {
        mvc.perform(request("/payments").param("type","ORDER").param("targetId","O001")
            .param("method","CASH").param("purpose","PARTIAL").param("amount",amount)
            .param("requestKey","test-key")).andExpect(status().isBadRequest())
            .andExpect(view().name("payment/make"))
            .andExpect(model().attribute("errors",hasKey("amount")))
            .andExpect(model().attribute("form",hasEntry("amount",amount)));
        assertFalse(Files.exists(data.resolve("payments.txt")));
    }
    @ParameterizedTest
    @CsvSource(value={"method,","method,PAYPAL","type,OTHER","targetId,INVALID","purpose,INVALID"})
    void rejectsMissingOrInvalidDropdownAndTarget(String field, String value) throws Exception {
        Map<String,String> values = new HashMap<>(Map.of("type","ORDER","targetId","O001","method","CASH",
            "purpose","PARTIAL","amount","1000","requestKey","test-key"));
        values.put(field,value == null ? "" : value);
        var req = request("/payments"); values.forEach(req::param);
        mvc.perform(req).andExpect(status().isBadRequest()).andExpect(model().attribute("errors",not(anEmptyMap())));
        assertTrue(service.history(customer,null,null,null).isEmpty());
    }
    @Test void missingAmountAndCustomerAreRejected() throws Exception {
        mvc.perform(request("/payments").param("type","ORDER").param("targetId","O001")
            .param("method","CASH").param("purpose","PARTIAL").param("requestKey","key"))
            .andExpect(status().isBadRequest()).andExpect(model().attribute("errors",hasKey("amount")));
        assertThrows(IllegalArgumentException.class,()->new Actor("",false));
        assertThrows(IllegalArgumentException.class,()->new Payable(TargetType.ORDER,"O",null,new BigDecimal("1")));
        service.setCustomerDirectory(id -> false);
        assertThrows(PaymentValidationException.class,()->create("1000",null));
    }
    @Test void sequentialIdsSurviveDeleteAndRestartAndCashReferenceIsAutomatic() {
        Payment first=create("1000",null);
        assertEquals("PAY001",first.getId()); assertEquals("CASH-PAY001",first.getReference());
        service.transition(customer,first.getId(),Status.CANCELLED); service.delete(admin,first.getId());
        PaymentService fresh = new PaymentService(new PaymentRepository(data.toString()),
            a -> List.of(new Payable(TargetType.ORDER,"O001","U001",new BigDecimal("5000"))));
        Payment second = fresh.create(customer,TargetType.ORDER,"O001",Method.CASH,Purpose.PARTIAL,
            new BigDecimal("1000"),null,"next-key");
        assertEquals("PAY002",second.getId());
        assertEquals(1,fresh.history(customer,null,null,null).size());
    }
    @Test void partialPaymentsFollowOutstandingBalanceExample() {
        Payment first=create("2000",null); service.transition(admin,first.getId(),Status.PAID);
        assertEquals(new BigDecimal("3000.00"),service.remaining(customer,first.getId()));
        assertThrows(PaymentValidationException.class,()->create("4000",null));
        Payment next=create("1000",null); assertEquals(new BigDecimal("1000.00"),next.getAmount());
    }
    @ParameterizedTest @ValueSource(strings={"", "ab", "abc|def", "<script>", "bank\nref"})
    void invalidNoncashReferencesDoNotWrite(String reference) {
        assertThrows(IllegalArgumentException.class,()->service.create(customer,TargetType.ORDER,"O001",
            Method.CARD,Purpose.PARTIAL,new BigDecimal("100"),reference,"key"));
        assertFalse(Files.exists(data.resolve("payments.txt")));
    }
    @Test void duplicateReferencesAreTrimmedCaseInsensitiveAndAcrossMethods() {
        Payment p=service.create(customer,TargetType.ORDER,"O001",Method.CARD,Purpose.PARTIAL,
            new BigDecimal("1000"),"  REF-001  ","key-1");
        assertEquals("REF-001",p.getReference());
        assertThrows(PaymentValidationException.class,()->service.create(customer,TargetType.BOOKING,"B001",
            Method.BANK_TRANSFER,Purpose.ADVANCE,new BigDecimal("1000"),"ref-001","key-2"));
    }
    @Test void invalidUpdateLeavesFileUnchangedAndValidUpdateWorks() throws Exception {
        Payment p=create("1000",null);
        String before=Files.readString(data.resolve("payments.txt"));
        mvc.perform(request("/payments/"+p.getId()+"/edit").param("amount","6000").param("reference","edited-ref"))
            .andExpect(status().isBadRequest()).andExpect(model().attribute("form",hasEntry("amount","6000")));
        assertEquals(before,Files.readString(data.resolve("payments.txt")));
        mvc.perform(request("/payments/"+p.getId()+"/edit").param("amount","1200").param("reference","edited-ref"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("success","Payment successfully updated."));
        assertEquals(new BigDecimal("1200.00"),service.get(customer,p.getId()).getAmount());
        service.transition(admin,p.getId(),Status.PAID);
        assertThrows(IllegalArgumentException.class,()->service.edit(customer,p.getId(),new BigDecimal("1"),"new-ref"));
    }
    @Test void invalidStatusAndUnauthorizedConfirmationHaveClearErrors() throws Exception {
        Payment p=create("1000",null);
        mvc.perform(request("/payments/"+p.getId()+"/status").param("status","BOGUS"))
            .andExpect(status().isBadRequest()).andExpect(model().attribute("errors",hasEntry("status","Invalid payment status.")));
        mvc.perform(request("/payments/"+p.getId()+"/status").param("status","PAID"))
            .andExpect(status().isBadRequest()).andExpect(model().attribute("errors",hasEntry("status","Only administrators can confirm payments.")));
        assertEquals(Status.PENDING,service.get(customer,p.getId()).getStatus());
        service.transition(admin,p.getId(),Status.PAID); service.transition(admin,p.getId(),Status.REFUNDED);
        assertThrows(PaymentValidationException.class,()->service.transition(admin,p.getId(),Status.PAID));
    }
    @Test void deletingPaidIsRejectedAndCancelledRequiresConfirmation() throws Exception {
        Payment p=create("1000",null); service.transition(admin,p.getId(),Status.PAID);
        session.setAttribute("testActor",admin);
        mvc.perform(request("/payments/"+p.getId()+"/delete").param("confirmed","true"))
            .andExpect(status().isBadRequest()).andExpect(model().attribute("errors",hasEntry("_global","Paid payments cannot be deleted.")));
        Payment next=create("1000",null); service.transition(customer,next.getId(),Status.CANCELLED);
        mvc.perform(request("/payments/"+next.getId()+"/delete")).andExpect(status().isBadRequest());
        mvc.perform(request("/payments/"+next.getId()+"/delete").param("confirmed","true")).andExpect(status().is3xxRedirection());
        assertEquals(1,service.history(admin,null,null,null).size());
    }
    @Test void invalidInvoiceAndOtherCustomerAccessAreRejected() throws Exception {
        mvc.perform(get("/payments/PAY999")).andExpect(status().isUnauthorized());
        mvc.perform(get("/payments/PAY999").session(session)).andExpect(status().isNotFound());
        Payment p=create("1000",null);
        session.setAttribute("testActor",new Actor("U002",false));
        mvc.perform(get("/payments/"+p.getId()+"/download").session(session)).andExpect(status().isNotFound());
    }
    @Test void invalidFiltersDoNotCrashAndSearchIsTrimmed() throws Exception {
        Payment p=create("1000",null);
        mvc.perform(get("/payments").session(session).param("method","CRYPTO")).andExpect(status().isBadRequest());
        mvc.perform(get("/payments").session(session).param("status","INVALID")).andExpect(status().isBadRequest());
        assertEquals(1,service.history(customer,"  "+p.getId()+"  ",null,null).size());
        assertTrue(service.history(customer,"invalid",null,null).isEmpty());
    }
    @Test void missingAndEmptyFilesAndDuplicateCorruptionAreSafe() throws Exception {
        assertTrue(service.history(customer,null,null,null).isEmpty());
        Files.writeString(data.resolve("payments.txt"),"");
        assertTrue(service.history(customer,null,null,null).isEmpty());
        Payment p=create("1000",null);
        List<String> lines=Files.readAllLines(data.resolve("payments.txt"));
        String record=lines.stream().filter(l->l.startsWith("v1|")).findFirst().orElseThrow();
        Files.writeString(data.resolve("payments.txt"),Files.readString(data.resolve("payments.txt"))+record+System.lineSeparator());
        String corrupted=Files.readString(data.resolve("payments.txt"));
        assertThrows(PaymentRepository.StorageException.class,()->service.delete(admin,p.getId()));
        assertEquals(corrupted,Files.readString(data.resolve("payments.txt")));
    }
    @Test void futureDateAndNullEnumsAreRejectedAtDomainBoundary() {
        assertThrows(IllegalArgumentException.class,()->Payment.create(Method.CASH,"PAY001",TargetType.ORDER,
            "O001","U001",new BigDecimal("10"),new BigDecimal("10"),Purpose.FULL,Status.PENDING,
            "cash-ref","key",Instant.now().plusSeconds(3600)));
        assertThrows(PaymentValidationException.class,()->service.create(customer,TargetType.ORDER,"O001",null,
            Purpose.PARTIAL,new BigDecimal("1"),"cash","key"));
        Payment p=create("1000",null);
        assertThrows(PaymentValidationException.class,()->service.transition(admin,p.getId(),null));
    }
    @Test void unavailableCancelledOrChangedTargetsCannotBeConfirmed() {
        Payment p=create("1000",null);
        PaymentService unavailable = new PaymentService(new PaymentRepository(data.toString()),a -> List.of());
        assertThrows(PaymentValidationException.class,()->unavailable.transition(admin,p.getId(),Status.PAID));
        PaymentService changed = new PaymentService(new PaymentRepository(data.toString()),
            a -> List.of(new Payable(TargetType.ORDER,"O001","U001",new BigDecimal("6000"))));
        assertThrows(PaymentValidationException.class,()->changed.edit(customer,p.getId(),new BigDecimal("1000"),"ref-new"));
    }
    @Test void postedIdentityIdDateAndStatusCannotOverrideServerValues() throws Exception {
        mvc.perform(request("/payments").param("type","ORDER").param("targetId","O001").param("method","CASH")
            .param("purpose","PARTIAL").param("amount","1000").param("requestKey","test-key")
            .param("customerId","U002").param("id","PAY999").param("createdAt","2099-01-01")
            .param("status","PAID")).andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("success",containsString("Payment successfully recorded.")));
        Payment p=service.history(customer,null,null,null).getFirst();
        assertEquals("PAY001",p.getId());assertEquals("U001",p.getCustomerId());
        assertEquals(Status.PENDING,p.getStatus());assertFalse(p.getCreatedAt().isAfter(Instant.now()));
    }
    @Test void fileReadErrorsAndInvalidSequenceAreReportedWithoutOverwriting() throws Exception {
        Files.createDirectory(data.resolve("payments.txt"));
        assertThrows(PaymentRepository.StorageException.class,()->create("1000",null));
        Files.delete(data.resolve("payments.txt"));
        Files.writeString(data.resolve("payments.txt"),"# sequence=-1");
        assertThrows(PaymentRepository.StorageException.class,()->create("1000",null));
        assertEquals("# sequence=-1",Files.readString(data.resolve("payments.txt")));
    }
    @Test void originalScaffoldIdIsReservedAndPreserved() throws Exception {
        String sample="PAY001|O001|3000.00|CASH|PENDING|INV001";
        Files.writeString(data.resolve("payments.txt"),sample);
        Payment p=create("1000",null);assertEquals("PAY002",p.getId());
        assertTrue(Files.readString(data.resolve("payments.txt")).contains(sample));
        assertEquals(1,service.history(customer,null,null,null).size());
    }
}