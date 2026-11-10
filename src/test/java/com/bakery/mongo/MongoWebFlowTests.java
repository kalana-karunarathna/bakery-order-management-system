package com.bakery.mongo;

import com.bakery.order.model.OrderStatus;
import com.bakery.order.model.OrderType;
import com.bakery.order.service.OrderService;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.security.BakeryUserPrincipal;
import com.bakery.user.service.UserService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The full customer and admin journey with MongoDB storage, through the real security filter chain
 * (temporary database, never bakery_db).
 */
@SpringBootTest
@AutoConfigureMockMvc
class MongoWebFlowTests {

    private static final String PASSWORD = "Bakery2026";

    @BeforeAll
    static void requireMongo() {
        assumeTrue(MongoTestSupport.isAvailable(), "Local MongoDB not running: MongoDB tests skipped");
    }

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        MongoTestSupport.register(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate template;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired OrderService orderService;

    private String admin;

    @BeforeEach
    void clean() {
        MongoTestSupport.clear(template);
        admin = userService.createAdmin("Bakery Admin", "admin@example.com", "Admin2026x").id();
    }

    private BakeryUserPrincipal as(String id) {
        return new BakeryUserPrincipal(users.findById(id).orElseThrow());
    }

    private Document orderDoc(String id) {
        return template.getCollection("orders").find(new Document("_id", id)).first();
    }

    @Test
    void registerLoginOrderAndAdminStatusChangeArePersistedInMongo() throws Exception {
        // Register → customer document in MongoDB
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Chris Fernando").param("email", "chris@example.com")
                        .param("phone", "077 333 3333").param("address", "3 Main Street, Colombo")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD)
                        .param("role", "ADMIN"))
                .andExpect(redirectedUrl("/login?registered"));
        Document customer = template.getCollection("users").find(new Document("email", "chris@example.com")).first();
        assertNotNull(customer);
        assertEquals("CUSTOMER", customer.getString("role"));
        String chris = customer.getString("_id");

        // Login → premium homepage
        MvcResult login = mvc.perform(formLogin("/login").userParameter("email").user("chris@example.com").password(PASSWORD))
                .andExpect(authenticated().withRoles("CUSTOMER")).andExpect(redirectedUrl("/")).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/").session(session)).andExpect(content().string(allOf(
                containsString("Something sweet for every occasion."), containsString("Chris Fernando"))));

        // Place order (spoofed customer ID ignored) → order document in MongoDB
        MvcResult placed = mvc.perform(post("/orders/place").session(session).with(csrf())
                        .param("customerId", admin)
                        .param("productId", "P004").param("productName", "Chocolate Cupcakes")
                        .param("quantity", "6").param("unitPrice", "200").param("orderType", "DELIVERY")
                        .param("deliveryAddress", "3 Main Street, Colombo"))
                .andExpect(redirectedUrlPattern("/orders/O*")).andReturn();
        String orderId = placed.getResponse().getRedirectedUrl().substring("/orders/".length());
        assertEquals(chris, orderDoc(orderId).getString("customerId"));

        // My Orders; logout; login again; data still there
        mvc.perform(get("/orders/my-orders").session(session)).andExpect(content().string(containsString("Chocolate Cupcakes")));
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        MvcResult again = mvc.perform(formLogin("/login").userParameter("email").user("chris@example.com").password(PASSWORD))
                .andExpect(authenticated()).andReturn();
        MockHttpSession session2 = (MockHttpSession) again.getRequest().getSession(false);
        mvc.perform(get("/orders/my-orders").session(session2)).andExpect(content().string(containsString("Chocolate Cupcakes")));

        // Admin sees customers and orders, updates the status
        mvc.perform(get("/users/admin/customers").with(user(as(admin)))).andExpect(content().string(containsString("Chris Fernando")));
        mvc.perform(get("/orders/admin").with(user(as(admin)))).andExpect(content().string(containsString("Chocolate Cupcakes")));
        mvc.perform(post("/orders/" + orderId + "/status").with(csrf()).with(user(as(admin))).param("status", "PREPARING"))
                .andExpect(flash().attribute("successMessage", containsString("PREPARING")));
        assertEquals("PREPARING", orderDoc(orderId).getString("status"));

        // Customer sees the new status and can no longer edit
        mvc.perform(get("/orders/" + orderId).session(session2))
                .andExpect(content().string(allOf(containsString("Preparing"), not(containsString("/edit\"")))));
    }

    @Test
    void ownershipRolesAndCsrfAreEnforcedWithMongoStorage() throws Exception {
        String alice = userService.register("Alice Perera", "alice@example.com", "077 111 1111", "1 Lake Road, Kandy", PASSWORD, PASSWORD).id();
        String bob = userService.register("Bob Silva", "bob@example.com", "077 222 2222", "2 Hill Street, Galle", PASSWORD, PASSWORD).id();
        String bobOrder = orderService.placeOrder(bob, "P002", "Bob Croissant", 3, 180, OrderType.PICKUP, null).getId();

        mvc.perform(get("/orders/" + bobOrder).with(user(as(alice)))).andExpect(redirectedUrl("/orders/my-orders"));
        mvc.perform(post("/orders/" + bobOrder + "/cancel").with(csrf()).with(user(as(alice)))).andExpect(redirectedUrl("/orders/my-orders"));
        mvc.perform(get("/orders/admin").with(user(as(alice)))).andExpect(status().isForbidden());
        mvc.perform(post("/orders/" + bobOrder + "/status").with(csrf()).with(user(as(alice))).param("status", "DELIVERED"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/orders/" + bobOrder + "/delete").with(user(as(admin)))).andExpect(status().isForbidden()); // no CSRF
        assertEquals(OrderStatus.PENDING.name(), orderDoc(bobOrder).getString("status"));
    }

    @Test
    void deactivatedAccountsCannotSignIn() throws Exception {
        String alice = userService.register("Alice Perera", "alice@example.com", "077 111 1111", "1 Lake Road, Kandy", PASSWORD, PASSWORD).id();
        mvc.perform(post("/users/admin/" + alice + "/status").param("active", "false").with(csrf()).with(user(as(admin))))
                .andExpect(redirectedUrl("/users/admin/" + alice));
        assertEquals("INACTIVE", template.getCollection("users").find(new Document("_id", alice)).first().getString("status"));
        mvc.perform(formLogin("/login").userParameter("email").user("alice@example.com").password(PASSWORD))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?disabled"));
    }
}
