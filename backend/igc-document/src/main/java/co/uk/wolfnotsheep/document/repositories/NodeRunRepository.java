package co.uk.wolfnotsheep.document.repositories;

import co.uk.wolfnotsheep.document.models.NodeRun;
import co.uk.wolfnotsheep.document.models.NodeRunStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NodeRunRepository extends MongoRepository<NodeRun, String> {

    List<NodeRun> findByPipelineRunIdOrderByStartedAtAsc(String pipelineRunId);

    Optional<NodeRun> findByPipelineRunIdAndNodeKey(String pipelineRunId, String nodeKey);

    Optional<NodeRun> findByJobId(String jobId);

    List<NodeRun> findByStatus(NodeRunStatus status);

    long countByPipelineRunIdAndStatus(String pipelineRunId, NodeRunStatus status);
}
