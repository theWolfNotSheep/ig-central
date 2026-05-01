package co.uk.wolfnotsheep.governance.models;

import java.util.List;

/**
 * Strongly-typed view of a {@code POLICY} block's content. Mirrors
 * {@code contracts/blocks/policy.schema.json} (v0.4.0). The
 * {@link co.uk.wolfnotsheep.governance.services.PolicyBlockResolver}
 * reads the raw {@code pipeline_blocks} collection and parses content
 * maps into instances of this record.
 *
 * <p>Phase 1.8 / CSV #35.
 *
 * @param categoryId          Taxonomy category this policy applies to.
 * @param categoryName        Optional human-readable name.
 * @param requiredScans       PII / PHI / PCI / CUSTOM scans the engine must run.
 * @param metadataSchemaIds   Ordered metadata-schema dispatch list.
 * @param governancePolicyIds Storage / retention / access-control refs.
 * @param sensitivityOverrides Optional per-sensitivity overrides;
 *                            empty list = no overrides.
 */
public record PolicyBlock(
        String categoryId,
        String categoryName,
        List<RequiredScan> requiredScans,
        List<String> metadataSchemaIds,
        List<String> governancePolicyIds,
        List<SensitivityOverride> sensitivityOverrides
) {

    public PolicyBlock {
        requiredScans = requiredScans == null ? List.of() : List.copyOf(requiredScans);
        metadataSchemaIds = metadataSchemaIds == null ? List.of() : List.copyOf(metadataSchemaIds);
        governancePolicyIds = governancePolicyIds == null ? List.of() : List.copyOf(governancePolicyIds);
        sensitivityOverrides = sensitivityOverrides == null ? List.of() : List.copyOf(sensitivityOverrides);
    }

    /**
     * Resolve a {@link PolicyBlock} effective for the given sensitivity
     * — applies the first matching {@link SensitivityOverride}'s lists
     * over the top-level lists (any field the override carries replaces;
     * fields the override doesn't carry inherit).
     */
    public PolicyBlock effectiveFor(String sensitivity) {
        if (sensitivity == null) return this;
        for (SensitivityOverride override : sensitivityOverrides) {
            if (override.sensitivities().contains(sensitivity)) {
                return new PolicyBlock(
                        categoryId, categoryName,
                        override.requiredScans() != null ? override.requiredScans() : requiredScans,
                        override.metadataSchemaIds() != null ? override.metadataSchemaIds() : metadataSchemaIds,
                        override.governancePolicyIds() != null ? override.governancePolicyIds() : governancePolicyIds,
                        sensitivityOverrides);
            }
        }
        return this;
    }

    /**
     * @param scanType `PII` / `PHI` / `PCI` / `CUSTOM`.
     * @param ref      `PiiTypeDefinition.key` or PROMPT block id depending on scanType.
     * @param blocking If true, scan failure stops the pipeline. If false, scan failure
     *                 is logged + audited but the pipeline continues.
     */
    public record RequiredScan(String scanType, String ref, boolean blocking) {
    }

    /**
     * Per-sensitivity override. The engine evaluates the first match
     * against the classification's sensitivity. Any of the three
     * lists may be {@code null} — null = inherit from the top-level
     * PolicyBlock.
     */
    public record SensitivityOverride(
            List<String> sensitivities,
            List<RequiredScan> requiredScans,
            List<String> metadataSchemaIds,
            List<String> governancePolicyIds
    ) {
        public SensitivityOverride {
            sensitivities = sensitivities == null ? List.of() : List.copyOf(sensitivities);
        }
    }
}
