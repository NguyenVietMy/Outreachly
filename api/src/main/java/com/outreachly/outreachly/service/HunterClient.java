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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class HunterClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${HUNTER_API_KEY:}")
    private String apiKey;

    @Value("${HUNTER_API_KEYS:}")
    private String apiKeysCsv;

    @Value("${HUNTER_BASE_URL:https://api.hunter.io/v2}")
    private String baseUrl;

    @Value("${HUNTER_TIMEOUT_MS:500}")
    private int timeoutMs;

    private final AtomicInteger nextKeyIndex = new AtomicInteger(0);

    private RestTemplate buildClient() {
        var rt = new RestTemplate();
        // Keep defaults; short timeouts can be configured with a custom factory if
        // needed.
        return rt;
    }

    public JsonNode emailFinder(String domain, String firstName, String lastName) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("domain", domain);
        params.put("first_name", firstName);
        params.put("last_name", lastName);
        ResponseEntity<String> res = getWithRotation("/email-finder", params);
        return objectMapper.readTree(res.getBody());
    }

    public JsonNode emailVerifier(String email) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("email", email);
        ResponseEntity<String> res = getWithRotation("/email-verifier", params);
        return objectMapper.readTree(res.getBody());
    }

    public JsonNode domainSearch(String domain, int limit) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("domain", domain);
        params.put("limit", String.valueOf(limit));
        ResponseEntity<String> res = getWithRotation("/domain-search", params);
        return objectMapper.readTree(res.getBody());
    }

    public JsonNode companySearch(String domain) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("domain", domain);
        ResponseEntity<String> res = getWithRotation("/companies/find", params);
        return objectMapper.readTree(res.getBody());
    }

    public JsonNode accountInfo() throws Exception {
        ResponseEntity<String> res = getWithRotation("/account", new HashMap<>());
        return objectMapper.readTree(res.getBody());
    }

    private ResponseEntity<String> getWithRotation(String path, Map<String, String> params) throws Exception {
        List<String> keys = getConfiguredKeys();
        if (keys.isEmpty()) {
            throw new IllegalStateException("No Hunter API key configured");
        }

        int start = Math.floorMod(nextKeyIndex.get(), keys.size());
        for (int i = 0; i < keys.size(); i++) {
            int idx = (start + i) % keys.size();
            String key = keys.get(idx);

            UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + path);
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (e.getValue() != null) {
                    b.queryParam(e.getKey(), e.getValue());
                }
            }
            b.queryParam("api_key", key);
            URI uri = b.build(true).toUri();

            ResponseEntity<String> res = buildClient().getForEntity(uri, String.class);
            if (res.getStatusCode().value() == HttpStatus.OK.value()) {
                nextKeyIndex.set((idx + 1) % keys.size());
                return res;
            }

            if (!isKeyExhaustedStatus(res.getStatusCode().value())) {
                throw new RuntimeException("Hunter request failed: " + res.getStatusCode());
            }

            log.warn("Hunter key limited/unauthorized (status={}), rotating to next key", res.getStatusCode());
        }

        throw new RuntimeException("All Hunter API keys exhausted or unauthorized");
    }

    private List<String> getConfiguredKeys() {
        if (apiKeysCsv != null && !apiKeysCsv.isBlank()) {
            return Arrays.stream(apiKeysCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        if (apiKey != null && !apiKey.isBlank()) {
            return List.of(apiKey);
        }
        return List.of();
    }

    private static boolean isKeyExhaustedStatus(int statusCode) {
        return statusCode == HttpStatus.UNAUTHORIZED.value()
                || statusCode == HttpStatus.PAYMENT_REQUIRED.value()
                || statusCode == HttpStatus.FORBIDDEN.value()
                || statusCode == HttpStatus.TOO_MANY_REQUESTS.value();
    }
}
