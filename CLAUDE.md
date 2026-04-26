# Governance-Led Storage — Project Guidelines

## Architecture

- **Backend:** Spring Boot 4.0.2 (Java 21), Maven multi-module monorepo, MongoDB
- **Frontend:** Next.js 16 (React 19, TypeScript, TailwindCSS 4)
- **Infra:** Docker Compose ��� Cloudflare tunnel, nginx, MongoDB, Elasticsearch, RabbitMQ, MinIO
- **Auth:** JWT + OAuth2 (optional), CSRF via cookie

### Module structure

- `gls-platform` — Core platform: identity, security, JWT, OAuth2, config, products/licensing
- `gls-governance` — Governance rules and policy engine
- `gls-document` — Document domain: models, repositories, storage services
- `gls-document-processing` — Document processing pipeline
- `gls-governance-enforcement` — Governance enforcement services
- `gls-mcp-server` — MCP server integration
- `gls-llm-orchestration` — LLM orchestration layer (RabbitMQ, Anthropic)
- `gls-app-assembly` — Spring Boot entry point, config, seeders, controllers

## API Contracts

**Every service-to-service interface must be defined by an OpenAPI 3.1.1 specification before any implementation code is written.** The contract is the source of truth; controllers, clients, and tests derive from it — never the other way round.

### Rules

- **OpenAPI 3.1.1** is the only accepted version for synchronous (REST/HTTP) contracts. No 3.0.x, no Swagger 2.0.
- **AsyncAPI 3.0** is the equivalent for asynchronous (RabbitMQ) interfaces — message schemas, channels, exchanges, routing keys.
- Contracts live under `contracts/<service>/openapi.yaml` (or `asyncapi.yaml`), each with its own `VERSION` file (semver) and `CHANGELOG.md`.
- Cross-cutting schemas (error envelope, security scheme, common headers, idempotency, pagination, capabilities) live under `contracts/_shared/` and are referenced via `$ref`. Block content schemas (PROMPT, ROUTER, EXTRACTOR, BERT_CLASSIFIER, ENFORCER) live under `contracts/blocks/` as JSON Schema 2020-12.
- A contract change requires a `VERSION` bump in the same PR. CI fails the build if the spec changes without a version bump.
- Breaking changes (removed fields, narrowed types, removed endpoints, changed required-ness) require a major version bump and a deprecation window — the old major stays callable until consumers migrate.
- Generated server stubs and client SDKs are committed under `contracts/<service>/generated/` so consumers don't need to re-run generators to inspect the surface area.

### Why OpenAPI 3.1.1 specifically

- Full JSON Schema 2020-12 alignment — the same schema vocabulary used in MongoDB documents, pipeline-block content, and metadata schemas.
- `null` is a first-class type, not a hack — matches Java records and TypeScript optionals cleanly.
- Webhooks are first-class — relevant for connector callbacks and Hub events.
- Latest stable patch (Oct 2024); tooling support is mature: springdoc 2.x, openapi-generator 7+, Spectral, Redocly, Swagger UI 5+.

### Workflow

1. Author or modify `contracts/<service>/openapi.yaml`.
2. Bump `contracts/<service>/VERSION`. Add a `CHANGELOG.md` entry.
3. Lint locally with Spectral or Redocly against the agreed ruleset.
4. Regenerate server stubs and client SDK; commit under `generated/`.
5. Implement against the generated stub — the controller honours what the spec says, not the other way round.
6. Open the PR. CI re-validates, regenerates, and fails on drift.

### What this means for new code

- **No controller method without an OpenAPI operation.** If you can't describe it in the spec, you can't ship it.
- **No hand-written HTTP client calls between services.** Use the generated SDK from the target service's contract. Hand-rolled HTTP will be rejected in review.
- **No "document it later".** The spec is part of the change, not a follow-up PR.
- **Existing services without specs are grandfathered** — specs are retrofitted via springdoc-openapi as a separate workstream — but new operations on those services still require a spec entry at PR time.
- **Cross-service refactors start in `contracts/`.** The PR that changes the contract is the PR that changes everything else.

## Decision log

**Every architectural decision lives in `version-2-decision-tree.csv`** — DECIDED, RECOMMENDED, OPEN, or DEFERRED. The CSV is the index; the rationale and full context live in `version-2-architecture.md` (referenced by the `Section` column).

### Rules

