package co.uk.wolfnotsheep.hub.app.services;

import co.uk.wolfnotsheep.hub.models.HubApiKey;
import co.uk.wolfnotsheep.hub.repositories.HubApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final int KEY_BYTE_LENGTH = 32;
    private static final String KEY_PREFIX = "ghk_";
    private static final int DEFAULT_RATE_LIMIT = 1000;
    private static final int DEFAULT_DOWNLOAD_QUOTA = 100;

    private final HubApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom;

    public ApiKeyService(HubApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a new API key. Returns the plaintext key exactly once — it is not stored.
     */
    public GeneratedKey generateKey(String tenantName, String tenantEmail, List<String> permissions) {
        if (tenantName == null || tenantName.isBlank()) {
            throw new IllegalArgumentException("Tenant name is required");
        }
        if (tenantEmail == null || tenantEmail.isBlank()) {
            throw new IllegalArgumentException("Tenant email is required");
        }

        byte[] keyBytes = new byte[KEY_BYTE_LENGTH];
        secureRandom.nextBytes(keyBytes);
        String plaintextKey = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);

        String keyHash = hashKey(plaintextKey);
        String keyPrefix = plaintextKey.substring(0, Math.min(12, plaintextKey.length()));

        HubApiKey apiKey = new HubApiKey();
        apiKey.setKeyHash(keyHash);
        apiKey.setKeyPrefix(keyPrefix);
        apiKey.setTenantName(tenantName);
        apiKey.setTenantEmail(tenantEmail);
        apiKey.setPermissions(permissions != null ? permissions : List.of());
        apiKey.setActive(true);
        apiKey.setRateLimit(DEFAULT_RATE_LIMIT);
        apiKey.setDownloadQuota(DEFAULT_DOWNLOAD_QUOTA);
        apiKey.setDownloadsThisMonth(0);
        apiKey.setCreatedAt(Instant.now());
        apiKey.setQuotaResetAt(Instant.now().plus(30, ChronoUnit.DAYS));

        HubApiKey saved = apiKeyRepository.save(apiKey);
        log.info("Generated API key for tenant: {} (prefix: {})", tenantName, keyPrefix);

        return new GeneratedKey(saved, plaintextKey);
    }

    public Optional<HubApiKey> validateKey(String keyHash) {
        Optional<HubApiKey> optKey = apiKeyRepository.findByKeyHash(keyHash);
        if (optKey.isEmpty()) {
            return Optional.empty();
        }

        HubApiKey key = optKey.get();

        if (!key.isActive()) {
            log.debug("API key is inactive: {}", key.getKeyPrefix());
            return Optional.empty();
        }

        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(Instant.now())) {
            log.debug("API key has expired: {}", key.getKeyPrefix());
            return Optional.empty();
        }

        if (key.getDownloadQuota() > 0 && key.getDownloadsThisMonth() >= key.getDownloadQuota()) {
            log.debug("API key download quota exceeded: {}", key.getKeyPrefix());
            return Optional.empty();
        }

        return Optional.of(key);
    }

    public List<HubApiKey> listKeys() {
        return apiKeyRepository.findAll();
    }

    public HubApiKey revokeKey(String id) {
        HubApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API key not found with id: " + id));

        key.setActive(false);
        log.info("Revoked API key: {} (tenant: {})", key.getKeyPrefix(), key.getTenantName());
        return apiKeyRepository.save(key);
    }

    public HubApiKey updateKey(String id, Boolean active, Integer rateLimit, Integer downloadQuota) {
        HubApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API key not found with id: " + id));

        if (active != null) {
            key.setActive(active);
        }
        if (rateLimit != null) {
            key.setRateLimit(rateLimit);
        }
        if (downloadQuota != null) {
            key.setDownloadQuota(downloadQuota);
        }

        log.info("Updated API key: {} (tenant: {})", key.getKeyPrefix(), key.getTenantName());
        return apiKeyRepository.save(key);
    }

    private String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public record GeneratedKey(HubApiKey apiKey, String plaintextKey) {}
}
