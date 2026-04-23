# IG Central — Production Roadmap

> This document describes the path from the current development state to a production-ready deployment for regulated industry customers handling millions of documents. Each section is ordered by priority within its category.

---

## Current State (April 2026)

### What's Built
- **14 Docker containers**: API, web, nginx, MongoDB, Elasticsearch, RabbitMQ, MinIO, MCP server, LLM worker, doc-processor, governance-enforcer, Cloudflare tunnel, Governance Hub + Hub MongoDB
- **AI classification pipeline**: document upload → text extraction (Tika + Tesseract OCR) → PII detection (configurable regex patterns) → LLM classification (Ollama local or Anthropic cloud) → governance enforcement → human review queue
- **MCP tool architecture**: LLM accesses taxonomy, sensitivity definitions, correction history, PII patterns, metadata schemas, retention schedules, traits, and governance policies via tool calls
- **Universal drive-based storage**: Local Storage, Google Drive, with interfaces ready for S3, SharePoint, Box, SMB
- **Governance Hub**: Separate marketplace service for sharing governance frameworks between instances
- **Full admin suite**: user management, RBAC with 3-layer access control (permission + taxonomy + sensitivity), audit trail, monitoring with real-time SSE, pipeline log, AI usage tracking
- **Configuration-driven**: all LLM settings, PII patterns, metadata schemas, sensitivity labels, taxonomy, policies, and retention schedules managed from the UI — no code changes needed

### Known Limitations
- Single instance of each worker (no horizontal scaling)
- No encryption at rest beyond what MongoDB/MinIO provide by default
- Audit logs are mutable (can be modified by DB admin)
- No backup/restore automation
- No CI/CD pipeline
- No load testing or performance benchmarks
- Development-grade nginx config (no TLS termination, rate limiting, or WAF)
- Docker Compose deployment (not orchestrated)

---

## Phase 1: Production Infrastructure (Weeks 1-4)

### 1.1 Kubernetes Migration
**Why**: Docker Compose doesn't support auto-scaling, rolling deployments, health-based restarts, or resource limits in production.

**Tasks**:
- [ ] Create Helm chart for IG Central with configurable values.yaml
- [ ] Define resource requests/limits for each container (LLM worker needs 2-4GB RAM, doc-processor 1GB, API 512MB)
- [ ] Configure horizontal pod autoscalers (HPA) for LLM worker (scale on RabbitMQ queue depth), doc-processor (scale on ingested queue depth), and API (scale on request rate)
- [ ] Set up persistent volume claims for MongoDB, MinIO, Elasticsearch, RabbitMQ
- [ ] Configure liveness/readiness probes (already have health endpoints on all services)
- [ ] Create staging and production namespaces with network policies
- [ ] Document deployment runbook

**Guidance for Claude**: The existing Docker Compose file at `docker-compose.yml` defines all services with their environment variables, health checks, and dependencies. Convert each service to a Kubernetes Deployment + Service. The Helm values.yaml should expose: replica counts, resource limits, image tags, MongoDB/RabbitMQ/MinIO connection strings, and all the env vars currently in docker-compose. Use ConfigMaps for non-sensitive config and Secrets for passwords/keys.

### 1.2 CI/CD Pipeline
**Why**: Manual docker-compose builds don't scale for a team and don't enforce quality gates.

**Tasks**:
- [ ] GitHub Actions (or GitLab CI) workflow: build → test → security scan → push images → deploy to staging
- [ ] Separate build jobs per Maven module (doc-processor, llm-worker, etc. can build in parallel)
- [ ] Container image scanning with Trivy or Snyk
- [ ] Dependency vulnerability scanning (Maven + npm)
- [ ] Automated database migration verification (MongoDB schema changes)
- [ ] Blue/green or canary deployment strategy
- [ ] Automated rollback on health check failure

**Guidance for Claude**: The backend is a Maven multi-module project built from `backend/pom.xml`. Each service has its own Dockerfile (`Dockerfile`, `Dockerfile.docprocessing`, `Dockerfile.llm`, `Dockerfile.mcp`, `Dockerfile.enforcement`, `Dockerfile.hub`). The frontend is a Next.js app at `web/`. Build matrix: build all backend modules in one step, then package each Dockerfile separately. Use GitHub Container Registry (ghcr.io) for images.

### 1.3 Observability Stack
**Why**: The current SSE-based monitoring doesn't persist metrics, has no alerting, and loses data on restart.

