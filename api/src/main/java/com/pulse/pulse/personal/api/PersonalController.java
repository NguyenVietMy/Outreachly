package com.pulse.pulse.personal.api;

import com.pulse.pulse.identity.application.CurrentUserView;
import com.pulse.pulse.identity.application.UserService;
import com.pulse.pulse.personal.api.dto.AiTaskDto;
import com.pulse.pulse.personal.api.dto.OnboardingRequest;
import com.pulse.pulse.personal.api.dto.UpdateGoalRequest;
import com.pulse.pulse.personal.api.dto.UserGoalDto;
import com.pulse.pulse.personal.api.dto.UserProfileDto;
import com.pulse.pulse.personal.application.AiTaskView;
import com.pulse.pulse.personal.application.DailySuggestionService;
import com.pulse.pulse.personal.application.DailySuggestionView;
import com.pulse.pulse.personal.application.GoalCommand;
import com.pulse.pulse.personal.application.PersonalService;
import com.pulse.pulse.personal.application.UserGoalView;
import com.pulse.pulse.personal.application.UserProfileView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/personal")
@RequiredArgsConstructor
public class PersonalController {

    private final PersonalService personalService;
    private final DailySuggestionService dailySuggestionService;
    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDto> getProfile(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfileView profile = personalService.getOrCreateProfile(user.id());
        return ResponseEntity.ok(toDto(profile));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileDto> updateProfile(
            @RequestBody UserProfileDto request,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfileView profile = personalService.updateProfile(
                user.id(), request.profileMarkdown(), request.knowledgeAreas());
        return ResponseEntity.ok(toDto(profile));
    }

    @PostMapping("/onboarding")
    public ResponseEntity<UserProfileDto> completeOnboarding(
            @RequestBody OnboardingRequest request,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfileView profile = personalService.completeOnboarding(user.id(), request);
        return ResponseEntity.ok(toDto(profile));
    }

    @GetMapping("/goals")
    public ResponseEntity<List<UserGoalDto>> getGoals(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        List<UserGoalDto> goals = personalService.getGoals(user.id()).stream()
                .map(this::toGoalDto)
                .toList();
        return ResponseEntity.ok(goals);
    }

    @PostMapping("/goals")
    public ResponseEntity<UserGoalDto> createGoal(
            @RequestBody UpdateGoalRequest request,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserGoalView goal = personalService.createGoal(user.id(), new GoalCommand(
                request.title(),
                request.category(),
                request.targetValue(),
                request.currentValue(),
                request.unit(),
                request.deadline(),
                null
        ));
        return ResponseEntity.ok(toGoalDto(goal));
    }

    @PutMapping("/goals/{id}")
    public ResponseEntity<UserGoalDto> updateGoal(
            @PathVariable UUID id,
            @RequestBody UpdateGoalRequest request,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserGoalView goal = personalService.updateGoal(user.id(), id, new GoalCommand(
                request.title(),
                request.category(),
                request.targetValue(),
                request.currentValue(),
                request.unit(),
                request.deadline(),
                request.status()
        ));
        return ResponseEntity.ok(toGoalDto(goal));
    }

    @DeleteMapping("/goals/{id}")
    public ResponseEntity<Void> deleteGoal(
            @PathVariable UUID id,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        personalService.deleteGoal(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/heatmap")
    public ResponseEntity<Map<String, Integer>> getHeatmap(
            @RequestParam(defaultValue = "3") int months,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(personalService.getActivityHeatmap(user.id(), months));
    }

    @PostMapping("/insights")
    public ResponseEntity<Map<String, String>> getInsights(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        String insights = personalService.generateInsights(user.id());
        return ResponseEntity.ok(Map.of("insights", insights));
    }

    @PostMapping("/leetcode/connect")
    public ResponseEntity<UserProfileDto> connectLeetCode(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        String username = request.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        UserProfileView profile = personalService.connectLeetCode(user.id(), username.trim());
        return ResponseEntity.ok(toDto(profile));
    }

    @PostMapping("/leetcode/refresh")
    public ResponseEntity<UserProfileDto> refreshLeetCode(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfileView profile = personalService.refreshLeetCode(user.id());
        return ResponseEntity.ok(toDto(profile));
    }

    @PostMapping("/resume/upload")
    public ResponseEntity<UserProfileDto> uploadResume(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfileView profile = personalService.uploadResume(user.id(), file);
        return ResponseEntity.ok(toDto(profile));
    }

    @PostMapping("/resume/score")
    public ResponseEntity<UserProfileDto> scoreResume(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfileView profile = personalService.scoreResume(user.id());
        return ResponseEntity.ok(toDto(profile));
    }

    @DeleteMapping("/resume")
    public ResponseEntity<UserProfileDto> deleteResume(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfileView profile = personalService.deleteResume(user.id());
        return ResponseEntity.ok(toDto(profile));
    }

    @PostMapping("/questionnaire/{axis}")
    public ResponseEntity<UserProfileDto> submitQuestionnaire(
            @PathVariable String axis,
            @RequestBody Map<String, Object> answers,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        if (!axis.equals("systemDesign") && !axis.equals("coreCs")) {
            return ResponseEntity.badRequest().build();
        }

        UserProfileView profile = personalService.submitQuestionnaire(user.id(), axis, answers);
        return ResponseEntity.ok(toDto(profile));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<AiTaskDto>> getTasks(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        List<AiTaskDto> tasks = personalService.getTasks(user.id()).stream()
                .map(this::toTaskDto)
                .toList();
        return ResponseEntity.ok(tasks);
    }

    @PutMapping("/tasks/{id}/toggle")
    public ResponseEntity<AiTaskDto> toggleTask(
            @PathVariable UUID id,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        AiTaskView task = personalService.toggleTask(user.id(), id);
        return ResponseEntity.ok(toTaskDto(task));
    }

    @PutMapping("/career")
    public ResponseEntity<UserProfileDto> updateCareer(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        String targetRole = (String) request.get("targetRole");
        Integer gradYear = request.get("graduationYear") != null
                ? ((Number) request.get("graduationYear")).intValue() : null;

        UserProfileView profile = personalService.updateCareer(user.id(), targetRole, gradYear);
        return ResponseEntity.ok(toDto(profile));
    }

    @GetMapping("/suggestions/today")
    public ResponseEntity<Map<String, Object>> getSuggestionsToday(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        try {
            DailySuggestionView suggestion = dailySuggestionService.getSuggestionsForToday(user.id());
            return ResponseEntity.ok(toSuggestionResponse(suggestion));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/suggestions/regenerate")
    public ResponseEntity<Map<String, Object>> regenerateSuggestions(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        DailySuggestionView suggestion = dailySuggestionService.regenerateSuggestions(user.id());
        if (suggestion == null) {
            LocalDateTime nextAllowed = dailySuggestionService.getNextAllowedRegenerateTime(user.id());
            return ResponseEntity.status(429).body(Map.of(
                    "error", "Too many regeneration requests",
                    "nextAllowedAt", nextAllowed != null ? nextAllowed.toString() : ""
            ));
        }
        return ResponseEntity.ok(toSuggestionResponse(suggestion));
    }

    private UserProfileDto toDto(UserProfileView profile) {
        return new UserProfileDto(
                profile.profileMarkdown(),
                profile.knowledgeAreas(),
                profile.onboardingCompleted(),
                profile.leetcodeUsername(),
                profile.leetcodeStats(),
                profile.resumeFilename(),
                profile.hasResume(),
                profile.systemDesignAnswers(),
                profile.coreCsAnswers(),
                profile.axisScores(),
                profile.resumeScoreBreakdown(),
                profile.targetRole(),
                profile.graduationYear()
        );
    }

    private UserGoalDto toGoalDto(UserGoalView goal) {
        return new UserGoalDto(
                goal.id(),
                goal.title(),
                goal.category(),
                goal.targetValue(),
                goal.currentValue(),
                goal.unit(),
                goal.deadline(),
                goal.status()
        );
    }

    private AiTaskDto toTaskDto(AiTaskView task) {
        return new AiTaskDto(
                task.id(),
                task.axis(),
                task.sectionId(),
                task.title(),
                task.description(),
                task.completed(),
                task.source(),
                task.priority(),
                task.orderIndex()
        );
    }

    private Map<String, Object> toSuggestionResponse(DailySuggestionView suggestion) {
        return Map.of(
                "date", suggestion.suggestionDate().toString(),
                "generatedAt", suggestion.generatedAt().toString(),
                "tasks", suggestion.tasks()
        );
    }

    private CurrentUserView getUser(Authentication authentication) {
        if (authentication == null) return null;
        return userService.getCurrentUser(authentication.getName());
    }
}
