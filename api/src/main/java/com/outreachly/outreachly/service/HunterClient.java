package com.outreachly.outreachly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
@RequiredArgsConstructor
@Slf4j
public class HunterClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${HUNTER_API_KEY:}")
    private String apiKey;

    @Value("${HUNTER_BASE_URL:https://api.hunter.io/v2}")
    private String baseUrl;

    @Value("${HUNTER_TIMEOUT_MS:500}")
    private int timeoutMs;

    private RestTemplate buildClient() {
        var rt = new RestTemplate();
        // Keep defaults; short timeouts can be configured with a custom factory if
        // needed.
        return rt;
    }

    public JsonNode emailFinder(String domain, String firstName, String lastName) throws Exception {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/email-finder")
                .queryParam("domain", domain)
                .queryParam("first_name", firstName)
                .queryParam("last_name", lastName)
                .queryParam("api_key", apiKey)
                .build(true)
                .toUri();

        ResponseEntity<String> res = buildClient().getForEntity(uri, String.class);
        if (res.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Hunter finder failed: " + res.getStatusCode());
        }
        return objectMapper.readTree(res.getBody());
    }

    public JsonNode emailVerifier(String email) throws Exception {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/email-verifier")
                .queryParam("email", email)
                .queryParam("api_key", apiKey)
                .build(true)
                .toUri();

        ResponseEntity<String> res = buildClient().getForEntity(uri, String.class);
        if (res.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Hunter verifier failed: " + res.getStatusCode());
        }
        return objectMapper.readTree(res.getBody());
    }
}
