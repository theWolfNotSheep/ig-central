# Performance & Scaling Challenges — Incident Log

A record of performance problems encountered, root causes identified, and fixes applied. This document serves as institutional memory for future scaling decisions.

---

## Incident 1: Runaway Re-Queue Storm (35,515 poison messages)

**Date:** 2026-04-19
**Severity:** Critical — pipeline completely stalled
**Duration:** ~6 hours before detection

### Symptoms
- 35,515 messages piled up in `gls.documents.processed` queue
- 0 documents classified in the last hour
- 439 documents stuck at PROCESSED status
- Monitoring page showed documents "in progress" but nothing was moving
- LLM worker logging 513+ Spring AI retry warnings

### Root cause chain

```
1. Ollama (qwen2.5:32b) became slow under load
2. LLM calls started timing out (480s timeout)
3. Documents stayed at PROCESSED status
4. StaleDocumentRecoveryTask (every 5 min) re-queued all 439 stuck docs
5. But it didn't touch updatedAt — so the SAME docs were re-queued every cycle
6. After 80 cycles: 439 × 80 = 35,120 duplicate messages
7. RabbitMQ consumer prefetch was unlimited (default 250)
8. 250 concurrent LLM requests hit Ollama simultaneously
9. Ollama's internal queue backed up → all calls timed out
10. Spring AI retry mechanism added more concurrent requests
11. Positive feedback loop: more retries → more load → more timeouts
```

### Why the UI didn't catch it
- Monitoring page showed correct document status counts (439 PROCESSED)
- But had no visibility into queue message counts growing from 439 → 35,515
- No circuit breaker — the LLM worker kept accepting and failing messages
- No alert threshold on queue depth

### Fixes applied

| Fix | What | Impact |
|-----|------|--------|
| Re-queue cooldown | StaleDocumentRecoveryTask now touches `updatedAt` on re-queue, 30-minute cooldown between re-queue attempts per document | Prevents duplicate messages |
| Retry cap | Documents stop being auto-re-queued after 3 attempts | Prevents infinite re-queue for permanently failing docs |
| Consumer prefetch | Reduced from unlimited (250) to 5 (app-assembly) and 2 (LLM worker) | Prevents Ollama overload |
| Queue purge | Manually purged 35,515 poison messages | Immediate relief |

### Lessons learned
- **Never re-queue without a cooldown.** Any scheduled task that re-publishes messages must track when it last did so and enforce a minimum interval.
- **Prefetch count matters for slow consumers.** Default prefetch is designed for fast consumers (millisecond processing). LLM calls taking 30-120 seconds need a prefetch of 1-2.
- **Queue depth is a critical metric.** Document status counts alone are insufficient — you need to see message counts in the queue to detect duplication.

---

## Incident 2: Ollama Timeout Cascade

**Date:** 2026-04-19
**Severity:** High — all classifications failing
**Duration:** Ongoing at time of detection

### Symptoms
- All 42 classification failures had error: "LLM call timed out after 480s"
- Spring AI retry counter at 513 and climbing
- Ollama was loaded and responding to `/api/tags` but `/api/chat` calls were timing out
- qwen2.5:32b model (26GB VRAM) was loaded but overwhelmed

### Root cause
- 250 concurrent classification requests (due to unlimited prefetch)
- Ollama serialises inference — it can only process one request at a time on a single GPU
- With 250 requests queued internally, each waited for all predecessors
- First request: ~60s. 250th request: 250 × 60s = 4+ hours wait → all timeout at 480s
- Spring AI's retry mechanism (no max configured) created additional concurrent requests

### Fix applied
- **Circuit breaker** on `ClassificationPipeline` — after 5 consecutive failures, stops consuming for 2 minutes
- **Prefetch reduction** to 2 — Ollama gets at most 2 concurrent requests
- **Queue TTL** of 1 hour on `gls.documents.processed` — stale messages expire instead of piling up

