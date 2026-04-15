package co.uk.wolfnotsheep.hub.app.bootstrap;

import co.uk.wolfnotsheep.hub.models.*;
import co.uk.wolfnotsheep.hub.repositories.GovernancePackRepository;
import co.uk.wolfnotsheep.hub.repositories.PackVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Seeds the Governance Hub with comprehensive UK governance packs.
 * Packs include legislation, cross-references between components
 * (taxonomy → retention schedule, taxonomy → metadata schema,
 *  retention → legislation, policy → legislation).
 * Runs once on first startup when the database is empty.
 */
@Component
@Order(1)
public class HubDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HubDataSeeder.class);

    private final GovernancePackRepository packRepo;
    private final PackVersionRepository versionRepo;

    public HubDataSeeder(GovernancePackRepository packRepo, PackVersionRepository versionRepo) {
        this.packRepo = packRepo;
        this.versionRepo = versionRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (packRepo.count() > 0) {
            log.info("Hub already seeded, skipping.");
            return;
        }

        log.info("Seeding Governance Hub with UK governance packs...");
        seedUkGeneralPack();
        seedUkHealthcarePack();
        log.info("Hub seeding complete.");
    }

    private void seedUkGeneralPack() {
        GovernancePack pack = new GovernancePack();
        pack.setName("UK General Governance Framework");
        pack.setSlug("uk-general-governance-framework");
        pack.setDescription("Comprehensive information governance framework for UK organisations. Includes document taxonomy, " +
                "retention schedules aligned with HMRC and Companies Act requirements, GDPR-compliant sensitivity labels, " +
                "PII detection patterns for UK-specific identifiers, governance policies, and linked legislation.");
        pack.setAuthor(new GovernancePack.PackAuthor("IG Central", "Wolf Not Sheep Ltd", "support@igcentral.com", true));
        pack.setJurisdiction("UK");
        pack.setIndustries(List.of("general", "financial-services", "legal", "professional-services"));
        pack.setRegulations(List.of("GDPR", "DPA 2018", "Companies Act 2006", "HMRC", "FCA SYSC 9", "Limitation Act 1980"));
        pack.setTags(List.of("taxonomy", "retention", "sensitivity", "pii", "policies", "metadata", "legislation"));
        pack.setStatus(GovernancePack.PackStatus.PUBLISHED);
        pack.setFeatured(true);
        pack.setLatestVersionNumber(1);
        pack.setCreatedAt(Instant.now());
        pack.setUpdatedAt(Instant.now());
        pack.setPublishedAt(Instant.now());
        GovernancePack saved = packRepo.save(pack);

        PackVersion v1 = new PackVersion();
        v1.setPackId(saved.getId());
        v1.setVersionNumber(1);
        v1.setCompatibilityVersion("2.0");
        v1.setChangelog("Initial release — UK general governance framework with joined-up legislation, taxonomy, retention, sensitivity, PII patterns, policies, and metadata schemas.");
        v1.setPublishedBy("IG Central");
        v1.setPublishedAt(Instant.now());
        v1.setComponents(List.of(
                buildLegislation(),
                buildTaxonomy(),
                buildRetentionSchedules(),
                buildSensitivityDefinitions(),
                buildMetadataSchemas(),
                buildPiiPatterns(),
                buildGovernancePolicies(),
                buildStorageTiers(),
                buildTraitDefinitions()
        ));
        versionRepo.save(v1);
        log.info("Seeded UK General Governance Framework v1");
    }

    private void seedUkHealthcarePack() {
        GovernancePack pack = new GovernancePack();
        pack.setName("UK Healthcare Records Management");
        pack.setSlug("uk-healthcare-records-management");
        pack.setDescription("Governance framework for UK healthcare organisations. Covers NHS records retention (based on NHS " +
                "Records Management Code of Practice), Caldicott principles, medical PII patterns, healthcare-specific taxonomy, and linked legislation.");
        pack.setAuthor(new GovernancePack.PackAuthor("IG Central", "Wolf Not Sheep Ltd", "support@igcentral.com", true));
        pack.setJurisdiction("UK");
        pack.setIndustries(List.of("healthcare", "nhs", "social-care"));
        pack.setRegulations(List.of("GDPR", "DPA 2018", "NHS Records Management Code", "Caldicott Principles", "Health and Social Care Act 2012"));
        pack.setTags(List.of("nhs", "healthcare", "medical-records", "caldicott", "retention", "legislation"));
        pack.setStatus(GovernancePack.PackStatus.PUBLISHED);
        pack.setFeatured(true);
        pack.setLatestVersionNumber(1);
        pack.setCreatedAt(Instant.now());
        pack.setUpdatedAt(Instant.now());
        pack.setPublishedAt(Instant.now());
        GovernancePack saved = packRepo.save(pack);

        PackVersion v1 = new PackVersion();
        v1.setPackId(saved.getId());
        v1.setVersionNumber(1);
        v1.setCompatibilityVersion("2.0");
        v1.setChangelog("Initial release — UK healthcare records management framework with linked legislation.");
        v1.setPublishedBy("IG Central");
        v1.setPublishedAt(Instant.now());
        v1.setComponents(List.of(
                buildHealthcareLegislation(),
                buildHealthcareTaxonomy(),
                buildHealthcareRetention(),
                buildHealthcarePiiPatterns()
        ));
        versionRepo.save(v1);
        log.info("Seeded UK Healthcare Records Management v1");
    }

    // ── Legislation ──────────────────────────────────

    private PackComponent buildLegislation() {
        List<Map<String, Object>> data = List.of(
                leg("GDPR", "General Data Protection Regulation", "GDPR", "UK",
                        "https://www.legislation.gov.uk/ukpga/2018/12/contents",
                        "UK implementation of GDPR via the Data Protection Act 2018. Governs processing of personal data.",
                        List.of("Article 5 — Principles relating to processing of personal data",
                                "Article 6 — Lawfulness of processing",
                                "Article 9 — Processing of special categories of personal data",
                                "Article 15 — Right of access by the data subject",
                                "Article 17 — Right to erasure",
                                "Article 30 — Records of processing activities")),
                leg("DPA_2018", "Data Protection Act 2018", "DPA 2018", "UK",
                        "https://www.legislation.gov.uk/ukpga/2018/12/contents",
                        "UK domestic data protection legislation implementing GDPR, including law enforcement and intelligence services processing.",
                        List.of("Part 2 — General processing (UK GDPR)",
                                "Part 3 — Law enforcement processing",
                                "Section 170 — Unlawful obtaining of personal data",
                                "Section 171 — Re-identification of de-identified personal data")),
                leg("COMPANIES_ACT_2006", "Companies Act 2006", "Companies Act", "UK",
                        "https://www.legislation.gov.uk/ukpga/2006/46/contents",
                        "Primary legislation governing UK companies, including record-keeping requirements.",
                        List.of("Section 386 — Duty to keep accounting records",
                                "Section 388 — Accounting records to be kept for 3/6 years",
                                "Section 355 — Duty to keep minutes of meetings",
                                "Section 113 — Register of members")),
                leg("LIMITATION_ACT_1980", "Limitation Act 1980", "Limitation Act", "UK",
                        "https://www.legislation.gov.uk/ukpga/1980/58/contents",
                        "Sets time limits for bringing legal claims. Drives minimum retention periods for many document types.",
                        List.of("Section 2 — 6 years for tort",
                                "Section 5 — 6 years for contract",
                                "Section 11 — 3 years for personal injury",
                                "Section 15 — 12 years for recovery of land")),
                leg("HMRC_RECORD_KEEPING", "HMRC Record Keeping Requirements", "HMRC", "UK",
                        "https://www.gov.uk/running-a-limited-company/company-and-accounting-records",
                        "HMRC requirements for keeping financial and tax records.",
                        List.of("6 years from end of last accounting period",
                                "VAT records for 6 years",
                                "Payroll records for 3 years after end of tax year")),
                leg("FCA_SYSC_9", "FCA Handbook SYSC 9 — Record Keeping", "FCA SYSC 9", "UK",
                        "https://www.handbook.fca.org.uk/handbook/SYSC/9.html",
                        "FCA record-keeping obligations for regulated financial services firms.",
                        List.of("SYSC 9.1.1 — Firms must arrange for orderly records to be kept",
                                "SYSC 9.1.2 — Records to be retained for 5 years (3 for non-MiFID)",
                                "SYSC 9.1.5 — Telephone recording requirements"))
        );
        return component(PackComponent.ComponentType.LEGISLATION, "UK Legislation & Regulations",
                "6 key pieces of UK legislation that drive governance decisions", data);
    }

    private PackComponent buildHealthcareLegislation() {
        List<Map<String, Object>> data = List.of(
                leg("GDPR", "General Data Protection Regulation", "GDPR", "UK",
                        "https://www.legislation.gov.uk/ukpga/2018/12/contents",
                        "UK implementation of GDPR — governs processing of patient personal data.",
                        List.of("Article 9 — Special categories (health data)",
                                "Article 17 — Right to erasure (limited for health records)",
                                "Article 89 — Safeguards for research processing")),
                leg("DPA_2018", "Data Protection Act 2018", "DPA 2018", "UK",
                        "https://www.legislation.gov.uk/ukpga/2018/12/contents",
                        "UK data protection law with healthcare-specific provisions.",
                        List.of("Schedule 1, Part 1 — Conditions for processing health data",
                                "Section 19 — Processing for health or social care purposes")),
                leg("NHS_RECORDS_MANAGEMENT_CODE", "NHS Records Management Code of Practice", "NHS RM Code", "UK",
                        "https://transform.england.nhs.uk/information-governance/guidance/records-management-code/",
                        "NHS code of practice for the management of records. Sets minimum retention periods for all NHS record types.",
                        List.of("Section 12 — Retention schedules for health records",
                                "Appendix III — Minimum retention periods by record type",
                                "Section 7 — Destruction and disposal of records")),
                leg("CALDICOTT_PRINCIPLES", "Caldicott Principles", "Caldicott", "UK",
                        "https://www.gov.uk/government/publications/the-caldicott-principles",
                        "8 principles governing the use and sharing of patient-identifiable information in the NHS.",
                        List.of("Principle 1 — Justify the purpose",
                                "Principle 2 — Use only when absolutely necessary",
                                "Principle 3 — Use the minimum necessary",
                                "Principle 7 — The duty to share information is as important as the duty to protect confidentiality")),
                leg("HEALTH_SOCIAL_CARE_ACT_2012", "Health and Social Care Act 2012", "HSCA 2012", "UK",
                        "https://www.legislation.gov.uk/ukpga/2012/7/contents",
                        "Legislation governing the structure and duties of NHS organisations, including information governance duties.",
                        List.of("Section 251B — Duty to share information for health purposes",
                                "Part 9 — Health and adult social care services: information"))
        );
        return component(PackComponent.ComponentType.LEGISLATION, "UK Healthcare Legislation",
                "5 key pieces of healthcare legislation driving NHS governance", data);
    }

    // ── Taxonomy (with cross-references) ─────────────

    private PackComponent buildTaxonomy() {
        List<Map<String, Object>> data = List.of(
                cat("Legal", null, "Legal documents, contracts, agreements, and litigation records", "CONFIDENTIAL",
                        List.of("contract", "legal", "agreement", "litigation"), null, null),
                cat("Contracts", "Legal", "Legally binding agreements between parties", "CONFIDENTIAL",
                        List.of("contract", "agreement", "terms"), "Standard Business", "Contract / Agreement"),
                cat("Litigation", "Legal", "Court proceedings, disputes, and legal claims", "RESTRICTED",
                        List.of("court", "claim", "dispute", "litigation"), "Regulatory Extended", null),
                cat("Finance", null, "Financial records, invoices, budgets, and tax documents", "CONFIDENTIAL",
                        List.of("finance", "invoice", "budget", "tax"), null, null),
                cat("Invoices & Receipts", "Finance", "Purchase and sales invoices, receipts", "INTERNAL",
                        List.of("invoice", "receipt", "purchase order"), "Financial Statutory", "Invoice"),
                cat("Payroll", "Finance", "Salary, pension, and employee compensation records", "RESTRICTED",
                        List.of("payroll", "salary", "pension", "P45", "P60"), "Financial Statutory", null),
                cat("Tax Returns", "Finance", "Corporation tax, VAT returns, HMRC submissions", "CONFIDENTIAL",
                        List.of("tax", "VAT", "HMRC", "corporation tax"), "Financial Statutory", null),
                cat("HR", null, "Human resources records — employee files, recruitment, training", "CONFIDENTIAL",
                        List.of("hr", "employee", "staff", "personnel"), null, null),
                cat("Employee Records", "HR", "Personal files, contracts of employment, performance reviews", "RESTRICTED",
                        List.of("employee file", "performance", "disciplinary"), "Regulatory Extended", null),
                cat("Recruitment", "HR", "Job applications, CVs, interview notes", "CONFIDENTIAL",
                        List.of("recruitment", "CV", "application", "interview"), "Short-Term Operational", null),
                cat("Operations", null, "Operational documents — policies, procedures, project records", "INTERNAL",
                        List.of("operations", "procedure", "process"), "Standard Business", null),
                cat("Compliance", null, "Regulatory compliance, audit reports, risk assessments", "CONFIDENTIAL",
                        List.of("compliance", "audit", "risk", "regulatory"), null, null),
                cat("Audit Reports", "Compliance", "Internal and external audit reports", "CONFIDENTIAL",
                        List.of("audit", "report", "finding"), "Regulatory Extended", null),
                cat("IT", null, "Information technology — security, infrastructure, systems", "INTERNAL",
                        List.of("IT", "technology", "system", "infrastructure"), null, null),
                cat("Security Incidents", "IT", "Cyber security incidents, breach reports", "RESTRICTED",
                        List.of("security", "incident", "breach", "vulnerability"), "Standard Business", null),
                cat("Marketing", null, "Marketing materials, campaigns, brand assets", "PUBLIC",
                        List.of("marketing", "campaign", "brand", "press"), "Short-Term Operational", null),
                cat("Sales", null, "Sales proposals, quotes, client communications", "INTERNAL",
                        List.of("sales", "proposal", "quote", "client"), "Standard Business", null)
        );
        return component(PackComponent.ComponentType.TAXONOMY_CATEGORIES, "UK Business Taxonomy",
                "Standard UK business document taxonomy with 17 categories across 7 top-level domains", data);
    }

    private PackComponent buildRetentionSchedules() {
        List<Map<String, Object>> data = List.of(
                retention("Short-Term Operational", "Operational documents with no statutory retention requirement",
                        365, "REVIEW", true,
                        "Business operational needs — no statutory requirement",
                        List.of()),
                retention("Standard Business", "General business records — 3 year retention",
                        1095, "ARCHIVE", true,
                        "Limitation Act 1980 — 3 years for personal injury claims",
                        List.of("LIMITATION_ACT_1980")),
                retention("Financial Statutory", "Financial records — 6 year minimum per HMRC requirements",
                        2190, "DELETE", true,
                        "HMRC: 6 years from end of accounting period. Companies Act 2006 s.388.",
                        List.of("HMRC_RECORD_KEEPING", "COMPANIES_ACT_2006")),
                retention("Regulatory Extended", "Records with extended regulatory retention — 7-10 years",
                        3650, "REVIEW", true,
                        "FCA SYSC 9: 5-10 years for regulated financial services. Limitation Act: 6 years for contract claims.",
                        List.of("FCA_SYSC_9", "LIMITATION_ACT_1980")),
                retention("Permanent", "Records that must be retained permanently",
                        -1, "ARCHIVE", false,
                        "Companies Act 2006: company registers, resolutions. Health and Safety: accident records for 40 years.",
                        List.of("COMPANIES_ACT_2006"))
        );
        return component(PackComponent.ComponentType.RETENTION_SCHEDULES, "UK Statutory Retention Schedules",
                "5 retention schedules aligned with UK legislation (HMRC, Companies Act, Limitation Act, FCA)", data);
    }

    private PackComponent buildSensitivityDefinitions() {
        List<Map<String, Object>> data = List.of(
                sensitivity("PUBLIC", "Public", "Information approved for public release", 0, "green",
                        List.of("Published reports, marketing materials, press releases", "No access restrictions required"),
                        List.of("Annual reports", "Press releases", "Marketing brochures", "Public policies"),
                        List.of()),
                sensitivity("INTERNAL", "Internal", "For internal use within the organisation", 1, "blue",
                        List.of("General business documents not intended for external sharing", "Available to all employees"),
                        List.of("Meeting minutes", "Internal memos", "Process documents", "Training materials"),
                        List.of()),
                sensitivity("CONFIDENTIAL", "Confidential", "Sensitive business information requiring controlled access", 2, "amber",
                        List.of("Contains commercially sensitive or personal data", "Need-to-know basis within the organisation", "Must not be shared externally without authorisation"),
                        List.of("Financial reports", "HR records", "Client contracts", "Strategic plans"),
                        List.of("GDPR", "DPA_2018")),
                sensitivity("RESTRICTED", "Restricted", "Highly sensitive — regulated, legally protected, or critical to the organisation", 3, "red",
                        List.of("Contains special category personal data (GDPR Art.9)", "Subject to legal privilege or regulatory protection", "Breach could cause significant harm to individuals or the organisation"),
                        List.of("Medical records", "Legal privileged documents", "Board resolutions", "Security incident reports"),
                        List.of("GDPR", "DPA_2018"))
        );
        return component(PackComponent.ComponentType.SENSITIVITY_DEFINITIONS, "UK Sensitivity Labels",
                "4-level sensitivity framework aligned with UK Government Security Classifications", data);
    }

    private PackComponent buildMetadataSchemas() {
        List<Map<String, Object>> data = List.of(
                Map.of("name", "Contract / Agreement",
                        "description", "Structured metadata for contracts and agreements",
                        "extractionContext", "Extract key contract details including parties, dates, and value",
                        "fields", List.of(
                                field("parties", "TEXT", true, "Names of the contracting parties", "e.g. Acme Ltd and Beta Corp"),
                                field("effective_date", "DATE", true, "Date the contract takes effect", "ISO format: 2026-01-15"),
                                field("expiry_date", "DATE", false, "Date the contract expires", "ISO format: 2028-01-15"),
                                field("contract_value", "CURRENCY", false, "Total value of the contract", "e.g. £50,000"),
                                field("governing_law", "KEYWORD", false, "Jurisdiction governing the contract", "e.g. England and Wales")
                        )),
                Map.of("name", "Invoice",
                        "description", "Structured metadata for invoices and receipts",
                        "extractionContext", "Extract invoice header details including parties, amounts, and dates",
                        "fields", List.of(
                                field("from_company", "TEXT", true, "Company issuing the invoice", "e.g. Supplier Ltd"),
                                field("to_company", "TEXT", true, "Company receiving the invoice", "e.g. Client Corp"),
                                field("invoice_number", "KEYWORD", true, "Invoice reference number", "e.g. INV-2026-0042"),
                                field("invoice_date", "DATE", true, "Date the invoice was issued", "ISO format"),
                                field("total_amount", "CURRENCY", true, "Total amount including VAT", "e.g. £1,200.00"),
                                field("net_amount", "CURRENCY", false, "Net amount before VAT", "e.g. £1,000.00"),
                                field("vat_amount", "CURRENCY", false, "VAT amount", "e.g. £200.00"),
                                field("due_date", "DATE", false, "Payment due date", "ISO format"),
                                field("description", "TEXT", false, "Description of goods or services", "")
                        )),
                Map.of("name", "HR Leave Request",
                        "description", "Structured metadata for employee leave requests",
                        "extractionContext", "Extract leave request details including employee, dates, and type of leave",
                        "fields", List.of(
                                field("employee_name", "TEXT", true, "Full name of the employee", "e.g. John Smith"),
                                field("leave_type", "KEYWORD", true, "Type of leave requested", "e.g. maternity, annual, sick"),
                                field("start_date", "DATE", true, "First day of leave", "ISO format"),
                                field("end_date", "DATE", true, "Last day of leave", "ISO format"),
                                field("approver", "TEXT", false, "Name of approving manager", "e.g. Jane Doe"),
                                field("department", "TEXT", false, "Employee's department", "e.g. Engineering")
                        ))
        );
        return component(PackComponent.ComponentType.METADATA_SCHEMAS, "UK Business Metadata Schemas",
                "3 metadata extraction schemas for contracts, invoices, and HR leave requests", data);
    }

    private PackComponent buildPiiPatterns() {
        List<Map<String, Object>> data = List.of(
                Map.of("name", "UK National Insurance", "type", "NATIONAL_INSURANCE", "regex", "\\b[A-CEGHJ-PR-TW-Z]{2}\\s?\\d{2}\\s?\\d{2}\\s?\\d{2}\\s?[A-D]\\b", "confidence", 0.95, "flags", "CASE_INSENSITIVE"),
                Map.of("name", "Email Address", "type", "EMAIL", "regex", "\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b", "confidence", 0.95),
                Map.of("name", "UK Phone Number", "type", "PHONE_UK", "regex", "\\b(?:(?:\\+44\\s?|0)(?:\\d\\s?){9,10})\\b", "confidence", 0.85),
                Map.of("name", "UK Postcode", "type", "POSTCODE_UK", "regex", "\\b[A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2}\\b", "confidence", 0.8, "flags", "CASE_INSENSITIVE"),
                Map.of("name", "UK Sort Code", "type", "SORT_CODE", "regex", "\\b\\d{2}[\\-\\s]\\d{2}[\\-\\s]\\d{2}\\b", "confidence", 0.85),
                Map.of("name", "Date of Birth", "type", "DATE_OF_BIRTH", "regex", "(?i)(?:d\\.?o\\.?b\\.?|date\\s+of\\s+birth|born)[:\\s]+\\d{1,2}[/\\-\\.]\\d{1,2}[/\\-\\.]\\d{2,4}", "confidence", 0.9),
                Map.of("name", "Credit/Debit Card", "type", "CREDIT_CARD", "regex", "\\b(?:\\d{4}[\\s\\-]?){3,4}\\d{1,4}\\b", "confidence", 0.7),
                Map.of("name", "UK NHS Number", "type", "NHS_NUMBER", "regex", "\\b\\d{3}\\s?\\d{3}\\s?\\d{4}\\b", "confidence", 0.7),
                Map.of("name", "North American Phone", "type", "PHONE_NA", "regex", "\\b(?:\\+?1[\\-\\s]?)?\\(?\\d{3}\\)?[\\-\\s.]?\\d{3}[\\-\\s.]?\\d{4}\\b", "confidence", 0.8),
                Map.of("name", "Canadian Postal Code", "type", "POSTAL_CODE_CA", "regex", "\\b[A-Za-z]\\d[A-Za-z]\\s?\\d[A-Za-z]\\d\\b", "confidence", 0.85),
                Map.of("name", "Person Name (Title)", "type", "PERSON_NAME", "regex", "(?:Mr\\.?|Mrs\\.?|Ms\\.?|Miss|Dr\\.?)\\s+[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+", "confidence", 0.6),
                Map.of("name", "Street Address", "type", "ADDRESS", "regex", "\\b\\d{1,5}\\s+[A-Z][a-zA-Z]+(?:\\s+[A-Z]?[a-zA-Z]+)*\\s+(?:Road|Rd|Street|St|Avenue|Ave|Drive|Dr|Lane|Ln|Court|Ct|Place|Pl|Way|Boulevard|Blvd|Crescent|Cres|Meadow|Circle|Cir)\\b", "confidence", 0.6, "flags", "CASE_INSENSITIVE")
        );
        return component(PackComponent.ComponentType.PII_TYPE_DEFINITIONS, "UK & International PII Patterns",
                "12 PII detection patterns covering UK, North American, and international formats", data);
    }

    private PackComponent buildGovernancePolicies() {
        List<Map<String, Object>> data = List.of(
                policy("Data Protection Policy",
                        "Organisation-wide data protection policy aligned with UK GDPR and DPA 2018",
                        List.of("CONFIDENTIAL", "RESTRICTED"),
                        List.of("Personal data must be processed lawfully, fairly and transparently",
                                "Data must be collected for specified, explicit and legitimate purposes",
                                "Data must be adequate, relevant and limited to what is necessary",
                                "Subject access requests must be responded to within 30 days"),
                        List.of("GDPR", "DPA_2018")),
                policy("Records Retention Policy",
                        "Policy governing the retention and disposal of business records",
                        List.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"),
                        List.of("Records must be retained for the minimum period required by law",
                                "Disposal must be documented in the audit trail",
                                "Legal hold overrides all retention schedules",
                                "Review before disposal for records over 6 years old"),
                        List.of("COMPANIES_ACT_2006", "HMRC_RECORD_KEEPING", "LIMITATION_ACT_1980")),
                policy("Information Security Policy",
                        "Controls for protecting information assets based on their sensitivity classification",
                        List.of("CONFIDENTIAL", "RESTRICTED"),
                        List.of("RESTRICTED documents must be encrypted at rest and in transit",
                                "Access to CONFIDENTIAL documents requires manager approval",
                                "Security incidents must be reported within 24 hours",
                                "Annual access reviews for all RESTRICTED document categories"),
                        List.of("GDPR", "DPA_2018"))
        );
        return component(PackComponent.ComponentType.GOVERNANCE_POLICIES, "UK Governance Policies",
                "3 core governance policies: data protection, retention, and information security", data);
    }

    private PackComponent buildStorageTiers() {
        List<Map<String, Object>> data = List.of(
                Map.of("name", "Public Store", "description", "Standard storage for public documents", "encryptionType", "AES-256", "immutable", false, "geographicallyRestricted", false),
                Map.of("name", "Internal Store", "description", "Standard encrypted storage for internal documents", "encryptionType", "AES-256", "immutable", false, "geographicallyRestricted", false),
                Map.of("name", "Confidential Store", "description", "Encrypted storage with access logging", "encryptionType", "AES-256", "immutable", true, "geographicallyRestricted", true, "region", "UK"),
                Map.of("name", "Restricted Vault", "description", "Maximum security storage for restricted documents", "encryptionType", "AES-256-GCM", "immutable", true, "geographicallyRestricted", true, "region", "UK")
        );
        return component(PackComponent.ComponentType.STORAGE_TIERS, "UK Storage Tiers",
                "4 storage tiers mapped to sensitivity levels with UK data residency", data);
    }

    private PackComponent buildTraitDefinitions() {
        List<Map<String, Object>> data = List.of(
                Map.of("key", "TEMPLATE", "displayName", "Template", "description", "A blank template with placeholder fields", "dimension", "COMPLETENESS", "suppressPii", true,
                        "detectionHint", "Look for placeholder patterns like {Name}, [Insert Date], <<field>>", "indicators", List.of("{", "[Insert", "<<", "complete this")),
                Map.of("key", "DRAFT", "displayName", "Draft", "description", "A work-in-progress document", "dimension", "COMPLETENESS", "suppressPii", false,
                        "detectionHint", "Look for 'DRAFT', 'WIP', version numbers, tracked changes", "indicators", List.of("DRAFT", "WIP", "v0.", "track changes")),
                Map.of("key", "FINAL", "displayName", "Final", "description", "An approved, finalised document", "dimension", "COMPLETENESS", "suppressPii", false),
                Map.of("key", "SIGNED", "displayName", "Signed", "description", "A document with signatures", "dimension", "COMPLETENESS", "suppressPii", false),
                Map.of("key", "INBOUND", "displayName", "Inbound", "description", "Received from an external party", "dimension", "DIRECTION", "suppressPii", false),
                Map.of("key", "OUTBOUND", "displayName", "Outbound", "description", "Sent to an external party", "dimension", "DIRECTION", "suppressPii", false),
                Map.of("key", "INTERNAL", "displayName", "Internal", "description", "Internal communication", "dimension", "DIRECTION", "suppressPii", false),
                Map.of("key", "ORIGINAL", "displayName", "Original", "description", "The original document", "dimension", "PROVENANCE", "suppressPii", false),
                Map.of("key", "COPY", "displayName", "Copy", "description", "A copy of another document", "dimension", "PROVENANCE", "suppressPii", false),
                Map.of("key", "SCAN", "displayName", "Scan", "description", "A scanned physical document", "dimension", "PROVENANCE", "suppressPii", false)
        );
        return component(PackComponent.ComponentType.TRAIT_DEFINITIONS, "Document Traits",
                "10 document traits across 3 dimensions: completeness, direction, provenance", data);
    }

    // ── Healthcare pack components ────────────────────

    private PackComponent buildHealthcareTaxonomy() {
        List<Map<String, Object>> data = List.of(
                cat("Patient Records", null, "Individual patient health records", "RESTRICTED",
                        List.of("patient", "medical", "clinical", "health record"), "Adult Patient Records", null),
                cat("Clinical Notes", "Patient Records", "Clinician notes, consultations, assessments", "RESTRICTED",
                        List.of("clinical", "consultation", "assessment"), "Adult Patient Records", null),
                cat("Prescriptions", "Patient Records", "Medication prescriptions and dispensing records", "RESTRICTED",
                        List.of("prescription", "medication", "dispensing"), "Adult Patient Records", null),
                cat("Test Results", "Patient Records", "Laboratory, imaging, and diagnostic test results", "RESTRICTED",
                        List.of("test", "laboratory", "imaging", "pathology"), "Adult Patient Records", null),
                cat("Referrals", "Patient Records", "Referral letters between healthcare providers", "CONFIDENTIAL",
                        List.of("referral", "transfer", "handover"), "Adult Patient Records", null),
                cat("Administrative", null, "Healthcare administrative records", "INTERNAL",
                        List.of("admin", "appointment", "registration"), null, null),
                cat("Appointments", "Administrative", "Patient appointment records", "CONFIDENTIAL",
                        List.of("appointment", "booking", "schedule"), null, null),
                cat("Consent Forms", "Administrative", "Patient consent and capacity documentation", "RESTRICTED",
                        List.of("consent", "capacity", "MCA"), "Adult Patient Records", null),
                cat("Safeguarding", null, "Safeguarding and child protection records", "RESTRICTED",
                        List.of("safeguarding", "child protection", "vulnerable adult"), "Children's Records", null),
                cat("Research", null, "Clinical research and trial documentation", "CONFIDENTIAL",
                        List.of("research", "clinical trial", "study"), "Regulatory Extended", null)
        );
        return component(PackComponent.ComponentType.TAXONOMY_CATEGORIES, "UK Healthcare Taxonomy",
                "10 healthcare-specific categories covering patient records, admin, safeguarding, and research", data);
    }

    private PackComponent buildHealthcareRetention() {
        List<Map<String, Object>> data = List.of(
                retention("Adult Patient Records", "GP and hospital records for adult patients",
                        2920, "REVIEW", true,
                        "NHS Records Management Code: 8 years after last attendance or 3 years after death",
                        List.of("NHS_RECORDS_MANAGEMENT_CODE", "GDPR")),
                retention("Children's Records", "Records relating to children and young people",
                        9125, "REVIEW", true,
                        "NHS Records Management Code: retain until patient's 25th birthday or 26th if 17 at conclusion of treatment",
                        List.of("NHS_RECORDS_MANAGEMENT_CODE", "GDPR")),
                retention("Mental Health Records", "Mental health treatment records",
                        7300, "REVIEW", true,
                        "NHS Records Management Code: 20 years after last contact or 8 years after death",
                        List.of("NHS_RECORDS_MANAGEMENT_CODE", "GDPR")),
                retention("Maternity Records", "Maternity and obstetric records",
                        9125, "REVIEW", true,
                        "NHS Records Management Code: 25 years after birth of last child",
                        List.of("NHS_RECORDS_MANAGEMENT_CODE", "GDPR"))
        );
        return component(PackComponent.ComponentType.RETENTION_SCHEDULES, "NHS Retention Schedules",
                "4 healthcare retention schedules based on NHS Records Management Code of Practice", data);
    }

    private PackComponent buildHealthcarePiiPatterns() {
        List<Map<String, Object>> data = List.of(
                Map.of("name", "NHS Number", "type", "NHS_NUMBER", "regex", "\\b\\d{3}\\s?\\d{3}\\s?\\d{4}\\b", "confidence", 0.8),
                Map.of("name", "Hospital Number", "type", "HOSPITAL_NUMBER", "regex", "(?i)(?:hospital|patient|MRN)[\\s#:]*[A-Z]?\\d{6,8}\\b", "confidence", 0.7),
                Map.of("name", "GMC Number", "type", "GMC_NUMBER", "regex", "(?i)(?:GMC|General Medical Council)[\\s#:]*\\d{7}\\b", "confidence", 0.9),
                Map.of("name", "NMC PIN", "type", "NMC_PIN", "regex", "(?i)(?:NMC|Nursing)[\\s#:]*\\d{2}[A-Z]\\d{4}[A-Z]\\b", "confidence", 0.9),
                Map.of("name", "Medical Condition", "type", "MEDICAL_CONDITION", "regex", "(?i)(?:diagnosed? with|suffering from|treatment for)\\s+[A-Za-z\\s]{3,30}", "confidence", 0.6)
        );
        return component(PackComponent.ComponentType.PII_TYPE_DEFINITIONS, "Healthcare PII Patterns",
                "5 healthcare-specific PII patterns: NHS number, hospital number, GMC, NMC, medical conditions", data);
    }

    // ── Helpers ───────────────────────────────────────

    private static Map<String, Object> cat(String name, String parentName, String description,
                                           String defaultSensitivity, List<String> keywords,
                                           String retentionScheduleRef, String metadataSchemaRef) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", name);
        if (parentName != null) m.put("parentName", parentName);
        m.put("description", description);
        m.put("defaultSensitivity", defaultSensitivity);
        m.put("keywords", keywords);
        if (retentionScheduleRef != null) m.put("retentionScheduleRef", retentionScheduleRef);
        if (metadataSchemaRef != null) m.put("metadataSchemaRef", metadataSchemaRef);
        return m;
    }

    private static Map<String, Object> leg(String key, String name, String shortName, String jurisdiction,
                                           String url, String description, List<String> relevantArticles) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("key", key);
        m.put("name", name);
        m.put("shortName", shortName);
        m.put("jurisdiction", jurisdiction);
        m.put("url", url);
        m.put("description", description);
        m.put("relevantArticles", relevantArticles);
        return m;
    }

    private static Map<String, Object> retention(String name, String description, int retentionDays,
                                                  String dispositionAction, boolean legalHoldOverride,
                                                  String regulatoryBasis, List<String> legislationRefs) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", name);
        m.put("description", description);
        m.put("retentionDays", retentionDays);
        m.put("dispositionAction", dispositionAction);
        m.put("legalHoldOverride", legalHoldOverride);
        m.put("regulatoryBasis", regulatoryBasis);
        if (!legislationRefs.isEmpty()) m.put("legislationRefs", legislationRefs);
        return m;
    }

    private static Map<String, Object> sensitivity(String key, String displayName, String description,
                                                    int level, String colour, List<String> guidelines,
                                                    List<String> examples, List<String> legislationRefs) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("key", key);
        m.put("displayName", displayName);
        m.put("description", description);
        m.put("level", level);
        m.put("colour", colour);
        m.put("guidelines", guidelines);
        m.put("examples", examples);
        if (!legislationRefs.isEmpty()) m.put("legislationRefs", legislationRefs);
        return m;
    }

    private static Map<String, Object> policy(String name, String description,
                                               List<String> applicableSensitivities, List<String> rules,
                                               List<String> legislationRefs) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", name);
        m.put("description", description);
        m.put("version", 1);
        m.put("active", true);
        m.put("applicableSensitivities", applicableSensitivities);
        m.put("rules", rules);
        if (!legislationRefs.isEmpty()) m.put("legislationRefs", legislationRefs);
        return m;
    }

    private static Map<String, Object> field(String fieldName, String dataType, boolean required,
                                              String description, String example) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("fieldName", fieldName);
        m.put("dataType", dataType);
        m.put("required", required);
        m.put("description", description);
        if (!example.isEmpty()) m.put("examples", List.of(example));
        return m;
    }

    private static PackComponent component(PackComponent.ComponentType type, String name,
                                           String description, List<Map<String, Object>> data) {
        PackComponent c = new PackComponent();
        c.setType(type);
        c.setName(name);
        c.setDescription(description);
        c.setItemCount(data.size());
        c.setData(data);
        return c;
    }
}
