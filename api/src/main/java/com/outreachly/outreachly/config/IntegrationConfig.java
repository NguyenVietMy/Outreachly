package com.outreachly.outreachly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class IntegrationConfig {

    @Bean
    public WebClient gitHubWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .build();
    }

    @Bean
    public WebClient slackWebClient() {
        return WebClient.builder()
                .baseUrl("https://slack.com/api")
                .build();
    }

    @Bean
    public WebClient linearWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.linear.app")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }
}
