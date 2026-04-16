package co.uk.wolfnotsheep.governance.services;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Generates ISO 15489-style hierarchical classification codes from taxonomy node names.
 *
 * Examples:
 *   "Human Resources" -> "HR"
 *   "Employee Records" under "HR" -> "HR-EMP"
 *   "Performance Reviews" under "HR-EMP" -> "HR-EMP-PER"
 *
 * Rules:
 *   - Multi-word names use initials (e.g., "Human Resources" -> "HR")
 *   - Single-word names use first 3 uppercase chars (e.g., "Legal" -> "LEG")
 *   - Short single words (<=3 chars) use the full word (e.g., "IT" -> "IT", "HR" -> "HR")
 *   - Special characters and numbers are stripped from code segments
 *   - Codes are admin-editable; this generates suggestions only
 */
public final class ClassificationCodeGenerator {

    private static final Pattern NON_ALPHA = Pattern.compile("[^A-Z ]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private ClassificationCodeGenerator() {}

    /**
     * Generate a classification code for a node given its name and optional parent code.
     *
     * @param name       the node name (e.g., "Employee Records")
     * @param parentCode the parent's classification code (null for root/FUNCTION nodes)
     * @return suggested classification code (e.g., "HR-EMP")
     */
    public static String generate(String name, String parentCode) {
        String segment = generateSegment(name);
        if (parentCode == null || parentCode.isBlank()) {
            return segment;
        }
        return parentCode + "-" + segment;
    }

    /**
     * Generate just the local segment for a node name (without parent prefix).
     */
    public static String generateSegment(String name) {
        if (name == null || name.isBlank()) {
            return "UNK";
        }

        String upper = name.toUpperCase(Locale.ROOT);
        String cleaned = NON_ALPHA.matcher(upper).replaceAll("");
        cleaned = MULTI_SPACE.matcher(cleaned).replaceAll(" ").trim();

        if (cleaned.isEmpty()) {
            return "UNK";
        }

        String[] words = cleaned.split(" ");

        if (words.length == 1) {
            // Single word: use first 3 chars (or full word if <= 3 chars)
            return words[0].length() <= 3 ? words[0] : words[0].substring(0, 3);
        }

        // Multi-word: use initials of each word
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(word.charAt(0));
            }
        }
        return sb.toString();
    }

    /**
     * Generate a unique code by appending a numeric suffix if the base code is taken.
     *
     * @param baseCode the generated code
     * @param suffix   numeric suffix (1, 2, 3...)
     * @return e.g., "HR-EMP2"
     */
    public static String withSuffix(String baseCode, int suffix) {
        return baseCode + suffix;
    }
}
