package com.bakery.user.model;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalisation and validation rules for account fields, shared by the model (which rejects
 * invalid state) and the service (which turns the messages into form errors).
 *
 * <p>Every check method returns {@code null} when the value is valid, or a user-facing message.
 * Messages never echo the submitted value back.</p>
 */
public final class UserInputRules {

    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 60;
    public static final int EMAIL_MAX = 254;
    public static final int ADDRESS_MIN = 5;
    public static final int ADDRESS_MAX = 200;
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 64;
    /** BCrypt only uses the first 72 bytes of a password. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private static final Pattern EMAIL =
            Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");
    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L}\\p{M} .'-]*$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()-]{7,20}$");
    /** Ordinary spaces only: line breaks and tabs are left in place so validation rejects them. */
    private static final Pattern WHITESPACE = Pattern.compile("[\\p{Zs}]+");

    private UserInputRules() {
    }

    // ── Normalisation ─────────────────────────────────────────────────────────

    /** Trims and collapses runs of spaces; {@code null} becomes an empty string. */
    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return WHITESPACE.matcher(value.strip()).replaceAll(" ");
    }

    /** Trims and lower-cases an email address so look-ups are case-insensitive. */
    public static String normalizeEmail(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * True if the value contains the file delimiter {@code |}, a line break or another control
     * character. Such values would corrupt the one-record-per-line {@code users.txt} format.
     */
    public static boolean containsUnsafeCharacters(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '|' || Character.isISOControl(c) || c == ' ' || c == ' ') {
                return true;
            }
        }
        return false;
    }

    // ── Field checks (null = valid) ───────────────────────────────────────────

    public static String nameError(String name) {
        if (name == null || name.isBlank()) return "Please enter your full name.";
        if (containsUnsafeCharacters(name)) return "Name contains characters that are not allowed.";
        if (name.length() < NAME_MIN || name.length() > NAME_MAX)
            return "Name must be between " + NAME_MIN + " and " + NAME_MAX + " characters.";
        if (!NAME.matcher(name).matches())
            return "Name can only contain letters, spaces, apostrophes, dots and hyphens.";
        return null;
    }

    public static String emailError(String email) {
        if (email == null || email.isBlank()) return "Please enter your email address.";
        if (containsUnsafeCharacters(email) || email.length() > EMAIL_MAX || !EMAIL.matcher(email).matches())
            return "Please enter a valid email address, for example name@example.com.";
        return null;
    }

    /** Phone is optional at model level (deleted accounts have none) but required on forms. */
    public static String phoneError(String phone, boolean required) {
        if (phone == null || phone.isBlank()) return required ? "Please enter your phone number." : null;
        if (containsUnsafeCharacters(phone) || !PHONE.matcher(phone).matches()
                || phone.chars().filter(Character::isDigit).count() < 7)
            return "Please enter a valid phone number (digits, spaces, +, - and brackets).";
        return null;
    }

    /** Address is optional at model level but required on forms. */
    public static String addressError(String address, boolean required) {
        if (address == null || address.isBlank()) return required ? "Please enter your address." : null;
        if (containsUnsafeCharacters(address)) return "Address contains characters that are not allowed (such as |).";
        if (address.length() < ADDRESS_MIN || address.length() > ADDRESS_MAX)
            return "Address must be between " + ADDRESS_MIN + " and " + ADDRESS_MAX + " characters.";
        return null;
    }

    /**
     * Password policy: 8 to 64 characters, at least one letter and one digit,
     * no control characters, and not the same as the email address.
     */
    public static String passwordError(String password, String normalizedEmail) {
        if (password == null || password.isEmpty()) return "Please enter a password.";
        if (password.length() < PASSWORD_MIN)
            return "Password must be at least " + PASSWORD_MIN + " characters long.";
        if (password.length() > PASSWORD_MAX
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES)
            return "Password must be at most " + PASSWORD_MAX + " characters long.";
        if (password.chars().anyMatch(Character::isISOControl))
            return "Password contains characters that are not allowed.";
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit))
            return "Password must include at least one letter and one number.";
        if (normalizedEmail != null && !normalizedEmail.isEmpty()
                && password.equalsIgnoreCase(normalizedEmail))
            return "Password must not be the same as your email address.";
        return null;
    }
}
