## WORKFLOW NODES

The document's journey is five conceptual stages. Implementation detail — which container does what, REST vs Rabbit, the BERT/SLM/LLM cascade inside step 3 — lives in §3 and §4. This section is the *intent* that everything else serves, and the contract used to drive the functional and non-functional design of each container.

```
 1. INGEST
    Capture the file and everything that can be known about it without
    opening its body.
       Inputs:      raw bytes, source (upload | connector), source-supplied
                    context (Drive fileId, Gmail messageId, uploader, …)
       Captures:    filename, size, hash, mimeType, owner, source path,
                    created/modified timestamps, connector reference,
                    number of pages, number of tabs (depending on Mime).
       Operations:  Single & Bulk.
       Conditions:  
       State:       
       Stores:      raw bytes in object storage; DocumentModel in MongoDB
       Outputs:     Status: UPLOADING, UPLOADED, CHECKING, DUPLICATE, ORIGINAL, VALIDATING, VALID, INVALID
                    OnwardChainInstructions: 
       Services:    Duplicate_Checker - understands if the system has an exact copy.
                    Validity_Checker - understands if the mime_type is a potential record.
                                     - understands if the owner is covered by records governance.
              

 2. PARSE
    Turn the file into searchable text, regardless of format.
       Inputs:   raw bytes + mimeType + totalChars + pageCounts
       Outputs:  extracted text (inline or textRef for large bodies),
                 page / segment structure where it applies, detected
                 language, parse-time confidence, parse-time metadata
       Format-aware: native text  /  office-doc parsing  /  OCR for
                     image-and-scan PDFs  /  audio transcription  /
                     archive recursion (zip / mbox / pst)
       Status:   PARSED

 3. CLASSIFY
    Decide what this document IS, using rules derived from the system,
    the data we hold, and the people who corrected past decisions.
       Inputs:   parsed text + file metadata + hints
       Rules:    taxonomy (the categories the org cares about),
                 prior human corrections (correction history via MCP),
                 organisation-specific signals (PII patterns, metadata
                 schemas linked to candidate categories)
       Outputs:  category (taxonomy node), sensitivity, confidence,
                 rationale, supporting evidence, tier-of-decision
       Status:   CLASSIFIED

 4. EXTRACT  &  ENFORCE
    With a classification in hand, do the work that depends on knowing
    what the document is. Both halves are driven by the taxonomy node
    chosen in step 3, not hardcoded.
       Extract:  PII per the patterns for this category;
                 structured metadata via the MetadataSchema(s) linked
                 to this taxonomy node (employee_name, leave_type,
                 start_date, contract_value, …)
       Enforce:  retention rules, sensitivity flags, access controls,
                 legal-hold flags — all derived from the taxonomy
       Outputs:  piiHits[], extractedMetadata{}, applied governance
                 state on the document
       Status:   GOVERNANCE_APPLIED

 5. TIDY UP
    Make the document discoverable, durable, and complete.
       Index:     document body + extractedMetadata to the search index
       Finalise:  pipeline run closed, counters incremented, derived
                  views invalidated where relevant
       Close:     final Tier 1 audit event hash-chained for this
                  document's lifecycle entry
       Status:    INDEXED


 — at every step —
    audit_outbox is committed in the same Mongo transaction as state.
    outbox-relay publishes to gls.audit.{tier1.domain | tier2.system}.
    gls-audit-collector  drains, hash-chains Tier 1 → WORM, fans Tier 2 → OpenSearch.
    traceparent is propagated end-to-end; otel-collector receives spans.
```

---

## INGEST — detail

INGEST's single responsibility is **fact capture, no decisions**. It extracts everything that can be known about the file so downstream stages have what they need to decide. The CHECK gate (duplicate / validity) is a separate stage that consumes INGEST's facts — it is not part of INGEST.

This separation matters because it makes CHECK a pure function of the envelope, and it stops decision logic creeping back into the byte-handling layer.

### Workflow engine

No new framework. Each stage is a RabbitMQ consumer; the "engine" is the contract every consumer honours:

1. Consume the envelope from the previous stage's exchange.
2. Mutate the document (status transition + persisted fields) inside a Mongo transaction.
3. Append an audit event to `audit_outbox` in the **same** transaction.
4. Append this stage's layer to the envelope and publish to the next exchange.

