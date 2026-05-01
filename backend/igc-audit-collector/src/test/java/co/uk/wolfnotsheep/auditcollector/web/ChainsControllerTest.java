package co.uk.wolfnotsheep.auditcollector.web;

import co.uk.wolfnotsheep.auditcollector.chain.ChainVerifier;
import co.uk.wolfnotsheep.auditcollector.model.ChainVerifyResponse;
import co.uk.wolfnotsheep.auditcollector.model.Tier1ChainResponse;
import co.uk.wolfnotsheep.auditcollector.store.StoredTier1Event;
import co.uk.wolfnotsheep.auditcollector.store.Tier1Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChainsControllerTest {

    private ChainVerifier verifier;
    private Tier1Store tier1Store;
    private ChainsController controller;

    @BeforeEach
    void setUp() {
        verifier = mock(ChainVerifier.class);
        tier1Store = mock(Tier1Store.class);
        controller = new ChainsController(verifier, tier1Store);
    }

    @Test
    void OK_result_returns_status_OK_with_first_last_event_ids() {
        when(verifier.verify("DOCUMENT", "doc-1")).thenReturn(
                ChainVerifier.Result.ok("DOCUMENT", "doc-1", "E1", "E5", 5, 12L));

        var resp = controller.verifyChain(validTraceparent(), "DOCUMENT", "doc-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ChainVerifyResponse body = resp.getBody();
        assertThat(body.getStatus()).isEqualTo(ChainVerifyResponse.StatusEnum.OK);
        assertThat(body.getEventsTraversed()).isEqualTo(5);
        assertThat(body.getFirstEventId()).isEqualTo("E1");
        assertThat(body.getLastEventId()).isEqualTo("E5");
        assertThat(body.getDurationMs()).isEqualTo(12);
    }

    @Test
    void BROKEN_result_returns_status_BROKEN_with_offending_id_and_hashes() {
        when(verifier.verify("DOCUMENT", "doc-1")).thenReturn(
                ChainVerifier.Result.broken("DOCUMENT", "doc-1", "E1", "E2", 2,
                        "sha256:abc", "sha256:def", 7L));

        var resp = controller.verifyChain(validTraceparent(), "DOCUMENT", "doc-1");

        ChainVerifyResponse body = resp.getBody();
        assertThat(body.getStatus()).isEqualTo(ChainVerifyResponse.StatusEnum.BROKEN);
        assertThat(body.getBrokenAtEventId()).isEqualTo("E2");
        assertThat(body.getExpectedPreviousHash()).isEqualTo("sha256:abc");
        assertThat(body.getComputedPreviousHash()).isEqualTo("sha256:def");
    }

    @Test
    void NOT_FOUND_result_throws_AuditResourceNotFoundException() {
        when(verifier.verify("DOCUMENT", "doc-x")).thenReturn(
                ChainVerifier.Result.notFound("DOCUMENT", "doc-x", 0L));

        assertThatThrownBy(() -> controller.verifyChain(validTraceparent(), "DOCUMENT", "doc-x"))
                .isInstanceOf(AuditResourceNotFoundException.class);
    }

    @Test
    void listTier1ForResource_returnsEventsInChronologicalOrder() {
        when(tier1Store.findChainAsc("DOCUMENT", "doc-1"))
                .thenReturn(List.of(makeStored("E1", "doc-1"), makeStored("E2", "doc-1")));

        var resp = controller.listTier1ForResource(validTraceparent(), "DOCUMENT", "doc-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Tier1ChainResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getResourceType()).isEqualTo(Tier1ChainResponse.ResourceTypeEnum.DOCUMENT);
        assertThat(body.getResourceId()).isEqualTo("doc-1");
        assertThat(body.getEvents()).hasSize(2);
        assertThat(body.getEvents().get(0).getEventId()).isEqualTo("E1");
        assertThat(body.getEvents().get(1).getEventId()).isEqualTo("E2");
    }

    @Test
    void listTier1ForResource_throwsNotFoundOnEmptyChain() {
        when(tier1Store.findChainAsc("DOCUMENT", "missing")).thenReturn(List.of());

        assertThatThrownBy(() -> controller.listTier1ForResource(validTraceparent(), "DOCUMENT", "missing"))
                .isInstanceOf(AuditResourceNotFoundException.class);
    }

    @Test
    void listTier1ForResource_capsAt10000Events() {
        // Synthesise 10001 events so the cap kicks in.
        java.util.List<StoredTier1Event> oversized = new java.util.ArrayList<>(10_001);
        for (int i = 0; i < 10_001; i++) oversized.add(makeStored("E" + i, "doc-big"));
        when(tier1Store.findChainAsc("DOCUMENT", "doc-big")).thenReturn(oversized);

        var resp = controller.listTier1ForResource(validTraceparent(), "DOCUMENT", "doc-big");

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getEvents()).hasSize(10_000);
    }

    private static StoredTier1Event makeStored(String eventId, String docId) {
        return new StoredTier1Event(
                eventId, "DocumentClassifiedV1", "1.0",
                Instant.parse("2026-04-30T12:00:00Z"),
                docId, null, null, null,
                "router", null,
                "DOCUMENT", docId,
                "classify", "SUCCESS",
                null, null,
                Map.of("actor", Map.of("service", "router")));
    }

    private static String validTraceparent() {
        return "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    }
}
