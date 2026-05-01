package co.uk.wolfnotsheep.auditcollector.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Mongo-backed {@link Tier2Store}. Default backend per
 * {@code igc.audit.collector.tier2-backend=mongo} (or absent) —
 * matches the PR2 behaviour exactly so the cutover to ES (PR3) is
 * a configuration flip, not a logic change.
 */
@Service
@ConditionalOnProperty(name = "igc.audit.collector.tier2-backend", havingValue = "mongo", matchIfMissing = true)
public class MongoTier2Store implements Tier2Store {

    private final Tier2Repository repo;
    private final MongoTemplate mongoTemplate;

    public MongoTier2Store(Tier2Repository repo, MongoTemplate mongoTemplate) {
        this.repo = repo;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(StoredTier2Event event) {
        try {
            repo.insert(event);
        } catch (DuplicateKeyException e) {
            // Idempotent — swallow.
        }
    }

    @Override
    public Optional<StoredTier2Event> findById(String eventId) {
        return repo.findById(eventId);
    }

    @Override
    public List<StoredTier2Event> search(SearchCriteria criteria, int pageIndex, int pageSize) {
        Query query = new Query();
        if (criteria.documentId() != null) {
            query.addCriteria(Criteria.where("documentId").is(criteria.documentId()));
        }
        if (criteria.eventType() != null) {
            query.addCriteria(Criteria.where("eventType").is(criteria.eventType()));
        }
        if (criteria.actorService() != null) {
            query.addCriteria(Criteria.where("actorService").is(criteria.actorService()));
        }
        if (criteria.fromInclusive() != null || criteria.toExclusive() != null) {
            Criteria ts = Criteria.where("timestamp");
            if (criteria.fromInclusive() != null) ts = ts.gte(criteria.fromInclusive());
            if (criteria.toExclusive() != null) ts = ts.lt(criteria.toExclusive());
            query.addCriteria(ts);
        }
        query.with(PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "timestamp")));
        return mongoTemplate.find(query, StoredTier2Event.class);
    }
}
