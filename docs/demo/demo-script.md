# IG Central — Demo Script

**Total runtime: ~36 minutes across 12 videos (each ≤3 minutes)**

Each video is self-contained. Record them in order — later videos reference state created in earlier ones.

---

## Video 1: Login & Dashboard Overview (2:30)

**Goal:** Show the first thing a user sees after logging in — the dashboard that gives an instant picture of their document estate.

### Script

1. **Open the browser** at `http://localhost`. Show the landing page briefly — logo, tagline, "Log In" button.

2. **Click "Log In"**. On the login page, enter admin credentials. Point out the Google OAuth option: *"You can also sign in with Google if your organisation uses it."*

3. **Land on the Dashboard.** Pause and walk through what's on screen:
   - *"The dashboard gives you an at-a-glance view of your entire document estate."*
   - **Stat cards** at the top: total documents, governed documents, documents awaiting review, failed documents, documents currently in progress. *"These numbers update in real time as documents flow through the pipeline."*
   - **Pipeline funnel**: *"This shows how many documents are at each stage — uploaded, processing, classifying, governance applied. You can see at a glance if anything is stuck or failing."*
   - **Performance metrics**: *"Last 24 hours, last 7 days, average classification time, and stale document count."*
   - **Recent documents**: *"The last 8 documents that came through, with their current status, sensitivity, and category."*
   - **Quick actions sidebar**: *"One-click access to upload, review queue, search, governance settings, and monitoring."*

4. **Click on a stat card** (e.g. "Review Required") to show it links directly to the relevant page. Navigate back.

**Key message:** *"Within 5 seconds of logging in, you know exactly where your documents stand."*

---

## Video 2: Uploading & Watching the Pipeline (3:00)

**Goal:** Upload documents and watch them flow through the four-stage pipeline in real time.

### Script

1. **From the Dashboard**, click "Upload Documents" in the quick actions.

2. **Drag and drop 3-4 sample files** — a PDF employment contract, a Word doc leave request, a spreadsheet with financial data, and an image of a scanned letter. *"IG Central supports over 40 file formats — PDFs, Word, Excel, images with OCR, and more."*

3. **Watch the documents appear** in the recent activity feed on the dashboard. Point out the animated spinners: *"Each document is now going through our four-stage pipeline — text extraction, PII detection, AI classification, and governance enforcement."*

4. **Navigate to Monitoring** (sidebar). Show the **pipeline activity feed** updating in real time with SSE: *"Here you can see every document's journey in real time. Text extracted, PII found, classification assigned, governance applied."*

5. **Show the processing log**: *"Every step is logged — timestamps, stages, durations. If something fails, you'll see exactly where and why."*

6. **Go back to Dashboard** and show the documents now classified — status changed to "Classified" or "Governance Applied", sensitivity badges assigned, categories populated.

7. **Point out one document** that landed in "Review Required": *"This document had a confidence score below our threshold, so the AI flagged it for human review. We'll look at that in the review video."*

**Key message:** *"Upload to fully classified and governed — in under 30 seconds, with zero manual effort."*

---

## Video 3: Document Viewer & Classification Detail (3:00)

**Goal:** Show how to view a classified document and understand what the AI did.

### Script

1. **Navigate to Documents** (sidebar). Show the document list with the **category tree** on the left: *"Documents are organised by your classification taxonomy. Click any category to filter."*

2. **Click "All Documents"**. Show the table with sortable columns — name, status, category, sensitivity, size, date. *"You can sort by any column and search across filenames and content."*

3. **Click on a classified document** (e.g. the employment contract). The detail view opens.

4. **Walk through the document viewer**: *"On the left, you can read the extracted text. On the right, the classification panel shows everything the AI determined."*

5. **Classification panel** — go through each field:
   - **Category**: *"The AI placed this under HR > Employment Contracts."*
   - **Sensitivity**: *"Marked as CONFIDENTIAL — shown in amber."*
   - **Confidence**: *"92% confidence — high enough to skip human review."*
   - **Tags**: *"Auto-generated tags: employment, contract, full-time, permanent."*
   - **Reasoning**: *"The AI explains why it chose this classification — you can read its reasoning here."*
   - **Extracted metadata**: *"Because this category has a metadata schema, the AI also extracted structured fields — employee name, start date, salary, job title."*

6. **Show the action buttons**: *"From here you can download the original, reprocess if you want a fresh classification, manually override the category or sensitivity, or flag PII that was missed."*

