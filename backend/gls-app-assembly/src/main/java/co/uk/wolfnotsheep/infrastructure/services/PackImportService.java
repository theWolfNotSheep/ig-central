package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.governance.models.*;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory.NodeStatus;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory.RetentionTrigger;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory.TaxonomyLevel;
import co.uk.wolfnotsheep.governance.models.MetadataSchema.FieldType;
import co.uk.wolfnotsheep.governance.models.MetadataSchema.MetadataField;
import co.uk.wolfnotsheep.governance.models.PiiTypeDefinition.ApprovalStatus;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule.DispositionAction;
import co.uk.wolfnotsheep.governance.repositories.*;
import co.uk.wolfnotsheep.governance.services.ClassificationCodeGenerator;
import co.uk.wolfnotsheep.infrastructure.services.HubPackDto.PackComponentDto;
import co.uk.wolfnotsheep.infrastructure.services.HubPackDto.PackVersionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Imports governance pack components from the Hub into the local tenant database.
 * Processes components in dependency order and resolves cross-references.
 */
@Service
public class PackImportService {

    private static final Logger log = LoggerFactory.getLogger(PackImportService.class);

    private final LegislationRepository legislationRepo;
    private final SensitivityDefinitionRepository sensitivityRepo;
    private final RetentionScheduleRepository retentionRepo;
    private final StorageTierRepository storageTierRepo;
    private final MetadataSchemaRepository metadataSchemaRepo;
    private final PiiTypeDefinitionRepository piiTypeRepo;
    private final TraitDefinitionRepository traitRepo;
    private final ClassificationCategoryRepository categoryRepo;
    private final GovernancePolicyRepository policyRepo;
    private final InstalledPackRepository installedPackRepo;
    private final PackUpdateAvailableRepository packUpdateRepo;
    private final ImportItemSnapshotRepository snapshotRepo;

    public PackImportService(
            LegislationRepository legislationRepo,
            SensitivityDefinitionRepository sensitivityRepo,
            RetentionScheduleRepository retentionRepo,
            StorageTierRepository storageTierRepo,
            MetadataSchemaRepository metadataSchemaRepo,
            PiiTypeDefinitionRepository piiTypeRepo,
            TraitDefinitionRepository traitRepo,
            ClassificationCategoryRepository categoryRepo,
            GovernancePolicyRepository policyRepo,
            InstalledPackRepository installedPackRepo,
            PackUpdateAvailableRepository packUpdateRepo,
            ImportItemSnapshotRepository snapshotRepo) {
        this.legislationRepo = legislationRepo;
        this.sensitivityRepo = sensitivityRepo;
        this.retentionRepo = retentionRepo;
        this.storageTierRepo = storageTierRepo;
        this.metadataSchemaRepo = metadataSchemaRepo;
        this.piiTypeRepo = piiTypeRepo;
        this.traitRepo = traitRepo;
        this.categoryRepo = categoryRepo;
        this.policyRepo = policyRepo;
        this.installedPackRepo = installedPackRepo;
        this.packUpdateRepo = packUpdateRepo;
        this.snapshotRepo = snapshotRepo;
    }

    // ── Import modes ─────────────────────────────────

    public enum ImportMode {
        MERGE,      // Import new items, skip existing
        OVERWRITE,  // Import everything, replace existing
        PREVIEW     // Dry-run — return what would change
    }

    // ── Import result ────────────────────────────────

    public record ImportResult(
            String packSlug,
            int packVersion,
            ImportMode mode,
            List<ComponentResult> components,
            int totalCreated,
            int totalUpdated,
            int totalSkipped,
            int totalFailed,
            List<String> errors
    ) {}

    public record ComponentResult(
            String componentType,
            String componentName,
            int created,
            int updated,
            int skipped,
            int failed,
            List<String> details
    ) {}

    // ── Main import ──────────────────────────────────

    public ImportResult importPack(PackVersionDto version, String packSlug, ImportMode mode) {
        log.info("Importing pack {} v{} in {} mode", packSlug, version.versionNumber(), mode);

        List<ComponentResult> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Instant importedAt = Instant.now();

        // Context for cross-reference resolution
        ImportContext ctx = new ImportContext(packSlug, version.versionNumber(), importedAt, mode);

        // Process in dependency order
        String[] importOrder = {
                "LEGISLATION", "SENSITIVITY_DEFINITIONS", "RETENTION_SCHEDULES",
                "STORAGE_TIERS", "METADATA_SCHEMAS", "PII_TYPE_DEFINITIONS",
                "TRAIT_DEFINITIONS", "TAXONOMY_CATEGORIES", "GOVERNANCE_POLICIES",
                "PIPELINE_BLOCKS"
        };

        // Index components by type for quick lookup
        Map<String, PackComponentDto> componentMap = new HashMap<>();
        for (PackComponentDto comp : version.components()) {
            componentMap.put(comp.type(), comp);
        }

        for (String type : importOrder) {
            PackComponentDto comp = componentMap.get(type);
            if (comp == null) continue;

            try {
                ComponentResult result = importComponent(comp, ctx);
                results.add(result);
            } catch (Exception e) {
                log.error("Failed to import component {}: {}", type, e.getMessage(), e);
                errors.add("Failed to import " + type + ": " + e.getMessage());
                results.add(new ComponentResult(type, comp.name(), 0, 0, 0, comp.itemCount(),
                        List.of("Error: " + e.getMessage())));
            }
        }

        int totalCreated = results.stream().mapToInt(ComponentResult::created).sum();
        int totalUpdated = results.stream().mapToInt(ComponentResult::updated).sum();
        int totalSkipped = results.stream().mapToInt(ComponentResult::skipped).sum();
        int totalFailed = results.stream().mapToInt(ComponentResult::failed).sum();

        log.info("Import complete: {} created, {} updated, {} skipped, {} failed",
                totalCreated, totalUpdated, totalSkipped, totalFailed);

        // Save import snapshots for diff/conflict detection
        if (mode != ImportMode.PREVIEW && !ctx.snapshots.isEmpty()) {
            try {
                if (mode == ImportMode.OVERWRITE) {
                    snapshotRepo.deleteAllByPackSlug(packSlug);
                }
                snapshotRepo.saveAll(ctx.snapshots);
                log.info("Saved {} import snapshots for pack {}", ctx.snapshots.size(), packSlug);
            } catch (Exception e) {
                log.warn("Failed to save import snapshots (non-fatal): {}", e.getMessage());
            }
        }

        // Record the installed pack for update observer (skip for preview mode)
        if (mode != ImportMode.PREVIEW && (totalCreated > 0 || totalUpdated > 0)) {
            recordInstallation(packSlug, version, importedAt, results);
        }

        return new ImportResult(packSlug, version.versionNumber(), mode,
                results, totalCreated, totalUpdated, totalSkipped, totalFailed, errors);
    }

