package co.uk.wolfnotsheep.platform.identity.configs;

import co.uk.wolfnotsheep.platform.identity.repositories.MongoUserRepository;
import co.uk.wolfnotsheep.platform.identity.models.UserFactory;
import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.utils.JwtUtils;

import com.mongodb.lang.NonNull;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.*;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(
        org.springframework.security.oauth2.client.registration.ClientRegistrationRepository.class
)
public class OAuth2LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final UserFactory userFactory;
    private final JwtUtils jwtUtils;
    private final MongoUserRepository userRepo;

    public OAuth2LoginSuccessHandler(UserFactory factory, JwtUtils jwtUtils, MongoUserRepository userRepo) {
        this.userFactory = factory;
        this.jwtUtils = jwtUtils;
        this.userRepo = userRepo;
    }

    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication authentication
    ) throws ServletException, IOException {

        log.debug("onAuthenticationSuccess called {}", request.getRequestURI());

        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            throw new IllegalStateException("Expected OAuth2AuthenticationToken but got: " + authentication.getClass().getName());
        }

        OAuthIdentity oauth = extractIdentity(token);

        UserModel user = findOrCreateUser(
                oauth.provider,
                oauth.subject,
                oauth.issuer,
                oauth.email,
                oauth.displayName
        );

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        DefaultOAuth2User springPrincipal = new DefaultOAuth2User(
                new ArrayList<>(authorities),
                oauth.attributes,
                oauth.nameAttributeKey
        );

        Authentication securityAuth = new OAuth2AuthenticationToken(
                springPrincipal,
                springPrincipal.getAuthorities(),
                oauth.provider
        );

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(securityAuth);

        String jwtToken = jwtUtils.generateTokenFromUsername(user);

        String targetUrl = UriComponentsBuilder
                .fromUriString(frontendUrl + "/oauth2/redirect")
                .queryParam("token", jwtToken)
                .build()
                .toUriString();

        this.setAlwaysUseDefaultTargetUrl(true);
        this.setDefaultTargetUrl(targetUrl);

        super.onAuthenticationSuccess(request, response, securityAuth);
    }

    private OAuthIdentity extractIdentity(OAuth2AuthenticationToken token) {

        String provider = token.getAuthorizedClientRegistrationId();
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("OAuth provider/registrationId is required");
        }

        OAuth2User principal = token.getPrincipal();
        if (principal == null) {
            throw new IllegalStateException("OAuth principal is null");
        }

        if (principal instanceof OidcUser oidcUser) {

            String subject = oidcUser.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new IllegalStateException("OIDC subject (sub) is required");
            }

            String issuer = (oidcUser.getIssuer() == null) ? "" : oidcUser.getIssuer().toString();

            Map<String, Object> claims = new LinkedHashMap<>(oidcUser.getClaims());
            String email = toStringSafe(claims.get("email"));
            String name = toStringSafe(claims.get("name"));

            return new OAuthIdentity(provider, subject, issuer, email, name, claims, "sub");
        }

        Map<String, Object> attributes = new LinkedHashMap<>(principal.getAttributes());

        String subject = toStringSafe(attributes.get("id"));
        String nameAttributeKey = "id";

        if (attributes.containsKey("sub")) {
            subject = toStringSafe(attributes.get("sub"));
            nameAttributeKey = "sub";
        }

        if (subject == null || subject.isBlank()) {
            throw new IllegalStateException("Provider user id missing. Expected 'id' or 'sub' in attributes for provider: " + provider);
        }

        String email = toStringSafe(attributes.get("email"));
        String displayName = toStringSafe(attributes.get("name"));

        if ("github".equals(provider)) {
            String login = toStringSafe(attributes.get("login"));
            if (!login.isBlank()) {
                displayName = login;
            }
        }

        return new OAuthIdentity(provider, subject, "", email, displayName, attributes, nameAttributeKey);
    }


    private UserModel findOrCreateUser(String provider, String subject, String issuer, String email, String username) {

        Optional<UserModel> byIdentity = userRepo.findByIdentityProviderAndIdentitySubject(provider, subject);
        if (byIdentity.isPresent()) {
            return byIdentity.get();
        }

        if (email != null && !email.isBlank()) {
            boolean emailExists = userRepo.existsByEmail(email);
            if (emailExists) {
                throw new IllegalStateException("Account exists for this email. Use the original sign-in method.");
            }
        }

        UserModel created = userFactory.createUser();

        UserModel.Identity identity = new UserModel.Identity();
        identity.setProvider(provider);
        identity.setSubject(subject);
        identity.setIssuer(issuer);

        created.setIdentity(identity);
        created.setSignUpMethod(provider);

        if (email != null && !email.isBlank()) {
            created.setEmail(email);
        }

        if (username != null && !username.isBlank()) {
            created.getIdentity().setUserName(username);
        }

        created.setAccountLocks(new UserModel.Locks());

        return userRepo.save(created);
    }

    private String toStringSafe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record OAuthIdentity(
            String provider,
            String subject,
            String issuer,
            String email,
            String displayName,
            Map<String, Object> attributes,
            String nameAttributeKey) {

        @Override
        @NonNull
        public String toString() {
            return new StringJoiner(", ", OAuthIdentity.class.getSimpleName() + "[", "]")
                    .add("provider='" + provider + "'")
                    .add("subject='" + subject + "'")
                    .add("issuer='" + issuer + "'")
                    .add("email='" + email + "'")
                    .add("displayName='" + displayName + "'")
                    .add("attributes=" + attributes)
                    .add("nameAttributeKey='" + nameAttributeKey + "'")
                    .toString();
        }
    }

}
