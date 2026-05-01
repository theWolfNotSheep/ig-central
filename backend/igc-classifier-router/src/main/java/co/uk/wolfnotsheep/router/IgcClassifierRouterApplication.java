package co.uk.wolfnotsheep.router;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class IgcClassifierRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgcClassifierRouterApplication.class, args);
    }
}
