package co.uk.wolfnotsheep.router.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);

    private final IdempotencyRepository repo;
    private final Duration ttl;

    public IdempotencyStore(
            IdempotencyRepository repo,
            @Value("${gls.router.idempotency.ttl:PT24H}") Duration ttl) {
        this.repo = repo;
        this.ttl = ttl;
    }

    public IdempotencyOutcome tryAcquire(String nodeRunId) {
        Optional<IdempotencyRecord> existing = repo.findById(nodeRunId);
        if (existing.isPresent()) {
            IdempotencyRecord row = existing.get();
            return row.isCompleted()
                    ? IdempotencyOutcome.cached(row.responseJson())
                    : IdempotencyOutcome.inFlight();
        }
        Instant now = Instant.now();
        IdempotencyRecord row = new IdempotencyRecord(nodeRunId, now, null, null, now.plus(ttl));
        try {
            repo.insert(row);
            return IdempotencyOutcome.acquired();
        } catch (DuplicateKeyException raceLost) {
            log.debug("idempotency: race lost on {}, re-reading", nodeRunId);
            return repo.findById(nodeRunId)
                    .map(r -> r.isCompleted()
                            ? IdempotencyOutcome.cached(r.responseJson())
                            : IdempotencyOutcome.inFlight())
                    .orElse(IdempotencyOutcome.inFlight());
        }
    }

    public void cacheResult(String nodeRunId, String responseJson) {
        repo.findById(nodeRunId).ifPresent(row ->
                repo.save(row.withResult(responseJson, Instant.now())));
    }

    public void releaseOnFailure(String nodeRunId) {
        repo.deleteById(nodeRunId);
    }
}
