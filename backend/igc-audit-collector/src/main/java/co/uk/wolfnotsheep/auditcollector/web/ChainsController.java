package co.uk.wolfnotsheep.auditcollector.web;

import co.uk.wolfnotsheep.auditcollector.api.ChainsApi;
import co.uk.wolfnotsheep.auditcollector.chain.ChainVerifier;
import co.uk.wolfnotsheep.auditcollector.model.AuditEvent;
import co.uk.wolfnotsheep.auditcollector.model.ChainVerifyResponse;
import co.uk.wolfnotsheep.auditcollector.model.Tier1ChainResponse;
import co.uk.wolfnotsheep.auditcollector.store.StoredTier1Event;
import co.uk.wolfnotsheep.auditcollector.store.Tier1Store;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class ChainsController implements ChainsApi {

    private static final int MAX_EVENTS = 10_000;

    private final ChainVerifier verifier;
    private final Tier1Store tier1Store;

    public ChainsController(ChainVerifier verifier, Tier1Store tier1Store) {
        this.verifier = verifier;
        this.tier1Store = tier1Store;
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

    @Override
    public ResponseEntity<Tier1ChainResponse> listTier1ForResource(
            String traceparent, String resourceType, String resourceId) {
        List<StoredTier1Event> rows = tier1Store.findChainAsc(resourceType, resourceId);
        if (rows.isEmpty()) {
            throw new AuditResourceNotFoundException(resourceType, resourceId);
        }
        if (rows.size() > MAX_EVENTS) {
            // Soft cap. Per the contract, we return the first MAX_EVENTS
            // and the caller decides whether to follow up.
            rows = rows.subList(0, MAX_EVENTS);
        }
        List<AuditEvent> events = new ArrayList<>(rows.size());
        for (StoredTier1Event row : rows) events.add(toApiEvent(row));

        Tier1ChainResponse body = new Tier1ChainResponse();
        body.setResourceType(Tier1ChainResponse.ResourceTypeEnum.fromValue(resourceType));
        body.setResourceId(resourceId);
        body.setEvents(events);
        return ResponseEntity.ok(body);
    }

    /** Mirrors the event-to-API mapping in {@link EventsController#toApiEvent}. */
    private static AuditEvent toApiEvent(StoredTier1Event row) {
        AuditEvent api = new AuditEvent();
        api.setEventId(row.getEventId());
        api.setEventType(row.getEventType());
        if (row.getTier() != null) {
            api.setTier(AuditEvent.TierEnum.fromValue(row.getTier()));
        }
        api.setSchemaVersion(row.getSchemaVersion());
        if (row.getTimestamp() != null) {
            api.setTimestamp(row.getTimestamp().atOffset(ZoneOffset.UTC));
        }
        api.setDocumentId(row.getDocumentId());
        api.setPipelineRunId(row.getPipelineRunId());
        api.setNodeRunId(row.getNodeRunId());
        api.setTraceparent(row.getTraceparent());
        api.setAction(row.getAction());
        if (row.getOutcome() != null) {
            api.setOutcome(AuditEvent.OutcomeEnum.fromValue(row.getOutcome()));
        }
        api.setRetentionClass(row.getRetentionClass());
        api.setPreviousEventHash(row.getPreviousEventHash());
        if (row.getEnvelope() != null) {
            api.setActor(asMap(row.getEnvelope().get("actor")));
            api.setResource(asMap(row.getEnvelope().get("resource")));
            api.setDetails(asMap(row.getEnvelope().get("details")));
        }
        return api;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }
}
