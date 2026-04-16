package co.uk.wolfnotsheep.hub.app.controllers;

import co.uk.wolfnotsheep.hub.models.HubUser;
import co.uk.wolfnotsheep.hub.repositories.HubUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/hub/admin/users")
public class UserAdminController {

    private final HubUserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public UserAdminController(HubUserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ResponseEntity<List<HubUser>> list() {
        List<HubUser> users = userRepo.findAll();
        // Never expose password hashes
        users.forEach(u -> u.setPasswordHash(null));
        return ResponseEntity.ok(users);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateUserRequest req, Authentication auth) {
        if (req.username() == null || req.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username is required"));
        }
        if (req.password() == null || req.password().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "password must be at least 8 characters"));
        }
        if (userRepo.existsByUsername(req.username())) {
            return ResponseEntity.badRequest().body(Map.of("error", "username already exists"));
        }
        HubUser u = new HubUser();
        u.setUsername(req.username());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setDisplayName(req.displayName());
        u.setEmail(req.email());
        u.setRoles(req.roles() != null && !req.roles().isEmpty() ? new HashSet<>(req.roles()) : Set.of("HUB_ADMIN"));
        u.setActive(true);
        u.setCreatedAt(Instant.now());
        u.setCreatedBy(auth != null ? auth.getName() : "admin");
        HubUser saved = userRepo.save(u);
        saved.setPasswordHash(null);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody UpdateUserRequest req) {
        return userRepo.findById(id).<ResponseEntity<?>>map(u -> {
            if (req.displayName() != null) u.setDisplayName(req.displayName());
            if (req.email() != null) u.setEmail(req.email());
            if (req.roles() != null) u.setRoles(new HashSet<>(req.roles()));
            if (req.active() != null) u.setActive(req.active());
            HubUser saved = userRepo.save(u);
            saved.setPasswordHash(null);
            return ResponseEntity.ok(saved);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable String id, @RequestBody ResetPasswordRequest req) {
        if (req.password() == null || req.password().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "password must be at least 8 characters"));
        }
        return userRepo.findById(id).<ResponseEntity<?>>map(u -> {
            u.setPasswordHash(passwordEncoder.encode(req.password()));
            userRepo.save(u);
            return ResponseEntity.ok(Map.of("status", "password_reset"));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id, Authentication auth) {
        return userRepo.findById(id).<ResponseEntity<?>>map(u -> {
            // Prevent deleting the last admin
            long adminCount = userRepo.findAll().stream()
                    .filter(x -> x.isActive() && x.getRoles().contains("HUB_ADMIN"))
                    .count();
            if (adminCount <= 1 && u.getRoles().contains("HUB_ADMIN") && u.isActive()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete the last active admin"));
            }
            // Prevent deleting yourself
            if (auth != null && u.getUsername().equals(auth.getName())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete your own account"));
            }
            userRepo.deleteById(id);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record CreateUserRequest(String username, String password, String displayName, String email, List<String> roles) {}
    public record UpdateUserRequest(String displayName, String email, List<String> roles, Boolean active) {}
    public record ResetPasswordRequest(String password) {}
}
