# Plan: Google Drive Labels + Email Classification

Two related features that extend IGC into the user's existing tools rather than asking them to upload everything into our system.

- **Feature 1 — Drive Labels:** push the IGC classification (category, sensitivity, retention) back to Google Drive as **native Drive Labels** so it shows up in the Drive UI, drives Drive's own search/filters, and survives outside IGC.
- **Feature 2 — Email Classification:** ingest emails from Gmail (and later other providers), classify the body as a document, and classify each attachment as a related document.

Both features build on the existing storage-provider abstraction, the OAuth/ConnectedDrive flow, the document ingest pipeline, and the per-pipeline-node config UI.

---

## What's already in place (no need to rebuild)

### Drive
- `ConnectedDrive` model with `accessToken`, `refreshToken`, `tokenExpiresAt`, `grantedScopes`, `hasWriteAccess()` — `igc-document/.../models/ConnectedDrive.java`
- OAuth flow + callback at `DriveController.getAuthUrl()` / `handleCallback()` — `igc-app-assembly/.../controllers/drives/DriveController.java`
- `GoogleDriveService` wraps `google-api-services-drive` v3 — list, browse folders, stream content, refresh tokens, `writeClassificationProperties()` (already writes `ig_central_*` custom file properties on terminal status)
- `GoogleDriveWriteBackService` — `@Async` consumer triggered when a Drive doc reaches GOVERNANCE_APPLIED / REVIEW_REQUIRED / etc. Logs to `SystemErrorRepository` on failure.
- `DocumentModel.storageProvider="GOOGLE_DRIVE"` + `externalStorageRef={driveId, fileId, ownerEmail, webViewLink}`
- `GovernanceDataSeeder.seedNodeTypeDefinitions()` registers all node types — adding new ones is a known pattern.

### Email (greenfield)
- `spring-boot-starter-mail` already on classpath — but it's **outbound SMTP only**. There is no IMAP code, no Gmail API client, no mail-ingest model. All net-new.

### Pipeline / ingest
- `DocumentService.ingest(MultipartFile, uploadedBy, organisationId)` is the canonical entry point — it stores to MinIO, creates the `DocumentModel`, publishes `DocumentIngestedEvent` to RabbitMQ, and the existing pipeline picks it up.

---

## Feature 1 — Google Drive Labels (write-back upgrade)

### What changes vs today

Today the write-back uses Drive **file properties** (`properties` field on the file). Properties are visible only via the Drive API — users in the Drive UI see nothing. The proper "show classification in Drive UI" feature is **Drive Labels** (different API, different OAuth scope, requires admin-managed label definitions in the Google Workspace).

We keep the existing properties write-back as a fallback (works for any Google account, no admin needed). The new code adds **Drive Labels** as the preferred path when the workspace admin has provisioned a matching label.

### Drive Labels primer

- A **label** is a Workspace-admin-defined schema (e.g. "IGC Classification") with **fields** (e.g. `category`, `sensitivity`, `retention_until`).
- Labels live in the Workspace, not on individual files — admins create them once via Google's Label Manager.
- The Drive Labels API lets you **apply** an instance of a label to a file with values for each field.
- Required scope: `https://www.googleapis.com/auth/drive.labels` (read) + `https://www.googleapis.com/auth/drive` (apply via Drive API `modifyLabels`).
- Labels surface in the Drive UI in the right-hand details pane and as filters in Drive search.

### Step-by-step plan

#### Step 1 — Add Drive Labels API client
- Add to `igc-app-assembly/pom.xml`: `google-api-services-drivelabels` (v2)
- No new transitive auth — uses the same `Credential` we already build.

#### Step 2 — Extend `ConnectedDrive` for label config
- Add `defaultLabelId` (String) and `fieldMappings` (Map<String,String>) to `ConnectedDrive` — maps IGC classification keys (`category`, `sensitivity`, `retention_until`, `vital_record`, `legal_hold`) to the Workspace's label field IDs.
- Optional: a discovery endpoint `/api/drives/{driveId}/labels` that lists available Workspace labels so the admin can pick one in the UI.
- Re-prompt for OAuth consent if the new `drive.labels` scope isn't in `grantedScopes`.

#### Step 3 — Extend `GoogleDriveService`
- New method `writeClassificationLabel(ConnectedDrive drive, String fileId, String labelId, Map<String,String> fieldValues)` — calls `Drive.files().modifyLabels()` with a `ModifyLabelsRequest` containing one `LabelModification`.
- New method `listAvailableLabels(ConnectedDrive drive)` — calls the Labels API for selection in the UI.
- Keep `writeClassificationProperties()` as the unconditional fallback so the data is always recoverable even when no label is configured.

