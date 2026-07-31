package com.pulse.pulse.personal.application;

import com.pulse.pulse.activity.application.DashboardService;
import com.pulse.pulse.identity.application.UserService;
import com.pulse.pulse.integrations.application.IntegrationService;
import com.pulse.pulse.personal.domain.UserProfile;
import com.pulse.pulse.personal.infrastructure.leetcode.LeetCodeService;
import com.pulse.pulse.personal.infrastructure.persistence.AiTaskRepository;
import com.pulse.pulse.personal.infrastructure.persistence.RoadmapItemRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserGoalRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserProfileRepository;
import com.pulse.pulse.personal.infrastructure.resume.ResumeService;
import com.pulse.pulse.platform.ai.AnthropicService;
import com.pulse.pulse.platform.observability.PulseObservability;
import com.pulse.pulse.platform.util.HashUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersonalServiceResumeScoreTest {

    private UserProfileRepository profileRepo;
    private AnthropicService anthropicService;
    private AiTaskRepository aiTaskRepo;
    private SimpleMeterRegistry meterRegistry;
    private PersonalService service;

    private static final String PROMPT_HASH = "deadbeef";
    private static final String RESUME_TEXT = "Java developer with 3 years experience at Acme Corp";
    private static final String VALID_SCORE_JSON =
            "{\"total_score\":75,\"decision\":\"ADVANCE\",\"summary\":\"Strong candidate\"," +
                    "\"flags\":[],\"sections\":{\"project_complexity\":{\"score\":7,\"max\":10,\"rationale\":\"test\"}}}";

    @BeforeEach
    void setUp() {
        profileRepo = Mockito.mock(UserProfileRepository.class);
        anthropicService = Mockito.mock(AnthropicService.class);
        aiTaskRepo = Mockito.mock(AiTaskRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        TestObservationRegistry observationRegistry = TestObservationRegistry.create();

        when(anthropicService.getResumeScoringPromptHash()).thenReturn(PROMPT_HASH);
        when(aiTaskRepo.findByUserIdAndAxisOrderByOrderIndexAsc(any(), any())).thenReturn(List.of());

        service = new PersonalService(
                profileRepo,
                Mockito.mock(UserGoalRepository.class),
                aiTaskRepo,
                Mockito.mock(RoadmapItemRepository.class),
                Mockito.mock(DashboardService.class),
                Mockito.mock(UserService.class),
                Mockito.mock(IntegrationService.class),
                anthropicService,
                Mockito.mock(LeetCodeService.class),
                Mockito.mock(ResumeService.class),
                new PulseObservability(observationRegistry, meterRegistry),
                Mockito.mock(KnowledgeIndexingService.class)
        );
    }

    @Test
    void scoreResumeReturnsCachedResultWhenHashMatches() {
        String expectedHash = HashUtil.sha256(RESUME_TEXT + "::" + PROMPT_HASH);
        UserProfile profile = UserProfile.builder()
                .userId(1L)
                .resumeText(RESUME_TEXT)
                .resumeContextHash(expectedHash)
                .resumeScoreBreakdown(Map.of("total_score", 75, "decision", "ADVANCE"))
                .axisScores(Map.of("dsa", 0, "projects", 70, "systemDesign", 0, "coreCs", 0, "resume", 75))
                .rawAxisScores(Map.of("dsa", 0, "projects", 70, "systemDesign", 0, "coreCs", 0, "resume", 75))
                .build();
        when(profileRepo.findByUserId(1L)).thenReturn(Optional.of(profile));

        service.scoreResume(1L);

        verify(anthropicService, never()).scoreResume(any());
        assertEquals(1.0, meterRegistry.get("pulse.resume.score.cache")
                .tag("result", "hit").counter().count());
    }

    @Test
    void scoreResumeCallsOpenAiWhenHashMismatches() {
        UserProfile profile = UserProfile.builder()
                .userId(1L)
                .resumeText(RESUME_TEXT)
                .resumeContextHash("stale-hash-from-old-resume")
                .resumeScoreBreakdown(Map.of("total_score", 50))
                .axisScores(Map.of("dsa", 0, "projects", 0, "systemDesign", 0, "coreCs", 0, "resume", 50))
                .rawAxisScores(Map.of("dsa", 0, "projects", 0, "systemDesign", 0, "coreCs", 0, "resume", 50))
                .build();
        when(profileRepo.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(anthropicService.scoreResume(RESUME_TEXT)).thenReturn(Mono.just(VALID_SCORE_JSON));
        when(profileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.scoreResume(1L);

        verify(anthropicService).scoreResume(RESUME_TEXT);
        assertEquals(1.0, meterRegistry.get("pulse.resume.score.cache")
                .tag("result", "miss").counter().count());

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepo).save(captor.capture());
        String expectedHash = HashUtil.sha256(RESUME_TEXT + "::" + PROMPT_HASH);
        assertEquals(expectedHash, captor.getValue().getResumeContextHash());
        assertNotNull(captor.getValue().getResumeScoredAt());
    }

    @Test
    void scoreResumeCallsOpenAiWhenNoPreviousScore() {
        UserProfile profile = UserProfile.builder()
                .userId(1L)
                .resumeText(RESUME_TEXT)
                .axisScores(Map.of("dsa", 0, "projects", 0, "systemDesign", 0, "coreCs", 0, "resume", 0))
                .rawAxisScores(Map.of("dsa", 0, "projects", 0, "systemDesign", 0, "coreCs", 0, "resume", 0))
                .build();
        when(profileRepo.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(anthropicService.scoreResume(RESUME_TEXT)).thenReturn(Mono.just(VALID_SCORE_JSON));
        when(profileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.scoreResume(1L);

        verify(anthropicService).scoreResume(RESUME_TEXT);
        assertEquals(1.0, meterRegistry.get("pulse.resume.score.cache")
                .tag("result", "miss").counter().count());
    }

    @Test
    void scoreResumeCallsOpenAiWhenBreakdownEmpty() {
        String matchingHash = HashUtil.sha256(RESUME_TEXT + "::" + PROMPT_HASH);
        UserProfile profile = UserProfile.builder()
                .userId(1L)
                .resumeText(RESUME_TEXT)
                .resumeContextHash(matchingHash)
                .resumeScoreBreakdown(Map.of())
                .axisScores(Map.of("dsa", 0, "projects", 0, "systemDesign", 0, "coreCs", 0, "resume", 0))
                .rawAxisScores(Map.of("dsa", 0, "projects", 0, "systemDesign", 0, "coreCs", 0, "resume", 0))
                .build();
        when(profileRepo.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(anthropicService.scoreResume(RESUME_TEXT)).thenReturn(Mono.just(VALID_SCORE_JSON));
        when(profileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.scoreResume(1L);

        verify(anthropicService).scoreResume(RESUME_TEXT);
    }

    @Test
    void deleteResumeClearsHashAndTimestamp() {
        UserProfile profile = UserProfile.builder()
                .userId(1L)
                .resumeText("some resume")
                .resumeContextHash("somehash")
                .resumeScoredAt(java.time.OffsetDateTime.now())
                .resumeScoreBreakdown(Map.of("total_score", 75))
                .axisScores(Map.of("dsa", 0, "projects", 50, "systemDesign", 0, "coreCs", 0, "resume", 75))
                .rawAxisScores(Map.of("dsa", 0, "projects", 50, "systemDesign", 0, "coreCs", 0, "resume", 75))
                .build();
        when(profileRepo.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(profileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deleteResume(1L);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepo).save(captor.capture());
        assertNull(captor.getValue().getResumeContextHash());
        assertNull(captor.getValue().getResumeScoredAt());
        assertTrue(captor.getValue().getResumeScoreBreakdown().isEmpty());
    }
}
