# Claude Prompt: Wire Pipeline Execution Engine

> Copy this entire file as a prompt to a fresh Claude Code session in the `/Users/woollers/dev/governance-led-storage` directory.

---

## Context

You are working on **IG Central**, an AI-powered Information Governance platform. The project has a visual pipeline editor (n8n-style) where admins build document processing workflows. Phase 1 (block wiring) is complete — the 3 hardcoded consumers now read their configuration from the Block Library. Phase 2 replaces the fixed 3-consumer architecture with a single execution engine that walks the visual graph.

## Current Architecture

### What exists:

**Backend (Java 21, Spring Boot 4, Maven multi-module):**
- `gls-document-processing` — text extraction + PII scan consumer, **reads config from EXTRACTOR block** (maxTextLength, extractDublinCore, extractMetadata)
- `gls-llm-orchestration` — LLM classification consumer, **reads prompt from PROMPT block** and **review threshold from ROUTER block** (falls back to AppConfigService)
- `gls-mcp-server` — 10 MCP tools the LLM calls during classification
- `gls-governance-enforcement` — governance enforcement, **reads toggles from ENFORCER block** (applyRetention, migrateStorageTier, enforcePolicies)

**Frontend (Next.js 16, React 19, TypeScript):**
- Visual pipeline editor at `web/src/components/pipeline-editor/` with 9 node types (trigger, textExtraction, piiScanner, aiClassification, condition, governance, humanReview, notification, errorHandler)
- Block Library with 5 block types (PROMPT, REGEX_SET, EXTRACTOR, ROUTER, ENFORCER)

**Data (MongoDB):**
- `pipeline_definitions` collection — stores pipelines with `steps[]`, `visualNodes[]`, `visualEdges[]`
- `pipeline_blocks` collection — versioned processing blocks with content configuration
- Steps have `blockId` and `blockVersion` fields **linked to blocks by the seeder and visual editor**

### The Remaining Problem:

1. ~~**Pipeline steps are not linked to blocks**~~ — ✅ Done (Phase 1)
2. **The visual graph is not used for execution** — the 3 consumers run in a fixed order regardless of the pipeline definition
3. **Condition nodes, error handlers, and human review routing are visual-only** — the backend has no branch execution logic
4. ~~**Block content is partially used**~~ — ✅ Done (Phase 1) — all 5 block types now drive their respective pipeline stages

## What Needs to Change

### Phase 1: Wire Blocks to Existing Execution (Non-Breaking) — ✅ COMPLETED

All 5 block types now drive their respective pipeline stages. Every change falls back to defaults if no block is found.

**What was done:**

1. **EXTRACTOR block → TextExtractionService** ✅
   - `TextExtractionService.extract()` now has an overloaded form accepting `maxTextLength`, `extractDublinCore`, `extractMetadata`
   - `DocumentProcessingPipeline` loads the active EXTRACTOR block via `PipelineBlockRepository.findByTypeAndActiveTrueOrderByNameAsc(EXTRACTOR)` and passes config to the extraction call
   - Falls back to defaults (500K chars, dublin core on, metadata on) if no block exists

2. **ROUTER block → ClassificationPipeline** ✅
   - New `getReviewThreshold()` method checks the active ROUTER block's `threshold` value first
   - Falls back to `AppConfigService.getValue("pipeline.confidence.review_threshold", 0.7)` if no ROUTER block
   - `PipelineBlockRepository` injected into `ClassificationPipeline` constructor

3. **ENFORCER block → EnforcementService** ✅
   - `enforce()` now loads the active ENFORCER block and reads `applyRetention`, `migrateStorageTier`, `enforcePolicies` booleans
   - Retention and storage tier migration are conditional on these toggles
   - `PipelineBlockRepository` injected into `EnforcementService` constructor

4. **Block IDs linked in seeder** ✅
   - `seedPipelineBlocks()` now runs BEFORE `seedDefaultPipeline()` and returns `Map<String, String>` of block type → block ID
   - Each pipeline step has `blockId` set: Text Extraction → EXTRACTOR, PII Scan → REGEX_SET, LLM Classification → PROMPT, Governance Enforcement → ENFORCER
   - For existing databases, `seedPipelineBlocks()` returns IDs from already-saved blocks

