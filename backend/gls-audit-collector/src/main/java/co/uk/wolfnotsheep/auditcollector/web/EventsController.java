package co.uk.wolfnotsheep.auditcollector.web;

import co.uk.wolfnotsheep.auditcollector.api.EventsApi;
import co.uk.wolfnotsheep.auditcollector.model.AuditEvent;
import co.uk.wolfnotsheep.auditcollector.model.EventListResponse;
import co.uk.wolfnotsheep.auditcollector.store.StoredAuditEvent;
import co.uk.wolfnotsheep.auditcollector.store.StoredTier2Event;
import co.uk.wolfnotsheep.auditcollector.store.Tier1Repository;
import co.uk.wolfnotsheep.auditcollector.store.Tier2Store;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implements {@link EventsApi}. Tier 2 search dispatches through
 * {@link Tier2Store}; the active backend (Mongo / ES) is selected
 * by {@code gls.audit.collector.tier2-backend}. Single-event fetch
 * consults Tier 1 first, then Tier 2 — the id is unique across both
 * per ULID semantics, so order doesn't matter for correctness.
 */
@RestController
public class EventsController implements EventsApi {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;

    private final Tier1Repository tier1Repo;
    private final Tier2Store tier2Store;

    public EventsController(Tier1Repository tier1Repo, Tier2Store tier2Store) {
        this.tier1Repo = tier1Repo;
        this.tier2Store = tier2Store;
    }

    @Override
    public ResponseEntity<EventListResponse> listEvents(
            String traceparent, String documentId, String eventType, String actorService,
            OffsetDateTime from, OffsetDateTime to, String pageToken, Integer pageSize) {

        if (from != null && to != null && !from.isBefore(to)) {
            throw new AuditQueryInvalidException("`from` must be earlier than `to`");
        }

        int size = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE
                : Math.min(pageSize, MAX_PAGE_SIZE);
        int page = decodePageToken(pageToken);

        Tier2Store.SearchCriteria criteria = new Tier2Store.SearchCriteria(
                documentId, eventType, actorService,
                from == null ? null : from.toInstant(),
                to == null ? null : to.toInstant());

        List<StoredTier2Event> rows = tier2Store.search(criteria, page, size);

        EventListResponse body = new EventListResponse();
        List<AuditEvent> events = new ArrayList<>(rows.size());
        for (StoredTier2Event row : rows) events.add(toApiEvent(row));
        body.setEvents(events);
        if (rows.size() == size) {
            body.setNextPageToken(encodePageToken(page + 1));
        }
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<AuditEvent> getEvent(String traceparent, String eventId) {
        Optional<? extends StoredAuditEvent> hit = tier1Repo.findById(eventId)
                .<StoredAuditEvent>map(r -> r)
                .or(() -> tier2Store.findById(eventId).map(r -> r));
        StoredAuditEvent row = hit.orElseThrow(() -> new AuditEventNotFoundException(eventId));
        return ResponseEntity.ok(toApiEvent(row));
    }

    private static AuditEvent toApiEvent(StoredAuditEvent row) {
        AuditEvent api = new AuditEvent();
        api.setEventId(row.getEventId());
        api.setEventType(row.getEventType());
        if (row.getTier() != null) {
            api.setTier(AuditEvent.TierEnum.fromValue(row.getTier()));
        }
        api.setSchemaVersion(row.getSchemaVersion());
        if (row.getTimestamp() != null) {
            api.setTimestamp(row.getTimestamp().atOffset(java.time.ZoneOffset.UTC));
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
        // actor / resource / details echo the raw envelope sub-objects
        if (row.getEnvelope() != null) {
            api.setActor(asMap(row.getEnvelope().get("actor")));
            api.setResource(asMap(row.getEnvelope().get("resource")));
            api.setDetails(asMap(row.getEnvelope().get("details")));
        }
        return api;
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> asMap(Object value) {
        if (value instanceof java.util.Map<?, ?> m) return (java.util.Map<String, Object>) m;
        return null;
    }

    /**
     * Page token is intentionally simple (zero-padded page index). Future
     * revisions can swap to opaque-base64-encoded cursor without breaking
     * the contract — the field is documented as opaque.
     */
    private static int decodePageToken(String token) {
        if (token == null || token.isBlank()) return 0;
        try {
            int n = Integer.parseInt(token);
            return Math.max(0, n);
        } catch (NumberFormatException e) {
            throw new AuditQueryInvalidException("`pageToken` is not a valid cursor");
        }
    }

    private static String encodePageToken(int page) {
        return Integer.toString(page);
    }
}