    private void recordInstallation(String packSlug, PackVersionDto version,
                                     Instant importedAt, List<ComponentResult> results) {
        try {
            InstalledPack installed = installedPackRepo.findByPackSlug(packSlug)
                    .orElseGet(InstalledPack::new);
            installed.setPackSlug(packSlug);
            installed.setPackName(packSlug); // best available — hub name not in the DTO
            installed.setInstalledVersion(version.versionNumber());
            installed.setImportedAt(importedAt);
            installed.setComponentTypesImported(
                    results.stream().map(ComponentResult::componentType).toList());
            installedPackRepo.save(installed);

            // Clear any stale update notification for this pack
            packUpdateRepo.deleteByPackSlug(packSlug);

            log.info("Recorded installed pack: {} v{}", packSlug, version.versionNumber());
        } catch (Exception e) {
            log.warn("Failed to record installed pack (non-fatal): {}", e.getMessage());
        }
    }

    // ── Selective import (per-item accept/reject) ─────

    public record SelectedItem(String componentType, String itemKey) {}

    public ImportResult importSelectedItems(PackVersionDto version, String packSlug, List<SelectedItem> selections) {
        log.info("Selective import for pack {} v{}: {} items selected", packSlug, version.versionNumber(), selections.size());

        Set<String> selectionKeys = selections.stream()
                .map(s -> s.componentType() + "::" + s.itemKey())
                .collect(Collectors.toSet());

        // Filter hub data to only selected items
        List<HubPackDto.PackComponentDto> filteredComponents = new ArrayList<>();
        for (PackComponentDto comp : version.components()) {
            List<Map<String, Object>> filteredData = comp.data().stream()
                    .filter(item -> {
                        String key = extractItemKey(comp.type(), item);
                        return key != null && selectionKeys.contains(comp.type() + "::" + key);
                    })
                    .toList();
            if (!filteredData.isEmpty()) {
                filteredComponents.add(new PackComponentDto(comp.type(), comp.name(),
                        comp.description(), filteredData.size(), new ArrayList<>(filteredData)));
            }
        }

        // Create a filtered version DTO and run through normal import in OVERWRITE mode
        PackVersionDto filteredVersion = new PackVersionDto(
                version.id(), version.packId(), version.versionNumber(),
                version.changelog(), version.publishedBy(), version.publishedAt(),
                filteredComponents, version.compatibilityVersion());

        return importPack(filteredVersion, packSlug, ImportMode.OVERWRITE);
    }

    private static String extractItemKey(String componentType, Map<String, Object> item) {
        return switch (componentType) {
            case "LEGISLATION" -> str(item, "key");
            case "SENSITIVITY_DEFINITIONS" -> str(item, "key");
            case "RETENTION_SCHEDULES" -> str(item, "name");
            case "STORAGE_TIERS" -> str(item, "name");
            case "METADATA_SCHEMAS" -> str(item, "name");
            case "PII_TYPE_DEFINITIONS" -> str(item, "type");
            case "TRAIT_DEFINITIONS" -> str(item, "key");
            case "TAXONOMY_CATEGORIES" -> str(item, "name");
            case "GOVERNANCE_POLICIES" -> str(item, "name");
            default -> null;
        };
    }

    // ── Component dispatch ───────────────────────────

    private ComponentResult importComponent(PackComponentDto comp, ImportContext ctx) {
        return switch (comp.type()) {
            case "LEGISLATION" -> importLegislation(comp, ctx);
            case "SENSITIVITY_DEFINITIONS" -> importSensitivityDefinitions(comp, ctx);
            case "RETENTION_SCHEDULES" -> importRetentionSchedules(comp, ctx);
            case "STORAGE_TIERS" -> importStorageTiers(comp, ctx);
            case "METADATA_SCHEMAS" -> importMetadataSchemas(comp, ctx);
            case "PII_TYPE_DEFINITIONS" -> importPiiTypes(comp, ctx);
            case "TRAIT_DEFINITIONS" -> importTraitDefinitions(comp, ctx);
            case "TAXONOMY_CATEGORIES" -> importTaxonomyCategories(comp, ctx);
            case "GOVERNANCE_POLICIES" -> importGovernancePolicies(comp, ctx);
            case "PIPELINE_BLOCKS" -> new ComponentResult("PIPELINE_BLOCKS", comp.name(), 0, 0, comp.itemCount(), 0,
                    List.of("Pipeline blocks import not yet supported"));
            default -> new ComponentResult(comp.type(), comp.name(), 0, 0, comp.itemCount(), 0,
                    List.of("Unknown component type: " + comp.type()));
        };
    }

