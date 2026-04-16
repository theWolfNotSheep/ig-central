package co.uk.wolfnotsheep.infrastructure.bootstrap;

import co.uk.wolfnotsheep.governance.models.*;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory.RetentionTrigger;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory.TaxonomyLevel;
import co.uk.wolfnotsheep.governance.models.PiiTypeDefinition.ApprovalStatus;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition.PipelineStep;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition.PipelineStep.StepType;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule.DispositionAction;
import co.uk.wolfnotsheep.governance.repositories.*;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Order(2)
public class GovernanceDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GovernanceDataSeeder.class);

    private final LegislationRepository legislationRepo;
    private final RetentionScheduleRepository retentionRepo;
    private final StorageTierRepository storageTierRepo;
    private final ClassificationCategoryRepository categoryRepo;
    private final GovernancePolicyRepository policyRepo;
    private final SensitivityDefinitionRepository sensitivityRepo;
    private final PipelineDefinitionRepository pipelineRepo;
    private final PiiTypeDefinitionRepository piiTypeRepo;
    private final MetadataSchemaRepository metadataSchemaRepo;
    private final PipelineBlockRepository blockRepo;
    private final TraitDefinitionRepository traitRepo;
    private final NodeTypeDefinitionRepository nodeTypeRepo;
    private final co.uk.wolfnotsheep.governance.services.NodeTypeDefinitionService nodeTypeService;
    private final GovernanceService governanceService;
    private final AppConfigService configService;

    public GovernanceDataSeeder(
            LegislationRepository legislationRepo,
            RetentionScheduleRepository retentionRepo,
            StorageTierRepository storageTierRepo,
            ClassificationCategoryRepository categoryRepo,
            GovernancePolicyRepository policyRepo,
            SensitivityDefinitionRepository sensitivityRepo,
            PipelineDefinitionRepository pipelineRepo,
            PiiTypeDefinitionRepository piiTypeRepo,
            MetadataSchemaRepository metadataSchemaRepo,
            PipelineBlockRepository blockRepo,
            TraitDefinitionRepository traitRepo,
            NodeTypeDefinitionRepository nodeTypeRepo,
            co.uk.wolfnotsheep.governance.services.NodeTypeDefinitionService nodeTypeService,
            GovernanceService governanceService,
            AppConfigService configService) {
        this.legislationRepo = legislationRepo;
        this.retentionRepo = retentionRepo;
        this.storageTierRepo = storageTierRepo;
        this.categoryRepo = categoryRepo;
        this.policyRepo = policyRepo;
        this.sensitivityRepo = sensitivityRepo;
        this.pipelineRepo = pipelineRepo;
        this.piiTypeRepo = piiTypeRepo;
        this.metadataSchemaRepo = metadataSchemaRepo;
        this.blockRepo = blockRepo;
        this.traitRepo = traitRepo;
        this.nodeTypeRepo = nodeTypeRepo;
        this.nodeTypeService = nodeTypeService;
        this.governanceService = governanceService;
        this.configService = configService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Governance data seeder starting...");

        // Jurisdiction-specific governance content (legislation, retention schedules,
        // metadata schemas, categories, policies) is no longer seeded by the platform.
        // The Governance Hub is the source of truth — admins import packs at /governance/hub.
        // Sensitivity definitions, storage tiers, traits, PII patterns, and pipeline
        // infrastructure ARE still seeded as they're universal/app-functional.
        log.info("Skipping legislation/retention/metadata/category/policy seed — comes from hub packs");
        Map<String, String> legislationIds = existingIdMap(legislationRepo);
        seedSensitivityDefinitions();
        Map<String, String> retentionIds = existingRetentionIdMap();
        seedStorageTiers();
        Map<String, String> schemaIds = existingSchemaIdMap();
        Map<String, String> categoryIds = seedCategories(retentionIds, schemaIds);
        governanceService.rebuildPaths();
        seedNodeTypeDefinitions();
        migrateNodeTypeDefinitions();
        nodeTypeService.refresh(); // reload cache after seed/migration
        Map<String, String> blockIds = seedPipelineBlocks();
        seedDefaultPipeline(blockIds);
        seedPipelineConfig();
        seedBertTrainingConfig();
        seedPiiTypeDefinitions();
        seedTraitDefinitions();

        log.info("Governance data seeder complete.");
    }

    // ── Legislation ───────────────────────────────────────────

    private Map<String, String> seedLegislation() {
        if (legislationRepo.count() > 0) {
            log.info("Legislation already seeded, skipping.");
            return legislationRepo.findAll().stream()
                    .collect(Collectors.toMap(Legislation::getKey, Legislation::getId));
        }

        List<Legislation> laws = List.of(
                legislation("GDPR", "General Data Protection Regulation", "GDPR", "UK",
                        "https://www.legislation.gov.uk/ukpga/2018/12/contents",
                        "UK implementation of GDPR via the Data Protection Act 2018. Governs processing of personal data.",
                        List.of("Article 5 — Principles relating to processing",
                                "Article 6 — Lawfulness of processing",
                                "Article 9 — Special categories of personal data",
                                "Article 15 — Right of access",
                                "Article 17 — Right to erasure",
                                "Article 30 — Records of processing activities")),
                legislation("DPA_2018", "Data Protection Act 2018", "DPA 2018", "UK",
                        "https://www.legislation.gov.uk/ukpga/2018/12/contents",
                        "UK domestic data protection legislation implementing GDPR.",
                        List.of("Part 2 — General processing (UK GDPR)",
                                "Part 3 — Law enforcement processing",
                                "Section 170 — Unlawful obtaining of personal data")),
                legislation("COMPANIES_ACT_2006", "Companies Act 2006", "Companies Act", "UK",
                        "https://www.legislation.gov.uk/ukpga/2006/46/contents",
                        "Primary legislation governing UK companies, including record-keeping requirements.",
                        List.of("Section 386 — Duty to keep accounting records",
                                "Section 388 — Accounting records kept for 3/6 years",
                                "Section 355 — Duty to keep minutes of meetings")),
                legislation("LIMITATION_ACT_1980", "Limitation Act 1980", "Limitation Act", "UK",
                        "https://www.legislation.gov.uk/ukpga/1980/58/contents",
                        "Sets time limits for bringing legal claims. Drives minimum retention periods.",
                        List.of("Section 2 — 6 years for tort",
                                "Section 5 — 6 years for contract",
                                "Section 11 — 3 years for personal injury")),
                legislation("HMRC_RECORD_KEEPING", "HMRC Record Keeping Requirements", "HMRC", "UK",
                        "https://www.gov.uk/running-a-limited-company/company-and-accounting-records",
                        "HMRC requirements for keeping financial and tax records.",
                        List.of("6 years from end of last accounting period",
                                "VAT records for 6 years",
                                "Payroll records for 3 years after end of tax year")),
                legislation("FCA_SYSC_9", "FCA Handbook SYSC 9 — Record Keeping", "FCA SYSC 9", "UK",
                        "https://www.handbook.fca.org.uk/handbook/SYSC/9.html",
                        "FCA record-keeping obligations for regulated financial services firms.",
                        List.of("SYSC 9.1.1 — Orderly records",
                                "SYSC 9.1.2 — Retained for 5 years (3 for non-MiFID)"))
        );

        List<Legislation> saved = legislationRepo.saveAll(laws);
        log.info("Seeded {} legislation entries.", saved.size());
        return saved.stream().collect(Collectors.toMap(Legislation::getKey, Legislation::getId));
    }

    private Legislation legislation(String key, String name, String shortName, String jurisdiction,
                                    String url, String description, List<String> articles) {
        Legislation l = new Legislation();
        l.setKey(key);
        l.setName(name);
        l.setShortName(shortName);
        l.setJurisdiction(jurisdiction);
        l.setUrl(url);
        l.setDescription(description);
        l.setRelevantArticles(articles);
        l.setActive(true);
        return l;
    }

    // ── Sensitivity Definitions ─────────────────────────────

    private void seedSensitivityDefinitions() {
        if (sensitivityRepo.count() > 0) {
            log.info("Sensitivity definitions already seeded, skipping.");
            return;
        }

        List<SensitivityDefinition> defs = List.of(
                sensitivityDef("PUBLIC", "Public", "No restrictions — can be shared externally", 0, "green",
                        List.of("Marketing materials, published reports, public-facing documents",
                                "Information already in the public domain",
                                "Press releases, brochures, newsletters"),
                        List.of("Published annual report", "Company brochure", "Public-facing FAQ document")),
                sensitivityDef("INTERNAL", "Internal", "Organisation-internal only — not for external sharing", 1, "blue",
                        List.of("Internal memos, meeting notes, non-sensitive operational docs",
                                "General business information not intended for public release",
                                "Internal communications, process documentation, SOPs"),
                        List.of("Team meeting minutes", "Internal process guide", "Market research report")),
                sensitivityDef("CONFIDENTIAL", "Confidential", "Restricted to named roles/teams — requires need-to-know access", 2, "amber",
                        List.of("Financial data, employee records, client contracts, strategy docs",
                                "Information that could cause harm if disclosed to unauthorised parties",
                                "Legal advice, board papers, M&A documentation, audit findings"),
                        List.of("Client contract", "Financial forecast", "Legal opinion letter", "Board minutes")),
                sensitivityDef("RESTRICTED", "Restricted", "Highest sensitivity — legal, PII, regulatory", 3, "red",
                        List.of("PII (personal identifiable information), health records, legal privilege",
                                "Trade secrets, regulatory filings, whistleblower reports",
                                "Information whose disclosure would cause severe harm or regulatory breach",
                                "When in doubt, assign this level — over-classification is safer than under-classification"),
                        List.of("Employee payroll records", "Patient health data", "Litigation correspondence", "Trade secret documentation"))
        );

        sensitivityRepo.saveAll(defs);
        log.info("Seeded {} sensitivity definitions.", defs.size());
    }

    private SensitivityDefinition sensitivityDef(String key, String displayName, String description,
                                                  int level, String colour, List<String> guidelines, List<String> examples) {
        SensitivityDefinition def = new SensitivityDefinition();
        def.setKey(key);
        def.setDisplayName(displayName);
        def.setDescription(description);
        def.setLevel(level);
        def.setColour(colour);
        def.setActive(true);
        def.setGuidelines(guidelines);
        def.setExamples(examples);
        return def;
    }

    // ── Retention Schedules ────────────────────────────────

    private Map<String, String> seedRetentionSchedules(Map<String, String> legislationIds) {
        if (retentionRepo.count() > 0) {
            log.info("Retention schedules already seeded, skipping.");
            return retentionRepo.findAll().stream()
                    .collect(Collectors.toMap(RetentionSchedule::getName, RetentionSchedule::getId));
        }

        List<RetentionSchedule> schedules = List.of(
                retention("Short-Term",
                        "Documents with no regulatory requirement — deleted after 1 year",
                        365, "P1Y", "DATE_CREATED", DispositionAction.DELETE, false,
                        "Internal policy", "UK",
                        List.of(), legislationIds),
                retention("Standard",
                        "General business records — archived after 7 years per Companies Act 2006",
                        2555, "P7Y", "DATE_CREATED", DispositionAction.ARCHIVE, false,
                        "Companies Act 2006", "UK",
                        List.of("COMPANIES_ACT_2006"), legislationIds),
                retention("Financial Statutory",
                        "Financial and tax records — reviewed after 7 years per HMRC requirements",
                        2555, "P7Y", "END_OF_FINANCIAL_YEAR", DispositionAction.REVIEW, true,
                        "HMRC requirements, Companies Act 2006 s.386", "UK",
                        List.of("HMRC_RECORD_KEEPING", "COMPANIES_ACT_2006"), legislationIds),
                retention("Regulatory Extended",
                        "Regulated records — reviewed after 10 years per FCA/GDPR requirements",
                        3650, "P10Y", "DATE_CREATED", DispositionAction.REVIEW, true,
                        "FCA SYSC 9, GDPR Art.17 derogations", "UK",
                        List.of("FCA_SYSC_9", "LIMITATION_ACT_1980"), legislationIds),
                retention("Permanent",
                        "Records that must be retained indefinitely — corporate constitution, board minutes",
                        36500, "PERMANENT", null, DispositionAction.PERMANENT, true,
                        "Legal requirement, corporate constitution", "UK",
                        List.of("COMPANIES_ACT_2006"), legislationIds)
        );

        List<RetentionSchedule> saved = retentionRepo.saveAll(schedules);
        log.info("Seeded {} retention schedules.", saved.size());
        return saved.stream().collect(Collectors.toMap(RetentionSchedule::getName, RetentionSchedule::getId));
    }

    private RetentionSchedule retention(String name, String desc, int days,
                                        String duration, String trigger,
                                        DispositionAction action, boolean legalHold, String basis,
                                        String jurisdiction,
                                        List<String> legislationKeys, Map<String, String> legislationIds) {
        RetentionSchedule rs = new RetentionSchedule();
        rs.setName(name);
        rs.setDescription(desc);
        rs.setRetentionDays(days);
        rs.setRetentionDuration(duration);
        rs.setRetentionTrigger(trigger);
        rs.setDispositionAction(action);
        rs.setLegalHoldOverride(legalHold);
        rs.setRegulatoryBasis(basis);
        rs.setJurisdiction(jurisdiction);
        rs.setLegislationIds(legislationKeys.stream()
                .map(legislationIds::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList()));
        return rs;
    }

    // ── Storage Tiers ──────────────────────────────────────

    private void seedStorageTiers() {
        if (storageTierRepo.count() > 0) {
            log.info("Storage tiers already seeded, skipping.");
            return;
        }

        List<StorageTier> tiers = List.of(
                storageTier("Public Store",
                        "Standard storage for public-facing documents with basic encryption",
                        "AES-128", false, false, null,
                        List.of(SensitivityLabel.PUBLIC),
                        500_000_000L, 0.01),
                storageTier("Internal Store",
                        "Encrypted storage for internal business documents",
                        "AES-256", false, false, "eu-west-2",
                        List.of(SensitivityLabel.PUBLIC, SensitivityLabel.INTERNAL),
                        1_000_000_000L, 0.03),
                storageTier("Confidential Store",
                        "Immutable encrypted storage for confidential documents — UK data residency",
                        "AES-256", true, true, "eu-west-2",
                        List.of(SensitivityLabel.CONFIDENTIAL),
                        2_000_000_000L, 0.08),
                storageTier("Restricted Vault",
                        "Maximum-security immutable vault for restricted documents — UK data residency, audit logging",
                        "AES-256-GCM", true, true, "eu-west-2",
                        List.of(SensitivityLabel.RESTRICTED),
                        5_000_000_000L, 0.15)
        );

        storageTierRepo.saveAll(tiers);
        log.info("Seeded {} storage tiers.", tiers.size());
    }

    private StorageTier storageTier(String name, String desc, String encryption,
                                    boolean immutable, boolean geoRestricted, String region,
                                    List<SensitivityLabel> sensitivities, long maxSize, double cost) {
        StorageTier st = new StorageTier();
        st.setName(name);
        st.setDescription(desc);
        st.setEncryptionType(encryption);
        st.setImmutable(immutable);
        st.setGeographicallyRestricted(geoRestricted);
        st.setRegion(region);
        st.setAllowedSensitivities(sensitivities);
        st.setMaxFileSizeBytes(maxSize);
        st.setCostPerGbMonth(cost);
        return st;
    }

    // ── Classification Categories ──────────────────────────

    private Map<String, String> seedCategories(Map<String, String> retentionIds, Map<String, String> schemaIds) {
        // Classification categories are no longer seeded by the platform — the
        // Governance Hub is the source of truth. Admins import them via packs at
        // /governance/hub. Returning the existing map keeps any hub-imported
        // categories visible to dependent seeders (e.g. policies).
        log.info("Classification category seeding is disabled — taxonomies come from hub packs. " +
                "Existing categories: {}", categoryRepo.count());
        return categoryRepo.findAll().stream()
                .collect(Collectors.toMap(ClassificationCategory::getName, ClassificationCategory::getId, (a, b) -> a));

        /* Original UK seed data left in place but unreachable — kept for reference
           in case anyone wants to restore the platform-default starter taxonomy.
        Map<String, String> ids = new HashMap<>();

        // Root categories (FUNCTION level)
        ids.putAll(saveCategories(List.of(
                category("LEG", "Legal",
                        "Legal documents, correspondence, and advice",
                        null, TaxonomyLevel.FUNCTION, SensitivityLabel.CONFIDENTIAL, retentionIds.get("Regulatory Extended"),
                        List.of("contract", "agreement", "litigation", "court", "solicitor", "barrister", "legal opinion", "NDA"),
                        null, "UK", null,
                        null, null, false, true,
                        "Includes: all legal correspondence, contracts, court documents. Excludes: compliance/regulatory (see COM)."),
                category("FIN", "Finance",
                        "Financial records, accounting, and tax documentation",
                        null, TaxonomyLevel.FUNCTION, SensitivityLabel.CONFIDENTIAL, retentionIds.get("Financial Statutory"),
                        List.of("invoice", "receipt", "budget", "P&L", "balance sheet", "accounts", "tax", "HMRC", "VAT"),
                        null, "UK", null,
                        RetentionTrigger.END_OF_FINANCIAL_YEAR, null, false, true,
                        "Includes: all financial transactions, budgets, forecasts."),
                category("HR", "HR",
                        "Human resources, personnel records, and employee management",
                        null, TaxonomyLevel.FUNCTION, SensitivityLabel.RESTRICTED, retentionIds.get("Standard"),
                        List.of("employee", "payroll", "disciplinary", "grievance", "recruitment", "appraisal", "absence"),
                        null, "UK", null,
                        null, "DPA 2018", true, false,
                        "Includes: all employee-related records."),
                category("OPS", "Operations",
                        "Operational procedures, workflows, and logistics documentation",
                        null, TaxonomyLevel.FUNCTION, SensitivityLabel.INTERNAL, retentionIds.get("Standard"),
                        List.of("procedure", "SOP", "process", "workflow", "logistics", "supply chain", "facilities"),
                        null, "UK", null,
                        RetentionTrigger.SUPERSEDED, null, false, false,
                        "Includes: SOPs, project documentation, process maps."),
                category("COM", "Compliance",
                        "Regulatory compliance, audits, and risk management",
                        null, TaxonomyLevel.FUNCTION, SensitivityLabel.CONFIDENTIAL, retentionIds.get("Regulatory Extended"),
                        List.of("audit", "regulation", "FCA", "ICO", "risk assessment", "DPA", "GDPR", "compliance"),
                        null, "UK", null,
                        null, null, false, true,
                        "Includes: audit reports, risk registers, regulatory correspondence."),
                category("IT", "IT",
                        "Information technology documentation, architecture, and security",
                        null, TaxonomyLevel.FUNCTION, SensitivityLabel.INTERNAL, retentionIds.get("Short-Term"),
                        List.of("system", "network", "architecture", "security", "incident", "change request", "SLA"),
                        null, "UK", null,
                        null, null, false, false,
                        "Includes: system documentation, change records, architecture diagrams."),
                category("MKT", "Marketing",
                        "Marketing materials, campaigns, and external communications",
                        null, TaxonomyLevel.FUNCTION, SensitivityLabel.PUBLIC, retentionIds.get("Short-Term"),
                        List.of("campaign", "brochure", "press release", "social media", "brand", "event", "newsletter"),
                        null, "UK", null,
                        RetentionTrigger.SUPERSEDED, null, false, false,
                        "Includes: campaign briefs, press releases, brand guidelines.")
        )));

        // Child categories (ACTIVITY level)
        ids.putAll(saveCategories(List.of(
                category("LEG-CON", "Contracts",
                        "Commercial agreements, NDAs, service contracts, and leases",
                        ids.get("Legal"), TaxonomyLevel.ACTIVITY, SensitivityLabel.CONFIDENTIAL, retentionIds.get("Regulatory Extended"),
                        List.of("NDA", "service agreement", "SLA", "terms", "lease", "employment contract", "vendor agreement"),
                        null, "UK", null,
                        RetentionTrigger.DATE_CLOSED, "Limitation Act 1980 s.5", false, false,
                        "Includes: service agreements, NDAs, employment contracts. Excludes: purchase orders (see FIN-IR)."),
                category("LEG-SA", "Service Agreements",
                        "Service level agreements, outsourcing contracts, and managed service terms",
                        ids.get("Legal"), TaxonomyLevel.ACTIVITY, SensitivityLabel.CONFIDENTIAL, retentionIds.get("Regulatory Extended"),
                        List.of("SLA", "service agreement", "managed service", "outsourcing", "MSA", "statement of work"),
                        null, "UK", null,
                        RetentionTrigger.DATE_CLOSED, "Limitation Act 1980 s.5", false, false,
                        "Includes: SLAs, MSAs, outsourcing contracts, statements of work."),
                category("LEG-LIT", "Litigation",
                        "Disputes, court proceedings, tribunal records, and legal privilege",
                        ids.get("Legal"), TaxonomyLevel.ACTIVITY, SensitivityLabel.RESTRICTED, retentionIds.get("Permanent"),
                        List.of("claim", "dispute", "court order", "tribunal", "settlement", "counsel advice", "legal privilege"),
                        null, "UK", null,
                        RetentionTrigger.DATE_CLOSED, "Limitation Act 1980", false, true,
                        "Includes: court filings, correspondence with solicitors, settlement agreements."),
                category("FIN-IR", "Invoices & Receipts",
                        "Invoices, purchase orders, credit notes, expense claims, and VAT receipts",
                        ids.get("Finance"), TaxonomyLevel.ACTIVITY, SensitivityLabel.INTERNAL, retentionIds.get("Financial Statutory"),
                        List.of("invoice", "purchase order", "credit note", "expense claim", "VAT receipt", "payment", "bill"),
                        null, "UK", null,
                        RetentionTrigger.END_OF_FINANCIAL_YEAR, "HMRC Record Keeping Requirements", false, false,
                        "Includes: purchase invoices, sales invoices, expense receipts."),
                category("HR-PAY", "Payroll",
                        "Salary records, P45/P60, pensions, and PAYE documentation",
                        ids.get("HR"), TaxonomyLevel.ACTIVITY, SensitivityLabel.RESTRICTED, retentionIds.get("Financial Statutory"),
                        List.of("salary", "P45", "P60", "pension", "national insurance", "PAYE", "payslip"),
                        null, "UK", null,
                        RetentionTrigger.END_OF_FINANCIAL_YEAR, "HMRC: payroll records 3 years after end of tax year", true, false,
                        "Includes: P45s, P60s, pension contributions, salary records."),
                category("HR-EMP", "Employee Records",
                        "Personnel files, employment contracts, and employee documentation",
                        ids.get("HR"), TaxonomyLevel.ACTIVITY, SensitivityLabel.RESTRICTED, retentionIds.get("Standard"),
                        List.of("personnel", "employee file", "employment contract", "starter form", "leaver form", "employee record"),
                        null, "UK", null,
                        RetentionTrigger.DATE_CLOSED, "DPA 2018 Schedule 1", true, false,
                        "Includes: employment contracts, disciplinary records, grievances. Excludes: recruitment (see HR-REC)."),
                category("HR-LET", "HR Letters",
                        "Leave confirmations, disciplinary letters, reference letters, and HR correspondence",
                        ids.get("HR"), TaxonomyLevel.ACTIVITY, SensitivityLabel.CONFIDENTIAL, retentionIds.get("Standard"),
                        List.of("leave confirmation", "maternity", "paternity", "disciplinary letter", "reference letter", "HR letter", "absence"),
                        null, "UK", null,
                        RetentionTrigger.DATE_CREATED, "DPA 2018", true, false,
                        "Includes: leave confirmations, disciplinary letters, reference letters."),
                category("HR-REC", "Recruitment",
                        "CVs, job descriptions, offer letters, interviews, and references",
                        ids.get("HR"), TaxonomyLevel.ACTIVITY, SensitivityLabel.CONFIDENTIAL, retentionIds.get("Standard"),
                        List.of("CV", "job description", "offer letter", "interview", "reference", "candidate"),
                        null, "UK", null,
                        RetentionTrigger.EVENT_BASED, "GDPR Art.17", true, false,
                        "Includes: CVs, application forms, interview notes. Trigger: position filled or application withdrawn."),
                category("COM-AUD", "Audit Reports",
                        "Internal and external audit findings, ISO compliance, and remediation",
                        ids.get("Compliance"), TaxonomyLevel.ACTIVITY, SensitivityLabel.CONFIDENTIAL, retentionIds.get("Regulatory Extended"),
                        List.of("internal audit", "external audit", "findings", "remediation", "ISO", "certification"),
                        null, "UK", null,
                        RetentionTrigger.DATE_CREATED, "Companies Act 2006", false, false,
                        "Includes: internal audit reports, external audit findings, management responses."),
                category("IT-SEC", "Security Incidents",
                        "Breach reports, vulnerability assessments, penetration tests, and forensics",
                        ids.get("IT"), TaxonomyLevel.ACTIVITY, SensitivityLabel.RESTRICTED, retentionIds.get("Regulatory Extended"),
                        List.of("breach", "vulnerability", "penetration test", "SIEM", "forensic", "incident response"),
                        null, "UK", null,
                        RetentionTrigger.DATE_CLOSED, "GDPR Art.33", true, false,
                        "Includes: incident reports, breach notifications, forensic reports."),
                category("MKT-PM", "Press & Media",
                        "Press releases, media enquiries, and PR materials",
                        ids.get("Marketing"), TaxonomyLevel.ACTIVITY, SensitivityLabel.PUBLIC, retentionIds.get("Short-Term"),
                        List.of("press release", "media enquiry", "spokesperson brief", "PR", "public statement"),
                        null, "UK", null,
                        RetentionTrigger.SUPERSEDED, null, false, false,
                        "Includes: press releases, media responses, PR materials.")
        )));

        // Link categories to metadata schemas
        linkCategoryToSchema(ids, "Employee Records", schemaIds, "HR Leave Request");
        linkCategoryToSchema(ids, "HR Letters", schemaIds, "HR Leave Request");
        linkCategoryToSchema(ids, "Contracts", schemaIds, "Contract / Agreement");
        linkCategoryToSchema(ids, "Service Agreements", schemaIds, "Contract / Agreement");
        linkCategoryToSchema(ids, "Invoices & Receipts", schemaIds, "Invoice");

        log.info("Seeded {} classification categories.", ids.size());
        return ids;
        */
    }

    private void linkCategoryToSchema(Map<String, String> categoryIds, String categoryName,
                                      Map<String, String> schemaIds, String schemaName) {
        String categoryId = categoryIds.get(categoryName);
        String schemaId = schemaIds.get(schemaName);
        if (categoryId == null) {
            throw new IllegalStateException("Seeder error: category '" + categoryName + "' not found — cannot link to schema '" + schemaName + "'");
        }
        if (schemaId == null) {
            throw new IllegalStateException("Seeder error: schema '" + schemaName + "' not found — cannot link to category '" + categoryName + "'");
        }
        categoryRepo.findById(categoryId).ifPresent(cat -> {
            cat.setMetadataSchemaId(schemaId);
            categoryRepo.save(cat);
        });
    }

    private Map<String, String> saveCategories(List<ClassificationCategory> categories) {
        List<ClassificationCategory> saved = categoryRepo.saveAll(categories);
        return saved.stream().collect(Collectors.toMap(ClassificationCategory::getName, ClassificationCategory::getId));
    }

    private ClassificationCategory category(String code, String name, String desc, String parentId,
                                            ClassificationCategory.TaxonomyLevel level,
                                            SensitivityLabel sensitivity, String retentionId,
                                            List<String> keywords,
                                            List<String> typicalRecords,
                                            String jurisdiction,
                                            String retentionPeriodText,
                                            ClassificationCategory.RetentionTrigger retentionTrigger,
                                            String legalCitation,
                                            boolean personalData, boolean vitalRecord,
                                            String scopeNotes) {
        ClassificationCategory cat = new ClassificationCategory();
        cat.setClassificationCode(code);
        cat.setName(name);
        cat.setDescription(desc);
        cat.setParentId(parentId);
        cat.setLevel(level);
        cat.setDefaultSensitivity(sensitivity);
        cat.setRetentionScheduleId(retentionId);
        cat.setKeywords(keywords);
        cat.setTypicalRecords(typicalRecords);
        cat.setJurisdiction(jurisdiction);
        cat.setRetentionPeriodText(retentionPeriodText);
        cat.setStatus(ClassificationCategory.NodeStatus.ACTIVE);
        cat.setRetentionTrigger(retentionTrigger);
        cat.setLegalCitation(legalCitation);
        cat.setPersonalDataFlag(personalData);
        cat.setVitalRecordFlag(vitalRecord);
        cat.setScopeNotes(scopeNotes);
        cat.setOwner("System");
        cat.setVersion(1);
        return cat;
    }

    // ── Governance Policies ────────────────────────────────

    private void seedPolicies(Map<String, String> categoryIds, Map<String, String> legislationIds) {
        if (policyRepo.count() > 0) {
            log.info("Governance policies already seeded, skipping.");
            return;
        }

        Instant now = Instant.now();
        Instant tenYears = now.plus(3650, ChronoUnit.DAYS);

        List<GovernancePolicy> policies = List.of(
                policy("UK Data Protection Policy",
                        "GDPR and DPA 2018 compliance requirements for personal data handling",
                        List.of(
                                "Documents containing personal data must be classified CONFIDENTIAL or above",
                                "Personal data must be stored in UK/EEA regions only",
                                "Right to erasure requests override retention schedules unless legal hold applies",
                                "Data subject access requests must be fulfilled within 30 days"
                        ),
                        List.of(),
                        List.of(SensitivityLabel.CONFIDENTIAL, SensitivityLabel.RESTRICTED),
                        Map.of("encryption", "AES-256", "accessReview", "quarterly", "dataResidency", "uk-eea"),
                        now, tenYears,
                        List.of("GDPR", "DPA_2018"), legislationIds),

                policy("Financial Records Retention",
                        "HMRC and Companies Act compliance for financial documentation",
                        List.of(
                                "Financial records must be retained for minimum 7 years",
                                "Tax-related documents must not be deleted without HMRC clearance",
                                "Audit trail must be maintained for all financial document access"
                        ),
                        List.of(categoryIds.get("Finance"), categoryIds.get("Invoices & Receipts")),
                        List.of(SensitivityLabel.INTERNAL, SensitivityLabel.CONFIDENTIAL),
                        Map.of("minimumRetentionYears", "7", "auditTrail", "mandatory", "deletionApproval", "finance-director"),
                        now, tenYears,
                        List.of("HMRC_RECORD_KEEPING", "COMPANIES_ACT_2006"), legislationIds),

                policy("Legal Hold Policy",
                        "Litigation and regulatory hold rules preventing document destruction",
                        List.of(
                                "Documents under legal hold cannot be deleted or modified",
                                "Legal hold overrides all retention schedules",
                                "Legal hold must be reviewed quarterly by General Counsel"
                        ),
                        List.of(categoryIds.get("Legal"), categoryIds.get("Litigation")),
                        List.of(SensitivityLabel.CONFIDENTIAL, SensitivityLabel.RESTRICTED),
                        Map.of("immutable", "true", "accessReview", "quarterly", "holdAuthority", "general-counsel"),
                        now, tenYears,
                        List.of("LIMITATION_ACT_1980"), legislationIds),

                policy("Restricted Access Control",
                        "Access control requirements for highest-sensitivity documents",
                        List.of(
                                "RESTRICTED documents require named-individual access only",
                                "Access must be logged and auditable",
                                "Quarterly access reviews are mandatory",
                                "Documents must be stored in immutable, encrypted storage"
                        ),
                        List.of(),
                        List.of(SensitivityLabel.RESTRICTED),
                        Map.of("accessControl", "named-individual", "auditLogging", "mandatory",
                                "accessReview", "quarterly", "storageType", "immutable-encrypted"),
                        now, tenYears,
                        List.of("GDPR", "DPA_2018"), legislationIds),

                policy("Employee Data Handling",
                        "HR data protection requirements under GDPR and employment law",
                        List.of(
                                "Employee personal data is classified RESTRICTED by default",
                                "Payroll data must be encrypted at rest and in transit",
                                "Recruitment data must be deleted within 6 months of process completion unless consent obtained"
                        ),
                        List.of(categoryIds.get("HR"), categoryIds.get("Payroll"), categoryIds.get("Recruitment")),
                        List.of(SensitivityLabel.CONFIDENTIAL, SensitivityLabel.RESTRICTED),
                        Map.of("encryption", "AES-256", "transitEncryption", "TLS-1.3", "recruitmentRetentionDays", "180"),
                        now, tenYears,
                        List.of("GDPR", "DPA_2018"), legislationIds),

                policy("Public Information Release",
                        "Rules governing external publication and sharing of documents",
                        List.of(
                                "Only documents classified PUBLIC may be shared externally",
                                "Marketing materials must be approved before publication",
                                "Press releases require communications team sign-off"
                        ),
                        List.of(categoryIds.get("Marketing"), categoryIds.get("Press & Media")),
                        List.of(SensitivityLabel.PUBLIC),
                        Map.of("approvalRequired", "true", "approvalAuthority", "communications-lead",
                                "externalSharingAllowed", "true"),
                        now, tenYears,
                        List.of(), legislationIds)
        );

        policyRepo.saveAll(policies);
        log.info("Seeded {} governance policies.", policies.size());
    }

    // ── Default Pipeline ────────────────────────────────

    private void seedDefaultPipeline(Map<String, String> blockIds) {
        if (pipelineRepo.count() > 0) {
            log.info("Pipeline definitions already seeded, skipping.");
            return;
        }

        PipelineDefinition pipeline = new PipelineDefinition();
        pipeline.setName("Document Ingestion Pipeline");
        pipeline.setDescription("Default pipeline: extract text, scan for PII, classify with LLM, enforce governance");
        pipeline.setActive(true);
        pipeline.setCreatedAt(Instant.now());
        pipeline.setUpdatedAt(Instant.now());

        PipelineStep extract = new PipelineStep();
        extract.setOrder(1);
        extract.setName("Text Extraction");
        extract.setDescription("Extract text and Dublin Core metadata from uploaded document using Apache Tika");
        extract.setType(StepType.BUILT_IN);
        extract.setEnabled(true);
        extract.setConfig(Map.of("handler", "TEXT_EXTRACTION"));
        extract.setBlockId(blockIds.get("EXTRACTOR"));

        PipelineStep piiScan = new PipelineStep();
        piiScan.setOrder(2);
        piiScan.setName("PII Scan (Pattern)");
        piiScan.setDescription("Tier 1: Fast regex-based PII detection — NI numbers, emails, phones, postcodes, bank details");
        piiScan.setType(StepType.PATTERN);
        piiScan.setEnabled(true);
        piiScan.setConfig(Map.of("handler", "PII_PATTERN_SCAN"));
        piiScan.setBlockId(blockIds.get("REGEX_SET"));

        PipelineStep classify = new PipelineStep();
        classify.setOrder(3);
        classify.setName("LLM Classification");
        classify.setDescription("Send document to Claude for classification against the governance taxonomy");
        classify.setType(StepType.LLM_PROMPT);
        classify.setEnabled(true);
        classify.setConfig(Map.of("handler", "LLM_CLASSIFICATION"));
        classify.setSystemPrompt("""
                You are a document classification and information governance specialist.
                Your job is to analyse documents and classify them according to the organisation's
                governance framework.

                You have access to tools that let you query the organisation's:
                - Classification taxonomy (document categories)
                - Sensitivity label definitions
                - Governance policies (rules that apply to documents)
                - Retention schedules (how long documents must be kept)
                - Storage capabilities (where documents can be stored)

                For each document you receive, you MUST:

                1. First, retrieve the classification taxonomy and sensitivity definitions
                   to understand the organisation's framework.

                2. Read the document text carefully. Consider:
                   - What type of document is this? (contract, invoice, memo, report, etc.)
                   - Does it contain personally identifiable information (PII)?
                   - Does it contain financial, legal, or health-related information?
                   - Who is the intended audience?

                3. Classify the document into the most specific category in the taxonomy.
                   If no category fits well, use the closest parent category.

                4. Assign a sensitivity label. When uncertain, choose the HIGHER level.

                5. Extract relevant tags and structured metadata (dates, names, amounts, etc.).

                6. Retrieve applicable governance policies and the correct retention schedule.

                7. Save the classification result using the save_classification_result tool.
                   Include your confidence score (0.0-1.0) and detailed reasoning.

                Confidence guidelines:
                - 0.9-1.0: Clear, unambiguous classification
                - 0.7-0.89: High confidence but some ambiguity
                - 0.5-0.69: Moderate confidence — flag for human review
                - Below 0.5: Low confidence — definitely requires human review

                IMPORTANT: Always err on the side of higher sensitivity. A document
                classified as too sensitive is an inconvenience; a document classified
                as not sensitive enough is a compliance risk.
                """);
        classify.setUserPromptTemplate("""
                Please classify the following document.

                **Document ID:** {documentId}
                **File name:** {fileName}
                **MIME type:** {mimeType}
                **File size:** {fileSizeBytes} bytes
                **Uploaded by:** {uploadedBy}

                IMPORTANT: When calling save_classification_result, you MUST use the exact
                Document ID shown above ({documentId}) as the documentId parameter. Do NOT use the
                file name as the document ID.

                ---

                **Document text:**

                {extractedText}
                """);
        classify.setBlockId(blockIds.get("PROMPT"));

        PipelineStep piiVerify = new PipelineStep();
        piiVerify.setOrder(4);
        piiVerify.setName("PII Verification (LLM)");
        piiVerify.setDescription("Tier 2: Send flagged PII to Claude for contextual verification — only runs if Tier 1 found PII");
        piiVerify.setType(StepType.CONDITIONAL);
        piiVerify.setEnabled(false);
        piiVerify.setCondition("piiFindings.size > 0");
        piiVerify.setSystemPrompt("You are a PII verification specialist. Review pattern-matched PII findings and determine if each is genuine personal data or a false positive.");
        piiVerify.setUserPromptTemplate("""
                Review the following PII findings detected by pattern matching in document {documentId}.
                For each finding, determine if it is genuine PII (CONFIRMED) or a false positive (FALSE_POSITIVE).
                Provide a brief reason for each determination.

                **Findings:**
                {piiFindings}

                **Document context (first 5000 chars):**
                {extractedText}
                """);
        piiVerify.setConfig(Map.of("handler", "LLM_PII_VERIFY"));

        PipelineStep enforce = new PipelineStep();
        enforce.setOrder(5);
        enforce.setName("Governance Enforcement");
        enforce.setDescription("Apply retention schedule, storage tier, and policy enforcement based on classification");
        enforce.setType(StepType.BUILT_IN);
        enforce.setEnabled(true);
        enforce.setConfig(Map.of("handler", "GOVERNANCE_ENFORCEMENT"));
        enforce.setBlockId(blockIds.get("ENFORCER"));

        pipeline.setSteps(List.of(extract, piiScan, classify, piiVerify, enforce));
        pipelineRepo.save(pipeline);
        log.info("Seeded default pipeline definition with {} steps.", pipeline.getSteps().size());
    }

    // ── Pipeline Configuration ──────────────────────────

    private void seedPipelineConfig() {
        // Only seed if no pipeline config exists yet
        if (!configService.getByCategory("pipeline").isEmpty()) {
            log.info("Pipeline config already seeded, skipping.");
            return;
        }

        configService.save("pipeline.confidence.review_threshold", "pipeline",
                0.7, "Confidence score below which documents are flagged for human review (0.0-1.0)");

        configService.save("pipeline.confidence.auto_approve_threshold", "pipeline",
                0.95, "Confidence score above which classification is auto-approved without review (0.0-1.0)");

        // Note: pipeline.llm.model removed — use llm.anthropic.model or llm.ollama.model instead

        configService.save("pipeline.llm.temperature", "pipeline",
                0.1, "LLM temperature — lower is more deterministic (0.0-1.0)");

        configService.save("pipeline.llm.max_tokens", "pipeline",
                4096, "Maximum tokens in the LLM classification response");

        configService.save("pipeline.llm.timeout_seconds", "pipeline",
                90, "Legacy global timeout. Per-provider keys below take precedence.");

        configService.save("pipeline.llm.timeout_seconds.anthropic", "pipeline",
                60, "Hard timeout for Anthropic (Claude) classification calls (seconds).");

        configService.save("pipeline.llm.timeout_seconds.ollama", "pipeline",
                240, "Hard timeout for Ollama (local) classification calls (seconds). Local models are slower; set generously.");

        configService.save("pipeline.text.max_length", "pipeline",
                100000, "Maximum characters of document text sent to the LLM");

        configService.save("pipeline.processing.auto_classify", "pipeline",
                true, "Automatically classify documents after text extraction");

        configService.save("pipeline.processing.extract_dublin_core", "pipeline",
                true, "Extract Dublin Core metadata from uploaded files during processing");

        configService.save("pipeline.governance.auto_enforce", "pipeline",
                true, "Automatically apply governance rules after classification");

        // Note: pipeline.system_prompt and pipeline.user_prompt_template removed
        // Prompts are defined in pipeline PROMPT blocks, not in app_config

        log.info("Seeded pipeline configuration.");
    }

    private void seedBertTrainingConfig() {
        if (!configService.getByCategory("bert").isEmpty()) {
            log.info("BERT training config already seeded, skipping.");
            return;
        }

        configService.save("bert.training.auto_collect_enabled", "bert",
                false, "Automatically collect training data from classified documents");
        configService.save("bert.training.auto_collect_min_confidence", "bert",
                0.8, "Minimum classification confidence to auto-collect a training sample (0.0-1.0)");
        configService.save("bert.training.auto_collect_categories", "bert",
                List.of(), "Category IDs to auto-collect from (empty = all categories)");
        configService.save("bert.training.auto_collect_corrected_only", "bert",
                false, "Only collect training data from human-corrected classifications");
        configService.save("bert.training.max_text_length", "bert",
                2000, "Maximum characters per training sample (BERT typically uses 512 tokens ~ 2000 chars)");

        log.info("Seeded BERT training configuration.");
    }

    private GovernancePolicy policy(String name, String desc, List<String> rules,
                                    List<String> categoryIds, List<SensitivityLabel> sensitivities,
                                    Map<String, String> enforcement, Instant from, Instant until,
                                    List<String> legislationKeys, Map<String, String> legislationIdMap) {
        GovernancePolicy p = new GovernancePolicy();
        p.setName(name);
        p.setDescription(desc);
        p.setRules(rules);
        p.setApplicableCategoryIds(categoryIds);
        p.setApplicableSensitivities(sensitivities);
        p.setEnforcementActions(enforcement);
        p.setVersion(1);
        p.setActive(true);
        p.setEffectiveFrom(from);
        p.setEffectiveUntil(until);
        p.setCreatedAt(from);
        p.setCreatedBy("system-seeder");
        p.setLegislationIds(legislationKeys.stream()
                .map(legislationIdMap::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList()));
        return p;
    }

    // ── Metadata Schemas ──────────────────────────────────

    private Map<String, String> seedMetadataSchemas() {
        if (metadataSchemaRepo.count() > 0) {
            log.info("Metadata schemas already seeded, skipping.");
            return metadataSchemaRepo.findAll().stream()
                    .collect(Collectors.toMap(MetadataSchema::getName, MetadataSchema::getId));
        }

        var hrLeave = new MetadataSchema();
        hrLeave.setName("HR Leave Request");
        hrLeave.setDescription("Employee leave request or confirmation letters");
        hrLeave.setExtractionContext("These are typically formal letters from HR to an employee confirming leave arrangements. " +
                "The employee name is usually in the salutation or subject line. Leave dates are stated explicitly. " +
                "The approver or signatory is at the bottom of the letter.");
        hrLeave.setFields(List.of(
                new MetadataSchema.MetadataField("employee_name", MetadataSchema.FieldType.TEXT, true,
                        "Full name of the employee",
                        "Look in the salutation (Dear...), subject line, or first paragraph",
                        List.of("John Smith", "Sarah Jones")),
                new MetadataSchema.MetadataField("leave_type", MetadataSchema.FieldType.KEYWORD, true,
                        "Type of leave",
                        "Usually stated explicitly: maternity, paternity, annual, sick, unpaid, compassionate",
                        List.of("maternity", "paternity", "annual", "sick")),
                new MetadataSchema.MetadataField("start_date", MetadataSchema.FieldType.DATE, true,
                        "Leave start date",
                        "Look for phrases like 'commencing on', 'starting from', 'effective from'",
                        List.of("2026-06-01")),
                new MetadataSchema.MetadataField("end_date", MetadataSchema.FieldType.DATE, true,
                        "Leave end date or expected return date",
                        "Look for 'returning on', 'until', 'ending on'. May be expressed as a duration instead",
                        List.of("2026-09-01")),
                new MetadataSchema.MetadataField("approver", MetadataSchema.FieldType.TEXT, false,
                        "Person who signed/approved the leave",
                        "Usually the signatory at the bottom of the letter, or named in 'approved by' line",
                        List.of("Jane Director, HR Manager")),
                new MetadataSchema.MetadataField("department", MetadataSchema.FieldType.TEXT, false,
                        "Employee's department",
                        "May appear in the header, reference line, or body text",
                        List.of("Engineering", "Marketing"))
        ));
        hrLeave.setActive(true);
        hrLeave.setCreatedAt(Instant.now());
        hrLeave.setUpdatedAt(Instant.now());

        var contract = new MetadataSchema();
        contract.setName("Contract / Agreement");
        contract.setDescription("Legal contracts, service agreements, terms of engagement");
        contract.setExtractionContext("Contracts typically have parties named at the top, an effective date, " +
                "termination/expiry clauses, and a governing law clause near the end. " +
                "The value may be in a schedule or payment terms section. " +
                "Parties are often defined in the first paragraph using terms like 'between' and 'and'.");
        contract.setFields(List.of(
                new MetadataSchema.MetadataField("parties", MetadataSchema.FieldType.TEXT, true,
                        "Names of all parties to the contract",
                        "Usually in the opening paragraph: 'This agreement is between [Party A] and [Party B]'",
                        List.of("Acme Ltd and Beta Corp")),
                new MetadataSchema.MetadataField("effective_date", MetadataSchema.FieldType.DATE, true,
                        "Date the contract takes effect",
                        "Look for 'effective as of', 'dated', 'commences on'. Often near the top of the document",
                        List.of("2026-01-01")),
                new MetadataSchema.MetadataField("expiry_date", MetadataSchema.FieldType.DATE, false,
                        "Date the contract expires",
                        "Look in termination/duration clauses. May say 'for a period of X years' instead of a fixed date",
                        List.of("2027-01-01")),
                new MetadataSchema.MetadataField("contract_value", MetadataSchema.FieldType.CURRENCY, false,
                        "Total value or consideration",
                        "Often in a payment/fees section or schedule. Include currency symbol",
                        List.of("£50,000", "$120,000")),
                new MetadataSchema.MetadataField("governing_law", MetadataSchema.FieldType.KEYWORD, false,
                        "Legal jurisdiction",
                        "Usually in a 'governing law' or 'jurisdiction' clause near the end",
                        List.of("England and Wales", "Scotland"))
        ));
        contract.setActive(true);
        contract.setCreatedAt(Instant.now());
        contract.setUpdatedAt(Instant.now());

        var invoice = new MetadataSchema();
        invoice.setName("Invoice");
        invoice.setDescription("Invoices, bills, payment requests from suppliers or vendors");
        invoice.setExtractionContext("Invoices have a standard structure: vendor details at the top, " +
                "invoice number and date in a header block, line items in a table, " +
                "and a total amount at the bottom. The 'from' party is the vendor/supplier, " +
                "the 'to' party is the customer being billed. Bank details and payment terms are usually at the bottom.");
        invoice.setFields(List.of(
                new MetadataSchema.MetadataField("from_company", MetadataSchema.FieldType.TEXT, true,
                        "Company or person issuing the invoice",
                        "The vendor/supplier name, usually prominent at the top or in a letterhead",
                        List.of("Edward Harris Design Ltd", "Acme Supplies")),
                new MetadataSchema.MetadataField("to_company", MetadataSchema.FieldType.TEXT, true,
                        "Company or person being invoiced",
                        "Look for 'Bill to', 'Invoice to', or 'To:' sections",
                        List.of("Fractional Exchange Ltd", "Beta Corp")),
                new MetadataSchema.MetadataField("invoice_number", MetadataSchema.FieldType.KEYWORD, true,
                        "Invoice reference number",
                        "Usually labelled 'Invoice No', 'Invoice #', or 'Ref:' near the top",
                        List.of("INV-1085", "2026/001")),
                new MetadataSchema.MetadataField("invoice_date", MetadataSchema.FieldType.DATE, true,
                        "Date the invoice was issued",
                        "Usually labelled 'Date' or 'Invoice Date' near the invoice number",
                        List.of("2026-03-15")),
                new MetadataSchema.MetadataField("total_amount", MetadataSchema.FieldType.CURRENCY, true,
                        "Total amount due including VAT/tax",
                        "Look for 'Total', 'Amount Due', 'Balance Due' — usually the last/largest number. Include currency symbol",
                        List.of("£1,500.00", "$3,200.00")),
                new MetadataSchema.MetadataField("net_amount", MetadataSchema.FieldType.CURRENCY, false,
                        "Subtotal before VAT/tax",
                        "Look for 'Subtotal', 'Net', or the sum before tax lines",
                        List.of("£1,250.00")),
                new MetadataSchema.MetadataField("vat_amount", MetadataSchema.FieldType.CURRENCY, false,
                        "VAT or tax amount",
                        "Look for 'VAT', 'Tax', usually between subtotal and total",
                        List.of("£250.00")),
                new MetadataSchema.MetadataField("due_date", MetadataSchema.FieldType.DATE, false,
                        "Payment due date",
                        "Look for 'Due Date', 'Payment Due', 'Due by'. May be expressed as 'Net 30' terms instead",
                        List.of("2026-04-15")),
                new MetadataSchema.MetadataField("description", MetadataSchema.FieldType.TEXT, false,
                        "Brief description of what the invoice is for",
                        "Summarise the line items or service description in one sentence",
                        List.of("Web design services for March 2026", "Office supplies Q1"))
        ));
        invoice.setActive(true);
        invoice.setCreatedAt(Instant.now());
        invoice.setUpdatedAt(Instant.now());

        List<MetadataSchema> saved = metadataSchemaRepo.saveAll(List.of(hrLeave, contract, invoice));
        log.info("Seeded {} metadata schemas.", saved.size());
        return saved.stream().collect(Collectors.toMap(MetadataSchema::getName, MetadataSchema::getId));
    }

    // ── Pipeline Blocks ──────────────────────────────────

    private Map<String, String> seedPipelineBlocks() {
        if (blockRepo.count() > 0) {
            log.info("Pipeline blocks already seeded, returning existing block IDs.");
            Map<String, String> existing = new HashMap<>();
            for (PipelineBlock block : blockRepo.findByActiveTrueOrderByNameAsc()) {
                existing.put(block.getType().name(), block.getId());
            }
            return existing;
        }

        // Classification Prompt Block
        var classPrompt = new PipelineBlock();
        classPrompt.setName("Classification Prompt");
        classPrompt.setDescription("System and user prompts for LLM document classification");
        classPrompt.setType(PipelineBlock.BlockType.PROMPT);
        classPrompt.setActive(true);
        classPrompt.setCreatedAt(Instant.now());
        classPrompt.setUpdatedAt(Instant.now());
        classPrompt.setCreatedBy("system-seeder");
        classPrompt.setVersions(List.of(new PipelineBlock.BlockVersion(1,
                Map.of(
                    "systemPrompt", "You are a document classification and information governance specialist.\n" +
                            "Your job is to analyse documents and classify them according to the organisation's governance framework.\n\n" +
                            "You MUST follow this workflow in order:\n" +
                            "1. Call get_classification_taxonomy to retrieve the category hierarchy\n" +
                            "2. Call get_sensitivity_definitions to understand sensitivity levels\n" +
                            "3. Call get_correction_history with the likely category and document MIME type\n" +
                            "4. Call get_org_pii_patterns to check for organisation-specific PII types and known false positives\n" +
                            "5. Call get_governance_policies if relevant policies might affect classification\n" +
                            "6. Analyse the document text against the taxonomy, corrections, and PII patterns\n" +
                            "7. Call get_metadata_schemas with the category ID you've chosen\n" +
                            "8. Call save_classification_result with your decision\n\n" +
                            "When corrections exist for a category, weight them heavily.\n\n" +
                            "METADATA EXTRACTION RULES:\n" +
                            "- Only extract fields defined in the metadata schema for the matched category\n" +
                            "- If no schema exists, do NOT include extractedMetadata\n" +
                            "- For required fields you cannot find, set value to \"NOT_FOUND\"\n" +
                            "- Be precise: dates ISO, amounts with currency, full names\n\n" +
                            "================= CRITICAL OUTPUT REQUIREMENT =================\n" +
                            "Your final action MUST be a call to save_classification_result.\n" +
                            "Do NOT write a prose conclusion. Do NOT explain your decision in text.\n" +
                            "The categoryId you pass MUST come from get_classification_taxonomy — never invent one.\n" +
                            "If you cannot determine a confident category, still call save_classification_result\n" +
                            "with the closest applicable category and a low confidence score (e.g. 0.3).\n" +
                            "A response without a save_classification_result tool call is a failed classification\n" +
                            "and the document will be marked as failed. Always end with the tool call.",
                    "userPromptTemplate", "Please classify the following document.\n\n" +
                            "**Document ID:** {documentId}\n" +
                            "**File name:** {fileName}\n" +
                            "**MIME type:** {mimeType}\n" +
                            "**File size:** {fileSizeBytes} bytes\n" +
                            "**Uploaded by:** {uploadedBy}\n\n" +
                            "IMPORTANT: Use the exact Document ID above when calling save_classification_result.\n\n" +
                            "---\n\n**Document text:**\n\n{extractedText}",
                    "model", "claude-sonnet-4-20250514",
                    "temperature", "0.1",
                    "maxTokens", "4096"
                ),
                "Initial classification prompt", "system-seeder", Instant.now())));
        classPrompt.setActiveVersion(1);

        // PII Pattern Scanner Block
        var piiRegex = new PipelineBlock();
        piiRegex.setName("UK PII Pattern Scanner");
        piiRegex.setDescription("Regex-based PII detection for UK document patterns — NI numbers, postcodes, bank details, etc.");
        piiRegex.setType(PipelineBlock.BlockType.REGEX_SET);
        piiRegex.setActive(true);
        piiRegex.setCreatedAt(Instant.now());
        piiRegex.setUpdatedAt(Instant.now());
        piiRegex.setCreatedBy("system-seeder");
        piiRegex.setVersions(List.of(new PipelineBlock.BlockVersion(1,
                Map.of("patterns", List.of(
                        Map.of("name", "UK National Insurance", "type", "NATIONAL_INSURANCE",
                                "regex", "\\b[A-CEGHJ-PR-TW-Z]{2}\\s?\\d{2}\\s?\\d{2}\\s?\\d{2}\\s?[A-D]\\b",
                                "confidence", 0.95, "flags", "CASE_INSENSITIVE"),
                        Map.of("name", "Email Address", "type", "EMAIL",
                                "regex", "\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b",
                                "confidence", 0.95),
                        Map.of("name", "UK Phone Number", "type", "PHONE_UK",
                                "regex", "\\b(?:(?:\\+44\\s?|0)(?:\\d\\s?){9,10})\\b",
                                "confidence", 0.85),
                        Map.of("name", "UK Postcode", "type", "POSTCODE_UK",
                                "regex", "\\b[A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2}\\b",
                                "confidence", 0.8, "flags", "CASE_INSENSITIVE"),
                        Map.of("name", "UK Sort Code", "type", "SORT_CODE",
                                "regex", "\\b\\d{2}[\\-\\s]\\d{2}[\\-\\s]\\d{2}\\b",
                                "confidence", 0.85),
                        Map.of("name", "Date of Birth", "type", "DATE_OF_BIRTH",
                                "regex", "(?i)(?:d\\.?o\\.?b\\.?|date\\s+of\\s+birth|born)[:\\s]+\\d{1,2}[/\\-\\.]\\d{1,2}[/\\-\\.]\\d{2,4}",
                                "confidence", 0.9),
                        Map.of("name", "Credit/Debit Card", "type", "CREDIT_CARD",
                                "regex", "\\b(?:\\d{4}[\\s\\-]?){3,4}\\d{1,4}\\b",
                                "confidence", 0.7),
                        Map.of("name", "VAT Number", "type", "VAT_NUMBER",
                                "regex", "(?i)(?:GB)?\\s?\\d{3}\\s?\\d{4}\\s?\\d{2}\\b",
                                "confidence", 0.6)
                )),
                "Initial UK PII regex patterns", "system-seeder", Instant.now())));
        piiRegex.setActiveVersion(1);

        // Text Extractor Block
        var extractor = new PipelineBlock();
        extractor.setName("Tika Text Extractor");
        extractor.setDescription("Apache Tika-based text extraction with Dublin Core metadata parsing");
        extractor.setType(PipelineBlock.BlockType.EXTRACTOR);
        extractor.setActive(true);
        extractor.setCreatedAt(Instant.now());
        extractor.setUpdatedAt(Instant.now());
        extractor.setCreatedBy("system-seeder");
        extractor.setVersions(List.of(new PipelineBlock.BlockVersion(1,
                Map.of(
                    "extractorType", "TIKA",
                    "maxTextLength", 500000,
                    "extractDublinCore", true,
                    "extractMetadata", true
                ),
                "Initial Tika extractor config", "system-seeder", Instant.now())));
        extractor.setActiveVersion(1);

        // Confidence Router Block
        var router = new PipelineBlock();
        router.setName("Confidence Router");
        router.setDescription("Routes documents to human review if classification confidence is below threshold");
        router.setType(PipelineBlock.BlockType.ROUTER);
        router.setActive(true);
        router.setCreatedAt(Instant.now());
        router.setUpdatedAt(Instant.now());
        router.setCreatedBy("system-seeder");
        router.setVersions(List.of(new PipelineBlock.BlockVersion(1,
                Map.of(
                    "condition", "confidence",
                    "operator", "LESS_THAN",
                    "threshold", 0.7,
                    "trueOutput", "human_review",
                    "falseOutput", "auto_approve"
                ),
                "Initial confidence threshold router", "system-seeder", Instant.now())));
        router.setActiveVersion(1);

        // Governance Enforcer Block
        var enforcer = new PipelineBlock();
        enforcer.setName("Standard Governance Enforcer");
        enforcer.setDescription("Applies retention schedules, storage tier migration, and policy enforcement");
        enforcer.setType(PipelineBlock.BlockType.ENFORCER);
        enforcer.setActive(true);
        enforcer.setCreatedAt(Instant.now());
        enforcer.setUpdatedAt(Instant.now());
        enforcer.setCreatedBy("system-seeder");
        enforcer.setVersions(List.of(new PipelineBlock.BlockVersion(1,
                Map.of(
                    "applyRetention", true,
                    "migrateStorageTier", true,
                    "enforcePolicies", true,
                    "autoArchiveOnExpiry", false
                ),
                "Initial governance enforcement config", "system-seeder", Instant.now())));
        enforcer.setActiveVersion(1);

        blockRepo.saveAll(List.of(classPrompt, piiRegex, extractor, router, enforcer));
        log.info("Seeded 5 pipeline blocks");

        Map<String, String> blockIds = new HashMap<>();
        blockIds.put("PROMPT", classPrompt.getId());
        blockIds.put("REGEX_SET", piiRegex.getId());
        blockIds.put("EXTRACTOR", extractor.getId());
        blockIds.put("ROUTER", router.getId());
        blockIds.put("ENFORCER", enforcer.getId());
        return blockIds;
    }

    // ── PII Type Definitions ──────────────────────────────

    private void seedPiiTypeDefinitions() {
        if (piiTypeRepo.count() > 0) {
            log.info("PII type definitions already seeded, skipping.");
            return;
        }

        List<PiiTypeDefinition> types = List.of(
                piiType("NATIONAL_INSURANCE", "National Insurance Number", "UK NI number (XX 99 99 99 X)", "identity"),
                piiType("NHS_NUMBER", "NHS Number", "UK NHS number (999 999 9999)", "medical"),
                piiType("EMAIL", "Email Address", "Personal or work email address", "contact"),
                piiType("PHONE_UK", "UK Phone Number", "UK landline or mobile number", "contact"),
                piiType("PHONE", "Phone Number", "International phone number", "contact"),
                piiType("POSTCODE_UK", "UK Postcode", "UK postal code", "contact"),
                piiType("SORT_CODE", "Sort Code", "UK bank sort code (99-99-99)", "financial"),
                piiType("BANK_ACCOUNT", "Bank Account Number", "UK bank account number (8 digits)", "financial"),
                piiType("CREDIT_CARD", "Credit/Debit Card", "Payment card number", "financial"),
                piiType("DATE_OF_BIRTH", "Date of Birth", "Personal date of birth", "identity"),
                piiType("PASSPORT", "Passport Number", "UK passport number (9 digits)", "identity"),
                piiType("DRIVING_LICENCE", "Driving Licence", "UK driving licence number", "identity"),
                piiType("IBAN", "IBAN", "International Bank Account Number", "financial"),
                piiType("VAT_NUMBER", "VAT Number", "UK VAT registration number", "financial"),
                piiType("COMPANY_REG", "Company Registration", "UK company registration number", "financial"),
                piiType("EMPLOYEE_ID", "Employee ID", "Internal employee identifier", "employment"),
                piiType("STAFF_NUMBER", "Staff Number", "Internal staff reference number", "employment"),
                piiType("PROJECT_CODE", "Project Code", "Internal project reference code", "employment"),
                piiType("REFERENCE_NUMBER", "Reference Number", "Internal reference or case number", "identity"),
                piiType("ADDRESS", "Address", "Residential or postal address", "contact"),
                piiType("SALARY", "Salary Information", "Pay, salary, or compensation details", "financial"),
                piiType("MEDICAL_INFO", "Medical Information", "Health records, diagnoses, prescriptions", "medical"),
                piiType("DISCIPLINARY", "Disciplinary Record", "Employee disciplinary actions or warnings", "employment"),
                piiType("PERFORMANCE_REVIEW", "Performance Review", "Employee performance evaluations", "employment"),
                piiType("BANK_DETAILS", "Bank Details", "Combined bank information (sort code + account)", "financial")
        );

        piiTypeRepo.saveAll(types);
        log.info("Seeded {} PII type definitions", types.size());
    }

    private PiiTypeDefinition piiType(String key, String displayName, String description, String category) {
        PiiTypeDefinition def = new PiiTypeDefinition();
        def.setKey(key);
        def.setDisplayName(displayName);
        def.setDescription(description);
        def.setCategory(category);
        def.setActive(true);
        def.setApprovalStatus(ApprovalStatus.APPROVED);
        def.setExamples(List.of());
        return def;
    }

    // ── Trait Definitions ──────────────────────────────────

    private void seedTraitDefinitions() {
        if (traitRepo.count() > 0) {
            log.info("Trait definitions already seeded, skipping.");
            return;
        }

        List<TraitDefinition> traits = List.of(
                trait("TEMPLATE", "Template", "A blank template with placeholder fields, not a filled-in document",
                        "COMPLETENESS",
                        "Look for placeholder patterns like {Name}, [Insert Date], [COMPANY], <<field>>, or form fields marked with underscores/brackets. Templates often have instructions like 'complete this section'.",
                        List.of("{", "[Insert", "<<", "__________", "complete this", "fill in"),
                        true),
                trait("DRAFT", "Draft", "A work-in-progress document, not yet finalised or approved",
                        "COMPLETENESS",
                        "Look for 'DRAFT' watermarks, 'draft' in the filename or header, version numbers like 'v0.1', 'for review', or 'not for distribution'.",
                        List.of("DRAFT", "draft", "v0.", "for review", "not final", "work in progress"),
                        false),
                trait("FINAL", "Final", "A completed, approved document",
                        "COMPLETENESS",
                        "Look for 'final' in the name/header, signatures, dates of execution, or absence of draft markers. Most business documents without draft indicators are final.",
                        List.of("final", "approved", "signed", "executed"),
                        false),
                trait("SIGNED", "Signed", "A document bearing signatures (physical or electronic)",
                        "COMPLETENESS",
                        "Look for signature blocks, 'signed by', DocuSign/Adobe Sign references, handwritten signatures in images, or 'electronically signed'.",
                        List.of("signed", "signature", "DocuSign", "e-signed", "executed by"),
                        false),

                trait("INBOUND", "Inbound", "Received from an external party",
                        "DIRECTION",
                        "Look for 'Dear [our company]', external letterheads, invoices addressed to us, emails received. The sender is outside the organisation.",
                        List.of("Dear", "Invoice to", "To:", "Attn:"),
                        false),
                trait("OUTBOUND", "Outbound", "Sent to an external party",
                        "DIRECTION",
                        "Look for our company letterhead, 'Dear [external party]', our address as sender. The document originated from within the organisation.",
                        List.of("From:", "Our ref:", "Yours sincerely"),
                        false),
                trait("INTERNAL", "Internal", "For internal use only, not shared externally",
                        "DIRECTION",
                        "Look for 'internal', 'confidential — internal only', memos between departments, internal reference numbers.",
                        List.of("internal", "memo", "inter-office", "staff only"),
                        false),

                trait("ORIGINAL", "Original", "The original version of the document",
                        "PROVENANCE",
                        "The document appears to be the original — not a copy, scan, or forwarded version. High quality, consistent formatting.",
                        List.of(),
                        false),
                trait("COPY", "Copy", "A copy of another document",
                        "PROVENANCE",
                        "Look for 'copy', 'duplicate', forwarded email headers, 'FW:', 'cc:', or references to the original.",
                        List.of("copy", "duplicate", "FW:", "Fwd:"),
                        false),
                trait("SCAN", "Scan", "A scanned physical document",
                        "PROVENANCE",
                        "Look for scan artifacts: skewed text, image-based PDF, poor resolution, stamp marks, handwritten annotations.",
                        List.of("scanned", "scan"),
                        false),
                trait("GENERATED", "Generated", "Automatically generated by a system",
                        "PROVENANCE",
                        "Look for system-generated markers: automated headers/footers, report IDs, 'generated on', timestamps in headers, uniform formatting.",
                        List.of("generated", "automated", "report ID", "system generated"),
                        false)
        );

        traitRepo.saveAll(traits);
        log.info("Seeded {} trait definitions", traits.size());
    }

    private TraitDefinition trait(String key, String displayName, String description,
                                   String dimension, String detectionHint,
                                   List<String> indicators, boolean suppressPii) {
        TraitDefinition t = new TraitDefinition();
        t.setKey(key);
        t.setDisplayName(displayName);
        t.setDescription(description);
        t.setDimension(dimension);
        t.setDetectionHint(detectionHint);
        t.setIndicators(indicators);
        t.setSuppressPii(suppressPii);
        t.setActive(true);
        return t;
    }

    // ── Node Type Definition Migrations ─────────────────────────────────

    private void migrateNodeTypeDefinitions() {
        // Migrate aiClassification from ASYNC_BOUNDARY to SYNC_LLM
        nodeTypeRepo.findByKey("aiClassification").ifPresent(def -> {
            boolean changed = false;
            if ("ASYNC_BOUNDARY".equals(def.getExecutionCategory())) {
                def.setExecutionCategory("SYNC_LLM");
                def.setPipelinePhase("BOTH");
                changed = true;
            }
            if (def.getPerformanceImpact() == null && "SYNC_LLM".equals(def.getExecutionCategory())) {
                def.setPerformanceImpact("high");
                def.setPerformanceWarning("External LLM call — adds 10-30 seconds per document");
                changed = true;
            }
            if (changed) {
                def.setUpdatedAt(Instant.now());
                nodeTypeRepo.save(def);
                log.info("Migrated aiClassification to SYNC_LLM with performance warning");
            }
        });

        // Add performance impact to BERT classifier
        nodeTypeRepo.findByKey("bertClassifier").ifPresent(def -> {
            if (def.getPerformanceImpact() == null) {
                def.setPerformanceImpact("medium");
                def.setPerformanceWarning("External BERT service call — adds 1-5 seconds per document");
                def.setUpdatedAt(Instant.now());
                nodeTypeRepo.save(def);
                log.info("Added performance warning to bertClassifier");
            }
        });

        // Add performance impact to GENERIC_HTTP types
        for (var def : nodeTypeRepo.findAll()) {
            if ("GENERIC_HTTP".equals(def.getExecutionCategory()) && def.getPerformanceImpact() == null) {
                def.setPerformanceImpact("medium");
                def.setPerformanceWarning("External HTTP call — adds variable processing time");
                def.setUpdatedAt(Instant.now());
                nodeTypeRepo.save(def);
                log.info("Added performance warning to {}", def.getKey());
            }
        }
    }

    // ── Node Type Definitions ────────────────────────────────────────────

    private void seedNodeTypeDefinitions() {
        var now = Instant.now();

        // Standard handles: top input, bottom output, right error
        var stdHandles = List.of(
                new NodeTypeDefinition.HandleDefinition("target", "Top", null, null, null),
                new NodeTypeDefinition.HandleDefinition("source", "Bottom", null, null, null),
                new NodeTypeDefinition.HandleDefinition("source", "Right", "error", "!bg-red-500", null)
        );
        // Simple handles: top input, bottom output (no error)
        var simpleHandles = List.of(
                new NodeTypeDefinition.HandleDefinition("target", "Top", null, null, null),
                new NodeTypeDefinition.HandleDefinition("source", "Bottom", null, null, null)
        );
        // Source only (trigger)
        var sourceOnly = List.of(
                new NodeTypeDefinition.HandleDefinition("source", "Bottom", null, null, null)
        );
        // Condition handles: top input, true (left 30%), false (right 70%)
        var conditionHandles = List.of(
                new NodeTypeDefinition.HandleDefinition("target", "Top", null, null, null),
                new NodeTypeDefinition.HandleDefinition("source", "Bottom", "true", "!bg-green-500", Map.of("left", "30%")),
                new NodeTypeDefinition.HandleDefinition("source", "Bottom", "false", "!bg-red-500", Map.of("left", "70%"))
        );
        // Human review handles
        var reviewHandles = List.of(
                new NodeTypeDefinition.HandleDefinition("target", "Top", null, null, null),
                new NodeTypeDefinition.HandleDefinition("source", "Bottom", "approved", "!bg-green-500", Map.of("left", "30%")),
                new NodeTypeDefinition.HandleDefinition("source", "Bottom", "rejected", "!bg-red-500", Map.of("left", "70%"))
        );
        // Error handler handles
        var errorHandles = List.of(
                new NodeTypeDefinition.HandleDefinition("target", "Top", "errorInput", "!bg-red-500", null),
                new NodeTypeDefinition.HandleDefinition("source", "Bottom", "retry", "!bg-amber-500", Map.of("left", "30%")),
                new NodeTypeDefinition.HandleDefinition("source", "Bottom", "fail", "!bg-red-500", Map.of("left", "70%"))
        );

        List<NodeTypeDefinition> types = new java.util.ArrayList<>(List.of(
                // ── TRIGGERS ──
                nodeType("trigger", "Trigger", "Pipeline entry point — determines what starts processing",
                        "TRIGGER", 0, "NOOP", null, false, "PRE_CLASSIFICATION",
                        "Zap", "amber", sourceOnly, null,
                        Map.of("properties", Map.of(
                                "triggerType", Map.of("type", "string", "enum", List.of("upload", "folderMonitor", "manual", "scheduled"),
                                        "ui:widget", "select", "ui:placeholder", "-- Select --")
                        )),
                        null, null,
                        "Type: {{triggerType}}", "triggerType", now),

                // ── PROCESSING ──
                nodeType("textExtraction", "Text Extraction", "Extracts text content from uploaded documents using Apache Tika or OCR",
                        "PROCESSING", 0, "BUILT_IN", null, true, "PRE_CLASSIFICATION",
                        "FileText", "blue", stdHandles, null,
                        Map.of("properties", Map.of(
                                "extractor", Map.of("type", "string", "enum", List.of("tika", "ocr"),
                                        "default", "tika", "ui:widget", "select"),
                                "maxTextLength", Map.of("type", "integer", "default", 500000, "ui:placeholder", "500000")
                        )),
                        "EXTRACTOR", null,
                        "Extractor: {{extractor}}", null, now),

                nodeType("piiScanner", "PII Scanner", "Scans extracted text for personally identifiable information using regex patterns",
                        "PROCESSING", 1, "BUILT_IN", null, true, "PRE_CLASSIFICATION",
                        "Scan", "purple", stdHandles, null,
                        Map.of("properties", Map.of()),
                        "REGEX_SET", null,
                        "{{blockName}} v{{blockVersion}}", "blockId", now),

                nodeType("aiClassification", "AI Classification", "Classifies documents using an LLM (Claude, Ollama) via MCP tools. Each call adds 10-30 seconds of processing time.",
                        "PROCESSING", 2, "SYNC_LLM", null, false, "BOTH",
                        "Brain", "indigo", stdHandles, null,
                        Map.of("properties", new java.util.LinkedHashMap<String, Object>() {{
                                // ── LLM model selection ──
                                put("provider", Map.of("type", "string", "enum", List.of("", "anthropic", "ollama"),
                                        "ui:widget", "select", "ui:placeholder", "Global default",
                                        "ui:group", "Model",
                                        "ui:help", "LLM provider. Leave blank to use global AI Settings default."));
                                put("model", Map.of("type", "string",
                                        "ui:placeholder", "e.g. claude-sonnet-4-20250514",
                                        "ui:group", "Model",
                                        "ui:help", "Model ID. Leave blank to use global default."));
                                put("temperature", Map.of("type", "number", "minimum", 0, "maximum", 1,
                                        "ui:widget", "range", "ui:step", 0.05,
                                        "ui:group", "Model",
                                        "ui:help", "0 = deterministic, 1 = creative. Leave blank for global default."));
                                put("maxTokens", Map.of("type", "integer", "minimum", 256, "maximum", 16384,
                                        "ui:widget", "number",
                                        "ui:group", "Model",
                                        "ui:help", "Max response tokens. Leave blank for global default."));
                                put("timeoutSeconds", Map.of("type", "integer", "minimum", 10, "maximum", 900,
                                        "ui:widget", "number",
                                        "ui:group", "Model",
                                        "ui:help", "Hard timeout for this node (seconds). Leave blank to use the per-provider default from Settings."));

                                // ── Prompt injection — pre-load context to skip MCP round-trips ──
                                put("injectTaxonomy", Map.of("type", "boolean", "default", true,
                                        "ui:widget", "checkbox",
                                        "ui:group", "Prompt Injection",
                                        "ui:help", "Pre-load the taxonomy directly into the system prompt. Saves an MCP round-trip and stops the model 'wandering' looking for it. Recommended ON."));
                                put("injectSensitivities", Map.of("type", "boolean", "default", true,
                                        "ui:widget", "checkbox",
                                        "ui:group", "Prompt Injection",
                                        "ui:help", "Pre-load sensitivity definitions directly into the system prompt. Saves an MCP round-trip. Recommended ON."));
                                put("injectTraits", Map.of("type", "boolean", "default", false,
                                        "ui:widget", "checkbox",
                                        "ui:group", "Prompt Injection",
                                        "ui:help", "Pre-load document trait definitions directly into the system prompt. Disable if you don't use traits."));
                                put("injectPiiTypes", Map.of("type", "boolean", "default", false,
                                        "ui:widget", "checkbox",
                                        "ui:group", "Prompt Injection",
                                        "ui:help", "Pre-load PII type definitions into the prompt. Most pipelines do PII scanning before classification — leave OFF unless your prompt needs it."));
                        }}),
                        "PROMPT", null,
                        "{{blockName}} v{{blockVersion}}", "blockId", now),

                // ── ACCELERATORS ──
                nodeType("templateFingerprint", "Template Fingerprint", "Hashes document structure and compares against known templates. Skips LLM if match found above threshold.",
                        "ACCELERATOR", 0, "ACCELERATOR", null, false, "PRE_CLASSIFICATION",
                        "Fingerprint", "teal", stdHandles, null,
                        Map.of("properties", Map.of(
                                "threshold", Map.of("type", "number", "minimum", 0.5, "maximum", 1.0, "default", 0.85,
                                        "ui:widget", "range", "ui:step", 0.05)
                        )),
                        null, null,
                        "Threshold: {{threshold}}", null, now),

                nodeType("rulesEngine", "Rules Engine", "Evaluates configurable rules against document properties. First match wins and skips the LLM.",
                        "ACCELERATOR", 1, "ACCELERATOR", null, false, "PRE_CLASSIFICATION",
                        "ListChecks", "orange", stdHandles, null,
                        Map.of("properties", Map.of(
                                "rules", Map.of("type", "string", "ui:widget", "custom:rulesEditor")
                        )),
                        null, null,
                        null, "rules", now),

                nodeType("smartTruncation", "Smart Truncation", "Reduces text sent to the LLM by extracting key sections. Does not skip the LLM — reduces token cost.",
                        "ACCELERATOR", 2, "BUILT_IN", null, true, "PRE_CLASSIFICATION",
                        "Scissors", "gray", simpleHandles, null,
                        Map.of("properties", Map.of(
                                "maxChars", Map.of("type", "integer", "minimum", 2000, "maximum", 50000, "default", 10000,
                                        "ui:widget", "range", "ui:step", 1000),
                                "includeHeaders", Map.of("type", "boolean", "default", true, "ui:widget", "checkbox"),
                                "includeToc", Map.of("type", "boolean", "default", true, "ui:widget", "checkbox"),
                                "includeSignatures", Map.of("type", "boolean", "default", true, "ui:widget", "checkbox")
                        )),
                        null, null,
                        "Max: {{maxChars}} chars", null, now),

                nodeType("similarityCache", "Similarity Cache", "Compares text against previously classified documents using n-gram shingling. Skips LLM if a near-duplicate is found above threshold.",
                        "ACCELERATOR", 3, "ACCELERATOR", null, false, "PRE_CLASSIFICATION",
                        "Search", "cyan", stdHandles, null,
                        Map.of("properties", Map.of(
                                "threshold", Map.of("type", "number", "minimum", 0.5, "maximum", 1.0, "default", 0.90,
                                        "ui:widget", "range", "ui:step", 0.05),
                                "maxCandidates", Map.of("type", "integer", "minimum", 10, "maximum", 500, "default", 100)
                        )),
                        null, null,
                        "Threshold: {{threshold}}", null, now),

                nodeType("bertClassifier", "BERT Classifier", "Runs document text through a fine-tuned BERT model for fast, local classification. Skips LLM if confidence exceeds threshold. Falls back gracefully if the service is unavailable.",
                        "ACCELERATOR", 4, "ACCELERATOR", null, false, "PRE_CLASSIFICATION",
                        "Cpu", "violet", stdHandles, null,
                        Map.of("properties", Map.of(
                                "confidenceThreshold", Map.of("type", "number", "minimum", 0.5, "maximum", 1.0, "default", 0.85,
                                        "ui:widget", "range", "ui:step", 0.05),
                                "modelId", Map.of("type", "string", "ui:placeholder", "default",
                                        "ui:help", "Model served by the BERT sidecar. Leave blank for default."),
                                "maxTokens", Map.of("type", "integer", "minimum", 64, "maximum", 512, "default", 512),
                                "serviceUrl", Map.of("type", "string", "ui:placeholder", "http://bert-classifier:8000",
                                        "ui:help", "Override the default BERT service endpoint."),
                                "timeoutMs", Map.of("type", "integer", "minimum", 1000, "maximum", 30000, "default", 5000,
                                        "ui:step", 1000)
                        )),
                        "BERT_CLASSIFIER", null,
                        "Threshold: {{confidenceThreshold}}", null, now),

                // ── LOGIC ──
                nodeType("condition", "Condition", "Evaluates a field against a threshold and branches the pipeline into true/false paths",
                        "LOGIC", 0, "BUILT_IN", null, false, "POST_CLASSIFICATION",
                        "GitBranch", "amber", conditionHandles,
                        List.of(new NodeTypeDefinition.BranchLabel("true", "True", "text-green-600"),
                                new NodeTypeDefinition.BranchLabel("false", "False", "text-red-500")),
                        Map.of("properties", Map.of(
                                "field", Map.of("type", "string", "enum", List.of("confidence", "piiCount", "sensitivity", "fileType"),
                                        "ui:widget", "select", "ui:placeholder", "-- Select --"),
                                "operator", Map.of("type", "string", "enum", List.of(">", "<", "==", "!="),
                                        "ui:widget", "select", "ui:placeholder", "-- Select --"),
                                "value", Map.of("type", "string", "ui:placeholder", "e.g. 0.8")
                        )),
                        "ROUTER", null,
                        "{{field}} {{operator}} {{value}}", "field,operator,value", now),

                // ── ACTIONS ──
                nodeType("governance", "Governance", "Applies retention, storage tier, and policy rules based on classification",
                        "ACTION", 0, "BUILT_IN", null, true, "POST_CLASSIFICATION",
                        "Shield", "green", simpleHandles, null,
                        Map.of("properties", Map.of(
                                "retention", Map.of("type", "boolean", "default", true, "ui:widget", "checkbox"),
                                "storage", Map.of("type", "boolean", "default", true, "ui:widget", "checkbox"),
                                "policies", Map.of("type", "boolean", "default", true, "ui:widget", "checkbox")
                        )),
                        "ENFORCER", null,
                        "Retention + policies", null, now),

                nodeType("humanReview", "Human Review", "Routes documents to a human review queue when confidence is below threshold",
                        "ACTION", 1, "BUILT_IN", null, true, "POST_CLASSIFICATION",
                        "UserCheck", "blue", reviewHandles,
                        List.of(new NodeTypeDefinition.BranchLabel("approved", "Approved", "text-green-600"),
                                new NodeTypeDefinition.BranchLabel("rejected", "Rejected", "text-red-500")),
                        Map.of("properties", Map.of(
                                "threshold", Map.of("type", "number", "minimum", 0.0, "maximum", 1.0, "default", 0.7,
                                        "ui:widget", "range", "ui:step", 0.1,
                                        "ui:help", "Route to review when confidence below this")
                        )),
                        null, null,
                        "Threshold: {{threshold}}", null, now),

                nodeType("notification", "Notification", "Sends a notification via email or webhook when a document reaches this stage",
                        "ACTION", 2, "BUILT_IN", null, false, "POST_CLASSIFICATION",
                        "Bell", "pink", simpleHandles, null,
                        Map.of("properties", Map.of(
                                "channel", Map.of("type", "string", "enum", List.of("email", "webhook"),
                                        "ui:widget", "select", "ui:placeholder", "-- Select --"),
                                "recipient", Map.of("type", "string", "ui:placeholder", "email or URL...")
                        )),
                        null, null,
                        "{{channel}} to {{recipient}}", "channel", now),

                // ── ERROR HANDLING ──
                nodeType("errorHandler", "Error Handler", "Catches pipeline failures and provides retry/fallback logic",
                        "ERROR_HANDLING", 0, "NOOP", null, false, "BOTH",
                        "AlertTriangle", "red", errorHandles,
                        List.of(new NodeTypeDefinition.BranchLabel("retry", "Retry", "text-amber-600"),
                                new NodeTypeDefinition.BranchLabel("fail", "Fail", "text-red-500")),
                        Map.of("properties", Map.of(
                                "retryCount", Map.of("type", "integer", "minimum", 0, "maximum", 10, "default", 3),
                                "retryDelay", Map.of("type", "integer", "minimum", 0, "default", 1000,
                                        "ui:placeholder", "1000"),
                                "fallback", Map.of("type", "string", "enum", List.of("fail", "skip", "review"),
                                        "default", "fail", "ui:widget", "select")
                        )),
                        null, null,
                        "{{retryCount}} retries, {{retryDelay}}ms", null, now)
        ));

        // External Service node — needs httpConfig set after construction
        var externalService = nodeType("externalService", "External Service",
                "Calls an external HTTP service (classifier, enrichment API, etc.). Configure the URL, auth token, and request/response mapping.",
                "PROCESSING", 3, "GENERIC_HTTP", null, false, "PRE_CLASSIFICATION",
                "Globe", "slate", stdHandles, null,
                Map.of("properties", Map.of(
                        "serviceUrl", Map.of("type", "string",
                                "ui:placeholder", "https://my-service:8080",
                                "ui:help", "Base URL of the external service"),
                        "path", Map.of("type", "string",
                                "ui:placeholder", "/api/classify",
                                "ui:help", "API path appended to the base URL"),
                        "method", Map.of("type", "string", "enum", List.of("POST", "GET", "PUT"),
                                "default", "POST", "ui:widget", "select"),
                        "authToken", Map.of("type", "string",
                                "ui:widget", "password",
                                "ui:help", "Bearer token for Authorization header. Leave blank if not required."),
                        "timeoutMs", Map.of("type", "integer", "minimum", 1000, "maximum", 60000,
                                "default", 10000, "ui:step", 1000,
                                "ui:help", "Request timeout in milliseconds"),
                        "confidenceThreshold", Map.of("type", "number", "minimum", 0, "maximum", 1,
                                "default", 0.8, "ui:widget", "range", "ui:step", 0.05,
                                "ui:help", "Minimum confidence to accept the result (accelerator mode)")
                )),
                null, null,
                "{{method}} {{serviceUrl}}{{path}}", "serviceUrl", now);
        externalService.setHttpConfig(new NodeTypeDefinition.GenericHttpConfig(
                "http://localhost:8000", "/classify", "POST", 10000,
                Map.of("Content-Type", "application/json"),
                "{\"text\": \"{{extractedText}}\", \"document_id\": \"{{documentId}}\", \"mime_type\": \"{{mimeType}}\"}",
                Map.of("categoryId", "category_id", "categoryName", "category_name",
                        "confidence", "confidence", "sensitivityLabel", "sensitivity_label")
        ));
        externalService.setPerformanceImpact("medium");
        externalService.setPerformanceWarning("Adds network latency. Ensure the service is running and reachable.");
        types.add(externalService);

        // Upsert by key so new/updated node types are added to existing deployments
        int created = 0;
        int updated = 0;
        for (NodeTypeDefinition def : types) {
            var existing = nodeTypeRepo.findByKey(def.getKey());
            if (existing.isPresent()) {
                var e = existing.get();
                e.setDisplayName(def.getDisplayName());
                e.setDescription(def.getDescription());
                e.setCategory(def.getCategory());
                e.setSortOrder(def.getSortOrder());
                e.setExecutionCategory(def.getExecutionCategory());
                e.setHandlerBeanName(def.getHandlerBeanName());
                e.setRequiresDocReload(def.isRequiresDocReload());
                e.setPipelinePhase(def.getPipelinePhase());
                e.setIconName(def.getIconName());
                e.setColorTheme(def.getColorTheme());
                e.setHandles(def.getHandles());
                e.setBranchLabels(def.getBranchLabels());
                e.setConfigSchema(def.getConfigSchema());
                e.setCompatibleBlockType(def.getCompatibleBlockType());
                e.setSummaryTemplate(def.getSummaryTemplate());
                e.setValidationExpression(def.getValidationExpression());
                if (def.getHttpConfig() != null) e.setHttpConfig(def.getHttpConfig());
                if (def.getPerformanceImpact() != null) e.setPerformanceImpact(def.getPerformanceImpact());
                if (def.getPerformanceWarning() != null) e.setPerformanceWarning(def.getPerformanceWarning());
                e.setUpdatedAt(now);
                nodeTypeRepo.save(e);
                updated++;
            } else {
                nodeTypeRepo.save(def);
                created++;
            }
        }
        log.info("Node type definitions: {} created, {} updated", created, updated);
    }

    private NodeTypeDefinition nodeType(String key, String displayName, String description,
                                         String category, int sortOrder,
                                         String executionCategory, String handlerBeanName,
                                         boolean requiresDocReload, String pipelinePhase,
                                         String iconName, String colorTheme,
                                         List<NodeTypeDefinition.HandleDefinition> handles,
                                         List<NodeTypeDefinition.BranchLabel> branchLabels,
                                         Map<String, Object> configSchema,
                                         String compatibleBlockType, Map<String, Object> configDefaults,
                                         String summaryTemplate, String validationExpression,
                                         Instant now) {
        var def = new NodeTypeDefinition();
        def.setKey(key);
        def.setDisplayName(displayName);
        def.setDescription(description);
        def.setCategory(category);
        def.setSortOrder(sortOrder);
        def.setExecutionCategory(executionCategory);
        def.setHandlerBeanName(handlerBeanName);
        def.setRequiresDocReload(requiresDocReload);
        def.setPipelinePhase(pipelinePhase);
        def.setIconName(iconName);
        def.setColorTheme(colorTheme);
        def.setHandles(handles);
        def.setBranchLabels(branchLabels);
        def.setConfigSchema(configSchema);
        def.setConfigDefaults(configDefaults);
        def.setCompatibleBlockType(compatibleBlockType);
        def.setSummaryTemplate(summaryTemplate);
        def.setValidationExpression(validationExpression);
        def.setActive(true);
        def.setBuiltIn(true);
        def.setCreatedAt(now);
        def.setUpdatedAt(now);
        return def;
    }

    // ── Helpers for hub-only governance content ────────────────
    // Used by run() to give downstream code access to existing items
    // (now sourced exclusively from hub packs) without re-seeding them.

    private Map<String, String> existingIdMap(LegislationRepository repo) {
        return repo.findAll().stream()
                .filter(l -> l.getKey() != null)
                .collect(Collectors.toMap(Legislation::getKey, Legislation::getId, (a, b) -> a));
    }

    private Map<String, String> existingRetentionIdMap() {
        return retentionRepo.findAll().stream()
                .filter(r -> r.getName() != null)
                .collect(Collectors.toMap(RetentionSchedule::getName, RetentionSchedule::getId, (a, b) -> a));
    }

    private Map<String, String> existingSchemaIdMap() {
        return metadataSchemaRepo.findAll().stream()
                .filter(s -> s.getName() != null)
                .collect(Collectors.toMap(MetadataSchema::getName, MetadataSchema::getId, (a, b) -> a));
    }
}
