# Phase 3: Accelerator Nodes — Testing Guide

> After `docker compose up --build -d`, verify that the 4 accelerator node types work correctly in the visual pipeline editor and execution engine: template fingerprint, rules engine, smart truncation, and similarity cache.

---

## Prerequisites

1. **Phase 2 working** — The execution engine must be enabled and handling documents end-to-end. Run through the Phase 2 testing guide first.
2. **Login** — `http://localhost/login` > `admin@governanceledstore.co.uk` / `ChangeMe123!`
3. **Configure LLM** — Settings > AI & Classification > set your Anthropic API key and provider
4. **Have classified documents** — Upload and classify at least 3-5 documents via the normal pipeline so the similarity cache and template fingerprint have candidates to match against.

---

## Test 1: Accelerator nodes appear in the pipeline editor palette

**What to verify:** The 4 new node types appear in the visual editor and can be dragged onto the canvas.

1. Go to **AI > Pipelines** > click "Document Ingestion Pipeline"
2. In the **Node Palette** (left sidebar), confirm a new **ACCELERATORS** category appears between PROCESSING and LOGIC:
   - **Template Fingerprint** — teal, fingerprint icon
   - **Rules Engine** — orange, checklist icon
   - **Smart Truncation** — gray, scissors icon
   - **Similarity Cache** — cyan, search icon
3. Drag each accelerator node onto the canvas — they should render correctly with handles (input top, output bottom)
4. Connect them between PII Scanner and AI Classification (the intended position for accelerators)

---

## Test 2: Rules engine skips LLM for matching documents

**What to verify:** A rules engine node with a matching rule auto-classifies the document and skips the LLM entirely.

### Setup

1. Open the pipeline editor
2. Drag a **Rules Engine** node onto the canvas
3. Connect it between **PII Scanner** (output) → **Rules Engine** (input) → **AI Classification** (input)
   - Disconnect PII Scanner → AI Classification first
   - Connect: PII Scanner → Rules Engine → AI Classification
4. Click the Rules Engine node to open the inspector
5. Click **+ Add Rule** and configure:
   - Field: `File Name`
   - Operator: `Contains`
   - Value: `invoice` (or a keyword in your test filename)
   - Category name: `Finance > Invoices & Receipts` (or any existing category)
   - Sensitivity: `INTERNAL`
6. Click **Save**

### Test

1. Upload a file with "invoice" in the filename (e.g. `acme-invoice-2026.pdf`)
2. Watch the API logs:
   ```bash
   docker logs gls-api --tail 50 -f 2>&1 | grep "\[Engine\|RulesEngine\|ACCELERATOR"
   ```
3. Expected log output:
   ```
   [Engine] Phase 1 starting for document: <id>
   [RulesEngine] Rule matched for doc <id> — fileName contains 'invoice' → Finance > Invoices & Receipts
   [Engine] Accelerator 'rulesEngine' short-circuited LLM for doc <id> → Finance > Invoices & Receipts
   [EngineConsumer] Accelerator short-circuited LLM for <id> — running Phase 2 immediately
   [Engine] Phase 2 starting for document: <id>
   ```
4. The LLM worker should NOT receive this document:
   ```bash
   docker logs gls-llm-worker --tail 10 | grep "<doc-id>"
   ```
   Should show no entry.

5. Verify the document:
   ```bash
   curl -s "http://localhost/api/proxy/documents/<doc-id>" | python3 -c "
   import json, sys; d = json.load(sys.stdin)
   print(f\"Status: {d['status']}\")
   print(f\"Category: {d.get('categoryName')}\")
   print(f\"Classification result: {d.get('classificationResultId')}\")
   "
   ```
   - Status should be `GOVERNANCE_APPLIED` or `REVIEW_REQUIRED` (depending on condition node threshold)
   - Category should be whatever you configured in the rule

### Test 2b: Non-matching filename falls through to LLM

1. Upload a file WITHOUT "invoice" in the name (e.g. `meeting-notes.pdf`)
2. The rules engine should miss → document proceeds to AI Classification → LLM classifies normally
3. Expected logs: `rulesEngine → MISS`, then `Published to LLM queue`

---