    // ── Legislation ──────────────────────────────────

    private ComponentResult importLegislation(PackComponentDto comp, ImportContext ctx) {
        int created = 0, updated = 0, skipped = 0;
        List<String> details = new ArrayList<>();

        for (Map<String, Object> item : comp.data()) {
            String key = str(item, "key");
            Optional<Legislation> existing = legislationRepo.findByKey(key);

            if (existing.isPresent()) {
                if (ctx.mode == ImportMode.OVERWRITE) {
                    Legislation law = existing.get();
                    applyLegislation(law, item, ctx);
                    if (ctx.mode != ImportMode.PREVIEW) {
                        legislationRepo.save(law);
                        ctx.recordSnapshot("LEGISLATION", key, law.getId(), item);
                    }
                    ctx.legislationKeyToId.put(key, law.getId());
                    updated++;
                    details.add("Updated: " + key);
                } else {
                    ctx.legislationKeyToId.put(key, existing.get().getId());
                    skipped++;
                }
            } else {
                Legislation law = new Legislation();
                applyLegislation(law, item, ctx);
                if (ctx.mode != ImportMode.PREVIEW) {
                    law = legislationRepo.save(law);
                    ctx.recordSnapshot("LEGISLATION", key, law.getId(), item);
                }
                ctx.legislationKeyToId.put(key, law.getId());
                created++;
                details.add("Created: " + key);
            }
        }

        return new ComponentResult("LEGISLATION", comp.name(), created, updated, skipped, 0, details);
    }

    private void applyLegislation(Legislation law, Map<String, Object> item, ImportContext ctx) {
        law.setKey(str(item, "key"));
        law.setName(str(item, "name"));
        law.setShortName(str(item, "shortName"));
        law.setJurisdiction(str(item, "jurisdiction"));
        law.setUrl(str(item, "url"));
        law.setDescription(str(item, "description"));
        law.setRelevantArticles(strList(item, "relevantArticles"));
        law.setActive(true);
        law.setSourcePackSlug(ctx.packSlug);
        law.setSourcePackVersion(ctx.packVersion);
        law.setImportedAt(ctx.importedAt);
    }

    // ── Sensitivity Definitions ──────────────────────

    private ComponentResult importSensitivityDefinitions(PackComponentDto comp, ImportContext ctx) {
        int created = 0, updated = 0, skipped = 0;
        List<String> details = new ArrayList<>();

        for (Map<String, Object> item : comp.data()) {
            String key = str(item, "key");
            Optional<SensitivityDefinition> existing = sensitivityRepo.findByKey(key);

            if (existing.isPresent()) {
                if (ctx.mode == ImportMode.OVERWRITE) {
                    SensitivityDefinition def = existing.get();
                    applySensitivity(def, item, ctx);
                    if (ctx.mode != ImportMode.PREVIEW) {
                        sensitivityRepo.save(def);
                        ctx.recordSnapshot("SENSITIVITY_DEFINITIONS", key, def.getId(), item);
                    }
                    updated++;
                    details.add("Updated: " + key);
                } else {
                    skipped++;
                }
            } else {
                SensitivityDefinition def = new SensitivityDefinition();
                applySensitivity(def, item, ctx);
                if (ctx.mode != ImportMode.PREVIEW) {
                    def = sensitivityRepo.save(def);
                    ctx.recordSnapshot("SENSITIVITY_DEFINITIONS", key, def.getId(), item);
                }
                created++;
                details.add("Created: " + key);
            }
        }

        return new ComponentResult("SENSITIVITY_DEFINITIONS", comp.name(), created, updated, skipped, 0, details);
    }

    private void applySensitivity(SensitivityDefinition def, Map<String, Object> item, ImportContext ctx) {
        def.setKey(str(item, "key"));
        def.setDisplayName(str(item, "displayName"));
        def.setDescription(str(item, "description"));
        def.setLevel(integer(item, "level"));
        def.setColour(str(item, "colour"));
        def.setGuidelines(strList(item, "guidelines"));
        def.setExamples(strList(item, "examples"));
        def.setActive(true);
        def.setSourcePackSlug(ctx.packSlug);
        def.setSourcePackVersion(ctx.packVersion);
        def.setImportedAt(ctx.importedAt);

        // Resolve legislation references
        List<String> legRefs = strList(item, "legislationRefs");
        if (!legRefs.isEmpty()) {
            def.setLegislationIds(resolveLegislationRefs(legRefs, ctx));
        }

        // Phase 1.7 / CSV #34. Preserve-on-missing — pack files that
        // don't carry applicableCategoryIds leave the existing value
        // alone (operator data wins).
        List<String> incomingCategories = strListOrNull(item, "applicableCategoryIds");
        if (incomingCategories != null) {
            def.setApplicableCategoryIds(incomingCategories);
        }
    }

    // ── Retention Schedules ──────────────────────────

