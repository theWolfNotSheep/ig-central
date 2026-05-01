# Changelog — 2026-04-19

Summary of all changes made during this session. Two new features (Drive Labels + Email Classification), a production incident investigation and fix, and performance tuning.

---

## Feature 1: Google Drive Labels

**Purpose:** Push classification metadata (category, sensitivity, retention) back to Google Drive as native Drive Labels that surface in the Drive UI, search, and filters — instead of invisible file properties.

### Files created
| File | Purpose |
|------|---------|
| `igc-app-assembly/.../services/drives/GoogleDriveLabelsService.java` | Lists Workspace labels via DriveLabels API, applies labels to files via Drive API `modifyLabels()` |
| `igc-app-assembly/.../controllers/admin/DriveLabelConfigController.java` | Admin endpoints: list labels, save/clear config, backfill existing docs |
| `web/src/app/(protected)/drives/[id]/labels/page.tsx` | Label configuration UI: select label, map fields, save/remove |

### Files modified
| File | Change |
|------|--------|
| `igc-app-assembly/pom.xml` | Added `google-api-services-drivelabels` v2-rev20250602-2.0.0 |
| `igc-document/.../models/ConnectedDrive.java` | Added `defaultLabelId`, `defaultLabelName`, `fieldMappings`, `needsLabelScope()` |
| `igc-app-assembly/.../services/drives/GoogleDriveService.java` | Added `drive.labels` scope to OAuth, made credential methods public |
| `igc-app-assembly/.../services/drives/GoogleDriveWriteBackService.java` | Injected `GoogleDriveLabelsService`, writes label after properties (independent try/catch) |
| `igc-app-assembly/.../bootstrap/GovernanceDataSeeder.java` | Registered `writeDriveLabel` pipeline node type |
| `web/src/app/(protected)/drives/page.tsx` | Added label status bar, "Configure Labels" link, label/scope indicators |

### How it works
1. Workspace admin creates a label in Google Admin → Drive → Labels
2. IG Central admin configures the label mapping at `/drives/{id}/labels`
3. On classification, `GoogleDriveWriteBackService` writes properties (always) + label (if configured)
4. Label appears in Drive UI right-hand pane and is searchable via Drive search
5. Backfill endpoint re-labels already-classified docs

---

## Feature 2: Email Classification (Gmail)

**Purpose:** Ingest emails from Gmail, classify the body as a document, and classify each attachment as a linked child document.

### Files created
| File | Purpose |
|------|---------|
| `igc-app-assembly/.../services/mail/GmailService.java` | Gmail API wrapper: list messages, get message, extract body/attachments, download |
| `igc-app-assembly/.../services/mail/EmailIngestionService.java` | Ingests email + attachments as linked DocumentModels, publishes to pipeline |
| `igc-app-assembly/.../controllers/mailboxes/MailboxController.java` | OAuth, browse, import, disconnect endpoints |
| `igc-app-assembly/.../services/mail/GmailPollingScheduler.java` | `@Scheduled` poller for `gmailWatcher` pipeline nodes |
| `igc-document/.../models/GmailIngestCursor.java` | Tracks polling position per account+query |
| `igc-document/.../repositories/GmailIngestCursorRepository.java` | MongoDB repository for cursor |
| `web/src/app/(protected)/mailboxes/page.tsx` | Gmail browser: connect, search, select, import, attachments filter |
| `web/src/components/email-view.tsx` | Email header display, attachment list, parent backlink |

### Files modified
| File | Change |
|------|--------|
| `igc-app-assembly/pom.xml` | Added `google-api-services-gmail` v1-rev20250331-2.0.0 |
| `igc-document/.../models/StorageProviderType.java` | Added `GMAIL` |
| `igc-document/.../models/DocumentModel.java` | Added `parentDocumentId`, `childDocumentIds` |
| `igc-document/.../repositories/DocumentRepository.java` | Added `findByParentDocumentId()` |
| `igc-document/.../repositories/ConnectedDriveRepository.java` | Added `findByUserIdAndProviderAndProviderAccountEmail()`, `findAccessibleByProvider()` |
| `igc-platform/.../configs/DefaultSecurityConfig.java` | Added CSRF exclusion for `/api/mailboxes/gmail/callback` |
| `igc-app-assembly/.../controllers/drives/DriveController.java` | OAuth callback now detects `GMAIL:` prefix in state, creates Gmail account correctly |
| `igc-app-assembly/.../bootstrap/GovernanceDataSeeder.java` | Seeded `gmailWatcher` node type, `EMAIL_BODY`/`EMAIL_ATTACHMENT` traits |
| `web/src/components/sidebar.tsx` | Added "Mailboxes" nav item with Mail icon |
| `web/src/app/(protected)/documents/page.tsx` | Integrated `EmailView` component for email/attachment docs |

