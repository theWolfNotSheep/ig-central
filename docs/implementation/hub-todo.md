# IG Hub — TODO

The IG Hub is the central governance marketplace — a standalone service for building, publishing, and distributing governance models (taxonomies, retention schedules, metadata schemas, PII patterns, policies, etc.) to IG Central tenant instances.

This document tracks what's needed to make the Hub a fully standalone, deployable service and to complete the integration with IG Central.

---

## 1. Standalone Hub Deployment

The Hub must run independently — its own Docker Compose, its own database, its own frontend, its own Cloudflare tunnel. No dependency on the IG Central monorepo at runtime.

### 1.1 Standalone Docker Compose

**Status:** Extraction guide exists (`backend/GOVERNANCE-HUB-EXTRACTION.md`) with a skeleton docker-compose, but it's incomplete.

**What's needed:**

Create `governance-hub/docker-compose.yml` with:

- [ ] **hub-mongo** — Dedicated MongoDB 7 instance for `governance_hub` database. Volume-backed, health-checked.
- [ ] **governance-hub** — Spring Boot service (port 8090). Depends on hub-mongo. Env vars: `SPRING_DATA_MONGODB_URI`, `HUB_ADMIN_USERNAME`, `HUB_ADMIN_PASSWORD`, `SERVER_PORT`.
- [ ] **web-hub** — Next.js admin UI (port 3001). Depends on governance-hub. Build arg: `NEXT_PUBLIC_HUB_URL`. Must point to the hub API via the nginx proxy, not directly.
- [ ] **nginx** — Reverse proxy fronting both the API and the admin frontend:
  - `/api/hub/*` → `governance-hub:8090`
  - `/` (everything else) → `web-hub:3001`
  - Client max body size, resolver, forwarded headers — match the IG Central nginx pattern.
- [ ] **cloudflared** — Cloudflare tunnel service. Requires `HUB_TUNNEL_TOKEN` in `.env`. Points to `nginx:80`. Depends on nginx health.
- [ ] **Network** — Single bridge network (`hub`), all services attached.
- [ ] **`.env.example`** — Document all required env vars:
  ```
  HUB_MONGO_PASSWORD=
  HUB_ADMIN_USERNAME=admin
  HUB_ADMIN_PASSWORD=
  HUB_TUNNEL_TOKEN=
  HUB_PUBLIC_URL=https://hub.igcentral.com
  ```

### 1.2 Standalone Docker Build

**Status:** `backend/Dockerfile.hub` exists and works within the monorepo. Needs adaptation for standalone context.

- [ ] Copy `Dockerfile.hub` into standalone repo root, update build context paths (no longer nested under `backend/`).
- [ ] Create standalone parent POM per extraction guide (Spring Boot 4.0.2 parent, Java 21).
- [ ] Update child POM parent references in `gls-governance-hub/pom.xml` and `gls-governance-hub-app/pom.xml`.
- [ ] Copy `.mvn/`, `mvnw`, `mvnw.cmd` into standalone repo.
- [ ] Create `web-hub/Dockerfile` — already exists, verify it works standalone (no monorepo context dependencies).

### 1.3 Nginx Configuration for Hub

- [ ] Create `nginx/default.conf` for the standalone hub:
  ```nginx
  server {
    listen 80;
    server_name _;
    resolver 127.0.0.11 valid=30s ipv6=off;

    set $upstream_api http://governance-hub:8090;
    set $upstream_web http://web-hub:3001;

    location /api/ {
      proxy_pass $upstream_api;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-Proto $scheme;
      proxy_set_header X-Forwarded-Host $host;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_http_version 1.1;
    }

    location /actuator/ {
      proxy_pass $upstream_api;
      # ... same headers
    }

    location / {
      proxy_pass $upstream_web;
      # ... same headers + websocket upgrade
    }
  }
  ```

### 1.4 Cloudflare Tunnel

- [ ] Add `cloudflared` service to the Hub's docker-compose, matching IG Central's pattern:
  ```yaml
  cloudflared:
    image: cloudflare/cloudflared:latest
    command: tunnel --no-autoupdate run --token ${HUB_TUNNEL_TOKEN}
    depends_on:
      nginx:
        condition: service_healthy
  ```
- [ ] Create a Cloudflare tunnel in the dashboard pointing to `nginx:80`.
- [ ] Document the tunnel setup in the Hub repo README.

### 1.5 Web-Hub Frontend Updates for Standalone

- [ ] `NEXT_PUBLIC_HUB_URL` should default to relative `/api` when behind nginx (not `http://localhost:8090`).
- [ ] Update `web-hub/lib/api.ts` to work with relative URLs when proxied.
- [ ] Add a public-facing browse page (not just admin) — tenants should be able to browse the hub marketplace without admin credentials.

---

## 2. Governance Model — Making It Joined Up