    private ComponentResult importRetentionSchedules(PackComponentDto comp, ImportContext ctx) {
        int created = 0, updated = 0, skipped = 0;
        List<String> details = new ArrayList<>();

        for (Map<String, Object> item : comp.data()) {
            String name = str(item, "name");
            List<RetentionSchedule> existingList = retentionRepo.findAll().stream()
                    .filter(r -> r.getName().equals(name)).toList();

            if (!existingList.isEmpty()) {
                if (ctx.mode == ImportMode.OVERWRITE) {
                    RetentionSchedule rs = existingList.getFirst();
                    applyRetention(rs, item, ctx);
                    if (ctx.mode != ImportMode.PREVIEW) {
                        retentionRepo.save(rs);
                        ctx.recordSnapshot("RETENTION_SCHEDULES", name, rs.getId(), item);
                    }
                    ctx.retentionNameToId.put(name, rs.getId());
                    updated++;
                    details.add("Updated: " + name);
                } else {
                    ctx.retentionNameToId.put(name, existingList.getFirst().getId());
                    skipped++;
                }
            } else {
                RetentionSchedule rs = new RetentionSchedule();
                applyRetention(rs, item, ctx);
                if (ctx.mode != ImportMode.PREVIEW) {
                    rs = retentionRepo.save(rs);
                    ctx.recordSnapshot("RETENTION_SCHEDULES", name, rs.getId(), item);
                }
                ctx.retentionNameToId.put(name, rs.getId());
                created++;
                details.add("Created: " + name);
            }
        }

        return new ComponentResult("RETENTION_SCHEDULES", comp.name(), created, updated, skipped, 0, details);
    }

    private void applyRetention(RetentionSchedule rs, Map<String, Object> item, ImportContext ctx) {
        rs.setName(str(item, "name"));
        rs.setDescription(str(item, "description"));
        rs.setRetentionDays(integer(item, "retentionDays"));
        rs.setDispositionAction(DispositionAction.valueOf(str(item, "dispositionAction")));
        rs.setLegalHoldOverride(bool(item, "legalHoldOverride"));
        rs.setRegulatoryBasis(str(item, "regulatoryBasis"));
        rs.setSourcePackSlug(ctx.packSlug);
        rs.setSourcePackVersion(ctx.packVersion);
        rs.setImportedAt(ctx.importedAt);

        // ISO 15489 fields
        String duration = str(item, "retentionDuration");
        if (duration != null) rs.setRetentionDuration(duration);
        String trigger = str(item, "retentionTrigger");
        if (trigger != null) rs.setRetentionTrigger(trigger);
        String rsJurisdiction = str(item, "jurisdiction");
        if (rsJurisdiction != null) rs.setJurisdiction(rsJurisdiction);

        List<String> legRefs = strList(item, "legislationRefs");
        if (!legRefs.isEmpty()) {
            rs.setLegislationIds(resolveLegislationRefs(legRefs, ctx));
        }
    }

    // ── Storage Tiers ────────────────────────────────

    private ComponentResult importStorageTiers(PackComponentDto comp, ImportContext ctx) {
        int created = 0, updated = 0, skipped = 0;
        List<String> details = new ArrayList<>();

        for (Map<String, Object> item : comp.data()) {
            String name = str(item, "name");
            List<StorageTier> existingList = storageTierRepo.findAll().stream()
                    .filter(s -> s.getName().equals(name)).toList();

            if (!existingList.isEmpty()) {
                if (ctx.mode == ImportMode.OVERWRITE) {
                    StorageTier tier = existingList.getFirst();
                    applyStorageTier(tier, item, ctx);
                    if (ctx.mode != ImportMode.PREVIEW) {
                        storageTierRepo.save(tier);
                        ctx.recordSnapshot("STORAGE_TIERS", name, tier.getId(), item);
                    }
                    updated++;
                    details.add("Updated: " + name);
                } else {
                    skipped++;
                }
            } else {
                StorageTier tier = new StorageTier();
                applyStorageTier(tier, item, ctx);
                if (ctx.mode != ImportMode.PREVIEW) {
                    tier = storageTierRepo.save(tier);
                    ctx.recordSnapshot("STORAGE_TIERS", name, tier.getId(), item);
                }
                created++;
                details.add("Created: " + name);
            }
        }

        return new ComponentResult("STORAGE_TIERS", comp.name(), created, updated, skipped, 0, details);
    }

    private void applyStorageTier(StorageTier tier, Map<String, Object> item, ImportContext ctx) {
        tier.setName(str(item, "name"));
        tier.setDescription(str(item, "description"));
        tier.setEncryptionType(str(item, "encryptionType"));
        tier.setImmutable(bool(item, "immutable"));
        tier.setGeographicallyRestricted(bool(item, "geographicallyRestricted"));
        tier.setRegion(str(item, "region"));
        tier.setSourcePackSlug(ctx.packSlug);
        tier.setSourcePackVersion(ctx.packVersion);
        tier.setImportedAt(ctx.importedAt);

        // Phase 1.7 / CSV #32. Preserve-on-missing.
        List<String> incomingCategories = strListOrNull(item, "applicableCategoryIds");
        if (incomingCategories != null) {
            tier.setApplicableCategoryIds(incomingCategories);
        }
    }

    // ── Metadata Schemas ─────────────────────────────

