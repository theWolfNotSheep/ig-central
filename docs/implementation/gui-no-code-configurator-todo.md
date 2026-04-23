# Visual Pipeline Builder — Unimplemented Nodes (Coming Soon)

These node types are defined in the pipeline palette but not yet implemented in the execution engine. They are marked `comingSoon: true` and appear greyed out in the visual editor. Each is essential for a production-grade no-code document processing pipeline.

---

## 1. Template Fingerprint (`templateFingerprint`)

**Category:** ACCELERATOR | **Phase:** PRE_CLASSIFICATION

**What it should do:** Hash document structure (layout, headings, table positions) and compare against a library of known templates. If a match exceeds the threshold, skip the LLM entirely and apply the cached classification.

**Why it's needed:**
- **Cost reduction** — organisations process thousands of identical forms (invoices, contracts, HR letters). Fingerprinting avoids paying for LLM classification on every copy.
- **Speed** — sub-second classification vs 10-30s LLM call.
- **Industry standard** — Nuxeo, ABBYY, and Kofax all use template-based fast paths before ML classification.

**Implementation scope:**
- `TemplateFingerPrintService` that hashes document structural features (text block positions, font sizes, table presence)
- Template library stored in MongoDB with linked classification results
- Threshold-based matching (configurable per node, default 0.85)
- Auto-learning: after LLM classifies a new document type, store its fingerprint as a template

---

## 2. Rules Engine (`rulesEngine`)

**Category:** ACCELERATOR | **Phase:** PRE_CLASSIFICATION

**What it should do:** Evaluate user-configured rules against document properties (file name, MIME type, text content). First match wins and applies the configured classification, skipping the LLM.

**Why it's needed:**
- **Deterministic classification** — some documents are always the same category based on filename pattern (e.g., `INV-*.pdf` is always "Invoices")
- **Zero-cost fast path** — no LLM call, no BERT call, instant classification
- **User empowerment** — records managers can define rules without touching code
- **Industry standard** — Apache NiFi's RouteOnAttribute, n8n's IF/Switch nodes, Nuxeo's automation chains all support rule-based routing

**Implementation scope:**
- Rule parser that evaluates the JSON rules from `custom:rulesEditor` widget
- Operators: `contains`, `startsWith`, `endsWith`, `matches` (regex), `equals`
- Fields: `fileName`, `mimeType`, `text` (extracted content)
- Return `AcceleratorResult.hit()` with category/sensitivity from the matched rule

---

## 3. Notification (`notification`)

**Category:** ACTION | **Phase:** POST_CLASSIFICATION

**What it should do:** Send notifications when a document reaches this stage — via email (SMTP) or webhook (HTTP POST).

**Why it's needed:**
- **Governance workflow** — notify records managers when sensitive documents are classified, when retention periods expire, or when documents require review
- **Integration** — webhooks enable Slack, Teams, Jira, or custom system integrations
- **Compliance** — audit requirements often mandate notifications for specific document types (PII, financial, legal)
- **Industry standard** — every workflow platform (n8n, Camunda, Temporal) includes notification/alert nodes

**Implementation scope:**
- Email channel: SMTP integration using Spring Boot mail starter
- Webhook channel: HTTP POST with configurable URL, headers, and JSON payload template
- Template variables: `{{documentName}}`, `{{category}}`, `{{sensitivity}}`, `{{confidence}}`
- Retry on failure with configurable attempts

---

## 4. Write Drive Label (`writeDriveLabel`)

**Category:** ACTION | **Phase:** POST_CLASSIFICATION

