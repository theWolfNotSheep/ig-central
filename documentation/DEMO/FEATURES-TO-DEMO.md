# IG Central — Features To Demo

A complete inventory of demoable features across the platform, grouped by domain.
Use this list as the source of truth when commissioning new demo videos.

Legend:
- ✅ = already covered in `documentation/DEMO_SCRIPT.md`
- 🆕 = not yet covered; candidate for a new demo script
- 🔧 = exists in backend but no user-facing UI yet (skip unless explicitly asked)

---

## 1. Authentication & Onboarding

| # | Feature | Status | Location |
|---|---|---|---|
| 1.1 | Login (email/password + Google OAuth) | ✅ Video 1 | `/login` |
| 1.2 | Dashboard — stats, pipeline funnel, recent activity | ✅ Video 1 | `/dashboard` |
| 1.3 | Landing page / public homepage | 🆕 | `/` |
| 1.4 | Help Centre (searchable docs by role) | 🆕 | `/help`, `/help/[slug]` |

## 2. Core Document Workflow

| # | Feature | Status | Location |
|---|---|---|---|
| 2.1 | Upload → 4-stage pipeline (extract, PII, classify, enforce) | ✅ Video 2 | `/upload` |
| 2.2 | Document viewer — text, classification panel, PII flag-by-selection | ✅ Video 3 | `/documents` |
| 2.3 | Category tree filtering | ✅ Video 3 | `/documents` |
| 2.4 | Inbox / Filing Queue — post-processing manual filing to folders | 🆕 | `/inbox` |
| 2.5 | Reprocess / override category / override sensitivity | ✅ Video 3 | Document detail |
| 2.6 | Slug-based document URLs (`?doc={slug}`) | 🆕 (quick mention) | `/documents` |

## 3. Human Review & Feedback Loop

| # | Feature | Status | Location |
|---|---|---|---|
| 3.1 | Review queue — approve / reclassify / reject / flag PII | ✅ Video 4 | `/review` |
| 3.2 | Correction feedback loop → MCP tools at next classification | ✅ Video 4 (narrated) | Backend, `gls-mcp-server` |
| 3.3 | PII quick-flag from document selection | ✅ Video 3 | Document viewer |

## 4. Search & Discovery

| # | Feature | Status | Location |
|---|---|---|---|
| 4.1 | Full-text search with faceted filters | ✅ Video 5 | `/search` |
| 4.2 | Metadata-driven structured search (per schema) | ✅ Video 5 | `/search` |
| 4.3 | Schema coverage matrix — categories × schemas, coverage % | 🆕 | `/governance/schema-coverage` |

## 5. Governance Framework

| # | Feature | Status | Location |
|---|---|---|---|
| 5.1 | Taxonomy / categories editor | ✅ Video 6 | `/governance` |
| 5.2 | Sensitivity definitions (colours, labels) | ✅ Video 6 | `/governance` |
| 5.3 | Retention schedules (retain / archive / delete) | ✅ Video 6 | `/governance` |
| 5.4 | Policies (rules triggered by category/sensitivity) | ✅ Video 6 | `/governance` |
| 5.5 | Metadata schemas (typed field extraction per category) | ✅ Video 7 | `/governance` (schemas tab) |
| 5.6 | Legislation / legal mappings | 🆕 | Governance pack tab |
| 5.7 | Storage locations & policies | 🆕 | Governance pack tab |
| 5.8 | Traits (cross-cutting tags / risk markers) | 🆕 | Governance pack tab |

## 6. Governance Hub (Marketplace)

| # | Feature | Status | Location |
|---|---|---|---|
| 6.1 | Browse Hub — filter by jurisdiction, downloads, ratings | 🆕 | `/governance/hub` |
| 6.2 | Pack detail — components, author, install status | 🆕 | `/governance/hub/[slug]` |
| 6.3 | Install pack into tenant | 🆕 | `/governance/hub/[slug]` |
| 6.4 | Update observer — check for updates, view changelog, update | 🆕 | `/governance/hub` |
| 6.5 | Pack diff / conflict detection on update | 🆕 | Update flow |

