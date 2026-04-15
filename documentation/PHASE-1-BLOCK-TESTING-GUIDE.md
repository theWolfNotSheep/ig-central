# Phase 1: Block Wiring — Testing Guide

> After `docker compose up --build -d`, verify that each pipeline stage reads its configuration from the Block Library and that changing a block's content changes the pipeline's behaviour on the next document — with no restart needed.

---

## Prerequisites

1. **Fresh database** — Drop the `governance_led_storage_main` database so the seeder runs cleanly with block IDs linked to pipeline steps:
   ```bash
   docker exec gls-mongo mongosh -u root -p devpassword123 --eval 'use governance_led_storage_main; db.dropDatabase()'
   docker compose up --build -d
   ```
2. **Login** — `http://localhost/login` → `admin@governanceledstore.co.uk` / `ChangeMe123!`
3. **Configure LLM** — Settings > AI & Classification > set your Anthropic API key and provider

---

## Test 1: Verify block IDs are linked to pipeline steps

**What to check:** The seeder now saves blocks first and links their IDs into the pipeline steps.

1. Go to **AI > Pipelines** in the sidebar
2. Click on "Document Ingestion Pipeline"
3. Click each node in the visual editor — the right-hand inspector should show a "Linked Block" dropdown with a block selected:
   - **Text Extraction** → "Tika Text Extractor" (EXTRACTOR)
   - **PII Scan (Pattern)** → "UK PII Pattern Scanner" (REGEX_SET)
   - **LLM Classification** → "Classification Prompt" (PROMPT)
   - **Governance Enforcement** → "Standard Governance Enforcer" (ENFORCER)

**API verification:**
```bash
# Get the pipeline and check blockId on each step
curl -s http://localhost/api/proxy/admin/pipelines | python3 -m json.tool | grep -A2 blockId
```
Every step (except PII Verification which is disabled) should have a non-null `blockId`.

---

## Test 2: EXTRACTOR block controls text extraction

**What it does:** The `TextExtractionService` now reads `maxTextLength`, `extractDublinCore`, and `extractMetadata` from the active EXTRACTOR block.

### Test 2a: Reduce maxTextLength

1. Go to **AI > Blocks** → click "Tika Text Extractor"
2. Edit the draft content — change `maxTextLength` from `500000` to `500`
3. Publish the new version (click "Publish")
4. Upload a document with more than 500 characters of text (any multi-page PDF)
5. After processing, open the document's classification panel
6. The extracted text should be truncated to ~500 characters
7. Check the pipeline log (Monitoring > Pipeline Log) — it should say something like "Extracted 500 chars"

### Test 2b: Disable Dublin Core extraction

1. Edit the EXTRACTOR block — set `extractDublinCore` to `false`, publish
2. Upload a new document
3. After processing, the document's Dublin Core metadata section should be empty
4. **Restore:** Set `extractDublinCore` back to `true` and publish

---

## Test 3: ROUTER block controls review threshold

**What it does:** The `ClassificationPipeline` now reads the confidence threshold from the active ROUTER block before falling back to `AppConfigService`.

### Test 3a: Raise the threshold to force review

1. Go to **AI > Blocks** → click "Confidence Router"
2. Edit the draft — change `threshold` from `0.7` to `0.99`
3. Publish the new version
4. Upload a document and let it classify
5. The document should be routed to **Review Required** (since almost no classification reaches 0.99 confidence)
6. Check the pipeline log — the classification confidence should be below 0.99

### Test 3b: Lower the threshold to auto-approve everything

1. Edit the ROUTER block — change `threshold` to `0.1`, publish
2. Upload another document
3. It should skip review and go straight to **Governance Applied**
4. **Restore:** Set threshold back to `0.7` and publish

### Test 3c: Verify fallback to AppConfigService

1. Deactivate the Confidence Router block (toggle it inactive in the Block Library)
2. Upload a document
3. The threshold should fall back to the Settings value (Settings > Pipeline > `pipeline.confidence.review_threshold` = 0.7)
4. **Restore:** Reactivate the block

