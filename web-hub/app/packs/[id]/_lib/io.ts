// Translate generic Map<String, Object> data <-> typed component items.

import {
    ComponentType, Level, LegislationItem, MetadataField, MetadataSchemaItem, PackComponent,
    PiiItem, PolicyItem, RetentionItem, SensitivityItem, StorageItem, TaxonomyItem, TraitItem,
    WorkspaceData,
} from "./types";

const str = (raw: Record<string, unknown>, k: string): string => raw[k] != null ? String(raw[k]) : "";
const opt = (raw: Record<string, unknown>, k: string): string | undefined => raw[k] != null ? String(raw[k]) : undefined;
const num = (raw: Record<string, unknown>, k: string): number => Number(raw[k] ?? 0);
const bool = (raw: Record<string, unknown>, k: string): boolean => raw[k] === true || raw[k] === "true";
const arr = (raw: Record<string, unknown>, k: string): string[] =>
    Array.isArray(raw[k]) ? (raw[k] as unknown[]).map(String) : [];

// LEGISLATION ---------------------------------------------------------------

export const toLegislation = (r: Record<string, unknown>): LegislationItem => ({
    key: str(r, "key"),
    name: str(r, "name"),
    shortName: str(r, "shortName"),
    jurisdiction: str(r, "jurisdiction"),
    url: str(r, "url"),
    description: str(r, "description"),
    relevantArticles: arr(r, "relevantArticles"),
});

export const fromLegislation = (i: LegislationItem): Record<string, unknown> => ({
    key: i.key, name: i.name, shortName: i.shortName, jurisdiction: i.jurisdiction,
    url: i.url, description: i.description, relevantArticles: i.relevantArticles,
});

// SENSITIVITY ---------------------------------------------------------------

export const toSensitivity = (r: Record<string, unknown>): SensitivityItem => ({
    key: str(r, "key"),
    displayName: str(r, "displayName"),
    description: str(r, "description"),
    level: num(r, "level"),
    colour: str(r, "colour"),
    guidelines: arr(r, "guidelines"),
    examples: arr(r, "examples"),
    legislationRefs: arr(r, "legislationRefs"),
});

export const fromSensitivity = (i: SensitivityItem): Record<string, unknown> => {
    const o: Record<string, unknown> = {
        key: i.key, displayName: i.displayName, description: i.description,
        level: i.level, colour: i.colour, guidelines: i.guidelines, examples: i.examples,
    };
    if (i.legislationRefs.length) o.legislationRefs = i.legislationRefs;
    return o;
};

// RETENTION -----------------------------------------------------------------

export const toRetention = (r: Record<string, unknown>): RetentionItem => ({
    name: str(r, "name"),
    description: str(r, "description"),
    retentionDays: num(r, "retentionDays"),
    retentionDuration: opt(r, "retentionDuration"),
    retentionTrigger: opt(r, "retentionTrigger"),
    dispositionAction: str(r, "dispositionAction") || "REVIEW",
    legalHoldOverride: bool(r, "legalHoldOverride"),
    regulatoryBasis: str(r, "regulatoryBasis"),
    jurisdiction: opt(r, "jurisdiction"),
    legislationRefs: arr(r, "legislationRefs"),
});

export const fromRetention = (i: RetentionItem): Record<string, unknown> => {
    const o: Record<string, unknown> = {
        name: i.name, description: i.description, retentionDays: i.retentionDays,
        dispositionAction: i.dispositionAction, legalHoldOverride: i.legalHoldOverride,
        regulatoryBasis: i.regulatoryBasis,
    };
    if (i.retentionDuration) o.retentionDuration = i.retentionDuration;
    if (i.retentionTrigger) o.retentionTrigger = i.retentionTrigger;
    if (i.jurisdiction) o.jurisdiction = i.jurisdiction;
    if (i.legislationRefs.length) o.legislationRefs = i.legislationRefs;
    return o;
};

// STORAGE -------------------------------------------------------------------

export const toStorage = (r: Record<string, unknown>): StorageItem => ({
    name: str(r, "name"),
    description: str(r, "description"),
    encryptionType: str(r, "encryptionType"),
    immutable: bool(r, "immutable"),
    geographicallyRestricted: bool(r, "geographicallyRestricted"),
    region: opt(r, "region"),
});

export const fromStorage = (i: StorageItem): Record<string, unknown> => {
    const o: Record<string, unknown> = {
        name: i.name, description: i.description, encryptionType: i.encryptionType,
        immutable: i.immutable, geographicallyRestricted: i.geographicallyRestricted,
    };
    if (i.region) o.region = i.region;
    return o;
};

