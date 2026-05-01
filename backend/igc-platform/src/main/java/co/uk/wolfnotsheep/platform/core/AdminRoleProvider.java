package co.uk.wolfnotsheep.platform.core;

/**
 * Provides the list of admin role names used for security configuration.
 * This decouples platform security config from app-specific role enums.
 */
public interface AdminRoleProvider {

    /**
     * Returns all admin role names that should have access to admin endpoints.
     *
     * @return array of role name strings (e.g. "ADMIN", "FULL_ADMIN")
     */
    String[] getAdminRoleNames();
}