- When a new decision is made, append it to the CSV in the same PR as the doc/code change that captures it.
- When an OPEN question is resolved, update its row in place (status → DECIDED or RECOMMENDED, decision text filled in) — do not delete and re-add.
- When a DECIDED item is reversed, do not delete the row. Add a new row that supersedes it, and mark the old row's status as `SUPERSEDED` with a note pointing to the new ID.
- Categories must come from the existing set: Topology, Domain Model, Scaling, Tier strategy, Audit, Contracts — Shape, Contracts — Conventions, Contracts — Non-functional, Contracts — Deferred, Auth & Security, Cost / FinOps, Reliability. Add a new category only when an existing one genuinely doesn't fit.
- Section column points at the canonical location (`§7.8`, `§11.A.2`, etc.) where the decision is discussed in `version-2-architecture.md`.

## Progress log

**Track what was done in every phase and sub-phase of the v2 implementation in `version-2-implementation-log.md`.** This is the "what happened" companion to the decision log's "why" and the implementation plan's "what's planned."

### Rules

- After completing any work item from `version-2-implementation-plan.md`, append an entry to `version-2-implementation-log.md` in the same PR. Never in a separate PR.
- Entry shape (also in the log file): date, phase/sub-phase reference, what was done, decisions logged (CSV row IDs), contracts touched (with VERSION bump notes), other files changed, open issues, next step.
- Append-only — never edit or delete past entries. If a decision reverses or a stage is redone, append a new entry referencing the old one.
- Chronological — newest at the bottom; read top-to-bottom for the story.
- Track granularity is sub-phase (1.1, 1.2, …). Multiple entries per sub-phase are fine if work spans sessions; mark the last as the closing entry.
- Update the per-phase status board at the top of the log when a phase's status changes.
- Truthful — record detours, false starts, and reversals. The log is for learning, not optics.

## Configuration-Driven Design

**All application behaviour that is not core data infrastructure must be driven by configuration, not hardcoded.**

This means:

### Must be configuration (stored in MongoDB, editable at runtime)
- Menu items, navigation structure, sidebar links
- UI labels, display text, page titles
- Feature flags and toggles
- Role display names, permission labels, capability descriptions
- Status options, category lists, dropdown values
- Workflow states and transitions
- Anything that looks like an enum from a UI perspective

### May remain as code
- Security filter chain paths (`/api/auth/**`, `/actuator/**`)
- Database collection names and document schemas
- Spring config keys and profiles
- JWT claim structure and token mechanics
- Java enums used for **type safety in code** (e.g. `UserAccountType`) — but their display labels, ordering, and visibility must come from config

### How it works
- Config lives in a MongoDB collection (e.g. `app_config`)
- A `ConfigService` loads config on startup and supports refresh without restart
- Frontend fetches UI config via a public endpoint (e.g. `GET /api/public/config`)
- Java enums define what the code can handle; config defines what the user sees

### Why
- No redeployment for label changes, menu reordering, or toggling features
- Non-developers can manage application behaviour
- Keeps the codebase focused on logic, not presentation data

## Licensable Features & Subscription Model

**Every feature, workflow, and permission must be tracked as a licensable unit.** This enables an admin panel where subscriptions, products, and access tiers can be created and managed — matching the pattern from fractional-exchange.

### Entity hierarchy

```
Product (purchasable subscription tier)
  ├── roleIds → Role[]
  │     └── featureIds → Feature[]
  └── featureIds → Feature[] (direct)

Subscription (links a user/company to a Product)
  └── status: ACTIVE | TRIAL | EXPIRED | CANCELLED
```

### MongoDB collections

| Collection | Purpose |
|---|---|
| `app_features` | Individual permissions (e.g. `CAN_UPLOAD_DOCUMENTS`, `CAN_VIEW_ANALYTICS`) |
| `app_roles` | Bundles of features, scoped to account types (e.g. `SUB_PREMIUM`, `SUB_ENTERPRISE`) |
| `app_products` | Purchasable products linking roles + features with pricing |
| `app_subscriptions` | User/company subscriptions to products with billing intervals |
| `app_config` | Runtime configuration (menus, labels, flags, etc.) |

### How permissions flow

1. Admin creates Features (`app_features`) — each has a unique `permissionKey`
2. Admin creates Roles (`app_roles`) — each bundles feature IDs, has a unique `key` (prefix `SUB_` for subscription roles)
3. Admin creates Products (`app_products`) — links role IDs and feature IDs with pricing
4. Admin creates Subscription for user/company → triggers `SubscriptionPermissionSyncService`
5. Sync service loads Product → Roles → Features → extracts `permissionKey` values
6. User's `roles` and `permissions` sets are updated on their `UserModel`
7. Frontend checks permissions via `/api/user/me` response

### Rules for new features

