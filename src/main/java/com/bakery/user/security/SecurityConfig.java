package com.bakery.user.security;

import com.bakery.user.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Application-wide security, owned by User Management (Component 01).
 *
 * <h2>Access rules</h2>
 * <ul>
 *   <li>Public: home page, {@code /users} landing, {@code /login}, {@code /register},
 *       static assets, error and access-denied pages.</li>
 *   <li>Signed-in users: {@code /profile/**} (always the signed-in user's own account).</li>
 *   <li>ADMIN only: {@code /users/admin/**}.</li>
 *   <li><b>TEMPORARY:</b> {@code /products}, {@code /orders}, {@code /cake-bookings},
 *       {@code /payments}, {@code /feedback} and everything below them stay public so other
 *       modules keep working. Each module must add its own rules when it integrates sign-in
 *       (e.g. Order admin routes should become ADMIN-only). See docs/USER_MANAGEMENT.md.</li>
 *   <li>Anything else requires sign-in.</li>
 * </ul>
 *
 * <p>CSRF protection is on for every state-changing request. Thymeleaf forms that use
 * {@code th:action} get the token automatically; plain {@code action="..."} forms do not.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize as a second check on admin controllers
public class SecurityConfig {

    /** TEMPORARY public routes of modules that have not integrated authentication yet. */
    static final String[] OTHER_MODULE_ROUTES = {
            "/products", "/products/**",
            "/orders", "/orders/**",
            "/cake-bookings", "/cake-bookings/**",
            "/payments", "/payments/**",
            "/feedback", "/feedback/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Checks the password first and only then whether the account is disabled, so the
     * "account disabled" message cannot be used to discover which emails are registered.
     * Registered as a bean so it is the only authentication provider.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(BakeryUserDetailsService userDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository userRepository,
                                                   LoginFailureHandler loginFailureHandler,
                                                   RoleBasedLoginSuccessHandler loginSuccessHandler) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/vendor/**", "/images/**", "/fonts/**",
                        "/favicon.ico", "/error", "/access-denied").permitAll()
                .requestMatchers("/", "/users", "/login", "/register").permitAll()
                .requestMatchers("/users/admin", "/users/admin/**").hasRole("ADMIN")
                .requestMatchers("/profile", "/profile/**").authenticated()
                .requestMatchers(OTHER_MODULE_ROUTES).permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(loginSuccessHandler)  // ADMIN -> /users/admin, CUSTOMER -> /profile
                .failureHandler(loginFailureHandler)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")                 // POST only, because CSRF is enabled
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID"))
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.changeSessionId()))
            .exceptionHandling(errors -> errors.accessDeniedPage("/access-denied"))
            .addFilterBefore(new AccountStatusFilter(userRepository), AuthorizationFilter.class);
        // Default security headers stay on, including Cache-Control: no-store, so the browser's
        // Back button cannot show a protected page after sign-out.
        return http.build();
    }
}
