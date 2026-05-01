package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface DocumentClassificationResultRepository extends MongoRepository<DocumentClassificationResult, String> {

    List<DocumentClassificationResult> findByDocumentIdOrderByClassifiedAtDesc(String documentId);

    List<DocumentClassificationResult> findByHumanReviewedFalseAndConfidenceLessThan(double threshold);

    List<DocumentClassificationResult> findByClassifiedAtBetween(Instant from, Instant to);

    List<DocumentClassificationResult> findByClassifiedAtAfterOrderByClassifiedAtAsc(Instant after);
}
