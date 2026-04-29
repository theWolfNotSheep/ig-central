package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.document.events.LlmJobCompletedEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spins up a JDK-builtin HTTP server, points the
 * {@link ClassifierRouterClient} at it, and asserts the response →
 * {@link LlmJobCompletedEvent} translation. No external dependencies.
 */
class ClassifierRouterClientTest {

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
    void classify_translates_router_response_to_LlmJobCompletedEvent() throws IOException {
        server.createContext("/v1/classify", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            lastRequestBody.set(new String(body, StandardCharsets.UTF_8));
            lastIdempotencyHeader.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            lastTraceparentHeader.set(exchange.getRequestHeaders().getFirst("traceparent"));

            String response = """
                {
                  "nodeRunId": "node-1",
                  "tierOfDecision": "LLM",
                  "confidence": 0.84,
                  "result": {
                    "categoryId": "cat-1",
                    "category": "HR Letter",
                    "sensitivity": "INTERNAL",
                    "tags": ["hr", "letter"],
                    "requiresHumanReview": false,
                    "retentionScheduleId": "ret-7yr",
                    "applicablePolicyIds": ["pol-1"],
                    "extractedMetadata": {"employeeName": "Alice"},
                    "customResult": {"foo": "bar"}
                  },
                  "durationMs": 421,
                  "costUnits": 12
                }
                """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        ClassifierRouterClient client = new ClassifierRouterClient(
                "http://127.0.0.1:" + port, /* timeoutMs */ 5000);
        LlmJobCompletedEvent event = client.classify(
                "job-1", "run-1", "node-1", "block-1", 3,
                "the document text", "idem-1");

        assertThat(event.success()).isTrue();
        assertThat(event.jobId()).isEqualTo("job-1");
        assertThat(event.pipelineRunId()).isEqualTo("run-1");
        assertThat(event.nodeRunId()).isEqualTo("node-1");
        assertThat(event.categoryId()).isEqualTo("cat-1");
        assertThat(event.categoryName()).isEqualTo("HR Letter");
        assertThat(event.sensitivityLabel()).isEqualTo("INTERNAL");
        assertThat(event.tags()).containsExactly("hr", "letter");
        assertThat(event.confidence()).isCloseTo(0.84, org.assertj.core.data.Offset.offset(0.001));
        assertThat(event.requiresHumanReview()).isFalse();
        assertThat(event.retentionScheduleId()).isEqualTo("ret-7yr");
        assertThat(event.applicablePolicyIds()).containsExactly("pol-1");
        assertThat(event.extractedMetadata()).containsEntry("employeeName", "Alice");
        assertThat(event.customResult()).containsEntry("foo", "bar");

        // Headers and body shape sanity-check.
        assertThat(lastIdempotencyHeader.get()).isEqualTo("idem-1");
        assertThat(lastTraceparentHeader.get()).matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$");
        assertThat(lastRequestBody.get()).contains("\"nodeRunId\":\"node-1\"");
        assertThat(lastRequestBody.get()).contains("\"id\":\"block-1\"");
        assertThat(lastRequestBody.get()).contains("\"version\":3");
        assertThat(lastRequestBody.get()).contains("the document text");
    }

    @Test
    void non_2xx_returns_failure_event() throws IOException {
        server.createContext("/v1/classify", exchange -> {
            String body = "{\"type\":\"https://gls.local/errors/ROUTER_BLOCK_NOT_FOUND\",\"code\":\"ROUTER_BLOCK_NOT_FOUND\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/problem+json");
            exchange.sendResponseHeaders(422, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        ClassifierRouterClient client = new ClassifierRouterClient(
                "http://127.0.0.1:" + port, 5000);
        LlmJobCompletedEvent event = client.classify(
                "job-2", "run-2", "node-2", "block-bad", null,
                "x", "idem-2");

        assertThat(event.success()).isFalse();
        assertThat(event.error()).contains("router HTTP 422");
        assertThat(event.error()).contains("ROUTER_BLOCK_NOT_FOUND");
    }

    @Test
    void empty_result_object_yields_success_with_nullable_fields() throws IOException {
        server.createContext("/v1/classify", exchange -> {
            String response = """
                {"nodeRunId":"node-3","tierOfDecision":"MOCK","confidence":0.5,"result":{}}
                """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        ClassifierRouterClient client = new ClassifierRouterClient(
                "http://127.0.0.1:" + port, 5000);
        LlmJobCompletedEvent event = client.classify(
                "job-3", "run-3", "node-3", "block-1", null, "x", "idem-3");

        assertThat(event.success()).isTrue();
        assertThat(event.categoryId()).isNull();
        assertThat(event.categoryName()).isNull();
        assertThat(event.tags()).isNull();
        assertThat(event.confidence()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
    }
}
