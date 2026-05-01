package co.uk.wolfnotsheep.document.repositories;

import co.uk.wolfnotsheep.document.models.PipelineRun;
import co.uk.wolfnotsheep.document.models.PipelineRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PipelineRunRepository extends MongoRepository<PipelineRun, String> {

    List<PipelineRun> findByDocumentIdOrderByCreatedAtDesc(String documentId);

    Optional<PipelineRun> findByCorrelationId(String correlationId);

    Page<PipelineRun> findByStatus(PipelineRunStatus status, Pageable pageable);

    Page<PipelineRun> findByOrganisationIdOrderByCreatedAtDesc(String organisationId, Pageable pageable);

    List<PipelineRun> findByStatusAndUpdatedAtBefore(PipelineRunStatus status, Instant cutoff);

    long countByStatus(PipelineRunStatus status);

    long countByOrganisationIdAndStatus(String organisationId, PipelineRunStatus status);
}
