package co.uk.wolfnotsheep.llmworker.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 2.2 PR2 — periodic MCP server reachability probe.
 *
 * <p>Pings {@code <mcp-base-url>/actuator/health} (or the configured
 * probe endpoint) at {@code gls.llm.worker.mcp.probe.interval} (default
 * 30 s) with a short HTTP timeout (default 3 s). Stores the result in
 * an {@link AtomicBoolean} that {@link McpCappingLlmService} reads
 * synchronously per classify call.
 *
 * <p>Why a probe rather than per-call check: per-call HTTP would add
 * latency to every classification and amplify load on the MCP server
 * during a partial outage. A 30 s probe is precise enough for
 * confidence-capping semantics — we don't need exact per-call accuracy
 * to flag results as MCP-degraded.
 *
 * <p>Initial state is "available" (optimistic). The first probe runs
 * after {@code initial-delay} so the worker doesn't fail-fast during
 * its own startup. Failures during probe are caught and logged at
 * DEBUG (every poll cycle would otherwise spam logs during a known
 * outage).
 */
@Component
public class McpAvailabilityProbe {

    private static final Logger log = LoggerFactory.getLogger(McpAvailabilityProbe.class);

    private final String mcpBaseUrl;
    private final String probePath;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final AtomicBoolean available = new AtomicBoolean(true);
    private volatile boolean lastLogState = true;

    public McpAvailabilityProbe(
            @Value("${spring.ai.mcp.client.sse.connections.governance.url:}") String mcpBaseUrl,
            @Value("${gls.llm.worker.mcp.probe.path:/actuator/health}") String probePath,
            @Value("${gls.llm.worker.mcp.probe.timeout:PT3S}") Duration timeout) {
        this.mcpBaseUrl = mcpBaseUrl == null ? "" : mcpBaseUrl.trim();
        this.probePath = probePath == null || probePath.isBlank() ? "/actuator/health" : probePath;
        this.timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    @Scheduled(
            fixedDelayString = "${gls.llm.worker.mcp.probe.interval:PT30S}",
            initialDelayString = "${gls.llm.worker.mcp.probe.initial-delay:PT30S}")
    public void probe() {
        if (mcpBaseUrl.isEmpty()) {
            // No MCP configured — treat as available (the cap doesn't fire).
            return;
        }
        boolean ok = doProbe();
        boolean previous = available.getAndSet(ok);
        if (ok != previous) {
            if (ok) {
                log.info("MCP probe: {} now reachable", mcpBaseUrl);
            } else {
                log.warn("MCP probe: {} unreachable — confidence will be capped on subsequent calls", mcpBaseUrl);
            }
        } else if (!ok && lastLogState) {
            // First time we observe down (since startup) — log even if 'previous' was already false from init.
            log.warn("MCP probe: {} unreachable", mcpBaseUrl);
        }
        lastLogState = ok;
    }

    /** Visible for testing. */
    boolean doProbe() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(stripTrailingSlash(mcpBaseUrl) + probePath))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            // 2xx and 3xx are reachable; 5xx + transport failures are not.
            // 4xx is treated as reachable (the server responded; the path was wrong is a config issue, not an outage).
            int status = resp.statusCode();
            return status < 500;
        } catch (Exception e) {
            log.debug("MCP probe transport failure for {}: {}", mcpBaseUrl, e.getMessage());
            return false;
        }
    }

    /**
     * @return whether the MCP server was reachable at the most recent probe
     *         (or initially-optimistic before the first probe runs).
     */
    public boolean isAvailable() {
        return available.get();
    }

    /** Visible for testing — set the availability state directly. */
    void setAvailable(boolean state) {
        available.set(state);
    }

    /** Visible for testing — whether MCP is even configured. */
    boolean isConfigured() {
        return !mcpBaseUrl.isEmpty();
    }

    private static String stripTrailingSlash(String s) {
        if (s.endsWith("/")) return s.substring(0, s.length() - 1);
        return s;
    }
}
