package co.uk.wolfnotsheep.indexing.service;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-tests the pure JSON-builder helper. The HTTP / Mongo dependencies
 * are not exercised here — those are covered by the controller tests +
 * a future Testcontainers smoke.
 */
class IndexingServiceBuildEsDocumentTest {

    @Test
    void minimal_document_serialises_with_required_fields() {
        DocumentModel doc = new DocumentModel();
        doc.setId("doc-1");
        doc.setStatus(DocumentStatus.CLASSIFIED);
        doc.setOriginalFileName("contract.pdf");
        doc.setMimeType("application/pdf");
        doc.setFileSizeBytes(12345L);

        String json = IndexingService.buildEsDocument(doc);

        assertThat(json).contains("\"originalFileName\":\"contract.pdf\"");
        assertThat(json).contains("\"status\":\"CLASSIFIED\"");
        assertThat(json).contains("\"mimeType\":\"application/pdf\"");
        assertThat(json).contains("\"fileSizeBytes\":12345");
        assertThat(json).startsWith("{").endsWith("}");
    }

    @Test
    void full_document_includes_classification_metadata_and_tags() {
        DocumentModel doc = new DocumentModel();
        doc.setId("doc-2");
        doc.setStatus(DocumentStatus.GOVERNANCE_APPLIED);
        doc.setOriginalFileName("hr-letter.pdf");
        doc.setMimeType("application/pdf");
        doc.setFileSizeBytes(0L);
        doc.setCategoryId("cat-hr");
        doc.setCategoryName("HR > Letters");
        doc.setSensitivityLabel(SensitivityLabel.CONFIDENTIAL);
        doc.setTags(List.of("hr", "letter"));
        doc.setExtractedMetadata(Map.of("employee_name", "Jane Doe", "leave_type", "maternity"));

        String json = IndexingService.buildEsDocument(doc);

        assertThat(json).contains("\"categoryId\":\"cat-hr\"");
        assertThat(json).contains("\"categoryName\":\"HR > Letters\"");
        assertThat(json).contains("\"sensitivityLabel\":\"CONFIDENTIAL\"");
        assertThat(json).contains("\"tags\":[\"hr\",\"letter\"]");
        assertThat(json).contains("\"extractedMetadata\":{");
        assertThat(json).contains("\"employee_name\":\"Jane Doe\"");
    }

    @Test
    void escapes_quotes_and_newlines_in_string_values() {
        DocumentModel doc = new DocumentModel();
        doc.setId("doc-3");
        doc.setOriginalFileName("file with \"quotes\" and\nnewlines.pdf");
        doc.setStatus(DocumentStatus.CLASSIFIED);
        doc.setMimeType("application/pdf");
        doc.setFileSizeBytes(0L);

        String json = IndexingService.buildEsDocument(doc);

        assertThat(json).contains("\\\"quotes\\\"");
        assertThat(json).contains("\\n");
    }

    @Test
    void truncates_extracted_text_at_50000_chars() {
        DocumentModel doc = new DocumentModel();
        doc.setId("doc-4");
        doc.setStatus(DocumentStatus.CLASSIFIED);
        doc.setOriginalFileName("big.pdf");
        doc.setMimeType("application/pdf");
        doc.setFileSizeBytes(0L);
        doc.setExtractedText("a".repeat(60000));

        String json = IndexingService.buildEsDocument(doc);

        // Extracted text segment should appear and be exactly 50000 'a' chars (no longer).
        int start = json.indexOf("\"extractedText\":\"") + "\"extractedText\":\"".length();
        int end = json.indexOf("\"", start);
        assertThat(end - start).isEqualTo(50000);
    }
}
