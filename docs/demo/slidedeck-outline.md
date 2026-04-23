# IG Central — Slide Deck Outline

> Presentation structure for investors, enterprise buyers, and technical evaluators.
> Each slide lists: title, key message, talking points, and suggested visual.

---

## Section 1: What It Is (Slides 1-6)

### Slide 1 — Title
**IG Central: AI-Powered Information Governance**

- Tagline: "Classify, protect, and govern every document — automatically."
- Logo, product name, presenter name and date

*Visual: Product logo on clean background.*

---

### Slide 2 — The Problem
**Organisations are drowning in unclassified, ungoverned documents**

- Enterprises hold millions of documents across drives, email, SharePoint, cloud storage — most unclassified
- Records managers manually review and categorise documents — slow, inconsistent, expensive
- Regulatory pressure is increasing: GDPR, FCA SYSC 9, NHS DSPT, SOX — all require demonstrable records management
- A single misclassified document can trigger a compliance breach, a failed audit, or a data leak
- Subject Access Requests (SARs) require finding every document mentioning a person — impossible without structured metadata

*Visual: Iceberg diagram — tip is "documents you know about", underwater is "unclassified, ungoverned, unknown risk."*

---

### Slide 3 — What IG Central Does
**An AI-powered platform that classifies, protects, and governs documents at scale**

- Upload or connect documents from any source (local storage, Google Drive, SharePoint, S3)
- AI automatically: extracts text (40+ formats, OCR for scans), detects PII, classifies by category and sensitivity, extracts structured metadata, applies governance rules
- Human experts review only edge cases — the AI handles the routine work
- Every human correction teaches the system — accuracy improves with every document
- Full audit trail: every classification decision is traceable, every override is recorded

*Visual: Simple flow — Documents in (any source) → AI pipeline → Classified, governed, searchable documents out.*

---

### Slide 4 — The Four-Stage Pipeline
**Upload to fully governed in under 30 seconds**

- **Stage 1 — Text Extraction:** Tika + Tesseract OCR. 40+ formats. Extracts text from PDFs, Word, Excel, images, scans.
- **Stage 2 — PII Detection:** Configurable regex patterns scan for personal data — NI numbers, credit cards, emails, phone numbers, NHS numbers. Every finding recorded.
- **Stage 3 — AI Classification:** LLM classifies the document into a multi-level taxonomy, assigns sensitivity, extracts structured metadata, generates reasoning. Uses MCP tools to consult taxonomy, correction history, metadata schemas, and governance policies.
- **Stage 4 — Governance Enforcement:** Retention schedules, storage tier assignments, access controls, and policies applied automatically based on the classification.

*Visual: Pipeline diagram with four connected stages, each with an icon and brief label.*

---

### Slide 5 — The Human-AI Feedback Loop
**Every correction makes the system smarter**

- Low-confidence classifications route to a human review queue
- Records managers approve, reclassify, or flag missed PII
- Every decision is stored as a correction: what the AI said, what the human changed, and why
- Next time: the AI consults correction history before classifying — few-shot learning at inference time
- Over time: corrections accumulate, accuracy rises, the review queue shrinks
- End state: routine documents are auto-classified; humans focus on policy and edge cases

*Visual: Circular loop — AI classifies → Human reviews → Correction stored → AI learns → Better next time.*

---

### Slide 6 — Governance Hub Marketplace
**Shared governance frameworks — download and go**

- The Governance Hub is a separate service that publishes versioned governance packs
- Packs contain: taxonomy categories, retention schedules, sensitivity definitions, PII patterns, governance policies, metadata schemas, storage tiers, document traits
- Pre-built packs: UK General Governance Framework, UK Healthcare (NHS), RRS Records Classification Framework (113 record types)
- Any IG Central instance can connect to the Hub, browse packs, and import what it needs
- Tenants extend hub knowledge with their own org-specific rules and corrections
- Future: community contributions, industry-specific packs, regional variants

*Visual: Hub in the centre, tenant instances around the outside pulling in packs. Each pack shown as a box with component icons.*

---

