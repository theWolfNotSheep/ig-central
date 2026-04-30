package co.uk.wolfnotsheep.auditcollector.web;

public class AuditQueryInvalidException extends RuntimeException {
    public AuditQueryInvalidException(String message) {
        super(message);
    }
}