### Email model
- Email body → `DocumentModel` with `mimeType=message/rfc822`, `storageProvider=GMAIL`, `traits=[EMAIL_BODY]`
- Each attachment → child `DocumentModel` with `parentDocumentId` linking back, `traits=[EMAIL_ATTACHMENT]`
- De-duplication by Gmail `messageId` per account
- Encrypted emails (S/MIME) detected and marked `PROCESSING_FAILED`

### Gmail Watcher (automated polling)
- New `gmailWatcher` pipeline TRIGGER node type
- Configurable: account, Gmail search query, poll interval (5-1440 min)
- `GmailPollingScheduler` checks every 60s, respects per-watcher poll interval
- Cursor tracking via `gmail_ingest_cursors` collection

---

## Incident: Runaway Queue Storm + Performance Fixes

### Problem discovered
- 35,515 messages stuck in `igc.documents.processed` queue
- 439 documents stuck at PROCESSED, 0 classified per hour
- 513 Spring AI retries against Ollama, all timing out
- 20 documents stuck at CLASSIFIED (governance never applied)

### Root cause
`StaleDocumentRecoveryTask` re-queued 439 documents every 5 minutes without tracking that they'd already been re-queued. Combined with unlimited consumer prefetch (250), this overwhelmed Ollama with concurrent requests, causing a timeout cascade.

### Fixes applied

#### 1. Re-queue storm prevention
**File:** `igc-app-assembly/.../services/StaleDocumentRecoveryTask.java`
- Added 30-minute cooldown between re-queue attempts (touches `updatedAt`)
- Added retry cap of 3 per document
- Documents stop being auto-re-queued after 3 failures

#### 2. Consumer prefetch limits
**Files:** `igc-app-assembly/.../config/RabbitMqConfig.java`, `igc-llm-orchestration/.../config/RabbitMqConfig.java`
- App-assembly prefetch: 5 (was unlimited/250)
- LLM worker prefetch: 2 (was unlimited/250)

#### 3. LLM Circuit Breaker
**File:** `igc-llm-orchestration/.../pipeline/ClassificationPipeline.java`
- Opens after 5 consecutive LLM failures
- Pauses classification for 2-minute cooldown
- Half-open probe: allows one request after cooldown to test recovery
- Logs CRITICAL SystemError when circuit opens
- Status exposed via `GET /api/internal/circuit-breaker`

**File:** `igc-llm-orchestration/.../api/ClassificationController.java`
- Added `/api/internal/circuit-breaker` endpoint

#### 4. Queue TTL
- 1-hour TTL policy on `igc.documents.processed` via RabbitMQ policy
- Expired messages route to dead-letter queue
- Prevents unbounded queue growth during outages
- Policy persists in RabbitMQ data volume

#### 5. Monitoring: Circuit Breaker Visibility
**Files:** `igc-app-assembly/.../services/MonitoringService.java`, `MonitoringController.java`
- Added `circuitBreaker` field to pipeline metrics (fetched from LLM worker internal API)
- Added `llmWorkerBaseUrl` config (`llm.service-url`)

**File:** `web/src/app/(protected)/monitoring/page.tsx`
- Circuit breaker status card above queue depths
- Green dot = CLOSED (healthy), red pulsing dot = OPEN (paused)
- Shows consecutive failure count and cooldown info

#### 6. Fixed stuck CLASSIFIED documents
- 20 docs had null `pipelineNodeId`, preventing Phase 2 execution
- Moved to INBOX (correct terminal state)

---

## Ollama Performance Tuning

**Hardware:** Mac Studio M3 Ultra, 96GB RAM

### Changes applied
| Setting | Before | After | Location |
|---------|--------|-------|----------|
| Ollama version | 0.20.2 | 0.21.0 | `brew upgrade ollama` |
| `OLLAMA_KEEP_ALIVE` | 5min (default) | -1 (forever) | LaunchAgent plist |
| `OLLAMA_NUM_PARALLEL` | 1 (default) | 2 | LaunchAgent plist |
| `OLLAMA_FLASH_ATTENTION` | off | 1 | LaunchAgent plist |
| `OLLAMA_KV_CACHE_TYPE` | f16 (default) | q8_0 | LaunchAgent plist |