## Section 2: Value Proposition (Slides 7-12)

### Slide 7 — Cost Reduction
**Slash records management costs by 80%+**

- Manual classification: ~5-10 minutes per document by a records manager
- IG Central: ~30 seconds per document, fully automated
- At 100,000 documents/year: manual = 8,000-16,000 hours. IG Central = near zero human hours for routine docs
- AI costs: ~$0.01-0.03 per document (cloud LLM). Drops to ~$0.0001 with local ModernBERT as categories mature
- Model stepping: system automatically identifies categories where a cheaper/faster model is safe
- Cost estimator built into Settings — plug in your volume, see your spend

*Visual: Cost comparison bar chart — manual vs IG Central. Second chart showing cost curve dropping as ModernBERT takes over.*

---

### Slide 8 — Compliance Confidence
**Demonstrate compliance at audit time — not scramble for it**

- Every document has: classification category, sensitivity label, retention schedule, applied policies, full audit trail
- Audit log is immutable — every status transition, every human decision, every AI reasoning is recorded
- **Document access tracking:** Every view, download, and access denial recorded with user, timestamp, and context — visible directly in the document viewer's Audit Trail tab
- **Security event auditing:** Failed access attempts (wrong sensitivity clearance, missing taxonomy grant) logged with a SECURITY flag — no silent 403s
- Retention enforcement: automatic archival or deletion when retention periods expire, unless under legal hold
- PII visibility: know exactly where personal data lives across your entire document estate
- SAR readiness: search for any person's data and compile a response package
- Maps to: GDPR Articles 15/17/30/32/35, FCA SYSC 9, NHS DSPT, ISO 27001, SOC 2

*Visual: Compliance checklist with green ticks. Document audit trail showing view/download/access denied events.*

---

### Slide 9 — Speed to Value
**Classified in weeks, not months**

- Import a governance pack from the Hub — taxonomy, retention, PII patterns all pre-configured
- Upload documents — classification starts immediately
- No training period needed: LLM + schema.org knowledge handles cold start
- Accuracy improves every day as corrections accumulate
- No code changes for configuration: taxonomy, schemas, PII patterns, retention, policies all editable from the UI at runtime
- Connect Google Drive and classify documents in-situ — no migration needed

*Visual: Timeline — Week 1: connect and configure. Week 2: documents flowing. Month 3: system self-improving.*

---

### Slide 10 — Self-Improving Intelligence
**The system that gets smarter the more you use it**

- **Day 1:** LLM + schema.org universal knowledge + hub governance pack → decent accuracy, generic extraction
- **Month 1:** LLM + correction history → org-specific adjustments, accuracy climbing
- **Month 3:** ModernBERT trained on accumulated data → 60-80% of routine documents bypass the LLM entirely
- **Month 6:** Schema suggestion engine proposes new metadata fields from correction patterns
- **Month 12:** ModernBERT handles routine work, LLM handles edge cases, humans handle policy
- Research benchmark target: 85-93% hierarchical F1 (outperforms expert human records managers by ~17%)

*Visual: Graph showing accuracy rising over time as corrections accumulate. Stacked area showing proportion handled by ModernBERT vs LLM vs Human.*

---

### Slide 11 — Classify Where Documents Already Live
**No migration. No duplication. No disruption.**

- Google Drive: OAuth2 connect, browse folders, classify in-situ. Files stay in Drive.
- Local Storage: upload directly or watch folders for automatic ingestion
- Future: SharePoint/OneDrive, S3, Box, SMB network shares — same interface
- Universal drive abstraction: every provider implements the same interface. Pipeline doesn't care where the file comes from.
- Viewer proxies content from the source — documents are never permanently copied unless you choose to

*Visual: Diagram showing multiple storage providers (Google Drive, SharePoint, S3, Local) all feeding into the same classification pipeline.*

---

### Slide 12 — Enterprise Search & Discovery
**Find any document by content, category, sensitivity, or extracted metadata**

