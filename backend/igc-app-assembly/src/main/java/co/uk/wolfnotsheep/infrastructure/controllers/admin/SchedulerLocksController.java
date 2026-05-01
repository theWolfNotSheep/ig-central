package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Admin REST surface for {@code @SchedulerLock} state visibility.
 *
 * <p>Reads ShedLock's {@code shedLock} Mongo collection (the same
 * collection the {@link net.javacrumbs.shedlock.provider.mongo.MongoLockProvider}
 * writes to) and returns the current rows. Each row represents a
 * named lock — {@code _id} is the lock name (e.g.
 * {@code audit-tier1-leader}, {@code stale-pipeline-recovery}),
 * {@code lockUntil} is when the lease expires, {@code lockedAt} is
 * when the current holder acquired it, {@code lockedBy} is the
 * holder's hostname.
 *
 * <p>{@code lockUntil} in the past = no current holder; the next
 * scheduled tick will acquire. {@code lockUntil} in the future = a
 * replica is currently the leader for this lock.
 */
@RestController
@RequestMapping("/api/admin/scheduler/locks")
public class SchedulerLocksController {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLocksController.class);

    private final MongoTemplate mongoTemplate;
    private final String collectionName;

    public SchedulerLocksController(
            MongoTemplate mongoTemplate,
            @Value("${igc.shedlock.collection:shedLock}") String collectionName) {
        this.mongoTemplate = mongoTemplate;
        this.collectionName = collectionName;
    }

    @GetMapping
    public ResponseEntity<LocksResponse> list() {
        List<LockRow> rows = new ArrayList<>();
        try {
            Instant now = Instant.now();
            for (Document d : mongoTemplate.getCollection(collectionName).find()) {
                String name = d.getString("_id");
                Instant lockUntil = toInstant(d.get("lockUntil"));
                Instant lockedAt = toInstant(d.get("lockedAt"));
                String lockedBy = d.getString("lockedBy");
                boolean active = lockUntil != null && lockUntil.isAfter(now);
                rows.add(new LockRow(name, lockUntil, lockedAt, lockedBy, active));
            }
        } catch (RuntimeException e) {
            log.warn("scheduler locks list failed: {}", e.getMessage());
        }
        return ResponseEntity.ok(new LocksResponse(collectionName, Instant.now(), rows));
    }

    private static Instant toInstant(Object o) {
        if (o == null) return null;
        if (o instanceof Instant i) return i;
        if (o instanceof Date d) return d.toInstant();
        return null;
    }

    public record LockRow(
            String name,
            Instant lockUntil,
            Instant lockedAt,
            String lockedBy,
            boolean active) {}

    public record LocksResponse(
            String collection,
            Instant queriedAt,
            List<LockRow> locks) {}
}