**What it should do:** After classification, write the classification result back to the source Google Drive file as a Drive Label (structured metadata that appears in Drive's UI).

**Why it's needed:**
- **In-situ governance** — documents stay in Google Drive but carry their classification as searchable, visible labels
- **Bidirectional sync** — classification in GLS is reflected in the user's Drive without them leaving their familiar environment
- **Compliance visibility** — sensitivity labels, retention dates, and categories visible directly in Drive search and list views
- **Unique differentiator** — this bridges the gap between a records management system and the user's actual file storage

**Implementation scope:**
- Google Drive Labels API v2 integration
- Create/update label fields: category, sensitivity, retention date, classification confidence
- Handle Drive API rate limits and quota
- Error modes: `continue` (log and move on) vs `fail` (stop pipeline)

---

## 5. Error Handler (`errorHandler`)

**Category:** ERROR_HANDLING | **Phase:** ANY

**What it should do:** Catch pipeline failures at specific points and provide configurable retry/fallback logic rather than using the engine's catch-all error handling.

**Why it's needed:**
- **Granular recovery** — different nodes need different error strategies (retry LLM timeout vs skip failed extraction)
- **Fallback paths** — route failed documents to a manual queue instead of marking them as permanently failed
- **Industry standard** — n8n error workflows, Camunda error boundary events, Temporal retry policies all provide per-node error configuration
- **Current gap** — the engine has only a global catch-all. Per-node error routing is the #1 missing control flow feature vs industry platforms

**Implementation scope:**
- Wire `errorHandler` nodes to catch errors from their connected source nodes
- Configurable: `retryCount`, `retryDelay` (with exponential backoff), `fallback` action
- Fallback options: retry, skip, route to review, set custom status
- Error context passed to handler: error message, failed node, attempt count

---

## 6. Gmail Watcher (`gmailWatcher`)

**Category:** TRIGGER | **Phase:** N/A (entry point)

**What it should do:** Poll a Gmail inbox for new messages matching a query, extract attachments, and feed them into the pipeline as documents.

**Why it's needed:**
- **Email-driven records management** — many organisations receive records via email (invoices, contracts, regulatory correspondence)
- **Automated ingestion** — eliminates manual upload for email-sourced documents
- **Query-based filtering** — `from:supplier@example.com has:attachment` to process only relevant emails
- **Industry standard** — n8n Gmail trigger, Zapier email triggers, Microsoft Power Automate email connectors

**Implementation scope:**
- Gmail API integration using existing OAuth2 infrastructure (same as Google Drive)
- Configurable poll interval and Gmail search query
- Attachment extraction with MIME type filtering
- Deduplication (track processed message IDs)
- Support for multiple Gmail accounts

---

## 7. Scheduled Trigger (option on `trigger` node)

**Category:** TRIGGER | **Phase:** N/A (entry point)

**What it should do:** Run a pipeline on a cron schedule — e.g., process all unclassified documents every hour, or scan a Drive folder nightly.

**Why it's needed:**
- **Batch processing** — classify accumulated documents during off-hours
- **Drive folder monitoring** — periodic scan of connected Drive folders for new files
- **Retention enforcement** — daily check for documents past their retention date
- **Industry standard** — every pipeline platform supports cron/schedule triggers (NiFi scheduling, Camunda timer events, Temporal schedules)

**Implementation scope:**
- Spring `@Scheduled` or Quartz integration reading cron expressions from node config
- Document selector: "all UPLOADED", "all in folder X", "all unclassified"
- Concurrency control: don't start a new run if the previous is still in progress
- Run history tracking per scheduled trigger

---

## Priority Order for Implementation

| Priority | Node | Impact | Effort |
|----------|------|--------|--------|
| 1 | **rulesEngine** | High — saves LLM costs on deterministic classifications | Low — rule parser is straightforward |
| 2 | **notification** | High — required for governance workflows | Medium — SMTP + webhook integration |
| 3 | **errorHandler** | High — production resilience | Medium — needs execution engine changes |
| 4 | **templateFingerprint** | High — major cost savings at scale | High — structural hashing algorithm |
| 5 | **writeDriveLabel** | Medium — Google Drive integration | Medium — Drive Labels API |
| 6 | **gmailWatcher** | Medium — email ingestion | Medium — Gmail API + polling |
| 7 | **scheduled trigger** | Medium — batch processing | Low — Spring scheduling |
