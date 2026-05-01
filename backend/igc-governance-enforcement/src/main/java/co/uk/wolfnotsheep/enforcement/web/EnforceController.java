package co.uk.wolfnotsheep.enforcement.web;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.enforcement.api.EnforceApi;
import co.uk.wolfnotsheep.enforcement.jobs.JobAcquisition;
import co.uk.wolfnotsheep.enforcement.jobs.JobStore;
import co.uk.wolfnotsheep.enforcement.model.EnforceRequest;
import co.uk.wolfnotsheep.enforcement.model.EnforceResponse;
import co.uk.wolfnotsheep.enforcement.model.JobAccepted;
import co.uk.wolfnotsheep.enforcement.services.EnforcementService;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

/**
 * Implements {@link EnforceApi}. Sync (200) without
 * {@code Prefer: respond-async}, 202 with poll URL when the header is
 * set (CSV #47). Sync and async share the {@link JobStore} row for
 * idempotency — same shape as {@code igc-classifier-router},
 * {@code igc-extraction-audio} and {@code igc-slm-worker}.
 *
 * The actual enforcement work is delegated to
 * {@link EnforcementService}; this controller is a thin HTTP veneer
 * plus before/after snapshot capture so {@code AppliedSummary} can
 * be composed from the diff without changing the service signature.
 */
@RestController
public class EnforceController implements EnforceApi {

    private static final Logger log = LoggerFactory.getLogger(EnforceController.class);

    private final EnforcementService enforcementService;
    private final DocumentService documentService;
    private final GovernanceService governanceService;
    private final JobStore jobs;
    private final ObjectMapper mapper;
    private final AsyncDispatcher asyncDispatcher;

    public EnforceController(EnforcementService enforcementService,
                             DocumentService documentService,
                             GovernanceService governanceService,
                             JobStore jobs,
                             ObjectMapper mapper,
                             AsyncDispatcher asyncDispatcher) {
        this.enforcementService = enforcementService;
        this.documentService = documentService;
        this.governanceService = governanceService;
        this.jobs = jobs;
        this.mapper = mapper;
        this.asyncDispatcher = asyncDispatcher;
    }

    @Override
    public ResponseEntity<EnforceResponse> enforce(
            String traceparent, EnforceRequest request, String idempotencyKey, String prefer) {

        boolean async = prefer != null && prefer.toLowerCase().contains("respond-async");
        JobAcquisition acq = jobs.tryAcquire(request.getNodeRunId());
        return switch (acq.status()) {
            case ACQUIRED -> async
                    ? handleAsyncAcquired(request)
                    : handleSyncAcquired(request);
            case RUNNING -> async
                    ? acceptedFor(request.getNodeRunId())
                    : runningCollision(request.getNodeRunId());
            case COMPLETED -> async
                    ? acceptedFor(request.getNodeRunId())
                    : ResponseEntity.ok(deserialiseCached(acq.existing().resultJson()));
            case FAILED -> async
                    ? acceptedFor(request.getNodeRunId())
                    : runningCollision(request.getNodeRunId());
        };
    }

    private ResponseEntity<EnforceResponse> runningCollision(String nodeRunId) {
        throw new JobInFlightException(nodeRunId);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<EnforceResponse> acceptedFor(String nodeRunId) {
        URI poll = URI.create("/v1/jobs/" + nodeRunId);
        JobAccepted body = new JobAccepted();
        body.setNodeRunId(nodeRunId);
        body.setStatus(JobAccepted.StatusEnum.PENDING);
        body.setPollUrl(poll);
        return (ResponseEntity<EnforceResponse>) (ResponseEntity<?>)
                ResponseEntity.accepted().header(HttpHeaders.LOCATION, poll.toString()).body(body);
    }

    private ResponseEntity<EnforceResponse> handleSyncAcquired(EnforceRequest request) {
        try {
            jobs.markRunning(request.getNodeRunId());
            EnforceResponse body = doEnforce(request);
            cacheCompleted(request.getNodeRunId(), body);
            return ResponseEntity.ok(body);
        } catch (RuntimeException failure) {
            String code = errorCodeFor(failure);
            jobs.markFailed(request.getNodeRunId(), code, safeMessage(failure));
            throw failure;
        }
    }

    private ResponseEntity<EnforceResponse> handleAsyncAcquired(EnforceRequest request) {
        asyncDispatcher.dispatch(request);
        return acceptedFor(request.getNodeRunId());
    }

    /** Background path. Package-private so {@link AsyncDispatcher} can {@code @Async}-invoke it. */
    void runAsync(EnforceRequest request) {
        try {
            jobs.markRunning(request.getNodeRunId());
            EnforceResponse body = doEnforce(request);
            cacheCompleted(request.getNodeRunId(), body);
        } catch (RuntimeException failure) {
            String code = errorCodeFor(failure);
            jobs.markFailed(request.getNodeRunId(), code, safeMessage(failure));
        }
    }

    private EnforceResponse doEnforce(EnforceRequest request) {
        DocumentClassifiedEvent event = EnforcementMapper.toDomainEvent(request);

        DocumentModel before = documentService.getById(event.documentId());
        if (before == null) {
            throw new DocumentNotFoundException(event.documentId());
        }
        String storageTierBefore = before.getStorageTierId();

        Instant started = Instant.now();
        DocumentModel after = enforcementService.enforce(event);
        long durationMs = EnforcementMapper.durationMs(started);

        if (after == null) {
            // EnforcementService.enforce returns null only when the document is missing —
            // we already checked above, but stay defensive in case of a race.
            throw new DocumentNotFoundException(event.documentId());
        }

        RetentionSchedule schedule = after.getRetentionScheduleId() == null
                ? null : governanceService.getRetentionSchedule(after.getRetentionScheduleId());

        // auditEventId left null in PR2 — igc-platform-audit integration lands later
        // (see contract README: legacy AuditEventRepository emits today).
        return EnforcementMapper.toResponse(request, storageTierBefore, after, schedule, durationMs, null);
    }

    private EnforceResponse deserialiseCached(String json) {
        try {
            return mapper.readValue(json, EnforceResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("idempotency cache deserialise failed: {}", e.getMessage());
            throw new IllegalStateException("idempotency cache row was unparseable", e);
        }
    }

    private void cacheCompleted(String nodeRunId, EnforceResponse response) {
        if (response == null) return;
        try {
            String json = mapper.writeValueAsString(response);
            jobs.markCompleted(nodeRunId, json);
        } catch (JsonProcessingException e) {
            log.warn("enforcement cache write failed for nodeRunId={}: {}", nodeRunId, e.getMessage());
        }
    }

    private static String safeMessage(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private static String errorCodeFor(Throwable cause) {
        if (cause instanceof DocumentNotFoundException) return "DOCUMENT_NOT_FOUND";
        if (cause instanceof EnforcementInvalidInputException) return "ENFORCEMENT_INVALID_INPUT";
        if (cause instanceof JobInFlightException) return "IDEMPOTENCY_IN_FLIGHT";
        return "ENFORCEMENT_UNEXPECTED";
    }
}
