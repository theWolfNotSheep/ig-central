# Document Processing Pipeline — Workflow & Class Reference

> Complete reference for how documents flow through the system, which classes handle each stage,
> how services hand off to each other, and how both happy and unhappy paths are handled.

---

## Two Operational Modes

The pipeline has two modes controlled by `pipeline.execution-engine.enabled`:

| Mode | Property Value | Who Handles Extraction | Who Handles Enforcement | How It Works |
|---|---|---|---|---|
| **Legacy** | `false` (default) | `DocumentProcessingPipeline` in gls-document-processing | `ClassificationEnforcementConsumer` in gls-governance-enforcement | Three independent consumers, one per stage |
| **Engine** | `true` | `PipelineExecutionEngine` in gls-app-assembly | `PipelineExecutionEngine` in gls-app-assembly | Single graph-driven engine walks visual pipeline nodes |

The LLM classification step (`ClassificationPipeline` in gls-llm-orchestration) is **always active** in both modes — it has no `@ConditionalOnProperty` annotation.

### How the Mode Flag Propagates

The flag must be set consistently across all services. Each worker service reads it from its own Spring config:

| Service | Config File | Default |
|---|---|---|
| gls-app-assembly | `application.yaml` → `pipeline.execution-engine.enabled: true` | `true` |
| gls-document-processing | `application.yaml` → `pipeline.execution-engine.enabled: ${PIPELINE_EXECUTION_ENGINE_ENABLED:false}` | `false` |
| gls-governance-enforcement | `application.yaml` → `pipeline.execution-engine.enabled: ${PIPELINE_EXECUTION_ENGINE_ENABLED:false}` | `false` |

In Docker, the `PIPELINE_EXECUTION_ENGINE_ENABLED=true` env var is set on both worker containers in `docker-compose.yml` to keep them in sync with gls-app-assembly.

**What goes wrong if they disagree:** If gls-app-assembly has engine mode ON but a worker service has it OFF, both the engine consumer and the legacy consumer register on the same RabbitMQ queue. RabbitMQ round-robins messages between competing consumers, so some documents hit the engine path and others hit the legacy path — causing inconsistent behaviour.

---

## RabbitMQ Topology

All services share a single topic exchange with three queues:

```
Exchange: gls.documents (TopicExchange, durable)
  │
  ├── document.ingested  →  Queue: gls.documents.ingested
  │                            Consumers: PipelineExecutionConsumer (engine) OR DocumentProcessingPipeline (legacy)
  │
  ├── document.processed →  Queue: gls.documents.processed
  │                            Consumers: ClassificationPipeline (always active)
  │
  └── document.classified → Queue: gls.documents.classified
                               Consumers: PipelineExecutionConsumer (engine) OR ClassificationEnforcementConsumer (legacy)
```

Each service declares its own queues and bindings in its `RabbitMqConfig` class, gated by `@ConditionalOnProperty`. The exchange and queue names are identical across services — RabbitMQ deduplicates declarations.

A dead-letter queue `gls.documents.classification.failed` exists for failed classifications (declared in gls-llm-orchestration) but is not actively consumed.

---

## Document Status Flow

```
UPLOADED
  │
  ├─ [success] → PROCESSING → PROCESSED → CLASSIFYING → CLASSIFIED
  │                  │              │            │             │
  │                  │              │            │             ├─ [auto-approve] → INBOX
  │                  │              │            │             └─ [low confidence / PII escalation] → REVIEW_REQUIRED
  │                  │              │            │
  │                  │              │            └─ [failure] → CLASSIFICATION_FAILED
  │                  │              │
  │                  │              └─ (this status is set AFTER extraction completes,
  │                  │                  BEFORE the LLM receives it)
  │                  │
  │                  └─ [failure] → PROCESSING_FAILED
  │
  └─ [failure before extraction starts] → PROCESSING_FAILED
```

Terminal states: `INBOX`, `FILED`, `ARCHIVED`, `DISPOSED`
Retryable failure states: `PROCESSING_FAILED`, `CLASSIFICATION_FAILED`, `ENFORCEMENT_FAILED`
Review state: `REVIEW_REQUIRED` → human approves → `INBOX`

---

## Happy Path — Engine Mode (Step by Step)

### Stage 1: Upload & Ingestion

**Class:** `DocumentController` (`gls-app-assembly/.../controllers/documents/DocumentController.java`)

