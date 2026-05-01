package co.uk.wolfnotsheep.llmworker.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Bounded thread pool for the {@code Prefer: respond-async} path
 * (CSV #47). Conservative defaults — LLM calls hold the request
 * thread for backend-call duration; tune via {@code igc.llm.worker.async.*}
 * once load profiling lands.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "llmAsyncExecutor")
    @ConditionalOnMissingBean(name = "llmAsyncExecutor")
    public Executor llmAsyncExecutor(
            @Value("${igc.llm.worker.async.core-size:4}") int coreSize,
            @Value("${igc.llm.worker.async.max-size:8}") int maxSize,
            @Value("${igc.llm.worker.async.queue-capacity:32}") int queueCapacity) {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(coreSize);
        exec.setMaxPoolSize(maxSize);
        exec.setQueueCapacity(queueCapacity);
        exec.setThreadNamePrefix("llm-async-");
        exec.initialize();
        return exec;
    }
}
