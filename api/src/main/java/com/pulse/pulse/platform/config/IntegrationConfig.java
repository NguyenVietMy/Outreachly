package com.pulse.pulse.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class IntegrationConfig {

    @Bean
    public WebClient gitHubWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .build();
    }

    @Bean
    public WebClient slackWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl("https://slack.com/api")
                .build();
    }

    @Bean
    public WebClient linearWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl("https://api.linear.app")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }
}
