# Phase 2: Pipeline Execution Engine — Testing Guide

> After `docker compose up --build -d`, verify that the execution engine walks the visual graph, handles the async LLM boundary, routes via condition nodes, and that the legacy path still works when the engine is disabled.

---

## Prerequisites

1. **Fresh database** — Drop the database so the seeder runs cleanly:
   ```bash
   docker exec gls-mongo mongosh -u root -p devpassword123 --eval 'use governance_led_storage_main; db.dropDatabase()'
   docker compose up --build -d
   ```
2. **Login** — `http://localhost/login` > `admin@governanceledstore.co.uk` / `ChangeMe123!`
3. **Configure LLM** — Settings > AI & Classification > set your Anthropic API key and provider
4. **Save the default pipeline graph** — The seeder creates pipeline steps but not visual nodes. The engine needs visual nodes to walk:
   1. Go to **AI > Pipelines**
   2. Click "Document Ingestion Pipeline"
   3. The default graph should appear: trigger > Text Extraction > PII Scanner > AI Classification > Confidence Check > Governance / Human Review
   4. Click **Save** — this persists the visual nodes and edges to MongoDB

**Verify visual nodes saved:**
```bash
curl -s http://localhost/api/proxy/admin/pipelines | python3 -c "
import json, sys
p = json.load(sys.stdin)
for pipeline in (p if isinstance(p, list) else [p]):
    nodes = pipeline.get('visualNodes', [])
    edges = pipeline.get('visualEdges', [])
    print(f\"{pipeline['name']}: {len(nodes)} nodes, {len(edges)} edges\")
    for n in nodes:
        print(f\"  {n['id']} ({n['type']}): {n.get('label', '?')}\")
"
```
Expected: 7 nodes (trigger, textExtraction, piiScanner, aiClassification, condition, governance, humanReview) and 6 edges.

---

## Test 1: Engine consumes ingested events (Phase 1)

**What to verify:** The execution engine (not the legacy doc-processor) handles text extraction and PII scanning.

1. Check that `pipeline.execution-engine.enabled: true` is set in `gls-app-assembly`'s `application.yaml`
2. Upload a document (any PDF or DOCX)
3. Watch the API container logs (the engine runs in gls-app-assembly):
   ```bash
   docker logs gls-api --tail 50 -f 2>&1 | grep "\[Engine"
   ```
4. You should see:
   ```
   [Engine] Phase 1 starting for document: <id> (<filename>)
   [Engine] Pipeline 'Document Ingestion Pipeline' has 7 nodes
   [Engine] Published to LLM queue for document: <id> — pausing pipeline at node classify-1
   ```

**Verify the old doc-processor is NOT consuming:**
```bash
docker logs gls-doc-processor --tail 20 2>&1 | grep "Processing document"
```
Should show no new entries after your upload (the legacy consumer is disabled).

**Verify document status progression:**
```bash
# Should show PROCESSING → PROCESSED within a few seconds
curl -s "http://localhost/api/proxy/documents/<doc-id>" | python3 -c "
import json, sys; d = json.load(sys.stdin)
print(f\"Status: {d['status']}, Pages: {d.get('pageCount', '?')}, PII: {d.get('piiStatus', '?')}\")
"
```

---

## Test 2: Async LLM boundary and Phase 2 resume

**What to verify:** After the LLM classifies the document, the engine resumes from the condition node and routes correctly.

1. After uploading in Test 1, wait for classification to complete (watch monitoring page or logs)
2. Watch the API logs for Phase 2:
   ```bash
   docker logs gls-api --tail 50 -f 2>&1 | grep "\[Engine\|EngineConsumer"
   ```
3. You should see:
   ```
   [EngineConsumer] Received classified event for document: <id> (category: <name>)
   [Engine] Phase 2 starting for document: <id> (category: <name>)
   [Engine] Condition 'Confidence Check': confidence >= 0.8 = <value> → TRUE/FALSE
   ```
4. Depending on confidence:
   - **TRUE** (confidence >= 0.8): `[Engine] Governance applied for document: <id>` → status becomes `GOVERNANCE_APPLIED`
   - **FALSE** (confidence < 0.8): `[Engine] Document <id> routed to human review` → status becomes `REVIEW_REQUIRED`

