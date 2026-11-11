package com.bakery.order;

import com.bakery.order.model.Order;
import com.bakery.order.model.OrderStatus;
import com.bakery.order.model.OrderType;
import com.bakery.order.repository.FileOrderRepository;
import com.bakery.order.repository.OrderRepository;
import com.bakery.order.service.OrderService;
import com.bakery.user.repository.FileUserRepository;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.security.BakeryUserPrincipal;
import com.bakery.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * User Management + Order Management integration through the real Spring Security filter chain:
 * ownership (no IDOR), role separation, CSRF, validation and the status workflow.
 * Uses a temporary data directory; data/orders.txt and data/users.txt are never touched.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderIntegrationTests {

    private static final Path DATA_DIR = createTempDir();
    private static final String PASSWORD = "Bakery2026";

    @DynamicPropertySource
    static void dataDirectory(DynamicPropertyRegistry registry) {
        registry.add("bakery.data-directory", DATA_DIR::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;

    private String alice;
    private String bob;
    private String admin;
    private String aliceOrder;   // pending delivery order
    private String bobOrder;     // pending pickup order

    @BeforeEach
    void reset() throws IOException {
        Files.deleteIfExists(DATA_DIR.resolve(FileUserRepository.FILE_NAME));
        Files.deleteIfExists(DATA_DIR.resolve(FileOrderRepository.FILE_NAME));
        alice = userService.register("Alice Perera", "alice@example.com", "077 111 1111", "1 Lake Road, Kandy", PASSWORD, PASSWORD).id();
        bob = userService.register("Bob Silva", "bob@example.com", "077 222 2222", "2 Hill Street, Galle", PASSWORD, PASSWORD).id();
        admin = userService.createAdmin("Bakery Admin", "admin@example.com", "Admin2026x").id();
        aliceOrder = orderService.placeOrder(alice, "P001", "Alice Sourdough", 2, 900, OrderType.DELIVERY, "1 Lake Road, Kandy").getId();
        bobOrder = orderService.placeOrder(bob, "P002", "Bob Croissant", 3, 180, OrderType.PICKUP, null).getId();
    }

    private BakeryUserPrincipal as(String userId) {
        return new BakeryUserPrincipal(userRepository.findById(userId).orElseThrow());
    }

    private Order order(String id) throws IOException {
        return orderRepository.findById(id).orElseThrow();
    }

    // ── Placing orders ────────────────────────────────────────────────────────

    @Test
    void customerPlacesOrderAndItIsLinkedToTheirAccountNotToSubmittedIds() throws Exception {
        MvcResult result = mvc.perform(post("/orders/place").with(csrf()).with(user(as(alice)))
                        .param("customerId", bob)                     // attempted spoof, must be ignored
                        // Demo menu product P107 (Glazed Cinnamon Roll, LKR 420). A tampered name and price must be ignored.
                        .param("productId", "P107").param("productName", "Free Cake").param("unitPrice", "1")
                        .param("quantity", "4").param("orderType", "PICKUP"))
                .andExpect(redirectedUrlPattern("/orders/O*"))
                .andExpect(flash().attribute("successMessage", containsString("placed successfully")))
                .andReturn();
        String newId = result.getResponse().getRedirectedUrl().substring("/orders/".length());
        Order saved = order(newId);
        assertEquals(alice, saved.getCustomerId());
        assertEquals("Glazed Cinnamon Roll", saved.getProductName(), "name comes from the catalog");
        assertEquals(420.0, saved.getUnitPrice(), 0.001, "price comes from the catalog");
        assertEquals(1680.0, saved.calculateTotal(), 0.001, "total is calculated on the server");
    }

    @Test
    void placeOrderFormShowsSignedInCustomerAndHasNoCustomerIdField() throws Exception {
        mvc.perform(get("/orders/place").with(user(as(alice))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Alice Perera"), containsString("alice@example.com"),
                        containsString("1 Lake Road, Kandy"),                 // delivery address pre-filled
                        not(containsString("name=\"customerId\"")))));
    }

    @Test
    void invalidOrdersAreRejectedWithFriendlyMessagesAndNothingIsSaved() throws Exception {
        String[][] cases = {
                // {quantity, productId, orderType, deliveryAddress, expected message}
                {"0", "P107", "PICKUP", "", "Quantity must be at least 1."},
                {"abc", "P107", "PICKUP", "", "Quantity must be a whole number."},
                {"101", "P107", "PICKUP", "", "please contact the bakery"},
                {"1", "", "PICKUP", "", "Please choose a product from the menu."},
                {"1", "P999", "PICKUP", "", "Please choose a product from the menu."},
                {"1", "P107", "DELIVERY", "", "Delivery address is required"},
                {"1", "P107", "BOGUS", "", "Please choose pickup or delivery."},
        };
        for (String[] c : cases) {
            mvc.perform(post("/orders/place").with(csrf()).with(user(as(alice)))
                            .param("productId", c[1])
                            .param("quantity", c[0]).param("orderType", c[2])
                            .param("deliveryAddress", c[3]))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(c[4])));
        }
        mvc.perform(post("/orders/place").with(csrf()).with(user(as(alice)))
                        .param("productId", "P107 | injected")
                        .param("quantity", "1").param("orderType", "PICKUP"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Please choose a product from the menu.")));
        assertEquals(2, orderRepository.findAll().size());
    }

    // ── My Orders and ownership (IDOR) ────────────────────────────────────────

    @Test
    void myOrdersShowsOnlyTheSignedInCustomersOrders() throws Exception {
        mvc.perform(get("/orders/my-orders").param("customerId", bob).with(user(as(alice))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Alice Sourdough"), not(containsString("Bob Croissant")))));
    }

    @Test
    void customerCannotViewAnotherCustomersOrder() throws Exception {
        mvc.perform(get("/orders/" + bobOrder).with(user(as(alice))))
                .andExpect(redirectedUrl("/orders/my-orders"))
                .andExpect(flash().attribute("errorMessage", "Order not found."));
        mvc.perform(get("/orders/" + aliceOrder).with(user(as(alice))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Alice Sourdough")));
    }

    @Test
    void customerCannotEditAnotherCustomersOrder() throws Exception {
        mvc.perform(get("/orders/" + bobOrder + "/edit").with(user(as(alice))))
                .andExpect(redirectedUrl("/orders/my-orders"));
        mvc.perform(post("/orders/" + bobOrder + "/edit").with(csrf()).with(user(as(alice))).param("quantity", "50"))
                .andExpect(redirectedUrl("/orders/my-orders"));
        assertEquals(3, order(bobOrder).getQuantity());
    }

    @Test
    void customerCannotCancelAnotherCustomersOrder() throws Exception {
        mvc.perform(post("/orders/" + bobOrder + "/cancel").with(csrf()).with(user(as(alice))))
                .andExpect(redirectedUrl("/orders/my-orders"));
        assertEquals(OrderStatus.PENDING, order(bobOrder).getStatus());
    }

    @Test
    void ownerCanEditAndCancelOnlyWhilePending() throws Exception {
        mvc.perform(post("/orders/" + aliceOrder + "/edit").with(csrf()).with(user(as(alice)))
                        .param("quantity", "5").param("deliveryAddress", "9 New Road, Kandy"))
                .andExpect(redirectedUrl("/orders/" + aliceOrder));
        assertEquals(5, order(aliceOrder).getQuantity());

        orderService.updateStatus(aliceOrder, OrderStatus.PREPARING);
        mvc.perform(post("/orders/" + aliceOrder + "/edit").with(csrf()).with(user(as(alice))).param("quantity", "7"))
                .andExpect(flash().attribute("errorMessage", "Only pending orders can be edited."));
        mvc.perform(post("/orders/" + aliceOrder + "/cancel").with(csrf()).with(user(as(alice))))
                .andExpect(flash().attribute("errorMessage", containsString("can't be cancelled online")));
        assertEquals(5, order(aliceOrder).getQuantity());
        assertEquals(OrderStatus.PREPARING, order(aliceOrder).getStatus());
    }

    @Test
    void ownerCanCancelPendingOrder() throws Exception {
        mvc.perform(post("/orders/" + bobOrder + "/cancel").with(csrf()).with(user(as(bob))))
                .andExpect(redirectedUrl("/orders/" + bobOrder));
        assertEquals(OrderStatus.CANCELLED, order(bobOrder).getStatus());
    }

    // ── Anonymous users ───────────────────────────────────────────────────────

    @Test
    void anonymousUsersMustSignIn() throws Exception {
        mvc.perform(get("/orders")).andExpect(status().isOk());
        for (String path : new String[]{"/orders/place", "/orders/my-orders", "/orders/" + aliceOrder,
                "/orders/" + aliceOrder + "/edit", "/orders/admin"}) {
            mvc.perform(get(path)).andExpect(redirectedUrlPattern("**/login"));
        }
        mvc.perform(post("/orders/place").with(csrf()).param("productId", "P1").param("productName", "Cake")
                        .param("quantity", "1").param("unitPrice", "100").param("orderType", "PICKUP"))
                .andExpect(redirectedUrlPattern("**/login"));
        assertEquals(2, orderRepository.findAll().size());
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @Test
    void adminCanViewAndManageAllOrders() throws Exception {
        mvc.perform(get("/orders/admin").with(user(as(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Alice Sourdough"), containsString("Bob Croissant"),
                        containsString("a-sidebar"), not(containsString("Access control pending")))));
        mvc.perform(get("/orders/" + bobOrder).with(user(as(admin)))).andExpect(status().isOk());

        mvc.perform(post("/orders/" + aliceOrder + "/status").with(csrf()).with(user(as(admin))).param("status", "PREPARING"))
                .andExpect(redirectedUrl("/orders/admin"))
                .andExpect(flash().attribute("successMessage", containsString("PREPARING")));
        assertEquals(OrderStatus.PREPARING, order(aliceOrder).getStatus());

        mvc.perform(post("/orders/" + bobOrder + "/delete").with(csrf()).with(user(as(admin))))
                .andExpect(redirectedUrl("/orders/admin"));
        assertEquals(1, orderRepository.findAll().size());
    }

    @Test
    void adminStatusChangesFollowTheWorkflow() throws Exception {
        mvc.perform(post("/orders/" + aliceOrder + "/status").with(csrf()).with(user(as(admin))).param("status", "DELIVERED"))
                .andExpect(flash().attribute("errorMessage", containsString("can't move from Pending to Delivered")));
        mvc.perform(post("/orders/" + aliceOrder + "/status").with(csrf()).with(user(as(admin))).param("status", "NOT_A_STATUS"))
                .andExpect(flash().attribute("errorMessage", "Choose a valid status."));
        orderService.updateStatus(aliceOrder, OrderStatus.CANCELLED);
        mvc.perform(post("/orders/" + aliceOrder + "/status").with(csrf()).with(user(as(admin))).param("status", "PENDING"))
                .andExpect(flash().attribute("errorMessage", containsString("can't move from Cancelled")));
        assertEquals(OrderStatus.CANCELLED, order(aliceOrder).getStatus());
        assertTrue(OrderService.canTransition(OrderStatus.READY, OrderStatus.DELIVERED));
    }

    @Test
    void customersCannotUseAdminOrderEndpoints() throws Exception {
        mvc.perform(get("/orders/admin").with(user(as(alice)))).andExpect(status().isForbidden());
        mvc.perform(post("/orders/" + aliceOrder + "/status").with(csrf()).with(user(as(alice))).param("status", "DELIVERED"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/orders/" + bobOrder + "/delete").with(csrf()).with(user(as(alice))))
                .andExpect(status().isForbidden());
        assertEquals(OrderStatus.PENDING, order(aliceOrder).getStatus());
        assertEquals(2, orderRepository.findAll().size());
    }

    @Test
    void adminsCannotPlaceCustomerOrders() throws Exception {
        mvc.perform(get("/orders/place").with(user(as(admin)))).andExpect(status().isForbidden());
    }

    // ── CSRF ──────────────────────────────────────────────────────────────────

    @Test
    void orderChangesWithoutCsrfTokenAreRejected() throws Exception {
        mvc.perform(post("/orders/place").with(user(as(alice))).param("productId", "P1").param("productName", "Cake")
                        .param("quantity", "1").param("unitPrice", "100").param("orderType", "PICKUP"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/orders/" + aliceOrder + "/cancel").with(user(as(alice)))).andExpect(status().isForbidden());
        mvc.perform(post("/orders/" + aliceOrder + "/status").with(user(as(admin))).param("status", "PREPARING"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/orders/" + bobOrder + "/delete").with(user(as(admin)))).andExpect(status().isForbidden());
        assertEquals(2, orderRepository.findAll().size());
        assertEquals(OrderStatus.PENDING, order(aliceOrder).getStatus());
    }

    // ── Homepage navigation ───────────────────────────────────────────────────

    @Test
    void homepageNavigationFollowsTheSignedInRole() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(allOf(containsString("Something sweet for every occasion."),
                        containsString("href=\"/login\""), containsString("href=\"/register\""),
                        not(containsString("action=\"/logout\"")), not(containsString("href=\"/orders/admin\"")))));
        mvc.perform(get("/").with(user(as(alice))))
                .andExpect(content().string(allOf(containsString("href=\"/orders/my-orders\""),
                        containsString("My account"), containsString("action=\"/logout\""), containsString("Order now"),
                        not(containsString("href=\"/orders/admin\"")), not(containsString("Create account")))));
        mvc.perform(get("/").with(user(as(admin))))
                .andExpect(content().string(allOf(containsString("href=\"/orders/admin\""),
                        containsString("href=\"/users/admin/customers\""), containsString("View site"),
                        containsString("action=\"/logout\""), not(containsString("Order now")))));
    }

    // ── Complete flow: register → login → order → my orders → logout ──────────

    @Test
    void registerLoginOrderAndLogout() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Chris Fernando").param("email", "chris@example.com")
                        .param("phone", "077 333 3333").param("address", "3 Main Street, Colombo")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD))
                .andExpect(redirectedUrl("/login?registered"));
        MvcResult login = mvc.perform(formLogin("/login").userParameter("email").user("chris@example.com").password(PASSWORD))
                .andExpect(authenticated().withRoles("CUSTOMER")).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        MvcResult placed = mvc.perform(post("/orders/place").session(session).with(csrf())
                        .param("productId", "P102")                     // Chocolate Cupcakes (6), LKR 1,650
                        .param("quantity", "2").param("orderType", "DELIVERY")
                        .param("deliveryAddress", "3 Main Street, Colombo"))
                .andExpect(redirectedUrlPattern("/orders/O*")).andReturn();
        String chris = userRepository.findByEmail("chris@example.com").orElseThrow().getId();
        String id = placed.getResponse().getRedirectedUrl().substring("/orders/".length());
        assertEquals(chris, order(id).getCustomerId());
        assertEquals(3550.0, order(id).calculateTotal(), 0.001);   // 2 × 1,650 + 250 delivery

        mvc.perform(get("/orders/my-orders").session(session))
                .andExpect(content().string(allOf(containsString("Chocolate Cupcakes"),
                        not(containsString("Alice Sourdough")), not(containsString("Bob Croissant")))));

        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        mvc.perform(get("/orders/my-orders").session(new MockHttpSession())).andExpect(redirectedUrlPattern("**/login"));
    }

    // ── Repository safety ─────────────────────────────────────────────────────

    @Test
    void unreadableOrderLinesAreKeptAndTheirIdsAreNotReused() throws Exception {
        List<String> lines = new java.util.ArrayList<>(Files.readAllLines(DATA_DIR.resolve(FileOrderRepository.FILE_NAME)));
        lines.add("O050|U999|P1|Broken|not-a-number|1.00|PICKUP||PENDING|2026-11-10");
        Files.write(DATA_DIR.resolve(FileOrderRepository.FILE_NAME), lines);

        assertEquals(2, orderRepository.findAll().size(), "a bad line must not break loading other orders");
        String id = orderService.placeOrder(alice, "P005", "Cookies", 1, 100, OrderType.PICKUP, null).getId();
        assertEquals("O051", id);
        assertTrue(Files.readString(DATA_DIR.resolve(FileOrderRepository.FILE_NAME)).contains("O050|U999|P1|Broken"));
    }

    private static Path createTempDir() {
        try {
            Path dir = Files.createTempDirectory("bakery-order-tests");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
