package co.uk.wolfnotsheep.document.repositories;

import co.uk.wolfnotsheep.document.models.SystemError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;

public interface SystemErrorRepository extends MongoRepository<SystemError, String> {

    Page<SystemError> findByResolvedFalseOrderByTimestampDesc(Pageable pageable);

    Page<SystemError> findByOrderByTimestampDesc(Pageable pageable);

    Page<SystemError> findByCategoryOrderByTimestampDesc(String category, Pageable pageable);

    Page<SystemError> findBySeverityOrderByTimestampDesc(String severity, Pageable pageable);

    long countByResolvedFalse();

    long countByResolvedFalseAndSeverity(String severity);

    long countByTimestampAfterAndResolvedFalse(Instant after);
}