---

## Test 4: ENFORCER block controls governance enforcement

**What it does:** The `EnforcementService` now reads `applyRetention`, `migrateStorageTier`, and `enforcePolicies` toggles from the active ENFORCER block.

### Test 4a: Disable retention enforcement

1. Go to **AI > Blocks** → click "Standard Governance Enforcer"
2. Edit the draft — change `applyRetention` to `false`, publish
3. Upload and classify a document that maps to a category with a retention schedule
4. After governance is applied, check the document — **Retention Expires** should be empty/null
5. **Restore:** Set `applyRetention` back to `true` and publish

### Test 4b: Disable storage tier migration

1. Edit the ENFORCER block — set `migrateStorageTier` to `false`, publish
2. Upload and classify a RESTRICTED or HIGHLY RESTRICTED document
3. After governance, the document should stay in the default `gls-documents` bucket (no tier migration)
4. **Restore:** Set `migrateStorageTier` back to `true` and publish

---

## Test 5: REGEX_SET block controls PII scanning (already wired — regression check)

The PII scanner was already reading from the REGEX_SET block. This test confirms it still works.

1. Go to **AI > Blocks** → click "UK PII Pattern Scanner"
2. Edit the draft — add a new pattern:
   ```json
   {
     "name": "Test Pattern",
     "type": "TEST_PII",
     "regex": "\\bTEST\\d{4}\\b",
     "confidence": 0.9
   }
   ```
3. Publish the new version
4. Upload a text file containing "Reference: TEST1234"
5. PII scan should detect "TEST1234" as `TEST_PII`
6. **Restore:** Remove the test pattern and publish

---

## Test 6: PROMPT block controls classification prompt (already wired — regression check)

1. Go to **AI > Blocks** → click "Classification Prompt"
2. Edit the draft — add a line to the `systemPrompt`: "IMPORTANT: Always add the tag 'PHASE1_TEST' to every classification."
3. Publish the new version
4. Upload a document and let it classify
5. Check the classification result — it should include the tag `PHASE1_TEST`
6. Check AI Usage Log (AI > Usage) — the system prompt should include your added line
7. **Restore:** Remove the test line and publish

---

## Test 7: Visual editor saves block links correctly

1. Go to **AI > Pipelines** → open the pipeline editor
2. Click the **PII Scanner** node
3. In the inspector, change the linked block dropdown to a different block (or clear it)
4. Click **Save**
5. Reload the page — the change should persist
6. Restore the original block link and save again

---

## Checklist

| # | Test | Pass? |
|---|------|-------|
| 1 | Block IDs linked in pipeline steps (seeder) | |
| 2a | EXTRACTOR maxTextLength respected | |
| 2b | EXTRACTOR extractDublinCore toggle works | |
| 3a | ROUTER high threshold → review required | |
| 3b | ROUTER low threshold → auto-approve | |
| 3c | ROUTER fallback to AppConfigService | |
| 4a | ENFORCER applyRetention toggle | |
| 4b | ENFORCER migrateStorageTier toggle | |
| 5 | REGEX_SET custom pattern detected | |
| 6 | PROMPT block customisation flows to LLM | |
| 7 | Visual editor saves/loads block links | |

---

## Troubleshooting

- **Blocks not loading?** Check `docker logs gls-doc-processor --tail 30` — you should see log lines like "Loaded X PII patterns from Y REGEX_SET block(s)"
- **Block changes not taking effect?** PII patterns cache for 60 seconds. EXTRACTOR, ROUTER, and ENFORCER blocks are loaded fresh on every document — no cache delay.
- **Pipeline steps still show null blockId?** You need a fresh database. The seeder only runs if `pipeline_definitions` collection is empty. Drop the collection: `docker exec gls-mongo mongosh -u root -p devpassword123 --eval 'use governance_led_storage_main; db.pipeline_definitions.drop(); db.pipeline_blocks.drop()'` then restart.
