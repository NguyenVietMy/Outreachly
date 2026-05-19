package com.pulse.pulse.platform.config;

import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitFilterTest {

    private SimpleMeterRegistry meterRegistry;
    private PulseObservability observability;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        observability = new PulseObservability(TestObservationRegistry.create(), meterRegistry);
        filterChain = Mockito.mock(FilterChain.class);
    }

    @Test
    void allowsRequestsWithinGeneralLimit() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(5, 2, observability);

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = doFilter(filter, "/api/personal/profile", "session-1");
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void rejectsRequestsExceedingGeneralLimit() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(3, 10, observability);

        for (int i = 0; i < 3; i++) {
            doFilter(filter, "/api/personal/profile", "session-1");
        }

        MockHttpServletResponse response = doFilter(filter, "/api/personal/profile", "session-1");
        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
    }

    @Test
    void aiEndpointHasStricterLimit() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(60, 2, observability);

        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse response = doFilter(filter, "/api/personal/suggestions/regenerate", "session-1");
            assertEquals(200, response.getStatus());
        }

        MockHttpServletResponse response = doFilter(filter, "/api/personal/suggestions/regenerate", "session-1");
        assertEquals(429, response.getStatus());
    }

    @Test
    void webhookEndpointsAreNotRateLimited() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(1, 1, observability);

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = doFilter(filter, "/api/webhooks/github", "session-1");
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void recordsMetricsOnRejection() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(1, 1, observability);

        doFilter(filter, "/api/personal/profile", "session-1");
        doFilter(filter, "/api/personal/profile", "session-1");

        assertEquals(1.0, meterRegistry.get("pulse.ratelimit.rejected")
                .tag("tier", "general")
                .counter()
                .count());
    }

    private MockHttpServletResponse doFilter(RateLimitFilter filter, String uri, String sessionId)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setSession(new MockHttpSession(null, sessionId));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);
        return response;
    }
}
