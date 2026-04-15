package co.uk.wolfnotsheep.infrastructure.controllers.pii;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.PiiEntity;
import co.uk.wolfnotsheep.document.models.SubjectAccessRequest;
import co.uk.wolfnotsheep.document.models.SubjectAccessRequest.SarNote;
import co.uk.wolfnotsheep.document.repositories.SubjectAccessRequestRepository;
import co.uk.wolfnotsheep.document.services.PiiRedactionService;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pii")
public class PiiSearchController {

    private static final Logger log = LoggerFactory.getLogger(PiiSearchController.class);

    private final MongoTemplate mongoTemplate;
    private final SubjectAccessRequestRepository sarRepo;
    private final PiiRedactionService piiRedactionService;
    private final ObjectMapper objectMapper;

    public PiiSearchController(MongoTemplate mongoTemplate,
                               SubjectAccessRequestRepository sarRepo,
                               PiiRedactionService piiRedactionService,
                               ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.sarRepo = sarRepo;
        this.piiRedactionService = piiRedactionService;
        this.objectMapper = objectMapper;
    }

    // ── PII Search ───────────────────────────────────

    /**
     * Search for documents containing specific PII.
     * Searches: piiFindings.matchedText, extractedText, extractedMetadata values
     */
    @PostMapping("/search")
    public ResponseEntity<List<PiiSearchResult>> searchPii(@RequestBody PiiSearchRequest request) {
        List<String> terms = request.searchTerms();
        if (terms == null || terms.isEmpty()) return ResponseEntity.ok(List.of());

        List<Criteria> orCriteria = new ArrayList<>();
        for (String term : terms) {
            String pattern = ".*" + java.util.regex.Pattern.quote(term.trim()) + ".*";
            orCriteria.add(Criteria.where("piiFindings.matchedText").regex(pattern, "i"));
            orCriteria.add(Criteria.where("extractedText").regex(pattern, "i"));
        }

        Query query = Query.query(new Criteria().orOperator(orCriteria.toArray(new Criteria[0])));
        query.limit(200);

        List<DocumentModel> docs = mongoTemplate.find(query, DocumentModel.class);

        List<PiiSearchResult> results = docs.stream().map(doc -> {
            // Find which PII findings match the search terms
            List<PiiMatch> matches = new ArrayList<>();
            if (doc.getPiiFindings() != null) {
                for (PiiEntity pii : doc.getPiiFindings()) {
                    for (String term : terms) {
                        if (pii.getMatchedText() != null &&
                                pii.getMatchedText().toLowerCase().contains(term.toLowerCase())) {
                            matches.add(new PiiMatch(pii.getType(), pii.getRedactedText(),
                                    pii.getConfidence(), pii.isDismissed()));
                        }
                    }
                }
            }

            // Also check extracted metadata
            List<String> metadataMatches = new ArrayList<>();
            if (doc.getExtractedMetadata() != null) {
                for (var entry : doc.getExtractedMetadata().entrySet()) {
                    for (String term : terms) {
                        if (entry.getValue() != null &&
                                entry.getValue().toLowerCase().contains(term.toLowerCase())) {
                            metadataMatches.add(entry.getKey() + ": " + entry.getValue());
                        }
                    }
                }
            }

            // Count text occurrences
            int textOccurrences = 0;
            if (doc.getExtractedText() != null) {
                for (String term : terms) {
                    String lower = doc.getExtractedText().toLowerCase();
                    String termLower = term.toLowerCase();
                    int idx = 0;
                    while ((idx = lower.indexOf(termLower, idx)) >= 0) {
                        textOccurrences++;
                        idx += termLower.length();
                    }
                }
            }

            return new PiiSearchResult(
                    doc.getId(), doc.getSlug(), doc.getOriginalFileName(),
                    doc.getCategoryName(), doc.getSensitivityLabel() != null ? doc.getSensitivityLabel().name() : null,
                    doc.getPiiStatus(), doc.getStatus() != null ? doc.getStatus().name() : null,
                    matches, metadataMatches, textOccurrences
            );
        }).toList();

        return ResponseEntity.ok(results);
    }