// METADATA SCHEMAS ----------------------------------------------------------

const toField = (r: Record<string, unknown>): MetadataField => ({
    fieldName: str(r, "fieldName"),
    dataType: str(r, "dataType") || "TEXT",
    required: bool(r, "required"),
    description: str(r, "description"),
    examples: arr(r, "examples"),
});

const fromField = (f: MetadataField): Record<string, unknown> => {
    const o: Record<string, unknown> = {
        fieldName: f.fieldName, dataType: f.dataType, required: f.required, description: f.description,
    };
    if (f.examples && f.examples.length) o.examples = f.examples;
    return o;
};

export const toMetadata = (r: Record<string, unknown>): MetadataSchemaItem => ({
    name: str(r, "name"),
    description: str(r, "description"),
    extractionContext: str(r, "extractionContext"),
    fields: Array.isArray(r.fields) ? (r.fields as Record<string, unknown>[]).map(toField) : [],
});

export const fromMetadata = (i: MetadataSchemaItem): Record<string, unknown> => ({
    name: i.name, description: i.description, extractionContext: i.extractionContext,
    fields: i.fields.map(fromField),
});

// PII -----------------------------------------------------------------------

export const toPii = (r: Record<string, unknown>): PiiItem => ({
    name: str(r, "name"),
    type: str(r, "type"),
    regex: str(r, "regex"),
    confidence: num(r, "confidence"),
    flags: opt(r, "flags"),
});

export const fromPii = (i: PiiItem): Record<string, unknown> => {
    const o: Record<string, unknown> = {
        name: i.name, type: i.type, regex: i.regex, confidence: i.confidence,
    };
    if (i.flags) o.flags = i.flags;
    return o;
};

// TRAITS --------------------------------------------------------------------

export const toTrait = (r: Record<string, unknown>): TraitItem => ({
    key: str(r, "key"),
    displayName: str(r, "displayName"),
    description: str(r, "description"),
    dimension: str(r, "dimension"),
    suppressPii: bool(r, "suppressPii"),
    detectionHint: opt(r, "detectionHint"),
    indicators: arr(r, "indicators"),
});

export const fromTrait = (i: TraitItem): Record<string, unknown> => {
    const o: Record<string, unknown> = {
        key: i.key, displayName: i.displayName, description: i.description,
        dimension: i.dimension, suppressPii: i.suppressPii,
    };
    if (i.detectionHint) o.detectionHint = i.detectionHint;
    if (i.indicators.length) o.indicators = i.indicators;
    return o;
};

// POLICIES ------------------------------------------------------------------

export const toPolicy = (r: Record<string, unknown>): PolicyItem => ({
    name: str(r, "name"),
    description: str(r, "description"),
    version: num(r, "version") || 1,
    active: r.active != null ? bool(r, "active") : true,
    applicableSensitivities: arr(r, "applicableSensitivities"),
    rules: arr(r, "rules"),
    legislationRefs: arr(r, "legislationRefs"),
});

export const fromPolicy = (i: PolicyItem): Record<string, unknown> => {
    const o: Record<string, unknown> = {
        name: i.name, description: i.description, version: i.version, active: i.active,
        applicableSensitivities: i.applicableSensitivities, rules: i.rules,
    };
    if (i.legislationRefs.length) o.legislationRefs = i.legislationRefs;
    return o;
};

// TAXONOMY ------------------------------------------------------------------

export const toTaxonomy = (r: Record<string, unknown>): TaxonomyItem => ({
    classificationCode: str(r, "classificationCode"),
    name: str(r, "name"),
    parentName: opt(r, "parentName"),
    level: (str(r, "level") || "TRANSACTION") as Level,
    description: opt(r, "description"),
    defaultSensitivity: opt(r, "defaultSensitivity"),
    keywords: arr(r, "keywords"),
    typicalRecords: arr(r, "typicalRecords"),
    jurisdiction: opt(r, "jurisdiction"),
    retentionPeriodText: opt(r, "retentionPeriodText"),
    legalCitation: opt(r, "legalCitation") ?? opt(r, "disposalAuthority"),
    retentionTrigger: opt(r, "retentionTrigger"),
    personalDataFlag: bool(r, "personalDataFlag"),
    vitalRecordFlag: bool(r, "vitalRecordFlag"),
    scopeNotes: opt(r, "scopeNotes"),
    retentionScheduleRef: opt(r, "retentionScheduleRef"),
    metadataSchemaRef: opt(r, "metadataSchemaRef"),
});

