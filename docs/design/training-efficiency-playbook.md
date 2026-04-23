# Training to Maximum Efficiency: A Complete Playbook

**Author:** IG Central Engineering
**Date:** April 2026
**Hardware:** Mac Studio M3 Ultra, 96GB Unified Memory, Ollama 0.21 (MLX backend)

---

## 1. The Problem

The IG Central classification pipeline has a cold-start problem. Every document currently requires an LLM call costing $0.01-0.03, taking 30-150 seconds, and dependent on cloud API availability or local Ollama reliability. The goal is to reduce LLM dependency to near-zero while maintaining or improving classification accuracy.

But this is not a flat classification problem. The system implements **ISO 15489 Business Classification Schemes (BCS)** — the international standard for records management, adopted in over 50 countries. Getting classification right is not just a UX concern; it determines retention schedules, access controls, disposition actions, and regulatory compliance. A misclassified document can mean destroyed records that should have been preserved, or retained records that should have been destroyed.

### 1.1 ISO 15489-1:2016 — The Standard

ISO 15489-1:2016 (*Information and documentation — Records management — Concepts and principles*) is the foundational international standard for records management. First published in 2001 (superseding Australian Standard AS 4390:1996), it was substantially revised in 2016 to take a principles-based approach rather than a prescriptive methodology.

The standard defines **four records controls** (Clause 8) that implement the outcomes of appraisal:

1. **Metadata schemas** — what metadata must be captured at point of creation and what accumulates over time
2. **Business Classification Schemes (BCS)** — tools for linking records to the context of their creation
3. **Access and permission rules** — who can access records, what actions are permitted, under what conditions
4. **Disposition authorities** — how long records are kept and what happens at end of life

And **eight records management processes** (Clause 9):

1. Capture — determining a record should be made and kept
2. Registration — assigning a unique identifier
3. **Classification** — systematic identification according to the BCS
4. **Access and security classification** — applying access restrictions (separate from records classification)
5. **Disposition status identification** — linking the record to its retention rule
6. Storage — secure and appropriate housing
7. Use and tracking — monitoring access and movement
8. Disposition implementation — executing retention actions

The standard requires that **records classification** (which business function/activity created the record) and **security classification** (who can access it) are treated as separate, independent metadata elements. A record is classified as "HR > Recruitment > Shortlisting" (records classification) AND "CONFIDENTIAL" (security classification). The training strategy must handle both.

### 1.2 The Business Classification Scheme (BCS)

ISO 15489 mandates that classification be based on an analysis of **business functions and activities**, not organisational structure. Functions are stable — they persist through restructures, mergers, and leadership changes. This is rooted in the archival principle of *respect des fonds* — records derive meaning from their creation context.

The canonical hierarchy is the **Function-Activity-Transaction (FAT) model**:

```
FUNCTION (Level 1)         ACTIVITY (Level 2)            TRANSACTION (Level 3)
──────────────────        ──────────────────            ──────────────────────
The largest units of      Major tasks performed         The smallest unit of
business activity.        to accomplish each            business activity.
Most stable — rarely      function.                     Individual actions
change.                                                 within an activity.

Example:                  Example:                      Example:
Finance & Accounting      Accounts Payable              Invoice Processing
Human Resources           Employee Records              Performance Reviews
Legal & Compliance        Contracts                     Service Agreements
```

**Depth is not fixed at three levels.** The FAT model is the canonical minimum, but:
- The National Archives of Australia (NAA) specifies schemes "can provide classification to two, three, and sometimes four levels"
- The UK Local Government Functional Classification Scheme (LGFCS, updated 2024) is structured in three tiers but councils commonly expand to levels 4-6
- Topic/subtopic levels can be added beneath transactions for further grouping
- Some implementations (e.g., Northern Territory, Australia) recommend limiting to two levels for simplicity

**Typical BCS sizes:**
- Enterprise: 10-20 functions, 50-150 activities, 200-1000+ transaction types
- Medium government department: 15-30 functions, each with 5-15 activities, yielding 200-500 classification points at the activity level
- The shared Australian AFDA Express covers common administrative functions across all government agencies as a baseline

### 1.3 Records Metadata Required by the Standard

ISO 15489 (supported by ISO 23081 *Metadata for Records*) requires metadata across four dimensions:

**Point-of-capture metadata** (immutable, set at classification time):
- Position in BCS (function/activity/transaction code)
- Classification date and classifying agent
- Classification scheme version
- Creating agent (person or system)
- Mandate/authority under which created

**Disposition metadata** (derived from classification + disposition authority):
- Applicable disposition authority
- Retention period and trigger type
- Calculated disposal date
- Disposition action: **destroy**, **transfer** (to archive), **review**, or **permanent retention**
- Legal hold status

**Access and security metadata** (independent from records classification):
- Security classification level
- Access permissions (agents, roles)
- Restriction conditions and periods

**Process metadata** (accumulates over record lifecycle):
- Actions taken on the record (access, modification, reclassification)
- Agents involved at each stage
- Audit trail of all classification changes with reasons

Every classification decision — whether made by a human, an LLM, or a BERT model — must produce this metadata. Automated classification must be auditable to the same standard as manual classification (ISO 16175:2020).

### 1.4 Retention and Disposition

A correctly classified record inherits its retention rules from the disposition authority linked to its BCS position. Getting classification wrong means getting retention wrong.

**Retention triggers** from the standard:

| Trigger Type | Example | Complexity |
|---|---|---|
| Time from creation | "Retain for 7 years from date of creation" | Simple |
| Time from last action | "Retain for 5 years from last access" | Requires tracking |
| End of financial year | "Retain for 6 years from end of FY in which created" | Calendar-dependent |
| Event-based | "Retain until termination of employment" | Requires event detection |
| Compound | "7 years after termination of employment" | Event + time |
| Supersession | "Until superseded, then retain for 2 years" | Version-aware |

**Disposition actions:**
- **Destroy** — secure, irreversible deletion (requires certificate of destruction)
- **Transfer** — move custody to an archive or other authority
- **Review** — re-evaluate to determine further action
- **Permanent retention** — keep indefinitely (typically transferred to national archive)

A cross-function misclassification can change the retention period by years and the disposition action entirely (destroy vs permanent retention). This is not an academic concern — it is a regulatory compliance failure.

### 1.5 Why Hierarchy Matters for Training

A flat classifier treats "Finance" and "Invoice Processing" as equally distant from "Accounts Payable." But in the BCS:

- Confusing `FIN-AP-INV` (Invoice Processing) with `FIN-AP-PO` (Purchase Orders) is a **minor error** — same parent, similar retention period, likely same sensitivity and disposition action
- Confusing `FIN-AP-INV` with `HR-EMP-PER` (Performance Reviews) is a **critical error** — wrong function, wrong retention regime, wrong sensitivity, wrong PII handling, potentially wrong jurisdiction

The training strategy must account for this. A model that sometimes confuses siblings but never confuses across functions is more useful — and more compliant — than one with higher flat accuracy but occasional cross-function errors.

**Industry benchmark:** Published case studies (AHRC, RecordPoint) report that supervised ML classification achieves ~80% accuracy with sufficient training data. The minimum cited for reliable supervised training is **1,000+ pre-classified records per category** — though this drops significantly with modern transformer architectures and data augmentation techniques.

### 1.6 Record Determination — What Is and Isn't a Record

Before classification can happen, the system must answer a more fundamental question: **is this a record at all?**

ISO 15489-1:2016 (Clause 4) defines a record as *"information created, received, and maintained as evidence and as an asset by an organisation in pursuit of legal obligations or in the transaction of business."* Not everything that enters a document management system meets this definition.

**Four characteristics of an authoritative record** (Clause 4):

1. **Authenticity** — can be proven to have been created or sent by the purported agent at the purported time
2. **Reliability** — contents can be trusted as a full and accurate representation of the transactions or facts they attest to
3. **Integrity** — complete and unaltered; protected against unauthorised modification
4. **Usability** — can be located, retrieved, presented, and interpreted

**Appraisal** (Clause 7) is the process of evaluating business activities to determine which records need to be created and captured, how long they need to be kept, and how they should be managed. It involves analysis of the business context, regulatory environment, and risk assessment.

