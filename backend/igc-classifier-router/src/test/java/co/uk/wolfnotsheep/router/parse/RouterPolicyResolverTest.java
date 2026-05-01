package co.uk.wolfnotsheep.router.parse;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouterPolicyResolverTest {

    private MongoTemplate mongo;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
    }

    private RouterPolicyResolver resolver(int refreshSeconds) {
        return new RouterPolicyResolver(mongo, "default-router", refreshSeconds);
    }

    @Test
    void parses_active_version_content_into_RouterPolicy() {
        Document content = new Document(Map.of(
                "tiers", new Document(Map.of(
                        "bert", new Document(Map.of("enabled", true, "accept", 0.92)),
                        "slm", new Document(Map.of("enabled", true, "accept", 0.85)),
                        "llm", new Document(Map.of("enabled", true, "accept", 0.0))
                ))
        ));
        Document blockDoc = new Document()
                .append("name", "default-router")
                .append("activeVersion", 1)
                .append("versions", List.of(
                        new Document().append("version", 1).append("content", content)));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(blockDoc);

        RouterPolicy policy = resolver(60).current();

        assertThat(policy.bert().enabled()).isTrue();
        assertThat(policy.bert().accept()).isCloseTo(0.92f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(policy.slm().accept()).isCloseTo(0.85f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(policy.llm().accept()).isEqualTo(0.0f);
    }

    @Test
    void returns_default_when_block_missing() {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(null);

        RouterPolicy policy = resolver(60).current();

        assertThat(policy).isEqualTo(RouterPolicy.DEFAULT);
    }

    @Test
    void returns_default_when_mongo_lookup_throws() {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenThrow(new RuntimeException("connection refused"));

        RouterPolicy policy = resolver(60).current();

        assertThat(policy).isEqualTo(RouterPolicy.DEFAULT);
    }

    @Test
    void returns_default_when_content_missing_tiers() {
        Document content = new Document(Map.of("notTiers", "junk"));
        Document blockDoc = new Document()
                .append("activeVersion", 1)
                .append("versions", List.of(
                        new Document().append("version", 1).append("content", content)));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(blockDoc);

        RouterPolicy policy = resolver(60).current();

        assertThat(policy).isEqualTo(RouterPolicy.DEFAULT);
    }

    @Test
    void caches_within_refresh_interval() {
        Document content = new Document(Map.of(
                "tiers", new Document(Map.of(
                        "bert", new Document(Map.of("enabled", true, "accept", 0.92)),
                        "slm", new Document(Map.of("enabled", true, "accept", 0.85)),
                        "llm", new Document(Map.of("enabled", true, "accept", 0.0))
                ))
        ));
        Document blockDoc = new Document()
                .append("activeVersion", 1)
                .append("versions", List.of(
                        new Document().append("version", 1).append("content", content)));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(blockDoc);

        RouterPolicyResolver r = resolver(60);
        RouterPolicy first = r.current();
        RouterPolicy second = r.current();
        RouterPolicy third = r.current();

        // All three calls should hit the cache after the first.
        verify(mongo, times(1)).findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks"));
        assertThat(first).isEqualTo(second);
        assertThat(second).isEqualTo(third);
    }

    @Test
    void invalidate_cache_forces_re_read() {
        Document content = new Document(Map.of(
                "tiers", new Document(Map.of(
                        "bert", new Document(Map.of("enabled", true, "accept", 0.5)),
                        "slm", new Document(Map.of("enabled", true, "accept", 0.5)),
                        "llm", new Document(Map.of("enabled", true, "accept", 0.0))
                ))
        ));
        Document blockDoc = new Document()
                .append("activeVersion", 1)
                .append("versions", List.of(
                        new Document().append("version", 1).append("content", content)));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(blockDoc);

        RouterPolicyResolver r = resolver(60);
        r.current();
        r.invalidateCache();
        r.current();

        verify(mongo, times(2)).findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks"));
    }

    @Test
    void uses_partial_content_with_fallback_for_missing_tiers() {
        // Only `bert` configured; `slm` and `llm` should fall back to defaults.
        Document content = new Document(Map.of(
                "tiers", new Document(Map.of(
                        "bert", new Document(Map.of("enabled", false, "accept", 0.5))
                ))
        ));
        Document blockDoc = new Document()
                .append("activeVersion", 1)
                .append("versions", List.of(
                        new Document().append("version", 1).append("content", content)));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(blockDoc);

        RouterPolicy policy = resolver(60).current();

        assertThat(policy.bert().enabled()).isFalse();
        assertThat(policy.bert().accept()).isEqualTo(0.5f);
        // SLM + LLM fall back to defaults.
        assertThat(policy.slm()).isEqualTo(RouterPolicy.DEFAULT.slm());
        assertThat(policy.llm()).isEqualTo(RouterPolicy.DEFAULT.llm());
    }
}
