package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.governance.models.PolicyBlock.RequiredScan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 1.9 PR2 — focused tests for {@link PolicyScanDispatcher}.
 * Mocks the {@link ScanRouterClient} via the {@link ObjectProvider}
 * the dispatcher consumes; validates the dispatcher's wiring,
 * fail-soft semantics, and {@link PolicyScanResult} shape.
 */
class PolicyScanDispatcherTest {

    private ScanRouterClient client;
    private ObjectProvider<ScanRouterClient> provider;
    private PolicyScanDispatcher dispatcher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(ScanRouterClient.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        dispatcher = new PolicyScanDispatcher(provider);
    }

    @Test
    void empty_scan_list_returns_empty_results() {
        assertThat(dispatcher.dispatch("run-1", List.of(), "text")).isEmpty();
        assertThat(dispatcher.dispatch("run-1", null, "text")).isEmpty();
    }

    @Test
    void single_scan_success_yields_dispatched_result() {
        when(client.dispatch(any(), eq("scan-pii-x"), any(), eq("body"), any()))
                .thenReturn(new ScanRouterClient.RouterScanOutcome(
                        true, "SLM", 0.92,
                        Map.of("found", true, "instances", List.of(Map.of("value", "AB123456C"))),
                        null, 42L));

        List<PolicyScanResult> results = dispatcher.dispatch(
                "run-1",
                List.of(new RequiredScan("PII", "scan-pii-x", true)),
                "body");

        assertThat(results).hasSize(1);
        PolicyScanResult r = results.get(0);
        assertThat(r.scanType()).isEqualTo("PII");
        assertThat(r.ref()).isEqualTo("scan-pii-x");
        assertThat(r.blocking()).isTrue();
        assertThat(r.dispatched()).isTrue();
        assertThat(r.tierOfDecision()).isEqualTo("SLM");
        assertThat(r.confidence()).isEqualTo(0.92);
        assertThat(r.error()).isNull();
        assertThat(r.success()).isTrue();
        assertThat(r.result()).containsEntry("found", true);
        assertThat(r.durationMs()).isEqualTo(42L);
    }

    @Test
    void transport_failure_is_recorded_as_error_with_dispatched_true() {
        when(client.dispatch(any(), eq("scan-pii-x"), any(), any(), any()))
                .thenReturn(ScanRouterClient.RouterScanOutcome.failure("HTTP 502: bad gateway", 100L));

        List<PolicyScanResult> results = dispatcher.dispatch(
                "run-1",
                List.of(new RequiredScan("PII", "scan-pii-x", false)),
                "body");

        assertThat(results).hasSize(1);
        PolicyScanResult r = results.get(0);
        assertThat(r.dispatched()).isTrue();
        assertThat(r.error()).contains("HTTP 502");
        assertThat(r.success()).isFalse();
        assertThat(r.blockingFailure()).isFalse(); // non-blocking scan
    }

    @Test
    void blocking_scan_failure_marks_blockingFailure_true() {
        when(client.dispatch(any(), any(), any(), any(), any()))
                .thenReturn(ScanRouterClient.RouterScanOutcome.failure("HTTP 500", 1L));

        List<PolicyScanResult> results = dispatcher.dispatch(
                "run-1",
                List.of(new RequiredScan("PII", "scan-pii-x", true)),
                "body");

        assertThat(results.get(0).blockingFailure()).isTrue();
    }