The envelope grows immutably across stages — INGEST writes the `ingest` block, CHECK adds a `check` block, PARSE adds a `parsed` block, and so on. No stage rewrites a previous stage's layer.

### Envelope produced by INGEST

```jsonc
{
  "envelopeVersion": "1.0",
  "documentId": "65f...",
  "slug": "maternity-letter-a3f2b1",
  "traceparent": "00-...-...-01",

  "ingest": {
    "ingestedAt": "2026-04-27T09:14:22Z",
    "ingestNodeId": "ingest-7c4f",

    "actor": {                          // who/what triggered the ingest
      "kind":   "USER",                 // USER | SYSTEM
      "userId": "...",
      "email":  "..."                   // null when kind = SYSTEM
    },

    "file": {
      "filename": "maternity-letter.pdf",
      "size":     482311,
      "hash":     { "algo": "SHA-256", "value": "9b2c..." },
      "mimeType": "application/pdf",
      "mimeDetection": {
        "method":     "MAGIC_BYTES",    // MAGIC_BYTES | EXTENSION | CONNECTOR_DECLARED
        "confidence": 0.99
      },
      "createdAt":  "2026-04-20T...",
      "modifiedAt": "2026-04-25T..."
    },

    "shape": {                          // structural, not semantic
      "pages":      4,                  // PDFs
      "tabs":       null,               // XLSX
      "durationMs": null,               // audio
      "entryCount": null                // zip / mbox / pst
    },

    "owner": {                          // who owns the underlying file
      "userId":     "...",
      "email":      "...",
      "department": null
    },

    "storage": {
      "provider":  "MINIO",             // where the pipeline reads bytes from
      "bucket":    "documents",
      "objectKey": "by-hash/9b/2c/9b2c...",
      "retention": "TRANSIENT",         // TRANSIENT | PINNED | IN_PLACE
      "originalSource": {               // always preserved, even for direct uploads
        "provider":    "GOOGLE_DRIVE",  // DIRECT_UPLOAD | GOOGLE_DRIVE | GMAIL | SHAREPOINT | ...
        "ref":         { "fileId": "...", "driveId": "..." },
        "ownerEmail":  "...",
        "webViewLink": "...",
        "snapshotAt":  "2026-04-27T09:14:22Z"
      }
    },

    "audit": { "ingestEventId": "..." } // outbox row id for the INGESTED event
  }
}
```

Notes on the shape:

- **No decisions.** No `isDuplicate`, no `isValid`. Those belong to CHECK.
- **`actor` vs `owner`** — actor is who triggered the ingest, owner is who owns the file. For direct uploads they are usually the same; for a scheduled connector pull they differ.
- **`file.hash` is the duplicate key**; `owner` is the validity key. Both must be present before CHECK runs.
- **`shape` is structural metadata** (page count, tab count). Reading a PDF's page count technically opens the file, but it is a structural read, not a semantic one — fits INGEST's remit.
- **`storage.objectKey` is content-addressed by hash**. This gives free dedup at the bytes layer and makes "we already have this" trivially true at MinIO too.
- **`envelopeVersion`** at the top so a later INGEST can produce a 1.1 envelope without breaking older CHECKs in flight.

### Storage retention policy

Connectors do not have to process in-place. Three policies cover the realistic cases:

| Policy | Bytes during run | Bytes after run | When to use |
|---|---|---|---|
| `IN_PLACE` | Streamed from source on demand | Never held | Tenant forbids byte egress; source is reliably available |
| `TRANSIENT` | In MinIO | Deleted at TIDY UP | Default for most connectors — auditable run, low storage |
| `PINNED` | In MinIO | Kept per retention rule | High-value records; source unreliable |

Why `TRANSIENT` is often *more* correct than `IN_PLACE` for governance:

- **Audit defensibility.** Hash-chaining is stronger when the bytes existed in the system's custody at the moment of decision. Streaming-on-demand means the source could mutate between the classification read and any future re-read, and the audit chain has nothing to fall back on.
- **Reprocessability.** Corrections, prompt changes, and metadata schema changes drive re-runs. If the source revokes OAuth or deletes the file, an `IN_PLACE` document becomes un-reprocessable. A `TRANSIENT` document is also un-reprocessable, but at least the *decision* is durable on the chain.

### Retention is a hint, not an instruction

