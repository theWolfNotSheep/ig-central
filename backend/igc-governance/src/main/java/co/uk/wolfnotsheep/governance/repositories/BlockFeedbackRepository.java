package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.BlockFeedback;
import co.uk.wolfnotsheep.governance.models.BlockFeedback.FeedbackType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BlockFeedbackRepository extends MongoRepository<BlockFeedback, String> {

    List<BlockFeedback> findByBlockIdOrderByTimestampDesc(String blockId);

    Page<BlockFeedback> findByBlockIdOrderByTimestampDesc(String blockId, Pageable pageable);

    List<BlockFeedback> findByBlockIdAndBlockVersion(String blockId, int blockVersion);

    List<BlockFeedback> findByBlockIdAndType(String blockId, FeedbackType type);

    long countByBlockId(String blockId);

    long countByBlockIdAndBlockVersion(String blockId, int blockVersion);
}
