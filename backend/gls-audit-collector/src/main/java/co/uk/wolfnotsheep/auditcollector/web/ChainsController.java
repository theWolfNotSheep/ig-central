package co.uk.wolfnotsheep.auditcollector.web;

import co.uk.wolfnotsheep.auditcollector.api.ChainsApi;
import co.uk.wolfnotsheep.auditcollector.chain.ChainVerifier;
import co.uk.wolfnotsheep.auditcollector.model.ChainVerifyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChainsController implements ChainsApi {

    private final ChainVerifier verifier;

    public ChainsController(ChainVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public ResponseEntity<ChainVerifyResponse> verifyChain(
            String traceparent, String resourceType, String resourceId) {
        ChainVerifier.Result result = verifier.verify(resourceType, resourceId);
        if (result.status() == ChainVerifier.Result.Status.NOT_FOUND) {
            throw new AuditResourceNotFoundException(resourceType, resourceId);
        }

        ChainVerifyResponse body = new ChainVerifyResponse();
        body.setResourceType(ChainVerifyResponse.ResourceTypeEnum.fromValue(result.resourceType()));
        body.setResourceId(result.resourceId());
        body.setStatus(result.status() == ChainVerifier.Result.Status.OK
                ? ChainVerifyResponse.StatusEnum.OK : ChainVerifyResponse.StatusEnum.BROKEN);
        body.setEventsTraversed(result.eventsTraversed());
        if (result.firstEventId() != null) body.setFirstEventId(result.firstEventId());
        if (result.lastEventId() != null) body.setLastEventId(result.lastEventId());
        if (result.brokenAtEventId() != null) body.setBrokenAtEventId(result.brokenAtEventId());
        if (result.expectedPreviousHash() != null) body.setExpectedPreviousHash(result.expectedPreviousHash());
        if (result.computedPreviousHash() != null) body.setComputedPreviousHash(result.computedPreviousHash());
        body.setDurationMs((int) Math.min(Integer.MAX_VALUE, Math.max(0L, result.durationMs())));
        return ResponseEntity.ok(body);
    }
}