export const fromTaxonomy = (i: TaxonomyItem): Record<string, unknown> => {
    const o: Record<string, unknown> = {
        classificationCode: i.classificationCode, name: i.name, level: i.level,
    };
    if (i.parentName) o.parentName = i.parentName;
    if (i.description) o.description = i.description;
    if (i.defaultSensitivity) o.defaultSensitivity = i.defaultSensitivity;
    if (i.keywords && i.keywords.length) o.keywords = i.keywords;
    if (i.typicalRecords && i.typicalRecords.length) o.typicalRecords = i.typicalRecords;
    if (i.jurisdiction) o.jurisdiction = i.jurisdiction;
    if (i.retentionPeriodText) o.retentionPeriodText = i.retentionPeriodText;
    if (i.legalCitation) o.legalCitation = i.legalCitation;
    if (i.retentionTrigger) o.retentionTrigger = i.retentionTrigger;
    if (i.personalDataFlag) o.personalDataFlag = true;
    if (i.vitalRecordFlag) o.vitalRecordFlag = true;
    if (i.scopeNotes) o.scopeNotes = i.scopeNotes;
    if (i.retentionScheduleRef) o.retentionScheduleRef = i.retentionScheduleRef;
    if (i.metadataSchemaRef) o.metadataSchemaRef = i.metadataSchemaRef;
    return o;
};

// Load WorkspaceData from a PackVersion -------------------------------------

const TYPED: ComponentType[] = [
    "TAXONOMY_CATEGORIES", "RETENTION_SCHEDULES", "LEGISLATION",
    "SENSITIVITY_DEFINITIONS", "METADATA_SCHEMAS", "PII_TYPE_DEFINITIONS",
    "GOVERNANCE_POLICIES", "STORAGE_TIERS", "TRAIT_DEFINITIONS",
];

export function loadWorkspace(components: PackComponent[]): WorkspaceData {
    const find = (t: ComponentType): Record<string, unknown>[] => {
        const c = components.find((x) => x.type === t);
        return c ? c.data : [];
    };
    return {
        legislation: find("LEGISLATION").map(toLegislation),
        sensitivity: find("SENSITIVITY_DEFINITIONS").map(toSensitivity),
        retention: find("RETENTION_SCHEDULES").map(toRetention),
        storage: find("STORAGE_TIERS").map(toStorage),
        metadata: find("METADATA_SCHEMAS").map(toMetadata),
        pii: find("PII_TYPE_DEFINITIONS").map(toPii),
        traits: find("TRAIT_DEFINITIONS").map(toTrait),
        policies: find("GOVERNANCE_POLICIES").map(toPolicy),
        taxonomy: find("TAXONOMY_CATEGORIES").map(toTaxonomy),
        preserved: components.filter((c) => !TYPED.includes(c.type)),
    };
}

// Build PackComponent[] from WorkspaceData for publish ----------------------

export function buildComponents(packName: string, data: WorkspaceData): PackComponent[] {
    const comps: PackComponent[] = [];
    const add = <T,>(type: ComponentType, label: string, items: T[], conv: (i: T) => Record<string, unknown>) => {
        if (items.length === 0) return;
        comps.push({
            type, name: `${packName} ${label}`,
            description: `${items.length} ${label.toLowerCase()}`,
            itemCount: items.length, data: items.map(conv),
        });
    };
    add("LEGISLATION", "Legislation", data.legislation, fromLegislation);
    add("SENSITIVITY_DEFINITIONS", "Sensitivity Labels", data.sensitivity, fromSensitivity);
    add("RETENTION_SCHEDULES", "Retention Schedules", data.retention, fromRetention);
    add("STORAGE_TIERS", "Storage Tiers", data.storage, fromStorage);
    add("METADATA_SCHEMAS", "Metadata Schemas", data.metadata, fromMetadata);
    add("PII_TYPE_DEFINITIONS", "PII Patterns", data.pii, fromPii);
    add("TRAIT_DEFINITIONS", "Document Traits", data.traits, fromTrait);
    add("GOVERNANCE_POLICIES", "Governance Policies", data.policies, fromPolicy);
    add("TAXONOMY_CATEGORIES", "Taxonomy", data.taxonomy, fromTaxonomy);
    return [...comps, ...data.preserved];
}
