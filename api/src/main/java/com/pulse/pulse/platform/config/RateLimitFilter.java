package com.pulse.pulse.platform.config;

import com.pulse.pulse.platform.observability.PulseObservability;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> AI_PATHS = Set.of(
            "/api/personal/suggestions/regenerate",
            "/api/personal/insights",
            "/api/personal/resume/score",
            "/api/personal/roadmap/generate"
    );

    private final int generalLimit;
    private final int aiLimit;
    private final PulseObservability observability;
    private final ConcurrentMap<String, Bucket> generalBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> aiBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${pulse.rate-limit.general.requests-per-minute:60}") int generalLimit,
            @Value("${pulse.rate-limit.ai.requests-per-minute:10}") int aiLimit,
            PulseObservability observability) {
        this.generalLimit = generalLimit;
        this.aiLimit = aiLimit;
        this.observability = observability;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/actuator") || path.startsWith("/api/webhooks")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = resolveKey(request);

        if (isAiEndpoint(path)) {
            Bucket aiBucket = aiBuckets.computeIfAbsent(key, k -> createBucket(aiLimit));
            if (!aiBucket.tryConsume(1)) {
                reject(response, "ai");
                return;
            }
        }

        Bucket generalBucket = generalBuckets.computeIfAbsent(key, k -> createBucket(generalLimit));
        if (!generalBucket.tryConsume(1)) {
            reject(response, "general");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            return "session:" + session.getId();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private boolean isAiEndpoint(String path) {
        return AI_PATHS.contains(path);
    }

    private Bucket createBucket(int tokensPerMinute) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(tokensPerMinute).refillGreedy(tokensPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    private void reject(HttpServletResponse response, String tier) throws IOException {
        observability.counter("pulse.ratelimit.rejected", "tier", tier).increment();
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"retryAfterSeconds\":60}");
    }
}
