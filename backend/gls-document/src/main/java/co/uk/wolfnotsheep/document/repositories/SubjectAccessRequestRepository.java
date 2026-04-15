package co.uk.wolfnotsheep.document.repositories;

import co.uk.wolfnotsheep.document.models.SubjectAccessRequest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SubjectAccessRequestRepository extends MongoRepository<SubjectAccessRequest, String> {

    List<SubjectAccessRequest> findByStatusNotOrderByDeadlineAsc(String excludeStatus);

    List<SubjectAccessRequest> findByStatusOrderByDeadlineAsc(String status);

    long countByStatus(String status);
}