7. **Demonstrate the PII flag** — select a piece of text in the document, show the quick-flag modal pop up: *"If you spot sensitive data the AI missed, highlight it, choose the PII type, and it's recorded. This teaches the system for next time."*

8. **Show a Google Drive document** if available — point out the "Drive" badge and the storage info showing it's still in Google Drive.

**Key message:** *"Full transparency into what the AI decided and why — with one-click overrides when you disagree."*

---

## Video 4: Human Review Queue (3:00)

**Goal:** Demonstrate the review workflow for low-confidence classifications.

### Script

1. **Navigate to Review** (sidebar). *"When the AI isn't confident enough, documents come here for human review. Your records managers only spend time on the edge cases."*

2. **Show the queue list** on the left — documents awaiting review with filenames and dates.

3. **Click on a document** in the queue. The detail panel opens on the right:
   - **Category, sensitivity, confidence score**: *"The AI classified this as HR > Miscellaneous with 58% confidence — below our 70% threshold, so it's here for review."*
   - **Reasoning**: *"Read the AI's explanation. It says: 'This document mentions both HR policies and financial data. I'm unsure whether it belongs under HR or Finance.'"*
   - **Tags**: Show the auto-generated tags.

4. **Demonstrate Approve**: *"If the AI got it right, click Approve. This records a positive signal — the AI learns that this type of document belongs here."* Click Approve. Document disappears from queue.

5. **Select the next document. Demonstrate Reclassify**: *"This one the AI got wrong. Click Reclassify."* Show the modal:
   - Select a different category from the dropdown.
   - Change sensitivity if needed.
   - *"You must provide a reason — this is captured as a correction that improves future accuracy."*
   - Save. *"Next time the AI sees a document like this, it'll consult this correction and get it right."*

6. **Select another document. Demonstrate Reject**: *"If the classification is completely wrong and needs reprocessing, click Reject."* Show the reason modal. Submit.

7. **Demonstrate Flag PII**: *"You can also report PII the scanner missed."* Show the PII report modal — add a PII type, description, and context. Submit.

8. **Show the empty state**: *"When the queue is clear — 'All caught up!' Your team can focus on policy work instead of manual classification."*

**Key message:** *"Every human decision feeds back to the AI. Your team's expertise becomes the system's expertise."*

---

## Video 5: Search & Discovery (2:30)

**Goal:** Show full-text search with faceted filtering and metadata-driven queries.

### Script

1. **Navigate to Search** (sidebar or quick action).

2. **Type a search term** (e.g. "maternity leave") in the search bar. Hit enter. *"Full-text search across filenames, document content, and extracted metadata."*

3. **Show the results** — cards with filename, category tag, sensitivity badge, tags, metadata fields, upload date.

4. **Open the filter panel**: *"You can narrow results with faceted filters."*
   - **Filter by category** — select "HR" from the dropdown. Results update.
   - **Filter by sensitivity** — click "CONFIDENTIAL". Results narrow further.
   - **Filter by date range** — set a "created from" date.
   - *"Notice the active filter indicator on the button — you can always see when filters are applied."*

5. **Demonstrate metadata search**:
   - Select a document type/schema (e.g. "HR Leave Request").
   - *"Dynamic filter fields appear based on the schema — these match the metadata the AI extracts."*
   - Filter by a metadata field (e.g. leave type = "maternity"). Results update.
   - *"This is structured search — not just keywords, but actual data fields extracted from the document content."*

6. **Click a result** to navigate to the document detail view.

7. **Clear all filters** and show the clean state.

**Key message:** *"Find any document by content, category, sensitivity, date, or extracted metadata — in seconds."*

---

## Video 6: Governance Framework — Taxonomy & Sensitivity (3:00)

**Goal:** Show how admins define the classification taxonomy and sensitivity levels.

### Script

1. **Navigate to Governance** (sidebar).

2. **Taxonomy/Categories section**: *"This is your classification taxonomy — the categories the AI uses to classify documents."*
   - Show the hierarchical tree: top-level categories (HR, Finance, Legal, Operations) with subcategories.
   - **Create a new category**: Click "Add Category". Enter name (e.g. "Recruitment"), select parent (HR), set default sensitivity to INTERNAL. Save. *"The new category is live immediately — the AI will start using it for the next classification."*
   - **Edit a category**: Click an existing one. Change the default sensitivity. Toggle active/inactive. *"Deactivating a category removes it from AI classification without deleting historical data."*

