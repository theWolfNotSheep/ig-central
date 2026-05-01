package co.uk.wolfnotsheep.platform.identity.services;

public interface UserAuthAuditService {

    void recordLoginSuccess(String userId);
    void recordLoginFailure(String userId);
    void recordLogout(String userId);

}
