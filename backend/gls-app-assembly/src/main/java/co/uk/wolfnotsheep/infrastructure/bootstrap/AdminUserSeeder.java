package co.uk.wolfnotsheep.infrastructure.bootstrap;

import co.uk.wolfnotsheep.platform.identity.models.UserAccountType;
import co.uk.wolfnotsheep.platform.identity.models.UserFactory;
import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.models.SignUpMethod;
import co.uk.wolfnotsheep.platform.identity.repositories.MongoUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final MongoUserRepository userRepo;
    private final UserFactory userFactory;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    public AdminUserSeeder(
            MongoUserRepository userRepo,
            UserFactory userFactory,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepo = userRepo;
        this.userFactory = userFactory;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (userRepo.existsByEmail(adminEmail)) {
            log.info("Admin user already exists ({}), skipping bootstrap.", adminEmail);
            return;
        }

        UserModel user = userFactory.createUser(adminEmail);
        user.setPassword(passwordEncoder.encode(adminPassword));
        user.setAccountType(UserAccountType.ADMIN_ACCOUNT);
        user.setSignUpMethod(SignUpMethod.ADMIN_CREATED);
        user.getRoles().add("ADMIN");

        // Enable the account
        user.getAccountLocks().setAccountNonDisabled(true);

        userRepo.save(user);

        log.info("Admin user bootstrapped successfully: {}", adminEmail);
    }
}
