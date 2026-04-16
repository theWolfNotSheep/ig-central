export type HelpSection = {
    slug: string;
    title: string;
    category: "getting-started" | "user" | "records-manager" | "admin";
    icon: string; // lucide icon name
    summary: string;
    content: HelpBlock[];
};

export type HelpBlock =
    | { type: "paragraph"; text: string }
    | { type: "heading"; text: string }
    | { type: "steps"; items: string[] }
    | { type: "tip"; text: string }
    | { type: "warning"; text: string }
    | { type: "list"; items: string[] }
    | { type: "faq"; question: string; answer: string }
    | { type: "table"; headers: string[]; rows: string[][] };

export const helpSections: HelpSection[] = [
    // ── Getting Started ───────────────────────────────
    {
        slug: "getting-started",
        title: "Getting Started",
        category: "getting-started",
        icon: "Rocket",
        summary: "Learn the basics of IG Central — logging in, navigating, and understanding the platform.",
        content: [
            { type: "paragraph", text: "IG Central is an AI-powered Information Governance platform. It classifies documents, detects PII, enforces governance policies, and manages the document lifecycle across your organisation." },
            { type: "heading", text: "Logging In" },
            { type: "steps", items: [
                "Navigate to your organisation's IG Central URL.",
                "Enter your email and password, or click 'Sign in with Google' if configured.",
                "You'll be taken to the Dashboard showing your document statistics and quick actions.",
            ]},
            { type: "heading", text: "Understanding the Sidebar" },
            { type: "list", items: [
                "**Dashboard** — overview of your documents, pipeline stats, and recent activity.",
                "**Drives** — browse and upload files to connected storage (local, Google Drive, S3, etc.).",
                "**Documents** — search and browse classified documents by category, sensitivity, or content.",
                "**PII & SARs** — search for personally identifiable information and manage Subject Access Requests.",
                "**Governance** — configure sensitivity labels, taxonomy, policies, retention schedules.",
                "**Review Queue** — review AI classifications that need human approval.",
            ]},
            { type: "heading", text: "Your First Document" },
            { type: "steps", items: [
                "Go to **Drives** in the sidebar.",
                "Select **Local Storage** (or connect a Google Drive).",
                "Click **Upload** or drag-and-drop files into the browser.",
                "The AI pipeline will automatically extract text, detect PII, classify the document, and apply governance policies.",
                "Check the **Dashboard** to see your document moving through the pipeline.",
            ]},
            { type: "tip", text: "The pipeline typically takes 10-30 seconds per document. You can watch progress in real-time on the Dashboard or Monitoring page." },
        ],
    },

    // ── Drives & Storage ──────────────────────────────
    {
        slug: "drives",
        title: "Drives & Storage",
        category: "user",
        icon: "Database",
        summary: "Upload files, connect external storage, and manage your documents across multiple drives.",
        content: [
            { type: "paragraph", text: "All files in IG Central live on a 'drive' — either Local Storage (built-in) or an external provider like Google Drive. The Drives page is where you browse, upload, and manage files." },
            { type: "heading", text: "Local Storage" },
            { type: "paragraph", text: "Local Storage is the built-in drive available to all users. Files uploaded here are stored in the platform's object storage and processed through the AI classification pipeline." },
            { type: "heading", text: "Connecting Google Drive" },
            { type: "steps", items: [
                "Click **Connect Google Drive** at the bottom of the drive selector.",
                "Sign in with your Google account in the popup window.",
                "Grant IG Central permission to read and write your Drive files.",
                "Your Google Drive appears in the drive selector — browse folders and select files to classify.",
            ]},
            { type: "heading", text: "Classifying External Files" },
            { type: "paragraph", text: "Files in Google Drive (and future providers) stay in their original location. IG Central reads the content, classifies it, and writes classification metadata back to the file as custom properties." },
            { type: "steps", items: [
                "Browse to a folder in your connected drive.",
                "Select files using the checkboxes.",
                "Click **Classify N** to queue them for AI classification.",
                "Classification results appear in the 'Classification' column once processing completes.",
            ]},
            { type: "heading", text: "Watching Folders" },
            { type: "paragraph", text: "You can set a folder to be 'watched' — new files added to that folder will be automatically picked up and classified every 5 minutes." },
            { type: "tip", text: "If you see an amber dot on a drive, it has read-only access. Disconnect and reconnect it to grant write permission for classification write-back." },
            { type: "heading", text: "Uploading Files" },
            { type: "list", items: [
                "Click the **Upload** button in the toolbar, or drag-and-drop files onto the page.",
                "Multiple files can be uploaded at once.",
                "Supported formats: PDF, DOCX, XLSX, CSV, TXT, images (with OCR), and more.",
                "Files are automatically queued for the AI classification pipeline.",
            ]},
        ],
    },

    // ── Searching Documents ───────────────────────────
    {
        slug: "documents",
        title: "Searching Documents",
        category: "user",
        icon: "Search",
        summary: "Find documents by content, category, sensitivity, or metadata using the search and browse interface.",
        content: [
            { type: "paragraph", text: "The Documents page is your search and browse interface for all classified documents. Use the search bar to find documents by content, or filter by category using the sidebar tree." },
            { type: "heading", text: "Full-Text Search" },
            { type: "paragraph", text: "Type keywords in the search bar and press Enter. The search looks across document names, extracted text, and metadata fields." },
            { type: "heading", text: "Category Browsing" },
            { type: "paragraph", text: "The left sidebar shows the taxonomy tree. Click a category to filter documents. The hierarchy is inherited — clicking a parent category shows documents from all child categories too." },
            { type: "heading", text: "Sorting" },
            { type: "paragraph", text: "Click any column header (Name, Status, Category, Sensitivity, Size, Date) to sort. Click again to reverse the sort direction." },
            { type: "heading", text: "Viewing a Document" },
            { type: "steps", items: [
                "Click any document row to open the document viewer.",
                "The viewer shows a preview/text extraction on the left and a classification panel on the right.",
                "The classification panel shows: category, sensitivity, PII findings, metadata, governance actions, and audit history.",
                "Click **Back to list** to return to the search results.",
            ]},
            { type: "heading", text: "Advanced Search" },
            { type: "paragraph", text: "For advanced faceted search with metadata filters, use the dedicated Search page at /search. This provides granular filtering by status, sensitivity, date range, and extracted metadata fields." },
        ],
    },

    // ── Understanding Classifications ─────────────────
    {
        slug: "classifications",
        title: "Understanding Classifications",
        category: "user",
        icon: "Brain",
        summary: "What categories, sensitivity labels, and confidence scores mean for your documents.",
        content: [
            { type: "paragraph", text: "When a document is uploaded, the AI analyses it and assigns a classification. This includes a taxonomy category, sensitivity label, confidence score, and optionally extracted metadata." },
            { type: "heading", text: "Taxonomy Categories" },
            { type: "paragraph", text: "Your organisation's taxonomy is a hierarchical tree of document categories (e.g. HR > Employee Records > Contracts). The AI selects the most appropriate category based on the document content and any correction history from past reviews." },
            { type: "heading", text: "Sensitivity Labels" },
            { type: "table", headers: ["Label", "Meaning", "Example"], rows: [
                ["PUBLIC", "No restrictions — can be shared freely", "Marketing brochures, public reports"],
                ["INTERNAL", "Internal use only — not for external sharing", "Team meeting notes, internal memos"],
                ["CONFIDENTIAL", "Restricted access — contains sensitive data", "Financial reports, HR records"],
                ["RESTRICTED", "Highest restriction — regulated or legally protected", "Medical records, legal contracts with PII"],
            ]},
            { type: "heading", text: "Confidence Scores" },
            { type: "paragraph", text: "Each classification includes a confidence score (0-100%). Documents below the confidence threshold are automatically routed to the Review Queue for human verification." },
            { type: "list", items: [
                "**90%+** — High confidence. Classification is likely correct.",
                "**70-89%** — Medium confidence. May be auto-approved but worth checking.",
                "**Below 70%** — Low confidence. Routed to Review Queue for human decision.",
            ]},
            { type: "heading", text: "Document Traits" },
            { type: "paragraph", text: "In addition to classification, the AI detects document traits — characteristics like whether it's a template, draft, final version, inbound/outbound, original or copy. Traits affect how PII is handled (e.g., PII in templates is informational only)." },
        ],
    },

    // ── PII Detection ──────────────────────────────���──
    {
        slug: "pii",
        title: "PII Detection & Management",
        category: "user",
        icon: "ShieldAlert",
        summary: "How IG Central detects personally identifiable information and how to manage findings.",
        content: [
            { type: "paragraph", text: "IG Central uses a two-tier PII detection system: Tier 1 uses regex pattern matching for known PII formats (NI numbers, postcodes, emails), and Tier 2 uses the AI model for contextual PII detection." },
            { type: "heading", text: "Viewing PII Findings" },
            { type: "paragraph", text: "When viewing a document, the classification panel shows a 'PII Detected' section listing all found PII entities. Each shows the type, a redacted preview, confidence score, and detection method (PATTERN or LLM)." },
            { type: "paragraph", text: "Duplicate PII (e.g., the same email appearing 4 times) is consolidated into a single row with a count badge." },
            { type: "heading", text: "Dismissing False Positives" },
            { type: "steps", items: [
                "Click **Not PII?** on any finding you believe is incorrect.",
                "Explain why it's not personal data (e.g., 'This is a public company address').",
                "Optionally add context about what the data actually represents.",
                "The dismissal is recorded and feeds back to improve future detection accuracy.",
            ]},
            { type: "heading", text: "Reporting Missed PII" },
            { type: "steps", items: [
                "Click the pencil icon on the PII section header.",
                "Select the PII type from the dropdown (e.g., Email, Phone, National Insurance).",
                "Describe what was missed and where in the document.",
                "This creates feedback that improves the PII detection patterns over time.",
            ]},
            { type: "heading", text: "PII Search" },
            { type: "paragraph", text: "The PII & SARs page lets you search across all documents for specific PII values — find every document containing a particular email address, name, or identifier." },
            { type: "tip", text: "Your PII feedback directly improves detection accuracy. Every false positive dismissal and missed PII report trains the system to be more precise." },
        ],
    },

    // ── Review Queue ──────────────────────────────────
    {
        slug: "review",
        title: "Review Queue",
        category: "records-manager",
        icon: "FileSearch",
        summary: "How to review, approve, reject, and override AI classifications.",
        content: [
            { type: "paragraph", text: "Documents classified with low confidence are automatically routed to the Review Queue. As a records manager, you review the AI's decision and either approve, reject, or override it." },
            { type: "heading", text: "Reviewing a Document" },
            { type: "steps", items: [
                "Click a document in the review queue to see its classification details.",
                "Review the AI's category, sensitivity, confidence score, and reasoning.",
                "Check the PII findings and extracted metadata.",
                "Choose an action: **Approve**, **Reject** (with reason), or **Override** (change category/sensitivity).",
            ]},
            { type: "heading", text: "Overriding Classifications" },
            { type: "paragraph", text: "If the AI got the category or sensitivity wrong, use Override to correct it. Your correction is stored and used to improve future classifications — the AI learns from every override." },
            { type: "heading", text: "The Feedback Loop" },
            { type: "paragraph", text: "Every review action feeds back into the system:" },
            { type: "list", items: [
                "**Approvals** — positive signal confirming the AI got it right.",
                "**Rejections** — the document goes back for reclassification.",
                "**Overrides** — the correction is stored and shown to the AI on similar future documents.",
                "**PII dismissals** — false positive patterns are learned.",
            ]},
            { type: "tip", text: "Over time, as corrections accumulate, the AI becomes more accurate and fewer documents need manual review." },
        ],
    },

    // ── Governance Framework ──────────────────────────
    {
        slug: "governance",
        title: "Governance Configuration",
        category: "records-manager",
        icon: "Shield",
        summary: "Managing sensitivity labels, taxonomy, policies, retention schedules, and metadata schemas.",
        content: [
            { type: "paragraph", text: "The Governance page is where you configure the rules that drive the entire platform. Everything here is configuration-driven — changes take effect immediately without redeployment." },
            { type: "heading", text: "Sensitivity Labels" },
            { type: "paragraph", text: "Drag to reorder priority (level 0 = lowest, highest number = most restricted). Each label has guidelines and examples that the AI uses when deciding which level to assign." },
            { type: "heading", text: "Taxonomy Categories (ISO 15489-aligned)" },
            { type: "paragraph", text: "The taxonomy follows the ISO 15489 Business Classification Scheme — a 3-tier hierarchy: Function → Activity → Record Class. Each node has a unique classification code (e.g. COR-GOV-BRD), level, jurisdiction, retention period text, legal citation, typical records list, and PII/vital record flags." },
            { type: "list", items: [
                "**FUNCTION** — top-level business function (e.g. Corporate Governance & Administration, code COR).",
                "**ACTIVITY** — major task within a function (e.g. Corporate Governance, code COR-GOV).",
                "**TRANSACTION** (Record Class) — specific record type (e.g. Board & Committee Records, code COR-GOV-BRD). Carries the retention period and legal citation.",
            ]},
            { type: "tip", text: "Add child entries with the + button on each Function or Activity row. Use the parent dropdown in the editor to re-parent existing entries. Functions and Activities can have children; Record Classes are leaf nodes." },
            { type: "heading", text: "Editing Taxonomy Entries" },
            { type: "paragraph", text: "Click the pencil icon on any taxonomy row to open the editor. The form has fields for every ISO 15489 attribute — classification code (auto-suggested), name, description, scope notes, retention trigger, retention period text, legal citation, jurisdiction, owner, custodian, review cycle, plus PII and vital-record flags." },
            { type: "heading", text: "CSV Export & Import" },
            { type: "paragraph", text: "Export the taxonomy as a 9-column CSV in the standard ISO 15489 format (Tier 1 Function, Function Code, Tier 2 Activity, Tier 3 Record Class, Record Code, Typical Records, Jurisdiction, Retention Period, Legal Citation). The same format can be imported in the Governance Hub to publish updated packs." },
            { type: "heading", text: "Policies" },
            { type: "paragraph", text: "Governance policies define rules that are enforced after classification — access controls, handling requirements, and compliance obligations." },
            { type: "heading", text: "Retention Schedules" },
            { type: "paragraph", text: "Define how long documents of each type must be kept. Now stores both legacy days and ISO 8601 duration (P7Y), plus retention trigger (DATE_CREATED, DATE_CLOSED, EVENT_BASED, END_OF_FINANCIAL_YEAR, SUPERSEDED) and jurisdiction (UK, US, EU). When a document's retention period expires, the configured disposition action (DELETE, ARCHIVE, TRANSFER, REVIEW, ANONYMISE, PERMANENT) is triggered automatically." },
            { type: "heading", text: "PII Types" },
            { type: "paragraph", text: "Manage the types of PII the system detects — National Insurance numbers, email addresses, phone numbers, etc. You can add organisation-specific PII types." },
            { type: "heading", text: "Metadata Schemas" },
            { type: "paragraph", text: "Define structured metadata extraction schemas linked to taxonomy categories. When the AI classifies a document into a category with a schema, it extracts the defined fields (dates, names, amounts, etc.) automatically." },
        ],
    },

    // ── ISO 15489 Reference ───────────────────────────
    {
        slug: "iso15489",
        title: "ISO 15489 Records Standard",
        category: "records-manager",
        icon: "BookOpen",
        summary: "How the platform's taxonomy aligns with the ISO 15489 records management standard.",
        content: [
            { type: "paragraph", text: "ISO 15489 is the international standard for records management. It defines a function-based Business Classification Scheme (BCS) with three tiers, retention triggers, and disposal authorities. IG Central's taxonomy and retention models are aligned to this standard." },
            { type: "heading", text: "The 3-Tier Hierarchy" },
            { type: "table", headers: ["Tier", "Level", "What it represents", "Example"], rows: [
                ["1", "FUNCTION", "Top-level area of responsibility — relatively stable over time", "Human Resources (HR), Finance (FIN)"],
                ["2", "ACTIVITY", "Major task performed to fulfil a function", "Recruitment (HR-REC), Payroll (FIN-PAY)"],
                ["3", "TRANSACTION", "Specific record class within an activity — carries retention rules", "Job Postings (HR-REC-APP), Tax Returns (FIN-TAX-FIL)"],
            ]},
            { type: "heading", text: "Classification Codes" },
            { type: "paragraph", text: "Each node has a hierarchical code combining its tier 1, 2, and 3 prefixes — e.g. COR-GOV-BRD = Corporate Governance Function → Corporate Governance Activity → Board & Committee Records. Codes are stable identifiers that survive taxonomy reorganisation, used for CSV export, hub pack distribution, and audit." },
            { type: "heading", text: "Retention Metadata" },
            { type: "list", items: [
                "**Retention Period Text** — human-readable retention (e.g. \"7 years after termination\", \"Permanent / 7+ years\").",
                "**Retention Trigger** — what starts the retention clock: DATE_CREATED, DATE_CLOSED, EVENT_BASED, END_OF_FINANCIAL_YEAR, SUPERSEDED.",
                "**Legal Citation** — the regulatory authority (e.g. \"IRS requirements (IRC §6001)\", \"NHS Records Management Code\").",
                "**Jurisdiction** — geographical/legal scope (US, UK, EU). Different jurisdictions have different retention requirements for the same record class.",
            ]},
            { type: "heading", text: "Per-Node Flags" },
            { type: "list", items: [
                "**Personal Data Flag** — records here typically contain personal data (GDPR relevance).",
                "**Vital Record Flag** — records are vital for business continuity and warrant extra protection.",
                "**Owner / Custodian** — business accountability for the records.",
                "**Review Cycle** — ISO 8601 duration (P1Y = annually) defining how often the classification node itself should be reviewed for currency.",
            ]},
            { type: "heading", text: "Pre-Built Frameworks" },
            { type: "paragraph", text: "The platform ships with seeded UK governance categories and can import additional frameworks from the Governance Hub:" },
            { type: "list", items: [
                "**UK General Governance Framework** — 17 categories aligned with GDPR, Companies Act 2006, HMRC, FCA SYSC 9.",
                "**UK Healthcare Records Management** — NHS-specific categories with retention from the NHS Records Management Code of Practice.",
                "**US Generic Records Taxonomy (ISO 15489)** — 8 functions, 33 activities, 34 record classes covering Corporate, Finance, HR, Operations, Sales, Procurement, Legal, IT — derived directly from the reference spreadsheet.",
            ]},
        ],
    },

    // ── Admin: User Management ────────────────────────
    {
        slug: "admin-users",
        title: "User Management",
        category: "admin",
        icon: "Users",
        summary: "Users list, individual profile pages, roles, taxonomy access matrix, password reset.",
        content: [
            { type: "paragraph", text: "Administrators manage user accounts, role assignments, taxonomy access grants, and sensitivity clearance levels. The user list shows all users; clicking a row opens that user's dedicated profile page." },
            { type: "heading", text: "Users List" },
            { type: "paragraph", text: "Browse all users with search by email, name, or department. Each row shows roles, clearance level, and active/disabled status. Click any row to open the user's profile." },
            { type: "heading", text: "User Profile Pages" },
            { type: "paragraph", text: "Each user has a dedicated profile at /admin/users/{id} with all administration tools in one place. Use the **Back to Users** link to return to the list. The profile is split into two columns:" },
            { type: "list", items: [
                "**Left column** — Profile card (email, department, login method, password reset for non-OAuth users), Roles editor, Sensitivity Clearance, and Effective Permissions.",
                "**Right column** — Taxonomy Access Matrix (see below).",
            ]},
            { type: "heading", text: "Creating Users" },
            { type: "steps", items: [
                "Click **Create User** in the toolbar.",
                "Fill in email, password, name, department, job title.",
                "Tick the roles to assign — defaults are preselected.",
                "Save. The user receives an email/password account they can sign in with immediately.",
            ]},
            { type: "heading", text: "Resetting Local Passwords" },
            { type: "paragraph", text: "On a user's profile, the Profile card shows a **Reset Password** link for users who signed up via email/password. Set a new password (minimum 8 characters). The button is hidden for OAuth users (Google/GitHub/LinkedIn) since they manage passwords with their identity provider." },
            { type: "heading", text: "Roles & Permissions" },
            { type: "paragraph", text: "Roles bundle permissions together. Each permission maps to a specific feature (e.g., DOCUMENT_UPLOAD, TAXONOMY_READ). Roles where adminRole=true grant access to /api/admin/* endpoints — the governance, users, and other admin pages. Without an admin role a user gets 403 on the admin UI even if they have taxonomy grants." },
            { type: "warning", text: "Granting taxonomy access in the matrix below does NOT grant access to the admin governance UI. Those are different layers — taxonomy grants control document access; admin role controls UI access." },
            { type: "heading", text: "Taxonomy Access Matrix" },
            { type: "paragraph", text: "The right column of the profile shows every Activity and Record Class in the taxonomy. Each row has 6 toggle buttons:" },
            { type: "table", headers: ["Button", "Effect"], rows: [
                ["READ", "Toggles READ permission. Required for the user to see documents in this category."],
                ["CREATE", "Toggles CREATE. Granting any write op (CREATE/UPDATE/DELETE) automatically adds READ."],
                ["UPDATE", "Toggles UPDATE."],
                ["DELETE", "Toggles DELETE."],
                ["ALL", "Grants or revokes all 4 operations at once."],
                ["+ subs", "Visible only when granted. Toggles whether access cascades to descendant categories."],
            ]},
            { type: "tip", text: "Use the search box to filter the matrix when there are many taxonomies. The summary at the top shows how many taxonomies the user has access to." },
            { type: "heading", text: "Three-Layer Access Control" },
            { type: "paragraph", text: "Document access requires all three layers to allow it:" },
            { type: "list", items: [
                "**Permission layer** — role-based feature access (can upload, can review, etc.).",
                "**Taxonomy layer** — which categories the user can see documents from (the matrix above).",
                "**Sensitivity layer** — clearance level determines maximum sensitivity they can access.",
            ]},
            { type: "warning", text: "Reducing a user's clearance level immediately restricts their access to documents above their new level. Audit this change before applying." },
        ],
    },

    // ── Admin: Governance Hub ─────────────────────────
    {
        slug: "admin-hub",
        title: "Governance Hub",
        category: "admin",
        icon: "Database",
        summary: "Connect to the marketplace of pre-built governance packs — taxonomies, retention schedules, legislation.",
        content: [
            { type: "paragraph", text: "The Governance Hub is a separate marketplace service hosting curated, versioned governance packs that can be imported into IG Central. The hub runs as its own stack on its own domain (e.g. hub.igcentral.com) and uses an API key for authentication." },
            { type: "heading", text: "What's in a Pack" },
            { type: "paragraph", text: "A governance pack is a versioned bundle that can contain any of these component types — you import only the pieces you need." },
            { type: "list", items: [
                "**TAXONOMY_CATEGORIES** — ISO 15489 BCS entries (Functions, Activities, Record Classes).",
                "**RETENTION_SCHEDULES** — retention periods with disposition actions and legal basis.",
                "**LEGISLATION** — referenced laws and regulations.",
                "**SENSITIVITY_DEFINITIONS** — sensitivity labels with guidelines.",
                "**METADATA_SCHEMAS** — structured field extraction templates.",
                "**PII_TYPE_DEFINITIONS** — PII detection patterns.",
                "**GOVERNANCE_POLICIES** — enforcement rules.",
                "**STORAGE_TIERS** — storage configurations.",
                "**TRAIT_DEFINITIONS** — document trait classifiers.",
            ]},
            { type: "heading", text: "Connecting to the Hub" },
            { type: "steps", items: [
                "Go to **Settings → Governance Hub** in the admin UI.",
                "Set the Hub URL (e.g. https://hub.igcentral.com).",
                "Generate an API key in the hub admin UI (API Keys page) and paste it into Settings.",
                "Save. Now you can browse packs at **/governance/hub**.",
            ]},
            { type: "heading", text: "Browsing & Importing Packs" },
            { type: "steps", items: [
                "Open **/governance/hub** to browse published packs.",
                "Filter by jurisdiction, industry, regulation, or tag.",
                "Click a pack to see its versions and components.",
                "Click **Import** on a version to bring it into your platform — preview first to see exactly what will be created/updated/skipped.",
                "Choose import mode: **MERGE** (skip existing items) or **OVERWRITE** (replace existing).",
            ]},
            { type: "tip", text: "Always run a Preview import first. The preview shows per-component counts (created, updated, skipped, failed) without writing any data." },
            { type: "heading", text: "Hub Admin (publishing your own packs)" },
            { type: "paragraph", text: "Hub admins can manage packs at the hub URL itself (https://hub.igcentral.com). Each pack has multiple versions — versions are immutable; changes always create new versions." },
            { type: "heading", text: "Three Ways to Author a Pack Version" },
            { type: "list", items: [
                "**Edit Taxonomy** (recommended) — opens a field-based tree editor at /packs/{id}/taxonomy. Add/edit/delete entries with full ISO 15489 fields, then publish as a new version.",
                "**Import CSV** — upload or paste a 9-column ISO 15489 CSV. The hub auto-builds the taxonomy hierarchy and unique retention schedules.",
                "**Publish New Version** — paste raw JSON for each component (advanced).",
            ]},
            { type: "heading", text: "CSV Import Format" },
            { type: "paragraph", text: "The CSV must match this exact 9-column header (matches platform export):" },
            { type: "table", headers: ["Column", "Example"], rows: [
                ["Tier 1 Function", "Finance & Accounting"],
                ["Function Code", "FIN"],
                ["Tier 2 Activity", "Financial Reporting"],
                ["Tier 3 Record Class", "Financial Statements"],
                ["Record Code", "FIN-REP-FST"],
                ["Typical Records", "Balance sheets, income statements"],
                ["Jurisdiction", "US"],
                ["Retention Period", "7 years"],
                ["Legal Citation", "IRS requirements (IRC §6001)"],
            ]},
        ],
    },

    // ── Admin: Pipelines ──────────────────────────────
    {
        slug: "admin-pipelines",
        title: "AI Pipelines",
        category: "admin",
        icon: "Workflow",
        summary: "Build document processing workflows with a visual low-code editor — drag nodes, connect steps, configure AI and logic.",
        content: [
            { type: "paragraph", text: "Pipelines define how documents are processed using a visual workflow builder. Drag nodes from the palette onto the canvas, connect them with edges to define the processing flow, and configure each step by clicking on it." },

            { type: "heading", text: "The Visual Editor" },
            { type: "paragraph", text: "The pipeline editor has three panels:" },
            { type: "list", items: [
                "**Left — Node Palette**: Drag node types onto the canvas to add steps. Nodes are grouped into categories: Triggers, Processing, Logic, Actions, and Error Handling.",
                "**Centre — Canvas**: The workflow diagram. Connect nodes by dragging from output handles (bottom/sides) to input handles (top). Edges show the flow direction with colour coding.",
                "**Right — Inspector**: Click any node to configure it. Shows label, description, and type-specific settings.",
            ]},
            { type: "tip", text: "Use the Auto Layout button in the toolbar to automatically arrange nodes top-to-bottom in execution order." },

            { type: "heading", text: "Node Types" },
            { type: "table", headers: ["Node", "Purpose", "Outputs"], rows: [
                ["Trigger", "What starts the pipeline (upload, folder watch, manual, scheduled)", "One output"],
                ["Text Extraction", "Extract text from documents using Tika or OCR", "Output + Error"],
                ["PII Scanner", "Detect PII using regex patterns from a Block Library REGEX_SET block", "Output + Error"],
                ["AI Classification", "Send document to LLM for classification using a PROMPT block", "Output + Error"],
                ["Condition", "Branch based on a rule (e.g. confidence > 0.8, PII detected)", "True (green) + False (red)"],
                ["Governance", "Apply retention schedules, storage tiers, and policies", "One output"],
                ["Human Review", "Route document to the review queue for manual approval", "Approved (green) + Rejected (red)"],
                ["Notification", "Send email or webhook notification on completion/failure", "Passthrough output"],
                ["Error Handler", "Catch errors from connected steps — define retry and fail behaviour", "Retry (amber) + Fail (red)"],
            ]},

            { type: "heading", text: "Building a Pipeline" },
            { type: "steps", items: [
                "Open the AI Pipelines page and click the **grid icon** on an existing pipeline, or create a new pipeline and enter the visual editor.",
                "**Drag a Trigger node** from the palette as the starting point of your workflow.",
                "**Add processing nodes** — drag Text Extraction, PII Scanner, and AI Classification onto the canvas.",
                "**Connect nodes** by dragging from the output handle (bottom) of one node to the input handle (top) of the next.",
                "**Add a Condition node** to create branches — for example, route low-confidence documents to Human Review and high-confidence ones directly to Governance.",
                "**Connect the Condition outputs**: drag from the green True handle to one path and the red False handle to another.",
                "**Add Error Handlers** by dragging from the red Error handle on any processing node to an Error Handler node.",
                "**Click each node** to open the inspector and configure it — link blocks, set conditions, choose models.",
                "Click **Save** in the toolbar to persist the workflow.",
            ]},

            { type: "heading", text: "Connecting Nodes" },
            { type: "paragraph", text: "Nodes have colour-coded handles that indicate what they connect to:" },
            { type: "list", items: [
                "**Gray** (bottom) — standard output, connects to the next step's input (top)",
                "**Green** — True/Approved branch from a Condition or Human Review node",
                "**Red** — False/Rejected/Error branch",
                "**Amber** — Retry output from an Error Handler",
                "Edges are colour-coded to match: green for happy paths, red for error/reject paths, amber for retry paths.",
            ]},

            { type: "heading", text: "Configuring Conditions" },
            { type: "paragraph", text: "Condition nodes let you branch the workflow based on document properties. In the inspector, set:" },
            { type: "list", items: [
                "**Field** — what to evaluate: confidence score, PII count, sensitivity level, or file type",
                "**Operator** — greater than, less than, equals, not equals",
                "**Value** — the threshold or value to compare against",
            ]},
            { type: "paragraph", text: "Example: Field = `confidence`, Operator = `>`, Value = `0.8` routes high-confidence documents down the True path and low-confidence ones down the False path." },

            { type: "heading", text: "Error Handling" },
            { type: "paragraph", text: "Any node with a red Error handle can be connected to an Error Handler. If that step fails, the error is caught and you control what happens:" },
            { type: "list", items: [
                "**Retry count** — how many times to retry before giving up",
                "**Retry delay** — seconds between retries",
                "**Fallback** — what to do after all retries fail: mark as failed, skip the step, or route to human review",
            ]},

            { type: "heading", text: "Linking Blocks" },
            { type: "paragraph", text: "Processing nodes (PII Scanner, AI Classification) can be linked to versioned Blocks from the Block Library. In the inspector, select a block and optionally pin to a specific version. This means you can update a prompt or PII pattern set in the Block Library without editing the pipeline." },

            { type: "heading", text: "Node Status" },
            { type: "paragraph", text: "Each node shows a status dot:" },
            { type: "list", items: [
                "**Green dot** — fully configured and ready",
                "**Amber dot** — incomplete configuration (missing required settings or block link)",
            ]},

            { type: "heading", text: "Taxonomy Routing" },
            { type: "paragraph", text: "Pipelines can be bound to specific taxonomy categories in the pipeline settings (gear icon or edit from the list view). Documents classified into those categories use that pipeline. One pipeline can be marked as the default fallback for unmatched categories." },

            { type: "heading", text: "Keyboard Shortcuts" },
            { type: "table", headers: ["Key", "Action"], rows: [
                ["Delete / Backspace", "Delete selected nodes or edges"],
                ["Click + Drag", "Move nodes on the canvas"],
                ["Scroll wheel", "Zoom in/out"],
                ["Click canvas", "Deselect all (closes inspector)"],
            ]},
        ],
    },

    // ── Admin: Block Library ──────────────────────────
    {
        slug: "admin-blocks",
        title: "Block Library",
        category: "admin",
        icon: "Blocks",
        summary: "Versioned processing blocks — prompts, patterns, extractors, routers, enforcers.",
        content: [
            { type: "paragraph", text: "Blocks are reusable, versioned processing units that plug into pipelines. Each block has full version history, user feedback tracking, and AI-assisted improvement." },
            { type: "heading", text: "Block Types" },
            { type: "table", headers: ["Type", "What it contains", "Example"], rows: [
                ["Prompt", "System prompt + user template", "Classification Prompt v3"],
                ["Regex Set", "PII detection patterns with confidence", "UK PII Patterns v2"],
                ["Extractor", "Text extraction config", "Tika Text Extractor"],
                ["Router", "Conditional routing logic", "Confidence Router"],
                ["Enforcer", "Governance enforcement rules", "Standard Enforcement"],
            ]},
            { type: "heading", text: "Version Control" },
            { type: "list", items: [
                "Every block has an immutable version history — publish creates a new version, never overwrites.",
                "The active version is what pipelines use by default.",
                "Save a draft, review it, then publish as a new version.",
                "Rollback to any previous version with one click.",
                "Compare versions side-by-side with a visual diff.",
            ]},
            { type: "heading", text: "Improve with AI" },
            { type: "paragraph", text: "When a block accumulates user feedback (corrections, false positives, missed detections), click 'Improve with AI' to generate an improved version. The AI analyses the feedback and suggests changes. Review the suggestion, then publish if satisfied." },
        ],
    },

    // ── Admin: Monitoring ─────────────────────────────
    {
        slug: "admin-monitoring",
        title: "Monitoring & Health",
        category: "admin",
        icon: "Activity",
        summary: "Pipeline metrics, service health, queue depths, and error management.",
        content: [
            { type: "paragraph", text: "The Monitoring page provides real-time visibility into the health and performance of the entire classification pipeline." },
            { type: "heading", text: "Pipeline Metrics" },
            { type: "list", items: [
                "**Status counts** — documents at each pipeline stage (uploaded, processing, classified, etc.).",
                "**Throughput** — documents processed in the last 24 hours and 7 days.",
                "**Average classification time** — how long the AI takes per document.",
                "**Queue depths** — messages waiting in each RabbitMQ queue.",
            ]},
            { type: "heading", text: "Managing Stuck Documents" },
            { type: "paragraph", text: "Documents can get stuck if a service restarts during processing. The system automatically detects documents stuck for more than 10 minutes and re-queues them. You can also manually:" },
            { type: "list", items: [
                "**Reset Stale** — find and re-queue documents stuck in processing states.",
                "**Retry Failed** — re-queue all failed documents for another attempt.",
                "**Cancel All** — purge queues and reset all in-progress documents.",
            ]},
            { type: "heading", text: "System Errors" },
            { type: "paragraph", text: "Unhandled errors are automatically persisted and visible in the monitoring page. Each error shows the category (PIPELINE, QUEUE, STORAGE, AUTH, EXTERNAL_API), stack trace, and affected document. You can resolve errors with notes." },
        ],
    },

    // ── Admin: Settings ───────────────────────────────
    {
        slug: "admin-settings",
        title: "Settings & Configuration",
        category: "admin",
        icon: "Settings",
        summary: "Platform configuration, LLM settings, Google Drive OAuth, and feature flags.",
        content: [
            { type: "paragraph", text: "Settings control platform-wide configuration — LLM model selection, API keys, OAuth credentials, feature flags, and runtime behaviour." },
            { type: "heading", text: "LLM Configuration" },
            { type: "list", items: [
                "**Model** — which Claude model to use for classification (Sonnet recommended for balanced cost/quality).",
                "**Max tokens** — maximum response length for the AI.",
                "**Temperature** — controls creativity vs consistency (0 = deterministic, 1 = creative).",
                "**Confidence threshold** — documents below this score are routed to human review.",
            ]},
            { type: "heading", text: "Google Drive OAuth" },
            { type: "steps", items: [
                "Create a project in Google Cloud Console.",
                "Enable the Google Drive API.",
                "Create OAuth 2.0 credentials (Web application type).",
                "Add the redirect URI from Settings into the Google Console.",
                "Enter the Client ID and Client Secret in Settings.",
            ]},
            { type: "warning", text: "OAuth credentials are sensitive. Only administrators should have access to the Settings page." },
        ],
    },

    // ── Admin: LLM & Ollama Setup ─────────────────────
    {
        slug: "admin-llm",
        title: "LLM Setup & Ollama",
        category: "admin",
        icon: "Brain",
        summary: "Configure AI providers — Anthropic Claude (cloud) or Ollama (local) — and set up Ollama on macOS.",
        content: [
            { type: "paragraph", text: "IG Central uses a Large Language Model (LLM) to classify documents, extract metadata, and detect PII. You can choose between a cloud provider (Anthropic Claude) or a local model via Ollama." },

            { type: "heading", text: "Choosing a Provider" },
            { type: "table", headers: ["Provider", "Cost", "Speed", "Privacy", "Best For"], rows: [
                ["Anthropic (Claude)", "Per-token API cost", "Fast (cloud)", "Data sent to Anthropic", "Production with internet access"],
                ["Ollama (Local)", "Free (runs on your hardware)", "Depends on GPU/RAM", "Data stays on-premises", "Air-gapped, cost-sensitive, or privacy-critical"],
            ]},
            { type: "paragraph", text: "Switch providers in **Settings > AI & Classification > LLM Provider**. All other LLM settings (model, temperature, tokens, thresholds) are in the same section." },

            { type: "heading", text: "Anthropic (Claude) Setup" },
            { type: "steps", items: [
                "Go to **console.anthropic.com** and create an account.",
                "Navigate to **API Keys** and generate a new key.",
                "In IG Central, go to **Settings > AI & Classification**.",
                "Set **LLM Provider** to **Anthropic (Claude)**.",
                "Paste your API key into the **Anthropic API Key** field.",
                "Choose a model — **Claude Sonnet 4** is recommended for balanced cost and quality.",
                "Save and restart the LLM worker.",
            ]},
            { type: "tip", text: "Claude Haiku 4.5 is significantly cheaper and faster than Sonnet. If your correction feedback loop is well-established (many past corrections), Haiku can match Sonnet's accuracy because the MCP tools provide the context the model needs." },

            { type: "heading", text: "Installing Ollama on macOS" },
            { type: "paragraph", text: "Ollama runs **natively on macOS** (not in Docker) to take advantage of Apple Silicon GPU acceleration via Metal. Docker on macOS has no GPU access, so running Ollama inside Docker would be CPU-only and significantly slower." },
            { type: "steps", items: [
                "Open Terminal and install Ollama via Homebrew:\n`brew install ollama`",
                "Pull a model — for a Mac Studio with 96GB RAM, the 72B parameter model gives the best quality:\n`ollama pull qwen2.5:72b`\nFor faster throughput (or machines with 32-64GB RAM):\n`ollama pull qwen2.5:32b`",
                "Start the Ollama server:\n`ollama serve`\nThis runs on **http://localhost:11434** by default.",
                "Verify it's working:\n`curl http://localhost:11434/api/tags`\nYou should see your downloaded models listed.",
                "In IG Central, go to **Settings > AI & Classification**.",
                "Set **LLM Provider** to **Ollama — Local Model**.",
                "Set **Ollama URL** to `http://host.docker.internal:11434` (this lets Docker containers reach Ollama on the host).",
                "Set **Ollama Model** to the model you pulled (e.g. `qwen2.5:72b` or `qwen2.5:32b`).",
                "Set **Ollama Context Window** to `32768` (or higher if your model supports it).",
                "Save and restart the LLM worker:\n`docker compose up --build llm-worker -d`",
            ]},
            { type: "warning", text: "Ollama must be running before the LLM worker starts. If Ollama is stopped, classification will fail. Consider setting Ollama to start on login: `brew services start ollama`" },

            { type: "heading", text: "Recommended Models" },
            { type: "table", headers: ["Model", "Size", "RAM Needed", "Speed", "Quality"], rows: [
                ["qwen2.5:72b", "72B params (Q4)", "~42 GB", "~15 tok/s", "Excellent — closest to Claude Sonnet"],
                ["qwen2.5:32b", "32B params (Q8)", "~34 GB", "~30 tok/s", "Very good — recommended for most setups"],
                ["mistral-small:22b", "22B params (Q4)", "~13 GB", "~45 tok/s", "Good — fast, lower RAM requirement"],
                ["llama3.1:8b", "8B params", "~5 GB", "~80 tok/s", "Basic — only for testing, not production"],
            ]},
            { type: "tip", text: "Start with **qwen2.5:32b** for the best balance of speed and quality. If you have 96GB RAM and want maximum accuracy, use **qwen2.5:72b**. The correction feedback loop (past human corrections fed to the model via MCP tools) significantly improves accuracy regardless of model size." },

            { type: "heading", text: "Keeping Ollama Running" },
            { type: "paragraph", text: "To have Ollama start automatically on macOS boot:" },
            { type: "steps", items: [
                "Run: `brew services start ollama`",
                "This registers Ollama as a background service that starts on login.",
                "To stop: `brew services stop ollama`",
                "To check status: `brew services list | grep ollama`",
            ]},

            { type: "heading", text: "Troubleshooting" },
            { type: "faq", question: "Classification fails with 'connection refused'", answer: "Ollama is not running. Start it with `ollama serve` or `brew services start ollama`. Check it's accessible at http://localhost:11434/api/tags." },
            { type: "faq", question: "Classification is very slow", answer: "Check which model you're using — larger models are slower. Ensure Ollama is running natively (not in Docker) for Metal GPU acceleration. On Apple Silicon, `ollama ps` shows GPU usage." },
            { type: "faq", question: "LLM worker shows 'model not found'", answer: "The model name in Settings must match exactly what `ollama list` shows. Pull it first: `ollama pull qwen2.5:32b`" },
            { type: "faq", question: "Tool calling doesn't work with Ollama", answer: "Not all Ollama models support tool/function calling. Qwen 2.5 and Mistral models have good tool support. Llama models may not." },
            { type: "faq", question: "Can I use both providers at the same time?", answer: "Not currently — the system uses one provider at a time. However, you can switch between them in Settings. Future: pipeline-level provider selection (use Ollama for bulk, Claude for complex documents)." },

            { type: "heading", text: "Cost Comparison" },
            { type: "paragraph", text: "With Anthropic, each document classification costs roughly $0.01-0.05 depending on document size and model. With Ollama, the cost is **zero** (electricity only). For 10,000 documents/month:" },
            { type: "table", headers: ["Provider", "Monthly Cost", "Notes"], rows: [
                ["Claude Sonnet 4", "~$200-500", "Best quality, requires internet"],
                ["Claude Haiku 4.5", "~$20-50", "Good quality, much cheaper"],
                ["Ollama (qwen2.5:72b)", "$0", "Excellent quality, runs locally, needs 42GB RAM"],
                ["Ollama (qwen2.5:32b)", "$0", "Very good quality, runs locally, needs 34GB RAM"],
            ]},
        ],
    },
];

export const helpCategories = [
    { key: "getting-started", label: "Getting Started", icon: "Rocket" },
    { key: "user", label: "Using IG Central", icon: "BookOpen" },
    { key: "records-manager", label: "Records Management", icon: "FileSearch" },
    { key: "admin", label: "Administration", icon: "Settings" },
];