1. User uploads a file via `POST /api/documents/upload` (or a Google Drive file is selected for classification)
2. `DocumentService.ingest()` creates a `DocumentModel` with status `UPLOADED`, stores the file in MinIO, generates a slug, creates an audit event
3. Controller publishes a `DocumentIngestedEvent` to RabbitMQ:
   - Exchange: `gls.documents`
   - Routing key: `document.ingested`
   - Payload: documentId, fileName, mimeType, fileSizeBytes, storageBucket, storageKey, uploadedBy, ingestedAt

### Stage 2: Text Extraction & PII Scanning (Engine Phase 1)

**Classes:**
- `PipelineExecutionConsumer` (`gls-app-assembly/.../pipeline/PipelineExecutionConsumer.java`)
- `PipelineExecutionEngine.executePhase1()` (`gls-app-assembly/.../pipeline/PipelineExecutionEngine.java`)

1. `PipelineExecutionConsumer.onDocumentIngested()` receives the event from `gls.documents.ingested`
2. Calls `engine.executePhase1(event)` which:
   a. Loads the `DocumentModel` from MongoDB
   b. Resolves the pipeline definition (visual graph)
   c. Performs a topological sort of the graph nodes
   d. Walks nodes in order until reaching the `aiClassification` node:

| Node Type | Handler | What It Does |
|---|---|---|
| `trigger` | `handleTrigger()` | Entry point — no-op |
| `textExtraction` | `handleTextExtraction()` | Sets status → `PROCESSING`. Calls `TextExtractionService` (Tika + Tesseract). Loads EXTRACTOR block config for maxTextLength, Dublin Core settings. Stores extracted text on document. |
| `piiScanner` | `handlePiiScan()` | Loads REGEX_SET block for patterns. Loads dismissals from correction history. Scans text. Sets `piiFindings` and `piiStatus` on document. Sets status → `PROCESSED`. |
| `templateFingerprint` | `handleClassifyingAccelerator()` | If document matches a known template fingerprint, short-circuits the LLM — creates a synthetic `DocumentClassifiedEvent` and returns immediately. |
| `rulesEngine` | `handleClassifyingAccelerator()` | If filename/metadata matches a configured rule, auto-classifies without LLM. |
| `similarityCache` | `handleClassifyingAccelerator()` | If document embedding is >threshold similar to a previously-classified document, inherits that classification. |
| `smartTruncation` | `handleSmartTruncation()` | Truncates extracted text intelligently for LLM context window. |
| `aiClassification` | `handleAiClassification()` | Publishes `DocumentProcessedEvent` to `document.processed` routing key. **This is the async boundary** — Phase 1 ends here. |

3. If an accelerator matched, `executePhase1()` returns `Phase1Result(skippedLlm=true, classificationEvent=<synthetic>)`. The consumer immediately calls `engine.executePhase2()` to complete governance.
4. If no accelerator matched, Phase 1 ends. The document waits in the `gls.documents.processed` queue for the LLM worker.

### Stage 3: LLM Classification

**Class:** `ClassificationPipeline` (`gls-llm-orchestration/.../pipeline/ClassificationPipeline.java`)

This service is **always active** — it doesn't check the execution engine flag.

1. `onDocumentProcessed()` receives the event from `gls.documents.processed`
2. Validates the document is in `PROCESSED` status (skips if not — prevents double-processing)
3. Sets status → `CLASSIFYING`
4. Calls the LLM via Spring AI `ChatClient`:
   - Builds the classification prompt using `ClassificationPromptBuilder`
   - Configures MCP tools: the LLM calls `get_classification_taxonomy`, `get_metadata_schemas`, `get_correction_history`, etc.
   - The LLM eventually calls `save_classification_result` which persists a `DocumentClassificationResult` to MongoDB
5. After the LLM responds, retrieves the saved `DocumentClassificationResult`
6. Creates an `AiUsageLog` record (tokens, cost, model, duration)
7. Calculates `needsReview = confidence < reviewThreshold`
8. Sets status → `CLASSIFIED`
9. Publishes `DocumentClassifiedEvent` to `document.classified` routing key with: categoryId, categoryName, sensitivityLabel, tags, applicablePolicyIds, retentionScheduleId, confidence, requiresHumanReview

### Stage 4: Governance Enforcement (Engine Phase 2)

