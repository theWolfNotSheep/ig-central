package co.uk.wolfnotsheep.indexing.consumer;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.indexing.service.DocumentNotFoundException;
import co.uk.wolfnotsheep.indexing.service.IndexBackendUnavailableException;
import co.uk.wolfnotsheep.indexing.service.IndexingService;
import co.uk.wolfnotsheep.indexing.service.MappingConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentClassifiedConsumerTest {

    private IndexingService indexingService;
    private DocumentClassifiedConsumer consumer;

    @BeforeEach
    void setUp() {
        indexingService = mock(IndexingService.class);
        consumer = new DocumentClassifiedConsumer(indexingService);
    }

    @Test
    void happy_path_indexes_the_document() {
        when(indexingService.indexDocument("doc-1"))
                .thenReturn(new IndexingService.IndexOutcome("doc-1", "ig_central_documents", 1L));

        consumer.onDocumentClassified(buildEvent("doc-1"));

        verify(indexingService, times(1)).indexDocument("doc-1");
    }

    @Test
    void null_event_is_discarded_without_calling_service() {
        assertThatNoException().isThrownBy(() -> consumer.onDocumentClassified(null));
        verify(indexingService, never()).indexDocument(any());
    }

    @Test
    void document_not_found_is_swallowed_so_message_is_acked() {
        when(indexingService.indexDocument(any())).thenThrow(new DocumentNotFoundException("doc-missing"));

        // No exception → Spring AMQP acks the message; stale event won't requeue.
        assertThatNoException().isThrownBy(() ->
                consumer.onDocumentClassified(buildEvent("doc-missing")));
    }

    @Test
    void mapping_conflict_is_swallowed_so_message_is_acked() {
        when(indexingService.indexDocument(any()))
                .thenThrow(new MappingConflictException("doc-x", 400, "{}"));

        // Service has parked the doc in index_quarantine; consumer just acks.
        assertThatNoException().isThrownBy(() ->
                consumer.onDocumentClassified(buildEvent("doc-x")));
    }

    @Test
    void backend_unavailable_propagates_so_message_is_requeued() {
        when(indexingService.indexDocument(any()))
                .thenThrow(new IndexBackendUnavailableException("ES down"));

        assertThatThrownBy(() -> consumer.onDocumentClassified(buildEvent("doc-y")))
                .isInstanceOf(IndexBackendUnavailableException.class);
    }

    private static DocumentClassifiedEvent buildEvent(String docId) {
        return new DocumentClassifiedEvent(
                docId, "cr-1", "cat-1", "HR > Letters",
                SensitivityLabel.INTERNAL,
                List.of("hr"),
                List.of("policy-1"),
                "ret-7y",
                0.92, false, Instant.now());
    }
}
