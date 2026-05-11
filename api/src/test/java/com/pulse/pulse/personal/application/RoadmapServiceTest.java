package com.pulse.pulse.personal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.personal.domain.RoadmapItem;
import com.pulse.pulse.personal.domain.UserProfile;
import com.pulse.pulse.personal.infrastructure.persistence.AiTaskRepository;
import com.pulse.pulse.personal.infrastructure.persistence.RoadmapItemRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserGoalRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserProfileRepository;
import com.pulse.pulse.platform.ai.OpenAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoadmapServiceTest {

    private RoadmapItemRepository roadmapRepo;
    private UserProfileRepository profileRepo;
    private OpenAiService openAiService;
    private RoadmapService service;

    @BeforeEach
    void setUp() {
        roadmapRepo = Mockito.mock(RoadmapItemRepository.class);
        profileRepo = Mockito.mock(UserProfileRepository.class);
        openAiService = Mockito.mock(OpenAiService.class);
        service = new RoadmapService(
                roadmapRepo,
                profileRepo,
                Mockito.mock(UserGoalRepository.class),
                Mockito.mock(AiTaskRepository.class),
                openAiService,
                new ObjectMapper()
        );
    }

    @Test
    void reorderRejectsMismatchedIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        RoadmapItem item1 = RoadmapItem.builder().id(id1).userId(1L).title("A").focusRank(0).build();
        RoadmapItem item2 = RoadmapItem.builder().id(id2).userId(1L).title("B").focusRank(1).build();
        when(roadmapRepo.findByUserIdOrderByFocusRankAsc(1L)).thenReturn(List.of(item1, item2));

        assertThrows(IllegalArgumentException.class,
                () -> service.reorderRoadmap(1L, List.of(id1)));
    }

    @Test
    void reorderRejectsExtraIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        RoadmapItem item1 = RoadmapItem.builder().id(id1).userId(1L).title("A").focusRank(0).build();
        RoadmapItem item2 = RoadmapItem.builder().id(id2).userId(1L).title("B").focusRank(1).build();
        when(roadmapRepo.findByUserIdOrderByFocusRankAsc(1L)).thenReturn(List.of(item1, item2));

        assertThrows(IllegalArgumentException.class,
                () -> service.reorderRoadmap(1L, List.of(id1, id2, foreign)));
    }

    @Test
    void reorderAcceptsExactMatch() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        RoadmapItem item1 = RoadmapItem.builder().id(id1).userId(1L).title("A").focusRank(0).build();
        RoadmapItem item2 = RoadmapItem.builder().id(id2).userId(1L).title("B").focusRank(1).build();
        when(roadmapRepo.findByUserIdOrderByFocusRankAsc(1L)).thenReturn(List.of(item1, item2));
        when(roadmapRepo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        List<RoadmapItemView> result = service.reorderRoadmap(1L, List.of(id2, id1));

        assertEquals(0, item2.getFocusRank());
        assertEquals(1, item1.getFocusRank());
    }

    @Test
    void updateStatusRejectsInvalidStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus(1L, UUID.randomUUID(), "invalid"));
    }

    @Test
    void updateStatusAcceptsValidStatuses() {
        UUID id = UUID.randomUUID();
        RoadmapItem item = RoadmapItem.builder().id(id).userId(1L).title("A").status("pending").build();
        when(roadmapRepo.findById(id)).thenReturn(java.util.Optional.of(item));
        when(roadmapRepo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        RoadmapItemView result = service.updateStatus(1L, id, "in_progress");
        assertEquals("in_progress", result.status());

        result = service.updateStatus(1L, id, "completed");
        assertEquals("completed", result.status());

        result = service.updateStatus(1L, id, "pending");
        assertEquals("pending", result.status());
    }

    @Test
    void reorderRejectsDuplicateIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        RoadmapItem item1 = RoadmapItem.builder().id(id1).userId(1L).title("A").focusRank(0).build();
        RoadmapItem item2 = RoadmapItem.builder().id(id2).userId(1L).title("B").focusRank(1).build();
        when(roadmapRepo.findByUserIdOrderByFocusRankAsc(1L)).thenReturn(List.of(item1, item2));

        assertThrows(IllegalArgumentException.class,
                () -> service.reorderRoadmap(1L, List.of(id1, id1, id2)));
    }

    @Test
    void roadmapContextUsesTotalScoreNotOverallScore() {
        UserProfile profile = UserProfile.builder()
                .userId(1L)
                .targetRole("swe_intern")
                .graduationYear(2028)
                .leetcodeUsername("testuser")
                .resumeScoreBreakdown(Map.of("total_score", 72, "decision", "ADVANCE"))
                .build();
        when(profileRepo.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(openAiService.generateRoadmap(any()))
                .thenReturn(Mono.just("[{\"title\":\"A\",\"description\":\"d\",\"phase\":\"Summer\",\"deadline\":\"2026-08-01\",\"rationale\":\"r\"}]"));
        when(roadmapRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.generateRoadmap(1L);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(openAiService).generateRoadmap(captor.capture());
        String context = captor.getValue();

        assertTrue(context.contains("Score: 72/100"), "Should contain 'Score: 72/100' from total_score field");
        assertFalse(context.contains("overallScore"), "Should not reference overallScore");
    }

    private void stubGeneratableProfile() {
        UserProfile profile = UserProfile.builder()
                .userId(1L).targetRole("swe_intern").leetcodeUsername("testuser").build();
        when(profileRepo.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(roadmapRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void generateRoadmapRejectsEmptyLlmResponse() {
        stubGeneratableProfile();
        when(openAiService.generateRoadmap(any())).thenReturn(Mono.just("[]"));

        assertThrows(RuntimeException.class, () -> service.generateRoadmap(1L));
    }

    @Test
    void generateRoadmapFiltersItemsWithoutTitle() {
        stubGeneratableProfile();
        when(openAiService.generateRoadmap(any())).thenReturn(Mono.just(
                "[{\"description\":\"no title\"},{\"title\":\"Valid\",\"description\":\"d\"}]"));

        List<RoadmapItemView> result = service.generateRoadmap(1L);
        assertEquals(1, result.size());
        assertEquals("Valid", result.get(0).title());
    }

    @Test
    void generateRoadmapCapsAtSixItems() {
        stubGeneratableProfile();
        when(openAiService.generateRoadmap(any())).thenReturn(Mono.just(
                "[{\"title\":\"A\"},{\"title\":\"B\"},{\"title\":\"C\"},{\"title\":\"D\"},"
                + "{\"title\":\"E\"},{\"title\":\"F\"},{\"title\":\"G\"},{\"title\":\"H\"}]"));

        List<RoadmapItemView> result = service.generateRoadmap(1L);
        assertEquals(6, result.size());
    }
}
