package co.uk.wolfnotsheep.platform.identity.controllers;

import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MeController {

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
                principal.getEmail(),
                principal.getEmail(),
                principal.getRoles() != null ? List.copyOf(principal.getRoles()) : List.of(),
                principal.getPermissions() != null ? List.copyOf(principal.getPermissions()) : List.of(),
                principal.getAccountType() != null ? principal.getAccountType().name() : null
        );

        return ResponseEntity.ok(response);
    }

    public record MeResponse(
            boolean isAuthenticated,
            String userId,
            String firstName,
            String lastName,
            List<String> roles,
            List<String> permissions,
            String accountType
    ) {}
}