    private ComponentResult importMetadataSchemas(PackComponentDto comp, ImportContext ctx) {
        int created = 0, updated = 0, skipped = 0;
        List<String> details = new ArrayList<>();

        for (Map<String, Object> item : comp.data()) {
            String name = str(item, "name");
            Optional<MetadataSchema> existing = metadataSchemaRepo.findByName(name);

            if (existing.isPresent()) {
                if (ctx.mode == ImportMode.OVERWRITE) {
                    MetadataSchema schema = existing.get();
                    applyMetadataSchema(schema, item, ctx);
                    if (ctx.mode != ImportMode.PREVIEW) {
                        metadataSchemaRepo.save(schema);
                        ctx.recordSnapshot("METADATA_SCHEMAS", name, schema.getId(), item);
                    }
                    ctx.schemaNameToId.put(name, schema.getId());
                    updated++;
                    details.add("Updated: " + name);
                } else {
                    ctx.schemaNameToId.put(name, existing.get().getId());
                    skipped++;
                }
            } else {
                MetadataSchema schema = new MetadataSchema();
                applyMetadataSchema(schema, item, ctx);
                if (ctx.mode != ImportMode.PREVIEW) {
                    schema = metadataSchemaRepo.save(schema);
                    ctx.recordSnapshot("METADATA_SCHEMAS", name, schema.getId(), item);
                }
                ctx.schemaNameToId.put(name, schema.getId());
                created++;
                details.add("Created: " + name);
            }
        }

        return new ComponentResult("METADATA_SCHEMAS", comp.name(), created, updated, skipped, 0, details);
    }

    @SuppressWarnings("unchecked")
    private void applyMetadataSchema(MetadataSchema schema, Map<String, Object> item, ImportContext ctx) {
        schema.setName(str(item, "name"));
        schema.setDescription(str(item, "description"));
        schema.setExtractionContext(str(item, "extractionContext"));
        schema.setActive(true);
        schema.setCreatedAt(ctx.importedAt);
        schema.setUpdatedAt(ctx.importedAt);
        schema.setSourcePackSlug(ctx.packSlug);
        schema.setSourcePackVersion(ctx.packVersion);
        schema.setImportedAt(ctx.importedAt);

        // Parse fields
        Object fieldsObj = item.get("fields");
        if (fieldsObj instanceof List<?> fieldsList) {
            List<MetadataField> fields = new ArrayList<>();
            for (Object f : fieldsList) {
                if (f instanceof Map<?, ?> fm) {
                    Map<String, Object> fieldMap = (Map<String, Object>) fm;
                    fields.add(new MetadataField(
                            str(fieldMap, "fieldName"),
                            FieldType.valueOf(str(fieldMap, "dataType")),
                            bool(fieldMap, "required"),
                            str(fieldMap, "description"),
                            str(fieldMap, "extractionHint"),
                            strList(fieldMap, "examples")
                    ));
                }
            }
            schema.setFields(fields);
        }
    }

    // ── PII Type Definitions ─────────────────────────

    private ComponentResult importPiiTypes(PackComponentDto comp, ImportContext ctx) {
        int created = 0, updated = 0, skipped = 0;
        List<String> details = new ArrayList<>();

        for (Map<String, Object> item : comp.data()) {
            String type = str(item, "type");
            Optional<PiiTypeDefinition> existing = piiTypeRepo.findByKey(type);

            if (existing.isPresent()) {
                if (ctx.mode == ImportMode.OVERWRITE) {
                    PiiTypeDefinition def = existing.get();
                    applyPiiType(def, item, ctx);
                    if (ctx.mode != ImportMode.PREVIEW) {
                        piiTypeRepo.save(def);
                        ctx.recordSnapshot("PII_TYPE_DEFINITIONS", type, def.getId(), item);
                    }
                    updated++;
                    details.add("Updated: " + type);
                } else {
                    skipped++;
                }
            } else {
                PiiTypeDefinition def = new PiiTypeDefinition();
                applyPiiType(def, item, ctx);
                if (ctx.mode != ImportMode.PREVIEW) {
                    def = piiTypeRepo.save(def);
                    ctx.recordSnapshot("PII_TYPE_DEFINITIONS", type, def.getId(), item);
                }
                created++;
                details.add("Created: " + type);
            }
        }

        return new ComponentResult("PII_TYPE_DEFINITIONS", comp.name(), created, updated, skipped, 0, details);
    }

    private void applyPiiType(PiiTypeDefinition def, Map<String, Object> item, ImportContext ctx) {
        def.setKey(str(item, "type"));
        def.setDisplayName(str(item, "name"));
        def.setDescription("Pattern: " + str(item, "regex"));
        def.setActive(true);
        def.setApprovalStatus(ApprovalStatus.APPROVED);
        def.setSourcePackSlug(ctx.packSlug);
        def.setSourcePackVersion(ctx.packVersion);
        def.setImportedAt(ctx.importedAt);

        // Phase 1.7 / CSV #31. Preserve-on-missing.
        List<String> incomingCategories = strListOrNull(item, "applicableCategoryIds");
        if (incomingCategories != null) {
            def.setApplicableCategoryIds(incomingCategories);
        }
    }

    // ── Trait Definitions ────────────────────────────

    private ComponentResult importTraitDefinitions(PackComponentDto comp, ImportContext ctx) {
        int created = 0, updated = 0, skipped = 0;
        List<String> details = new ArrayList<>();

        for (Map<String, Object> item : comp.data()) {
            String key = str(item, "key");
            Optional<TraitDefinition> existing = traitRepo.findByKey(key);

            if (existing.isPresent()) {
                if (ctx.mode == ImportMode.OVERWRITE) {
                    TraitDefinition def = existing.get();
                    applyTrait(def, item, ctx);
                    if (ctx.mode != ImportMode.PREVIEW) {
                        traitRepo.save(def);
                        ctx.recordSnapshot("TRAIT_DEFINITIONS", key, def.getId(), item);
                    }
                    updated++;
                    details.add("Updated: " + key);
                } else {
                    skipped++;
                }
            } else {
                TraitDefinition def = new TraitDefinition();
                applyTrait(def, item, ctx);
                if (ctx.mode != ImportMode.PREVIEW) {
                    def = traitRepo.save(def);
                    ctx.recordSnapshot("TRAIT_DEFINITIONS", key, def.getId(), item);
                }
                created++;
                details.add("Created: " + key);
            }
        }

        return new ComponentResult("TRAIT_DEFINITIONS", comp.name(), created, updated, skipped, 0, details);
    }

