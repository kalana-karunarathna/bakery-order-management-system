package com.bakery.user.controller;

import com.bakery.user.model.CustomerType;
import com.bakery.user.service.UserView;

/**
 * Editable account details. Used for a user's own profile (customerType ignored) and for the
 * admin's customer edit form (customerType applied).
 */
public class ProfileForm {

    static final String[] OWN_FIELDS = {"fullName", "email", "phone", "address"};
    static final String[] ADMIN_FIELDS = {"fullName", "email", "phone", "address", "customerType"};

    private String fullName = "";
    private String email = "";
    private String phone = "";
    private String address = "";
    private CustomerType customerType;

    static ProfileForm from(UserView view) {
        ProfileForm form = new ProfileForm();
        form.fullName = view.fullName();
        form.email = view.email();
        form.phone = view.phone();
        form.address = view.address();
        form.customerType = view.customerType();
        return form;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public CustomerType getCustomerType() { return customerType; }
    public void setCustomerType(CustomerType customerType) { this.customerType = customerType; }
}
