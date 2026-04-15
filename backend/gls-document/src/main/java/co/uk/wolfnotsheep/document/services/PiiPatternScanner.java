package co.uk.wolfnotsheep.document.services;

import co.uk.wolfnotsheep.document.models.PiiEntity;
import co.uk.wolfnotsheep.document.models.PiiEntity.DetectionMethod;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tier 1 PII detection: fast regex-based scanning against extracted text.
 * No LLM calls — runs locally at zero cost.
 *
 * <p>Patterns are loaded from the active REGEX_SET pipeline block in MongoDB.
 * This makes them fully user-configurable via the Block Library UI.
 * Patterns are cached and refreshed every 60 seconds.
 */
@Service
public class PiiPatternScanner {

    private static final Logger log = LoggerFactory.getLogger(PiiPatternScanner.class);
    private static final long CACHE_TTL_MS = 60_000; // 1 minute

    private record PiiPattern(String name, String type, Pattern pattern, double confidence, int redactKeep) {}

    private final PipelineBlockRepository blockRepo;
    private List<PiiPattern> cachedPatterns;
    private long cacheLoadedAt = 0;

    public PiiPatternScanner(PipelineBlockRepository blockRepo) {
        this.blockRepo = blockRepo;
    }

    /**
     * Load patterns from the active REGEX_SET block, with caching.
     */
    private List<PiiPattern> getPatterns() {
        if (cachedPatterns != null && System.currentTimeMillis() - cacheLoadedAt < CACHE_TTL_MS) {
            return cachedPatterns;
        }

        try {
            List<PipelineBlock> regexBlocks = blockRepo.findByTypeAndActiveTrueOrderByNameAsc(
                    PipelineBlock.BlockType.REGEX_SET);

            if (!regexBlocks.isEmpty()) {
                List<PiiPattern> loaded = new ArrayList<>();
                for (PipelineBlock block : regexBlocks) {
                    Map<String, Object> content = block.getActiveContent();
                    if (content == null) continue;

                    Object patternsObj = content.get("patterns");
                    if (patternsObj instanceof List<?> patternList) {
                        for (Object item : patternList) {
                            if (item instanceof Map<?, ?> raw) {
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> map = (Map<String, Object>) raw;
                                    String name = String.valueOf(map.getOrDefault("name", ""));
                                    String type = String.valueOf(map.getOrDefault("type", "UNKNOWN"));
                                    String regex = String.valueOf(map.getOrDefault("regex", ""));
                                    double confidence = toDouble(map.get("confidence"), 0.8);
                                    String flags = String.valueOf(map.getOrDefault("flags", ""));

                                    if (regex.isBlank()) continue;

                                    int regexFlags = 0;
                                    for (String flag : flags.split("[|,\\s]+")) {
                                        switch (flag.trim().toUpperCase()) {
                                            case "CASE_INSENSITIVE" -> regexFlags |= Pattern.CASE_INSENSITIVE;
                                            case "MULTILINE" -> regexFlags |= Pattern.MULTILINE;
                                            case "DOTALL" -> regexFlags |= Pattern.DOTALL;
                                            default -> { /* ignore unknown flags */ }
                                        }
                                    }

                                    loaded.add(new PiiPattern(name, type, Pattern.compile(regex, regexFlags), confidence, 3));
                                } catch (Exception e) {
                                    log.warn("Invalid PII pattern in block {}: {}", block.getName(), e.getMessage());
                                }
                            }
                        }
                    }
                }

                if (!loaded.isEmpty()) {
                    log.debug("Loaded {} PII patterns from {} REGEX_SET block(s)", loaded.size(), regexBlocks.size());
                    cachedPatterns = loaded;
                    cacheLoadedAt = System.currentTimeMillis();
                    return cachedPatterns;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load PII patterns from blocks: {}", e.getMessage());
        }

        // Fallback: no blocks configured
        log.debug("No REGEX_SET blocks found — using empty pattern list. Configure patterns in Block Library.");
        cachedPatterns = List.of();
        cacheLoadedAt = System.currentTimeMillis();
        return cachedPatterns;
    }

    /**
     * Force reload patterns from database on next scan.
     */
    public void invalidateCache() {
        cacheLoadedAt = 0;
    }

    /**
     * Scan extracted text for PII patterns.
     */
    public List<PiiEntity> scan(String text) {
        return scan(text, List.of());
    }

    /**
     * Scan extracted text for PII patterns, preserving previous dismissals.
     */
    public List<PiiEntity> scan(String text, List<PiiEntity> previousFindings) {
        if (text == null || text.isBlank()) return List.of();

        List<PiiPattern> patterns = getPatterns();

        // Build a set of previously dismissed findings for quick lookup
        Set<String> dismissedKeys = new HashSet<>();
        Map<String, PiiEntity> dismissedEntities = new HashMap<>();
        if (previousFindings != null) {
            for (PiiEntity prev : previousFindings) {
                if (prev.isDismissed()) {
                    if (prev.getMatchedText() != null) {
                        String key = prev.getType() + "::" + prev.getMatchedText();
                        dismissedKeys.add(key);
                        dismissedEntities.put(key, prev);
                    }
                    if (prev.getRedactedText() != null) {
                        String redactedKey = prev.getType() + "::" + prev.getRedactedText();
                        dismissedKeys.add(redactedKey);
                        dismissedEntities.put(redactedKey, prev);
                    }
                }
            }
        }

        List<PiiEntity> findings = new ArrayList<>();

        for (PiiPattern pp : patterns) {
            Matcher matcher = pp.pattern.matcher(text);
            while (matcher.find()) {
                String matched = matcher.group().trim();
                String redacted = redact(matched, pp.redactKeep);
                String key = pp.type + "::" + matched;

                PiiEntity entity = new PiiEntity(
                        pp.type, matched, redacted,
                        matcher.start(), pp.confidence, DetectionMethod.PATTERN);

                // Auto-dismiss if this finding was previously dismissed
                String redactedKey = pp.type + "::" + redacted;
                PiiEntity prev = dismissedEntities.get(key);
                if (prev == null) prev = dismissedEntities.get(redactedKey);
                if (prev != null) {
                    entity.setDismissed(true);
                    entity.setDismissedBy(prev.getDismissedBy());
                    entity.setDismissalReason(prev.getDismissalReason());
                }

                findings.add(entity);
            }
        }

        // Deduplicate overlapping matches (keep highest confidence)
        findings.sort((a, b) -> {
            int cmp = Integer.compare(a.getOffset(), b.getOffset());
            return cmp != 0 ? cmp : Double.compare(b.getConfidence(), a.getConfidence());
        });

        List<PiiEntity> deduped = new ArrayList<>();
        int lastEnd = -1;
        for (PiiEntity e : findings) {
            int end = e.getOffset() + e.getMatchedText().length();
            if (e.getOffset() >= lastEnd) {
                deduped.add(e);
                lastEnd = end;
            }
        }

        return deduped;
    }

    private String redact(String text, int keepChars) {
        if (text.length() <= keepChars) return "***";
        return text.substring(0, keepChars) + "***" + text.substring(Math.max(keepChars, text.length() - keepChars));
    }

    private static double toDouble(Object val, double defaultVal) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) { try { return Double.parseDouble(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }
}
