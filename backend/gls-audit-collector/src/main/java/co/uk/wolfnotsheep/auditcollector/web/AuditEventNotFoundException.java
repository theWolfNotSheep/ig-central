package co.uk.wolfnotsheep.auditcollector.web;

public class AuditEventNotFoundException extends RuntimeException {
    public AuditEventNotFoundException(String eventId) {
        super("no audit event with id " + eventId);
    }
}
