package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 1.9 PR3 — focused tests for {@link MetadataExtractionDispatcher}.
 * Same fixture pattern as {@code PolicyScanDispatcherTest}: the
 * {@link ScanRouterClient} is mocked via the {@link ObjectProvider} the
 * dispatcher consumes.
 */
class MetadataExtractionDispatcherTest {

    private ScanRouterClient client;
    private ObjectProvider<ScanRouterClient> provider;
    private MetadataExtractionDispatcher dispatcher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(ScanRouterClient.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        dispatcher = new MetadataExtractionDispatcher(provider);
    }

    @Test
    void empty_or_null_schema_list_returns_empty_results() {
        assertThat(dispatcher.dispatch("run-1", List.of(), "text")).isEmpty();
        assertThat(dispatcher.dispatch("run-1", null, "text")).isEmpty();
    }

    @Test
    void single_schema_success_uses_extract_metadata_block_name_and_returns_fields() {
        when(client.dispatch(any(), eq("extract-metadata-hr-leave"), any(), eq("body"), any()))
                .thenReturn(new ScanRouterClient.RouterScanOutcome(
                        true, "SLM", 0.88,
                        Map.of("employee_name", "Alice", "leave_type", "maternity"),
                        null, 33L));

        List<MetadataExtractionResult> results = dispatcher.dispatch(
                "run-1", List.of("hr-leave"), "body");

        assertThat(results).hasSize(1);
        MetadataExtractionResult r = results.get(0);
        assertThat(r.schemaId()).isEqualTo("hr-leave");
        assertThat(r.blockRef()).isEqualTo("extract-metadata-hr-leave");
        assertThat(r.dispatched()).isTrue();
        assertThat(r.tierOfDecision()).isEqualTo("SLM");
        assertThat(r.confidence()).isEqualTo(0.88);
        assertThat(r.error()).isNull();
        assertThat(r.success()).isTrue();
        assertThat(r.extractedFields())
                .containsEntry("employee_name", "Alice")
                .containsEntry("leave_type", "maternity");
        assertThat(r.durationMs()).isEqualTo(33L);
    }

    @Test
    void transport_failure_is_recorded_with_dispatched_true_and_error() {
        when(client.dispatch(any(), eq("extract-metadata-x"), any(), any(), any()))
                .thenReturn(ScanRouterClient.RouterScanOutcome.failure("HTTP 502", 100L));

        List<MetadataExtractionResult> results = dispatcher.dispatch(
                "run-1", List.of("x"), "body");

        assertThat(results).hasSize(1);
        MetadataExtractionResult r = results.get(0);
        assertThat(r.dispatched()).isTrue();
        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("HTTP 502");
    }

    @Test
    void multiple_schemas_yield_results_in_order() {
        when(client.dispatch(any(), eq("extract-metadata-a"), any(), any(), any()))
                .thenReturn(new ScanRouterClient.RouterScanOutcome(true, "BERT", 0.99,
                        Map.of("foo", "bar"), null, 5));
        when(client.dispatch(any(), eq("extract-metadata-b"), any(), any(), any()))
                .thenReturn(new ScanRouterClient.RouterScanOutcome(true, "LLM", 0.7,
                        Map.of("baz", "qux"), null, 80));

        List<MetadataExtractionResult> results = dispatcher.dispatch(
                "run-1", Arrays.asList("a", "b"), "body");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).schemaId()).isEqualTo("a");
        assertThat(results.get(0).tierOfDecision()).isEqualTo("BERT");
        assertThat(results.get(1).schemaId()).isEqualTo("b");
        assertThat(results.get(1).tierOfDecision()).isEqualTo("LLM");
    }

    @Test
    void no_client_records_each_schema_as_not_dispatched() {
        when(provider.getIfAvailable()).thenReturn(null);

        List<MetadataExtractionResult> results = dispatcher.dispatch(
                "run-1", Arrays.asList("a", "b"), "body");

        assertThat(results).hasSize(2);
        for (MetadataExtractionResult r : results) {
            assertThat(r.dispatched()).isFalse();
            assertThat(r.error()).contains("ScanRouterClient bean unavailable");
            assertThat(r.tierOfDecision()).isNull();
            assertThat(r.durationMs()).isEqualTo(-1L);
        }
    }

    @Test
    void blank_or_null_schemaId_is_recorded_with_error() {
        List<MetadataExtractionResult> results = dispatcher.dispatch(
                "run-1", Arrays.asList("", "  ", null), "body");

        assertThat(results).hasSize(3);
        for (MetadataExtractionResult r : results) {
            assertThat(r.dispatched()).isFalse();
            assertThat(r.error()).contains("schemaId missing or blank");
        }
    }

    @Test
    void dispatcher_exception_is_caught_and_recorded() {
        when(client.dispatch(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        List<MetadataExtractionResult> results = dispatcher.dispatch(
                "run-1", List.of("x"), "body");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).dispatched()).isTrue();
        assertThat(results.get(0).error()).contains("dispatch exception").contains("boom");
    }

    @Test
    void nodeRunId_is_pipelineRunId_plus_extract_schema() {
        org.mockito.ArgumentCaptor<String> nodeRunIdCap = org.mockito.ArgumentCaptor.forClass(String.class);
        when(client.dispatch(nodeRunIdCap.capture(), any(), any(), any(), any()))
                .thenReturn(new ScanRouterClient.RouterScanOutcome(true, "MOCK", 1.0, Map.of(), null, 1L));

        dispatcher.dispatch("pipeline-run-99", List.of("hr-leave"), "body");

        assertThat(nodeRunIdCap.getValue()).isEqualTo("pipeline-run-99-extract-hr-leave");
    }

    @Test
    void null_pipelineRunId_falls_back_to_schema_id_only() {
        org.mockito.ArgumentCaptor<String> nodeRunIdCap = org.mockito.ArgumentCaptor.forClass(String.class);
        when(client.dispatch(nodeRunIdCap.capture(), any(), any(), any(), any()))
                .thenReturn(new ScanRouterClient.RouterScanOutcome(true, "MOCK", 1.0, Map.of(), null, 1L));

        dispatcher.dispatch(null, List.of("hr-leave"), "body");

        assertThat(nodeRunIdCap.getValue()).isEqualTo("extract-hr-leave");
    }
}
