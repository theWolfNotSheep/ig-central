package co.uk.wolfnotsheep.indexing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

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
public class GlsIndexingWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlsIndexingWorkerApplication.class, args);
    }
}
