# CISO Security Assessment — Governance-Led Storage

> An honest, pre-sales security posture review. This document catalogues the questions a CISO would ask to qualify out from buying this platform, what they would find, and what needs fixing before enterprise readiness.

---

## Table of Contents

1. [Authentication & Session Management](#1-authentication--session-management)
2. [Authorization & Access Control](#2-authorization--access-control)
3. [Encryption — At Rest & In Transit](#3-encryption--at-rest--in-transit)
4. [PII Handling & Data Protection](#4-pii-handling--data-protection)
5. [LLM Security & External Data Transmission](#5-llm-security--external-data-transmission)
6. [Secrets Management](#6-secrets-management)
7. [Audit Logging & Tamper Resistance](#7-audit-logging--tamper-resistance)
8. [GDPR & Regulatory Compliance](#8-gdpr--regulatory-compliance)
9. [Network Segmentation & Exposed Services](#9-network-segmentation--exposed-services)
10. [Container & Supply Chain Security](#10-container--supply-chain-security)
11. [CI/CD Security Gates](#11-cicd-security-gates)
12. [File Upload & Input Validation](#12-file-upload--input-validation)
13. [Password Policy & Brute-Force Protection](#13-password-policy--brute-force-protection)
14. [CORS, Browser Headers & Cookie Security](#14-cors-browser-headers--cookie-security)
15. [Business Continuity & Disaster Recovery](#15-business-continuity--disaster-recovery)
16. [Vendor Risk & Sub-Processor Management](#16-vendor-risk--sub-processor-management)
17. [Key Rotation & Token Lifecycle](#17-key-rotation--token-lifecycle)
18. [Security Monitoring & Alerting](#18-security-monitoring--alerting)
19. [Incident Response](#19-incident-response)
20. [Compliance Programme](#20-compliance-programme)

---

## 1. Authentication & Session Management

**CISO question:** *"What happens when a user logs out — is the session actually destroyed?"*

**Current state: CRITICAL**

| Issue | Detail | Location |
|-------|--------|----------|
| No server-side token revocation | Logout clears the browser cookie but the JWT remains valid for up to **48 hours**. No blacklist, no JTI tracking, no Redis-backed deny list. A stolen token is valid until expiry. | `AuthController.java` |
| JWT exposed in OAuth redirect URL | `OAuth2LoginSuccessHandler` redirects to `/oauth2/redirect?token=<JWT>`, exposing the token in browser history, server logs, and Referer headers. | `OAuth2LoginSuccessHandler.java` |
| JWT returned in response body | `LoginResponse` includes the token in the JSON body alongside the HttpOnly cookie, creating a JavaScript-accessible copy. | `AuthController.java` |
| No refresh token flow | Users must re-authenticate after 48 hours. No token rotation, no sliding window. | `JwtUtilsWithCookieSupport.java` |
| JWT logged at TRACE level | `log.debug("AuthTokenFilter.java: {}", jwt)` logs raw tokens. Base config sets `co.uk.wolfnotsheep: TRACE`, meaning local dev and any deployment without explicit log level override exposes every token. | `AuthTokenFilter.java:85` |

**Remediation:**
- Implement server-side revocation (per-user `tokenValidFrom` timestamp or Redis blacklist)
- Remove JWT from OAuth redirect URLs — set the cookie directly on the callback response
- Remove JWT from response body (cookie-only)
- Add refresh token rotation with short-lived access tokens (15 min) and longer refresh tokens
- Gate TRACE logging behind a dev-only profile

---

## 2. Authorization & Access Control

**CISO question:** *"How do you enforce that every API endpoint requires authentication?"*

**Current state: CRITICAL**

| Issue | Detail | Location |
|-------|--------|----------|
| `anyRequest().permitAll()` | The Spring Security filter chain ends with `anyRequest().permitAll()`. Authentication is **not enforced at the framework level**. Every controller must individually check `@AuthenticationPrincipal`. Any endpoint that forgets is publicly accessible. | `DefaultSecurityConfig.java:84` |
| Zero `@PreAuthorize` annotations | No method-level security annotations exist anywhere in the codebase. | All controllers |
| `/api/internal/**` is unauthenticated | CSRF-exempt and unprotected by design ("not externally exposed"), but only network topology prevents access. `PipelineWebhookController` accepts unauthenticated POST requests. | `DefaultSecurityConfig.java`, `PipelineWebhookController.java` |
| `/api/public/config` exposes secrets | `GET /api/public/config` without a category filter returns **all** `app_config` entries — including OAuth client secrets, Anthropic API keys, and Hub API keys — to unauthenticated callers. | `PublicConfigController.java` |

**Remediation:**
- Change to `anyRequest().authenticated()` and explicitly enumerate public paths
- Add `@PreAuthorize` annotations or a global method-security policy
- Whitelist-filter `/api/public/config` to non-sensitive categories only
- Add authentication to `/api/internal/**` or bind it to a localhost-only listener

---

## 3. Encryption — At Rest & In Transit

**CISO question:** *"Is our data encrypted at rest and in transit between services?"*

**Current state: CRITICAL**

| Issue | Detail | Location |
|-------|--------|----------|
| No encryption at rest | MongoDB, MinIO, and Elasticsearch store data as plaintext on bind-mounted volumes (`./data/mongo`, `./data/minio`). No application-layer encryption. No `Cipher`, `AES`, or `SecretKeySpec` usage. | All storage services |
| No TLS between services | All inter-service traffic (api→mongo, api→minio, api→rabbitmq, api→elasticsearch) is plaintext HTTP inside the Docker network. | `application.yaml`, `docker-compose.yml` |
| MongoDB has no TLS | Connection URI has no `tls=true` parameter. | `application.yaml` |
| Elasticsearch security disabled | `xpack.security.enabled=false` in **both** dev and prod compose files. No auth, no TLS. | `docker-compose.yml`, `docker-compose.prod.yml` |
| HSTS served over HTTP | `Strict-Transport-Security` header is set on the nginx port 80 listener. HSTS only has meaning on HTTPS responses. | `nginx/conf.d/default.conf` |
| Google OAuth tokens stored unencrypted | `ConnectedDrive.accessToken` and `refreshToken` are plain strings in MongoDB. These are long-lived tokens granting access to users' Google Drives. | `ConnectedDrive.java` |
| `StorageTier.encryptionType` is cosmetic | The governance model has an encryption type field but no enforcement — it's a label, not a mechanism. | `StorageTier.java` |

**Remediation:**
- Enable MongoDB TLS (certificates + `tls=true` in URI)
- Enable Elasticsearch `xpack.security` with TLS and authentication in production
- Encrypt Google OAuth tokens with AES-GCM before writing to MongoDB (field-level encryption)
- Consider MongoDB Client-Side Field Level Encryption (CSFLE) for sensitive fields
- Move HSTS to the Cloudflare or TLS termination layer

---

## 4. PII Handling & Data Protection

**CISO question:** *"How is PII stored, masked, and protected throughout the system?"*

**Current state: HIGH**

| Issue | Detail | Location |
|-------|--------|----------|
| PII stored in plaintext | `DocumentModel.piiFindings` stores matched PII values (`matchedText`) unencrypted in MongoDB. | `DocumentModel.java` |
| Original files not redacted | `PiiRedactionService` replaces PII in `extractedText` within MongoDB but the original file in MinIO is unchanged. | `PiiRedactionService.java` |
| PII sent to Anthropic API | Up to 100,000 characters of raw document text — PII included — is sent to Anthropic with no pre-send redaction. | `ClassificationPromptBuilder.java` |
| Full prompts persisted in `ai_usage_log` | `AiUsageLog` stores the complete system prompt and user prompt (containing document text with PII), creating a shadow data store. | `AiUsageLog.java` |
| No PII masking in application logs | Only basic regex sanitisation in `AuditInterceptor`. No structured PII filtering. TRACE-level security logging can expose usernames and auth decisions. | `AuditInterceptor.java` |

**Remediation:**
- Run the Tier-1 regex PII scanner **before** sending text to Anthropic, and redact findings
- Encrypt PII findings at rest or store only redacted references
- Implement a retention policy for `ai_usage_log` with automatic purging
- Add structured PII masking to the logging framework

---

## 5. LLM Security & External Data Transmission

**CISO question:** *"Is document content sent to external AI providers? What prevents prompt injection?"*

**Current state: HIGH**

| Issue | Detail | Location |
|-------|--------|----------|
| Full document text to Anthropic | Up to 100,000 chars of raw text sent externally. If the document contains confidential, privileged, or classified content, it leaves the perimeter. | `ClassificationPromptBuilder.java` |
| No prompt injection mitigation | Document text is string-interpolated via `.replace("{extractedText}", ...)` with no escaping or structural separation. A malicious document could manipulate classification. | `ClassificationPromptBuilder.java` |
| No content filtering pre-send | No check for sensitivity level, classification, or document type before sending to external LLM. | Pipeline services |
| LLM worker has `anyRequest().permitAll()` | The LLM orchestration service has no authentication. If network segmentation fails, any caller can trigger classification. | `SecurityConfig.java` (igc-llm-orchestration) |

**Remediation:**
- Add a pre-send PII redaction step using the existing regex scanner
- Allow admins to configure which sensitivity levels may be sent to external LLMs vs local Ollama
- Implement structural prompt separation (XML tags, delimiters) rather than raw string interpolation
- Authenticate the LLM worker's internal API

---

## 6. Secrets Management

**CISO question:** *"What secrets management solution do you use? How are secrets rotated?"*

**Current state: HIGH**

| Issue | Detail | Location |
|-------|--------|----------|
| No secrets manager | All secrets flow via `.env` files. No Vault, no KMS, no AWS Secrets Manager. | Project-wide |
| Hardcoded fallback JWT secret | `application.yaml` contains a default JWT signing key used if `JWT_SECRET` env var is missing. | `application.yaml` |
| Grafana password hardcoded | `GF_SECURITY_ADMIN_PASSWORD=admin` in docker-compose, not parameterised. | `docker-compose.yml` |
| Default passwords in source | `ChangeMe123!` appears as default in `HubSecurityConfig.java`, `hub/docker-compose.yml`, and `application.yaml`. | Multiple files |
| RabbitMQ default credentials | `guest/guest` fallback if env vars not set. | `docker-compose.yml` |
| Anthropic API key in plaintext MongoDB | Stored as a plain `Object` value in `app_config` collection, retrievable via unauthenticated `/api/public/config`. | `AppConfigService.java` |

**Remediation:**
- Adopt a secrets manager (HashiCorp Vault, AWS Secrets Manager, or Docker secrets at minimum)
- Remove all hardcoded fallback secrets from source
- Parameterise every credential through env vars with no defaults
- Implement secret rotation procedures

---

## 7. Audit Logging & Tamper Resistance

**CISO question:** *"Is the audit log tamper-proof? Does it cover read access?"*

**Current state: MEDIUM**

| Issue | Detail | Location |
|-------|--------|----------|
| Not tamper-proof | Audit events are plain MongoDB documents. Anyone with DB access can modify or delete them. No cryptographic chaining, no WORM storage, no append-only enforcement. | `system_audit_log` collection |
| GET requests not audited | Document downloads, PII viewing, search queries, and SAR content reads leave no audit trail. | `AuditInterceptor.java` |
| Auth events not persisted to audit DB | Login success/failure goes to `log.warn()` only — not to the `system_audit_log` or `audit_events` collections. | `DefaultUserAuthAuditService.java` |
| Sanitisation is brittle | Regex `(password|secret|token|key)=[^,\]]*` misses JSON bodies, nested objects, and unrecognised field names. | `AuditInterceptor.java` |

**Remediation:**
- Write audit events to an append-only store (separate MongoDB collection with restricted write access, or an external SIEM)
- Extend auditing to cover GET requests on sensitive resources
- Persist auth events (login, logout, failed attempts) to the audit database
- Consider cryptographic hash chaining for tamper evidence

---

## 8. GDPR & Regulatory Compliance

**CISO question:** *"Can you demonstrate GDPR Article 17 (right to erasure) compliance?"*

**Current state: HIGH**

| Issue | Detail | Location |
|-------|--------|----------|
| No automated erasure flow | No user deletion endpoint. No coordinated deletion across MinIO, MongoDB, Elasticsearch, and audit logs. | `AdminUserController.java` |
| SAR workflow is a scaffold | `SubjectAccessRequest` model has status tracking but no automated data export or compilation. | `SubjectAccessRequest.java` |
| No data retention policy enforcement | No TTL indexes, no automated purging of aged documents, no retention schedule enforcement. | Project-wide |
| No Records of Processing Activities | No ROPA documentation or Article 30 register. | N/A |
| No DPIA | No Data Protection Impact Assessment despite processing special category data and using AI profiling. | N/A |
| Document content sent to external processor (Anthropic) without DPA | Raw text including PII transmitted externally with no documented data processing agreement. | `ClassificationPromptBuilder.java` |

**Remediation:**
- Build an erasure workflow that coordinates: MinIO deletion, MongoDB purge, Elasticsearch removal, and audit log handling
- Implement data retention policies with automated enforcement
- Complete a DPIA for the LLM classification pipeline
- Establish DPAs with Anthropic and Google as sub-processors
- Create a ROPA

---

## 9. Network Segmentation & Exposed Services

**CISO question:** *"Which management ports are accessible from outside the Docker network?"*

**Current state: CRITICAL**

The following ports are bound to `0.0.0.0` (all host interfaces):

| Port | Service | Auth | Risk |
|------|---------|------|------|
| `9200` | Elasticsearch | **None** (`xpack.security.enabled=false`) | Unauthenticated access to all indexed data |
| `15672` | RabbitMQ Management | `guest/guest` defaults | Queue manipulation, message inspection |
| `9001` | MinIO Console | env-var credentials | Admin access to all stored documents |
| `9000` | MinIO API | env-var credentials | S3 API access to all objects |
| `9090` | Prometheus | **None** | Full metrics exposure |
| `3003` | Grafana | `admin/admin` hardcoded | Dashboard access, potential data source queries |

All services share a single flat Docker network (`igc`). No network isolation between application services, data stores, and monitoring.

**Remediation:**
- Bind management ports to `127.0.0.1` only in production
- Create separate Docker networks (frontend, backend, monitoring) with explicit inter-network rules
- Enable Elasticsearch `xpack.security` with authentication
- Change all default credentials

---

## 10. Container & Supply Chain Security

**CISO question:** *"Are your container images scanned, signed, and running as non-root?"*

**Current state: HIGH**

| Issue | Detail | Location |
|-------|--------|----------|
| All containers run as root | No `USER` directive in any of the 7 Dockerfiles. All processes execute as UID 0. | All Dockerfiles |
| No image signing | No Cosign, Sigstore, or Docker Content Trust. | CI/CD |
| No SBOM generation | No CycloneDX, Syft, or `bom.xml`. | Project-wide |
| Mutable base image tags | `grafana/grafana:latest`, `prom/prometheus:latest`, `cloudflare/cloudflared:latest` — unpredictable upgrade surface. | `docker-compose.yml` |
| No container vulnerability scanning | No Trivy, Snyk Container, or Grype in CI. | `.github/workflows/ci.yml` |

**Remediation:**
- Add `USER` directives to all Dockerfiles (non-root)
- Pin base images to digests, not mutable tags
- Add container image scanning (Trivy) to CI before GHCR push
- Generate and publish SBOM with each release
- Sign images with Cosign

---

## 11. CI/CD Security Gates

**CISO question:** *"What security checks run before code reaches production?"*

**Current state: HIGH**

| Issue | Detail | Location |
|-------|--------|----------|
| No SAST | No static analysis security testing (Semgrep, CodeQL, SonarQube). | `.github/workflows/ci.yml` |
| No DAST | No dynamic application security testing (OWASP ZAP). | N/A |
| No dependency scanning | No `npm audit`, no OWASP Dependency-Check Maven plugin, no Dependabot. | `.github/workflows/ci.yml` |
| No secret scanning | No TruffleHog, gitleaks, or GitHub secret scanning. | N/A |
| Release pushes unscanned images | `release.yml` pushes to GHCR immediately after `docker compose build` with no scanning gate. | `.github/workflows/release.yml` |

**Remediation:**
- Add SAST (CodeQL or Semgrep) to CI
- Add dependency scanning (`npm audit --audit-level=high`, OWASP Dependency-Check)
- Add container scanning (Trivy) before image push
- Enable GitHub secret scanning and Dependabot
- Gate releases on all security checks passing

---

## 12. File Upload & Input Validation

**CISO question:** *"How do you validate uploaded files? Is there antivirus scanning?"*

**Current state: HIGH**

| Issue | Detail | Location |
|-------|--------|----------|
| No file type validation | `DocumentService.ingest()` accepts any `MultipartFile`. Content type is taken from client-declared `file.getContentType()` — no server-side magic-byte validation, no allowlist. | `DocumentService.java` |
| No antivirus scanning | No ClamAV or similar malware scanning on upload. | Project-wide |
| No `@Valid` on request bodies | Bean Validation dependency exists but is not applied to any controller parameters. | All controllers |
| Dynamic field path injection in search | User-controlled `key` from `request.metadata()` is used to build a MongoDB field path: `"extractedMetadata." + key`. Could pivot queries to unintended fields. | `SearchController.java` |
| Partial regex injection | `value.contains("*")` constructs a raw regex without full quoting when wildcards are present. | `SearchController.java` |

**Remediation:**
- Implement file type allowlist with magic-byte validation
- Add ClamAV scanning on upload (or async post-upload)
- Apply `@Valid` annotations to all controller request bodies
- Whitelist permitted metadata field names in search
- Use `Pattern.quote()` consistently

---

## 13. Password Policy & Brute-Force Protection

**CISO question:** *"What prevents credential stuffing and brute-force attacks?"*

**Current state: HIGH**

| Issue | Detail | Location |
|-------|--------|----------|
| No password complexity enforcement | Signup validates only null/blank. No minimum length, no complexity rules, no breach database check. | `SignupController.java` |
| No account lockout | `DefaultUserAuthAuditService.recordLoginFailure()` only calls `log.warn()`. No counter, no threshold, no lockout. The `UserModel.Locks.accountNonLocked` field exists but nothing sets it to `false`. | `DefaultUserAuthAuditService.java` |
| Login rate limit only | nginx limits login to 5 req/min per IP, but `/api/auth/public/signup` and OAuth endpoints are unlimited. | `nginx/conf.d/production.conf` |
| User enumeration | Distinct error messages for "user not found" vs "bad password" allow username enumeration. | `AuthController.java` |

**Remediation:**
- Enforce password complexity (min 12 chars, mixed case, digits, symbols)
- Implement account lockout after N failed attempts (e.g. 5 in 15 min)
- Rate-limit signup and OAuth callback endpoints
- Return a generic "invalid credentials" message for all auth failures

---

## 14. CORS, Browser Headers & Cookie Security

**CISO question:** *"Are cookies secure? Is there a Content Security Policy?"*

**Current state: HIGH**

| Issue | Detail | Location |
|-------|--------|----------|
| `secure=false` on JWT cookie | The Spring-issued `access_token` cookie is sent over HTTP unconditionally. | `AuthController.java` |
| Hub CORS wildcard with credentials | `setAllowedOriginPatterns(List.of("*"))` + `setAllowCredentials(true)` — any origin can make credentialed requests to the Hub. | `HubSecurityConfig.java` |
| No Content-Security-Policy | CSP is the primary browser defence against XSS. Not set in nginx, Spring, or Next.js. | `nginx/conf.d/default.conf`, `DefaultSecurityConfig.java` |
| No OAuth state parameter | Google OAuth callback has no `state` parameter to prevent CSRF on the authorization flow. | `GoogleAuthController.java` |

**Remediation:**
- Set `secure=true` on all cookies (derive from environment/profile)
- Restrict Hub CORS to specific allowed origins
- Implement a CSP header (start with report-only, then enforce)
- Add OAuth `state` parameter with CSRF validation

---

## 15. Business Continuity & Disaster Recovery

**CISO question:** *"What's your RTO and RPO? How do you handle data centre failure?"*

**Current state: HIGH**

| Issue | Detail | Location |
|-------|--------|----------|
| Every service is a single point of failure | Single MongoDB (no replica set), single Elasticsearch (single-node), single RabbitMQ, single MinIO, single backend instance. | `docker-compose.yml` |
| Docker Compose only | No orchestrator (Kubernetes, ECS). No rolling deployments, no auto-scaling, no health-based recovery beyond `restart: always`. | `docker-compose.yml` |
| Manual backup only | `backup.sh` runs `mongodump` to a local directory. No encryption, no offsite copy, no scheduling. ES and RabbitMQ not backed up. | `scripts/backup.sh` |
| No documented RTO/RPO | No recovery time or recovery point objectives defined. | N/A |
| No restore procedure | Backup exists but no documented or tested restore runbook. | N/A |

**Remediation:**
- Define RTO/RPO targets appropriate to the data classification
- Deploy MongoDB as a replica set (minimum 3 nodes)
- Automate encrypted backups with offsite transfer (S3, GCS)
- Document and regularly test restore procedures
- Consider container orchestration (Kubernetes) for HA

---

## 16. Vendor Risk & Sub-Processor Management

**CISO question:** *"What DPAs do you have with your AI and cloud sub-processors?"*

**Current state: MEDIUM**

| Issue | Detail | Location |
|-------|--------|----------|
| No DPA with Anthropic | Document text (PII included) is sent to Anthropic's API with no documented data processing agreement. | `ClassificationPromptBuilder.java` |
| No DPA with Google | Google Drive OAuth integration stores and transmits user data with no referenced DPA. | `GoogleDriveService.java` |
| No sub-processor register | No list of sub-processors, their data access scope, or geographic locations. | N/A |
| No data residency controls | No ability to restrict which region LLM API calls are routed to. | N/A |
| Local Ollama option exists but not default | Ollama can avoid external transmission but is not the default path. | `LlmClientFactory.java` |

**Remediation:**
- Execute DPAs with Anthropic and Google
- Maintain a sub-processor register
- Allow admins to enforce local-only LLM processing for sensitive documents
- Document data flows and geographic boundaries

---

## 17. Key Rotation & Token Lifecycle

**CISO question:** *"How do you rotate cryptographic keys and API credentials?"*

**Current state: HIGH**

| Issue | Detail | Location |
|-------|--------|----------|
| No JWT key rotation | Static `JWT_SECRET` set at deployment. Changing it invalidates all sessions with no graceful migration. No dual-key support. | `JwtUtilsWithCookieSupport.java` |
| No API key rotation | Anthropic API key, Google OAuth credentials — no rotation mechanism or scheduled renewal. | `AppConfigService.java` |
| Hub API keys use SHA-256 | Plain SHA-256 (not PBKDF2/bcrypt/Argon2) makes stored hashes vulnerable to brute-force if the database is compromised. | `HubApiKey.java` |
| No Google OAuth token rotation | Beyond basic `tokenExpiresAt` tracking, no proactive refresh rotation or token binding. | `ConnectedDrive.java` |

**Remediation:**
- Implement dual-key JWT signing (old key verifies, new key signs) for zero-downtime rotation
- Hash Hub API keys with bcrypt or Argon2
- Document and automate key rotation procedures
- Add Google OAuth token rotation with proactive refresh

---

## 18. Security Monitoring & Alerting

**CISO question:** *"What security events generate alerts, and who gets paged?"*

**Current state: HIGH**

| Issue | Detail | Location |
|-------|--------|----------|
| Zero security alerts configured | Prometheus has no `rule_files` or `alerting` block. No AlertManager. No PagerDuty/OpsGenie. | `monitoring/prometheus.yml` |
| Grafana dashboards are operational only | 5 panels: JVM heap, HTTP rate, latency, RabbitMQ depth, pipeline status. No security metrics. | `monitoring/grafana/` |
| Failed logins produce only `log.warn()` | No aggregation, no threshold detection, no alert. | `DefaultUserAuthAuditService.java` |
| No anomaly detection | Unusual LLM API spend, mass document access, privilege escalation — none trigger any notification. | Project-wide |

**Events that should alert but don't:**
- Failed login spike (credential stuffing)
- Rate limit violations
- JWT validation failures
- Admin config changes
- Bulk document download/search
- LLM API spend anomaly

**Remediation:**
- Configure Prometheus alerting rules for security events
- Integrate with PagerDuty/OpsGenie for on-call notification
- Add security dashboards to Grafana (failed logins, auth errors, rate limit hits)
- Consider a SIEM integration for log aggregation and correlation

---

## 19. Incident Response

**CISO question:** *"Show me your incident response plan and last tabletop exercise."*

**Current state: MEDIUM (process gap)**

- No incident response plan or playbook exists in the repository
- No runbooks for common security scenarios (compromised credentials, data breach, service compromise)
- No documented escalation path or communication templates
- No evidence of tabletop exercises or IR drills
- No forensic readiness (log retention policies, evidence preservation procedures)

**Remediation:**
- Create an incident response plan covering: detection, containment, eradication, recovery, lessons learned
- Write runbooks for top scenarios: credential compromise, data breach, ransomware, insider threat
- Schedule quarterly tabletop exercises
- Define log retention periods sufficient for forensic investigation

---

## 20. Compliance Programme

**CISO question:** *"What certifications do you hold? Show me your SOC 2 Type II report."*

**Current state: MEDIUM (process gap)**

- No SOC 2, ISO 27001, Cyber Essentials, or any security certification
- The production roadmap acknowledges all compliance items as unchecked (Appendix B)
- No controls mapping to any framework
- No evidence collection or continuous compliance monitoring
- No penetration testing history (listed as future: "annual third-party pen test")
- No vulnerability management programme (listed as future: "automated weekly scans")

**Target certifications for enterprise sales:**

| Framework | Relevance |
|-----------|-----------|
| SOC 2 Type II | Required by most enterprise procurement |
| ISO 27001 | International standard, expected in EU/UK markets |
| Cyber Essentials Plus | UK government supply chain requirement |
| GDPR Article 32 compliance | Legal obligation for processing EU personal data |
| NHS DSPT | Required for health sector customers |

**Remediation:**
- Select a target framework (SOC 2 Type II is the usual starting point)
- Engage a compliance platform (Vanta, Drata, or Secureframe)
- Map existing controls and identify gaps
- Commission an annual penetration test
- Establish a vulnerability management programme with SLA-driven remediation

---

## 21. Security & Data Protection Positives

Not everything is a gap. The platform has meaningful security foundations that a CISO should weigh against the risks above. These represent genuine architectural decisions, not checkbox features.

### 21.1 ISO 15489 Records Management Framework

The platform implements the international standard for records management (ISO 15489-1:2016), not a homegrown classification scheme. This means:

- **Business Classification Schemes** with materialised paths, hierarchical taxonomy, and classification codes
- **Retention schedules** with ISO 8601 durations, trigger types (`DATE_CREATED`, `DATE_CLOSED`, `EVENT_BASED`, `END_OF_FINANCIAL_YEAR`, `SUPERSEDED`), and disposition actions (`DELETE`, `ARCHIVE`, `TRANSFER`, `REVIEW`, `ANONYMISE`, `PERMANENT`)
- **Legal hold override** flag on retention schedules — disposition is blocked when litigation hold is active
- **Jurisdiction-aware retention** with regulatory basis, legislation references (e.g. "Section 386, Companies Act 2006"), and schedule reference numbers
- **Automated disposition enforcement** via `EnforcementService` and `RetentionScheduledTask`

This is the correct foundation for regulated environments. Most competing platforms implement retention as an afterthought; here it's a first-class domain model.

### 21.2 Three-Dimensional Document Access Control

`DocumentAccessService` enforces three independent gates, all of which must pass:

| Gate | Mechanism | Detail |
|------|-----------|--------|
| **Sensitivity clearance** | Numeric level comparison | 4 levels: PUBLIC (0), INTERNAL (1), CONFIDENTIAL (2), RESTRICTED (3). User clearance must meet or exceed document sensitivity. |
| **Taxonomy grant** | Time-bounded, per-category | Grants have `expiresAt`, optional `includeChildren` with BFS traversal, and per-operation scoping (`READ`, `WRITE`, `DELETE`). |
| **Query-level enforcement** | MongoDB criteria injection | `buildAccessCriteria()` pushes access checks into the database query itself, preventing over-fetching before application-level filtering. |

Admins bypass all gates. This is a materially stronger access model than simple role-based checks.

### 21.3 Human-in-the-Loop AI with Dual-Threshold Confidence Routing

The classification pipeline does not blindly trust the LLM:

- **Below 0.70 confidence** → `REVIEW_REQUIRED`, routed to human review queue
- **0.70–0.95 confidence** → requires explicit human approval
- **Above 0.95 confidence** → auto-approved
- **PII escalation** → if new PII is found at a higher sensitivity than the existing label, `REVIEW_REQUIRED` is triggered regardless of confidence
- **Both thresholds are runtime-configurable** via `AppConfigService` — no redeployment needed

The review queue supports `approve`, `override`, and `reject` actions, each producing a `ClassificationCorrection` that feeds back into future classifications.

### 21.4 Classification Correction Feedback Loop (Few-Shot Learning)

Human corrections are not just recorded — they are actively surfaced to the LLM at inference time via MCP tools:

| MCP Tool | Purpose |
|----------|---------|
| `CorrectionHistoryTool` | Returns past human corrections for the target category/MIME type. The LLM is instructed: "ALWAYS call this BEFORE making a classification decision." |
| `OrgPiiPatternsTool` | Returns organisation-specific PII patterns derived from corrections |
| `TraitDetectionTool` | Surfaces document traits from prior classifications |
| `MetadataSchemasTool` | Provides typed extraction schemas linked to categories |
| `RetentionScheduleTool` | Returns applicable retention rules for governance enforcement |

This creates a learning system where accuracy improves with use, without fine-tuning or retraining.

### 21.5 BERT Accelerator — Path to Zero External LLM Dependency

The platform has a complete pipeline for eliminating external LLM calls:

1. **Automatic training data collection** — LLM classifications above configurable confidence (default 0.8) are automatically collected as `TrainingDataSample` records
2. **Human correction ingestion** — review queue corrections feed directly into training data
3. **Historical backfill** — `BertTrainingDataBackfillRunner` mines past classifications
4. **ONNX inference sidecar** — `BertClassifierService` calls a FastAPI/ONNX Python service; if BERT confidence exceeds threshold (default 0.85), the LLM call is skipped entirely
5. **Graceful fallback** — BERT predictions of `_OTHER` or below threshold silently escalate to LLM

At maturity, this means: no document content leaves the perimeter. The LLM becomes the exception path, not the default. This directly addresses the data sovereignty concerns in Section 5.

### 21.6 Local LLM Option (Ollama) — On-Premise Data Sovereignty

Setting `llm.provider=ollama` in the admin UI switches the entire classification pipeline to a local Ollama instance. No document content leaves the infrastructure. The switch requires only an LLM worker restart, not a code change or redeployment. This gives organisations a spectrum:

- **Maximum accuracy:** Anthropic Claude (external, highest capability)
- **Data sovereign:** Ollama (local, no data egress)
- **Maximum efficiency:** BERT accelerator (local, sub-second, no LLM at all)

### 21.7 Two-Tier PII Detection

| Tier | Mechanism | Detail |
|------|-----------|--------|
| **Tier 1 — Regex** | `PiiPatternScanner` with configurable patterns from MongoDB | Patterns are versioned pipeline blocks, editable via the Block Library UI. Includes: UK NI, NHS number, email, phone, postcode, sort code, DOB, credit card, person names, street addresses, medical identifiers. 60-second cache with manual invalidation. |
| **Tier 2 — LLM** | Contextual detection during classification | Catches PII that regex cannot (e.g. contextual references, implied identifiers) |

Previously dismissed PII findings are **auto-dismissed on rescan** (keyed by type + matched text), preserving the dismissal reason and who dismissed it. This prevents alert fatigue from known false positives.

### 21.8 Comprehensive Write Audit Trail

`AuditInterceptor` uses AOP to intercept every `POST`, `PUT`, `DELETE`, and `PATCH` across all controller packages. Each audit record captures:

- User identity (ID, email)
- HTTP method, endpoint URI, client IP (X-Forwarded-For aware), user agent
- Action and resource type (derived from method/controller names)
- Resource ID (extracted from path)
- Sanitised request summary (`password|secret|token|key` values masked to `***`)
- Response status and success/failure outcome
- Timestamp

Document-level lifecycle events (upload, delete, status transitions) are separately audited via `AuditEvent`. The interceptor is framework-enforced via AOP — individual controllers cannot opt out.

### 21.9 Pipeline Resilience — Explicit Failure States with Automatic Recovery

The pipeline does not silently drop documents:

| Mechanism | Detail |
|-----------|--------|
| **Explicit failure statuses** | `PROCESSING_FAILED`, `CLASSIFICATION_FAILED`, `ENFORCEMENT_FAILED` — each with `lastError`, `lastErrorStage`, `failedAt` |
| **Stale document recovery** | `StaleDocumentRecoveryTask` runs every 5 minutes, detecting documents stuck in in-flight states for >15 minutes |
| **Automatic retry with limits** | `MAX_AUTO_RETRIES = 3` — after exhaustion, document is failed with error preserved |
| **Re-queue cooldown** | 30-minute cooldown on PROCESSED re-queuing prevents runaway queue growth when the LLM worker is overwhelmed |
| **Error persistence** | All errors written to `SystemError` collection with full context, visible on the admin monitoring page |
| **Pipeline execution tracking** | `PipelineRun` and `NodeRun` records track node-level execution state |

### 21.10 Password Hashing — BCrypt

All user passwords are hashed with `BCryptPasswordEncoder` (Spring Security standard). Both the main application and the Governance Hub maintain independent BCrypt beans. Raw passwords are never stored.

### 21.11 CSRF Protection

Cookie-based CSRF is active via `CookieCsrfTokenRepository.withHttpOnlyFalse()`, allowing the frontend to read `XSRF-TOKEN` and submit it as a header. Exclusions are limited to public auth endpoints, internal pipeline webhooks, and actuator health. On logout, the CSRF token cookie is explicitly cleared.

### 21.12 CORS — Restricted to Configured Origin

The main application restricts allowed origins to the single configured `${public.url}` value — not a wildcard. Credentials are enabled. Max age is 3600 seconds. (The Hub uses a wildcard pattern — flagged in Section 14.)

### 21.13 Security Headers — Full nginx Suite

nginx applies five security headers with `always`:

| Header | Value |
|--------|-------|
| `Strict-Transport-Security` | `max-age=63072000; includeSubDomains; preload` (2-year HSTS) |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `SAMEORIGIN` (also set in Spring Security as defence-in-depth) |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` |

### 21.14 HttpOnly JWT Cookies

The `access_token` JWT is issued as an `HttpOnly` cookie with `SameSite=Lax`, preventing JavaScript access and mitigating CSRF on cross-origin requests.

### 21.15 MongoDB Injection Prevention

All user-supplied search strings are wrapped with `Pattern.quote()` before interpolation into MongoDB regex queries — applied consistently across 10+ call sites in document search, classification code search, MIME type filter, uploader filter, and PII search.

### 21.16 Path Traversal Prevention

Storage keys are generated as `UUID.randomUUID() + extension` — the original filename is never used in the storage path. Email ingestion uses `UUID.randomUUID() + "/" + sanitizedFilename`. This prevents path traversal and filename-based enumeration in MinIO/S3.

### 21.17 Slug-Based URLs — ID Enumeration Prevention

Public-facing document URLs use slugs (`hr-policy-2024-ab3f7c`) rather than raw MongoDB ObjectIds. Slugs are generated from `{slugified-name}-{last-6-chars-of-id}`, making sequential enumeration impractical. A `SlugBackfillRunner` retroactively populates slugs for pre-existing documents.

### 21.18 Account Locking Infrastructure

`UserModel` implements Spring Security's `UserDetails` with four independent lock flags: `accountNonLocked`, `accountNonExpired`, `accountNonDisabled`, `accountNonBanned`. Credentials expiry is enforced via `credentialsExpiryDate`. `AuthController` catches and differentiates `DisabledException` and `LockedException` with distinct 403 codes. The infrastructure exists — what's missing is automated lockout triggering (see Section 13).

### 21.19 Login Rate Limiting

nginx enforces 5 requests/minute per IP on the login endpoint with burst of 3 and `nodelay`. Document upload is limited to 2 requests/minute with burst of 20. Both return HTTP 429.

### 21.20 Permission-Key Based Authorization (Not Role-Name Checks)

Access checks are against stable permission strings (`PERM_documents.review`) rather than role names. Roles bundle feature IDs; `RolePermissionSyncService` resolves the chain to `permissionKey` values written to the user model. Roles can be restructured without changing enforcement logic.

### 21.21 Multi-Stage Docker Builds

All backend Dockerfiles use a two-stage build: JDK for compilation, JRE for runtime. Maven wrapper, source code, and build tools are excluded from the final image. The runtime image includes only the JAR and `curl` for health checks.

### 21.22 Secrets Excluded from Version Control

`.gitignore` explicitly excludes `.env`, `.env.local`, `.env.*.local`, `data/` bind-mount directories, IDE files, and build artifacts. No credentials are tracked in git.

### 21.23 Governance Hub — Shared Marketplace with API Key Isolation

The Governance Hub runs as a separate Spring Boot application with its own security config and database. Tenant API keys are generated from `SecureRandom` (32 bytes), SHA-256 hashed before storage (plaintext shown once), and carry a `rateLimit` field per key. Governance packs are versioned, diffable, and reviewable.

### 21.24 Configuration-Driven Runtime Behaviour

`AppConfigService` provides a MongoDB-backed key-value store with `ConcurrentHashMap` caching. LLM provider, confidence thresholds, BERT training parameters, and pipeline behaviour are all runtime-configurable without redeployment. This reduces the frequency of code changes needed for operational tuning — fewer deployments means fewer opportunities for configuration drift.

### 21.25 Versioned Pipeline Blocks with Feedback Tracking

Every prompt, regex pattern set, extractor, router, and enforcer is a versioned block with immutable version history. Publishing creates a new version — never overwrites. User corrections automatically create block feedback (`CORRECTION`, `FALSE_POSITIVE`, `MISSED`, `SUGGESTION`), enabling data-driven improvement. Admins can roll back to any previous version.

---

### Positives Summary

| Category | Capability | Maturity |
|----------|-----------|----------|
| Records management | ISO 15489 BCS, retention schedules, legal hold, disposition automation | Strong |
| Access control | Three-dimensional gates (sensitivity + taxonomy + query-level) | Strong |
| AI governance | Dual-threshold confidence routing, human review queue, correction feedback loop | Strong |
| Data sovereignty | Ollama local LLM, BERT accelerator path to zero external calls | Strong |
| PII detection | Two-tier (regex + LLM), configurable patterns, false positive suppression | Good |
| Audit trail | AOP-enforced write auditing across all controllers | Good |
| Pipeline resilience | Explicit failure states, automatic recovery, retry limits, cooldowns | Good |
| Auth foundations | BCrypt, CSRF, HttpOnly cookies, CORS restriction, security headers | Good (with gaps) |
| Injection prevention | Pattern.quote(), UUID storage keys, slug URLs | Good |
| Operational agility | Runtime config, versioned blocks, no-redeploy tuning | Good |

These positives mean the platform is not starting from zero. The domain model and data protection architecture are materially ahead of most early-stage platforms. The gaps identified in Sections 1–20 are predominantly infrastructure hardening and operational security — fixable without architectural redesign.

---

## Risk Summary Matrix

| # | Area | Severity | Enterprise Blocker? |
|---|------|----------|-------------------|
| 1 | Authentication & session management | CRITICAL | Yes |
| 2 | Authorization (`permitAll()`) | CRITICAL | Yes |
| 3 | Encryption at rest & in transit | CRITICAL | Yes |
| 4 | PII handling | HIGH | Yes |
| 5 | LLM data transmission | HIGH | Yes |
| 6 | Secrets management | HIGH | Yes |
| 7 | Audit logging | MEDIUM | Likely |
| 8 | GDPR compliance | HIGH | Yes (EU/UK) |
| 9 | Exposed management ports | CRITICAL | Yes |
| 10 | Container & supply chain | HIGH | Likely |
| 11 | CI/CD security gates | HIGH | Likely |
| 12 | File upload & input validation | HIGH | Yes |
| 13 | Password policy & brute-force | HIGH | Yes |
| 14 | CORS, headers & cookies | HIGH | Yes |
| 15 | Business continuity & DR | HIGH | Yes |
| 16 | Vendor risk & sub-processors | MEDIUM | Yes (regulated) |
| 17 | Key rotation | HIGH | Likely |
| 18 | Security monitoring & alerting | HIGH | Yes |
| 19 | Incident response | MEDIUM | Yes (regulated) |
| 20 | Compliance programme | MEDIUM | Yes (enterprise) |

---

## Prioritised Remediation Roadmap

### Phase 1 — Immediate (blocks any enterprise conversation)

1. Fix `anyRequest().permitAll()` → `anyRequest().authenticated()`
2. Restrict `/api/public/config` to non-sensitive keys only
3. Set `secure=true` on JWT cookies
4. Remove JWT from OAuth redirect URLs and response bodies
5. Enable Elasticsearch `xpack.security` in production
6. Bind all management ports to `127.0.0.1` in production compose
7. Remove hardcoded default passwords and fallback JWT secret from source
8. Add password complexity enforcement and account lockout

### Phase 2 — Short term (required for procurement)

9. Implement server-side JWT revocation
10. Encrypt Google OAuth tokens at rest (AES-GCM field-level)
11. Add pre-send PII redaction before LLM API calls
12. Add file type allowlist with magic-byte validation
13. Add `USER` directives to all Dockerfiles (non-root)
14. Enable MongoDB TLS in production
15. Implement CSP header
16. Fix Hub CORS wildcard
17. Add OAuth state parameter

### Phase 3 — Medium term (required for certification)

18. Add SAST/DAST/SCA to CI pipeline
19. Implement container image scanning and signing
20. Generate SBOM with each release
21. Automate encrypted offsite backups
22. Extend audit logging to read operations and auth events
23. Configure security alerting (Prometheus rules + PagerDuty)
24. Implement JWT key rotation with dual-key support
25. Hash Hub API keys with bcrypt/Argon2

### Phase 4 — Certification readiness

26. Commission penetration test
27. Create incident response plan and runbooks
28. Execute DPAs with Anthropic and Google
29. Complete DPIA for LLM classification pipeline
30. Begin SOC 2 Type II / ISO 27001 readiness programme
31. Implement GDPR erasure workflow
32. Deploy MongoDB replica set and HA infrastructure
