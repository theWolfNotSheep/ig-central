# Low-Code Workflow Engine: Nuxeo Comparison & Build Plan

> What we have, what Nuxeo has, what we need to build to make the visual pipeline a real low-code workflow engine.

---

## Side-by-Side Comparison

| Capability | Nuxeo | GLS (Current) | Gap |
|-----------|-------|---------------|-----|
| **Visual designer** | Nuxeo Studio (SaaS, proprietary) | React Flow canvas with palette, inspector, auto-layout | Parity on core canvas. Nuxeo has form designer; we don't. |
| **Storage model** | Workflows are documents in Nuxeo repository | `pipeline_definitions` in MongoDB with `visualNodes`/`visualEdges` | Equivalent. |
| **Node types** | Start, stop, task (human), automation, fork, merge | 17 types: trigger, extraction, PII, LLM, condition, governance, review, accelerators, HTTP | We have more domain-specific nodes; Nuxeo has more workflow primitives. |
| **Expression language** | MVEL with rich context bindings (`WorkflowVariables`, `NodeVariables`, `CurrentDate`, `Fn.*`) | Basic condition evaluator (confidence >, piiCount ==, sensitivity ==, fileType ==) | **Major gap.** Our conditions are hardcoded to 4 fields with simple operators. No expression language. |
| **Human tasks** | Full task service: assignees (static + MVEL), due dates, forms, buttons (approve/reject/custom), reassignment, delegation | Review queue with approve/override/reject. No configurable task forms, no assignee expressions, no due dates. | **Major gap.** No configurable task routing, no custom buttons, no dynamic assignees. |
| **Notifications** | Template-based email on task creation, with variable substitution | Node defined but **not implemented** (coming soon) | Gap — definition exists, no executor. |
| **Escalation rules** | Per-node timer-based rules with MVEL conditions. Periodic evaluator runs against all suspended nodes. | `StaleDocumentRecoveryTask` — global 10-minute stale detection. No per-node escalation. | **Significant gap.** No configurable escalation per node. |
| **Parallel execution** | Fork node + merge node with `one` (XOR) or `all` (AND) semantics | Not implemented. Sequential only. | **Major gap.** |
| **Sub-workflows** | Full sub-route support with variable passing. Parent suspends until child completes. | Not implemented. Flat graph only. | Gap — needed for complex governance flows. |
| **Variables** | Workflow-scoped + node-scoped variables with XSD schemas. Accessible in expressions as `WorkflowVariables["key"]`. | `sharedContext` map passed between nodes. No typed schema, no node-scoped variables. | Gap — no typed variables, no expression access. |
| **Automation chains** | Reusable logic units (input chain, output chain, transition chain). Built visually in Studio or as JavaScript. | Pipeline blocks (PROMPT, REGEX_SET, EXTRACTOR, ROUTER, ENFORCER) with version history. | Partial parity. Blocks are similar to chains but more limited in scope. |
| **Versioning** | Workflow model lifecycle (draft → validated). Instance cloning on start. | No pipeline versioning. Save overwrites. Block versioning exists but pipeline-level does not. | Gap — need pipeline version history. |
| **Audit/Events** | 15+ lifecycle events (beforeRouteStart, afterStepRunning, taskCreated, etc.) | `PipelineRun` + `NodeRun` records. `AuditEvent` on document status changes. | Partial parity — we track execution, but no before/after hooks. |
| **REST API** | Full JSON serialisation for workflows, tasks, pending work. JSON enrichers on documents. | Pipeline CRUD endpoints. No task API, no pending-work enrichers. | Gap for task management API. |
| **Extensibility** | Custom operations via Java, JavaScript automation scripts, or Nuxeo packages. | New node types require Java Spring beans. No scripting, no plugin system. | **Major gap.** No user-extensible logic. |

---

## What Nuxeo Gets Right (and we should learn from)

### 1. Separation of graph structure from execution logic