5. **Frontend handleSave** ✅ (already correct, no changes needed)
   - `PipelineEditor.tsx` `handleSave` maps `d.blockId` → step `blockId` and `block?.activeVersion` → step `blockVersion`

**Files changed (Phase 1):**
- `backend/gls-document-processing/.../TextExtractionService.java` — new overloaded `extract()` with config params
- `backend/gls-document-processing/.../DocumentProcessingPipeline.java` — loads EXTRACTOR block, passes config
- `backend/gls-llm-orchestration/.../ClassificationPipeline.java` — `getReviewThreshold()` reads ROUTER block
- `backend/gls-governance-enforcement/.../EnforcementService.java` — reads ENFORCER block toggles
- `backend/gls-app-assembly/.../GovernanceDataSeeder.java` — reordered seeding, block IDs linked to steps

### Phase 2: Pipeline-Driven Execution (Breaking Change) — ✅ COMPLETED

The hardcoded 3-consumer pipeline has been replaced with a single `PipelineExecutionEngine` that walks the visual graph. The LLM worker is unchanged — it still consumes `document.processed` events independently.

**What was done:**

1. **`DocumentModel.pipelineNodeId`** ✅ — Tracks the document's current position in the visual graph (null when pipeline is complete)

2. **Legacy consumers made conditional** ✅ — `@ConditionalOnProperty(name = "pipeline.execution-engine.enabled", havingValue = "false", matchIfMissing = true)` added to:
   - `DocumentProcessingPipeline` + its `RabbitMqConfig` (gls-document-processing)
   - `ClassificationEnforcementConsumer` + its `RabbitMqConfig` (gls-governance-enforcement)
   - When running as standalone workers, these default to active. In gls-app-assembly (engine enabled), they're disabled.

3. **`gls-app-assembly` wired to worker modules** ✅
   - `gls-document-processing` and `gls-governance-enforcement` added as Maven deps
   - `co.uk.wolfnotsheep.docprocessing` and `co.uk.wolfnotsheep.enforcement` added to `scanBasePackages`
   - `RabbitMqConfig` expanded to declare all 3 queues (ingested, processed, classified)
   - `pipeline.execution-engine.enabled: true` set in `application.yaml`

4. **`PipelineExecutionEngine`** ✅ — New service in `gls-app-assembly/services/pipeline/` that:
   - Loads the pipeline's visual graph and topologically sorts nodes (Kahn's algorithm)
   - **Phase 1** (on `document.ingested`): walks trigger → textExtraction → piiScanner → aiClassification, publishes to LLM queue, pauses
   - **Phase 2** (on `document.classified`): resumes from after aiClassification, evaluates condition nodes (branching TRUE/FALSE), runs governance/humanReview/notification
   - Reads block config from linked blocks on each node (falls back to active blocks by type)
   - Supports disabled nodes, error handlers, and nested condition branching
   - Tracks position via `doc.pipelineNodeId`

5. **`PipelineExecutionConsumer`** ✅ — RabbitMQ listener that delegates ingested events to Phase 1 and classified events to Phase 2

6. **`EnforcementService` refactored** ✅ — `enforce()` now only applies governance rules (retention, storage tier, policies, metadata) and returns the `DocumentModel` without setting status. Callers own the status decision:
   - Legacy consumer: sets status based on `event.requiresHumanReview()`
   - Engine: the graph's condition/humanReview nodes set the status

7. **`ClassificationPipeline` cleaned up** ✅ — LLM worker always sets status to `CLASSIFIED` (never `REVIEW_REQUIRED`). The `requiresHumanReview` flag remains on the event for the legacy consumer.

**Architecture:**

```
document.ingested → [Engine Phase 1] → trigger → textExtraction → piiScanner
                                        → publishes to document.processed queue

document.processed → [LLM Worker — unchanged] → ClassificationPipeline
                                        → publishes to document.classified queue

document.classified → [Engine Phase 2] → condition → governance / humanReview → notification
```

**Node type handlers:**

