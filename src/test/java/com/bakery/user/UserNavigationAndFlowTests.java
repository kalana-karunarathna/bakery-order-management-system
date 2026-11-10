package com.bakery.user;

import com.bakery.user.model.User;
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

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-aware navigation, login redirects and the complete customer account journey.
 * Uses its own temporary data directory (never data/users.txt).
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserNavigationAndFlowTests {

    private static final Path DATA_DIR = createTempDir();
    private static final String PASSWORD = "Bakery2026";
    private static final String ADMIN_PASSWORD = "Admin2026x";

    @DynamicPropertySource
    static void dataDirectory(DynamicPropertyRegistry registry) {
        registry.add("bakery.data-directory", DATA_DIR::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired UserRepository repository;

    private String alice;
    private String admin;

    @BeforeEach
    void resetUsers() throws IOException {
        Files.deleteIfExists(DATA_DIR.resolve(UserRepository.FILE_NAME));
        alice = userService.register("Alice Perera", "alice@example.com", "077 111 1111", "1 Lake Road, Kandy", PASSWORD, PASSWORD).id();
        userService.register("Bob Silva", "bob@example.com", "077 222 2222", "2 Hill Street, Galle", PASSWORD, PASSWORD);
        admin = userService.createAdmin("Bakery Admin", "admin@example.com", ADMIN_PASSWORD).id();
    }

    private BakeryUserPrincipal principal(String id) {
        return new BakeryUserPrincipal(repository.findById(id).orElseThrow());
    }

    // ── Authentication pages use the minimal header ───────────────────────────

    @Test
    void loginAndRegisterShowOnlyTheMinimalAuthHeader() throws Exception {
        for (String page : new String[]{"/login", "/register"}) {
            mvc.perform(get(page))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("u-auth-header"),
                            containsString("Back to shop"),
                            not(containsString("id=\"main-navigation\"")),
                            not(containsString("href=\"/payments\"")),
                            not(containsString("href=\"/feedback\"")),
                            not(containsString("href=\"/users/admin\"")))));
        }
    }

    // ── Role-aware navigation ─────────────────────────────────────────────────

    @Test
    void anonymousNavigationOffersSignInAndCreateAccount() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(allOf(
                        containsString("href=\"/login\""), containsString("Create account"),
                        containsString("href=\"/products\""), containsString("href=\"/cake-bookings\""),
                        not(containsString("My Orders")), not(containsString("Admin dashboard")),
                        not(containsString("Log out")))));
    }

    @Test
    void customerNavigationIsCustomerOriented() throws Exception {
        mvc.perform(get("/").with(user(principal(alice))))
                .andExpect(content().string(allOf(
                        containsString("My Orders"), containsString("Alice Perera"),
                        containsString("My account"), containsString("Log out"),
                        containsString("href=\"/orders\""),                // My Orders target on this branch
                        not(containsString("Create account")), not(containsString(">Sign in<")),
                        not(containsString("Admin dashboard")), not(containsString("href=\"/users/admin")))));
    }

    @Test
    void adminNavigationIsSeparate() throws Exception {
        mvc.perform(get("/").with(user(principal(admin))))
                .andExpect(content().string(allOf(
                        containsString("Admin dashboard"), containsString("href=\"/users/admin/customers\""),
                        containsString("Admin profile"), containsString("Log out"),
                        not(containsString("My Orders")), not(containsString("Create account")))));
        mvc.perform(get("/users/admin").with(user(principal(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("a-sidebar"), containsString("Dashboard"), containsString("Customers"),
                        containsString("Admin profile"), containsString("Newest customers"),
                        containsString("Alice Perera"))));
    }

    // ── Login redirects ───────────────────────────────────────────────────────

    @Test
    void customerLoginGoesToProfileAndAdminLoginGoesToDashboard() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").user("alice@example.com").password(PASSWORD))
                .andExpect(authenticated().withRoles("CUSTOMER")).andExpect(redirectedUrl("/profile"));
        mvc.perform(formLogin("/login").userParameter("email").user("admin@example.com").password(ADMIN_PASSWORD))
                .andExpect(authenticated().withRoles("ADMIN")).andExpect(redirectedUrl("/users/admin"));
    }

    @Test
    void loginReturnsToTheProtectedPageThatWasRequested() throws Exception {
        MvcResult bounce = mvc.perform(get("/profile/password")).andExpect(redirectedUrlPattern("**/login")).andReturn();
        MockHttpSession session = (MockHttpSession) bounce.getRequest().getSession();
        mvc.perform(post("/login").param("email", "alice@example.com").param("password", PASSWORD).with(csrf()).session(session))
                .andExpect(redirectedUrl("http://localhost/profile/password?continue"));
    }

    @Test
    void customerIsNotSentBackToAnAdminPageAfterLogin() throws Exception {
        MvcResult bounce = mvc.perform(get("/users/admin/customers")).andExpect(redirectedUrlPattern("**/login")).andReturn();
        MockHttpSession session = (MockHttpSession) bounce.getRequest().getSession();
        mvc.perform(post("/login").param("email", "alice@example.com").param("password", PASSWORD).with(csrf()).session(session))
                .andExpect(redirectedUrl("/profile"));
    }

    @Test
    void signedInUsersAreRedirectedAwayFromLoginAndRegister() throws Exception {
        mvc.perform(get("/login").with(user(principal(alice)))).andExpect(redirectedUrl("/profile"));
        mvc.perform(get("/register").with(user(principal(alice)))).andExpect(redirectedUrl("/profile"));
    }

    // ── Admin dashboard / customer list ───────────────────────────────────────

    @Test
    void adminCustomerListSearches() throws Exception {
        mvc.perform(get("/users/admin/customers").param("q", "alice").with(user(principal(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Alice Perera"), not(containsString("Bob Silva")))));
        mvc.perform(get("/users/admin/customers").with(user(principal(alice)))).andExpect(status().isForbidden());
    }

    // ── Complete customer journey ─────────────────────────────────────────────

    @Test
    void fullCustomerJourney() throws Exception {
        // 1-8: register (role/status fields are ignored) and land on the login page
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Chris Fernando").param("email", "Chris@Example.com")
                        .param("phone", "077 333 3333").param("address", "3 Main Street, Colombo")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD)
                        .param("role", "ADMIN").param("status", "ACTIVE").param("customerType", "LOYALTY"))
                .andExpect(redirectedUrl("/login?registered"));
        User chris = repository.findByEmail("chris@example.com").orElseThrow();
        assertEquals("CUSTOMER", chris.getRole().name());
        assertEquals("REGULAR", chris.getCustomerType().name());
        assertTrue(chris.getPasswordHash().startsWith("$2a$"));
        assertFalse(Files.readString(DATA_DIR.resolve(UserRepository.FILE_NAME)).contains(PASSWORD));

        // duplicate email is rejected
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Other Chris").param("email", "chris@example.com")
                        .param("phone", "077 444 4444").param("address", "4 Main Street, Colombo")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD))
                .andExpect(status().isOk()).andExpect(content().string(containsString(UserService.EMAIL_TAKEN)));

        // 9-10: sign in, land on profile
        MvcResult login = mvc.perform(formLogin("/login").userParameter("email").user("chris@example.com").password(PASSWORD))
                .andExpect(redirectedUrl("/profile")).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/profile").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Chris Fernando")));

        // 11: edit profile
        mvc.perform(post("/profile/edit").session(session).with(csrf())
                        .param("fullName", "Chris J Fernando").param("email", "chris@example.com")
                        .param("phone", "077 333 3334").param("address", "5 Temple Road, Colombo"))
                .andExpect(redirectedUrl("/profile"));
        mvc.perform(get("/profile").session(session)).andExpect(content().string(containsString("Chris J Fernando")));

        // 12: change password
        mvc.perform(post("/profile/password").session(session).with(csrf())
                        .param("currentPassword", PASSWORD).param("newPassword", "NewBakery2027")
                        .param("confirmPassword", "NewBakery2027"))
                .andExpect(redirectedUrl("/profile"));

        // 13: log out; the old session no longer works
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        assertTrue(session.isInvalid());

        // 14-15: old password fails, new password works
        mvc.perform(formLogin("/login").userParameter("email").user("chris@example.com").password(PASSWORD))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin("/login").userParameter("email").user("chris@example.com").password("NewBakery2027"))
                .andExpect(authenticated().withRoles("CUSTOMER"));

        // 16: the customer still cannot reach admin pages
        String chrisId = repository.findByEmail("chris@example.com").orElseThrow().getId();
        mvc.perform(get("/users/admin").with(user(principal(chrisId)))).andExpect(status().isForbidden());
    }

    // ── Other team pages keep rendering for every role ────────────────────────

    @Test
    void teamPagesRenderForEveryRole() throws Exception {
        String[] pages = {"/", "/users", "/products", "/orders", "/cake-bookings", "/payments", "/feedback"};
        for (String page : pages) {
            mvc.perform(get(page)).andExpect(status().isOk());
            mvc.perform(get(page).with(user(principal(alice)))).andExpect(status().isOk());
            mvc.perform(get(page).with(user(principal(admin)))).andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"/cake-bookings\"")));
        }
    }

    private static Path createTempDir() {
        try {
            Path dir = Files.createTempDirectory("bakery-nav-tests");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
