package com.bakery.user;

import com.bakery.user.model.AccountStatus;
import com.bakery.user.model.User;
import com.bakery.user.repository.UserRepository;
import com.bakery.user.security.BakeryUserPrincipal;
import com.bakery.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentication, authorisation, CSRF and ownership through the real Spring Security filter chain.
 * Uses its own temporary data directory, so data/users.txt is never touched.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserWebSecurityTests {

    private static final Path DATA_DIR = createTempDir();
    private static final String PASSWORD = "Bakery2026";

    @DynamicPropertySource
    static void dataDirectory(DynamicPropertyRegistry registry) {
        registry.add("bakery.data-directory", DATA_DIR::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired UserRepository repository;
    @Value("${bakery.data-directory}") String configuredDir;

    private String alice;   // customer
    private String bob;     // customer
    private String admin;

    @BeforeEach
    void resetUsers() throws IOException {
        assertEquals(DATA_DIR.toString(), configuredDir, "tests must not use the real data folder");
        Files.deleteIfExists(DATA_DIR.resolve(UserRepository.FILE_NAME));
        alice = userService.register("Alice Perera", "alice@example.com", "077 111 1111", "1 Lake Road, Kandy", PASSWORD, PASSWORD).id();
        bob = userService.register("Bob Silva", "bob@example.com", "077 222 2222", "2 Hill Street, Galle", PASSWORD, PASSWORD).id();
        admin = userService.createAdmin("Bakery Admin", "admin@example.com", "Admin2026x").id();
    }

    private BakeryUserPrincipal principal(String id) {
        return new BakeryUserPrincipal(repository.findById(id).orElseThrow());
    }

    private User stored(String id) {
        return repository.findById(id).orElseThrow();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Test
    void registrationAlwaysCreatesCustomerEvenIfRoleIsSubmitted() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Chris Fernando").param("email", "chris@example.com")
                        .param("phone", "077 333 3333").param("address", "3 Main Street, Colombo")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD)
                        .param("role", "ADMIN").param("customerType", "LOYALTY"))
                .andExpect(redirectedUrl("/login?registered"));
        User chris = repository.findByEmail("chris@example.com").orElseThrow();
        assertEquals("CUSTOMER", chris.getRole().name());
        assertEquals("REGULAR", chris.getCustomerType().name());
    }

    @Test
    void registrationFormShowsErrorsWithoutEchoingPassword() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Chris Fernando").param("email", "alice@example.com")
                        .param("phone", "077 333 3333").param("address", "3 Main Street")
                        .param("password", "Secret99x").param("confirmPassword", "Secret99x"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(UserService.EMAIL_TAKEN)))
                .andExpect(content().string(not(containsString("Secret99x"))));
    }

    // ── Authentication ────────────────────────────────────────────────────────

    @Test
    void loginSucceedsWithCorrectPassword() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").user("ALICE@example.com").password(PASSWORD))
                .andExpect(authenticated().withRoles("CUSTOMER"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void adminLoginHasAdminRole() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").user("admin@example.com").password("Admin2026x"))
                .andExpect(authenticated().withRoles("ADMIN"));
    }

    @Test
    void loginFailsWithWrongPasswordOrUnknownEmail() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").user("alice@example.com").password("wrong-pass1"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin("/login").userParameter("email").user("nobody@example.com").password(PASSWORD))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void disabledAccountCannotLogInAndIsOnlyRevealedAfterCorrectPassword() throws Exception {
        userService.setCustomerActive(alice, false);
        mvc.perform(formLogin("/login").userParameter("email").user("alice@example.com").password(PASSWORD))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?disabled"));
        mvc.perform(formLogin("/login").userParameter("email").user("alice@example.com").password("wrong-pass1"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        MvcResult login = mvc.perform(formLogin("/login").userParameter("email").user("alice@example.com").password(PASSWORD))
                .andExpect(authenticated()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/profile").session(session)).andExpect(status().isOk());

        mvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(redirectedUrl("/login?logout"));
        assertEquals(true, session.isInvalid());
        mvc.perform(get("/profile").session(new MockHttpSession()))
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void logoutRequiresPostWithCsrf() throws Exception {
        mvc.perform(post("/logout").with(user(principal(alice))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatedUserIsSignedOutOnNextRequest() throws Exception {
        MvcResult login = mvc.perform(formLogin("/login").userParameter("email").user("alice@example.com").password(PASSWORD))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        userService.setCustomerActive(alice, false);
        mvc.perform(get("/profile").session(session)).andExpect(redirectedUrl("/login?disabled"));
    }

    @Test
    void protectedPagesAreNotCached() throws Exception {
        mvc.perform(get("/profile").with(user(principal(alice))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    // ── Authorisation ─────────────────────────────────────────────────────────

    @Test
    void anonymousUsersAreSentToLogin() throws Exception {
        mvc.perform(get("/profile")).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/users/admin")).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void accessDeniedPageRendersWith403() throws Exception {
        mvc.perform(get("/access-denied").with(user(principal(alice))))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("have access to this page")));
    }

    @Test
    void publicPagesStayPublic() throws Exception {
        for (String path : new String[]{"/", "/users", "/login", "/register", "/products", "/orders",
                "/cake-bookings", "/payments", "/feedback"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void customerCannotAccessAdminPages() throws Exception {
        // Spring Security forwards to the access-denied page (MockMvc records the forward).
        mvc.perform(get("/users/admin").with(user(principal(alice))))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/access-denied"));
        mvc.perform(get("/users/admin/" + bob).with(user(principal(alice))))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotModifyAnotherCustomer() throws Exception {
        // Admin endpoints are refused...
        mvc.perform(post("/users/admin/" + bob + "/delete").with(csrf()).with(user(principal(alice))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/users/admin/" + bob + "/status").param("active", "false").with(csrf()).with(user(principal(alice))))
                .andExpect(status().isForbidden());
        // ...and the profile form always edits the signed-in account, whatever IDs are submitted.
        mvc.perform(post("/profile/edit").with(csrf()).with(user(principal(alice)))
                        .param("id", bob).param("userId", bob)
                        .param("fullName", "Hacked Name").param("email", "alice@example.com")
                        .param("phone", "077 111 1111").param("address", "1 Lake Road, Kandy"))
                .andExpect(redirectedUrl("/profile"));
        assertEquals("Bob Silva", stored(bob).getFullName());
        assertEquals(AccountStatus.ACTIVE, stored(bob).getStatus());
        assertEquals("Hacked Name", stored(alice).getFullName());
    }

    @Test
    void customerCannotPromoteThemselves() throws Exception {
        mvc.perform(post("/profile/edit").with(csrf()).with(user(principal(alice)))
                        .param("fullName", "Alice Perera").param("email", "alice@example.com")
                        .param("phone", "077 111 1111").param("address", "1 Lake Road, Kandy")
                        .param("role", "ADMIN").param("customerType", "LOYALTY"))
                .andExpect(redirectedUrl("/profile"));
        assertEquals("CUSTOMER", stored(alice).getRole().name());
        assertEquals("REGULAR", stored(alice).getCustomerType().name());
    }

    @Test
    void adminCanManageCustomers() throws Exception {
        mvc.perform(get("/users/admin").with(user(principal(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alice Perera")))
                .andExpect(content().string(not(containsString("$2a$"))));
        mvc.perform(get("/users/admin/" + alice).with(user(principal(admin)))).andExpect(status().isOk());
        mvc.perform(post("/users/admin/" + alice + "/status").param("active", "false").with(csrf()).with(user(principal(admin))))
                .andExpect(redirectedUrl("/users/admin/" + alice));
        assertEquals(AccountStatus.INACTIVE, stored(alice).getStatus());
        mvc.perform(post("/users/admin/" + alice + "/edit").with(csrf()).with(user(principal(admin)))
                        .param("fullName", "Alice Perera").param("email", "alice@example.com")
                        .param("phone", "077 111 1111").param("address", "1 Lake Road, Kandy")
                        .param("customerType", "LOYALTY"))
                .andExpect(redirectedUrl("/users/admin/" + alice));
        assertEquals("LOYALTY", stored(alice).getCustomerType().name());
        mvc.perform(post("/users/admin/" + bob + "/delete").with(csrf()).with(user(principal(admin))))
                .andExpect(redirectedUrl("/users/admin/customers"));
        assertEquals(AccountStatus.DELETED, stored(bob).getStatus());
    }

    @Test
    void adminCannotChangeAdminAccountsFromDashboard() throws Exception {
        mvc.perform(post("/users/admin/" + admin + "/status").param("active", "false").with(csrf()).with(user(principal(admin))))
                .andExpect(redirectedUrl("/users/admin/" + admin));
        assertEquals(AccountStatus.ACTIVE, stored(admin).getStatus());
    }

    // ── CSRF ──────────────────────────────────────────────────────────────────

    @Test
    void stateChangingRequestsWithoutCsrfAreRejected() throws Exception {
        mvc.perform(post("/register").param("fullName", "No Token").param("email", "nt@example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/profile/edit").with(user(principal(alice)))
                        .param("fullName", "Changed").param("email", "alice@example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/users/admin/" + bob + "/delete").with(user(principal(admin))))
                .andExpect(status().isForbidden());
        assertEquals("Alice Perera", stored(alice).getFullName());
        assertEquals(AccountStatus.ACTIVE, stored(bob).getStatus());
    }

    // ── Profile pages ─────────────────────────────────────────────────────────

    @Test
    void profilePasswordAndDeleteFlowsWork() throws Exception {
        mvc.perform(get("/profile").with(user(principal(alice))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alice Perera")))
                .andExpect(content().string(not(containsString("$2a$"))));
        mvc.perform(post("/profile/password").with(csrf()).with(user(principal(alice)))
                        .param("currentPassword", "wrong").param("newPassword", "Bakery2027").param("confirmPassword", "Bakery2027"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("current password is incorrect")));
        mvc.perform(post("/profile/password").with(csrf()).with(user(principal(alice)))
                        .param("currentPassword", PASSWORD).param("newPassword", "Bakery2027").param("confirmPassword", "Bakery2027"))
                .andExpect(redirectedUrl("/profile"));
        mvc.perform(post("/profile/delete").with(csrf()).with(user(principal(alice))).param("password", "Bakery2027"))
                .andExpect(redirectedUrl("/login?deleted"));
        assertEquals(AccountStatus.DELETED, stored(alice).getStatus());
    }

    private static Path createTempDir() {
        try {
            Path dir = Files.createTempDirectory("bakery-user-tests");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
