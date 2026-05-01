package co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Accelerator: evaluates a list of rules (filename patterns, keyword density,
 * source/sender rules) before LLM classification. If a rule matches,
 * the document is auto-classified without calling the LLM.
 *
 * Rules are stored in the node's data map as a JSON array under the "rules" key.
 * Each rule has: field, operator, value, categoryId, categoryName, sensitivityLabel.
 */
@Service
public class RulesEngineService implements AcceleratorHandler {

    @Override
    public String getNodeTypeKey() { return "rulesEngine"; }

    private static final Logger log = LoggerFactory.getLogger(RulesEngineService.class);

    /**
     * Evaluate rules against the document. First matching rule wins (priority ordering).
     */
    public AcceleratorResult evaluate(DocumentModel doc, Map<String, Object> nodeConfig) {
        Object rulesObj = nodeConfig.get("rules");
        if (!(rulesObj instanceof List<?> rulesList) || rulesList.isEmpty()) {
            return AcceleratorResult.miss();
        }

        for (Object ruleObj : rulesList) {
            if (!(ruleObj instanceof Map<?, ?> rule)) continue;

            String field = str(rule.get("field"));
            String operator = str(rule.get("operator"));
            String value = str(rule.get("value"));

            if (field == null || operator == null || value == null) continue;

            String docValue = getFieldValue(doc, field);
            if (docValue == null) continue;

            boolean matched = evaluateRule(docValue, operator, value);
            if (matched) {
                String categoryId = str(rule.get("categoryId"));
                String categoryName = str(rule.get("categoryName"));
                String sensLabel = str(rule.get("sensitivityLabel"));
                double confidence = toDouble(rule.get("confidence"), 0.90);

                SensitivityLabel sensitivity = sensLabel != null
                        ? SensitivityLabel.valueOf(sensLabel) : SensitivityLabel.INTERNAL;

                log.info("[RulesEngine] Rule matched for doc {} — {} {} '{}' → {}",
                        doc.getId(), field, operator, value, categoryName);

                return AcceleratorResult.hit(
                        categoryId, categoryName, sensitivity, List.of(),
                        null, confidence,
                        "Rules engine match: " + field + " " + operator + " '" + value + "'",
                        "rulesEngine"
                );
            }
        }

        return AcceleratorResult.miss();
    }

    private String getFieldValue(DocumentModel doc, String field) {
        return switch (field) {
            case "fileName", "filename" -> doc.getOriginalFileName();
            case "mimeType" -> doc.getMimeType();
            case "text" -> doc.getExtractedText();
            case "fileSize" -> String.valueOf(doc.getFileSizeBytes());
            default -> null;
        };
    }

    private boolean evaluateRule(String docValue, String operator, String ruleValue) {
        return switch (operator) {
            case "contains" -> docValue.toLowerCase().contains(ruleValue.toLowerCase());
            case "startsWith" -> docValue.toLowerCase().startsWith(ruleValue.toLowerCase());
            case "endsWith" -> docValue.toLowerCase().endsWith(ruleValue.toLowerCase());
            case "matches" -> {
                try {
                    yield Pattern.compile(ruleValue, Pattern.CASE_INSENSITIVE).matcher(docValue).find();
                } catch (Exception e) {
                    yield false;
                }
            }
            case "equals" -> docValue.equalsIgnoreCase(ruleValue);
            case "keywordDensity" -> {
                // Check if the keyword appears more than N times
                int threshold = toInt(ruleValue, 3);
                String text = docValue.toLowerCase();
                // Count occurrences using a simple approach
                String keyword = ruleValue.toLowerCase();
                if (keyword.isEmpty()) yield false;
                // Extract keyword before ':' if format is "keyword:count"
                String[] parts = ruleValue.split(":");
                if (parts.length == 2) {
                    keyword = parts[0].toLowerCase();
                    threshold = toInt(parts[1], 3);
                }
                int count = 0;
                int idx = 0;
                while ((idx = text.indexOf(keyword, idx)) != -1) { count++; idx += keyword.length(); }
                yield count >= threshold;
            }
            default -> false;
        };
    }

    private static String str(Object val) {
        return val != null ? val.toString() : null;
    }

    private static double toDouble(Object val, double defaultVal) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) { try { return Double.parseDouble(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }

    private static int toInt(Object val, int defaultVal) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }
}
