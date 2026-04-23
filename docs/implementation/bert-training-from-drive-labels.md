# BERT Training from Google Drive Labels

**Date:** April 2026

---

## Overview

Organisations using Google Workspace often have existing Drive labels applied to documents — categories like "Finance", "HR", "Legal", or more specific values like "Invoice", "Contract", "Performance Review". These labels represent **human classification decisions already made** and are a high-value source of training data for BERT.

This guide covers how to leverage existing Drive labels to accelerate BERT training, how the data flows from Drive into the training pipeline, and how to configure the label-taxonomy bridge.

---

## 1. The Data Flow

```
Google Drive                    IG Central                      BERT Training
────────────                    ──────────                      ─────────────
                                                                
Files with                      Label-Taxonomy                  Training Data
Workspace Labels ──────────────► Bridge Service ─────────────► Collection
                  read labels    (maps values                   (source: DRIVE_LABEL)
                  via Drive API   to BCS codes)                       │
                                       │                              │
                                       ▼                              ▼
                              Pre-Classification              bert_training_data
                              (categoryId, code,              MongoDB collection
                               confidence 0.90)                       │
                                       │                              │
                                       ▼                              ▼
                              Text Extraction               Training Worker
                              (document content              (fine-tunes BERT)
                               → extractedText)                       │
                                       │                              │
                                       ▼                              ▼
                              Training Sample Created        New BERT Model
                              (text + category + code)       (handles these docs
                                                             without LLM next time)
```

---

## 2. Prerequisites

1. **Google Workspace account** — Drive Labels are a Workspace feature (not available on consumer @gmail.com accounts)
2. **Existing labels applied to files** — at least one label with a classification-relevant field (e.g., a "Category" selection field)
3. **Connected Drive in IG Central** — OAuth with `drive` + `drive.labels` scopes
4. **Imported BCS taxonomy** — at least one governance pack imported so categories exist to map to
5. **Documents registered in IG Central** — files must be tracked (registered for classification) to have extracted text for training

---

## 3. Setting Up the Label-Taxonomy Mapping

### Step 1: Navigate to Drive Labels

1. Go to **Drives** in the sidebar
2. Click the **Settings** (gear) icon on your connected drive
3. Click **Labels** to open the label configuration page

### Step 2: Select the Workspace Label

The page lists all published labels in your Google Workspace. Select the label that contains classification information (e.g., "Document Classification" or "Records Category").

### Step 3: Configure Field Mapping (Outbound)

Map GLS classification fields to label fields for write-back:
- **Category** → the label field that shows category names
- **Sensitivity** → a sensitivity/confidentiality field
- **Classification Code** → if you have a field for ISO codes
- **Jurisdiction** → if applicable

### Step 4: Configure Taxonomy Mapping (Inbound)

This is the key step. In the "Label → Taxonomy Mapping" section:

1. **Select the classification field** — which label field contains the category value
2. **Add value mappings** — for each value that field can contain, select the BCS category:

| Label Value | Maps To | BCS Code | Level |
|---|---|---|---|
| "Finance - Invoice" | Invoice Processing | FIN-AP-INV | TRANSACTION |
| "Finance - General" | Finance & Accounting | FIN | FUNCTION |
| "HR" | Human Resources | HR | FUNCTION |
| "Contract" | Contracts | LEG-CON | ACTIVITY |
| "Misc" | (unmapped) | — | — |

**You can map at any BCS level.** A broad label like "HR" maps to the FUNCTION level. A specific label like "Performance Review" maps to the TRANSACTION level. The system handles both.

### Step 5: Configure Sync Settings

- **Sync Direction:**
  - **Bidirectional** — read existing labels AND write classification back
  - **Read only** — import labels for training but never modify Drive labels
  - **Write only** — ignore existing labels, only write classification results back

- **Conflict Policy** (when label and AI disagree):
  - **Flag for review** — safest; puts the document in the review queue
  - **Label wins** — trusts the human who applied the label
  - **AI wins** — trusts the classification pipeline