Nuxeo's nodes define WHAT happens (which automation chain to run, which transition to follow) — the HOW is delegated to reusable automation chains. This means non-developers can rewire the graph without touching the logic.

**Our equivalent:** Pipeline blocks serve a similar role (a node references a block, the block contains the config). But we don't have arbitrary automation chains — our blocks are typed (PROMPT, REGEX_SET, etc.) and each type has a fixed handler.

**What to build:** Allow blocks to contain lightweight scripting (JavaScript or SpEL expressions) that can transform data, set variables, or make decisions without requiring a Java handler.

### 2. Rich expression language everywhere

Nuxeo uses MVEL expressions for:
- Transition conditions: `NodeVariables["button"] == "approve"`
- Task assignees: `WorkflowVariables["participants"]`
- Due dates: `CurrentDate.days(5)`
- Escalation rules: `WorkflowFn.timeSinceTaskWasStarted() > 86400000`

**Our equivalent:** We have a basic condition evaluator that supports 4 fields (confidence, piiCount, sensitivity, fileType) with 6 operators. No expression language.

**What to build:** A proper expression evaluator with access to document fields, classification results, pipeline variables, and date functions.

### 3. First-class human task management

Nuxeo treats human tasks as a core primitive: configurable assignees, due dates, custom buttons, task forms, reassignment, delegation, and notification templates — all set per node.

**Our equivalent:** We have a review queue with approve/override/reject. The routing to review is based on a single confidence threshold. No configurable assignees, no due dates, no custom actions.

**What to build:** A task node that lets admins configure who reviews, what buttons appear, what happens on each button click, and when to escalate.

### 4. Fork/merge for parallel paths

Nuxeo supports forking a workflow into parallel branches and merging them back with AND (wait for all) or OR (continue on first) semantics.

**Our equivalent:** Not implemented. All execution is sequential.

**What to build:** Fork and merge nodes with configurable merge semantics. This enables patterns like "run BERT and LLM in parallel, take the more confident result."

---

## What We Have That Nuxeo Doesn't

| Feature | GLS | Nuxeo |
|---------|-----|-------|
| AI/LLM classification as a first-class node | Built-in with MCP tools, model selection, prompt blocks | Would require custom automation chain + external service |
| BERT accelerator node | Built-in with configurable endpoint and model | Not applicable |
| Similarity cache accelerator | N-gram shingling with configurable threshold | Not applicable |
| Template fingerprinting (planned) | Defined with config schema | Not applicable |
| Block versioning with feedback | Full version history, feedback loop, AI-assisted improvement | Automation chains have no feedback mechanism |
| Pipeline block library | Shared, versioned, reusable blocks across pipelines | Automation chains are reusable but no marketplace/library UI |
| Visual performance indicators | Nodes show latency impact (amber/red badges) | No performance awareness |
| Governance-specific nodes | Retention, storage tier, policy enforcement built-in | Generic — would need custom automation |

**Key insight:** Our system is more domain-specialised (records management + AI classification). Nuxeo is a general-purpose workflow engine. We don't need to replicate all of Nuxeo — we need to add the workflow primitives that make our domain-specific pipeline configurable by admins.

---

## The Build Plan: What to Add

### Phase 1: Expression Language & Condition Upgrade (1 week)

**Problem:** Conditions are limited to 4 hardcoded fields with simple operators. Admins can't express "if document is from HR folder AND confidence < 0.8, route to HR manager."

**Solution:** Replace the basic condition evaluator with Spring Expression Language (SpEL). SpEL is already on the classpath (Spring Boot dependency), well-documented, and supports:

```java
// Current (hardcoded):
if (field.equals("confidence") && operator.equals(">")) {
    return confidence > threshold;
}

// Proposed (SpEL):
ExpressionParser parser = new SpelExpressionParser();
EvaluationContext ctx = new StandardEvaluationContext();
ctx.setVariable("doc", document);
ctx.setVariable("classification", classificationResult);
ctx.setVariable("pipeline", pipelineVariables);
ctx.setVariable("now", Instant.now());
Boolean result = parser.parseExpression(conditionExpression).getValue(ctx, Boolean.class);
```

