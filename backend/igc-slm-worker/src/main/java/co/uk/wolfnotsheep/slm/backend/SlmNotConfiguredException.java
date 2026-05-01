package co.uk.wolfnotsheep.slm.backend;

/**
 * Thrown by {@link SlmService#classify} when no SLM backend is
 * configured on this build. Mapped to RFC 7807 503
 * {@code SLM_NOT_CONFIGURED}; the cascade router treats this as a
 * fallthrough signal.
 */
public class SlmNotConfiguredException extends RuntimeException {
    public SlmNotConfiguredException(String message) {
        super(message);
    }
}
