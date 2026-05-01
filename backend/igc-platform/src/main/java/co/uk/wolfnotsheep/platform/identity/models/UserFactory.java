package co.uk.wolfnotsheep.platform.identity.models;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.UUID;

@Component
public class UserFactory {

    public UserModel createUser(){
        String uuid = UUID.randomUUID().toString();
        UserModel user = new UserModel();
        user.setId(uuid);
        setBaseUserDetail(user);
        user.setCreatedDate(Instant.now());
        return user;
    }

    public UserModel createUser(String email) {
        String uuid = UUID.randomUUID().toString();
        UserModel user = new UserModel();
        user.setId(uuid);
        user.setEmail(email);
        user.setCreatedDate(Instant.now());

        UserModel.Identity identity = new UserModel.Identity();
        identity.setProvider("LOCAL");
        identity.setSubject(uuid);
        identity.setIssuer("LOCAL");
        identity.setUserName(email);

        user.setIdentity(identity);
        setBaseUserDetail(user);

        return user;
    }

    private void setBaseUserDetail(UserModel user) {
        UserModel.Locks locks = new UserModel.Locks();
        setLocksStdPolicy(locks);

        user.setCredentialsExpiryDate(getCredentialsStandardExpiry());
        user.setTwoFactorSecret("");
        user.setTwoFactorEnabled(false);
        user.setIdentity(new UserModel.Identity());
        user.setRoles(new HashSet<>());
        user.setAccountLocks(locks);
        user.setPermissions(new HashSet<>());
    }

    private void setLocksStdPolicy(UserModel.Locks locks) {
        locks.setAccountNonLocked(true);
        locks.setAccountNonExpired(true);
        locks.setAccountNonDisabled(false);
        locks.setAccountNonBanned(true);
    }

    private LocalDate getCredentialsStandardExpiry() {
        return LocalDate.now().plusDays(90);
    }

}
