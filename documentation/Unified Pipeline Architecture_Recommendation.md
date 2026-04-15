<img src="https://r2cdn.perplexity.ai/pplx-full-logo-primary-dark%402x.png" style="height:64px;margin-right:32px"/>

# Unified Pipeline Architecture

Overview
The Governance-Led Storage (GLS) platform processes documents through a configurable, visual pipeline. Documents flow through extraction, PII scanning, LLM classification, governance enforcement, and routing — all driven by a graph of nodes that users build in a drag-and-drop editor.
As of April 2026, the pipeline executes in a unified single-pass model: the execution engine walks the entire node graph from start to finish in one thread. When it encounters an LLM node, it calls the LLM worker synchronously via HTTP, waits for the result, and continues to the next node. This replaced the earlier Phase 1/Phase 2 split architecture where the engine stopped at the first LLM node and waited for an asynchronous RabbitMQ response.
Service Architecture
+------------------+
|     nginx:80     |
|  (reverse proxy) |
+--------+---------+
|
+----------------+----------------+
|                                 |
+------+------+                   +------+------+
|   web:3000  |                   |  api:8080   |
| (Next.js)   |                   | (Spring Boot)|
+-------------+                   +------+------+
|
+----------------------------------+----------------------------------+
|                    |                    |                            |
+------+------+     +------+------+     +------+------+              +------+------+
|llm-worker   |     | mcp-server  |     |  rabbitmq  |              |   mongo     |
|   :8082     |---->|   :8081     |     |   :5672   |              |  :27017     |
|(Spring AI + |     | (MCP tools) |     | (AMQP)    |              | (MongoDB)   |
| Anthropic)  |     +-------------+     +-----------+              +-------------+
+-------------+
|
+------+------+
| bert-classifier|
|   :8000       |
| (FastAPI/ONNX)|
+---------------+

ServicePortRole
api (gls-app-assembly)
8080
Main app: pipeline engine, REST API, document management
llm-worker (gls-llm-orchestration)
8082
LLM classification via Claude/Ollama + MCP tools
mcp-server (gls-mcp-server)
8081
MCP tool provider (taxonomy, corrections, save results)
bert-classifier
8000
BERT model inference sidecar (Python/FastAPI/ONNX)
web (Next.js)
3000
Frontend UI
nginx
80
Reverse proxy
mongo
27017
Document store, governance config, classification results
rabbitmq
5672
Message broker for document events
minio
9000
S3-compatible object storage for document files
elasticsearch
9200
Full-text search index
All services run on a shared Docker network (gls) and communicate via HTTP and AMQP.
Unified Pipeline Execution Flow
When a document is uploaded, the following happens in a single thread:
User uploads document
|
v
[DocumentController] saves to MinIO + MongoDB, publishes DocumentIngestedEvent to RabbitMQ
|
v
[PipelineExecutionConsumer] receives event, calls engine.executePipeline(event)
|
v
[PipelineExecutionEngine.executePipeline()]
|
|  1. Resolve pipeline definition (visual graph from MongoDB)
|  2. Topological sort of all nodes
|  3. Walk every node in order:
|
+---> [trigger]           NOOP — entry point marker
|
+---> [textExtraction]    Downloads file from MinIO, extracts text via Apache Tika
|                         Sets: extractedText, pageCount, dublinCore
|                         Status: UPLOADED -> PROCESSING
|
+---> [piiScanner]        Regex-based PII detection (Tier 1)
|                         Sets: piiFindings, piiStatus, piiScannedAt
|                         Status: -> PROCESSED
|
+---> [accelerators]      Optional: templateFingerprint, rulesEngine, similarityCache, bertClassifier
|                         If confident match -> short-circuit LLM, skip to governance
|
+---> [aiClassification]  SYNC_LLM — calls llm-worker via HTTP POST
|     (1st LLM node)      Mode: CLASSIFICATION (full MCP tool chain)
|                         LLM calls 8 MCP tools, saves DocumentClassificationResult
|                         Sets: categoryId, categoryName, sensitivityLabel, classifiedAt
|                         Status: -> CLASSIFIED
|
+---> [aiClassification]  SYNC_LLM — calls llm-worker via HTTP POST
|     (2nd LLM node)      Mode: CUSTOM_PROMPT (no MCP tools, custom instruction)
|                         e.g. "count the words in the document"
|                         Returns structured JSON, stored in document metadata
|
+---> [condition]         Evaluates field (e.g. confidence >= 0.8)
|                         Branches to TRUE or FALSE path via graph edges
|
+---> [governance]        Applies retention schedule, storage tier, policies
|     (TRUE branch)       Status: -> INBOX
|
+---> [humanReview]       Routes to review queue
(FALSE branch)      Status: -> REVIEW_REQUIRED

