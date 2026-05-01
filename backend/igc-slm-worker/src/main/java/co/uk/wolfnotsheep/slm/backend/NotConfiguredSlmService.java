package co.uk.wolfnotsheep.slm.backend;

/**
 * Default backend for builds without an SLM backend wired. Always
 * throws {@link SlmNotConfiguredException}; the controller maps to
 * 503 so the cascade router falls through to the LLM tier.
 *
 * <p>Swapped out by the real Anthropic Haiku or Ollama backend when
 * {@code igc.slm.worker.backend} is set to {@code anthropic} or
 * {@code ollama} and the matching config is present.
 */
public class NotConfiguredSlmService implements SlmService {

    @Override
    public SlmResult classify(String blockId, Integer blockVersion, String text) {
        throw new SlmNotConfiguredException(
                "no SLM backend is configured on this replica. Set igc.slm.worker.backend "
                        + "to `anthropic` (with ANTHROPIC_API_KEY) or `ollama` (with a reachable "
                        + "Ollama endpoint) to enable classification.");
    }

    @Override
    public SlmBackendId activeBackend() {
        return SlmBackendId.NONE;
    }

    @Override
    public boolean isReady() {
        return false;
    }
}
