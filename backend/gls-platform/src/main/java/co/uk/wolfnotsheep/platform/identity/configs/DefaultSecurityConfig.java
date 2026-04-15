package co.uk.wolfnotsheep.platform.identity.configs;

import co.uk.wolfnotsheep.platform.core.AdminRoleProvider;
import co.uk.wolfnotsheep.platform.identity.config.IdentityPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@ConditionalOnWebApplication
@EnableWebSecurity
@EnableMethodSecurity
public class DefaultSecurityConfig {

    Logger log = LogManager.getLogger();

    private final AuthTokenFilter authenticationJwtTokenFilter;
    private final AuthEntryPointJwt unauthorizedHandler;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final AdminRoleProvider adminRoleProvider;

    @Autowired(required = false)
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    public DefaultSecurityConfig(
            AuthEntryPointJwt unauthorizedHandler,
            AuthTokenFilter authTokenFilter,
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            AdminRoleProvider adminRoleProvider) {
        this.unauthorizedHandler = unauthorizedHandler;
        this.authenticationJwtTokenFilter = authTokenFilter;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.adminRoleProvider = adminRoleProvider;
    }


    @Bean
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/auth/public/**")
                        .ignoringRequestMatchers("/api/internal/**")
                        .ignoringRequestMatchers("/api/drives/google/callback")
                        .ignoringRequestMatchers("/api/auth/public/google/**")
                        .ignoringRequestMatchers(IdentityPaths.VERIFY)
                        .ignoringRequestMatchers("/actuator/**")
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
        );
        http.headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
        );
        http.cors(Customizer.withDefaults());
        http.authorizeHttpRequests((requests)
                        -> requests
                        .requestMatchers(IdentityPaths.CFG_ADMIN_PATHS_ALL).hasAnyRole(adminRoleProvider.getAdminRoleNames())
                        .requestMatchers(IdentityPaths.VERIFY).permitAll()
                        .requestMatchers(IdentityPaths.VERIFY + "/**").permitAll()
                        .requestMatchers(IdentityPaths.CFG_CSRF_TOKEN).permitAll()
                        .requestMatchers(IdentityPaths.CFG_PUBLIC_AUTH).permitAll()
                        .requestMatchers(IdentityPaths.CFG_OAUTH2).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().permitAll());

        if (oAuth2LoginSuccessHandler != null) {
            http.oauth2Login(oauth2 -> oauth2.successHandler(oAuth2LoginSuccessHandler));
        }

        http.exceptionHandling(exception
                -> exception.authenticationEntryPoint(unauthorizedHandler));
        http.addFilterBefore(authenticationJwtTokenFilter,
                UsernamePasswordAuthenticationFilter.class);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(false);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

}