    private void applyTrait(TraitDefinition def, Map<String, Object> item, ImportContext ctx) {
        def.setKey(str(item, "key"));
        def.setDisplayName(str(item, "displayName"));
        def.setDescription(str(item, "description"));
        def.setDimension(str(item, "dimension"));
        def.setDetectionHint(str(item, "detectionHint"));
        def.setIndicators(strList(item, "indicators"));
        def.setSuppressPii(bool(item, "suppressPii"));
        def.setActive(true);
        def.setSourcePackSlug(ctx.packSlug);
        def.setSourcePackVersion(ctx.packVersion);
        def.setImportedAt(ctx.importedAt);

        // Phase 1.7 / CSV #33. Preserve-on-missing.
        List<String> incomingCategories = strListOrNull(item, "applicableCategoryIds");
        if (incomingCategories != null) {
            def.setApplicableCategoryIds(incomingCategories);
        }
    }

    // ── Taxonomy Categories ──────────────────────────

    private ComponentResult importTaxonomyCategories(PackComponentDto comp, ImportContext ctx) {
        int created = 0, updated = 0, skipped = 0;
        List<String> details = new ArrayList<>();

        // First pass: import root categories (no parentName)
        // Second pass: import children (have parentName)
        List<Map<String, Object>> roots = new ArrayList<>();
        List<Map<String, Object>> children = new ArrayList<>();
        for (Map<String, Object> item : comp.data()) {
            if (item.get("parentName") == null) {
                roots.add(item);
            } else {
                children.add(item);
            }
        }

        for (Map<String, Object> item : roots) {
            int[] counts = importCategory(item, null, ctx, details);
            created += counts[0]; updated += counts[1]; skipped += counts[2];
        }
        for (Map<String, Object> item : children) {
            String parentName = str(item, "parentName");
            String parentId = ctx.categoryNameToId.get(parentName);
            // If parent wasn't in this pack, try to find it in existing data
            if (parentId == null) {
                parentId = categoryRepo.findByNameIgnoreCase(parentName)
                        .map(ClassificationCategory::getId).orElse(null);
            }
            int[] counts = importCategory(item, parentId, ctx, details);
            created += counts[0]; updated += counts[1]; skipped += counts[2];
        }

        // Rebuild materialised paths for all imported categories
        if (ctx.mode != ImportMode.PREVIEW) {
            rebuildCategoryPaths(ctx.categoryNameToId.values());
        }

        return new ComponentResult("TAXONOMY_CATEGORIES", comp.name(), created, updated, skipped, 0, details);
    }

    /**
     * Rebuild materialised paths for the given category IDs by walking up the parent chain.
     */
    private void rebuildCategoryPaths(Collection<String> categoryIds) {
        Map<String, ClassificationCategory> allById = new HashMap<>();
        for (String id : categoryIds) {
            if (id != null) {
                categoryRepo.findById(id).ifPresent(c -> allById.put(c.getId(), c));
            }
        }
        // Also load parents that may not be in the import set
        Set<String> parentIds = new HashSet<>();
        for (ClassificationCategory cat : allById.values()) {
            if (cat.getParentId() != null && !allById.containsKey(cat.getParentId())) {
                parentIds.add(cat.getParentId());
            }
        }
        for (String pid : parentIds) {
            categoryRepo.findById(pid).ifPresent(c -> allById.put(c.getId(), c));
        }

        for (ClassificationCategory cat : allById.values()) {
            List<String> path = new ArrayList<>();
            buildPath(cat, allById, path);
            Collections.reverse(path);
            cat.setPath(path);
        }
        categoryRepo.saveAll(allById.values().stream()
                .filter(c -> categoryIds.contains(c.getId()))
                .toList());
    }

    private void buildPath(ClassificationCategory node, Map<String, ClassificationCategory> byId, List<String> path) {
        if (node.getClassificationCode() != null) {
            path.add(node.getClassificationCode());
        }
        if (node.getParentId() != null) {
            ClassificationCategory parent = byId.get(node.getParentId());
            if (parent == null) {
                parent = categoryRepo.findById(node.getParentId()).orElse(null);
                if (parent != null) byId.put(parent.getId(), parent);
            }
            if (parent != null) {
                buildPath(parent, byId, path);
            }
        }
    }

