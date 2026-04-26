# Schema.org + RAG Strategy for LLM-Powered Classification

## Problem

The classification pipeline relies on manually-curated `MetadataSchema` definitions to tell the LLM what fields to extract from documents. This has two limitations:

1. **Cold start** — categories without a schema get no metadata extraction at all. Of the current GLS taxonomy (~7 top-level categories), only 3 schemas exist (HR Leave Request, Contract/Agreement, Invoice).
2. **Manual effort** — every new category requires an admin to define fields, types, hints, and examples from scratch.

Meanwhile, two powerful knowledge sources already exist that the LLM could learn from:

- **Schema.org** — structured vocabularies for document types that the LLM already understands from training data
- **RRS Classifier Taxonomy** — a comprehensive 3-tier records retention taxonomy (15 functional areas, 42 record classes, 113 record types) with retention rules, legal bases, and a 63-document validation test set

## Core Idea

Build a **layered knowledge base** that the MCP retrieves from at classification time, combining:

1. **RRS taxonomy** as the authoritative classification structure (far richer than the current GLS taxonomy)
2. **Schema.org** as universal document-type knowledge that bootstraps metadata extraction
3. **Org-specific corrections** that accumulate over time and specialise the system

The LLM gets rich context for every document — even categories no admin has touched — and the system improves with every human correction.

## Knowledge Sources

### RRS Classifier Taxonomy (`RRS_Classifier_Taxonomy.xlsx`)

A production-grade 3-tier taxonomy derived from NARA GRS, UK TNA, and ARMA standards:

**Structure:** Functional Area (FA) → Record Class (RC) → Record Type (RT)

| FA# | Functional Area | Record Classes | Record Types |
|---|---|---|---|
| FA-01 | Human Resources | Recruitment, Employment, Payroll, Benefits | RT-001 to RT-013 |
| FA-02 | Finance & Accounting | GL & Journals, AP, AR, Reporting, Tax | RT-014 to RT-027 |
| FA-03 | Legal & Compliance | Contracts, Litigation, Regulatory | RT-028 to RT-038 |
| FA-04 | Corporate Governance | Board & Executive, Incorporation | RT-039 to RT-044 |
| FA-05 | Information Technology | Governance, Logs, Licensing, Data Mgmt | RT-045 to RT-054 |
| FA-06 | Sales & Marketing | Sales Ops, Marketing, Customer Records | RT-055 to RT-062 |
| FA-07 | Procurement & Supply Chain | Sourcing, Inventory & Logistics | RT-063 to RT-068 |
| FA-08 | Operations & Facilities | Real Estate, H&S, Asset Management | RT-069 to RT-076 |
| FA-09 | Research & Development | IP, Product Dev, Quality Management | RT-077 to RT-084 |
| FA-10 | Communications & Public Affairs | Internal, External, Government Affairs | RT-085 to RT-090 |
| FA-11 | Risk Management & Insurance | Enterprise Risk, Insurance | RT-091 to RT-095 |
| FA-12 | Customer Service & Support | Case Management, Warranties | RT-096 to RT-099 |
| FA-13 | Strategy & Planning | Strategic Planning, M&A | RT-100 to RT-104 |
| FA-14 | Treasury & Banking | Cash Management, Investments | RT-105 to RT-108 |
| FA-15 | Privacy & Data Protection | Privacy Governance, Third-Party Sharing | RT-109 to RT-113 |

**Each record type includes:**
- Retention trigger (what starts the clock)
- Retention period (how long to keep)
- Disposition (Destroy vs Archive)
- Legal/regulatory basis (EEOC, FLSA, IRS, GDPR, SOX, etc.)

**Test validation set:** 63 test documents graded Easy/Medium/Hard with expected classifications and edge case notes. This is the benchmark for measuring whether RAG improves accuracy.

### RRS Training Sources (`RRS_Training_Sources.docx`)

A curated guide to 24 publicly available data sources, rated by quality, covering:

**Taxonomy sources (ground truth labels):**
- NARA General Records Schedules (US federal) — primary source for FA-01 to FA-05
- UK TNA Records Management Guidance — strong on functional classification, correspondence, policy
- Library and Archives Canada — bilingual, multi-jurisdiction
- ARMA International — industry-specific (healthcare, financial services, manufacturing)

**Document corpora (training data):**

