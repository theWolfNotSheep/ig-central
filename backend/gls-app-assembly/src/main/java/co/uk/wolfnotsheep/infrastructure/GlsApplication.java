package co.uk.wolfnotsheep.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@EnableMongoRepositories(basePackages = {
        "co.uk.wolfnotsheep.apps",
        "co.uk.wolfnotsheep.platform"
})
@SpringBootApplication(scanBasePackages = {
        "co.uk.wolfnotsheep.infrastructure",
        "co.uk.wolfnotsheep.platform",
        "co.uk.wolfnotsheep.apps"
})
@ConfigurationPropertiesScan
public class GlsApplication {

    public static void main(String[] args) {
        System.out.println("GLS Platform starting...");
        SpringApplication.run(GlsApplication.class, args);
    }

}