**Classes:**
- `PipelineExecutionConsumer.onDocumentClassified()` receives the event from `gls.documents.classified`
- `PipelineExecutionEngine.executePhase2()` walks the graph nodes after `aiClassification`

1. Loads the document, validates status is `CLASSIFIED` or `REVIEW_REQUIRED`
2. Resolves the pipeline, sorts nodes, finds the position after `aiClassification`
3. Walks remaining nodes:

| Node Type | Handler | What It Does |
|---|---|---|
| `condition` | `evaluateCondition()` | Checks `event.requiresHumanReview()`. Branches to governance (true path) or humanReview (false path). |
| `governance` | `handleGovernance()` | Calls `EnforcementService.enforce(event)` — see below. Sets status → `INBOX`. Saves document. |
| `humanReview` | `handleHumanReview()` | Sets status → `REVIEW_REQUIRED`. |
| `notification` | `handleNotification()` | Emits a pipeline status log. |

4. After all nodes execute, clears `pipelineNodeId` and saves the document.

### What `EnforcementService.enforce()` Does

**Class:** `EnforcementService` (`gls-governance-enforcement/.../services/EnforcementService.java`)

This is the core governance logic, called by both the engine and legacy consumer:

1. **Copies classification to document:** Sets `categoryId`, `categoryName`, `sensitivityLabel`, `tags`, `confidence`, `classifiedAt`, `classificationResultId` on the `DocumentModel`
2. **Extracts document traits** from Dublin Core metadata (e.g. DRAFT/FINAL, INBOUND/OUTBOUND) if a TRAIT_DEFINITIONS block or traits config exists
3. **PII sensitivity escalation:**
   - Loads `highRiskPiiTypes` from the active ENFORCER block (defaults: NI number, NHS number, passport, driving licence, credit card, bank account, sort code, IBAN, DOB)
   - If any high-risk PII found → escalate to at least `CONFIDENTIAL`
   - If active PII findings >= threshold → escalate to `RESTRICTED`
   - Escalation creates an audit event
4. **Applies retention schedule:** Links the document to a retention schedule, sets `retentionExpiresAt` based on trigger date + retention period
5. **Migrates storage tier:** Moves the file in MinIO to the appropriate bucket based on sensitivity (e.g. `gls-documents` → `gls-confidential-store` → `gls-restricted-vault`)
6. **Creates audit event** with category, sensitivity, traits, storage tier, and retention info
7. Returns the updated `DocumentModel` (caller decides final status)

---

## Unhappy Path — Error Handling

### Design Rules

From CLAUDE.md:
- Every RabbitMQ consumer must catch exceptions and set a `*_FAILED` status
- Store the error: `lastError`, `lastErrorStage`, `failedAt`
- Failed documents must be retryable
- No silent returns — if a step can't produce a result, record it as a failure
- Every status transition (including failures) must produce an audit event

### How `DocumentService.setError()` Works

**Class:** `DocumentService` (`gls-document/.../services/DocumentService.java`)

```java
public void setError(String documentId, DocumentStatus failedStatus, String stage, String errorMessage)
```

1. Sets `status` to the failed status (e.g. `PROCESSING_FAILED`)
2. Sets `lastError` to the error message
3. Sets `lastErrorStage` to the stage name (e.g. "EXTRACTION", "CLASSIFICATION", "ENFORCEMENT")
4. Sets `failedAt` to `Instant.now()`
5. Saves the document
6. Creates an audit event with the error details
7. Notifies pipeline status subscribers (SSE for the monitoring page)

### Error Handling by Stage

#### Stage 1 Failures (Text Extraction / PII)

**Engine mode:**
```
PipelineExecutionConsumer.onDocumentIngested()
  └── catch (Exception e)
        └── safeSetError(docId, PROCESSING_FAILED, "ENGINE_PHASE1", message)
              └── documentService.setError(...)
                    └── [inner catch] → SystemError record saved to DB
```

**Legacy mode:**
```
DocumentProcessingPipeline.onDocumentIngested()
  └── catch (Exception e)
        └── documentService.setError(docId, PROCESSING_FAILED, "EXTRACTION", message)
  └── catch on queue publish
        └── documentService.setError(docId, PROCESSING_FAILED, "QUEUE", message)
```

#### Stage 2 Failures (LLM Classification)