#### Step 4 — Upgrade `GoogleDriveWriteBackService.writeBackIfNeeded()`
- After the existing properties write, **also** call `writeClassificationLabel()` if `drive.defaultLabelId` is set.
- Build `fieldValues` from the document: category name, sensitivity, computed `retentionTriggerEventDate` + `retentionPeriodText`, vital-record flag, legal-hold flag.
- Errors logged to `SystemErrorRepository` (already wired). One failure does not block the other path.

#### Step 5 — Pipeline node: `writeDriveLabel`
- Register a new ACTION node type in `GovernanceDataSeeder.seedNodeTypeDefinitions()`.
- `configSchema` properties:
  - `labelId` (select, populated from the connected drive's available labels)
  - `mode` (enum: `properties` | `label` | `both`, default `both`)
  - `onError` (enum: `continue` | `fail`, default `continue`)
- Lets pipeline authors place this explicitly after `governance` instead of the implicit post-enforcement hook — useful when only certain branches should write back.
- Existing implicit hook stays in place for non-pipeline-driven docs (legacy upload path).

#### Step 6 — UI: `/drives/[driveId]` settings panel
- New "Drive Labels" section showing:
  - Current `defaultLabelId` + label name (or "Not configured — using file properties only")
  - Button: **"Choose a Workspace label"** → modal lists labels from `listAvailableLabels`, lets admin pick one
  - Field-mapping table — for each IGC field (category, sensitivity, retention_until, …) show a dropdown of label fields from the chosen label
- Also surface in the document detail page: when a Drive-stored doc has been label-written, show "Label applied: ✓ {labelName}" with a link to open in Drive.

#### Step 7 — Backfill for already-classified docs
- One-off admin endpoint `POST /api/admin/drives/{driveId}/relabel` — finds all `GOOGLE_DRIVE` docs in terminal status linked to that drive and re-runs `writeBackIfNeeded`.
- Idempotent — Drive Labels API replaces existing field values on a label modification.

### Out of scope (defer)
- Multi-label support per file (one label per file is enough for v1)
- Sharing-level enforcement based on labels (Google has DLP for this; we just emit the metadata)
- Reading user-applied label changes back into IGC (one-way push only for v1)

---

## Feature 2 — Email + Attachment Classification

### Mental model

An email is a "document with attachments". We treat the **email itself** as a `DocumentModel` (mime type `message/rfc822`, body text used for extraction) and **each attachment** as its own `DocumentModel`. They are linked via a parent/child relationship so the user can see "this PDF was attached to this email."

This means **no new pipeline** is needed — the existing classification pipeline runs on each document. The only new pieces are: the ingest source (Gmail), the parent/child link, and a UI affordance for viewing email + attachments together.

### Step-by-step plan

#### Step 1 — Add Gmail API client
- `igc-app-assembly/pom.xml`: `google-api-services-gmail` (v1, latest revision).
- Extend the existing Google OAuth flow to optionally request the `https://www.googleapis.com/auth/gmail.readonly` scope (or `gmail.modify` if we later want to apply Gmail labels back).

#### Step 2 — New storage provider: `GMAIL`
- `DocumentModel.storageProvider = "GMAIL"`
- `externalStorageRef = {messageId, threadId, accountEmail, snippet, gmailUrl}`
- `ConnectedDrive` is the wrong name for a mailbox — but it's already a generic "connected external account" model. Either:
  - **Option A (recommended):** rename to `ConnectedAccount` with a `provider` field that distinguishes `GOOGLE_DRIVE` vs `GMAIL` vs future providers. Backwards-compatible read path.
  - **Option B:** new `ConnectedMailbox` model. More duplication, less abstraction win.

#### Step 3 — New parent/child link on `DocumentModel`
- `parentDocumentId` (String, indexed, sparse) — the email document each attachment belongs to.
- `childDocumentIds` (List<String>) — denormalised on the parent for quick lookup.
- On the email document, the "view" page shows a child list; on each attachment, it shows a "From email: …" backlink.

#### Step 4 — `GmailService` (mirrors `GoogleDriveService`)
- `listMessages(account, query, pageToken)` — paginated, accepts a Gmail search query (`from:`, `subject:`, `has:attachment`, `after:`, label IDs, etc.)
- `getMessage(account, messageId)` — full message with payload tree
- `extractBody(message)` — walk the MIME parts, prefer `text/plain` then strip `text/html`. Handles base64url decoding.
- `extractAttachments(message)` — returns `List<EmailAttachment{filename, mimeType, sizeBytes, attachmentId, partId}>`
- `downloadAttachment(account, messageId, attachmentId)` — streams the raw bytes.
- Watch token refresh — same pattern as `GoogleDriveService.refreshTokenIfNeeded`.

#### Step 5 — `EmailIngestionService`
- `ingestMessage(ConnectedAccount account, String messageId, String uploadedBy, String organisationId)`:
  1. Fetch full message via `GmailService.getMessage`
  2. Build the email's text body (headers + plain body)
  3. Call `DocumentService.ingest()` with the body bytes and synthesised `from-subject.eml` filename, mime `message/rfc822`. Sets `storageProvider="GMAIL"`, `externalStorageRef` with `{messageId, threadId, accountEmail, ...}`.
  4. For each attachment, download bytes via `GmailService.downloadAttachment` and call `DocumentService.ingest()` again — set `parentDocumentId` to the email doc's id, also set `storageProvider="GMAIL"` with `externalStorageRef.attachmentId`.
  5. Update the email doc's `childDocumentIds` after all attachments are saved.
- Each ingest publishes `DocumentIngestedEvent` — the existing pipeline classifies them automatically.

#### Step 6 — Three ingest triggers (different node types / endpoints)

**a) Manual import (UI):**
- New page `/mailboxes` mirrors `/drives` — connect a Gmail account, browse messages with filters, click "Import" on a thread or message.
- Backend: `POST /api/mailboxes/{accountId}/messages/import` body `{ messageIds: [...] }`