The current Hub packs contain flat, disconnected component lists. In reality, a governance model is a **joined-up graph**: taxonomy categories link to retention schedules, which cite legislation; metadata schemas attach to categories; sensitivity levels drive storage tier selection. The Hub must model and distribute these relationships, not just the individual parts.

### 2.1 Legislation / Regulatory Basis as a First-Class Entity

**Current state:** Regulations are free-text strings on `RetentionSchedule.regulatoryBasis` and `GovernancePack.regulations[]`. No structured model.

**What's needed:**

- [ ] Create `Legislation` model (in `gls-governance-hub` and `gls-governance`):
  ```
  id, key (unique, e.g. "GDPR", "DPA_2018", "COMPANIES_ACT_2006")
  name ("General Data Protection Regulation")
  shortName ("GDPR")
  jurisdiction ("UK", "EU", "US")
  url (link to legislation text)
  relevantArticles[] (e.g. "Article 17 — Right to Erasure", "Section 33 — Research")
  description
  active
  ```
- [ ] Link `RetentionSchedule` to `Legislation[]` by ID (replace free-text `regulatoryBasis` with `legislationIds[]` + optional `legislationNotes` for specifics like "Section 386, Companies Act 2006").
- [ ] Link `GovernancePolicy` to `Legislation[]` by ID — policies exist because of laws.
- [ ] Link `SensitivityDefinition` to `Legislation[]` — e.g. RESTRICTED exists because of GDPR/DPA.
- [ ] Include `LEGISLATION` as a new `PackComponent.ComponentType` in Hub packs.
- [ ] Seed legislation data in `HubDataSeeder` for the UK packs (GDPR, DPA 2018, Companies Act 2006, Limitation Act 1980, HMRC regulations, FCA SYSC 9, NHS Records Management Code, Caldicott Principles, Health and Social Care Act 2012).

### 2.2 Metadata Schemas Linked to Taxonomy Categories

**Current state:** `ClassificationCategory` has a `metadataSchemaId` field, but Hub packs distribute taxonomy categories and metadata schemas as separate, unlinked components. On import, there's no way to know which schema goes with which category.

**What's needed:**

- [ ] Add `metadataSchemaRef` to taxonomy category data within Hub packs — a reference by name or key so that on import, the schema can be resolved and linked.
- [ ] The Hub admin UI should allow linking schemas to categories when building a pack.
- [ ] On import, the `PackImportService` must:
  1. Import metadata schemas first.
  2. Import taxonomy categories second.
  3. Resolve `metadataSchemaRef` → look up the imported schema's ID → set `metadataSchemaId` on the category.

### 2.3 Retention Schedules Linked to Taxonomy Categories

**Current state:** `ClassificationCategory` has `retentionScheduleId`, but Hub packs distribute them separately with no cross-reference.

**What's needed:**

- [ ] Add `retentionScheduleRef` to taxonomy category data in Hub packs.
- [ ] On import, resolve retention schedule references → set `retentionScheduleId` on categories.
- [ ] The Hub admin UI should allow linking retention schedules to categories when editing a pack.

### 2.4 Sensitivity Levels Linked to Taxonomy Categories + Storage Tiers

**Current state:** `ClassificationCategory.defaultSensitivity` is a string/enum. `StorageTier.allowedSensitivities` is a list. But these aren't cross-referenced in Hub packs.

**What's needed:**

- [ ] Taxonomy categories in Hub packs should include `defaultSensitivity` as a key reference (already partially there).
- [ ] Storage tiers in Hub packs should reference sensitivity definition keys (already partially there via `allowedSensitivities`).
- [ ] On import, validate that all referenced sensitivity keys exist locally before linking.

### 2.5 Governance Policies Linked to Categories + Legislation

**Current state:** `GovernancePolicy` has `applicableCategoryIds[]` and `applicableSensitivities[]`, but Hub packs don't link policies to specific categories by reference.

**What's needed:**

- [ ] Policies in Hub packs should include `applicableCategoryRefs[]` (by name/key) and `legislationRefs[]`.
- [ ] On import, resolve category and legislation references → set IDs.

### 2.6 Pack Data Structure — Joined-Up Format

Currently each `PackComponent.data` is a flat `List<Map<String, Object>>` with no cross-references. The pack format needs to evolve:

- [ ] Define a `PackManifest` that describes the relationships between components:
  ```json
  {
    "relationships": [
      {
        "from": { "type": "TAXONOMY_CATEGORIES", "ref": "HR > Employee Records" },
        "to": { "type": "RETENTION_SCHEDULES", "ref": "Standard Business Records" },
        "field": "retentionScheduleRef"
      },
      {
        "from": { "type": "TAXONOMY_CATEGORIES", "ref": "HR > Employee Records" },
        "to": { "type": "METADATA_SCHEMAS", "ref": "Employee Record Schema" },
        "field": "metadataSchemaRef"
      },
      {
        "from": { "type": "RETENTION_SCHEDULES", "ref": "Financial Statutory" },
        "to": { "type": "LEGISLATION", "ref": "COMPANIES_ACT_2006" },
        "field": "legislationRef"
      }
    ]
  }
  ```
