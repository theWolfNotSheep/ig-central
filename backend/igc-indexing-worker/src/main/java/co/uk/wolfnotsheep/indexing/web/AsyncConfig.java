package co.uk.wolfnotsheep.indexing.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Bounded thread pool for the {@code POST /v1/reindex} async path.
 * Conservative defaults — bulk reindex holds the worker thread for
 * the full ES walk. Tune via {@code igc.indexing.worker.async.*}
 * once load profiling lands.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "indexingAsyncExecutor")
    @ConditionalOnMissingBean(name = "indexingAsyncExecutor")
    public Executor indexingAsyncExecutor(
            @Value("${igc.indexing.worker.async.core-size:2}") int coreSize,
            @Value("${igc.indexing.worker.async.max-size:4}") int maxSize,
            @Value("${igc.indexing.worker.async.queue-capacity:8}") int queueCapacity) {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(coreSize);
        exec.setMaxPoolSize(maxSize);
        exec.setQueueCapacity(queueCapacity);
        exec.setThreadNamePrefix("indexing-async-");
        exec.initialize();
        return exec;
    }
}
