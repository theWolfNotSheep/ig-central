package co.uk.wolfnotsheep.infrastructure.services;

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
}
