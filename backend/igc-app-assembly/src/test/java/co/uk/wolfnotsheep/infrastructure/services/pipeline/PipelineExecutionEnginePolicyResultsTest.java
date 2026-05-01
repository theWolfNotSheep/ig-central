package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1.9 PR4 — focused tests for the static aggregation helpers
 * {@code mergeExtractedFields} and {@code aggregateScanFindings} on
 * {@link PipelineExecutionEngine}. The full
 * {@code persistPolicyResults} flow needs the engine wired with all
 * its repository deps + a real classification result; integration
 * coverage lives with the broader pipeline test suite (gated on
 * issue #7).
 */
class PipelineExecutionEnginePolicyResultsTest {

    // ── mergeExtractedFields ──────────────────────────

    @Test
    void mergeExtractedFields_returns_empty_map_for_null_inputs() {
        Map<String, String> merged = PipelineExecutionEngine.mergeExtractedFields(null, null);
        assertThat(merged).isEmpty();
    }

    @Test
    void mergeExtractedFields_passes_through_existing_when_no_extractions() {
        Map<String, String> existing = Map.of("a", "1", "b", "2");
        Map<String, String> merged = PipelineExecutionEngine.mergeExtractedFields(existing, List.of());
        assertThat(merged).containsExactlyInAnyOrderEntriesOf(existing);
    }

    @Test
    void mergeExtractedFields_adds_extracted_fields_from_successful_results() {
        // Successful row = dispatched=true and error key absent or null.
        // The dispatcher emits error=null on success, never error=false.
        Map<String, Object> r = Map.of(
                "dispatched", true,
                "extractedFields", Map.of("c", "3", "d", "4"));
        Map<String, Object> r2 = Map.of(
                "dispatched", true,
                "extractedFields", Map.of("e", "5"));
        Map<String, String> merged = PipelineExecutionEngine.mergeExtractedFields(
                Map.of("a", "1"), Arrays.asList(r, r2));

        assertThat(merged)
                .containsEntry("a", "1")
                .containsEntry("c", "3")
                .containsEntry("d", "4")
                .containsEntry("e", "5")
                .hasSize(4);
    }

    @Test
    void mergeExtractedFields_does_not_overwrite_existing_keys() {
        Map<String, Object> r = Map.of(
                "dispatched", true,
                "extractedFields", Map.of("a", "from-extraction", "b", "new"));
        Map<String, String> merged = PipelineExecutionEngine.mergeExtractedFields(
                Map.of("a", "from-classification"), List.of(r));

        // classification-time metadata wins on conflict
        assertThat(merged.get("a")).isEqualTo("from-classification");
        assertThat(merged.get("b")).isEqualTo("new");
    }

    @Test
    void mergeExtractedFields_skips_failed_results() {
        // dispatched=false and error≠null both disqualify the row
        Map<String, Object> notDispatched = Map.of(
                "dispatched", false,
                "extractedFields", Map.of("x", "1"));
        Map<String, Object> errored = new LinkedHashMap<>();
        errored.put("dispatched", true);
        errored.put("error", "HTTP 502");
        errored.put("extractedFields", Map.of("y", "2"));
        Map<String, Object> ok = Map.of(
                "dispatched", true,
                "extractedFields", Map.of("z", "3"));

        Map<String, String> merged = PipelineExecutionEngine.mergeExtractedFields(
                null, Arrays.asList(notDispatched, errored, ok));

        assertThat(merged).containsOnlyKeys("z").containsEntry("z", "3");
    }

    @Test
    void mergeExtractedFields_string_coerces_non_string_values() {
        Map<String, Object> r = Map.of(
                "dispatched", true,
                "extractedFields", Map.of("count", 42, "amount", 99.5, "active", true));
        Map<String, String> merged = PipelineExecutionEngine.mergeExtractedFields(null, List.of(r));

        assertThat(merged)
                .containsEntry("count", "42")
                .containsEntry("amount", "99.5")
                .containsEntry("active", "true");
    }

    @Test
    void mergeExtractedFields_skips_null_or_blank_keys_and_null_values() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("good", "ok");
        fields.put("", "blank-key");
        fields.put("   ", "whitespace-key");
        fields.put("nullValue", null);
        Map<String, Object> r = Map.of("dispatched", true, "extractedFields", fields);
        Map<String, String> merged = PipelineExecutionEngine.mergeExtractedFields(null, List.of(r));

        assertThat(merged).containsOnlyKeys("good");
    }

    @Test
    void mergeExtractedFields_skips_rows_with_non_map_extractedFields() {
        Map<String, Object> r = Map.of("dispatched", true, "extractedFields", "not-a-map");
        Map<String, String> merged = PipelineExecutionEngine.mergeExtractedFields(null, List.of(r));
        assertThat(merged).isEmpty();
    }

    // ── aggregateScanFindings ─────────────────────────

    @Test
    void aggregateScanFindings_returns_empty_map_for_null_input() {
        assertThat(PipelineExecutionEngine.aggregateScanFindings(null)).isEmpty();
    }

    @Test
    void aggregateScanFindings_returns_empty_map_for_empty_input() {
        assertThat(PipelineExecutionEngine.aggregateScanFindings(List.of())).isEmpty();
    }

    @Test
    void aggregateScanFindings_keys_by_ref_and_passes_row_through() {
        Map<String, Object> r1 = Map.of(
                "ref", "scan-pii-x",
                "scanType", "PII",
                "dispatched", true,
                "result", Map.of("found", true));
        Map<String, Object> r2 = Map.of(
                "ref", "scan-pii-y",
                "scanType", "PII",
                "dispatched", true,
                "result", Map.of("found", false));

        Map<String, Object> findings = PipelineExecutionEngine.aggregateScanFindings(List.of(r1, r2));

        assertThat(findings).hasSize(2);
        assertThat(findings.get("scan-pii-x")).isEqualTo(r1);
        assertThat(findings.get("scan-pii-y")).isEqualTo(r2);
    }

    @Test
    void aggregateScanFindings_skips_rows_with_null_ref() {
        Map<String, Object> noRef = new LinkedHashMap<>();
        noRef.put("scanType", "PII");
        noRef.put("ref", null);
        Map<String, Object> ok = Map.of("ref", "scan-pii-x", "scanType", "PII");

        Map<String, Object> findings = PipelineExecutionEngine.aggregateScanFindings(
                Arrays.asList(noRef, ok));

        assertThat(findings).hasSize(1).containsKey("scan-pii-x");
    }

    @Test
    void aggregateScanFindings_last_row_for_duplicate_ref_wins() {
        Map<String, Object> first = Map.of("ref", "scan-pii-x", "result", Map.of("found", true));
        Map<String, Object> second = Map.of("ref", "scan-pii-x", "result", Map.of("found", false));

        Map<String, Object> findings = PipelineExecutionEngine.aggregateScanFindings(
                Arrays.asList(first, second));

        assertThat(findings).hasSize(1);
        assertThat(findings.get("scan-pii-x")).isEqualTo(second);
    }
}
