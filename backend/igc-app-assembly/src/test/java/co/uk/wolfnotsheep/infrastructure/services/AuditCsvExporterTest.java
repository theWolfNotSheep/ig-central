package co.uk.wolfnotsheep.infrastructure.services;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditCsvExporterTest {

    private final AuditCollectorClient client = mock(AuditCollectorClient.class);
    private final AuditCsvExporter exporter = new AuditCsvExporter(client);

    @Test
    void singlePage_writesHeaderAndRow() {
        when(client.listTier2Events(any())).thenReturn("""
                {"events":[
                  {"eventId":"e1","eventType":"DocumentClassifiedV1","tier":"DOMAIN",
                   "schemaVersion":"1.0","timestamp":"2026-04-30T12:00:00Z",
                   "action":"classify","outcome":"SUCCESS","documentId":"doc-1",
                   "actor":{"service":"router","userId":"u1"},
                   "resource":{"type":"DOCUMENT","id":"doc-1"}}
                ],"nextPageToken":null}
                """);
        StringWriter out = new StringWriter();

        AuditCsvExporter.ExportResult result = exporter.export(
                searchParams(), 1000, out);

        assertThat(result.eventCount()).isEqualTo(1);
        assertThat(result.hitCap()).isFalse();

        String csv = out.toString();
        // Header
        assertThat(csv).startsWith("timestamp,eventId,eventType,tier,schemaVersion,action,outcome,documentId,pipelineRunId,nodeRunId,traceparent,actorService,actorUserId,resourceType,resourceId,retentionClass\n");
        // Row
        assertThat(csv).contains("2026-04-30T12:00:00Z,e1,DocumentClassifiedV1,DOMAIN,1.0,classify,SUCCESS,doc-1,,,,router,u1,DOCUMENT,doc-1,");
    }

    @Test
    void paginates_acrossPagesUntilNoNextToken() {
        when(client.listTier2Events(any())).thenReturn(
                "{\"events\":[{\"eventId\":\"a\"}],\"nextPageToken\":\"1\"}",
                "{\"events\":[{\"eventId\":\"b\"}],\"nextPageToken\":\"2\"}",
                "{\"events\":[{\"eventId\":\"c\"}],\"nextPageToken\":null}");
        StringWriter out = new StringWriter();

        AuditCsvExporter.ExportResult result = exporter.export(searchParams(), 1000, out);

        assertThat(result.eventCount()).isEqualTo(3);
        verify(client, times(3)).listTier2Events(any());
        ArgumentCaptor<AuditCollectorClient.SearchParams> captor =
                ArgumentCaptor.forClass(AuditCollectorClient.SearchParams.class);
        verify(client, times(3)).listTier2Events(captor.capture());
        // Page tokens forwarded: null, "1", "2"
        List<AuditCollectorClient.SearchParams> calls = captor.getAllValues();
        assertThat(calls.get(0).pageToken()).isNull();
        assertThat(calls.get(1).pageToken()).isEqualTo("1");
        assertThat(calls.get(2).pageToken()).isEqualTo("2");
    }

    @Test
    void hardCapStopsIterationMidPage() {
        // Two events on page 1, two more on page 2 — cap at 3.
        when(client.listTier2Events(any())).thenReturn(
                "{\"events\":[{\"eventId\":\"a\"},{\"eventId\":\"b\"}],\"nextPageToken\":\"1\"}",
                "{\"events\":[{\"eventId\":\"c\"},{\"eventId\":\"d\"}],\"nextPageToken\":\"2\"}");
        StringWriter out = new StringWriter();

        AuditCsvExporter.ExportResult result = exporter.export(searchParams(), 3, out);

        assertThat(result.eventCount()).isEqualTo(3);
        assertThat(result.hitCap()).isTrue();
        // Row "d" must NOT be in output.
        String csv = out.toString();
        assertThat(csv).contains(",a,").contains(",b,").contains(",c,");
        assertThat(csv).doesNotContain(",d,");
    }

    @Test
    void csvEscaping_quotesFieldsWithCommasAndQuotes() {
        when(client.listTier2Events(any())).thenReturn("""
                {"events":[
                  {"eventId":"e1","eventType":"X,Y","action":"say \\"hi\\"",
                   "actor":{"service":"router\\nwith-newline"}}
                ],"nextPageToken":null}
                """);
        StringWriter out = new StringWriter();

        exporter.export(searchParams(), 100, out);

        String csv = out.toString();
        assertThat(csv).contains("\"X,Y\"");           // comma → quoted
        assertThat(csv).contains("\"say \"\"hi\"\"\""); // embedded quote → doubled, wrapped
        assertThat(csv).contains("\"router\nwith-newline\""); // newline → quoted
    }

    @Test
    void emptyResults_writesHeaderOnly() {
        when(client.listTier2Events(any())).thenReturn(
                "{\"events\":[],\"nextPageToken\":null}");
        StringWriter out = new StringWriter();

        AuditCsvExporter.ExportResult result = exporter.export(searchParams(), 100, out);

        assertThat(result.eventCount()).isZero();
        assertThat(result.hitCap()).isFalse();
        // Just the header line, no data rows.
        assertThat(out.toString().trim().split("\n")).hasSize(1);
    }

    @Test
    void escapeCsv_helperCases() {
        assertThat(AuditCsvExporter.escapeCsv(null)).isEmpty();
        assertThat(AuditCsvExporter.escapeCsv("")).isEmpty();
        assertThat(AuditCsvExporter.escapeCsv("plain")).isEqualTo("plain");
        assertThat(AuditCsvExporter.escapeCsv("a,b")).isEqualTo("\"a,b\"");
        assertThat(AuditCsvExporter.escapeCsv("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(AuditCsvExporter.escapeCsv("a\nb")).isEqualTo("\"a\nb\"");
    }

    private static AuditCollectorClient.SearchParams searchParams() {
        return new AuditCollectorClient.SearchParams(null, null, null, null, null, null, null);
    }
}