In practice, a significant portion of documents entering any system are **not records**:

| Category | Examples | Why Not a Record |
|----------|---------|-----------------|
| **Transient/ephemeral** | Calendar invites, auto-notifications, system alerts, read receipts | No evidential value; serve momentary purpose only |
| **Drafts and working copies** | Incomplete documents, tracked-changes versions, personal notes | Not the authoritative final version; may mislead if retained |
| **Duplicates and copies** | Forwarded emails, CC copies, downloaded duplicates | Another copy is the record of reference; retaining duplicates inflates storage and creates version confusion |
| **Personal/non-business** | Personal correspondence, lunch orders, social messages | Not created in the transaction of business |
| **Reference material** | Published articles, external reports, marketing collateral received | Created by external parties; not evidence of the organisation's own transactions |
| **Superseded content** | Outdated policies, expired price lists, previous brochure versions | The current version is the record; superseded versions may be disposable depending on retention rules |
| **Spam and junk** | Unsolicited marketing, phishing attempts, automated spam | No business value; potential security risk |
| **System-generated artefacts** | Log files, temp files, cache files, thumbnails | Infrastructure artefacts, not business records (though some log files may be records for audit purposes) |

**Why this matters for training efficiency:**

A system that classifies everything wastes LLM calls on non-records. If 30-40% of incoming documents are not records, that's 30-40% of classification cost, human review time, and training data pollution eliminated by a pre-classification gate.

More critically, non-records that get classified and retained create **governance risk**:
- They consume retention schedule resources unnecessarily
- They may surface in Subject Access Requests (SARs) or legal discovery, creating liability
- They dilute the quality of the records collection, making genuine records harder to find
- They pollute BERT training data if auto-collected, teaching the model to classify non-records into BCS categories

**Record determination in the pipeline should be the first gate — before BCS classification, before BERT, before LLM.**

### 1.7 Auto-Classification and the Standard

ISO 15489-1:2016 is technology-neutral. It does not specifically mention AI or machine learning, but it permits automated capture and classification as long as:

- Records controls are applied correctly regardless of method
- Classification decisions are **auditable** (who/what classified, when, why)
- Incorrect classifications are **correctable** with full reclassification audit trail
- The BCS itself is authoritative and current

ISO 16175:2020 (*Processes and functional requirements for software managing records*) addresses automated classification capabilities directly, including requirements for confidence scoring, human review of low-confidence results, and audit logging.

ARMA International's 2026 roadmap identifies AI-assisted classification as a primary use case, noting that "without a robust BCS, AI struggles to make sense of disorganised or poorly classified data." The BCS must come first; automation follows.

### 1.8 Current State

- **45 training samples across 18 categories** (2.5 average per category, 11 singletons)
- **Auto-collection disabled** by default
- **Corrections don't feed BERT training** in the current deployment
- **Tool-calling failures** waste 20-30% of Ollama classification attempts
- **No confidence calibration** — BERT's 0.85 threshold is arbitrary
- **No stable label registry** — category mappings break between training runs
- **No hierarchical error weighting** — sibling confusion penalised same as cross-function
- **No record determination gate** — everything that enters the system is treated as a record and classified

---

## 2. Record Determination — The Pre-Classification Gate

Before any BCS classification, the system must determine whether the document is a record worth classifying. This is a binary decision that sits upstream of the entire classification pipeline.

### 2.1 The Two-Stage Model

```
Document arrives
       │
       ▼
┌──────────────────┐
│ RECORD            │
│ DETERMINATION     │  "Is this a record?"
│ (BERT binary,     │
│  ~2ms)            │
└────────┬─────────┘
         │
    ┌────┴────┐
    │         │
  RECORD    NOT A RECORD
    │         │
    ▼         ▼
  BCS       DISPOSITION
  Classification   │
  Pipeline    ┌────┴────┐
              │         │
           DISCARD   HOLD FOR
           (spam,    REVIEW
           junk)     (uncertain)
```

Record determination produces one of three outcomes:

1. **RECORD** (high confidence) — proceed to BCS classification pipeline
2. **NOT_A_RECORD** (high confidence) — route to disposition (discard or archive as non-record)
3. **UNCERTAIN** — route to human review for determination

### 2.2 Training the Record Determination Model

This is a binary BERT classifier (RECORD vs NOT_RECORD), separate from the BCS classification model. It is simpler, faster to train, and requires less data:

- **Two classes** — much easier than 50-100 BCS categories
- **Distinctive patterns** — non-records have strong signal (email signatures, "unsubscribe", "FYI only", calendar formatting, system-generated templates)
- **Trainable with 100-200 examples** per class for useful accuracy
- **Fast inference** — binary classifier on DistilBERT runs in ~1ms

**Training data sources for NOT_RECORD class:**

| Source | Examples | Signal Strength |
|--------|---------|----------------|
| Email metadata | Auto-replies, out-of-office, delivery receipts, read receipts | Very strong — template patterns |
| Transient notifications | Calendar invites, Slack digests, system alerts, password resets | Very strong — system-generated |
| Drafts | Documents with "DRAFT" watermark, tracked changes, "[WIP]" in filename | Moderate — some drafts become records |
| Duplicates | Documents with identical content hash to existing records | Strong — deduplication signal |
| Personal content | Non-business correspondence, personal file names | Moderate — context-dependent |
| Marketing/spam | Unsolicited commercial content, newsletters, promotions | Very strong — vocabulary patterns |
| Reference material | Published standards, external reports, downloaded articles | Moderate — some reference material may be records if received in transaction of business |

**Training data sources for RECORD class:**

Use the existing classified documents in the system — anything that has been through BCS classification and either approved or corrected is by definition a record.

**Handling ambiguity:**

Some document types are contextually dependent:
- A **draft contract** is not a record, but a **final signed contract** is
- A **forwarded email** may be a duplicate (not a record) or the recipient's copy (a record in their context)
- **Meeting notes** may be personal working notes (not a record) or formal minutes (a record)
- **Reference material** received from a regulator in the context of an audit is a record

The model should assign low confidence to these ambiguous cases, routing them to human review. Over time, human determinations on these edge cases become the highest-value training data for improving the gate.

### 2.3 Record Determination Metadata

Per ISO 15489, the determination decision is itself an auditable action:

- **Determination outcome** — RECORD, NOT_RECORD, UNCERTAIN
- **Determination method** — BERT_AUTO, LLM_ASSISTED, HUMAN_REVIEW
- **Determining agent** — system identifier or user ID
- **Determination date** — timestamp
- **Confidence score** — model confidence for automated determinations
- **Reason** (for non-records) — e.g., "duplicate", "transient notification", "personal correspondence", "draft"

Non-records should not simply be deleted immediately. They should follow their own lightweight disposition:
- **Spam/junk** — destroy immediately (configurable auto-destroy after review period)
- **Duplicates** — link to record of reference, then destroy or archive
- **Drafts** — hold for configurable period (e.g., 30 days) in case the final version hasn't been captured
- **Transient** — destroy after review period
- **Uncertain** — hold in review queue until human determination

### 2.4 Integration with BCS Training

Record determination and BCS classification share a training feedback loop:

- Documents determined as NOT_RECORD should **never** enter BCS training data (prevents pollution)
- Documents incorrectly determined as NOT_RECORD (human override → RECORD) become high-value training examples for the determination model
- The existing `_OTHER` class in BCS training partially serves this purpose but conflates "unrecognised record" with "not a record" — these should be separated
- Auto-collection filters should check record determination status before collecting BCS training samples

### 2.5 Cost Impact

If 30% of incoming documents are not records (a conservative estimate for typical email-heavy environments):

| Without gate | With gate |
|-------------|-----------|
| 1000 docs × $0.02 LLM cost = $20 | 700 records × $0.02 + 300 non-records × $0.0001 = $14.03 |
| 1000 docs through review queue | 700 records through review queue |
| Training data polluted with non-records | Clean training data, records only |

At scale the savings compound — fewer LLM calls, smaller review queue, cleaner training data, faster BERT convergence.

---

## 3. The Five Stages

