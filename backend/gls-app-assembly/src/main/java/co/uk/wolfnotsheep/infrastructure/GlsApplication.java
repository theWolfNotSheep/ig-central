package co.uk.wolfnotsheep.infrastructure;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@EnableMongock
@EnableMongoRepositories(basePackages = {
        "co.uk.wolfnotsheep.apps",
        "co.uk.wolfnotsheep.platform",
        "co.uk.wolfnotsheep.governance.repositories",
        "co.uk.wolfnotsheep.document.repositories"
})
@SpringBootApplication(scanBasePackages = {
        "co.uk.wolfnotsheep.infrastructure",
        "co.uk.wolfnotsheep.platform",
        "co.uk.wolfnotsheep.apps",
        "co.uk.wolfnotsheep.governance",
        "co.uk.wolfnotsheep.document",
        "co.uk.wolfnotsheep.docprocessing",
        "co.uk.wolfnotsheep.enforcement"
})
@ConfigurationPropertiesScan
@EnableScheduling
public class GlsApplication {

    public static void main(String[] args) {
        System.out.println("GLS Platform starting...");
        SpringApplication.run(GlsApplication.class, args);
    }

}