```
ClassificationPipeline.onDocumentProcessed()
  └── catch (Exception e)
        └── aiUsageLog.status = "FAILED", aiUsageLog.errorMessage = message
        └── documentService.setError(docId, CLASSIFICATION_FAILED, "CLASSIFICATION", message)
  └── if LLM returned no result (empty classification_results)
        └── aiUsageLog.status = "NO_RESULT"
        └── documentService.setError(docId, CLASSIFICATION_FAILED, "CLASSIFICATION",
              "LLM did not produce a classification result")
  └── catch on queue publish
        └── documentService.setError(docId, CLASSIFICATION_FAILED, "QUEUE", message)
```

#### Stage 3 Failures (Governance Enforcement)

**Engine mode:**
```
PipelineExecutionConsumer.onDocumentClassified()
  └── catch (Exception e)
        └── safeSetError(docId, ENFORCEMENT_FAILED, "ENGINE_PHASE2", message)
```

**Legacy mode:**
```
ClassificationEnforcementConsumer.onDocumentClassified()
  └── catch (Exception e)
        └── documentService.setError(docId, ENFORCEMENT_FAILED, "ENFORCEMENT", message)
              └── [inner catch] → log.error (last resort)
```

### Stale Document Detection

Documents stuck in in-flight states (`PROCESSING`, `CLASSIFYING`) for more than 10 minutes are considered stale. The monitoring page flags them and provides "Reset Stale" to clear them back to a retryable state.

### Retry Flow

When an admin clicks "Retry Failed" or "Reprocess" on a failed document:

1. `DocumentService.clearErrorForReprocess()` clears `lastError`, `lastErrorStage`, `failedAt`, `cancelledAt`
2. Increments `retryCount`
3. Sets status back to `UPLOADED`
4. A new `DocumentIngestedEvent` is published — the document re-enters the pipeline from the beginning

Targeted retries are also available:
- `rerunClassify()` → sets status to `PROCESSED`, publishes `DocumentProcessedEvent` (skips extraction)
- `rerunEnforce()` → publishes `DocumentClassifiedEvent` using the existing classification result (skips LLM)

---

## Status Guards

Each consumer validates the document's current status before processing to prevent double-processing or out-of-order execution:

| Consumer | Expected Status | Skips If |
|---|---|---|
| `DocumentProcessingPipeline` | `UPLOADED` | Status is not UPLOADED |
| `ClassificationPipeline` | `PROCESSED` | Status is not PROCESSED (logs "Skipping classification — status is X") |
| `ClassificationEnforcementConsumer` | `CLASSIFIED` or `REVIEW_REQUIRED` | Status is neither |
| `PipelineExecutionEngine.executePhase2()` | `CLASSIFIED` or `REVIEW_REQUIRED` | Status is neither |

These guards protect against:
- Duplicate messages (RabbitMQ redelivery)
- Cancelled documents that were already in a queue
- Race conditions between competing consumers (now fixed — see below)

---

## Bug Fixes Applied (April 2026)

### Fix 1: Stale Document Overwrite in PipelineExecutionEngine

**Problem:** In `executePhase2()`, the `doc` variable was loaded once at the start (line 218). When `handleGovernance()` ran, it called `enforcementService.enforce(event)` which returned a **new copy** of the document with classification fields set (categoryName, sensitivityLabel, etc.) and saved it. But `executePhase2()` then continued using the **stale original `doc`** and saved it again at line 284, overwriting all the enforcement changes. The document ended up with status `CLASSIFIED` and no classification metadata.

**Fix:** After any handler that saves its own copy of the document (`governance`, `humanReview`), reload `doc` from the database:

```java
if ("governance".equals(nodeType) || "humanReview".equals(nodeType)) {
    doc = documentService.getById(docId);
}
```

Applied in both `executePhase2()` and `executeFromNode()`.

### Fix 2: Stale Document Overwrite in Phase 1

**Problem:** Same root cause as Fix 1 but in `executePhase1()`. The `doc` variable was loaded once at the start of the loop. `handleTextExtraction()` called `documentService.updateStatus(PROCESSING)` which did a fresh `findById` and saved with the new status. But the next loop iteration saved the stale `doc` (still `UPLOADED`), overwriting the status. By the time the LLM worker received the `DocumentProcessedEvent`, the document's status in MongoDB was `UPLOADED` instead of `PROCESSED`, so the status guard in `ClassificationPipeline` skipped it: `"Skipping classification — status is UPLOADED (expected PROCESSED)"`.

