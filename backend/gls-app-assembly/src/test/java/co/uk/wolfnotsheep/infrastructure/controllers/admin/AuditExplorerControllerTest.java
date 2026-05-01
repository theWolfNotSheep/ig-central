package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.infrastructure.services.AuditCollectorClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditExplorerControllerTest {

    private final AuditCollectorClient client = mock(AuditCollectorClient.class);
    private final AuditExplorerController controller = new AuditExplorerController(client);

    @Test
    void search_forwardsAllParamsToClient() {
        when(client.listTier2Events(any())).thenReturn("{\"events\":[],\"nextPageToken\":null}");

        ResponseEntity<String> resp = controller.searchTier2(
                "doc-123", "DocumentClassifiedV1", "router",
                "2026-04-01T00:00:00Z", "2026-05-01T00:00:00Z",
                "5", 100);

        ArgumentCaptor<AuditCollectorClient.SearchParams> captor =
                ArgumentCaptor.forClass(AuditCollectorClient.SearchParams.class);
        verify(client).listTier2Events(captor.capture());
        AuditCollectorClient.SearchParams p = captor.getValue();
        assertThat(p.documentId()).isEqualTo("doc-123");
        assertThat(p.eventType()).isEqualTo("DocumentClassifiedV1");
        assertThat(p.actorService()).isEqualTo("router");
        assertThat(p.from()).isEqualTo("2026-04-01T00:00:00Z");
        assertThat(p.to()).isEqualTo("2026-05-01T00:00:00Z");
        assertThat(p.pageToken()).isEqualTo("5");
        assertThat(p.pageSize()).isEqualTo(100);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("events");
    }

    @Test
    void search_passesThroughUpstreamJsonBody() {
        String upstream = "{\"events\":[{\"eventId\":\"abc\",\"eventType\":\"X\"}],\"nextPageToken\":\"7\"}";
        when(client.listTier2Events(any())).thenReturn(upstream);

        ResponseEntity<String> resp = controller.searchTier2(
                null, null, null, null, null, null, null);

        assertThat(resp.getBody()).isEqualTo(upstream);
    }

    @Test
    void search_returns502WhenCollectorFails() {
        when(client.listTier2Events(any())).thenThrow(
                new AuditCollectorClient.AuditCollectorException("connection refused"));

        ResponseEntity<String> resp = controller.searchTier2(
                null, null, null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(502);
        assertThat(resp.getBody()).contains("audit-collector unavailable");
        assertThat(resp.getBody()).contains("connection refused");
    }

    @Test
    void getEvent_passesThroughOnHit() {
        when(client.findEventById("evt-1")).thenReturn(Optional.of("{\"eventId\":\"evt-1\"}"));

        ResponseEntity<String> resp = controller.getEvent("evt-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo("{\"eventId\":\"evt-1\"}");
    }

    @Test
    void getEvent_returns404OnMiss() {
        when(client.findEventById(eq("missing"))).thenReturn(Optional.empty());

        ResponseEntity<String> resp = controller.getEvent("missing");

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(resp.getBody()).contains("event not found").contains("missing");
    }

    @Test
    void getEvent_returns502OnCollectorFailure() {
        when(client.findEventById(any())).thenThrow(
                new AuditCollectorClient.AuditCollectorException("upstream timeout"));

        ResponseEntity<String> resp = controller.getEvent("evt-9");

        assertThat(resp.getStatusCode().value()).isEqualTo(502);
        assertThat(resp.getBody()).contains("audit-collector unavailable");
    }

    @Test
    void getEvent_jsonEscapesEventIdInErrorBody() {
        when(client.findEventById(any())).thenReturn(Optional.empty());

        ResponseEntity<String> resp = controller.getEvent("ev\"id");

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(resp.getBody()).contains("ev\\\"id");
    }
}