| Node Type | Handler | What It Does |
|---|---|---|
| `trigger` | No-op | Entry point, passes through |
| `textExtraction` | `TextExtractionService` | Extract text, reads EXTRACTOR block config |
| `piiScanner` | `PiiPatternScanner` | Regex PII detection with dismissal context |
| `aiClassification` | Publish to RabbitMQ | Async boundary — Phase 1 stops here |
| `condition` | Evaluate field/operator/threshold | Routes to TRUE or FALSE branch via edges |
| `governance` | `EnforcementService.enforce()` | Apply retention, policies; sets GOVERNANCE_APPLIED |
| `humanReview` | Set REVIEW_REQUIRED | Routes document to review queue |
| `notification` | Log + status notifier | Emits notification event |
| `errorHandler` | (error path only) | Skipped during normal flow |

**Files changed (Phase 2):**
- `backend/gls-document/.../DocumentModel.java` — added `pipelineNodeId` field
- `backend/gls-document-processing/.../DocumentProcessingPipeline.java` — added `@ConditionalOnProperty`
- `backend/gls-document-processing/.../config/RabbitMqConfig.java` — added `@ConditionalOnProperty`
- `backend/gls-governance-enforcement/.../ClassificationEnforcementConsumer.java` — added `@ConditionalOnProperty`, now sets status after `enforce()`
- `backend/gls-governance-enforcement/.../config/RabbitMqConfig.java` — added `@ConditionalOnProperty`
- `backend/gls-governance-enforcement/.../EnforcementService.java` — `enforce()` returns `DocumentModel`, no longer sets status
- `backend/gls-llm-orchestration/.../ClassificationPipeline.java` — always sets CLASSIFIED status
- `backend/gls-app-assembly/pom.xml` — added gls-document-processing and gls-governance-enforcement deps
- `backend/gls-app-assembly/.../GlsApplication.java` — added docprocessing and enforcement to scanBasePackages
- `backend/gls-app-assembly/.../config/RabbitMqConfig.java` — declares all 3 queues (ingested, processed, classified)
- `backend/gls-app-assembly/.../services/pipeline/PipelineExecutionEngine.java` — **NEW** — the graph-walking engine
- `backend/gls-app-assembly/.../services/pipeline/PipelineExecutionConsumer.java` — **NEW** — RabbitMQ listener
- `backend/gls-app-assembly/src/main/resources/application.yaml` — added `pipeline.execution-engine.enabled: true`

### Phase 3: Accelerator Nodes (New Node Types) — ✅ COMPLETED

Added 4 new node types in the visual editor and backend. They slot between `piiScanner` and `aiClassification` in the visual graph, allowing admins to configure a pre-classification acceleration chain that reduces LLM calls.

**What was done:**

1. **`AcceleratorResult` record** ✅ — Shared result type for all classifying accelerators with `matched`, classification fields, and `acceleratorType`

2. **`TemplateFingerprintService`** ✅ — Hashes document structure (headings, layout patterns) via SHA-256, checks against `template_fingerprints` MongoDB collection. Includes `learnFromDocument()` for indexing classified docs.

3. **`RulesEngineService`** ✅ — Evaluates ordered rules (field + operator + value → classification). Supports: fileName, mimeType, text, fileSize fields; contains, startsWith, endsWith, matches (regex), equals, keywordDensity operators. First match wins.

4. **`SmartTruncationService`** ✅ — Extracts key sections (first/last pages, headings, TOC, signatures) to reduce token count. Does NOT produce a classification — modifies `doc.extractedText` before the LLM node.

5. **`SimilarityCacheService`** ✅ — N-gram shingling with Jaccard similarity against previously classified documents of the same MIME type. Configurable threshold and max candidates.

6. **`Phase1Result` return type** ✅ — `PipelineExecutionEngine.executePhase1()` now returns `Phase1Result(skippedLlm, classificationEvent)`. When an accelerator short-circuits, the consumer immediately runs Phase 2 synchronously.

7. **Engine node handlers** ✅ — `templateFingerprint`, `rulesEngine`, `similarityCache` route through `handleClassifyingAccelerator()` → `applyAcceleratorResult()` which creates a `DocumentClassificationResult` and synthetic `DocumentClassifiedEvent`. `smartTruncation` routes through `handleSmartTruncation()`.