**Available in expressions:**
```
doc.mimeType == 'application/pdf'
doc.fileSizeBytes > 1048576
doc.uploadedBy == 'admin@company.com'
doc.fileName.contains('CONFIDENTIAL')
doc.traits.contains('INBOUND')
doc.categoryName.startsWith('HR')
classification.confidence > 0.8
classification.sensitivityLabel.name() == 'CONFIDENTIAL'
classification.extractedMetadata['contract_type'] == 'permanent'
pipeline.bertConfidence > 0.9
pipeline.acceleratorHit == true
piiCount > 0
now.isAfter(doc.createdAt.plus(Duration.ofHours(24)))
```

**Frontend change:** Replace the field/operator/value dropdowns in the condition node inspector with a text input that accepts SpEL expressions, plus a cheat sheet of available variables. Keep the simple dropdowns as a "basic mode" with a toggle to "advanced mode" (expression editor).

**Deliverables:**
- `SpelConditionEvaluator` service replacing `BasicConditionEvaluator`
- Expression context builder that populates doc/classification/pipeline variables
- Frontend toggle between basic mode (dropdowns) and advanced mode (expression editor)
- Expression validation endpoint (parse + dry-run against a sample document)

---

### Phase 2: Configurable Human Task Node (1 week)

**Problem:** The review queue is a single global queue with fixed approve/override/reject actions. Admins can't configure who reviews, what actions are available, or what happens next.

**Solution:** Upgrade the `humanReview` node to a configurable task node.

**Node configuration schema:**
```json
{
  "assigneeExpression": "doc.uploadedBy",
  "assigneeRole": "RECORDS_MANAGER",
  "assigneeFallback": "admin@company.com",
  "dueInHours": 48,
  "taskTitle": "Review {{doc.categoryName}} classification",
  "taskInstructions": "This document was classified with {{classification.confidence}} confidence. Please review.",
  "buttons": [
    {"id": "approve", "label": "Approve Classification", "style": "success", "transition": "approved"},
    {"id": "override", "label": "Override Category", "style": "warning", "transition": "overridden", "requiresForm": true},
    {"id": "reject", "label": "Reject & Flag", "style": "danger", "transition": "rejected"},
    {"id": "escalate", "label": "Escalate to Manager", "style": "info", "transition": "escalated"}
  ],
  "escalation": {
    "afterHours": 48,
    "action": "reassign",
    "reassignTo": "admin@company.com",
    "notifyOriginalAssignee": true
  }
}
```

**Visual editor changes:**
- Task node gets output handles for each button (approved, overridden, rejected, escalated)
- Each handle can connect to a different downstream path
- Inspector shows button editor with label, style, and transition name

**Backend changes:**
- `TaskService` — creates task records with assignee, due date, buttons
- `TaskController` — REST API for listing/completing tasks
- Task completion triggers pipeline resumption via the appropriate transition edge
- Escalation scheduler checks overdue tasks periodically

**Data model:**
```
task_items (new collection)
├── id
├── pipelineRunId → pipeline_runs.id
├── nodeRunId → node_runs.id
├── documentId → documents.id
├── assignedTo (userId or role key)
├── title, instructions
├── buttons (list of {id, label})
├── dueAt
├── status (PENDING, COMPLETED, ESCALATED, CANCELLED)
├── completedBy
├── completedAt
├── selectedButton (which button was clicked)
├── formData (map — for override forms)
└── createdAt
```

---

### Phase 3: Pipeline Variables & Context (3–5 days)

**Problem:** Nodes can't share data beyond the `sharedContext` map. There's no typed variable system, no way for admins to define pipeline-level variables, and no way for expressions to read/write them.

**Solution:** Add a variables system inspired by Nuxeo's but simpler.

