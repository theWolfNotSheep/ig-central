package co.uk.wolfnotsheep.platform.identity.configs;

import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.services.impl.UserLoadingService;
import co.uk.wolfnotsheep.platform.identity.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserLoadingService userLoader;

    private static final Logger log = LogManager.getLogger(AuthTokenFilter.class);

    public AuthTokenFilter(JwtUtils jwtUtils, UserLoadingService userLoader) {
        this.jwtUtils = jwtUtils;
        this.userLoader = userLoader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        log.debug("AuthTokenFilter called for URI: {}", request.getRequestURI());

        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                UserDetails userDetails = userLoader.loadUserByUsername(username);

                if (userDetails instanceof UserModel user) {
                    if (!user.isAccountNonLocked()) {
                        log.warn("JWT request rejected: account locked for user {}", username);
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "ACCOUNT_LOCKED");
                        return;
                    }
                    if (!user.isEnabled()) {
                        log.warn("JWT request rejected: account disabled for user {}", username);
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "ACCOUNT_DISABLED");
                        return;
                    }
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails,
                                null,
                                userDetails.getAuthorities());
                log.debug("Roles from JWT: {}", userDetails.getAuthorities());

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e);
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/");
    }

    private String parseJwt(HttpServletRequest request) {
        String jwt = jwtUtils.getJwtFromHeader(request);
        log.debug("AuthTokenFilter.java: {}", jwt);
        return jwt;
    }

}
