package co.uk.wolfnotsheep.extraction.tika;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@EnableMongoRepositories(basePackages = "co.uk.wolfnotsheep.extraction.tika")
@SpringBootApplication
public class IgcExtractionTikaApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgcExtractionTikaApplication.class, args);
    }
}
