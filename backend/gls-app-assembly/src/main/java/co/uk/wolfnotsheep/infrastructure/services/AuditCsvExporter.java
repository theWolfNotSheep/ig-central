package co.uk.wolfnotsheep.infrastructure.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;

/**
 * Phase 3 PR9 — CSV export for Tier 2 audit events. Paginates through
 * the {@link AuditCollectorClient} results and writes a flat CSV to a
 * caller-supplied writer.
 *
 * <p>Columns are a fixed projection of the envelope. Free-form
 * {@code details} are intentionally excluded — they vary per event-type
 * and would either explode the column set or stuff a JSON blob into a
 * cell. Keeping them out makes the CSV consumable by Excel / sheets.
 *
 * <p>The hard-cap parameter prevents OOM on a million-event pull. When
 * the cap is hit the writer still ends with valid CSV; the caller sees
 * {@link ExportResult#hitCap()} = {@code true} and can tell the
 * operator to narrow the filters.
 */
@Service
public class AuditCsvExporter {

    private static final Logger log = LoggerFactory.getLogger(AuditCsvExporter.class);
    private static final int PAGE_SIZE = 500;

    static final List<String> COLUMNS = List.of(
            "timestamp", "eventId", "eventType", "tier", "schemaVersion",
            "action", "outcome",
            "documentId", "pipelineRunId", "nodeRunId", "traceparent",
            "actorService", "actorUserId",
            "resourceType", "resourceId", "retentionClass");

    private final AuditCollectorClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuditCsvExporter(AuditCollectorClient client) {
        this.client = client;
    }

    public ExportResult export(AuditCollectorClient.SearchParams base, int hardCap, Writer out) {
        try {
            writeHeader(out);
            int eventCount = 0;
            boolean hitCap = false;
            String pageToken = null;
            while (true) {
                AuditCollectorClient.SearchParams params = new AuditCollectorClient.SearchParams(
                        base.documentId(), base.eventType(), base.actorService(),
                        base.from(), base.to(), pageToken, PAGE_SIZE);
                String body = client.listTier2Events(params);
                JsonNode root = mapper.readTree(body);
                for (JsonNode event : root.path("events")) {
                    if (eventCount >= hardCap) { hitCap = true; break; }
                    writeRow(out, event);
                    eventCount++;
                }
                if (hitCap) break;
                String next = root.path("nextPageToken").asText(null);
                if (next == null || next.isBlank() || "null".equals(next)) break;
                pageToken = next;
            }
            log.info("audit-csv export: {} events, cap={}", eventCount, hitCap);
            return new ExportResult(eventCount, hitCap);
        } catch (IOException e) {
            throw new UncheckedIOException("audit CSV export failed", e);
        }
    }

    private static void writeHeader(Writer out) throws IOException {
        out.write(String.join(",", COLUMNS));
        out.write("\n");
    }

    private static void writeRow(Writer out, JsonNode event) throws IOException {
        JsonNode actor = event.path("actor");
        JsonNode resource = event.path("resource");
        for (int i = 0; i < COLUMNS.size(); i++) {
            String col = COLUMNS.get(i);
            String value = switch (col) {
                case "actorService" -> textOrEmpty(actor.path("service"));
                case "actorUserId" -> textOrEmpty(actor.path("userId"));
                case "resourceType" -> textOrEmpty(resource.path("type"));
                case "resourceId" -> textOrEmpty(resource.path("id"));
                default -> textOrEmpty(event.path(col));
            };
            if (i > 0) out.write(",");
            out.write(escapeCsv(value));
        }
        out.write("\n");
    }

    private static String textOrEmpty(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        return node.asText("");
    }

    /** RFC 4180 escaping: wrap in quotes if value contains comma, quote, or newline; double up internal quotes. */
    static String escapeCsv(String value) {
        if (value == null || value.isEmpty()) return "";
        boolean needsQuoting = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuoting) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public record ExportResult(int eventCount, boolean hitCap) {}
}
