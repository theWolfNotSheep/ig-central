package co.uk.wolfnotsheep.docprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = {
        "co.uk.wolfnotsheep.docprocessing",
        "co.uk.wolfnotsheep.document",
        "co.uk.wolfnotsheep.governance"
})
@EnableMongoRepositories(basePackages = {
        "co.uk.wolfnotsheep.document.repositories",
        "co.uk.wolfnotsheep.governance.repositories"
})
public class GlsDocumentProcessingApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlsDocumentProcessingApplication.class, args);
    }
}