8. **Frontend: 4 new node types** ✅ — Added to `PipelineNodes.tsx` (icons, colors, handles) and `PipelineEditor.tsx` (ACCELERATORS palette category, inspector panels with threshold sliders, rule list builder, truncation toggles, minimap colors).

**Files changed (Phase 3):**
- `backend/gls-governance/.../TemplateFingerprint.java` — **NEW** — MongoDB model for document structure fingerprints
- `backend/gls-governance/.../TemplateFingerprintRepository.java` — **NEW** — Repository for fingerprint lookups
- `backend/gls-app-assembly/.../accelerators/AcceleratorResult.java` — **NEW** — Shared result record
- `backend/gls-app-assembly/.../accelerators/TemplateFingerprintService.java` — **NEW** — Structure hashing + matching
- `backend/gls-app-assembly/.../accelerators/RulesEngineService.java` — **NEW** — Rule evaluation engine
- `backend/gls-app-assembly/.../accelerators/SmartTruncationService.java` — **NEW** — Key section extraction
- `backend/gls-app-assembly/.../accelerators/SimilarityCacheService.java` — **NEW** — N-gram similarity matching
- `backend/gls-app-assembly/.../PipelineExecutionEngine.java` — Added `Phase1Result`, accelerator handlers, `applyAcceleratorResult()`
- `backend/gls-app-assembly/.../PipelineExecutionConsumer.java` — Checks `Phase1Result.skippedLlm()`, runs Phase 2 immediately
- `backend/gls-document/.../DocumentRepository.java` — Added `findByStatusAndMimeType()`
- `web/src/components/pipeline-editor/PipelineNodes.tsx` — 4 new node type definitions
- `web/src/components/pipeline-editor/PipelineEditor.tsx` — ACCELERATORS palette, inspector panels, summaries, minimap colors

**Testing guide:** `PHASE-3-ACCELERATOR-TESTING-GUIDE.md`

**Files to read first:**
- `backend/gls-app-assembly/.../services/pipeline/PipelineExecutionEngine.java` — the graph-walking engine (add new node type handlers here)
- `web/src/components/pipeline-editor/PipelineNodes.tsx` — 9 existing node types (add 4 new ones)
- `web/src/components/pipeline-editor/PipelineEditor.tsx` — palette categories and inspector (add new palette entries + inspector panels)
- `backend/gls-governance/.../PipelineBlock.java` — block model (may need new BlockType values for accelerator config)
- `backend/gls-governance/.../PipelineDefinition.java` — VisualNode record (no changes needed — `data` map handles accelerator config)

**New node types:**

1. **`templateFingerprint`** — Hash document structure (headings, layout), check against known templates, skip LLM if match found. Needs:
   - Backend: `TemplateFingerprintService` that hashes document structure and compares against a `template_fingerprints` MongoDB collection
   - Engine handler: if match found with confidence above threshold, auto-classify and skip remaining pre-classification nodes + aiClassification
   - Frontend: inspector with threshold slider, "Learn from classified docs" button
   - Icon: `Fingerprint`, color: teal

2. **`rulesEngine`** — Evaluate filename patterns, keyword density, source/sender rules before LLM. Needs:
   - Backend: `RulesEngineService` that evaluates a list of rules (field + operator + value → classification)
   - Engine handler: if a rule matches, auto-classify and skip LLM
   - Frontend: inspector with rule list builder (field + operator + value + classification), add/remove rules, priority ordering
   - Icon: `ListChecks`, color: orange

3. **`smartTruncation`** — Reduce text sent to LLM by extracting only headers, first/last pages, TOC. Needs:
   - Backend: `SmartTruncationService` that extracts key sections from document text
   - Engine handler: replaces `doc.extractedText` with truncated version before aiClassification
   - Frontend: inspector with max chars slider, toggles for include-headers/include-toc/include-signatures
   - Icon: `Scissors`, color: gray

4. **`similarityCache`** — Embed text with a small model, check vector similarity against previously classified documents. Needs:
   - Backend: `SimilarityCacheService` that generates embeddings and queries Elasticsearch or a vector store
   - Engine handler: if similar document found above threshold, auto-classify and skip LLM
   - Frontend: inspector with similarity threshold slider, embedding model selector, cache stats display
   - Icon: `Search`, color: cyan

