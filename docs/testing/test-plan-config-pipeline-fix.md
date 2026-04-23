# Test Plan: AI Configuration, Service Wiring & Pipeline Creation Fix

## What Changed

The AI Settings page, pipeline creation, and processing toggles were cosmetic — values were saved to MongoDB but the backend never read them. This fix makes all configuration functional with a 4-level resolution chain:

```
Per-node config (visual editor inspector)
  -> Global config (AI Settings page -> MongoDB)
    -> Environment variable (.env / docker-compose)
      -> Hardcoded default
```

---

## Pre-Requisites

- All containers running: `docker compose up --build -d`
- Admin login: `admin@governanceledstore.co.uk` / `ChangeMe123!`
- At least one document uploaded for classification testing
- Access to: https://dev.igcentral.com

---

## Test 1: AI Classification Node — Per-Node Config Fields

**What:** The aiClassification node inspector now shows provider, model, temperature, and maxTokens fields.

**Steps:**
1. Go to `/ai#pipelines`
2. Open the **Document Ingestion Pipeline** in the visual editor (grid icon)
3. Click the **AI Classification** node
4. Check the inspector panel on the right

**Expected:**
- [ ] **Provider** dropdown with options: "Global default", "anthropic", "ollama"
- [ ] **Model** text field with placeholder "e.g. claude-sonnet-4-20250514"
- [ ] **Temperature** range slider 0-1 with step 0.05
- [ ] **Max Tokens** number field with min 256, max 16384
- [ ] Each field shows help text about falling back to global default

---

## Test 2: External Service Node in Palette

**What:** A new "External Service" node appears in the palette for wiring to any HTTP service.

**Steps:**
1. In the visual editor, check the **PROCESSING** section of the left palette

**Expected:**
- [ ] "External Service" node appears with globe icon
- [ ] Drag it onto the canvas
- [ ] Click it — inspector shows: serviceUrl, path, method (POST/GET/PUT), authToken (password field with show/hide), timeoutMs, confidenceThreshold
- [ ] authToken field renders as a password input (masked by default)

---

## Test 3: Global Settings Now Actually Work

**What:** Changing model/temperature/maxTokens in AI Settings takes effect immediately without restart.

**Steps:**
1. Go to `/ai#settings`
2. Note the blue info banner: "These are global defaults..."
3. Change **Temperature** from 0.1 to 0.3
4. Click **Save**
5. Upload and classify a document
6. Go to `/ai#usage` and check the latest AI Usage Log entry

**Expected:**
- [ ] Blue info banner visible at top of LLM Provider section
- [ ] Settings save successfully
- [ ] No container restart needed
- [ ] AI Usage Log shows the classification used the updated temperature

**Steps (model change):**
1. Change **Model** to "Claude Haiku 4.5 — fast & cheap"
2. Save
3. Classify another document
4. Check AI Usage Log

**Expected:**
- [ ] Usage log shows `claude-haiku-4-5-20251001` as the model
- [ ] No restart required

**Cleanup:** Change model back to Claude Sonnet 4, temperature back to 0.1.

---

## Test 4: Per-Node Override Beats Global Setting

**What:** Setting model on a specific pipeline node overrides the global setting.

**Steps:**
1. Go to `/ai#pipelines` -> open visual editor for default pipeline
2. Click the AI Classification node
3. Set **Model** to `claude-haiku-4-5-20251001` on the node
4. Save the pipeline
5. Verify global AI Settings still shows Claude Sonnet 4
6. Classify a document
7. Check AI Usage Log

**Expected:**
- [ ] Usage log shows Haiku was used (node override), not Sonnet (global default)

**Steps (clear override):**
1. Clear the model field on the node (empty = use global)
2. Save pipeline
3. Classify another document

**Expected:**
- [ ] Usage log now shows Sonnet (fell back to global)

---

## Test 5: Auto-Classify Toggle

**What:** Turning off auto-classify stops documents at PROCESSED without calling the LLM.

**Steps:**
1. Go to `/ai#settings`
2. Turn **OFF** "Auto-Classify After Extraction"
3. Save
4. Upload a new document
5. Check document status in `/documents` or monitoring

**Expected:**
- [ ] Document reaches PROCESSED status and stops
- [ ] No AI Usage Log entry created (LLM never called)
- [ ] No classification result

**Cleanup:** Turn auto-classify back ON.

---

## Test 6: Auto-Enforce Toggle

**What:** Turning off auto-enforce stops documents at CLASSIFIED without applying governance.

**Steps:**
1. Go to `/ai#settings`
2. Turn **OFF** "Auto-Enforce Governance Rules"
3. Save
4. Upload and let a document classify
5. Check document status

**Expected:**
- [ ] Document reaches CLASSIFIED status
- [ ] No retention schedule or storage tier applied
- [ ] Status does NOT progress to GOVERNANCE_APPLIED or INBOX

**Cleanup:** Turn auto-enforce back ON.

---

## Test 7: Auto-Approve Threshold

**What:** Documents with confidence above the auto-approve threshold skip the review queue.

**Steps:**
1. Go to `/ai#settings`
2. Set **Human Review Threshold** to 0.7
3. Set **Auto-Approve Threshold** to 0.8
4. Save
5. Upload a clear, unambiguous document (e.g. a simple invoice) that should classify with high confidence
6. Check document status and review queue

