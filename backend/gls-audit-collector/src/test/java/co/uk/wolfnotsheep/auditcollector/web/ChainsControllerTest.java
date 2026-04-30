package co.uk.wolfnotsheep.auditcollector.web;

import co.uk.wolfnotsheep.auditcollector.chain.ChainVerifier;
import co.uk.wolfnotsheep.auditcollector.model.ChainVerifyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChainsControllerTest {

    private ChainVerifier verifier;
    private ChainsController controller;

    @BeforeEach
    void setUp() {
        verifier = mock(ChainVerifier.class);
        controller = new ChainsController(verifier);
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

    private static String validTraceparent() {
        return "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    }
}
