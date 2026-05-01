package co.uk.wolfnotsheep.llmworker.backend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Claude backend for the LLM tier. Uses Spring AI's
 * {@link AnthropicChatModel} (same starter the LLM orchestrator
 * already depends on) and the model id configured via
 * {@code igc.llm.worker.anthropic.model} (defaults to
 * {@code claude-sonnet-4-5}, the current ig-central default per
 * CLAUDE.md / architecture §8.1).
 *
 * <p>Shape of the call:
 * <ol>
 *     <li>Resolve the PROMPT block via {@link PromptBlockResolver}
 *         to get {@code systemPrompt} + {@code userPromptTemplate}.</li>
 *     <li>Substitute {@code {{text}}} in the template with the input
 *         (or append the text to the template if no placeholder is
 *         present).</li>
 *     <li>Call Anthropic, parse the response body as JSON to extract
 *         {@code result} (block-shaped map), {@code confidence}, and
 *         {@code rationale}. If the response isn't JSON, fall back
 *         to wrapping the raw text in a {@code { rationale: ... }}
 *         shape with {@code confidence=0.5} so callers still get a
 *         usable shape.</li>
 *     <li>Surface usage tokens from the response metadata for the
 *         {@code costUnits} computation in the controller.</li>
 * </ol>
 *
 * <p>MCP tool integration is deferred to a follow-up PR — when it
 * lands, the {@code ChatClient} builder gains a
 * {@code .defaultToolCallbacks(toolCallbackProvider)} call mirroring
 * the orchestrator's {@code LlmClientFactory}.
 */