Data passed between nodes
The DocumentModel is the primary data carrier. Each node reads from and writes to it:
NodeReadsWrites
textExtraction
storageBucket, storageKey (file location)
extractedText, pageCount, dublinCore
piiScanner
extractedText
piiFindings, piiStatus
accelerators
extractedText, mimeType
categoryId, categoryName, confidence (if matched)
aiClassification
extractedText, piiFindings, pipelineId
categoryId, categoryName, sensitivityLabel, classificationResultId
condition
confidence (from classification event)
— (routes via graph edges)
governance
categoryId, sensitivityLabel
retentionScheduleId, storageTierId, status
humanReview
confidence
status (REVIEW_REQUIRED)
After each node that mutates the document, the engine reloads from MongoDB to avoid stale writes (controlled by the requiresDocReload flag on each node type definition).
How the LLM Call Works
Synchronous HTTP call from engine to LLM worker
The SyncLlmNodeExecutor in gls-app-assembly makes an HTTP POST to the LLM worker:
Request:
POST http://llm-worker:8082/api/internal/classify
Content-Type: application/json

{
"documentId": "69deb9bbad7d1e763e7377e4",
"fileName": "Insurgo John Woolley Executive Biography",
"mimeType": "application/pdf",
"fileSizeBytes": 35940,
"extractedText": "John Woolley is a technology executive...",
"storageLocation": "gls-documents/69deb9bb...",
"uploadedBy": "admin@governanceledstore.co.uk",
"pipelineId": "69dd60f82c782246a38d1ca7",
"blockId": "69dd60f82c782246a38d1ca2",
"mode": "CLASSIFICATION"
}

CLASSIFICATION mode (full MCP tool chain)
The LLM worker receives the request, builds a system prompt + user prompt from the linked PROMPT block, and calls Claude via Spring AI's ChatClient. Claude has access to 10 MCP tools exposed by the MCP server:
Claude receives prompt with document text
|
+---> calls get_classification_taxonomy()     -- loads category hierarchy
+---> calls get_sensitivity_definitions()      -- loads sensitivity levels
+---> calls get_correction_history()           -- past human corrections
+---> calls get_org_pii_patterns()             -- organisation-specific PII
+---> calls get_governance_policies()          -- applicable policies
+---> calls get_document_traits()              -- trait definitions
+---> calls get_metadata_schemas()             -- extraction schemas for chosen category
+---> calls save_classification_result()        -- persists the classification
|
v
Classification result saved to MongoDB (classification_results collection)

Each MCP tool call is a round-trip: Claude decides which tool to call, Spring AI invokes the Java method, the result is fed back to Claude for the next decision. This chain typically involves 8-12 tool calls and takes 30-150 seconds depending on document length and model speed.
Response:
{
"success": true,
"classificationResultId": "69deb9c02dc7ac378cf741a0",
"categoryId": "cat-employee-records",
"categoryName": "Employee Records",
"sensitivityLabel": "CONFIDENTIAL",
"tags": ["biography", "executive"],
"confidence": 0.85,
"needsReview": false,
"retentionScheduleId": "ret-7-years",
"applicablePolicyIds": ["policy-gdpr", "policy-hr"]
}

CUSTOM_PROMPT mode (arbitrary LLM instruction)
For the second LLM node (or any non-classification LLM task), the controller reads the prompt from the linked PROMPT block and calls Claude without MCP tools:
Claude receives custom system prompt + document text
|
v
Returns structured response (e.g. word count JSON)

This is simpler and faster (typically 5-60 seconds) because there are no tool call round-trips.
Response:
{
"success": true,
"llmResponse": "{ \"wordcount\": 2431 }",
"parsedResult": { "wordcount": 2431 }
}