- [ ] Alternatively (simpler): embed reference keys directly in the component data maps and resolve them at import time. E.g. a taxonomy category entry includes `"retentionScheduleRef": "Financial Statutory"` and `"metadataSchemaRef": "Invoice Metadata"`.
- [ ] Increment `compatibilityVersion` to `"2.0"` when the pack format changes. Import service must handle both v1 (flat, unlinked) and v2 (joined-up).

---

## 3. Pack Import into IG Central

The download flow exists (frontend → proxy controller → hub API → response). But there is **no import logic** — the downloaded pack data is returned to the frontend and discarded. The TODO comment at `web/src/app/(protected)/governance/hub/[slug]/page.tsx:81` confirms this.

### 3.1 PackImportService (Backend)

- [ ] Create `PackImportService` in `gls-app-assembly` (or `gls-governance`):
  - Accepts a `PackVersion` (the download response).
  - Iterates over `components[]`, dispatching each to a type-specific importer.
  - Maintains an import context (map of `ref → localId`) for cross-reference resolution.
  - Returns an `ImportResult` summarising what was created, updated, skipped, and failed.

- [ ] **Import order matters** (dependencies must be imported first):
  1. `LEGISLATION` — no dependencies
  2. `SENSITIVITY_DEFINITIONS` — may reference legislation
  3. `RETENTION_SCHEDULES` — references legislation
  4. `STORAGE_TIERS` — references sensitivity keys
  5. `METADATA_SCHEMAS` — no dependencies
  6. `PII_TYPE_DEFINITIONS` — no dependencies
  7. `TRAIT_DEFINITIONS` — no dependencies
  8. `TAXONOMY_CATEGORIES` — references retention schedules, metadata schemas, sensitivity keys
  9. `GOVERNANCE_POLICIES` — references categories, sensitivities, legislation
  10. `PIPELINE_BLOCKS` — may reference other components

### 3.2 Conflict Resolution

When importing into a tenant that already has governance data:

- [ ] **Match by key/name** — if a sensitivity definition with key `CONFIDENTIAL` already exists, update it or skip it (admin choice).
- [ ] **Import modes:**
  - `MERGE` — import new items, skip existing (default).
  - `OVERWRITE` — import everything, replace existing items matched by key/name.
  - `PREVIEW` — dry-run, return what would change without writing.
- [ ] **Admin confirmation step** — show the import preview in the UI before committing. List: "X new categories, Y updated retention schedules, Z new legislation entries, N conflicts".

### 3.3 Import API Endpoint

- [ ] `POST /api/admin/governance/import` — accepts:
  ```json
  {
    "packSlug": "uk-general-governance",
    "versionNumber": 1,
    "componentTypes": ["TAXONOMY_CATEGORIES", "RETENTION_SCHEDULES", ...],
    "mode": "MERGE"
  }
  ```
  - Downloads the pack from the Hub (via `GovernanceHubClient`).
  - Runs `PackImportService`.
  - Returns `ImportResult`.

- [ ] `POST /api/admin/governance/import/preview` — same input, returns preview without committing.

### 3.4 Import Provenance Tracking

- [ ] Add provenance fields to all governance models:
  ```
  sourcePackSlug (String) — which Hub pack this came from
  sourcePackVersion (int) — which version
  importedAt (Instant) — when it was imported
  ```
- [ ] This enables:
  - Showing "Imported from UK General Governance v1" in the admin UI.
  - Checking for updates — "v2 available, would change 3 retention schedules".
  - Preventing accidental re-import of the same version.

### 3.5 Frontend Import Flow

- [ ] Update `web/src/app/(protected)/governance/hub/[slug]/page.tsx`:
  1. User selects components and clicks "Import".
  2. Call `POST /api/admin/governance/import/preview` → show preview dialog.
  3. Preview shows: new items, updates, conflicts, skipped items.
  4. User confirms → call `POST /api/admin/governance/import`.
  5. Show success summary with links to imported items (e.g. "View taxonomy", "View retention schedules").

- [ ] Add an "Imported Packs" section to the governance admin page showing:
  - Which packs have been imported, which version, when.
  - "Check for updates" button per pack.
  - "Re-import" option for specific component types.

---

## 4. Hub Admin UI — Pack Builder

The current `web-hub` admin frontend has basic CRUD for packs, but it needs to support building the joined-up governance model described in section 2.

### 4.1 Pack Component Editor

