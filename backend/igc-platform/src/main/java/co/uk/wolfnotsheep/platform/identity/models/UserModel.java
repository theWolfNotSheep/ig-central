package co.uk.wolfnotsheep.platform.identity.models;

import co.uk.wolfnotsheep.platform.core.AuthenticatedUser;
import com.mongodb.lang.NonNull;
import org.springframework.data.annotation.Id;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Document("app_security_user_account")
public class UserModel implements UserDetails, AuthenticatedUser {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String password;

    @Indexed
    private UserAccountType accountType;
    private SignUpMethod signUpMethod;

    private Locks accountLocks;

    private LocalDate accountExpiryDate;
    private LocalDate credentialsExpiryDate;

    private String twoFactorSecret;
    private Boolean isTwoFactorEnabled = false;

    private Identity identity;

    private Set<String> roles;
    private Set<String> permissions;

    // ── Profile ──────────────────────────────────
    private String firstName;
    private String lastName;
    private String displayName;
    private String department;
    private String jobTitle;
    private String organisationId;
    private String avatarUrl;

    // ── Access Control ───────────────────────────
    private int sensitivityClearanceLevel; // 0=PUBLIC, 1=INTERNAL, 2=CONFIDENTIAL, 3=RESTRICTED
    private Set<String> taxonomyGrantIds;

    // ── Directory Service ────────────────────────
    private String externalDirectoryId;
    private String externalDirectorySource;
    private Instant lastDirectorySyncAt;

    private LocalDateTime createdDate;
    private Instant lastLoginAt;


    protected UserModel() {}

    // GETTERS
    //-----------------------------------------------------------------------------------------------------------------
    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    @Override
    @NonNull
    public String getUsername() {
        return this.email;
    }

    public UserAccountType getAccountType() {
        return accountType;
    }

    public String getSignUpMethod() {
        return signUpMethod.name();
    }

    public Locks getAccountLocks() {
        return accountLocks;
    }

    public LocalDate getAccountExpiryDate() {
        return accountExpiryDate;
    }

    public LocalDate getCredentialsExpiryDate() {
        return credentialsExpiryDate;
    }

    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public Boolean getTwoFactorEnabled() {
        return isTwoFactorEnabled;
    }

