package com.bakery.user.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Base class for every account (inheritance).
 *
 * <p><b>Encapsulation:</b> all fields are private. State only changes through methods that
 * validate their input, so an invalid {@code User} cannot be built or produced by a setter.
 * The role is not stored as a free field: each subclass decides it, so no form value can turn a
 * customer into an administrator.</p>
 *
 * <p><b>Abstraction / polymorphism:</b> {@link DiscountPolicy#calculateDiscount(BigDecimal)},
 * {@link #getRole()} and {@link #getCustomerType()} are implemented differently by
 * {@link RegularCustomer}, {@link LoyaltyCustomer} and {@link AdminUser}.</p>
 */
public abstract class User implements DiscountPolicy {

    /** Stored instead of a hash for accounts that have no usable password (e.g. migrated records). */
    public static final String NO_PASSWORD = "-";

    private static final Pattern ID = Pattern.compile("^U\\d{3,}$");
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private String id;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private String passwordHash;
    private AccountStatus status;
    private final LocalDate createdDate;

    /**
     * @param id           {@code null} for a new account (the repository assigns one), otherwise e.g. {@code U001}
     * @param passwordHash a BCrypt hash, or {@link #NO_PASSWORD}
     */
    protected User(String id, String fullName, String email, String phone, String address,
                   String passwordHash, AccountStatus status, LocalDate createdDate) {
        if (id != null) {
            assignId(id);
        }
        changeFullName(fullName);
        changeEmail(email);
        changePhone(phone);
        changeAddress(address);
        changePasswordHash(passwordHash);
        this.status = Objects.requireNonNull(status, "status");
        this.createdDate = Objects.requireNonNull(createdDate, "createdDate");
        if (status == AccountStatus.ACTIVE && !hasUsablePassword()) {
            throw new IllegalArgumentException("An active account must have a password.");
        }
    }

    // ── Polymorphic behaviour ─────────────────────────────────────────────────

    /** Permission level, fixed by the account's class. */
    public abstract UserRole getRole();

    /** Customer category, fixed by the account's class. */
    public abstract CustomerType getCustomerType();

    /** Shared guard for subclasses' discount calculations. */
    protected static BigDecimal requireValidSubtotal(BigDecimal subtotal) {
        if (subtotal == null || subtotal.signum() < 0) {
            throw new IllegalArgumentException("Subtotal must be zero or more.");
        }
        return subtotal;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public AccountStatus getStatus() { return status; }
    public LocalDate getCreatedDate() { return createdDate; }

    /**
     * Stored password hash. Only the repository (persistence) and the authentication service
     * read this; it is never put into a view model.
     */
    public String getPasswordHash() { return passwordHash; }

    public boolean hasUsablePassword() { return !NO_PASSWORD.equals(passwordHash); }
    public boolean isActive() { return status == AccountStatus.ACTIVE; }
    public boolean isDeleted() { return status == AccountStatus.DELETED; }
    public boolean isAdmin() { return getRole() == UserRole.ADMIN; }

    // ── Validated state changes ───────────────────────────────────────────────

    /** Sets the ID once; used by the repository when a new account is saved. */
    public final void assignId(String newId) {
        if (this.id != null) {
            throw new IllegalStateException("The account already has an ID.");
        }
        if (newId == null || !ID.matcher(newId).matches()) {
            throw new IllegalArgumentException("Account IDs look like U001.");
        }
        this.id = newId;
    }

    public final void changeFullName(String newName) {
        String value = UserInputRules.normalizeText(newName);
        requireValid(UserInputRules.nameError(value));
        this.fullName = value;
    }

    public final void changeEmail(String newEmail) {
        String value = UserInputRules.normalizeEmail(newEmail);
        requireValid(UserInputRules.emailError(value));
        this.email = value;
    }

    public final void changePhone(String newPhone) {
        String value = UserInputRules.normalizeText(newPhone);
        requireValid(UserInputRules.phoneError(value, false));
        this.phone = value;
    }

    public final void changeAddress(String newAddress) {
        String value = UserInputRules.normalizeText(newAddress);
        requireValid(UserInputRules.addressError(value, false));
        this.address = value;
    }

    /** Accepts only a BCrypt hash or {@link #NO_PASSWORD}; plain text is rejected. */
    public final void changePasswordHash(String newHash) {
        if (newHash == null || !(NO_PASSWORD.equals(newHash) || BCRYPT.matcher(newHash).matches())) {
            throw new IllegalArgumentException("Password must be stored as a BCrypt hash.");
        }
        this.passwordHash = newHash;
    }

    public final void activate() {
        if (isDeleted()) throw new IllegalStateException("A deleted account cannot be reactivated.");
        if (!hasUsablePassword()) throw new IllegalStateException("This account has no password, so it cannot be activated.");
        this.status = AccountStatus.ACTIVE;
    }

    public final void deactivate() {
        if (isDeleted()) throw new IllegalStateException("A deleted account cannot be changed.");
        this.status = AccountStatus.INACTIVE;
    }

    /**
     * Erases personal details but keeps the ID, so orders that reference it still resolve and
     * the ID is never handed to someone else. The email becomes a unique placeholder so the real
     * address can be used to register again.
     */
    public final void markDeleted() {
        if (id == null) throw new IllegalStateException("Only saved accounts can be deleted.");
        this.fullName = "Deleted user";
        this.email = "deleted+" + id.toLowerCase() + "@deleted.invalid";
        this.phone = "";
        this.address = "";
        this.passwordHash = NO_PASSWORD;
        this.status = AccountStatus.DELETED;
    }

    private static void requireValid(String error) {
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }

    /** Never prints the password hash. */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", role=" + getRole() + ", status=" + status + "}";
    }
}
