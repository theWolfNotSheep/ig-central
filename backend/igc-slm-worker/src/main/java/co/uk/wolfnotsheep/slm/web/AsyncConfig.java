package co.uk.wolfnotsheep.slm.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Bounded thread pool for the {@code Prefer: respond-async} path
 * (CSV #47). Conservative defaults — SLM calls hold the request
 * thread for backend-call duration; tune via {@code igc.slm.worker.async.*}
 * once load profiling lands.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "slmAsyncExecutor")
    @ConditionalOnMissingBean(name = "slmAsyncExecutor")
    public Executor slmAsyncExecutor(
            @Value("${igc.slm.worker.async.core-size:4}") int coreSize,
            @Value("${igc.slm.worker.async.max-size:8}") int maxSize,
            @Value("${igc.slm.worker.async.queue-capacity:32}") int queueCapacity) {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(coreSize);
        exec.setMaxPoolSize(maxSize);
        exec.setQueueCapacity(queueCapacity);
        exec.setThreadNamePrefix("slm-async-");
        exec.initialize();
        return exec;
    }
}