`retention: TRANSIENT` is a **hint for TIDY UP**, not an immediate-delete instruction at INGEST.

By the time TIDY UP runs, ENFORCE has applied governance — and governance may have set a legal hold, extended retention, or classified the document into a category whose retention rule overrides the connector default. So the rule is:

> `TRANSIENT` means: delete after the pipeline closes, **unless** ENFORCE has pinned it.

This keeps the workflow engine dumb (each stage adds a layer; TIDY UP reads the final state and acts) and keeps the policy decision in the right place — governance, not connector config.

### Where the policy is decided

- Per-connector default lives on the connector configuration (`connected_drives` or equivalent).
- Tenant-level override is permitted (some tenants forbid byte egress entirely → forces `IN_PLACE` for all connectors).
- INGEST stamps the resolved policy into `storage.retention` at envelope creation.
- Once stamped, the value is immutable for the run. CHECK and PARSE never decide retention. ENFORCE may *upgrade* `TRANSIENT → PINNED` via governance rules; it never downgrades.

### Open questions

INGEST-specific questions are tracked in the consolidated list at the end of this document — see Q9–Q16.

---

## Workflow open questions

The list of decisions that need answers before the workflow is fully specified. Numbered globally so we can refer to them as we work through. Items already deferred in conversation are marked *(parked)*.

When a question is resolved, it should be promoted to a row in `version-2-decision-tree.csv` (status `DECIDED`) and removed from this list.

### Cross-cutting & engine

- **Q1. Pipeline run identity.** Is `documentId` sufficient as the run key, or does a re-run (re-classification, retry) need a separate `pipelineRunId`?
- **Q2. Concurrent runs.** Can the same `documentId` have two runs in flight at once (e.g. re-classify triggered while a previous extract is still running)? If yes, how is the latest-wins / merge rule defined?
- **Q3. Retry policy.** Per-stage max attempts, backoff strategy, and dead-letter behaviour. Same policy everywhere or per-stage?
- **Q4. Block & config version pinning.** Are taxonomy version, prompt version, regex set version, metadata schema version pinned into the envelope at run start (reproducible re-runs) or read fresh at each stage?
- **Q5. Envelope version bump policy.** Who decides 1.0 → 1.1, what's the consumer-side compatibility rule, and how do in-flight envelopes from a previous version get drained?
- **Q6. Pipeline pause/resume.** Is admin-controlled halt of an in-flight run in scope? If yes, granularity (per-document, per-stage, global)?
- **Q7. Per-stage SLOs.** Target median and p95 latency for each of INGEST, CHECK, PARSE, CLASSIFY, EXTRACT & ENFORCE, TIDY UP.
- **Q8. Cost attribution.** Are per-stage costs (LLM tokens, OCR pages, storage bytes-seconds) recorded against the document for FinOps reporting?

### INGEST (Q9–Q16)

- **Q9. Synchronous vs async caller feedback.** *(parked)* Direct uploads could block on CHECK and return a 4xx with reasons; bulk and connector pulls cannot. Pick one model or support both?
- **Q10. Hash-while-streaming or hash-after-write.** *(parked)* One-pass streaming digest into MinIO, or two-phase write-then-hash?
- **Q11. INGEST as own deployable.** *(parked)* Own `gls-ingest` module for independent scaling, or a controller on `gls-app-assembly` for Phase 1?
- **Q12. Ingest contract count.** Single ingest contract serving direct upload + single connector pull + bulk connector pull, or distinct entry paths per cardinality?
- **Q13. Connector pulls without a clean owner.** *(parked)* Shared inboxes, group folders. Refuse, fall back to actor, or stamp a synthetic owner?
- **Q14. Idempotency on duplicate ingest submission.** Client retries POST on a network blip — what's the contract? Idempotency key on the request, or hash-based dedup at INGEST?
- **Q15. Hash collision handling.** SHA-256 collisions are vanishingly unlikely. Defensive code path (e.g. confirm size + filename match), or trust the math?
- **Q16. INGEST transactional boundary.** What's atomic: MinIO write, `DocumentModel` insert, `audit_outbox` row? Mongo txn covers Mongo writes; MinIO sits outside. What's the rollback/cleanup story on partial failure?

### CHECK (Q17–Q21)

