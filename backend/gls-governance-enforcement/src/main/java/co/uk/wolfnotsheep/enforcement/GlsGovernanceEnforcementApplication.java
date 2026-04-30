package co.uk.wolfnotsheep.enforcement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "co.uk.wolfnotsheep.enforcement",
        "co.uk.wolfnotsheep.document",
        "co.uk.wolfnotsheep.governance"
})
@EnableMongoRepositories(basePackages = {
        "co.uk.wolfnotsheep.enforcement.jobs",
        "co.uk.wolfnotsheep.document.repositories",
        "co.uk.wolfnotsheep.governance.repositories"
})
@EnableScheduling
@EnableAsync
public class GlsGovernanceEnforcementApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlsGovernanceEnforcementApplication.class, args);
    }
}
