package co.uk.wolfnotsheep.platform.identity.controllers;

import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.services.UserAuthAuditService;
import co.uk.wolfnotsheep.platform.identity.utils.JwtUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.security.core.AuthenticationException;


import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AuthController {


    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final UserAuthAuditService auditService;

    public AuthController(JwtUtils jwtUtils, AuthenticationManager authenticationManager, UserAuthAuditService auditService) {
        this.jwtUtils = jwtUtils;
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
    }


    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {

        Authentication authentication;

        try {

            authentication = authenticationManager
                    .authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    loginRequest.username(), loginRequest.password()
                            )
                    );

        } catch (AuthenticationException e) {

            Map<String, Object> map = new HashMap<>();
            map.put("message", "Bad credentials");
            map.put("status", false);

            return new ResponseEntity<Object>(map, HttpStatus.NOT_FOUND);

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

        LoginResponse response = new LoginResponse(userModel.getUsername(), roles, jwtToken);

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .body(response);

    }


    public record LoginRequest(
            String username,
            String password
    ) {}

    public record LoginResponse(
            String username,
            List<String> roles,
            String jwtToken
    ) {}
}