## 7. Pipelines & Blocks

| # | Feature | Status | Location |
|---|---|---|---|
| 7.1 | Pipeline list & step types (built-in / pattern / LLM / router) | ✅ Video 8 | `/pipelines` |
| 7.2 | Visual pipeline editor (React Flow) | ✅ Video 8 (brief) | `/pipelines` |
| 7.3 | Block Library — filter by type, edit, publish | ✅ Video 8 | `/blocks` |
| 7.4 | Block versioning — compare versions, rollback | ✅ Video 8 | `/blocks` |
| 7.5 | Block feedback aggregation (per version) | ✅ Video 8 | `/blocks` |
| 7.6 | "Improve with AI" — regenerate block from feedback | ✅ Video 8 (mention) | `/blocks` |
| 7.7 | Node type definitions (custom step types) | 🆕 | `/admin/node-types` |

## 8. PII, SARs & Privacy

| # | Feature | Status | Location |
|---|---|---|---|
| 8.1 | PII search across estate | ✅ Video 9 | `/pii` |
| 8.2 | PII summary / landscape dashboard | ✅ Video 9 | `/pii` |
| 8.3 | Subject Access Request tracking (deadline, notes, status) | ✅ Video 9 | `/pii` |

## 9. External Storage

| # | Feature | Status | Location |
|---|---|---|---|
| 9.1 | Connect Google Drive (OAuth) | ✅ Video 10 | `/drives` |
| 9.2 | Browse Drive folders & classify in-situ | ✅ Video 10 | `/drives` |
| 9.3 | Watch Folder (auto-classify new arrivals) | ✅ Video 10 (mention) | `/drives` |
| 9.4 | Multi-drive abstraction (S3, SharePoint, Box, SMB stubs) | 🔧 | Backend |

## 10. Admin — Users, Roles & Access

| # | Feature | Status | Location |
|---|---|---|---|
| 10.1 | User management — list, create, enable/disable, assign roles | 🆕 | `/admin/users` |
| 10.2 | User detail — edit clearance, taxonomy grants, reset password | 🆕 | `/admin/users/[id]` |
| 10.3 | Role management — features, default clearance, system flag | 🆕 | `/admin/users` (modal) |
| 10.4 | Directory mappings — Google Workspace domain/group → role | 🆕 | `/admin/directory` |
| 10.5 | Access audit matrix — users × categories | ✅ Video 12 | `/admin/access` |
| 10.6 | Document access lookup (who can see doc X?) | ✅ Video 12 | `/admin/access` |
| 10.7 | Category access lookup (who can see category X?) | ✅ Video 12 | `/admin/access` |
| 10.8 | System audit log — filterable, paginated | 🆕 | `/admin/audit` |

## 11. Admin — Subscriptions & Licensing

| # | Feature | Status | Location |
|---|---|---|---|
| 11.1 | Features / Roles / Products / Subscriptions model | 🔧 | Backend only, no UI yet |
| 11.2 | Subscription permission sync service | 🔧 | Backend (narrated if relevant) |

## 12. AI Configuration & Cost

| # | Feature | Status | Location |
|---|---|---|---|
| 12.1 | Classification thresholds (review / auto-approve) | ✅ Video 12 | `/settings` (classification) |
| 12.2 | LLM provider switch (Anthropic / Ollama) | 🆕 | `/ai#settings` |
| 12.3 | Model selection (Haiku / Sonnet / Opus) + temperature, tokens | ✅ Video 12 | `/ai#settings` |
| 12.4 | Ollama local model pull / delete / status | 🆕 | `/ai#models` |
| 12.5 | Pipeline diagnostics (LLM connectivity, config validity) | 🆕 | `/ai#settings` |
| 12.6 | AI usage log (every call: prompt, tokens, cost, overridden?) | 🆕 | `/ai#usage` |
| 12.7 | Cost estimator (docs/month → £ projection) | ✅ Video 12 | `/settings` |

## 13. BERT / Local Classifier

