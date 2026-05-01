package co.uk.wolfnotsheep.extraction.archive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@EnableMongoRepositories(basePackages = "co.uk.wolfnotsheep.extraction.archive")
@SpringBootApplication
public class IgcExtractionArchiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgcExtractionArchiveApplication.class, args);
    }
}
