package co.uk.wolfnotsheep.governance.services;

import co.uk.wolfnotsheep.governance.models.PolicyBlock;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyBlockResolverTest {

    private MongoTemplate mongo;
    private PolicyBlockResolver resolver;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        resolver = new PolicyBlockResolver(mongo);
    }

    @Test
    void resolves_policy_block_by_categoryId_with_scans_and_metadata() {
        Document content = new Document(Map.of(
                "categoryId", "cat-hr",
                "categoryName", "HR Letter",
                "requiredScans", List.of(
                        new Document(Map.of("scanType", "PII", "ref", "uk-passport", "blocking", true)),
                        new Document(Map.of("scanType", "PHI", "ref", "nhs-number"))),
                "metadataSchemaIds", List.of("schema-hr-leave"),
                "governancePolicyIds", List.of("policy-7yr-retention")
        ));
        Document blockDoc = new Document()
                .append("type", "POLICY")
                .append("activeVersion", 1)
                .append("versions", List.of(
                        new Document().append("version", 1).append("content", content)));
        when(mongo.find(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(List.of(blockDoc));

        PolicyBlock policy = resolver.resolveByCategoryId("cat-hr").orElseThrow();

        assertThat(policy.categoryId()).isEqualTo("cat-hr");
        assertThat(policy.categoryName()).isEqualTo("HR Letter");
        assertThat(policy.requiredScans()).hasSize(2);
        assertThat(policy.requiredScans().get(0).scanType()).isEqualTo("PII");
        assertThat(policy.requiredScans().get(0).ref()).isEqualTo("uk-passport");
        assertThat(policy.requiredScans().get(0).blocking()).isTrue();
        // blocking defaults to true when not specified
        assertThat(policy.requiredScans().get(1).blocking()).isTrue();
        assertThat(policy.metadataSchemaIds()).containsExactly("schema-hr-leave");
        assertThat(policy.governancePolicyIds()).containsExactly("policy-7yr-retention");
        assertThat(policy.sensitivityOverrides()).isEmpty();
    }

    @Test
    void parses_blocking_false_explicitly() {
        Document content = new Document(Map.of(
                "categoryId", "cat-x",
                "requiredScans", List.of(
                        new Document(Map.of("scanType", "CUSTOM", "ref", "weird-scan",
                                "blocking", false)))
        ));
        Document blockDoc = blockDocWithContent(content);
        when(mongo.find(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(List.of(blockDoc));

        PolicyBlock policy = resolver.resolveByCategoryId("cat-x").orElseThrow();
        assertThat(policy.requiredScans().get(0).blocking()).isFalse();
    }

    @Test
    void returns_empty_when_categoryId_does_not_match_any_block() {
        Document blockDoc = blockDocWithContent(new Document("categoryId", "cat-other"));
        when(mongo.find(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(List.of(blockDoc));

        assertThat(resolver.resolveByCategoryId("cat-not-there")).isEmpty();
    }

    @Test
    void returns_empty_when_no_POLICY_blocks_exist() {
        when(mongo.find(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(List.of());

        assertThat(resolver.resolveByCategoryId("cat-hr")).isEmpty();
    }

    @Test
    void returns_empty_when_Mongo_lookup_throws() {
        when(mongo.find(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenThrow(new RuntimeException("connection refused"));

        assertThat(resolver.resolveByCategoryId("cat-hr")).isEmpty();
    }

    @Test
    void returns_empty_for_blank_categoryId() {
        assertThat(resolver.resolveByCategoryId(null)).isEmpty();
        assertThat(resolver.resolveByCategoryId("")).isEmpty();
        assertThat(resolver.resolveByCategoryId("   ")).isEmpty();
    }

    @Test
    void parses_sensitivity_overrides_with_partial_apply_block() {
        // RESTRICTED docs in this category get an extra scan but inherit
        // the metadata + governance lists.
        Document override = new Document(Map.of(
                "sensitivities", List.of("RESTRICTED"),
                "apply", new Document(Map.of(
                        "requiredScans", List.of(
                                new Document(Map.of("scanType", "PII", "ref", "extended-scan")))))));
        Document content = new Document(Map.of(
                "categoryId", "cat-hr",
                "requiredScans", List.of(
                        new Document(Map.of("scanType", "PII", "ref", "default-scan"))),
                "metadataSchemaIds", List.of("schema-default"),
                "conditions", new Document("bySensitivity", List.of(override))));
        Document blockDoc = blockDocWithContent(content);
        when(mongo.find(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(List.of(blockDoc));

        PolicyBlock policy = resolver.resolveByCategoryId("cat-hr").orElseThrow();
        assertThat(policy.sensitivityOverrides()).hasSize(1);

        // Default sensitivity = no override applied.
        PolicyBlock def = policy.effectiveFor("INTERNAL");
        assertThat(def.requiredScans()).hasSize(1);
        assertThat(def.requiredScans().get(0).ref()).isEqualTo("default-scan");
        assertThat(def.metadataSchemaIds()).containsExactly("schema-default");

        // RESTRICTED → override on requiredScans only; metadata inherited.
        PolicyBlock restricted = policy.effectiveFor("RESTRICTED");
        assertThat(restricted.requiredScans()).hasSize(1);
        assertThat(restricted.requiredScans().get(0).ref()).isEqualTo("extended-scan");
        assertThat(restricted.metadataSchemaIds()).containsExactly("schema-default");
    }

    @Test
    void uses_draftContent_when_no_active_version_exists() {
        Document content = new Document(Map.of("categoryId", "cat-hr"));
        Document blockDoc = new Document()
                .append("type", "POLICY")
                .append("activeVersion", 0)
                .append("draftContent", content);
        when(mongo.find(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(List.of(blockDoc));

        Optional<PolicyBlock> resolved = resolver.resolveByCategoryId("cat-hr");
        assertThat(resolved).isPresent();
    }

    @Test
    void skips_malformed_scan_entries_silently() {
        Document content = new Document(Map.of(
                "categoryId", "cat-hr",
                "requiredScans", List.of(
                        new Document(Map.of("scanType", "PII", "ref", "valid")),
                        new Document(Map.of("scanType", "MISSING_REF")), // no ref
                        "not-a-map",                                       // string
                        new Document(Map.of("ref", "no-scan-type")))));   // no scanType
        Document blockDoc = blockDocWithContent(content);
        when(mongo.find(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(List.of(blockDoc));

        PolicyBlock policy = resolver.resolveByCategoryId("cat-hr").orElseThrow();
        assertThat(policy.requiredScans()).hasSize(1);
        assertThat(policy.requiredScans().get(0).ref()).isEqualTo("valid");
    }

    private static Document blockDocWithContent(Document content) {
        return new Document()
                .append("type", "POLICY")
                .append("activeVersion", 1)
                .append("versions", List.of(
                        new Document().append("version", 1).append("content", content)));
    }
}