    @Test
    void multiple_scans_return_results_in_order() {
        when(client.dispatch(any(), eq("scan-pii-a"), any(), any(), any()))
                .thenReturn(new ScanRouterClient.RouterScanOutcome(true, "BERT", 0.99, Map.of("found", true), null, 5));
        when(client.dispatch(any(), eq("scan-pii-b"), any(), any(), any()))
                .thenReturn(new ScanRouterClient.RouterScanOutcome(true, "LLM", 0.7, Map.of("found", false), null, 80));

        List<PolicyScanResult> results = dispatcher.dispatch(
                "run-1",
                List.of(
                        new RequiredScan("PII", "scan-pii-a", true),
                        new RequiredScan("PII", "scan-pii-b", false)),
                "body");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).ref()).isEqualTo("scan-pii-a");
        assertThat(results.get(0).tierOfDecision()).isEqualTo("BERT");
        assertThat(results.get(1).ref()).isEqualTo("scan-pii-b");
        assertThat(results.get(1).tierOfDecision()).isEqualTo("LLM");
    }

    @Test
    void no_client_records_each_scan_as_not_dispatched() {
        when(provider.getIfAvailable()).thenReturn(null);

        List<PolicyScanResult> results = dispatcher.dispatch(
                "run-1",
                List.of(
                        new RequiredScan("PII", "scan-pii-x", true),
                        new RequiredScan("PII", "scan-pii-y", false)),
                "body");

        assertThat(results).hasSize(2);
        for (PolicyScanResult r : results) {
            assertThat(r.dispatched()).isFalse();
            assertThat(r.error()).contains("ScanRouterClient bean unavailable");
            assertThat(r.tierOfDecision()).isNull();
            assertThat(r.durationMs()).isEqualTo(-1L);
        }
    }

    @Test
    void scan_with_null_ref_is_recorded_with_error_and_not_dispatched() {
        List<PolicyScanResult> results = dispatcher.dispatch(
                "run-1",
                List.of(
                        new RequiredScan("PII", null, true),
                        new RequiredScan("PII", "  ", false)),
                "body");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).dispatched()).isFalse();
        assertThat(results.get(0).error()).contains("scan ref missing or blank");
        assertThat(results.get(1).dispatched()).isFalse();
        assertThat(results.get(1).error()).contains("scan ref missing or blank");
    }

    @Test
    void dispatcher_exception_is_caught_and_recorded() {
        when(client.dispatch(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        List<PolicyScanResult> results = dispatcher.dispatch(
                "run-1",
                List.of(new RequiredScan("PII", "scan-pii-x", true)),
                "body");

        assertThat(results).hasSize(1);
        // dispatched=true (we attempted) but error captured
        assertThat(results.get(0).dispatched()).isTrue();
        assertThat(results.get(0).error()).contains("dispatch exception").contains("boom");
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).blockingFailure()).isTrue();
    }

    @Test
    void nodeRunId_is_pipelineRunId_plus_scan_ref() {
        // Capture the nodeRunId passed to the client to verify the
        // idempotency-key convention is what PR3 + PR4 expect.
        org.mockito.ArgumentCaptor<String> nodeRunIdCap = org.mockito.ArgumentCaptor.forClass(String.class);
        when(client.dispatch(nodeRunIdCap.capture(), any(), any(), any(), any()))
                .thenReturn(new ScanRouterClient.RouterScanOutcome(true, "MOCK", 1.0, Map.of(), null, 1L));

        dispatcher.dispatch(
                "pipeline-run-99",
                List.of(new RequiredScan("PII", "scan-pii-x", true)),
                "body");

        assertThat(nodeRunIdCap.getValue()).isEqualTo("pipeline-run-99-scan-scan-pii-x");
    }

    @Test
    void null_pipelineRunId_falls_back_to_scan_ref_only() {
        org.mockito.ArgumentCaptor<String> nodeRunIdCap = org.mockito.ArgumentCaptor.forClass(String.class);
        when(client.dispatch(nodeRunIdCap.capture(), any(), any(), any(), any()))
                .thenReturn(new ScanRouterClient.RouterScanOutcome(true, "MOCK", 1.0, Map.of(), null, 1L));

        dispatcher.dispatch(
                null,
                List.of(new RequiredScan("PII", "scan-pii-x", true)),
                "body");

        assertThat(nodeRunIdCap.getValue()).isEqualTo("scan-scan-pii-x");
    }
}
