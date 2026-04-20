package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.TrainingDataSample;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TrainingDataSampleRepository extends MongoRepository<TrainingDataSample, String> {

    Page<TrainingDataSample> findBySource(String source, Pageable pageable);

    Page<TrainingDataSample> findByCategoryId(String categoryId, Pageable pageable);

    Page<TrainingDataSample> findByVerifiedTrue(Pageable pageable);

    Page<TrainingDataSample> findBySourceAndCategoryId(String source, String categoryId, Pageable pageable);

    List<TrainingDataSample> findByCategoryId(String categoryId);

    boolean existsBySourceDocumentId(String sourceDocumentId);

    Optional<TrainingDataSample> findBySourceDocumentId(String sourceDocumentId);

    long countByCategoryName(String categoryName);

    long countByVerifiedTrue();

    long countBySource(String source);

    boolean existsByText(String text);
}
