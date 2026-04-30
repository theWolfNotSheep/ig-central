package co.uk.wolfnotsheep.infrastructure.services.mail;

import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.models.GmailIngestCursor;
import co.uk.wolfnotsheep.document.repositories.ConnectedDriveRepository;
import co.uk.wolfnotsheep.document.repositories.GmailIngestCursorRepository;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition;
import co.uk.wolfnotsheep.governance.repositories.PipelineDefinitionRepository;
import co.uk.wolfnotsheep.infrastructure.services.connectors.PerSourceLock;
import co.uk.wolfnotsheep.infrastructure.services.mail.GmailService.GmailMessageSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Periodically checks active pipelines for gmailWatcher nodes and
 * ingests new messages from configured Gmail accounts.
 * Runs every 60 seconds, checking each watcher's poll interval.
 */
@Service
public class GmailPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(GmailPollingScheduler.class);

    private final PipelineDefinitionRepository pipelineRepo;
    private final ConnectedDriveRepository driveRepo;
    private final GmailIngestCursorRepository cursorRepo;
    private final GmailService gmailService;
    private final EmailIngestionService emailIngestionService;
    private final PerSourceLock perSourceLock;

    public GmailPollingScheduler(PipelineDefinitionRepository pipelineRepo,
                                  ConnectedDriveRepository driveRepo,
                                  GmailIngestCursorRepository cursorRepo,
                                  GmailService gmailService,
                                  EmailIngestionService emailIngestionService,
                                  PerSourceLock perSourceLock) {
        this.pipelineRepo = pipelineRepo;
        this.driveRepo = driveRepo;
        this.cursorRepo = cursorRepo;
        this.gmailService = gmailService;
        this.emailIngestionService = emailIngestionService;
        this.perSourceLock = perSourceLock;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 120000) // check every 60s, start after 2min
    public void pollGmailWatchers() {
        List<PipelineDefinition> activePipelines = pipelineRepo.findByActiveTrue();

        for (PipelineDefinition pipeline : activePipelines) {
            if (pipeline.getVisualNodes() == null) continue;

            for (PipelineDefinition.VisualNode node : pipeline.getVisualNodes()) {
                if (!"gmailWatcher".equals(node.type())) continue;

                // Per-source lock keyed on (pipeline, node) so multiple replicas
                // can poll different watchers concurrently but never the same
                // one (Phase 1.13 — per-source watch sharding via ShedLock).
                String lockName = "gmail-poll-" + pipeline.getId() + "-" + node.id();
                perSourceLock.withLock(lockName, Duration.ofMinutes(5), () -> {
                    try {
                        processWatcherNode(pipeline, node);
                    } catch (Exception e) {
                        log.error("Gmail watcher failed for pipeline {} node {}: {}",
                                pipeline.getName(), node.id(), e.getMessage());
                    }
                });
            }
        }
    }

    private void processWatcherNode(PipelineDefinition pipeline, PipelineDefinition.VisualNode node) throws Exception {
        Map<String, Object> data = node.data();
        if (data == null) return;

        String accountId = getString(data, "accountId");
        String query = getString(data, "query");
        int pollIntervalMinutes = getInt(data, "pollIntervalMinutes", 15);

        if (accountId == null || accountId.isBlank()) {
            log.debug("gmailWatcher node {} has no accountId configured, skipping", node.id());
            return;
        }

        // Check poll interval
        GmailIngestCursor cursor = cursorRepo.findByConnectedDriveIdAndQuery(accountId, query != null ? query : "")
                .orElse(null);

        if (cursor != null && cursor.getLastPollAt() != null) {
            Duration sinceLast = Duration.between(cursor.getLastPollAt(), Instant.now());
            if (sinceLast.toMinutes() < pollIntervalMinutes) {
                return; // Not yet time to poll
            }
        }

        // Verify account is active
        ConnectedDrive account = driveRepo.findById(accountId).orElse(null);
        if (account == null || !account.isActive() || !"GMAIL".equals(account.getProvider())) {
            log.debug("Gmail account {} not available for watcher", accountId);
            return;
        }

        log.info("Polling Gmail watcher: pipeline={}, account={}, query={}",
                pipeline.getName(), account.getProviderAccountEmail(), query);

        // List messages matching query
        var result = gmailService.listMessages(account, query, null, 50);
        int ingested = 0;

        for (GmailMessageSummary msg : result.messages()) {
            try {
                var doc = emailIngestionService.ingestMessage(
                        account, msg.id(), "system:gmail-watcher", null);
                if (doc != null) ingested++;
            } catch (Exception e) {
                log.warn("Failed to ingest message {} from watcher: {}", msg.id(), e.getMessage());
            }
        }

        // Update cursor
        if (cursor == null) {
            cursor = new GmailIngestCursor();
            cursor.setConnectedDriveId(accountId);
            cursor.setQuery(query != null ? query : "");
        }
        cursor.setLastPollAt(Instant.now());
        cursor.setMessagesIngested(cursor.getMessagesIngested() + ingested);
        cursorRepo.save(cursor);

        if (ingested > 0) {
            log.info("Gmail watcher ingested {} new messages from {}", ingested, account.getProviderAccountEmail());
        }
    }

    private String getString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

    private int getInt(Map<String, Object> data, String key, int defaultValue) {
        Object val = data.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
