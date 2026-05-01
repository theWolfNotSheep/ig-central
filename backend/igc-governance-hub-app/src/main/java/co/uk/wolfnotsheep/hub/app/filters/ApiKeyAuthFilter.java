package co.uk.wolfnotsheep.hub.app.filters;

import co.uk.wolfnotsheep.hub.models.HubApiKey;
import co.uk.wolfnotsheep.hub.repositories.HubApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String API_KEY_HEADER = "X-Hub-Api-Key";

    private final HubApiKeyRepository apiKeyRepository;

    public ApiKeyAuthFilter(HubApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/hub/admin/") || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String keyHash = hashKey(apiKey);
            Optional<HubApiKey> optKey = apiKeyRepository.findByKeyHash(keyHash);

            if (optKey.isEmpty()) {
                log.warn("API key not found for hash prefix: {}", keyHash.substring(0, 8));
                filterChain.doFilter(request, response);
                return;
            }

            HubApiKey hubKey = optKey.get();

            if (!hubKey.isActive()) {
                log.warn("Inactive API key used by tenant: {}", hubKey.getTenantName());
                filterChain.doFilter(request, response);
                return;
            }

            if (hubKey.getExpiresAt() != null && hubKey.getExpiresAt().isBefore(Instant.now())) {
                log.warn("Expired API key used by tenant: {}", hubKey.getTenantName());
                filterChain.doFilter(request, response);
                return;
            }

            if (hubKey.getDownloadQuota() > 0
                    && hubKey.getDownloadsThisMonth() >= hubKey.getDownloadQuota()) {
                log.warn("Download quota exceeded for tenant: {}", hubKey.getTenantName());
                filterChain.doFilter(request, response);
                return;
            }

            List<SimpleGrantedAuthority> authorities = hubKey.getPermissions().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(hubKey.getTenantName(), null, authorities);
            authentication.setDetails(hubKey);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            hubKey.setLastUsedAt(Instant.now());
            apiKeyRepository.save(hubKey);

        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
        }

        filterChain.doFilter(request, response);
    }

    private String hashKey(String key) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
