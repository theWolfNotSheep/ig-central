package co.uk.wolfnotsheep.llmworker.backend;

/**
 * Thrown when the daily token budget would be exceeded by the next
 * call. Mapped to RFC 7807 429 {@code LLM_BUDGET_EXCEEDED} with a
 * {@code Retry-After: <seconds-until-midnight-UTC>} header so
 * clients can back off until the next budget window.
 */
public class BudgetExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public BudgetExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
