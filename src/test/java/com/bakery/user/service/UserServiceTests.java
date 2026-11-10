package com.bakery.user.service;

import com.bakery.common.TextFileStorage;
import com.bakery.user.model.AccountStatus;
import com.bakery.user.model.CustomerType;
import com.bakery.user.model.LoyaltyCustomer;
import com.bakery.user.model.User;
import com.bakery.user.model.UserRole;
import com.bakery.user.repository.FileUserRepository;
import com.bakery.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Business rules of UserService, using a real repository on a temporary directory. */
class UserServiceTests {

    @TempDir
    Path dir;

    private UserRepository repository;
    private UserService service;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @BeforeEach
    void setUp() {
        repository = new FileUserRepository(new TextFileStorage(dir.toString()));
        service = new UserService(repository, encoder);
    }

    private UserView register(String name, String email) {
        return service.register(name, email, "077 123 4567", "12 Lake Road, Kandy", "Bakery2026", "Bakery2026");
    }

    private static Map<String, String> errors(Runnable action) {
        return assertThrows(UserValidationException.class, action::run).getFieldErrors();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Test
    void registrationCreatesRegularCustomerWithHashedPassword() {
        UserView view = service.register("  Nimal   Perera ", " Nimal@Example.COM ", "077 123 4567",
                "12 Lake Road, Kandy", "Bakery2026", "Bakery2026");
        assertEquals("U001", view.id());
        assertEquals("Nimal Perera", view.fullName());
        assertEquals("nimal@example.com", view.email());
        assertEquals(UserRole.CUSTOMER, view.role());
        assertEquals(CustomerType.REGULAR, view.customerType());
        assertEquals(AccountStatus.ACTIVE, view.status());

        User stored = repository.findById("U001").orElseThrow();
        assertNotEquals("Bakery2026", stored.getPasswordHash());
        assertTrue(encoder.matches("Bakery2026", stored.getPasswordHash()));
    }

    @Test
    void registrationGivesUniqueSequentialIds() {
        assertEquals("U001", register("Nimal Perera", "nimal@example.com").id());
        assertEquals("U002", register("Kamala Silva", "kamala@example.com").id());
        assertEquals("U003", register("Sunil Fernando", "sunil@example.com").id());
    }

    @Test
    void duplicateEmailIsRejected() {
        register("Nimal Perera", "nimal@example.com");
        assertEquals(UserService.EMAIL_TAKEN, errors(() -> register("Other Person", "NIMAL@example.com")).get("email"));
    }

    @Test
    void invalidEmailIsRejected() {
        assertTrue(errors(() -> register("Nimal Perera", "nimal@")).containsKey("email"));
        assertTrue(errors(() -> register("Nimal Perera", "a|b@example.com")).containsKey("email"));
    }

    @Test
    void weakPasswordsAreRejected() {
        assertTrue(errors(() -> service.register("Nimal Perera", "n@example.com", "0771234567", "12 Lake Road",
                "short1", "short1")).containsKey("password"));
        assertTrue(errors(() -> service.register("Nimal Perera", "n@example.com", "0771234567", "12 Lake Road",
                "onlyletters", "onlyletters")).containsKey("password"));
        assertTrue(errors(() -> service.register("Nimal Perera", "n@example.com", "0771234567", "12 Lake Road",
                "12345678", "12345678")).containsKey("password"));
    }

    @Test
    void passwordConfirmationMustMatch() {
        Map<String, String> e = errors(() -> service.register("Nimal Perera", "n@example.com", "0771234567",
                "12 Lake Road", "Bakery2026", "Bakery2027"));
        assertEquals("Passwords do not match.", e.get("confirmPassword"));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void requiredFieldsAndUnsafeCharactersAreReported() {
        Map<String, String> e = errors(() -> service.register("", "", "", "line\nbreak", "", ""));
        assertTrue(e.keySet().containsAll(List.of("fullName", "email", "phone", "address", "password")));
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @Test
    void customerCanUpdateOwnProfile() {
        register("Nimal Perera", "nimal@example.com");
        UserView updated = service.updateOwnProfile("U001", "Nimal K Perera", "nimal.k@example.com",
                "+94 71 555 0000", "45 Temple Street, Kandy");
        assertEquals("nimal.k@example.com", updated.email());
        assertEquals("45 Temple Street, Kandy", repository.findById("U001").orElseThrow().getAddress());
        assertEquals(UserRole.CUSTOMER, updated.role(), "profile edits never change the role");
    }

    @Test
    void profileUpdateRejectsAnotherCustomersEmail() {
        register("Nimal Perera", "nimal@example.com");
        register("Kamala Silva", "kamala@example.com");
        Map<String, String> e = errors(() -> service.updateOwnProfile("U002", "Kamala Silva", "nimal@example.com",
                "077 123 4567", "12 Lake Road, Kandy"));
        assertEquals(UserService.EMAIL_TAKEN, e.get("email"));
    }

    @Test
    void passwordChangeRequiresCorrectCurrentPassword() {
        register("Nimal Perera", "nimal@example.com");
        Map<String, String> e = errors(() -> service.changePassword("U001", "wrong", "NewBakery2027", "NewBakery2027"));
        assertTrue(e.containsKey("currentPassword"));

        service.changePassword("U001", "Bakery2026", "NewBakery2027", "NewBakery2027");
        String hash = repository.findById("U001").orElseThrow().getPasswordHash();
        assertTrue(encoder.matches("NewBakery2027", hash));
        assertFalse(encoder.matches("Bakery2026", hash));
    }

    @Test
    void newPasswordMustDifferFromCurrent() {
        register("Nimal Perera", "nimal@example.com");
        assertTrue(errors(() -> service.changePassword("U001", "Bakery2026", "Bakery2026", "Bakery2026"))
                .containsKey("newPassword"));
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    @Test
    void deleteRequiresPasswordAndAnonymises() {
        register("Nimal Perera", "nimal@example.com");
        assertTrue(errors(() -> service.deleteOwnAccount("U001", "wrong")).containsKey("password"));
        assertEquals(AccountStatus.ACTIVE, repository.findById("U001").orElseThrow().getStatus());

        service.deleteOwnAccount("U001", "Bakery2026");
        User deleted = repository.findById("U001").orElseThrow();
        assertEquals(AccountStatus.DELETED, deleted.getStatus());
        assertEquals("Deleted user", deleted.getFullName());
        // The same email can register again, and gets a new ID.
        assertEquals("U002", register("Nimal Perera", "nimal@example.com").id());
    }

    @Test
    void lastActiveAdminCannotDeleteOwnAccount() {
        service.createAdmin("First Admin", "admin1@example.com", "Admin2026x");
        assertThrows(IllegalStateException.class, () -> service.deleteOwnAccount("U001", "Admin2026x"));

        service.createAdmin("Second Admin", "admin2@example.com", "Admin2026y");
        service.deleteOwnAccount("U001", "Admin2026x");
        assertEquals(AccountStatus.DELETED, repository.findById("U001").orElseThrow().getStatus());
    }

    // ── Admin management ──────────────────────────────────────────────────────

    @Test
    void adminCanUpgradeCustomerToLoyalty() {
        register("Nimal Perera", "nimal@example.com");
        UserView view = service.adminUpdateCustomer("U001", "Nimal Perera", "nimal@example.com", "077 123 4567",
                "12 Lake Road, Kandy", CustomerType.LOYALTY);
        assertEquals(CustomerType.LOYALTY, view.customerType());
        assertEquals(new BigDecimal("250.00"), view.exampleDiscount());
        User stored = repository.findById("U001").orElseThrow();
        assertInstanceOf(LoyaltyCustomer.class, stored);
        assertTrue(encoder.matches("Bakery2026", stored.getPasswordHash()), "type change keeps the password");
    }

    @Test
    void adminCannotManageAdministratorAccountsHere() {
        service.createAdmin("Bakery Admin", "admin@example.com", "Admin2026x");
        assertThrows(IllegalArgumentException.class, () -> service.setCustomerActive("U001", false));
        assertThrows(IllegalArgumentException.class, () -> service.adminDeleteCustomer("U001"));
        assertThrows(IllegalArgumentException.class, () -> service.getCustomerForAdmin("U001"));
        assertEquals(AccountStatus.ACTIVE, repository.findById("U001").orElseThrow().getStatus());
    }

    @Test
    void adminDeactivatesActivatesAndCounts() {
        register("Nimal Perera", "nimal@example.com");
        register("Kamala Silva", "kamala@example.com");
        service.setCustomerActive("U002", false);
        UserService.CustomerStats stats = service.customerStats();
        assertEquals(2, stats.total());
        assertEquals(1, stats.active());
        assertEquals(1, stats.inactive());

        service.setCustomerActive("U002", true);
        assertEquals(2, service.customerStats().active());
    }

    @Test
    void adminSearchesByNameEmailOrId() {
        register("Nimal Perera", "nimal@example.com");
        register("Kamala Silva", "kamala@example.com");
        assertEquals(List.of("U001"), service.listCustomers("perera", "").stream().map(UserView::id).toList());
        assertEquals(List.of("U002"), service.listCustomers("KAMALA@", "").stream().map(UserView::id).toList());
        assertEquals(List.of("U002"), service.listCustomers("u002", null).stream().map(UserView::id).toList());
        service.adminDeleteCustomer("U001");
        assertEquals(List.of("U002"), service.listCustomers("", "").stream().map(UserView::id).toList());
        assertEquals(List.of("U001"), service.listCustomers("", "DELETED").stream().map(UserView::id).toList());
    }
}
