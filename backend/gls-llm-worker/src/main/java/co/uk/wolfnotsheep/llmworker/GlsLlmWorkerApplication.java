package co.uk.wolfnotsheep.llmworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class GlsLlmWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlsLlmWorkerApplication.class, args);
    }
}
