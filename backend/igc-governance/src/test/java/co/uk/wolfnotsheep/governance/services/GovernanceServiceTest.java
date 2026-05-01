package co.uk.wolfnotsheep.governance.services;

import co.uk.wolfnotsheep.governance.models.*;
import co.uk.wolfnotsheep.governance.models.ClassificationCorrection.CorrectionType;
import co.uk.wolfnotsheep.governance.models.ClassificationCorrection.PiiCorrection;
import co.uk.wolfnotsheep.governance.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GovernanceServiceTest {

    @Mock
    private GovernancePolicyRepository policyRepository;

    @Mock
    private ClassificationCategoryRepository categoryRepository;

    @Mock
    private RetentionScheduleRepository retentionRepository;

    @Mock
    private StorageTierRepository storageTierRepository;

    @Mock
    private DocumentClassificationResultRepository classificationResultRepository;

    @Mock
    private ClassificationCorrectionRepository correctionRepository;

    @Mock
    private MetadataSchemaRepository metadataSchemaRepository;

    @InjectMocks
    private GovernanceService governanceService;

    // ── getTaxonomyAsText ────────────────────────────────────

    @Test
    void getTaxonomyAsText_withCategories_buildsNestedTree() {
        ClassificationCategory root = makeCategory("root-1", "Legal", "Legal documents", null, SensitivityLabel.CONFIDENTIAL);
        ClassificationCategory child = makeCategory("child-1", "Contracts", "Contract files", "root-1", SensitivityLabel.INTERNAL);

        when(categoryRepository.findByActiveTrue()).thenReturn(List.of(root, child));

        String result = governanceService.getTaxonomyAsText();

        assertThat(result).contains("- Legal: Legal documents [default sensitivity: CONFIDENTIAL]");
        assertThat(result).contains("  - Contracts: Contract files [default sensitivity: INTERNAL]");
    }

    @Test
    void getTaxonomyAsText_empty_returnsEmptyString() {
        when(categoryRepository.findByActiveTrue()).thenReturn(List.of());

        String result = governanceService.getTaxonomyAsText();

        assertThat(result).isEmpty();
    }

    // ── getPiiCorrections ────────────────────────────────────

    @Test
    void getPiiCorrections_returnsBoundedResults() {
        List<ClassificationCorrection> corrections = List.of(makePiiCorrection(), makePiiCorrection());
        when(correctionRepository.findTop50ByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_FLAGGED))
                .thenReturn(corrections);

        List<ClassificationCorrection> result = governanceService.getPiiCorrections();

        assertThat(result).hasSize(2);
        verify(correctionRepository).findTop50ByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_FLAGGED);
    }

    // ── getPiiDismissals ─────────────────────────────────────

    @Test
    void getPiiDismissals_returnsBoundedResults() {
        List<ClassificationCorrection> dismissals = List.of(makePiiCorrection());
        when(correctionRepository.findTop50ByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_DISMISSED))
                .thenReturn(dismissals);

        List<ClassificationCorrection> result = governanceService.getPiiDismissals();

        assertThat(result).hasSize(1);
        verify(correctionRepository).findTop50ByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_DISMISSED);
    }

    // ── getCorrectionsSummaryForLlm ──────────────────────────

    @Test
    void getCorrectionsSummaryForLlm_withCorrections_includesCategorySections() {
        ClassificationCorrection catCorrection = new ClassificationCorrection();
        catCorrection.setCorrectionType(CorrectionType.CATEGORY_CHANGED);
        catCorrection.setOriginalCategoryName("Legal");
        catCorrection.setCorrectedCategoryName("HR");
        catCorrection.setReason("Was an employment doc");

        ClassificationCorrection piiCorrection = makePiiCorrection();

        when(correctionRepository.findTop50ByOriginalCategoryIdOrderByCorrectedAtDesc("cat-1"))
                .thenReturn(List.of(catCorrection));
        when(correctionRepository.findTop50ByMimeTypeOrderByCorrectedAtDesc("application/pdf"))
                .thenReturn(List.of());
        when(correctionRepository.findTop50ByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_FLAGGED))
                .thenReturn(List.of(piiCorrection));
        when(correctionRepository.findTop20ByOrderByCorrectedAtDesc())
                .thenReturn(List.of());

        String result = governanceService.getCorrectionsSummaryForLlm("cat-1", "application/pdf");

        assertThat(result).contains("## Corrections for this category");
        assertThat(result).contains("Category changed: Legal");
        assertThat(result).contains("HR");
        assertThat(result).contains("reason: Was an employment doc");
        assertThat(result).contains("## PII corrections");
    }

    @Test
    void getCorrectionsSummaryForLlm_empty_returnsNoPriorMessage() {
        when(correctionRepository.findTop50ByOriginalCategoryIdOrderByCorrectedAtDesc("cat-1"))
                .thenReturn(List.of());
        when(correctionRepository.findTop50ByMimeTypeOrderByCorrectedAtDesc("text/plain"))
                .thenReturn(List.of());
        when(correctionRepository.findTop50ByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_FLAGGED))
                .thenReturn(List.of());
        when(correctionRepository.findTop20ByOrderByCorrectedAtDesc())
                .thenReturn(List.of());

        String result = governanceService.getCorrectionsSummaryForLlm("cat-1", "text/plain");

        assertThat(result).isEqualTo("No prior human corrections recorded yet.");
    }

    @Test
    void getCorrectionsSummaryForLlm_nullCategoryAndMime_handlesGracefully() {
        when(correctionRepository.findTop50ByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_FLAGGED))
                .thenReturn(List.of());
        when(correctionRepository.findTop20ByOrderByCorrectedAtDesc())
                .thenReturn(List.of());

        String result = governanceService.getCorrectionsSummaryForLlm(null, null);

        assertThat(result).isEqualTo("No prior human corrections recorded yet.");
    }

    @Test
    void getCorrectionsSummaryForLlm_onlyRecentCorrections_showsGeneralSection() {
        ClassificationCorrection recent = new ClassificationCorrection();
        recent.setCorrectionType(CorrectionType.SENSITIVITY_CHANGED);
        recent.setOriginalSensitivity(SensitivityLabel.PUBLIC);
        recent.setCorrectedSensitivity(SensitivityLabel.CONFIDENTIAL);

        when(correctionRepository.findTop50ByOriginalCategoryIdOrderByCorrectedAtDesc("cat-1"))
                .thenReturn(List.of());
        when(correctionRepository.findTop50ByMimeTypeOrderByCorrectedAtDesc("text/plain"))
                .thenReturn(List.of());
        when(correctionRepository.findTop50ByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_FLAGGED))
                .thenReturn(List.of());
        when(correctionRepository.findTop20ByOrderByCorrectedAtDesc())
                .thenReturn(List.of(recent));

        String result = governanceService.getCorrectionsSummaryForLlm("cat-1", "text/plain");

        assertThat(result).contains("## Recent corrections (general)");
        assertThat(result).contains("Sensitivity changed: PUBLIC");
    }

    // ── getActivePolicies ────────────────────────────────────

    @Test
    void getActivePolicies_returnsPolicies() {
        GovernancePolicy policy = new GovernancePolicy();
        policy.setId("p1");
        policy.setName("Data Protection Policy");
        policy.setActive(true);

        when(policyRepository.findByActiveTrue()).thenReturn(List.of(policy));

        List<GovernancePolicy> result = governanceService.getActivePolicies();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Data Protection Policy");
    }

    @Test
    void getActivePolicies_empty_returnsEmptyList() {
        when(policyRepository.findByActiveTrue()).thenReturn(List.of());

        List<GovernancePolicy> result = governanceService.getActivePolicies();

        assertThat(result).isEmpty();
    }

    // ── saveClassificationResult ─────────────────────────────

    @Test
    void saveClassificationResult_setsTimestampAndSaves() {
        DocumentClassificationResult input = new DocumentClassificationResult();
        input.setDocumentId("doc-1");
        input.setCategoryName("Legal");
        input.setConfidence(0.95);

        when(classificationResultRepository.save(any(DocumentClassificationResult.class)))
                .thenAnswer(invocation -> {
                    DocumentClassificationResult saved = invocation.getArgument(0);
                    saved.setId("result-1");
                    return saved;
                });

        Instant before = Instant.now();
        DocumentClassificationResult result = governanceService.saveClassificationResult(input);
        Instant after = Instant.now();

        assertThat(result.getId()).isEqualTo("result-1");
        assertThat(result.getClassifiedAt()).isNotNull();
        assertThat(result.getClassifiedAt()).isBetween(before, after);
        verify(classificationResultRepository).save(input);
    }

    // ── Helpers ──────────────────────────────────────────────

    private ClassificationCategory makeCategory(String id, String name, String description,
                                                 String parentId, SensitivityLabel sensitivity) {
        ClassificationCategory cat = new ClassificationCategory();
        cat.setId(id);
        cat.setName(name);
        cat.setDescription(description);
        cat.setParentId(parentId);
        cat.setDefaultSensitivity(sensitivity);
        cat.setActive(true);
        return cat;
    }

    private ClassificationCorrection makePiiCorrection() {
        ClassificationCorrection correction = new ClassificationCorrection();
        correction.setCorrectionType(CorrectionType.PII_FLAGGED);
        correction.setPiiCorrections(List.of(
                new PiiCorrection("NATIONAL_INSURANCE", "NI number found", "AB123456C in paragraph 2")
        ));
        correction.setCorrectedAt(Instant.now());
        return correction;
    }
}
