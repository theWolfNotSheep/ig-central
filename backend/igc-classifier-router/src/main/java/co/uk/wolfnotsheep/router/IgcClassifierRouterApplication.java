package co.uk.wolfnotsheep.router;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableMongoRepositories(basePackages = "co.uk.wolfnotsheep.router")
@SpringBootApplication
@EnableAsync
public class IgcClassifierRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgcClassifierRouterApplication.class, args);
    }
}
