package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.dto.*;
import com.outreachly.outreachly.entity.AiTask;
import com.outreachly.outreachly.entity.UserGoal;
import com.outreachly.outreachly.entity.UserProfile;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.PersonalService;
import com.outreachly.outreachly.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/personal")
@RequiredArgsConstructor
public class PersonalController {

    private final PersonalService personalService;
    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDto> getProfile(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfile profile = personalService.getOrCreateProfile(user.getId());
        return ResponseEntity.ok(toDto(profile));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileDto> updateProfile(
            @RequestBody UserProfileDto request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfile profile = personalService.updateProfile(
                user.getId(), request.profileMarkdown(), request.knowledgeAreas());
        return ResponseEntity.ok(toDto(profile));
    }

    @PostMapping("/onboarding")
    public ResponseEntity<UserProfileDto> completeOnboarding(
            @RequestBody OnboardingRequest request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfile profile = personalService.completeOnboarding(user.getId(), request);
        return ResponseEntity.ok(toDto(profile));
    }

    @GetMapping("/goals")
    public ResponseEntity<List<UserGoalDto>> getGoals(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        List<UserGoalDto> goals = personalService.getGoals(user.getId()).stream()
                .map(this::toGoalDto)
                .toList();
        return ResponseEntity.ok(goals);
    }

    @PostMapping("/goals")
    public ResponseEntity<UserGoalDto> createGoal(
            @RequestBody UpdateGoalRequest request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserGoal goal = personalService.createGoal(user.getId(), UserGoal.builder()
                .title(request.title())
                .category(request.category())
                .targetValue(request.targetValue())
                .currentValue(request.currentValue() != null ? request.currentValue() : 0)
                .unit(request.unit() != null ? request.unit() : "items")
                .deadline(request.deadline())
                .build());
        return ResponseEntity.ok(toGoalDto(goal));
    }

    @PutMapping("/goals/{id}")
    public ResponseEntity<UserGoalDto> updateGoal(
            @PathVariable UUID id,
            @RequestBody UpdateGoalRequest request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserGoal updates = UserGoal.builder()
                .title(request.title())
                .category(request.category())
                .targetValue(request.targetValue())
                .currentValue(request.currentValue())
                .unit(request.unit())
                .deadline(request.deadline())
                .status(request.status())
                .build();
        UserGoal goal = personalService.updateGoal(user.getId(), id, updates);
        return ResponseEntity.ok(toGoalDto(goal));
    }

    @DeleteMapping("/goals/{id}")
    public ResponseEntity<Void> deleteGoal(
            @PathVariable UUID id,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        personalService.deleteGoal(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/heatmap")
    public ResponseEntity<Map<String, Integer>> getHeatmap(
            @RequestParam(defaultValue = "3") int months,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(personalService.getActivityHeatmap(user.getId(), months));
    }

    @PostMapping("/insights")
    public ResponseEntity<Map<String, String>> getInsights(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        String insights = personalService.generateInsights(user.getId());
        return ResponseEntity.ok(Map.of("insights", insights));
    }

    @PostMapping("/leetcode/connect")
    public ResponseEntity<UserProfileDto> connectLeetCode(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        String username = request.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        UserProfile profile = personalService.connectLeetCode(user.getId(), username.trim());
        return ResponseEntity.ok(toDto(profile));
    }

    @PostMapping("/leetcode/refresh")
    public ResponseEntity<UserProfileDto> refreshLeetCode(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfile profile = personalService.refreshLeetCode(user.getId());
        return ResponseEntity.ok(toDto(profile));
    }

    @PostMapping("/resume/upload")
    public ResponseEntity<UserProfileDto> uploadResume(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfile profile = personalService.uploadResume(user.getId(), file);
        return ResponseEntity.ok(toDto(profile));
    }

    @PostMapping("/resume/score")
    public ResponseEntity<UserProfileDto> scoreResume(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfile profile = personalService.scoreResume(user.getId());
        return ResponseEntity.ok(toDto(profile));
    }

    @DeleteMapping("/resume")
    public ResponseEntity<UserProfileDto> deleteResume(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        UserProfile profile = personalService.deleteResume(user.getId());
        return ResponseEntity.ok(toDto(profile));
    }

    @PostMapping("/questionnaire/{axis}")
    public ResponseEntity<UserProfileDto> submitQuestionnaire(
            @PathVariable String axis,
            @RequestBody Map<String, Object> answers,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        if (!axis.equals("systemDesign") && !axis.equals("coreCs")) {
            return ResponseEntity.badRequest().build();
        }

        UserProfile profile = personalService.submitQuestionnaire(user.getId(), axis, answers);
        return ResponseEntity.ok(toDto(profile));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<AiTaskDto>> getTasks(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        List<AiTaskDto> tasks = personalService.getTasks(user.getId()).stream()
                .map(this::toTaskDto)
                .toList();
        return ResponseEntity.ok(tasks);
    }

    @PutMapping("/tasks/{id}/toggle")
    public ResponseEntity<AiTaskDto> toggleTask(
            @PathVariable UUID id,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        AiTask task = personalService.toggleTask(user.getId(), id);
        return ResponseEntity.ok(toTaskDto(task));
    }

    @PutMapping("/career")
    public ResponseEntity<UserProfileDto> updateCareer(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        String targetRole = (String) request.get("targetRole");
        Integer gradYear = request.get("graduationYear") != null
                ? ((Number) request.get("graduationYear")).intValue() : null;

        UserProfile profile = personalService.updateCareer(user.getId(), targetRole, gradYear);
        return ResponseEntity.ok(toDto(profile));
    }

    private UserProfileDto toDto(UserProfile profile) {
        return new UserProfileDto(
                profile.getProfileMarkdown(),
                profile.getKnowledgeAreas(),
                profile.isOnboardingCompleted(),
                profile.getLeetcodeUsername(),
                profile.getLeetcodeStats(),
                profile.getResumeFilename(),
                profile.getResumeText() != null && !profile.getResumeText().isBlank(),
                profile.getSystemDesignAnswers(),
                profile.getCoreCsAnswers(),
                profile.getAxisScores(),
                profile.getResumeScoreBreakdown(),
                profile.getTargetRole(),
                profile.getGraduationYear()
        );
    }

    private UserGoalDto toGoalDto(UserGoal goal) {
        return new UserGoalDto(
                goal.getId(),
                goal.getTitle(),
                goal.getCategory(),
                goal.getTargetValue(),
                goal.getCurrentValue(),
                goal.getUnit(),
                goal.getDeadline(),
                goal.getStatus()
        );
    }

    private AiTaskDto toTaskDto(AiTask task) {
        return new AiTaskDto(
                task.getId(),
                task.getAxis(),
                task.getSectionId(),
                task.getTitle(),
                task.getDescription(),
                task.isCompleted(),
                task.getSource(),
                task.getPriority(),
                task.getOrderIndex()
        );
    }

    private User getUser(Authentication authentication) {
        if (authentication == null) return null;
        return userService.findByEmail(authentication.getName());
    }
}