**Fix:** After `textExtraction` and `piiScanner` handlers, reload `doc` from MongoDB — same pattern as the Phase 2 fix.

### Fix 3: Missing Classification Fields on Review Path

**Problem:** When the engine's condition node routed to `handleHumanReview` (low confidence), it only set the status to `REVIEW_REQUIRED` without copying classification fields (`categoryName`, `sensitivityLabel`, `confidence`, etc.) from the `DocumentClassifiedEvent` onto the `DocumentModel`. Documents in the review queue had no category or sensitivity — they were invisible/broken in the UI. The legacy `ClassificationEnforcementConsumer` didn't have this problem because it always called `enforcementService.enforce()` first (which copies all fields), then decided review vs inbox.

**Fix:** `handleHumanReview` now copies `categoryId`, `categoryName`, `sensitivityLabel`, `tags`, `confidence`, `classificationResultId`, and `classifiedAt` from the event onto the document before setting `REVIEW_REQUIRED` status.

### Fix 4: Competing Consumers on Classified Queue

**Problem:** `gls-app-assembly` had `pipeline.execution-engine.enabled=true`, but `gls-governance-enforcement` and `gls-document-processing` did not define this property. Their `@ConditionalOnProperty(matchIfMissing=true)` annotations meant their legacy consumers **stayed active** alongside the engine consumers. RabbitMQ saw 2 consumers on `gls.documents.classified` and round-robined messages — some went to the engine (which had the stale-overwrite bug), some to the legacy consumer (which worked correctly).

**Fix:** Added `pipeline.execution-engine.enabled` to both worker services' `application.yaml` and `application-docker.yaml`, defaulting to `false` but overridable via `PIPELINE_EXECUTION_ENGINE_ENABLED` env var. Set this env var to `"true"` in `docker-compose.yml` for both `doc-processor` and `governance-enforcer` containers.

---

## Class Reference

### Entry Points

| Class | Module | Responsibility |
|---|---|---|
| `DocumentController` | gls-app-assembly | Upload, reprocess, and rerun endpoints. Publishes initial events. |

### Consumers (one active per queue depending on mode)

| Class | Module | Queue | Mode | ConditionalOnProperty |
|---|---|---|---|---|
| `PipelineExecutionConsumer` | gls-app-assembly | ingested + classified | Engine | `enabled=true` |
| `DocumentProcessingPipeline` | gls-document-processing | ingested | Legacy | `enabled=false`, matchIfMissing=true |
| `ClassificationPipeline` | gls-llm-orchestration | processed | **Both** | None — always active |
| `ClassificationEnforcementConsumer` | gls-governance-enforcement | classified | Legacy | `enabled=false`, matchIfMissing=true |

### Processing Logic

| Class | Module | Responsibility |
|---|---|---|
| `PipelineExecutionEngine` | gls-app-assembly | Graph walker. Phase 1 (extraction→PII→accelerators→LLM handoff), Phase 2 (condition→governance/review). |
| `TextExtractionService` | gls-document-processing | Tika + Tesseract. Reads EXTRACTOR block config. |
| `PiiPatternScanner` | gls-document | Regex-based PII scanning. Reads REGEX_SET block patterns. |
| `ClassificationPromptBuilder` | gls-llm-orchestration | Builds the system+user prompt for the LLM. |
| `EnforcementService` | gls-governance-enforcement | Applies classification to document, PII escalation, retention, storage tier migration. |

### Domain Services

| Class | Module | Responsibility |
|---|---|---|
| `DocumentService` | gls-document | CRUD, status transitions, `setError()`, `clearErrorForReprocess()`, slug generation, audit events. |
| `GovernanceService` | gls-governance | Taxonomy, metadata schemas, correction history, sensitivity definitions, retention schedules. |
| `ObjectStorageService` | gls-document | MinIO operations — upload, download, copy, delete, bucket management. |
| `PipelineStatusNotifier` | gls-document | SSE broadcaster for real-time pipeline monitoring. |

### Configuration

| Class | Module | Responsibility |
|---|---|---|
| `RabbitMqConfig` (gls-app-assembly) | gls-app-assembly | Declares all 3 queues + exchange. Always active. |
| `RabbitMqConfig` (gls-document-processing) | gls-document-processing | Declares ingested + processed queues. Legacy mode only. |
| `RabbitMqConfig` (gls-llm-orchestration) | gls-llm-orchestration | Declares processed + classified queues. Always active. |
| `RabbitMqConfig` (gls-governance-enforcement) | gls-governance-enforcement | Declares classified queue. Legacy mode only. |

