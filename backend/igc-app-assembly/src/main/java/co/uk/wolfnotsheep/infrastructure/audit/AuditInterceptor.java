package co.uk.wolfnotsheep.infrastructure.audit;

import co.uk.wolfnotsheep.document.models.SystemAuditEvent;
import co.uk.wolfnotsheep.document.repositories.SystemAuditEventRepository;
import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * AOP interceptor that records an audit event for every controller method
 * annotated with @PostMapping, @PutMapping, @DeleteMapping, or @PatchMapping.
 * GET requests are not audited (read-only).
 */
@Aspect
@Component
public class AuditInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);

    private final SystemAuditEventRepository auditRepo;

    public AuditInterceptor(SystemAuditEventRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @Around("execution(* co.uk.wolfnotsheep.infrastructure.controllers..*(..)) || " +
            "execution(* co.uk.wolfnotsheep.platform..controllers..*(..))")
    public Object auditControllerMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

        // Only audit write operations
        if (!isWriteOperation(method)) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = getRequest();
        SystemAuditEvent event = new SystemAuditEvent();

        // User context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserModel user) {
            event.setUserId(user.getId());
            event.setUserEmail(user.getEmail());
        }

        // Request context
        if (request != null) {
            event.setHttpMethod(request.getMethod());
            event.setEndpoint(request.getRequestURI());
            event.setIpAddress(getClientIp(request));
            event.setUserAgent(truncate(request.getHeader("User-Agent"), 200));
        }

        // Action + resource type from controller/method name
        String controllerName = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        event.setAction(deriveAction(methodName));
        event.setResourceType(deriveResourceType(controllerName));

        // Extract resource ID from path if available
        event.setResourceId(extractResourceId(request));

        // Request summary (sanitised — no passwords or tokens)
        event.setRequestSummary(truncate(summariseArgs(joinPoint.getArgs()), 500));

        try {
            Object result = joinPoint.proceed();

            // Record success
            event.setSuccess(true);
            if (result instanceof ResponseEntity<?> re) {
                event.setResponseStatus(re.getStatusCode().value());
            } else {
                event.setResponseStatus(200);
            }

            saveAuditEvent(event);
            return result;

        } catch (Throwable t) {
            // Record failure
            event.setSuccess(false);
            event.setResponseStatus(500);
            event.setErrorMessage(truncate(t.getMessage(), 500));

            saveAuditEvent(event);
            throw t; // Re-throw — don't swallow
        }
    }

    private boolean isWriteOperation(Method method) {
        return method.isAnnotationPresent(PostMapping.class) ||
               method.isAnnotationPresent(PutMapping.class) ||
               method.isAnnotationPresent(DeleteMapping.class) ||
               method.isAnnotationPresent(PatchMapping.class);
    }

    private String deriveAction(String methodName) {
        // Convert camelCase to SCREAMING_SNAKE_CASE
        return methodName.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    private String deriveResourceType(String controllerName) {
        return controllerName
                .replace("Controller", "")
                .replace("Admin", "")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toUpperCase();
    }

    private String extractResourceId(HttpServletRequest request) {
        if (request == null) return null;
        String path = request.getRequestURI();
        String[] parts = path.split("/");
        // Look for path segments that look like IDs (hex strings > 10 chars)
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].length() > 10 && parts[i].matches("[a-f0-9]+")) {
                return parts[i];
            }
        }
        return null;
    }

    private String summariseArgs(Object[] args) {
        if (args == null || args.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg == null) continue;
            // Skip request/response objects and security principals
            if (arg instanceof HttpServletRequest) continue;
            if (arg instanceof jakarta.servlet.http.HttpServletResponse) continue;
            if (arg instanceof UserModel) continue;
            if (arg instanceof org.springframework.security.core.userdetails.UserDetails) continue;

            String str = arg.toString();
            // Sanitise sensitive fields
            str = str.replaceAll("(?i)(password|secret|token|key)=[^,\\]]*", "$1=***");
            sb.append(truncate(str, 200)).append("; ");
        }
        return sb.toString();
    }

    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri;
        return request.getRemoteAddr();
    }

    private void saveAuditEvent(SystemAuditEvent event) {
        try {
            auditRepo.save(event);
        } catch (Exception e) {
            // Last resort — log to file if DB save fails
            log.error("AUDIT SAVE FAILED: action={}, user={}, endpoint={}, error={}",
                    event.getAction(), event.getUserEmail(), event.getEndpoint(), e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
