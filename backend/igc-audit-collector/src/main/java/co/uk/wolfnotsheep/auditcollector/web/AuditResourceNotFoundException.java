package co.uk.wolfnotsheep.auditcollector.web;

public class AuditResourceNotFoundException extends RuntimeException {
    public AuditResourceNotFoundException(String resourceType, String resourceId) {
        super("no Tier 1 events recorded for " + resourceType + ":" + resourceId);
    }
}
