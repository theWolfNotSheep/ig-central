package co.uk.wolfnotsheep.enforcement.web;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.enforcement.model.AppliedSummary;
import co.uk.wolfnotsheep.enforcement.model.ClassificationEvent;
import co.uk.wolfnotsheep.enforcement.model.EnforceRequest;
import co.uk.wolfnotsheep.enforcement.model.EnforceResponse;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Translates between the OpenAPI-generated contract types and the
 * internal {@link DocumentClassifiedEvent} / {@link DocumentModel}
 * shapes used by {@link co.uk.wolfnotsheep.enforcement.services.EnforcementService}.
 *
 * Validation lives here too — fields that are required by the
 * {@link EnforcementService}-internal happy path but optional in the
 * contract (so the caller can omit them when re-fetching from Mongo
 * is fine) are checked at request time and surface as
 * {@code 422 ENFORCEMENT_INVALID_INPUT}.
 */
public final class EnforcementMapper {

    private EnforcementMapper() {}

    public static DocumentClassifiedEvent toDomainEvent(EnforceRequest request) {
        if (request == null || request.getClassification() == null) {
            throw new EnforcementInvalidInputException("classification is required");
        }
        ClassificationEvent c = request.getClassification();
        if (isBlank(c.getDocumentId())) {
            throw new EnforcementInvalidInputException("classification.documentId is required");
        }
        if (isBlank(c.getClassificationResultId())) {
            throw new EnforcementInvalidInputException("classification.classificationResultId is required");
        }
        if (isBlank(c.getCategoryId())) {
            throw new EnforcementInvalidInputException("classification.categoryId is required");
        }
        if (c.getClassifiedAt() == null) {
            throw new EnforcementInvalidInputException("classification.classifiedAt is required");
        }

        SensitivityLabel sensitivity = c.getSensitivityLabel() == null
                ? null : SensitivityLabel.valueOf(c.getSensitivityLabel().getValue());

        return new DocumentClassifiedEvent(
                c.getDocumentId(),
                c.getClassificationResultId(),
                c.getCategoryId(),
                c.getCategoryName(),
                sensitivity,
                c.getTags() == null ? List.of() : c.getTags(),
                c.getApplicablePolicyIds() == null ? List.of() : c.getApplicablePolicyIds(),
                c.getRetentionScheduleId(),
                c.getConfidence() == null ? 0.0 : c.getConfidence().doubleValue(),
                Boolean.TRUE.equals(c.getRequiresHumanReview()),
                c.getClassifiedAt().toInstant());
    }

    /**
     * Compose the response body from the document-snapshot diff plus
     * the optional resolved retention schedule.
     *
     * @param request          incoming request (for echo of nodeRunId / docId)
     * @param storageTierBefore tierId before the call (snapshot)
     * @param after            document state after the call (may be null when not found)
     * @param schedule         resolved retention schedule (may be null)
     * @param durationMs       wall-clock duration of the enforcement call
     * @param auditEventId     id of the GOVERNANCE_APPLIED event written by the service (nullable)
     */
    public static EnforceResponse toResponse(EnforceRequest request,
                                             String storageTierBefore,
                                             DocumentModel after,
                                             RetentionSchedule schedule,
                                             long durationMs,
                                             String auditEventId) {
        EnforceResponse response = new EnforceResponse();
        response.setNodeRunId(request.getNodeRunId());
        response.setDocumentId(request.getClassification().getDocumentId());
        response.setApplied(toApplied(storageTierBefore, after, schedule, auditEventId));
        response.setDurationMs((int) Math.min(Integer.MAX_VALUE, durationMs));
        return response;
    }

    private static AppliedSummary toApplied(String storageTierBefore,
                                            DocumentModel after,
                                            RetentionSchedule schedule,
                                            String auditEventId) {
        AppliedSummary summary = new AppliedSummary();
        if (after == null) {
            return summary;
        }
        if (after.getAppliedPolicyIds() != null && !after.getAppliedPolicyIds().isEmpty()) {
            summary.setAppliedPolicyIds(after.getAppliedPolicyIds());
        }
        if (after.getRetentionScheduleId() != null) {
            summary.setRetentionScheduleId(after.getRetentionScheduleId());
        }
        if (after.getRetentionPeriodText() != null) {
            summary.setRetentionPeriodText(after.getRetentionPeriodText());
        }
        if (after.getRetentionTrigger() != null) {
            summary.setRetentionTrigger(
                    AppliedSummary.RetentionTriggerEnum.valueOf(after.getRetentionTrigger().name()));
        }
        RetentionSchedule.DispositionAction disposition = after.getExpectedDispositionAction();
        if (disposition == null && schedule != null) {
            disposition = schedule.getDispositionAction();
        }
        if (disposition != null) {
            summary.setExpectedDispositionAction(
                    AppliedSummary.ExpectedDispositionActionEnum.valueOf(disposition.name()));
        }
        String storageTierAfter = after.getStorageTierId();
        if (storageTierBefore != null) {
            summary.setStorageTierBefore(storageTierBefore);
        }
        if (storageTierAfter != null) {
            summary.setStorageTierAfter(storageTierAfter);
        }
        summary.setStorageMigrated(!sameTier(storageTierBefore, storageTierAfter));
        if (auditEventId != null) {
            summary.setAuditEventId(auditEventId);
        }
        return summary;
    }

    private static boolean sameTier(String before, String after) {
        if (before == null && after == null) return true;
        if (before == null || after == null) return false;
        return before.equals(after);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Wall-clock helper so controllers don't need to import
     * {@link Instant} just to compute durationMs.
     */
    public static long durationMs(Instant started) {
        return Math.max(0L, java.time.Duration.between(started, Instant.now()).toMillis());
    }

    /** Convenience pass-through used by the controller for snapshot capture. */
    public static String storageTierIdOf(Optional<DocumentModel> doc) {
        return doc.map(DocumentModel::getStorageTierId).orElse(null);
    }
}
