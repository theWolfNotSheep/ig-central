package co.uk.wolfnotsheep.llmworker.backend;

/**
 * Default backend for builds without an LLM backend wired. Always
 * throws {@link LlmNotConfiguredException}; the controller maps to
 * 503 so the cascade router falls through to the LLM tier.
 *
 * <p>Swapped out by the real Anthropic Haiku or Ollama backend when
 * {@code igc.llm.worker.backend} is set to {@code anthropic} or
 * {@code ollama} and the matching config is present.
 */
public class NotConfiguredLlmService implements LlmService {

    @Override
    public LlmResult classify(String blockId, Integer blockVersion, String text) {
        throw new LlmNotConfiguredException(
                "no LLM backend is configured on this replica. Set igc.llm.worker.backend "
                        + "to `anthropic` (with ANTHROPIC_API_KEY) or `ollama` (with a reachable "
                        + "Ollama endpoint) to enable classification.");
    }

    @Override
    public LlmBackendId activeBackend() {
        return LlmBackendId.NONE;
    }

    @Override
    public boolean isReady() {
        return false;
    }
}
