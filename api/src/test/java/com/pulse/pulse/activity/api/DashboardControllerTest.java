package com.pulse.pulse.activity.api;

import com.pulse.pulse.activity.application.DashboardService;
import com.pulse.pulse.identity.application.CurrentUserView;
import com.pulse.pulse.identity.application.UserService;
import com.pulse.pulse.platform.config.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = DashboardController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private UserService userService;

    @Test
    void getMetrics_authenticated_returns200() throws Exception {
        when(userService.getCurrentUser(any())).thenReturn(testUser());
        when(dashboardService.getMetrics(1L)).thenReturn(Map.of(
                "github", 42L, "obsidian", 10L, "slack", 5L, "linear", 3L));
        when(dashboardService.getEventTypeBreakdown(1L)).thenReturn(Map.of());
        when(dashboardService.getGreeting()).thenReturn("Good morning");
        when(dashboardService.getDateLabel()).thenReturn("Monday, May 19");

        mockMvc.perform(get("/api/dashboard/metrics").with(user("test@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubCommits").value(42))
                .andExpect(jsonPath("$.greeting").value("Good morning"));
    }

    @Test
    void getMetrics_unauthenticated_redirectsOrDenies() throws Exception {
        int status = mockMvc.perform(get("/api/dashboard/metrics"))
                .andReturn().getResponse().getStatus();
        assertTrue(status == 302 || status == 401, "Expected 302 or 401 but got " + status);
    }

    @Test
    void generateDigest_authenticated_returns200() throws Exception {
        when(userService.getCurrentUser(any())).thenReturn(testUser());
        when(dashboardService.generateDigest(1L)).thenReturn("You pushed 5 commits today.");

        mockMvc.perform(post("/api/dashboard/digest").with(user("test@test.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.digest").value("You pushed 5 commits today."));
    }

    private CurrentUserView testUser() {
        return new CurrentUserView(1L, "test@test.com", "Test", "User", null, "USER", null, "UTC", LocalDateTime.now());
    }
}
