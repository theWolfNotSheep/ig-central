package co.uk.wolfnotsheep.infrastructure.controllers.internal;

import co.uk.wolfnotsheep.infrastructure.services.ElasticsearchIndexService;
import co.uk.wolfnotsheep.infrastructure.services.PipelineEventBroadcaster;
import co.uk.wolfnotsheep.infrastructure.services.drives.GoogleDriveWriteBackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal webhook for worker services to notify status changes.
 * Called by doc-processor, llm-worker, governance-enforcer after
 * each document status transition. Not exposed externally.
 */
@RestController
@RequestMapping("/api/internal/pipeline")
public class PipelineWebhookController {

    private final PipelineEventBroadcaster broadcaster;
    private final ElasticsearchIndexService esIndexService;
    private final GoogleDriveWriteBackService driveWriteBack;

    public PipelineWebhookController(PipelineEventBroadcaster broadcaster,
                                      ElasticsearchIndexService esIndexService,
                                      GoogleDriveWriteBackService driveWriteBack) {
        this.broadcaster = broadcaster;
        this.esIndexService = esIndexService;
        this.driveWriteBack = driveWriteBack;
    }

    @PostMapping("/status-changed")
    public ResponseEntity<Void> statusChanged(@RequestBody StatusChangeEvent event) {
        broadcaster.broadcastDocumentStatus(event.documentId(), event.status(), event.fileName());
        esIndexService.indexDocument(event.documentId());
        driveWriteBack.writeBackIfNeeded(event.documentId(), event.status());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/log")
    public ResponseEntity<Void> pipelineLog(@RequestBody PipelineLogEvent event) {
        broadcaster.broadcastPipelineLog(
                event.documentId(), event.fileName(), event.stage(),
                event.level(), event.message(), event.durationMs());
        return ResponseEntity.ok().build();
    }

    record StatusChangeEvent(String documentId, String status, String fileName) {}
    record PipelineLogEvent(String documentId, String fileName, String stage,
                            String level, String message, Long durationMs) {}
}
