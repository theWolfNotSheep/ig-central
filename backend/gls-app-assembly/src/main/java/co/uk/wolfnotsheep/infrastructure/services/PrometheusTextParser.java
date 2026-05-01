package co.uk.wolfnotsheep.infrastructure.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 3 PR16 — minimal parser for the Prometheus text exposition
 * format. Extracts samples for an allowlist of metric names — the
 * cross-service dashboard surfaces a curated set per service, not
 * every metric a service emits.
 *
 * <p>The full Prometheus exposition format is well-specified
 * (<a href="https://prometheus.io/docs/instrumenting/exposition_formats/">spec</a>);
 * this parser handles the subset that Spring Boot Actuator's
 * Micrometer exporter actually emits:
 * <ul>
 *   <li>{@code # HELP …} / {@code # TYPE …} comment lines (skipped)</li>
 *   <li>{@code metric_name value timestamp?} (no labels)</li>
 *   <li>{@code metric_name{label="value", …} value timestamp?}</li>
 * </ul>
 *
 * <p>What we don't try to handle: histogram buckets ({@code _bucket}),
 * summary quantiles ({@code _sum} / {@code _count}), exemplars,
 * escaped-quote label values. The dashboard's curated metric list
 * doesn't include any of those edge cases.
 *
 * <p>Counter metrics are exported with a {@code _total} suffix per
 * the OpenMetrics convention; the parser includes the suffix in the
 * matched name. The caller passes the suffixed form when querying
 * counters.
 */
public final class PrometheusTextParser {

    private PrometheusTextParser() {}

    public static List<Sample> parse(String body, Set<String> metricAllowlist) {
        List<Sample> out = new ArrayList<>();
        if (body == null || body.isEmpty()) return out;
        for (String line : body.split("\\R")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            Sample sample = parseLine(line, metricAllowlist);
            if (sample != null) out.add(sample);
        }
        return out;
    }

    static Sample parseLine(String line, Set<String> allowlist) {
        // metric_name OR metric_name{labels…}
        int braceStart = line.indexOf('{');
        int spaceAfterName;
        String metricName;
        Map<String, String> labels;
        if (braceStart < 0) {
            spaceAfterName = line.indexOf(' ');
            if (spaceAfterName < 0) return null;
            metricName = line.substring(0, spaceAfterName);
            labels = Map.of();
        } else {
            metricName = line.substring(0, braceStart);
            int braceEnd = line.indexOf('}', braceStart);
            if (braceEnd < 0) return null;
            labels = parseLabels(line.substring(braceStart + 1, braceEnd));
            spaceAfterName = braceEnd + 1;
        }
        if (!allowlist.contains(metricName)) return null;
        // The remainder is "value timestamp?" — we want the value.
        String rest = line.substring(spaceAfterName).trim();
        int sp = rest.indexOf(' ');
        String valueStr = sp < 0 ? rest : rest.substring(0, sp);
        try {
            double value = Double.parseDouble(valueStr);
            return new Sample(metricName, labels, value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Splits a labels string like {@code k1="v1",k2="v2"} into a map. */
    static Map<String, String> parseLabels(String inside) {
        Map<String, String> out = new LinkedHashMap<>();
        if (inside.isEmpty()) return out;
        int i = 0;
        while (i < inside.length()) {
            int eq = inside.indexOf('=', i);
            if (eq < 0) break;
            String key = inside.substring(i, eq).trim();
            int firstQuote = inside.indexOf('"', eq + 1);
            int closingQuote = firstQuote < 0 ? -1 : inside.indexOf('"', firstQuote + 1);
            if (firstQuote < 0 || closingQuote < 0) break;
            String value = inside.substring(firstQuote + 1, closingQuote);
            out.put(key, value);
            int comma = inside.indexOf(',', closingQuote);
            if (comma < 0) break;
            i = comma + 1;
            while (i < inside.length() && inside.charAt(i) == ' ') i++;
        }
        return out;
    }

    /** One parsed sample. {@code labels} is empty for label-less metrics. */
    public record Sample(String metricName, Map<String, String> labels, double value) {}
}
