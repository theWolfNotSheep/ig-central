package co.uk.wolfnotsheep.infrastructure.config;

import co.uk.wolfnotsheep.platform.core.AdminRoleProvider;
import org.springframework.stereotype.Component;

@Component
public class DefaultAdminRoleProvider implements AdminRoleProvider {

    @Override
    public String[] getAdminRoleNames() {
        return new String[]{"ADMIN"};
    }
}