```
Stage 0        Stage 1         Stage 2          Stage 3          Stage 4
BOOTSTRAP  →   COLD TRAIN  →   WARM LOOP   →   ACCELERATION →   SELF-SUSTAINING
                
0 samples       50-200          200-1000         1000-5000        5000+
No BERT         First model     BERT handles     BERT handles     BERT handles
LLM only        LLM + review    40-60%           70-85%           90%+
$0.02/doc       $0.02/doc       $0.01/doc        $0.003/doc       $0.001/doc
```

Each stage has specific actions, exit criteria, and efficiency targets. The system should progress through these without manual intervention beyond human review gates.

**Record determination runs across all stages.** It can be bootstrapped with a simple rule-based gate in Stage 0 (file type filters, duplicate detection, spam keywords) and upgraded to a trained BERT binary classifier in Stage 1-2. By Stage 3, it should be handling 95%+ of non-record filtering automatically.

---

## 3. Stage 0 — Bootstrap (0 to 50 samples)

### 3.1 Goal

Get enough high-quality labeled data to train a first BERT model. Every label at this stage directly shapes the model's foundation.

### 3.2 LLM Selection

**Use Claude Haiku via API.** Not Ollama.

Rationale:
- Tool-calling reliability is critical — every failed classification is a wasted opportunity for training data
- Claude Haiku costs ~$0.002 per classification (50 docs = $0.10)
- Haiku's tool discipline is materially better than any Ollama model for multi-tool MCP chains
- Speed: 2-5 seconds vs 30-150 seconds on Ollama
- At 50 documents, the total API cost is less than the electricity cost of running Ollama for the same work

The goal is not to save money at this stage — it's to generate perfect labels as fast as possible.

### 3.3 Bootstrap by Taxonomy Level

Not all BCS categories need equal attention. Prioritise by the ISO 15489 hierarchy:

**Level 1 — Functions (typically 8-20 in an enterprise BCS): Train these first.**

Function-level classification is the highest-value, lowest-risk starting point:
- Fewest categories — functions represent the broadest business areas
- Most distinct from each other — Finance vs HR vs Legal have fundamentally different vocabulary
- Largest error impact — cross-function misclassification means wrong retention regime, wrong disposition action, potentially wrong jurisdiction
- Easiest for BERT to learn — broad document patterns correlate strongly with functions

A BERT model that reliably identifies the function can cut the LLM's work in half — the LLM only needs to determine the activity and transaction within the correct function.

**Level 2 — Activities (typically 50-150 across a full BCS): Train alongside functions.**

Activity categories within the same function share vocabulary but differ in purpose. "Contracts" vs "Litigation" under Legal — both mention legal terms, but contracts are about agreements and litigation about disputes. The standard defines activities as "major tasks performed to accomplish each function."

**Level 3 — Transactions (potentially 200-1000+ in a deep BCS): Train last.**

Transaction-level categories are the finest-grained and hardest to distinguish. "Invoice Processing" vs "Purchase Orders" within Accounts Payable — both mention amounts, vendors, and dates. These require the most training data per category to achieve reliable separation. Some organisations limit their BCS to two levels (function + activity) and only introduce transaction-level classification for high-volume areas where the distinction matters for retention or access control.

### 3.4 Actions

**3.4.1 Enable auto-collection immediately**

```
Settings → bert.training.auto_collect_enabled = true
Settings → bert.training.auto_collect_min_confidence = 0.85
```

Every LLM classification above 0.85 confidence automatically becomes a training sample. This is the single most important configuration change.

**3.4.2 Run backfill on existing classifications**

The system has 76 existing classifications with 0.93 average confidence that were never collected. The `BertTrainingDataBackfillRunner` should run on startup to harvest these. If it hasn't been enabled, trigger it manually or run:

```javascript
// In MongoDB shell
db.classification_results.find({confidence: {$gte: 0.85}}).forEach(function(cr) {
    if (!db.bert_training_data.findOne({sourceDocumentId: cr.documentId})) {
        db.bert_training_data.insertOne({
            text: db.documents.findOne({_id: ObjectId(cr.documentId)})?.extractedText?.substring(0, 2000),
            categoryId: cr.categoryId,
            categoryName: cr.categoryName,
            sensitivityLabel: cr.sensitivityLabel || "INTERNAL",
            source: "AUTO_COLLECTED",
            sourceDocumentId: cr.documentId,
            confidence: cr.confidence,
            verified: false,
            createdAt: new Date(),
            updatedAt: new Date()
        });
    }
});
```

**3.4.3 Target documents by BCS level**

Upload representative documents systematically across the taxonomy. The approach scales with BCS depth:

- **2-level BCS** (function + activity, ~15-50 categories): 2-3 documents per function, 1-2 per high-frequency activity. Bootstrap completes quickly.
- **3-level BCS** (function + activity + transaction, ~75-500 categories): 2-3 documents per function, 1-2 per high-frequency activity. Transaction-level categories will populate naturally through volume — don't try to manually bootstrap 200+ transaction types.
- **Multi-jurisdiction BCS**: Ensure each jurisdiction has representation. UK invoices and US invoices use different vocabulary, date formats, legal references, and regulatory context even when they map to equivalent categories.

Focus on the functions that receive the most document volume in production. Common high-volume functions across most organisations: Finance, HR, Legal, Procurement. These should be bootstrapped first.

**3.4.4 Review and correct with ISO context**

Every correction at this stage is worth 3x in training weight. When reviewing:
- Check the **classification code** — is the document in the right branch of the tree?
- If the FUNCTION is correct but the ACTIVITY is wrong, that's a minor correction (valuable but low severity)
- If the FUNCTION is wrong, that's a critical correction (highest training value)
- Verify the **sensitivity label** aligns with the category's `defaultSensitivity`
- Check whether the **personal data flag** is appropriate for the document content

Corrections should flow into training data. If they don't (current gap), the correction collection in `BertTrainingDataCollector.collectFromCorrection()` needs to be wired into the correction save path.

### 3.5 Exit Criteria

- 50+ total samples
- All functions in the BCS have 3+ samples
- 5+ categories with 5+ samples each (graduation threshold)
- At least 10 human-verified samples (corrections or approved)

### 3.6 Time Estimate

With focused effort: 2-4 hours. Upload documents, let them classify, review results. The LLM does the heavy lifting.

---

## 4. Stage 1 — Cold Train (50 to 200 samples)

### 4.1 Goal

Train the first BERT model and begin the feedback loop. The model won't be great — expect 60-75% accuracy. That's fine. It creates the flywheel.

### 4.2 Hierarchical Training Strategy

**Option A: Single flat classifier (current approach)**

Train one BERT model on all categories (FUNCTIONs + ACTIVITYs + TRANSACTIONs as flat labels). Simple but treats all errors equally.

**Option B: Hierarchical cascade (recommended at scale)**

Train separate BERT models per taxonomy level:
1. **FUNCTION classifier** — 8-20 classes, high accuracy achievable with limited data
2. **ACTIVITY classifier per FUNCTION** — 3-6 classes each, focused scope
3. **TRANSACTION classifier per ACTIVITY** — 2-4 classes each, most specific

Cascade at inference: FUNCTION → ACTIVITY → TRANSACTION. Each level narrows the problem.

**For Stage 1, use Option A** — you don't have enough data to train per-function classifiers yet. But design the label registry to support the hierarchy from day one (see 4.4).

### 4.3 First Training Run

Trigger training from AI > Models page. The system sends samples to the Python training worker with:

- Base model: `distilbert-base-uncased`
- Epochs: 3 (effective batch size 16 via gradient accumulation)
- Categories below 5 samples → merged into `_OTHER`
- Correction samples included 3x
- `_OTHER` class padded with external samples (25% of training set or minimum 5)

### 4.4 Hierarchical Label Registry

**Current problem:** Each training run assigns new label indices to categories. This breaks incremental learning.

**Fix:** Maintain a persistent `bert_label_registry` that encodes the BCS hierarchy:

```json
{
    "categoryId": "abc123",
    "categoryName": "Invoice Processing",
    "classificationCode": "FIN-AP-INV",
    "taxonomyLevel": "TRANSACTION",
    "parentCode": "FIN-AP",
    "functionCode": "FIN",
    "path": ["FIN", "FIN-AP", "FIN-AP-INV"],
    "labelIndex": 3,
    "jurisdiction": "US",
    "bcsVersion": 2,
    "createdAt": "2026-04-20T00:00:00Z"
}
```

New categories get the next available index. Removed categories keep their index (never reassigned). The training worker reads this registry instead of computing indices dynamically. The `bcsVersion` tracks the classification scheme version — ISO 15489 requires that records classified under previous versions remain findable, and the label registry must support this.

Storing the hierarchy in the label registry enables:
- Hierarchical error weighting in loss functions (Stage 2)
- Per-function classifier training (Stage 3)
- Confusion analysis scoped to siblings vs cross-function
- Audit trail linking BERT label indices back to authoritative BCS codes

### 4.5 Evaluate Before Promoting

After training completes, examine the per-class metrics — but group them by FUNCTION:

```
FUNCTION: Finance (FIN)
  FIN-AP  Accounts Payable    F1: 0.82  ✓
  FIN-AR  Accounts Receivable F1: 0.71  ✓
  FIN-FR  Financial Reporting F1: 0.45  ⚠ (confused with FIN-AP)

FUNCTION: Human Resources (HR)
  HR-EMP  Employee Records    F1: 0.88  ✓
  HR-REC  Recruitment         F1: 0.35  ⚠ (only 3 samples, below graduation)

FUNCTION: Legal (LEG)
  LEG-CON Contracts           F1: 0.79  ✓
  LEG-LIT Litigation          F1: 0.91  ✓
```

**Key evaluation criteria:**
- Cross-function accuracy should be >90% (Finance doc classified as Finance, not HR)
- Within-function accuracy varies — siblings with similar vocabulary will confuse early on
- Categories below graduation threshold (5 samples) will show as `_OTHER` — this is expected

**Do not promote a model below 60% accuracy.** The 50% gate exists as a safety net, but a 55% model generates more confusion than value.

### 4.6 LLM Selection Change

Once BERT is active with a promoted model:

**Switch to Ollama with `command-r:35b` or `llama3.3:70b`.**

Rationale:
- BERT now handles the confident cases — LLM only sees the uncertain/rejected documents
- Volume to LLM drops 30-50%, making Ollama's slower speed acceptable
- Tool-calling reliability still matters, so pick a model with strong tool discipline
- Zero marginal cost — the M3 Ultra is already running

### 4.7 The Pre-Injection Optimisation

**This is the single highest-impact architectural change for training efficiency.**

Currently, every LLM classification makes 8-10 MCP tool calls:
1. `get_classification_taxonomy` — returns the full tree (all FUNCTIONs → ACTIVITYs → TRANSACTIONs with codes, scope notes, typical records, retention info)
2. `get_sensitivity_definitions` — sensitivity levels
3. `get_correction_history` — past corrections by category + MIME type
4. `get_org_pii_patterns` — org-specific PII + false positives
5. `get_governance_policies` — relevant policy info
6. `get_document_traits` — document characteristics
7. `get_metadata_schemas` — extraction fields for the matched category
8. `save_classification_result` — persist the decision

Total: 8-20 seconds of tool-call overhead, plus the model frequently fails to make the final `save_classification_result` call after the long chain.

**The fix:** Pre-inject steps 1-7 into the system prompt. The pipeline already supports `injectTaxonomy`, `injectSensitivities`, `injectTraits`, and `injectPiiTypes`. Extend this to include corrections and metadata schemas. The model then only needs to make one tool call: `save_classification_result`.

**Taxonomy-aware pre-injection:** For large taxonomies (75+ categories), the full tree is 3000-5000 tokens. Two optimisation strategies:

1. **Full injection** — include the entire taxonomy. Works for taxonomies under 50 categories. The LLM sees the complete tree with codes, scope notes, and retention.

2. **BERT-guided injection** — when BERT has a prediction (even low confidence), only inject the relevant FUNCTION branch plus its siblings:
   ```
   BERT predicts: FIN-AP (confidence 0.45 — too low to accept)
   Inject: Full FIN branch (FIN-AP, FIN-AR, FIN-FR + all TRANSACTIONs)
           + sibling FUNCTIONs as one-liners for cross-check
   ```
   This reduces taxonomy tokens from 4000 to ~800 while giving the LLM enough context to confirm or override BERT's guess. The LLM's job shifts from "classify from scratch" to "validate and refine BERT's suggestion."

**Impact:**
- Tool-calling failure rate drops from ~20% to near-zero (one tool call vs eight)
- Classification latency drops 40-60% (no round-trip overhead)
- Unlocks smaller, faster models (tool discipline no longer a constraint)
- `qwen2.5:32b` becomes viable immediately (excellent JSON output)
- BERT-guided injection makes the LLM dramatically more efficient on deep taxonomies

**Implementation priority: Do this before anything else in Stage 1.**

### 4.8 Active Data Collection Strategy

With BERT running and the pre-injection optimisation:

1. **BERT-confident documents** (>0.85) → auto-classified, auto-collected into training data
2. **BERT-uncertain documents** (0.5-0.85) → sent to LLM with BERT-guided injection → auto-collected
3. **BERT-rejected documents** (_OTHER) → sent to LLM with full taxonomy → auto-collected
4. **Human corrections** → collected with source=CORRECTION, verified=true

The correction feedback loop is the engine:
- User overrides a classification → correction sample created (3x weight)
- User approves a classification → positive signal (1x weight)
- Both feed directly into BERT training data

### 4.9 Exit Criteria

- BERT model promoted with >60% accuracy
- Cross-function accuracy >85% (function-level correctness per BCS)
- Pre-injection optimisation deployed (single tool call)
- Auto-collection running and accumulating samples
- BERT handling 30-50% of classifications without LLM
- 200+ total training samples
- All BCS functions have 10+ samples; 10+ activities graduated

---

## 5. Stage 2 — Warm Loop (200 to 1000 samples)

### 5.1 Goal

Establish the self-reinforcing training loop. Every document that flows through the system makes the next classification better.

### 5.2 Retraining Cadence

The retraining advisor checks daily at 06:00 and recommends retraining when:
- 20+ new samples since last training
- 10+ new corrections since last training
- Model older than 14 days with 5+ new samples

**At Stage 2 volume, expect retraining every 3-7 days.** Each retrain should show measurable improvement in accuracy and F1 as data accumulates.

Track across versions — grouped by taxonomy level:

```
v1: 50 samples,  8 categories
    FUNCTION accuracy: 78%   ACTIVITY accuracy: 55%   Overall: 62%

v2: 120 samples, 12 categories
    FUNCTION accuracy: 88%   ACTIVITY accuracy: 65%   Overall: 71%

v3: 250 samples, 15 categories
    FUNCTION accuracy: 93%   ACTIVITY accuracy: 74%   Overall: 78%

v4: 500 samples, 18 categories + TRANSACTIONs graduating
    FUNCTION accuracy: 95%   ACTIVITY accuracy: 81%   TRANSACTION accuracy: 68%   Overall: 83%
```

FUNCTION accuracy should hit 90%+ early. ACTIVITY takes longer. TRANSACTION accuracy is the long tail.

### 5.3 Hierarchical Loss Weighting

**Current problem:** The training loss treats all misclassifications equally. Confusing `FIN-AP` with `FIN-AR` (siblings under Finance) has the same penalty as confusing `FIN-AP` with `HR-EMP` (cross-function).

**Fix:** Implement hierarchical cross-entropy loss that penalises by tree distance:

```python
def hierarchical_loss(logits, labels, label_registry, alpha=0.5):
    """
    Standard CE loss + weighted penalty for cross-function errors.
    alpha controls the hierarchy weight (0 = flat CE, 1 = heavy hierarchy penalty)
    """
    ce_loss = F.cross_entropy(logits, labels)
    
    predictions = logits.argmax(dim=-1)
    
    # For each misclassification, compute tree distance penalty
    hierarchy_penalty = 0
    for pred, true in zip(predictions, labels):
        if pred != true:
            pred_fn = label_registry[pred.item()]['functionCode']
            true_fn = label_registry[true.item()]['functionCode']
            if pred_fn != true_fn:
                hierarchy_penalty += 1.0   # Cross-function: full penalty
            else:
                pred_act = label_registry[pred.item()].get('parentCode', '')
                true_act = label_registry[true.item()].get('parentCode', '')
                if pred_act != true_act:
                    hierarchy_penalty += 0.5  # Cross-activity: half penalty
                else:
                    hierarchy_penalty += 0.1  # Same activity, different transaction: minor
    
    hierarchy_penalty /= len(labels)
    return ce_loss + alpha * hierarchy_penalty
```

**Impact:** BERT learns that getting the FUNCTION right is more important than the exact TRANSACTION. This matches the governance reality — a document filed under the wrong FUNCTION may get the wrong retention schedule entirely, while a document under the wrong sibling TRANSACTION typically has the same retention.

### 5.4 LLM-Assisted Data Augmentation (Taxonomy-Aware)

When specific categories plateau, use the LLM to generate targeted training data.

**Strategy 1: Synthetic generation using ISO metadata**

For each category with <10 samples, the taxonomy provides rich context for generation:

> "Generate 5 realistic document excerpts (200-500 words each) for the following records category:
> 
> **Classification code:** FIN-AP-INV
> **Category:** Invoice Processing
> **Function:** Finance & Accounting > Accounts Payable > Invoice Processing
> **Description:** [category description]
> **Scope notes:** [inclusion/exclusion guidance]
> **Typical records:** Vendor invoices, credit notes, payment vouchers, invoice dispute correspondence
> **Jurisdiction:** US
> **Legal citation:** IRS requirements (IRC 6001); state sales tax statutes
> **Retention:** 7 years from end of financial year
> **Sensitivity:** CONFIDENTIAL
> **Contains personal data:** No
> 
> Each excerpt should be clearly distinguishable and vary in writing style, formality, and specific details. Include realistic dates, amounts, vendor names, and reference numbers."

The ISO metadata gives the LLM everything it needs to generate realistic, correctly-scoped documents. The scope notes are particularly valuable — they tell the LLM what to include and exclude.

Mark as `source=LLM_SYNTHETIC, verified=false`. Weight at 0.5x in training.

**Strategy 2: Confusion-driven collection using classification codes**

After each training run, examine confusion pairs. The classification codes reveal whether confusion is structural:

| Confused Pair | Codes | Relationship | Action |
|---|---|---|---|
| Accounts Payable ↔ Accounts Receivable | `FIN-AP` ↔ `FIN-AR` | Siblings (same FUNCTION) | Generate boundary examples |
| Contracts ↔ Service Agreements | `LEG-CON` ↔ `PRO-SC-SVC` | Cross-function | Check taxonomy — may need scope refinement |
| Employee Records ↔ Recruitment | `HR-EMP` ↔ `HR-REC` | Siblings | Expected early confusion, will resolve with data |

For sibling confusion, generate boundary examples using both categories' scope notes:

> "These two categories under [FUNCTION] are being confused:
> Category A: [code] [name] — Scope: [scope notes]. Typical records: [typical records]
> Category B: [code] [name] — Scope: [scope notes]. Typical records: [typical records]
> 
> Generate 3 documents for each that sit near the boundary. Use the scope notes to ensure each document clearly belongs to one category."

**Strategy 3: Taxonomy refinement using confusion data**

If two categories consistently confuse the model (F1 < 0.5 for both despite 20+ samples each), the ISO taxonomy itself may need refinement:

- **Sibling confusion** (same parent): Scope notes may overlap. Refine the inclusion/exclusion criteria.
- **Cross-function confusion**: Categories may be genuinely similar (e.g., "IT Security Incidents" vs "Compliance Audit Reports" about security). Consider adding a scope note exclusion.
- **Parent-child confusion**: A FUNCTION absorbing all its children's documents. This is normal at low data volumes — the ACTIVITY level needs more distinct examples.

Taxonomy improvements in the Governance Hub propagate to all tenants and compound across all future training runs.

### 5.5 Implement Confidence Calibration

BERT's raw confidence scores are poorly calibrated. A 0.85 score doesn't mean "85% chance of being correct." Temperature scaling fixes this:

1. After training, hold out 20% of validation data
2. Find temperature T that minimises negative log-likelihood on held-out data:
   ```python
   from scipy.optimize import minimize_scalar
   
   def nll(T):
       scaled_logits = logits / T
       probs = softmax(scaled_logits)
       return -np.mean(np.log(probs[range(len(labels)), labels]))
   
   result = minimize_scalar(nll, bounds=(0.1, 10), method='bounded')
   temperature = result.x
   ```
3. At inference, divide logits by T before softmax
4. Store T as part of the model version metadata

**Calibrate per taxonomy level.** FUNCTION-level confidence and TRANSACTION-level confidence have different distributions. A model might be very confident about FUNCTION (correctly) but artificially confident about TRANSACTION (incorrectly). Separate calibration temperatures per level would be ideal but requires the hierarchical cascade (Stage 3).

### 5.6 Implement Drift Detection

Track BERT classifications that get overridden in the review queue. Compute a rolling 7-day override rate:

```
override_rate = corrections_that_changed_category / total_reviews
```

**Track separately by taxonomy level:**
- FUNCTION override rate >5% = critical (wrong branch of the tree)
- ACTIVITY override rate >15% = moderate (wrong sub-branch)
- TRANSACTION override rate >20% = expected during training (fine-grained)

Alert (amber banner in monitoring) when FUNCTION override rate exceeds 5%. Alert (red) when it exceeds 10%.

**The Reports page already shows this** in the Feedback Loop tab. Wire the threshold alerts into the monitoring page and add per-level breakdown.

### 5.7 Jurisdiction-Aware Training

Documents from different jurisdictions have different vocabulary, date formats, legal references, and regulatory context:

- **UK documents**: "Ltd", "HMRC", "Companies Act 2006", DD/MM/YYYY dates, GBP amounts
- **US documents**: "Inc", "IRS", "Sarbanes-Oxley", MM/DD/YYYY dates, USD amounts
- **EU documents**: "GmbH", "GDPR", "Directive 2006/43/EC", DD.MM.YYYY dates, EUR amounts

If a tenant imports multiple jurisdiction packs, the training data should be balanced across jurisdictions. A BERT model trained predominantly on US invoices may struggle with UK invoices even though they map to equivalent categories.

**Action:** Track jurisdiction distribution in training data (available via `classificationCode` → `label_registry` → `jurisdiction`). Alert if any jurisdiction has <20% representation for a shared category.

### 5.8 LLM Model Transition

With pre-injection active and BERT handling 40-60% of documents:

**Switch to `qwen2.5:32b` via Ollama.**

Rationale:
- Pre-injection means only 1 tool call — Qwen's tool-calling weakness is neutralised
- Qwen 2.5:32b has the best structured JSON output of any Ollama model
- ~18 tok/s on M3 Ultra — fast enough for the reduced LLM volume
- Uses ~20 GB RAM, leaving 76 GB for Docker, BERT, and KV cache

### 5.9 Exit Criteria

- 1000+ training samples
- BERT accuracy >80% with calibrated confidence
- FUNCTION-level accuracy >95%
- ACTIVITY-level accuracy >80%
- BERT handling 40-60% of classifications
- Stable label registry operational with hierarchy encoded
- Override rate below 15% (FUNCTION override rate below 5%)
- LLM-assisted augmentation used for at least 3 underperforming categories
- 3+ successful retraining cycles completed
- All FUNCTIONs fully graduated; 80%+ of ACTIVITYs graduated

---

## 6. Stage 3 — Acceleration (1000 to 5000 samples)

### 6.1 Goal

Push BERT hit rate to 70-85%. The LLM becomes a specialist for edge cases rather than the primary classifier.

### 6.2 Hierarchical Cascade Classifier

At 1000+ samples, implement the hierarchical cascade:

```
Document text
     │
     ▼
┌─────────────────────┐
│ FUNCTION Classifier  │  8-20 classes, very high accuracy
│ (BERT, ~2ms)         │
└─────────┬───────────┘
          │ Predicted: FIN (confidence 0.97)
          ▼
┌─────────────────────┐
│ FIN ACTIVITY Classifier │  3-6 classes within Finance
│ (BERT, ~2ms)             │
└─────────┬───────────────┘
          │ Predicted: FIN-AP (confidence 0.89)
          ▼
┌─────────────────────────┐
│ FIN-AP TRANSACTION Classifier │  2-4 classes within Accounts Payable
│ (BERT, ~2ms)                   │
└─────────┬─────────────────────┘
          │ Predicted: FIN-AP-INV (confidence 0.82)
          ▼
     Combined: FIN-AP-INV
     Path: ["FIN", "FIN-AP", "FIN-AP-INV"]
     Min confidence across chain: 0.82
```

**Advantages over flat classification:**
- Each classifier has fewer classes → higher accuracy per level
- FUNCTION classifier trains effectively with just 5-10 samples per class
- A 95% FUNCTION accuracy × 90% ACTIVITY accuracy × 85% TRANSACTION accuracy = 73% overall
- But cross-function errors drop to near-zero (the most dangerous error type)
- Can route to LLM at any level — if FUNCTION is confident but ACTIVITY isn't, only the ACTIVITY needs LLM resolution

**Training:** Separate DistilBERT models per level. The FUNCTION classifier trains on all samples (label = FUNCTION code). Each ACTIVITY classifier trains only on samples within its FUNCTION. Same for TRANSACTION.

**Inference:** Total latency ~6ms for all three levels (ONNX). Same as a single flat classifier since each model is smaller.

### 6.3 MLX Fine-Tuning the LLM

This is the stage where fine-tuning the LLM itself pays off. The training data now includes:
- 1000+ classified documents with full ISO metadata (codes, paths, retention, jurisdiction)
- 200+ human corrections with explicit reasoning
- Dozens of `ai_usage_log` entries showing successful prompt → classification pairs

**Fine-tune a domain-specific Qwen 2.5:14b using MLX LoRA:**

```bash
pip install mlx-lm

mlx_lm.lora \
    --model mlx-community/Qwen2.5-14B-Instruct-4bit \
    --train \
    --data ./training_data \
    --batch-size 4 \
    --lora-layers 16 \
    --epochs 3 \
    --learning-rate 1e-5 \
    --adapter-path ./adapters/classification-v1
```

Training data format — each example includes the full ISO classification output:
```json
{
    "messages": [
        {"role": "system", "content": "[pre-injected taxonomy with codes, retention, scope notes]"},
        {"role": "user", "content": "Classify this document:\n\nDocument ID: abc123\nFile name: invoice-2026-0042.pdf\nMIME type: application/pdf\n\n[document text]"},
        {"role": "assistant", "content": "<tool_call>{\"name\": \"save_classification_result\", \"arguments\": {\"documentId\": \"abc123\", \"categoryId\": \"cat-fin-ap-inv\", \"categoryName\": \"Invoice Processing\", \"classificationCode\": \"FIN-AP-INV\", \"classificationPath\": [\"FIN\", \"FIN-AP\", \"FIN-AP-INV\"], \"classificationLevel\": \"TRANSACTION\", \"sensitivityLabel\": \"CONFIDENTIAL\", \"jurisdiction\": \"US\", \"legalCitation\": \"IRS requirements (IRC §6001)\", \"confidence\": 0.92, \"reasoning\": \"Document is a vendor invoice from ACME Corp dated 2026-03-15 for $12,450. Contains purchase order reference PO-2026-0891. Standard accounts payable document.\", \"retentionTrigger\": \"END_OF_FINANCIAL_YEAR\", \"retentionPeriodText\": \"7 years from end of financial year\"}}</tool_call>"}
    ]
}
```

**Include the full ISO output structure in training data.** This teaches the model to produce complete governance metadata — classification code, path, jurisdiction, legal citation, retention — not just a category name. This is the critical differentiator vs generic classification.

**Export to GGUF and serve through Ollama:**
```bash
mlx_lm.fuse --model mlx-community/Qwen2.5-14B-Instruct-4bit --adapter-path ./adapters/classification-v1
python convert_hf_to_gguf.py ./fused_model --outtype q4_K_M
ollama create igcentral-classifier -f Modelfile
```

### 6.4 Shadow Mode Deployment

Before promoting the fine-tuned model, run it in shadow mode:

1. Primary path: Current model (Qwen 2.5:32b or Claude) classifies as normal
2. Shadow path: Fine-tuned model classifies the same document in parallel
3. Record both results in `bert_disagreements` collection
4. After 100+ shadow classifications, compare:
   - Agreement rate on FUNCTION level (should be >95%)
   - Agreement rate on ACTIVITY level (should be >85%)
   - Agreement rate on TRANSACTION level (should be >80%)
   - Where they disagree, which one is right? (check against human corrections)
   - Latency and token usage comparison

Only promote the fine-tuned model when shadow results confirm it matches or exceeds the current model at every taxonomy level.

### 6.5 BERT Model Upgrade Path

At 1000+ samples, consider upgrading from DistilBERT to a larger model:

| Model | Parameters | RAM | Accuracy Gain | Training Time | Max Tokens |
|-------|-----------|-----|---------------|---------------|------------|
| distilbert-base (current) | 66M | ~0.5 GB | Baseline | ~5 min | 512 |
| bert-base-uncased | 110M | ~0.8 GB | +3-5% | ~10 min | 512 |
| modernbert-base | 149M | ~1.1 GB | +5-8% | ~15 min | 8192 |
| modernbert-large | 395M | ~2.8 GB | +8-12% | ~45 min | 8192 |

ModernBERT's 8192 token context window is significant for document classification — many governance documents exceed DistilBERT's 512 token limit. Currently text is truncated to 256 tokens (2000 chars) for training, potentially losing critical content in the middle or end of documents. ModernBERT can see the full document.

With the hierarchical cascade, each individual classifier is small (DistilBERT is fine per-level). But if staying with a flat classifier, ModernBERT-base is the recommended upgrade.

### 6.6 Train/Val Data Leakage Fix

**Current problem:** Samples from the same document can end up in both training and validation sets, inflating accuracy metrics.

**Fix:** Group the train/val split by `sourceDocumentId`:

```python
from sklearn.model_selection import GroupShuffleSplit

gss = GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=42)
groups = [sample['source_document_id'] for sample in samples]
train_idx, val_idx = next(gss.split(X, y, groups=groups))
```

All samples from the same document stay together in either train or val. This gives honest accuracy numbers.

### 6.7 Tiered Inference Architecture

At this stage, implement four inference tiers — starting with record determination:

```
Document → Record Determination (BERT binary, ~1ms)
              │
              ├── NOT_RECORD (high confidence)
              │   → Skip classification entirely
              │   → Disposition: discard / archive as non-record
              │
              ├── UNCERTAIN → Human review (is this a record?)
              │
              └── RECORD (high confidence)
                  │
                  ▼
              BERT BCS Cascade (ONNX, ~6ms total)
                  │
                  ├── All levels confident (>0.85 calibrated)
                  │   → Done with full ISO metadata (70-85% of records)
                  │   → classificationCode, path, jurisdiction, retention all set
                  │
                  ├── Function confident, activity/transaction uncertain
                  │   → Fine-tuned LLM with BERT-guided injection (~3s)
                  │   → Only needs to resolve within the correct function branch
                  │
                  ├── _OTHER at any level
                  │   → Fine-tuned LLM with full taxonomy injection (~5s)
                  │   → Novel record type BERT hasn't seen
                  │
                  └── Still uncertain after LLM (<0.7 confidence)
                      → Human review queue
                      → Correction feeds back into all tiers
```

**No cloud API calls.** Everything runs locally. Total cost per document: electricity only. Non-records are filtered in ~1ms before any classification cost is incurred.

### 6.8 Exit Criteria

- 5000+ training samples
- Hierarchical cascade deployed (or flat classifier >85%)
- FUNCTION accuracy >97%, ACTIVITY >88%, TRANSACTION >80%
- BERT handling 70-85% of classifications
- Fine-tuned LLM deployed and handling remaining 15-30%
- FUNCTION override rate below 2%
- Shadow mode validation completed
- All FUNCTIONs and 90%+ of ACTIVITYs graduated