- Elasticsearch-powered full-text search across all documents
- Faceted filtering: by category, sensitivity, date range, storage provider
- Metadata-driven queries: "show me all invoices over $50,000 due this quarter"
- Dynamic filter fields based on metadata schemas — different filters for invoices vs contracts vs HR records
- PII search: find every document containing a specific person's data
- Slug-based URLs: every document addressable by human-readable slug, not database ID

*Visual: Search UI screenshot showing faceted filters, results with metadata badges.*

---

## Section 3: Architecture (Slides 13-18)

### Slide 13 — System Architecture Overview
**14 containerised services, deployed with Docker Compose or Kubernetes**

- **API Service** (Spring Boot 4, Java 21) — REST API, controllers, config, auth
- **Web Frontend** (Next.js 16, React 19, TypeScript) — dashboard, document viewer, admin UI
- **Document Processor** — text extraction (Tika + Tesseract), PII regex scanning
- **LLM Worker** — AI classification via MCP tool architecture
- **Governance Enforcer** — applies retention, policies, storage tier assignments
- **MCP Server** — 10 tools the LLM calls during classification (taxonomy, schemas, corrections, etc.)
- **Governance Hub** — separate marketplace service for shared governance frameworks
- **Infrastructure:** MongoDB, Elasticsearch, RabbitMQ, MinIO (object storage), nginx (reverse proxy)

*Visual: Architecture diagram — boxes for each service, arrows for communication paths. Colour-coded: blue for app services, green for infrastructure, orange for AI.*

---

### Slide 14 — MCP Tool Architecture
**The LLM doesn't guess — it consults**

- Model Context Protocol (MCP) gives the LLM structured access to organisation knowledge
- 10 tools available during classification:
  - `get_classification_taxonomy` — the full category hierarchy
  - `get_metadata_schemas` — what fields to extract for this document type
  - `get_correction_history` — what humans corrected before
  - `get_sensitivity_definitions` — sensitivity level guidelines
  - `get_governance_policies` — applicable policies and rules
  - `get_retention_schedules` — how long to keep this type of document
  - `get_org_pii_patterns` — organisation-specific PII types and false positives
  - `get_document_traits` — template vs real, draft vs final, inbound vs outbound
  - `get_storage_capabilities` — available storage tiers and their constraints
  - `save_classification_result` — persist the final classification
- Every tool call is logged in the AI usage log — full transparency into LLM behaviour
- Future: unified RAG tool (`get_document_type_knowledge`) merges multiple knowledge sources into one retrieval call

*Visual: LLM in the centre with tool call arrows radiating out to knowledge sources. Each tool labelled with what it returns.*

---

### Slide 15 — Pipeline Block Architecture
**Every AI decision traces to a versioned, auditable processing block**

- Five block types: PROMPT, REGEX_SET, EXTRACTOR, ROUTER, ENFORCER (+ MODEL for ModernBERT)
- Each block has immutable version history — publish creates a new version, never overwrites
- Pipeline steps link to specific block versions
- User corrections create feedback on the block that produced them
- "Improve with AI" generates better block versions from accumulated feedback
- Visual pipeline editor (React Flow) — drag-and-drop pipeline configuration
- Accelerator nodes: template fingerprint, rules engine, smart truncation, similarity cache

*Visual: Pipeline editor screenshot or diagram showing blocks flowing through stages, with version history sidebar.*

---

### Slide 16 — Multi-Model AI Strategy
**Right model for the right document at the right cost**

- **LLM (Claude/Ollama):** Full reasoning for complex, ambiguous, or new document types. MCP tools provide context. Highest accuracy, highest cost.
- **ModernBERT (local):** Fine-tuned on accumulated classification data. Handles routine documents at ~100x cheaper, ~50x faster. Runs locally — sensitive data never leaves infrastructure.
- **Confidence routing:**
  - BERT high confidence → accept, skip LLM
  - BERT medium confidence → LLM with BERT hint (cheaper prompt)
  - BERT low confidence → full LLM classification
- **Cross-validation:** when both run, agreement score validates confidence. Disagreement → human review regardless of individual scores.
- **Self-training:** LLM teaches ModernBERT through its classifications. Humans teach both through corrections. ModernBERT retrains nightly on accumulated data.

