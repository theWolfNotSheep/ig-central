package co.uk.wolfnotsheep.llm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = {
        "co.uk.wolfnotsheep.llm",
        "co.uk.wolfnotsheep.governance"
})
@EnableMongoRepositories(basePackages = "co.uk.wolfnotsheep.governance.repositories")
public class GlsLlmOrchestrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlsLlmOrchestrationApplication.class, args);
    }
}
