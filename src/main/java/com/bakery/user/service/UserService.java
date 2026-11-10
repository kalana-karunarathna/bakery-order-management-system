package com.bakery.user.service;

import com.bakery.user.model.AccountStatus;
import com.bakery.user.model.AdminUser;
import com.bakery.user.model.CustomerType;
import com.bakery.user.model.LoyaltyCustomer;
import com.bakery.user.model.RegularCustomer;
import com.bakery.user.model.User;
import com.bakery.user.model.UserInputRules;
import com.bakery.user.model.UserRole;
import com.bakery.user.repository.DuplicateEmailException;
import com.bakery.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Business rules for accounts: registration, profile changes, passwords and admin management.
 *
 * <p>Every method that changes an account takes the acting user's ID from the authenticated
 * session (passed in by the controller), never from a request parameter, and re-checks
 * ownership and roles here, independent of what the page showed.</p>
 */
@Service
public class UserService {

    public static final String EMAIL_TAKEN = "An account with this email address already exists.";
    private static final String WRONG_PASSWORD = "Your current password is incorrect.";

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers a new customer. The account is always a {@link RegularCustomer} with the CUSTOMER
     * role, whatever else the request contains.
     */
    public UserView register(String fullName, String email, String phone, String address,
                             String password, String confirmPassword) {
        String name = UserInputRules.normalizeText(fullName);
        String mail = UserInputRules.normalizeEmail(email);
        String tel = UserInputRules.normalizeText(phone);
        String addr = UserInputRules.normalizeText(address);

        Map<String, String> errors = new LinkedHashMap<>();
        put(errors, "fullName", UserInputRules.nameError(name));
        put(errors, "email", UserInputRules.emailError(mail));
        put(errors, "phone", UserInputRules.phoneError(tel, true));
        put(errors, "address", UserInputRules.addressError(addr, true));
        put(errors, "password", UserInputRules.passwordError(password, mail));
        if (!errors.containsKey("password") && !password.equals(confirmPassword)) {
            errors.put("confirmPassword", "Passwords do not match.");
        }
        if (!errors.containsKey("email") && repository.findByEmail(mail).isPresent()) {
            errors.put("email", EMAIL_TAKEN);
        }
        throwIfAny(errors);

        User customer = new RegularCustomer(null, name, mail, tel, addr,
                passwordEncoder.encode(password), AccountStatus.ACTIVE, LocalDate.now());
        try {
            return UserView.of(repository.save(customer));
        } catch (DuplicateEmailException e) {
            // Another registration with the same email won the race.
            throw UserValidationException.of("email", EMAIL_TAKEN);
        }
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    public Optional<UserView> findView(String id) {
        return repository.findById(id).map(UserView::of);
    }

    public UserView getView(String id) {
        return findView(id).orElseThrow(() -> new IllegalArgumentException("Account not found."));
    }

    // ── Own profile ───────────────────────────────────────────────────────────

    /** Updates the signed-in user's own details. Role and customer type are not editable here. */
    public UserView updateOwnProfile(String userId, String fullName, String email, String phone, String address) {
        ProfileValues values = validateProfile(userId, fullName, email, phone, address, !isAdminId(userId));
        return save(userId, (current, all) -> {
            requireNotDeleted(current);
            applyProfile(current, values);
            return current;
        });
    }

    /** Changes the signed-in user's password after verifying the current one. */
    public void changePassword(String userId, String currentPassword, String newPassword, String confirmPassword) {
        User user = repository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Account not found."));
        Map<String, String> errors = new LinkedHashMap<>();
        if (currentPassword == null || currentPassword.isEmpty() || !matches(currentPassword, user)) {
            errors.put("currentPassword", WRONG_PASSWORD);
        }
        put(errors, "newPassword", UserInputRules.passwordError(newPassword, user.getEmail()));
        if (!errors.containsKey("newPassword") && !newPassword.equals(confirmPassword)) {
            errors.put("confirmPassword", "Passwords do not match.");
        }
        if (errors.isEmpty() && matches(newPassword, user)) {
            errors.put("newPassword", "Choose a password different from your current one.");
        }
        throwIfAny(errors);

        String hash = passwordEncoder.encode(newPassword);
        repository.update(userId, (current, all) -> {
            requireNotDeleted(current);
            current.changePasswordHash(hash);
            return current;
        });
    }

    /**
     * Deletes the signed-in user's own account after password confirmation.
     * The record is anonymised rather than removed, so order history keeps a valid customer ID.
     * The last active administrator cannot delete their own account.
     */
    public void deleteOwnAccount(String userId, String password) {
        User user = repository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (password == null || password.isEmpty() || !matches(password, user)) {
            throw UserValidationException.of("password", "Password is incorrect.");
        }
        repository.update(userId, (current, all) -> {
            requireNotDeleted(current);
            if (current.isAdmin() && countActiveAdmins(all) <= 1) {
                throw new IllegalStateException("You are the only active administrator, so this account can't be deleted.");
            }
            current.markDeleted();
            return current;
        });
    }

    // ── Admin: customer management ────────────────────────────────────────────

    /** Status filter for the admin list: {@code null} or blank = all except deleted. */
    public List<UserView> listCustomers(String query, String statusFilter) {
        String q = UserInputRules.normalizeText(query).toLowerCase(Locale.ROOT);
        AccountStatus status = parseStatus(statusFilter);
        return repository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.CUSTOMER)
                .filter(u -> status == null ? !u.isDeleted() : u.getStatus() == status)
                .filter(u -> q.isEmpty()
                        || u.getId().toLowerCase(Locale.ROOT).contains(q)
                        || u.getFullName().toLowerCase(Locale.ROOT).contains(q)
                        || u.getEmail().contains(q))
                .sorted(Comparator.comparing(User::getId))
                .map(UserView::of)
                .toList();
    }

