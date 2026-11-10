package com.bakery.user.model;

/** Kind of customer account. Staff (admin) accounts use {@link #NONE}. */
public enum CustomerType {
    REGULAR("Regular customer"),
    LOYALTY("Loyalty customer"),
    NONE("Staff account");

    private final String label;

    CustomerType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
