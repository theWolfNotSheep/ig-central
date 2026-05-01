package co.uk.wolfnotsheep.indexing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "co.uk.wolfnotsheep.indexing",
        "co.uk.wolfnotsheep.document"
})
@EnableMongoRepositories(basePackages = {
        "co.uk.wolfnotsheep.indexing.jobs",
        "co.uk.wolfnotsheep.indexing.quarantine",
        "co.uk.wolfnotsheep.document.repositories"
})
@EnableAsync
@EnableScheduling
public class IgcIndexingWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgcIndexingWorkerApplication.class, args);
    }
}
