package co.uk.wolfnotsheep.llmworker.backend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama-backed LLM service. Same shape as
 * {@link AnthropicLlmService} — only the backend driver and
 * default model differ. Ollama is selected via
 * {@code igc.llm.worker.backend=ollama}; the model id defaults to
 * {@code llama3.1:8b} (a reasonable middle-tier local model) and is
 * tuneable per replica via {@code igc.llm.worker.ollama.model}.
 *
 * <p>Ollama is the local fallback for the LLM tier when cloud LLM
 * access is restricted (e.g. air-gapped deployments). Performance
 * varies wildly with the host machine's GPU; the
 * {@code igc.llm.worker.ollama.num-ctx} property controls the
 * context-window length.
 */
public class OllamaLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmService.class);
    private static final String TEXT_PLACEHOLDER = "{{text}}";

    private final OllamaChatModel chatModel;
    private final PromptBlockResolver blockResolver;
    private final String modelId;
    private final double temperature;
    private final int numCtx;
    private final ObjectMapper mapper;
    private final ToolCallbackProvider[] toolCallbackProviders;

    public OllamaLlmService(
            OllamaChatModel chatModel,
            PromptBlockResolver blockResolver,
            String modelId,
            double temperature,
            int numCtx,
            ObjectMapper mapper) {
        this(chatModel, blockResolver, modelId, temperature, numCtx, mapper,
                new ToolCallbackProvider[0]);
    }

    public OllamaLlmService(
            OllamaChatModel chatModel,
            PromptBlockResolver blockResolver,
            String modelId,
            double temperature,
            int numCtx,
            ObjectMapper mapper,
            ToolCallbackProvider[] toolCallbackProviders) {
        this.chatModel = chatModel;
        this.blockResolver = blockResolver;
        this.modelId = modelId == null || modelId.isBlank() ? "llama3.1:8b" : modelId;
        this.temperature = temperature;
        this.numCtx = numCtx;
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
                    .options(OllamaChatOptions.builder()
                            .model(modelId)
                            .temperature(temperature)
                            .numCtx(numCtx))
                    .call()
                    .chatResponse();
        } catch (RuntimeException e) {
            throw new UncheckedIOException("ollama call failed: " + e.getMessage(),
                    new java.io.IOException(e));
        }

        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            throw new UncheckedIOException("ollama returned an empty response",
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
                LlmBackendId.OLLAMA,
                modelId,
                tokensIn,
                tokensOut);
    }

    @Override
    public LlmBackendId activeBackend() {
        return LlmBackendId.OLLAMA;
    }

    @Override
    public boolean isReady() {
        // The Spring AI starter only autoconfigures the model bean
        // when the Ollama endpoint property is set; if we got
        // constructed at all, the configured endpoint exists. A
        // real /api/tags ping belongs in a follow-up health probe.
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
        String trimmed = stripCodeFence(raw.trim());
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
                log.debug("ollama content was not valid JSON, falling back to text wrap: {}", e.getMessage());
            }
        }
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