    private int[] importCategory(Map<String, Object> item, String parentId, ImportContext ctx, List<String> details) {
        String name = str(item, "name");
        Optional<ClassificationCategory> existing = categoryRepo.findByNameIgnoreCase(name);

        if (existing.isPresent()) {
            if (ctx.mode == ImportMode.OVERWRITE) {
                ClassificationCategory cat = existing.get();
                applyCategory(cat, item, parentId, ctx);
                if (ctx.mode != ImportMode.PREVIEW) {
                    categoryRepo.save(cat);
                    ctx.recordSnapshot("TAXONOMY_CATEGORIES", name, cat.getId(), item);
                }
                ctx.categoryNameToId.put(name, cat.getId());
                details.add("Updated: " + name);
                return new int[]{0, 1, 0};
            } else {
                ctx.categoryNameToId.put(name, existing.get().getId());
                return new int[]{0, 0, 1};
            }
        } else {
            ClassificationCategory cat = new ClassificationCategory();
            applyCategory(cat, item, parentId, ctx);
            if (ctx.mode != ImportMode.PREVIEW) {
                cat = categoryRepo.save(cat);
                ctx.recordSnapshot("TAXONOMY_CATEGORIES", name, cat.getId(), item);
            }
            ctx.categoryNameToId.put(name, cat.getId());
            details.add("Created: " + name);
            return new int[]{1, 0, 0};
        }
    }

    private void applyCategory(ClassificationCategory cat, Map<String, Object> item, String parentId, ImportContext ctx) {
        cat.setName(str(item, "name"));
        cat.setDescription(str(item, "description"));
        cat.setParentId(parentId);
        cat.setKeywords(strList(item, "keywords"));
        cat.setStatus(NodeStatus.ACTIVE);
        cat.setSourcePackSlug(ctx.packSlug);
        cat.setSourcePackVersion(ctx.packVersion);
        cat.setImportedAt(ctx.importedAt);

        // ISO 15489 BCS fields
        String code = str(item, "classificationCode");
        if (code != null && !code.isEmpty()) {
            // Check for code collision — append pack slug prefix if needed
            if (categoryRepo.existsByClassificationCode(code) && cat.getId() == null) {
                String existing = categoryRepo.findByClassificationCode(code)
                        .map(ClassificationCategory::getId).orElse(null);
                if (existing != null && !existing.equals(cat.getId())) {
                    code = ctx.packSlug.substring(0, Math.min(3, ctx.packSlug.length())).toUpperCase() + "-" + code;
                }
            }
            cat.setClassificationCode(code);
        } else {
            // Auto-generate code from name and parent
            String parentCode = null;
            if (parentId != null) {
                parentCode = categoryRepo.findById(parentId)
                        .map(ClassificationCategory::getClassificationCode).orElse(null);
            }
            cat.setClassificationCode(ClassificationCodeGenerator.generate(cat.getName(), parentCode));
        }

        String level = str(item, "level");
        if (level != null && !level.isEmpty()) {
            try { cat.setLevel(TaxonomyLevel.valueOf(level)); } catch (IllegalArgumentException ignored) {}
        } else {
            // Infer level from hierarchy position
            cat.setLevel(parentId == null ? TaxonomyLevel.FUNCTION : TaxonomyLevel.ACTIVITY);
        }

        String scopeNotes = str(item, "scopeNotes");
        if (scopeNotes != null) cat.setScopeNotes(scopeNotes);

        String retTrigger = str(item, "retentionTrigger");
        if (retTrigger != null && !retTrigger.isEmpty()) {
            try { cat.setRetentionTrigger(RetentionTrigger.valueOf(retTrigger)); } catch (IllegalArgumentException ignored) {}
        }

        String retTriggerDesc = str(item, "retentionTriggerDescription");
        if (retTriggerDesc != null) cat.setRetentionTriggerDescription(retTriggerDesc);

        // ISO 15489 spreadsheet fields
        String jurisdiction = str(item, "jurisdiction");
        if (jurisdiction != null) cat.setJurisdiction(jurisdiction);

        List<String> typicalRecords = strList(item, "typicalRecords");
        if (!typicalRecords.isEmpty()) cat.setTypicalRecords(typicalRecords);

        String retPeriodText = str(item, "retentionPeriodText");
        if (retPeriodText != null) cat.setRetentionPeriodText(retPeriodText);

        // legalCitation (also accept legacy "disposalAuthority" key)
        String legalCitation = str(item, "legalCitation");
        if (legalCitation == null) legalCitation = str(item, "disposalAuthority");
        if (legalCitation != null) cat.setLegalCitation(legalCitation);

        cat.setPersonalDataFlag(bool(item, "personalDataFlag"));
        cat.setVitalRecordFlag(bool(item, "vitalRecordFlag"));

        String reviewCycle = str(item, "reviewCycleDuration");
        if (reviewCycle != null) cat.setReviewCycleDuration(reviewCycle);

        String owner = str(item, "owner");
        if (owner != null) cat.setOwner(owner);

        String custodian = str(item, "custodian");
        if (custodian != null) cat.setCustodian(custodian);

        // Resolve defaultSensitivity
        String sensitivity = str(item, "defaultSensitivity");
        if (sensitivity != null && !sensitivity.isEmpty()) {
            try {
                cat.setDefaultSensitivity(SensitivityLabel.valueOf(sensitivity));
            } catch (IllegalArgumentException ignored) {}
        }

        // Resolve retention schedule cross-reference
        String retentionRef = str(item, "retentionScheduleRef");
        if (retentionRef != null && !retentionRef.isEmpty()) {
            String retentionId = ctx.retentionNameToId.get(retentionRef);
            if (retentionId != null) {
                cat.setRetentionScheduleId(retentionId);
            }
        }

        // Resolve metadata schema cross-reference
        String schemaRef = str(item, "metadataSchemaRef");
        if (schemaRef != null && !schemaRef.isEmpty()) {
            String schemaId = ctx.schemaNameToId.get(schemaRef);
            if (schemaId != null) {
                cat.setMetadataSchemaId(schemaId);
            }
        }
    }

    // ── Governance Policies ──────────────────────────