*Visual: Decision tree — document enters, BERT pre-screens, routes to BERT-only, LLM-assisted, or full LLM based on confidence.*

---

### Slide 17 — RAG Knowledge Architecture
**Schema.org + RRS taxonomy + corrections = a system that knows what it doesn't know**

- **Layer 1 — RRS Taxonomy (authoritative):** 15 functional areas, 42 record classes, 113 record types. Each with retention triggers, periods, legal basis. Derived from NARA, UK TNA, ARMA standards.
- **Layer 2 — Admin-curated schemas:** MetadataSchema definitions with typed fields, extraction hints, and examples. Authoritative for metadata extraction.
- **Layer 3 — Correction history:** Every human override stored and consulted. Org-specific patterns emerge over time.
- **Layer 4 — Schema.org (cold start):** Universal document-type definitions (Invoice, Legislation, JobPosting, etc.) provide extraction knowledge when no admin schema exists.
- **Layer 5 — Exemplar documents:** Past high-confidence classifications retrieved by similarity for few-shot context.
- Distributed via Governance Hub as importable packs — tenants get a rich baseline on day one

*Visual: Layered pyramid — RRS at base (broadest), corrections at top (most specific). Arrow showing query flowing down through layers until it finds what it needs.*

---

### Slide 18 — Security & Access Control
**Three-layer access control + encryption + immutable audit**

- **Layer 1 — Permission-based:** Features controlled by `permissionKey` values, bundled into roles, sold as products/subscriptions
- **Layer 2 — Taxonomy-scoped:** Users can only see documents in categories they're granted access to
- **Layer 3 — Sensitivity-cleared:** Users need sufficient clearance level to see documents at a given sensitivity
- **Authentication:** JWT + OAuth2 (Google login), CSRF protection
- **Encryption:** At rest (MongoDB WiredTiger, MinIO SSE), in transit (mTLS between services)
- **Audit:** Immutable audit trail — every status change, every classification, every human decision logged with timestamp, user, and full context
- **Access audit UI:** Matrix view showing who can access what, with one-click compliance checks

*Visual: Three concentric circles — inner (permission), middle (taxonomy), outer (sensitivity). Document in the centre, user must pass all three layers.*

---

## Section 4: Features (Slides 19-28)

### Slide 19 — Dashboard & Real-Time Monitoring
**Know where your documents stand in 5 seconds**

- Stat cards: total documents, governed, awaiting review, failed, in progress
- Pipeline funnel visualisation: documents at each stage
- Performance metrics: last 24h/7d throughput, avg classification time, stale count
- Recent documents feed with live status updates
- Quick actions: upload, review, search, governance, monitoring
- Monitoring page: service health, queue depths, pipeline activity feed (SSE), processing logs

*Visual: Dashboard screenshot.*

---

### Slide 20 — Document Viewer & Classification Panel
**Full transparency into every AI decision**

- **Two-tab viewer:** Document tab shows the content, Audit Trail tab shows who viewed, downloaded, or was denied access
- Extracted text viewer (left) with classification panel (right)
- Category, sensitivity, confidence score, auto-generated tags
- AI reasoning: supporting signals, contradicting signals, alternative categories considered
- Extracted metadata: structured fields pulled from the document content
- PII findings: type, location, confidence, matched text
- Document traits: draft/final, template/real, inbound/outbound
- One-click actions: download, reprocess, override, flag PII
- Real-time updates: classification panel refreshes live via SSE as the document moves through the pipeline
- Google Drive documents show storage info and "Open in Drive" link

*Visual: Document viewer screenshot showing the Document/Audit Trail tabs with classification panel expanded.*

---

### Slide 21 — Human Review Queue
**Experts review edge cases, not every document**

- Documents below confidence threshold route to review queue
- Reviewers see: AI classification, confidence breakdown, reasoning, alternative categories
- Actions: Approve (positive signal), Reclassify (correction with reason), Reject (reprocess), Flag PII
- Every action stored as a correction that improves future accuracy
- Queue clears as accuracy improves — records managers focus on policy, not paperwork

