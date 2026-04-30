package co.uk.wolfnotsheep.indexing.consumer;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.indexing.service.DocumentNotFoundException;
import co.uk.wolfnotsheep.indexing.service.IndexBackendUnavailableException;
import co.uk.wolfnotsheep.indexing.service.IndexingService;
import co.uk.wolfnotsheep.indexing.service.MappingConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Async primary path for the indexing worker. Consumes
 * {@code document.classified} from the worker's own bound queue
 * (see {@link RabbitMqConfig}) and writes the document to
 * Elasticsearch via {@link IndexingService}.
 *
 * <p>Per CLAUDE.md happy/unhappy-path rules:
 * <ul>
 *   <li><b>Mapping conflicts</b> are absorbed and parked in
 *       {@code index_quarantine} (the service does the parking; this
 *       consumer just logs). The message is acked so it doesn't
 *       requeue indefinitely against a permanent shape error.</li>
 *   <li><b>Backend-unavailable failures</b> rethrow so Spring AMQP
 *       requeues / DLXs per the broker config — these are transient.</li>
 *   <li><b>Document missing</b> is acked + logged. The classified
 *       event is stale; nothing to index.</li>
 * </ul>
 */
@Component
public class DocumentClassifiedConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentClassifiedConsumer.class);

    private final IndexingService indexingService;

    public DocumentClassifiedConsumer(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_CLASSIFIED)
    public void onDocumentClassified(DocumentClassifiedEvent event) {
        if (event == null || event.documentId() == null) {
            log.warn("indexing consumer: malformed event with null documentId — discarding");
            return;
        }
        try {
            IndexingService.IndexOutcome outcome = indexingService.indexDocument(event.documentId());
            log.info("indexed document {} (es version {})", outcome.documentId(), outcome.version());
        } catch (DocumentNotFoundException e) {
            log.warn("indexing consumer: document {} not found — event stale, discarding",
                    event.documentId());
        } catch (MappingConflictException e) {
            log.warn("indexing consumer: mapping conflict for {} (HTTP {}); parked in quarantine",
                    e.documentId(), e.httpStatus());
        } catch (IndexBackendUnavailableException e) {
            log.error("indexing consumer: ES unavailable for {}: {}",
                    event.documentId(), e.getMessage());
            throw e; // requeue / DLX per broker config
        }
    }
}
