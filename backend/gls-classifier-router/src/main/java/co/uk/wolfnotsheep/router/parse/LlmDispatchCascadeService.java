package co.uk.wolfnotsheep.router.parse;

import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Real LLM tier of the cascade. Dispatches the prompt to the
 * existing async LLM worker via the {@code gls.pipeline} exchange
 * (routing key {@code pipeline.llm.requested}); listens on a
 * per-replica auto-named queue bound to
 * {@code pipeline.llm.completed} and correlates the response back
 * to the dispatching call by {@code jobId} via an in-memory
 * {@link CompletableFuture} map.
 *
 * <p>Only the LLM tier is wired here — the BERT and SLM tiers in
 * the architecture's cascade fill in behind {@link CascadeService}
 * later. This service reports {@code tierOfDecision="LLM"} on
 * success and surfaces failures / timeouts as
 * {@link LlmJobFailedException} / {@link LlmJobTimeoutException}.
 *
 * <p>**Per-replica queue.** Each router replica binds its own
 * non-durable, exclusive, auto-named queue to
 * {@code gls.pipeline} with routing key {@code pipeline.llm.completed}.
 * That way every replica receives every completion event without
 * stealing from {@code PipelineResumeConsumer}'s shared durable
 * queue. Replicas drop events whose {@code jobId} doesn't match a
 * registered future — a mild fanout cost, exact correlation.
 */
public class LlmDispatchCascadeService implements CascadeService {

    private static final Logger log = LoggerFactory.getLogger(LlmDispatchCascadeService.class);

    private final RabbitTemplate rabbitTemplate;
    private final String pipelineExchange;
    private final String requestedRoutingKey;
    private final Duration waitTimeout;
    private final Map<String, CompletableFuture<LlmJobResult>> pending = new ConcurrentHashMap<>();

    public LlmDispatchCascadeService(
            RabbitTemplate rabbitTemplate,
            String pipelineExchange,
            String requestedRoutingKey,
            Duration waitTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.pipelineExchange = pipelineExchange;
        this.requestedRoutingKey = requestedRoutingKey;
        this.waitTimeout = waitTimeout;
    }

    @Override
    @Observed(name = "cascade.run", contextualName = "cascade-run-llm",
            lowCardinalityKeyValues = {"component", "router", "tier", "llm"})
    public CascadeOutcome run(String blockId, Integer blockVersion, String blockType, String text) {
        String jobId = UUID.randomUUID().toString();
        long byteCount = text == null ? 0L : text.getBytes(StandardCharsets.UTF_8).length;
        LlmJobRequest job = new LlmJobRequest(
                jobId,
                /* pipelineRunId */ jobId,
                /* nodeRunId */ jobId,
                /* documentId */ jobId,
                /* nodeKey */ "router-direct",
                /* mode */ inferMode(blockType),
                blockId,
                blockVersion,
                /* pipelineId */ "router-direct",
                /* extractedText */ text == null ? "" : text,
                /* fileName */ "router-text",
                /* mimeType */ "text/plain",
                byteCount,
                /* uploadedBy */ "gls-classifier-router",
                /* idempotencyKey */ jobId);

        CompletableFuture<LlmJobResult> future = new CompletableFuture<>();
        pending.put(jobId, future);
        long started = System.currentTimeMillis();
        try {
            rabbitTemplate.convertAndSend(pipelineExchange, requestedRoutingKey, job);
        } catch (AmqpException e) {
            pending.remove(jobId);
            throw new java.io.UncheckedIOException("LLM dispatch failed",
                    new java.io.IOException(e));
        }

        LlmJobResult result;
        try {
            result = future.get(waitTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            pending.remove(jobId);
            throw new LlmJobTimeoutException(
                    "LLM tier did not complete within " + waitTimeout + " for jobId=" + jobId);
        } catch (InterruptedException ie) {
            pending.remove(jobId);
            Thread.currentThread().interrupt();
            throw new LlmJobTimeoutException("LLM tier wait interrupted for jobId=" + jobId);
        } catch (ExecutionException ee) {
            pending.remove(jobId);
            throw new LlmJobFailedException("LLM tier wait errored for jobId=" + jobId
                    + ": " + ee.getCause());
        }
        long durationMs = System.currentTimeMillis() - started;

        if (!result.success()) {
            throw new LlmJobFailedException("LLM tier returned failure for jobId="
                    + jobId + ": " + result.error());
        }

        Map<String, Object> resultMap = buildResultMap(result, blockType);
        float confidence = result.confidence() == null ? 0f : result.confidence().floatValue();

        return new CascadeOutcome(
                "LLM",
                confidence,
                resultMap,
                /* rationale */ null,
                /* evidence */ List.of(),
                /* trace */ List.of(new CascadeOutcome.TraceStep(
                        "LLM", true, confidence, durationMs, byteCount / 1024, null)),
                /* costUnits */ Math.max(0L, byteCount / 1024),
                byteCount);
    }

    /**
     * Listener for {@code pipeline.llm.completed}. Bound to a
     * non-durable, exclusive, auto-named queue that exists only for
     * this replica's lifetime — see {@link RouterRabbitMqConfig}.
     */
    @RabbitListener(queues = "#{routerLlmCompletedQueue.name}",
            containerFactory = "routerRabbitListenerContainerFactory")
    public void onLlmJobCompleted(LlmJobResult result) {
        if (result == null || result.jobId() == null) {
            log.debug("router: dropped null / unkeyed llm completion");
            return;
        }
        CompletableFuture<LlmJobResult> future = pending.remove(result.jobId());
        if (future == null) {
            // Either this completion belongs to a sibling replica
            // (we still see every event due to the per-replica queue
            // bound on the same exchange), or the dispatching request
            // already timed out. Drop quietly.
            log.debug("router: orphan llm completion jobId={}", result.jobId());
            return;
        }
        future.complete(result);
    }

    /**
     * Visible for tests — lets us hand-feed completions in unit tests
     * that don't stand up the full Spring listener.
     */
    void completeTestOnly(LlmJobResult result) {
        onLlmJobCompleted(result);
    }

    int pendingCount() {
        return pending.size();
    }

    private static String inferMode(String blockType) {
        if (blockType == null) return "CLASSIFICATION";
        return switch (blockType.toUpperCase()) {
            case "BERT_CLASSIFIER" -> "CLASSIFICATION";
            default -> "CLASSIFICATION";
        };
    }

    private static Map<String, Object> buildResultMap(LlmJobResult result, String blockType) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (result.categoryId() != null) map.put("categoryId", result.categoryId());
        if (result.categoryName() != null) map.put("category", result.categoryName());
        if (result.sensitivityLabel() != null) map.put("sensitivity", result.sensitivityLabel());
        if (result.tags() != null && !result.tags().isEmpty()) map.put("tags", result.tags());
        if (result.confidence() != null) map.put("confidence", result.confidence());
        if (result.requiresHumanReview() != null) {
            map.put("requiresHumanReview", result.requiresHumanReview());
        }
        if (result.retentionScheduleId() != null) {
            map.put("retentionScheduleId", result.retentionScheduleId());
        }
        if (result.applicablePolicyIds() != null && !result.applicablePolicyIds().isEmpty()) {
            map.put("applicablePolicyIds", result.applicablePolicyIds());
        }
        if (result.extractedMetadata() != null && !result.extractedMetadata().isEmpty()) {
            map.put("extractedMetadata", result.extractedMetadata());
        }
        if (result.customResult() != null && !result.customResult().isEmpty()) {
            map.put("customResult", result.customResult());
        }
        return map;
    }
}
