package co.uk.wolfnotsheep.infrastructure.config;

import co.uk.wolfnotsheep.platform.identity.services.UserAuthAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultUserAuthAuditService implements UserAuthAuditService {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserAuthAuditService.class);

    @Override
    public void recordLoginSuccess(String userId) {
        log.info("Login success: {}", userId);
    }

    @Override
    public void recordLoginFailure(String userId) {
        log.warn("Login failure: {}", userId);
    }

    @Override
    public void recordLogout(String userId) {
        log.info("Logout: {}", userId);
    }
}
