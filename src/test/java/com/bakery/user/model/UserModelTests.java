package com.bakery.user.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OOP behaviour of the account model: inheritance, encapsulation, abstraction and polymorphism. */
class UserModelTests {

    /** A syntactically valid BCrypt hash (cost 10) for model tests; not a real password. */
    static final String HASH = "$2a$10$" + "a".repeat(53);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static RegularCustomer regular() {
        return new RegularCustomer("U001", "Nimal Perera", "nimal@example.com", "077 123 4567",
                "12 Lake Road, Kandy", HASH, AccountStatus.ACTIVE, TODAY);
    }

    private static LoyaltyCustomer loyalty() {
        return new LoyaltyCustomer("U002", "Kamala Silva", "kamala@example.com", "077 765 4321",
                "8 Hill Street, Galle", HASH, AccountStatus.ACTIVE, TODAY);
    }

    private static AdminUser admin() {
        return new AdminUser("U003", "Bakery Admin", "admin@example.com", "", "", HASH, AccountStatus.ACTIVE, TODAY);
    }

    // ── Discounts (abstraction + polymorphism) ────────────────────────────────

    @Test
    void regularCustomerGetsNoDiscount() {
        assertEquals(new BigDecimal("0.00"), regular().calculateDiscount(new BigDecimal("5000.00")));
        assertEquals(new BigDecimal("5000.00"), regular().applyDiscount(new BigDecimal("5000.00")));
    }

    @Test
    void loyaltyCustomerGetsFivePercentRoundedHalfUp() {
        assertEquals(new BigDecimal("250.00"), loyalty().calculateDiscount(new BigDecimal("5000.00")));
        assertEquals(new BigDecimal("10.00"), loyalty().calculateDiscount(new BigDecimal("199.99"))); // 9.9995
        assertEquals(new BigDecimal("950.00"), loyalty().applyDiscount(new BigDecimal("1000.00")));
    }

    @Test
    void sameCallBehavesDifferentlyPerSubclass() {
        List<DiscountPolicy> accounts = List.of(regular(), loyalty(), admin());
        List<BigDecimal> discounts = accounts.stream()
                .map(account -> account.calculateDiscount(new BigDecimal("1000.00")))
                .toList();
        assertEquals(List.of(new BigDecimal("0.00"), new BigDecimal("50.00"), new BigDecimal("0.00")), discounts);
    }

    @Test
    void discountRejectsNegativeOrMissingSubtotal() {
        assertThrows(IllegalArgumentException.class, () -> loyalty().calculateDiscount(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> regular().calculateDiscount(null));
    }

    // ── Inheritance: role and type fixed by class ─────────────────────────────

    @Test
    void roleAndCustomerTypeComeFromTheSubclass() {
        assertEquals(UserRole.CUSTOMER, regular().getRole());
        assertEquals(CustomerType.REGULAR, regular().getCustomerType());
        assertEquals(UserRole.CUSTOMER, loyalty().getRole());
        assertEquals(CustomerType.LOYALTY, loyalty().getCustomerType());
        assertEquals(UserRole.ADMIN, admin().getRole());
        assertTrue(admin().isAdmin());
        assertInstanceOf(User.class, loyalty());
    }

    // ── Encapsulation ─────────────────────────────────────────────────────────

    @Test
    void emailIsNormalisedAndValidated() {
        User user = regular();
        user.changeEmail("  New.Address@Example.COM ");
        assertEquals("new.address@example.com", user.getEmail());
        assertThrows(IllegalArgumentException.class, () -> user.changeEmail("not-an-email"));
        assertEquals("new.address@example.com", user.getEmail(), "invalid change must leave state untouched");
    }

    @Test
    void delimiterAndLineBreaksAreRejected() {
        User user = regular();
        assertThrows(IllegalArgumentException.class, () -> user.changeFullName("Nimal | Perera"));
        assertThrows(IllegalArgumentException.class, () -> user.changeAddress("12 Lake Road\nKandy"));
        assertThrows(IllegalArgumentException.class, () -> user.changePhone("077\r123"));
    }

    @Test
    void plainTextPasswordsCannotBeStored() {
        User user = regular();
        assertThrows(IllegalArgumentException.class, () -> user.changePasswordHash("Secret123"));
        assertThrows(IllegalArgumentException.class, () -> user.changePasswordHash(null));
    }

    @Test
    void activeAccountMustHaveAPassword() {
        assertThrows(IllegalArgumentException.class, () -> new RegularCustomer(null, "No Pass", "np@example.com",
                "", "", User.NO_PASSWORD, AccountStatus.ACTIVE, TODAY));
        User legacy = new RegularCustomer("U009", "Legacy User", "legacy@example.com", "", "",
                User.NO_PASSWORD, AccountStatus.INACTIVE, TODAY);
        assertThrows(IllegalStateException.class, legacy::activate);
    }

    @Test
    void idCanOnlyBeAssignedOnce() {
        User user = new RegularCustomer(null, "New Person", "new@example.com", "", "", HASH, AccountStatus.ACTIVE, TODAY);
        user.assignId("U010");
        assertThrows(IllegalStateException.class, () -> user.assignId("U011"));
        assertThrows(IllegalArgumentException.class,
                () -> new RegularCustomer(null, "X Y", "x@example.com", "", "", HASH, AccountStatus.ACTIVE, TODAY).assignId("../U1"));
    }

    @Test
    void markDeletedErasesPersonalDetailsButKeepsId() {
        User user = regular();
        user.markDeleted();
        assertEquals("U001", user.getId());
        assertEquals("Deleted user", user.getFullName());
        assertEquals("deleted+u001@deleted.invalid", user.getEmail());
        assertEquals("", user.getPhone());
        assertEquals("", user.getAddress());
        assertFalse(user.hasUsablePassword());
        assertEquals(AccountStatus.DELETED, user.getStatus());
        assertThrows(IllegalStateException.class, user::activate);
    }

    @Test
    void toStringNeverContainsThePasswordHash() {
        assertFalse(regular().toString().contains(HASH));
    }
}