**Pipeline-level variables** (defined on PipelineDefinition):
```json
{
  "variables": [
    {"name": "reviewThreshold", "type": "number", "default": 0.7, "description": "Confidence below this triggers review"},
    {"name": "escalationContact", "type": "string", "default": "admin@company.com"},
    {"name": "maxRetries", "type": "number", "default": 3},
    {"name": "requireDualApproval", "type": "boolean", "default": false}
  ]
}
```

**Node output variables** (set by node handlers):
```
After textExtraction:
  pipeline.wordCount = 3200
  pipeline.pageCount = 8
  pipeline.hasStructuredHeadings = true

After bertClassifier:
  pipeline.bertTopCategory = "HR.ER.LR"
  pipeline.bertConfidence = 0.87
  pipeline.bertTop3 = ["HR.ER.LR", "HR.ER.CT", "HR.REC.AP"]

After aiClassification:
  pipeline.llmCategory = "HR.ER.LR"
  pipeline.llmConfidence = 0.92
```

**Expressions can read variables:**
```
pipeline.bertConfidence > pipeline.reviewThreshold
pipeline.wordCount > 10000 ? 'long-doc-path' : 'standard-path'
```

**Frontend:** Pipeline editor gets a "Variables" tab where admins define pipeline-level variables with names, types, defaults, and descriptions. The expression editor autocompletes variable names.

---

### Phase 4: Fork & Merge Nodes (1–2 weeks)

**Problem:** All execution is sequential. Can't run BERT and rules engine in parallel, can't fan out to multiple reviewers, can't compare results from two classifiers.

**Solution:** Add fork and merge nodes with configurable semantics.

**Fork node:**
- Multiple outgoing edges (all followed, creating parallel branches)
- Each branch executes independently
- Branches share the same pipeline variables (copy-on-fork, merge-on-join)

**Merge node:**
- Multiple incoming edges
- Configurable merge strategy:
  - `ALL` — wait for all branches to complete (AND-join)
  - `FIRST` — continue when first branch arrives (OR-join, cancel others)
  - `BEST` — wait for all, then pick the result with highest confidence
  - `VOTE` — wait for all, take majority classification

**Implementation approach:**
```
PipelineRun gets new fields:
  activeBranches: Map<String, BranchState>  // branchId → {status, completedNodeKeys}
  mergeBuffer: Map<String, Map<String, Object>>  // mergeNodeId → {branchId → result}
```

When a fork node executes:
1. Create N branch entries in `activeBranches`
2. Add all fork target nodes to the execution queue with a `branchId` tag
3. Each branch executes independently, writing results to `mergeBuffer`

When a merge node is reached:
1. Check if merge condition is met (all/first/best/vote)
2. If not, suspend this branch (WAITING state)
3. If yes, apply merge strategy, continue to downstream nodes

**Use case: parallel classification comparison:**
```
[trigger] → [extract] → [fork]
                           ├── [bertClassifier] ──┐
                           └── [aiClassification] ─┤
                                                   └── [merge (BEST)] → [governance]
```

---

### Phase 5: Implement Missing Nodes (2 weeks)

Complete the 6 "coming soon" nodes that already have frontend definitions:

| Node | Effort | What to build |
|------|--------|---------------|
| **rulesEngine** | 2–3 days | Parse `custom:rulesEditor` JSON → evaluate rules against document fields → return `AcceleratorResult` |
| **notification** | 2–3 days | Spring Mail sender + webhook HTTP POST. Template variable interpolation (`{{doc.fileName}}`, `{{classification.categoryName}}`). |
| **errorHandler** | 3–4 days | Hook into `walkNodes` exception flow. Retry with exponential backoff. Fallback actions: skip, route to review, set custom status. |
| **templateFingerprint** | 5–7 days | Document structure hashing (heading positions, text block layout, font patterns). Template library in MongoDB. Threshold-based matching. |
| **writeDriveLabel** | 3–4 days | Google Drive Labels API v2 integration. Create/update label fields from classification result. |
| **gmailWatcher** | 3–4 days | Gmail API polling trigger. Message parsing + attachment extraction. Deduplication by messageId. |

---

### Phase 6: Sub-Workflows (1 week)

