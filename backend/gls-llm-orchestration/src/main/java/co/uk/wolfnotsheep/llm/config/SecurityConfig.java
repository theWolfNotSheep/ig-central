package co.uk.wolfnotsheep.llm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the LLM worker.
 * The LLM worker is an internal service — no user-facing auth needed.
 * Permits all requests (internal API calls from the pipeline engine + actuator).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain llmSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