**How accelerators interact with the engine:**

Accelerators that produce a classification (templateFingerprint, rulesEngine, similarityCache) should:
1. Create a `DocumentClassificationResult` (same as the LLM does)
2. Set the document status to `CLASSIFIED`
3. Skip all remaining pre-classification nodes AND the `aiClassification` node
4. The engine should continue directly to the post-classification phase (condition → governance/humanReview)

This means the engine's Phase 1 loop needs a "skip to Phase 2" mechanism when an accelerator short-circuits the LLM.

**Suggested engine changes:**
- Add a `Phase1Result` return type with fields: `skippedLlm: boolean`, `classificationEvent: DocumentClassifiedEvent` (if short-circuited)
- If `skippedLlm`, the engine immediately runs Phase 2 synchronously instead of waiting for the LLM queue
- If not skipped, current behaviour: publish to LLM queue and wait

**Testing guide:** `PHASE-2-ENGINE-TESTING-GUIDE.md` covers the execution engine. Phase 3 tests should verify:
- Each accelerator can short-circuit the LLM when a match is found
- Documents that don't match any accelerator still reach the LLM
- Accelerator statistics are tracked (hit rate, accuracy vs LLM)
- Admin can reorder accelerators in the visual editor to change the cascade priority

## Key Files Reference

```
# Backend — pipeline execution engine (Phase 2)
backend/gls-app-assembly/.../services/pipeline/PipelineExecutionEngine.java   # Graph-walking engine (Phase 1 + Phase 2)
backend/gls-app-assembly/.../services/pipeline/PipelineExecutionConsumer.java # RabbitMQ listener → delegates to engine
backend/gls-app-assembly/.../config/RabbitMqConfig.java                       # All 3 queues (ingested, processed, classified)

# Backend — legacy consumers (disabled when engine active)
backend/gls-document-processing/.../DocumentProcessingPipeline.java    # Text extraction + PII consumer (conditional)
backend/gls-llm-orchestration/.../ClassificationPipeline.java          # LLM classification consumer (unchanged)
backend/gls-governance-enforcement/.../ClassificationEnforcementConsumer.java  # Enforcement consumer (conditional)
backend/gls-governance-enforcement/.../EnforcementService.java         # Governance rules (no status setting)

# Backend — models and routing
backend/gls-governance/.../PipelineDefinition.java                     # Pipeline model (steps + visual graph)
backend/gls-governance/.../PipelineBlock.java                          # Block model (versioned config)
backend/gls-governance/.../services/PipelineRoutingService.java        # Resolves which pipeline to use
backend/gls-document/.../DocumentModel.java                            # Document model (pipelineNodeId tracks position)

# Backend — configuration
backend/gls-document/.../PiiPatternScanner.java                        # Loads patterns from REGEX_SET block
backend/gls-llm-orchestration/.../ClassificationPromptBuilder.java     # Loads prompt from PROMPT block
backend/gls-platform/.../AppConfigService.java                         # Runtime config from MongoDB

# Frontend — visual editor
web/src/components/pipeline-editor/PipelineEditor.tsx                   # 3-column editor (palette, canvas, inspector)
web/src/components/pipeline-editor/PipelineNodes.tsx                    # 9 node type definitions (add 4 for Phase 3)
web/src/app/(protected)/ai/pipelines/page.tsx                          # Pipelines list + editor page

# Seeder
backend/gls-app-assembly/.../GovernanceDataSeeder.java                 # Seeds pipeline + blocks on first startup

# Backend — accelerator services (Phase 3)
backend/gls-app-assembly/.../services/pipeline/accelerators/AcceleratorResult.java       # Shared result record
backend/gls-app-assembly/.../services/pipeline/accelerators/TemplateFingerprintService.java  # Structure hashing
backend/gls-app-assembly/.../services/pipeline/accelerators/RulesEngineService.java      # Rule evaluation
backend/gls-app-assembly/.../services/pipeline/accelerators/SmartTruncationService.java  # Text reduction
backend/gls-app-assembly/.../services/pipeline/accelerators/SimilarityCacheService.java  # N-gram similarity
backend/gls-governance/.../models/TemplateFingerprint.java                               # Fingerprint MongoDB model

# Testing
PHASE-1-BLOCK-TESTING-GUIDE.md                                         # Phase 1 testing guide
PHASE-2-ENGINE-TESTING-GUIDE.md                                        # Phase 2 testing guide
PHASE-3-ACCELERATOR-TESTING-GUIDE.md                                   # Phase 3 testing guide
```

