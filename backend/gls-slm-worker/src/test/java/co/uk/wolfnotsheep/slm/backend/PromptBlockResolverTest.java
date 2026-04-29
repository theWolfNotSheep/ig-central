package co.uk.wolfnotsheep.slm.backend;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptBlockResolverTest {

    private MongoTemplate mongo;
    private PromptBlockResolver resolver;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        resolver = new PromptBlockResolver(mongo);
    }

    @Test
    void resolves_active_version_when_no_version_pinned() {
        Document content = new Document(Map.of(
                "systemPrompt", "You are a classifier.",
                "userPromptTemplate", "Classify: {{text}}"));
        Document blockDoc = new Document()
                .append("_id", "block-1")
                .append("type", "PROMPT")
                .append("activeVersion", 3)
                .append("versions", List.of(
                        new Document().append("version", 1).append("content", new Document()),
                        new Document().append("version", 3).append("content", content)));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(blockDoc);

        PromptBlockResolver.ResolvedPrompt resolved = resolver.resolve("block-1", null);

        assertThat(resolved.blockId()).isEqualTo("block-1");
        assertThat(resolved.blockVersion()).isEqualTo(3);
        assertThat(resolved.systemPrompt()).isEqualTo("You are a classifier.");
        assertThat(resolved.userPromptTemplate()).isEqualTo("Classify: {{text}}");
    }

    @Test
    void resolves_pinned_version_when_provided() {
        Document v1 = new Document(Map.of("systemPrompt", "v1 prompt"));
        Document v2 = new Document(Map.of("systemPrompt", "v2 prompt"));
        Document blockDoc = new Document()
                .append("_id", "block-1")
                .append("type", "PROMPT")
                .append("activeVersion", 2)
                .append("versions", List.of(
                        new Document().append("version", 1).append("content", v1),
                        new Document().append("version", 2).append("content", v2)));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(blockDoc);

        PromptBlockResolver.ResolvedPrompt resolved = resolver.resolve("block-1", 1);
        assertThat(resolved.blockVersion()).isEqualTo(1);
        assertThat(resolved.systemPrompt()).isEqualTo("v1 prompt");
    }

    @Test
    void unknown_block_id_throws_BlockUnknownException() {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(null);

        assertThatThrownBy(() -> resolver.resolve("ghost", null))
                .isInstanceOf(BlockUnknownException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void wrong_block_type_throws_BlockUnknownException() {
        Document blockDoc = new Document()
                .append("_id", "block-1")
                .append("type", "BERT_CLASSIFIER")
                .append("activeVersion", 1);
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(blockDoc);

        assertThatThrownBy(() -> resolver.resolve("block-1", null))
                .isInstanceOf(BlockUnknownException.class)
                .hasMessageContaining("expected PROMPT");
    }

    @Test
    void missing_version_throws_BlockUnknownException() {
        Document blockDoc = new Document()
                .append("_id", "block-1")
                .append("type", "PROMPT")
                .append("activeVersion", 1)
                .append("versions", List.of(
                        new Document().append("version", 1).append("content", new Document())));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(blockDoc);

        assertThatThrownBy(() -> resolver.resolve("block-1", 99))
                .isInstanceOf(BlockUnknownException.class)
                .hasMessageContaining("version=99");
    }

    @Test
    void empty_content_throws_BlockUnknownException() {
        Document blockDoc = new Document()
                .append("_id", "block-1")
                .append("type", "PROMPT")
                .append("activeVersion", 1)
                .append("versions", List.of(
                        new Document().append("version", 1).append("content", new Document())));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(blockDoc);

        assertThatThrownBy(() -> resolver.resolve("block-1", null))
                .isInstanceOf(BlockUnknownException.class)
                .hasMessageContaining("neither systemPrompt nor userPromptTemplate");
    }

    @Test
    void falls_back_to_draftContent_when_no_versions_yet() {
        Document draft = new Document(Map.of("systemPrompt", "draft prompt"));
        Document blockDoc = new Document()
                .append("_id", "block-1")
                .append("type", "PROMPT")
                .append("activeVersion", 0)
                .append("draftContent", draft);
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("pipeline_blocks")))
                .thenReturn(blockDoc);

        PromptBlockResolver.ResolvedPrompt resolved = resolver.resolve("block-1", null);
        assertThat(resolved.systemPrompt()).isEqualTo("draft prompt");
    }

    @Test
    void blank_blockId_throws_BlockUnknownException() {
        assertThatThrownBy(() -> resolver.resolve("", null))
                .isInstanceOf(BlockUnknownException.class);
        assertThatThrownBy(() -> resolver.resolve(null, null))
                .isInstanceOf(BlockUnknownException.class);
    }
}
