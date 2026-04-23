# IG Central — Demo Feature List

This is the master inventory of features to demo. Each feature here should have (or eventually have) a dedicated demo script under `documentation/DEMO/scripts/`.

Status legend:
- ✅ **Covered** — already scripted in `documentation/DEMO_SCRIPT.md` (the 12-video master script)
- 🟡 **Partial** — touched on in the master script but would benefit from a standalone deep-dive script
- ⬜ **Not covered** — needs a new demo script written
- 🔒 **Planned / not shipped** — do not script until the feature lands

---

## 1. Core Workflow

| # | Feature | Status | Location | Notes |
|---|---------|--------|----------|-------|
| 1.1 | Login & Dashboard overview | ✅ | `web/app/(protected)/dashboard` | Video 1 of master script |
| 1.2 | Upload & four-stage pipeline | ✅ | Upload flow + monitoring feed | Video 2 |
| 1.3 | Document viewer & classification detail | ✅ | `/documents/[id]` | Video 3 — reasoning, metadata, PII flag |
| 1.4 | Human review queue | ✅ | `/review` | Video 4 — approve / reclassify / reject / PII flag |
| 1.5 | Search & discovery (full-text + faceted) | ✅ | `/search` | Video 5 |

## 2. Governance Framework

| # | Feature | Status | Location | Notes |
|---|---------|--------|----------|-------|
| 2.1 | Taxonomy / category tree | ✅ | `/governance` | Video 6 |
| 2.2 | Sensitivity level definitions | ✅ | `/governance` | Video 6 |
| 2.3 | Retention schedules | ✅ | `/governance` | Video 6 |
| 2.4 | Policies & enforcement rules | ✅ | `/governance` | Video 6 |
| 2.5 | Metadata extraction schemas | ✅ | `/governance` → Metadata Schemas | Video 7 |
| 2.6 | Node type definitions (custom taxonomy nodes) | ✅ | `/admin/node-types` | `scripts/node-type-definitions.md` |

## 3. AI & Automation

| # | Feature | Status | Location | Notes |
|---|---------|--------|----------|-------|
| 3.1 | AI pipelines (visual editor) | ✅ | `/ai/pipelines` | Video 8 |
| 3.2 | Block Library (prompt / regex / extractor / router / enforcer) | ✅ | `/ai/blocks` | Video 8 |
| 3.3 | Block versioning, diff, rollback | ✅ | `/ai/blocks/[id]` → Versions | Deep dive: `scripts/block-versioning.md` |
| 3.4 | Block feedback loop ("Improve with AI") | ✅ | `/ai/blocks/[id]` → Feedback | Deep dive: `scripts/block-feedback-loop.md` |
| 3.5 | Correction feedback loop → MCP tools | ✅ | MCP server + review corrections | `scripts/correction-feedback-mcp.md` |
| 3.6 | LLM model selection & configuration | ✅ | `/settings` | Video 12 |
| 3.7 | AI usage tracking (tokens / cost) | ✅ | `/ai/usage` | `scripts/ai-usage-tracking.md` |
| 3.8 | Ollama local inference / hybrid routing | ✅ | Settings + backend config | `scripts/ollama-local-inference.md` |
| 3.9 | BERT training job dashboard | ✅ | Admin AI section (`BertTrainingJobController`) | `scripts/bert-training.md` |
| 3.10 | Training data sample collection | ✅ | Admin AI section | `scripts/training-data-samples.md` |
| 3.11 | Pipeline diagnostics (prompt traces, confidence internals) | ✅ | `/admin/ai/diagnostics` | `scripts/pipeline-diagnostics.md` |
| 3.12 | ML Reports (model performance, quality, cost, feedback, scatter) | ✅ | `/reports` | `scripts/ml-reports.md` |

## 4. PII & Compliance

| # | Feature | Status | Location | Notes |
|---|---------|--------|----------|-------|
| 4.1 | PII search across estate | ✅ | `/pii` → Search | Video 9 |
| 4.2 | PII summary & breakdown by type | ✅ | `/pii` → Summary | Video 9 |
| 4.3 | Subject Access Requests (SARs) workflow | ✅ | `/pii` → SARs | Video 9 |
| 4.4 | PII pattern management (regex sets as blocks) | ✅ | `/ai/blocks` (REGEX_SET) | `scripts/pii-pattern-management.md` |

## 5. Integrations

| # | Feature | Status | Location | Notes |
|---|---------|--------|----------|-------|
| 5.1 | Google Drive connect & in-situ classification | ✅ | `/drives` | Video 10 |
| 5.2 | Google Drive watch folders | ✅ | `/drives` | `scripts/drive-watch-folders.md` |
| 5.3 | Google OAuth login | ✅ | `/login` | `scripts/google-oauth-login.md` |
| 5.4 | Google Drive labels — read, write, taxonomy bridge | ✅ | `/drives/[id]/labels` | `scripts/drive-labels-taxonomy-bridge.md` |
| 5.5 | Label → taxonomy mapping & bulk import as training data | ✅ | `/drives/[id]/labels` | `scripts/drive-labels-taxonomy-bridge.md` |
| 5.6 | SharePoint / OneDrive connectors | 🔒 | Planned | Interfaces ready per CLAUDE.md |

