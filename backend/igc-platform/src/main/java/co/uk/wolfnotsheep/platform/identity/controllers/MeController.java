package co.uk.wolfnotsheep.platform.identity.controllers;

import co.uk.wolfnotsheep.platform.identity.models.SignUpMethod;
import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.repositories.MongoUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class MeController {

    private static final Set<String> OAUTH_METHODS = Set.of(
            SignUpMethod.GOOGLE.name(), SignUpMethod.GITHUB.name(), SignUpMethod.LINKEDIN.name());

    private final MongoUserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public MeController(MongoUserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/api/user/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal UserModel principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String userId = principal.getId();
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        MeResponse response = new MeResponse(
                true,
                userId,
                principal.getFirstName() != null ? principal.getFirstName() : principal.getEmail(),
                principal.getLastName() != null ? principal.getLastName() : "",
                principal.getDisplayName(),
                principal.getAvatarUrl(),
                principal.getRoles() != null ? List.copyOf(principal.getRoles()) : List.of(),
                principal.getPermissions() != null ? List.copyOf(principal.getPermissions()) : List.of(),
                principal.getAccountType() != null ? principal.getAccountType().name() : null,
                principal.getSensitivityClearanceLevel(),
                principal.getSignUpMethod(),
                principal.getIdentity() != null ? principal.getIdentity().getProvider() : null
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/user/me/password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal UserModel principal,
                                            @RequestBody ChangePasswordRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Block OAuth users — they don't have a local password
        String signUpMethod = principal.getSignUpMethod();
        if (signUpMethod != null && OAUTH_METHODS.contains(signUpMethod)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "OAUTH_ACCOUNT",
                    "message", "Password cannot be changed for OAuth accounts. Manage your password with your identity provider."));
        }

        if (request.currentPassword() == null || request.newPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "MISSING_FIELDS",
                    "message", "Both current password and new password are required."));
        }

        if (request.newPassword().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "PASSWORD_TOO_SHORT",
                    "message", "New password must be at least 8 characters."));
        }

        // Reload from DB to get the stored password hash
        return userRepo.findById(principal.getId())
                .map(user -> {
                    if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "error", "WRONG_PASSWORD",
                                "message", "Current password is incorrect."));
                    }
                    user.setPassword(passwordEncoder.encode(request.newPassword()));
                    userRepo.save(user);
                    return ResponseEntity.ok(Map.of("status", "password_changed"));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "USER_NOT_FOUND",
                        "message", "User not found.")));
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    public record MeResponse(
            boolean isAuthenticated,
            String userId,
            String firstName,
            String lastName,
            String displayName,
            String avatarUrl,
            List<String> roles,
            List<String> permissions,
            String accountType,
            int sensitivityClearanceLevel,
            String signUpMethod,
            String identityProvider
    ) {}
}