### Results
| Metric | Before | After |
|--------|--------|-------|
| Cold start | 30-50s after 5min idle | Never (always loaded) |
| Prefill speed | 109 tok/s | 130 tok/s |
| 2 docs parallel | N/A (serial) | 98s (vs 184s sequential) |
| Effective throughput | ~2 docs/min | ~4 docs/min |
| Classified/hour (measured) | 0 (during incident) → 15 (after fix) | Stable |

### Config location
`~/Library/LaunchAgents/homebrew.mxcl.ollama.plist` — environment variables in the `EnvironmentVariables` dict. Persists across reboots. Restart with `brew services restart ollama`.

---

## Pipeline Throttling

**Purpose:** Prevent overwhelming the LLM by limiting how many documents can be in-flight at once. Admin-configurable in Settings.

### Files created
| File | Purpose |
|------|---------|
| `igc-app-assembly/.../services/PipelineThrottleService.java` | Checks in-flight count against configurable limit, returns 429 when at capacity |

### Files modified
| File | Change |
|------|--------|
| `igc-app-assembly/.../controllers/documents/DocumentController.java` | Throttle check before upload |
| `igc-app-assembly/.../controllers/drives/DriveController.java` | Throttle check before Drive classify |
| `igc-app-assembly/.../controllers/mailboxes/MailboxController.java` | Throttle check before Gmail import |
| `igc-app-assembly/.../controllers/admin/MonitoringController.java` | Added `GET /pipeline/throttle` status endpoint |
| `igc-app-assembly/.../bootstrap/GovernanceDataSeeder.java` | Seeds `pipeline.throttle.max_in_flight` (50) and `pipeline.throttle.max_batch_size` (20) |
| `web/src/app/(protected)/settings/page.tsx` | Added "Pipeline Throttling" section with both values editable |
| `web/src/app/(protected)/drives/page.tsx` | 429 error handling with user-friendly toast |
| `web/src/app/(protected)/mailboxes/page.tsx` | 429 error handling with user-friendly toast |

### Configuration
- `pipeline.throttle.max_in_flight` = 50 — max documents in PROCESSING + CLASSIFYING + PROCESSED
- `pipeline.throttle.max_batch_size` = 20 — max docs per single operation
- Editable at runtime in **Settings > Pipeline Throttling**
- Takes effect immediately (no restart needed)

---

## Documentation & Help

### Files created
| File | Purpose |
|------|---------|
| `documentation/PERFORMANCE-SCALING-CHALLENGES.md` | Full incident log, root cause analysis, scaling characteristics, current safeguards, remaining risks |
| `documentation/DRIVE-LABELS-AND-EMAIL-CLASSIFICATION-PLAN.md` | Original plan document (pre-existing, reviewed and implemented) |

### Help content updated
| Section | Changes |
|---------|---------|
| `/help/drives` | Added Drive Labels setup instructions |
| `/help/mailboxes` (new) | Gmail connection, importing, automated watching |
| `/help/google-cloud-setup` (new) | Full Google Cloud Console setup: APIs, OAuth consent, credentials, troubleshooting |
| `/help/monitoring` | Added circuit breaker documentation, queue TTL, overflow protection |
| `/help/ai-providers` | Added Mac Studio performance tuning guide with benchmarks |

### Icon updates
- `web/src/app/(protected)/help/page.tsx` — Added `Cloud`, `Mail` to icon map
- `web/src/app/(protected)/help/[slug]/page.tsx` — Same

---

## TODO: Planned Performance Features (not yet implemented)

Discussed and designed but not built in this session:

1. **Infrastructure Profiles** — "Small/Medium/Large/Cloud/Custom" dropdown in Settings that auto-tunes max_in_flight, batch_size, prefetch, timeout in one click
2. **Multi-provider Registry** — Register multiple LLM endpoints (primary, overflow, fallback) with automatic routing based on queue depth and circuit breaker state
3. **BERT Fast Path** — Skip LLM entirely when BERT confidence > 0.95, apply governance directly
4. **Priority Queues** — Separate high-priority (user upload) from low-priority (bulk/watcher) processing
5. **Taxonomy Caching** — Cache MCP tool responses in LLM worker with configurable TTL

See conversation notes for detailed design of each.

---

## Build verification

All changes compile and deploy successfully:
- Backend: `./mvnw compile -DskipTests -pl igc-app-assembly -am` — BUILD SUCCESS
- Frontend: `npm run build` — all pages build, no TypeScript errors
- Docker: `docker compose up --build -d` — all containers healthy
- Queues: all clean, circuit breaker CLOSED, model loaded Forever
