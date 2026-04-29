# Governance-Led Storage (IG Central)

**AI-powered document governance that classifies, protects, and manages your records — automatically.**

---

## The Problem

Organisations drown in unclassified documents. Sensitive files sit in shared drives with no retention policy, no sensitivity label, and no audit trail. Records managers can't keep up manually, and compliance gaps grow with every upload. When regulators come knocking, finding out what you have — and proving you've been managing it properly — becomes a costly, stressful scramble.

---

## What GLS Does

GLS is an intelligent document governance platform that **automatically classifies, labels, and enforces policy on every document** the moment it enters your organisation — whether uploaded directly or sitting in Google Drive.

### AI Classification at Scale

Every document passes through a set of workflow tools:

1. **Text extraction** — content is pulled from PDFs, Word docs, spreadsheets, images (with OCR), and 40+ formats
2. **PII detection** — credit card numbers, national insurance numbers, emails, phone numbers, and custom patterns are flagged automatically
3. **AI classification** — Claude reads the document, consults your taxonomy, policies, and past human corrections, then assigns a category, sensitivity level, tags, and structured metadata
4. **Governance enforcement** — retention schedules, storage tiers, and access restrictions are applied instantly based on the classification

No manual tagging. No guesswork. Documents are classified in seconds, not days.

### Human-in-the-Loop Review

AI doesn't replace your records managers — it amplifies them. When the AI's confidence is low, documents route to a **review queue** where staff can approve, override, or flag issues. Every correction feeds back into the system, making future classifications more accurate over time. The AI learns your organisation's patterns.

### Define Your Own Governance Framework

GLS doesn't impose a one-size-fits-all taxonomy. You define:

- **Classification taxonomy** — hierarchical categories that match your records management scheme
- **Sensitivity levels** — PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED (or whatever your organisation uses)
- **Retention schedules** — how long to keep documents and what happens when they expire (delete, archive, or review)
- **Governance policies** — rules that trigger based on category or sensitivity (encrypt, watermark, restrict sharing, log access)
- **Metadata schemas** — structured fields the AI extracts per document type (e.g. employee name, leave dates, contract value)

All configuration is runtime-editable. No redeployment needed. Non-technical staff can manage policies from the admin panel.

### Google Drive Integration

Classify documents **where they already live**. Connect your Google Drive, browse folders, and select files for classification. Documents stay in Drive — GLS reads them, classifies them, and applies governance metadata without moving or copying anything.

### Real-Time Monitoring & Operations

- **Pipeline dashboard** — see documents flowing through each stage in real time
- **Health monitoring** — service status, response times, queue depths
- **Stale detection** — documents stuck in processing are flagged and can be retried with one click
- **Error recovery** — failed documents are logged with full error context and can be retried individually or in bulk
- **Cost estimator** — project your monthly AI spend based on document volume and model choice

### Full Audit Trail

Every action is logged — uploads, classifications, approvals, overrides, PII flags, retention actions, and disposals. When compliance asks "who classified this and when?", the answer is always one click away.

### Legal Holds

Mark documents with a legal hold to prevent deletion or archival, regardless of retention policy. Holds are tracked, auditable, and require explicit release.

---

## Who It's For

- **Records Managers** — stop manually classifying documents; review only what the AI isn't sure about
- **Information Governance Officers** — define policies once, enforce them everywhere, automatically
- **Compliance Teams** — prove your retention and sensitivity controls with complete audit trails
- **IT Leaders** — deploy a single platform that handles classification, PII detection, retention, and storage tiering
- **Any regulated organisation** — healthcare, finance, legal, government, education — where document governance isn't optional

---

## Key Benefits

