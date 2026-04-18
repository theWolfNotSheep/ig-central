package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.governance.models.*;
import co.uk.wolfnotsheep.governance.repositories.*;
import co.uk.wolfnotsheep.infrastructure.services.HubPackDto.PackComponentDto;
import co.uk.wolfnotsheep.infrastructure.services.HubPackDto.PackVersionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes a three-way diff between local governance data, import snapshots (baseline),
 * and new hub pack data. Detects conflicts where both local and hub have changed.
 */
@Service
public class PackDiffService {

    private static final Logger log = LoggerFactory.getLogger(PackDiffService.class);

    private final ImportItemSnapshotRepository snapshotRepo;
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
    private final ObjectMapper objectMapper;

    public PackDiffService(
            ImportItemSnapshotRepository snapshotRepo,
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
            ObjectMapper objectMapper) {
        this.snapshotRepo = snapshotRepo;
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
        this.objectMapper = objectMapper;
    }

    // ── DTOs ─────────────────────────────────────────────

    public enum ItemDiffStatus { NEW, CHANGED, UNCHANGED, CONFLICT }

    public record FieldDiff(String field, Object currentValue, Object hubValue) {}

    public record ItemDiff(
            String componentType, String itemKey, String displayName,
            ItemDiffStatus status,
            List<FieldDiff> hubChanges,
            List<FieldDiff> localChanges
    ) {}

    public record ComponentDiff(
            String componentType, String componentName,
            List<ItemDiff> items,
            int newCount, int changedCount, int conflictCount, int unchangedCount
    ) {}

    public record PackDiffResult(
            String packSlug, int currentVersion, int newVersion,
            List<ComponentDiff> components,
            int totalNew, int totalChanged, int totalConflicts, int totalUnchanged
    ) {}

    // ── Main diff ────────────────────────────────────────

    public PackDiffResult computeDiff(PackVersionDto newVersion, String packSlug) {
        int currentVersion = installedPackRepo.findByPackSlug(packSlug)
                .map(InstalledPack::getInstalledVersion).orElse(0);

        // Index snapshots by componentType+itemKey
        Map<String, Map<String, ImportItemSnapshot>> snapshotIndex = new HashMap<>();
        for (ImportItemSnapshot snap : snapshotRepo.findAllByPackSlug(packSlug)) {
            snapshotIndex.computeIfAbsent(snap.getComponentType(), k -> new HashMap<>())
                    .put(snap.getItemKey(), snap);
        }

        List<ComponentDiff> components = new ArrayList<>();
        int totalNew = 0, totalChanged = 0, totalConflicts = 0, totalUnchanged = 0;

        for (PackComponentDto comp : newVersion.components()) {
            Map<String, ImportItemSnapshot> typeSnapshots = snapshotIndex.getOrDefault(comp.type(), Map.of());
            ComponentDiff cd = diffComponent(comp, typeSnapshots, packSlug);
            components.add(cd);
            totalNew += cd.newCount();
            totalChanged += cd.changedCount();
            totalConflicts += cd.conflictCount();
            totalUnchanged += cd.unchangedCount();
        }

        return new PackDiffResult(packSlug, currentVersion, newVersion.versionNumber(),
                components, totalNew, totalChanged, totalConflicts, totalUnchanged);
    }

    // ── Per-component diff ───────────────────────────────

