package co.uk.wolfnotsheep.llmworker.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-replica daily token budget tracker. In-memory atomic counter
 * resetting at UTC midnight. Phase 1.6 PR4 — first cut keeps the
 * budget per-replica for simplicity; a global budget across replicas
 * is a future enhancement (would need Mongo / Redis).
 *
 * <p>{@link #checkBudget()} throws {@link BudgetExceededException}
 * when the running daily total has crossed the configured cap.
 * Callers run this BEFORE the LLM call. Tokens are recorded AFTER
 * via {@link #recordUsage(long, long)} so over-budget detection
 * always lags by one call — the simplest correct behaviour.
 *
 * <p>Set {@code igc.llm.worker.budget.daily-token-cap=0} (default)
 * to disable budget enforcement entirely.
 */
@Component
public class CostBudgetTracker {

    private static final Logger log = LoggerFactory.getLogger(CostBudgetTracker.class);

    private final long dailyTokenCap;
    private final Clock clock;
    private final AtomicLong tokenCount = new AtomicLong(0L);
    private volatile LocalDate currentDay;

    public CostBudgetTracker(
            @Value("${igc.llm.worker.budget.daily-token-cap:0}") long dailyTokenCap) {
        this(dailyTokenCap, Clock.systemUTC());
    }

    /** Test-friendly constructor — pass a fixed Clock. */
    CostBudgetTracker(long dailyTokenCap, Clock clock) {
        this.dailyTokenCap = dailyTokenCap;
        this.clock = clock;
        this.currentDay = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (dailyTokenCap > 0) {
            log.info("llm: cost budget enabled — daily-token-cap={}", dailyTokenCap);
        } else {
            log.info("llm: cost budget disabled (igc.llm.worker.budget.daily-token-cap=0)");
        }
    }

    /**
     * Throws if the current day's token total has already exceeded the
     * cap. No-op when {@code dailyTokenCap <= 0}.
     */
    public void checkBudget() {
        if (dailyTokenCap <= 0) return;
        rolloverIfNeeded();
        long current = tokenCount.get();
        if (current >= dailyTokenCap) {
            long retryAfter = secondsUntilNextDay();
            throw new BudgetExceededException(
                    "daily LLM token budget exceeded: " + current + " >= " + dailyTokenCap
                            + " — retry after " + retryAfter + "s",
                    retryAfter);
        }
    }

    /** Record consumed tokens on a successful call. */
    public void recordUsage(long tokensIn, long tokensOut) {
        rolloverIfNeeded();
        long total = tokensIn + tokensOut;
        if (total <= 0) return;
        long after = tokenCount.addAndGet(total);
        if (dailyTokenCap > 0 && after >= dailyTokenCap) {
            log.warn("llm: daily token budget hit — running total {} >= cap {}",
                    after, dailyTokenCap);
        }
    }

    /** Visible for tests + ops dashboards. */
    public long currentDayUsage() {
        rolloverIfNeeded();
        return tokenCount.get();
    }

    public long dailyTokenCap() {
        return dailyTokenCap;
    }

    private void rolloverIfNeeded() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!today.equals(currentDay)) {
            synchronized (this) {
                if (!today.equals(currentDay)) {
                    long carryOver = tokenCount.getAndSet(0L);
                    log.info("llm: budget rollover — previous day total {} reset to 0", carryOver);
                    currentDay = today;
                }
            }
        }
    }

    private long secondsUntilNextDay() {
        Instant now = Instant.now(clock);
        Instant tomorrow = LocalDate.now(clock.withZone(ZoneOffset.UTC))
                .plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return Math.max(1L, Duration.between(now, tomorrow).getSeconds());
    }
}