**b) Scheduled poller (TRIGGER node):**
- New pipeline node `gmailWatcher` (TRIGGER category) with config:
  - `accountId` (select from connected accounts)
  - `query` (string — Gmail search syntax, e.g. `label:inbox has:attachment newer_than:1d`)
  - `pollIntervalMinutes` (int, default 15)
  - `markRead` (bool — apply the Gmail label `READ` after import? optional)
- A `GmailPollingScheduler` uses `@Scheduled` reading the active node configs and calls `EmailIngestionService` for each new message id (tracked per-account in a small `gmail_ingest_cursor` collection by `historyId`).

**c) Push (Pub/Sub watch):**
- Out of scope for v1 — Gmail's `watch()` API requires a Google Pub/Sub subscription and a publicly-reachable webhook. Add later.

#### Step 7 — UI: email viewer
- Document detail page detects `mimeType="message/rfc822"` → renders an "Email" view: From / To / Subject / Date / body / **Attachments** list (each is a clickable child doc with its own classification).
- Each attachment doc detail shows "Attached to: {email subject}" linking back to the parent.
- Search results group an email + its attachments under one collapsible row when they all match.

#### Step 8 — Per-attachment overrides via traits
- Reuse the existing `TraitDefinition` system: seed two new traits — `EMAIL_BODY` and `EMAIL_ATTACHMENT`.
- `EmailIngestionService` tags each new doc with one of these traits.
- The classification prompt already injects traits — the LLM gets context that "this is an email body" vs "this is an attachment from an email about X".
- Pipelines can branch on these traits (existing `condition` node).

#### Step 9 — Validation + edge cases
- **Encrypted / signed emails (S/MIME)** — store as `message/rfc822` but skip body extraction; flag as `PROCESSING_FAILED` with reason "encrypted email".
- **Inline images / nested forwarded messages** — extract recursively; use `Content-ID` to link inline images back to the body. v1 can flatten and treat inline images as attachments.
- **Calendar invites (`text/calendar`)** — treat as an attachment, classify normally; future feature could parse them as structured events.
- **Massive threads** — ingest message-by-message, not thread-as-blob. Each message is its own doc.
- **De-dup** — Gmail `messageId` is unique per account; refuse to ingest the same `messageId` twice (uniqueness check on `externalStorageRef.messageId` for that account).

### Out of scope (defer)
- Outlook / Microsoft 365 (same pattern with Graph API — implement after Gmail proves the model)
- IMAP for arbitrary mailboxes (the pattern works but auth is per-account and ugly)
- Writing back to Gmail (apply a "Classified" Gmail label, move to a folder) — easy follow-up once `gmail.modify` scope is in
- Auto-reply / quarantine workflows when classification flags PII

---

## Required env / config additions

