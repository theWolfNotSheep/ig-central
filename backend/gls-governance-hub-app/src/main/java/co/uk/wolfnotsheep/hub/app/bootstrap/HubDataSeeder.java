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
 * Seeds the Governance Hub with comprehensive governance packs.
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

        log.info("Seeding Governance Hub with governance packs...");
        seedUkGeneralPack();
        seedUkHealthcarePack();
        seedUsGenericPack();
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

    private void seedUsGenericPack() {
        GovernancePack pack = new GovernancePack();
        pack.setName("US Generic Records Taxonomy (ISO 15489)");
        pack.setSlug("us-generic-records-taxonomy-iso15489");
        pack.setDescription("ISO 15489-aligned records taxonomy for US organisations. 8 business functions, 34 record classes with US retention periods and legal citations.");
        pack.setAuthor(new GovernancePack.PackAuthor("IG Central", "Wolf Not Sheep Ltd", "support@igcentral.com", true));
        pack.setJurisdiction("US");
        pack.setIndustries(List.of("general", "corporate", "financial-services", "professional-services"));
        pack.setRegulations(List.of("Sarbanes-Oxley Act", "IRS IRC §6001", "EEOC 29 CFR §1602", "FLSA", "NIST"));
        pack.setTags(List.of("taxonomy", "retention", "iso-15489", "us", "generic"));
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
        v1.setChangelog("Initial release — US generic records taxonomy with ISO 15489 structure, US retention periods, and legal citations.");
        v1.setPublishedBy("IG Central");
        v1.setPublishedAt(Instant.now());
        v1.setComponents(List.of(
                buildUsLegislation(),
                buildUsTaxonomy(),
                buildUsRetention()
        ));
        versionRepo.save(v1);
        log.info("Seeded US Generic Records Taxonomy (ISO 15489) v1");
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

    private PackComponent buildUsLegislation() {
        List<Map<String, Object>> data = List.of(
                leg("SOX", "Sarbanes-Oxley Act", "SOX", "US",
                        "https://www.congress.gov/bill/107th-congress/house-bill/3763",
                        "Federal law mandating corporate governance, financial disclosure, and record retention for public companies.",
                        List.of("Section 302 — Corporate responsibility for financial reports",
                                "Section 404 — Management assessment of internal controls",
                                "Section 802 (18 USC §1519) — Destruction, alteration, or falsification of records",
                                "Section 103 — Audit documentation retention (7 years)")),
                leg("IRS_IRC_6001", "IRS IRC §6001", "IRS §6001", "US",
                        "https://www.law.cornell.edu/uscode/text/26/6001",
                        "IRS requirement for taxpayers to keep records sufficient to establish income, deductions, and credits.",
                        List.of("§6001 — General requirement to keep records",
                                "§6501 — Statute of limitations on assessment (3-6 years)",
                                "Revenue Procedure — 7 years recommended for supporting documents")),
                leg("EEOC_29CFR1602", "EEOC 29 CFR §1602", "EEOC §1602", "US",
                        "https://www.law.cornell.edu/cfr/text/29/part-1602",
                        "EEOC regulations requiring employers to retain employment records for specified periods.",
                        List.of("§1602.14 — Preservation of records for 1 year from making or personnel action",
                                "§1602.31 — Records to be kept by apprenticeship programs",
                                "Title VII, ADA, ADEA — Underlying statutes")),
                leg("FLSA", "Fair Labor Standards Act", "FLSA", "US",
                        "https://www.dol.gov/agencies/whd/flsa",
                        "Federal law establishing minimum wage, overtime pay, and record-keeping requirements for employers.",
                        List.of("29 CFR §516 — Payroll records to be kept for 3 years",
                                "29 CFR §516.6 — Records of hours worked and wages paid",
                                "29 USC §211(c) — Employer record-keeping obligation")),
                leg("NIST", "NIST Cybersecurity Framework", "NIST CSF", "US",
                        "https://www.nist.gov/cyberframework",
                        "NIST framework for managing cybersecurity risk, widely adopted as a standard for IT governance and security record-keeping.",
                        List.of("PR.DS — Data Security controls",
                                "DE.AE — Anomalies and Events detection",
                                "RS.AN — Incident analysis and response",
                                "ID.GV — Governance policies and procedures")),
                leg("UCC", "Uniform Commercial Code", "UCC", "US",
                        "https://www.law.cornell.edu/ucc",
                        "Standardised set of commercial laws governing sales, leases, and commercial transactions across US states.",
                        List.of("Article 2 — Sales of goods",
                                "Article 2A — Leases",
                                "§2-725 — 4-year statute of limitations for sales contracts"))
        );
        return component(PackComponent.ComponentType.LEGISLATION, "US Legislation & Regulations",
                "6 key pieces of US legislation that drive governance decisions", data);
    }

    // ── Taxonomy (with cross-references) ─────────────

    private PackComponent buildTaxonomy() {
        List<Map<String, Object>> data = List.of(
                cat("LEG", "Legal", null, "FUNCTION",
                        "Legal documents, contracts, agreements, and litigation records", "CONFIDENTIAL",
                        List.of("contract", "legal", "agreement", "litigation"), null, null,
                        null, "UK", null, null,
                        null, false, true, "P1Y",
                        "Includes: all legal correspondence, contracts, court documents. Excludes: compliance/regulatory (see COM)."),
                cat("LEG-CON", "Contracts", "Legal", "ACTIVITY",
                        "Legally binding agreements between parties", "CONFIDENTIAL",
                        List.of("contract", "agreement", "terms"), "Standard Business", "Contract / Agreement",
                        null, "UK", null, "Limitation Act 1980 s.5",
                        "DATE_CLOSED", false, false, "P1Y",
                        "Includes: service agreements, NDAs, employment contracts. Excludes: purchase orders (see FIN-IR)."),
                cat("LEG-LIT", "Litigation", "Legal", "ACTIVITY",
                        "Court proceedings, disputes, and legal claims", "RESTRICTED",
                        List.of("court", "claim", "dispute", "litigation"), "Regulatory Extended", null,
                        null, "UK", null, "Limitation Act 1980",
                        "DATE_CLOSED", false, true, "P1Y",
                        "Includes: court filings, correspondence with solicitors, settlement agreements."),
                cat("FIN", "Finance", null, "FUNCTION",
                        "Financial records, invoices, budgets, and tax documents", "CONFIDENTIAL",
                        List.of("finance", "invoice", "budget", "tax"), null, null,
                        null, "UK", null, null,
                        "END_OF_FINANCIAL_YEAR", false, true, "P1Y",
                        "Includes: all financial transactions, budgets, forecasts. Excludes: payroll (see HR-PAY via HR)."),
                cat("FIN-IR", "Invoices & Receipts", "Finance", "ACTIVITY",
                        "Purchase and sales invoices, receipts", "INTERNAL",
                        List.of("invoice", "receipt", "purchase order"), "Financial Statutory", "Invoice",
                        null, "UK", null, "HMRC Record Keeping Requirements",
                        "END_OF_FINANCIAL_YEAR", false, false, null,
                        "Includes: purchase invoices, sales invoices, expense receipts."),
                cat("FIN-PAY", "Payroll", "Finance", "ACTIVITY",
                        "Salary, pension, and employee compensation records", "RESTRICTED",
                        List.of("payroll", "salary", "pension", "P45", "P60"), "Financial Statutory", null,
                        null, "UK", null, "HMRC: payroll records 3 years after end of tax year",
                        "END_OF_FINANCIAL_YEAR", true, false, null,
                        "Includes: P45s, P60s, pension contributions, salary records."),
                cat("FIN-TAX", "Tax Returns", "Finance", "ACTIVITY",
                        "Corporation tax, VAT returns, HMRC submissions", "CONFIDENTIAL",
                        List.of("tax", "VAT", "HMRC", "corporation tax"), "Financial Statutory", null,
                        null, "UK", null, "HMRC: 6 years from end of accounting period",
                        "END_OF_FINANCIAL_YEAR", false, false, null,
                        "Includes: corporation tax returns, VAT returns, HMRC correspondence."),
                cat("HR", "HR", null, "FUNCTION",
                        "Human resources records — employee files, recruitment, training", "CONFIDENTIAL",
                        List.of("hr", "employee", "staff", "personnel"), null, null,
                        null, "UK", null, "DPA 2018",
                        null, true, false, "P1Y",
                        "Includes: all employee-related records. Excludes: payroll financials (see FIN-PAY)."),
                cat("HR-EMP", "Employee Records", "HR", "ACTIVITY",
                        "Personal files, contracts of employment, performance reviews", "RESTRICTED",
                        List.of("employee file", "performance", "disciplinary"), "Regulatory Extended", null,
                        null, "UK", null, "DPA 2018 Schedule 1",
                        "DATE_CLOSED", true, false, "P1Y",
                        "Includes: employment contracts, disciplinary records, grievances. Excludes: recruitment (see HR-REC)."),
                cat("HR-REC", "Recruitment", "HR", "ACTIVITY",
                        "Job applications, CVs, interview notes", "CONFIDENTIAL",
                        List.of("recruitment", "CV", "application", "interview"), "Short-Term Operational", null,
                        null, "UK", null, "GDPR Art.17 — right to erasure",
                        "EVENT_BASED", true, false, null,
                        "Includes: CVs, application forms, interview notes. Trigger: position filled or application withdrawn."),
                cat("OPS", "Operations", null, "FUNCTION",
                        "Operational documents — policies, procedures, project records", "INTERNAL",
                        List.of("operations", "procedure", "process"), "Standard Business", null,
                        null, "UK", null, null,
                        "SUPERSEDED", false, false, "P2Y",
                        "Includes: SOPs, project documentation, process maps."),
                cat("COM", "Compliance", null, "FUNCTION",
                        "Regulatory compliance, audit reports, risk assessments", "CONFIDENTIAL",
                        List.of("compliance", "audit", "risk", "regulatory"), null, null,
                        null, "UK", null, null,
                        null, false, true, "P1Y",
                        "Includes: audit reports, risk registers, regulatory correspondence."),
                cat("COM-AUD", "Audit Reports", "Compliance", "ACTIVITY",
                        "Internal and external audit reports", "CONFIDENTIAL",
                        List.of("audit", "report", "finding"), "Regulatory Extended", null,
                        null, "UK", null, "Companies Act 2006",
                        "DATE_CREATED", false, false, null,
                        "Includes: internal audit reports, external audit findings, management responses."),
                cat("IT", "IT", null, "FUNCTION",
                        "Information technology — security, infrastructure, systems", "INTERNAL",
                        List.of("IT", "technology", "system", "infrastructure"), null, null,
                        null, "UK", null, null,
                        null, false, false, "P2Y",
                        "Includes: system documentation, change records, architecture diagrams."),
                cat("IT-SEC", "Security Incidents", "IT", "ACTIVITY",
                        "Cyber security incidents, breach reports", "RESTRICTED",
                        List.of("security", "incident", "breach", "vulnerability"), "Standard Business", null,
                        null, "UK", null, "GDPR Art.33 — breach notification",
                        "DATE_CLOSED", true, false, null,
                        "Includes: incident reports, breach notifications, forensic reports."),
                cat("MKT", "Marketing", null, "FUNCTION",
                        "Marketing materials, campaigns, brand assets", "PUBLIC",
                        List.of("marketing", "campaign", "brand", "press"), "Short-Term Operational", null,
                        null, "UK", null, null,
                        "SUPERSEDED", false, false, "P2Y",
                        "Includes: campaign briefs, press releases, brand guidelines."),
                cat("SAL", "Sales", null, "FUNCTION",
                        "Sales proposals, quotes, client communications", "INTERNAL",
                        List.of("sales", "proposal", "quote", "client"), "Standard Business", null,
                        null, "UK", null, null,
                        "DATE_CLOSED", false, false, "P1Y",
                        "Includes: proposals, quotes, tender responses, client correspondence.")
        );
        return component(PackComponent.ComponentType.TAXONOMY_CATEGORIES, "UK Business Taxonomy",
                "Standard UK business document taxonomy with 17 categories across 7 top-level domains", data);
    }

    private PackComponent buildRetentionSchedules() {
        List<Map<String, Object>> data = List.of(
                retention("Short-Term Operational", "Operational documents with no statutory retention requirement",
                        365, "P1Y", "DATE_CREATED", "REVIEW", true,
                        "Business operational needs — no statutory requirement",
                        List.of()),
                retention("Standard Business", "General business records — 3 year retention",
                        1095, "P3Y", "DATE_CREATED", "ARCHIVE", true,
                        "Limitation Act 1980 — 3 years for personal injury claims",
                        List.of("LIMITATION_ACT_1980")),
                retention("Financial Statutory", "Financial records — 6 year minimum per HMRC requirements",
                        2190, "P6Y", "END_OF_FINANCIAL_YEAR", "DELETE", true,
                        "HMRC: 6 years from end of accounting period. Companies Act 2006 s.388.",
                        List.of("HMRC_RECORD_KEEPING", "COMPANIES_ACT_2006")),
                retention("Regulatory Extended", "Records with extended regulatory retention — 7-10 years",
                        3650, "P10Y", "DATE_CREATED", "REVIEW", true,
                        "FCA SYSC 9: 5-10 years for regulated financial services. Limitation Act: 6 years for contract claims.",
                        List.of("FCA_SYSC_9", "LIMITATION_ACT_1980")),
                retention("Permanent", "Records that must be retained permanently",
                        -1, "PERMANENT", null, "PERMANENT", false,
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
                cat("PAT", "Patient Records", null, "FUNCTION",
                        "Individual patient health records", "RESTRICTED",
                        List.of("patient", "medical", "clinical", "health record"), "Adult Patient Records", null,
                        null, "UK", null, "NHS Records Management Code s.12",
                        "DATE_CLOSED", true, true, "P1Y",
                        "Includes: all patient-identifiable clinical records. Excludes: anonymised research data (see RES)."),
                cat("PAT-CLN", "Clinical Notes", "Patient Records", "ACTIVITY",
                        "Clinician notes, consultations, assessments", "RESTRICTED",
                        List.of("clinical", "consultation", "assessment"), "Adult Patient Records", null,
                        null, "UK", null, "NHS RM Code Appendix III",
                        "DATE_CLOSED", true, false, null,
                        "Includes: consultation notes, care plans, clinical assessments."),
                cat("PAT-RX", "Prescriptions", "Patient Records", "ACTIVITY",
                        "Medication prescriptions and dispensing records", "RESTRICTED",
                        List.of("prescription", "medication", "dispensing"), "Adult Patient Records", null,
                        null, "UK", null, "NHS RM Code Appendix III",
                        "DATE_CREATED", true, false, null,
                        "Includes: prescriptions, dispensing records, medication reviews."),
                cat("PAT-TST", "Test Results", "Patient Records", "ACTIVITY",
                        "Laboratory, imaging, and diagnostic test results", "RESTRICTED",
                        List.of("test", "laboratory", "imaging", "pathology"), "Adult Patient Records", null,
                        null, "UK", null, "NHS RM Code Appendix III",
                        "DATE_CREATED", true, false, null,
                        "Includes: blood tests, X-rays, MRIs, pathology reports."),
                cat("PAT-REF", "Referrals", "Patient Records", "ACTIVITY",
                        "Referral letters between healthcare providers", "CONFIDENTIAL",
                        List.of("referral", "transfer", "handover"), "Adult Patient Records", null,
                        null, "UK", null, "NHS RM Code",
                        "DATE_CREATED", true, false, null,
                        "Includes: GP referrals, inter-hospital transfers, discharge summaries."),
                cat("ADM", "Administrative", null, "FUNCTION",
                        "Healthcare administrative records", "INTERNAL",
                        List.of("admin", "appointment", "registration"), null, null,
                        null, "UK", null, null,
                        null, false, false, "P2Y",
                        "Includes: appointment records, registrations, administrative correspondence."),
                cat("ADM-APT", "Appointments", "Administrative", "ACTIVITY",
                        "Patient appointment records", "CONFIDENTIAL",
                        List.of("appointment", "booking", "schedule"), null, null,
                        null, "UK", null, null,
                        "DATE_CREATED", true, false, null,
                        "Includes: appointment bookings, cancellations, DNA records."),
                cat("ADM-CON", "Consent Forms", "Administrative", "ACTIVITY",
                        "Patient consent and capacity documentation", "RESTRICTED",
                        List.of("consent", "capacity", "MCA"), "Adult Patient Records", null,
                        null, "UK", null, "Caldicott Principles",
                        "DATE_CLOSED", true, true, null,
                        "Includes: treatment consent, mental capacity assessments, advance decisions."),
                cat("SAF", "Safeguarding", null, "FUNCTION",
                        "Safeguarding and child protection records", "RESTRICTED",
                        List.of("safeguarding", "child protection", "vulnerable adult"), "Children's Records", null,
                        null, "UK", null, "HSCA 2012; Children Act",
                        "DATE_CLOSED", true, true, "P1Y",
                        "Includes: child protection referrals, safeguarding assessments, MARAC records."),
                cat("RES", "Research", null, "FUNCTION",
                        "Clinical research and trial documentation", "CONFIDENTIAL",
                        List.of("research", "clinical trial", "study"), "Regulatory Extended", null,
                        null, "UK", null, "GDPR Art.89",
                        "DATE_CLOSED", true, false, "P1Y",
                        "Includes: clinical trial data, research protocols, ethics approvals. Excludes: patient records used in research (see PAT).")
        );
        return component(PackComponent.ComponentType.TAXONOMY_CATEGORIES, "UK Healthcare Taxonomy",
                "10 healthcare-specific categories covering patient records, admin, safeguarding, and research", data);
    }

    private PackComponent buildHealthcareRetention() {
        List<Map<String, Object>> data = List.of(
                retention("Adult Patient Records", "GP and hospital records for adult patients",
                        2920, "P8Y", "DATE_CLOSED", "REVIEW", true,
                        "NHS Records Management Code: 8 years after last attendance or 3 years after death",
                        List.of("NHS_RECORDS_MANAGEMENT_CODE", "GDPR")),
                retention("Children's Records", "Records relating to children and young people",
                        9125, "P25Y", "EVENT_BASED", "REVIEW", true,
                        "NHS Records Management Code: retain until patient's 25th birthday or 26th if 17 at conclusion of treatment",
                        List.of("NHS_RECORDS_MANAGEMENT_CODE", "GDPR")),
                retention("Mental Health Records", "Mental health treatment records",
                        7300, "P20Y", "DATE_CLOSED", "REVIEW", true,
                        "NHS Records Management Code: 20 years after last contact or 8 years after death",
                        List.of("NHS_RECORDS_MANAGEMENT_CODE", "GDPR")),
                retention("Maternity Records", "Maternity and obstetric records",
                        9125, "P25Y", "EVENT_BASED", "REVIEW", true,
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

    // ── US pack components ───────────────────────────

    private PackComponent buildUsTaxonomy() {
        List<Map<String, Object>> data = List.of(
                // ── Corporate Governance & Administration ──
                cat("COR", "Corporate Governance & Administration", null, "FUNCTION",
                        "Corporate governance, administration, and strategic planning", "CONFIDENTIAL",
                        List.of("governance", "board", "corporate", "administration"),
                        null, null, null, "US", null, null,
                        null, false, true, null, null),
                cat("COR-GOV", "Corporate Governance", "Corporate Governance & Administration", "ACTIVITY",
                        "Board and committee governance activities", "CONFIDENTIAL",
                        List.of("board", "committee", "governance"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("COR-GOV-BRD", "Board & Committee Records", "Corporate Governance", "TRANSACTION",
                        "Board agendas, minutes, resolutions, committee records", "CONFIDENTIAL",
                        List.of("board", "minutes", "resolutions", "committee"),
                        null, null,
                        List.of("Board agendas", "minutes", "resolutions"), "US",
                        "Permanent / 7+ years", "State corporate statutes; Sarbanes-Oxley Act",
                        null, false, true, null, null),
                cat("COR-GOV-POL", "Policies & Frameworks", "Corporate Governance", "TRANSACTION",
                        "Corporate policies, governance frameworks", "CONFIDENTIAL",
                        List.of("policy", "framework", "governance", "corporate"),
                        null, null,
                        List.of("Corporate policies", "governance frameworks"), "US",
                        "6 years after superseded", "Sarbanes-Oxley Act (18 USC §1519)",
                        null, false, false, null, null),
                cat("COR-ENT", "Legal Entity Management", "Corporate Governance & Administration", "ACTIVITY",
                        "Legal entity and statutory filing activities", "CONFIDENTIAL",
                        List.of("entity", "statutory", "incorporation"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("COR-ENT-STA", "Statutory Filings", "Legal Entity Management", "TRANSACTION",
                        "Annual returns, incorporation records", "CONFIDENTIAL",
                        List.of("statutory", "filing", "incorporation", "annual return"),
                        null, null,
                        List.of("Annual returns", "incorporation records"), "US",
                        "Permanent", "State corporate law statutes",
                        null, false, true, null, null),
                cat("COR-STR", "Strategic Planning", "Corporate Governance & Administration", "ACTIVITY",
                        "Corporate strategic and business planning activities", "CONFIDENTIAL",
                        List.of("strategy", "planning", "business plan"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("COR-STR-PLN", "Corporate Plans", "Strategic Planning", "TRANSACTION",
                        "Strategic and business plans", "CONFIDENTIAL",
                        List.of("strategic plan", "business plan", "corporate plan"),
                        null, null,
                        List.of("Strategic and business plans"), "US",
                        "6 years", "Business practice (no specific statute)",
                        null, false, false, null, null),

                // ── Finance & Accounting ──
                cat("FIN", "Finance & Accounting", null, "FUNCTION",
                        "Financial reporting, accounts, budgeting, and taxation", "CONFIDENTIAL",
                        List.of("finance", "accounting", "budget", "tax"),
                        null, null, null, "US", null, null,
                        null, false, true, null, null),
                cat("FIN-REP", "Financial Reporting", "Finance & Accounting", "ACTIVITY",
                        "Financial statement preparation and reporting", "CONFIDENTIAL",
                        List.of("financial statements", "reporting", "balance sheet"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("FIN-REP-FST", "Financial Statements", "Financial Reporting", "TRANSACTION",
                        "Balance sheets, income statements", "CONFIDENTIAL",
                        List.of("balance sheet", "income statement", "financial statement"),
                        null, null,
                        List.of("Balance sheets", "income statements"), "US",
                        "7 years", "IRS requirements (IRC §6001)",
                        null, false, true, null, null),
                cat("FIN-AP", "Accounts Payable", "Finance & Accounting", "ACTIVITY",
                        "Supplier payment and invoice processing", "INTERNAL",
                        List.of("accounts payable", "supplier", "invoice"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("FIN-AP-PAY", "Supplier Payments", "Accounts Payable", "TRANSACTION",
                        "Invoices, supplier payments", "INTERNAL",
                        List.of("invoice", "supplier payment", "accounts payable"),
                        null, null,
                        List.of("Invoices", "supplier payments"), "US",
                        "7 years", "IRS requirements (IRC §6001)",
                        null, false, false, null, null),
                cat("FIN-AR", "Accounts Receivable", "Finance & Accounting", "ACTIVITY",
                        "Customer billing and receivables management", "INTERNAL",
                        List.of("accounts receivable", "billing", "customer invoice"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("FIN-AR-BIL", "Customer Billing", "Accounts Receivable", "TRANSACTION",
                        "Customer invoices, statements", "INTERNAL",
                        List.of("customer invoice", "statement", "billing"),
                        null, null,
                        List.of("Customer invoices", "statements"), "US",
                        "7 years", "IRS requirements (IRC §6001)",
                        null, false, false, null, null),
                cat("FIN-BUD", "Budgeting", "Finance & Accounting", "ACTIVITY",
                        "Budget preparation and forecasting", "INTERNAL",
                        List.of("budget", "forecast", "financial planning"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("FIN-BUD-FOR", "Budgets & Forecasts", "Budgeting", "TRANSACTION",
                        "Budgets and forecasts", "INTERNAL",
                        List.of("budget", "forecast"),
                        null, null,
                        List.of("Budgets and forecasts"), "US",
                        "3 years", "GAO Records Schedule guidance",
                        null, false, false, null, null),
                cat("FIN-TAX", "Taxation", "Finance & Accounting", "ACTIVITY",
                        "Tax filing and assessment activities", "CONFIDENTIAL",
                        List.of("tax", "IRS", "tax return"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("FIN-TAX-FIL", "Tax Filings", "Taxation", "TRANSACTION",
                        "Tax returns, assessments", "CONFIDENTIAL",
                        List.of("tax return", "tax filing", "assessment"),
                        null, null,
                        List.of("Tax returns", "assessments"), "US",
                        "7+ years", "IRS Recordkeeping Rules",
                        null, false, true, null, null),

                // ── Human Resources ──
                cat("HR", "Human Resources", null, "FUNCTION",
                        "Human resources management including recruitment, employment, payroll, and training", "CONFIDENTIAL",
                        List.of("hr", "human resources", "employee", "personnel"),
                        null, null, null, "US", null, null,
                        null, true, false, null, null),
                cat("HR-REC", "Recruitment", "Human Resources", "ACTIVITY",
                        "Recruitment and hiring activities", "CONFIDENTIAL",
                        List.of("recruitment", "hiring", "job posting"),
                        null, null, null, "US", null, null,
                        null, true, false, null, null),
                cat("HR-REC-APP", "Recruitment Records", "Recruitment", "TRANSACTION",
                        "Job postings, interview notes", "CONFIDENTIAL",
                        List.of("job posting", "interview", "application", "resume"),
                        null, null,
                        List.of("Job postings", "interview notes"), "US",
                        "1 year", "EEOC (29 CFR §1602)",
                        null, true, false, null, null),
                cat("HR-EMP", "Employment", "Human Resources", "ACTIVITY",
                        "Employment administration and records management", "RESTRICTED",
                        List.of("employment", "employee record", "contract"),
                        null, null, null, "US", null, null,
                        null, true, false, null, null),
                cat("HR-EMP-REC", "Employee Master Records", "Employment", "TRANSACTION",
                        "Employment contracts, job descriptions", "RESTRICTED",
                        List.of("employee record", "employment contract", "job description"),
                        null, null,
                        List.of("Employment contracts", "job descriptions"), "US",
                        "7 years after termination", "FLSA; IRS requirements",
                        null, true, false, null, null),
                cat("HR-PAY", "Payroll", "Human Resources", "ACTIVITY",
                        "Payroll processing and compensation administration", "RESTRICTED",
                        List.of("payroll", "salary", "compensation"),
                        null, null, null, "US", null, null,
                        null, true, false, null, null),
                cat("HR-PAY-PAY", "Payroll Processing", "Payroll", "TRANSACTION",
                        "Payslips, payroll registers", "RESTRICTED",
                        List.of("payslip", "payroll register", "wages"),
                        null, null,
                        List.of("Payslips", "payroll registers"), "US",
                        "3 years", "FLSA (29 CFR §516)",
                        null, true, false, null, null),
                cat("HR-PER", "Performance Management", "Human Resources", "ACTIVITY",
                        "Performance evaluation and objectives management", "CONFIDENTIAL",
                        List.of("performance", "appraisal", "objectives"),
                        null, null, null, "US", null, null,
                        null, true, false, null, null),
                cat("HR-PER-REV", "Performance Reviews", "Performance Management", "TRANSACTION",
                        "Appraisals, objectives", "CONFIDENTIAL",
                        List.of("appraisal", "performance review", "objectives"),
                        null, null,
                        List.of("Appraisals", "objectives"), "US",
                        "3 years", "EEOC (29 CFR §1602)",
                        null, true, false, null, null),
                cat("HR-LRN", "Learning & Development", "Human Resources", "ACTIVITY",
                        "Employee training and professional development activities", "INTERNAL",
                        List.of("training", "learning", "development", "certification"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("HR-LRN-TRN", "Training Records", "Learning & Development", "TRANSACTION",
                        "Training attendance, certificates", "INTERNAL",
                        List.of("training record", "certificate", "attendance"),
                        null, null,
                        List.of("Training attendance", "certificates"), "US",
                        "3-5 years", "OSHA training rules",
                        null, false, false, null, null),

                // ── Operations & Service Delivery ──
                cat("OPS", "Operations & Service Delivery", null, "FUNCTION",
                        "Operational planning, service delivery, quality, and asset management", "INTERNAL",
                        List.of("operations", "service", "quality", "asset"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("OPS-PLN", "Operational Planning", "Operations & Service Delivery", "ACTIVITY",
                        "Operational and production planning activities", "INTERNAL",
                        List.of("operational plan", "production plan"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("OPS-PLN-OPR", "Operating Plans", "Operational Planning", "TRANSACTION",
                        "Production and service plans", "INTERNAL",
                        List.of("production plan", "service plan", "operating plan"),
                        null, null,
                        List.of("Production and service plans"), "US",
                        "6 years", "Business practice",
                        null, false, false, null, null),
                cat("OPS-SRV", "Service Delivery", "Operations & Service Delivery", "ACTIVITY",
                        "Service execution and delivery activities", "INTERNAL",
                        List.of("service delivery", "work order", "service report"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("OPS-SRV-DEL", "Service Records", "Service Delivery", "TRANSACTION",
                        "Work orders, service reports", "INTERNAL",
                        List.of("work order", "service report"),
                        null, null,
                        List.of("Work orders", "service reports"), "US",
                        "6 years", "Contract law statutes of limitation",
                        null, false, false, null, null),
                cat("OPS-QLT", "Quality Management", "Operations & Service Delivery", "ACTIVITY",
                        "Quality assurance and inspection activities", "INTERNAL",
                        List.of("quality", "audit", "inspection"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("OPS-QLT-QAR", "Quality Records", "Quality Management", "TRANSACTION",
                        "Quality audits, inspections", "INTERNAL",
                        List.of("quality audit", "inspection", "quality record"),
                        null, null,
                        List.of("Quality audits", "inspections"), "US",
                        "5 years", "ISO / regulatory requirements",
                        null, false, false, null, null),
                cat("OPS-AST", "Asset Management", "Operations & Service Delivery", "ACTIVITY",
                        "Asset tracking and maintenance activities", "INTERNAL",
                        List.of("asset", "maintenance", "equipment"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("OPS-AST-REG", "Asset Records", "Asset Management", "TRANSACTION",
                        "Asset registers, maintenance logs", "INTERNAL",
                        List.of("asset register", "maintenance log", "equipment"),
                        null, null,
                        List.of("Asset registers", "maintenance logs"), "US",
                        "Life of asset + 7 years", "IRS depreciation rules",
                        null, false, false, null, null),

                // ── Sales & Marketing ──
                cat("SAL", "Sales & Marketing", null, "FUNCTION",
                        "Sales management, customer relations, and marketing activities", "INTERNAL",
                        List.of("sales", "marketing", "customer", "campaign"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("SAL-SLS", "Sales Management", "Sales & Marketing", "ACTIVITY",
                        "Sales order and contract management", "INTERNAL",
                        List.of("sales order", "sales contract"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("SAL-SLS-ORD", "Sales Orders", "Sales Management", "TRANSACTION",
                        "Sales contracts, order forms", "INTERNAL",
                        List.of("sales contract", "order form", "sales order"),
                        null, null,
                        List.of("Sales contracts", "order forms"), "US",
                        "6 years", "UCC / State contract law",
                        null, false, false, null, null),
                cat("SAL-CUS", "Customer Management", "Sales & Marketing", "ACTIVITY",
                        "Customer relationship and account management", "INTERNAL",
                        List.of("customer", "CRM", "account"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("SAL-CUS-CRM", "Customer Records", "Customer Management", "TRANSACTION",
                        "Customer profiles, correspondence", "INTERNAL",
                        List.of("customer profile", "correspondence", "CRM"),
                        null, null,
                        List.of("Customer profiles", "correspondence"), "US",
                        "6 years", "Contract law statutes",
                        null, false, false, null, null),
                cat("SAL-MKT", "Marketing Campaigns", "Sales & Marketing", "ACTIVITY",
                        "Marketing campaign planning and execution", "INTERNAL",
                        List.of("marketing", "campaign", "creative"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("SAL-MKT-CMP", "Campaign Materials", "Marketing Campaigns", "TRANSACTION",
                        "Marketing plans, creatives", "INTERNAL",
                        List.of("marketing plan", "creative", "campaign material"),
                        null, null,
                        List.of("Marketing plans", "creatives"), "US",
                        "3 years", "Business practice",
                        null, false, false, null, null),
                cat("SAL-MKR", "Market Research", "Sales & Marketing", "ACTIVITY",
                        "Market research and analysis activities", "INTERNAL",
                        List.of("market research", "analysis", "survey"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("SAL-MKT-RES", "Research Reports", "Market Research", "TRANSACTION",
                        "Market research reports", "INTERNAL",
                        List.of("market research", "research report"),
                        null, null,
                        List.of("Market research reports"), "US",
                        "5 years", "Business practice",
                        null, false, false, null, null),

                // ── Procurement & Supply Chain ──
                cat("PRO", "Procurement & Supply Chain", null, "FUNCTION",
                        "Procurement, sourcing, contracting, purchasing, and inventory management", "INTERNAL",
                        List.of("procurement", "supply chain", "purchasing", "inventory"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("PRO-SRC", "Sourcing", "Procurement & Supply Chain", "ACTIVITY",
                        "Supplier sourcing and evaluation activities", "INTERNAL",
                        List.of("sourcing", "RFP", "bid"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("PRO-SRC-EVL", "Supplier Selection", "Sourcing", "TRANSACTION",
                        "RFPs, bid evaluations", "INTERNAL",
                        List.of("RFP", "bid evaluation", "supplier selection"),
                        null, null,
                        List.of("RFPs", "bid evaluations"), "US",
                        "6 years", "Contract law statutes",
                        null, false, false, null, null),
                cat("PRO-CON", "Contracting", "Procurement & Supply Chain", "ACTIVITY",
                        "Supplier contract management", "CONFIDENTIAL",
                        List.of("contract", "supplier agreement"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("PRO-CON-AGR", "Supplier Contracts", "Contracting", "TRANSACTION",
                        "Contracts and amendments", "CONFIDENTIAL",
                        List.of("supplier contract", "amendment", "agreement"),
                        null, null,
                        List.of("Contracts and amendments"), "US",
                        "6 years after expiry", "State contract law statutes",
                        null, false, false, null, null),
                cat("PRO-PUR", "Purchasing", "Procurement & Supply Chain", "ACTIVITY",
                        "Purchase order processing", "INTERNAL",
                        List.of("purchase order", "PO", "purchasing"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("PRO-PUR-PO", "Purchase Orders", "Purchasing", "TRANSACTION",
                        "Purchase orders, confirmations", "INTERNAL",
                        List.of("purchase order", "PO", "confirmation"),
                        null, null,
                        List.of("Purchase orders", "confirmations"), "US",
                        "7 years", "IRS requirements",
                        null, false, false, null, null),
                cat("PRO-INV", "Inventory Control", "Procurement & Supply Chain", "ACTIVITY",
                        "Inventory tracking and control activities", "INTERNAL",
                        List.of("inventory", "stock", "warehouse"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("PRO-INV-REG", "Inventory Records", "Inventory Control", "TRANSACTION",
                        "Stock lists, inventory movements", "INTERNAL",
                        List.of("stock list", "inventory", "movement"),
                        null, null,
                        List.of("Stock lists", "inventory movements"), "US",
                        "3 years", "IRS requirements",
                        null, false, false, null, null),

                // ── Legal & Compliance ──
                cat("LEG", "Legal & Compliance", null, "FUNCTION",
                        "Legal advice, contracts management, compliance, and litigation", "CONFIDENTIAL",
                        List.of("legal", "compliance", "litigation", "contract"),
                        null, null, null, "US", null, null,
                        null, false, true, null, null),
                cat("LEG-ADV", "Legal Advice", "Legal & Compliance", "ACTIVITY",
                        "Legal counsel and advisory activities", "RESTRICTED",
                        List.of("legal advice", "memo", "opinion"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("LEG-ADV-OPN", "Legal Opinions", "Legal Advice", "TRANSACTION",
                        "Legal memos, opinions", "RESTRICTED",
                        List.of("legal memo", "opinion", "legal advice"),
                        null, null,
                        List.of("Legal memos", "opinions"), "US",
                        "6 years", "Attorney-client privilege practice",
                        null, false, false, null, null),
                cat("LEG-CON", "Contracts Management", "Legal & Compliance", "ACTIVITY",
                        "Contract lifecycle management", "CONFIDENTIAL",
                        List.of("contract", "agreement", "legal contract"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("LEG-CON-CTR", "Contracts", "Contracts Management", "TRANSACTION",
                        "Contracts, agreements", "CONFIDENTIAL",
                        List.of("contract", "agreement"),
                        null, null,
                        List.of("Contracts", "agreements"), "US",
                        "6 years after expiry", "State statutes of limitation",
                        null, false, false, null, null),
                cat("LEG-CMP", "Compliance", "Legal & Compliance", "ACTIVITY",
                        "Regulatory compliance and audit activities", "CONFIDENTIAL",
                        List.of("compliance", "regulatory", "audit"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("LEG-CMP-RPT", "Compliance Reports", "Compliance", "TRANSACTION",
                        "Regulatory filings, audit reports", "CONFIDENTIAL",
                        List.of("compliance report", "regulatory filing", "audit report"),
                        null, null,
                        List.of("Regulatory filings", "audit reports"), "US",
                        "5-7 years", "Regulator-specific statutes",
                        null, false, false, null, null),
                cat("LEG-LIT", "Litigation", "Legal & Compliance", "ACTIVITY",
                        "Litigation and dispute management", "RESTRICTED",
                        List.of("litigation", "case", "court"),
                        null, null, null, "US", null, null,
                        null, false, true, null, null),
                cat("LEG-LIT-CAS", "Case Files", "Litigation", "TRANSACTION",
                        "Case files, court correspondence", "RESTRICTED",
                        List.of("case file", "court", "litigation"),
                        null, null,
                        List.of("Case files", "court correspondence"), "US",
                        "Final disposition + 6 years", "Federal Rules of Civil Procedure",
                        null, false, true, null, null),

                // ── Information & Technology ──
                cat("IT", "Information & Technology", null, "FUNCTION",
                        "IT governance, system development, service management, and information security", "INTERNAL",
                        List.of("IT", "technology", "system", "security"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("IT-GOV", "IT Governance", "Information & Technology", "ACTIVITY",
                        "IT policy and standards management", "INTERNAL",
                        List.of("IT policy", "IT governance", "standards"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("IT-GOV-POL", "IT Policies", "IT Governance", "TRANSACTION",
                        "IT policies and standards", "INTERNAL",
                        List.of("IT policy", "IT standard"),
                        null, null,
                        List.of("IT policies and standards"), "US",
                        "6 years after superseded", "Sarbanes-Oxley Act",
                        null, false, false, null, null),
                cat("IT-DEV", "System Development", "Information & Technology", "ACTIVITY",
                        "System design and development activities", "INTERNAL",
                        List.of("system development", "design", "requirements"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("IT-DEV-SYS", "System Documentation", "System Development", "TRANSACTION",
                        "System requirements, designs", "INTERNAL",
                        List.of("system requirements", "design document", "architecture"),
                        null, null,
                        List.of("System requirements", "designs"), "US",
                        "System life + 6 years", "Business practice",
                        null, false, false, null, null),
                cat("IT-SRV", "Service Management", "Information & Technology", "ACTIVITY",
                        "IT service management and incident handling", "INTERNAL",
                        List.of("service management", "incident", "ITSM"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("IT-SRV-INC", "Incident Records", "Service Management", "TRANSACTION",
                        "Incident tickets, resolutions", "INTERNAL",
                        List.of("incident ticket", "resolution", "service desk"),
                        null, null,
                        List.of("Incident tickets", "resolutions"), "US",
                        "3 years", "Business practice",
                        null, false, false, null, null),
                cat("IT-SEC", "Information Security", "Information & Technology", "ACTIVITY",
                        "Information security and access control activities", "RESTRICTED",
                        List.of("security", "access control", "cybersecurity"),
                        null, null, null, "US", null, null,
                        null, false, false, null, null),
                cat("IT-SEC-LOG", "Security Records", "Information Security", "TRANSACTION",
                        "Access logs, security incidents", "RESTRICTED",
                        List.of("access log", "security incident", "audit log"),
                        null, null,
                        List.of("Access logs", "security incidents"), "US",
                        "3-5 years", "NIST / regulatory guidance",
                        null, false, false, null, null)
        );
        return component(PackComponent.ComponentType.TAXONOMY_CATEGORIES, "US Business Taxonomy (ISO 15489)",
                "ISO 15489-aligned US business taxonomy with 8 functions, 26 activities, and 34 record classes", data);
    }

    private PackComponent buildUsRetention() {
        List<Map<String, Object>> data = List.of(
                retention("Permanent US", "Records that must be retained permanently",
                        -1, "PERMANENT", null, "PERMANENT", false,
                        "Board records, incorporation documents — permanent retention required by state corporate statutes and SOX.",
                        List.of("SOX")),
                retention("Short-Term US", "Short-term operational records — 1 year retention",
                        365, "P1Y", "DATE_CREATED", "REVIEW", true,
                        "EEOC: employment records retained for 1 year from making or personnel action.",
                        List.of("EEOC_29CFR1602")),
                retention("Standard US", "Standard business records — 3 year retention",
                        1095, "P3Y", "DATE_CREATED", "ARCHIVE", true,
                        "FLSA: payroll records for 3 years. GAO: budget records for 3 years.",
                        List.of("FLSA")),
                retention("Extended US", "Extended retention records — 5-6 years",
                        2190, "P6Y", "DATE_CREATED", "REVIEW", true,
                        "UCC: 4-year statute of limitations for sales. State contract law: 6 years for contract claims.",
                        List.of("UCC")),
                retention("Statutory US", "Statutory retention records — 7 years",
                        2555, "P7Y", "DATE_CREATED", "DELETE", true,
                        "IRS IRC §6001: 7 years for financial and tax records. SOX: 7 years for audit documentation.",
                        List.of("IRS_IRC_6001", "SOX"))
        );
        return component(PackComponent.ComponentType.RETENTION_SCHEDULES, "US Statutory Retention Schedules",
                "5 retention schedules aligned with US legislation (IRS, SOX, FLSA, EEOC, UCC)", data);
    }

    // ── Helpers ───────────────────────────────────────

    private static Map<String, Object> cat(String classificationCode, String name, String parentName,
                                           String level, String description,
                                           String defaultSensitivity, List<String> keywords,
                                           String retentionScheduleRef, String metadataSchemaRef,
                                           List<String> typicalRecords, String jurisdiction,
                                           String retentionPeriodText, String legalCitation,
                                           String retentionTrigger,
                                           boolean personalDataFlag, boolean vitalRecordFlag,
                                           String reviewCycleDuration, String scopeNotes) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("classificationCode", classificationCode);
        m.put("name", name);
        if (parentName != null) m.put("parentName", parentName);
        m.put("level", level);
        m.put("description", description);
        m.put("defaultSensitivity", defaultSensitivity);
        m.put("keywords", keywords);
        if (retentionScheduleRef != null) m.put("retentionScheduleRef", retentionScheduleRef);
        if (metadataSchemaRef != null) m.put("metadataSchemaRef", metadataSchemaRef);
        if (typicalRecords != null && !typicalRecords.isEmpty()) m.put("typicalRecords", typicalRecords);
        if (jurisdiction != null) m.put("jurisdiction", jurisdiction);
        if (retentionPeriodText != null) m.put("retentionPeriodText", retentionPeriodText);
        if (legalCitation != null) m.put("legalCitation", legalCitation);
        if (retentionTrigger != null) m.put("retentionTrigger", retentionTrigger);
        m.put("personalDataFlag", personalDataFlag);
        m.put("vitalRecordFlag", vitalRecordFlag);
        if (reviewCycleDuration != null) m.put("reviewCycleDuration", reviewCycleDuration);
        if (scopeNotes != null) m.put("scopeNotes", scopeNotes);
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
                                                  String retentionDuration, String retentionTrigger,
                                                  String dispositionAction, boolean legalHoldOverride,
                                                  String regulatoryBasis, List<String> legislationRefs) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", name);
        m.put("description", description);
        m.put("retentionDays", retentionDays);
        m.put("retentionDuration", retentionDuration);
        if (retentionTrigger != null) m.put("retentionTrigger", retentionTrigger);
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
