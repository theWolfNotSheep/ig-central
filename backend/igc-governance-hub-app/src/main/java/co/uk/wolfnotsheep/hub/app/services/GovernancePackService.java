package co.uk.wolfnotsheep.hub.app.services;

import co.uk.wolfnotsheep.hub.models.GovernancePack;
import co.uk.wolfnotsheep.hub.models.GovernancePack.PackStatus;
import co.uk.wolfnotsheep.hub.repositories.GovernancePackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class GovernancePackService {

    private static final Logger log = LoggerFactory.getLogger(GovernancePackService.class);

    private final GovernancePackRepository packRepository;
    private final MongoTemplate mongoTemplate;

    public GovernancePackService(GovernancePackRepository packRepository, MongoTemplate mongoTemplate) {
        this.packRepository = packRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<GovernancePack> listAll() {
        return packRepository.findAll();
    }

    public GovernancePack getPackById(String id) {
        return packRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pack not found: " + id));
    }

    public Page<GovernancePack> search(String query, String jurisdiction, String industry,
                                       String regulation, String tag, Boolean featured,
                                       Pageable pageable) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("status").is(PackStatus.PUBLISHED));

        if (query != null && !query.isBlank()) {
            Criteria textCriteria = new Criteria().orOperator(
                    Criteria.where("name").regex(query, "i"),
                    Criteria.where("description").regex(query, "i"),
                    Criteria.where("tags").regex(query, "i")
            );
            criteriaList.add(textCriteria);
        }

        if (jurisdiction != null && !jurisdiction.isBlank()) {
            criteriaList.add(Criteria.where("jurisdiction").is(jurisdiction));
        }

        if (industry != null && !industry.isBlank()) {
            criteriaList.add(Criteria.where("industries").is(industry));
        }

        if (regulation != null && !regulation.isBlank()) {
            criteriaList.add(Criteria.where("regulations").is(regulation));
        }

        if (tag != null && !tag.isBlank()) {
            criteriaList.add(Criteria.where("tags").is(tag));
        }

        if (featured != null && featured) {
            criteriaList.add(Criteria.where("featured").is(true));
        }

        Query mongoQuery = new Query();
        if (!criteriaList.isEmpty()) {
            mongoQuery.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(mongoQuery, GovernancePack.class);

        mongoQuery.with(pageable);
        List<GovernancePack> results = mongoTemplate.find(mongoQuery, GovernancePack.class);

        return new PageImpl<>(results, pageable, total);
    }

    public GovernancePack getPackDetail(String slug) {
        return packRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Pack not found with slug: " + slug));
    }

    public GovernancePack createPack(GovernancePack pack) {
        if (pack.getName() == null || pack.getName().isBlank()) {
            throw new IllegalArgumentException("Pack name is required");
        }

        String slug = generateSlug(pack.getName());
        pack.setSlug(slug);
        pack.setStatus(PackStatus.DRAFT);
        pack.setLatestVersionNumber(0);
        pack.setDownloadCount(0);
        pack.setAverageRating(0.0);
        pack.setReviewCount(0);
        pack.setCreatedAt(Instant.now());
        pack.setUpdatedAt(Instant.now());

        log.info("Creating governance pack: {} (slug: {})", pack.getName(), slug);
        return packRepository.save(pack);
    }

    public GovernancePack updatePack(String id, GovernancePack updates) {
        GovernancePack existing = packRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pack not found with id: " + id));

        if (updates.getName() != null && !updates.getName().isBlank()) {
            existing.setName(updates.getName());
        }
        if (updates.getDescription() != null) {
            existing.setDescription(updates.getDescription());
        }
        if (updates.getAuthor() != null) {
            existing.setAuthor(updates.getAuthor());
        }
        if (updates.getJurisdiction() != null) {
            existing.setJurisdiction(updates.getJurisdiction());
        }
        if (updates.getIndustries() != null) {
            existing.setIndustries(updates.getIndustries());
        }
        if (updates.getRegulations() != null) {
            existing.setRegulations(updates.getRegulations());
        }
        if (updates.getTags() != null) {
            existing.setTags(updates.getTags());
        }
        existing.setFeatured(updates.isFeatured());
        existing.setUpdatedAt(Instant.now());

        log.info("Updating governance pack: {} (id: {})", existing.getName(), id);
        return packRepository.save(existing);
    }

    public GovernancePack deprecatePack(String id) {
        GovernancePack pack = packRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pack not found with id: " + id));

        pack.setStatus(PackStatus.DEPRECATED);
        pack.setUpdatedAt(Instant.now());

        log.info("Deprecating governance pack: {} (id: {})", pack.getName(), id);
        return packRepository.save(pack);
    }

    public List<String> getDistinctJurisdictions() {
        return mongoTemplate.query(GovernancePack.class)
                .distinct("jurisdiction")
                .as(String.class)
                .all();
    }

    public List<String> getDistinctIndustries() {
        return mongoTemplate.query(GovernancePack.class)
                .distinct("industries")
                .as(String.class)
                .all();
    }

    public List<String> getDistinctRegulations() {
        return mongoTemplate.query(GovernancePack.class)
                .distinct("regulations")
                .as(String.class)
                .all();
    }

    private String generateSlug(String name) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        String suffix = Long.toHexString(System.nanoTime()).substring(0, 6);
        return base + "-" + suffix;
    }
}