Node Type Definition Registry
Node types are stored in the node_type_definitions MongoDB collection. This drives:
The pipeline editor palette (which nodes are available to drag)
The inspector config form (what settings each node has)
The execution engine dispatch (how each node runs)
Performance warning badges in the UI
Each definition includes:
executionCategory: how the engine runs it (NOOP, BUILT_IN, ACCELERATOR, SYNC_LLM, GENERIC_HTTP)
configSchema: JSON Schema that renders the inspector config form dynamically
performanceImpact / performanceWarning: drives UI warnings on slow nodes
requiresDocReload: whether the engine reloads the document after this node
compatibleBlockType: which block types can be linked (PROMPT, REGEX_SET, etc.)
New node types can be added via the admin UI at /admin/node-types without code changes.
Known Issue: LLM Call Timeout
Problem
The CLASSIFICATION mode LLM call involves 8-12 MCP tool round-trips with Claude. Each round-trip includes:
Claude deciding which tool to call (~1-3 seconds inference)
Network latency between llm-worker and mcp-server (~10-50ms)
MongoDB query in the MCP tool (~10-100ms)
Response fed back to Claude for next decision
Total classification time: 30-150+ seconds depending on:
Document length (longer text = more tokens = slower inference)
Number of correction history entries (more context = slower)
Model load (Anthropic API rate limits, queueing)
MCP tool response sizes (large taxonomies, many schemas)
Current state
The SyncLlmNodeExecutor HTTP timeout is set to 300 seconds (5 minutes). In testing:
A 1-page PDF (2,752 chars) classification took ~147 seconds — timed out at the original 120s limit
The same document's CUSTOM_PROMPT (word count) took 60 seconds
Impact
If the timeout is exceeded:
The engine logs an error and continues to the next node
The classification result is lost (even though the LLM worker may complete it after the timeout)
Downstream nodes (condition, governance) skip because no classification context exists
The document ends up in an incomplete state
Options under consideration
Increase timeout further (current: 300s) — simple but ties up the consumer thread longer. A pipeline with 2 LLM nodes could block for 10+ minutes.
Streaming/SSE response — the LLM worker streams progress (tool call status) back to the engine. The engine knows the call is alive and extends the timeout dynamically. More complex but prevents false timeouts.
Async with polling — the engine starts the LLM call, gets a job ID, and polls for completion. Frees the thread between polls. More resilient but adds complexity.
Reduce MCP tool calls — cache taxonomy/sensitivity/traits across calls (they rarely change). Pre-fetch correction history. Could cut classification time by 30-50%.
Virtual threads (Java 21) — use virtual threads for the consumer so blocking on HTTP calls doesn't exhaust the thread pool. Doesn't reduce wall-clock time but improves concurrency.
Hybrid approach — first LLM node (CLASSIFICATION) uses the existing async RabbitMQ path (proven, handles long calls). Subsequent LLM nodes (CUSTOM_PROMPT) use synchronous HTTP (faster, simpler).
Recommendation needed
The timeout issue is the primary blocker for production use of multiple LLM nodes. We need to decide which approach balances reliability, complexity, and user experience. The hybrid approach (option 6) may be the pragmatic choice: it keeps the battle-tested async classification path while enabling synchronous custom prompts for faster operations.
Performance Characteristics
StageTypical DurationNotes
Document upload + MinIO storage
< 1s
Text extraction (Tika)
0.5-5s
Depends on file size/type
PII regex scan
0.1-0.5s
Accelerator check (BERT/fingerprint/rules)
0.1-5s
Can skip LLM entirely
LLM classification (SYNC_LLM, CLASSIFICATION)
30-150s
MCP tool chain bottleneck
LLM custom prompt (SYNC_LLM, CUSTOM_PROMPT)
5-60s
No MCP tools, faster
Governance enforcement
0.5-2s
Total (1 LLM node)
35-160s
Total (2 LLM nodes)
65-220s
UI performance warnings
The pipeline editor shows:
Amber clock badge on LLM and external HTTP nodes
Warning text in the inspector panel: "External LLM call - adds 10-30 seconds per document"
Save confirmation dialog when pipeline has multiple LLM nodes with estimated processing time
Rollback
To revert to the Phase 1/Phase 2 async architecture:
Update the aiClassification node type definition in MongoDB:
db.node_type_definitions.updateOne(
{ key: "aiClassification" },
{ \$set: { executionCategory: "ASYNC_BOUNDARY", pipelinePhase: "PRE_CLASSIFICATION" } }
)

