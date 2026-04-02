package co.uk.wolfnotsheep.platform.identity.controllers;

import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.services.UserAuthAuditService;
import co.uk.wolfnotsheep.platform.identity.utils.JwtUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AuthController {

    private static final Logger log = LogManager.getLogger(AuthController.class);

    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final UserAuthAuditService auditService;

    public AuthController(JwtUtils jwtUtils, AuthenticationManager authenticationManager, UserAuthAuditService auditService) {
        this.jwtUtils = jwtUtils;
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
    }


    @PostMapping("/api/auth/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {

        Authentication authentication;

        try {
            authentication = authenticationManager
                    .authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    loginRequest.username(), loginRequest.password()
                            )
                    );

        } catch (UsernameNotFoundException e) {
            return errorResponse("USER_NOT_FOUND", HttpStatus.NOT_FOUND);

        } catch (DisabledException e) {
            return errorResponse("ACCOUNT_DISABLED", HttpStatus.FORBIDDEN);

        } catch (LockedException e) {
            return errorResponse("ACCOUNT_LOCKED", HttpStatus.FORBIDDEN);

        } catch (BadCredentialsException e) {
            return errorResponse("Bad credentials", HttpStatus.UNAUTHORIZED);

        } catch (AuthenticationException e) {
            return errorResponse("Bad credentials", HttpStatus.UNAUTHORIZED);
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserModel userModel = (UserModel) authentication.getPrincipal();

        String jwtToken = jwtUtils.generateTokenFromUsername(userModel);

        List<String> roles = userModel.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        ResponseCookie accessCookie = ResponseCookie.from("access_token", jwtToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(172800)
                .build();

        LoginResponse response = new LoginResponse(
                userModel.getUsername(),
                roles,
                jwtToken,
                userModel.getAccountType() != null ? userModel.getAccountType().name() : null
        );

        auditService.recordLoginSuccess(userModel.getId());

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .body(response);
    }


    @PostMapping("/api/auth/logout")
    public ResponseEntity<?> logoutUser(HttpServletRequest req, HttpServletResponse resp) {

        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;

        if (principal instanceof UserModel user) {
            auditService.recordLogout(user.getId());
        }

        ResponseCookie clearAccessToken = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        ResponseCookie clearXsrf = ResponseCookie.from("XSRF-TOKEN", "")
                .httpOnly(false)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearAccessToken.toString())
                .header(HttpHeaders.SET_COOKIE, clearXsrf.toString())
                .build();
    }


    private ResponseEntity<Object> errorResponse(String message, HttpStatus status) {
        Map<String, Object> map = new HashMap<>();
        map.put("message", message);
        map.put("status", false);
        return new ResponseEntity<>(map, status);
    }


    public record LoginRequest(
            String username,
            String password
    ) {}

    public record LoginResponse(
            String username,
            List<String> roles,
            String jwtToken,
            String accountType
    ) {}
}
