package com.pulse.pulse.personal.application;

import com.pulse.pulse.activity.application.DashboardService;
import com.pulse.pulse.integrations.application.IntegrationService;
import com.pulse.pulse.personal.domain.AiTask;
import com.pulse.pulse.personal.domain.DailySuggestion;
import com.pulse.pulse.personal.infrastructure.persistence.AiTaskRepository;
import com.pulse.pulse.personal.infrastructure.persistence.DailySuggestionRepository;
import com.pulse.pulse.personal.infrastructure.persistence.RoadmapItemRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserGoalRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserProfileRepository;
import com.pulse.pulse.platform.ai.AnthropicService;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailySuggestionServiceTest {

    private DailySuggestionRepository suggestionRepository;
    private AnthropicService anthropicService;
    private IntegrationService integrationService;
    private AiTaskRepository aiTaskRepo;
    private RoadmapItemRepository roadmapRepo;
    private SimpleMeterRegistry meterRegistry;
    private TestObservationRegistry observationRegistry;
    private DailySuggestionService service;

    @BeforeEach
    void setUp() {
        suggestionRepository = Mockito.mock(DailySuggestionRepository.class);
        anthropicService = Mockito.mock(AnthropicService.class);
        integrationService = Mockito.mock(IntegrationService.class);
        aiTaskRepo = Mockito.mock(AiTaskRepository.class);
        roadmapRepo = Mockito.mock(RoadmapItemRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        observationRegistry = TestObservationRegistry.create();

        when(aiTaskRepo.findByUserIdOrderByAxisAscOrderIndexAsc(any())).thenReturn(List.of());
        when(roadmapRepo.findByUserIdOrderByFocusRankAsc(any())).thenReturn(List.of());

        service = new DailySuggestionService(
                suggestionRepository,
                Mockito.mock(UserProfileRepository.class),
                Mockito.mock(UserGoalRepository.class),
                Mockito.mock(DashboardService.class),
                anthropicService,
                new PulseObservability(observationRegistry, meterRegistry),
                integrationService,
                aiTaskRepo,
                roadmapRepo
        );
    }

    @Test
    void getSuggestionsForTodayUsesCacheWhenFresh() {
        DailySuggestion suggestion = DailySuggestion.builder()
                .userId(7L)
                .suggestionDate(LocalDate.now())
                .generatedAt(LocalDateTime.now())
                .tasks(List.of(Map.of("title", "Cached task")))
                .build();
        when(suggestionRepository.findByUserIdAndSuggestionDate(7L, LocalDate.now()))
                .thenReturn(Optional.of(suggestion));

        DailySuggestionView view = service.getSuggestionsForToday(7L);

        assertEquals("Cached task", view.tasks().get(0).get("title"));
        assertEquals(1.0, meterRegistry.get("pulse.suggestions.requests")
                .tag("operation", "today")
                .tag("result", "cache_hit")
                .counter()
                .count());
    }

    @Test
    void getSuggestionsForTodayGeneratesAndPersistsWhenCacheMisses() {
        when(suggestionRepository.findByUserIdAndSuggestionDate(eq(7L), any()))
                .thenReturn(Optional.empty());
        when(anthropicService.generateDailySuggestions(any()))
                .thenReturn(Mono.just("[{\"title\":\"Do thing\",\"priority\":0}]"));
        when(suggestionRepository.save(any(DailySuggestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DailySuggestionView view = service.getSuggestionsForToday(7L);

        assertEquals("Do thing", view.tasks().get(0).get("title"));
        assertEquals(1.0, meterRegistry.get("pulse.suggestions.requests")
                .tag("operation", "today")
                .tag("result", "generated")
                .counter()
                .count());
        assertEquals(1L, meterRegistry.get("pulse.suggestions.generate.duration").timer().count());
        assertThat(observationRegistry).hasObservationWithNameEqualTo("pulse.suggestions.generate");
    }

    @Test
    void regenerateSuggestionsReturnsNullDuringCooldown() {
        DailySuggestion suggestion = DailySuggestion.builder()
                .userId(7L)
                .suggestionDate(LocalDate.now())
                .generatedAt(LocalDateTime.now())
                .tasks(List.of(Map.of("title", "Cached task")))
                .build();
        when(suggestionRepository.findByUserIdAndSuggestionDate(7L, LocalDate.now()))
                .thenReturn(Optional.of(suggestion));

        assertNull(service.regenerateSuggestions(7L));

        assertEquals(1.0, meterRegistry.get("pulse.suggestions.requests")
                .tag("operation", "regenerate")
                .tag("result", "cooldown")
                .counter()
                .count());
    }

    @Test
    void buildContextIncludesStudyPlanWhenTasksExist() {
        AiTask task1 = AiTask.builder().userId(7L).axis("dsa").title("Practice binary search").priority(0).completed(false).orderIndex(0).build();
        AiTask task2 = AiTask.builder().userId(7L).axis("coreCs").title("Review OS scheduling").priority(1).completed(false).orderIndex(1).build();
        when(aiTaskRepo.findByUserIdOrderByAxisAscOrderIndexAsc(7L)).thenReturn(List.of(task1, task2));

        when(suggestionRepository.findByUserIdAndSuggestionDate(eq(7L), any())).thenReturn(Optional.empty());
        when(anthropicService.generateDailySuggestions(any()))
                .thenReturn(Mono.just("[{\"title\":\"task\",\"priority\":0}]"));
        when(suggestionRepository.save(any(DailySuggestion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.getSuggestionsForToday(7L);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(anthropicService).generateDailySuggestions(captor.capture());
        String context = captor.getValue();

        assertTrue(context.contains("=== STUDY PLAN (UNCOMPLETED TASKS) ==="));
        assertTrue(context.contains("Practice binary search"));
        assertTrue(context.contains("[HIGH PRIORITY]"));
        assertTrue(context.contains("Review OS scheduling"));
    }

    @Test
    void buildContextIncludesObsidianDiffsWhenConnected() {
        String sampleDiff = "Updated daily note (2026-05-11)\n--- Daily Notes/2026-05-11.md ---\n+Worked on Pulse API\n";
        when(integrationService.getObsidianVaultDiffs(7L)).thenReturn(sampleDiff);

        when(suggestionRepository.findByUserIdAndSuggestionDate(eq(7L), any())).thenReturn(Optional.empty());
        when(anthropicService.generateDailySuggestions(any()))
                .thenReturn(Mono.just("[{\"title\":\"task\",\"priority\":0}]"));
        when(suggestionRepository.save(any(DailySuggestion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.getSuggestionsForToday(7L);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(anthropicService).generateDailySuggestions(captor.capture());
        String context = captor.getValue();

        assertTrue(context.contains("=== OBSIDIAN DAILY NOTES (LAST 7 DAYS) ==="));
        assertTrue(context.contains("Worked on Pulse API"));
    }

    @Test
    void buildContextSkipsObsidianWhenDisconnected() {
        when(integrationService.getObsidianVaultDiffs(7L)).thenReturn(null);

        when(suggestionRepository.findByUserIdAndSuggestionDate(eq(7L), any())).thenReturn(Optional.empty());
        when(anthropicService.generateDailySuggestions(any()))
                .thenReturn(Mono.just("[{\"title\":\"task\",\"priority\":0}]"));
        when(suggestionRepository.save(any(DailySuggestion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.getSuggestionsForToday(7L);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(anthropicService).generateDailySuggestions(captor.capture());

        assertFalse(captor.getValue().contains("=== OBSIDIAN DAILY NOTES"));
    }

    @Test
    void buildContextIncludesStudyTaskIds() {
        java.util.UUID taskId1 = java.util.UUID.randomUUID();
        java.util.UUID taskId2 = java.util.UUID.randomUUID();
        AiTask task1 = AiTask.builder().id(taskId1).userId(7L).axis("dsa").title("Practice binary search").priority(0).completed(false).orderIndex(0).build();
        AiTask task2 = AiTask.builder().id(taskId2).userId(7L).axis("coreCs").title("Review OS scheduling").priority(1).completed(false).orderIndex(1).build();
        when(aiTaskRepo.findByUserIdOrderByAxisAscOrderIndexAsc(7L)).thenReturn(List.of(task1, task2));

        when(suggestionRepository.findByUserIdAndSuggestionDate(eq(7L), any())).thenReturn(Optional.empty());
        when(anthropicService.generateDailySuggestions(any()))
                .thenReturn(Mono.just("[{\"title\":\"task\",\"priority\":0}]"));
        when(suggestionRepository.save(any(DailySuggestion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.getSuggestionsForToday(7L);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(anthropicService).generateDailySuggestions(captor.capture());
        String context = captor.getValue();

        assertTrue(context.contains("(studyTaskId: " + taskId1 + ")"), "Should contain studyTaskId for task1");
        assertTrue(context.contains("(studyTaskId: " + taskId2 + ")"), "Should contain studyTaskId for task2");
    }
}