The engine's ASYNC_BOUNDARY fallback case publishes to RabbitMQ and stops (old behaviour)
The ClassificationPipeline RabbitMQ consumer in llm-worker handles classification asynchronously
The PipelineExecutionConsumer.onDocumentClassified listener picks up Phase 2
No code deployment needed — the rollback is entirely data-driven.

Two questions.
1 - how could we improve the architecture.
2 - what are the options on solving the queuing / timing issue bearing in mind this is a user configurable pipeline.

Yes: the biggest improvement is to stop treating the whole user-defined graph as one in-memory, one-thread transaction and instead separate **control-plane orchestration** from **worker execution**, with durable state between steps. Your current unified pass is simple, but it couples graph traversal, long-running LLM latency, retries, and document state consistency too tightly, which is exactly why the timeout problem becomes production-critical.[^1][^2]

## Architecture changes

Your current model is strong in one respect: it gives a simple mental model for a drag-and-drop pipeline and keeps non-LLM nodes fast and deterministic. But it is brittle because one slow node can hold the whole execution hostage, and a timed-out synchronous step can leave the document in an incomplete or ambiguous state even if the LLM eventually finishes elsewhere.[^1][^3]

The architectural shift I would make is: **keep the visual graph and node registry, but execute the graph as a durable state machine**, not as a single-pass thread. Durable execution platforms exist specifically to make long-running, crash-prone workflows resumable, with persisted state, retries, and pause/resume semantics rather than relying on one live process surviving the whole flow. AWS Step Functions’ callback pattern shows the same idea in a simpler form: the workflow can pause on an external task and continue only when the external worker returns a token or result, instead of blocking a worker thread the whole time.[^2][^4][^1]

## Recommended target model

I would split GLS into four logical concerns:


| Concern | What it should do | Why it helps |
| :-- | :-- | :-- |
| Graph compiler | Validate graph, topologically sort reachable nodes, compile branches into an execution plan | Lets you preserve the low-code editor while turning the graph into an explicit runtime plan [^1] |
| Orchestrator | Advance node-by-node, persist step state, retries, branch decisions, waiting states | Prevents loss of progress when a process crashes or a call takes minutes [^1][^4] |
| Executors | Run node types as isolated workers: built-in, accelerator, LLM, generic HTTP | Lets slow and fast node classes scale independently [^3][^5] |
| State/audit store | Persist run state, node inputs/outputs, status transitions, correlation IDs | Makes debugging, replay, and compliance much easier [^6][^7] |

That model also fits your product requirement that pipelines are user-configurable. User-configurable graphs are much safer when the runtime stores explicit execution state per node, because you cannot assume every user-created graph will have predictable latency or failure characteristics.[^1][^8]

## Specific improvements

### Treat each pipeline run as a durable execution

Persist a **PipelineRun** and **NodeRun** record for every document execution, including node status such as `PENDING`, `RUNNING`, `WAITING`, `SUCCEEDED`, `FAILED`, `SKIPPED`, and `COMPENSATED`. Durable execution systems emphasize exactly this benefit: they preserve application state and resume after crashes instead of forcing you to rebuild retry and timeout logic manually in business code.[^1][^9]

That means the `DocumentModel` should stop being the only runtime carrier. Keep it as business state, but add a separate execution record so orchestration state is not inferred indirectly from document fields alone.[^6][^7]

### Move from document reloads to versioned state writes

Reloading from Mongo after each mutating node is a symptom that multiple writers can race or overwrite each other. Instead, use:

- document version numbers or optimistic locking,
- append-only node output records,
- a reducer/merger step that updates the canonical document view.

Idempotency guidance for distributed workflows is clear: retries are unavoidable, so writes need request-state tracking and safe replay semantics, or duplicate/late results will corrupt state.[^7][^10]

### Separate execution state from business state

Right now statuses like `PROCESSING`, `CLASSIFIED`, `INBOX`, `REVIEW_REQUIRED` are doing double duty as both workflow progress and business outcome. That becomes messy once a pipeline can pause, retry, time out, or resume. A better split is:

