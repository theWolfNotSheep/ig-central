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

@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);
    private static final int DEFAULT_MAX_TEXT_LENGTH = 500_000;

    private final Tika tika = new Tika();

    /**
     * Extract text using default settings.
     */
    public ExtractionResult extract(InputStream inputStream, String fileName) {
        return extract(inputStream, fileName, DEFAULT_MAX_TEXT_LENGTH, true, true);
    }

    /**
     * Extract text using configuration from the EXTRACTOR block.
     *
     * @param maxTextLength    maximum characters to extract (from block: "maxTextLength")
     * @param extractDublinCore whether to extract Dublin Core metadata (from block: "extractDublinCore")
     * @param extractMetadata  whether to include raw Tika metadata (from block: "extractMetadata")
     */
    public ExtractionResult extract(InputStream inputStream, String fileName,
                                     int maxTextLength, boolean extractDublinCore, boolean extractMetadata) {
        try {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

            String text = tika.parseToString(inputStream, metadata, maxTextLength);

            Map<String, String> rawMetadata = new HashMap<>();
            if (extractMetadata) {
                for (String name : metadata.names()) {
                    rawMetadata.put(name, metadata.get(name));
                }
            } else {
                // Always need page count keys even if metadata extraction is off
                for (String key : new String[]{"xmpTPg:NPages", "meta:page-count", "Content-Type"}) {
                    String val = metadata.get(key);
                    if (val != null) rawMetadata.put(key, val);
                }
            }

            int pageCount = 0;
            String pages = rawMetadata.get("xmpTPg:NPages");
            if (pages == null) pages = rawMetadata.get("meta:page-count");
            if (pages != null) {
                try { pageCount = Integer.parseInt(pages); } catch (NumberFormatException ignored) {}
            }

            // Build Dublin Core from the extracted string map
            Map<String, String> dublinCore = extractDublinCore
                    ? extractDublinCore(rawMetadata)
                    : Map.of();

            log.info("Extracted {} chars from {} (type: {}, pages: {}, dc fields: {})",
                    text.length(), fileName,
                    rawMetadata.get("Content-Type"),
                    pageCount, dublinCore.size());

            return new ExtractionResult(text, rawMetadata, dublinCore, pageCount);
        } catch (Exception e) {
            log.error("Text extraction failed for {}: {}", fileName, e.getMessage(), e);
            // Return with error marker so the pipeline can detect extraction failure
            return new ExtractionResult(
                    "[EXTRACTION_FAILED: " + e.getMessage() + "]",
                    Map.of("_extractionError", e.getMessage() != null ? e.getMessage() : "unknown"),
                    Map.of(), 0);
        }
    }

    private Map<String, String> extractDublinCore(Map<String, String> raw) {
        Map<String, String> dc = new HashMap<>();

        // Core 15 Dublin Core elements
        mapDc(dc, "title", raw, "dc:title", "title", "pdf:docinfo:title", "cp:title");
        mapDc(dc, "creator", raw, "dc:creator", "meta:author", "meta:last-author", "Author", "pdf:docinfo:author");
        mapDc(dc, "subject", raw, "dc:subject", "subject", "pdf:docinfo:subject", "cp:subject");
        mapDc(dc, "description", raw, "dc:description", "description", "comment", "w:Comments", "cp:description");
        mapDc(dc, "publisher", raw, "dc:publisher", "publisher");
        mapDc(dc, "contributor", raw, "dc:contributor", "contributor");
        mapDc(dc, "date", raw, "dcterms:created", "dc:date", "meta:creation-date", "created", "Creation-Date", "pdf:docinfo:created");
        mapDc(dc, "type", raw, "dc:type");
        mapDc(dc, "format", raw, "dc:format", "Content-Type");
        mapDc(dc, "identifier", raw, "dc:identifier", "identifier");
        mapDc(dc, "source", raw, "dc:source", "source");
        mapDc(dc, "language", raw, "dc:language", "language", "Content-Language");
        mapDc(dc, "relation", raw, "dc:relation", "relation");
        mapDc(dc, "coverage", raw, "dc:coverage", "coverage");
        mapDc(dc, "rights", raw, "dc:rights", "rights", "pdf:docinfo:rights");

        // Extended / useful fields
        mapDc(dc, "modified", raw, "dcterms:modified", "Last-Modified", "meta:save-date", "pdf:docinfo:modified");
        mapDc(dc, "keywords", raw, "meta:keyword", "Keywords", "pdf:docinfo:keywords");
        mapDc(dc, "pageCount", raw, "xmpTPg:NPages", "meta:page-count", "Page-Count");
        mapDc(dc, "wordCount", raw, "meta:word-count");
        mapDc(dc, "characterCount", raw, "meta:character-count");
        mapDc(dc, "lineCount", raw, "meta:line-count");
        mapDc(dc, "paragraphCount", raw, "meta:paragraph-count");
        mapDc(dc, "application", raw, "extended-properties:Application", "Application-Name",
                "xmp:CreatorTool", "pdf:docinfo:creator_tool", "pdf:docinfo:producer", "pdf:producer");
        mapDc(dc, "revision", raw, "cp:revision");
        mapDc(dc, "template", raw, "extended-properties:Template");
        mapDc(dc, "company", raw, "extended-properties:Company");
        mapDc(dc, "lastAuthor", raw, "meta:last-author");
        mapDc(dc, "printDate", raw, "meta:print-date");

        dc.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isBlank());
        return dc;
    }

    private void mapDc(Map<String, String> dc, String dcKey, Map<String, String> raw, String... keys) {
        for (String key : keys) {
            String val = raw.get(key);
            if (val != null && !val.isBlank()) {
                dc.put(dcKey, val);
                return;
            }
        }
    }

    public record ExtractionResult(
            String text,
            Map<String, String> metadata,
            Map<String, String> dublinCore,
            int pageCount
    ) {}
}