| Source | What It Provides | Maps To |
|---|---|---|
| SEC EDGAR | Millions of labeled corporate filings (10-K, 8-K, contracts as Exhibit 10) | FA-02 Finance, FA-03 Legal, FA-04 Governance |
| CourtListener | Millions of court opinions, pleadings, motions, settlements | FA-03 Litigation (RT-032 to RT-034) |
| EDGAR Exhibit 10 | Hundreds of thousands of real executed contracts (MSAs, NDAs, employment, M&A) | FA-03 Contracts (RT-028 to RT-031), FA-13 M&A |
| CUAD (HuggingFace) | 500 expert-labeled contracts with 41 clause categories | FA-03 Contracts — gold standard for contract NLP |
| Enron Email Dataset | 500K corporate emails from executives, legal, finance, HR, trading | FA-10 Communications, FA-01 HR, FA-02 Finance |
| GovInfo.gov | Congressional reports, GAO audits, budget docs, Federal Register | FA-04 Governance, FA-03 Regulatory, FA-02 Budget |
| HuggingFace Hub | CUAD, LexGLUE, EUR-Lex, LEDGAR — labeled legal/financial datasets | Cross-functional benchmarking |
| EDPB Decisions | GDPR enforcement decisions, DPIAs, DSAR guidance | FA-15 Privacy (RT-109 to RT-113) |
| ICO Templates | UK GDPR templates: RoPA, DSAR, consent, DPAs | FA-15 Privacy |
| Job posting datasets | LinkedIn/Indeed scraped postings | FA-01 Recruitment (RT-001) |

**Recommended build sequence:**
1. Lock taxonomy from NARA + UK TNA
2. Anchor corpus from EDGAR + Enron + CourtListener + CUAD
3. Gap fill from GovInfo, HuggingFace, job postings
4. Privacy & compliance from EDPB + ICO
5. Benchmark against Payne thesis targets (85-93% hierarchical F1)

### Schema.org Type Definitions

Universal document-type vocabulary that maps to the RRS taxonomy:

| RRS Record Types | Schema.org Type | Key Properties |
|---|---|---|
| RT-017 to RT-021 (Invoices, POs) | `Invoice` | `customer`, `provider`, `totalPaymentDue`, `paymentDueDate`, `paymentStatus` |
| RT-028 to RT-031 (Contracts) | `CreativeWork` + `Legislation` | `author`, `contributor`, `dateCreated`, `expires`, `jurisdiction` |
| RT-032 to RT-034 (Litigation) | `Legislation` | `legislationType`, `jurisdiction`, `legislationDate`, `legislationLegalForce` |
| RT-001, RT-004 (Job Postings, Offers) | `JobPosting` | `hiringOrganization`, `jobTitle`, `datePosted`, `validThrough`, `employmentType` |
| RT-022 to RT-024 (Financial Reports) | `Report` (CreativeWork) | `author`, `datePublished`, `about`, `abstract`, `creativeWorkStatus` |
| RT-087 to RT-088 (Press, Media) | `NewsArticle` / `PressRelease` | `headline`, `datePublished`, `author`, `publisher`, `about` |
| RT-045 to RT-046 (IT Policies, Architecture) | `TechArticle` | `proficiencyLevel`, `dependencies`, `about` |
| RT-069 to RT-071 (Leases, Property) | `RealEstateListing` + `CreativeWork` | `address`, `price`, `dateCreated`, `expires` |
| RT-077 to RT-079 (Patents, IP) | `CreativeWork` | `copyrightHolder`, `copyrightYear`, `license`, `datePublished` |
| RT-109 to RT-113 (Privacy/GDPR) | `Legislation` + `DigitalDocument` | `jurisdiction`, `legislationType`, `dateCreated`, `about` |
| All document types | `CreativeWork` (base) | `author`, `dateCreated`, `dateModified`, `inLanguage`, `encodingFormat`, `keywords`, `genre`, `creativeWorkStatus` |

## How the LLM Gets Context Today

The current flow uses direct MCP tool calls — each returns a specific, pre-structured result:

```
Document text arrives
  → LLM calls get_classification_taxonomy     → flat list of categories (~7 top-level)
  → LLM calls get_correction_history           → past human corrections for this category
  → LLM calls get_metadata_schemas             → admin-curated field definitions (if any)
  → LLM calls get_sensitivity_definitions      → sensitivity label guidelines
  → LLM calls get_governance_policies          → retention, access rules
  → LLM calls save_classification_result       → persists the result
```

