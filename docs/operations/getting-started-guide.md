fles# Getting Started Guide

A step-by-step walkthrough of setting up IG Central from a bare installation to a fully operational governance-led document classification system. This guide assumes no prior knowledge of the platform.

---

## What We Are Building

IG Central is a document governance platform. We upload documents, and the system automatically:

1. **Extracts text** from PDFs, Word documents, spreadsheets, and other file types
2. **Scans for PII** (personal information like names, National Insurance numbers, email addresses)
3. **Classifies** the document into a taxonomy category (e.g., "HR > Leave Records") and assigns a sensitivity level
4. **Applies governance** — retention schedules, storage tiers, and policies based on the classification
5. **Routes uncertain classifications to humans** for review, and learns from their corrections

The platform is made up of several microservices that communicate through RabbitMQ message queues. Each service has a single responsibility, and they run together in Docker containers.

---

## Part 1: Prerequisites

Before we begin, we need the following installed on our machine:

| Tool | Purpose | Install |
|---|---|---|
| **Docker Desktop** | Runs all the services in containers | [docker.com/products/docker-desktop](https://docker.com/products/docker-desktop) |
| **Git** | Clone the repository | Likely already installed — run `git --version` to check |

That is all we need. Everything else — the database, the message queue, the search engine, the object storage — runs inside Docker.

### Hardware

The full stack runs comfortably on:
- 16 GB RAM (Docker will use ~8 GB)
- 4+ CPU cores
- 10 GB free disk space

If we are running on an M-series Mac, Docker Desktop handles ARM translation automatically.

---

## Part 2: Clone and Configure

### 2.1 Clone the Repository

```bash
git clone <repository-url> governance-led-storage
cd governance-led-storage
```

### 2.2 Create the Environment File

Copy the example environment file:

```bash
cp .env.example .env
```

Open `.env` in a text editor. Here is what each section means and what we need to set:

#### Project Settings (leave as defaults)

```env
COMPOSE_PROJECT_NAME=igc
APP_ENV=DEV
```

`COMPOSE_PROJECT_NAME` prefixes all Docker container names (e.g., `igc-mongo-1`). `APP_ENV` controls which Spring profile is active.

#### URLs

```env
FRONTEND_URL=http://localhost:3000
BACKEND_BASE_URL=http://localhost:8080
```

These are the internal URLs the services use to find each other. Leave them as-is for local development. If we later set up a Cloudflare tunnel for external access, we add a `PUBLIC_URL` pointing to our tunnel domain.

#### MongoDB

```env
MONGO_PASSWORD=example
MONGO_DB_NAME=governance_led_storage_main
```

The password for the MongoDB root user. For local development, `example` is fine. For production, use a strong password. The database name can stay as-is.

#### JWT Secret (required)

```env
JWT_SECRET=
```

This must be set. It is the secret key used to sign authentication tokens. Generate one:

```bash
openssl rand -base64 64
```

Paste the output as the value. Without this, authentication will not work.

#### Admin Account

```env
ADMIN_EMAIL=admin@governanceledstore.co.uk
ADMIN_PASSWORD=ChangeMe123!
```

The platform creates this admin account on first startup. We use these credentials to log in. Change the password to something secure for production.

#### Email (optional for now)

```env
EMAIL_SMTP_HOST=smtp.office365.com
EMAIL_SMTP_PORT=587
EMAIL_ADDRESS=
EMAIL_PASSWORD=
```

Used for sending email verification and notifications. We can leave this blank for initial setup and configure it later. The platform will still work — it just will not send emails.

#### Cloudflare Tunnel (optional)

```env
TUNNEL_TOKEN=
```

If we want the platform accessible from the internet (not just localhost), we set up a Cloudflare tunnel and paste the token here. For local development, leave it blank.

#### RabbitMQ

```env
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest
```

Credentials for the message queue. The defaults work for development.

#### MinIO (Object Storage)

```env
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=igc-documents
```

MinIO is an S3-compatible object store where uploaded documents are physically stored. The defaults work for development.

#### Anthropic API Key (required for LLM classification)

```env
ANTHROPIC_API_KEY=
```

We can set this here or configure it later through the Settings UI after first login. Without an API key (and without Ollama configured), the classification step will fail — documents will extract text and scan for PII but will not classify.

To get a key: sign up at [console.anthropic.com](https://console.anthropic.com) and create an API key.

#### OAuth Providers (optional)

```env
LINKEDIN_CLIENT_ID=
LINKEDIN_CLIENT_SECRET=
GIT_HUB_CLIENT_ID=
GIT_HUB_CLIENT_SECRET=
```

For social login. Not needed for initial setup.

---

## Part 3: Start the Platform

### 3.1 Build and Launch

From the project root directory:

```bash
docker compose up --build -d
```

This builds all the service images and starts everything in the background. The first build takes several minutes as it downloads dependencies. Subsequent starts are much faster.

### 3.2 What Starts

The platform consists of 14 containers:

| Container | Port | What It Does |
|---|---|---|
| **nginx** | 80 | The front door — routes web traffic to the right service |
| **web** | 3000 | The Next.js frontend (the UI we interact with) |
| **api** | 8080 | The main backend — handles API requests, user auth, admin operations |
| **mcp-server** | 8081 | Provides tools the LLM calls during classification (taxonomy lookups, saving results) |
| **llm-worker** | 8082 | Runs the LLM classification step (calls Claude or Ollama) |
| **doc-processor** | 8083 | Extracts text from documents and scans for PII patterns |
| **governance-enforcer** | 8084 | Applies retention, storage, and policy rules after classification |
| **mongo** | 27017 | The main database — stores documents, users, governance rules, everything |
| **rabbitmq** | 5672/15672 | The message queue — services communicate by publishing and consuming messages |
| **minio** | 9000/9001 | Object storage — where uploaded files physically live |
| **elasticsearch** | 9200 | Full-text search index for document content and metadata |
| **governance-hub** | 8090 | Shared governance marketplace (separate service) |
| **hub-mongo** | — | Separate database for the governance hub |
| **cloudflared** | — | Cloudflare tunnel for external access (optional) |

### 3.3 Check Everything Started

```bash
docker compose ps
```

All containers should show `running` or `healthy`. If any show `restarting` or `exited`, check the logs:

```bash
docker compose logs <service-name>
```

Common issues on first start:
- **mongo fails to start**: Check `MONGO_PASSWORD` is set in `.env`
- **api keeps restarting**: Usually waiting for MongoDB — give it 30 seconds and check again
- **web shows errors**: Run `docker compose logs web` — usually a dependency install issue, retry with `docker compose up --build web`

### 3.4 What Happens on First Boot

When the API service starts for the first time, it runs several bootstrap tasks automatically:

1. **Creates the admin account** using the email and password from `.env`
2. **Creates 32 permission features** — every capability in the system (e.g., `DOCUMENT_CREATE`, `GOV_POLICY_READ`, `MONITORING_ACTION`)
3. **Creates 4 roles** — Admin (all permissions), Compliance Officer, Document Manager, Standard User
4. **Seeds the governance framework**:
   - 4 sensitivity levels: Public, Internal, Confidential, Restricted
   - 5 retention schedules: Short-Term (1 year) through Permanent
   - 4 storage tiers: Public Store through Restricted Vault
   - 15 taxonomy categories: Legal, Finance, HR, Operations, Compliance, IT, Marketing — plus child categories like Contracts, Invoices, Payroll
   - 6 governance policies: UK Data Protection, Financial Records Retention, Legal Hold, and more
5. **Creates the default processing pipeline** with four steps: text extraction, PII scan, LLM classification, governance enforcement
6. **Creates seed pipeline blocks** — the Tika extractor config, PII regex patterns, and classification prompt
7. **Sets up the local storage drive** pointing to MinIO

All of this is ready before we even log in.

---

## Part 4: First Login

### 4.1 Open the Platform

Navigate to **http://localhost** in a browser.

We land on the homepage. Click **Login** or navigate directly to **http://localhost/login**.

### 4.2 Log In

Enter the credentials from `.env`:
- **Email:** `admin@governanceledstore.co.uk` (or whatever we set as `ADMIN_EMAIL`)
- **Password:** `ChangeMe123!` (or whatever we set as `ADMIN_PASSWORD`)

Click **Sign In**. We are redirected to the Dashboard.

### 4.3 What We See: The Dashboard

The dashboard is the operational overview of the platform. On a fresh install with no documents, we see:

- **5 status cards** across the top: Total Documents (0), Governed (0), Review Queue (0), Failed (0), In Progress (0)
- **Pipeline Funnel** — a visual breakdown of documents by status (empty for now)
- **Performance metrics** — classification throughput, average time, stale document count
- **Recent documents** — the last 20 documents processed (empty)
- **Quick action buttons** — Upload, Review, Search, Governance, Monitoring

### 4.4 The Sidebar

The left sidebar is our main navigation. As an admin, we see everything:

**Main section:**

| Link | What It Does |
|---|---|
| **Dashboard** | The overview we are on now |
| **Inbox** | Documents waiting for us to file or acknowledge (shows a badge count) |
| **Drives** | Connected storage providers — Local Storage is already set up, and we can connect Google Drive |
| **Documents** | Browse, upload, and manage all documents |
| **Advanced Search** | Full-text and metadata search powered by Elasticsearch |
| **PII & SARs** | PII management and Subject Access Requests |
| **Governance** | The heart of the system — taxonomy, policies, retention, metadata schemas |
| **Review Queue** | Documents the LLM was not confident about, waiting for human review |

**Admin section (bottom of sidebar):**

| Link | What It Does |
|---|---|
| **Users** | Create accounts, assign roles, set sensitivity clearances |
| **AI** | Configure the LLM provider (Anthropic or Ollama) |
| **Monitoring** | Real-time pipeline health, queue depths, service status |
| **Audit Log** | Every document status transition and error |
| **Directory** | Directory-to-role mappings (for AD/LDAP integration) |
| **Access Audit** | Who accessed which documents and when |
| **Settings** | Runtime application configuration |

**Help** is at the very bottom — contextual guidance for each user type.

---

## Part 5: Configure the LLM

Before documents can be classified, we need a working LLM connection. We have two options: Anthropic (Claude, cloud-hosted) or Ollama (local, self-hosted).

### Option A: Anthropic (Recommended for Getting Started)

1. Navigate to **AI** in the admin sidebar
2. Select **Anthropic** as the provider
3. Paste our Anthropic API key
4. Save

The platform uses Claude to classify documents. This costs money per document (roughly $0.01–0.03 depending on document length) but produces the best results.

### Option B: Ollama (Free, Local)

If we want to run classification entirely locally with no external API calls:

1. Install Ollama on our machine: [ollama.com](https://ollama.com)
2. Pull a model: `ollama pull qwen2.5:32b` (or `mistral`, `llama3`, etc.)
3. Navigate to **AI** in the admin sidebar
4. Select **Ollama** as the provider
5. Set the base URL to `http://host.docker.internal:11434` (this lets Docker containers reach Ollama running on our host machine)
6. Select the model we pulled
7. Save

Ollama is free but classification quality depends on the model. Larger models (32B+ parameters) produce significantly better results than smaller ones.

### Verify the Connection

After saving, the AI page should show a green status indicator confirming the LLM is reachable.

---

## Part 6: Understand the Governance Framework

Before we upload our first document, it helps to understand what the seeded governance data means and how it controls what happens to documents.

### 6.1 Taxonomy Categories

Navigate to **Governance** in the sidebar.

The taxonomy is the classification scheme the LLM uses to categorise documents. It is a hierarchy:

```
Legal
  ├── Contracts
  └── Litigation
Finance
  └── Invoices & Receipts
HR
  ├── Payroll
  └── Recruitment
Operations
Compliance
  └── Audit Reports
IT
  └── Security Incidents
Marketing
  └── Press & Media
```

Each category has:
- **Name and description** — what types of documents belong here
- **Keywords** — terms the LLM looks for when classifying
- **Default sensitivity** — the sensitivity level assigned unless the LLM has reason to override
- **Linked retention schedule** — how long documents in this category are kept

We can add, edit, or remove categories at any time. The LLM reads the taxonomy fresh on every classification, so changes take effect immediately.

### 6.2 Sensitivity Levels

Also under **Governance**, the sensitivity levels define how sensitive a document is:

| Level | Name | Colour | Meaning |
|---|---|---|---|
| 0 | **Public** | Green | Published materials, press releases — no access restrictions |
| 1 | **Internal** | Blue | Internal documents, SOPs, memos — staff only |
| 2 | **Confidential** | Amber | Contracts, financial data, strategy — need-to-know basis |
| 3 | **Restricted** | Red | PII, trade secrets, legal privilege — strictest controls |

Each user has a **sensitivity clearance level** (0–3). A user with clearance 2 can see Public, Internal, and Confidential documents but not Restricted ones.

### 6.3 Retention Schedules

Retention schedules define how long we keep documents and what happens when the period expires:

| Schedule | Period | Rationale |
|---|---|---|
| Short-Term | 1 year | Transient documents with no regulatory requirement |
| Standard | 7 years | General business records (Companies Act 2006) |
| Financial Statutory | 7 years | Tax and financial records (HMRC requirements) |
| Regulatory Extended | 10 years | Regulated industry records (FCA, GDPR) |
| Permanent | Indefinite | Board minutes, constitutional documents |

When a document is classified, the LLM assigns the retention schedule associated with its category. The platform tracks expiry dates and can flag documents approaching end-of-life.

### 6.4 Storage Tiers

Each sensitivity level maps to a storage tier with specific security controls:

| Tier | Encryption | Immutable | Region Lock | Cost |
|---|---|---|---|---|
| Public Store | AES-128 | No | No | £0.01/GB |
| Internal Store | AES-256 | No | No | £0.03/GB |
| Confidential Store | AES-256 | Yes | UK-resident | £0.08/GB |
| Restricted Vault | AES-256-GCM | Yes | UK-resident | £0.15/GB |

### 6.5 Governance Policies

Policies are rules that apply to documents based on their category and sensitivity. The seeded policies include:

- **UK Data Protection Policy** — applies to Confidential/Restricted documents, enforces GDPR and DPA 2018 compliance
- **Financial Records Retention** — 7-year hold on all Finance category documents
- **Legal Hold Policy** — prevents deletion of documents in the Litigation category
- **Restricted Access Control** — additional controls for Restricted-sensitivity documents
- **Employee Data Handling** — GDPR rules for HR category documents
- **Public Information Release** — guidelines for Public-sensitivity documents

After classification, the enforcement service checks which policies apply and records them on the document.

---

## Part 7: Upload Our First Document

### 7.1 Upload

1. Navigate to **Documents** in the sidebar
2. Click **Upload** (or drag and drop files)
3. Select a file — PDF, Word (.docx), Excel, plain text, or any common document format
4. The file uploads to MinIO object storage

The document appears in the list with status **Uploaded**.

### 7.2 Watch It Process

Within seconds, the pipeline begins. We can watch progress on the **Dashboard** or the **Monitoring** page:

**Step 1 — Text Extraction (doc-processor)**

The document moves to **Processing**. The doc-processor service:
- Downloads the file from MinIO
- Runs Apache Tika to extract the text content
- Extracts metadata (author, creation date, page count)
- Runs PII regex patterns against the text (National Insurance numbers, email addresses, phone numbers, postcodes, bank account numbers)
- Stores the extracted text and any PII findings on the document

If this succeeds, the document moves to **Processed**. If it fails (corrupted file, unsupported format), it moves to **Processing Failed** with an error message.

**Step 2 — LLM Classification (llm-worker)**

The document moves to **Classifying**. The LLM worker:
- Loads the document text and the classification prompt from the active PROMPT block
- Calls the LLM (Claude or Ollama) with MCP tools available
- The LLM reads the taxonomy, sensitivity definitions, governance policies, correction history, and metadata schemas through those tools
- The LLM decides: which category, which sensitivity, what tags, what metadata to extract
- The LLM saves its decision with a confidence score (0.0–1.0) and reasoning

If this succeeds, the document moves to **Classified**. If it fails (API error, timeout, no LLM configured), it moves to **Classification Failed**.

**Step 3 — Governance Enforcement (governance-enforcer)**

The enforcement service:
- Looks up which policies apply to this category and sensitivity combination
- Assigns the retention schedule and storage tier
- If the LLM's confidence was below the review threshold (default 0.7), marks the document for human review

If confidence was high enough: the document moves to **Governance Applied**. Done.

If confidence was low: the document moves to **Review Required** and appears in the Review Queue.

### 7.3 View the Result

Click on the document in the Documents list. We see:

- **Classification** — the assigned category, sensitivity level, and confidence score
- **Reasoning** — the LLM's explanation of why it chose this classification
- **Tags** — additional descriptive tags the LLM assigned
- **Summary** — a brief summary of the document content
- **Extracted metadata** — structured fields pulled from the text (e.g., employee name, dates, amounts)
- **PII findings** — any personal information detected, with type and location
- **Governance** — which policies apply, the retention schedule, and storage tier
- **Audit trail** — every status transition with timestamps

---

## Part 8: The Review Queue

### 8.1 Why Documents End Up Here

When the LLM is not confident about its classification (confidence below the review threshold), the document is routed to the Review Queue for human review. This is deliberate — we do not want uncertain classifications applied automatically.

### 8.2 Review a Document

Navigate to **Review Queue** in the sidebar. Each document shows the LLM's proposed classification and confidence score.

For each document, we can:

| Action | What It Does |
|---|---|
| **Approve** | Accept the LLM's classification as correct. Records an `APPROVED_CORRECT` correction — positive feedback. |
| **Override** | Change the category, sensitivity, or both. We provide the correct values and a reason. This creates a correction record that the LLM will learn from. |
| **Flag PII** | Report PII that the system missed (e.g., a name the regex did not catch). Creates a `PII_FLAGGED` correction. |
| **Dismiss PII** | Mark a PII finding as a false positive (e.g., a number that looks like a phone number but is not). Creates a `PII_DISMISSED` correction. |
| **Reject** | Flag the document for further investigation. |

### 8.3 How Corrections Improve the System

Every correction we make is stored in MongoDB. The next time the LLM classifies a similar document:

1. It calls the `get_correction_history` MCP tool
2. The tool returns past corrections relevant to this category and document type
3. The LLM reads those corrections and adjusts its decision accordingly

For example: if we override three HR documents from "HR > General Correspondence" to "HR > Leave Records" and explain "documents containing specific leave dates are leave records, not general correspondence," the LLM will start getting that distinction right without being overridden.

This is the core learning loop. The more we review, the better the system gets.

---

## Part 9: The Monitoring Dashboard

Navigate to **Monitoring** in the admin sidebar. This is the operations centre.

### 9.1 Service Health

The top section shows the health of every microservice:
- **API** (main backend)
- **MCP Server** (tool provider)
- **LLM Worker** (classification)
- **Doc Processor** (text extraction)
- **Governance Enforcer** (policy application)

Green = healthy. Red = down. If a service is down, the relevant pipeline step will fail, but documents queue up in RabbitMQ and will process when the service recovers.

### 9.2 Pipeline Metrics

- **Documents by status** — how many are in each state right now
- **Throughput** — documents processed in the last 24 hours and 7 days
- **Average classification time** — how long the LLM step takes per document
- **Stale documents** — documents stuck in Processing or Classifying for more than 10 minutes (something went wrong)

### 9.3 Queue Depths

Shows how many messages are waiting in each RabbitMQ queue:
- `igc.documents.ingested` — waiting for text extraction
- `igc.documents.processed` — waiting for LLM classification
- `igc.documents.classified` — waiting for governance enforcement

If a queue is growing, it means the consuming service cannot keep up or is down.

### 9.4 Admin Actions

| Action | When to Use |
|---|---|
| **Retry Failed** | Re-queues all documents in a failed state (Processing Failed, Classification Failed, Enforcement Failed) |
| **Reset Stale** | Resets documents stuck in-flight for more than 10 minutes back to their previous state for re-processing |
| **Purge Queue** | Clears all messages from a specific RabbitMQ queue (use with caution — those documents will need to be reprocessed manually) |
| **Reindex Search** | Rebuilds the Elasticsearch index from MongoDB (use after bulk imports or if search results seem stale) |

---

## Part 10: Pipeline Blocks — The Block Library

Navigate to **Blocks** (via `/blocks` in the sidebar or under Governance).

Blocks are the configurable building pieces of the pipeline. Instead of hardcoding how text extraction works or what the LLM prompt says, each pipeline step references a versioned block. We can edit, test, publish, and roll back blocks without touching code or restarting any service. When we publish a new version of a block, every pipeline using it picks up the change on the next document.

### 10.1 What We See

The Block Library page shows a list of all blocks, filterable by type. Each block in the list shows:
- **Name** — e.g., "Classification Prompt", "UK PII Pattern Scanner"
- **Type badge** — PROMPT, REGEX_SET, EXTRACTOR, ROUTER, or ENFORCER
- **Active version number** — e.g., "v3"
- **Documents processed** — how many documents have been run through this block
- **Corrections received** — how many human corrections are linked to this block
- **Feedback count** — total feedback items (corrections, suggestions, false positives)

Click on any block to open the detail panel with four tabs: **Configuration**, **Versions**, **Feedback**, and (for some blocks) **Improve**.

### 10.2 Block Types in Detail

#### EXTRACTOR Block — "How do we read the document?"

The Extractor block controls how text is pulled out of uploaded files. The seeded block uses Apache Tika, which handles PDFs, Word documents, Excel spreadsheets, plain text, and most common formats.

**Configuration fields:**

| Field | What It Does | Default |
|---|---|---|
| **Extraction Engine** | Which engine to use: Tika (recommended), Tesseract OCR, or Custom | Tika |
| **Max Text Length** | Maximum characters to extract. Documents longer than this are truncated. Set high enough to capture full documents but low enough to avoid overwhelming the LLM. | 500,000 |
| **Extract Dublin Core** | Whether to pull Dublin Core metadata (author, title, subject, creation date) from the file. This metadata is stored on the document and shown in the UI. | Yes |
| **Extract File Metadata** | Whether to pull technical metadata (page count, word count, encoding). | Yes |

**When to change it:**
- If we are processing scanned documents (images of paper), switch to Tesseract OCR or add it as a second extractor
- If the LLM is running slowly, reduce Max Text Length to send less text per document
- If we do not care about Dublin Core metadata, disable it to speed up extraction slightly

**Seeded as:** "Tika Text Extractor v1"

---

#### REGEX_SET Block — "What PII patterns do we scan for?"

The Regex Set block defines the regular expression patterns used during PII scanning. This runs after text extraction and before classification — it is the first, cheapest line of PII detection (no LLM cost, pure pattern matching).

**Configuration fields:**

Each pattern in the set has:

| Field | What It Does | Example |
|---|---|---|
| **Display Name** | Human-readable name shown in PII findings | "UK National Insurance" |
| **PII Type Key** | Machine key used for filtering and reporting | `NATIONAL_INSURANCE` |
| **Regex Pattern** | The regular expression to match | `\b[A-CEGHJ-PR-TW-Z]{2}\s?\d{2}\s?\d{2}\s?\d{2}\s?[A-D]\b` |
| **Confidence** | How confident we are that a match is real PII (0.0–1.0). Higher values mean more certain matches. | 0.95 |
| **Flags** | Regex flags: None, Case Insensitive, or Multiline | Case Insensitive |

The confidence slider is colour-coded: green (>= 0.90), amber (0.70–0.89), red (< 0.70). This helps us visually distinguish between high-confidence patterns (like National Insurance numbers, which have a very specific format) and lower-confidence ones (like phone numbers, which can match non-phone sequences).

**Seeded patterns (8):**

| Pattern | Type Key | Confidence | What It Catches |
|---|---|---|---|
| UK National Insurance | `NATIONAL_INSURANCE` | 0.95 | AB 12 34 56 C format |
| Email Address | `EMAIL` | 0.95 | standard email format |
| UK Phone Number | `PHONE` | 0.85 | UK mobile and landline numbers |
| UK Postcode | `POSTCODE` | 0.90 | UK postcode format |
| Bank Sort Code | `SORT_CODE` | 0.90 | 12-34-56 format |
| Date of Birth | `DOB` | 0.80 | Common date formats |
| Credit Card Number | `CREDIT_CARD` | 0.90 | 16-digit card numbers |
| VAT Number | `VAT_NUMBER` | 0.90 | GB VAT format |

**When to change it:**
- Add patterns for organisation-specific identifiers (employee IDs, patient numbers, policy reference numbers)
- Adjust confidence values if we are seeing too many false positives (lower the confidence) or missing real PII (raise it)
- Remove patterns that do not apply to our document types
- Add patterns for non-UK PII if we operate in other jurisdictions

**How corrections feed back:** When a reviewer dismisses a PII finding as a false positive, or flags PII the system missed, that feedback is linked to this block. The Feedback tab shows these corrections grouped by pattern, so we can see which patterns are over-matching or under-matching.

**Seeded as:** "UK PII Pattern Scanner v1"

---

#### PROMPT Block — "What do we tell the LLM?"

The Prompt block is the most important block in the pipeline. It contains the instructions we give to the LLM when it classifies a document. The quality of these instructions directly affects classification accuracy.

**Configuration fields:**

| Field | What It Does |
|---|---|
| **System Prompt** | The role and behavioural instructions for the LLM. This tells it what it is (a document classification specialist), what tools to call, and in what order. |
| **User Prompt Template** | The per-document prompt. Contains placeholders that are filled in with the actual document data at runtime. |

**Available placeholders in the user prompt template:**

| Placeholder | Replaced With |
|---|---|
| `{documentId}` | The document's ID |
| `{fileName}` | Original filename |
| `{mimeType}` | File type (e.g., `application/pdf`) |
| `{fileSizeBytes}` | File size |
| `{uploadedBy}` | Who uploaded the document |
| `{extractedText}` | The full extracted text from the document |
| `{piiFindings}` | PII findings from the regex scan (JSON) |

The editor shows a guide panel listing all available placeholders. We can toggle between **Form View** (separate text areas for system prompt and user prompt) and **Raw JSON** (edit the full JSON object directly).

**When to change it:**
- If classification accuracy is poor for certain categories, add more specific guidance to the system prompt
- If the LLM is not extracting metadata correctly, adjust the user prompt template to be more explicit about what fields to look for
- If we want the LLM to pay more attention to correction history, emphasise that in the system prompt
- After accumulating enough feedback, use **Improve with AI** to have the LLM suggest a better version based on the corrections

**Seeded as:** "Classification Prompt v1"

---

#### ROUTER Block — "When does a document need human review?"

The Router block defines a condition that splits the pipeline into two paths. The most common use is routing low-confidence classifications to the review queue.

**Configuration fields:**

| Field | What It Does | Options |
|---|---|---|
| **Condition** | What value to evaluate | Classification Confidence, PII Entity Count, Sensitivity Level, File Size |
| **Operator** | How to compare | Less Than, Greater Than, Equals, Not Equals |
| **Threshold** | The comparison value | e.g., 0.7 for confidence |
| **If TRUE output** | Where documents go when the condition is met | e.g., "human_review" |
| **If FALSE output** | Where documents go when the condition is not met | e.g., "auto_approve" |

The editor shows a visual routing diagram: a green box for the TRUE path and a red box for the FALSE path, making it immediately clear how documents will flow.

**Example configuration:**
- Condition: Classification Confidence
- Operator: Less Than
- Threshold: 0.7
- If TRUE → human_review (confidence is below 0.7, so the document needs review)
- If FALSE → auto_approve (confidence is 0.7 or above, proceed automatically)

**When to change it:**
- Raise the threshold (e.g., to 0.85) if we want more documents reviewed by humans — higher quality, more manual work
- Lower the threshold (e.g., to 0.5) if we trust the LLM enough and want to reduce the review queue
- Change the condition to PII Entity Count > 0 if we want every document with detected PII to be reviewed
- Create multiple router blocks for different pipelines — e.g., a stricter threshold for HR documents than for Marketing

**Seeded as:** "Confidence Router v1" (threshold 0.7)

---

#### ENFORCER Block — "What governance rules do we apply?"

The Enforcer block controls which governance actions are taken after classification.

**Configuration fields (toggle switches):**

| Toggle | What It Does | Default |
|---|---|---|
| **Apply retention schedules** | Sets the retention period based on the category's linked retention schedule. The document gets an expiry date. | On |
| **Migrate storage tier** | Moves the document to the appropriate storage tier based on its sensitivity level (e.g., Restricted documents go to the encrypted vault). | On |
| **Enforce governance policies** | Applies access controls, compliance labels, and any policy rules that match the document's category and sensitivity. | On |
| **Auto-archive on expiry** | Automatically archives documents when their retention period expires. If off, expired documents are flagged but not moved. | Off |

**When to change it:**
- Enable auto-archive if we want the system to manage the full document lifecycle without manual intervention
- Disable storage tier migration if all our documents live in the same storage tier
- Create separate enforcer blocks for different pipelines — e.g., a strict enforcer for regulated documents and a lighter one for internal memos

**Seeded as:** "Standard Governance Enforcer v1"

### 10.3 Editing a Block — The Workflow

Blocks use a draft/publish model to prevent accidental changes to live pipelines:

1. **Open the block** — click on it in the Block Library
2. **Edit the configuration** — changes are saved as a **draft**. The draft is not live. Documents still process using the currently active version.
3. **Preview** — test the changes mentally or by running a test document through the pipeline
4. **Write a changelog** — describe what we changed and why (e.g., "Lowered phone number confidence to reduce false positives in financial documents")
5. **Publish** — click **Publish** to create a new immutable version. This becomes the active version immediately. The previous version is preserved in full.

**Important:** Publishing is instant. The next document that enters the pipeline will use the new version. There is no deployment, no restart, no downtime.

### 10.4 Version History

Every publish creates a new numbered version. The Versions tab shows a timeline of all versions with:

- **Version number** — v1, v2, v3, etc.
- **Changelog** — what changed and why
- **Published by** — who made the change
- **Published at** — timestamp

We can:
- **View any version** — see the exact configuration at that point in time
- **Compare two versions** — a side-by-side diff showing what changed between any two versions (e.g., compare v2 to v5 to see everything that evolved)
- **Roll back** — revert to a previous version with one click. This does not delete newer versions — it sets the selected version as active. We can always roll forward again.

### 10.5 Feedback and AI Improvement

The Feedback tab shows human corrections and suggestions linked to this block:

**For PROMPT blocks:**
- Classification overrides (the LLM assigned the wrong category or sensitivity)
- Each correction shows: original classification, corrected classification, the reviewer's reason, and the document that triggered it

**For REGEX_SET blocks:**
- False positives (a pattern matched something that was not PII)
- Missed PII (PII was present but no pattern caught it)
- Each correction shows: the pattern involved, the text it matched (or missed), and the reviewer's judgement

**Feedback summary** shows aggregate stats: total feedback count, corrections, false positives, missed items — with a breakdown by type.

**Improve with AI:**

When enough feedback has accumulated, we can click **Improve with AI**. The system:

1. Collects all feedback for the current active version
2. Builds a prompt describing the feedback (what was wrong, what the correct answers were)
3. Sends it to the LLM and asks it to propose an improved version of the block
4. Saves the suggestion as a **draft** — it does not go live

We then review the suggested draft, make any adjustments, and publish it as a new version if we are satisfied. The AI never changes a live block without our approval.

---

## Part 11: The Visual Pipeline Editor

Navigate to **Pipelines** (via the admin section or `/pipelines`).

The visual pipeline editor is where we define how documents flow through processing steps. Instead of writing code, we drag nodes onto a canvas, connect them with edges, and configure each node's behaviour by linking it to a block.

### 11.1 What We See

The pipeline page shows a list of all pipelines. The seeded "Document Ingestion Pipeline" is the default. Click on a pipeline to open the visual editor.

The editor has three areas:
- **Node palette** (left) — categorised list of available node types we can drag onto the canvas
- **Canvas** (centre) — the visual pipeline with nodes and connecting edges
- **Mini map** (bottom-right corner) — a zoomed-out view of the entire pipeline for quick navigation

### 11.2 Node Types

Nodes are the building blocks of the visual pipeline. Each represents a processing step. They are grouped into categories in the palette:

#### Triggers

| Node | What It Does |
|---|---|
| **Trigger** | The entry point of the pipeline. Every pipeline starts with one trigger node. When a document enters the pipeline, execution begins here. |

#### Processing

| Node | What It Does |
|---|---|
| **Text Extraction** | Extracts text and metadata from the document using the linked EXTRACTOR block. Connects to the doc-processor service. |
| **PII Scanner** | Scans extracted text against regex patterns from the linked REGEX_SET block. Runs locally, no LLM cost. |
| **AI Classification** | Sends the document to the LLM for classification using the linked PROMPT block. This is where category, sensitivity, tags, metadata, and confidence are determined. |

#### Accelerators

| Node | What It Does |
|---|---|
| **Template Fingerprint** | Compares the document against known templates (e.g., standard HR forms, invoice layouts). If matched, we can inherit the classification without running the LLM. |
| **Smart Truncation** | Intelligently truncates long documents before sending to the LLM, preserving the most classification-relevant sections (beginning and end). |
| **Similarity Cache** | Checks if a nearly identical document has already been classified. If the similarity score is above a threshold, inherits the existing classification. |

#### Logic

| Node | What It Does |
|---|---|
| **Condition** | A branching node with two outputs: TRUE (green) and FALSE (red). Routes documents based on a condition from the linked ROUTER block (e.g., "confidence < 0.7"). |
| **Rules Engine** | Applies a set of business rules to the document. More complex than a single condition — can evaluate multiple criteria. |

#### Actions

| Node | What It Does |
|---|---|
| **Governance** | Applies governance rules using the linked ENFORCER block. Sets retention, storage tier, and policies. |
| **Human Review** | A gate that pauses the pipeline and waits for human review. Has two outputs: Approved (green) and Rejected (red). |
| **Notification** | Sends a notification (email, webhook, or in-app) when a document reaches this point. |

#### Error Handling

| Node | What It Does |
|---|---|
| **Error Handler** | Catches failures from upstream nodes. Has two outputs: Retry (green) and Fail (red). We can configure retry limits and backoff. |

### 11.3 Node Connections (Edges)

Nodes connect via **edges** — lines drawn from an output handle on one node to an input handle on the next.

**Handle positions:**
- **Input** (top of node) — where the document enters this step
- **Output** (bottom of node) — where the document goes next
- **Error output** (right side, red) — where the document goes if this step fails

**Branching nodes** (Condition, Human Review, Error Handler) have two outputs:
- **Left output (green)** — the TRUE / Approved / Retry path
- **Right output (red)** — the FALSE / Rejected / Fail path

To connect two nodes: click and drag from an output handle to an input handle. The edge snaps into place.

**Edge labels** appear on conditional edges to show which path is which (e.g., "confidence >= 0.7" on the green path, "confidence < 0.7" on the red path).

### 11.4 Node Status Indicators

Each node shows a small status dot:
- **Green dot** — configured and ready. The node is linked to a block and all required settings are in place.
- **Amber dot** — incomplete. The node exists but is missing a block link or required configuration.
- **No dot** — the node has not been configured yet.

### 11.5 The Seeded Default Pipeline

The default "Document Ingestion Pipeline" looks like this in the visual editor:

```
    ┌──────────┐
    │ Trigger  │
    └────┬─────┘
         │
    ┌────▼──────────────┐
    │ Text Extraction   │──── error ──→ [Error Handler]
    │ (Tika v1)         │
    └────┬──────────────┘
         │
    ┌────▼──────────────┐
    │ PII Scanner       │──── error ──→ [Error Handler]
    │ (UK PII v1)       │
    └────┬──────────────┘
         │
    ┌────▼──────────────┐
    │ AI Classification │──── error ──→ [Error Handler]
    │ (Prompt v1)       │
    └────┬──────────────┘
         │
    ┌────▼──────────────┐
    │ Condition         │
    │ (Confidence < 0.7)│
    └──┬─────────────┬──┘
       │ TRUE        │ FALSE
       │             │
  ┌────▼────┐   ┌────▼──────────┐
  │ Human   │   │ Governance    │
  │ Review  │   │ (Enforcer v1) │
  └──┬───┬──┘   └───────────────┘
     │   │
  Approved  Rejected
     │       │
┌────▼──────────┐
│ Governance    │
│ (Enforcer v1) │
└───────────────┘
```

Documents flow top to bottom. If any step fails, the error edge routes to an error handler. After classification, the confidence router splits the flow: uncertain documents go through human review before governance is applied.

### 11.6 Building a Custom Pipeline — Step by Step

Let us walk through creating a pipeline for HR documents that uses a specialised prompt and a stricter review threshold.

#### Step 1: Create the Pipeline

1. Click **Create Pipeline** on the pipelines page
2. Name: "HR Document Pipeline"
3. Description: "Specialised pipeline for HR documents with strict review threshold"
4. Set **Applicable Categories**: select "HR" and tick **Include Sub-Categories** (this covers HR, Payroll, Recruitment, and any future HR subcategories)
5. Leave MIME types blank (handles all file types)
6. Save — the visual editor opens with an empty canvas

#### Step 2: Create Specialised Blocks (if needed)

Before building the pipeline, we might want blocks tailored to HR documents. Go to the Block Library and:

**Create an HR-specific PROMPT block:**
1. Click **Create Block**
2. Name: "HR Classification Prompt"
3. Type: PROMPT
4. In the system prompt, add HR-specific guidance: "Pay special attention to employment dates, salary information, and personal employee details. Documents containing specific leave dates should be classified as HR > Leave Records, not HR > General Correspondence."
5. Publish as v1

**Create a stricter ROUTER block:**
1. Click **Create Block**
2. Name: "HR Confidence Router"
3. Type: ROUTER
4. Set condition: Classification Confidence, Less Than, **0.85** (stricter than the default 0.7)
5. Publish as v1

We can reuse the existing Tika Extractor, PII Scanner, and Governance Enforcer blocks — they work the same regardless of document type.

#### Step 3: Add Nodes to the Canvas

Drag nodes from the palette onto the canvas in order:

1. **Trigger** — the starting point
2. **Text Extraction** — link to "Tika Text Extractor"
3. **PII Scanner** — link to "UK PII Pattern Scanner"
4. **AI Classification** — link to "HR Classification Prompt" (our new HR-specific prompt)
5. **Condition** — link to "HR Confidence Router" (our stricter threshold)
6. **Human Review** — on the TRUE (low confidence) path
7. **Governance** (x2) — one after Human Review (approved path), one on the FALSE (high confidence) path. Both link to "Standard Governance Enforcer"

#### Step 4: Connect the Nodes

Draw edges between the nodes:
- Trigger → Text Extraction
- Text Extraction → PII Scanner
- PII Scanner → AI Classification
- AI Classification → Condition
- Condition TRUE → Human Review
- Condition FALSE → Governance
- Human Review Approved → Governance

#### Step 5: Link Blocks to Nodes

Click each node and assign its block:
- Text Extraction → select "Tika Text Extractor" (uses active version by default, or pin to a specific version)
- PII Scanner → select "UK PII Pattern Scanner"
- AI Classification → select "HR Classification Prompt"
- Condition → select "HR Confidence Router"
- Governance → select "Standard Governance Enforcer"

Each node's status dot should turn green once it has a linked block.

#### Step 6: Save and Activate

1. Click **Save** — the pipeline definition, visual nodes, and edges are all persisted
2. Toggle the pipeline to **Active**
3. The pipeline routing service will now match HR documents to this pipeline instead of the default

#### Step 7: Verify Routing

Use the **Resolve** tool on the pipelines page: enter a category (e.g., "HR > Leave Records") and a MIME type (e.g., "application/pdf"). The system shows which pipeline would handle that document. It should show our new HR pipeline.

We can also use **Overlap Check** to see if our new pipeline's category coverage overlaps with other pipelines. If it does, the more specific pipeline wins (direct category match beats inherited match beats default).

### 11.7 Pipeline Scoping

Each pipeline can be scoped to handle specific documents:

| Scope | What It Means |
|---|---|
| **No categories, no MIME types** | Handles everything — typically only the default pipeline |
| **Specific categories** | Only handles documents classified into (or initially routed to) those categories |
| **Include Sub-Categories** | If we select "HR", also handles "HR > Payroll", "HR > Recruitment", etc. |
| **Specific MIME types** | Only handles certain file types (e.g., `application/pdf`, `image/*`) |
| **Categories + MIME types** | Both must match |

**Routing priority** when multiple pipelines could match:
1. Direct category match (exact category ID in the pipeline's list)
2. Inherited match (parent category with sub-categories included)
3. MIME type only match (no category restrictions)
4. Default pipeline (marked as `isDefault`)

### 11.8 Pinning Block Versions

By default, a pipeline step uses whichever version of the block is currently active. If we publish a new version of the Classification Prompt, all pipelines using it will pick up the change.

Sometimes we want to lock a pipeline to a specific block version — for example, if we are testing a new prompt version on one pipeline while keeping the proven version on others.

To pin a version: in the pipeline editor, select the node, and choose a specific version number from the block version dropdown instead of "Active (latest)".

Pinned steps will not change when the block is updated. We must manually update the pin to use a newer version.

### 11.9 The Disabled PII Verification Step

The seeded default pipeline includes a fifth step that is **disabled by default**: **PII Verification (LLM)**. This is a conditional step that runs only if the regex scanner found PII entities. When enabled, it sends the PII findings to the LLM for a second opinion — the LLM can confirm, dismiss, or reclassify each finding.

This step adds LLM cost per document (only those with PII findings), but significantly reduces false positives. To enable it:

1. Open the default pipeline in the visual editor
2. Find the PII Verification node (it will be greyed out or marked as disabled)
3. Enable it
4. Save

---

## Part 12: Customising the Governance Framework

Everything seeded on first boot is a starting point. We should customise it to match our organisation.

### 11.1 Add Our Own Taxonomy Categories

Navigate to **Governance > Taxonomy**.

Think about how our organisation categorises documents. The seeded categories (Legal, Finance, HR, etc.) are generic. We might need:
- Industry-specific categories (e.g., "Patient Records" for healthcare, "Policy Documents" for insurance)
- Department-specific subcategories (e.g., "HR > Grievances", "Finance > Budgets")
- Regulatory categories (e.g., "GDPR Data Subject Requests", "FOI Responses")

For each category, provide:
- **Name** — clear and specific (the LLM reads this)
- **Description** — what types of documents belong here (the LLM reads this too)
- **Keywords** — terms commonly found in these documents (helps the LLM match)
- **Default sensitivity** — what sensitivity to assign by default
- **Retention schedule** — how long to keep documents in this category
- **Parent category** — if this is a subcategory

### 11.2 Add Metadata Schemas

Navigate to **Governance > Metadata Schemas**.

Metadata schemas tell the LLM what structured data to extract from documents in a specific category. For example, for "HR > Leave Records" we might define:

| Field | Type | Required | Example |
|---|---|---|---|
| employee_name | TEXT | Yes | "Jane Smith" |
| leave_type | KEYWORD | Yes | "maternity" |
| start_date | DATE | Yes | "2026-06-01" |
| end_date | DATE | No | "2027-06-01" |
| approving_manager | TEXT | No | "David Jones" |

When the LLM classifies a document as "HR > Leave Records", it also extracts these fields from the text. The extracted values are searchable in Advanced Search.

### 11.3 Adjust Policies

Navigate to **Governance > Policies**.

Review the seeded policies and adjust them for our regulatory environment. We can:
- Change which categories and sensitivities a policy applies to
- Edit the policy rules
- Set effective date ranges
- Create new policies for our specific compliance requirements

### 11.4 Adjust Sensitivity Levels

The four seeded levels (Public, Internal, Confidential, Restricted) follow the UK Government Security Classifications. If our organisation uses a different scheme, we can rename, reorder, or add levels.

---

## Part 13: Create User Accounts

Navigate to **Users** in the admin sidebar.

### 13.1 Roles

The platform ships with four roles. Each bundles a set of permissions:

| Role | Who It Is For | What They Can Do |
|---|---|---|
| **Admin** | System administrators | Everything — full platform access |
| **Compliance Officer** | Records managers, DPOs | Manage governance, review classifications, manage policies, view monitoring |
| **Document Manager** | Team leads, department admins | Upload, manage, and review documents in their area |
| **Standard User** | General staff | Upload and view documents, use search |

### 13.2 Create a User

1. Click **Create User**
2. Fill in: email, password, first name, last name
3. Optionally: department, job title
4. Assign one or more roles (Standard User is the default)
5. Set **sensitivity clearance** (0–3) — this gates which documents they can access:
   - 0 = Public only
   - 1 = Public + Internal
   - 2 = Public + Internal + Confidential
   - 3 = All documents including Restricted
6. Save

The user can now log in at http://localhost/login. They see only the sidebar items their permissions allow.

### 13.3 Taxonomy Grants (Optional)

We can restrict which taxonomy categories a user can access. By default, users can see all categories their sensitivity clearance allows. With taxonomy grants, we can narrow this — for example, limiting an HR team member to only see HR category documents.

---

## Part 14: Connect External Storage (Optional)

Navigate to **Drives** in the sidebar.

The platform can classify documents stored in external providers without importing them. The files stay where they are — we just stream the content temporarily for classification.

### 14.1 Local Storage

Already set up on first boot. Documents uploaded through the UI are stored in MinIO (the local object store). This is the default.

### 14.2 Google Drive

To connect a Google Drive account:

1. Set up Google OAuth credentials in Google Cloud Console (create an OAuth 2.0 Client ID)
2. Add the redirect URI: `{PUBLIC_URL}/api/drives/google/callback`
3. Navigate to **Settings** and enter the Google OAuth Client ID and Secret
4. Navigate to **Drives** and click **Connect Google Drive**
5. Authorise IGC to read the Drive
6. Browse folders, select files, and classify them in place

Classified Google Drive files show their Drive location (owner, account, "Open in Drive" link) alongside the classification result.

---

## Part 15: Pipeline Configuration Summary

The visual pipeline editor (Part 11) is where we build and modify pipelines. Here is a quick reference for the key configuration decisions:

### 15.1 The Default Pipeline

The seeded default pipeline has five steps (one disabled):

```
1. Text Extraction    → EXTRACTOR block (Tika v1)          — enabled
2. PII Scan           → REGEX_SET block (UK PII v1)        — enabled
3. LLM Classification → PROMPT block (Classification v1)   — enabled
4. PII Verification   → CONDITIONAL (LLM verifies PII)     — disabled by default
5. Governance         → ENFORCER block (Standard v1)        — enabled
```

This pipeline handles all documents regardless of type or category.

### 15.2 When to Create Custom Pipelines

We create additional pipelines when different document types need different handling:

- A pipeline for spreadsheets with a different extractor tuned for tabular data
- A pipeline for HR documents with a specialised classification prompt and stricter review threshold
- A pipeline for images that adds an OCR step before classification
- A pipeline for regulated documents with PII Verification enabled

### 15.3 The Confidence Threshold

The **ROUTER** block sets the review threshold — currently 0.7. This means:

- Confidence >= 0.7 → governance applied automatically
- Confidence < 0.7 → routed to the Review Queue

We can adjust this based on our risk appetite:
- Lower threshold (e.g., 0.5) → fewer documents in the review queue, but more risk of incorrect classifications going unreviewed
- Higher threshold (e.g., 0.9) → more documents reviewed by humans, higher quality, but more manual work

---

## Part 16: Day-to-Day Operations

### 16.1 Daily Routine for a Records Manager

1. **Check the Dashboard** — look at the Review Queue count and any failed documents
2. **Work the Review Queue** — approve correct classifications, override incorrect ones, flag missed PII
3. **Check Monitoring** — ensure all services are healthy, no stale documents, queues are draining
4. **Review PII findings** — go to PII & SARs to review detected personal information

### 16.2 Weekly Routine for an Admin

1. **Check Audit Log** — review document access patterns and any unusual activity
2. **Review Block Feedback** — check if any blocks have accumulated enough corrections to warrant improvement
3. **Review pipeline metrics** — check classification accuracy (correction rate), throughput, and error rates
4. **Update governance** — adjust taxonomy, policies, or retention schedules based on organisational changes

### 16.3 Handling Failures

When documents fail at any pipeline stage:

1. Go to **Monitoring**
2. View the failed documents — each shows the error message and which stage failed
3. Fix the underlying issue (e.g., restart a downed service, fix an API key)
4. Click **Retry Failed** to re-queue all failed documents

The platform never silently drops documents. Every failure is recorded with an error message, the stage that failed, and a timestamp. Failed documents can always be retried.

---

## Part 17: Advanced Search

Navigate to **Advanced Search** in the sidebar.

Elasticsearch indexes every classified document. We can search by:

- **Full text** — search within the extracted document content
- **Category** — filter by taxonomy category
- **Sensitivity** — filter by sensitivity level
- **Tags** — filter by LLM-assigned tags
- **Extracted metadata** — search structured fields (e.g., "employee_name: Jane Smith")
- **PII status** — find documents with detected PII
- **Date ranges** — filter by upload date, classification date, or metadata dates

---

## Part 18: PII and Subject Access Requests

Navigate to **PII & SARs** in the sidebar.

### 18.1 PII Management

Every document is scanned for PII during text extraction. The PII findings show:
- **Type** — National Insurance number, email, phone, postcode, bank account, or custom types
- **Value** — the detected text (masked for sensitive types)
- **Location** — where in the document the PII was found
- **Status** — Detected, Reviewed, Redacted, Dismissed

We can review PII findings, dismiss false positives, or flag items the system missed.

### 18.2 Subject Access Requests

When someone exercises their right to access their personal data (under GDPR), we can:
1. Create a Subject Access Request
2. Search across all documents for PII matching that person
3. Review and compile the results
4. Track the request through to completion

---

## Part 19: Troubleshooting

### Nothing Happens After Upload

**Symptom:** Document stays in "Uploaded" status.

**Check:** Is RabbitMQ running? `docker compose logs rabbitmq`. Is the doc-processor service running? `docker compose ps doc-processor`.

**Fix:** `docker compose restart doc-processor`

### Classification Fails for Every Document

**Symptom:** Documents move to "Classification Failed".

**Check:** Navigate to AI settings — is an LLM provider configured? Is the API key valid? If using Ollama, is it running? `docker compose logs llm-worker` will show the specific error.

**Fix:** Configure a valid LLM provider in Settings > AI, then retry failed documents from Monitoring.

### "Connection Refused" Errors in Logs

**Symptom:** Services cannot reach each other.

**Check:** `docker compose ps` — are all services running? Services reference each other by container name (e.g., `mongo:27017`, `rabbitmq:5672`).

**Fix:** `docker compose down && docker compose up --build -d` to rebuild and restart everything.

### Documents Stuck in "Processing" or "Classifying"

**Symptom:** Documents have been in an in-flight state for more than 10 minutes.

**Fix:** Go to Monitoring, click **Reset Stale**. This pushes them back to the previous state so they re-enter the queue.

### RabbitMQ Management Console

For deeper queue debugging, RabbitMQ has its own web console at **http://localhost:15672** (username: guest, password: guest). We can see queue depths, message rates, and consumer connections.

### MinIO Console

To inspect stored files directly, MinIO has a web console at **http://localhost:9001** (username: minioadmin, password: minioadmin).

---

## Quick Reference

| What | Where |
|---|---|
| Platform UI | http://localhost |
| Login | http://localhost/login |
| RabbitMQ console | http://localhost:15672 |
| MinIO console | http://localhost:9001 |
| Elasticsearch | http://localhost:9200 |
| API health check | http://localhost/api/actuator/health |
| Default admin email | `admin@governanceledstore.co.uk` |
| Default admin password | `ChangeMe123!` |
| Start everything | `docker compose up --build -d` |
| Stop everything | `docker compose down` |
| View logs | `docker compose logs <service-name>` |
| Rebuild one service | `docker compose up --build -d <service-name>` |
