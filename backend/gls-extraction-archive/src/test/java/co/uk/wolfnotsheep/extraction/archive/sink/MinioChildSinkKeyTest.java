package co.uk.wolfnotsheep.extraction.archive.sink;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the package-private key derivation in
 * {@link MinioChildSink}. The full upload path is exercised in the
 * controller test against a mocked {@link ChildSink}; this targets the
 * static helper directly so the key shape is locked.
 */
class MinioChildSinkKeyTest {

    @Test
    void key_strips_path_separators_and_keeps_extension() {
        assertThat(MinioChildSink.childKey("node-1", 0, "nested/dir/file.pdf"))
                .isEqualTo("node-1/0-nested_dir_file.pdf");
    }

    @Test
    void key_handles_null_filename() {
        assertThat(MinioChildSink.childKey("node-1", 7, null))
                .isEqualTo("node-1/7-child");
    }

    @Test
    void key_replaces_control_characters() {
        assertThat(MinioChildSink.childKey("n", 0, "a\u0001b\u0002.bin"))
                .isEqualTo("n/0-a_b_.bin");
    }
}