    private ComponentResult importGovernancePolicies(PackComponentDto comp, ImportContext ctx) {
        int created = 0, updated = 0, skipped = 0;
        List<String> details = new ArrayList<>();

        for (Map<String, Object> item : comp.data()) {
            String name = str(item, "name");
            List<GovernancePolicy> existingList = policyRepo.findAll().stream()
                    .filter(p -> p.getName().equals(name)).toList();

            if (!existingList.isEmpty()) {
                if (ctx.mode == ImportMode.OVERWRITE) {
                    GovernancePolicy policy = existingList.getFirst();
                    applyPolicy(policy, item, ctx);
                    if (ctx.mode != ImportMode.PREVIEW) {
                        policyRepo.save(policy);
                        ctx.recordSnapshot("GOVERNANCE_POLICIES", name, policy.getId(), item);
                    }
                    updated++;
                    details.add("Updated: " + name);
                } else {
                    skipped++;
                }
            } else {
                GovernancePolicy policy = new GovernancePolicy();
                applyPolicy(policy, item, ctx);
                if (ctx.mode != ImportMode.PREVIEW) {
                    policy = policyRepo.save(policy);
                    ctx.recordSnapshot("GOVERNANCE_POLICIES", name, policy.getId(), item);
                }
                created++;
                details.add("Created: " + name);
            }
        }

        return new ComponentResult("GOVERNANCE_POLICIES", comp.name(), created, updated, skipped, 0, details);
    }

    private void applyPolicy(GovernancePolicy policy, Map<String, Object> item, ImportContext ctx) {
        policy.setName(str(item, "name"));
        policy.setDescription(str(item, "description"));
        policy.setVersion(integer(item, "version"));
        policy.setActive(bool(item, "active"));
        policy.setRules(strList(item, "rules"));
        policy.setCreatedAt(ctx.importedAt);
        policy.setCreatedBy("hub-import");
        policy.setSourcePackSlug(ctx.packSlug);
        policy.setSourcePackVersion(ctx.packVersion);
        policy.setImportedAt(ctx.importedAt);

        // Resolve sensitivity references
        List<String> sensRefs = strList(item, "applicableSensitivities");
        if (!sensRefs.isEmpty()) {
            List<SensitivityLabel> labels = sensRefs.stream()
                    .map(s -> { try { return SensitivityLabel.valueOf(s); } catch (IllegalArgumentException e) { return null; } })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            policy.setApplicableSensitivities(labels);
        }

        // Resolve legislation references
        List<String> legRefs = strList(item, "legislationRefs");
        if (!legRefs.isEmpty()) {
            policy.setLegislationIds(resolveLegislationRefs(legRefs, ctx));
        }
    }

    // ── Cross-reference resolution ───────────────────

    private List<String> resolveLegislationRefs(List<String> refs, ImportContext ctx) {
        return refs.stream()
                .map(ref -> {
                    // First check import context
                    String id = ctx.legislationKeyToId.get(ref);
                    if (id != null) return id;
                    // Fallback: look up existing legislation by key
                    return legislationRepo.findByKey(ref).map(Legislation::getId).orElse(null);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── Import context ───────────────────────────────

    public static final Set<String> SNAPSHOT_EXCLUDED_FIELDS = Set.of(
            "sourcePackSlug", "sourcePackVersion", "importedAt", "id", "createdAt", "createdBy");

    public static Map<String, Object> stripProvenanceFields(Map<String, Object> data) {
        Map<String, Object> clean = new LinkedHashMap<>(data);
        SNAPSHOT_EXCLUDED_FIELDS.forEach(clean::remove);
        return clean;
    }

    static class ImportContext {
        final String packSlug;
        final int packVersion;
        final Instant importedAt;
        final ImportMode mode;

        // Cross-reference maps (populated during import, used for resolution)
        final Map<String, String> legislationKeyToId = new HashMap<>();
        final Map<String, String> retentionNameToId = new HashMap<>();
        final Map<String, String> schemaNameToId = new HashMap<>();
        final Map<String, String> categoryNameToId = new HashMap<>();

        // Snapshots to save after import
        final List<ImportItemSnapshot> snapshots = new ArrayList<>();

        ImportContext(String packSlug, int packVersion, Instant importedAt, ImportMode mode) {
            this.packSlug = packSlug;
            this.packVersion = packVersion;
            this.importedAt = importedAt;
            this.mode = mode;
        }

        void recordSnapshot(String componentType, String itemKey, String entityId, Map<String, Object> hubData) {
            ImportItemSnapshot snap = new ImportItemSnapshot();
            snap.setPackSlug(packSlug);
            snap.setPackVersion(packVersion);
            snap.setComponentType(componentType);
            snap.setItemKey(itemKey);
            snap.setEntityId(entityId);
            snap.setSnapshotFields(stripProvenanceFields(hubData));
            snap.setImportedAt(importedAt);
            snapshots.add(snap);
        }
    }

    // ── Map extraction helpers ───────────────────────

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static int integer(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    private static boolean bool(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return List.of();
    }

    /**
     * Returns the list value for {@code key}, or {@code null} when the
     * key is absent. Distinguishes "missing field" from "explicit empty
     * array" — used by Phase 1.7's {@code applicableCategoryIds[]}
     * preserve-on-missing semantics. A pack file without the field
     * leaves the existing value alone; a pack file with `[]` resets it
     * to global.
     */
    @SuppressWarnings("unchecked")
    static List<String> strListOrNull(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) return null;
        Object v = map.get(key);
        if (v instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return null;
    }
}