- **Collect as training data** — enabled by default; every mapped document becomes a BERT training sample
- **Confidence** — the confidence score to assign (default 0.90; lower if you don't fully trust the labels)

### Step 6: Import Existing Labels

Click **"Import Existing Labels as Training Data"** to scan all tracked documents from this drive, read their labels, and:
1. Pre-classify any documents with matching label values
2. Collect them as BERT training samples (source: `DRIVE_LABEL`)

---

## 4. How Labels Feed BERT Training

### Training Sample Creation

When a document is imported from Drive with a mapped label:

```
TrainingDataSample {
    text: "[first 2000 chars of extracted text]",
    categoryId: "cat-fin-ap-inv",
    categoryName: "Invoice Processing",
    sensitivityLabel: "INTERNAL",
    source: "DRIVE_LABEL",                    ← distinguishes from other sources
    sourceDocumentId: "doc-abc-123",
    confidence: 0.90,                          ← configurable per mapping
    verified: false,                           ← not human-verified in IG Central
    fileName: "invoice-2026-0042.pdf",
    createdAt: "2026-04-20T12:00:00Z"
}
```

### Training Weight

In the BERT training pipeline, samples are weighted by source:

| Source | Weight | Rationale |
|---|---|---|
| CORRECTION | 3x | Explicit human override — highest signal |
| MANUAL_UPLOAD | 1x | Deliberately provided for training |
| DRIVE_LABEL | 1x | Human-applied label, not verified in IG Central |
| AUTO_COLLECTED | 1x | LLM-classified with high confidence |
| LLM_SYNTHETIC | 0.5x | Generated by LLM, may contain artifacts |

Drive label samples carry the same weight as manually uploaded samples — someone applied the label intentionally, which is a strong classification signal.

### Training Data Quality

Drive labels have some characteristics that affect training quality:

**Strengths:**
- Human-applied — someone familiar with the content made a deliberate choice
- High volume — organisations may have thousands of labeled files
- Pre-existing — zero additional effort to create
- Diverse — covers real document variety, not synthetic examples

**Weaknesses:**
- May be outdated — labels applied years ago might not reflect current taxonomy
- May be inconsistent — different people apply labels differently
- May be coarse — "Finance" label doesn't tell you if it's AP, AR, or FR
- Not verified in IG Central — no review queue approval

**Mitigation:**
- Set confidence to 0.85 (not 1.0) to acknowledge uncertainty
- Use the confidence threshold to determine which samples enter training (default: 0.80)
- Track override rate for label-sourced documents separately
- If override rate >20% for a specific label value mapping, the mapping may be wrong

---

## 5. Accessing Drive Content for BERT Training

### How Text Gets to BERT

BERT needs document text, not the Drive file itself. The flow:

1. **File registered** → document record created in MongoDB
2. **Content downloaded** → cached in MinIO (or streamed in stream mode)
3. **Text extracted** → Tika/OCR processes the file → `extractedText` field populated on DocumentModel
4. **Training sample created** → first 2000 characters of `extractedText` become the training sample `text` field

### Storage Mode Configuration

```
Settings → drives.storage_mode
```

- **`cache`** (default) — downloads file content from Drive to MinIO on registration. Text extraction happens from MinIO. Drive API is only called once.
- **`stream`** — content is fetched from Drive on-demand for text extraction. No local cache. Requires Drive access at training time if reprocessing needed.

**For training purposes, `cache` mode is recommended.** It ensures document content is available locally even if the Drive connection is interrupted.

### Token Length Considerations

BERT (DistilBERT-base) has a 512 token limit (~256 tokens used in current training config). This means:
- Only the first ~2000 characters of document text are used for training
- Long documents (10+ pages) have most of their content truncated
- Classification-relevant information at the end of documents is lost

**ModernBERT upgrade** (planned) extends this to 8192 tokens, allowing full document classification for most documents.

**Current workaround:** The LLM sees the full document (up to 100,000 chars). When BERT is uncertain due to truncation, the LLM handles it with full context.

---

## 6. Bulk Training Data Generation from Drive Labels

### The Power of Existing Labels

If an organisation has:
- 500 files labeled "Finance" → 500 training samples for the Finance function
- 200 files labeled "HR - Recruitment" → 200 training samples for HR-REC activity
- 50 files labeled "Board Minutes" → 50 training samples for COR-GOV-BRD transaction

**That's 750 training samples at zero LLM cost, available immediately.**

Compare to the cold-start bootstrap (Stage 0 of the Training Efficiency Playbook):
- Manual classification: 2-4 hours of human time for ~50 samples
- Drive label import: 30 seconds for potentially thousands of samples

### The Import Process

1. Admin configures label-taxonomy mapping (Section 3 above)
2. Admin clicks "Import Existing Labels as Training Data"
3. System scans all tracked documents from the drive
4. For each document with a matching label:
   - Reads the label value via Drive API
   - Looks up the mapping → finds BCS category
   - Creates a `TrainingDataSample` with source `DRIVE_LABEL`
   - If document has `extractedText` → sample includes text
   - If document lacks `extractedText` → marks for text extraction first
5. Reports: X scanned, Y mapped, Z collected as training data

### When to Retrain After Import

After a bulk import of label-sourced training data:

- If 20+ new samples were collected → retraining advisor will recommend retrain next morning (06:00)
- To train immediately: go to AI > Models > Start Training
- Monitor per-class metrics after training — label-sourced data may need quality filtering

---

## 7. Ongoing Sync: Labels and Classification in Harmony

### New Files with Labels (Inbound)

When a new file is registered from a labeled Drive:
1. System reads its labels during registration
2. If a mapping exists → pre-classifies with the mapped category
3. File still goes through text extraction
4. After extraction, training sample is created automatically
5. If BERT later disagrees during pipeline classification → conflict policy determines outcome

### Classification Written to Labels (Outbound)

When a document is classified (by BERT or LLM):
1. `GoogleDriveWriteBackService` triggers asynchronously
2. Writes classification metadata to the configured label fields
3. The label in Drive now shows: category, sensitivity, retention, etc.
4. Other Google Workspace users see the classification in Drive UI

### Corrections (Bidirectional)

When a records manager corrects a classification in IG Central:
1. Correction is stored as `ClassificationCorrection`
2. Training sample is updated/created with source `CORRECTION` (3x weight)
3. If outbound sync is enabled: the Drive label is updated to match the corrected classification
4. The corrected value feeds back to BERT on next retrain

---

## 8. Measuring Label-Sourced Training Effectiveness

### Key Metrics

Track in the ML Reports page:

- **Training samples by source** (Training Data tab) — how many samples came from DRIVE_LABEL vs other sources
- **Per-category accuracy** for label-sourced categories vs LLM-sourced categories
- **Override rate** for documents that were pre-classified from labels — are users frequently correcting them?
- **BERT hit rate** on categories that were bootstrapped from labels

### Quality Signals

| Signal | Good | Concerning |
|---|---|---|
| Override rate for label-sourced docs | <10% | >20% (mapping may be wrong) |
| BERT accuracy on label-bootstrapped categories | >75% after first retrain | <50% (labels may be inconsistent) |
| Training samples from labels vs corrections | Labels > Corrections | Corrections >> Labels (labels unreliable) |

If a specific label value mapping consistently produces overrides, the mapping may be incorrect — either the label value doesn't actually correspond to that BCS category, or the labels were inconsistently applied.

---

## 9. Providing Context to BERT: Beyond Text

### What BERT Currently Sees

Currently, BERT training uses only the document text (truncated to 2000 chars). But labels and metadata could provide additional context:

**Possible enrichments for BERT training text:**

1. **Filename prefix:** `"[invoice-2026-0042.pdf] " + text` — filenames often contain classification hints
2. **MIME type prefix:** `"[application/pdf] " + text` — file type correlates with category
3. **Label value prefix:** `"[Finance - Invoice] " + text` — the Drive label itself as a training signal
4. **Folder path prefix:** `"[Finance/Accounts Payable/2026/] " + text` — Drive folder structure contains semantic information

### Implementation

Modify `BertTrainingDataCollector` to optionally prepend metadata:

```java
String trainingText = buildTrainingText(doc, labelValue);

private String buildTrainingText(DocumentModel doc, String labelValue) {
    StringBuilder sb = new StringBuilder();
    
    // Optional metadata prefix (configurable)
    if (configService.getBoolValue("bert.training.include_filename_prefix", true)) {
        sb.append("[").append(doc.getOriginalFileName()).append("] ");
    }
    if (configService.getBoolValue("bert.training.include_label_prefix", false) && labelValue != null) {
        sb.append("[Label: ").append(labelValue).append("] ");
    }
    
    // Document text (truncated)
    String text = doc.getExtractedText();
    if (text.length() > 2000) text = text.substring(0, 2000);
    sb.append(text);
    
    return sb.toString();
}
```

**Caution:** Including the label value as a prefix during training means BERT learns to rely on it. At inference time, if the file doesn't have a label, BERT won't have that signal. Only use label prefix if you expect most files to arrive with labels. Filename prefix is safe — all files have filenames.

### Folder Structure as Context

Google Drive folder paths often mirror the BCS taxonomy:
- `/Finance/Accounts Payable/2026/` → likely FIN-AP
- `/HR/Recruitment/Applications/` → likely HR-REC-APP
- `/Legal/Contracts/Active/` → likely LEG-CON

This is additional training signal. The folder path could be prepended as context:

```java
// Get folder path from Drive API
String folderPath = googleDriveService.getFilePath(drive, fileId);
// Prepend to training text
String enrichedText = "[" + folderPath + "] " + documentText;
```

This teaches BERT that documents in certain folder structures tend to be certain categories — a legitimate pattern that humans also use for classification.

---

## 10. Access Control: Drive Content in Training

### Security Considerations

Training data contains document excerpts (2000 chars). Access to training data should be restricted:

- Training data is stored in MongoDB `bert_training_data` collection
- Only admin users (with `PIPELINE_READ` permission) can view training samples
- The Python training worker accesses samples via internal HTTP (not exposed externally)
- Training happens on-host (M3 Ultra) — document text never leaves the machine

### OAuth Token Lifecycle

The Drive connection's OAuth tokens are used to:
1. Read labels from files (at registration time)
2. Download file content (for text extraction)
3. Write labels back (after classification)

Tokens refresh automatically (60-second buffer before expiry). If a refresh token is revoked:
- Existing training data is unaffected (already in MongoDB)
- New file registrations will fail (drive shows "needs reconnect")
- Write-back will fail (labels won't update in Drive)

### Multi-Tenant Considerations

If training data is scoped per tenant (future multi-tenant feature):
- Label-taxonomy mappings are per-drive, which is effectively per-user/per-tenant
- Training samples carry `sourceDocumentId` linking back to the document (and thus the drive/tenant)
- BERT models would be trained per-tenant on their own training data only

---

## Summary

Google Drive labels are the fastest path to a trained BERT model:

1. **Configure the mapping** — 5 minutes in the admin UI
2. **Import existing labels** — 30 seconds, potentially thousands of training samples
3. **Train BERT** — 5-15 minutes on M3 Ultra
4. **Result:** BERT handles documents matching those label patterns without any LLM call

The label-taxonomy bridge turns years of existing human classification work in Google Drive into an instant training dataset. Combined with the correction feedback loop and auto-collection from LLM classifications, it creates a flywheel where every source of classification signal — human labels, LLM output, and human corrections — feeds into a continuously improving BERT model.