**Problem:** Complex governance flows (e.g., "dual approval for RESTRICTED documents") require nested workflow logic that makes the main pipeline unreadable.

**Solution:** A `subPipeline` node type that references another `PipelineDefinition` and executes it inline.

**Configuration:**
```json
{
  "subPipelineId": "pip_dual_approval",
  "inputMapping": {
    "document": "doc",
    "initialCategory": "classification.categoryName"
  },
  "outputMapping": {
    "finalApproval": "pipeline.approvalResult",
    "approvedBy": "pipeline.approvers"
  }
}
```

**Execution:**
1. Sub-pipeline gets its own `PipelineRun` linked to the parent via `parentPipelineRunId`
2. Parent node enters SUSPENDED state
3. Sub-pipeline executes with mapped input variables
4. On completion, output variables are mapped back to parent context
5. Parent node resumes

---

### Phase 7: Pipeline Versioning & Rollout (3–5 days)

**Problem:** Pipeline saves overwrite the previous version. No rollback, no history, no A/B testing.

**Solution:**
- Snapshot pipeline definition on each save → `pipeline_versions` collection
- Version number auto-increments
- "Active version" pointer (like blocks already have)
- Rollback = set active version pointer to an older version
- Optional: percentage-based routing for A/B testing (10% of docs go to new pipeline version)

---

## Implementation Priority

```
Phase 1: Expression Language         ████░░░░░░  1 week    ← unlocks everything else
Phase 2: Configurable Task Node      ████░░░░░░  1 week    ← human workflow
Phase 3: Pipeline Variables          ███░░░░░░░  3-5 days  ← data flow between nodes
Phase 5: Missing Node Implementations ████████░░  2 weeks   ← production completeness
Phase 4: Fork & Merge               ██████░░░░  1-2 weeks ← parallel execution
Phase 6: Sub-Workflows              ████░░░░░░  1 week    ← complex flow encapsulation
Phase 7: Pipeline Versioning         ███░░░░░░░  3-5 days  ← operational safety

Total: ~7-9 weeks for full low-code parity
```

### The shortest path to "user-configurable workflow"

If you want the fastest route to something an admin can configure without developer involvement:

1. **Expression language** (Phase 1) — conditions become powerful
2. **Configurable task node** (Phase 2) — human steps become flexible
3. **Rules engine implementation** (from Phase 5) — deterministic classification without LLM
4. **Notification node** (from Phase 5) — governance workflows notify stakeholders

These four items (~3 weeks) transform the pipeline from a developer-configured processing chain into an admin-configurable governance workflow.

---

## Architecture Decisions

### Expression language: SpEL vs MVEL vs JavaScript

| | SpEL | MVEL | JavaScript (GraalVM) |
|--|------|------|---------------------|
| Already on classpath | Yes (Spring) | No | No |
| Sandbox safety | Good (configurable) | Weak | Excellent (GraalVM sandbox) |
| Familiar to admins | No | No | Yes |
| Power | Medium | Medium | High |
| Performance | Fast | Fast | Medium |
| **Recommendation** | **Use this** | | |

SpEL is already available via Spring, supports property navigation (`doc.fileName`), method calls (`doc.traits.contains('X')`), ternary expressions, and collection projection. It can be sandboxed to prevent arbitrary code execution.

### Task management: custom vs existing

Build a lightweight task system on MongoDB rather than integrating an external task engine (Camunda, Flowable). Reasons:
- Our task model is records-management-specific (approve/override/reject classification)
- External engines add deployment complexity and licensing concerns
- A single `task_items` collection with a REST API covers 90% of needs
- The review queue frontend already exists — extend it rather than replace it

### Workflow model: keep it document-centric

Nuxeo's insight that "workflows are documents" is powerful but over-engineered for our use case. Our model — pipeline definitions as MongoDB documents with visual graph data — is simpler and sufficient. Don't adopt Nuxeo's pattern of storing each node as a separate document; keep the flat `visualNodes` array.