## Acceptance Criteria

### Phase 1 (wire blocks) — ✅ COMPLETED:
- [x] Each pipeline step has a `blockId` linking it to the corresponding block
- [x] TextExtractionService reads config from the EXTRACTOR block (maxTextLength, extractDublinCore, extractMetadata)
- [x] Confidence threshold reads from ROUTER block if present, falls back to AppConfigService
- [x] EnforcementService respects ENFORCER block toggles (applyRetention, migrateStorageTier, enforcePolicies)
- [x] Changing a block's content in the Block Library UI changes the pipeline's behaviour on next document (no restart needed)
- [x] Backend compiles, frontend builds — no breaking changes (all reads fall back to defaults)

### Phase 2 (execution engine) — ✅ COMPLETED:
- [x] A new document follows the visual graph path, not the hardcoded path
- [x] Condition nodes correctly route documents down TRUE or FALSE branches
- [x] Error handler nodes catch failures and retry/fail as configured
- [x] The execution survives the async LLM boundary (resumes after classification)
- [x] Admin can reorder nodes in the visual editor and the execution order changes
- [x] Admin can disable a node and it's skipped during execution
- [x] EnforcementService separated from status routing — callers own the status decision
- [x] LLM worker always sets CLASSIFIED — downstream handles review routing
- [x] Legacy consumers still work when `pipeline.execution-engine.enabled=false`

### Phase 3 (accelerators) — ✅ COMPLETED:
- [x] Template fingerprint node skips LLM for known document structures
- [x] Rules engine node classifies simple documents without LLM
- [x] Smart truncation node reduces token count sent to LLM
- [x] Similarity cache node skips LLM for near-duplicate documents
- [x] Admin can add/remove accelerator nodes without code changes
- [x] Accelerator cascade: first match wins, remaining skipped
- [x] Phase1Result mechanism allows accelerators to short-circuit LLM and run Phase 2 immediately
- [x] Backend compiles, frontend builds — no breaking changes

## UI Changes Required

### Phase 1: Block Linking UX

**Files to read:**
- `web/src/components/pipeline-editor/PipelineEditor.tsx` — the 3-column editor
- `web/src/components/pipeline-editor/PipelineNodes.tsx` — 9 node types with `WorkflowNodeData`
- `web/src/app/(protected)/ai/pipelines/page.tsx` — pipeline list + editor shell
- `web/src/app/(protected)/ai/blocks/page.tsx` — block library page

**What to do:**

1. **Node inspector must show block linkage clearly**: When a user clicks a node (e.g., `piiScanner`), the right-panel inspector should:
   - Show a prominent "Linked Block" section with a dropdown of compatible blocks
   - Show the block's active version number and a "View Block" link to the Block Library
   - Show a summary of the block's content (e.g., "12 PII patterns" or "System prompt: 847 chars")
   - Show a warning if no block is linked ("This node has no block — it will use default behaviour")
   - Allow version pinning (dropdown of available versions, default = "Active")

2. **Node status must reflect block linkage**: The green/amber status dot on each node should be:
   - **Green**: block linked and configured
   - **Amber**: no block linked (using defaults)
   - **Red**: linked block is inactive or has no active version

3. **Pipeline validation on save**: Before saving, validate that all required nodes have blocks linked. Show a confirmation modal listing any unlinked nodes: "2 nodes have no linked block and will use default behaviour. Save anyway?"

4. **Default pipeline template**: When creating a new pipeline, offer a "Standard Template" button that creates the default 5-node flow (trigger → textExtraction → piiScanner → aiClassification → condition → governance) with blocks auto-linked to the active blocks of each type.

### Phase 2: Execution Visualisation