### Events (shared records in gls-document)

| Event | Published By | Consumed By | Routing Key |
|---|---|---|---|
| `DocumentIngestedEvent` | DocumentController | PipelineExecutionConsumer / DocumentProcessingPipeline | `document.ingested` |
| `DocumentProcessedEvent` | PipelineExecutionEngine / DocumentProcessingPipeline | ClassificationPipeline | `document.processed` |
| `DocumentClassifiedEvent` | ClassificationPipeline | PipelineExecutionConsumer / ClassificationEnforcementConsumer | `document.classified` |

---

## Sequence Diagram — Engine Mode Happy Path

```
User              DocumentController    RabbitMQ          PipelineExecConsumer   PipelineExecEngine    ClassificationPipeline   EnforcementService
  │                      │                  │                    │                      │                       │                     │
  │── upload file ──────►│                  │                    │                      │                       │                     │
  │                      │── ingest() ─────►│                    │                      │                       │                     │
  │                      │   (UPLOADED)      │                    │                      │                       │                     │
  │                      │── publish ───────►│ ingested           │                      │                       │                     │
  │                      │                  │──────────────────►│                      │                       │                     │
  │                      │                  │                    │── executePhase1() ──►│                       │                     │
  │                      │                  │                    │                      │── extract text         │                     │
  │                      │                  │                    │                      │   (PROCESSING)         │                     │
  │                      │                  │                    │                      │── scan PII             │                     │
  │                      │                  │                    │                      │   (PROCESSED)          │                     │
  │                      │                  │                    │                      │── publish ────────────►│ processed            │
  │                      │                  │                    │                      │                       │──────────────────────►│
  │                      │                  │                    │                      │                       │   (CLASSIFYING)       │
  │                      │                  │                    │                      │                       │── call LLM            │
  │                      │                  │                    │                      │                       │── save result         │
  │                      │                  │                    │                      │                       │   (CLASSIFIED)        │
  │                      │                  │                    │                      │                       │── publish ───────────►│ classified
  │                      │                  │                    │◄─────────────────────────────────────────────────────────────────────│
  │                      │                  │                    │── executePhase2() ──►│                       │                     │
  │                      │                  │                    │                      │── condition node       │                     │
  │                      │                  │                    │                      │── governance node ────────────────────────────►│
  │                      │                  │                    │                      │                       │                     │── enforce()
  │                      │                  │                    │                      │                       │                     │   set category
  │                      │                  │                    │                      │                       │                     │   set sensitivity
  │                      │                  │                    │                      │                       │                     │   PII escalation
  │                      │                  │                    │                      │                       │                     │   retention
  │                      │                  │                    │                      │                       │                     │   storage tier
  │                      │                  │                    │                      │◄─────────────────────────────────────────────│
  │                      │                  │                    │                      │   (INBOX)              │                     │
  │                      │                  │                    │                      │── save doc             │                     │
  │                      │                  │                    │                      │── done                 │                     │
```

## Sequence Diagram — Unhappy Path (Classification Failure)

```
PipelineExecEngine          RabbitMQ          ClassificationPipeline       DocumentService
       │                        │                       │                        │
       │── publish processed ──►│                       │                        │
       │                        │──────────────────────►│                        │
       │                        │                       │── status → CLASSIFYING ►│
       │                        │                       │── call LLM              │
       │                        │                       │   [LLM throws error]    │
       │                        │                       │                        │
       │                        │                       │── save AI usage log     │
       │                        │                       │   (status: FAILED)      │
       │                        │                       │                        │
       │                        │                       │── setError() ──────────►│
       │                        │                       │   CLASSIFICATION_FAILED │── set lastError
       │                        │                       │   stage: CLASSIFICATION │── set lastErrorStage
       │                        │                       │                        │── set failedAt
       │                        │                       │                        │── create audit event
       │                        │                       │                        │── notify SSE subscribers
       │                        │                       │                        │
       │                        │                       │   [no classified event  │
       │                        │                       │    published — pipeline │
       │                        │                       │    stops here]          │
```

The document is now visible in the monitoring page with status `CLASSIFICATION_FAILED`, the error message, and the stage. An admin can click "Retry" which calls `clearErrorForReprocess()` → resets to `UPLOADED` → re-enters the pipeline.