- **Every new capability must have a corresponding Feature** with a `permissionKey`
- Group Features into Roles for subscription tiers
- Never hardcode permission checks against role names — check `permissionKey` values
- Subscription roles use `SUB_` prefix to distinguish from system roles (e.g. `ADMIN`)
- The admin panel will manage all CRUD for Features, Roles, Products, and Subscriptions

### Platform services (in `gls-platform`)

- `AppConfigService` — runtime config CRUD with in-memory cache + refresh
- `SubscriptionPermissionSyncService` — syncs subscription roles/permissions to user model
- `PublicConfigController` — `GET /api/public/config` for frontend config
- `AdminConfigController` — `GET/PUT /api/admin/config` for admin management

## Pipeline Blocks

**Blocks are versioned, reusable processing units that plug into pipelines.** Every prompt, regex pattern set, extractor, router, and enforcer is a block with full version history and user feedback tracking.

### Block types

| Type | What it contains | Example |
|---|---|---|
| `PROMPT` | System prompt + user template + model config | "Classification Prompt v3" |
| `REGEX_SET` | Regex patterns with types + confidence | "UK PII Patterns v2" |
| `EXTRACTOR` | Text extraction config | "Tika Text Extractor v1" |
| `ROUTER` | Conditional routing logic | "Confidence Router v1" |
| `ENFORCER` | Governance enforcement rules | "Standard Enforcement v1" |

### Version control

- Every block has an immutable version history — publish creates a new version, never overwrites
- Active version is what pipelines use by default
- Drafts can be edited and previewed before publishing
- Rollback to any previous version with one click
- Each version records: content snapshot, changelog, published by, timestamp

### Feedback loop

User corrections automatically create block feedback:
- Classification overrides → CORRECTION feedback on the PROMPT block
- PII dismissals → FALSE_POSITIVE feedback on the REGEX_SET block
- PII flags (missed) → MISSED feedback on the REGEX_SET block
- Admin can add SUGGESTION feedback manually

Feedback accumulates per block per version. When enough feedback exists, admins can use "Improve with AI" to generate a better version.

### Admin: Block Library (`/blocks`)

Block list with type filters, version counts, feedback badges. Detail panel with:
- **Configuration** — JSON editor for block content, save draft, publish
- **Versions** — timeline with rollback, version comparison
- **Feedback** — user corrections grouped by type

## Happy Path AND Unhappy Path

**Every pipeline stage and async workflow must handle both success and failure explicitly.** Never silently swallow errors or leave resources in an intermediate state.

### Rules

- **Every RabbitMQ consumer must catch exceptions and set a `*_FAILED` status** on the document (e.g. `PROCESSING_FAILED`, `CLASSIFICATION_FAILED`, `ENFORCEMENT_FAILED`). Never re-throw exceptions that cause infinite requeue loops.
- **Store the error** — `lastError` (message), `lastErrorStage` (which step failed), `failedAt` (timestamp) must be set on the document so admins and users can see what went wrong.
- **Failed documents must be retryable** — the monitoring page provides "Retry Failed" to re-queue them. The `reprocess` endpoint clears error state and increments `retryCount`.
- **Stale detection** — documents stuck in in-flight states (PROCESSING, CLASSIFYING) for >10 minutes are considered stale and can be reset from the monitoring page.
- **Audit everything** — every status transition, including failures, must produce an audit event.
- **No silent returns** — if a pipeline step cannot produce a result (e.g. LLM returns no classification), that is a failure and must be recorded as such.

### Document status flow

```
UPLOADED → PROCESSING → PROCESSED → CLASSIFYING → CLASSIFIED → GOVERNANCE_APPLIED
              ↓                          ↓                          ↓
       PROCESSING_FAILED        CLASSIFICATION_FAILED       ENFORCEMENT_FAILED
              ↓                          ↓                          ↓
           (retry)                    (retry)                    (retry)
```

### Pipeline error statuses

| Status | Stage | Set by |
|---|---|---|
| `PROCESSING_FAILED` | Text extraction / PII scan | `DocumentProcessingPipeline` |
| `CLASSIFICATION_FAILED` | LLM classification | `ClassificationPipeline` |
| `ENFORCEMENT_FAILED` | Governance enforcement | `ClassificationEnforcementConsumer` |

## Slug-Based URLs

**All user-facing items must be addressable by slug, not raw MongoDB ObjectId.**

- Slugs are generated on creation: `{slugified-name}-{last-6-chars-of-id}` (e.g. `maternity-leave-confirmation-a3f2b1`)
- `SlugGenerator` utility in `gls-document` handles generation
- Documents have a `slug` field with a unique sparse index
- Backend exposes `/by-slug/{slug}` endpoints alongside ID-based endpoints
- Frontend uses slugs in query params (e.g. `/documents?doc=maternity-leave-confirmation-a3f2b1`)
- Existing documents without slugs are backfilled on startup via `SlugBackfillRunner`

