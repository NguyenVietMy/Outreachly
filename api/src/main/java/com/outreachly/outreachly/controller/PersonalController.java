package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.dto.*;
import com.outreachly.outreachly.entity.UserGoal;
import com.outreachly.outreachly.entity.UserProfile;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.PersonalService;
import com.outreachly.outreachly.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    private UserProfileDto toDto(UserProfile profile) {
        return new UserProfileDto(
                profile.getProfileMarkdown(),
                profile.getKnowledgeAreas(),
                profile.isOnboardingCompleted()
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

    private User getUser(Authentication authentication) {
        if (authentication == null) return null;
        return userService.findByEmail(authentication.getName());
    }
}