| # | Feature | Status | Location |
|---|---|---|---|
| 13.1 | BERT service status (ONNX / HF / demo mode) | 🆕 | `/ai#models` |
| 13.2 | Training data per category — upload, manual entry, verify | 🆕 | `/ai#models` |
| 13.3 | Auto-collection from pipeline (confidence gate, corrected-only) | 🆕 | `/ai#models` |
| 13.4 | Training jobs — start, track, metrics (accuracy, F1, loss) | 🆕 | `/ai#models` |
| 13.5 | Promote trained model to active | 🆕 | `/ai#models` |
| 13.6 | Export training data (JSONL + label_map.json) | 🆕 | `/ai#models` |
| 13.7 | BERT hit-rate statistics (BERT vs LLM split) | 🆕 | `/ai#models` |

## 14. Monitoring & Ops

| # | Feature | Status | Location |
|---|---|---|---|
| 14.1 | Service health (5 microservices, ping) | ✅ Video 11 | `/monitoring` |
| 14.2 | Pipeline stage visualisation & throughput metrics | ✅ Video 11 | `/monitoring` |
| 14.3 | Stale document detection & reset | ✅ Video 11 | `/monitoring` |
| 14.4 | Retry failed / Cancel all in-flight | ✅ Video 11 | `/monitoring` |
| 14.5 | RabbitMQ queue depths & purge | ✅ Video 11 | `/monitoring` |
| 14.6 | Pipeline activity feed (SSE, real-time) | ✅ Video 11 | `/monitoring` |
| 14.7 | Processing log with error highlighting | ✅ Video 11 | `/monitoring` |
| 14.8 | Infrastructure overview (DB, storage, MQ) | ✅ Video 11 | `/monitoring` |

## 15. Settings & Integrations

| # | Feature | Status | Location |
|---|---|---|---|
| 15.1 | Google Drive OAuth config (client ID/secret) | 🆕 | `/settings` |
| 15.2 | Governance Hub URL + API key config | 🆕 | `/settings` |
| 15.3 | Processing toggles (auto-classify, auto-enforce, Dublin Core) | ✅ Video 12 | `/settings` |
| 15.4 | Runtime config — menus, labels, feature flags | 🆕 (admin-only) | `/admin/config` |

## 16. Hub App (separate `web-hub` frontend for pack publishers)

| # | Feature | Status | Location |
|---|---|---|---|
| 16.1 | Hub admin dashboard — pack count, downloads, API keys | 🆕 | `web-hub/` |
| 16.2 | Pack authoring — multi-tab editor (taxonomy, sensitivity, retention, PII, policies, storage, legislation, metadata, traits) | 🆕 | `web-hub/packs/[id]` |
| 16.3 | Pack version history, diff, rollback, publish | 🆕 | `web-hub/packs/[id]` |
| 16.4 | Pack cloning | 🆕 | `web-hub/packs` |
| 16.5 | API key generation for tenants | 🆕 | `web-hub/api-keys` |
| 16.6 | Download analytics per pack | 🆕 | `web-hub/downloads` |

---

## Priority Demo Candidates (not yet scripted)

If the next batch of videos should cover the highest-impact gaps, prioritise:

1. **Admin: Users, Roles & Directory Mapping** (10.1–10.4) — core admin story
2. **Governance Hub: browse, install, update** (6.1–6.5) — differentiator
3. **BERT Local Classifier training loop** (13.1–13.7) — cost-reduction narrative
4. **AI Usage Log & Cost Telemetry** (12.5–12.6) — trust / observability
5. **Inbox / Filing Workflow** (2.4) — missing piece in core workflow
6. **Hub App (web-hub)** (16.1–16.6) — dedicated pack-publisher persona
7. **Schema Coverage Matrix** (4.3) — governance completeness story
8. **Help Centre** (1.4) — short onboarding-focused piece
9. **Audit Log** (10.8) — compliance / ISO 15489 story

---

## Source

Inventory derived from codebase sweep on 2026-04-19. Regenerate by scanning:
- `web/app/**` (Next.js routes)
- `web-hub/app/**` (Hub publisher routes)
- `backend/**/*Controller.java` (API surface)
- `backend/gls-app-assembly/src/main/resources/seeders` (seeded capabilities)
