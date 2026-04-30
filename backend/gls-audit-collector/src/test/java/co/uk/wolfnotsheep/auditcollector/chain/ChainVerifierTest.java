package co.uk.wolfnotsheep.auditcollector.chain;

import co.uk.wolfnotsheep.auditcollector.store.StoredTier1Event;
import co.uk.wolfnotsheep.auditcollector.store.Tier1Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChainVerifierTest {

    private Tier1Store tier1Store;
    private ChainVerifier verifier;

    @BeforeEach
    void setUp() {
        tier1Store = mock(Tier1Store.class);
        verifier = new ChainVerifier(tier1Store);
    }

    @Test
    void empty_chain_returns_NOT_FOUND() {
        when(tier1Store.findChainAsc("DOCUMENT", "doc-x"))
                .thenReturn(List.of());

        ChainVerifier.Result r = verifier.verify("DOCUMENT", "doc-x");

        assertThat(r.status()).isEqualTo(ChainVerifier.Result.Status.NOT_FOUND);
        assertThat(r.eventsTraversed()).isZero();
    }

    @Test
    void single_first_in_chain_event_with_null_previousHash_is_OK() {
        StoredTier1Event first = build("E1", Instant.parse("2026-04-30T10:00:00Z"), null);
        when(tier1Store.findChainAsc("DOCUMENT", "doc-1"))
                .thenReturn(List.of(first));

        ChainVerifier.Result r = verifier.verify("DOCUMENT", "doc-1");

        assertThat(r.status()).isEqualTo(ChainVerifier.Result.Status.OK);
        assertThat(r.eventsTraversed()).isEqualTo(1);
        assertThat(r.firstEventId()).isEqualTo("E1");
        assertThat(r.lastEventId()).isEqualTo("E1");
    }

    @Test
    void multi_event_chain_with_correct_links_is_OK() {
        StoredTier1Event e1 = build("E1", Instant.parse("2026-04-30T10:00:00Z"), null);
        String h1 = EventHasher.hashOf(e1);
        StoredTier1Event e2 = build("E2", Instant.parse("2026-04-30T10:01:00Z"), h1);
        String h2 = EventHasher.hashOf(e2);
        StoredTier1Event e3 = build("E3", Instant.parse("2026-04-30T10:02:00Z"), h2);

        when(tier1Store.findChainAsc("DOCUMENT", "doc-1"))
                .thenReturn(List.of(e1, e2, e3));

        ChainVerifier.Result r = verifier.verify("DOCUMENT", "doc-1");

        assertThat(r.status()).isEqualTo(ChainVerifier.Result.Status.OK);
        assertThat(r.eventsTraversed()).isEqualTo(3);
        assertThat(r.firstEventId()).isEqualTo("E1");
        assertThat(r.lastEventId()).isEqualTo("E3");
    }

    @Test
    void broken_link_reports_BROKEN_with_offending_event_and_hashes() {
        StoredTier1Event e1 = build("E1", Instant.parse("2026-04-30T10:00:00Z"), null);
        // Deliberately wrong previousEventHash on e2 — chain broken at E2
        StoredTier1Event e2 = build("E2", Instant.parse("2026-04-30T10:01:00Z"),
                "sha256:0000000000000000000000000000000000000000000000000000000000000000");

        when(tier1Store.findChainAsc("DOCUMENT", "doc-1"))
                .thenReturn(List.of(e1, e2));

        ChainVerifier.Result r = verifier.verify("DOCUMENT", "doc-1");

        assertThat(r.status()).isEqualTo(ChainVerifier.Result.Status.BROKEN);
        assertThat(r.brokenAtEventId()).isEqualTo("E2");
        assertThat(r.expectedPreviousHash()).startsWith("sha256:0");
        assertThat(r.computedPreviousHash()).isEqualTo(EventHasher.hashOf(e1));
    }

    @Test
    void first_event_with_non_null_previousHash_is_BROKEN() {
        // First-in-chain MUST have null previousHash; non-null breaks the invariant.
        StoredTier1Event first = build("E1", Instant.parse("2026-04-30T10:00:00Z"),
                "sha256:abc");
        when(tier1Store.findChainAsc("DOCUMENT", "doc-1"))
                .thenReturn(List.of(first));

        ChainVerifier.Result r = verifier.verify("DOCUMENT", "doc-1");

        assertThat(r.status()).isEqualTo(ChainVerifier.Result.Status.BROKEN);
        assertThat(r.brokenAtEventId()).isEqualTo("E1");
        assertThat(r.computedPreviousHash()).isNull();
    }

    private static StoredTier1Event build(String eventId, Instant ts, String previousEventHash) {
        return new StoredTier1Event(eventId, "DOCUMENT_CLASSIFIED", "1.0.0", ts,
                "doc-1", null, null, null,
                "test-svc", "SYSTEM", "DOCUMENT", "doc-1",
                "CLASSIFY", "SUCCESS", "7Y", previousEventHash, Map.of());
    }
}