Each tool queries one MongoDB collection and returns one type of context. There is no search, no ranking, and no fallback if a collection has no data.

## What RAG Changes

RAG replaces the "query one collection" pattern with "search a knowledge base and return the most relevant results, whatever the source."

### The Knowledge Base — Five Layers

| Layer | Source | Priority | When It Helps |
|---|---|---|---|
| **RRS taxonomy** | `RRS_Classifier_Taxonomy.xlsx` ingested into MongoDB | Highest | Always — provides the 3-tier classification target, retention rules, and legal basis |
| **Admin-curated schemas** | Current `MetadataSchema` collection | High | Categories with manual schemas — authoritative for metadata extraction |
| **Correction feedback** | `ClassificationCorrection` collection | High | After human review cycles — org-specific adjustments |
| **Schema.org definitions** | Ingested type definitions | Medium | Cold start for metadata extraction when no admin schema exists |
| **Training corpus examples** | Indexed exemplar documents from RRS Training Sources | Low | Pattern matching — "here's what a correctly-classified vendor invoice looks like" |

### The Retrieval Flow

```
Document text arrives
  → LLM calls get_classification_taxonomy
      Now returns the full RRS 3-tier hierarchy (FA → RC → RT)
      Each RT includes: retention trigger, period, disposition, legal basis
      
  → LLM determines candidate RT (e.g. RT-017 Vendor Invoices)
  
  → LLM calls get_document_type_knowledge(recordTypeId, textSnippet)
      RAG tool searches across all layers:
      1. RRS record type definition → retention rules, legal basis, key signals
      2. Admin MetadataSchema (if exists) → authoritative extraction fields
      3. Correction history → org-specific patterns and overrides
      4. Schema.org type → universal field definitions (fallback)
      5. Similar exemplar documents → "here's what a good classification looked like"
      
      Returns merged, ranked context block
      
  → LLM classifies and extracts metadata with full context
  → LLM calls save_classification_result
```

## The Learning Loop

```
Day 1 (cold start):
  RRS taxonomy loaded. Schema.org definitions loaded.
  No org data, no corrections, no exemplars.
  LLM classifies using RRS hierarchy + schema.org field knowledge.
  Result: accurate classification (taxonomy is comprehensive), decent metadata extraction.

Week 2 (early feedback):
  Human reviewers correct classifications and flag missing metadata.
  Corrections stored in MongoDB.
  RAG returns: RRS + schema.org + corrections.
  LLM adjusts: "this org's vendor invoices always have a PO number and cost centre."

Month 3 (patterns emerge):
  Enough corrections to surface org-specific patterns.
  System suggests new MetadataSchema fields from correction patterns + schema.org.
  Admin reviews and promotes useful suggestions.
  RAG priority shifts: admin schema > corrections > schema.org.

Month 6+ (mature):
  Admin-curated schemas cover most active categories.
  Training corpus exemplars indexed — similar past documents improve accuracy.
  Schema.org becomes a fallback for rare/new record types.
  System targets 85-93% hierarchical F1 (Payne thesis benchmark).
  
Month 12+ (model stepping):
  Rich correction history + exemplars reduce reliance on model reasoning.
  Step down from Sonnet to Haiku for routine classifications.
  RAG context does the heavy lifting; model does pattern matching.
```

## The RRS Test Set as Validation Benchmark

The 63-document test set in `RRS_Classifier_Taxonomy.xlsx` provides a ready-made evaluation framework:

**Difficulty distribution:**
- Easy (clear signals, single FA) — baseline accuracy target
- Medium (ambiguity, contextual reasoning needed) — where RAG context helps most  
- Hard (multi-functional, edge cases, privileged material) — stress test

**Key edge cases the test set covers:**
- Multi-functional documents (board presentation covering finance + strategy + governance → classify by audience)
- Cross-FA ambiguity (expense claim → HR payroll or Finance AP?)
- Privilege markers (attorney-client email → flag separately from classification)
- System-generated reports (ERP output → classify by content subject, not source)
- Embedded sub-documents (vendor contract with DPA as Schedule 3 → classify by primary wrapper)

**How to use it:**
1. Run all 63 test descriptions through the classifier
2. Compare predicted FA/RC/RT against expected values
3. Measure accuracy by difficulty tier
4. Track improvement as each RAG layer is added (RRS taxonomy → + schema.org → + corrections → + exemplars)