## Test 3: Template fingerprint learns and matches

**What to verify:** The template fingerprint service can learn from classified documents and auto-classify future documents with the same structure.

### Setup: Learn fingerprints from existing documents

The template fingerprint accelerator needs to learn templates from existing classified documents. Use the API to trigger learning:

```bash
# Get a classified document ID
DOC_ID=$(curl -s "http://localhost/api/proxy/documents?status=GOVERNANCE_APPLIED&size=1" | python3 -c "
import json, sys
data = json.load(sys.stdin)
docs = data.get('content', data) if isinstance(data, dict) else data
print(docs[0]['id'] if docs else '')
")

echo "Learning from document: $DOC_ID"
```

> Note: In the current implementation, template fingerprints are learned when the accelerator encounters a classified document. For testing, you need to upload a document through the normal pipeline first (without the accelerator), then add the accelerator and upload a structurally similar document.

### Test: Add template fingerprint to pipeline

1. Open the pipeline editor
2. Drag a **Template Fingerprint** node between PII Scanner and AI Classification
3. Set threshold to `0.80` in the inspector (lower for easier matching during testing)
4. Save the pipeline

### Test: Upload a structurally similar document

1. Upload a document that's structurally similar to one already classified (same template, different data)
2. Watch the logs:
   ```bash
   docker logs gls-api --tail 50 -f 2>&1 | grep "\[TemplateFP\|ACCELERATOR"
   ```
3. If a match is found: `[TemplateFP] Match found for doc <id>` → LLM skipped
4. If no match: `templateFingerprint → MISS` → proceeds to LLM

---

## Test 4: Smart truncation reduces text before LLM

**What to verify:** The smart truncation node reduces the extracted text sent to the LLM, but does NOT skip the LLM.

### Setup

1. Open the pipeline editor
2. Drag a **Smart Truncation** node between PII Scanner and AI Classification
3. In the inspector, set:
   - Max Characters: `5000` (use slider)
   - Include headers: checked
   - Include table of contents: checked
   - Include signatures: checked
4. Save the pipeline

### Test

1. Upload a large document (10+ pages PDF)
2. Watch the logs:
   ```bash
   docker logs gls-api --tail 50 -f 2>&1 | grep "\[SmartTrunc\|ACCELERATOR"
   ```
3. Expected: `Smart truncation: 45000 → 5000 chars` (actual numbers will vary)
4. The document should STILL go to the LLM for classification — smart truncation only reduces text, it doesn't skip the LLM
5. Verify the LLM received the request:
   ```bash
   docker logs gls-llm-worker --tail 10 -f 2>&1 | grep "Classification"
   ```

### Test 4b: Short documents pass through unchanged

1. Upload a very short document (< 5000 chars)
2. Expected log: `Text already within limit` — no truncation applied

---

## Test 5: Similarity cache matches near-duplicate documents

**What to verify:** The similarity cache detects near-duplicate documents and reuses an existing classification.

### Prerequisites

You need at least one document with status `GOVERNANCE_APPLIED` in the database of the same MIME type as your test document.

### Setup

1. Open the pipeline editor
2. Drag a **Similarity Cache** node between PII Scanner and AI Classification
3. Set threshold to `0.70` in the inspector (lower for easier matching during testing)
4. Set max candidates to `100`
5. Save the pipeline

### Test: Upload a near-duplicate

1. Take a previously classified document and make a minor edit (change one paragraph, update a date)
2. Upload the edited version
3. Watch the logs:
   ```bash
   docker logs gls-api --tail 50 -f 2>&1 | grep "\[SimilarityCache\|ACCELERATOR"
   ```
4. If similarity is above threshold: `[SimilarityCache] Match found for doc <id> — similar to <other-id> (similarity: 0.85)` → LLM skipped
5. Verify classification reused:
   ```bash
   curl -s "http://localhost/api/proxy/documents/<doc-id>" | python3 -c "
   import json, sys; d = json.load(sys.stdin)
   print(f\"Status: {d['status']}\")
   print(f\"Category: {d.get('categoryName')}\")
   "
   ```

### Test 5b: Dissimilar documents fall through

1. Upload a completely different document type
2. Expected: `similarityCache → MISS` → proceeds to LLM

