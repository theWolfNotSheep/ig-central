package co.uk.wolfnotsheep.slm.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the deterministic parts of
 * {@link AnthropicHaikuSlmService} — content parsing + user template
 * rendering. The actual chat-model call requires the Anthropic SDK
 * + an API key and is exercised in integration tests (gated on
 * issue #7).
 */
class AnthropicHaikuSlmServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private AnthropicHaikuSlmService underTest() {
        // Pass nulls for the model + resolver — the parse / render
        // helpers we exercise here don't touch them.
        return new AnthropicHaikuSlmService(
                null, null, "claude-haiku-4-5", 0.1, 1024, mapper);
    }

    @Test
    void renderUser_with_placeholder_substitutes_text() {
        String out = AnthropicHaikuSlmService.renderUser(
                "Classify this:\n{{text}}", "the doc body");
        assertThat(out).isEqualTo("Classify this:\nthe doc body");
    }

    @Test
    void renderUser_without_placeholder_appends_text_after_blank_line() {
        String out = AnthropicHaikuSlmService.renderUser(
                "Classify this document.", "the doc body");
        assertThat(out).isEqualTo("Classify this document.\n\nthe doc body");
    }

    @Test
    void renderUser_with_blank_template_returns_text_only() {
        assertThat(AnthropicHaikuSlmService.renderUser("", "doc")).isEqualTo("doc");
        assertThat(AnthropicHaikuSlmService.renderUser(null, "doc")).isEqualTo("doc");
    }

    @Test
    void parseContent_pure_json_extracts_result_confidence_rationale() {
        AnthropicHaikuSlmService svc = underTest();
        String raw = """
                {
                  "category": "HR Letter",
                  "sensitivity": "INTERNAL",
                  "confidence": 0.91,
                  "rationale": "Header references HR policy"
                }
                """;
        AnthropicHaikuSlmService.Parsed parsed = svc.parseContent(raw);

        assertThat(parsed.confidence()).isCloseTo(0.91f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(parsed.rationale()).isEqualTo("Header references HR policy");
        assertThat(parsed.result()).containsEntry("category", "HR Letter");
        assertThat(parsed.result()).containsEntry("sensitivity", "INTERNAL");
        // Confidence + rationale are pulled out of the result map
        // to match the SlmResult shape.
        assertThat(parsed.result()).doesNotContainKeys("confidence", "rationale");
    }

    @Test
    void parseContent_strips_markdown_json_fences() {
        AnthropicHaikuSlmService svc = underTest();
        String raw = """
                ```json
                {"category": "HR", "confidence": 0.7}
                ```""";
        AnthropicHaikuSlmService.Parsed parsed = svc.parseContent(raw);

        assertThat(parsed.confidence()).isCloseTo(0.7f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(parsed.result()).containsEntry("category", "HR");
    }

    @Test
    void parseContent_non_json_wraps_as_rationale_with_default_confidence() {
        AnthropicHaikuSlmService svc = underTest();
        AnthropicHaikuSlmService.Parsed parsed = svc.parseContent(
                "I can't classify this without more context.");
        assertThat(parsed.confidence()).isEqualTo(0.5f);
        assertThat(parsed.result()).containsEntry("rationale",
                "I can't classify this without more context.");
        assertThat(parsed.rationale()).isEqualTo(
                "I can't classify this without more context.");
    }

    @Test
    void parseContent_empty_or_blank_returns_empty_rationale_with_zero_confidence() {
        AnthropicHaikuSlmService svc = underTest();
        AnthropicHaikuSlmService.Parsed parsed = svc.parseContent("   ");
        assertThat(parsed.confidence()).isEqualTo(0.0f);
        assertThat(parsed.result()).containsEntry("rationale", "");
    }

    @Test
    void parseContent_json_without_confidence_defaults_to_0_5() {
        AnthropicHaikuSlmService svc = underTest();
        AnthropicHaikuSlmService.Parsed parsed = svc.parseContent(
                "{\"category\": \"Other\"}");
        assertThat(parsed.confidence()).isEqualTo(0.5f);
        assertThat(parsed.result()).containsEntry("category", "Other");
    }

    @Test
    void activeBackend_is_ANTHROPIC_HAIKU() {
        AnthropicHaikuSlmService svc = underTest();
        assertThat(svc.activeBackend()).isEqualTo(SlmBackendId.ANTHROPIC_HAIKU);
    }

    @Test
    void constructor_accepts_tool_callbacks_for_MCP_integration() {
        // Sanity check: the seven-arg constructor (with
        // ToolCallbackProvider[]) wires cleanly. Real tool-invocation
        // testing requires a running MCP server + Anthropic SDK
        // round-trip and is gated on issue #7.
        ToolCallbackProvider[] tools = new ToolCallbackProvider[] { mock(ToolCallbackProvider.class) };
        AnthropicHaikuSlmService svc = new AnthropicHaikuSlmService(
                null, null, "claude-haiku-4-5", 0.1, 1024, mapper, tools);
        assertThat(svc.activeBackend()).isEqualTo(SlmBackendId.ANTHROPIC_HAIKU);
    }

    @Test
    void constructor_handles_null_tool_callbacks_array() {
        AnthropicHaikuSlmService svc = new AnthropicHaikuSlmService(
                null, null, "claude-haiku-4-5", 0.1, 1024, mapper, null);
        assertThat(svc.activeBackend()).isEqualTo(SlmBackendId.ANTHROPIC_HAIKU);
    }
}
