package com.bakery.user.model;

/**
 * Lifecycle of an account.
 * <ul>
 *   <li>{@code ACTIVE}: can sign in.</li>
 *   <li>{@code INACTIVE}: kept but cannot sign in (deactivated by an admin, or a migrated
 *       legacy record without a password).</li>
 *   <li>{@code DELETED}: personal details erased; the ID is kept so order history still resolves
 *       and the ID is never reused.</li>
 * </ul>
 */
public enum AccountStatus {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    DELETED("Deleted");

    private final String label;

    AccountStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