**What to do:**

1. **Live execution path on document view**: When viewing a document that's being processed, show which pipeline node it's currently at. The classification panel already has a processing status section — extend it to show the pipeline graph with the current node highlighted (pulsing blue), completed nodes (green), and pending nodes (gray).

2. **Pipeline execution history per document**: Add a "Pipeline" accordion section to the classification panel that shows:
   - Which pipeline was used
   - Which nodes executed, in what order, with timing
   - Which branch was taken at condition nodes
   - Which blocks were used (with version numbers)
   - Link to the AI Usage Log for the LLM call

3. **Monitoring pipeline tab enhancement**: The monitoring page's Pipeline tab (`web/src/app/(protected)/monitoring/page.tsx`, tab `monTab === "pipeline"`) should show the pipeline funnel with clickable stages. Clicking a stage (e.g., "Classifying") should filter the documents table below to show only documents in that stage.

### Phase 3: Accelerator Node Configuration

**What to do:**

1. **New node types in palette**: Add these to the palette in `PipelineNodes.tsx` and the drag palette in `PipelineEditor.tsx`:
   - `templateFingerprint` — icon: Fingerprint, color: teal. Inspector: threshold slider, "Learn from" button that indexes existing classified documents
   - `rulesEngine` — icon: ListChecks, color: orange. Inspector: rule list builder (field + operator + value + classification), add/remove rules, priority ordering
   - `smartTruncation` — icon: Scissors, color: gray. Inspector: max chars slider, toggles for include-headers/include-toc/include-signatures
   - `similarityCache` — icon: Search, color: cyan. Inspector: similarity threshold slider, embedding model selector, cache stats display

2. **Inspector panels for each**: Each accelerator node needs a rich inspector with:
   - Configuration fields specific to the node type
   - A "Test" button that runs the accelerator on a selected document and shows what would happen (would it skip LLM? what would it classify as?)
   - Statistics panel showing: how many documents this accelerator has handled, hit rate, accuracy vs LLM classification

3. **Accelerator chain visualisation**: When multiple accelerators are chained before the LLM node, show the cascade visually — each accelerator that "misses" passes to the next one, only documents that pass all accelerators reach the LLM.

### Frontend Conventions

- All pages are `"use client"` React components
- State management: React hooks (useState, useCallback, useEffect) — no Redux/Zustand
- API calls: `import api from "@/lib/axios/axios.client"` — base URL is `/api/proxy`
- Icons: `lucide-react` — import individually (e.g., `import { Brain } from "lucide-react"`)
- Toasts: `import { toast } from "sonner"`
- Modals: use `FormModal` from `@/components/form-modal` for forms, or inline `fixed inset-0` overlays for custom modals
- Forms: every input must have `id` and a `<label htmlFor>` for accessibility
- Tables: use `ResizableTh` from `@/components/resizable-th` for table headers
- Loading: use `SkeletonTable`/`SkeletonCards` from `@/components/skeleton`
- Empty states: use `EmptyState` from `@/components/empty-state`
- Tailwind CSS 4 (v4 import syntax: `@import "tailwindcss"`)
- Next.js 16 with App Router, TypeScript strict

## Build & Test

```bash
# Backend compile
cd backend && ./mvnw compile -DskipTests

# Frontend build
cd web && npx next build

# Docker rebuild all
docker compose up --build -d

# Check logs
docker logs gls-doc-processor --tail 20
docker logs gls-llm-worker --tail 20
docker logs gls-governance-enforcer --tail 20
```

## Important Conventions

- Java 21, Spring Boot 4.0.2, MongoDB (not JPA)
- Jackson 3.x: use `tools.jackson.databind.json.JsonMapper`, NOT `com.fasterxml.jackson`
- Config-driven: all behaviour configurable from UI, not hardcoded
- No `.env` for config — use `AppConfigService` (reads from `app_config` MongoDB collection)
- Every AI call logged to `ai_usage_log` collection via `AiUsageLogRepository`
- Pipeline status notifier: `statusNotifier.emitLog()` for real-time monitoring
- Blocks are loaded by type from `PipelineBlockRepository.findByTypeAndActiveTrueOrderByNameAsc()`