## Implementation Approach

### Phase 1: Ingest RRS Taxonomy as the Classification Backbone

Replace the current flat taxonomy with the full RRS 3-tier hierarchy.

**New/updated MongoDB collection: `classification_taxonomy`**

```json
{
  "functionalArea": {
    "code": "FA-02",
    "name": "Finance & Accounting"
  },
  "recordClass": {
    "code": "RC-06",
    "name": "Accounts Payable"
  },
  "recordType": {
    "code": "RT-017",
    "name": "Vendor Invoices",
    "retentionTrigger": "Payment date",
    "retentionPeriod": "7 years",
    "disposition": "Destroy",
    "legalBasis": "IRS",
    "keySignals": ["invoice", "vendor name", "invoice number", "payment terms", "AP"],
    "edgeCaseNotes": "SaaS subscription invoices may also appear under IT licensing"
  },
  "active": true
}
```

**Impact:** The `get_classification_taxonomy` MCP tool now returns a richer hierarchy. The LLM gets retention rules and key signals alongside category names — it knows *why* a document matters, not just *what* to call it.

### Phase 2: Ingest Schema.org Definitions

Store relevant schema.org type definitions linked to RRS record types.

**New MongoDB collection: `schema_org_types`**

```json
{
  "schemaType": "Invoice",
  "schemaUrl": "https://schema.org/Invoice",
  "description": "A statement of the money due for goods or services; a bill.",
  "properties": [
    {
      "name": "customer",
      "type": "Organization or Person",
      "description": "Party placing the order or paying the invoice."
    },
    {
      "name": "provider",
      "type": "Organization or Person",
      "description": "The service provider, service operator, or service performer."
    },
    {
      "name": "totalPaymentDue",
      "type": "MonetaryAmount",
      "description": "The total amount due."
    },
    {
      "name": "paymentDueDate",
      "type": "Date",
      "description": "The date that payment is due."
    },
    {
      "name": "paymentStatus",
      "type": "Text",
      "description": "The status of payment: whether the invoice has been paid or not."
    }
  ],
  "linkedRecordTypes": ["RT-017", "RT-018", "RT-019", "RT-020", "RT-021"],
  "active": true
}
```

**Seeded types (~15-20):** Invoice, Legislation, LegislationObject, Report, NewsArticle, JobPosting, HowTo, TechArticle, DigitalDocument, CreativeWork, MonetaryAmount, Person, Organization, RealEstateListing, SoftwareSourceCode.

### Phase 3: RAG-Powered MCP Tool

**New MCP tool: `get_document_type_knowledge`**

```
Input:  recordTypeCode (e.g. "RT-017"), documentTextSnippet (first ~500 chars)
Output: merged context from all knowledge layers
```

**Retrieval logic:**

1. **RRS record type** — load the full RT definition including retention rules, legal basis, key signals, and edge case notes
2. **Admin-curated schema** (if exists) — authoritative extraction fields
3. **Correction history** — aggregate patterns from past corrections for this RT
4. **Schema.org fallback** — if no admin schema, find linked schema.org type and return its properties as suggested extraction fields
5. **Exemplar match** — if training corpus is indexed, find the most similar past document and return its classification as a reference point

**Output format to LLM:**

```
### RT-017: Vendor Invoices (FA-02 Finance & Accounting > RC-06 Accounts Payable)

**Retention:** 7 years from payment date → Destroy | Legal basis: IRS
**Key signals:** 'invoice', vendor name, invoice number, payment terms, AP

**Metadata extraction (authoritative schema):**
Extract ONLY these fields:
  - from_company (TEXT, required): The company issuing the invoice
  - to_company (TEXT, required): The company receiving the invoice
  - invoice_number (KEYWORD, required): Unique invoice identifier
  - total_amount (CURRENCY, required): The total amount due
  - due_date (DATE): When payment is due
  ...

**Org-specific patterns (from 47 past corrections):**
  - 89% of invoices in this org include a PO number (not in schema — consider adding)
  - VAT is always 20% — flag if different

**Schema.org reference (Invoice):**
  Standard fields not in your schema: paymentStatus, billingPeriod, accountId
  These may be present — extract if clearly visible.

**Edge case note:** SaaS subscription invoices may also appear under IT licensing — verify primary function.
```

### Phase 4: Training Corpus Ingestion

Following the build sequence from `RRS_Training_Sources.docx`:

**Phase 4a — Anchor corpus:**
- EDGAR filings (Finance, Legal, Governance) — bulk download via API
- Enron emails (Communications, HR) — CMU dataset
- CourtListener (Litigation) — bulk download API
- CUAD contracts (Contracts) — HuggingFace

**Phase 4b — Gap fill:**
- GovInfo.gov (Governance, Regulatory) — API
- HuggingFace LexGLUE/LEDGAR (Legal benchmarks)
- Job posting datasets (Recruitment)

**Phase 4c — Privacy & compliance:**
- EDPB decisions and guidance
- ICO templates

**Storage:** Index exemplar documents into Elasticsearch with their known RT classification. The RAG tool searches these by text similarity to find reference points for ambiguous documents.

### Phase 5: Schema Suggestion Engine

When correction feedback reveals patterns that don't match any existing schema field:

1. Aggregate corrections: "12 vendor invoices were corrected to include a PO number"
2. Match against schema.org: `Order.orderNumber` is the closest match
3. Surface to admin: "Suggest adding field `po_number` (KEYWORD) to Vendor Invoice schema — based on 12 corrections"

### Phase 6: Elasticsearch as Full Retrieval Engine

Move beyond simple MongoDB queries to hybrid search:

- Index RRS definitions, schema.org types, corrections, metadata schemas, and exemplars into ES
- BM25 for keyword matching + vector similarity (if embedding model available) for semantic matching
- Cross-RT learning: "this document looks like a vendor invoice but was filed under Operations"

## What Changes in the Codebase

| Component | Change | Phase | Effort |
|---|---|---|---|
| `gls-governance` | Extend taxonomy model to support RRS 3-tier hierarchy (FA/RC/RT) with retention metadata | 1 | Medium |
| `gls-app-assembly` | Seeder to ingest `RRS_Classifier_Taxonomy.xlsx` into MongoDB | 1 | Small |
| `gls-mcp-server` | Update `get_classification_taxonomy` to return enriched hierarchy | 1 | Small |
| `gls-governance` | New `SchemaOrgType` model + repository | 2 | Small |
| `gls-app-assembly` | Seeder for schema.org type definitions linked to RTs | 2 | Small |
| `gls-mcp-server` | New `get_document_type_knowledge` RAG tool | 3 | Medium |
| `gls-llm-orchestration` | Update classification prompt to use RAG tool | 3 | Small |
| `gls-app-assembly` | Training corpus ingestion pipeline (EDGAR, CUAD, etc.) | 4 | Large |
| `gls-app-assembly` | ES indexing for exemplar documents | 4 | Medium |
| `gls-governance` | Schema suggestion service (correction pattern → field suggestion) | 5 | Medium |
| `gls-app-assembly` | ES hybrid search for full RAG retrieval | 6 | Medium |

## Relationship to Existing MCP Tools

The RAG approach **evolves** existing tools rather than replacing them:

| Current Tool | Evolution |
|---|---|
| `get_classification_taxonomy` | Returns full RRS 3-tier hierarchy with retention rules and key signals (Phase 1) |
| `get_metadata_schemas` | Still authoritative. Called internally by `get_document_type_knowledge` (Phase 3) |
| `get_correction_history` | Still works. Incorporated into RAG tool's merged response (Phase 3) |
| `get_org_pii_patterns` | Unchanged — PII is a separate concern |
| `get_sensitivity_definitions` | Could be enriched with RRS legal basis data (Phase 1) |
| `get_retention_schedules` | Superseded by RT-level retention metadata from RRS taxonomy (Phase 1) |
| `get_governance_policies` | Could be enriched with RRS disposition rules (Phase 1) |
| **NEW** `get_document_type_knowledge` | Unified RAG tool merging all knowledge layers (Phase 3) |

## Validation Strategy

### Using the RRS Test Set

Run the 63-document test set at each phase and track improvement:

| Metric | Baseline (current) | + RRS Taxonomy | + Schema.org | + Corrections | + Exemplars | Target |
|---|---|---|---|---|---|---|
| FA-level accuracy | ? | ? | ? | ? | ? | >95% |
| RC-level accuracy | ? | ? | ? | ? | ? | >90% |
| RT-level accuracy | ? | ? | ? | ? | ? | 85-93% |
| Easy docs correct | ? | ? | ? | ? | ? | >98% |
| Medium docs correct | ? | ? | ? | ? | ? | >85% |
| Hard docs correct | ? | ? | ? | ? | ? | >70% |
| Metadata extraction F1 | ? | ? | ? | ? | ? | >80% |