- **execution state**: node/task progress,
- **document lifecycle state**: business state visible to users.

That avoids cases where a document is “PROCESSING” forever because one background LLM node is still outstanding, even though extraction and PII are already complete.[^1][^8]

### Introduce node capability classes

Your registry already has `executionCategory`; I would extend that into runtime policy:

- `INLINE_FAST`: sub-second or low-second built-ins,
- `OFFLOAD_JOB`: long-running async jobs,
- `CALLBACK_JOB`: external step that signals completion,
- `FANOUT_SAFE`: parallelizable,
- `USER_REVIEW_GATE`: human-in-the-loop pause.

This matters because a user-configurable pipeline should not let every node behave like every other node. Step Functions explicitly separates request-response, sync job, and callback wait patterns because those patterns have different operational behavior.[^2]

### Add backpressure and admission control

LLM nodes need quotas and scheduling per tenant, per pipeline, or per model. Otherwise a user can create a graph with three LLM nodes and unintentionally saturate your workers. Async orchestration helps throughput because downstream steps do not sit idle while an LLM is thinking, but you still need a scheduler to limit in-flight expensive work.[^2][^11]

### Add first-class observability

For each node run, capture:

- queued time,
- start/end time,
- retries,
- model used,
- token counts if available,
- MCP tool count,
- tool latencies,
- result size,
- error category,
- correlation ID.

Saga and orchestration guidance consistently stresses state tracking and monitoring because long-running workflows are otherwise impossible to debug well.[^12][^6]

## Best-fit runtime options

There are three realistic architecture tiers.

### Option A: Improve current system, keep custom orchestrator

This means:

- keep Spring Boot engine,
- add `PipelineRun`/`NodeRun`,
- make LLM nodes job-based,
- use RabbitMQ or Mongo-backed state transitions,
- add idempotency keys and callback completion.

This is the least disruptive and probably the best next step if you want production reliability in the near term. It addresses the timeout issue without replacing the entire platform.[^7][^10]

### Option B: Hybrid custom orchestrator plus callback jobs

This is my preferred medium-term choice. The engine still walks the graph logically, but any node marked slow or external becomes:

1. create job,
2. persist `WAITING`,
3. release thread,
4. resume when callback or poll result arrives.

This preserves your low-code graph runtime while making slow nodes behave like durable external tasks, which is exactly what callback-based workflow systems are designed for.[^2][^13]

### Option C: Adopt a durable workflow engine

Temporal is the clearest example: it persists event history so workflows can pause, resume, retry, and survive restarts without you writing most of that machinery yourself. The tradeoff is operational and conceptual complexity, plus the challenge of mapping your arbitrary visual graph cleanly into workflow code and versioning that safely for existing runs.[^1][^4][^8]

For GLS, I would not jump to this first unless you expect:

- many long-running steps,
- high concurrency,
- human review pauses,
- replay/versioning needs,
- multi-day or multi-week workflows.


## Solving the queuing and timeout issue

The core issue is not really “timeout”; it is **blocking orchestration on a user-defined, high-variance external step**. Synchronous request-response is a poor fit when one node can take 30-150+ seconds and user-configured pipelines may chain multiple such nodes.[^2][^3]

## Option set for LLM timing

### 1. Increase timeout

This is the easiest change, but it only masks the mismatch. It improves false timeouts, yet keeps a consumer occupied for the full wall-clock duration and makes throughput more sensitive to slow or stalled requests.[^2][^3]

Use only as a temporary safety margin, not as the design answer.

### 2. Streaming or SSE heartbeat

This is better than a blind long timeout because it distinguishes “alive but slow” from “dead”. Step Functions also exposes the general idea of heartbeat timeouts for waiting tasks so long-running work can prove liveness without completing immediately.[^2]

This helps user experience and monitoring, but it still leaves your engine fundamentally waiting on a live connection unless you redesign the runtime around heartbeats and resumability.

### 3. Async start + poll

Engine submits LLM job, gets `jobId`, marks node `WAITING`, then polls until completion. This frees the main worker between polls and works even if the result takes minutes.[^2][^14]

It is workable, but callback is usually cleaner than polling because polling adds latency, load, and another schedule to tune.

### 4. Async start + callback/webhook

