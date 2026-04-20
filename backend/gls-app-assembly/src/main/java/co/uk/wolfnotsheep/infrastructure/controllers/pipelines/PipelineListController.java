package co.uk.wolfnotsheep.infrastructure.controllers.pipelines;

import co.uk.wolfnotsheep.governance.repositories.PipelineDefinitionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pipelines")
public class PipelineListController {

    private final PipelineDefinitionRepository pipelineRepo;

    public PipelineListController(PipelineDefinitionRepository pipelineRepo) {
        this.pipelineRepo = pipelineRepo;
    }

    @GetMapping("/available")
    public ResponseEntity<List<Map<String, Object>>> available() {
        List<Map<String, Object>> result = pipelineRepo.findByActiveTrue().stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "name", p.getName(),
                        "description", p.getDescription() != null ? p.getDescription() : "",
                        "isDefault", p.isDefault()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }
}