**Verify final status:**
```bash
curl -s "http://localhost/api/proxy/documents/<doc-id>" | python3 -c "
import json, sys; d = json.load(sys.stdin)
print(f\"Status: {d['status']}, Category: {d.get('categoryName', '?')}, Sensitivity: {d.get('sensitivityLabel', '?')}\")
"
```

---

## Test 3: Condition node branching

**What to verify:** The condition node routes documents down the correct branch based on confidence.

### Test 3a: Force the TRUE branch (high confidence)

Upload a clear, unambiguous document (e.g. a simple invoice or employment contract). Most documents classify with confidence > 0.8.

- Expected: condition evaluates TRUE → governance node runs → status = `GOVERNANCE_APPLIED`
- Check the review queue (`/review`) — document should NOT appear

### Test 3b: Force the FALSE branch (low confidence)

1. Go to **AI > Pipelines** > edit "Document Ingestion Pipeline"
2. Click the **Confidence Check** condition node
3. In the inspector, change the threshold to `0.99` (almost nothing will pass)
4. Click **Save**
5. Upload a document
6. Expected: condition evaluates FALSE → humanReview node runs → status = `REVIEW_REQUIRED`
7. Check the review queue (`/review`) — document SHOULD appear

**Reset:** Change the condition threshold back to `0.8` after testing.

### Test 3c: Change condition field/operator

1. Edit the condition node config to use operator `<` instead of `>=`
2. Save and upload a document
3. Expected: branching is inverted — high confidence goes to review, low goes to governance

---

## Test 4: Governance node applies rules correctly

**What to verify:** EnforcementService applies classification results, retention, and storage tier without setting status (the engine handles status).

1. Upload a document that classifies with high confidence (passes condition)
2. After governance is applied, check:
   ```bash
   curl -s "http://localhost/api/proxy/documents/<doc-id>" | python3 -c "
   import json, sys; d = json.load(sys.stdin)
   print(f\"Status: {d['status']}\")
   print(f\"Category: {d.get('categoryName')}\")
   print(f\"Sensitivity: {d.get('sensitivityLabel')}\")
   print(f\"Retention: {d.get('retentionScheduleId')}\")
   print(f\"Governance applied at: {d.get('governanceAppliedAt')}\")
   print(f\"Summary: {d.get('summary', 'none')[:80]}\")
   "
   ```
3. Status should be `GOVERNANCE_APPLIED` (set by the engine, not by EnforcementService)
4. Classification fields (categoryName, sensitivityLabel, tags) should be populated
5. Governance fields (retentionScheduleId, governanceAppliedAt) should be populated

---

## Test 5: Disabled nodes are skipped

**What to verify:** A node with `disabled: true` in its data is skipped during execution.

1. Go to **AI > Pipelines** > edit the pipeline
2. This test requires adding `"disabled": "true"` to a node's config via the inspector or direct API:
   ```bash
   # Get pipeline ID
   PIPELINE_ID=$(curl -s http://localhost/api/proxy/admin/pipelines | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['id'])")

   # Manually set disabled on the PII Scanner node via the pipeline update API
   # (or use the visual editor if it supports disabling nodes)
   ```
3. Upload a document
4. In the logs, look for: `[Engine] Skipping disabled node: PII Scanner (piiScanner)`
5. The document should still classify successfully — just without PII scan results
6. Re-enable after testing

---

## Test 6: Pipeline position tracking (pipelineNodeId)

**What to verify:** The document's `pipelineNodeId` field tracks the current position in the graph.

1. Upload a document
2. While it's processing, query the document quickly:
   ```bash
   curl -s "http://localhost/api/proxy/documents/<doc-id>" | python3 -c "
   import json, sys; d = json.load(sys.stdin)
   print(f\"Pipeline node: {d.get('pipelineNodeId', 'null')}\")
   print(f\"Pipeline ID: {d.get('pipelineId', 'null')}\")
   "
   ```
3. During Phase 1: `pipelineNodeId` should progress through trigger-1, extract-1, pii-1, classify-1
4. During LLM wait: `pipelineNodeId` = `classify-1` (paused at the async boundary)
5. After Phase 2 completes: `pipelineNodeId` = `null` (pipeline complete)