3. **Sensitivity Definitions section**: *"These define your sensitivity levels — the labels, colours, and descriptions."*
   - Show the four levels: PUBLIC (green), INTERNAL (blue), CONFIDENTIAL (amber), RESTRICTED (red).
   - *"Each level has a display name, colour code, and description. These drive the colour-coded badges you see throughout the system."*
   - *"You can customise these to match your organisation's terminology — Official, Official-Sensitive, Secret, Top Secret — whatever your framework uses."*

4. **Retention Schedules section**: *"Retention schedules define how long to keep documents and what happens when they expire."*
   - Show an existing schedule: e.g. "HR Records — 7 years — Delete".
   - **Create a new schedule**: Name it "Financial Records", set retention to 6 years, action to "Archive". Link it to the Finance category.
   - *"When a document classified as Finance reaches 6 years old, the system will automatically archive it — unless it's under legal hold."*

5. **Policies section**: *"Policies define rules that trigger based on category or sensitivity."*
   - Show an existing policy: e.g. "RESTRICTED documents must be encrypted and sharing restricted."
   - *"Policies are enforced automatically in the fourth pipeline stage — no manual intervention."*

**Key message:** *"Your entire governance framework is defined here — and it's all editable at runtime without touching code."*

---

## Video 7: Metadata Schemas (2:30)

**Goal:** Show how admins define structured metadata extraction per document type.

### Script

1. **Navigate to Governance > Metadata Schemas** (or the schemas section).

2. *"Metadata schemas tell the AI what structured data to extract from specific document types. Instead of just classifying a document as 'HR > Leave Request', the AI also extracts the employee name, leave type, start date, and end date."*

3. **Show an existing schema** (e.g. "HR Leave Request"):
   - Fields: employee_name (TEXT, required), leave_type (KEYWORD, required), start_date (DATE), end_date (DATE), manager_name (TEXT).
   - *"Each field has a type — text, keyword, date, number, currency, boolean — and can be marked required."*
   - *"Fields include examples to guide the AI: leave_type might show 'maternity, paternity, annual, sick'."*
   - Show linked categories: "HR > Leave Requests", "HR > HR Letters".

4. **Create a new schema**: *"Let's create one for financial invoices."*
   - Name: "Invoice"
   - Add fields:
     - vendor_name (TEXT, required)
     - invoice_number (KEYWORD, required)
     - amount (CURRENCY, required, example: "£1,500.00")
     - invoice_date (DATE)
     - payment_terms (KEYWORD, example: "Net 30, Net 60")
   - Link to category: Finance > Invoices.
   - Save.

5. *"Next time a document is classified under Finance > Invoices, the AI will extract these five fields automatically. They'll appear in the document detail view and be searchable in the search page."*

**Key message:** *"Turn unstructured documents into structured, searchable data — defined by your team, extracted by AI."*

---

## Video 8: AI Pipelines & Block Library (3:00)

**Goal:** Show the configurable AI processing pipeline and reusable processing blocks.

### Script

1. **Navigate to Pipelines** (sidebar).

2. *"Pipelines define the processing steps for each document type. Different categories can have different pipelines."*

3. **Show the default pipeline**: Expand it to see the steps:
   - Step 1: Text Extraction (BUILT_IN)
   - Step 2: PII Pattern Scan (PATTERN)
   - Step 3: AI Classification (LLM_PROMPT)
   - Step 4: Governance Enforcement (BUILT_IN)
   - *"Each step has a type — built-in processing, regex patterns, LLM prompts, or conditional branching."*

4. **Show the step detail** — click the LLM Classification step:
   - *"This step uses a processing block from the Block Library. The block contains the system prompt and user template that the AI receives."*
   - Show the linked block name and version.

5. **Navigate to Block Library** (sidebar). *"Blocks are reusable processing components — prompts, regex sets, extractors, routers, enforcers."*

6. **Show the type filters** — PROMPT, REGEX_SET, EXTRACTOR, ROUTER, ENFORCER.

7. **Click a PROMPT block** (e.g. the classification prompt):
   - **Configuration tab**: Show the system prompt and user template. *"This is exactly what the AI sees. You can edit the prompt, save a draft, and publish when ready."*
   - **Versions tab**: *"Every change is versioned. You can compare versions side by side and rollback if a change made accuracy worse."* Show the diff view.
   - **Feedback tab**: *"When reviewers correct classifications, the feedback is linked back to the block that produced them. You can see exactly which prompt version caused a misclassification."*

8. **Show the "Improve with AI" button**: *"If a block has accumulated feedback, you can ask the AI to suggest improvements based on the corrections."*

