package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.document.models.SystemAuditEvent;
import co.uk.wolfnotsheep.document.repositories.SystemAuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditLogController {

    private final SystemAuditEventRepository auditRepo;
    private final MongoTemplate mongoTemplate;

    public AuditLogController(SystemAuditEventRepository auditRepo, MongoTemplate mongoTemplate) {
        this.auditRepo = auditRepo;
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping
    public ResponseEntity<Page<SystemAuditEvent>> getAuditLog(
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Pageable pageable) {

        List<Criteria> filters = new ArrayList<>();

        if (user != null && !user.isBlank()) {
            filters.add(Criteria.where("userEmail").regex(".*" + user + ".*", "i"));
        }
        if (action != null && !action.isBlank()) {
            filters.add(Criteria.where("action").regex(".*" + action + ".*", "i"));
        }
        if (resourceType != null && !resourceType.isBlank()) {
            filters.add(Criteria.where("resourceType").is(resourceType));
        }
        if (success != null) {
            filters.add(Criteria.where("success").is(success));
        }
        if (from != null) {
            filters.add(Criteria.where("timestamp").gte(Instant.parse(from)));
        }
        if (to != null) {
            filters.add(Criteria.where("timestamp").lte(Instant.parse(to)));
        }

        Query query = filters.isEmpty()
                ? new Query().with(pageable)
                : new Query(new Criteria().andOperator(filters.toArray(new Criteria[0]))).with(pageable);

        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "timestamp"));

        List<SystemAuditEvent> results = mongoTemplate.find(query, SystemAuditEvent.class);
        return ResponseEntity.ok(PageableExecutionUtils.getPage(results, pageable,
                () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), SystemAuditEvent.class)));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long total = auditRepo.count();
        long errorsLast24h = auditRepo.countBySuccessFalseAndTimestampAfter(
                Instant.now().minusSeconds(86400));

        return ResponseEntity.ok(Map.of(
                "totalEvents", total,
                "errorsLast24h", errorsLast24h
        ));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<SystemAuditEvent>> getRecent() {
        return ResponseEntity.ok(auditRepo.findTop50ByOrderByTimestampDesc());
    }
}
