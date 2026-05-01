package co.uk.wolfnotsheep.extraction.audio.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Bounded thread pool for the {@code Prefer: respond-async} path
 * (CSV #47). Defaults are conservative — transcription is per-call
 * heavy, so eight concurrent jobs is plenty for a single replica;
 * tune via {@code igc.extraction.audio.async.*} when load profiling
 * lands.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "audioAsyncExecutor")
    @ConditionalOnMissingBean(name = "audioAsyncExecutor")
    public Executor audioAsyncExecutor(
            @Value("${igc.extraction.audio.async.core-size:4}") int coreSize,
            @Value("${igc.extraction.audio.async.max-size:8}") int maxSize,
            @Value("${igc.extraction.audio.async.queue-capacity:32}") int queueCapacity) {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(coreSize);
        exec.setMaxPoolSize(maxSize);
        exec.setQueueCapacity(queueCapacity);
        exec.setThreadNamePrefix("audio-async-");
        exec.initialize();
        return exec;
    }
}
