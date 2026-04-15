package co.uk.wolfnotsheep.infrastructure.audit;

import co.uk.wolfnotsheep.document.models.SystemError;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;

/**
 * Global exception handler for all controllers. Ensures every error
 * gets a consistent response format, is logged, and persisted to SystemError
 * for admin visibility.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SystemErrorRepository errorRepo;

    public GlobalExceptionHandler(SystemErrorRepository errorRepo) {
        this.errorRepo = errorRepo;
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AuthorizationDeniedException e, HttpServletRequest req) {
        log.warn("Access denied: {} on {}", e.getMessage(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "ACCESS_DENIED",
                "message", "You do not have permission to perform this action",
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e, HttpServletRequest req) {
        log.warn("Bad request on {}: {}", req.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "BAD_REQUEST",
                "message", e.getMessage() != null ? e.getMessage() : "Invalid request",
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException e, HttpServletRequest req) {
        log.error("Unhandled runtime exception on {}: {}", req.getRequestURI(), e.getMessage(), e);
        persistError("ERROR", categorise(e), e, req);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", e.getMessage() != null ? e.getMessage() : "An unexpected error occurred",
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e, HttpServletRequest req) {
        log.error("Unhandled exception on {}: {}", req.getRequestURI(), e.getMessage(), e);
        persistError("ERROR", categorise(e), e, req);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "An unexpected error occurred",
                "timestamp", Instant.now().toString()
        ));
    }

    private void persistError(String severity, String category, Exception e, HttpServletRequest req) {
        try {
            SystemError error = SystemError.of(severity, category, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            error.setEndpoint(req.getRequestURI());
            error.setHttpMethod(req.getMethod());

            // Capture first 2000 chars of stack trace
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String trace = sw.toString();
            error.setStackTrace(trace.length() > 2000 ? trace.substring(0, 2000) : trace);

            // Extract user if available
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                error.setUserId(auth.getName());
            }

            errorRepo.save(error);
        } catch (Exception ex) {
            log.warn("Failed to persist SystemError: {}", ex.getMessage());
        }
    }

    private String categorise(Exception e) {
        String name = e.getClass().getSimpleName().toLowerCase();
        String msg = (e.getMessage() != null ? e.getMessage() : "").toLowerCase();

        if (name.contains("amqp") || name.contains("rabbit") || msg.contains("queue") || msg.contains("rabbit")) return "QUEUE";
        if (name.contains("minio") || msg.contains("minio") || msg.contains("object storage") || msg.contains("upload verification") || msg.contains("nosuchkey") || msg.contains("download object") || msg.contains("s3")) return "STORAGE";
        if (name.contains("mongo") || name.contains("data")) return "STORAGE";
        if (name.contains("auth") || name.contains("security") || name.contains("jwt")) return "AUTH";
        if (name.contains("io") || msg.contains("google") || msg.contains("drive") || msg.contains("http")) return "EXTERNAL_API";
        if (msg.contains("pipeline") || msg.contains("classif") || msg.contains("extract")) return "PIPELINE";
        return "INTERNAL";
    }
}