*Visual: Review queue screenshot showing document list + detail panel.*

---

### Slide 22 — Governance Framework Management
**Define your entire governance framework from the UI — no code changes**

- **Taxonomy:** Hierarchical classification categories with drag-and-drop tree builder
- **Sensitivity levels:** Custom labels, colours, descriptions (align to your org's framework)
- **Retention schedules:** Per-category retention periods with triggers and dispositions
- **Governance policies:** Rules that trigger based on category or sensitivity
- **Metadata schemas:** Structured field definitions per document type — AI extracts these automatically
- **PII patterns:** Configurable regex patterns with confidence scores and false positive tracking
- **Storage tiers:** Define storage locations with encryption, immutability, and geographic constraints
- **Document traits:** Detection dimensions (completeness, direction, provenance)
- All changes take effect immediately — no restart, no redeployment

*Visual: Governance admin page screenshot showing taxonomy tree + retention schedule editor.*

---

### Slide 23 — Metadata Schemas & Structured Extraction
**Turn unstructured documents into searchable, structured data**

- Admin defines schemas: fields with types (text, keyword, date, number, currency, boolean), required flags, extraction hints, examples
- Schemas link to taxonomy categories — AI only extracts when the schema applies
- AI extracts metadata during classification using MCP tools
- Extracted fields: searchable in Elasticsearch, visible in document viewer, exportable
- Schema.org-informed defaults: when no admin schema exists, schema.org type definitions provide a starting point
- Schema suggestion engine: system proposes new fields when correction patterns reveal missing data

*Visual: Side-by-side — schema definition form (left) and extracted metadata on a document (right).*

---

### Slide 24 — AI Pipeline Configuration
**Visual pipeline editor with versioned, auditable processing blocks**

- React Flow-based visual editor — drag, drop, connect processing nodes
- Node types: text extraction, PII scan, LLM classification, governance enforcement, conditional routing
- Accelerator nodes: template fingerprint (skip known templates), rules engine (keyword-based auto-classify), smart truncation (optimise text for LLM), similarity cache (inherit from similar docs)
- Each node links to a processing block from the Block Library
- Block Library: browse, version, compare, rollback, and improve blocks with AI assistance
- Feedback flows from human corrections to the specific block version that produced them

*Visual: Pipeline editor screenshot showing the visual graph with connected nodes.*

---

### Slide 25 — PII Management & Subject Access Requests
**Know exactly where personal data lives**

- PII detection: configurable regex patterns + ModernBERT NER (future)
- PII search: find every document containing a specific person's data
- PII summary dashboard: documents with PII, findings by type, risk distribution
- SAR workflow: create, track, deadline management (30 days for UK GDPR)
- SAR package: compile all documents mentioning a data subject
- PII corrections: flag missed PII or dismiss false positives — corrections improve future detection

*Visual: PII summary dashboard screenshot.*

---

### Slide 26 — External Storage Connectors
**Classify documents where they live**

- Google Drive: OAuth2 connect, folder browser, in-situ classification, watched folders
- Local Storage: direct upload, drag-and-drop, batch processing
- Universal interface: `StorageProviderService` — every provider implements the same contract
- Documents stay in their source — content streamed temporarily for extraction, then discarded
- Classification results stored in IG Central regardless of where the file lives
- Future providers: SharePoint/OneDrive (Microsoft Graph), S3 (AWS SDK), Box, SMB/CIFS

*Visual: Drive connection page screenshot showing Google Drive folder browser.*

---

### Slide 27 — Access Control & Audit
**Three-layer access with full audit visibility**

- **Layer 1 — Permission-based:** Features gated by `permissionKey` values, bundled into roles, sold as products
- **Layer 2 — Taxonomy-scoped:** Users assigned to specific categories — can only see documents in granted categories
- **Layer 3 — Sensitivity-cleared:** Users need sufficient clearance to view documents at a given sensitivity level
- **Access denied = audited:** Every failed access attempt records who, what, when, and why — visible in the document's audit trail with a SECURITY badge
- **Document-level audit trail:** Every view, download, classification override, PII report, and access denial recorded as an immutable audit event
- **Per-document audit tab:** Built into the document viewer — compliance officers can see the full access history for any document without leaving the page
- **Filterable timeline:** Default view shows views and security events; toggle to see all events including pipeline status changes
- Access audit matrix: colour-coded grid showing who can access what across the entire estate
- Per-document access check: "who can see this document and why?"
- Per-category access check: "who has access to this category?"

*Visual: Split view — access audit matrix (left), document audit trail timeline with SECURITY badge on an access denied event (right).*

---

### Slide 28 — Settings & Cost Management
**Full control over AI behaviour and costs**

- Confidence thresholds: review threshold, auto-approve threshold (sliders)
- Model selection: Claude Haiku (fast/cheap), Sonnet (balanced), Opus (max accuracy), Ollama (local/free)
- Processing toggles: auto-classify, auto-enforce, Dublin Core extraction
- Temperature and token limits
- Cost estimator: input documents/month + avg size → per-document, monthly, yearly cost
- Model comparison: see cost difference between models side-by-side
- All settings take effect immediately — no restart

*Visual: Settings page screenshot showing model selector + cost estimator.*

---

## Section 5: Design Principles (Slides 29-35)

### Slide 29 — Configuration Over Code
**Every behaviour is editable at runtime — no redeployment needed**

- Taxonomy, retention, sensitivity, PII patterns, metadata schemas, policies, storage tiers, traits — all stored in MongoDB, editable from the UI
- LLM prompts are versioned blocks, not hardcoded strings
- Pipeline configuration is visual, not code
- Menu items, labels, feature flags — all configuration
- Code handles mechanics (security filters, data schemas, token generation). Configuration handles behaviour (what categories exist, what fields to extract, what policies to apply).
- Non-developers can manage the system — records managers control governance, admins control access, developers control infrastructure

*Visual: Comparison — "Traditional system: change a label → code change → PR → build → deploy → hope" vs "IG Central: change a label → save → live."*

---

### Slide 30 — Happy Path AND Unhappy Path
**Every pipeline stage handles both success and failure explicitly**

- Every async stage has a corresponding `*_FAILED` status (PROCESSING_FAILED, CLASSIFICATION_FAILED, ENFORCEMENT_FAILED)
- Failed documents store: error message, failed stage, timestamp, retry count
- All failures are visible: monitoring page, dashboard stat cards, pipeline activity feed
- Retry: one-click retry from monitoring page, or bulk "Retry Failed"
- Stale detection: documents stuck >10 minutes flagged and resettable
- No silent failures: if a pipeline step cannot produce a result, it records a failure — never silently returns
- Audit trail: every status transition including failures produces an audit event

*Visual: Pipeline flow diagram showing success path (top) and failure path (bottom) with retry arrows.*

---

### Slide 31 — Licensable Everything
**Every feature is a permission. Every permission is a product.**

- Entity hierarchy: Product → Roles → Features (each with a `permissionKey`)
- Subscription model: ACTIVE, TRIAL, EXPIRED, CANCELLED
- Subscription sync: when a subscription changes, the user's permissions update automatically
- Never hardcode permission checks against role names — always check `permissionKey` values
- Admin panel manages all CRUD: Features, Roles, Products, Subscriptions
- Enables: freemium tiers, enterprise upgrades, per-feature billing, trial periods

*Visual: Entity hierarchy diagram — Product boxes connecting to Role boxes connecting to Feature boxes.*

---

### Slide 32 — Human-in-the-Loop by Design
**AI proposes, humans dispose — and every decision teaches the system**

- Confidence-based routing: high → auto-approve, medium → review queue, low → full review
- Multi-dimensional confidence: separate scores for category, sensitivity, PII, metadata
- Structured reasoning: the AI explains its evidence and counter-evidence
- Corrections are first-class data: stored, weighted, retrieved, and used to improve future accuracy
- Calibration tracking: "when the AI says 0.9, is it actually right 90% of the time?"
- Per-category threshold tuning: some categories need stricter review, others are safe to auto-approve
- Block feedback: corrections link back to the specific prompt version that produced them

*Visual: Confidence spectrum bar — green (auto-approve) → amber (review) → red (manual). Feedback loop arrow.*

---

### Slide 33 — Progressive Model Sophistication
**Start expensive and accurate. End cheap and accurate.**

- Day 1: Cloud LLM (Claude Sonnet) does all classification — expensive but accurate out of the box
- Month 3: ModernBERT trained on accumulated data — handles 60-80% of routine docs locally at ~100x cheaper
- Month 6+: Confidence cross-validation (BERT + LLM agreement) catches errors neither model spots alone
- The system tracks: per-category accuracy, correction rate, model agreement. Admin decides when to step down.
- Graceful degradation: if BERT service is down, pipeline falls through to LLM automatically
- All model decisions are auditable: `classifiedBy` field records which model(s) were used

*Visual: Stacked area chart over time — LLM area shrinking, ModernBERT area growing. Cost line dropping.*

---

### Slide 34 — Governance as a Distributable Product
**Build once. Share everywhere.**

- Governance Hub: separate service, separate database, API key authentication
- Governance packs: versioned bundles of taxonomy + retention + PII + policies + schemas
- Tenants import packs selectively — choose which components to adopt
- Hub knowledge (universal) vs tenant knowledge (org-specific) — clean ownership boundary
- Pack updates flow to tenants with selective merge — don't overwrite customisations
- Future: community contributions, quality scoring, regional variants, industry-specific packs
- RRS taxonomy (113 record types), schema.org mappings, classification test sets — all distributable as pack components

*Visual: Hub-and-spoke diagram — central Hub with pack icons, tenant instances pulling selected components.*

---

### Slide 35 — Slug-Based, Searchable, Auditable
**Every document addressable, discoverable, and traceable**

- Slug-based URLs: `maternity-leave-confirmation-a3f2b1` — human-readable, shareable
- Elasticsearch full-text search + faceted filtering + metadata-driven queries
- Immutable classification history: every re-classification creates a new record, never overwrites
- AI usage logging: every LLM call, tool invocation, token count, cost — fully transparent
- Access audit: who accessed what, when, and why — matrix view for compliance officers
- Backfill on startup: existing documents without slugs are automatically assigned them

*Visual: URL bar showing a slug-based document URL. Search results showing metadata badges.*

---

## Section 6: Closing (Slides 36-37)

### Slide 36 — The IG Central Difference
**Summary of differentiators**

| Capability | Traditional DMS | IG Central |
|---|---|---|
| Classification | Manual | AI-automated with human review |
| Metadata extraction | Manual data entry | AI extracts structured fields from document content |
| PII detection | Point-in-time scan | Continuous, correctable, learning |
| Governance enforcement | Policy docs in a binder | Automated rules applied at classification time |
| Accuracy over time | Degrades (staff turnover) | Improves (correction feedback loop + ModernBERT) |
| Cost per document | £5-10 (human time) | £0.01-0.03 (AI) → £0.0001 (local BERT) |
| Time to value | Months (manual taxonomy build) | Days (import hub governance pack) |
| Compliance evidence | Scramble at audit time | Always audit-ready |
| Access auditing | Access logs buried in server logs | Per-document audit trail with view tracking, download logging, and security event flagging — visible in the document viewer |

*Visual: Comparison table with tick/cross or colour coding.*

---

### Slide 37 — What's Next / Call to Action
**Roadmap and next steps**

- **Now:** 14 containers, full pipeline, Google Drive connector, Governance Hub, visual pipeline editor
- **Next:** Kubernetes + Helm deployment, ModernBERT integration, SharePoint/S3 connectors, RAG knowledge architecture
- **Future:** Community hub marketplace, natural language search, multi-tenant SaaS, SAR redaction workflow
- Performance targets: 150,000+ docs/day with horizontal scaling, 85-93% classification accuracy

*Visual: Roadmap timeline with phases. Contact information / demo request CTA.*
