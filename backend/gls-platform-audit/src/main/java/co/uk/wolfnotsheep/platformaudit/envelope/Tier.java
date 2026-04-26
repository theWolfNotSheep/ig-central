package co.uk.wolfnotsheep.platformaudit.envelope;

/**
 * Audit event tier per architecture §7.2 / contracts/audit/event-envelope.schema.json.
 *
 * <ul>
 *     <li>{@link #DOMAIN} — Tier 1, compliance / audit-of-record. Hash-chained per resource.</li>
 *     <li>{@link #SYSTEM} — Tier 2, operations / debugging. Time-series; no chain.</li>
 * </ul>
 *
 * <p>Tier 3 (distributed traces) is OpenTelemetry-only and not represented here —
 * the envelope's {@code traceparent} field references back to those spans.
 */
public enum Tier {
    DOMAIN,
    SYSTEM
}