---

## Test 6: Accelerator cascade (multiple accelerators)

**What to verify:** When multiple accelerators are chained, they execute in graph order. The first match wins and skips all remaining accelerators AND the LLM.

### Setup

1. Open the pipeline editor
2. Chain all 3 classifying accelerators between PII Scanner and AI Classification:
   ```
   PII Scanner → Template Fingerprint → Rules Engine → Similarity Cache → Smart Truncation → AI Classification
   ```
3. Configure each with rules/thresholds that will test the cascade
4. Save

### Test

1. Upload a document that matches the rules engine (e.g. filename contains "invoice")
2. Expected:
   - Template fingerprint: MISS (no learned template)
   - Rules engine: HIT → short-circuit
   - Similarity cache: NEVER REACHED (skipped because rules engine matched)
   - AI Classification: NEVER REACHED
3. Verify in logs that only template fingerprint and rules engine executed

---

## Test 7: Accelerator nodes can be reordered

**What to verify:** Admins can drag accelerator nodes to change their execution order.

1. In the pipeline editor, move **Similarity Cache** before **Rules Engine** in the graph
2. Save the pipeline
3. Upload a document
4. Verify in logs that Similarity Cache evaluates before Rules Engine

---

## Test 8: Accelerator nodes can be removed without code changes

**What to verify:** Admins can remove accelerator nodes and the pipeline still works.

1. In the pipeline editor, delete all accelerator nodes
2. Reconnect PII Scanner directly to AI Classification
3. Save
4. Upload a document — it should follow the normal LLM classification path
5. No errors in logs

---

## Test 9: Accelerator statistics in monitoring

**What to verify:** Accelerator hits and misses appear in the pipeline monitoring logs.

1. Go to **Monitoring** page
2. Upload a few documents with accelerators active
3. The pipeline log should show entries like:
   - `ACCELERATOR | rulesEngine → HIT: Finance > Invoices & Receipts | 3ms`
   - `ACCELERATOR | templateFingerprint → MISS | 12ms`
   - `ACCELERATOR | Smart truncation: 45000 → 5000 chars | 8ms`

---

## Quick Smoke Test Checklist

| # | Test | Expected | Pass? |
|---|------|----------|-------|
| 1 | 4 accelerator nodes in palette | ACCELERATORS category with 4 items | |
| 2a | Rules engine HIT | Matching filename → LLM skipped, doc classified | |
| 2b | Rules engine MISS | Non-matching filename → falls through to LLM | |
| 3 | Template fingerprint (if templates learned) | Similar structure → LLM skipped | |
| 4a | Smart truncation reduces text | Large doc text truncated, LLM still called | |
| 4b | Smart truncation passes short docs | Short doc text unchanged | |
| 5a | Similarity cache HIT | Near-duplicate → LLM skipped | |
| 5b | Similarity cache MISS | Different doc → falls through to LLM | |
| 6 | Accelerator cascade | First match wins, remaining skipped | |
| 7 | Reorder accelerators | Execution follows graph order | |
| 8 | Remove all accelerators | Normal LLM path works | |
| 9 | Monitoring shows accelerator logs | HIT/MISS entries with timing | |

---

## Troubleshooting

- **Accelerator not running?** Check the pipeline visual nodes were saved. Open the editor, verify the accelerator is connected in the graph, and click Save.
- **Rules engine not matching?** The field comparison is case-insensitive. Check the rule's field/operator/value in the inspector. Verify the rule list is valid JSON in the node's config.
- **Similarity cache always misses?** You need existing documents with status `GOVERNANCE_APPLIED` and the same MIME type. Check `maxCandidates` isn't set too low.
- **Template fingerprint always misses?** Templates need to be learned first. The service learns by comparing document structure hashes. Upload documents with identical structure (same template) to build the fingerprint database.
- **Smart truncation not reducing text?** Check `maxChars` in the inspector. If the document text is already shorter than maxChars, no truncation occurs.
- **Phase 2 not running after accelerator?** Check the consumer logs for `Accelerator short-circuited LLM ... running Phase 2 immediately`. If missing, the `Phase1Result` may not be propagating correctly.
