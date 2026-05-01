package co.uk.wolfnotsheep.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = {
        "co.uk.wolfnotsheep.mcp",
        "co.uk.wolfnotsheep.governance"
})
@EnableMongoRepositories(basePackages = "co.uk.wolfnotsheep.governance.repositories")
public class IgcMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgcMcpServerApplication.class, args);
    }
}
