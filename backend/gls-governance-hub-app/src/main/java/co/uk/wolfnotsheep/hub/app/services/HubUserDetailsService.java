package co.uk.wolfnotsheep.hub.app.services;

import co.uk.wolfnotsheep.hub.models.HubUser;
import co.uk.wolfnotsheep.hub.repositories.HubUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class HubUserDetailsService implements UserDetailsService {

    private final HubUserRepository userRepo;

    public HubUserDetailsService(HubUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        HubUser u = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Update last login (best-effort, don't fail auth if write fails)
        try {
            u.setLastLoginAt(Instant.now());
            userRepo.save(u);
        } catch (Exception ignored) {}

        Set<SimpleGrantedAuthority> auths = u.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toSet());
        return User.builder()
                .username(u.getUsername())
                .password(u.getPasswordHash())
                .authorities(auths)
                .disabled(!u.isActive())
                .build();
    }
}