## 6. Governance Hub (Marketplace)

| # | Feature | Status | Location | Notes |
|---|---------|--------|----------|-------|
| 6.1 | Browse hub pack library | ✅ | `/governance/hub` (web) | `scripts/hub-browse-packs.md` |
| 6.2 | Pack detail, versions, components | ✅ | `/governance/hub/[slug]` | `scripts/hub-pack-details.md` |
| 6.3 | Import a governance pack into tenant | ✅ | `/admin/governance/import` | `scripts/hub-import-pack.md` |
| 6.4 | Pack update observer (notifications when newer version exists) | ✅ | Admin badge + `/api/admin/governance/updates` | `scripts/hub-pack-update-observer.md` |
| 6.5 | Pack diff & conflict detection on update | ✅ | Import / update flow | `scripts/hub-pack-diff-conflicts.md` |
| 6.6 | Hub app — pack authoring & publishing | ✅ | `web-hub` or `hub` app → `/packs/[id]` | `scripts/hub-pack-authoring.md` |
| 6.7 | Hub app — API keys | ✅ | Hub `/api-keys` | `scripts/hub-api-keys.md` |
| 6.8 | Hub app — downloads / version management | ✅ | Hub `/downloads` | `scripts/hub-downloads.md` |

## 7. Operations & Monitoring

| # | Feature | Status | Location | Notes |
|---|---------|--------|----------|-------|
| 7.1 | Monitoring dashboard — service health | ✅ | `/monitoring` | Video 11 |
| 7.2 | Pipeline activity feed (SSE) | ✅ | `/monitoring` | Video 11 |
| 7.3 | Queue depths & pipeline controls (reset stale / retry failed / cancel) | ✅ | `/monitoring` | Video 11 |
| 7.4 | Processing log & error inspection | ✅ | `/monitoring` | Video 11 |
| 7.5 | Failure handling & document retry lifecycle | ✅ | Across monitoring + document detail | `scripts/failure-recovery.md` |

## 8. Admin, Access & Identity

| # | Feature | Status | Location | Notes |
|---|---------|--------|----------|-------|
| 8.1 | Access audit matrix | ✅ | `/admin/access` | Video 12 |
| 8.2 | User management, roles, permissions | ✅ | `/admin/users` | Deep dive: `scripts/user-management.md` |
| 8.3 | Clearance levels & 3-layer access control | ✅ | `/admin/users` | `scripts/clearance-access-control.md` |
| 8.4 | Directory / LDAP mapping | ✅ | `/admin/directory` | `scripts/directory-ldap-mapping.md` |
| 8.5 | Audit trail / activity log | ✅ | `/admin/audit` | `scripts/audit-trail.md` |
| 8.6 | Settings — classification thresholds, processing toggles, cost estimator | ✅ | `/settings` | Video 12 |
| 8.7 | Runtime config (menus, labels, flags) | ✅ | `/admin/config` | `scripts/runtime-config.md` |

## 9. Subscriptions & Licensing

| # | Feature | Status | Location | Notes |
|---|---------|--------|----------|-------|
| 9.1 | Features / Roles / Products admin | ✅ | `/admin/...` | `scripts/features-roles-products.md` |
| 9.2 | Subscription creation & permission sync | ✅ | `/admin/subscriptions` | `scripts/subscriptions-sync.md` |
| 9.3 | Signup + trial + Stripe payments | 🔒 | Planned phases 1–6 | Not yet shipped |
| 9.4 | Email verification | 🔒 | Planned | Not yet shipped |

## 10. Emerging / Roadmap (do not script until shipped)

| # | Feature | Status | Notes |
|---|---------|--------|-------|
| 10.1 | Saved searches & alerts | 🔒 | Roadmap |
| 10.2 | MongoDB CSFLE encryption at rest | 🔒 | Roadmap |
| 10.3 | S3 / SharePoint / Box / SMB connectors | 🔒 | Roadmap |
| 10.4 | Kubernetes & CI/CD | 🔒 | Roadmap |
| 10.5 | OpenTelemetry tracing | 🔒 | Roadmap |

---

## Demo script creation

Each feature marked ⬜ or 🟡 above is a candidate for its own standalone demo script. When writing new scripts, follow the style and structure in `documentation/DEMO_SCRIPT.md`:

- Goal sentence
- Numbered script steps with spoken lines in italics
- Key message at the end
- Target duration 2:30–3:00 unless the feature genuinely needs more

Store new standalone scripts under `documentation/DEMO/scripts/<feature-slug>.md` and reference them back here by updating the Status column.

See `documentation/DEMO/DEMO_SCRIPT_PROMPT.md` for the standing instructions to use when asked to build a demo script.
