package com.bakery.order;

import com.bakery.order.catalog.DemoProductCatalog;
import com.bakery.order.catalog.DemoProductCatalog.DemoProduct;
import com.bakery.order.model.Order;
import com.bakery.order.service.OrderPaymentSummary;
import com.bakery.order.service.OrderService;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.security.BakeryUserPrincipal;
import com.bakery.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Demo product catalog: menu content, menu pages, server-side pricing, and the Products → Order → Payment journey. */
@SpringBootTest
@AutoConfigureMockMvc
class DemoProductCatalogTests {

    private static final String PASSWORD = "Bakery2026";

    @TempDir static Path data;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("bakery.data-directory", () -> data.toString());
    }

    @Autowired MockMvc mvc;
    @Autowired DemoProductCatalog catalog;
    @Autowired OrderService orders;
    @Autowired UserService userService;
    @Autowired UserRepository users;

    private String alice;

    @BeforeEach
    void setUp() throws Exception {
        Files.deleteIfExists(data.resolve("orders.txt"));
        Files.deleteIfExists(data.resolve("payments.txt"));
        if (users.findAll().isEmpty()) {
            userService.register("Alice Perera", "alice@example.com", "077 111 1111", "1 Lake Road, Kandy", PASSWORD, PASSWORD);
        }
        alice = users.findByEmail("alice@example.com").orElseThrow().getId();
    }

    private RequestPostProcessor asAlice() {
        return user(new BakeryUserPrincipal(users.findById(alice).orElseThrow()));
    }

    @Test
    void catalogHasEightValidProductsWithImages() {
        List<DemoProduct> all = catalog.all();
        assertEquals(8, all.size());
        assertEquals(8, new HashSet<>(all.stream().map(DemoProduct::id).toList()).size(), "unique IDs");
        for (DemoProduct p : all) {
            assertTrue(p.id().matches("P1[0-9]{2}"), p.id());
            assertTrue(p.price().signum() > 0 && p.price().scale() == 2, p.id() + " price");
            assertTrue(p.name().length() >= 2 && p.name().length() <= 100 && !p.name().contains("|"), p.id() + " name");
            assertTrue(new ClassPathResource("static" + p.image()).exists(), p.image() + " exists");
        }
        assertEquals("P103", catalog.find(" p103 ").orElseThrow().id(), "lookup is trimmed and case-insensitive");
        assertTrue(catalog.find("P999").isEmpty());
        assertTrue(catalog.find(null).isEmpty());
    }

    @Test
    void orderLandingPageShowsTheMenuWithOrderNowLinks() throws Exception {
        mvc.perform(get("/orders")).andExpect(status().isOk()).andExpect(content().string(allOf(
                containsString("Today's menu"), containsString("Cherry Chocolate Layer Cake"), containsString("LKR 4,850.00"),
                containsString("href=\"/orders/place?product=P101\""), containsString("/images/bakery/product-croissant.webp"))));
    }

    @Test
    void placeOrderPageListsSelectableProductsAndPreselectsFromTheLink() throws Exception {
        String page = mvc.perform(get("/orders/place").param("product", "P103").with(asAlice()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(8, page.split("class=\"o-menu-radio\"", -1).length - 1, "one radio card per product");
        java.util.regex.Matcher tag = java.util.regex.Pattern.compile("<input[^>]*id=\"product-P103\"[^>]*>").matcher(page);
        assertTrue(tag.find(), "P103 radio rendered");
        assertTrue(tag.group().contains("checked=\"checked\""), "P103 preselected: " + tag.group());
        assertEquals(1, java.util.regex.Pattern.compile("<input[^>]*class=\"o-menu-radio\"[^>]*checked=\"checked\"[^>]*>")
                .matcher(page).results().count(), "exactly one product selected");
        assertTrue(page.contains("data-price=\"1250.00\""));
        assertTrue(!page.contains("name=\"unitPrice\"") && !page.contains("name=\"productName\""), "no price or name inputs");

        String none = mvc.perform(get("/orders/place").param("product", "NOPE").with(asAlice()))
                .andReturn().getResponse().getContentAsString();
        assertEquals(0, java.util.regex.Pattern.compile("<input[^>]*class=\"o-menu-radio\"[^>]*checked[^>]*>").matcher(none).results().count(),
                "unknown product: nothing preselected");
    }

    @Test
    void productsToOrderToPayment() throws Exception {
        // 1. Order: P101 layer cake x1, delivery -> 4,850 + 250 = 5,100 (price set by the server)
        String location = mvc.perform(post("/orders/place").with(asAlice()).with(csrf())
                        .param("productId", "P101").param("unitPrice", "10").param("productName", "Cheap cake")
                        .param("quantity", "1").param("orderType", "DELIVERY").param("deliveryAddress", "1 Lake Road, Kandy"))
                .andExpect(redirectedUrlPattern("/orders/O*")).andReturn().getResponse().getRedirectedUrl();
        String orderId = location.substring("/orders/".length());
        Order order = orders.getOwnOrder(orderId, alice);
        assertEquals("Cherry Chocolate Layer Cake", order.getProductName());
        assertEquals(5100.0, order.calculateTotal(), 0.001);

        // 2. Payment form uses the same server-side total
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/payments/new").param("orderId", orderId).session(session).with(asAlice()))
                .andExpect(content().string(allOf(containsString("data-total=\"5100.00\""), containsString("value=\"5100.00\""))));

        // 3. Record the payment: it is Pending until an admin verifies it
        mvc.perform(post("/payments").session(session).with(asAlice()).with(csrf())
                        .param("csrf", (String) session.getAttribute("paymentCsrf"))
                        .param("type", "ORDER").param("targetId", orderId).param("method", "CASH")
                        .param("purpose", "FULL").param("amount", "5100.00").param("requestKey", "catalog-journey"))
                .andExpect(redirectedUrlPattern("/payments/PAY*"));
        OrderPaymentSummary summary = orders.paymentSummary(orders.getOwnOrder(orderId, alice)).orElseThrow();
        assertEquals(OrderPaymentSummary.Status.PAYMENT_PENDING, summary.status());
        assertEquals(new BigDecimal("5100.00"), summary.pending());
    }
}