| | |
|---|---|
| **Reduce classification backlog** | AI processes documents in seconds, not days |
| **Cut compliance risk** | Retention and sensitivity policies enforced automatically on every document |
| **Detect sensitive data** | PII scanning catches what humans miss — credit cards, NI numbers, personal data |
| **Learn from your team** | Human corrections improve AI accuracy over time — no retraining required |
| **Govern in place** | Classify Google Drive files without moving them |
| **Stay audit-ready** | Complete trail of every classification, correction, and policy action |
| **Control costs** | Choose your AI model (Claude Haiku to Opus), monitor spend, and step down as correction history grows |
| **No vendor lock-in** | Self-hosted, cloud-agnostic storage (MinIO, S3, Google Drive) |

---

## Architecture Highlights

- **Event-driven microservices** — RabbitMQ pipelines scale each stage independently
- **Configuration-driven** — governance rules, menus, labels, and policies are all runtime-editable from MongoDB
- **MCP tool integration** — the AI consults your live taxonomy, policies, and correction history during every classification
- **Elasticsearch** — full-text search across documents, metadata, and extracted fields
- **Self-hosted** — runs on Docker Compose; your data never leaves your infrastructure

---

## Quick Start

```bash
# One-command bring-up: validates .env, builds images, waits for healthy.
scripts/dev-up.sh

# Or, if you prefer raw compose:
docker compose up --build -d

# Access
http://localhost              # Homepage
http://localhost/login        # Login
http://localhost/dashboard    # Dashboard (auth required)
http://localhost:8080/actuator/health   # API health
http://localhost:9090         # Prometheus
http://localhost:3003         # Grafana (admin / admin)
http://localhost:15672        # RabbitMQ admin (guest / guest)
http://localhost:9001         # MinIO console
```

The doc-processor and governance-enforcer modules used to ship as separate containers; they are now bundled into the `api` container (per `gls-app-assembly`'s startup wiring). New v2 services arrive as commented-out placeholders in `docker-compose.yml` — uncomment per phase.

## Local Development

```bash
# Backend
cd backend && ./mvnw compile -DskipTests -pl gls-app-assembly -am

# Backend tests (per module, fast)
cd backend && ./mvnw -pl gls-platform-audit,gls-platform-config -am test

# Frontend
cd web && npm install && npm run dev

# OpenAPI / AsyncAPI contract lint (Spectral)
pre-commit run --all-files     # or: spectral lint contracts/**/*.yaml
```

The architecture is contract-first: every service-to-service interface is declared under `contracts/` (OpenAPI 3.1.1 for sync, AsyncAPI 3.0 for async, JSON Schema 2020-12 for shared envelopes) before any code lands. See `CLAUDE.md` for the rules.

### Substrate libraries

- **`gls-platform-audit`** — outbox-pattern audit emitter, schema-validating, with the outbox-to-Rabbit relay. Single dependency every JVM service imports for audit traffic. See `backend/gls-platform-audit/README.md`.
- **`gls-platform-config`** — change-driven config-cache primitive. Replaces the previous Caffeine TTL pattern; per-replica in-memory cache, invalidated via the `gls.config.changed` Rabbit channel. See `backend/gls-platform-config/README.md`.

### v2 progress

- `version-2-architecture.md` — how the system works.
- `version-2-decision-tree.csv` — every architectural decision (DECIDED / RECOMMENDED / OPEN / DEFERRED).
- `version-2-implementation-plan.md` — phased plan with acceptance gates and `[x]` / `[ ]` checkboxes.
- `version-2-implementation-log.md` — append-only record of what's actually shipped.

## Tech Stack

- **Backend:** Spring Boot 4.0.2 (Java 21), Maven multi-module monorepo
- **Frontend:** Next.js 16 (React 19, TypeScript, TailwindCSS 4)
- **Database:** MongoDB
- **Search:** Elasticsearch 8.17
- **Messaging:** RabbitMQ
- **Object Storage:** MinIO (S3-compatible)
- **AI:** Anthropic Claude via Spring AI + MCP
- **Infrastructure:** Docker Compose, nginx, Cloudflare tunnel
