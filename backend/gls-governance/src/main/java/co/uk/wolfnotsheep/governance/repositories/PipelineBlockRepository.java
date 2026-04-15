package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.models.PipelineBlock.BlockType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineBlockRepository extends MongoRepository<PipelineBlock, String> {

    List<PipelineBlock> findByActiveTrueOrderByNameAsc();

    List<PipelineBlock> findByTypeAndActiveTrueOrderByNameAsc(BlockType type);

    Optional<PipelineBlock> findByName(String name);

    long countByTypeAndActiveTrue(BlockType type);
}
