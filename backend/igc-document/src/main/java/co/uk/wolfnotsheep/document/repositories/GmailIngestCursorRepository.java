package co.uk.wolfnotsheep.document.repositories;

import co.uk.wolfnotsheep.document.models.GmailIngestCursor;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface GmailIngestCursorRepository extends MongoRepository<GmailIngestCursor, String> {

    Optional<GmailIngestCursor> findByConnectedDriveIdAndQuery(String connectedDriveId, String query);
}