## Correction Feedback Loop

**Human corrections feed back to the LLM via MCP tools to improve classification accuracy over time.**

### How it works

1. LLM classifies a document → low confidence routes to review queue
2. Records manager approves, overrides, or flags PII → stored as `ClassificationCorrection` in MongoDB
3. Next classification: LLM calls `get_correction_history` and `get_org_pii_patterns` MCP tools
4. Tools return past human corrections relevant to the document's category/type
5. LLM adjusts its decision based on correction history — few-shot learning at inference time

### Correction types

| Type | Trigger | What's stored |
|---|---|---|
| `APPROVED_CORRECT` | Reviewer approves | Positive signal — LLM got it right |
| `CATEGORY_CHANGED` | Override with different category | Original vs corrected category + reason |
| `SENSITIVITY_CHANGED` | Override with different sensitivity | Original vs corrected sensitivity + reason |
| `BOTH_CHANGED` | Override changes both | Both corrections + reason |
| `PII_FLAGGED` | Report missed PII | PII type, description, context |

### MCP tools for feedback

| Tool | Called when | Returns |
|---|---|---|
| `get_correction_history` | Before every classification (required) | Past corrections for this category/mime type |
| `get_org_pii_patterns` | Before PII analysis | Organisation-specific PII types from corrections |

### Path to cheaper models

As corrections accumulate, the MCP tools provide richer context, reducing reliance on model reasoning. This enables stepping down from Sonnet to Haiku because the model's job shifts from open-ended classification to pattern matching against provided examples.

## Metadata Extraction Schemas

**The LLM extracts structured metadata from documents based on configurable schemas linked to taxonomy categories.**

### How it works

1. Admin creates a `MetadataSchema` (e.g. "HR Leave Request") with typed fields: `employee_name (text)`, `leave_type (keyword)`, `start_date (date)`, etc.
2. Schema is linked to taxonomy categories (e.g. "HR > Employee Records", "HR > HR Letters")
3. During classification, the LLM calls `get_metadata_schemas` MCP tool after determining the category
4. If a schema matches, the LLM extracts the defined fields from the document text
5. Extracted metadata is saved as a JSON key-value map on the `DocumentClassificationResult.extractedMetadata` field
6. Metadata is indexed into Elasticsearch for structured search

### Field types

| Type | Description | Example |
|---|---|---|
| TEXT | Free-form text | "John Smith" |
| KEYWORD | Enumerated/short value | "maternity", "England and Wales" |
| DATE | ISO date | "2026-06-01" |
| NUMBER | Numeric value | "42" |
| CURRENCY | Amount with currency | "£50,000" |
| BOOLEAN | True/false | "true" |

### Rules for new schemas

- Link schemas to specific taxonomy categories — the LLM only extracts fields when the schema applies
- Mark critical fields as `required` — the LLM will set "NOT_FOUND" if it cannot determine the value
- Include examples on fields to guide the LLM on expected formats
- Schemas are managed via Governance > Metadata Schemas in the admin UI

## Search Infrastructure

**Elasticsearch** provides full-text search and structured queries across documents and their extracted metadata.

- ES 8.17 runs as a Docker container alongside the existing services
- MongoDB remains the system of record; ES is the search index
- `spring-boot-starter-data-elasticsearch` is included in gls-app-assembly
- Future: ES indexing consumer on RabbitMQ will index documents on each status change

## External Storage Connectors

**Documents can be classified in-situ from external storage providers without importing them.**

### Google Drive

- OAuth2 flow: user connects via `/drives` page → authorises GLS to read their Drive
- Tokens stored in `connected_drives` collection
- File browser: navigate folders, select files, classify individually or entire folders
- Files stay in Google Drive — content is streamed temporarily for text extraction, then discarded
- `DocumentModel.storageProvider` = `"GOOGLE_DRIVE"`, with `externalStorageRef` holding fileId, driveId, ownerEmail, webViewLink
- Viewer proxies content from Google API via `/api/drives/{driveId}/content/{fileId}`
- Classification, PII detection, metadata extraction work identically to uploaded documents
- Classification panel shows Google Drive storage info (owner, account, "Open in Drive" link)

### Configuration

```
PUBLIC_URL=https://your-domain.com
GOOGLE_OAUTH_CLIENT_ID=your_client_id.apps.googleusercontent.com
GOOGLE_OAUTH_CLIENT_SECRET=your_client_secret
```

