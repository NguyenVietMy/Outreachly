package com.pulse.pulse.personal.api;

import com.pulse.pulse.identity.infrastructure.security.InternalApiSecurityConfig;
import com.pulse.pulse.personal.application.InternalContextService;
import com.pulse.pulse.personal.application.InternalContextService.GithubContext;
import com.pulse.pulse.personal.application.InternalContextService.Goal;
import com.pulse.pulse.personal.application.InternalContextService.ItemsContext;
import com.pulse.pulse.personal.application.InternalContextService.ProfileContext;
import com.pulse.pulse.personal.application.InternalContextService.Repo;
import com.pulse.pulse.personal.application.InternalContextService.RoadmapEntry;
import com.pulse.pulse.personal.application.InternalContextService.Task;
import com.pulse.pulse.platform.config.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = InternalContextController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@Import(InternalApiSecurityConfig.class)
@TestPropertySource(properties = "pulse.internal.token=test-token")
class InternalContextControllerTest {

    private static final String TOKEN = "test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InternalContextService internalContextService;

    @Test
    void profile_withToken_returnsProfileContext() throws Exception {
        when(internalContextService.getProfile(1L)).thenReturn(new ProfileContext(
                "# Profile", "SWE Intern", 2027,
                Map.of("dsa", 3), Map.of("totalSolved", 120),
                "resume body", 11));

        mockMvc.perform(get("/internal/context/profile").param("userId", "1")
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileMarkdown").value("# Profile"))
                .andExpect(jsonPath("$.targetRole").value("SWE Intern"))
                .andExpect(jsonPath("$.graduationYear").value(2027))
                .andExpect(jsonPath("$.axisScores.dsa").value(3))
                .andExpect(jsonPath("$.leetcodeStats.totalSolved").value(120))
                .andExpect(jsonPath("$.resumeText").value("resume body"))
                .andExpect(jsonPath("$.resumeChars").value(11));
    }

    @Test
    void items_withToken_returnsGoalsTasksRoadmap() throws Exception {
        when(internalContextService.getItems(1L)).thenReturn(new ItemsContext(
                List.of(new Goal("Solve 300 problems", 120, 300, "problems", LocalDate.of(2026, 12, 1), "active")),
                List.of(new Task("dsa", "Practice binary search")),
                List.of(new RoadmapEntry("Ship a side project", "build", null, "pending"))));

        mockMvc.perform(get("/internal/context/items").param("userId", "1")
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goals[0].title").value("Solve 300 problems"))
                .andExpect(jsonPath("$.goals[0].currentValue").value(120))
                .andExpect(jsonPath("$.goals[0].deadline").value("2026-12-01"))
                .andExpect(jsonPath("$.tasks[0].axis").value("dsa"))
                .andExpect(jsonPath("$.roadmap[0].phase").value("build"));
    }

    @Test
    void github_withToken_returnsRepos() throws Exception {
        when(internalContextService.getGithub(1L)).thenReturn(new GithubContext(
                List.of(new Repo("viet/pulse", "career platform", "Java", "# Pulse"))));

        mockMvc.perform(get("/internal/context/github").param("userId", "1")
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repos[0].name").value("viet/pulse"))
                .andExpect(jsonPath("$.repos[0].primaryLanguage").value("Java"))
                .andExpect(jsonPath("$.repos[0].readmeContent").value("# Pulse"));
    }

    @Test
    void profile_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/internal/context/profile").param("userId", "1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(internalContextService);
    }

    @Test
    void profile_withWrongToken_returns401() throws Exception {
        mockMvc.perform(get("/internal/context/profile").param("userId", "1")
                        .header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(internalContextService);
    }

    @Test
    void profile_withEmptyToken_returns401() throws Exception {
        mockMvc.perform(get("/internal/context/profile").param("userId", "1")
                        .header("X-Internal-Token", ""))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(internalContextService);
    }

    @Test
    void profile_withSessionUserButNoToken_returns401() throws Exception {
        mockMvc.perform(get("/internal/context/profile").param("userId", "1")
                        .with(user("test@test.com")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(internalContextService);
    }
}
