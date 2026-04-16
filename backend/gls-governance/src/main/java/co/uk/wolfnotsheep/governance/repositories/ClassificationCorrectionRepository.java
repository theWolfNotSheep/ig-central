package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.ClassificationCorrection;
import co.uk.wolfnotsheep.governance.models.ClassificationCorrection.CorrectionType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ClassificationCorrectionRepository extends MongoRepository<ClassificationCorrection, String> {

    List<ClassificationCorrection> findByDocumentId(String documentId);

    List<ClassificationCorrection> findByCorrectedCategoryIdOrderByCorrectedAtDesc(String categoryId);

    List<ClassificationCorrection> findByOriginalCategoryIdOrderByCorrectedAtDesc(String categoryId);

    List<ClassificationCorrection> findByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType type);

    List<ClassificationCorrection> findTop50ByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType type);

    List<ClassificationCorrection> findByMimeTypeOrderByCorrectedAtDesc(String mimeType);

    List<ClassificationCorrection> findTop50ByOriginalCategoryIdOrderByCorrectedAtDesc(String categoryId);

    List<ClassificationCorrection> findTop50ByMimeTypeOrderByCorrectedAtDesc(String mimeType);

    List<ClassificationCorrection> findTop20ByOrderByCorrectedAtDesc();

    long countByCorrectionType(CorrectionType type);
}