    private ComponentDiff diffComponent(PackComponentDto comp, Map<String, ImportItemSnapshot> snapshots, String packSlug) {
        List<ItemDiff> items = new ArrayList<>();

        for (Map<String, Object> hubItem : comp.data()) {
            String itemKey = extractItemKey(comp.type(), hubItem);
            if (itemKey == null) continue;

            String displayName = extractDisplayName(comp.type(), hubItem);
            Object localEntity = lookupLocalEntity(comp.type(), itemKey);
            ImportItemSnapshot snapshot = snapshots.get(itemKey);

            Map<String, Object> hubFields = PackImportService.stripProvenanceFields(hubItem);

            if (localEntity == null) {
                items.add(new ItemDiff(comp.type(), itemKey, displayName,
                        ItemDiffStatus.NEW, fieldDiffsFromMap(hubFields), List.of()));
                continue;
            }

            // Serialize local entity to map for comparison
            @SuppressWarnings("unchecked")
            Map<String, Object> localFields = PackImportService.stripProvenanceFields(
                    objectMapper.convertValue(localEntity, Map.class));

            if (snapshot == null) {
                // No baseline — compare directly, treat any diff as CHANGED
                List<FieldDiff> diffs = compareFields(localFields, hubFields);
                ItemDiffStatus status = diffs.isEmpty() ? ItemDiffStatus.UNCHANGED : ItemDiffStatus.CHANGED;
                items.add(new ItemDiff(comp.type(), itemKey, displayName, status, diffs, List.of()));
                continue;
            }

            // Three-way diff
            Map<String, Object> baselineFields = snapshot.getSnapshotFields();
            List<FieldDiff> hubChanges = compareFields(baselineFields, hubFields);
            List<FieldDiff> localChanges = compareFields(baselineFields, localFields);

            if (hubChanges.isEmpty()) {
                items.add(new ItemDiff(comp.type(), itemKey, displayName,
                        ItemDiffStatus.UNCHANGED, List.of(), localChanges));
            } else if (localChanges.isEmpty()) {
                items.add(new ItemDiff(comp.type(), itemKey, displayName,
                        ItemDiffStatus.CHANGED, hubChanges, List.of()));
            } else {
                // Both changed — check if they conflict on same fields
                Set<String> hubChangedFields = hubChanges.stream().map(FieldDiff::field).collect(Collectors.toSet());
                Set<String> localChangedFields = localChanges.stream().map(FieldDiff::field).collect(Collectors.toSet());
                boolean hasConflict = hubChangedFields.stream().anyMatch(localChangedFields::contains);

                items.add(new ItemDiff(comp.type(), itemKey, displayName,
                        hasConflict ? ItemDiffStatus.CONFLICT : ItemDiffStatus.CHANGED,
                        hubChanges, localChanges));
            }
        }

        int newCount = (int) items.stream().filter(i -> i.status() == ItemDiffStatus.NEW).count();
        int changedCount = (int) items.stream().filter(i -> i.status() == ItemDiffStatus.CHANGED).count();
        int conflictCount = (int) items.stream().filter(i -> i.status() == ItemDiffStatus.CONFLICT).count();
        int unchangedCount = (int) items.stream().filter(i -> i.status() == ItemDiffStatus.UNCHANGED).count();

        return new ComponentDiff(comp.type(), comp.name(), items, newCount, changedCount, conflictCount, unchangedCount);
    }

    // ── Field comparison ─────────────────────────────────

    private List<FieldDiff> compareFields(Map<String, Object> baseline, Map<String, Object> target) {
        List<FieldDiff> diffs = new ArrayList<>();
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(baseline.keySet());
        allKeys.addAll(target.keySet());

        for (String key : allKeys) {
            if (PackImportService.SNAPSHOT_EXCLUDED_FIELDS.contains(key)) continue;
            Object baseVal = baseline.get(key);
            Object targetVal = target.get(key);
            if (!Objects.equals(normalise(baseVal), normalise(targetVal))) {
                diffs.add(new FieldDiff(key, baseVal, targetVal));
            }
        }
        return diffs;
    }

    private List<FieldDiff> fieldDiffsFromMap(Map<String, Object> fields) {
        return fields.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> new FieldDiff(e.getKey(), null, e.getValue()))
                .toList();
    }

    /** Normalise values for comparison — convert numbers to comparable form, etc. */
    private Object normalise(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        return val;
    }

    // ── Entity lookups (reusing same keys as PackImportService) ──

    private String extractItemKey(String componentType, Map<String, Object> item) {
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

    private String extractDisplayName(String componentType, Map<String, Object> item) {
        String name = str(item, "name");
        if (name != null) return name;
        String displayName = str(item, "displayName");
        if (displayName != null) return displayName;
        return extractItemKey(componentType, item);
    }

    private Object lookupLocalEntity(String componentType, String key) {
        return switch (componentType) {
            case "LEGISLATION" -> legislationRepo.findByKey(key).orElse(null);
            case "SENSITIVITY_DEFINITIONS" -> sensitivityRepo.findByKey(key).orElse(null);
            case "RETENTION_SCHEDULES" -> retentionRepo.findAll().stream()
                    .filter(r -> r.getName().equals(key)).findFirst().orElse(null);
            case "STORAGE_TIERS" -> storageTierRepo.findAll().stream()
                    .filter(s -> s.getName().equals(key)).findFirst().orElse(null);
            case "METADATA_SCHEMAS" -> metadataSchemaRepo.findByName(key).orElse(null);
            case "PII_TYPE_DEFINITIONS" -> piiTypeRepo.findByKey(key).orElse(null);
            case "TRAIT_DEFINITIONS" -> traitRepo.findByKey(key).orElse(null);
            case "TAXONOMY_CATEGORIES" -> categoryRepo.findByNameIgnoreCase(key).orElse(null);
            case "GOVERNANCE_POLICIES" -> policyRepo.findAll().stream()
                    .filter(p -> p.getName().equals(key)).findFirst().orElse(null);
            default -> null;
        };
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
