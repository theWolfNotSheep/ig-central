package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spins up a JDK-builtin HTTP server, points the
 * {@link EnforcementWorkerClient} at it, and asserts the request body
 * shape + response → {@link EnforcementWorkerClient.Outcome} translation.
 * No external dependencies (mirrors {@code ClassifierRouterClientTest}).
 */
class EnforcementWorkerClientTest {

    private HttpServer server;
    private int port;
    private AtomicReference<String> lastRequestBody;
    private AtomicReference<String> lastIdempotencyHeader;
    private AtomicReference<String> lastTraceparentHeader;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        lastRequestBody = new AtomicReference<>();
        lastIdempotencyHeader = new AtomicReference<>();
        lastTraceparentHeader = new AtomicReference<>();
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void enforce_translates_worker_response_to_outcome() throws IOException {
        server.createContext("/v1/enforce", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            lastRequestBody.set(new String(body, StandardCharsets.UTF_8));
            lastIdempotencyHeader.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            lastTraceparentHeader.set(exchange.getRequestHeaders().getFirst("traceparent"));

            String response = """
                {
                  "nodeRunId": "nr-engine-1",
                  "documentId": "doc-1",
                  "applied": {
                    "appliedPolicyIds": ["policy-uk-gdpr"],
                    "retentionScheduleId": "ret-7y",
                    "retentionPeriodText": "7 years after termination",
                    "retentionTrigger": "DATE_CREATED",
                    "expectedDispositionAction": "DELETE",
                    "storageTierBefore": "tier-hot",
                    "storageTierAfter": "tier-cold",
                    "storageMigrated": true,
                    "auditEventId": "audit-123"
                  },
                  "durationMs": 320
                }
                """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        EnforcementWorkerClient client = new EnforcementWorkerClient(
                "http://127.0.0.1:" + port, 5000);
        EnforcementWorkerClient.Outcome outcome = client.enforce("nr-engine-1", buildEvent("doc-1"));

        assertThat(outcome.nodeRunId()).isEqualTo("nr-engine-1");
        assertThat(outcome.documentId()).isEqualTo("doc-1");
        assertThat(outcome.storageTierBefore()).isEqualTo("tier-hot");
        assertThat(outcome.storageTierAfter()).isEqualTo("tier-cold");
        assertThat(outcome.storageMigrated()).isTrue();
        assertThat(outcome.auditEventId()).isEqualTo("audit-123");
        assertThat(outcome.durationMs()).isEqualTo(320L);

        assertThat(lastIdempotencyHeader.get()).isEqualTo("nodeRun:nr-engine-1");
        assertThat(lastTraceparentHeader.get()).matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$");
        assertThat(lastRequestBody.get()).contains("\"nodeRunId\":\"nr-engine-1\"");
        assertThat(lastRequestBody.get()).contains("\"documentId\":\"doc-1\"");
        assertThat(lastRequestBody.get()).contains("\"classificationResultId\":\"cr-1\"");
        assertThat(lastRequestBody.get()).contains("\"sensitivityLabel\":\"INTERNAL\"");
    }

    @Test
    void non_2xx_throws_EnforcementWorkerException() throws IOException {
        server.createContext("/v1/enforce", exchange -> {
            String body = "{\"type\":\"https://igc.local/errors/DOCUMENT_NOT_FOUND\",\"code\":\"DOCUMENT_NOT_FOUND\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/problem+json");
            exchange.sendResponseHeaders(404, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        EnforcementWorkerClient client = new EnforcementWorkerClient(
                "http://127.0.0.1:" + port, 5000);

        assertThatThrownBy(() -> client.enforce("nr-bad", buildEvent("doc-missing")))
                .isInstanceOf(EnforcementWorkerException.class)
                .hasMessageContaining("HTTP 404")
                .hasMessageContaining("DOCUMENT_NOT_FOUND");
    }

    @Test
    void minimal_response_yields_outcome_with_default_fields() throws IOException {
        server.createContext("/v1/enforce", exchange -> {
            String response = """
                {"nodeRunId":"nr-min","documentId":"doc-2","applied":{}}
                """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        EnforcementWorkerClient client = new EnforcementWorkerClient(
                "http://127.0.0.1:" + port, 5000);
        EnforcementWorkerClient.Outcome outcome = client.enforce("nr-min", buildEvent("doc-2"));

        assertThat(outcome.nodeRunId()).isEqualTo("nr-min");
        assertThat(outcome.documentId()).isEqualTo("doc-2");
        assertThat(outcome.storageTierBefore()).isNull();
        assertThat(outcome.storageTierAfter()).isNull();
        assertThat(outcome.storageMigrated()).isFalse();
        assertThat(outcome.auditEventId()).isNull();
        assertThat(outcome.durationMs()).isZero();
    }

    @Test
    void event_omits_optional_fields_when_null() throws IOException {
        server.createContext("/v1/enforce", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            lastRequestBody.set(new String(body, StandardCharsets.UTF_8));
            String response = "{\"nodeRunId\":\"nr-1\",\"documentId\":\"doc-1\",\"applied\":{}}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        DocumentClassifiedEvent event = new DocumentClassifiedEvent(
                "doc-1", "cr-1", "cat-1",
                /* categoryName */ null,
                /* sensitivityLabel */ null,
                /* tags */ null,
                /* applicablePolicyIds */ null,
                /* retentionScheduleId */ null,
                0.5, false, Instant.parse("2026-04-30T10:00:00Z"));

        EnforcementWorkerClient client = new EnforcementWorkerClient(
                "http://127.0.0.1:" + port, 5000);
        client.enforce("nr-1", event);

        String body = lastRequestBody.get();
        assertThat(body).doesNotContain("\"categoryName\"");
        assertThat(body).doesNotContain("\"sensitivityLabel\"");
        assertThat(body).doesNotContain("\"tags\"");
        assertThat(body).doesNotContain("\"retentionScheduleId\"");
        assertThat(body).contains("\"classifiedAt\":\"2026-04-30T10:00:00Z\"");
    }

    private static DocumentClassifiedEvent buildEvent(String docId) {
        return new DocumentClassifiedEvent(
                docId, "cr-1", "cat-1", "HR > Contracts",
                SensitivityLabel.INTERNAL,
                List.of("hr", "contract"),
                List.of("policy-uk-gdpr"),
                "ret-7y",
                0.92, false, Instant.parse("2026-04-30T10:00:00Z"));
    }
}
