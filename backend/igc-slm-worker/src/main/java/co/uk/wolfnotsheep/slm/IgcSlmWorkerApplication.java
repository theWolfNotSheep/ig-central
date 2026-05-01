package co.uk.wolfnotsheep.slm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableMongoRepositories(basePackages = "co.uk.wolfnotsheep.slm")
@SpringBootApplication
@EnableAsync
public class IgcSlmWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgcSlmWorkerApplication.class, args);
    }
}