public class AnthropicLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmService.class);
    private static final String TEXT_PLACEHOLDER = "{{text}}";

    private final AnthropicChatModel chatModel;
    private final PromptBlockResolver blockResolver;
    private final String modelId;
    private final double temperature;
    private final int maxTokens;
    private final ObjectMapper mapper;
    private final ToolCallbackProvider[] toolCallbackProviders;

    public AnthropicLlmService(
            AnthropicChatModel chatModel,
            PromptBlockResolver blockResolver,
            String modelId,
            double temperature,
            int maxTokens,
            ObjectMapper mapper) {
        this(chatModel, blockResolver, modelId, temperature, maxTokens, mapper,
                new ToolCallbackProvider[0]);
    }

    public AnthropicLlmService(
            AnthropicChatModel chatModel,
            PromptBlockResolver blockResolver,
            String modelId,
            double temperature,
            int maxTokens,
            ObjectMapper mapper,
            ToolCallbackProvider[] toolCallbackProviders) {
        this.chatModel = chatModel;
        this.blockResolver = blockResolver;
        this.modelId = modelId == null || modelId.isBlank() ? "claude-sonnet-4-5" : modelId;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.mapper = mapper;
        this.toolCallbackProviders = toolCallbackProviders == null
                ? new ToolCallbackProvider[0]
                : toolCallbackProviders;
    }

    @Override
    public LlmResult classify(String blockId, Integer blockVersion, String text) {
        PromptBlockResolver.ResolvedPrompt prompt = blockResolver.resolve(blockId, blockVersion);
        String userMessage = renderUser(prompt.userPromptTemplate(), text);
        String systemMessage = prompt.systemPrompt() == null ? "" : prompt.systemPrompt();

        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (toolCallbackProviders.length > 0) {
            builder = builder.defaultToolCallbacks(toolCallbackProviders);
        }
        ChatClient client = builder.build();
        ChatResponse response;
        try {
            response = client.prompt()
                    .system(systemMessage)
                    .user(userMessage)
                    .options(AnthropicChatOptions.builder()
                            .model(modelId)
                            .temperature(temperature)
                            .maxTokens(maxTokens))
                    .call()
                    .chatResponse();
        } catch (RuntimeException e) {
            // Anthropic SDK throws RuntimeExceptions on transport /
            // 5xx errors. Treat as upstream-unavailable so the cascade
            // router can fall through.
            throw new UncheckedIOException("anthropic call failed: " + e.getMessage(),
                    new java.io.IOException(e));
        }

        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            throw new UncheckedIOException("anthropic returned an empty response",
                    new java.io.IOException("empty"));
        }
        String content = response.getResult().getOutput().getText();
        Parsed parsed = parseContent(content);

        long tokensIn = 0L;
        long tokensOut = 0L;
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage != null) {
            Integer in = usage.getPromptTokens();
            Integer out = usage.getCompletionTokens();
            if (in != null) tokensIn = in;
            if (out != null) tokensOut = out;
        }

        return new LlmResult(
                parsed.result,
                parsed.confidence,
                parsed.rationale,
                LlmBackendId.ANTHROPIC,
                modelId,
                tokensIn,
                tokensOut);
    }

    @Override
    public LlmBackendId activeBackend() {
        return LlmBackendId.ANTHROPIC;
    }

    @Override
    public boolean isReady() {
        // The Spring AI starter only autoconfigures the model bean
        // when the API key is set, so if we got constructed at all
        // the API key is present. Real reachability check (a
        // ping-style call) belongs in a follow-up health probe.
        return chatModel != null;
    }

    static String renderUser(String template, String text) {
        if (template == null || template.isBlank()) {
            return text == null ? "" : text;
        }
        if (template.contains(TEXT_PLACEHOLDER)) {
            return template.replace(TEXT_PLACEHOLDER, text == null ? "" : text);
        }
        return template + "\n\n" + (text == null ? "" : text);
    }

    Parsed parseContent(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Parsed(Map.of("rationale", ""), 0.0f, "");
        }
        String trimmed = raw.trim();
        // Anthropic Claude often wraps JSON in ```json ... ``` fences
        // when prompted for structured output — strip them.
        trimmed = stripCodeFence(trimmed);
        if (looksLikeJson(trimmed)) {
            try {
                JsonNode root = mapper.readTree(trimmed);
                if (root.isObject()) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    Iterator<Map.Entry<String, JsonNode>> it = root.fields();
                    while (it.hasNext()) {
                        Map.Entry<String, JsonNode> e = it.next();
                        if ("confidence".equals(e.getKey()) || "rationale".equals(e.getKey())) {
                            continue;
                        }
                        result.put(e.getKey(), unwrap(e.getValue()));
                    }
                    float confidence = root.has("confidence")
                            ? (float) root.get("confidence").asDouble(0.5)
                            : 0.5f;
                    String rationale = root.has("rationale")
                            ? root.get("rationale").asText(null)
                            : null;
                    return new Parsed(result, confidence, rationale);
                }
            } catch (JsonProcessingException e) {
                log.debug("anthropic content was not valid JSON, falling back to text wrap: {}", e.getMessage());
            }
        }
        // Non-JSON response — wrap as rationale with a default confidence so
        // callers still get a stable shape.
        return new Parsed(Map.of("rationale", trimmed), 0.5f, trimmed);
    }

    private static String stripCodeFence(String s) {
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0 && s.endsWith("```")) {
                return s.substring(firstNewline + 1, s.length() - 3).trim();
            }
        }
        return s;
    }

    private static boolean looksLikeJson(String s) {
        return s.startsWith("{") || s.startsWith("[");
    }

    private static Object unwrap(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isInt() || node.isLong()) return node.asLong();
        if (node.isDouble() || node.isFloat()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isArray()) {
            List<Object> out = new java.util.ArrayList<>(node.size());
            for (JsonNode n : node) out.add(unwrap(n));
            return out;
        }
        if (node.isObject()) {
            Map<String, Object> out = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                out.put(e.getKey(), unwrap(e.getValue()));
            }
            return out;
        }
        return null;
    }

    record Parsed(Map<String, Object> result, float confidence, String rationale) {
    }
}