    public Identity getIdentity() {
        return identity;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getDisplayName() { return displayName; }
    public String getDepartment() { return department; }
    public String getJobTitle() { return jobTitle; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getOrganisationId() { return organisationId; }
    public int getSensitivityClearanceLevel() { return sensitivityClearanceLevel; }
    public Set<String> getTaxonomyGrantIds() { return taxonomyGrantIds; }
    public String getExternalDirectoryId() { return externalDirectoryId; }
    public String getExternalDirectorySource() { return externalDirectorySource; }
    public Instant getLastDirectorySyncAt() { return lastDirectorySyncAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }

    @Override
    public boolean isAccountNonLocked() {
        return accountLocks == null || !Boolean.FALSE.equals(accountLocks.getAccountNonLocked());
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountLocks == null || !Boolean.FALSE.equals(accountLocks.getAccountNonExpired());
    }

    @Override
    public boolean isEnabled() {
        return accountLocks == null || Boolean.TRUE.equals(accountLocks.getAccountNonDisabled());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsExpiryDate == null || !LocalDate.now().isAfter(credentialsExpiryDate);
    }

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {

        List<GrantedAuthority> authorities = new ArrayList<>();

        if (roles != null) {
            for (String r : roles) {
                authorities.add((new SimpleGrantedAuthority("ROLE_" + r)));
            }
        }

        if (permissions != null) {
            for (String p : permissions) {
                authorities.add(new SimpleGrantedAuthority("PERM_" + p));
            }
        }
        return authorities;
    }
    //-----------------------------------------------------------------------------------------------------------------


    // SETTERS
    //-----------------------------------------------------------------------------------------------------------------
    public void setId(String id) {
        if (this.id != null) { throw new IllegalStateException("ID is already set on this model"); }
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setAccountType(UserAccountType accountType) {
        this.accountType = accountType;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setSignUpMethod(String signUpMethod) {
        this.signUpMethod = SignUpMethod.valueOf(signUpMethod);
    }

    public void setSignUpMethod(SignUpMethod signUpMethod) {
        this.signUpMethod = signUpMethod;
    }

    public void setAccountLocks(Locks accountLocks) {
        this.accountLocks = accountLocks;
    }

    public void setAccountExpiryDate(LocalDate accountExpiryDate) {
        this.accountExpiryDate = accountExpiryDate;
    }

    public void setCredentialsExpiryDate(LocalDate credentialsExpiryDate) {
        this.credentialsExpiryDate = credentialsExpiryDate;
    }

    public void setTwoFactorSecret(String twoFactorSecret) {
        this.twoFactorSecret = twoFactorSecret;
    }

    public void setTwoFactorEnabled(Boolean twoFactorEnabled) {
        isTwoFactorEnabled = twoFactorEnabled;
    }

    public void setIdentity(Identity identity) {
        this.identity = identity;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = LocalDateTime.ofInstant(createdDate, ZoneId.systemDefault());
    }

    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setDepartment(String department) { this.department = department; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public void setOrganisationId(String organisationId) { this.organisationId = organisationId; }
    public void setSensitivityClearanceLevel(int sensitivityClearanceLevel) { this.sensitivityClearanceLevel = sensitivityClearanceLevel; }
    public void setTaxonomyGrantIds(Set<String> taxonomyGrantIds) { this.taxonomyGrantIds = taxonomyGrantIds; }
    public void setExternalDirectoryId(String externalDirectoryId) { this.externalDirectoryId = externalDirectoryId; }
    public void setExternalDirectorySource(String externalDirectorySource) { this.externalDirectorySource = externalDirectorySource; }
    public void setLastDirectorySyncAt(Instant lastDirectorySyncAt) { this.lastDirectorySyncAt = lastDirectorySyncAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    //-----------------------------------------------------------------------------------------------------------------


    // EQUALS
    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        UserModel userModel = (UserModel) o;
        return Objects.equals(getId(), userModel.getId()) &&
                Objects.equals(getUsername(), userModel.getUsername()) &&
                Objects.equals(getEmail(), userModel.getEmail());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getUsername(), getEmail());
    }
    //-----------------------------------------------------------------------------------------------------------------


    // TO_STRING
    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public String toString() {
        return new StringJoiner(", ", UserModel.class.getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("email='" + email + "'")
                .add("accountType=" + accountType)
                .add("signUpMethod=" + signUpMethod)
                .add("accountLocks=" + accountLocks)
                .add("roles=" + roles)
                .add("permissions=" + permissions)
                .toString();
    }
    //-----------------------------------------------------------------------------------------------------------------


    // INTERNAL_CLASSES
    //-----------------------------------------------------------------------------------------------------------------
    public static class Locks {
        private Boolean accountNonLocked;
        private Boolean accountNonExpired;
        private Boolean accountNonDisabled;
        private Boolean accountNonBanned;
        private Boolean accountDeleted = false;

        public Locks() {
            this.accountNonLocked   = true;
            this.accountNonExpired  = true;
            this.accountNonBanned   = true;
            this.accountNonDisabled = false;
            this.accountDeleted     = false;
        }

        public Boolean getAccountNonLocked() { return accountNonLocked; }
        public Boolean getAccountNonExpired() { return accountNonExpired; }
        public Boolean getAccountNonDisabled() { return accountNonDisabled; }
        public Boolean getAccountNonBanned() { return accountNonBanned; }
        public Boolean getAccountDeleted() { return accountDeleted; }

        public void setAccountNonLocked(Boolean accountNonLocked) { this.accountNonLocked = accountNonLocked; }
        public void setAccountNonExpired(Boolean accountNonExpired) { this.accountNonExpired = accountNonExpired; }
        public void setAccountNonDisabled(Boolean accountNonDisabled) { this.accountNonDisabled = accountNonDisabled; }
        public void setAccountNonBanned(Boolean accountNonBanned) { this.accountNonBanned = accountNonBanned; }
        public void setAccountDeleted(Boolean accountDeleted) { this.accountDeleted = accountDeleted; }

        @Override
        public String toString() {
            return new StringJoiner(", ", Locks.class.getSimpleName() + "[", "]")
                    .add("accountNonLocked=" + accountNonLocked)
                    .add("accountNonExpired=" + accountNonExpired)
                    .add("accountNonDisabled=" + accountNonDisabled)
                    .add("accountNonBanned=" + accountNonBanned)
                    .add("accountDeleted=" + accountDeleted)
                    .toString();
        }
    }

    public static class Identity {
        private String provider;
        private String subject;
        private String issuer;
        private String userName;

        public Identity() {}

        public String getProvider() { return provider; }
        public String getSubject() { return subject; }
        public String getIssuer() { return issuer; }
        public String getUserName() { return userName; }

        public void setProvider(String provider) { this.provider = provider; }
        public void setSubject(String subject) { this.subject = subject; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public void setUserName(String userName) { this.userName = userName; }

        @Override
        public String toString() {
            return new StringJoiner(", ", Identity.class.getSimpleName() + "[", "]")
                    .add("provider='" + provider + "'")
                    .add("subject='" + subject + "'")
                    .add("issuer='" + issuer + "'")
                    .add("userName='" + userName + "'")
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Identity that = (Identity) o;
            return Objects.equals(getProvider(), that.getProvider()) && Objects.equals(getSubject(), that.getSubject());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getProvider(), getSubject());
        }
    }
    //-----------------------------------------------------------------------------------------------------------------

}
