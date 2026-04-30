package co.uk.wolfnotsheep.indexing.jobs;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface JobRepository extends MongoRepository<JobRecord, String> {
}
