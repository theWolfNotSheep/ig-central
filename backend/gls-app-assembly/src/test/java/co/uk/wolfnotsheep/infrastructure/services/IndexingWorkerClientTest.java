package co.uk.wolfnotsheep.infrastructure.services;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spins up a JDK-builtin HTTP server, points the
 * {@link IndexingWorkerClient} at it, and asserts the request shape +
 * response parsing for all three operations. No external dependencies
 * (mirrors {@code ClassifierRouterClientTest} / enforcement client test).
 */
class IndexingWorkerClientTest {

    private HttpServer server;
    private int port;
    private AtomicReference<String> lastMethod;
    private AtomicReference<String> lastPath;
    private AtomicReference<String> lastIdempotency;
    private AtomicReference<String> lastTraceparent;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        lastMethod = new AtomicReference<>();
        lastPath = new AtomicReference<>();
        lastIdempotency = new AtomicReference<>();
        lastTraceparent = new AtomicReference<>();
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void indexDocument_posts_to_v1_index_and_returns_version() throws IOException {
        server.createContext("/v1/index/doc-1", exchange -> {
            lastMethod.set(exchange.getRequestMethod());
            lastPath.set(exchange.getRequestURI().toString());
            lastIdempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            lastTraceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));

            String response = "{\"nodeRunId\":\"index-doc-1-1\",\"documentId\":\"doc-1\","
                    + "\"indexName\":\"ig_central_documents\",\"version\":7,\"durationMs\":12}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        IndexingWorkerClient client = new IndexingWorkerClient("http://127.0.0.1:" + port, 5000);
        long version = client.indexDocument("doc-1");

        assertThat(version).isEqualTo(7L);
        assertThat(lastMethod.get()).isEqualTo("POST");
        assertThat(lastPath.get()).startsWith("/v1/index/doc-1?nodeRunId=");
        assertThat(lastIdempotency.get()).startsWith("nodeRun:");
        assertThat(lastTraceparent.get()).matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$");
    }

    @Test
    void indexDocument_throws_on_non_2xx() throws IOException {
        server.createContext("/v1/index/doc-bad", exchange -> {
            byte[] bytes = "{\"code\":\"INDEX_BACKEND_UNAVAILABLE\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/problem+json");
            exchange.sendResponseHeaders(503, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        IndexingWorkerClient client = new IndexingWorkerClient("http://127.0.0.1:" + port, 5000);
        assertThatThrownBy(() -> client.indexDocument("doc-bad"))
                .isInstanceOf(IndexingWorkerException.class)
                .hasMessageContaining("HTTP 503")
                .hasMessageContaining("INDEX_BACKEND_UNAVAILABLE");
    }

    @Test
    void removeDocument_calls_DELETE_and_returns_result() throws IOException {
        server.createContext("/v1/index/doc-2", exchange -> {
            lastMethod.set(exchange.getRequestMethod());
            String response = "{\"documentId\":\"doc-2\",\"result\":\"DELETED\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        IndexingWorkerClient client = new IndexingWorkerClient("http://127.0.0.1:" + port, 5000);
        String result = client.removeDocument("doc-2");

        assertThat(result).isEqualTo("DELETED");
        assertThat(lastMethod.get()).isEqualTo("DELETE");
    }

    @Test
    void removeDocument_returns_NOT_FOUND_when_idempotent_response() throws IOException {
        server.createContext("/v1/index/doc-3", exchange -> {
            String response = "{\"documentId\":\"doc-3\",\"result\":\"NOT_FOUND\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        IndexingWorkerClient client = new IndexingWorkerClient("http://127.0.0.1:" + port, 5000);
        assertThat(client.removeDocument("doc-3")).isEqualTo("NOT_FOUND");
    }

    @Test
    void reindex_dispatches_async_and_returns_nodeRunId() throws IOException {
        server.createContext("/v1/reindex", exchange -> {
            lastMethod.set(exchange.getRequestMethod());
            String body = "{\"nodeRunId\":\"reindex-x\",\"status\":\"PENDING\","
                    + "\"pollUrl\":\"/v1/jobs/reindex-x\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Location", "/v1/jobs/reindex-x");
            exchange.sendResponseHeaders(202, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        IndexingWorkerClient client = new IndexingWorkerClient("http://127.0.0.1:" + port, 5000);
        String nodeRunId = client.reindex();

        assertThat(nodeRunId).startsWith("reindex-");
        assertThat(lastMethod.get()).isEqualTo("POST");
    }

    @Test
    void reindex_throws_on_non_202() throws IOException {
        server.createContext("/v1/reindex", exchange -> {
            byte[] bytes = "{\"code\":\"IDEMPOTENCY_IN_FLIGHT\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(409, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        IndexingWorkerClient client = new IndexingWorkerClient("http://127.0.0.1:" + port, 5000);
        assertThatThrownBy(client::reindex)
                .isInstanceOf(IndexingWorkerException.class)
                .hasMessageContaining("HTTP 409");
    }
}
