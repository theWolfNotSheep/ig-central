package co.uk.wolfnotsheep.governance.services;

import co.uk.wolfnotsheep.governance.models.PolicyBlock;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the {@code POLICY} block for a given {@code categoryId}
 * by reading the {@code pipeline_blocks} collection. Same minimal-
 * coupling pattern as the v2 worker resolvers — operates on the raw
 * collection through {@link MongoTemplate} rather than the
 * {@code PipelineBlock} domain model so future shape changes don't
 * cascade into consumers.
 *
 * <p>Phase 1.8 / CSV #35. The pipeline-engine interpreter (PR3)
 * consumes this; the admin UI's category-detail screen also consumes
 * it for display.
 */
@Service
public class PolicyBlockResolver {

    private static final Logger log = LoggerFactory.getLogger(PolicyBlockResolver.class);
    private static final String COLLECTION = "pipeline_blocks";

    private final MongoTemplate mongo;

    public PolicyBlockResolver(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * Look up the active POLICY block for {@code categoryId}. Returns
     * {@link Optional#empty()} when no block exists for the category
     * — callers fall back to defaults (no scans, no metadata, no
     * extra governance policies).
     */
    public Optional<PolicyBlock> resolveByCategoryId(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) return Optional.empty();
        Query q = new Query(Criteria.where("type").is("POLICY"));
        List<Document> candidates;
        try {
            candidates = mongo.find(q, Document.class, COLLECTION);
        } catch (RuntimeException e) {
            log.warn("policy resolver: Mongo lookup failed for categoryId={}: {}",
                    categoryId, e.getMessage());
            return Optional.empty();
        }
        for (Document doc : candidates) {
            Map<String, Object> content = activeContent(doc);
            if (content == null) continue;
            Object cat = content.get("categoryId");
            if (cat != null && categoryId.equals(cat.toString())) {
                return Optional.of(parse(content));
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> activeContent(Document doc) {
        Integer activeVersion = doc.getInteger("activeVersion");
        List<Document> versions = (List<Document>) doc.get("versions");
        if (activeVersion != null && versions != null) {
            for (Document v : versions) {
                Integer ver = v.getInteger("version");
                if (ver != null && ver.equals(activeVersion)) {
                    Object content = v.get("content");
                    if (content instanceof Document d) return d;
                    if (content instanceof Map<?, ?> m) return (Map<String, Object>) m;
                }
            }
        }
        Object draft = doc.get("draftContent");
        if (draft instanceof Document d) return d;
        if (draft instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    @SuppressWarnings("unchecked")
    static PolicyBlock parse(Map<String, Object> content) {
        String categoryId = stringField(content, "categoryId");
        String categoryName = stringField(content, "categoryName");
        List<PolicyBlock.RequiredScan> scans = parseScans(content.get("requiredScans"));
        List<String> metadataSchemaIds = parseStringList(content.get("metadataSchemaIds"));
        List<String> governancePolicyIds = parseStringList(content.get("governancePolicyIds"));
        List<PolicyBlock.SensitivityOverride> overrides = parseSensitivityOverrides(content.get("conditions"));
        return new PolicyBlock(
                categoryId, categoryName, scans, metadataSchemaIds, governancePolicyIds, overrides);
    }

    @SuppressWarnings("unchecked")
    private static List<PolicyBlock.RequiredScan> parseScans(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<PolicyBlock.RequiredScan> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Map<String, Object> map = (Map<String, Object>) m;
            String scanType = stringField(map, "scanType");
            String ref = stringField(map, "ref");
            if (scanType == null || ref == null) continue;
            boolean blocking = !Boolean.FALSE.equals(map.get("blocking"));
            out.add(new PolicyBlock.RequiredScan(scanType, ref, blocking));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<PolicyBlock.SensitivityOverride> parseSensitivityOverrides(Object rawConditions) {
        if (!(rawConditions instanceof Map<?, ?> conditionsMap)) return List.of();
        Object raw = ((Map<String, Object>) conditionsMap).get("bySensitivity");
        if (!(raw instanceof List<?> list)) return List.of();
        List<PolicyBlock.SensitivityOverride> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Map<String, Object> map = (Map<String, Object>) m;
            List<String> sensitivities = parseStringList(map.get("sensitivities"));
            if (sensitivities.isEmpty()) continue;
            Object applyRaw = map.get("apply");
            if (!(applyRaw instanceof Map<?, ?> apply)) {
                out.add(new PolicyBlock.SensitivityOverride(sensitivities, null, null, null));
                continue;
            }
            Map<String, Object> applyMap = (Map<String, Object>) apply;
            List<PolicyBlock.RequiredScan> scans = applyMap.containsKey("requiredScans")
                    ? parseScans(applyMap.get("requiredScans")) : null;
            List<String> meta = applyMap.containsKey("metadataSchemaIds")
                    ? parseStringList(applyMap.get("metadataSchemaIds")) : null;
            List<String> policies = applyMap.containsKey("governancePolicyIds")
                    ? parseStringList(applyMap.get("governancePolicyIds")) : null;
            out.add(new PolicyBlock.SensitivityOverride(sensitivities, scans, meta, policies));
        }
        return out;
    }

    private static List<String> parseStringList(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item == null) continue;
            String s = item.toString();
            if (!s.isBlank()) out.add(s);
        }
        return out;
    }

    private static String stringField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        String s = v.toString();
        return s.isBlank() ? null : s;
    }
}