    /** Newest customer accounts (not deleted), newest first, for the admin dashboard. */
    public List<UserView> recentCustomers(int limit) {
        return repository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.CUSTOMER && !u.isDeleted())
                .sorted(Comparator.comparing(User::getCreatedDate).thenComparing(User::getId).reversed())
                .limit(Math.max(0, limit))
                .map(UserView::of)
                .toList();
    }

    public CustomerStats customerStats() {
        List<User> customers = repository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.CUSTOMER).toList();
        long active = customers.stream().filter(User::isActive).count();
        long inactive = customers.stream().filter(u -> u.getStatus() == AccountStatus.INACTIVE).count();
        long deleted = customers.stream().filter(User::isDeleted).count();
        long loyalty = customers.stream()
                .filter(u -> !u.isDeleted() && u.getCustomerType() == CustomerType.LOYALTY).count();
        return new CustomerStats(active + inactive, active, inactive, loyalty, deleted);
    }

    public record CustomerStats(long total, long active, long inactive, long loyalty, long deleted) {
    }

    /** Returns a customer for the admin detail page; admin accounts are not managed here. */
    public UserView getCustomerForAdmin(String customerId) {
        return repository.findById(customerId)
                .filter(u -> u.getRole() == UserRole.CUSTOMER)
                .map(UserView::of)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found."));
    }

    /** Admin edit of a customer's details and customer type. Password and role are never changed here. */
    public UserView adminUpdateCustomer(String customerId, String fullName, String email, String phone,
                                        String address, CustomerType newType) {
        if (newType == null || newType == CustomerType.NONE) {
            throw UserValidationException.of("customerType", "Choose Regular or Loyalty.");
        }
        ProfileValues values = validateProfile(customerId, fullName, email, phone, address, false);
        return save(customerId, (current, all) -> {
            requireCustomer(current);
            requireNotDeleted(current);
            applyProfile(current, values);
            return withCustomerType(current, newType);
        });
    }

    public UserView setCustomerActive(String customerId, boolean active) {
        return UserView.of(repository.update(customerId, (current, all) -> {
            requireCustomer(current);
            if (active) current.activate(); else current.deactivate();
            return current;
        }));
    }

    /** Admin deletion: anonymises the customer (see {@link User#markDeleted()}). */
    public void adminDeleteCustomer(String customerId) {
        repository.update(customerId, (current, all) -> {
            requireCustomer(current);
            requireNotDeleted(current);
            current.markDeleted();
            return current;
        });
    }

    // ── Admin bootstrap ───────────────────────────────────────────────────────

    /** Used only by the development bootstrap. Validates like registration. */
    public UserView createAdmin(String fullName, String email, String password) {
        String name = UserInputRules.normalizeText(fullName);
        String mail = UserInputRules.normalizeEmail(email);
        Map<String, String> errors = new LinkedHashMap<>();
        put(errors, "fullName", UserInputRules.nameError(name));
        put(errors, "email", UserInputRules.emailError(mail));
        put(errors, "password", UserInputRules.passwordError(password, mail));
        throwIfAny(errors);
        User admin = new AdminUser(null, name, mail, "", "", passwordEncoder.encode(password),
                AccountStatus.ACTIVE, LocalDate.now());
        try {
            return UserView.of(repository.save(admin));
        } catch (DuplicateEmailException e) {
            throw UserValidationException.of("email", EMAIL_TAKEN);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private record ProfileValues(String name, String email, String phone, String address) {
    }

    private ProfileValues validateProfile(String userId, String fullName, String email, String phone,
                                          String address, boolean contactRequired) {
        String name = UserInputRules.normalizeText(fullName);
        String mail = UserInputRules.normalizeEmail(email);
        String tel = UserInputRules.normalizeText(phone);
        String addr = UserInputRules.normalizeText(address);
        Map<String, String> errors = new LinkedHashMap<>();
        put(errors, "fullName", UserInputRules.nameError(name));
        put(errors, "email", UserInputRules.emailError(mail));
        put(errors, "phone", UserInputRules.phoneError(tel, contactRequired));
        put(errors, "address", UserInputRules.addressError(addr, contactRequired));
        if (!errors.containsKey("email")) {
            repository.findByEmail(mail)
                    .filter(other -> !other.getId().equals(userId))
                    .ifPresent(other -> errors.put("email", EMAIL_TAKEN));
        }
        throwIfAny(errors);
        return new ProfileValues(name, mail, tel, addr);
    }

    private static void applyProfile(User user, ProfileValues v) {
        user.changeFullName(v.name());
        user.changeEmail(v.email());
        user.changePhone(v.phone());
        user.changeAddress(v.address());
    }

    private UserView save(String userId, UserRepository.UserChange change) {
        try {
            return UserView.of(repository.update(userId, change));
        } catch (DuplicateEmailException e) {
            throw UserValidationException.of("email", EMAIL_TAKEN);
        }
    }

    /** Builds the matching subclass, copying every field. Only CUSTOMER accounts reach this. */
    private static User withCustomerType(User user, CustomerType type) {
        if (user.getCustomerType() == type) return user;
        return switch (type) {
            case REGULAR -> new RegularCustomer(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                    user.getAddress(), user.getPasswordHash(), user.getStatus(), user.getCreatedDate());
            case LOYALTY -> new LoyaltyCustomer(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                    user.getAddress(), user.getPasswordHash(), user.getStatus(), user.getCreatedDate());
            case NONE -> throw new IllegalArgumentException("Customers need a customer type.");
        };
    }

    private boolean matches(String rawPassword, User user) {
        return user.hasUsablePassword() && passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    private boolean isAdminId(String userId) {
        return repository.findById(userId).map(User::isAdmin).orElse(false);
    }

    private static long countActiveAdmins(List<User> users) {
        return users.stream().filter(u -> u.isAdmin() && u.isActive()).count();
    }

    private static void requireCustomer(User user) {
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new IllegalArgumentException("Customer not found.");
        }
    }

    private static void requireNotDeleted(User user) {
        if (user.isDeleted()) throw new IllegalStateException("This account has been deleted.");
    }

    private static AccountStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return AccountStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void put(Map<String, String> errors, String field, String message) {
        if (message != null) errors.put(field, message);
    }

    private static void throwIfAny(Map<String, String> errors) {
        if (!errors.isEmpty()) throw new UserValidationException(errors);
    }
}