9. **Back to Pipelines** — show the visual pipeline editor briefly (React Flow graph view).

**Key message:** *"Every AI decision is traceable to a specific prompt version — and every correction improves the next version."*

---

## Video 9: PII Management & Subject Access Requests (3:00)

**Goal:** Show PII search, detection results, and the SAR workflow.

### Script

1. **Navigate to PII** (sidebar).

2. **PII Search tab**:
   - *"Search for specific PII across your entire document estate."*
   - Type a search term (e.g. an email address or name). Hit search.
   - Show results: documents containing the PII, with match type, redacted text, sensitivity label.
   - *"This is critical for Subject Access Requests — finding every document that mentions a specific person."*

3. **Summary tab**:
   - Show the overview cards: documents with PII, active findings, PII types detected.
   - Show the breakdown by PII type (bar chart): credit cards, emails, NI numbers, phone numbers.
   - *"At a glance, you can see the PII landscape across your entire document estate."*

4. **SARs tab**: *"Subject Access Requests — when someone asks 'what data do you hold about me?' — managed directly in IG Central."*
   - Show existing SARs with status badges: Received, Searching, Reviewing, Completed.
   - **Click "New SAR"**: Fill in the form:
     - Data subject: "Jane Smith"
     - Email: "jane.smith@example.com"
     - Search terms: "Jane Smith, J Smith"
     - Jurisdiction: UK GDPR
   - Create. *"The SAR is now tracked with a deadline based on the jurisdiction — 30 days for UK GDPR."*

5. **Expand a SAR** to show details:
   - Status progression, deadline countdown, days remaining.
   - Document count found.
   - Notes with timestamps and authors.
   - *"Your team can add notes, track progress, and mark it complete when the response is sent."*

6. *"Overdue SARs are flagged in red — you'll never miss a deadline."*

**Key message:** *"PII visibility across your entire document estate — with SAR tracking built in."*

---

## Video 10: Google Drive Integration (2:30)

**Goal:** Show connecting Google Drive and classifying documents in-situ.

### Script

1. **Navigate to Drives** (sidebar).

2. **Show the drive list** on the left: *"Local Storage is your default — documents uploaded directly. But you can connect external storage providers."*

3. **Click "Connect Google Drive"**: *"This starts an OAuth2 flow. You authorise IG Central to read your Google Drive."* (Walk through or show the connected state if already set up.)

4. **Show a connected Google Drive**: Click it in the sidebar.
   - The **folder tree** loads in the middle panel.
   - *"You can browse your entire Drive — folders, shared drives, everything."*

5. **Navigate into a folder**. Show the file list with columns: name, size, modified date, owner.

6. **Select files for classification**: Check 3-4 files using the checkboxes.
   - *"These files stay in Google Drive. We don't download or copy them — we stream the content temporarily for text extraction, then discard it."*
   - Click **"Classify"**. *"The selected files enter the same four-stage pipeline as uploaded documents."*

7. **Show the status updating**: Files show processing spinners, then "Classification Applied" badges.

8. **Click a classified Drive file** — show it opens in the document viewer with the classification panel. Point out the Google Drive storage info: *"You can see the Drive owner, account, and an 'Open in Drive' link."*

9. **Show the "Watch Folder" button**: *"You can set a folder to be watched — new files added to that folder will be automatically classified."*

**Key message:** *"Govern documents where they already live — no migration, no duplication, no disruption."*

---

## Video 11: Monitoring & Operations (3:00)

**Goal:** Show the ops dashboard — health, metrics, queue management, and error recovery.

### Script

1. **Navigate to Monitoring** (sidebar).

2. **Service Health section**: *"Five microservices power the pipeline. Each one reports its health status and response time."*
   - Show green checkmarks for healthy services.
   - *"If a service goes down, it turns red with an error message. You can click Ping to check manually."*

3. **Document Pipeline section**:
   - Show the **pipeline status visualisation** — coloured blocks showing document counts at each stage.
   - **Metrics cards**: throughput (24h and 7d), average classification time, stale documents.
   - *"Stale documents are anything stuck in processing for more than 10 minutes — the system flags them automatically."*

4. **Pipeline Controls**: *"Admin actions for when things go wrong."*
   - **Reset Stale**: *"Clears stuck documents so they can be reprocessed."*
   - **Retry Failed**: *"Re-queues all failed documents for another attempt."*
   - **Cancel All In-Flight**: *"Emergency stop — clears all queues."*
   - Show the confirmation dialog: *"Every destructive action requires confirmation."*

