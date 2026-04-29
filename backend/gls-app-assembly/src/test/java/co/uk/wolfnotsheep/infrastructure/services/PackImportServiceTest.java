package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.governance.models.PiiTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1.7 PR2 — focused tests for the {@code strListOrNull} helper
 * that drives the preserve-on-missing semantics for
 * {@code applicableCategoryIds[]}. Full apply* method coverage
 * requires a test harness with all the repository deps mocked; left
 * as a follow-up alongside the broader PackImportService test suite.
 */
class PackImportServiceTest {

    @Test
    void strListOrNull_returns_null_when_key_is_absent() {
        assertThat(PackImportService.strListOrNull(Map.of("other", "x"), "applicableCategoryIds"))
                .isNull();
    }

    @Test
    void strListOrNull_returns_empty_list_when_value_is_explicit_empty_array() {
        Map<String, Object> map = Map.of("applicableCategoryIds", List.of());
        assertThat(PackImportService.strListOrNull(map, "applicableCategoryIds"))
                .isNotNull()
                .isEmpty();
    }

    @Test
    void strListOrNull_returns_populated_list_when_value_is_array_of_strings() {
        Map<String, Object> map = Map.of("applicableCategoryIds", List.of("cat-hr", "cat-finance"));
        assertThat(PackImportService.strListOrNull(map, "applicableCategoryIds"))
                .containsExactly("cat-hr", "cat-finance");
    }

    @Test
    void strListOrNull_coerces_non_string_elements_via_toString() {
        Map<String, Object> map = Map.of("applicableCategoryIds", List.of(1, 2L, "three"));
        assertThat(PackImportService.strListOrNull(map, "applicableCategoryIds"))
                .containsExactly("1", "2", "three");
    }

    @Test
    void strListOrNull_returns_null_when_value_is_not_a_list() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("applicableCategoryIds", "not-a-list");
        assertThat(PackImportService.strListOrNull(map, "applicableCategoryIds"))
                .isNull();
    }

    @Test
    void strListOrNull_distinguishes_missing_key_from_explicit_null_value() {
        // containsKey + null value = explicit null. The helper treats
        // this as "value not a list" → null. Same as "missing key".
        // Both leave the existing field untouched, which is the
        // preserve-on-missing semantics we want.
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("applicableCategoryIds", null);
        assertThat(PackImportService.strListOrNull(map, "applicableCategoryIds"))
                .isNull();
    }

    // ── Phase 1.9 PR1 — buildScanSystemPrompt ─────────

    @Test
    void buildScanSystemPrompt_includes_displayName_when_present() {
        PiiTypeDefinition pii = new PiiTypeDefinition();
        pii.setKey("UK_NATIONAL_INSURANCE_NUMBER");
        pii.setDisplayName("UK National Insurance Number");
        pii.setDescription("UK NI number, format: 2 letters + 6 digits + 1 letter");
        pii.setExamples(List.of("AB123456C", "QQ123456D"));

        String prompt = PackImportService.buildScanSystemPrompt(pii);

        assertThat(prompt)
                .contains("UK National Insurance Number")
                .contains("Definition: UK NI number")
                .contains("Examples: AB123456C, QQ123456D")
                .contains("found")
                .contains("instances")
                .contains("confidence");
    }

    @Test
    void buildScanSystemPrompt_falls_back_to_key_when_displayName_is_null() {
        PiiTypeDefinition pii = new PiiTypeDefinition();
        pii.setKey("CUSTOM_PII_KEY");

        String prompt = PackImportService.buildScanSystemPrompt(pii);

        assertThat(prompt).contains("CUSTOM_PII_KEY");
    }

    @Test
    void buildScanSystemPrompt_omits_definition_line_when_description_is_blank() {
        PiiTypeDefinition pii = new PiiTypeDefinition();
        pii.setKey("X");
        pii.setDisplayName("Thing");
        pii.setDescription("   ");

        String prompt = PackImportService.buildScanSystemPrompt(pii);

        assertThat(prompt).doesNotContain("Definition:");
    }

    @Test
    void buildScanSystemPrompt_omits_examples_line_when_examples_is_empty_or_null() {
        PiiTypeDefinition piiNoExamples = new PiiTypeDefinition();
        piiNoExamples.setKey("X");
        piiNoExamples.setDisplayName("Thing");
        piiNoExamples.setExamples(List.of());

        assertThat(PackImportService.buildScanSystemPrompt(piiNoExamples))
                .doesNotContain("Examples:");

        PiiTypeDefinition piiNullExamples = new PiiTypeDefinition();
        piiNullExamples.setKey("Y");
        piiNullExamples.setDisplayName("Other");
        piiNullExamples.setExamples(null);

        assertThat(PackImportService.buildScanSystemPrompt(piiNullExamples))
                .doesNotContain("Examples:");
    }

    @Test
    void buildScanSystemPrompt_emits_strict_JSON_response_contract() {
        PiiTypeDefinition pii = new PiiTypeDefinition();
        pii.setKey("X");
        pii.setDisplayName("Thing");

        String prompt = PackImportService.buildScanSystemPrompt(pii);

        // The prompt instructs the model to return strict JSON. Without
        // this, the cascade-router worker can't parse the response.
        assertThat(prompt)
                .contains("strict JSON")
                .contains("\"found\"")
                .contains("\"instances\"")
                .contains("\"confidence\"")
                .contains("found=false");
    }
}
