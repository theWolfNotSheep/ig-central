package co.uk.wolfnotsheep.hub.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = {"co.uk.wolfnotsheep.hub"})
@EnableMongoRepositories(basePackages = "co.uk.wolfnotsheep.hub.repositories")
public class GlsGovernanceHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlsGovernanceHubApplication.class, args);
    }
}
