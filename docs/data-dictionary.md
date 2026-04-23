so noho# Data Dictionary — Governance-Led Storage

> Generated 2026-04-22. Covers all 50 MongoDB document collections across 10 modules.

---

## Table of Contents

- [Document Management](#document-management)
- [Pipeline Execution](#pipeline-execution)
- [Governance & Taxonomy](#governance--taxonomy)
- [Pipeline Blocks & Feedback](#pipeline-blocks--feedback)
- [Classification & Corrections](#classification--corrections)
- [Metadata Schemas](#metadata-schemas)
- [BERT / ML Training](#bert--ml-training)
- [External Storage & Drives](#external-storage--drives)
- [PII & Subject Access Requests](#pii--subject-access-requests)
- [Audit & Monitoring](#audit--monitoring)
- [Identity & Access Control](#identity--access-control)
- [Platform Configuration](#platform-configuration)
- [Licensing & Subscriptions](#licensing--subscriptions)
- [Governance Hub (SaaS)](#governance-hub-saas)
- [Entity Relationship Summary](#entity-relationship-summary)

---

## Document Management

### `documents` — DocumentModel

Core document record representing a single uploaded or connected file through its entire lifecycle.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `fileName` | String | | Current file name |
| `originalFileName` | String | | Original uploaded file name |
| `mimeType` | String | | MIME type (e.g. application/pdf) |
| `fileSizeBytes` | long | | File size in bytes |
| `sha256Hash` | String | | Content hash for deduplication |
| `storageProvider` | String | Yes | LOCAL, GOOGLE_DRIVE, S3, SHAREPOINT, BOX, SMB |
| `storageBucket` | String | | MinIO bucket name |
| `storageKey` | String | | MinIO object key |
| `storageTierId` | String | | Reference to assigned storage tier |
| `connectedDriveId` | String | Yes | FK to `connected_drives` |
| `externalStorageRef` | Map<String, String> | | Provider-specific refs (fileId, driveId, webViewLink, etc.) |
| `status` | DocumentStatus | Yes | UPLOADED, PROCESSING, PROCESSED, CLASSIFYING, CLASSIFIED, GOVERNANCE_APPLIED, TRIAGE, FILED, PROCESSING_FAILED, CLASSIFICATION_FAILED, ENFORCEMENT_FAILED, CANCELLED |
| `extractedText` | String | | Full extracted text content |
| `thumbnailKey` | String | | MinIO key for thumbnail |
| `pageCount` | int | | Number of pages |
| `classificationResultId` | String | | FK to `classification_results` |
| `categoryId` | String | Yes | Denormalised FK to `classification_categories` |
| `categoryName` | String | Yes | Denormalised category display name |
| `sensitivityLabel` | SensitivityLabel | Yes | PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED |
| `tags` | List<String> | | Classification-assigned tags |
| `summary` | String | | LLM-generated document summary |
| `extractedMetadata` | Map<String, String> | | Schema-driven extracted metadata |
| `classificationCode` | String | Yes | ISO 15489 classification code (e.g. FIN-ACC-INV) |
| `classificationPath` | List<String> | | Materialised path (e.g. [Finance, Accounts, Invoices]) |
| `classificationLevel` | TaxonomyLevel | Yes | FUNCTION, ACTIVITY, TRANSACTION |
| `jurisdiction` | String | Yes | Legal jurisdiction |
| `legalCitation` | String | | Applicable legislation reference |
| `categoryPersonalData` | boolean | Yes | Whether category involves personal data |
| `vitalRecord` | boolean | Yes | Whether document is a vital record |
| `taxonomyVersion` | int | | Version of taxonomy at classification time |
| `retentionScheduleId` | String | | FK to `retention_schedules` |
| `retentionExpiresAt` | Instant | | When retention period expires |
| `retentionTrigger` | RetentionTrigger | | DATE_CREATED, DATE_LAST_MODIFIED, DATE_CLOSED, EVENT_BASED, END_OF_FINANCIAL_YEAR, SUPERSEDED |
| `retentionPeriodText` | String | | Human-readable retention period |
| `retentionTriggerEventDate` | Instant | | Date of the triggering event |
| `retentionStatus` | RetentionStatus | Yes | Current retention lifecycle status |
| `expectedDispositionAction` | DispositionAction | | DELETE, ARCHIVE, TRANSFER, REVIEW, ANONYMISE, PERMANENT |
| `legalHold` | boolean | | Whether document is under legal hold |
| `legalHoldReason` | String | | Reason for legal hold |
| `appliedPolicyIds` | List<String> | | FKs to `governance_policies` |
| `lastError` | String | | Last error message |
| `lastErrorStage` | String | | Pipeline stage that failed |
| `cancelledAt` | Instant | | When processing was cancelled |
| `failedAt` | Instant | | When failure occurred |
| `retryCount` | int | | Number of retry attempts |
| `traits` | List<String> | Yes | Document traits (INCOMING, OUTGOING, DRAFT, etc.) |
| `piiStatus` | String | Yes | NONE, DETECTED, REVIEWED, REDACTED, EXEMPT |
| `piiFindings` | List<PiiEntity> | | Embedded PII detection results |
| `piiScannedAt` | Instant | | When PII scan completed |
| `dublinCore` | Map<String, String> | | Dublin Core metadata fields |
| `slug` | String | Yes (unique, sparse) | URL-friendly identifier |
| `pipelineId` | String | | FK to `pipeline_definitions` |
| `pipelineSelectionMethod` | String | | How pipeline was selected (AUTO, MANUAL) |
| `pipelineNodeId` | String | | Current pipeline node being executed |
| `folderId` | String | Yes | FK to `folders` |
| `parentDocumentId` | String | Yes (sparse) | FK to parent document (email → attachments) |
| `childDocumentIds` | List<String> | | FKs to child documents |
| `filedToDriveId` | String | Yes | FK to `connected_drives` (filing destination) |
| `filedToFolderId` | String | | Folder ID within the drive |
| `filedAt` | Instant | | When document was filed |
| `filedBy` | String | | User who filed the document |
| `uploadedBy` | String | Yes | FK to `app_security_user_account` |
| `organisationId` | String | | Organisation scope |
| `createdAt` | Instant | Yes | Upload timestamp |
| `updatedAt` | Instant | Yes | Last modification timestamp |
| `processedAt` | Instant | | Text extraction completion time |
| `classifiedAt` | Instant | Yes | Classification completion time |
| `governanceAppliedAt` | Instant | | Governance enforcement completion time |

**Embedded: PiiEntity**

| Field | Type | Description |
|-------|------|-------------|
| `type` | String | PII type key (EMAIL, PHONE, SSN, etc.) |
| `matchedText` | String | Original matched text |
| `redactedText` | String | Redacted replacement text |
| `offset` | int | Character offset in extracted text |
| `confidence` | double | Detection confidence (0.0–1.0) |
| `method` | PiiDetectionMethod | REGEX, NER, LLM |
| `verified` | boolean | Whether human-verified |
| `verifiedBy` | String | User who verified |
| `dismissed` | boolean | Whether dismissed as false positive |
| `dismissedBy` | String | User who dismissed |
| `dismissalReason` | String | Reason for dismissal |

**Relations:**
- `connectedDriveId` → `connected_drives.id`
- `classificationResultId` → `classification_results.id`
- `categoryId` → `classification_categories.id`
- `retentionScheduleId` → `retention_schedules.id`
- `pipelineId` → `pipeline_definitions.id`
- `folderId` → `folders.id`
- `parentDocumentId` → `documents.id` (self-referential)
- `filedToDriveId` → `connected_drives.id`
- `uploadedBy` → `app_security_user_account.id`
- `appliedPolicyIds` → `governance_policies.id` (1:N)
- `storageTierId` → `storage_tiers.id`
- `id` ← `classification_results.documentId`
- `id` ← `audit_events.documentId`
- `id` ← `classification_corrections.documentId`
- `id` ← `pipeline_runs.documentId`

**Endpoints:**
- `POST /api/documents` — Upload document (multipart, triggers pipeline)
- `GET /api/documents/{id}` — Get document metadata (with access control audit)
- `GET /api/documents/{id}/download` — Download original file
- `GET /api/documents/{id}/classification` — Get classification history
- `GET /api/documents/{id}/preview` — Stream file content for preview
- `GET /api/documents/{id}/preview-html` — Render Office docs as HTML
- `GET /api/documents/{id}/audit` — Get document-specific audit trail (paginated)
- `GET /api/documents` — List documents with advanced filtering
- `GET /api/documents/by-category/{categoryId}` — Get documents in a category
- `GET /api/documents/unclassified` — Get unclassified documents for user
- `GET /api/documents/by-slug/{slug}` — Get document by URL slug
- `POST /api/documents/{id}/run-pipeline/{pipelineId}` — Manually run specific pipeline
- `POST /api/documents/{id}/reprocess` — Re-queue for full pipeline
- `POST /api/documents/{id}/cancel` — Cancel processing
- `POST /api/documents/{id}/reclassify` — Reclassify with optional pipeline override
- `POST /api/documents/{id}/rerun/extract` — Re-extract text only
- `POST /api/documents/{id}/rerun/pii` — Re-scan PII
- `POST /api/documents/{id}/rerun/classify` — Re-classify
- `POST /api/documents/{id}/rerun/enforce` — Re-apply retention/policies
- `POST /api/documents/{id}/pii/{piiIndex}/dismiss` — Dismiss PII finding
- `POST /api/documents/{id}/pii/{piiIndex}/verify` — Verify PII finding
- `GET /api/documents/{id}/pii/redacted-text` — Preview redacted text
- `POST /api/documents/{id}/pii/redact` — Permanently redact PII
- `POST /api/documents/{id}/metadata/remove-field` — Remove metadata field
- `GET /api/documents/{id}/metadata-schema` — Get metadata schema + values
- `PUT /api/documents/{id}/metadata` — Save extracted metadata
- `GET /api/documents/pii-types` — Get approved active PII types
- `POST /api/documents/pii-types/suggest` — Suggest custom PII type
- `GET /api/documents/stats` — Global document statistics
- (internal) `PipelineExecutionConsumer` processes documents through extraction, classification, and enforcement via RabbitMQ
- (internal) `StaleDocumentRecoveryTask` recovers documents stuck in PROCESSING state
- (internal) `SlugBackfillRunner` backfills slugs on startup
- (internal) `DocumentISOMigrationRunner` migrates ISO 15489 fields on startup
- (internal) `RetentionScheduledTask` processes expired retention daily at 2am

---

### `folders` — FolderModel

Logical folder for organising documents within the platform.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `name` | String | | Folder name |
| `description` | String | | Folder description |
| `parentId` | String | Yes | FK to parent folder (self-referential) |
| `createdBy` | String | Yes | FK to user who created |
| `organisationId` | String | | Organisation scope |
| `createdAt` | Instant | | Creation timestamp |
| `updatedAt` | Instant | | Last update timestamp |

**Relations:**
- `parentId` → `folders.id` (self-referential)
- `createdBy` → `app_security_user_account.id`
- `id` ← `documents.folderId`

**Endpoints:**
- (internal) Created via document filing operations

---

## Pipeline Execution

### `pipeline_runs` — PipelineRun

Tracks execution of a pipeline against a single document.

**Compound Indexes:**
- `idx_pipeline_run_doc_created` (documentId: 1, createdAt: -1)
- `idx_pipeline_run_org_status` (organisationId: 1, status: 1)

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `documentId` | String | Yes | FK to `documents` |
| `organisationId` | String | Yes | Organisation scope |
| `pipelineId` | String | | FK to `pipeline_definitions` |
| `pipelineVersion` | int | | Pipeline version at execution time |
| `status` | PipelineRunStatus | Yes | PENDING, RUNNING, COMPLETED, FAILED, CANCELLED |
| `currentNodeKey` | String | | Currently executing node |
| `correlationId` | String | Yes (unique) | Idempotency key |
| `executionPlan` | List<String> | | Ordered list of node keys to execute |
| `currentNodeIndex` | int | | Index into execution plan |
| `sharedContext` | Map<String, Object> | | Data shared between pipeline nodes |
| `startedAt` | Instant | | Execution start time |
| `completedAt` | Instant | | Execution completion time |
| `totalDurationMs` | long | | Total execution duration |
| `error` | String | | Error message if failed |
| `errorNodeKey` | String | | Node that caused failure |
| `retryCount` | int | | Number of retry attempts |
| `createdAt` | Instant | | Record creation time |
| `updatedAt` | Instant | | Last update time |

**Relations:**
- `documentId` → `documents.id`
- `pipelineId` → `pipeline_definitions.id`
- `id` ← `node_runs.pipelineRunId`

**Endpoints:**
- (internal) Created by `PipelineExecutionEngine` when a document enters the pipeline
- (internal) Updated by `PipelineExecutionConsumer` as nodes complete

---

### `node_runs` — NodeRun

Tracks execution of a single pipeline node within a pipeline run.

**Compound Indexes:**
- `idx_node_run_pipeline_node` (pipelineRunId: 1, nodeKey: 1)
- `idx_node_run_status` (status: 1, startedAt: -1)

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `pipelineRunId` | String | Yes | FK to `pipeline_runs` |
| `documentId` | String | | FK to `documents` |
| `nodeKey` | String | | Pipeline node identifier |
| `nodeType` | String | | Node type key (e.g. TIKA_EXTRACT, LLM_CLASSIFY) |
| `executionCategory` | String | | NOOP, BUILT_IN, ACCELERATOR, GENERIC_HTTP, ASYNC_BOUNDARY |
| `status` | NodeRunStatus | Yes | PENDING, RUNNING, COMPLETED, FAILED, SKIPPED |
| `jobId` | String | Yes (unique, sparse) | External job ID for async nodes |
| `idempotencyKey` | String | | Deduplication key |
| `input` | Map<String, Object> | | Input data for node |
| `output` | Map<String, Object> | | Output data from node |
| `startedAt` | Instant | | Node start time |
| `completedAt` | Instant | | Node completion time |
| `durationMs` | long | | Node execution duration |
| `error` | String | | Error message if failed |
| `retryCount` | int | | Number of retries |

**Relations:**
- `pipelineRunId` → `pipeline_runs.id`
- `documentId` → `documents.id`

**Endpoints:**
- (internal) Created and updated by `PipelineExecutionEngine` during pipeline execution

---

## Governance & Taxonomy

### `classification_categories` — ClassificationCategory

Taxonomy node representing a records management category (ISO 15489 aligned).

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `classificationCode` | String | Yes (unique, sparse) | ISO 15489 code (e.g. FIN-ACC-INV) |
| `name` | String | | Category display name |
| `description` | String | | Detailed description |
| `scopeNotes` | String | | Scope notes for classification guidance |
| `level` | TaxonomyLevel | | FUNCTION, ACTIVITY, TRANSACTION |
| `parentId` | String | | FK to parent category (self-referential) |
| `path` | List<String> | | Materialised path of ancestor names |
| `sortOrder` | int | | Display order among siblings |
| `keywords` | List<String> | | Keywords to aid classification |
| `defaultSensitivity` | SensitivityLabel | | Default sensitivity for this category |
| `retentionScheduleId` | String | | FK to `retention_schedules` |
| `metadataSchemaId` | String | | FK to `metadata_schemas` |
| `jurisdiction` | String | | Legal jurisdiction |
| `typicalRecords` | List<String> | | Example record types |
| `retentionPeriodText` | String | | Human-readable retention period |
| `legalCitation` | String | | Primary legislation reference |
| `retentionTrigger` | RetentionTrigger | | Trigger for retention countdown |
| `retentionTriggerDescription` | String | | Description of trigger event |
| `owner` | String | | Business owner of category |
| `custodian` | String | | Records custodian |
| `reviewCycleDuration` | String | | How often category is reviewed |
| `lastReviewedAt` | Instant | | Last review date |
| `nextReviewAt` | Instant | | Next scheduled review |
| `personalDataFlag` | boolean | | Whether category involves personal data |
| `vitalRecordFlag` | boolean | | Whether category contains vital records |
| `status` | NodeStatus | | ACTIVE, INACTIVE, DEPRECATED |
| `version` | int | | Version counter (incremented on update) |
| `sourcePackSlug` | String | | Governance pack that imported this |
| `sourcePackVersion` | Integer | | Pack version at import time |
| `importedAt` | Instant | | When imported from pack |

**Relations:**
- `parentId` → `classification_categories.id` (self-referential tree)
- `retentionScheduleId` → `retention_schedules.id`
- `metadataSchemaId` → `metadata_schemas.id`
- `id` ← `documents.categoryId`
- `id` ← `classification_results.categoryId`
- `id` ← `pipeline_definitions.applicableCategoryIds`

**Endpoints:**
- `GET /api/admin/governance/taxonomy` — List all categories
- `GET /api/admin/governance/taxonomy/tree` — Get taxonomy as text tree
- `POST /api/admin/governance/taxonomy` — Create category (auto-generates classification code)
- `PUT /api/admin/governance/taxonomy/{id}` — Update category (increments version)
- `PUT /api/admin/governance/taxonomy/{id}/review` — Mark category reviewed
- `DELETE /api/admin/governance/taxonomy/{id}` — Deactivate category
- `GET /api/admin/governance/taxonomy/export` — Export taxonomy as CSV
- `GET /api/search/taxonomy-tree` — Taxonomy tree with document counts
- (internal) `GovernanceDataSeeder` rebuilds taxonomy paths on startup
- (internal) `TaxonomyMigrationRunner` migrates taxonomy data on startup

---

### `governance_policies` — GovernancePolicy

Governance rules applied to documents based on category and sensitivity.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `name` | String | | Policy name |
| `description` | String | | Policy description |
| `version` | int | | Version counter |
| `active` | boolean | | Whether policy is in effect |
| `effectiveFrom` | Instant | | Policy start date |
| `effectiveUntil` | Instant | | Policy end date |
| `rules` | List<String> | | Policy rule expressions |
| `applicableCategoryIds` | List<String> | | FKs to `classification_categories` |
| `applicableSensitivities` | List<SensitivityLabel> | | Which sensitivities this applies to |
| `enforcementActions` | Map<String, String> | | Actions to take (e.g. encrypt, restrict) |
| `legislationIds` | List<String> | | FKs to `legislation` |
| `createdAt` | Instant | | Creation timestamp |
| `createdBy` | String | | Creator user ID |
| `updatedAt` | Instant | | Last update timestamp |
| `sourcePackSlug` | String | | Governance pack source |
| `sourcePackVersion` | Integer | | Pack version |
| `importedAt` | Instant | | Import timestamp |

**Relations:**
- `applicableCategoryIds` → `classification_categories.id` (M:N)
- `legislationIds` → `legislation.id` (M:N)
- `id` ← `documents.appliedPolicyIds`

**Endpoints:**
- `GET /api/admin/governance/policies` — List active policies
- `POST /api/admin/governance/policies` — Create policy
- `PUT /api/admin/governance/policies/{id}` — Update policy (increments version)
- `DELETE /api/admin/governance/policies/{id}` — Deactivate policy
- (internal) Applied by governance enforcement pipeline stage

---

### `retention_schedules` — RetentionSchedule

Defines how long records must be kept and what happens when retention expires.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `name` | String | | Schedule name |
| `description` | String | | Schedule description |
| `retentionDays` | int | | Retention period in days |
| `retentionDuration` | String | | ISO 8601 duration (e.g. P7Y) |
| `retentionTrigger` | String | | What starts the retention countdown |
| `dispositionAction` | DispositionAction | | DELETE, ARCHIVE, TRANSFER, REVIEW, ANONYMISE, PERMANENT |
| `legalHoldOverride` | boolean | | Whether legal hold overrides disposition |
| `jurisdiction` | String | | Legal jurisdiction |
| `regulatoryBasis` | String | | Regulatory authority |
| `scheduleReference` | String | | External reference number |
| `approvedBy` | String | | Approver name |
| `approvedDate` | Instant | | Approval date |
| `reviewDate` | Instant | | Next review date |
| `legislationIds` | List<String> | | FKs to `legislation` |
| `legislationNotes` | String | | Notes on legislation applicability |
| `sourcePackSlug` | String | | Governance pack source |
| `sourcePackVersion` | Integer | | Pack version |
| `importedAt` | Instant | | Import timestamp |

**Relations:**
- `legislationIds` → `legislation.id` (M:N)
- `id` ← `documents.retentionScheduleId`
- `id` ← `classification_categories.retentionScheduleId`

**Endpoints:**
- `GET /api/admin/governance/retention` — List retention schedules
- `POST /api/admin/governance/retention` — Create retention schedule
- `PUT /api/admin/governance/retention/{id}` — Update retention schedule
- (internal) Applied during governance enforcement stage
- (internal) `RetentionScheduledTask` processes expired retention daily

---

### `legislation` — Legislation

Legal and regulatory references that support governance policies and retention schedules.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `key` | String | Yes (unique) | Unique legislation key |
| `name` | String | | Full legislation name |
| `shortName` | String | | Abbreviated name |
| `jurisdiction` | String | | Jurisdiction (e.g. UK, EU) |
| `url` | String | | Link to legislation text |
| `description` | String | | Description of relevance |
| `active` | boolean | | Whether currently in force |
| `relevantArticles` | List<String> | | Specific articles/sections |
| `sourcePackSlug` | String | | Governance pack source |
| `sourcePackVersion` | Integer | | Pack version |
| `importedAt` | Instant | | Import timestamp |

**Relations:**
- `id` ← `governance_policies.legislationIds`
- `id` ← `retention_schedules.legislationIds`
- `id` ← `sensitivity_definitions.legislationIds`

**Endpoints:**
- (internal) Imported via governance packs

---

### `sensitivity_definitions` — SensitivityDefinition

Configurable sensitivity levels with display properties and access guidelines.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `key` | String | Yes (unique) | Unique key (e.g. CONFIDENTIAL) |
| `displayName` | String | | Display label |
| `description` | String | | Description of sensitivity level |
| `level` | int | | Numeric level for ordering/comparison |
| `colour` | String | | UI colour code |
| `active` | boolean | | Whether currently active |
| `guidelines` | List<String> | | Handling guidelines |
| `examples` | List<String> | | Example documents at this level |
| `legislationIds` | List<String> | | FKs to `legislation` |
| `sourcePackSlug` | String | | Governance pack source |
| `sourcePackVersion` | Integer | | Pack version |
| `importedAt` | Instant | | Import timestamp |

**Relations:**
- `legislationIds` → `legislation.id` (M:N)

**Endpoints:**
- `GET /api/admin/governance/sensitivities` — List active sensitivities
- `GET /api/admin/governance/sensitivities/all` — List all sensitivities
- `POST /api/admin/governance/sensitivities` — Create sensitivity definition
- `PUT /api/admin/governance/sensitivities/{id}` — Update sensitivity
- `DELETE /api/admin/governance/sensitivities/{id}` — Deactivate sensitivity
- (internal) Seeded by `GovernanceDataSeeder` on first startup

---

### `storage_tiers` — StorageTier

Storage infrastructure tiers with security and compliance properties.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `name` | String | | Tier name (HOT, WARM, COLD) |
| `description` | String | | Tier description |
| `encryptionType` | String | | Encryption standard |
| `immutable` | boolean | | Whether storage is write-once |
| `geographicallyRestricted` | boolean | | Data residency restricted |
| `region` | String | | Storage region |
| `allowedSensitivities` | List<SensitivityLabel> | | Which sensitivities allowed |
| `maxFileSizeBytes` | long | | Max file size |
| `costPerGbMonth` | double | | Monthly cost per GB |
| `sourcePackSlug` | String | | Governance pack source |
| `sourcePackVersion` | Integer | | Pack version |
| `importedAt` | Instant | | Import timestamp |

**Relations:**
- `id` ← `documents.storageTierId`

**Endpoints:**
- `GET /api/admin/governance/storage-tiers` — List storage tiers
- `POST /api/admin/governance/storage-tiers` — Create storage tier
- `PUT /api/admin/governance/storage-tiers/{id}` — Update storage tier
- (internal) Seeded by `GovernanceDataSeeder` on first startup

---

### `trait_definitions` — TraitDefinition

Document trait definitions for characterising document attributes (completeness, direction, provenance).

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `key` | String | Yes (unique) | Unique trait key (e.g. INCOMING, DRAFT) |
| `displayName` | String | | Display label |
| `description` | String | | Trait description |
| `dimension` | String | | COMPLETENESS, DIRECTION, PROVENANCE |
| `detectionHint` | String | | Hint for LLM trait detection |
| `indicators` | List<String> | | Textual indicators |
| `suppressPii` | boolean | | Whether to suppress PII for this trait |
| `active` | boolean | | Whether active |
| `sourcePackSlug` | String | | Governance pack source |
| `sourcePackVersion` | Integer | | Pack version |
| `importedAt` | Instant | | Import timestamp |

**Relations:**
- `key` ← `documents.traits` (documents store trait keys)

**Endpoints:**
- (internal) Seeded by `GovernanceDataSeeder` on first startup

---

### `pii_type_definitions` — PiiTypeDefinition

Approved PII types with approval workflow for user-suggested types.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `key` | String | Yes (unique) | Unique PII type key (e.g. EMAIL, PHONE) |
| `displayName` | String | | Display label |
| `description` | String | | Description |
| `category` | String | | PII category grouping |
| `active` | boolean | | Whether active |
| `examples` | List<String> | | Example values |
| `approvalStatus` | ApprovalStatus | | APPROVED, PENDING, REJECTED |
| `submittedBy` | String | | User who suggested |
| `submittedAt` | Instant | | Suggestion timestamp |
| `reviewedBy` | String | | Admin who reviewed |
| `reviewedAt` | Instant | | Review timestamp |
| `rejectionReason` | String | | Reason if rejected |
| `sourcePackSlug` | String | | Governance pack source |
| `sourcePackVersion` | Integer | | Pack version |
| `importedAt` | Instant | | Import timestamp |

**Relations:**
- `key` ← `documents.piiFindings[].type`

**Endpoints:**
- `GET /api/admin/governance/pii-types` — List approved PII types
- `GET /api/admin/governance/pii-types/all` — List all (including pending/rejected)
- `GET /api/admin/governance/pii-types/pending` — List pending suggestions
- `POST /api/admin/governance/pii-types` — Create PII type (auto-approved)
- `PUT /api/admin/governance/pii-types/{id}` — Update PII type
- `DELETE /api/admin/governance/pii-types/{id}` — Deactivate PII type
- `PUT /api/admin/governance/pii-types/{id}/approve` — Approve suggestion
- `PUT /api/admin/governance/pii-types/{id}/reject` — Reject suggestion
- `GET /api/documents/pii-types` — Get approved active types (public)
- `POST /api/documents/pii-types/suggest` — Submit suggestion
- (internal) Seeded by `GovernanceDataSeeder` on first startup

---

### `node_type_definitions` — NodeTypeDefinition

Pipeline node type definitions (visual editor palette).

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `key` | String | Yes (unique) | Unique node type key |
| `displayName` | String | | Display label |
| `description` | String | | Node type description |
| `category` | String | | TRIGGER, PROCESSING, ACCELERATOR, LOGIC, ACTION, ERROR_HANDLING |
| `sortOrder` | int | | Display order |
| `executionCategory` | String | | NOOP, BUILT_IN, ACCELERATOR, GENERIC_HTTP, ASYNC_BOUNDARY |
| `handlerBeanName` | String | | Spring bean name for executor |
| `requiresDocReload` | boolean | | Whether to reload document after execution |
| `pipelinePhase` | String | | PRE_CLASSIFICATION, POST_CLASSIFICATION, BOTH |
| `iconName` | String | | UI icon name |
| `colorTheme` | String | | UI colour theme |
| `handles` | List<HandleDefinition> | | Input/output port definitions |
| `branchLabels` | List<BranchLabel> | | Labels for branching handles |
| `configSchema` | Map<String, Object> | | JSON Schema for node config |
| `configDefaults` | Map<String, Object> | | Default config values |
| `compatibleBlockType` | String | | Which block type this node uses |
| `summaryTemplate` | String | | Template for node summary text |
| `validationExpression` | String | | Validation rule |
| `performanceImpact` | String | | LOW, MEDIUM, HIGH |
| `performanceWarning` | String | | Warning text for high-impact nodes |
| `httpConfig` | GenericHttpConfig | | Configuration for HTTP nodes |
| `active` | boolean | | Whether available |
| `builtIn` | boolean | | Whether system-provided |
| `comingSoon` | boolean | | Whether coming soon (disabled) |
| `createdAt` | Instant | | Creation timestamp |
| `updatedAt` | Instant | | Last update timestamp |

**Embedded: HandleDefinition**

| Field | Type | Description |
|-------|------|-------------|
| `type` | String | Handle type |
| `position` | String | Position on node |
| `id` | String | Handle ID |
| `color` | String | Handle colour |
| `style` | Map<String, Object> | CSS styles |

**Embedded: BranchLabel**

| Field | Type | Description |
|-------|------|-------------|
| `handleId` | String | FK to handle |
| `label` | String | Branch label text |
| `color` | String | Label colour |

**Embedded: GenericHttpConfig**

| Field | Type | Description |
|-------|------|-------------|
| `defaultUrl` | String | Default HTTP URL |
| `path` | String | URL path |
| `method` | String | HTTP method |
| `defaultTimeoutMs` | int | Default timeout |
| `defaultHeaders` | Map<String, String> | Default headers |
| `requestBodyTemplate` | String | Request body template |
| `responseMapping` | Map<String, String> | Response field mapping |

**Relations:**
- `compatibleBlockType` ↔ `pipeline_blocks.type`

**Endpoints:**
- (internal) Seeded by `GovernanceDataSeeder` on first startup

---

## Pipeline Blocks & Feedback

### `pipeline_definitions` — PipelineDefinition

Configurable document processing pipeline with visual node graph.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `name` | String | | Pipeline name |
| `description` | String | | Pipeline description |
| `active` | boolean | | Whether pipeline is active |
| `isDefault` | boolean | | Whether this is the default pipeline |
| `applicableCategoryIds` | List<String> | | FKs to `classification_categories` for routing |
| `includeSubCategories` | boolean | | Whether to include child categories |
| `applicableMimeTypes` | List<String> | | MIME type filters |
| `steps` | List<PipelineStep> | | Legacy linear step definitions |
| `visualNodes` | List<VisualNode> | | Visual node graph nodes |
| `visualEdges` | List<VisualEdge> | | Visual node graph edges |
| `createdAt` | Instant | | Creation timestamp |
| `updatedAt` | Instant | | Last update timestamp |

**Embedded: PipelineStep**

| Field | Type | Description |
|-------|------|-------------|
| `order` | int | Step execution order |
| `name` | String | Step name |
| `description` | String | Step description |
| `type` | StepType | BUILT_IN, PATTERN, LLM_PROMPT, CONDITIONAL, ACCELERATOR, SYNC_LLM |
| `enabled` | boolean | Whether step is active |
| `blockId` | String | FK to `pipeline_blocks` |
| `blockVersion` | int | Block version to use |
| `systemPrompt` | String | System prompt override |
| `userPromptTemplate` | String | User prompt template |
| `condition` | String | Conditional expression |
| `config` | Map<String, Object> | Step-specific config |

**Embedded: VisualNode**

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Node ID |
| `type` | String | Node type key |
| `label` | String | Display label |
| `x` | double | X position in editor |
| `y` | double | Y position in editor |
| `blockId` | String | FK to `pipeline_blocks` |
| `status` | String | Node status |
| `data` | Map<String, Object> | Node configuration data |

**Embedded: VisualEdge**

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Edge ID |
| `source` | String | Source node ID |
| `target` | String | Target node ID |
| `sourceHandle` | String | Source handle ID |
| `targetHandle` | String | Target handle ID |
| `label` | String | Edge label |

**Relations:**
- `applicableCategoryIds` → `classification_categories.id` (M:N)
- `steps[].blockId` → `pipeline_blocks.id`
- `visualNodes[].blockId` → `pipeline_blocks.id`
- `id` ← `documents.pipelineId`
- `id` ← `pipeline_runs.pipelineId`

**Endpoints:**
- `GET /api/pipelines/available` — List available pipelines for manual selection
- `GET /api/admin/pipelines` — List all pipelines (admin)
- `GET /api/admin/pipelines/{id}` — Get pipeline definition
- `POST /api/admin/pipelines` — Create new pipeline
- `PUT /api/admin/pipelines/{id}` — Update pipeline definition
- `DELETE /api/admin/pipelines/{id}` — Delete pipeline
- `POST /api/admin/pipelines/pii-scan/batch` — Batch PII scan
- `POST /api/admin/pipelines/pii-scan/{documentId}` — Single document PII scan
- `GET /api/admin/pipelines/overlap-check` — Check category overlap
- `GET /api/admin/pipelines/resolve` — Resolve overlapping classifications
- `POST /api/admin/pipelines/test-node` — Test individual pipeline node

---

### `pipeline_blocks` — PipelineBlock

Versioned, reusable processing units with feedback tracking and AI-assisted improvement.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `name` | String | Yes (unique) | Block name |
| `description` | String | | Block description |
| `type` | BlockType | | PROMPT, REGEX_SET, EXTRACTOR, ROUTER, ENFORCER, BERT_CLASSIFIER |
| `active` | boolean | | Whether block is active |
| `activeVersion` | int | | Currently active version number |
| `versions` | List<BlockVersion> | | Immutable version history |
| `draftContent` | Map<String, Object> | | Draft content (not yet published) |
| `draftChangelog` | String | | Draft changelog notes |
| `documentsProcessed` | long | | Total documents processed by this block |
| `correctionsReceived` | long | | Total corrections received |
| `feedbackCount` | long | | Total feedback entries |
| `createdAt` | Instant | | Creation timestamp |
| `createdBy` | String | | Creator |
| `updatedAt` | Instant | | Last update timestamp |
| `sourcePackSlug` | String | | Governance pack source |
| `sourcePackVersion` | Integer | | Pack version |
| `importedAt` | Instant | | Import timestamp |

**Embedded: BlockVersion**

| Field | Type | Description |
|-------|------|-------------|
| `version` | int | Version number |
| `content` | Map<String, Object> | Full block content snapshot |
| `changelog` | String | What changed |
| `publishedBy` | String | Who published |
| `publishedAt` | Instant | Publication timestamp |

**Relations:**
- `id` ← `pipeline_definitions.steps[].blockId`
- `id` ← `pipeline_definitions.visualNodes[].blockId`
- `id` ← `block_feedback.blockId`
- `id` ← `ai_usage_log.promptBlockId`

**Endpoints:**
- `GET /api/admin/blocks` — List active blocks
- `GET /api/admin/blocks/all` — List all blocks
- `GET /api/admin/blocks/{id}` — Get block with active version
- `POST /api/admin/blocks` — Create new block
- `PUT /api/admin/blocks/{id}` — Update block content
- `DELETE /api/admin/blocks/{id}` — Delete block
- `PUT /api/admin/blocks/{id}/draft` — Manage draft version
- `POST /api/admin/blocks/{id}/publish` — Publish version
- `POST /api/admin/blocks/{id}/rollback/{version}` — Rollback to version
- `POST /api/admin/blocks/{id}/improve` — LLM-powered improvement
- `GET /api/admin/blocks/{id}/compare/{v1}/{v2}` — Compare versions
- `GET /api/admin/blocks/{id}/feedback` — Get feedback
- `POST /api/admin/blocks/{id}/feedback` — Submit feedback

---

### `block_feedback` — BlockFeedback

User feedback on pipeline block performance, driving improvement.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `blockId` | String | Yes | FK to `pipeline_blocks` |
| `blockVersion` | int | | Version feedback applies to |
| `documentId` | String | | FK to `documents` |
| `userId` | String | | User who provided feedback |
| `userEmail` | String | | User email |
| `type` | FeedbackType | | CORRECTION, FALSE_POSITIVE, MISSED, SUGGESTION |
| `details` | String | | Feedback details |
| `suggestion` | String | | Suggested improvement |
| `originalValue` | String | | Original block output |
| `correctedValue` | String | | Corrected value |
| `timestamp` | Instant | | Feedback timestamp |

**Relations:**
- `blockId` → `pipeline_blocks.id`
- `documentId` → `documents.id`

**Endpoints:**
- `GET /api/admin/blocks/{id}/feedback` — Get block feedback
- `POST /api/admin/blocks/{id}/feedback` — Submit feedback
- (internal) Created automatically when users dismiss PII or override classifications

---

## Classification & Corrections

### `classification_results` — DocumentClassificationResult

LLM classification output for a document.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `documentId` | String | | FK to `documents` |
| `categoryId` | String | | FK to `classification_categories` |
| `categoryName` | String | | Denormalised category name |
| `classificationCode` | String | | ISO 15489 classification code |
| `classificationPath` | List<String> | | Materialised path |
| `classificationLevel` | TaxonomyLevel | | FUNCTION, ACTIVITY, TRANSACTION |
| `jurisdiction` | String | | Legal jurisdiction |
| `legalCitation` | String | | Legislation reference |
| `categoryPersonalData` | boolean | | Personal data flag |
| `vitalRecord` | boolean | | Vital record flag |
| `taxonomyVersion` | int | | Taxonomy version at classification |
| `retentionTrigger` | RetentionTrigger | | Retention trigger type |
| `retentionPeriodText` | String | | Retention period text |
| `expectedDispositionAction` | DispositionAction | | Expected disposition |
| `sensitivityLabel` | SensitivityLabel | | Assigned sensitivity |
| `tags` | List<String> | | Assigned tags |
| `extractedMetadata` | Map<String, String> | | Schema-driven metadata |
| `applicablePolicyIds` | List<String> | | Matched policy IDs |
| `retentionScheduleId` | String | | Matched retention schedule |
| `confidence` | double | | Classification confidence (0.0–1.0) |
| `reasoning` | String | | LLM reasoning text |
| `summary` | String | | Document summary |
| `modelId` | String | | LLM model used |
| `classifiedAt` | Instant | | Classification timestamp |
| `humanReviewed` | boolean | | Whether human-reviewed |
| `reviewedBy` | String | | Reviewer user ID |

**Relations:**
- `documentId` → `documents.id`
- `categoryId` → `classification_categories.id`
- `retentionScheduleId` → `retention_schedules.id`
- `applicablePolicyIds` → `governance_policies.id` (M:N)

**Endpoints:**
- `GET /api/documents/{id}/classification` — Get classification history
- `GET /api/review/{documentId}/classification` — Get full classification history (review)
- (internal) Created by `ClassificationPipeline` RabbitMQ consumer

---

### `classification_corrections` — ClassificationCorrection

Human corrections to LLM classifications, feeding back into future classifications via MCP.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `documentId` | String | Yes | FK to `documents` |
| `originalCategoryId` | String | | Original LLM category |
| `originalCategoryName` | String | | Original category name |
| `originalSensitivity` | SensitivityLabel | | Original sensitivity |
| `originalConfidence` | double | | Original confidence score |
| `correctedCategoryId` | String | | Human-corrected category |
| `correctedCategoryName` | String | | Corrected category name |
| `correctedSensitivity` | SensitivityLabel | | Corrected sensitivity |
| `correctionType` | CorrectionType | | CATEGORY_CHANGED, SENSITIVITY_CHANGED, BOTH_CHANGED, PII_FLAGGED, PII_DISMISSED, APPROVED_CORRECT |
| `reason` | String | | Reason for correction |
| `mimeType` | String | | Document MIME type |
| `keywords` | List<String> | | Document keywords |
| `piiCorrections` | List<PiiCorrection> | | PII-specific corrections |
| `correctedBy` | String | Yes | User who corrected |
| `correctedAt` | Instant | | Correction timestamp |

**Embedded: PiiCorrection**

| Field | Type | Description |
|-------|------|-------------|
| `type` | String | PII type |
| `description` | String | Description of PII issue |
| `context` | String | Surrounding text context |

**Relations:**
- `documentId` → `documents.id`
- `originalCategoryId` → `classification_categories.id`
- `correctedCategoryId` → `classification_categories.id`
- `correctedBy` → `app_security_user_account.id`

**Endpoints:**
- `POST /api/review/{documentId}/approve` — Create APPROVED_CORRECT correction
- `POST /api/review/{documentId}/override` — Create category/sensitivity correction
- `POST /api/review/{documentId}/report-pii` — Create PII_FLAGGED correction
- `GET /api/review/corrections` — Get recent corrections
- (internal) Used by MCP tools `get_correction_history` and `get_org_pii_patterns`

---

### `template_fingerprints` — TemplateFingerprint

Learned document fingerprints for accelerated classification (template matching).

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `fingerprint` | String | Yes | Document structure fingerprint hash |
| `categoryId` | String | | FK to `classification_categories` |
| `categoryName` | String | | Denormalised category name |
| `sensitivityLabel` | SensitivityLabel | | Assigned sensitivity |
| `tags` | List<String> | | Tags |
| `retentionScheduleId` | String | | FK to `retention_schedules` |
| `confidence` | double | | Match confidence |
| `matchCount` | long | | Number of times matched |
| `learnedFromDocumentId` | String | | FK to `documents` (source) |
| `mimeType` | String | | MIME type |
| `createdAt` | Instant | | Creation timestamp |
| `lastMatchedAt` | Instant | | Last match timestamp |

**Relations:**
- `categoryId` → `classification_categories.id`
- `retentionScheduleId` → `retention_schedules.id`
- `learnedFromDocumentId` → `documents.id`

**Endpoints:**
- (internal) Created and queried by pipeline accelerator nodes

---

## Metadata Schemas

### `metadata_schemas` — MetadataSchema

Configurable schemas defining what structured metadata to extract from documents.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `name` | String | Yes (unique) | Schema name |
| `description` | String | | Schema description |
| `extractionContext` | String | | Context hints for LLM extraction |
| `fields` | List<MetadataField> | | Field definitions |
| `linkedMimeTypes` | List<String> | | Applicable MIME types |
| `active` | boolean | | Whether active |
| `createdAt` | Instant | | Creation timestamp |
| `updatedAt` | Instant | | Last update timestamp |
| `sourcePackSlug` | String | | Governance pack source |
| `sourcePackVersion` | Integer | | Pack version |
| `importedAt` | Instant | | Import timestamp |

**Embedded: MetadataField**

| Field | Type | Description |
|-------|------|-------------|
| `fieldName` | String | Field identifier |
| `dataType` | FieldType | TEXT, NUMBER, DATE, CURRENCY, BOOLEAN, KEYWORD |
| `required` | boolean | Whether LLM must extract this field |
| `description` | String | Field description |
| `extractionHint` | String | Hint for LLM on how to find this value |
| `examples` | List<String> | Example values |

**Relations:**
- `id` ← `classification_categories.metadataSchemaId`

**Endpoints:**
- `GET /api/admin/governance/metadata-schemas` — List active schemas
- `GET /api/admin/governance/metadata-schemas/all` — List all schemas
- `POST /api/admin/governance/metadata-schemas` — Create schema
- `PUT /api/admin/governance/metadata-schemas/{id}` — Update schema
- `POST /api/admin/governance/metadata-schemas/{id}/fields` — Add field
- `DELETE /api/admin/governance/metadata-schemas/{id}/fields/{fieldName}` — Remove field
- `DELETE /api/admin/governance/metadata-schemas/{id}` — Deactivate schema
- `GET /api/admin/governance/metadata-schemas/coverage` — Coverage report
- `GET /api/admin/governance/metadata-schemas/quality/{documentId}` — Quality assessment
- `POST /api/admin/governance/metadata-schemas/suggest` — LLM-powered field suggestion
- `POST /api/admin/governance/metadata-schemas/test` — Test extraction on document
- `POST /api/admin/governance/ai/bulk-schema-suggest` — Bulk schema generation
- (internal) Used by LLM via MCP tool `get_metadata_schemas` during classification

---

## BERT / ML Training

### `bert_training_jobs` — BertTrainingJob

Tracks BERT model training runs.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `status` | JobStatus | | PENDING, TRAINING, COMPLETED, FAILED, PROMOTED |
| `modelVersion` | String | | Trained model version identifier |
| `baseModel` | String | | Base model name |
| `trainingConfig` | Map<String, Object> | | Training hyperparameters |
| `sampleCount` | int | | Number of training samples used |
| `categoryCount` | int | | Number of categories trained |
| `labelMap` | Map<String, Object> | | Label-to-category mapping |
| `metrics` | Map<String, Object> | | Training metrics (accuracy, loss, etc.) |
| `modelPath` | String | | Path to trained model |
| `promoted` | boolean | | Whether promoted to production |
| `startedBy` | String | | User who started training |
| `startedAt` | Instant | | Training start time |
| `completedAt` | Instant | | Training completion time |
| `error` | String | | Error if failed |

**Endpoints:**
- `GET /api/admin/bert/status` — Get BERT model status
- `GET /api/admin/bert/stats` — Training statistics
- `GET /api/admin/bert/training-readiness` — Check retraining readiness
- `GET /api/admin/bert/retraining-advice` — Get retraining advice

---

### `bert_training_data` — TrainingDataSample

Training samples for BERT classifier, collected manually or auto-collected from classifications.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `text` | String | | Document text sample |
| `categoryId` | String | Yes | FK to `classification_categories` |
| `categoryName` | String | | Denormalised category name |
| `sensitivityLabel` | String | | Sensitivity level |
| `source` | String | Yes | MANUAL_UPLOAD, AUTO_COLLECTED, BULK_IMPORT |
| `sourceDocumentId` | String | Yes | FK to `documents` (source document) |
| `confidence` | double | | Classification confidence |
| `verified` | boolean | | Whether human-verified |
| `fileName` | String | | Source file name |
| `createdAt` | Instant | | Creation timestamp |
| `updatedAt` | Instant | | Last update timestamp |

**Relations:**
- `categoryId` → `classification_categories.id`
- `sourceDocumentId` → `documents.id`

**Endpoints:**
- `GET /api/admin/bert/training-data` — List training data
- `GET /api/admin/bert/label-map` — Get label-to-category mappings
- `GET /api/admin/bert/training-samples` — List samples
- `GET /api/admin/bert/training-samples/stats` — Sample statistics
- `POST /api/admin/bert/training-samples` — Create sample
- `POST /api/admin/bert/training-samples/upload` — Bulk upload (multipart)
- `PUT /api/admin/bert/training-samples/{id}` — Update sample
- `POST /api/admin/bert/training-samples/verify/{id}` — Verify sample
- `DELETE /api/admin/bert/training-samples/{id}` — Delete sample
- `DELETE /api/admin/bert/training-samples/bulk` — Bulk delete
- `GET /api/admin/bert/training-samples/export` — Export samples
- `GET /api/admin/bert/training-samples/config` — Get sample config
- `PUT /api/admin/bert/training-samples/config` — Update sample config
- (internal) `BertTrainingDataBackfillRunner` backfills from corrections on startup

---

## External Storage & Drives

### `connected_drives` — ConnectedDrive

OAuth-connected external storage providers (Google Drive, Gmail, future SharePoint).

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `userId` | String | Yes | FK to `app_security_user_account` |
| `provider` | String | | Provider identifier |
| `providerType` | StorageProviderType | | GOOGLE_DRIVE, GMAIL, SHAREPOINT, BOX, SMB |
| `displayName` | String | | User-friendly drive name |
| `providerAccountEmail` | String | | OAuth account email |
| `providerAccountName` | String | | OAuth account name |
| `config` | Map<String, String> | | Provider-specific config |
| `accessToken` | String | | OAuth access token (encrypted) |
| `refreshToken` | String | | OAuth refresh token (encrypted) |
| `tokenExpiresAt` | Instant | | Token expiry |
| `grantedScopes` | String | | OAuth scopes granted |
| `defaultLabelId` | String | | Default Google Workspace label ID |
| `defaultLabelName` | String | | Default label display name |
| `fieldMappings` | Map<String, String> | | Label field-to-taxonomy mappings |
| `monitoredFolderIds` | List<String> | | Folders being auto-monitored |
| `systemDrive` | boolean | | Whether this is the local system drive |
| `active` | boolean | | Whether drive is active |
| `connectedAt` | Instant | | Connection timestamp |
| `lastSyncAt` | Instant | | Last sync timestamp |

**Relations:**
- `userId` → `app_security_user_account.id`
- `id` ← `documents.connectedDriveId`
- `id` ← `documents.filedToDriveId`
- `id` ← `label_taxonomy_mappings.driveId`
- `id` ← `gmail_ingest_cursors.connectedDriveId`

**Endpoints:**
- `GET /api/drives/google/auth-url` — Get OAuth URL
- `GET /api/drives/google/callback` — OAuth callback
- `GET /api/drives` — List connected drives
- `GET /api/drives/config-status` — Check OAuth config status
- `GET /api/drives/stats` — Drive statistics
- `DELETE /api/drives/{driveId}` — Disconnect drive
- `GET /api/drives/{driveId}/shared-drives` — List shared drives
- `GET /api/drives/{driveId}/folders` — List folders
- `POST /api/drives/{driveId}/monitor/{folderId}` — Start monitoring folder
- `DELETE /api/drives/{driveId}/monitor/{folderId}` — Stop monitoring folder
- `GET /api/drives/{driveId}/files` — List files
- `POST /api/drives/{driveId}/register` — Register files for classification
- `GET /api/drives/{driveId}/content/{fileId}` — Download file content
- `POST /api/drives/sync-classification/{documentId}` — Write-back classification to Drive
- `GET /api/drives/{driveId}/labels` — Get Workspace labels
- `PUT /api/drives/{driveId}/label-config` — Save label config
- `DELETE /api/drives/{driveId}/label-config` — Remove label config
- `POST /api/drives/{driveId}/relabel` — Batch relabel classified docs
- (internal) `LocalDriveBootstrap` creates local system drive on startup
- (internal) `GoogleDriveFolderMonitor` auto-ingests from monitored folders

---

### `label_taxonomy_mappings` — LabelTaxonomyMapping

Maps Google Workspace label values to taxonomy categories for bidirectional sync.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `driveId` | String | Yes | FK to `connected_drives` |
| `labelId` | String | | Google Workspace label ID |
| `labelName` | String | | Label display name |
| `fieldId` | String | | Label field ID |
| `fieldName` | String | | Label field name |
| `fieldType` | String | | Label field type |
| `valueMappings` | List<ValueMapping> | | Value-to-category mappings |
| `conflictPolicy` | ConflictPolicy | | LABEL_WINS, AI_WINS, FLAG_FOR_REVIEW |
| `syncDirection` | SyncDirection | | READ_ONLY, WRITE_ONLY, BIDIRECTIONAL |
| `collectAsTrainingData` | boolean | | Whether to collect for BERT training |
| `labelConfidence` | double | | Confidence level for label-based classifications |
| `createdAt` | Instant | | Creation timestamp |
| `updatedAt` | Instant | | Last update timestamp |
| `createdBy` | String | | Creator |

**Embedded: ValueMapping**

| Field | Type | Description |
|-------|------|-------------|
| `labelValue` | String | Label option value |
| `categoryId` | String | FK to `classification_categories` |
| `categoryName` | String | Denormalised category name |
| `classificationCode` | String | ISO 15489 code |
| `taxonomyLevel` | String | Taxonomy level |
| `unmapped` | boolean | Whether value is unmapped |

**Relations:**
- `driveId` → `connected_drives.id`
- `valueMappings[].categoryId` → `classification_categories.id`

**Endpoints:**
- `GET /api/drives/{driveId}/label-taxonomy-mapping` — Get mappings
- `PUT /api/drives/{driveId}/label-taxonomy-mapping` — Save/upsert mapping
- `DELETE /api/drives/{driveId}/label-taxonomy-mapping` — Delete mappings
- `POST /api/drives/{driveId}/import-from-labels` — Bulk import from labels
- `GET /api/drives/{driveId}/files/{fileId}/labels` — Read file labels

---

### `gmail_ingest_cursors` — GmailIngestCursor

Tracks Gmail polling state for email ingestion.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `connectedDriveId` | String | Yes | FK to `connected_drives` |
| `query` | String | | Gmail search query |
| `lastHistoryId` | String | | Last processed Gmail history ID |
| `lastPollAt` | Instant | | Last poll timestamp |
| `messagesIngested` | int | | Total messages ingested |

**Relations:**
- `connectedDriveId` → `connected_drives.id`

**Endpoints:**
- (internal) Updated by `GmailPollingScheduler` every 60 seconds

---

## PII & Subject Access Requests

### `subject_access_requests` — SubjectAccessRequest

GDPR/DSAR Subject Access Requests tracking matched documents.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `reference` | String | Yes (unique) | Unique SAR reference number |
| `dataSubjectName` | String | | Name of data subject |
| `dataSubjectEmail` | String | | Email of data subject |
| `searchTerms` | List<String> | | PII search terms |
| `requestedBy` | String | | User who created the SAR |
| `requestDate` | Instant | | Request received date |
| `deadline` | Instant | | Response deadline |
| `status` | String | Yes | RECEIVED, SEARCHING, REVIEWING, COMPILING, COMPLETED, OVERDUE |
| `jurisdiction` | String | | Legal jurisdiction |
| `matchedDocumentIds` | List<String> | | FKs to `documents` |
| `totalMatches` | int | | Number of matched documents |
| `notes` | List<SarNote> | | Progress notes |
| `assignedTo` | String | | Assigned user ID |
| `completedAt` | Instant | | Completion timestamp |

**Embedded: SarNote**

| Field | Type | Description |
|-------|------|-------------|
| `text` | String | Note content |
| `author` | String | Note author |
| `timestamp` | Instant | Note timestamp |

**Relations:**
- `matchedDocumentIds` → `documents.id` (M:N)
- `assignedTo` → `app_security_user_account.id`

**Endpoints:**
- `GET /api/pii/sar` — List active SARs
- `GET /api/pii/sar/all` — List all SARs
- `GET /api/pii/sar/{id}` — Get SAR details
- `POST /api/pii/sar` — Create SAR (auto-searches documents)
- `PUT /api/pii/sar/{id}/status` — Update SAR status
- `POST /api/pii/sar/{id}/assign` — Assign SAR to user
- `GET /api/pii/sar/{id}/export` — Export SAR manifest
- `POST /api/pii/search` — Search documents by PII
- `GET /api/pii/summary` — PII summary across all documents

---

## Audit & Monitoring

### `audit_events` — AuditEvent

Document-level audit trail tracking every status transition and user action.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `documentId` | String | Yes | FK to `documents` |
| `action` | String | Yes | Action performed |
| `performedBy` | String | | Actor identifier |
| `performedByType` | String | | USER, SYSTEM, LLM |
| `details` | Map<String, String> | | Action-specific details |
| `timestamp` | Instant | | When action occurred |

**Relations:**
- `documentId` → `documents.id`

**Endpoints:**
- `GET /api/documents/{id}/audit` — Get document audit trail
- (internal) Created by every pipeline stage and user action

---

### `system_audit_log` — SystemAuditEvent

System-wide audit log capturing HTTP requests and admin operations.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `timestamp` | Instant | Yes | Event timestamp |
| `userId` | String | Yes | FK to user |
| `userEmail` | String | | User email |
| `action` | String | Yes | Action type |
| `resourceType` | String | Yes | Resource type (DOCUMENT, USER, etc.) |
| `resourceId` | String | | Resource identifier |
| `httpMethod` | String | | HTTP method |
| `endpoint` | String | | API endpoint |
| `requestSummary` | String | | Request summary |
| `responseStatus` | int | | HTTP response status |
| `success` | boolean | | Whether action succeeded |
| `errorMessage` | String | | Error message if failed |
| `ipAddress` | String | | Client IP |
| `userAgent` | String | | Client user agent |

**Relations:**
- `userId` → `app_security_user_account.id`

**Endpoints:**
- `GET /api/admin/audit` — Query audit logs (filterable)
- `GET /api/admin/audit/stats` — Audit statistics
- `GET /api/admin/audit/recent` — Recent audit events
- `GET /api/admin/access/document/{documentId}` — Document access audit
- `GET /api/admin/access/category/{categoryId}` — Category access audit
- `GET /api/admin/access/matrix` — Full access control matrix

---

### `system_errors` — SystemError

System errors with severity tracking and resolution workflow.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `timestamp` | Instant | Yes | Error timestamp |
| `severity` | String | | CRITICAL, ERROR, WARNING |
| `category` | String | Yes | PIPELINE, STORAGE, QUEUE, AUTH, EXTERNAL_API, INTERNAL |
| `service` | String | | Service name |
| `message` | String | | Error message |
| `stackTrace` | String | | Full stack trace |
| `documentId` | String | | FK to `documents` (if applicable) |
| `userId` | String | | FK to user (if applicable) |
| `endpoint` | String | | API endpoint (if applicable) |
| `httpMethod` | String | | HTTP method (if applicable) |
| `resolved` | boolean | Yes | Whether resolved |
| `resolvedBy` | String | | User who resolved |
| `resolvedAt` | Instant | | Resolution timestamp |
| `resolution` | String | | Resolution notes |

**Endpoints:**
- (internal) Created by error handlers across all services
- Exposed via admin monitoring endpoints

---

### `ai_usage_log` — AiUsageLog

Tracks every LLM invocation with full prompt/response audit and cost tracking.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `timestamp` | Instant | Yes | Invocation timestamp |
| `usageType` | String | Yes | CLASSIFY, SUGGEST_SCHEMA, TEST_SCHEMA, IMPROVE_BLOCK, METADATA_EXTRACT |
| `triggeredBy` | String | Yes | User or system that triggered |
| `documentId` | String | | FK to `documents` |
| `documentName` | String | | Document file name |
| `provider` | String | | LLM provider (ANTHROPIC, OLLAMA) |
| `model` | String | | Model ID used |
| `pipelineId` | String | | FK to `pipeline_definitions` |
| `promptBlockId` | String | | FK to `pipeline_blocks` |
| `promptBlockVersion` | Integer | | Block version used |
| `systemPrompt` | String | | Full system prompt sent |
| `userPrompt` | String | | Full user prompt sent |
| `toolCalls` | List<ToolCall> | | MCP tool calls made |
| `response` | String | | LLM response text |
| `reasoning` | String | | LLM reasoning/thinking |
| `result` | Map<String, Object> | | Parsed result |
| `durationMs` | long | | Call duration |
| `inputTokens` | int | | Input token count |
| `outputTokens` | int | | Output token count |
| `estimatedCost` | double | | Estimated cost in USD |
| `status` | String | Yes | SUCCESS, FAILED, NO_RESULT |
| `errorMessage` | String | | Error if failed |
| `outcome` | String | | ACCEPTED, OVERRIDDEN, REJECTED |
| `overriddenBy` | String | | User who overrode |
| `outcomeAt` | Instant | | Outcome timestamp |

**Embedded: ToolCall**

| Field | Type | Description |
|-------|------|-------------|
| `toolName` | String | MCP tool name |
| `input` | String | Tool input |
| `output` | String | Tool output |
| `durationMs` | long | Call duration |

**Relations:**
- `documentId` → `documents.id`
- `pipelineId` → `pipeline_definitions.id`
- `promptBlockId` → `pipeline_blocks.id`

**Endpoints:**
- `GET /api/admin/ai-usage` — AI usage statistics
- (internal) Created by every LLM invocation in the classification pipeline

---

## Identity & Access Control

### `app_security_user_account` — UserModel

User accounts with authentication, roles, permissions, and directory sync.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `email` | String | Yes (unique) | Login email |
| `password` | String | | BCrypt hashed password |
| `accountType` | UserAccountType | Yes | Account type enum |
| `signUpMethod` | SignUpMethod | | LOCAL, GOOGLE, ENTRA_ID |
| `accountLocks` | Locks | | Account lock/ban/disable state |
| `accountExpiryDate` | LocalDate | | Account expiry date |
| `credentialsExpiryDate` | LocalDate | | Password expiry date |
| `twoFactorSecret` | String | | TOTP secret |
| `isTwoFactorEnabled` | Boolean | | Whether 2FA is on |
| `identity` | Identity | | OAuth identity info |
| `roles` | Set<String> | | Role keys (synced from subscriptions) |
| `permissions` | Set<String> | | Permission keys (synced from subscriptions) |
| `firstName` | String | | First name |
| `lastName` | String | | Last name |
| `displayName` | String | | Display name |
| `department` | String | | Department |
| `jobTitle` | String | | Job title |
| `organisationId` | String | | Organisation scope |
| `avatarUrl` | String | | Profile picture URL |
| `sensitivityClearanceLevel` | int | | Max sensitivity level accessible |
| `taxonomyGrantIds` | Set<String> | | FKs to `taxonomy_grants` |
| `externalDirectoryId` | String | | External directory user ID |
| `externalDirectorySource` | String | | GOOGLE, ENTRA_ID |
| `lastDirectorySyncAt` | Instant | | Last directory sync |
| `createdDate` | LocalDateTime | | Account creation date |
| `lastLoginAt` | Instant | | Last login timestamp |

**Embedded: Locks**

| Field | Type | Description |
|-------|------|-------------|
| `accountNonLocked` | Boolean | Not locked |
| `accountNonExpired` | Boolean | Not expired |
| `accountNonDisabled` | Boolean | Not disabled |
| `accountNonBanned` | Boolean | Not banned |
| `accountDeleted` | Boolean | Soft-deleted |

**Embedded: Identity**

| Field | Type | Description |
|-------|------|-------------|
| `provider` | String | OAuth provider |
| `subject` | String | OAuth subject ID |
| `issuer` | String | Token issuer |
| `userName` | String | Provider username |

**Relations:**
- `taxonomyGrantIds` → `taxonomy_grants.id` (M:N)
- `id` ← `documents.uploadedBy`
- `id` ← `connected_drives.userId`
- `id` ← `app_subscriptions.userId`

**Endpoints:**
- `POST /api/auth/login` — Login (JWT + cookie)
- `POST /api/auth/logout` — Logout
- `GET /api/user/me` — Get current user profile and permissions
- `PUT /api/user/me/password` — Change password
- `GET /api/admin/users` — List all users (admin)
- `GET /api/admin/users/{id}` — Get user details
- `POST /api/admin/users` — Create user
- `PUT /api/admin/users/{id}` — Update user
- `PUT /api/admin/users/{id}/roles` — Update user roles
- `PUT /api/admin/users/{id}/clearance` — Update sensitivity clearance
- `PUT /api/admin/users/{id}/status` — Update account status
- `POST /api/admin/users/{id}/reset-password` — Reset password
- (internal) `AdminUserSeeder` creates default admin on first startup
- (internal) `PermissionDataSeeder` seeds default roles on startup
- (internal) `SubscriptionPermissionSyncService` syncs roles/permissions from subscriptions

---

### `taxonomy_grants` — TaxonomyGrant

Grants users access to specific taxonomy categories (row-level access control).

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `userId` | String | Yes | FK to `app_security_user_account` |
| `categoryId` | String | Yes | FK to `classification_categories` |
| `includeChildren` | boolean | | Whether grant includes child categories |
| `operations` | Set<String> | | Allowed operations (READ, WRITE, DELETE) |
| `grantedBy` | String | | User who granted access |
| `grantedAt` | Instant | | Grant timestamp |
| `expiresAt` | Instant | | Expiry timestamp |
| `reason` | String | | Reason for grant |

**Relations:**
- `userId` → `app_security_user_account.id`
- `categoryId` → `classification_categories.id`

**Endpoints:**
- `GET /api/admin/users/{id}/taxonomy-grants` — Get user's grants
- (internal) Created by directory sync and admin operations

---

### `directory_role_mappings` — DirectoryRoleMapping

Maps external directory groups (Google, Entra ID) to internal roles.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `directorySource` | String | Yes | GOOGLE, ENTRA_ID |
| `externalGroupName` | String | | External group display name |
| `externalGroupEmail` | String | | External group email |
| `internalRoleKey` | String | | FK to `app_roles.key` |
| `sensitivityClearanceLevel` | int | | Clearance level for members |
| `taxonomyGrantCategoryIds` | List<String> | | FKs to `classification_categories` |
| `active` | boolean | | Whether mapping is active |

**Relations:**
- `internalRoleKey` → `app_roles.key`
- `taxonomyGrantCategoryIds` → `classification_categories.id` (M:N)

**Endpoints:**
- `GET /api/admin/directory-mappings` — List mappings
- `POST /api/admin/directory-mappings` — Create mapping
- `PUT /api/admin/directory-mappings/{id}` — Update mapping
- `DELETE /api/admin/directory-mappings/{id}` — Delete mapping
- `POST /api/admin/directory-mappings/sync` — Sync directory

---

## Platform Configuration

### `app_config` — AppConfig

Runtime application configuration (menus, labels, feature flags, etc.).

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `key` | String | Yes (unique) | Config key (e.g. sidebar.menu, feature.pii) |
| `category` | String | | Config category |
| `value` | Object | | Config value (any JSON type) |
| `description` | String | | Description of config item |
| `updatedAt` | Instant | | Last update timestamp |

**Endpoints:**
- `GET /api/config` — Get public config (frontend)
- `GET /api/admin/config` — Get admin config
- (internal) Managed by `AppConfigService` with in-memory cache

---

## Licensing & Subscriptions

### `app_features` — Feature

Individual licensable permissions/capabilities.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `name` | String | | Feature name |
| `permissionKey` | String | Yes (unique) | Permission key (e.g. CAN_UPLOAD_DOCUMENTS) |
| `description` | String | | Feature description |
| `category` | String | | Feature category |
| `group` | String | | Feature group |
| `status` | String | | ACTIVE, INACTIVE |

**Relations:**
- `id` ← `app_roles.featureIds`
- `id` ← `app_products.featureIds`

**Endpoints:**
- `GET /api/admin/users/features` — List available features
- (internal) Seeded by `PermissionDataSeeder`

---

### `app_roles` — Role

Role bundles grouping features with account type scoping.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `name` | String | | Role name |
| `key` | String | Yes (unique) | Role key (e.g. ADMIN, SUB_PREMIUM) |
| `description` | String | | Role description |
| `roleType` | String | | Role type |
| `featureIds` | List<String> | | FKs to `app_features` |
| `accountTypeScope` | List<String> | | Which account types can hold this role |
| `status` | String | | ACTIVE, INACTIVE |
| `systemProtected` | boolean | | Whether role cannot be deleted |
| `adminRole` | boolean | | Whether this is an admin role |
| `defaultForNewUsers` | boolean | | Auto-assign to new users |
| `defaultSensitivityClearance` | int | | Default clearance level for role holders |

**Relations:**
- `featureIds` → `app_features.id` (M:N)
- `id` ← `app_products.roleIds`
- `key` ← `app_security_user_account.roles`
- `key` ← `directory_role_mappings.internalRoleKey`

**Endpoints:**
- `GET /api/admin/users/roles` — List roles
- `POST /api/admin/users/roles` — Create role
- `PUT /api/admin/users/roles/{id}` — Update role
- (internal) Seeded by `PermissionDataSeeder`

---

### `app_products` — Product

Purchasable subscription products bundling roles and features with pricing.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `name` | String | | Product name |
| `description` | String | | Product description |
| `roleIds` | List<String> | | FKs to `app_roles` |
| `featureIds` | List<String> | | FKs to `app_features` (direct) |
| `billingType` | String | | Billing model |
| `monthlyPriceInPence` | Long | | Monthly price in pence |
| `annualPriceInPence` | Long | | Annual price in pence |
| `status` | String | | ACTIVE, INACTIVE |

**Relations:**
- `roleIds` → `app_roles.id` (M:N)
- `featureIds` → `app_features.id` (M:N)
- `id` ← `app_subscriptions.productId`

**Endpoints:**
- (internal) Managed via admin panel (endpoints in platform module)

---

### `app_subscriptions` — Subscription

Links users/companies to products with billing status tracking.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `userId` | String | Yes | FK to `app_security_user_account` |
| `companyId` | String | Yes | Company ID (for company-wide subscriptions) |
| `productId` | String | Yes | FK to `app_products` |
| `billingInterval` | String | | MONTHLY, ANNUAL |
| `status` | String | | ACTIVE, TRIAL, EXPIRED, CANCELLED |
| `startDate` | Instant | | Subscription start |
| `endDate` | Instant | | Subscription end |
| `createdAt` | Instant | | Creation timestamp |

**Relations:**
- `userId` → `app_security_user_account.id`
- `productId` → `app_products.id`

**Endpoints:**
- (internal) Managed via admin panel; triggers `SubscriptionPermissionSyncService`

---

## Governance Hub (SaaS)

### `governance_packs` — GovernancePack

Shareable governance packs (taxonomy, policies, retention schedules) published to the hub.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `slug` | String | Yes (unique) | URL-friendly pack identifier |
| `name` | String | | Pack name |
| `description` | String | | Pack description |
| `author` | PackAuthor | | Pack author info |
| `jurisdiction` | String | Yes | Legal jurisdiction |
| `industries` | List<String> | | Target industries |
| `regulations` | List<String> | | Covered regulations |
| `tags` | List<String> | | Search tags |
| `status` | PackStatus | | DRAFT, PUBLISHED, DEPRECATED, ARCHIVED |
| `featured` | boolean | | Whether featured on hub |
| `downloadCount` | long | | Total downloads |
| `averageRating` | double | | Average review rating |
| `reviewCount` | int | | Number of reviews |
| `latestVersionNumber` | int | | Latest published version |
| `createdAt` | Instant | | Creation timestamp |
| `updatedAt` | Instant | | Last update timestamp |
| `publishedAt` | Instant | | First publication timestamp |

**Embedded: PackAuthor**

| Field | Type | Description |
|-------|------|-------------|
| `name` | String | Author name |
| `organisation` | String | Organisation |
| `email` | String | Contact email |
| `verified` | boolean | Whether verified author |

**Relations:**
- `id` ← `pack_versions.packId`
- `id` ← `pack_reviews.packId`
- `slug` ← `installed_packs.packSlug`
- `slug` ← `pack_updates_available.packSlug`
- `slug` ← `pack_feedback.packSlug`

**Endpoints:**
- `GET /api/hub/packs/*` — Browse packs
- `GET /api/hub/admin/packs/*` — Admin pack management
- (internal) Seeded by `HubDataSeeder`

---

### `pack_versions` — PackVersion

Immutable versions of governance packs with component payloads.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `packId` | String | Yes | FK to `governance_packs` |
| `versionNumber` | int | | Sequential version number |
| `changelog` | String | | What changed in this version |
| `publishedBy` | String | | Publisher |
| `publishedAt` | Instant | | Publication timestamp |
| `components` | List<PackComponent> | | Pack component payloads |
| `compatibilityVersion` | String | | Minimum platform version |

**Embedded: PackComponent**

| Field | Type | Description |
|-------|------|-------------|
| `type` | ComponentType | TAXONOMY_CATEGORIES, RETENTION_SCHEDULES, SENSITIVITY_DEFINITIONS, GOVERNANCE_POLICIES, PII_TYPE_DEFINITIONS, METADATA_SCHEMAS, STORAGE_TIERS, TRAIT_DEFINITIONS, PIPELINE_BLOCKS, LEGISLATION |
| `name` | String | Component name |
| `description` | String | Component description |
| `itemCount` | int | Number of items |
| `data` | List<Map<String, Object>> | Component data payload |

**Relations:**
- `packId` → `governance_packs.id`

**Endpoints:**
- `GET /api/hub/downloads/*` — Download pack version
- (internal) Created when pack is published

---

### `pack_reviews` — PackReview

User reviews of governance packs.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `packId` | String | Yes | FK to `governance_packs` |
| `apiKeyPrefix` | String | | API key prefix (reviewer identity) |
| `tenantName` | String | | Reviewer's tenant name |
| `rating` | int | | Rating (1-5) |
| `comment` | String | | Review comment |
| `createdAt` | Instant | | Review timestamp |

**Relations:**
- `packId` → `governance_packs.id`

---

### `pack_feedback` — PackFeedback

Anonymised classification feedback from tenants using a pack.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `packSlug` | String | Yes | FK to `governance_packs.slug` |
| `packVersion` | int | | Pack version |
| `tenantId` | String | | Anonymous tenant ID |
| `totalDocumentsClassified` | int | | Documents classified with pack |
| `totalCorrections` | int | | Total corrections made |
| `correctionsByType` | Map<String, Integer> | | Corrections breakdown |
| `topReclassifications` | List<Map<String, Object>> | | Most common reclassifications |
| `averageConfidence` | double | | Average classification confidence |
| `receivedAt` | Instant | Yes | Feedback timestamp |

**Relations:**
- `packSlug` → `governance_packs.slug`

**Endpoints:**
- `POST /api/hub/packs/{slug}/feedback` — Submit pack feedback
- `GET /api/hub/admin/packs/{slug}/feedback` — Get aggregated feedback
- `POST /api/admin/governance/feedback/preview` — Preview feedback for sharing
- `POST /api/admin/governance/feedback/share` — Share feedback with hub

---

### `pack_download_records` — PackDownloadRecord

Tracks pack downloads by tenant.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `packId` | String | | FK to `governance_packs` |
| `versionNumber` | int | | Version downloaded |
| `apiKeyPrefix` | String | | Downloader API key prefix |
| `tenantName` | String | | Tenant name |
| `downloadedAt` | Instant | | Download timestamp |
| `componentsDownloaded` | List<String> | | Which components were downloaded |

**Relations:**
- `packId` → `governance_packs.id`

---

### `installed_packs` — InstalledPack

Tracks which governance packs are installed on a tenant instance.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `packSlug` | String | Yes (unique) | FK to `governance_packs.slug` |
| `packName` | String | | Pack display name |
| `installedVersion` | int | | Currently installed version |
| `importedAt` | Instant | | Installation timestamp |
| `componentTypesImported` | List<String> | | Which component types were imported |

**Relations:**
- `packSlug` → `governance_packs.slug`

**Endpoints:**
- (internal) Created during pack installation
- (internal) `InstalledPackBackfillRunner` backfills on startup

---

### `pack_updates_available` — PackUpdateAvailable

Notifications about available pack updates.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `packSlug` | String | Yes (unique) | FK to `governance_packs.slug` |
| `packName` | String | | Pack display name |
| `installedVersion` | int | | Current installed version |
| `latestVersion` | int | | Available version |
| `changelog` | String | | What changed |
| `publishedAt` | Instant | | When new version was published |
| `componentTypes` | List<String> | | Affected component types |
| `dismissed` | boolean | | Whether user dismissed notification |
| `detectedAt` | Instant | | When update was detected |

**Relations:**
- `packSlug` → `governance_packs.slug`
- `packSlug` → `installed_packs.packSlug`

**Endpoints:**
- (internal) Created by `PackUpdateObserver` scheduled task

---

### `import_item_snapshots` — ImportItemSnapshot

Snapshots of imported pack items for diff/conflict detection on upgrade.

**Compound Index:** `idx_pack_component_key` (packSlug: 1, componentType: 1, itemKey: 1, unique)

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `packSlug` | String | | Pack that provided this item |
| `packVersion` | int | | Pack version at import time |
| `componentType` | String | | Component type |
| `itemKey` | String | | Unique key within component type |
| `entityId` | String | | MongoDB ID of the imported entity |
| `snapshotFields` | Map<String, Object> | | Snapshot of imported field values |
| `importedAt` | Instant | | Import timestamp |

**Relations:**
- `packSlug` → `governance_packs.slug`

**Endpoints:**
- (internal) Created during pack import; used for conflict detection
- (internal) `ImportSnapshotBackfillRunner` backfills on startup

---

### `hub_users` — HubUser

User accounts for the governance hub SaaS application.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `username` | String | Yes (unique) | Login username |
| `passwordHash` | String | | BCrypt hashed password |
| `displayName` | String | | Display name |
| `email` | String | | Email address |
| `roles` | Set<String> | | Hub roles (ADMIN, EDITOR, VIEWER) |
| `active` | boolean | | Whether account is active |
| `createdAt` | Instant | | Creation timestamp |
| `lastLoginAt` | Instant | | Last login timestamp |
| `createdBy` | String | | Creator |

**Endpoints:**
- `GET /api/hub/admin/users` — Hub user management
- (internal) `HubAdminUserBootstrap` creates default hub admin

---

### `hub_api_keys` — HubApiKey

API keys for tenant access to the governance hub.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `keyHash` | String | Yes (unique) | SHA-256 hash of API key |
| `keyPrefix` | String | | First 8 chars (for display) |
| `tenantName` | String | | Tenant name |
| `tenantEmail` | String | | Tenant contact email |
| `permissions` | List<String> | | Allowed operations |
| `active` | boolean | | Whether key is active |
| `rateLimit` | int | | Requests per minute |
| `downloadQuota` | int | | Monthly download quota |
| `downloadsThisMonth` | int | | Downloads used this month |
| `quotaResetAt` | Instant | | When quota resets |
| `createdAt` | Instant | | Key creation timestamp |
| `lastUsedAt` | Instant | | Last usage timestamp |
| `expiresAt` | Instant | | Key expiry |

**Endpoints:**
- `GET /api/hub/admin/api-keys` — API key management

---

### `hub_ai_usage_log` — HubAiUsageLog

AI usage tracking for the governance hub application.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `timestamp` | Instant | Yes | Invocation timestamp |
| `usageType` | String | Yes | Usage type |
| `triggeredBy` | String | | Triggering user |
| `provider` | String | | LLM provider |
| `model` | String | | Model ID |
| `userPrompt` | String | | User prompt |
| `response` | String | | LLM response |
| `durationMs` | long | | Call duration |
| `status` | String | | SUCCESS, FAILED |
| `errorMessage` | String | | Error if failed |

---

### `hub_legislation` — HubLegislation

Legislation reference data for the governance hub (separate from tenant legislation).

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| `id` | String | @Id | MongoDB ObjectId |
| `key` | String | Yes (unique) | Unique legislation key |
| `name` | String | | Full legislation name |
| `shortName` | String | | Abbreviated name |
| `jurisdiction` | String | | Jurisdiction |
| `url` | String | | Link to legislation text |
| `description` | String | | Description |
| `active` | boolean | | Whether in force |
| `relevantArticles` | List<String> | | Relevant articles/sections |

---

## Entity Relationship Summary

```
app_security_user_account (Users)
├── connected_drives (1:N) — user's OAuth-connected storage
│   ├── documents (1:N) — documents from this drive
│   ├── label_taxonomy_mappings (1:N) — Drive label mappings
│   └── gmail_ingest_cursors (1:N) — Gmail polling state
├── app_subscriptions (1:N) — user's product subscriptions
│   └── app_products (N:1) — subscribed product
│       ├── app_roles (M:N) — roles included
│       │   └── app_features (M:N) — features in role
│       └── app_features (M:N) — features (direct)
├── taxonomy_grants (1:N) — category access grants
│   └── classification_categories (N:1)
└── directory_role_mappings — external group→role mappings

documents (Core Entity)
├── classification_results (1:N) — LLM classification history
├── classification_corrections (1:N) — human corrections
├── audit_events (1:N) — document audit trail
├── pipeline_runs (1:N) — pipeline executions
│   └── node_runs (1:N) — per-node execution records
├── ai_usage_log (1:N) — LLM invocation records
├── block_feedback (1:N) — block performance feedback
├── bert_training_data (1:N) — training samples sourced from doc
├── folders (N:1) — logical folder
├── documents (1:N) — child documents (email→attachments)
├── connected_drives (N:1) — source drive
├── pipeline_definitions (N:1) — processing pipeline
├── classification_categories (N:1) — assigned category
├── retention_schedules (N:1) — assigned retention
└── storage_tiers (N:1) — assigned storage tier

classification_categories (Taxonomy Tree)
├── classification_categories (1:N) — child categories (self-ref)
├── retention_schedules (N:1) — default retention
├── metadata_schemas (N:1) — linked extraction schema
├── governance_policies (M:N) — applicable policies
│   └── legislation (M:N) — supporting legislation
├── pipeline_definitions (M:N) — routed pipelines
└── sensitivity_definitions — default sensitivity

pipeline_definitions (Processing Pipelines)
├── pipeline_blocks (M:N) — blocks used in steps/nodes
│   ├── block_feedback (1:N) — user feedback
│   └── block_versions (embedded) — version history
└── node_type_definitions — palette of node types

governance_packs (Hub — Shareable Governance)
├── pack_versions (1:N) — immutable version history
│   └── pack_components (embedded) — taxonomy, policies, etc.
├── pack_reviews (1:N) — user reviews
├── pack_feedback (1:N) — anonymised tenant feedback
├── pack_download_records (1:N) — download audit
├── installed_packs (1:1) — local installation record
├── pack_updates_available (1:1) — update notifications
└── import_item_snapshots (1:N) — imported item snapshots

subject_access_requests (PII/GDPR)
└── documents (M:N) — matched documents

system_audit_log — system-wide HTTP audit
system_errors — error tracking with resolution
app_config — runtime configuration key-value store
hub_users / hub_api_keys — hub authentication
hub_ai_usage_log / hub_legislation — hub-specific data
```
