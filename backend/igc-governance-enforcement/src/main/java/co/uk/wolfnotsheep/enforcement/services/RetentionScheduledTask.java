package co.uk.wolfnotsheep.enforcement.services;

import co.uk.wolfnotsheep.document.models.SystemError;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
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
    private final SystemErrorRepository systemErrorRepo;

    public RetentionScheduledTask(EnforcementService enforcementService,
                                   SystemErrorRepository systemErrorRepo) {
        this.enforcementService = enforcementService;
        this.systemErrorRepo = systemErrorRepo;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void processExpiredRetentions() {
        log.info("Starting daily retention enforcement run");
        try {
            enforcementService.processExpiredRetentions();
            log.info("Daily retention enforcement run complete");
        } catch (Exception e) {
            log.error("Daily retention enforcement run failed: {}", e.getMessage(), e);
            try {
                SystemError error = SystemError.of("CRITICAL", "PIPELINE",
                        "Daily retention enforcement run failed: " + e.getMessage());
                error.setService("governance-enforcer");
                systemErrorRepo.save(error);
            } catch (Exception ex) {
                log.error("Failed to persist retention run error: {}", ex.getMessage());
            }
        }
    }
}