**Benchmark source:** Payne (UBC thesis) reports 85-93% hierarchical F1 for automated records classification, outperforming expert human records managers by ~17%.

### Regression Testing

After each phase, re-run the test set to ensure new RAG layers don't degrade accuracy on previously-correct classifications. The edge case notes in the test set (T-059 to T-063) are specifically designed to catch regressions.

## Distribution via the Governance Hub

### The Idea

The governance hub already distributes governance packs (taxonomy, retention, PII patterns, etc.) as versioned, importable bundles. The RAG knowledge layers — RRS taxonomy, schema.org mappings, metadata schemas, and training corpus references — should be **published as hub components** that any tenant instance can pull from.

This means:
- The hub becomes the **single source of truth** for classification knowledge
- Tenants like IG Central subscribe to the packs they need (e.g. "UK General + RRS Full Taxonomy")
- New knowledge (schema.org mappings, updated retention rules, training corpus references) flows to all tenants via version updates
- Tenants can extend hub knowledge with their own org-specific corrections and schemas

### New Component Types

The hub currently supports 9 `ComponentType` values: `TAXONOMY_CATEGORIES`, `RETENTION_SCHEDULES`, `SENSITIVITY_DEFINITIONS`, `GOVERNANCE_POLICIES`, `PII_TYPE_DEFINITIONS`, `METADATA_SCHEMAS`, `STORAGE_TIERS`, `TRAIT_DEFINITIONS`, `PIPELINE_BLOCKS`.

Three new component types are needed for the RAG strategy:

| New ComponentType | What It Contains | Purpose |
|---|---|---|
| `SCHEMA_ORG_MAPPINGS` | Schema.org type definitions linked to record types | Universal document-type knowledge for metadata extraction cold start |
| `TRAINING_CORPUS_REFS` | Curated references to public data sources (URLs, descriptions, RT mappings, quality ratings) | Tells tenants where to find training data for their classifier |
| `CLASSIFICATION_TEST_SETS` | Test document descriptions with expected classifications and difficulty ratings | Validation benchmark — tenants can measure their classifier's accuracy |

### How It Fits the Existing Pack Model

The RRS taxonomy becomes a **new pack** on the hub, separate from the existing UK General and UK Healthcare packs:

```
Governance Hub
├── UK General Governance Framework (existing)
│   └── v1: 7 components (taxonomy, retention, sensitivity, PII, policies, storage, traits)
│
├── UK Healthcare Records Management (existing)
│   └── v1: 3 components (taxonomy, retention, PII)
│
├── RRS Records Classification Framework (NEW)
│   └── v1: 5 components
│       ├── TAXONOMY_CATEGORIES — full 3-tier RRS hierarchy (15 FA, 42 RC, 113 RT)
│       │   with retention triggers, periods, dispositions, legal bases
│       ├── RETENTION_SCHEDULES — derived from RRS RT-level retention rules
│       ├── METADATA_SCHEMAS — schema.org-informed extraction schemas per RT
│       ├── SCHEMA_ORG_MAPPINGS — schema.org type→RT linkages with property definitions
│       └── CLASSIFICATION_TEST_SETS — 63 test documents with expected RT classifications
│
├── RRS Training Corpus Guide (NEW)
│   └── v1: 1 component
│       └── TRAINING_CORPUS_REFS — 24 curated public data sources with quality ratings,
│           RT mappings, and recommended build sequence
│
└── (future packs: US NARA, EU GDPR, industry-specific, community-contributed)
```

### Pack Component Data Structures

**SCHEMA_ORG_MAPPINGS component data:**

```json
[
  {
    "schemaType": "Invoice",
    "schemaUrl": "https://schema.org/Invoice",
    "description": "A statement of the money due for goods or services; a bill.",
    "linkedRecordTypes": ["RT-017", "RT-018", "RT-019", "RT-020", "RT-021"],
    "properties": [
      { "name": "customer", "type": "Organization or Person", "description": "Party paying the invoice." },
      { "name": "totalPaymentDue", "type": "MonetaryAmount", "description": "The total amount due." },
      { "name": "paymentDueDate", "type": "Date", "description": "The date that payment is due." }
    ]
  },
  {
    "schemaType": "Legislation",
    "schemaUrl": "https://schema.org/Legislation",
    "linkedRecordTypes": ["RT-032", "RT-033", "RT-035", "RT-037"],
    "properties": [
      { "name": "jurisdiction", "type": "Text", "description": "The jurisdiction of the legislation." },
      { "name": "legislationType", "type": "Text", "description": "The type of legislation (act, decree, bill)." },
      { "name": "legislationDate", "type": "Date", "description": "The date of adoption or signature." }
    ]
  }
]
```

