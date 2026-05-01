package co.uk.wolfnotsheep.enforcement.web;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.enforcement.model.AppliedSummary;
import co.uk.wolfnotsheep.enforcement.model.ClassificationEvent;
import co.uk.wolfnotsheep.enforcement.model.EnforceRequest;
import co.uk.wolfnotsheep.enforcement.model.EnforceResponse;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnforcementMapperTest {

    @Test
    void toDomainEvent_passesAllFieldsThrough() {
        ClassificationEvent c = new ClassificationEvent();
        c.setDocumentId("doc-1");
        c.setClassificationResultId("cr-1");
        c.setCategoryId("cat-1");
        c.setCategoryName("HR > Contracts");
        c.setSensitivityLabel(ClassificationEvent.SensitivityLabelEnum.CONFIDENTIAL);
        c.setTags(List.of("hr", "contract"));
        c.setApplicablePolicyIds(List.of("policy-uk-gdpr"));
        c.setRetentionScheduleId("ret-7y");
        c.setConfidence(0.92f);
        c.setRequiresHumanReview(true);
        c.setClassifiedAt(OffsetDateTime.parse("2026-04-29T10:00:00Z"));

        EnforceRequest req = new EnforceRequest("nr-1", c);
        DocumentClassifiedEvent event = EnforcementMapper.toDomainEvent(req);

        assertThat(event.documentId()).isEqualTo("doc-1");
        assertThat(event.classificationResultId()).isEqualTo("cr-1");
        assertThat(event.categoryId()).isEqualTo("cat-1");
        assertThat(event.categoryName()).isEqualTo("HR > Contracts");
        assertThat(event.sensitivityLabel()).isEqualTo(SensitivityLabel.CONFIDENTIAL);
        assertThat(event.tags()).containsExactly("hr", "contract");
        assertThat(event.applicablePolicyIds()).containsExactly("policy-uk-gdpr");
        assertThat(event.retentionScheduleId()).isEqualTo("ret-7y");
        assertThat(event.confidence()).isEqualTo(0.92, org.assertj.core.data.Offset.offset(0.001));
        assertThat(event.requiresHumanReview()).isTrue();
        assertThat(event.classifiedAt()).isEqualTo(OffsetDateTime.parse("2026-04-29T10:00:00Z").toInstant());
    }

    @Test
    void toDomainEvent_optionalFieldsDefaultSafely() {
        ClassificationEvent c = new ClassificationEvent();
        c.setDocumentId("doc-1");
        c.setClassificationResultId("cr-1");
        c.setCategoryId("cat-1");
        c.setClassifiedAt(OffsetDateTime.now(ZoneOffset.UTC));

        DocumentClassifiedEvent event = EnforcementMapper.toDomainEvent(new EnforceRequest("nr-1", c));

        assertThat(event.tags()).isEmpty();
        assertThat(event.applicablePolicyIds()).isEmpty();
        assertThat(event.confidence()).isEqualTo(0.0);
        assertThat(event.requiresHumanReview()).isFalse();
        assertThat(event.sensitivityLabel()).isNull();
    }

    @Test
    void toDomainEvent_throwsOnMissingDocumentId() {
        ClassificationEvent c = new ClassificationEvent();
        c.setClassificationResultId("cr-1");
        c.setCategoryId("cat-1");
        c.setClassifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
        EnforceRequest req = new EnforceRequest("nr-1", c);

        assertThatThrownBy(() -> EnforcementMapper.toDomainEvent(req))
                .isInstanceOf(EnforcementInvalidInputException.class)
                .hasMessageContaining("documentId");
    }

    @Test
    void toDomainEvent_throwsOnMissingClassificationResultId() {
        ClassificationEvent c = new ClassificationEvent();
        c.setDocumentId("doc-1");
        c.setCategoryId("cat-1");
        c.setClassifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
        EnforceRequest req = new EnforceRequest("nr-1", c);

        assertThatThrownBy(() -> EnforcementMapper.toDomainEvent(req))
                .isInstanceOf(EnforcementInvalidInputException.class)
                .hasMessageContaining("classificationResultId");
    }

    @Test
    void toDomainEvent_throwsOnMissingCategoryId() {
        ClassificationEvent c = new ClassificationEvent();
        c.setDocumentId("doc-1");
        c.setClassificationResultId("cr-1");
        c.setClassifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
        EnforceRequest req = new EnforceRequest("nr-1", c);

        assertThatThrownBy(() -> EnforcementMapper.toDomainEvent(req))
                .isInstanceOf(EnforcementInvalidInputException.class)
                .hasMessageContaining("categoryId");
    }

    @Test
    void toDomainEvent_throwsOnMissingClassifiedAt() {
        ClassificationEvent c = new ClassificationEvent();
        c.setDocumentId("doc-1");
        c.setClassificationResultId("cr-1");
        c.setCategoryId("cat-1");
        EnforceRequest req = new EnforceRequest("nr-1", c);

        assertThatThrownBy(() -> EnforcementMapper.toDomainEvent(req))
                .isInstanceOf(EnforcementInvalidInputException.class)
                .hasMessageContaining("classifiedAt");
    }

    @Test
    void toDomainEvent_throwsOnNullClassification() {
        EnforceRequest req = new EnforceRequest();
        req.setNodeRunId("nr-1");

        assertThatThrownBy(() -> EnforcementMapper.toDomainEvent(req))
                .isInstanceOf(EnforcementInvalidInputException.class)
                .hasMessageContaining("classification");
    }

    @Test
    void toResponse_diffsStorageTierAndComposesAppliedSummary() {
        DocumentModel after = new DocumentModel();
        after.setAppliedPolicyIds(List.of("policy-uk-gdpr"));
        after.setRetentionScheduleId("ret-7y");
        after.setRetentionPeriodText("7 years after termination");
        after.setRetentionTrigger(ClassificationCategory.RetentionTrigger.DATE_CREATED);
        after.setExpectedDispositionAction(RetentionSchedule.DispositionAction.DELETE);
        after.setStorageTierId("tier-cold");

        EnforceRequest req = buildBasicRequest();
        EnforceResponse response = EnforcementMapper.toResponse(req, "tier-hot", after, null, 42L, "audit-123");

        assertThat(response.getNodeRunId()).isEqualTo("nr-1");
        assertThat(response.getDocumentId()).isEqualTo("doc-1");
        assertThat(response.getDurationMs()).isEqualTo(42);

        AppliedSummary applied = response.getApplied();
        assertThat(applied.getAppliedPolicyIds()).containsExactly("policy-uk-gdpr");
        assertThat(applied.getRetentionScheduleId()).isEqualTo("ret-7y");
        assertThat(applied.getRetentionPeriodText()).isEqualTo("7 years after termination");
        assertThat(applied.getRetentionTrigger()).isEqualTo(AppliedSummary.RetentionTriggerEnum.DATE_CREATED);
        assertThat(applied.getExpectedDispositionAction()).isEqualTo(AppliedSummary.ExpectedDispositionActionEnum.DELETE);
        assertThat(applied.getStorageTierBefore()).isEqualTo("tier-hot");
        assertThat(applied.getStorageTierAfter()).isEqualTo("tier-cold");
        assertThat(applied.getStorageMigrated()).isTrue();
        assertThat(applied.getAuditEventId()).isEqualTo("audit-123");
    }

    @Test
    void toResponse_storageMigratedFalseWhenTierUnchanged() {
        DocumentModel after = new DocumentModel();
        after.setStorageTierId("tier-hot");

        EnforceResponse response = EnforcementMapper.toResponse(
                buildBasicRequest(), "tier-hot", after, null, 0L, null);

        assertThat(response.getApplied().getStorageMigrated()).isFalse();
        assertThat(response.getApplied().getAuditEventId()).isNull();
    }

    @Test
    void toResponse_fallsBackToScheduleDispositionWhenDocFieldUnset() {
        DocumentModel after = new DocumentModel();
        // No expectedDispositionAction set on doc
        RetentionSchedule schedule = new RetentionSchedule();
        schedule.setDispositionAction(RetentionSchedule.DispositionAction.ARCHIVE);

        EnforceResponse response = EnforcementMapper.toResponse(
                buildBasicRequest(), null, after, schedule, 0L, null);

        assertThat(response.getApplied().getExpectedDispositionAction())
                .isEqualTo(AppliedSummary.ExpectedDispositionActionEnum.ARCHIVE);
    }

    @Test
    void toResponse_emptyAppliedWhenAfterIsNull() {
        EnforceResponse response = EnforcementMapper.toResponse(
                buildBasicRequest(), "tier-hot", null, null, 0L, null);

        AppliedSummary applied = response.getApplied();
        assertThat(applied.getAppliedPolicyIds()).isEmpty();
        assertThat(applied.getStorageTierBefore()).isNull();
        assertThat(applied.getStorageTierAfter()).isNull();
    }

    private static EnforceRequest buildBasicRequest() {
        ClassificationEvent c = new ClassificationEvent();
        c.setDocumentId("doc-1");
        c.setClassificationResultId("cr-1");
        c.setCategoryId("cat-1");
        c.setClassifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return new EnforceRequest("nr-1", c);
    }
}
