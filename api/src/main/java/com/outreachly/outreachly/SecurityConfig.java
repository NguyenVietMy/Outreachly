package com.outreachly.outreachly;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// for matching actuator endpoints
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;

@Configuration
public class SecurityConfig {

    // Chain 0: management /actuator/** (and your /health) – allow without auth
    @Bean
    @Order(0)
    public SecurityFilterChain managementChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint()) // matches /actuator/** automatically
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    // Chain 1: OAuth2 endpoints - handled by OAuth2Config
    @Bean
    @Order(1)
    public SecurityFilterChain oauth2Chain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/oauth2/**", "/login/**", "/auth/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    // Chain 2: the rest of the app - now uses OAuth2Config
    @Bean
    @Order(2)
    public SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        // This will be overridden by OAuth2Config, but keeping for fallback
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
