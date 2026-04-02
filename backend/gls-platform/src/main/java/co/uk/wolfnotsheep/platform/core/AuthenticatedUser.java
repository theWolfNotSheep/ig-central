package co.uk.wolfnotsheep.platform.core;

/**
 * Minimal interface for authenticated user identity.
 * Allows platform modules to extract user ID without depending on
 * the full UserModel class from the identity module.
 */
public interface AuthenticatedUser {

    String getId();
}
