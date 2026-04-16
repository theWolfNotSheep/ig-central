package co.uk.wolfnotsheep.platform;

import co.uk.wolfnotsheep.platform.identity.utils.JwtUtilsWithCookieSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilsTest {

    private JwtUtilsWithCookieSupport jwtUtils;

    // A valid Base64-encoded 256-bit HMAC key for testing
    private static final String TEST_SECRET = "dGhpcyBpcyBhIHN1cGVyIHNlY3JldCBrZXkgZm9yIHRlc3Rpbmch";
    private static final int TEST_EXPIRATION_MS = 60_000; // 1 minute

    @BeforeEach
    void setUp() throws Exception {
        jwtUtils = new JwtUtilsWithCookieSupport();
        setField(jwtUtils, "jwtSecret", TEST_SECRET);
        setField(jwtUtils, "jwtExpirationMs", TEST_EXPIRATION_MS);
    }

    // -- generateTokenFromUsername --

    @Test
    void generateTokenFromUsername_createsValidToken() {
        UserDetails userDetails = testUser("alice@example.com", "ROLE_USER");

        String token = jwtUtils.generateTokenFromUsername(userDetails);

        assertThat(token).isNotBlank();
        assertThat(jwtUtils.validateJwtToken(token)).isTrue();
    }

    @Test
    void generateTokenFromUsername_setsCorrectSubject() {
        UserDetails userDetails = testUser("bob@example.com", "ROLE_ADMIN");

        String token = jwtUtils.generateTokenFromUsername(userDetails);

        assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("bob@example.com");
    }

    @Test
    void generateTokenFromUsername_includesRolesClaim() {
        UserDetails userDetails = testUser("carol@example.com", "ROLE_USER", "ROLE_ADMIN");

        String token = jwtUtils.generateTokenFromUsername(userDetails);

        // Parse the token and verify roles claim
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        assertThat(roles).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    // -- validateJwtToken --

    @Test
    void validateJwtToken_returnsTrueForValidToken() {
        String token = jwtUtils.generateTokenFromUsername(testUser("user@test.com", "ROLE_USER"));

        assertThat(jwtUtils.validateJwtToken(token)).isTrue();
    }

    @Test
    void validateJwtToken_returnsFalseForTokenSignedWithDifferentKey() throws Exception {
        // Create token with a different secret
        JwtUtilsWithCookieSupport otherUtils = new JwtUtilsWithCookieSupport();
        setField(otherUtils, "jwtSecret", "differentSecretKeyThatIsLongEnoughForHmacSha256AlgorithmRequirements");
        setField(otherUtils, "jwtExpirationMs", 3600000);

        String tokenFromOtherKey = otherUtils.generateTokenFromUsername(testUser("user@test.com", "ROLE_USER"));

        assertThat(jwtUtils.validateJwtToken(tokenFromOtherKey)).isFalse();
    }

    @Test
    void validateJwtToken_returnsFalseForExpiredToken() throws Exception {
        // Create an instance with 0ms expiration so the token is immediately expired
        JwtUtilsWithCookieSupport expiredUtils = new JwtUtilsWithCookieSupport();
        setField(expiredUtils, "jwtSecret", TEST_SECRET);
        setField(expiredUtils, "jwtExpirationMs", 0);

        String token = expiredUtils.generateTokenFromUsername(testUser("user@test.com", "ROLE_USER"));

        // Small sleep to ensure expiry
        Thread.sleep(5);

        assertThat(jwtUtils.validateJwtToken(token)).isFalse();
    }

    @Test
    void validateJwtToken_returnsFalseForMalformedToken() {
        assertThat(jwtUtils.validateJwtToken("not.a.jwt")).isFalse();
    }

    @Test
    void validateJwtToken_returnsFalseForEmptyString() {
        assertThat(jwtUtils.validateJwtToken("")).isFalse();
    }

    // -- getUserNameFromJwtToken --

    @Test
    void getUserNameFromJwtToken_extractsCorrectUsername() {
        String token = jwtUtils.generateTokenFromUsername(testUser("dave@example.com", "ROLE_USER"));

        assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("dave@example.com");
    }

    // -- getJwtFromHeader --

    @Test
    void getJwtFromHeader_extractsTokenFromBearerHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer my.jwt.token");

        String result = jwtUtils.getJwtFromHeader(request);

        assertThat(result).isEqualTo("my.jwt.token");
    }

    @Test
    void getJwtFromHeader_returnsNullWhenNoAuthorizationHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(null);

        assertThat(jwtUtils.getJwtFromHeader(request)).isNull();
    }

    @Test
    void getJwtFromHeader_returnsNullWhenBearerPrefixOnly() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer ");
        when(request.getCookies()).thenReturn(null);

        assertThat(jwtUtils.getJwtFromHeader(request)).isNull();
    }

    @Test
    void getJwtFromHeader_ignoresNonBearerAuthorizationHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");
        when(request.getCookies()).thenReturn(null);

        assertThat(jwtUtils.getJwtFromHeader(request)).isNull();
    }

    @Test
    void getJwtFromHeader_extractsTokenFromAccessTokenCookie() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);
        Cookie accessCookie = new Cookie("access_token", "cookie.jwt.token");
        when(request.getCookies()).thenReturn(new Cookie[]{accessCookie});

        assertThat(jwtUtils.getJwtFromHeader(request)).isEqualTo("cookie.jwt.token");
    }

    @Test
    void getJwtFromHeader_prefersAuthorizationHeaderOverCookie() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer header.jwt.token");
        Cookie accessCookie = new Cookie("access_token", "cookie.jwt.token");
        when(request.getCookies()).thenReturn(new Cookie[]{accessCookie});

        assertThat(jwtUtils.getJwtFromHeader(request)).isEqualTo("header.jwt.token");
    }

    @Test
    void getJwtFromHeader_ignoresUnrelatedCookies() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);
        Cookie otherCookie = new Cookie("session_id", "abc123");
        when(request.getCookies()).thenReturn(new Cookie[]{otherCookie});

        assertThat(jwtUtils.getJwtFromHeader(request)).isNull();
    }

    // -- Helpers --

    private UserDetails testUser(String username, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new User(username, "password", authorities);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
