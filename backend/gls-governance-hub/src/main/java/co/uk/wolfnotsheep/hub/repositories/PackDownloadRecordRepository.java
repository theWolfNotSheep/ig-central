package co.uk.wolfnotsheep.hub.repositories;

import co.uk.wolfnotsheep.hub.models.PackDownloadRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PackDownloadRecordRepository extends MongoRepository<PackDownloadRecord, String> {

    Page<PackDownloadRecord> findByPackIdOrderByDownloadedAtDesc(String packId, Pageable pageable);
}
