package com.bakery.user.security;

import com.bakery.user.model.User;
import com.bakery.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads accounts for sign-in by (normalised) email. Deleted accounts and accounts without a
 * password are treated as unknown; Spring Security reports them as "bad credentials", the same
 * as a wrong password, so the login form does not reveal which emails exist.
 */
@Service
public class BakeryUserDetailsService implements UserDetailsService {

    private final UserRepository repository;

    public BakeryUserDetailsService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = repository.findByEmail(email)
                .filter(u -> !u.isDeleted() && u.hasUsablePassword())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
        return new BakeryUserPrincipal(user);
    }
}
