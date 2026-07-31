package com.pulse.pulse.identity.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security chain for the service-to-service {@code /internal/**} API. Ordered ahead of
 * {@link OAuth2Config}'s chain, and stateless — the session cookie carries no authority here, so
 * these routes are reachable only with the shared secret.
 */
@Configuration
public class InternalApiSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain internalApiFilterChain(
            HttpSecurity http,
            @Value("${pulse.internal.token:}") String internalToken) throws Exception {
        http
                .securityMatcher("/internal/**")
                .cors(cors -> cors.disable())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .anonymous(anonymous -> anonymous.disable())
                .addFilterBefore(new InternalTokenFilter(internalToken), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authz -> authz.anyRequest().authenticated());

        return http.build();
    }
}
