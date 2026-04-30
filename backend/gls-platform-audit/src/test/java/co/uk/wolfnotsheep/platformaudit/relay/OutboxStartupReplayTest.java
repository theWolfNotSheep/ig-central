package co.uk.wolfnotsheep.platformaudit.relay;

import co.uk.wolfnotsheep.platformaudit.outbox.AuditOutboxRecord;
import co.uk.wolfnotsheep.platformaudit.outbox.OutboxStatus;
import com.mongodb.client.result.UpdateResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxStartupReplayTest {

    private MongoTemplate mongoTemplate;
    private MeterRegistry meterRegistry;
    private OutboxStartupReplay replay;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        replay = new OutboxStartupReplay(mongoTemplate, providerOf(meterRegistry));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MeterRegistry> providerOf(MeterRegistry mr) {
        ObjectProvider<MeterRegistry> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(mr);
        return p;
    }

    private double counter(String name) {
        var c = meterRegistry.find(name).counter();
        return c == null ? 0.0 : c.count();
    }

    @Test
    void resets_backed_off_pending_rows_and_records_metric() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(AuditOutboxRecord.class)))
                .thenReturn(UpdateResult.acknowledged(7L, 7L, null));

        long modified = replay.replayBackedOffPendingRows();

        assertThat(modified).isEqualTo(7);
        assertThat(counter("audit.outbox.startup_replay.reset")).isEqualTo(7.0);
    }

    @Test
    void no_backed_off_rows_is_a_clean_no_op() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(AuditOutboxRecord.class)))
                .thenReturn(UpdateResult.acknowledged(0L, 0L, null));

        long modified = replay.replayBackedOffPendingRows();

        assertThat(modified).isEqualTo(0);
        // Counter never registered when there's nothing to count.
        assertThat(meterRegistry.find("audit.outbox.startup_replay.reset").counter()).isNull();
    }

    @Test
    void query_filters_PENDING_status_and_future_nextRetryAt_only() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(AuditOutboxRecord.class)))
                .thenReturn(UpdateResult.acknowledged(0L, 0L, null));

        replay.replayBackedOffPendingRows();

        ArgumentCaptor<Query> queryCap = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCap = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(1))
                .updateMulti(queryCap.capture(), updateCap.capture(), eq(AuditOutboxRecord.class));

        // The query targets only PENDING rows whose nextRetryAt is in the future.
        // Note: Query.getQueryObject() returns the unconverted criteria — the
        // values are still Java types (the enum, the Instant) at this layer;
        // conversion to BSON happens further down the stack.
        var queryDoc = queryCap.getValue().getQueryObject();
        assertThat(queryDoc.get("status")).isEqualTo(OutboxStatus.PENDING);
        assertThat(queryDoc.get("nextRetryAt"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKey("$gt");

        // The update touches only nextRetryAt — attempts / lastError / status preserved.
        var updateDoc = updateCap.getValue().getUpdateObject();
        var setDoc = (org.bson.Document) updateDoc.get("$set");
        assertThat(setDoc.keySet()).containsExactly("nextRetryAt");
    }

    @Test
    void absent_MeterRegistry_does_not_break_replay() {
        OutboxStartupReplay noMetrics = new OutboxStartupReplay(mongoTemplate, providerOf(null));
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(AuditOutboxRecord.class)))
                .thenReturn(UpdateResult.acknowledged(3L, 3L, null));

        long modified = noMetrics.replayBackedOffPendingRows();

        assertThat(modified).isEqualTo(3);
    }

    @Test
    void onApplicationReady_swallows_exceptions_so_app_boot_is_never_blocked() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(AuditOutboxRecord.class)))
                .thenThrow(new RuntimeException("mongo down"));

        // Must not throw — startup replay failure can't block app boot.
        replay.onApplicationReady();
    }

    @Test
    void onApplicationReady_delegates_to_replayBackedOffPendingRows() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(AuditOutboxRecord.class)))
                .thenReturn(UpdateResult.acknowledged(2L, 2L, null));

        replay.onApplicationReady();

        // Single round-trip to Mongo from the listener path.
        verify(mongoTemplate, times(1))
                .updateMulti(any(Query.class), any(Update.class), eq(AuditOutboxRecord.class));
        // The Instant captured by the query is "now" at listener time — we don't pin
        // an exact value; the contract is just that the cutoff is set to now-ish.
        Instant beforeNow = Instant.now().plusSeconds(1);
        ArgumentCaptor<Query> queryCap = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate)
                .updateMulti(queryCap.capture(), any(Update.class), eq(AuditOutboxRecord.class));
        var queryDoc = queryCap.getValue().getQueryObject();
        var nextRetryClause = (org.bson.Document) queryDoc.get("nextRetryAt");
        var cutoff = (Instant) nextRetryClause.get("$gt");
        assertThat(cutoff).isBefore(beforeNow);
    }
}