**TRAINING_CORPUS_REFS component data:**

```json
[
  {
    "sourceId": "EDGAR",
    "name": "SEC EDGAR — Corporate Filings",
    "url": "https://www.sec.gov/cgi-bin/browse-edgar",
    "bulkDownloadUrl": "https://www.sec.gov/Archives/edgar/full-index/",
    "category": "Corporate Filings",
    "quality": "RECOMMENDED",
    "description": "Millions of labeled corporate filings (10-K, 8-K, contracts as Exhibit 10).",
    "linkedRecordTypes": ["RT-022", "RT-028", "RT-029", "RT-039", "RT-087"],
    "format": "HTML, XBRL, plain text",
    "license": "Public domain (US government)",
    "buildPhase": 2,
    "notes": "Single richest source of labeled business documents available publicly."
  },
  {
    "sourceId": "CUAD",
    "name": "Contract Understanding Atticus Dataset",
    "url": "https://huggingface.co/datasets/cuad",
    "category": "Legal — Annotated Contracts",
    "quality": "RECOMMENDED",
    "description": "500 expert-labeled commercial contracts with 41 clause categories.",
    "linkedRecordTypes": ["RT-028", "RT-029", "RT-030", "RT-031"],
    "format": "HuggingFace dataset",
    "license": "Open (Atticus Project)",
    "buildPhase": 2,
    "notes": "Gold standard for contract NLP. Use as validation set for contract RTs."
  }
]
```

**CLASSIFICATION_TEST_SETS component data:**

```json
[
  {
    "testId": "T-001",
    "description": "LinkedIn job posting for a Senior Software Engineer position, listing responsibilities, required qualifications, salary range, and application instructions.",
    "keySignals": ["job posting", "role title", "salary range", "application link"],
    "expectedFA": "FA-01",
    "expectedRC": "RC-01",
    "expectedRT": "RT-001",
    "difficulty": "EASY",
    "edgeCaseNotes": null
  },
  {
    "testId": "T-059",
    "description": "Email thread between the CFO and outside counsel discussing litigation strategy and potential settlement range for a pending lawsuit.",
    "keySignals": ["attorney-client", "settlement", "CFO", "counsel", "without prejudice"],
    "expectedFA": "FA-03",
    "expectedRC": "RC-11",
    "expectedRT": "RT-032",
    "difficulty": "HARD",
    "edgeCaseNotes": "Privileged email — no single RT is perfect; closest is Litigation. Classifier should flag privilege markers."
  }
]
```

### Tenant Import Flow

When IG Central (or any tenant) imports the RRS pack:

```
1. Admin browses hub → finds "RRS Records Classification Framework"
2. Admin selects components to import:
   ☑ TAXONOMY_CATEGORIES — full 3-tier hierarchy
   ☑ RETENTION_SCHEDULES — RT-level retention rules
   ☑ METADATA_SCHEMAS — schema.org-informed extraction schemas
   ☑ SCHEMA_ORG_MAPPINGS — for RAG cold start
   ☐ CLASSIFICATION_TEST_SETS — optional, for benchmarking
   
3. Import service merges into tenant's MongoDB:
   - Taxonomy categories created/updated (FA → RC → RT hierarchy)
   - Retention schedules linked to categories
   - MetadataSchemas created with schema.org-informed fields
   - Schema.org mappings stored for RAG tool to query

4. Classification pipeline immediately benefits:
   - get_classification_taxonomy returns 113 RTs (not 7 categories)
   - get_document_type_knowledge has schema.org fallbacks for all RTs
   - Tenant-specific corrections layer on top over time
```

### Tenant-Specific vs Shared Knowledge