### Why Spring AI retry made it worse
Spring AI's auto-retry (`SpringAiRetryAutoConfiguration`) has no max retry count or circuit breaker. When the LLM is down, it retries indefinitely with exponential backoff, but each retry still ties up a thread and a queue slot. The retry count reached 513 in this incident. The retry is at the HTTP client level (retrying the Ollama HTTP call), not at the message level — so one queued message can generate dozens of HTTP retries.

### Lessons learned
- **Single-GPU LLM inference is a serial bottleneck.** Prefetch must match the model's actual throughput, not the queue's capacity.
- **Circuit breakers are essential for external service calls.** Without one, a slow/down service causes cascading failure across the entire pipeline.
- **Spring AI retry defaults are dangerous for local LLM.** The retry mechanism assumes transient cloud API errors (429, 503) that resolve quickly. Local LLM timeouts are structural — retrying makes it worse.

---

## Incident 3: 20 Documents Stuck at CLASSIFIED

**Date:** 2026-04-19
**Severity:** Medium — documents classified but governance never applied
**Duration:** Unknown (accumulated over time)

### Symptoms
- 20 documents at CLASSIFIED status, never progressed to GOVERNANCE_APPLIED or INBOX
- `pipelineNodeId` was `undefined` on all 20
- `gls.documents.classified` queue was empty (messages were consumed)

### Root cause
The `PipelineExecutionConsumer.onDocumentClassified()` consumed the classified events and ran Phase 2 (post-classification pipeline nodes). However, the pipeline engine needs `pipelineNodeId` to know where to resume execution. These 20 documents had `pipelineNodeId = null/undefined`, meaning the pipeline engine couldn't determine their position in the graph and silently skipped governance enforcement.

This likely happened because:
1. The documents were classified via the async (legacy) path
2. The pipeline engine's Phase 2 checked the document's pipeline position
3. With no position set, it had nothing to execute
4. No error was thrown — it was treated as "already complete"

### Fix applied
- Manually moved 20 documents from CLASSIFIED to INBOX (their correct terminal state)
- These documents had already been classified but governance enforcement was skipped

### Lessons learned
- **Silent skips are worse than failures.** The Phase 2 consumer should log a warning or set an error when it encounters a document with no pipeline position instead of silently returning.
- **State transitions need validation.** A document at CLASSIFIED with no `pipelineNodeId` is an inconsistent state that should be caught.

---

## Scaling Characteristics Observed

### LLM Classification Throughput

| Configuration | Throughput | Notes |
|---------------|-----------|-------|
| Ollama qwen2.5:32b, prefetch=250 | ~0 docs/hr (cascade failure) | All timeout due to internal queuing |
| Ollama qwen2.5:32b, prefetch=2 | ~2-4 docs/hr | Sequential processing, 60-120s per doc |
| Anthropic Claude Sonnet, prefetch=5 | ~60-120 docs/hr | Cloud API, parallel requests work |

### Queue Behaviour Under Load

| Scenario | Queue growth rate | Time to crisis |
|----------|------------------|----------------|
| LLM down, no cooldown | +440 msgs/5min | ~2 hours to 10K+ messages |
| LLM down, 30-min cooldown | +440 msgs/30min | Capped at ~1,300 after 3 retries |
| LLM down, circuit breaker | 0 msgs (consumer paused) | No growth — self-healing |

### Memory & Resource Constraints

| Resource | Limit | Impact when exceeded |
|----------|-------|---------------------|
| Ollama VRAM (qwen2.5:32b) | 26GB | Model unloads → cold start on next request (~30s) |
| RabbitMQ message memory | ~1MB per PROCESSED message (includes extractedText) | 35K messages = ~35GB in queue (swapped to disk) |
| MongoDB connections | Default pool (100) | Not hit yet, but 250 concurrent doc lookups could saturate |

---

## Ollama Tuning Applied (2026-04-19)

**Hardware:** Mac Studio M3 Ultra, 96GB RAM, qwen2.5:32b Q4_K_M

### Configuration changes

