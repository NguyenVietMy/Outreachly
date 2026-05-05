package com.pulse.pulse.personal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.activity.application.DashboardService;
import com.pulse.pulse.personal.domain.DailySuggestion;
import com.pulse.pulse.personal.infrastructure.persistence.DailySuggestionRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserGoalRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserProfileRepository;
import com.pulse.pulse.platform.ai.OpenAiService;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class DailySuggestionServiceTest {

    private DailySuggestionRepository suggestionRepository;
    private OpenAiService openAiService;
    private SimpleMeterRegistry meterRegistry;
    private TestObservationRegistry observationRegistry;
    private DailySuggestionService service;

    @BeforeEach
    void setUp() {
        suggestionRepository = Mockito.mock(DailySuggestionRepository.class);
        openAiService = Mockito.mock(OpenAiService.class);
        meterRegistry = new SimpleMeterRegistry();
        observationRegistry = TestObservationRegistry.create();

        service = new DailySuggestionService(
                suggestionRepository,
                Mockito.mock(UserProfileRepository.class),
                Mockito.mock(UserGoalRepository.class),
                Mockito.mock(DashboardService.class),
                openAiService,
                new ObjectMapper(),
                new PulseObservability(observationRegistry, meterRegistry)
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
        when(openAiService.generateDailySuggestions(any()))
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
}
