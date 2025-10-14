package com.outreachly.outreachly.config;

import com.outreachly.outreachly.security.OAuth2AuthenticationFailureHandler;
import com.outreachly.outreachly.security.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class OAuth2Config {

        private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
        private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
        private final CorsConfigurationSource corsConfigurationSource;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(
                                                                org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED)
                                                .maximumSessions(1)
                                                .maxSessionsPreventsLogin(false))
                                .authorizeHttpRequests(authz -> authz
                                                .requestMatchers("/", "/login", "/oauth2/**", "/actuator/**",
                                                                "/api/auth/**")
                                                .permitAll()
                                                .requestMatchers("/api/**")
                                                .authenticated()
                                                .anyRequest().authenticated())
                                .oauth2Login(oauth2 -> oauth2
                                                .successHandler(oAuth2AuthenticationSuccessHandler)
                                                .failureHandler(oAuth2AuthenticationFailureHandler))
                                .logout(logout -> logout
                                                .logoutSuccessUrl("/")
                                                .invalidateHttpSession(true)
                                                .clearAuthentication(true)
                                                .deleteCookies("JSESSIONID"));

                return http.build();
        }

        @Bean
        public OAuth2AuthorizedClientService authorizedClientService(
                        ClientRegistrationRepository clientRegistrationRepository) {
                return new org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService(
                                clientRegistrationRepository);
        }
}
