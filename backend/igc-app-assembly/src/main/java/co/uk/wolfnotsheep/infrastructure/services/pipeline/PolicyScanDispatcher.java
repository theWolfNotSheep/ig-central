package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.governance.models.PolicyBlock.RequiredScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Iterates the {@code requiredScans[]} of a resolved POLICY block,
 * dispatches each through {@link ScanRouterClient}, and returns the
 * aggregated {@link PolicyScanResult}s. Phase 1.9 PR2 / CSV #36.
 *
 * <p>Behaviour is observe-only at this phase: the dispatcher records
 * the result but does not gate the pipeline. PR4 (results aggregated,
 * passed to enforcement) wires blocking-failure short-circuit + audit
 * emission into the engine.
 *
 * <p>Bean is always present; the underlying {@link ScanRouterClient}
 * is optional ({@code @ConditionalOnProperty}). When the client isn't
 * wired (test contexts, sync HTTP path disabled), the dispatcher
 * records each scan as {@code dispatched=false} so the engine still
 * has a deterministic record.
 */
@Service
public class PolicyScanDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PolicyScanDispatcher.class);

    private final ObjectProvider<ScanRouterClient> clientProvider;

    public PolicyScanDispatcher(ObjectProvider<ScanRouterClient> clientProvider) {
        this.clientProvider = clientProvider;
    }

    /**
     * Run each scan against the cascade router. Synchronous — blocks
     * until every scan completes (or fails). The order of returned
     * results matches the input order.
     *
     * @param pipelineRunId pipeline-run id; combined with the scan ref
     *                      to form an idempotency key per CSV #16.
     * @param scans         the resolved {@code requiredScans[]} from
     *                      the POLICY block.
     * @param extractedText document text the cascade evaluates.
     */
    public List<PolicyScanResult> dispatch(
            String pipelineRunId,
            List<RequiredScan> scans,
            String extractedText) {

        if (scans == null || scans.isEmpty()) return List.of();
        ScanRouterClient client = clientProvider.getIfAvailable();
        List<PolicyScanResult> out = new ArrayList<>(scans.size());

        for (RequiredScan scan : scans) {
            if (scan == null || scan.ref() == null || scan.ref().isBlank()) {
                out.add(new PolicyScanResult(
                        scan == null ? null : scan.scanType(),
                        scan == null ? null : scan.ref(),
                        scan != null && scan.blocking(),
                        false, null, null, null,
                        "scan ref missing or blank",
                        -1));
                continue;
            }

            if (client == null) {
                log.debug("[PolicyScanDispatcher] no ScanRouterClient bean — recording scan {} as not-dispatched",
                        scan.ref());
                out.add(new PolicyScanResult(
                        scan.scanType(), scan.ref(), scan.blocking(),
                        false, null, null, null,
                        "ScanRouterClient bean unavailable (pipeline.scan-router.enabled=false)",
                        -1));
                continue;
            }

            String nodeRunId = pipelineRunId == null
                    ? "scan-" + scan.ref()
                    : pipelineRunId + "-scan-" + scan.ref();

            ScanRouterClient.RouterScanOutcome outcome;
            try {
                outcome = client.dispatch(nodeRunId, scan.ref(), null, extractedText, nodeRunId);
            } catch (RuntimeException e) {
                log.warn("[PolicyScanDispatcher] scan {} dispatch threw: {}", scan.ref(), e.getMessage());
                out.add(new PolicyScanResult(
                        scan.scanType(), scan.ref(), scan.blocking(),
                        true, null, null, null,
                        "dispatch exception: " + e.getMessage(),
                        -1));
                continue;
            }

            out.add(new PolicyScanResult(
                    scan.scanType(), scan.ref(), scan.blocking(),
                    true,
                    outcome.tierOfDecision(),
                    outcome.confidence(),
                    outcome.result(),
                    outcome.success() ? null : outcome.error(),
                    outcome.durationMs()));
        }

        long blockingFailures = out.stream().filter(PolicyScanResult::blockingFailure).count();
        if (blockingFailures > 0) {
            log.warn("[PolicyScanDispatcher] {} blocking scan failure(s) out of {} — observe-only at Phase 1.9 PR2",
                    blockingFailures, out.size());
        } else {
            log.info("[PolicyScanDispatcher] dispatched {} scan(s); 0 blocking failures", out.size());
        }
        return out;
    }
}
