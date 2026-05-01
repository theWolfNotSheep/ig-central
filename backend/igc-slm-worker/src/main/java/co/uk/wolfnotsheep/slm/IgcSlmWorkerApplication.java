package co.uk.wolfnotsheep.slm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class IgcSlmWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgcSlmWorkerApplication.class, args);
    }
}
