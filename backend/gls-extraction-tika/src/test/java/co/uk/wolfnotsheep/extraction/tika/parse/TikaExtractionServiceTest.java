package co.uk.wolfnotsheep.extraction.tika.parse;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Format-agnostic tests for the Tika extractor. Real-format coverage
 * (PDFs, Office docs, .eml) lives in 0.5.4's integration tests once the
 * full module ships — these unit tests prove the wrapper logic is right,
 * not Tika itself.
 */
class TikaExtractionServiceTest {

    @Test
    void plain_text_passes_through_with_byte_count() {
        TikaExtractionService svc = new TikaExtractionService();
        byte[] bytes = "hello world".getBytes(StandardCharsets.UTF_8);

        ExtractedText out = svc.extract(new ByteArrayInputStream(bytes), "hello.txt");

        assertThat(out.text()).contains("hello world");
        assertThat(out.byteCount()).isEqualTo(bytes.length);
        assertThat(out.detectedMimeType()).startsWith("text/plain");
        assertThat(out.truncated()).isFalse();
        assertThat(out.pageCount()).isNull();
    }

    @Test
    void empty_input_returns_empty_text_not_failure() {
        TikaExtractionService svc = new TikaExtractionService();

        ExtractedText out = svc.extract(new ByteArrayInputStream(new byte[0]), "empty.txt");

        assertThat(out.text()).isEmpty();
        assertThat(out.byteCount()).isZero();
        assertThat(out.truncated()).isFalse();
    }

    @Test
    void character_ceiling_truncates_and_flags_truncated() {
        TikaExtractionService svc = new TikaExtractionService(/* maxCharacters */ 100);
        byte[] bytes = ("A".repeat(500)).getBytes(StandardCharsets.UTF_8);

        ExtractedText out = svc.extract(new ByteArrayInputStream(bytes), "long.txt");

        assertThat(out.text().length()).isEqualTo(100);
        assertThat(out.truncated()).isTrue();
    }

    @Test
    void null_filename_is_tolerated() {
        TikaExtractionService svc = new TikaExtractionService();

        ExtractedText out = svc.extract(
                new ByteArrayInputStream("plain bytes".getBytes(StandardCharsets.UTF_8)), null);

        // Tika still detects a mime; sniffing without a filename hint
        // typically classifies short ASCII as text/plain.
        assertThat(out.text()).contains("plain bytes");
        assertThat(out.detectedMimeType()).isNotBlank();
    }

    @Test
    void corrupt_bytes_marked_with_a_format_extension_throw_unparseable() {
        // Pretending to be a PDF but with garbage content. The PDF parser
        // refuses to parse non-PDF bytes — surfaces as a TikaException
        // wrapped in our UnparseableDocumentException.
        TikaExtractionService svc = new TikaExtractionService();
        byte[] notReallyPdf = "%PDF-1.4\nthis is not a real pdf at all\n%%EOF".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() ->
                svc.extract(new ByteArrayInputStream(notReallyPdf), "fake.pdf"))
                .isInstanceOf(UnparseableDocumentException.class);
    }

    @Test
    void zero_or_negative_max_characters_is_rejected() {
        assertThatThrownBy(() -> new TikaExtractionService(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TikaExtractionService(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
