package co.uk.wolfnotsheep.document.repositories;

import co.uk.wolfnotsheep.document.models.FolderModel;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FolderRepository extends MongoRepository<FolderModel, String> {

    List<FolderModel> findByParentIdIsNullAndCreatedByOrderByNameAsc(String createdBy);

    List<FolderModel> findByParentIdOrderByNameAsc(String parentId);

    long countByParentId(String parentId);
}