**Tasks**:
- [ ] Add Micrometer/Prometheus metrics to all Spring Boot services (classification latency histogram, documents processed counter, queue depth gauge, error rate counter)
- [ ] Deploy Prometheus + Grafana (or use managed Datadog/New Relic)
- [ ] Create dashboards: pipeline throughput, LLM latency percentiles (p50/p95/p99), queue depth over time, error rate by stage, storage growth
- [ ] Add OpenTelemetry distributed tracing across all services (trace a document's journey from upload through all 4 workers)
- [ ] Deploy Jaeger or Zipkin for trace visualization
- [ ] Centralise logs: ship all container logs to ELK stack or Loki
- [ ] Configure alerts: queue depth > 100, error rate > 5%, stale documents > 10, service down > 2 minutes, disk > 80%
- [ ] PagerDuty/OpsGenie integration for on-call routing

**Guidance for Claude**: Spring Boot 4 includes Micrometer by default. Add `micrometer-registry-prometheus` to each service's pom.xml. The existing `MonitoringService.java` calculates metrics — expose them as Prometheus gauges/counters instead of JSON. The SSE broadcaster can remain for the UI but metrics should flow to Prometheus for persistence and alerting.

### 1.4 Backup & Disaster Recovery
**Why**: No backup strategy means data loss on any failure.

**Tasks**:
- [ ] Automated MongoDB backup to S3 (daily full, hourly incremental via oplog)
- [ ] Automated Elasticsearch snapshot to S3
- [ ] MinIO bucket replication or versioning
- [ ] RabbitMQ durable queues with mirroring (already durable, need mirroring)
- [ ] Documented restore procedure with tested RTO < 1 hour
- [ ] Cross-region backup replication for disaster recovery
- [ ] Quarterly restore drill

**Guidance for Claude**: MongoDB supports `mongodump` for logical backups and filesystem snapshots for point-in-time recovery. For production, use MongoDB Atlas or a managed service that handles backups automatically. If self-hosted, create a CronJob in Kubernetes that runs `mongodump` to an S3 bucket using the `aws-cli` image.

---

## Phase 2: Security Hardening (Weeks 3-6)

### 2.1 Encryption at Rest
**Why**: Regulated industries require data at rest to be encrypted. A database breach should not expose document content or PII.

**Tasks**:
- [ ] Enable MongoDB encrypted storage engine (WiredTiger encryption)
- [ ] Enable S3/MinIO server-side encryption (SSE-S3 or SSE-KMS with customer-managed keys)
- [ ] Implement field-level encryption for PII fields in DocumentModel: `piiFindings[].matchedText`, `extractedText` (consider performance trade-off — extractedText is large)
- [ ] Encrypt `accessToken` and `refreshToken` in ConnectedDrive at the application level (currently stored in plaintext)
- [ ] Encrypt API keys in HubApiKey (currently hashed with SHA-256, which is correct — but the audit log may contain plaintext snippets)

**Guidance for Claude**: MongoDB Client-Side Field Level Encryption (CSFLE) is the right approach for PII fields. Add `spring-boot-starter-data-mongodb-reactive` isn't needed — use the MongoDB Java driver's CSFLE directly. Create an encryption service that wraps `ClientEncryption` from the MongoDB driver. Key management: use AWS KMS, Azure Key Vault, or HashiCorp Vault as the key provider.

### 2.2 Network Security
**Why**: Current Docker network has no encryption between containers. All inter-service communication is plaintext.

**Tasks**:
- [ ] Enable mutual TLS (mTLS) between all services using a service mesh (Istio or Linkerd) or cert-manager
- [ ] Network policies in Kubernetes: doc-processor can only reach MongoDB and RabbitMQ, LLM worker can reach MongoDB/RabbitMQ/MCP server, etc.
- [ ] API gateway (Kong or AWS API Gateway) in front of nginx for: rate limiting, request logging, IP allowlisting, DDoS protection
- [ ] TLS termination at the ingress (replace Cloudflare tunnel with proper ingress + cert-manager for production)
- [ ] Remove all exposed ports except HTTPS 443

### 2.3 Secret Management
**Why**: The current `.env` file and MongoDB `app_config` collection store secrets in plaintext.

**Tasks**:
- [ ] Migrate all secrets to HashiCorp Vault or AWS Secrets Manager
- [ ] Automatic secret rotation for: MongoDB passwords, JWT signing key, Anthropic API key, Google OAuth credentials
- [ ] Remove secrets from `.env` file entirely — Kubernetes Secrets or Vault Agent Injector
- [ ] Audit log for secret access (who read which secret, when)

### 2.4 Audit Log Immutability
**Why**: Regulated industries require tamper-proof audit trails. The current `system_audit_log` and `ai_usage_log` collections can be modified by anyone with database access.

**Tasks**:
- [ ] Stream audit events to an append-only store: AWS QLDB, Azure Immutable Blob Storage, or a blockchain-backed ledger
- [ ] Cryptographic chaining: each audit event includes a hash of the previous event (tamper detection)
- [ ] Separate the audit database from the application database with different credentials (audit DB is write-only from the app, read-only for admins)
- [ ] Retention policy: audit logs retained for 7 years minimum (regulatory requirement for most financial services)
- [ ] Digital signatures on audit events using the server's private key

**Guidance for Claude**: The simplest approach is to write audit events to both MongoDB (for fast querying) and an S3 bucket with Object Lock enabled (WORM — Write Once Read Many). Each event gets a SHA-256 hash of (previous_hash + event_data), creating an audit chain. On read, verify the chain integrity.

---

## Phase 3: Scaling for Millions (Weeks 5-10)

### 3.1 LLM Worker Horizontal Scaling
**Why**: The LLM worker is the pipeline bottleneck. A single Ollama instance processes ~1 doc/minute. 1M documents = 694 days with one worker.

**Tasks**:
- [ ] Run multiple LLM worker replicas (Kubernetes Deployment with replicas: 5-10)
- [ ] RabbitMQ competing consumers pattern (already works — multiple workers consume from the same queue)
- [ ] Ollama load balancer: run multiple Ollama instances on GPU nodes, LLM workers round-robin between them
- [ ] Model routing: use smaller/faster models (Haiku, Mistral Small) for simple documents, larger models (Sonnet, Qwen 72B) for complex or low-confidence documents
- [ ] Batch classification: group similar documents and classify in batches with shared context (reduces redundant tool calls)
- [ ] Skip classification for known documents: if SHA-256 hash matches a previously classified document, copy the result

**Performance targets**:
- 10 LLM workers + 5 Ollama instances = ~50 docs/minute = ~72,000 docs/day
- With hash dedup and model routing: ~150,000 docs/day achievable

### 3.2 MongoDB Scaling
**Why**: A single MongoDB instance with millions of documents will hit I/O and memory limits.

**Tasks**:
- [ ] Replica set (3 nodes minimum) for high availability
- [ ] Read replicas for search/reporting workloads (the monitoring page, facet counts, PII summary should read from secondaries)
- [ ] Shard the `documents` collection by `organisationId` (or `createdAt` for time-based partitioning)
- [ ] Archive old documents: move GOVERNANCE_APPLIED documents older than 1 year to a cold collection
- [ ] Add compound indexes for common query patterns: `{status: 1, updatedAt: 1}`, `{categoryId: 1, status: 1}`, `{storageProvider: 1, externalStorageRef.fileId: 1}`
- [ ] Monitor slow queries with MongoDB profiler

**Guidance for Claude**: The existing indexes on DocumentModel (see the `@Indexed` annotations) cover the main query patterns. The missing ones identified in the performance audit were added. For sharding, `organisationId` is the best shard key because it distributes evenly and queries are almost always scoped to one org. If single-tenant, use `createdAt` as the shard key with a hashed prefix.

### 3.3 Elasticsearch Scaling
**Why**: Full-text search across millions of documents with faceted filtering requires a proper ES cluster.

**Tasks**:
- [ ] 3-node Elasticsearch cluster (1 master, 2 data nodes minimum)
- [ ] Index lifecycle management: hot (current month) → warm (last 12 months) → cold (archive)
- [ ] Separate indices per sensitivity level for access control at the search layer
- [ ] Optimize mappings: `extractedText` as `text` type with custom analyser, `categoryName`/`sensitivityLabel` as `keyword` for exact-match facets
- [ ] Bulk indexing pipeline: instead of indexing per-document on status change, batch index every 30 seconds

### 3.4 Document Processing Optimization
**Why**: Text extraction and PII scanning are CPU-bound and can be parallelised.

**Tasks**:
- [ ] Scale doc-processor to 3-5 replicas
- [ ] Add document size-based routing: small documents (< 1MB) to fast queue, large documents (> 10MB) to dedicated heavy queue
- [ ] Parallel PII scanning: run regex patterns in parallel threads (the current sequential scan is fine for one document but a threadpool improves throughput when processing many)
- [ ] OCR optimization: only run Tesseract on image documents and image-heavy PDFs, skip for text-based documents
- [ ] Deduplication: before processing, check SHA-256 hash against existing documents

---

## Phase 4: Advanced Features (Weeks 8-16)

### 4.1 Multi-Model AI Strategy
**Why**: Different documents benefit from different models. Cost optimization at scale requires model routing.

**Tasks**:
- [ ] Implement model routing in the classification pipeline: based on document type, size, and historical accuracy
- [ ] A/B testing framework: classify the same document with two models, compare results, learn which model works best for which category
- [ ] Confidence-based escalation: start with cheapest model, escalate to more expensive model if confidence < threshold
- [ ] Fine-tuning pipeline: export correction history, fine-tune a smaller model on the organisation's specific document patterns
- [ ] Cost tracking per classification: log tokens used, calculate cost, show in AI Usage Log

### 4.2 Real-Time Document Streaming
**Why**: For organisations receiving thousands of documents per day, batch upload isn't efficient.

**Tasks**:
- [ ] Watched folder integration for local storage (the Google Drive folder monitor pattern, applied to local/S3 folders)
- [ ] Email ingestion: monitor an inbox, classify email attachments automatically
- [ ] API-based ingestion: REST endpoint for programmatic document submission with callback URL for classification results
- [ ] Webhook notifications: call customer endpoints when classification completes, when PII is detected, when review is needed

### 4.3 Advanced Search & Analytics
**Why**: Millions of documents need more than basic search — analytics, trends, and insights.

**Tasks**:
- [ ] Elasticsearch aggregation dashboards: documents by category over time, sensitivity distribution trends, PII detection rates
- [ ] Saved searches and search alerts ("notify me when a new RESTRICTED document is classified in Legal")
- [ ] Natural language search: "find all contracts with Acme Corp expiring in 2026" using LLM to translate to ES query
- [ ] Export/reporting: scheduled CSV/PDF reports for compliance teams (documents processed this month, PII summary, retention compliance)

### 4.4 SAR & Redaction (Phase 3)
**Why**: Subject Access Requests require finding all PII for a person and optionally redacting it.

**Tasks**:
- [ ] Implement PII search across all documents' extracted text (not just piiFindings)
- [ ] Document redaction: generate a copy with PII replaced by [REDACTED]
- [ ] PDF redaction using PDFBox or iText — overlay black rectangles on PII locations
- [ ] Redaction audit trail: what was redacted, by whom, for which SAR
- [ ] SAR package generation: compile all documents mentioning a person into a downloadable package

### 4.5 Additional Storage Providers
**Why**: Enterprise customers use SharePoint, S3, Box, and network shares.

**Tasks**:
- [ ] S3 provider (`S3StorageProviderService`): uses AWS SDK, config from ConnectedDrive.config map (bucket, region, access key)
- [ ] SharePoint/OneDrive provider: Microsoft Graph API, OAuth2 flow
- [ ] Box provider: Box API, OAuth2 flow
- [ ] SMB/CIFS provider: jcifs-ng library for network shares
- [ ] Each provider implements the existing `StorageProviderService` interface — no pipeline changes needed

**Guidance for Claude**: The `StorageProviderService` interface at `gls-document/src/main/java/co/uk/wolfnotsheep/document/services/StorageProviderService.java` defines the contract. `LocalStorageProviderService` and `GoogleDriveService` are reference implementations. The `StorageProviderRegistry` auto-discovers implementations. A new provider just needs to implement the interface and be annotated with `@Service`.

---

## Phase 5: Governance Hub Evolution (Weeks 10-20)

### 5.1 Hub Admin UI
**Why**: The Hub currently has an API-only admin interface. Pack authoring needs a proper UI.

**Tasks**:
- [ ] Complete the Next.js admin frontend at `web-hub/` (skeleton created, needs polish and Dockerisation)
- [ ] Pack authoring wizard: guided flow for creating a governance pack with component selection
- [ ] Visual taxonomy editor: drag-and-drop tree builder for taxonomy categories
- [ ] Schema template library: pre-built metadata schemas for common document types (invoices, contracts, HR letters)
- [ ] Version diff viewer: compare pack versions side-by-side

**Guidance for Claude**: The Hub admin frontend skeleton is at `/Users/woollers/dev/governance-led-storage/web-hub/`. It's a standalone Next.js app with login, pack management, API key management, and download audit pages. The extraction guide at `backend/GOVERNANCE-HUB-EXTRACTION.md` describes how to split it into its own repo.

### 5.2 Community Contributions
**Why**: A marketplace is only valuable if governance professionals can contribute.

**Tasks**:
- [ ] Contributor accounts (separate from tenant API keys)
- [ ] Pack submission workflow: draft → review → published
- [ ] Pack quality scoring: based on download count, reviews, and how many overrides occur after import
- [ ] Regional pack variants: "UK GDPR" vs "EU GDPR" vs "California CCPA"
- [ ] Industry-specific packs: healthcare (NHS), financial services (FCA), legal, education

### 5.3 Pack Update Notifications
**Why**: Tenants need to know when the governance frameworks they imported have been updated.

**Tasks**:
- [ ] Track which pack version each tenant imported
- [ ] Notify tenants when a new version is available (email, in-app notification)
- [ ] Diff view: show what changed between imported version and latest version
- [ ] One-click update with selective component merge (don't overwrite tenant customisations)

---

## Appendix A: Performance Benchmarks to Establish

Before scaling, establish baselines on current hardware (Mac Studio 96GB):

| Metric | Target | How to Measure |
|---|---|---|
| Documents per minute (Ollama qwen2.5:32b) | 1 | Process 10 docs, measure time |
| Documents per minute (Claude Sonnet) | 3-5 | Process 10 docs, measure time |
| Text extraction (Tika, typical PDF) | < 2 seconds | Monitor pipeline logs |
| PII scan (regex, 10K chars) | < 100ms | Monitor pipeline logs |
| Search query (1M docs, full-text) | < 500ms | Elasticsearch slow log |
| Facet count (1M docs) | < 200ms | MongoDB aggregation timing |
| Concurrent SSE clients | 50+ | Load test with k6 |
| API response time (p95) | < 200ms | Prometheus histogram |

## Appendix B: Compliance Checklist

For regulated industry deployment, verify:

- [ ] **GDPR Article 30**: Records of processing activities (the audit log covers this)
- [ ] **GDPR Article 32**: Security of processing (encryption at rest + in transit)
- [ ] **GDPR Article 35**: Data Protection Impact Assessment completed
- [ ] **GDPR Article 17**: Right to erasure (retention enforcement + SAR workflow)
- [ ] **ISO 27001**: Information security management system
- [ ] **SOC 2 Type II**: If offering as SaaS — security, availability, confidentiality, processing integrity
- [ ] **FCA SYSC 9**: Record keeping requirements (for financial services)
- [ ] **NHS DSPT**: Data Security and Protection Toolkit (for healthcare)
- [ ] **Penetration test**: Annual third-party pen test
- [ ] **Vulnerability scan**: Automated weekly scans of all containers and dependencies

## Appendix C: Key Files for Future Development

| Area | File | Purpose |
|---|---|---|
| Pipeline execution | `gls-llm-orchestration/.../ClassificationPipeline.java` | Main LLM classification flow |
| Pipeline blocks | `gls-governance/.../PipelineBlock.java` | Versioned processing units |
| MCP tools | `gls-mcp-server/.../tools/*.java` | 10 tools the LLM calls during classification |
| Document model | `gls-document/.../DocumentModel.java` | Core document entity with all fields |
| Storage providers | `gls-document/.../StorageProviderService.java` | Interface for adding new drive types |
| PII scanning | `gls-document/.../PiiPatternScanner.java` | Regex patterns loaded from Block Library |
| Governance data | `gls-governance/.../GovernanceService.java` | Service layer for all governance operations |
| AI usage logging | `gls-document/.../AiUsageLog.java` | Every AI interaction tracked |
| Config service | `gls-platform/.../AppConfigService.java` | Runtime config from MongoDB |
| Hub service | `gls-governance-hub-app/` | Separate Spring Boot service for the marketplace |
| Hub extraction | `backend/GOVERNANCE-HUB-EXTRACTION.md` | Guide to split Hub into own repo |
| Frontend pipeline editor | `web/src/components/pipeline-editor/` | n8n-style visual workflow builder |
| Frontend help system | `web/src/app/(protected)/help/` | In-app documentation with 12 articles |
