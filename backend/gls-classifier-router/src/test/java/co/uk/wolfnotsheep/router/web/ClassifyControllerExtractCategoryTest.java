package co.uk.wolfnotsheep.router.web;

import co.uk.wolfnotsheep.router.model.ClassifyResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClassifyControllerExtractCategoryTest {

    @Test
    void prefers_categoryCode_over_categoryId_and_category() {
        ClassifyResponse body = new ClassifyResponse();
        body.setResult(Map.of(
                "categoryCode", "HR_LETTERS",
                "categoryId", "abc-123",
                "category", "HR Letters"));

        assertThat(ClassifyController.extractCategory(body)).isEqualTo("HR_LETTERS");
    }

    @Test
    void falls_back_to_categoryId_when_no_code() {
        ClassifyResponse body = new ClassifyResponse();
        body.setResult(Map.of("categoryId", "abc-123", "category", "HR Letters"));

        assertThat(ClassifyController.extractCategory(body)).isEqualTo("abc-123");
    }

    @Test
    void falls_back_to_category_name_when_no_code_or_id() {
        ClassifyResponse body = new ClassifyResponse();
        body.setResult(Map.of("category", "HR Letters"));

        assertThat(ClassifyController.extractCategory(body)).isEqualTo("HR Letters");
    }

    @Test
    void returns_null_when_result_has_no_category_keys() {
        ClassifyResponse body = new ClassifyResponse();
        body.setResult(Map.of("confidence", 0.9));

        assertThat(ClassifyController.extractCategory(body)).isNull();
    }

    @Test
    void returns_null_for_null_body_or_null_result() {
        assertThat(ClassifyController.extractCategory(null)).isNull();
        ClassifyResponse empty = new ClassifyResponse();
        assertThat(ClassifyController.extractCategory(empty)).isNull();
    }

    @Test
    void blank_string_value_skipped_in_favour_of_next_key() {
        ClassifyResponse body = new ClassifyResponse();
        body.setResult(Map.of("categoryCode", "", "categoryId", "abc-123"));

        assertThat(ClassifyController.extractCategory(body)).isEqualTo("abc-123");
    }

    @Test
    void non_string_category_value_skipped() {
        ClassifyResponse body = new ClassifyResponse();
        body.setResult(Map.of("categoryCode", 42, "category", "Letters"));

        assertThat(ClassifyController.extractCategory(body)).isEqualTo("Letters");
    }
}