Google Drive and Google Login redirect URIs are derived automatically from `PUBLIC_URL`:
- Drive: `${PUBLIC_URL}/api/drives/google/callback`
- Login: `${PUBLIC_URL}/api/auth/public/google/callback`

Register both in Google Cloud Console → Credentials → Authorized redirect URIs.

### Future: SharePoint / OneDrive

Same `storageProvider` abstraction — implement `SharePointDriveService` behind the same interface.

## Audit Relay Pattern

**Audit events are persisted to `audit_outbox` in the same transaction as the state change, then relayed to RabbitMQ by a separate process.** This guarantees no audit drops on crashes and no duplicates downstream (per architecture §7.7 / CSV #4 / #6).

### Why an outbox

- **Crash before publish** → replay: the outbox row is still in Mongo. The relay picks it up next cycle.
- **Crash after publish but before status update** → at-least-once delivery: the downstream idempotent consumer (`gls-audit-collector`) deduplicates by `eventId`.
- **Without the outbox** → state change committed but audit not published. Compliance hole.

### How it runs

- Each service writes to `audit_outbox` via the `gls-platform-audit` shared library. The write is part of the **same Mongo transaction** as the originating state change.
- A relay component (also in `gls-platform-audit`) polls or subscribes to change streams, publishes to `audit.tier1.{eventType}` (DOMAIN) or `audit.tier2.{eventType}` (SYSTEM) per the envelope's `tier`, then marks the row `PUBLISHED`.
- Failed publishes increment `attempts` and set `nextRetryAt` (exponential backoff). After a cap, the row is marked `FAILED` and surfaced via metrics.

### Rules

- **Never bypass the outbox.** No direct AMQP publishes from service code for audit traffic. The library is the only emitter.
- **Never edit the outbox post-publish.** Published rows stay for diagnostics until cleaned by a TTL job (Phase 2 work).
- **The envelope schema is `contracts/audit/event-envelope.schema.json`.** The library validates emitted events against it; malformed envelopes are rejected at the call site, not silently dropped.
- **Idempotency:** `eventId` is unique-indexed. Idempotent re-emission of the same event is a no-op upsert.

### Indexes (managed by Mongock)

- `idx_status_nextRetry` — relay's primary query.
- `idx_eventId_unique` — uniqueness guard for idempotent re-emission.
- `idx_createdAt` — retention / cleanup.

See `V002_AuditOutboxIndexes` in `gls-app-assembly`. The `AuditOutboxRecord` POJO that maps the document shape lives in the upcoming `gls-platform-audit` shared library.

## Schema Migrations

**MongoDB schema changes are versioned, tracked, and applied automatically via Mongock at app startup** (per CSV #41 / Phase 0.10).

### Rules

- **Every schema change is a Mongock `@ChangeUnit`.** New collections, new indexes, renamed fields, backfilled values — all of these go through this. No ad-hoc shell scripts; no `@PostConstruct` migrations.
- Change units live under `co.uk.wolfnotsheep.infrastructure.migrations` in `gls-app-assembly`. Naming convention: `V<numeric-order>_<short-name>` (e.g. `V001_MongockSmoke`, `V002_AddDocumentSlugIndex`).
- Each `@ChangeUnit` declares a unique `id` and a numeric `order`. **`id` is immutable once landed** — renaming breaks the `mongockChangeLog` collection's tracking.
- Provide a `@RollbackExecution` method for any non-trivial change. No-op for additive cases (new indexes, new collections).
- Mongock's lock coordination handles multi-replica startup safely.

### How it runs

- On `gls-app-assembly` startup, Mongock scans `co.uk.wolfnotsheep.infrastructure.migrations`, compares declared change units against the `mongockChangeLog` collection, and runs any unrun units in declared order.
- **Failure halts startup loudly.** The fix-forward pattern is: write a new `@ChangeUnit` that corrects the bad state — never edit a landed unit.

### Disabling Mongock (rare)

`MONGOCK_ENABLED=false` env var skips migration on startup. Use only for hotfix scenarios where a buggy migration is already in-flight; resolve the underlying issue before re-enabling.

## Build & Run

```bash
# Local backend
cd backend && ./mvnw compile -DskipTests -pl gls-app-assembly -am

# Docker (all services including Cloudflare tunnel)
docker compose up --build -d

# Access
http://localhost          # Homepage
http://localhost/login    # Login
http://localhost/dashboard # Dashboard (auth required)
```

## Admin credentials (dev)

Seeded on first startup from `.env`:
- Email: `admin@governanceledstore.co.uk`
- Password: `ChangeMe123!`
