package co.uk.wolfnotsheep.document.repositories;

import co.uk.wolfnotsheep.document.models.AiUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface AiUsageLogRepository extends MongoRepository<AiUsageLog, String> {

    Page<AiUsageLog> findByOrderByTimestampDesc(Pageable pageable);

    Page<AiUsageLog> findByUsageTypeOrderByTimestampDesc(String usageType, Pageable pageable);

    Page<AiUsageLog> findByStatusOrderByTimestampDesc(String status, Pageable pageable);

    Page<AiUsageLog> findByUsageTypeAndStatusOrderByTimestampDesc(String usageType, String status, Pageable pageable);

    Page<AiUsageLog> findByDocumentIdOrderByTimestampDesc(String documentId, Pageable pageable);

    Page<AiUsageLog> findByTriggeredByOrderByTimestampDesc(String triggeredBy, Pageable pageable);

    long countByUsageType(String usageType);

    long countByStatus(String status);

    long countByTimestampAfter(Instant after);

    List<AiUsageLog> findByDocumentIdAndUsageTypeOrderByTimestampDesc(String documentId, String usageType);
}
