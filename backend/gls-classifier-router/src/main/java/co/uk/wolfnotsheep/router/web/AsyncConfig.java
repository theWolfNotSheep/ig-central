package co.uk.wolfnotsheep.router.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Bounded thread pool for the {@code Prefer: respond-async} path
 * (CSV #47). Defaults are conservative — cascade calls hold the
 * request thread for up to the LLM tier's timeout (60s) so eight
 * concurrent jobs is plenty for a single replica; tune via
 * {@code gls.router.async.*} when load profiling lands.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "routerAsyncExecutor")
    @ConditionalOnMissingBean(name = "routerAsyncExecutor")
    public Executor routerAsyncExecutor(
            @Value("${gls.router.async.core-size:4}") int coreSize,
            @Value("${gls.router.async.max-size:8}") int maxSize,
            @Value("${gls.router.async.queue-capacity:32}") int queueCapacity) {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(coreSize);
        exec.setMaxPoolSize(maxSize);
        exec.setQueueCapacity(queueCapacity);
        exec.setThreadNamePrefix("router-async-");
        exec.initialize();
        return exec;
    }
}
