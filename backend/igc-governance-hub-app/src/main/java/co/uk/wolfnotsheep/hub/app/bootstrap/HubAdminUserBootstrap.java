package co.uk.wolfnotsheep.hub.app.bootstrap;

import co.uk.wolfnotsheep.hub.app.config.HubSecurityConfig;
import co.uk.wolfnotsheep.hub.models.HubUser;
import co.uk.wolfnotsheep.hub.repositories.HubUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * On first startup, ensures the env-configured admin user exists in MongoDB.
 * Subsequent updates to the env vars do NOT overwrite the DB user — the DB
 * is the source of truth once seeded. Use the user manager UI to change passwords.
 */
@Component
@Order(0) // Run before other bootstrap (HubDataSeeder is Order 1)
public class HubAdminUserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HubAdminUserBootstrap.class);

    private final HubUserRepository userRepo;
    private final HubSecurityConfig securityConfig;
    private final PasswordEncoder passwordEncoder;

    public HubAdminUserBootstrap(HubUserRepository userRepo, HubSecurityConfig securityConfig, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.securityConfig = securityConfig;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepo.count() > 0) {
            log.info("Hub users already exist ({}), skipping bootstrap.", userRepo.count());
            return;
        }
        String username = securityConfig.getEnvAdminUsername();
        String password = securityConfig.getEnvAdminPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("HUB_ADMIN_USERNAME / HUB_ADMIN_PASSWORD not set — no bootstrap admin created.");
            return;
        }
        HubUser u = new HubUser();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setDisplayName("Hub Administrator");
        u.setRoles(Set.of("HUB_ADMIN"));
        u.setActive(true);
        u.setCreatedAt(Instant.now());
        u.setCreatedBy("bootstrap");
        userRepo.save(u);
        log.info("Bootstrapped hub admin user: {}", username);
    }
}
