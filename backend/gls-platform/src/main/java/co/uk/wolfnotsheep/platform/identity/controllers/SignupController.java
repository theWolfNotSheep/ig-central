package co.uk.wolfnotsheep.platform.identity.controllers;

import co.uk.wolfnotsheep.platform.identity.models.SignUpMethod;
import co.uk.wolfnotsheep.platform.identity.models.UserAccountType;
import co.uk.wolfnotsheep.platform.identity.models.UserFactory;
import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.repositories.MongoUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SignupController {

    private static final Logger log = LoggerFactory.getLogger(SignupController.class);

    private final MongoUserRepository userRepo;
    private final UserFactory userFactory;
    private final PasswordEncoder passwordEncoder;

    public SignupController(MongoUserRepository userRepo, UserFactory userFactory, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.userFactory = userFactory;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/api/auth/public/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {

        if (request.email() == null || request.email().isBlank() ||
                request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email and password are required"));
        }

        if (userRepo.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Email already registered"));
        }

        UserModel user = userFactory.createUser(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setAccountType(UserAccountType.APP_ACCOUNT);
        user.setSignUpMethod(SignUpMethod.WEB_FORM);

        // Enable account immediately (no email verification for now)
        user.getAccountLocks().setAccountNonDisabled(true);

        userRepo.save(user);

        log.info("New user registered: {}", request.email());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Account created successfully",
                "email", request.email()
        ));
    }

    public record SignupRequest(
            String email,
            String password
    ) {}
}
