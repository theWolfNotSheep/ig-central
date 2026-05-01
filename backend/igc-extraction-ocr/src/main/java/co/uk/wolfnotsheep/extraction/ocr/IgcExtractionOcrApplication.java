package co.uk.wolfnotsheep.extraction.ocr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@EnableMongoRepositories(basePackages = "co.uk.wolfnotsheep.extraction.ocr")
@SpringBootApplication
public class IgcExtractionOcrApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgcExtractionOcrApplication.class, args);
    }
}
