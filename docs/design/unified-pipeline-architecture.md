# Unified Pipeline Architecture

## Overview

The Governance-Led Storage (GLS) platform processes documents through a configurable, visual pipeline. Documents flow through extraction, PII scanning, LLM classification, governance enforcement, and routing — all driven by a graph of nodes that users build in a drag-and-drop editor.

As of April 2026, the pipeline executes in a **unified single-pass model**: the execution engine walks the entire node graph from start to finish in one thread. When it encounters an LLM node, it calls the LLM worker synchronously via HTTP, waits for the result, and continues to the next node. This replaced the earlier Phase 1/Phase 2 split architecture where the engine stopped at the first LLM node and waited for an asynchronous RabbitMQ response.

---

## Service Architecture

```
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
```

| Service | Port | Role |
|---------|------|------|
| **api** (gls-app-assembly) | 8080 | Main app: pipeline engine, REST API, document management |
| **llm-worker** (gls-llm-orchestration) | 8082 | LLM classification via Claude/Ollama + MCP tools |
| **mcp-server** (gls-mcp-server) | 8081 | MCP tool provider (taxonomy, corrections, save results) |
| **bert-classifier** | 8000 | BERT model inference sidecar (Python/FastAPI/ONNX) |
| **web** (Next.js) | 3000 | Frontend UI |
| **nginx** | 80 | Reverse proxy |
| **mongo** | 27017 | Document store, governance config, classification results |
| **rabbitmq** | 5672 | Message broker for document events |
| **minio** | 9000 | S3-compatible object storage for document files |
| **elasticsearch** | 9200 | Full-text search index |

All services run on a shared Docker network (`gls`) and communicate via HTTP and AMQP.

---

## Unified Pipeline Execution Flow

When a document is uploaded, the following happens in a single thread:

```
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
```

### Data passed between nodes

The `DocumentModel` is the primary data carrier. Each node reads from and writes to it:

| Node | Reads | Writes |
|------|-------|--------|
| textExtraction | storageBucket, storageKey (file location) | extractedText, pageCount, dublinCore |
| piiScanner | extractedText | piiFindings, piiStatus |
| accelerators | extractedText, mimeType | categoryId, categoryName, confidence (if matched) |
| aiClassification | extractedText, piiFindings, pipelineId | categoryId, categoryName, sensitivityLabel, classificationResultId |
| condition | confidence (from classification event) | — (routes via graph edges) |
| governance | categoryId, sensitivityLabel | retentionScheduleId, storageTierId, status |
| humanReview | confidence | status (REVIEW_REQUIRED) |

After each node that mutates the document, the engine reloads from MongoDB to avoid stale writes (controlled by the `requiresDocReload` flag on each node type definition).

---

## How the LLM Call Works

### Synchronous HTTP call from engine to LLM worker

The `SyncLlmNodeExecutor` in gls-app-assembly makes an HTTP POST to the LLM worker:

**Request:**
```
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
```

### CLASSIFICATION mode (full MCP tool chain)

The LLM worker receives the request, builds a system prompt + user prompt from the linked PROMPT block, and calls Claude via Spring AI's ChatClient. Claude has access to 10 MCP tools exposed by the MCP server:

```
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
```

Each MCP tool call is a round-trip: Claude decides which tool to call, Spring AI invokes the Java method, the result is fed back to Claude for the next decision. This chain typically involves **8-12 tool calls** and takes **30-150 seconds** depending on document length and model speed.

**Response:**
```json
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
```

### CUSTOM_PROMPT mode (arbitrary LLM instruction)

For the second LLM node (or any non-classification LLM task), the controller reads the prompt from the linked PROMPT block and calls Claude **without MCP tools**:

```
Claude receives custom system prompt + document text
    |
    v
Returns structured response (e.g. word count JSON)
```

This is simpler and faster (typically 5-60 seconds) because there are no tool call round-trips.

**Response:**
```json
{
  "success": true,
  "llmResponse": "{ \"wordcount\": 2431 }",
  "parsedResult": { "wordcount": 2431 }
}
```

---

## Node Type Definition Registry

Node types are stored in the `node_type_definitions` MongoDB collection. This drives:
- The pipeline editor palette (which nodes are available to drag)
- The inspector config form (what settings each node has)
- The execution engine dispatch (how each node runs)
- Performance warning badges in the UI

Each definition includes:
- `executionCategory`: how the engine runs it (NOOP, BUILT_IN, ACCELERATOR, SYNC_LLM, GENERIC_HTTP)
- `configSchema`: JSON Schema that renders the inspector config form dynamically
- `performanceImpact` / `performanceWarning`: drives UI warnings on slow nodes
- `requiresDocReload`: whether the engine reloads the document after this node
- `compatibleBlockType`: which block types can be linked (PROMPT, REGEX_SET, etc.)