    /**
     * Get PII summary across all documents — types and counts.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getPiiSummary() {
        long totalDocuments = mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(), DocumentModel.class);

        // PII status counts via aggregation
        Map<String, Long> byStatus = new LinkedHashMap<>();
        try {
            var statusPipe = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                    org.springframework.data.mongodb.core.aggregation.Aggregation.group("piiStatus").count().as("count"));
            mongoTemplate.aggregate(statusPipe, "documents", org.bson.Document.class)
                    .getMappedResults().forEach(d -> byStatus.put(
                            d.getString("_id") != null ? d.getString("_id") : "UNKNOWN",
                            d.get("count", Number.class).longValue()));
        } catch (Exception e) { log.error("PII status aggregation failed: {}", e.getMessage()); }

        // PII type counts via aggregation — unwind piiFindings, filter dismissed, group by type
        Map<String, Long> byType = new LinkedHashMap<>();
        long totalFindings = 0;
        long docsWithPii = 0;
        try {
            var typePipe = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                    org.springframework.data.mongodb.core.aggregation.Aggregation.match(
                            org.springframework.data.mongodb.core.query.Criteria.where("piiFindings").ne(null).and("piiFindings").not().size(0)),
                    org.springframework.data.mongodb.core.aggregation.Aggregation.unwind("piiFindings"),
                    org.springframework.data.mongodb.core.aggregation.Aggregation.match(
                            org.springframework.data.mongodb.core.query.Criteria.where("piiFindings.dismissed").ne(true)),
                    org.springframework.data.mongodb.core.aggregation.Aggregation.group("piiFindings.type").count().as("count"));
            var results = mongoTemplate.aggregate(typePipe, "documents", org.bson.Document.class).getMappedResults();
            for (var d : results) {
                long count = d.get("count", Number.class).longValue();
                byType.put(d.getString("_id"), count);
                totalFindings += count;
            }

            // Count distinct documents with PII
            docsWithPii = mongoTemplate.count(
                    org.springframework.data.mongodb.core.query.Query.query(
                            org.springframework.data.mongodb.core.query.Criteria.where("piiStatus").is("DETECTED")),
                    DocumentModel.class);
        } catch (Exception e) { log.error("PII type aggregation failed: {}", e.getMessage()); }

        return ResponseEntity.ok(Map.of(
                "totalDocuments", totalDocuments,
                "documentsWithPii", docsWithPii,
                "totalFindings", totalFindings,
                "byType", byType,
                "byStatus", byStatus
        ));
    }

    // ── Subject Access Requests ──────────────────────

    @GetMapping("/sar")
    public ResponseEntity<List<SubjectAccessRequest>> listSars() {
        return ResponseEntity.ok(sarRepo.findByStatusNotOrderByDeadlineAsc("COMPLETED"));
    }

    @GetMapping("/sar/all")
    public ResponseEntity<List<SubjectAccessRequest>> listAllSars() {
        return ResponseEntity.ok(sarRepo.findAll());
    }

    @GetMapping("/sar/{id}")
    public ResponseEntity<SubjectAccessRequest> getSar(@PathVariable String id) {
        return sarRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/sar")
    public ResponseEntity<SubjectAccessRequest> createSar(
            @RequestBody CreateSarRequest request, Authentication auth) {
        SubjectAccessRequest sar = new SubjectAccessRequest();
        sar.setReference(generateReference());
        sar.setDataSubjectName(request.dataSubjectName());
        sar.setDataSubjectEmail(request.dataSubjectEmail());
        sar.setSearchTerms(request.searchTerms());
        sar.setRequestedBy(auth != null ? auth.getName() : "SYSTEM");
        sar.setRequestDate(Instant.now());
        sar.setJurisdiction(request.jurisdiction() != null ? request.jurisdiction() : "UK_GDPR");

        // Set deadline based on jurisdiction
        int deadlineDays = switch (sar.getJurisdiction()) {
            case "CCPA" -> 45;
            case "HIPAA" -> 30;
            default -> 30; // UK GDPR
        };
        sar.setDeadline(Instant.now().plus(deadlineDays, ChronoUnit.DAYS));
        sar.setStatus("RECEIVED");
        sar.setNotes(new ArrayList<>());

        // Auto-search
        sar.setStatus("SEARCHING");
        List<String> matchedIds = autoSearch(sar.getSearchTerms());
        sar.setMatchedDocumentIds(matchedIds);
        sar.setTotalMatches(matchedIds.size());
        sar.setStatus(matchedIds.isEmpty() ? "COMPLETED" : "REVIEWING");

        if (matchedIds.isEmpty()) {
            sar.setCompletedAt(Instant.now());
            sar.getNotes().add(new SarNote("Auto-search found no matching documents",
                    "SYSTEM", Instant.now()));
        } else {
            sar.getNotes().add(new SarNote("Auto-search found " + matchedIds.size() + " document(s)",
                    "SYSTEM", Instant.now()));
        }

        SubjectAccessRequest saved = sarRepo.save(sar);
        log.info("SAR {} created — {} matches found for '{}'",
                saved.getReference(), matchedIds.size(), sar.getDataSubjectName());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/sar/{id}/status")
    public ResponseEntity<SubjectAccessRequest> updateSarStatus(
            @PathVariable String id, @RequestBody Map<String, String> body, Authentication auth) {
        return sarRepo.findById(id).map(sar -> {
            String newStatus = body.get("status");
            sar.setStatus(newStatus);
            if ("COMPLETED".equals(newStatus)) sar.setCompletedAt(Instant.now());
            if (body.get("note") != null) {
                var notes = sar.getNotes() != null ? new ArrayList<>(sar.getNotes()) : new ArrayList<SarNote>();
                notes.add(new SarNote(body.get("note"), auth != null ? auth.getName() : "ADMIN", Instant.now()));
                sar.setNotes(notes);
            }
            return ResponseEntity.ok(sarRepo.save(sar));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/sar/{id}/assign")
    public ResponseEntity<SubjectAccessRequest> assignSar(
            @PathVariable String id, @RequestBody Map<String, String> body) {
        return sarRepo.findById(id).map(sar -> {
            sar.setAssignedTo(body.get("assignedTo"));
            return ResponseEntity.ok(sarRepo.save(sar));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Export a SAR as a compiled report. Returns a JSON manifest listing all matched documents,
     * their PII findings, and redacted text for each. Updates SAR status to COMPILING then COMPLETED.
     */
    @GetMapping("/sar/{id}/export")
    public ResponseEntity<byte[]> exportSar(@PathVariable String id, Authentication auth) {
        Optional<SubjectAccessRequest> opt = sarRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        SubjectAccessRequest sar = opt.get();

        // Update status to COMPILING
        sar.setStatus("COMPILING");
        sarRepo.save(sar);

        List<String> docIds = sar.getMatchedDocumentIds();
        if (docIds == null || docIds.isEmpty()) {
            sar.setStatus("COMPLETED");
            sar.setCompletedAt(Instant.now());
            var notes = sar.getNotes() != null ? new ArrayList<>(sar.getNotes()) : new ArrayList<SarNote>();
            notes.add(new SarNote("Export completed — no matched documents", auth != null ? auth.getName() : "SYSTEM", Instant.now()));
            sar.setNotes(notes);
            sarRepo.save(sar);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("[]".getBytes(StandardCharsets.UTF_8));
        }

        // Build export for each matched document
        List<Map<String, Object>> exportDocs = new ArrayList<>();
        for (String docId : docIds) {
            Query q = Query.query(Criteria.where("_id").is(docId));
            DocumentModel doc = mongoTemplate.findOne(q, DocumentModel.class);
            if (doc == null) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("documentId", doc.getId());
            entry.put("fileName", doc.getOriginalFileName());
            entry.put("mimeType", doc.getMimeType());
            entry.put("categoryName", doc.getCategoryName());
            entry.put("sensitivityLabel", doc.getSensitivityLabel() != null ? doc.getSensitivityLabel().name() : null);
            entry.put("piiStatus", doc.getPiiStatus());

            // PII findings with redacted values only (no raw matched text)
            List<Map<String, Object>> findings = new ArrayList<>();
            if (doc.getPiiFindings() != null) {
                for (PiiEntity pii : doc.getPiiFindings()) {
                    if (pii.isDismissed()) continue;
                    findings.add(Map.of(
                            "type", pii.getType(),
                            "redactedText", pii.getRedactedText() != null ? pii.getRedactedText() : "",
                            "confidence", pii.getConfidence(),
                            "verified", pii.isVerified()
                    ));
                }
            }
            entry.put("piiFindings", findings);

            // Redacted text
            String redactedText = piiRedactionService.redactText(doc);
            // Only include relevant excerpts with search term context
            List<String> excerpts = new ArrayList<>();
            if (redactedText != null && sar.getSearchTerms() != null) {
                for (String term : sar.getSearchTerms()) {
                    String lower = redactedText.toLowerCase();
                    String termLower = term.toLowerCase();
                    int idx = 0;
                    while ((idx = lower.indexOf(termLower, idx)) >= 0) {
                        int start = Math.max(0, idx - 200);
                        int end = Math.min(redactedText.length(), idx + term.length() + 200);
                        excerpts.add("..." + redactedText.substring(start, end) + "...");
                        idx += termLower.length();
                        if (excerpts.size() >= 20) break;
                    }
                    if (excerpts.size() >= 20) break;
                }
            }
            entry.put("relevantExcerpts", excerpts);

            // Extracted metadata
            entry.put("extractedMetadata", doc.getExtractedMetadata());

            exportDocs.add(entry);
        }

        // Mark SAR as completed
        sar.setStatus("COMPLETED");
        sar.setCompletedAt(Instant.now());
        var notes = sar.getNotes() != null ? new ArrayList<>(sar.getNotes()) : new ArrayList<SarNote>();
        notes.add(new SarNote("Export compiled: " + exportDocs.size() + " documents",
                auth != null ? auth.getName() : "SYSTEM", Instant.now()));
        sar.setNotes(notes);
        sarRepo.save(sar);

        // Serialize to JSON
        try {
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of(
                    "sarReference", sar.getReference(),
                    "dataSubjectName", sar.getDataSubjectName(),
                    "dataSubjectEmail", sar.getDataSubjectEmail() != null ? sar.getDataSubjectEmail() : "",
                    "jurisdiction", sar.getJurisdiction(),
                    "searchTerms", sar.getSearchTerms(),
                    "exportedAt", Instant.now().toString(),
                    "totalDocuments", exportDocs.size(),
                    "documents", exportDocs
            ));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + sar.getReference() + "-export.json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json);
        } catch (Exception e) {
            log.error("Failed to serialize SAR export: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Helpers ──────────────────────────────────────

    private List<String> autoSearch(List<String> searchTerms) {
        if (searchTerms == null || searchTerms.isEmpty()) return List.of();

        List<Criteria> orCriteria = new ArrayList<>();
        for (String term : searchTerms) {
            String pattern = ".*" + java.util.regex.Pattern.quote(term.trim()) + ".*";
            orCriteria.add(Criteria.where("piiFindings.matchedText").regex(pattern, "i"));
            orCriteria.add(Criteria.where("extractedText").regex(pattern, "i"));
            orCriteria.add(Criteria.where("uploadedBy").regex(pattern, "i"));
        }

        Query query = Query.query(new Criteria().orOperator(orCriteria.toArray(new Criteria[0])));
        return mongoTemplate.find(query, DocumentModel.class).stream()
                .map(DocumentModel::getId).toList();
    }

    private String generateReference() {
        long count = sarRepo.count() + 1;
        return "SAR-" + java.time.Year.now().getValue() + "-" + String.format("%03d", count);
    }

    // ── DTOs ─────────────────────────────────────────

    record PiiSearchRequest(List<String> searchTerms) {}

    record PiiSearchResult(
            String documentId, String slug, String fileName,
            String categoryName, String sensitivityLabel,
            String piiStatus, String documentStatus,
            List<PiiMatch> piiMatches, List<String> metadataMatches, int textOccurrences
    ) {}

    record PiiMatch(String type, String redactedText, double confidence, boolean dismissed) {}

    record CreateSarRequest(String dataSubjectName, String dataSubjectEmail,
                            List<String> searchTerms, String jurisdiction) {}
}
