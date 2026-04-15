package co.uk.wolfnotsheep.document.repositories;

import co.uk.wolfnotsheep.document.models.SystemAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface SystemAuditEventRepository extends MongoRepository<SystemAuditEvent, String> {

    Page<SystemAuditEvent> findByOrderByTimestampDesc(Pageable pageable);

    Page<SystemAuditEvent> findByUserEmailContainingIgnoreCaseOrderByTimestampDesc(String email, Pageable pageable);

    Page<SystemAuditEvent> findByActionOrderByTimestampDesc(String action, Pageable pageable);

    Page<SystemAuditEvent> findByResourceTypeOrderByTimestampDesc(String resourceType, Pageable pageable);

    Page<SystemAuditEvent> findBySuccessFalseOrderByTimestampDesc(Pageable pageable);

    Page<SystemAuditEvent> findByTimestampBetweenOrderByTimestampDesc(Instant from, Instant to, Pageable pageable);

    long countBySuccessFalseAndTimestampAfter(Instant after);

    List<SystemAuditEvent> findTop50ByOrderByTimestampDesc();
}
