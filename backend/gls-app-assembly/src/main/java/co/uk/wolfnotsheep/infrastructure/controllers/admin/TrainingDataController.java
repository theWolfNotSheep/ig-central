package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.governance.models.TrainingDataSample;
import co.uk.wolfnotsheep.governance.repositories.TrainingDataSampleRepository;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/bert/training-samples")
public class TrainingDataController {

    private static final Logger log = LoggerFactory.getLogger(TrainingDataController.class);

    private final TrainingDataSampleRepository sampleRepo;
    private final AppConfigService configService;

    public TrainingDataController(TrainingDataSampleRepository sampleRepo,
                                   AppConfigService configService) {
        this.sampleRepo = sampleRepo;
        this.configService = configService;
    }

    // ── List / Search ────────────────────────────────────

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Boolean verified) {

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TrainingDataSample> result;
        if (source != null && categoryId != null) {
            result = sampleRepo.findBySourceAndCategoryId(source, categoryId, pageable);
        } else if (source != null) {
            result = sampleRepo.findBySource(source, pageable);
        } else if (categoryId != null) {
            result = sampleRepo.findByCategoryId(categoryId, pageable);
        } else if (verified != null && verified) {
            result = sampleRepo.findByVerifiedTrue(pageable);
        } else {
            result = sampleRepo.findAll(pageable);
        }

        return ResponseEntity.ok(Map.of(
                "samples", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber(),
                "size", result.getSize()
        ));
    }

    // ── Stats ────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        long total = sampleRepo.count();
        long verified = sampleRepo.countByVerifiedTrue();
        long manual = sampleRepo.countBySource("MANUAL_UPLOAD");
        long auto = sampleRepo.countBySource("AUTO_COLLECTED");
        long bulk = sampleRepo.countBySource("BULK_IMPORT");

        // Category distribution
        var allSamples = sampleRepo.findAll();
        var categories = allSamples.stream()
                .filter(s -> s.getCategoryName() != null)
                .collect(Collectors.groupingBy(TrainingDataSample::getCategoryName, Collectors.counting()));

        return ResponseEntity.ok(Map.of(
                "total", total,
                "verified", verified,
                "manual", manual,
                "autoCollected", auto,
                "bulkImport", bulk,
                "categoryCount", categories.size(),
                "categories", categories
        ));
    }

    // ── Create (manual text entry) ───────────────────────

    @PostMapping
    public ResponseEntity<TrainingDataSample> create(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        String categoryId = body.get("categoryId");
        String categoryName = body.get("categoryName");
        String sensitivityLabel = body.getOrDefault("sensitivityLabel", "INTERNAL");

        if (text == null || text.isBlank() || categoryName == null || categoryName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        int maxLen = configService.getValue("bert.training.max_text_length", 2000);
        if (text.length() > maxLen) text = text.substring(0, maxLen);

        var sample = new TrainingDataSample();
        sample.setText(text);
        sample.setCategoryId(categoryId);
        sample.setCategoryName(categoryName);
        sample.setSensitivityLabel(sensitivityLabel);
        sample.setSource("MANUAL_UPLOAD");
        sample.setVerified(true); // manually entered = verified
        sample.setCreatedAt(Instant.now());
        sample.setUpdatedAt(Instant.now());

        return ResponseEntity.ok(sampleRepo.save(sample));
    }

    // ── Upload file — extract text with Tika ─────────────

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TrainingDataSample> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("categoryId") String categoryId,
            @RequestParam("categoryName") String categoryName,
            @RequestParam(value = "sensitivityLabel", defaultValue = "INTERNAL") String sensitivityLabel) {

        if (file.isEmpty() || categoryName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // Extract text with Tika
            var handler = new BodyContentHandler(-1);
            var metadata = new Metadata();
            new AutoDetectParser().parse(file.getInputStream(), handler, metadata);
            String text = handler.toString().trim();

            if (text.isBlank()) {
                return ResponseEntity.ok(createSample(
                        "[No text extracted]", categoryId, categoryName, sensitivityLabel,
                        "MANUAL_UPLOAD", file.getOriginalFilename()));
            }

            int maxLen = configService.getValue("bert.training.max_text_length", 2000);
            if (text.length() > maxLen) text = text.substring(0, maxLen);

            return ResponseEntity.ok(createSample(
                    text, categoryId, categoryName, sensitivityLabel,
                    "MANUAL_UPLOAD", file.getOriginalFilename()));

        } catch (Exception e) {
            log.error("Failed to extract text from training file {}: {}", file.getOriginalFilename(), e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Update ───────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<TrainingDataSample> update(
            @PathVariable String id, @RequestBody Map<String, String> body) {
        return sampleRepo.findById(id).map(sample -> {
            if (body.containsKey("categoryId")) sample.setCategoryId(body.get("categoryId"));
            if (body.containsKey("categoryName")) sample.setCategoryName(body.get("categoryName"));
            if (body.containsKey("sensitivityLabel")) sample.setSensitivityLabel(body.get("sensitivityLabel"));
            if (body.containsKey("text")) {
                String text = body.get("text");
                int maxLen = configService.getValue("bert.training.max_text_length", 2000);
                sample.setText(text.length() > maxLen ? text.substring(0, maxLen) : text);
            }
            sample.setUpdatedAt(Instant.now());
            return ResponseEntity.ok(sampleRepo.save(sample));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Verify ───────────────────────────────────────────

    @PostMapping("/verify/{id}")
    public ResponseEntity<TrainingDataSample> verify(@PathVariable String id) {
        return sampleRepo.findById(id).map(sample -> {
            sample.setVerified(true);
            sample.setUpdatedAt(Instant.now());
            return ResponseEntity.ok(sampleRepo.save(sample));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Delete ───────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!sampleRepo.existsById(id)) return ResponseEntity.notFound().build();
        sampleRepo.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<Map<String, Object>> bulkDelete(@RequestBody Map<String, List<String>> body) {
        List<String> ids = body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "ids required"));
        sampleRepo.deleteAllById(ids);
        return ResponseEntity.ok(Map.of("deleted", ids.size()));
    }

    // ── Export ────────────────────────────────────────────

    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export() {
        var allSamples = sampleRepo.findAll();
        var lines = allSamples.stream()
                .filter(s -> s.getText() != null && !s.getText().isBlank())
                .map(s -> Map.of(
                        "text", s.getText(),
                        "label", s.getCategoryName() != null ? s.getCategoryName() : "Unknown",
                        "category_id", s.getCategoryId() != null ? s.getCategoryId() : "",
                        "sensitivity_label", s.getSensitivityLabel() != null ? s.getSensitivityLabel() : "INTERNAL",
                        "verified", s.isVerified(),
                        "source", s.getSource() != null ? s.getSource() : "UNKNOWN"
                ))
                .toList();

        // Category distribution
        var categories = allSamples.stream()
                .filter(s -> s.getCategoryName() != null)
                .collect(Collectors.groupingBy(TrainingDataSample::getCategoryName, Collectors.counting()));

        // Label map
        var labelMap = new LinkedHashMap<String, Map<String, String>>();
        int idx = 0;
        var seen = new LinkedHashSet<String>();
        for (var s : allSamples) {
            if (s.getCategoryId() != null && seen.add(s.getCategoryId())) {
                labelMap.put(String.valueOf(idx++), Map.of(
                        "category_id", s.getCategoryId(),
                        "category_name", s.getCategoryName() != null ? s.getCategoryName() : "",
                        "sensitivity_label", s.getSensitivityLabel() != null ? s.getSensitivityLabel() : "INTERNAL"
                ));
            }
        }

        return ResponseEntity.ok(Map.of(
                "totalSamples", lines.size(),
                "categoryCount", categories.size(),
                "categories", categories,
                "labelMap", labelMap,
                "samples", lines
        ));
    }

    // ── Training Config ──────────────────────────────────

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "autoCollectEnabled", configService.getValue("bert.training.auto_collect_enabled", false),
                "autoCollectMinConfidence", configService.getValue("bert.training.auto_collect_min_confidence", 0.8),
                "autoCollectCategories", configService.getValue("bert.training.auto_collect_categories", List.of()),
                "autoCollectCorrectedOnly", configService.getValue("bert.training.auto_collect_corrected_only", false),
                "maxTextLength", configService.getValue("bert.training.max_text_length", 2000)
        ));
    }

    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        if (body.containsKey("autoCollectEnabled"))
            configService.save("bert.training.auto_collect_enabled", "bert", body.get("autoCollectEnabled"), "Auto-collect training data from classified documents");
        if (body.containsKey("autoCollectMinConfidence"))
            configService.save("bert.training.auto_collect_min_confidence", "bert", body.get("autoCollectMinConfidence"), "Minimum confidence to auto-collect");
        if (body.containsKey("autoCollectCategories"))
            configService.save("bert.training.auto_collect_categories", "bert", body.get("autoCollectCategories"), "Category IDs to auto-collect from (empty = all)");
        if (body.containsKey("autoCollectCorrectedOnly"))
            configService.save("bert.training.auto_collect_corrected_only", "bert", body.get("autoCollectCorrectedOnly"), "Only collect human-corrected classifications");
        if (body.containsKey("maxTextLength"))
            configService.save("bert.training.max_text_length", "bert", body.get("maxTextLength"), "Maximum characters per training sample");

        return getConfig();
    }

    // ── Helpers ───────────────────────────────────────────

    private TrainingDataSample createSample(String text, String categoryId, String categoryName,
                                             String sensitivityLabel, String source, String fileName) {
        var sample = new TrainingDataSample();
        sample.setText(text);
        sample.setCategoryId(categoryId);
        sample.setCategoryName(categoryName);
        sample.setSensitivityLabel(sensitivityLabel);
        sample.setSource(source);
        sample.setVerified(false);
        sample.setFileName(fileName);
        sample.setCreatedAt(Instant.now());
        sample.setUpdatedAt(Instant.now());
        return sampleRepo.save(sample);
    }
}
