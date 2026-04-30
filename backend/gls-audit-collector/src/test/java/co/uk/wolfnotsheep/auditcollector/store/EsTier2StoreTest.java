package co.uk.wolfnotsheep.auditcollector.store;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spins up a JDK-builtin HTTP server, points {@link EsTier2Store} at
 * it, and asserts the request shape + response parsing for save +
 * find + search. No external ES dependency.
 */
class EsTier2StoreTest {

    private HttpServer server;
    private int port;
    private AtomicReference<String> lastBody;
    private AtomicReference<String> lastMethod;
    private AtomicReference<String> lastPath;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        lastBody = new AtomicReference<>();
        lastMethod = new AtomicReference<>();
        lastPath = new AtomicReference<>();
        // Soak any startup ensureIndex probe — return 200 so the @PostConstruct hits the "exists" path.
        server.createContext("/" + EsTier2Store.INDEX_NAME, exchange -> {
            if ("GET".equals(exchange.getRequestMethod())
                    && exchange.getRequestURI().toString().equals("/" + EsTier2Store.INDEX_NAME)) {
                respond(exchange, 200, "{}");
                return;
            }
            // For PUT (create-index) requests during tests, also 200.
            if ("PUT".equals(exchange.getRequestMethod())
                    && exchange.getRequestURI().toString().equals("/" + EsTier2Store.INDEX_NAME)) {
                respond(exchange, 200, "{\"acknowledged\":true}");
                return;
            }
            respond(exchange, 404, "{}");
        });
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void save_PUTs_document_to_es() throws IOException {
        server.createContext("/" + EsTier2Store.INDEX_NAME + "/_doc/E1", recorder(200, "{}"));
        server.start();

        EsTier2Store store = new EsTier2Store("http://127.0.0.1:" + port, 5000);
        store.save(build("E1"));

        assertThat(lastMethod.get()).isEqualTo("PUT");
        assertThat(lastPath.get()).isEqualTo("/" + EsTier2Store.INDEX_NAME + "/_doc/E1");
        assertThat(lastBody.get())
                .contains("\"eventId\":\"E1\"")
                .contains("\"eventType\":\"DOCUMENT_CLASSIFIED\"")
                .contains("\"actorService\":\"test-svc\"");
    }

    @Test
    void findById_returns_empty_on_404() throws IOException {
        server.createContext("/" + EsTier2Store.INDEX_NAME + "/_doc/missing",
                staticResponse(404, "{\"found\":false}"));
        server.start();

        EsTier2Store store = new EsTier2Store("http://127.0.0.1:" + port, 5000);

        assertThat(store.findById("missing")).isEmpty();
    }

    @Test
    void findById_parses_source_into_StoredTier2Event() throws IOException {
        String body = """
            {
              "_id": "E1",
              "_source": {
                "eventId": "E1",
                "eventType": "DOCUMENT_CLASSIFIED",
                "tier": "SYSTEM",
                "schemaVersion": "1.0.0",
                "timestamp": "2026-04-30T10:00:00Z",
                "documentId": "doc-1",
                "actorService": "test-svc",
                "actorType": "SYSTEM",
                "resourceType": "DOCUMENT",
                "resourceId": "doc-1",
                "action": "CLASSIFY",
                "outcome": "SUCCESS",
                "envelope": {"action": "CLASSIFY"}
              }
            }
            """;
        server.createContext("/" + EsTier2Store.INDEX_NAME + "/_doc/E1",
                staticResponse(200, body));
        server.start();

        EsTier2Store store = new EsTier2Store("http://127.0.0.1:" + port, 5000);
        Optional<StoredTier2Event> found = store.findById("E1");

        assertThat(found).isPresent();
        assertThat(found.get().getEventId()).isEqualTo("E1");
        assertThat(found.get().getEventType()).isEqualTo("DOCUMENT_CLASSIFIED");
        assertThat(found.get().getActorService()).isEqualTo("test-svc");
        assertThat(found.get().getTimestamp()).isEqualTo(Instant.parse("2026-04-30T10:00:00Z"));
    }

    @Test
    void search_POSTs_a_bool_filter_query_and_parses_hits() throws IOException {
        String hits = """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_id": "E1",
                    "_source": {
                      "eventId": "E1",
                      "eventType": "DOCUMENT_CLASSIFIED",
                      "tier": "SYSTEM",
                      "schemaVersion": "1.0.0",
                      "timestamp": "2026-04-30T10:00:00Z",
                      "actorService": "test-svc",
                      "envelope": {}
                    }
                  }
                ]
              }
            }
            """;
        server.createContext("/" + EsTier2Store.INDEX_NAME + "/_search", recorder(200, hits));
        server.start();

        EsTier2Store store = new EsTier2Store("http://127.0.0.1:" + port, 5000);
        List<StoredTier2Event> rows = store.search(
                new Tier2Store.SearchCriteria("doc-1", "DOCUMENT_CLASSIFIED", null, null, null),
                0, 50);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEventId()).isEqualTo("E1");
        assertThat(lastMethod.get()).isEqualTo("POST");
        // bool/filter query shape sanity-check
        assertThat(lastBody.get()).contains("\"bool\"").contains("\"filter\"");
        assertThat(lastBody.get()).contains("\"documentId\":\"doc-1\"");
        assertThat(lastBody.get()).contains("\"eventType\":\"DOCUMENT_CLASSIFIED\"");
        assertThat(lastBody.get()).contains("\"size\":50");
    }

    @Test
    void search_with_no_filters_uses_match_all() throws IOException {
        server.createContext("/" + EsTier2Store.INDEX_NAME + "/_search",
                recorder(200, "{\"hits\":{\"hits\":[]}}"));
        server.start();

        EsTier2Store store = new EsTier2Store("http://127.0.0.1:" + port, 5000);
        store.search(new Tier2Store.SearchCriteria(null, null, null, null, null), 1, 25);

        assertThat(lastBody.get()).contains("\"match_all\"");
        assertThat(lastBody.get()).contains("\"from\":25");
        assertThat(lastBody.get()).contains("\"size\":25");
    }

    private HttpHandler staticResponse(int status, String body) {
        return exchange -> respond(exchange, status, body);
    }

    private HttpHandler recorder(int status, String body) {
        return exchange -> {
            byte[] req = exchange.getRequestBody().readAllBytes();
            lastBody.set(new String(req, StandardCharsets.UTF_8));
            lastMethod.set(exchange.getRequestMethod());
            lastPath.set(exchange.getRequestURI().toString());
            respond(exchange, status, body);
        };
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private static StoredTier2Event build(String eventId) {
        return new StoredTier2Event(eventId, "DOCUMENT_CLASSIFIED", "1.0.0",
                Instant.parse("2026-04-30T10:00:00Z"),
                "doc-1", null, null, null,
                "test-svc", "SYSTEM", "DOCUMENT", "doc-1",
                "CLASSIFY", "SUCCESS", "30D", Map.of("action", "CLASSIFY"));
    }
}
