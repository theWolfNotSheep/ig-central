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

/**
 * Spins up a JDK-builtin HTTP server, points the
 * {@link BertHttpDispatcher} at it, and asserts the response →
 * {@link BertInferenceResult} translation + the fallthrough vs.
 * propagation behaviour for the various error shapes.
 *
 * <p>Same harness pattern as
 * {@code ClassifierRouterClientTest} in {@code gls-app-assembly}.
 */
class BertHttpDispatcherTest {

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

    private BertHttpDispatcher dispatcher() {
        return new BertHttpDispatcher(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                URI.create("http://127.0.0.1:" + port),
                Duration.ofSeconds(5));
    }

    @Test
    void infer_200_returns_BertInferenceResult_with_label_and_confidence() throws IOException {
        server.createContext("/v1/infer", exchange -> {
            lastRequestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = """
                {
                  "label": "hr_letter",
                  "confidence": 0.93,
                  "modelVersion": "2026.04.0",
                  "costUnits": 4
                }
                """;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        BertInferenceResult result = dispatcher().infer("block-1", 3, "node-1", "the doc");

        assertThat(result.label()).isEqualTo("hr_letter");
        assertThat(result.confidence()).isCloseTo(0.93f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(result.modelVersion()).isEqualTo("2026.04.0");
        assertThat(lastRequestBody.get()).contains("\"id\":\"block-1\"");
        assertThat(lastRequestBody.get()).contains("\"version\":3");
        assertThat(lastRequestBody.get()).contains("\"nodeRunId\":\"node-1\"");
        assertThat(lastRequestBody.get()).contains("the doc");
        assertThat(lastRequestBody.get()).contains("\"encoding\":\"utf-8\"");
    }

    @Test
    void infer_503_MODEL_NOT_LOADED_throws_BertTierFallthroughException_with_code() throws IOException {
        server.createContext("/v1/infer", exchange -> {
            String body = "{\"type\":\"https://gls.local/errors/MODEL_NOT_LOADED\","
                    + "\"code\":\"MODEL_NOT_LOADED\","
                    + "\"detail\":\"no model is loaded on this replica\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/problem+json");
            exchange.sendResponseHeaders(503, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        BertTierFallthroughException ex = (BertTierFallthroughException)
                catchThrowable(() -> dispatcher().infer("block-1", null, null, "x"));

        assertThat(ex).isNotNull();
        assertThat(ex.errorCode()).isEqualTo("MODEL_NOT_LOADED");
    }

    @Test
    void infer_503_with_no_code_falls_back_to_MODEL_NOT_LOADED() throws IOException {
        server.createContext("/v1/infer", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        BertTierFallthroughException ex = (BertTierFallthroughException)
                catchThrowable(() -> dispatcher().infer("block-1", null, null, "x"));

        assertThat(ex).isNotNull();
        assertThat(ex.errorCode()).isEqualTo("MODEL_NOT_LOADED");
    }

    @Test
    void infer_422_throws_BertBlockUnknownException_not_fallthrough() throws IOException {
        server.createContext("/v1/infer", exchange -> {
            String body = "{\"code\":\"BLOCK_UNKNOWN\",\"detail\":\"block not found\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(422, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        assertThatThrownBy(() -> dispatcher().infer("block-1", null, null, "x"))
                .isInstanceOf(BertBlockUnknownException.class)
                .hasMessageContaining("block not found");
    }

    @Test
    void infer_500_falls_through_with_BERT_HTTP_500_code() throws IOException {
        server.createContext("/v1/infer", exchange -> {
            byte[] bytes = "internal".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        BertTierFallthroughException ex = (BertTierFallthroughException)
                catchThrowable(() -> dispatcher().infer("block-1", null, null, "x"));

        assertThat(ex).isNotNull();
        assertThat(ex.errorCode()).isEqualTo("BERT_HTTP_500");
    }

    @Test
    void transport_failure_falls_through_with_BERT_TRANSPORT_ERROR() {
        // Server intentionally not started → connection refused.
        // (HttpServer is bound to a port that's been reserved but
        // never starts accepting; client gets a connection refused.)
        BertHttpDispatcher dispatcher = new BertHttpDispatcher(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build(),
                URI.create("http://127.0.0.1:" + port),
                Duration.ofSeconds(1));

        BertTierFallthroughException ex = (BertTierFallthroughException)
                catchThrowable(() -> dispatcher.infer("block-1", null, null, "x"));

        assertThat(ex).isNotNull();
        assertThat(ex.errorCode()).isEqualTo("BERT_TRANSPORT_ERROR");
    }

    @Test
    void infer_200_with_missing_label_falls_through_with_RESPONSE_INVALID() throws IOException {
        server.createContext("/v1/infer", exchange -> {
            byte[] bytes = "{\"confidence\":0.5}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        BertTierFallthroughException ex = (BertTierFallthroughException)
                catchThrowable(() -> dispatcher().infer("block-1", null, null, "x"));

        assertThat(ex).isNotNull();
        assertThat(ex.errorCode()).isEqualTo("BERT_RESPONSE_INVALID");
    }
}
