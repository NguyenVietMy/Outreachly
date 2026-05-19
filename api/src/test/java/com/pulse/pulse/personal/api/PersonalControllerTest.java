package com.pulse.pulse.personal.api;

import com.pulse.pulse.identity.application.CurrentUserView;
import com.pulse.pulse.identity.application.UserService;
import com.pulse.pulse.personal.application.DailySuggestionService;
import com.pulse.pulse.personal.application.DailySuggestionView;
import com.pulse.pulse.personal.application.PersonalService;
import com.pulse.pulse.personal.application.RoadmapService;
import com.pulse.pulse.personal.application.UserProfileView;
import com.pulse.pulse.platform.config.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = PersonalController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
class PersonalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonalService personalService;

    @MockitoBean
    private DailySuggestionService dailySuggestionService;

    @MockitoBean
    private RoadmapService roadmapService;

    @MockitoBean
    private UserService userService;

    @Test
    void getProfile_authenticated_returns200() throws Exception {
        when(userService.getCurrentUser(any())).thenReturn(testUser());
        when(personalService.getOrCreateProfile(1L)).thenReturn(testProfile());

        mockMvc.perform(get("/api/personal/profile").with(user("test@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetRole").value("SWE Intern"))
                .andExpect(jsonPath("$.onboardingCompleted").value(true));
    }

    @Test
    void getProfile_unauthenticated_redirectsOrDenies() throws Exception {
        int status = mockMvc.perform(get("/api/personal/profile"))
                .andReturn().getResponse().getStatus();
        assertTrue(status == 302 || status == 401, "Expected 302 or 401 but got " + status);
    }

    @Test
    void getSuggestions_authenticated_returns200() throws Exception {
        when(userService.getCurrentUser(any())).thenReturn(testUser());
        when(dailySuggestionService.getSuggestionsForToday(1L)).thenReturn(testSuggestion());

        mockMvc.perform(get("/api/personal/suggestions/today").with(user("test@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").exists())
                .andExpect(jsonPath("$.tasks").isArray());
    }

    @Test
    void getGoals_authenticated_returns200() throws Exception {
        when(userService.getCurrentUser(any())).thenReturn(testUser());

        mockMvc.perform(get("/api/personal/goals").with(user("test@test.com")))
                .andExpect(status().isOk());
    }

    private CurrentUserView testUser() {
        return new CurrentUserView(1L, "test@test.com", "Test", "User", null, "USER", null, "UTC", LocalDateTime.now());
    }

    private UserProfileView testProfile() {
        return new UserProfileView("# Profile", true, null, null, null, false, null, null, null, null, "SWE Intern", 2027);
    }

    private DailySuggestionView testSuggestion() {
        return new DailySuggestionView(
                LocalDate.now(),
                LocalDateTime.now(),
                List.of(Map.of("title", "Practice binary search", "priority", 0))
        );
    }
}
