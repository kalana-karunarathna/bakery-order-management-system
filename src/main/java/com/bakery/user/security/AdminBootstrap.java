package com.bakery.user.security;

import com.bakery.user.repository.UserRepository;
import com.bakery.user.service.UserService;
import com.bakery.user.service.UserValidationException;
import com.bakery.user.service.UserView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * <b>Development only.</b> Creates the first administrator from environment variables when
 * {@code BAKERY_ADMIN_BOOTSTRAP=true}. There is no default password and no public admin sign-up.
 *
 * <ul>
 *   <li>Missing email or password: logs a warning and does nothing (the app still starts).</li>
 *   <li>Email already registered: does nothing; existing accounts are never overwritten.</li>
 *   <li>The password is validated like any other and is never logged.</li>
 * </ul>
 * See docs/USER_MANAGEMENT.md for the steps.
 */
@Component
@ConditionalOnProperty(name = "bakery.admin.bootstrap.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserService userService;
    private final UserRepository repository;
    private final String email;
    private final String password;
    private final String name;

    public AdminBootstrap(UserService userService, UserRepository repository,
                          @Value("${bakery.admin.bootstrap.email:}") String email,
                          @Value("${bakery.admin.bootstrap.password:}") String password,
                          @Value("${bakery.admin.bootstrap.name:Bakery Admin}") String name) {
        this.userService = userService;
        this.repository = repository;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isEmpty()) {
            log.warn("Admin bootstrap skipped: set both BAKERY_ADMIN_EMAIL and BAKERY_ADMIN_PASSWORD.");
            return;
        }
        var existing = repository.findByEmail(email);
        if (existing.isPresent()) {
            // Never overwrite or promote an existing account, and never change its password.
            var account = existing.get();
            if (account.isAdmin()) {
                log.info("Admin bootstrap skipped: administrator {} already exists (status {}). Sign in with its existing password; "
                        + "the BAKERY_ADMIN_PASSWORD value was not used.", account.getId(), account.getStatus());
            } else {
                log.warn("Admin bootstrap skipped: that email belongs to customer account {}, which is not promoted to ADMIN. "
                        + "Use a different BAKERY_ADMIN_EMAIL.", account.getId());
            }
            return;
        }
        try {
            UserView admin = userService.createAdmin(name, email, password);
            log.info("Admin bootstrap created administrator {}. Remove BAKERY_ADMIN_PASSWORD from your environment now.",
                    admin.id());
        } catch (UserValidationException e) {
            // Messages describe the rule that failed; they never contain the password.
            log.warn("Admin bootstrap skipped: {}", e.getFieldErrors().values());
        }
    }
}
