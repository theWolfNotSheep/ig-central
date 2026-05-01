package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.document.models.AiUsageLog;
import co.uk.wolfnotsheep.document.repositories.AiUsageLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BulkReclassifyCostEstimatorTest {

    private final AiUsageLogRepository repo = mock(AiUsageLogRepository.class);
    private final BulkReclassifyCostEstimator estimator = new BulkReclassifyCostEstimator(repo);

    @Test
    void zeroDocumentCount_returnsEmptyEstimate() {
        BulkReclassifyCostEstimator.Estimate e = estimator.estimateForCount(0);
        assertThat(e.documentCount()).isZero();
        assertThat(e.sampleSize()).isZero();
        assertThat(e.estimatedTotalCostUsd()).isZero();
    }

    @Test
    void noClassifyHistory_returnsEmptyEstimate() {
        when(repo.findByUsageTypeOrderByTimestampDesc(eq("CLASSIFY"), any(Pageable.class)))
                .thenReturn(emptyPage());

        BulkReclassifyCostEstimator.Estimate e = estimator.estimateForCount(50);

        assertThat(e.sampleSize()).isZero();
        assertThat(e.estimatedTotalCostUsd()).isZero();
        assertThat(e.averageCostPerDocumentUsd()).isZero();
    }

    @Test
    void averagesAcrossSampleAndScalesByDocumentCount() {
        when(repo.findByUsageTypeOrderByTimestampDesc(eq("CLASSIFY"), any(Pageable.class)))
                .thenReturn(pageOf(
                        log(0.01, 100, 50),
                        log(0.02, 200, 100),
                        log(0.03, 300, 150)));

        BulkReclassifyCostEstimator.Estimate e = estimator.estimateForCount(100);

        assertThat(e.documentCount()).isEqualTo(100);
        assertThat(e.sampleSize()).isEqualTo(3);
        assertThat(e.averageCostPerDocumentUsd()).isEqualTo(0.02);
        assertThat(e.estimatedTotalCostUsd()).isEqualTo(2.0);
        assertThat(e.averageInputTokens()).isEqualTo(200.0);
        assertThat(e.averageOutputTokens()).isEqualTo(100.0);
        assertThat(e.estimatedTotalInputTokens()).isEqualTo(20_000.0);
        assertThat(e.estimatedTotalOutputTokens()).isEqualTo(10_000.0);
    }

    @Test
    void zeroCostSamplesAreSkippedFromCostAverage() {
        // Three logs but only two have cost > 0 (e.g. local Ollama runs report 0)
        when(repo.findByUsageTypeOrderByTimestampDesc(eq("CLASSIFY"), any(Pageable.class)))
                .thenReturn(pageOf(
                        log(0.0, 100, 50),
                        log(0.04, 200, 100),
                        log(0.06, 300, 150)));

        BulkReclassifyCostEstimator.Estimate e = estimator.estimateForCount(10);

        // Average over the two non-zero cost samples = (0.04 + 0.06) / 2 = 0.05
        assertThat(e.averageCostPerDocumentUsd()).isEqualTo(0.05);
        assertThat(e.estimatedTotalCostUsd()).isEqualTo(0.5);
        // Token average still uses all three samples
        assertThat(e.averageInputTokens()).isEqualTo(200.0);
    }

    @Test
    void customSampleSizeIsForwardedToRepository() {
        when(repo.findByUsageTypeOrderByTimestampDesc(eq("CLASSIFY"), any(Pageable.class)))
                .thenReturn(pageOf(log(0.01, 100, 50)));

        estimator.estimateForCount(5, 250);

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(repo)
                .findByUsageTypeOrderByTimestampDesc(eq("CLASSIFY"), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(250);
    }

    private static AiUsageLog log(double cost, int inTokens, int outTokens) {
        AiUsageLog log = new AiUsageLog();
        log.setEstimatedCost(cost);
        log.setInputTokens(inTokens);
        log.setOutputTokens(outTokens);
        return log;
    }

    private static Page<AiUsageLog> pageOf(AiUsageLog... logs) {
        return new PageImpl<>(List.of(logs));
    }

    private static Page<AiUsageLog> emptyPage() {
        return new PageImpl<>(List.of());
    }
}