**Expected:**
- [ ] If confidence >= 0.8: document goes straight to governance, NOT to review queue
- [ ] If confidence between 0.7-0.8: document goes through normal flow
- [ ] If confidence < 0.7: document goes to review queue
- [ ] Check the pipeline log — auto-approved documents should log "auto-approved"

---

## Test 8: New Pipeline Creation — Template Picker

**What:** "New Pipeline" now shows a template picker with 4 templates instead of an empty form.

**Steps:**
1. Go to `/ai#pipelines`
2. Click **New Pipeline**

**Expected:**
- [ ] Template picker modal appears (NOT the old empty form)
- [ ] Shows: Pipeline Name field (required), Description field
- [ ] Shows 4 template cards:
  - **Blank** (1 node)
  - **Standard Classification** (7 nodes)
  - **Quick Classify** (4 nodes)
  - **Accelerated** (9 nodes)
- [ ] Each card shows node count and description
- [ ] Template cards are disabled until name is entered

**Steps (create from template):**
1. Enter name: "Test Pipeline"
2. Click "Standard Classification"

**Expected:**
- [ ] Pipeline created successfully (toast message)
- [ ] Visual editor opens immediately with pre-wired nodes
- [ ] Nodes are: Trigger -> Text Extraction -> PII Scanner -> AI Classification -> Confidence Check -> Governance / Human Review
- [ ] Edges connect nodes correctly
- [ ] Condition node branches: True -> Governance, False -> Human Review

---

## Test 9: Node Type Error Handling

**What:** If the node types API fails, the palette shows an error with retry instead of silently being empty.

**Steps:**
1. Stop the API container: `docker compose stop api`
2. Open visual editor for any pipeline (may need to be already on the page)
3. Check the palette

**Expected:**
- [ ] Error message visible in palette: "Failed to load node types..."
- [ ] Retry button visible

**Steps (recovery):**
1. Restart API: `docker compose start api`
2. Click Retry

**Expected:**
- [ ] Node types load successfully
- [ ] Palette populates with all categories

---

## Test 10: Node Type Upsert on Restart

**What:** New/updated node types are seeded on restart even if types already exist.

**Steps:**
1. Check MongoDB `node_type_definitions` collection:
   ```
   db.node_type_definitions.countDocuments()
   ```
2. Note the count (should be 16 — 15 original + 1 externalService)
3. Check `aiClassification` configSchema:
   ```
   db.node_type_definitions.findOne({key: "aiClassification"}).configSchema
   ```

**Expected:**
- [ ] 16 node type definitions
- [ ] `aiClassification` has `provider`, `model`, `temperature`, `maxTokens` in configSchema.properties
- [ ] `externalService` exists with `executionCategory: "GENERIC_HTTP"` and `httpConfig` populated

---

## Test 11: Dead Config Keys Removed

**What:** Duplicate/unused config keys no longer seeded.

**Steps:**
1. Check MongoDB `app_config` collection for removed keys:
   ```
   db.app_config.findOne({key: "pipeline.llm.model"})
   db.app_config.findOne({key: "pipeline.system_prompt"})
   db.app_config.findOne({key: "pipeline.user_prompt_template"})
   ```

**Expected:**
- [ ] `pipeline.llm.model` — may still exist from previous seed (won't be re-created on fresh deploy)
- [ ] `pipeline.system_prompt` — same
- [ ] `pipeline.user_prompt_template` — same
- [ ] On a fresh database, none of these 3 keys will exist

---

## Test 12: GenericHttpNodeExecutor Auth Token

**What:** External service nodes pass auth tokens as Bearer headers.

**Steps (manual / integration):**
1. Create a pipeline with an External Service node
2. Configure: serviceUrl = `http://httpbin.org`, path = `/headers`, method = `GET`, authToken = `test-token-123`
3. Save and trigger the pipeline on a document
4. Check logs for the HTTP call

**Expected:**
- [ ] Request includes `Authorization: Bearer test-token-123` header
- [ ] Path and method are respected from node config

---

## Test 13: Password Widget in DynamicConfigForm

**What:** Fields with `ui:widget: "password"` render as password inputs.

**Steps:**
1. Open visual editor
2. Drag an **External Service** node onto the canvas
3. Click it — find the **Auth Token** field

**Expected:**
- [ ] Field is masked (shows dots, not plaintext)
- [ ] "Show"/"Hide" toggle button visible
- [ ] Clicking "Show" reveals the token text
- [ ] Clicking "Hide" masks it again

---

## Regression Checks

| Area | What to verify | How |
|------|---------------|-----|
| Existing pipeline | Default pipeline still classifies documents correctly | Upload a doc, verify classification result |
| Review queue | Low-confidence documents still route to review | Upload ambiguous doc, check review queue |
| PII detection | Regex PII scanner still finds NI numbers, emails, etc. | Upload doc with PII, check detection |
| Monitoring | Failed documents still show in monitoring with retry | Force a failure, check monitoring page |
| Blocks | Prompt blocks still override default prompts | Edit a block, classify, verify prompt used |
| Usage log | All classifications still log to AI Usage | Check `/ai#usage` after any classification |
| Google Drive | Drive classification still works (if configured) | Classify a Drive file |

---

## Environment Notes

- **Fresh database**: All 16 node types seeded, all config keys correct, no dead keys
- **Existing database**: Upsert updates existing node types, adds `externalService`, updates `aiClassification` schema. Old dead config keys may still exist but are harmless (nothing reads them)
- **Ollama**: If Ollama is running locally, you can test provider switching. Set provider to "ollama" in Settings or on a node.
