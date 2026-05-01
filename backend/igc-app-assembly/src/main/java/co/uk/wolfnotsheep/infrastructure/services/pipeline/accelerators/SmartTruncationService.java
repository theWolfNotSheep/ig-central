package co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Accelerator: reduces the text sent to the LLM by extracting only key sections
 * (headers, first/last pages, TOC, signatures). Unlike other accelerators, this
 * does NOT produce a classification — it modifies the document's extracted text
 * to reduce token count before the aiClassification node.
 */
@Service
public class SmartTruncationService {

    private static final Logger log = LoggerFactory.getLogger(SmartTruncationService.class);

    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^\\s*(#{1,6}\\s+.+|[A-Z][A-Z\\s]{3,}:?|\\d+\\.\\s+[A-Z].+|={3,}|—{3,})$",
            Pattern.MULTILINE
    );

    private static final Pattern SIGNATURE_PATTERN = Pattern.compile(
            "(?i)(sign(ed|ature)|witness|dated?|name:\\s|title:\\s|authoris|approv)",
            Pattern.MULTILINE
    );

    private static final Pattern TOC_PATTERN = Pattern.compile(
            "(?i)(table\\s+of\\s+contents|contents|index)",
            Pattern.MULTILINE
    );

    /**
     * Truncate the document's extracted text to key sections.
     * Returns the truncated text. Does NOT produce a classification.
     */
    public String truncate(DocumentModel doc, Map<String, Object> nodeConfig) {
        String text = doc.getExtractedText();
        if (text == null || text.isBlank()) return text;

        int maxChars = toInt(nodeConfig.get("maxChars"), 10_000);
        boolean includeHeaders = toBool(nodeConfig.get("includeHeaders"), true);
        boolean includeToc = toBool(nodeConfig.get("includeToc"), true);
        boolean includeSignatures = toBool(nodeConfig.get("includeSignatures"), true);

        int originalLength = text.length();
        if (originalLength <= maxChars) {
            log.debug("[SmartTrunc] Text already within limit ({} <= {})", originalLength, maxChars);
            return text;
        }

        List<String> sections = new ArrayList<>();
        int budgetUsed = 0;

        // Always include first page (first ~2000 chars)
        int firstPageEnd = Math.min(2000, text.length());
        String firstPage = text.substring(0, firstPageEnd);
        sections.add("--- FIRST PAGE ---\n" + firstPage);
        budgetUsed += firstPage.length();

        // Extract TOC if present
        if (includeToc) {
            String toc = extractSection(text, TOC_PATTERN, 1500);
            if (toc != null && budgetUsed + toc.length() <= maxChars) {
                sections.add("--- TABLE OF CONTENTS ---\n" + toc);
                budgetUsed += toc.length();
            }
        }

        // Extract headings
        if (includeHeaders) {
            String headings = extractHeadings(text);
            if (!headings.isBlank() && budgetUsed + headings.length() <= maxChars) {
                sections.add("--- HEADINGS ---\n" + headings);
                budgetUsed += headings.length();
            }
        }

        // Extract signature sections
        if (includeSignatures) {
            String sigs = extractSection(text, SIGNATURE_PATTERN, 1000);
            if (sigs != null && budgetUsed + sigs.length() <= maxChars) {
                sections.add("--- SIGNATURES ---\n" + sigs);
                budgetUsed += sigs.length();
            }
        }

        // Include last page (last ~2000 chars)
        if (text.length() > 4000) {
            int lastPageStart = Math.max(text.length() - 2000, firstPageEnd);
            String lastPage = text.substring(lastPageStart);
            if (budgetUsed + lastPage.length() <= maxChars) {
                sections.add("--- LAST PAGE ---\n" + lastPage);
                budgetUsed += lastPage.length();
            }
        }

        String truncated = String.join("\n\n", sections);
        log.info("[SmartTrunc] Truncated doc {} from {} to {} chars",
                doc.getId(), originalLength, truncated.length());

        return truncated;
    }

    private String extractHeadings(String text) {
        StringBuilder sb = new StringBuilder();
        Matcher m = HEADING_PATTERN.matcher(text);
        while (m.find()) {
            sb.append(m.group().trim()).append("\n");
        }
        return sb.toString();
    }

    private String extractSection(String text, Pattern sectionStart, int maxLen) {
        Matcher m = sectionStart.matcher(text);
        if (m.find()) {
            int start = Math.max(0, m.start() - 100);
            int end = Math.min(text.length(), m.start() + maxLen);
            return text.substring(start, end);
        }
        return null;
    }

    private static int toInt(Object val, int defaultVal) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }

    private static boolean toBool(Object val, boolean defaultVal) {
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultVal;
    }
}