5. **RabbitMQ Queue Depths**: *"See how many documents are waiting at each stage. If a queue is backing up, you know where the bottleneck is."*
   - Show the queue cards with depth counts.
   - *"You can purge individual queues if needed."*

6. **Pipeline Activity Feed**: *"Real-time status changes via server-sent events — no polling, no refresh."*
   - Show documents appearing as they're processed.

7. **Processing Log**: *"Detailed logs with timestamps, stages, log levels, and durations. If a classification took 8 seconds instead of 2, you'll see it here."*
   - Point out an ERROR log if visible: *"Errors are highlighted in red with the full error message."*

8. **Infrastructure section**: *"Database size, storage usage, and message queue status at a glance."*

**Key message:** *"Full operational visibility — from service health to individual document journeys."*

---

## Video 12: Access Audit & Settings (2:30)

**Goal:** Show access control auditing and system configuration.

### Script

1. **Navigate to Access Audit** (Admin sidebar).

2. **Access Matrix tab**: *"This shows who can access what — every user mapped against every category."*
   - Show the colour-coded matrix: purple (admin), green (granted), amber (clearance blocked), grey (no access).
   - *"At a glance, you can verify that only authorised staff can see restricted categories."*

3. **Document Access tab**: *"Check who can access a specific document."*
   - Enter a document ID. Click "Check Access".
   - Show the results: users with their clearance levels and access reasons.

4. **Category Access tab**: *"Check who can access a category."*
   - Select a category. Show users with access, their clearance levels, and grant sources.

5. **Navigate to Settings** (sidebar).

6. **Classification settings**:
   - *"Human review threshold — set the confidence cutoff. Below this, documents go to the review queue."* Show the slider: 0.7 default.
   - *"Auto-approve threshold — above this, classifications are applied without any review."*

7. **LLM Model settings**:
   - *"Choose your AI model — Claude Haiku for speed and cost, Sonnet for balance, Opus for maximum accuracy."*
   - Show temperature and max tokens sliders.

8. **Processing settings**:
   - Auto-classify after extraction: on/off.
   - Auto-enforce governance: on/off.
   - Extract Dublin Core metadata: on/off.

9. **Cost Estimator**: *"Estimate your monthly AI spend."*
   - Enter documents per month and average size.
   - Show the calculated cost: per-document, monthly, yearly.
   - *"Switch models and see the cost change — Haiku is roughly 10x cheaper than Sonnet."*

10. *"Every setting takes effect immediately — no restart, no redeployment."*

**Key message:** *"Full control over access, AI behaviour, and costs — all configurable at runtime."*

---

## Recording Tips

- **Screen resolution**: Record at 1920x1080. Use browser zoom (110-125%) so UI elements are clearly visible.
- **Pace**: Pause briefly on each screen before clicking. Let viewers read key labels.
- **Cursor**: Use a cursor highlighter so viewers can follow your clicks.
- **Data**: Pre-seed the system with 20-30 classified documents across multiple categories so the dashboard, search, and pipeline visualisations have data to show.
- **Voiceover**: Record narration separately if possible — it's easier to edit. If recording live, speak slowly and clearly.
- **Order**: Record Videos 1-4 first (they build on each other). Videos 5-12 are mostly independent.
- **Failures**: For the monitoring video, consider intentionally stopping the LLM worker before uploading a document, so you can show a real failure and recovery.

## Video Index

| # | Title | Duration | Key Feature |
|---|---|---|---|
| 1 | Login & Dashboard | 2:30 | First impressions, key metrics |
| 2 | Upload & Pipeline | 3:00 | Four-stage processing pipeline |
| 3 | Document Viewer | 3:00 | Classification detail, metadata, PII flagging |
| 4 | Review Queue | 3:00 | Human-in-the-loop, corrections, feedback loop |
| 5 | Search & Discovery | 2:30 | Full-text + metadata search, faceted filters |
| 6 | Governance Framework | 3:00 | Taxonomy, sensitivity, retention, policies |
| 7 | Metadata Schemas | 2:30 | Structured extraction configuration |
| 8 | Pipelines & Blocks | 3:00 | AI pipeline configuration, prompt versioning |
| 9 | PII & SARs | 3:00 | PII search, subject access requests |
| 10 | Google Drive | 2:30 | In-situ classification, Drive integration |
| 11 | Monitoring | 3:00 | Ops dashboard, health, queues, error recovery |
| 12 | Access & Settings | 2:30 | Access audit, model config, cost estimator |
| | **Total** | **~34 min** | |
