package co.uk.wolfnotsheep.platformaudit.envelope;

/** Resource the audited action acted on. Determines the per-resource hash chain (CSV #4). */
public enum ResourceType {
    DOCUMENT,
    BLOCK,
    USER,
    PIPELINE_RUN,
    POLICY,
    CATEGORY,
    RETENTION_SCHEDULE
}
