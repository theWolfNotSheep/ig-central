package co.uk.wolfnotsheep.router.parse;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class LlmHttpDispatcherTest {

    private HttpServer server;
    private int port;
    private AtomicReference<String> lastRequestBody;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        lastRequestBody = new AtomicReference<>();
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private LlmHttpDispatcher dispatcher() {
        return new LlmHttpDispatcher(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                URI.create("http://127.0.0.1:" + port),
                Duration.ofSeconds(5));
    }

    @Test
    void classify_200_returns_LlmInferenceResult_with_full_shape() throws IOException {
        server.createContext("/v1/classify", exchange -> {
            lastRequestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = """
                {
                  "nodeRunId": "node-1",
                  "modelId": "claude-sonnet-4-5",
                  "confidence": 0.81,
                  "result": {
                    "category": "HR Letter",
                    "sensitivity": "INTERNAL"
                  },
                  "durationMs": 245,
                  "costUnits": 1,
                  "tokensIn": 320,
                  "tokensOut": 40
                }
                """;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        LlmInferenceResult result = dispatcher().classify("block-1", 3, "node-1", "the doc");

        assertThat(result.confidence()).isCloseTo(0.81f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(result.modelId()).isEqualTo("claude-sonnet-4-5");
        assertThat(result.tokensIn()).isEqualTo(320L);
        assertThat(result.tokensOut()).isEqualTo(40L);
        assertThat(result.costUnits()).isEqualTo(1L);
        assertThat(result.result()).containsEntry("category", "HR Letter");
        assertThat(result.result()).containsEntry("sensitivity", "INTERNAL");
        assertThat(lastRequestBody.get()).contains("\"id\":\"block-1\"");
        assertThat(lastRequestBody.get()).contains("\"version\":3");
        assertThat(lastRequestBody.get()).contains("\"nodeRunId\":\"node-1\"");
        assertThat(lastRequestBody.get()).contains("the doc");
    }

    @Test
    void classify_503_LLM_NOT_CONFIGURED_throws_fallthrough_with_code() throws IOException {
        server.createContext("/v1/classify", exchange -> {
            String body = "{\"code\":\"LLM_NOT_CONFIGURED\","
                    + "\"detail\":\"no backend wired\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/problem+json");
            exchange.sendResponseHeaders(503, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        LlmTierFallthroughException ex = (LlmTierFallthroughException)
                catchThrowable(() -> dispatcher().classify("block-1", null, null, "x"));

        assertThat(ex).isNotNull();
        assertThat(ex.errorCode()).isEqualTo("LLM_NOT_CONFIGURED");
    }

    @Test
    void classify_503_with_no_code_falls_back_to_LLM_NOT_CONFIGURED() throws IOException {
        server.createContext("/v1/classify", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        LlmTierFallthroughException ex = (LlmTierFallthroughException)
                catchThrowable(() -> dispatcher().classify("block-1", null, null, "x"));

        assertThat(ex).isNotNull();
        assertThat(ex.errorCode()).isEqualTo("LLM_NOT_CONFIGURED");
    }

    @Test
    void classify_422_throws_LlmBlockUnknownException_not_fallthrough() throws IOException {
        server.createContext("/v1/classify", exchange -> {
            String body = "{\"code\":\"LLM_BLOCK_UNKNOWN\",\"detail\":\"block not found\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(422, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        assertThatThrownBy(() -> dispatcher().classify("block-1", null, null, "x"))
                .isInstanceOf(LlmBlockUnknownException.class)
                .hasMessageContaining("block not found");
    }

    @Test
    void classify_500_falls_through_with_LLM_HTTP_500_code() throws IOException {
        server.createContext("/v1/classify", exchange -> {
            byte[] bytes = "internal".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        LlmTierFallthroughException ex = (LlmTierFallthroughException)
                catchThrowable(() -> dispatcher().classify("block-1", null, null, "x"));

        assertThat(ex).isNotNull();
        assertThat(ex.errorCode()).isEqualTo("LLM_HTTP_500");
    }

    @Test
    void transport_failure_falls_through_with_LLM_TRANSPORT_ERROR() {
        LlmHttpDispatcher dispatcher = new LlmHttpDispatcher(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build(),
                URI.create("http://127.0.0.1:" + port),
                Duration.ofSeconds(1));

        LlmTierFallthroughException ex = (LlmTierFallthroughException)
                catchThrowable(() -> dispatcher.classify("block-1", null, null, "x"));

        assertThat(ex).isNotNull();
        assertThat(ex.errorCode()).isEqualTo("LLM_TRANSPORT_ERROR");
    }
}
