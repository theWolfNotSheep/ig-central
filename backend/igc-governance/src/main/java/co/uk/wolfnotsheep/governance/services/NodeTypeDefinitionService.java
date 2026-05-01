package co.uk.wolfnotsheep.governance.services;

import co.uk.wolfnotsheep.governance.models.NodeTypeDefinition;
import co.uk.wolfnotsheep.governance.repositories.NodeTypeDefinitionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing pipeline node type definitions.
 * Uses a ConcurrentHashMap cache loaded on startup for fast lookups
 * during pipeline execution (same pattern as AppConfigService).
 */
@Service
public class NodeTypeDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(NodeTypeDefinitionService.class);

    private final NodeTypeDefinitionRepository repo;
    private final Map<String, NodeTypeDefinition> cache = new ConcurrentHashMap<>();

    public NodeTypeDefinitionService(NodeTypeDefinitionRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void loadAll() {
        cache.clear();
        repo.findAll().forEach(d -> cache.put(d.getKey(), d));
        log.info("Loaded {} node type definitions", cache.size());
    }

    public Optional<NodeTypeDefinition> getByKey(String key) {
        NodeTypeDefinition cached = cache.get(key);
        if (cached != null) return Optional.of(cached);
        // Fallback to DB (e.g. newly created custom type not yet in cache)
        Optional<NodeTypeDefinition> fromDb = repo.findByKey(key);
        fromDb.ifPresent(d -> cache.put(d.getKey(), d));
        return fromDb;
    }

    public List<NodeTypeDefinition> getActive() {
        return repo.findByActiveTrueOrderByCategoryAscSortOrderAsc();
    }

    public List<NodeTypeDefinition> getAll() {
        return repo.findAll();
    }

    public NodeTypeDefinition save(NodeTypeDefinition definition) {
        definition.setUpdatedAt(Instant.now());
        if (definition.getCreatedAt() == null) {
            definition.setCreatedAt(Instant.now());
        }
        NodeTypeDefinition saved = repo.save(definition);
        cache.put(saved.getKey(), saved);
        return saved;
    }

    public void delete(String id) {
        repo.findById(id).ifPresent(d -> {
            if (d.isBuiltIn()) {
                throw new IllegalArgumentException("Cannot delete built-in node type: " + d.getKey());
            }
            repo.deleteById(id);
            cache.remove(d.getKey());
        });
    }

    public void refresh() {
        loadAll();
    }
}
