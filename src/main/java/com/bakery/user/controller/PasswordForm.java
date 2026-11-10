package com.bakery.user.controller;

/** Change-password form. Values are never rendered back into the page. */
public class PasswordForm {

    static final String[] FIELDS = {"currentPassword", "newPassword", "confirmPassword"};

    private String currentPassword = "";
    private String newPassword = "";
    private String confirmPassword = "";

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    void clear() {
        currentPassword = "";
        newPassword = "";
        confirmPassword = "";
    }
}