| Knowledge Layer | Owned By | How It Flows |
|---|---|---|
| RRS taxonomy (FA/RC/RT definitions) | Hub | Hub → tenant (import). Tenant can extend with custom RTs. |
| Retention rules, legal basis | Hub | Hub → tenant. Tenant can override per jurisdiction. |
| Schema.org mappings | Hub | Hub → tenant. Universal — rarely needs customisation. |
| MetadataSchemas (field definitions) | Hub seeds, tenant extends | Hub provides defaults. Tenant adds org-specific fields via admin UI. |
| Training corpus references | Hub | Hub → tenant. Tenant decides which to download and ingest. |
| Test sets | Hub | Hub → tenant. Tenant runs them to benchmark their classifier. |
| Corrections | Tenant only | Never flows back to hub (org-specific, potentially sensitive). |
| Exemplar documents | Tenant only | Indexed locally. Contains actual document content. |

### Future: Community Feedback to Hub

Tenants could optionally contribute back to the hub:
- **Schema suggestions** — "our tenants keep adding `po_number` to invoice schemas" → hub adds it to the next version
- **Test case contributions** — anonymised test descriptions that improve the validation set
- **Accuracy reports** — aggregated F1 scores that help the hub maintainers know which RTs need better training data

This would require a contribution/review pipeline on the hub, similar to how the existing `PackReview` model works but for knowledge contributions.

### Hub Codebase Changes

| Component | Change | Effort |
|---|---|---|
| `gls-governance-hub` | Add `SCHEMA_ORG_MAPPINGS`, `TRAINING_CORPUS_REFS`, `CLASSIFICATION_TEST_SETS` to `ComponentType` enum | Small |
| `gls-governance-hub-app` | Seed RRS pack with taxonomy, retention, schema.org mappings, test set | Medium |
| `gls-governance-hub-app` | Seed Training Corpus Guide pack | Small |
| `gls-app-assembly` | Extend import service to handle new component types | Medium |
| `gls-app-assembly` | Store imported schema.org mappings in a queryable collection | Small |
| `gls-mcp-server` | RAG tool queries imported schema.org mappings as a knowledge layer | Medium |
| Frontend | Hub browse UI shows new component types with appropriate icons/descriptions | Small |

## Key Design Decisions Still Open

1. **RRS taxonomy as default or optional** — does every tenant get the full 113-RT taxonomy, or is it an opt-in pack? Smaller orgs may want a simplified subset. Consider a "RRS Lite" pack with just the FAs and RCs, letting tenants enable specific RTs.
2. **Hub-first or tenant-first** — do new schema.org mappings and test sets originate on the hub and flow down, or can tenants create them locally and optionally push up?
3. **Embedding model for semantic search** — local model (Ollama), API-based, or start with keyword/BM25 only?
4. **Training corpus ingestion** — does the hub store actual corpus documents, or just references/URLs? References are lightweight but require tenants to download and ingest themselves. Actual documents raise storage and licensing concerns.
5. **Confidence thresholds for schema suggestions** — how many corrections before suggesting a new field? Configurable per org?
6. **Multi-RT documents** — the test set notes that some documents span multiple RTs (e.g. vendor contract with embedded DPA). Primary-purpose rule is documented, but should the system also tag secondary RTs?
7. **Model stepping timeline** — at what correction volume / accuracy level do we step down from Sonnet to Haiku for routine classifications?
8. **Community contribution model** — if tenants can contribute test cases, schema suggestions, or accuracy reports back to the hub, what's the review/approval process? Who curates quality?
9. **Pack composition** — can a tenant combine components from multiple packs (e.g. RRS taxonomy + UK Healthcare PII patterns + their own custom retention schedules)?
10. **Version compatibility** — when the hub publishes a new version of the RRS pack, how does a tenant that has extended the taxonomy with custom RTs merge the update without losing their additions?

## Reference Files

| File | Location | Purpose |
|---|---|---|
| RRS Classifier Taxonomy | `documentation/RRS_Classifier_Taxonomy.xlsx` | 3-tier taxonomy + test set + legend |
| RRS Training Sources | `documentation/RRS_Training_Sources.docx` | 24 curated data sources with build sequence |
| Current MetadataSchema model | `backend/gls-governance/.../models/MetadataSchema.java` | Existing field extraction model |
| MCP tools | `backend/gls-mcp-server/.../tools/` | Current tool implementations |
| Classification prompt | `backend/gls-llm-orchestration/.../prompts/ClassificationPromptBuilder.java` | Current LLM prompt |
| GovernanceDataSeeder | `backend/gls-app-assembly/.../bootstrap/GovernanceDataSeeder.java` | Current taxonomy + schema seeding |