---

## Test 7: Error handling

**What to verify:** Failures set the correct error status and don't silently swallow errors.

### Test 7a: Text extraction failure

1. Upload a corrupted file (e.g. a .pdf that's actually a renamed .txt with garbage bytes)
2. Expected: status = `PROCESSING_FAILED`, `lastError` populated, `lastErrorStage` = `PHASE_1`
3. Check monitoring page shows the failed document

### Test 7b: LLM classification failure

1. Set an invalid API key in Settings > AI & Classification
2. Upload a document — extraction and PII scan should succeed
3. The LLM call should fail — status = `CLASSIFICATION_FAILED`
4. Note: this error is set by the LLM worker (unchanged), not the engine

---

## Test 8: Legacy path regression (engine disabled)

**What to verify:** When the execution engine is disabled, the old 3-consumer pipeline still works identically.

1. **Temporarily disable the engine** by setting the environment variable:
   ```bash
   # In docker-compose.yml or .env, add for the API service:
   PIPELINE_EXECUTION_ENGINE_ENABLED=false
   # OR edit application.yaml: pipeline.execution-engine.enabled: false
   ```
2. Rebuild and restart:
   ```bash
   docker compose up --build -d
   ```
3. Upload a document
4. Watch the legacy worker logs:
   ```bash
   docker logs gls-doc-processor --tail 20 -f   # Should show "Processing document: ..."
   docker logs gls-governance-enforcer --tail 20 -f  # Should show "Enforcing governance for document: ..."
   ```
5. The legacy path should handle the full flow:
   - `gls-doc-processor`: extracts text, scans PII, publishes to `document.processed`
   - `gls-llm-worker`: classifies, sets `CLASSIFIED`, publishes to `document.classified`
   - `gls-governance-enforcer`: applies governance, sets `GOVERNANCE_APPLIED` or `REVIEW_REQUIRED`
6. Verify the final document has the same fields populated as with the engine

**Re-enable the engine** after testing:
```bash
# Remove the override / set back to true
docker compose up --build -d
```

---

## Test 9: LLM worker always sets CLASSIFIED

**What to verify:** The LLM worker no longer sets `REVIEW_REQUIRED` — it always sets `CLASSIFIED`, and the downstream consumer (engine or legacy) handles routing.

1. Upload a document that you expect to have low confidence (ambiguous content)
2. Check the LLM worker logs:
   ```bash
   docker logs gls-llm-worker --tail 30 | grep "classified as"
   ```
3. You should see: `Document <id> classified as <category> ... Human review: true`
4. But the document status at that point should be `CLASSIFIED` (not `REVIEW_REQUIRED`):
   ```bash
   # Check immediately after classification, before enforcement runs
   docker logs gls-api --tail 30 | grep "Condition"
   ```
5. The engine's condition node (or legacy consumer) then decides the final status

---

## Test 10: Multiple documents in parallel

**What to verify:** The engine handles concurrent documents without interference.

1. Upload 3-5 documents in quick succession (drag-and-drop multiple files)
2. Watch the logs — each document should get its own Phase 1 and Phase 2 execution
3. All documents should reach a terminal status (`GOVERNANCE_APPLIED` or `REVIEW_REQUIRED`)
4. No documents should get stuck in an intermediate status

---

## Quick Smoke Test Checklist

| # | Test | Expected | Pass? |
|---|------|----------|-------|
| 1 | Upload PDF with engine enabled | Reaches GOVERNANCE_APPLIED or REVIEW_REQUIRED | |
| 2 | Engine logs show Phase 1 + Phase 2 | `[Engine]` entries in API logs | |
| 3 | Legacy doc-processor silent | No new processing entries | |
| 4 | Condition routes high confidence to governance | Status = GOVERNANCE_APPLIED | |
| 5 | Condition routes low confidence to review | Status = REVIEW_REQUIRED (set threshold to 0.99) | |
| 6 | Pipeline graph visible in editor | 7 nodes, 6 edges after save | |
| 7 | Disable engine, legacy path works | doc-processor + enforcer handle flow | |
| 8 | Re-enable engine, upload works | Engine handles flow again | |
