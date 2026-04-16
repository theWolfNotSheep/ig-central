// Shared types for the pack workspace editor.
// Each component type has its own typed shape; helpers translate to/from
// the generic Map<String, Object> the hub stores.

export type Level = "FUNCTION" | "ACTIVITY" | "TRANSACTION";

export type ComponentType =
    | "TAXONOMY_CATEGORIES"
    | "RETENTION_SCHEDULES"
    | "SENSITIVITY_DEFINITIONS"
    | "GOVERNANCE_POLICIES"
    | "PII_TYPE_DEFINITIONS"
    | "METADATA_SCHEMAS"
    | "STORAGE_TIERS"
    | "TRAIT_DEFINITIONS"
    | "PIPELINE_BLOCKS"
    | "LEGISLATION";

export interface PackComponent {
    type: ComponentType;
    name: string;
    description: string;
    itemCount: number;
    data: Record<string, unknown>[];
}

export interface PackVersion {
    id: string;
    packId: string;
    versionNumber: number;
    changelog: string;
    publishedBy: string;
    publishedAt: string;
    components: PackComponent[];
    compatibilityVersion: string;
}

export interface PackAuthor {
    name: string;
    organisation: string;
    email: string;
    verified: boolean;
}

export interface GovernancePack {
    id: string;
    name: string;
    slug: string;
    description: string;
    author: PackAuthor | null;
    jurisdiction: string;
    industries: string[];
    regulations: string[];
    tags: string[];
    status: string;
    featured: boolean;
    downloadCount: number;
    averageRating: number;
    reviewCount: number;
    latestVersionNumber: number;
    createdAt: string;
    updatedAt: string;
    publishedAt: string;
}

// Component data shapes -----------------------------------------------------

export interface LegislationItem {
    key: string;
    name: string;
    shortName: string;
    jurisdiction: string;
    url: string;
    description: string;
    relevantArticles: string[];
}

export interface SensitivityItem {
    key: string;
    displayName: string;
    description: string;
    level: number;
    colour: string;
    guidelines: string[];
    examples: string[];
    legislationRefs: string[];
}

export interface RetentionItem {
    name: string;
    description: string;
    retentionDays: number;
    retentionDuration?: string;
    retentionTrigger?: string;
    dispositionAction: string;
    legalHoldOverride: boolean;
    regulatoryBasis: string;
    jurisdiction?: string;
    legislationRefs: string[];
}

export interface StorageItem {
    name: string;
    description: string;
    encryptionType: string;
    immutable: boolean;
    geographicallyRestricted: boolean;
    region?: string;
}

export interface MetadataField {
    fieldName: string;
    dataType: string;
    required: boolean;
    description: string;
    examples?: string[];
}

export interface MetadataSchemaItem {
    name: string;
    description: string;
    extractionContext: string;
    fields: MetadataField[];
}

export interface PiiItem {
    name: string;
    type: string;
    regex: string;
    confidence: number;
    flags?: string;
}

export interface TraitItem {
    key: string;
    displayName: string;
    description: string;
    dimension: string;
    suppressPii: boolean;
    detectionHint?: string;
    indicators: string[];
}

export interface PolicyItem {
    name: string;
    description: string;
    version: number;
    active: boolean;
    applicableSensitivities: string[];
    rules: string[];
    legislationRefs: string[];
}

export interface TaxonomyItem {
    classificationCode: string;
    name: string;
    parentName?: string;
    level: Level;
    description?: string;
    defaultSensitivity?: string;
    keywords?: string[];
    typicalRecords?: string[];
    jurisdiction?: string;
    retentionPeriodText?: string;
    legalCitation?: string;
    retentionTrigger?: string;
    personalDataFlag?: boolean;
    vitalRecordFlag?: boolean;
    scopeNotes?: string;
    retentionScheduleRef?: string;
    metadataSchemaRef?: string;
}

// Workspace state -----------------------------------------------------------

export interface WorkspaceData {
    legislation: LegislationItem[];
    sensitivity: SensitivityItem[];
    retention: RetentionItem[];
    storage: StorageItem[];
    metadata: MetadataSchemaItem[];
    pii: PiiItem[];
    traits: TraitItem[];
    policies: PolicyItem[];
    taxonomy: TaxonomyItem[];
    /** Components we don't edit (e.g. PIPELINE_BLOCKS) — preserved on publish. */
    preserved: PackComponent[];
}

export const TABS = [
    { key: "versions", label: "Versions" },
    { key: "taxonomy", label: "Taxonomy", componentType: "TAXONOMY_CATEGORIES" as ComponentType },
    { key: "retention", label: "Retention", componentType: "RETENTION_SCHEDULES" as ComponentType },
    { key: "legislation", label: "Legislation", componentType: "LEGISLATION" as ComponentType },
    { key: "sensitivity", label: "Sensitivity", componentType: "SENSITIVITY_DEFINITIONS" as ComponentType },
    { key: "metadata", label: "Metadata", componentType: "METADATA_SCHEMAS" as ComponentType },
    { key: "pii", label: "PII Patterns", componentType: "PII_TYPE_DEFINITIONS" as ComponentType },
    { key: "policies", label: "Policies", componentType: "GOVERNANCE_POLICIES" as ComponentType },
    { key: "storage", label: "Storage", componentType: "STORAGE_TIERS" as ComponentType },
    { key: "traits", label: "Traits", componentType: "TRAIT_DEFINITIONS" as ComponentType },
] as const;

export type TabKey = (typeof TABS)[number]["key"];
