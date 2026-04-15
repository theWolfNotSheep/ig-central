package co.uk.wolfnotsheep.infrastructure.config;

import co.uk.wolfnotsheep.platform.core.AdminRoleProvider;
import co.uk.wolfnotsheep.platform.products.models.Role;
import co.uk.wolfnotsheep.platform.products.repositories.RoleRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads admin role names from MongoDB (app_roles where adminRole=true).
 * Falls back to "ADMIN" if no roles are configured yet (bootstrap scenario).
 */
@Component
public class DefaultAdminRoleProvider implements AdminRoleProvider {

    private final RoleRepository roleRepository;

    public DefaultAdminRoleProvider(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public String[] getAdminRoleNames() {
        List<Role> adminRoles = roleRepository.findByAdminRoleTrueAndStatus("ACTIVE");
        if (adminRoles.isEmpty()) {
            return new String[]{"ADMIN"};
        }
        return adminRoles.stream().map(Role::getKey).toArray(String[]::new);
    }
}
