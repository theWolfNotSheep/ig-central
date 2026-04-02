package co.uk.wolfnotsheep.enforcement.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs retention enforcement daily at 2am.
 * Processes documents whose retention period has expired.
 */
@Component
public class RetentionScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduledTask.class);

    private final EnforcementService enforcementService;

    public RetentionScheduledTask(EnforcementService enforcementService) {
        this.enforcementService = enforcementService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void processExpiredRetentions() {
        log.info("Starting daily retention enforcement run");
        enforcementService.processExpiredRetentions();
        log.info("Daily retention enforcement run complete");
    }
}
