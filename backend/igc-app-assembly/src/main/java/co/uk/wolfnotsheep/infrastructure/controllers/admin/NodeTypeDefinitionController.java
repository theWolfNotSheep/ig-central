package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.governance.models.NodeTypeDefinition;
import co.uk.wolfnotsheep.governance.services.NodeTypeDefinitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin endpoints for managing pipeline node type definitions.
 * GET is used by both the pipeline editor (palette, config forms) and the admin UI.
 * POST/PUT are for creating and editing custom node types.
 */
@RestController
@RequestMapping("/api/admin/node-types")
public class NodeTypeDefinitionController {

    private final NodeTypeDefinitionService service;

    public NodeTypeDefinitionController(NodeTypeDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public List<NodeTypeDefinition> listActive() {
        return service.getActive();
    }

    @GetMapping("/all")
    public List<NodeTypeDefinition> listAll() {
        return service.getAll();
    }

    @GetMapping("/{key}")
    public ResponseEntity<NodeTypeDefinition> getByKey(@PathVariable String key) {
        return service.getByKey(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<NodeTypeDefinition> create(@RequestBody NodeTypeDefinition definition) {
        if (definition.getKey() == null || definition.getKey().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (service.getByKey(definition.getKey()).isPresent()) {
            return ResponseEntity.status(409).build(); // Conflict — key already exists
        }
        definition.setBuiltIn(false);
        definition.setActive(true);
        return ResponseEntity.ok(service.save(definition));
    }

    @PutMapping("/{key}")
    public ResponseEntity<NodeTypeDefinition> update(@PathVariable String key,
                                                      @RequestBody NodeTypeDefinition definition) {
        return service.getByKey(key)
                .map(existing -> {
                    definition.setId(existing.getId());
                    definition.setKey(key);
                    definition.setBuiltIn(existing.isBuiltIn());
                    definition.setCreatedAt(existing.getCreatedAt());
                    return ResponseEntity.ok(service.save(definition));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        return service.getByKey(key)
                .map(existing -> {
                    if (existing.isBuiltIn()) {
                        return ResponseEntity.status(403).<Void>build();
                    }
                    service.delete(existing.getId());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh() {
        service.refresh();
        return ResponseEntity.ok().build();
    }
}
