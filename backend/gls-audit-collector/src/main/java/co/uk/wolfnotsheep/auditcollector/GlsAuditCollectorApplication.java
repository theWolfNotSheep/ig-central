package co.uk.wolfnotsheep.auditcollector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "co.uk.wolfnotsheep.auditcollector")
@EnableMongoRepositories(basePackages = "co.uk.wolfnotsheep.auditcollector.store")
@EnableScheduling
public class GlsAuditCollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlsAuditCollectorApplication.class, args);
    }
}
