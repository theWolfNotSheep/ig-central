package co.uk.wolfnotsheep.extraction.audio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class IgcExtractionAudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgcExtractionAudioApplication.class, args);
    }
}