This is usually the best custom solution. Engine submits job with correlation ID and idempotency key, persists `WAITING`, and the llm-worker later calls back or publishes completion. That matches Step Functions’ callback-token pattern almost exactly: start external work, pause workflow, resume on completion signal.[^2][^13]

For your setup, this can be done with RabbitMQ events rather than HTTP callbacks:

- `LlmJobRequested`
- `LlmJobStarted`
- `LlmJobProgressed`
- `LlmJobCompleted`
- `LlmJobFailed`

That keeps the pipeline user-configurable without assuming a fixed phase split.

### 5. Reduce MCP round-trips

This is probably the highest ROI performance win inside the LLM path. Every extra tool call adds model deliberation time plus network and DB overhead, and long chains compound latency. Caching mostly static artifacts like taxonomy, sensitivity definitions, trait definitions, and metadata schemas should materially reduce total duration, especially if the same tenant or pipeline is classifying many documents.[^1][^2]

I would strongly consider:

- bundling multiple slow-changing MCP resources into one “classification context” payload,
- precomputing tenant-specific context snapshots,
- retrieving correction history separately with size limits,
- trimming tool payload sizes,
- caching per pipeline version.


### 6. Virtual threads

Virtual threads improve concurrency for blocking I/O because blocked virtual threads do not pin platform threads in the same way traditional thread pools do, which can reduce thread exhaustion and improve throughput under load. But they do **not** solve the underlying latency or correctness problem; they only make the system less wasteful while waiting.[^15][^5][^16][^17]

So virtual threads are worth doing, but as an optimization layer, not the primary architecture decision.

### 7. Restore async only for classification

Your proposed hybrid is sensible because classification is the slow, tool-heavy, state-critical LLM step, while custom prompts are often shorter and simpler. This is close to the callback-job model above, but limited to one node type.[^1]

I think this is a good **transitional** design, but not the final one. The risk is product inconsistency: because the pipeline is user-configurable, future users may create other long-running LLM or HTTP nodes that have the same issue. So the better principle is not “classification is async”; it is “any node class marked long-running executes asynchronously.”

## My recommendation

I would recommend this sequence.

### Near term

Implement a **generic async job pattern for any long-running node**, starting with `aiClassification`:

- engine submits job and stores `NodeRun`,
- node becomes `WAITING`,
- worker completes via RabbitMQ callback/event,
- orchestrator resumes from that node,
- all requests use idempotency keys,
- late results are accepted if still relevant and ignored safely if superseded.[^7][^10]

At the same time:

- increase timeout modestly only as a safety net,
- add virtual threads in consumers,
- add better metrics around queue time, run time, and MCP tool counts.[^15][^5]


### Medium term

Refactor node execution policy so the registry drives runtime behavior:

- `INLINE_FAST`,
- `ASYNC_JOB`,
- `CALLBACK_JOB`,
- `HUMAN_GATE`,
- `EXTERNAL_HTTP_SYNC` only for truly short calls.

That keeps the pipeline user-configurable while still protecting the platform from arbitrary slow graphs.[^2][^8]

### Longer term

If GLS becomes more workflow-heavy, especially with approvals, SLAs, pauses, retries, resumptions, and versioned graph migration, consider adopting a durable orchestration platform instead of continuing to grow a custom one. Durable execution platforms are specifically built to preserve state, survive crashes, and handle very long-running workflows cleanly.[^1][^4]

## Concrete design for GLS

A practical GLS-specific model would be:

1. Upload creates `PipelineRun` with compiled graph version.
2. Orchestrator executes fast nodes inline.
3. On slow node, orchestrator creates `NodeRun(jobId, idempotencyKey, timeoutPolicy)` and exits.
4. Worker processes job independently.
5. Worker emits progress events for UI visibility.
6. Worker emits completion event with result payload.
7. Orchestrator resumes from the next reachable nodes.
8. Conditional branches evaluate from persisted node outputs, not transient in-memory state.

That design preserves the visual editor, supports multiple LLM nodes, and avoids tying correctness to a single live HTTP call.[^2][^6]

## Decision on your listed options

If I rank your current options for GLS:


| Option | Reliability | Complexity | Fit for configurable pipelines |
| :-- | --: | --: | --: |
| Increase timeout | Low | Low | Poor [^3] |
| Streaming/SSE | Medium | Medium | Fair [^2] |
| Async with polling | High | Medium | Good [^2][^14] |
| Reduce MCP calls | High impact on latency, not enough alone | Medium | Excellent [^1] |
| Virtual threads | Throughput improvement only | Low-Medium | Helpful but insufficient [^15][^16] |
| Hybrid classification async, custom prompt sync | High | Low-Medium | Good transitional fit [^1] |
| Generic async-by-node-policy | Very high | Medium-High | Best overall [^2][^8] |

My answer in one line: **do not solve this as a bigger timeout problem; solve it as a durable orchestration problem with async/callback execution for any long-running user-configurable node, plus aggressive MCP-context reduction for classification**.[^1][^2][^7]

If you want, I can turn this into a concrete target design with:

- revised service boundaries,
- Mongo collections/events,
- node state machine,
- retry/idempotency rules,
- and a migration path from the current unified engine.
<span style="display:none">[^18][^19][^20][^21][^22][^23][^24][^25][^26][^27][^28][^29][^30]</span>

<div align="center">⁂</div>

[^1]: https://www.irjmets.com/upload_newfiles/irjmets80300217443/paper_file/irjmets80300217443.pdf

[^2]: https://www.linkedin.com/pulse/asynchronous-workflow-orchestration-llm-latency-tanya-bisht-uhpkc

[^3]: https://www.linkedin.com/learning/integrating-ai-into-the-product-architecture/sync-vs-async-integrating-llms-effectively

[^4]: https://www.xgrid.co/resources/enterprise-workflows-with-temporal-architecture/

[^5]: https://www.linkedin.com/posts/chandru15062004_hook-if-your-spring-boot-services-silently-activity-7426354279763861504--pUw

[^6]: https://temporal.io/blog/mastering-saga-patterns-for-distributed-transactions-in-microservices

[^7]: https://orkes.io/blog/idempotency-and-retry-safety-in-distributed-workflows/

[^8]: https://debugg.ai/resources/queue-is-not-a-workflow-engine-durable-execution-temporal-step-functions-2025

[^9]: https://temporal.io/blog/what-is-durable-execution

[^10]: https://dev.to/matt_frank_usa/idempotency-in-apis-designing-safe-retry-logic-1oal

[^11]: https://dasroot.net/posts/2026/02/async-llm-pipelines-python-bottlenecks/

[^12]: https://brandonlincolnhendricks.com/research/implementing-saga-pattern-long-running-ai-agent-workflows

[^13]: https://docs.aws.amazon.com/step-functions/latest/dg/callback-task-sample-sqs.html

[^14]: https://wrschneider.github.io/2019/06/20/step-function-callbacks.html

[^15]: https://engnotes.dev/blog/project-loom/virtual-threads-revolution-part-1

[^16]: https://www.reddit.com/r/java/comments/1lfa991/virtual_threads_in_java_24_we_ran_realworld/

[^17]: https://davidvlijmincx.com/posts/virtual-thread-performance-short-blocking-methods/

[^18]: https://twenty20sys.com/understanding-synchronous-and-asynchronous-function-calls-in-workato/

[^19]: https://www.emergentmind.com/topics/asynchronous-pipeline-parallelism

[^20]: https://www.val.town/x/nbbaier/wrkflw/branch/fix-api/version/8/code/workflow-engines-research-report.md

[^21]: https://www.ibm.com/docs/en/tap/5.0.0?topic=workflows-synchronous-versus-asynchronous

[^22]: https://arxiv.org/pdf/2507.01663.pdf

[^23]: https://temporal.io/blog/very-long-running-workflows

[^24]: https://dev.to/temporalio/how-durable-execution-works-2b88

[^25]: https://innovationessence.com/temporal-io-durable-execution-and-workflow-orchestration-for-modern-applications/

[^26]: https://thearchitectsnotebook.substack.com/p/advanced-idempotency-in-system-design

[^27]: https://www.youtube.com/watch?v=csAfj_KhUnk

[^28]: https://docs.aws.amazon.com/step-functions/latest/dg/connect-to-resource.html

[^29]: https://www.reddit.com/r/programming/comments/1j9ncni/durable_execution_this_changes_everything/

[^30]: https://oneuptime.com/blog/post/2026-01-30-aws-step-functions-best-practices/view

