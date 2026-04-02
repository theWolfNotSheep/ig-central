package co.uk.wolfnotsheep.document.repositories;

import co.uk.wolfnotsheep.document.models.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {

    Page<AuditEvent> findByDocumentIdOrderByTimestampDesc(String documentId, Pageable pageable);

    Page<AuditEvent> findByActionOrderByTimestampDesc(String action, Pageable pageable);

    Page<AuditEvent> findByPerformedByOrderByTimestampDesc(String performedBy, Pageable pageable);
}
