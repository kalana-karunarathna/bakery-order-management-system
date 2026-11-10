package com.bakery.user.controller;

/**
 * Registration form fields. There is intentionally no role or customer-type field: every
 * registration becomes a regular CUSTOMER account (see {@code UserService#register}).
 */
public class RegistrationForm {

    static final String[] FIELDS = {"fullName", "email", "phone", "address", "password", "confirmPassword"};

    private String fullName = "";
    private String email = "";
    private String phone = "";
    private String address = "";
    private String password = "";
    private String confirmPassword = "";

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    /** Passwords are never sent back to the browser when the form is shown again. */
    void clearPasswords() {
        this.password = "";
        this.confirmPassword = "";
    }
}
