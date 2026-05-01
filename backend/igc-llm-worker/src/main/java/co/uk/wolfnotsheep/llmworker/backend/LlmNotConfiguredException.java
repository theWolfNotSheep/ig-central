package co.uk.wolfnotsheep.llmworker.backend;

/**
 * Thrown by {@link LlmService#classify} when no LLM backend is
 * configured on this build. Mapped to RFC 7807 503
 * {@code LLM_NOT_CONFIGURED}; the cascade router treats this as a
 * fallthrough signal.
 */
public class LlmNotConfiguredException extends RuntimeException {
    public LlmNotConfiguredException(String message) {
        super(message);
    }
}