New node types can be added via the admin UI at `/admin/node-types` without code changes.

---

## Known Issue: LLM Call Timeout

### Problem

The CLASSIFICATION mode LLM call involves 8-12 MCP tool round-trips with Claude. Each round-trip includes:
1. Claude deciding which tool to call (~1-3 seconds inference)
2. Network latency between llm-worker and mcp-server (~10-50ms)
3. MongoDB query in the MCP tool (~10-100ms)
4. Response fed back to Claude for next decision

Total classification time: **30-150+ seconds** depending on:
- Document length (longer text = more tokens = slower inference)
- Number of correction history entries (more context = slower)
- Model load (Anthropic API rate limits, queueing)
- MCP tool response sizes (large taxonomies, many schemas)

### Current state

The `SyncLlmNodeExecutor` HTTP timeout is set to **300 seconds** (5 minutes). In testing:
- A 1-page PDF (2,752 chars) classification took **~147 seconds** — timed out at the original 120s limit
- The same document's CUSTOM_PROMPT (word count) took **60 seconds**

### Impact

If the timeout is exceeded:
- The engine logs an error and continues to the next node
- The classification result is lost (even though the LLM worker may complete it after the timeout)
- Downstream nodes (condition, governance) skip because no classification context exists
- The document ends up in an incomplete state

### Options under consideration

1. **Increase timeout further** (current: 300s) — simple but ties up the consumer thread longer. A pipeline with 2 LLM nodes could block for 10+ minutes.

2. **Streaming/SSE response** — the LLM worker streams progress (tool call status) back to the engine. The engine knows the call is alive and extends the timeout dynamically. More complex but prevents false timeouts.

3. **Async with polling** — the engine starts the LLM call, gets a job ID, and polls for completion. Frees the thread between polls. More resilient but adds complexity.

4. **Reduce MCP tool calls** — cache taxonomy/sensitivity/traits across calls (they rarely change). Pre-fetch correction history. Could cut classification time by 30-50%.

5. **Virtual threads (Java 21)** — use virtual threads for the consumer so blocking on HTTP calls doesn't exhaust the thread pool. Doesn't reduce wall-clock time but improves concurrency.

6. **Hybrid approach** — first LLM node (CLASSIFICATION) uses the existing async RabbitMQ path (proven, handles long calls). Subsequent LLM nodes (CUSTOM_PROMPT) use synchronous HTTP (faster, simpler).

### Recommendation needed

The timeout issue is the primary blocker for production use of multiple LLM nodes. We need to decide which approach balances reliability, complexity, and user experience. The hybrid approach (option 6) may be the pragmatic choice: it keeps the battle-tested async classification path while enabling synchronous custom prompts for faster operations.

---

## Performance Characteristics

| Stage | Typical Duration | Notes |
|-------|-----------------|-------|
| Document upload + MinIO storage | < 1s | |
| Text extraction (Tika) | 0.5-5s | Depends on file size/type |
| PII regex scan | 0.1-0.5s | |
| Accelerator check (BERT/fingerprint/rules) | 0.1-5s | Can skip LLM entirely |
| LLM classification (SYNC_LLM, CLASSIFICATION) | 30-150s | MCP tool chain bottleneck |
| LLM custom prompt (SYNC_LLM, CUSTOM_PROMPT) | 5-60s | No MCP tools, faster |
| Governance enforcement | 0.5-2s | |
| **Total (1 LLM node)** | **35-160s** | |
| **Total (2 LLM nodes)** | **65-220s** | |

### UI performance warnings

The pipeline editor shows:
- Amber clock badge on LLM and external HTTP nodes
- Warning text in the inspector panel: "External LLM call - adds 10-30 seconds per document"
- Save confirmation dialog when pipeline has multiple LLM nodes with estimated processing time

---

## Rollback

To revert to the Phase 1/Phase 2 async architecture:

1. Update the `aiClassification` node type definition in MongoDB:
   ```
   db.node_type_definitions.updateOne(
     { key: "aiClassification" },
     { $set: { executionCategory: "ASYNC_BOUNDARY", pipelinePhase: "PRE_CLASSIFICATION" } }
   )
   ```
2. The engine's `ASYNC_BOUNDARY` fallback case publishes to RabbitMQ and stops (old behaviour)
3. The `ClassificationPipeline` RabbitMQ consumer in llm-worker handles classification asynchronously
4. The `PipelineExecutionConsumer.onDocumentClassified` listener picks up Phase 2

No code deployment needed — the rollback is entirely data-driven.