- [ ] Visual editor for each component type (not raw JSON):
  - **Taxonomy editor** — tree view for categories with drag-and-drop hierarchy. Each node shows linked retention schedule, metadata schema, default sensitivity.
  - **Retention schedule editor** — form with duration picker, disposition action dropdown, legislation multi-select.
  - **Metadata schema editor** — field builder with type dropdowns, required toggles, example inputs.
  - **Legislation editor** — form with jurisdiction dropdown, URL field, articles list.
  - **Sensitivity editor** — ordered list with colour picker, guidelines, examples.
  - **PII pattern editor** — regex builder with test input, confidence slider.
  - **Policy editor** — rule list builder, category multi-select, sensitivity multi-select, legislation multi-select.
  - **Storage tier editor** — form with encryption options, geo-restriction toggle, sensitivity multi-select.
  - **Trait editor** — form with dimension dropdown, detection hint, indicator keywords.

### 4.2 Relationship Builder

- [ ] When editing a taxonomy category in the pack builder, dropdowns to link:
  - Retention schedule (from the same pack's retention schedules).
  - Metadata schema (from the same pack's metadata schemas).
  - Default sensitivity (from the same pack's sensitivity definitions).
- [ ] When editing a retention schedule, multi-select for linked legislation.
- [ ] When editing a governance policy, multi-select for categories, sensitivities, and legislation.
- [ ] Validation — warn if a category references a retention schedule that isn't included in the pack.

### 4.3 Pack Validation

- [ ] Before publishing a new version, validate:
  - All cross-references resolve within the pack.
  - No orphaned metadata schemas (schemas not linked to any category).
  - No duplicate keys/names within a component type.
  - Taxonomy hierarchy is valid (no circular parents).
  - At least one taxonomy category, one retention schedule, and one sensitivity definition exist (minimum viable governance model).

---

## 5. Update Checking & Version Sync

Once a tenant imports a pack, they need to know when updates are available.

### 5.1 Hub-Side

- [ ] `GET /api/hub/packs/{slug}/versions/latest` — returns latest published version number.
- [ ] `GET /api/hub/packs/{slug}/versions/{from}/changelog-since` — returns aggregated changelog from version `from` to latest.

### 5.2 Tenant-Side

- [ ] Background check (configurable interval or manual) — for each imported pack, call the Hub to compare local version vs latest.
- [ ] Admin notification — "UK General Governance has an update: v1 → v2. Changes: 3 new categories, 1 updated retention schedule".
- [ ] Diff preview before re-import — show what would change compared to current local state.
- [ ] Selective update — allow importing only specific component types from the new version.

---

## 6. Public Hub Browse (Unauthenticated)

Currently the Hub's browse endpoints require an API key. For the Hub to function as a marketplace, there should be a public browse experience.

### 6.1 Hub API

- [ ] Make `GET /api/hub/packs` (search), `GET /api/hub/packs/{slug}` (detail), and `GET /api/hub/packs/meta/*` accessible without an API key (read-only).
- [ ] Keep download endpoints behind API key auth (this is what tenants pay for / register for).
- [ ] Add rate limiting on public endpoints to prevent abuse.

### 6.2 Web-Hub Public Pages

- [ ] Public browse page at `/` — search and filter published packs, view details.
- [ ] Pack detail page shows full metadata, component list, version history, reviews — but "Download" requires login/API key.
- [ ] Separate admin pages behind `/admin` prefix with Basic Auth.

---

## 7. Community & Contribution

### 7.1 Pack Submissions

- [ ] Allow tenants to submit their own governance packs to the Hub.
- [ ] Submission review workflow — Hub admin approves/rejects before publishing.
- [ ] Author attribution and verified badge for Hub-curated packs vs community packs.

### 7.2 Pack Forking

- [ ] Tenants can fork a published pack to create their own variant.
- [ ] Fork tracks its upstream source for future merge/diff.

---

## Priority Order

| Priority | Section | Rationale |
|----------|---------|-----------|
| **P0** | 1. Standalone deployment | Hub can't function without its own infra |
| **P0** | 2.1 Legislation model | Foundation for joined-up governance |
| **P0** | 2.2–2.5 Cross-references | Makes the governance model coherent |
| **P0** | 3.1–3.3 PackImportService | Core integration — without import, the Hub is useless to tenants |
| **P1** | 2.6 Pack manifest format | Enables reliable cross-reference resolution |
| **P1** | 3.4 Import provenance | Enables update checking |
| **P1** | 3.5 Frontend import flow | Users need to see what they're importing |
| **P1** | 4.1–4.3 Pack builder UI | Hub admins need a proper editor, not raw JSON |
| **P2** | 5. Update checking | Important for ongoing governance maintenance |
| **P2** | 6. Public browse | Marketplace visibility |
| **P3** | 7. Community features | Nice-to-have, not critical for MVP |