| Setting | Before | After | Impact |
|---------|--------|-------|--------|
| Ollama version | 0.20.2 | 0.21.0 | MLX improvements, flash attention |
| OLLAMA_KEEP_ALIVE | 5min (default) | -1 (forever) | Eliminates 30-50s cold start |
| OLLAMA_NUM_PARALLEL | 1 (default) | 2 | 1.9x batch throughput |
| OLLAMA_FLASH_ATTENTION | off | 1 | Faster prefill (109→130 tok/s) |
| OLLAMA_KV_CACHE_TYPE | f16 (default) | q8_0 | Lower memory per context slot |

### Benchmark results

| Metric | Before | After |
|--------|--------|-------|
| Cold start | 30-50s after 5min idle | Never (always loaded) |
| Prefill speed | 109 tok/s | 130 tok/s |
| Decode speed | 26 tok/s | 27 tok/s |
| 2 docs (sequential) | 184s | 184s |
| 2 docs (parallel) | N/A | 98s |
| Effective throughput | ~2 docs/min | ~4 docs/min |

### What didn't help

- **Reduced context (32K → 4K):** Ollama reloaded the model when context size changed, negating the benefit. Left at 32K since classification prompts are small (~1,500 tokens) and the overhead is acceptable.
- **MLX model format:** qwen2.5:32b is served as GGUF even on v0.21.0. Ollama's MLX runner may auto-convert at load time for supported architectures but the performance gain wasn't measurable. The model format reports as "gguf" regardless.

### Configuration location

Persistent in `~/Library/LaunchAgents/homebrew.mxcl.ollama.plist` under `EnvironmentVariables`. Survives reboots. After editing, restart with `brew services restart ollama`.

---

## Current Safeguards

| Safeguard | Protects against | Configuration |
|-----------|-----------------|---------------|
| Circuit breaker | LLM overload/downtime | Opens after 5 failures, 2-min cooldown |
| Consumer prefetch | Queue flooding | app-assembly=5, llm-worker=2 |
| Re-queue cooldown | Duplicate messages | 30-min minimum between re-queues |
| Retry cap | Infinite re-queue loops | Max 3 auto-retries per document |
| Queue TTL | Unbounded queue growth | 1-hour TTL on processed queue |
| Dead-letter queue | Lost messages | All queues route rejected messages to DLQ |

---

## Known Remaining Risks

### 1. Spring AI retry has no max
Spring AI's `SpringAiRetryAutoConfiguration` will retry Ollama HTTP calls indefinitely. If a single LLM call hangs (not timeout — actually hangs), the consumer thread is blocked forever. The circuit breaker only triggers on exceptions, not on calls that never return.

**Mitigation:** The `callWithTimeout()` wrapper in `ClassificationPipeline` uses `CompletableFuture.get(timeout)` to enforce a hard timeout, which should prevent truly infinite hangs. But the Spring AI retry still fires between timeouts.

### 2. Single consumer for all queues
The LLM worker has one consumer thread for `gls.documents.processed` and one for `gls.pipeline.llm.jobs`. If one queue is backed up, the other still processes — but there's no priority system. A flood of re-queued stale documents can starve new document processing.

**Future:** Consider separate consumer instances or priority queues.

### 3. No backpressure from Ollama
Ollama accepts all requests and queues them internally. There's no way to ask "how many requests are you currently processing?" before sending more. The prefetch limit is the only throttle.

**Future:** Consider a semaphore-based rate limiter that checks Ollama's `/api/ps` endpoint before sending requests.

### 4. Stale recovery doesn't check queue depth
The `StaleDocumentRecoveryTask` re-queues documents without checking how many messages are already in the queue. If the queue already has 1,000 messages, adding 440 more just makes it worse.

**Future:** Check `gls.documents.processed` queue depth via RabbitMQ management API before re-queuing. Skip if queue depth > threshold.

### 5. No horizontal scaling for LLM
The current architecture assumes a single Ollama instance. To scale throughput, you'd need multiple Ollama instances behind a load balancer, each with its own GPU. The RabbitMQ consumer model supports this (just run more LLM worker instances), but Ollama deployment is single-instance.

**Future:** Kubernetes deployment with multiple Ollama pods, or cloud LLM (Anthropic) which handles scaling transparently.
