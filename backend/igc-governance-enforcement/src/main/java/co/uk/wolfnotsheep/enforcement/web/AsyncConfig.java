package co.uk.wolfnotsheep.enforcement.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Bounded thread pool for the {@code Prefer: respond-async} path
 * (CSV #47). Conservative defaults — enforcement holds the request
 * thread for storage-tier-migration duration; tune via
 * {@code igc.enforcement.worker.async.*} once load profiling lands.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "enforcementAsyncExecutor")
    @ConditionalOnMissingBean(name = "enforcementAsyncExecutor")
    public Executor enforcementAsyncExecutor(
            @Value("${igc.enforcement.worker.async.core-size:4}") int coreSize,
            @Value("${igc.enforcement.worker.async.max-size:8}") int maxSize,
            @Value("${igc.enforcement.worker.async.queue-capacity:32}") int queueCapacity) {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(coreSize);
        exec.setMaxPoolSize(maxSize);
        exec.setQueueCapacity(queueCapacity);
        exec.setThreadNamePrefix("enforce-async-");
        exec.initialize();
        return exec;
    }
}
