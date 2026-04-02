package co.uk.wolfnotsheep.infrastructure.bootstrap;

import co.uk.wolfnotsheep.governance.models.*;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule.DispositionAction;
import co.uk.wolfnotsheep.governance.repositories.*;
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

    private final RetentionScheduleRepository retentionRepo;
    private final StorageTierRepository storageTierRepo;
    private final ClassificationCategoryRepository categoryRepo;
    private final GovernancePolicyRepository policyRepo;

    public GovernanceDataSeeder(
            RetentionScheduleRepository retentionRepo,
            StorageTierRepository storageTierRepo,
            ClassificationCategoryRepository categoryRepo,
            GovernancePolicyRepository policyRepo) {
        this.retentionRepo = retentionRepo;
        this.storageTierRepo = storageTierRepo;
        this.categoryRepo = categoryRepo;
        this.policyRepo = policyRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Governance data seeder starting...");

        Map<String, String> retentionIds = seedRetentionSchedules();
        seedStorageTiers();
        Map<String, String> categoryIds = seedCategories(retentionIds);
        seedPolicies(categoryIds);

        log.info("Governance data seeder complete.");
    }

    // ── Retention Schedules ────────────────────────────────

    private Map<String, String> seedRetentionSchedules() {
        if (retentionRepo.count() > 0) {
            log.info("Retention schedules already seeded, skipping.");
            return retentionRepo.findAll().stream()
                    .collect(Collectors.toMap(RetentionSchedule::getName, RetentionSchedule::getId));
        }

        List<RetentionSchedule> schedules = List.of(
                retention("Short-Term",
                        "Documents with no regulatory requirement — deleted after 1 year",
                        365, DispositionAction.DELETE, false,
                        "Internal policy"),
                retention("Standard",
                        "General business records — archived after 7 years per Companies Act 2006",
                        2555, DispositionAction.ARCHIVE, false,
                        "Companies Act 2006"),
                retention("Financial Statutory",
                        "Financial and tax records — reviewed after 7 years per HMRC requirements",
                        2555, DispositionAction.REVIEW, true,
                        "HMRC requirements, Companies Act 2006 s.386"),
                retention("Regulatory Extended",
                        "Regulated records — reviewed after 10 years per FCA/GDPR requirements",
                        3650, DispositionAction.REVIEW, true,
                        "FCA SYSC 9, GDPR Art.17 derogations"),
                retention("Permanent",
                        "Records that must be retained indefinitely — corporate constitution, board minutes",
                        36500, DispositionAction.ARCHIVE, true,
                        "Legal requirement, corporate constitution")
        );

        List<RetentionSchedule> saved = retentionRepo.saveAll(schedules);
        log.info("Seeded {} retention schedules.", saved.size());
        return saved.stream().collect(Collectors.toMap(RetentionSchedule::getName, RetentionSchedule::getId));
    }

    private RetentionSchedule retention(String name, String desc, int days,
                                        DispositionAction action, boolean legalHold, String basis) {
        RetentionSchedule rs = new RetentionSchedule();
        rs.setName(name);
        rs.setDescription(desc);
        rs.setRetentionDays(days);
        rs.setDispositionAction(action);
        rs.setLegalHoldOverride(legalHold);
        rs.setRegulatoryBasis(basis);
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

    private Map<String, String> seedCategories(Map<String, String> retentionIds) {
        if (categoryRepo.count() > 0) {
            log.info("Classification categories already seeded, skipping.");
            return categoryRepo.findAll().stream()
                    .collect(Collectors.toMap(ClassificationCategory::getName, ClassificationCategory::getId));
        }

        Map<String, String> ids = new HashMap<>();

        // Root categories
        ids.putAll(saveCategories(List.of(
                category("Legal",
                        "Legal documents, correspondence, and advice",
                        null, SensitivityLabel.CONFIDENTIAL, retentionIds.get("Regulatory Extended"),
                        List.of("contract", "agreement", "litigation", "court", "solicitor", "barrister", "legal opinion", "NDA")),
                category("Finance",
                        "Financial records, accounting, and tax documentation",
                        null, SensitivityLabel.CONFIDENTIAL, retentionIds.get("Financial Statutory"),
                        List.of("invoice", "receipt", "budget", "P&L", "balance sheet", "accounts", "tax", "HMRC", "VAT")),
                category("HR",
                        "Human resources, personnel records, and employee management",
                        null, SensitivityLabel.RESTRICTED, retentionIds.get("Standard"),
                        List.of("employee", "payroll", "disciplinary", "grievance", "recruitment", "appraisal", "absence")),
                category("Operations",
                        "Operational procedures, workflows, and logistics documentation",
                        null, SensitivityLabel.INTERNAL, retentionIds.get("Standard"),
                        List.of("procedure", "SOP", "process", "workflow", "logistics", "supply chain", "facilities")),
                category("Compliance",
                        "Regulatory compliance, audits, and risk management",
                        null, SensitivityLabel.CONFIDENTIAL, retentionIds.get("Regulatory Extended"),
                        List.of("audit", "regulation", "FCA", "ICO", "risk assessment", "DPA", "GDPR", "compliance")),
                category("IT",
                        "Information technology documentation, architecture, and security",
                        null, SensitivityLabel.INTERNAL, retentionIds.get("Short-Term"),
                        List.of("system", "network", "architecture", "security", "incident", "change request", "SLA")),
                category("Marketing",
                        "Marketing materials, campaigns, and external communications",
                        null, SensitivityLabel.PUBLIC, retentionIds.get("Short-Term"),
                        List.of("campaign", "brochure", "press release", "social media", "brand", "event", "newsletter"))
        )));

        // Child categories
        ids.putAll(saveCategories(List.of(
                category("Contracts",
                        "Commercial agreements, NDAs, service contracts, and leases",
                        ids.get("Legal"), SensitivityLabel.CONFIDENTIAL, retentionIds.get("Regulatory Extended"),
                        List.of("NDA", "service agreement", "SLA", "terms", "lease", "employment contract", "vendor agreement")),
                category("Litigation",
                        "Disputes, court proceedings, tribunal records, and legal privilege",
                        ids.get("Legal"), SensitivityLabel.RESTRICTED, retentionIds.get("Permanent"),
                        List.of("claim", "dispute", "court order", "tribunal", "settlement", "counsel advice", "legal privilege")),
                category("Invoices & Receipts",
                        "Purchase orders, credit notes, expense claims, and VAT receipts",
                        ids.get("Finance"), SensitivityLabel.INTERNAL, retentionIds.get("Financial Statutory"),
                        List.of("purchase order", "credit note", "expense claim", "VAT receipt", "payment")),
                category("Payroll",
                        "Salary records, P45/P60, pensions, and PAYE documentation",
                        ids.get("HR"), SensitivityLabel.RESTRICTED, retentionIds.get("Financial Statutory"),
                        List.of("salary", "P45", "P60", "pension", "national insurance", "PAYE", "payslip")),
                category("Recruitment",
                        "CVs, job descriptions, offer letters, interviews, and references",
                        ids.get("HR"), SensitivityLabel.CONFIDENTIAL, retentionIds.get("Standard"),
                        List.of("CV", "job description", "offer letter", "interview", "reference", "candidate")),
                category("Audit Reports",
                        "Internal and external audit findings, ISO compliance, and remediation",
                        ids.get("Compliance"), SensitivityLabel.CONFIDENTIAL, retentionIds.get("Regulatory Extended"),
                        List.of("internal audit", "external audit", "findings", "remediation", "ISO", "certification")),
                category("Security Incidents",
                        "Breach reports, vulnerability assessments, penetration tests, and forensics",
                        ids.get("IT"), SensitivityLabel.RESTRICTED, retentionIds.get("Regulatory Extended"),
                        List.of("breach", "vulnerability", "penetration test", "SIEM", "forensic", "incident response")),
                category("Press & Media",
                        "Press releases, media enquiries, and PR materials",
                        ids.get("Marketing"), SensitivityLabel.PUBLIC, retentionIds.get("Short-Term"),
                        List.of("press release", "media enquiry", "spokesperson brief", "PR", "public statement"))
        )));

        log.info("Seeded {} classification categories.", ids.size());
        return ids;
    }

    private Map<String, String> saveCategories(List<ClassificationCategory> categories) {
        List<ClassificationCategory> saved = categoryRepo.saveAll(categories);
        return saved.stream().collect(Collectors.toMap(ClassificationCategory::getName, ClassificationCategory::getId));
    }

    private ClassificationCategory category(String name, String desc, String parentId,
                                            SensitivityLabel sensitivity, String retentionId,
                                            List<String> keywords) {
        ClassificationCategory cat = new ClassificationCategory();
        cat.setName(name);
        cat.setDescription(desc);
        cat.setParentId(parentId);
        cat.setDefaultSensitivity(sensitivity);
        cat.setRetentionScheduleId(retentionId);
        cat.setKeywords(keywords);
        cat.setActive(true);
        return cat;
    }

    // ── Governance Policies ────────────────────────────────

    private void seedPolicies(Map<String, String> categoryIds) {
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
                        now, tenYears),

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
                        now, tenYears),

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
                        now, tenYears),

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
                        now, tenYears),

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
                        now, tenYears),

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
                        now, tenYears)
        );

        policyRepo.saveAll(policies);
        log.info("Seeded {} governance policies.", policies.size());
    }

    private GovernancePolicy policy(String name, String desc, List<String> rules,
                                    List<String> categoryIds, List<SensitivityLabel> sensitivities,
                                    Map<String, String> enforcement, Instant from, Instant until) {
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
        return p;
    }
}
