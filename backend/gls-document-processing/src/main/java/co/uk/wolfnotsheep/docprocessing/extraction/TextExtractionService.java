package co.uk.wolfnotsheep.docprocessing.extraction;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Extracts text and metadata from documents using Apache Tika.
 * Supports PDF, Office (docx, xlsx, pptx), images (with OCR if Tesseract available),
 * plain text, HTML, email formats, and hundreds more.
 */
@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);
    private static final int MAX_TEXT_LENGTH = 500_000;

    private final Tika tika = new Tika();

    public ExtractionResult extract(InputStream inputStream, String fileName) {
        try {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

            String text = tika.parseToString(inputStream, metadata, MAX_TEXT_LENGTH);

            Map<String, String> extractedMetadata = new HashMap<>();
            for (String name : metadata.names()) {
                extractedMetadata.put(name, metadata.get(name));
            }

            int pageCount = 0;
            String pages = metadata.get("xmpTPg:NPages");
            if (pages != null) {
                try { pageCount = Integer.parseInt(pages); } catch (NumberFormatException ignored) {}
            }

            log.info("Extracted {} chars from {} (detected type: {}, pages: {})",
                    text.length(), fileName,
                    metadata.get(Metadata.CONTENT_TYPE),
                    pageCount);

            return new ExtractionResult(text, extractedMetadata, pageCount);
        } catch (Exception e) {
            log.error("Text extraction failed for {}: {}", fileName, e.getMessage(), e);
            return new ExtractionResult("", Map.of(), 0);
        }
    }

    public record ExtractionResult(
            String text,
            Map<String, String> metadata,
            int pageCount
    ) {}
}
