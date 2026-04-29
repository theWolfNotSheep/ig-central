package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1.9 PR2 — focused tests for {@link ScanRouterClient}. Spins up
 * a JDK-builtin HTTP server, points the client at it, and asserts the
 * router's response → {@link ScanRouterClient.RouterScanOutcome}
 * translation. No external dependencies.
 */
class ScanRouterClientTest {

    private HttpServer server;
    private int port;
    private AtomicReference<String> lastRequestBody;
    private AtomicReference<String> lastIdempotencyHeader;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        lastRequestBody = new AtomicReference<>();
        lastIdempotencyHeader = new AtomicReference<>();
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void successful_response_yields_RouterScanOutcome_with_tier_and_result() throws IOException {
        server.createContext("/v1/classify", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            lastRequestBody.set(new String(body, StandardCharsets.UTF_8));
            lastIdempotencyHeader.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));

            String response = """
                {
                  "nodeRunId": "scan-1",
                  "tierOfDecision": "SLM",
                  "confidence": 0.91,
                  "result": {
                    "found": true,
                    "instances": [{"value": "AB123456C", "context": "NI: AB123456C"}],
                    "confidence": 0.95
                  },
                  "durationMs": 320,
                  "costUnits": 4
                }
                """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        ScanRouterClient client = new ScanRouterClient(
                "http://127.0.0.1:" + port, /* timeoutMs */ 5000);
        ScanRouterClient.RouterScanOutcome outcome = client.dispatch(
                "scan-1", "scan-pii-uk_ni", null,
                "Doc text containing NI: AB123456C", "scan-1");

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.tierOfDecision()).isEqualTo("SLM");
        assertThat(outcome.confidence()).isCloseTo(0.91, org.assertj.core.data.Offset.offset(0.001));
        assertThat(outcome.result()).containsEntry("found", true);
        assertThat(outcome.result()).containsKey("instances");
        assertThat(outcome.error()).isNull();
        assertThat(outcome.durationMs()).isGreaterThanOrEqualTo(0L);

        // Request shape sanity check.
        assertThat(lastIdempotencyHeader.get()).isEqualTo("scan-1");
        assertThat(lastRequestBody.get())
                .contains("\"nodeRunId\":\"scan-1\"")
                .contains("\"id\":\"scan-pii-uk_ni\"")
                .contains("\"type\":\"PROMPT\"");
    }

    @Test
    void non_2xx_returns_failure_outcome() throws IOException {
        server.createContext("/v1/classify", exchange -> {
            String body = "{\"type\":\"https://gls.local/errors/ROUTER_BLOCK_NOT_FOUND\",\"code\":\"ROUTER_BLOCK_NOT_FOUND\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/problem+json");
            exchange.sendResponseHeaders(422, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        ScanRouterClient client = new ScanRouterClient(
                "http://127.0.0.1:" + port, 5000);
        ScanRouterClient.RouterScanOutcome outcome = client.dispatch(
                "scan-2", "scan-bad", null, "x", "idem-2");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.error()).contains("scan HTTP 422");
        assertThat(outcome.error()).contains("ROUTER_BLOCK_NOT_FOUND");
        assertThat(outcome.tierOfDecision()).isNull();
        assertThat(outcome.confidence()).isNull();
    }

    @Test
    void result_object_with_only_found_yields_minimal_outcome() throws IOException {
        server.createContext("/v1/classify", exchange -> {
            String response = """
                {"nodeRunId":"scan-3","tierOfDecision":"MOCK","confidence":0.5,"result":{"found":false}}
                """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        ScanRouterClient client = new ScanRouterClient(
                "http://127.0.0.1:" + port, 5000);
        ScanRouterClient.RouterScanOutcome outcome = client.dispatch(
                "scan-3", "scan-pii-x", null, "x", "idem-3");

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.tierOfDecision()).isEqualTo("MOCK");
        assertThat(outcome.confidence()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
        assertThat(outcome.result()).containsEntry("found", false);
    }

    @Test
    void no_idempotency_key_falls_back_to_nodeRunId() throws IOException {
        server.createContext("/v1/classify", exchange -> {
            lastIdempotencyHeader.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            String response = "{\"nodeRunId\":\"scan-4\",\"tierOfDecision\":\"MOCK\",\"confidence\":0,\"result\":{}}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        ScanRouterClient client = new ScanRouterClient(
                "http://127.0.0.1:" + port, 5000);
        client.dispatch("scan-4", "scan-pii-x", null, "x", null);

        assertThat(lastIdempotencyHeader.get()).isEqualTo("scan-4");
    }

    @Test
    void blockVersion_when_set_lands_in_request_body() throws IOException {
        server.createContext("/v1/classify", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            lastRequestBody.set(new String(body, StandardCharsets.UTF_8));
            String response = "{\"nodeRunId\":\"scan-5\",\"tierOfDecision\":\"MOCK\",\"confidence\":0,\"result\":{}}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        ScanRouterClient client = new ScanRouterClient(
                "http://127.0.0.1:" + port, 5000);
        client.dispatch("scan-5", "scan-pii-x", 7, "x", "scan-5");

        assertThat(lastRequestBody.get()).contains("\"version\":7");
    }
}