---

## 7. Stage 4 — Self-Sustaining (5000+ samples)

### 7.1 Goal

The system classifies 90%+ of documents without any human intervention. Humans review only genuine edge cases. The training loop is self-sustaining.

### 7.2 The Self-Teaching Cycle

```
┌───────────────────────────────────────────────────────────────┐
│                                                               │
│  Upload → BERT Cascade → All levels confident?                │
│              │                    │                            │
│              │               Yes  ▼                           │
│              │          Auto-classify with full ISO metadata   │
│              │          (code, path, retention, jurisdiction)  │
│              │                    │                            │
│              No                   │                            │
│              ▼                    │                            │
│       Fine-tuned LLM             │                            │
│       (BERT-guided injection)    │                            │
│              │                    │                            │
│       Confident? ──Yes──► Classify with ISO metadata          │
│              │                    │                            │
│              No                   │                            │
│              ▼                    │                            │
│       Human Review  ◄────────────┘                            │
│              │                                                │
│       ┌──────┴──────┐                                         │
│       │             │                                         │
│   Approve       Override                                      │
│       │             │                                         │
│       ▼             ▼                                         │
│  Training Data  Training Data (3x weight)                     │
│       │             │                                         │
│       └──────┬──────┘                                         │
│              │                                                │
│       Retrain trigger? ──Yes──► Train BERT (per-level)        │
│              │                         │                      │
│              No                        ▼                      │
│              │                 Promote if metrics pass         │
│              │                         │                      │
│              └─────────────────────────┘                      │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

Every document that passes through carries full ISO 15489 metadata:
- Classification code and materialised path
- Jurisdiction and legal citation
- Retention schedule with trigger and disposition
- Sensitivity label aligned to category default
- Personal data and vital record flags

This metadata is immutable once classified — stored on both `DocumentClassificationResult` and `DocumentModel` for compliance audit.

### 7.3 Downsize the LLM

With BERT handling 90%+ and corrections providing rich context, the fallback LLM only needs to handle novel document types and genuine ambiguity. Downsize:

**`qwen2.5:7b` (fine-tuned) via Ollama**

- 5 GB RAM, ~50 tok/s on M3 Ultra
- Fine-tuned on your taxonomy, tool-calling format, ISO output structure, and correction history
- More than sufficient for pattern-matching with pre-injected context
- Frees memory for everything else

### 7.4 Automated Retraining

At this stage, consider removing the manual promotion gate for incremental retrains:

**Auto-promote if ALL conditions met:**
1. New model accuracy >= previous promoted model accuracy (per taxonomy level)
2. FUNCTION accuracy >= 95%, ACTIVITY accuracy >= 85%
3. No per-class F1 dropped by more than 10% vs previous model
4. FUNCTION override rate in last 7 days < 3%
5. Shadow validation against 50+ recent documents shows >= 90% agreement

This keeps the system improving without admin intervention for routine retrains, while still requiring human approval for:
- First-ever promotion
- New base model (e.g., upgrading from DistilBERT to ModernBERT)
- Accuracy below thresholds
- Any FUNCTION-level per-class regression
- New governance pack import (new categories entering the system)

### 7.5 Continuous Monitoring

The Reports page provides all the charts needed. The key metrics to watch at Stage 4:

| Metric | Target | Alert Threshold | Action |
|--------|--------|-----------------|--------|
| BERT hit rate | >90% | <80% | Check for new document types |
| FUNCTION override rate | <2% | >5% | Critical — wrong branch of tree |
| ACTIVITY override rate | <8% | >15% | Retrain ACTIVITY classifiers |
| TRANSACTION override rate | <15% | >25% | Augment TRANSACTION categories |
| Avg classification latency | <10ms | >500ms | Check BERT service health |
| Training data growth | Steady | Flat for 14+ days | Verify auto-collection enabled |
| Cost per document | ~$0.001 | >$0.01 | Check BERT confidence threshold |
| Retention accuracy | >98% | <95% | Critical for compliance — verify schedule linkage |

### 7.6 Handling New Categories and Packs

**When a new taxonomy category is added:**

1. It starts with zero BERT training data → all documents route to LLM
2. LLM classifies with full context → auto-collected into training data
3. After 5+ samples → category graduates from _OTHER at its taxonomy level
4. After 20+ samples → retraining advisor triggers for the affected classifier
5. After retrain → BERT begins handling the new category
6. Cycle time: typically 1-2 weeks depending on document volume

**When a new governance pack is imported from the Hub:**

1. New FUNCTIONs, ACTIVITYs, TRANSACTIONs added to taxonomy
2. Label registry updated with new classification codes
3. All new categories start as _OTHER in BERT
4. LLM handles 100% of documents in new categories
5. Existing categories unaffected — BERT continues handling them
6. As data accumulates, new categories graduate into BERT
7. Full integration: 2-4 weeks for a new pack to be BERT-accelerated

**When a governance pack is updated (new version):**

1. Categories may be renamed, merged, split, or have retention changes
2. Stable label registry preserves existing label indices
3. Renamed categories: update registry, retrain with new labels
4. Split categories: new indices created, old samples may need re-labeling
5. Merged categories: both indices map to same label, auto-deduplicated in training
6. Retention/sensitivity changes: classification still valid, governance enforcement picks up new rules

---

## 8. Cost Model

### 8.1 Per-Document Cost at Each Stage

| Stage | BERT Cost | LLM Cost | Human Review | Total/Doc | Monthly (1000 docs) |
|-------|-----------|----------|--------------|-----------|-------------------|
| 0 (Bootstrap) | N/A | $0.002 (Haiku) | 30% reviewed | $0.003 | $3.00 |
| 1 (Cold Train) | $0.0001 | $0.015 (Ollama amortised) | 20% reviewed | $0.005 | $5.00 |
| 2 (Warm Loop) | $0.0001 | $0.008 (50% to LLM) | 10% reviewed | $0.003 | $3.00 |
| 3 (Acceleration) | $0.0001 | $0.003 (20% to LLM) | 5% reviewed | $0.001 | $1.00 |
| 4 (Self-Sustaining) | $0.0001 | $0.001 (10% to LLM) | 2% reviewed | $0.0005 | $0.50 |

*Ollama "cost" is amortised electricity + hardware depreciation. Actual marginal cost is near-zero.*

### 8.2 Time Investment

| Stage | Calendar Time | Active Human Hours | Automated |
|-------|-------------|-------------------|-----------|
| 0 → 1 | 1-2 days | 4-8 hours (uploading, reviewing) | LLM classification |
| 1 → 2 | 1-2 weeks | 2-4 hours (review queue, augmentation) | Auto-collection, retraining |
| 2 → 3 | 2-4 weeks | 2-4 hours (fine-tuning, shadow validation) | Self-teaching loop |
| 3 → 4 | 1-3 months | 1-2 hours/week (monitoring) | Fully automated |

The human investment front-loads heavily at bootstrap and tapers to monitoring.

---

## 9. Implementation Priority Order

These are ordered by impact-to-effort ratio. Each item is independently valuable.

### Tier 1 — Do This Week

| # | Action | Effort | Impact |
|---|--------|--------|--------|
| 1 | Enable auto-collection (`bert.training.auto_collect_enabled = true`) | 1 minute | Starts accumulating training data from every classification |
| 2 | Backfill existing 76 classifications into training data | 30 minutes | Instant 70+ samples, enough to begin training |
| 3 | Wire corrections into training data collection | 2 hours | Every human review improves BERT |
| 4 | Pre-inject MCP context into system prompt (taxonomy, corrections, schemas) | 4-6 hours | Eliminates tool-calling failures, enables smaller models, BERT-guided injection |

### Tier 2 — Do This Month

| # | Action | Effort | Impact |
|---|--------|--------|--------|
| 5 | Implement hierarchical label registry with ISO codes and paths | 3-4 hours | Enables incremental learning + hierarchical loss + per-level metrics |
| 6 | Rule-based record determination gate (file type, duplicate hash, spam keywords) | 3-4 hours | Filters 20-30% of non-records before classification, protects training data |
| 7 | Add confidence calibration (temperature scaling) | 3-4 hours | Right-sizes review queue, meaningful confidence thresholds |
| 8 | Track override rate per taxonomy level (function vs activity vs transaction) | 2-3 hours | Differentiates critical errors from minor ones |
| 9 | Train/val split by sourceDocumentId | 1 hour | Honest accuracy metrics |
| 10 | LLM-assisted synthetic generation using ISO metadata (scope notes, typical records) | 2-3 hours | Accelerates graduation for underrepresented categories |

### Tier 3 — Do This Quarter

| # | Action | Effort | Impact |
|---|--------|--------|--------|
| 11 | Train BERT binary classifier for record determination (RECORD vs NOT_RECORD) | 1 day | Replaces rule-based gate with ML, handles ambiguous cases |
| 12 | Hierarchical cascade classifier (function → activity → transaction) | 2-3 days | Near-zero cross-function errors, per-level accuracy tracking |
| 13 | Hierarchical loss weighting (cross-function errors penalised more) | 3-4 hours | Better model priorities, governance-aligned error cost |
| 14 | Fine-tune Qwen 2.5:14b with MLX LoRA (including ISO output structure) | 1-2 days | Domain-specific LLM that produces full governance metadata |
| 15 | Shadow mode validation framework | 1 day | Safe deployment of new models |
| 16 | Upgrade BERT to ModernBERT-base (8192 token context) | 2-3 hours | Full-document classification, +5-8% accuracy |
| 17 | BERT-guided LLM injection (inject relevant function branch only) | 3-4 hours | Dramatic token reduction for deep taxonomies |
| 18 | Automated retraining with per-level promotion gates | 1 day | Removes manual bottleneck |

---

## 10. Measuring Success

### 10.1 North Star Metric

**BERT hit rate** — the percentage of documents classified by BERT without any LLM call.

```
Stage 0:  0%    (no BERT)
Stage 1:  30%   (first model, low accuracy)
Stage 2:  50%   (improving, warm loop active)
Stage 3:  75%   (acceleration, hierarchical cascade)
Stage 4:  90%+  (self-sustaining)
```

### 10.2 Quality Metrics by BCS Level

| BCS Level | Target Accuracy | Override Rate Target | ISO 15489 Compliance Impact |
|-----------|----------------|---------------------|----------------------------|
| Function | >97% | <2% | Wrong disposition authority, wrong retention regime, potential regulatory breach |
| Activity | >88% | <8% | Wrong sub-process, but likely same retention and sensitivity |
| Transaction | >80% | <15% | Minor — same retention, same sensitivity, same disposition action |

### 10.3 ISO 15489 Compliance Metrics

These metrics map directly to the standard's requirements:

- **Disposition authority accuracy** >98% — records linked to the correct retention schedule (Clause 8, disposition authorities)
- **Security classification accuracy** >95% — records get the correct access level (Clause 9, process 4)
- **Personal data flag accuracy** >95% — records containing personal data correctly identified (access and permission rules)
- **Jurisdiction correctness** 100% — records assigned to correct legal/regulatory framework
- **Audit completeness** 100% — every classification decision has agent, timestamp, BCS version, and confidence score (Clause 9, process 2-3; ISO 23081 metadata requirements)
- **Reclassification audit trail** — every correction preserves original classification, correcting agent, reason, and timestamp (ISO 23081 process metadata)

These matter more than raw classification accuracy. A model that gets the transaction wrong but the function and retention right is compliant. A model that gets the function wrong may trigger an incorrect disposition action — destroying records that should be permanently retained, or retaining records that should have been destroyed years ago. That is a governance failure with potential legal consequences.

### 10.4 What the Reports Page Shows

| Report Tab | Key Chart | What It Tells You |
|---|---|---|
| Model Performance | Accuracy/F1 over versions | Is BERT improving with each retrain? |
| Model Performance | BERT hit rate over time | Is BERT handling more documents? |
| Classification Quality | Confidence distribution | Is confidence well-calibrated? |
| Classification Quality | Top categories | Which ISO categories are most common? |
| Classification Quality | Sensitivity distribution | Is sensitivity spread realistic for the taxonomy? |
| Training Data | Samples per category | Which categories need more data? Which FUNCTIONs are underrepresented? |
| Training Data | Data growth over time | Is auto-collection working across all governance packs? |
| Cost & Efficiency | Daily cost trend | Is cost decreasing as BERT takes over? |
| Feedback Loop | Override rate over time | Is the model drifting? At which taxonomy level? |
| Feedback Loop | Confusion pairs | Which sibling categories need boundary examples? |
| Scatter Analysis | Samples vs F1 | Does more data actually improve the model per category? |
| Scatter Analysis | Doc size vs confidence | Are certain document lengths problematic (BERT 512 token limit)? |

---

## 11. Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Cross-function misclassification | Medium | **Critical** — wrong retention regime | Hierarchical loss weighting, FUNCTION accuracy gate >95% |
| Wrong retention schedule assigned | Medium | **Critical** — regulatory non-compliance | Denormalise retention at classification time, verify against schedule |
| BERT model promotes with hidden FUNCTION-level bias | Medium | High | Mandatory per-FUNCTION F1 review before promotion |
| Auto-collected data contains mislabeled samples | Medium | Medium | LLM quality audit using scope notes |
| Governance pack update changes categories | Medium | Medium | Stable label registry + automatic retraining trigger |
| Ollama model update changes behaviour | Low | High | Pin model versions, shadow mode before switching |
| Training data leakage inflates metrics | High (current) | Medium | GroupShuffleSplit by sourceDocumentId |
| Category drift (document distribution changes) | Medium | Medium | Rolling override rate per taxonomy level |
| Fine-tuned LLM produces incorrect ISO metadata | Low | High | Validate classification code exists in taxonomy before saving |
| New governance pack import breaks existing model | Medium | Medium | New categories start as _OTHER, existing unaffected |
| Jurisdiction mismatch (UK doc classified under US category) | Low | High | Track jurisdiction distribution, alert on mismatch patterns |

---

## 12. Summary

The path from zero to self-sustaining classification is not a single project — it's a flywheel that compounds with every document processed. The architecture for this flywheel already exists in IG Central. The gap is operational: enabling auto-collection, wiring corrections into training, and implementing the pre-injection optimisation.

Two dimensions shape the training strategy:

**First, record determination (ISO 15489 Clause 7, appraisal).** Not everything that enters the system is a record. Teaching the system to distinguish records from non-records — drafts, duplicates, spam, transient notifications, personal correspondence — eliminates 20-40% of classification workload, protects training data from pollution, and reduces governance risk from retaining non-records that could surface in legal discovery or SARs.

**Second, hierarchical classification (ISO 15489 Clause 8, BCS).** Not all classification errors are equal. Cross-function misclassification triggers wrong disposition authorities, wrong retention periods, and potential regulatory breach. Sibling confusion within the same function is operationally minor — same retention, same sensitivity, same disposition action. The training strategy must reflect the BCS hierarchy through:

1. **Record determination gate** — binary classifier filtering non-records before BCS classification begins
2. **Hierarchical label registry** encoding classification codes and materialised paths
3. **Hierarchical loss weighting** penalising cross-function errors more heavily
4. **Per-level accuracy tracking** with different thresholds per BCS level
5. **Hierarchical cascade classification** (function → activity → transaction)
6. **BERT-guided LLM injection** scoping the taxonomy to the relevant branch
7. **Taxonomy-aware synthetic generation** using ISO scope notes and typical records

The four highest-impact actions — each independently valuable, each taking less than a day — are:

1. **Enable auto-collection** (1 minute, Settings toggle)
2. **Backfill existing classifications** (30 minutes, one-time)
3. **Wire corrections to training data** (2 hours, code change)
4. **Pre-inject MCP context** (4-6 hours, eliminates tool-calling failures)

Everything else builds on this foundation. Start the flywheel. The data will come. The model will learn what is and isn't a record. It will learn the BCS hierarchy. The governance will be accurate. The costs will drop.