```
# Gmail OAuth — same client ID is fine, just add the scope
GOOGLE_GMAIL_SCOPES=https://www.googleapis.com/auth/gmail.readonly

# Drive Labels — no new env, but admins must provision the label in Workspace first
```

OAuth callback paths (`/api/drives/google/callback`, `/api/auth/public/google/callback`) stay the same. We re-use the Drive consent flow but add `gmail.readonly` and `drive.labels` to the requested scope list — Google will re-prompt the user for consent on the new scopes.

---

## Files to create

| File | Purpose |
|---|---|
| `igc-app-assembly/.../services/drives/GoogleDriveLabelsService.java` | Drive Labels API wrapper (list + apply) |
| `igc-app-assembly/.../controllers/admin/DriveLabelConfigController.java` | List labels, save defaultLabelId + fieldMappings |
| `web/src/app/(protected)/drives/[id]/labels/page.tsx` | Drive Labels config UI |
| `igc-document/.../models/ConnectedAccount.java` (rename of ConnectedDrive) | Generic connected account, provider field |
| `igc-document/.../models/EmailAttachment.java` | Record describing an email attachment for ingest |
| `igc-app-assembly/.../services/mail/GmailService.java` | Gmail API wrapper |
| `igc-app-assembly/.../services/mail/EmailIngestionService.java` | Ingest email + attachments as documents |
| `igc-app-assembly/.../services/mail/GmailPollingScheduler.java` | `@Scheduled` poller driven by `gmailWatcher` node configs |
| `igc-app-assembly/.../controllers/mailboxes/MailboxController.java` | OAuth + browse + manual import endpoints |
| `web/src/app/(protected)/mailboxes/page.tsx` | Mailbox list + browse + import UI |
| `web/src/components/document-viewer/EmailView.tsx` | Email-specific document viewer |

## Files to modify

| File | Change |
|---|---|
| `igc-app-assembly/pom.xml` | Add `google-api-services-drivelabels`, `google-api-services-gmail` |
| `igc-document/.../models/DocumentModel.java` | Add `parentDocumentId`, `childDocumentIds` |
| `igc-document/.../models/ConnectedDrive.java` | Add `defaultLabelId`, `fieldMappings`; or rename to ConnectedAccount |
| `igc-app-assembly/.../services/drives/GoogleDriveService.java` | Use new labels service alongside properties |
| `igc-app-assembly/.../services/drives/GoogleDriveWriteBackService.java` | Call labels write after properties write |
| `igc-app-assembly/.../bootstrap/GovernanceDataSeeder.java` | Register `writeDriveLabel` + `gmailWatcher` node types; seed `EMAIL_BODY` / `EMAIL_ATTACHMENT` traits |
| `igc-app-assembly/.../controllers/drives/DriveController.java` | Add `drive.labels` scope to OAuth request when admin enables it |
| `web/src/app/(protected)/help/help-content.ts` | Document the two features |

---

## Suggested order of implementation

Both features share the OAuth + Google API client foundation, but neither blocks the other. Recommended sequence:

1. **Drive Labels** first — small, high-value, builds on existing write-back. ~2 days of work.
2. **Gmail OAuth + manual import** — gets a working email viewer end-to-end without scheduling complexity. ~3 days.
3. **Email attachments + parent/child link + UI viewer** — the bit users will actually see. ~2 days.
4. **Scheduled `gmailWatcher` node** + cursor tracking. ~2 days.
5. **`ConnectedDrive` → `ConnectedAccount` rename** as a separate refactor PR once the second provider is real, not before.

---

## Verification

**Drive Labels:**
1. Workspace admin creates a "IGC Classification" label with fields `category`, `sensitivity`, `retention_until` in Google Admin → Drive → Labels.
2. Connect drive in IGC, configure label mapping in `/drives/[id]/labels`.
3. Classify a Drive doc → open in Drive → label appears in right-hand pane with correct values.
4. Override classification in review queue → re-run write-back → label fields update.
5. Drive search `labels:IGCClassification.sensitivity = "RESTRICTED"` returns the file.

**Email:**
1. Connect Gmail in `/mailboxes`.
2. Browse messages, click "Import" on a thread with attachments.
3. Email + each attachment appear in `/documents`.
4. Open the email doc → see attachment list, click attachment → open its classification.
5. Configure a `gmailWatcher` pipeline node with `query: label:inbox has:attachment newer_than:1d` → wait for poll interval → new messages auto-ingested.
6. Search for a term that appears in both email body and an attachment — both surface, grouped under the email parent.
