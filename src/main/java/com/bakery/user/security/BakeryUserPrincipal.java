package com.bakery.user.security;

import com.bakery.user.model.CustomerType;
import com.bakery.user.model.User;
import com.bakery.user.model.UserRole;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The signed-in account as Spring Security sees it. Controllers obtain it with
 * {@code @AuthenticationPrincipal BakeryUserPrincipal principal} and use {@link #getId()} as the
 * trusted identity; never a customer ID from the request.
 *
 * <p>The password hash is only held until authentication finishes; Spring Security then calls
 * {@link #eraseCredentials()}, so it is not kept in the session.</p>
 */
public class BakeryUserPrincipal implements UserDetails, CredentialsContainer {

    private final String id;
    private final String email;
    private final String fullName;
    private final UserRole role;
    private final CustomerType customerType;
    private final boolean enabled;
    private String passwordHash;

    public BakeryUserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.fullName = user.getFullName();
        this.role = user.getRole();
        this.customerType = user.getCustomerType();
        this.enabled = user.isActive() && user.hasUsablePassword();
        this.passwordHash = user.getPasswordHash();
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public UserRole getRole() { return role; }
    public CustomerType getCustomerType() { return customerType; }
    public boolean isAdmin() { return role == UserRole.ADMIN; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void eraseCredentials() { this.passwordHash = null; }

    @Override
    public String toString() {
        return "BakeryUserPrincipal{id=" + id + ", role=" + role + "}";
    }
}