- **Q17. Duplicate-second-uploader behaviour.** *(parked, deferred)* Hard terminate, co-owner, pointer-only, or something else.
- **Q18. Dup pool membership.** Are prior `INVALID` records included in duplicate lookups (so legitimate re-uploads of corrected files are blocked), or filtered out?
- **Q19. Status enum granularity.** Collapse `CHECKING / VALIDATING / ORIGINAL / VALID` into fewer transitions, or keep the existing four?
- **Q20. CHECK extras.** Does Validity_Checker also enforce file size limits, detect encrypted/password-protected PDFs (which would fail PARSE), and consult a known-bad-hash list (malware deny-list)?
- **Q21. Terminal record retention.** Do `DUPLICATE` and `INVALID` records keep their `DocumentModel` + audit row as evidence-of-rejection, or get purged?

### PARSE (Q22–Q29)

- **Q22. PARSE deployable.** Same container as ingest/check, or its own service? Tika, OCR, audio transcription are heavyweight and benefit from independent scaling.
- **Q23. OCR policy.** Always-on for image-only PDFs, opt-in per category, or cost-ceiling-gated?
- **Q24. Inline text vs `textRef`.** At what size do we externalise the parsed body to MinIO/object storage rather than embed it in the envelope?
- **Q25. Archive recursion semantics.** Does parsing a zip / mbox / pst produce N child documents that each enter CHECK independently, or one composite document with internal segment structure?
- **Q26. Parse failure handling.** Tika OOM / hang / unparseable format. Fallback paths (try OCR? mark `PARSE_FAILED`?), and what minimum fields PARSE must guarantee.
- **Q27. Language detection.** Required output of PARSE, or a downstream concern handled by CLASSIFY?
- **Q28. Audio transcription scope.** v2.0 deliverable or pushed to a v2.x?
- **Q29. Partial parses.** If some pages OCR cleanly but others don't, do we proceed with what we have or fail the stage?

### CLASSIFY (Q30–Q35)

- **Q30. Cascade thresholds.** BERT → SLM → LLM cascade — are confidence cutoffs configurable per taxonomy category, or globally?
- **Q31. Correction-history MCP usage.** Always called pre-classification (mandatory pre-fetch), or exposed as an LLM tool the model invokes when it judges relevant?
- **Q32. Multi-label classification.** Can one document belong to multiple taxonomy nodes, or is it strictly single-label?
- **Q33. Re-classification triggers.** Manual only, scheduled (e.g. when N corrections accumulate), or threshold-based on prompt/schema/taxonomy version changes?
- **Q34. Degraded-input policy.** If PARSE produced low-quality text (low confidence, partial OCR), still classify or fail?
- **Q35. Tier-of-decision auditability.** Recorded in the envelope so audit can see which model tier produced the classification, and so re-runs can compare?

### EXTRACT & ENFORCE (Q36–Q40)

- **Q36. Order.** Extract then enforce, enforce then extract, or interleaved (e.g. PII extraction informs sensitivity enforcement)?
- **Q37. Field-level extraction failure.** A required metadata field returns `NOT_FOUND` — does the whole stage fail, or do we record the missing field and continue?
- **Q38. Governance reversibility.** If a classification is overridden later, do enforcement side-effects (legal hold, retention period, access controls) reverse, or are they audit-only and the new classification produces a new enforcement layer?
- **Q39. Auto-pin without legal hold.** Confirmed: ENFORCE can upgrade `TRANSIENT → PINNED` for legal hold. Does it also auto-pin based on sensitivity alone (e.g. anything classified `RESTRICTED` is pinned)?
- **Q40. PII redaction in scope.** Is redaction (rewriting bytes) part of ENFORCE, or is ENFORCE strictly flagging + access-control with redaction as a separate concern?

### TIDY UP (Q41–Q45)

- **Q41. Order of operations.** Index then close audit, close audit then index, or parallel?
- **Q42. Search index scope.** Full text, extracted metadata only, or both? Is the body indexed in MongoDB too, or only in OpenSearch?
- **Q43. Failed indexing.** Does an indexing failure fail the pipeline (back to retry), or is search a derived view that can be rebuilt asynchronously without blocking the run?
- **Q44. `TRANSIENT` byte deletion timing.** Exactly when after `INDEXED` does deletion happen? Inline in TIDY UP or a separate job? How is the deletion itself audited?
- **Q45. Pipeline-run-closed state.** A separate `pipeline_runs` document, or a flag on `DocumentModel`?