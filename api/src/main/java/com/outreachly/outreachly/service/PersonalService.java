package com.outreachly.outreachly.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.outreachly.outreachly.dto.OnboardingRequest;
import com.outreachly.outreachly.entity.IntegrationEvent;
import com.outreachly.outreachly.entity.UserGoal;
import com.outreachly.outreachly.entity.UserProfile;
import com.outreachly.outreachly.repository.IntegrationEventRepository;
import com.outreachly.outreachly.repository.UserGoalRepository;
import com.outreachly.outreachly.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalService {

    private final UserProfileRepository profileRepo;
    private final UserGoalRepository goalRepo;
    private final IntegrationEventRepository eventRepo;
    private final OpenAiService openAiService;
    private final LeetCodeService leetCodeService;
    private final ResumeService resumeService;

    public UserProfile getOrCreateProfile(Long userId) {
        return profileRepo.findByUserId(userId)
                .orElseGet(() -> profileRepo.save(UserProfile.builder()
                        .userId(userId)
                        .build()));
    }

    @Transactional
    public UserProfile updateProfile(Long userId, String profileMarkdown,
                                      List<Map<String, Object>> knowledgeAreas) {
        UserProfile profile = getOrCreateProfile(userId);
        if (profileMarkdown != null) {
            profile.setProfileMarkdown(profileMarkdown);
        }
        if (knowledgeAreas != null) {
            profile.setKnowledgeAreas(knowledgeAreas);
        }
        return profileRepo.save(profile);
    }

    @Transactional
    public UserProfile completeOnboarding(Long userId, OnboardingRequest request) {
        UserProfile profile = getOrCreateProfile(userId);

        profile.setKnowledgeAreas(request.knowledgeAreas());
        profile.setProfileMarkdown(generateProfileMarkdown(request));
        profile.setOnboardingCompleted(true);
        profileRepo.save(profile);

        if (request.goals() != null) {
            for (OnboardingRequest.GoalInput goal : request.goals()) {
                goalRepo.save(UserGoal.builder()
                        .userId(userId)
                        .title(goal.title())
                        .category(goal.category())
                        .targetValue(goal.targetValue())
                        .unit(goal.unit() != null ? goal.unit() : "items")
                        .build());
            }
        }

        return profile;
    }

    private String generateProfileMarkdown(OnboardingRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Personal Profile\n\n");

        if (request.bio() != null && !request.bio().isBlank()) {
            sb.append("## Background\n");
            sb.append(request.bio()).append("\n\n");
        }

        if (request.knowledgeAreas() != null && !request.knowledgeAreas().isEmpty()) {
            List<Map<String, Object>> strong = new ArrayList<>();
            List<Map<String, Object>> weak = new ArrayList<>();

            for (Map<String, Object> area : request.knowledgeAreas()) {
                int level = ((Number) area.getOrDefault("level", 1)).intValue();
                if (level >= 4) strong.add(area);
                else if (level <= 2) weak.add(area);
            }

            if (!strong.isEmpty()) {
                sb.append("## Strengths\n");
                for (Map<String, Object> area : strong) {
                    sb.append("- ").append(area.get("area"))
                            .append(" (").append(area.get("level")).append("/5)\n");
                }
                sb.append("\n");
            }

            if (!weak.isEmpty()) {
                sb.append("## Areas to Improve\n");
                for (Map<String, Object> area : weak) {
                    sb.append("- ").append(area.get("area"))
                            .append(" (").append(area.get("level")).append("/5)\n");
                }
                sb.append("\n");
            }
        }

        if (request.goals() != null && !request.goals().isEmpty()) {
            sb.append("## Current Goals\n");
            for (OnboardingRequest.GoalInput goal : request.goals()) {
                sb.append("- ").append(goal.title());
                if (goal.targetValue() != null) {
                    sb.append(" (target: ").append(goal.targetValue())
                            .append(" ").append(goal.unit() != null ? goal.unit() : "items").append(")");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    // --- Career Target ---

    @Transactional
    public UserProfile updateCareer(Long userId, String targetRole, Integer graduationYear) {
        UserProfile profile = getOrCreateProfile(userId);
        if (targetRole != null) {
            profile.setTargetRole(targetRole);
        }
        if (graduationYear != null) {
            profile.setGraduationYear(graduationYear);
        }
        return profileRepo.save(profile);
    }

    // --- LeetCode ---

    @Transactional
    public UserProfile connectLeetCode(Long userId, String username) {
        Map<String, Object> stats = leetCodeService.fetchStats(username);
        UserProfile profile = getOrCreateProfile(userId);
        profile.setLeetcodeUsername(username);
        profile.setLeetcodeStats(stats);
        profile.setLeetcodeFetchedAt(OffsetDateTime.now());
        updateAxisScore(profile, "dsa", ((Number) stats.get("dsaScore")).intValue());
        return profileRepo.save(profile);
    }

    @Transactional
    public UserProfile refreshLeetCode(Long userId) {
        UserProfile profile = getOrCreateProfile(userId);
        if (profile.getLeetcodeUsername() == null) {
            throw new RuntimeException("LeetCode not connected");
        }
        Map<String, Object> stats = leetCodeService.fetchStats(profile.getLeetcodeUsername());
        profile.setLeetcodeStats(stats);
        profile.setLeetcodeFetchedAt(OffsetDateTime.now());
        updateAxisScore(profile, "dsa", ((Number) stats.get("dsaScore")).intValue());
        return profileRepo.save(profile);
    }

    // --- Resume ---

    @Transactional
    public UserProfile uploadResume(Long userId, MultipartFile file) {
        String text = resumeService.extractText(file);
        UserProfile profile = getOrCreateProfile(userId);
        profile.setResumeText(text);
        profile.setResumeFilename(file.getOriginalFilename());
        profile.setResumeUploadedAt(OffsetDateTime.now());
        profileRepo.save(profile);

        try {
            return applyResumeScore(profile);
        } catch (Exception e) {
            log.warn("Resume scoring failed on upload, score will be 0 until re-scored", e);
            return profile;
        }
    }

    @Transactional
    public UserProfile scoreResume(Long userId) {
        UserProfile profile = getOrCreateProfile(userId);
        if (profile.getResumeText() == null || profile.getResumeText().isBlank()) {
            throw new RuntimeException("No resume uploaded");
        }
        return applyResumeScore(profile);
    }

    private UserProfile applyResumeScore(UserProfile profile) {
        String raw = openAiService.scoreResume(profile.getResumeText()).block();
        ObjectMapper mapper = new ObjectMapper();
        try {
            Map<String, Object> breakdown = mapper.readValue(raw, new TypeReference<>() {});
            profile.setResumeScoreBreakdown(breakdown);

            Number totalScore = (Number) breakdown.get("total_score");
            int score = totalScore != null ? totalScore.intValue() : 0;
            updateAxisScore(profile, "resume", score);

            int projectScore = extractProjectScore(breakdown);
            updateAxisScore(profile, "projects", projectScore);

            return profileRepo.save(profile);
        } catch (Exception e) {
            log.error("Failed to parse resume scoring response: {}", raw, e);
            throw new RuntimeException("Failed to parse resume score", e);
        }
    }

    @SuppressWarnings("unchecked")
    private int extractProjectScore(Map<String, Object> breakdown) {
        try {
            Map<String, Object> sections = (Map<String, Object>) breakdown.get("sections");
            if (sections == null) return 0;
            Map<String, Object> complexity = (Map<String, Object>) sections.get("project_complexity");
            if (complexity == null) return 0;
            Number score = (Number) complexity.get("score");
            Number max = (Number) complexity.get("max");
            if (score == null || max == null || max.intValue() == 0) return 0;
            return (int) Math.round((double) score.intValue() / max.intValue() * 100);
        } catch (Exception e) {
            log.warn("Could not extract project score from breakdown", e);
            return 0;
        }
    }

    @Transactional
    public UserProfile deleteResume(Long userId) {
        UserProfile profile = getOrCreateProfile(userId);
        profile.setResumeText("");
        profile.setResumeFilename(null);
        profile.setResumeUploadedAt(null);
        profile.setResumeScoreBreakdown(Map.of());
        updateAxisScore(profile, "resume", 0);
        updateAxisScore(profile, "projects", 0);
        return profileRepo.save(profile);
    }

    // --- Questionnaires ---

    @Transactional
    public UserProfile submitQuestionnaire(Long userId, String axis, Map<String, Object> payload) {
        UserProfile profile = getOrCreateProfile(userId);

        if (isNewFormatSection(payload)) {
            Map<String, Object> merged = mergeSection(axis, profile, payload);
            int score = computeNewFormatScore(merged);

            if ("systemDesign".equals(axis)) {
                profile.setSystemDesignAnswers(merged);
                updateAxisScore(profile, "systemDesign", score);
            } else if ("coreCs".equals(axis)) {
                profile.setCoreCsAnswers(merged);
                updateAxisScore(profile, "coreCs", score);
            }
        } else {
            int score = computeLegacyScore(payload);

            if ("systemDesign".equals(axis)) {
                profile.setSystemDesignAnswers(payload);
                updateAxisScore(profile, "systemDesign", score);
            } else if ("coreCs".equals(axis)) {
                profile.setCoreCsAnswers(payload);
                updateAxisScore(profile, "coreCs", score);
            }
        }

        return profileRepo.save(profile);
    }

    private boolean isNewFormatSection(Map<String, Object> payload) {
        return payload != null && payload.containsKey("sectionId") && payload.containsKey("subskills");
    }

    private boolean isNewFormatAssessment(Map<String, Object> payload) {
        return payload != null && payload.containsKey("sections") && payload.containsKey("axisId");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeSection(String axis, UserProfile profile, Map<String, Object> incomingSection) {
        Map<String, Object> existing = "systemDesign".equals(axis)
                ? profile.getSystemDesignAnswers()
                : profile.getCoreCsAnswers();

        Map<String, Object> assessment;
        if (isNewFormatAssessment(existing)) {
            assessment = new HashMap<>(existing);
            assessment.put("sections", new ArrayList<>((List<?>) existing.get("sections")));
        } else {
            assessment = new HashMap<>();
            assessment.put("axisId", "systemDesign".equals(axis) ? "system_design" : "cs_fundamentals");
            assessment.put("completedAt", null);
            assessment.put("sections", new ArrayList<>());
        }

        List<Map<String, Object>> sections = (List<Map<String, Object>>) assessment.get("sections");
        String incomingSectionId = (String) incomingSection.get("sectionId");
        sections.removeIf(s -> incomingSectionId.equals(s.get("sectionId")));
        sections.add(new HashMap<>(incomingSection));

        int expectedSections = "systemDesign".equals(axis) ? 11 : 8;
        if (sections.size() >= expectedSections) {
            assessment.put("completedAt", OffsetDateTime.now().toString());
        }

        return assessment;
    }

    @SuppressWarnings("unchecked")
    private int computeNewFormatScore(Map<String, Object> assessment) {
        List<?> sections = (List<?>) assessment.get("sections");
        if (sections == null || sections.isEmpty()) return 0;

        int totalTiers = 0;
        int totalSubskills = 0;
        for (Object sec : sections) {
            Map<String, Object> section = (Map<String, Object>) sec;
            List<?> subskills = (List<?>) section.get("subskills");
            if (subskills == null) continue;
            for (Object ss : subskills) {
                Map<String, Object> subskill = (Map<String, Object>) ss;
                Number tier = (Number) subskill.get("tier");
                if (tier != null) {
                    totalTiers += tier.intValue();
                    totalSubskills++;
                }
            }
        }
        return totalSubskills > 0
                ? (int) Math.round((double) totalTiers / (totalSubskills * 4) * 100)
                : 0;
    }

    private int computeLegacyScore(Map<String, Object> answers) {
        if (answers == null || answers.isEmpty()) return 0;

        Object answersObj = answers.get("answers");
        if (!(answersObj instanceof List<?> answerList) || answerList.isEmpty()) return 0;

        int totalPoints = 0;
        int maxPoints = answerList.size() * 3;

        for (Object a : answerList) {
            if (a instanceof Map<?, ?> answer) {
                Object val = answer.get("value");
                if (val instanceof Number n) {
                    totalPoints += n.intValue();
                }
            }
        }

        return maxPoints > 0 ? (int) Math.round((double) totalPoints / maxPoints * 100) : 0;
    }

    private void updateAxisScore(UserProfile profile, String axis, int score) {
        Map<String, Object> scores = new HashMap<>(profile.getAxisScores());
        scores.put(axis, score);
        profile.setAxisScores(scores);
    }

    // --- Goals ---

    public List<UserGoal> getGoals(Long userId) {
        return goalRepo.findByUserId(userId);
    }

    @Transactional
    public UserGoal createGoal(Long userId, UserGoal goal) {
        goal.setUserId(userId);
        return goalRepo.save(goal);
    }

    @Transactional
    public UserGoal updateGoal(Long userId, UUID goalId, UserGoal updates) {
        UserGoal goal = goalRepo.findById(goalId)
                .orElseThrow(() -> new RuntimeException("Goal not found"));
        if (!goal.getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized");
        }

        if (updates.getTitle() != null) goal.setTitle(updates.getTitle());
        if (updates.getCategory() != null) goal.setCategory(updates.getCategory());
        if (updates.getTargetValue() != null) goal.setTargetValue(updates.getTargetValue());
        if (updates.getCurrentValue() != null) goal.setCurrentValue(updates.getCurrentValue());
        if (updates.getUnit() != null) goal.setUnit(updates.getUnit());
        if (updates.getDeadline() != null) goal.setDeadline(updates.getDeadline());
        if (updates.getStatus() != null) goal.setStatus(updates.getStatus());

        return goalRepo.save(goal);
    }

    @Transactional
    public void deleteGoal(Long userId, UUID goalId) {
        UserGoal goal = goalRepo.findById(goalId)
                .orElseThrow(() -> new RuntimeException("Goal not found"));
        if (!goal.getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized");
        }
        goalRepo.delete(goal);
    }

    // --- Heatmap ---

    public Map<String, Integer> getActivityHeatmap(Long userId, int months) {
        LocalDateTime since = LocalDateTime.now().minusMonths(months);
        List<Object[]> rows = eventRepo.countByUserIdGroupByDate(userId, since);

        Map<String, Integer> heatmap = new HashMap<>();
        for (Object[] row : rows) {
            heatmap.put(row[0].toString(), ((Long) row[1]).intValue());
        }
        return heatmap;
    }

    // --- AI Insights ---

    public String generateInsights(Long userId) {
        UserProfile profile = getOrCreateProfile(userId);
        List<UserGoal> goals = goalRepo.findByUserIdAndStatus(userId, "active");
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<IntegrationEvent> recentEvents = eventRepo
                .findByUserIdAndEventTimestampAfterOrderByEventTimestampDesc(userId, since);

        StringBuilder context = new StringBuilder();

        if (!profile.getProfileMarkdown().isBlank()) {
            context.append("=== STUDENT PROFILE ===\n");
            context.append(profile.getProfileMarkdown()).append("\n\n");
        }

        if (profile.getLeetcodeStats() != null && !profile.getLeetcodeStats().isEmpty()) {
            context.append("=== LEETCODE STATS ===\n");
            Map<String, Object> lc = profile.getLeetcodeStats();
            context.append(String.format("Total solved: %s (Easy: %s, Medium: %s, Hard: %s)\n",
                    lc.get("total"), lc.get("easy"), lc.get("medium"), lc.get("hard")));
            context.append(String.format("DSA Score: %s/100\n\n", lc.get("dsaScore")));
        }

        Map<String, Object> scores = profile.getAxisScores();
        if (scores != null && !scores.isEmpty()) {
            context.append("=== AXIS SCORES ===\n");
            context.append(String.format("DSA: %s, System Design: %s, Core CS: %s, Projects: %s, Resume: %s\n\n",
                    scores.get("dsa"), scores.get("systemDesign"), scores.get("coreCs"),
                    scores.get("projects"), scores.get("resume")));
        }

        if (!goals.isEmpty()) {
            context.append("=== ACTIVE GOALS ===\n");
            for (UserGoal goal : goals) {
                context.append(String.format("- %s: %d/%s %s",
                        goal.getTitle(), goal.getCurrentValue(),
                        goal.getTargetValue() != null ? goal.getTargetValue().toString() : "?",
                        goal.getUnit()));
                if (goal.getDeadline() != null) {
                    context.append(" (deadline: ").append(goal.getDeadline()).append(")");
                }
                context.append("\n");
            }
            context.append("\n");
        }

        if (!recentEvents.isEmpty()) {
            context.append("=== RECENT ACTIVITY (last 7 days) ===\n");
            for (IntegrationEvent e : recentEvents) {
                context.append(String.format("- [%s] %s\n", e.getProvider(), e.getTitle()));
            }
        }

        if (context.isEmpty()) {
            return "Complete your profile and set some goals to get personalized insights.";
        }

        try {
            return openAiService.generatePersonalInsights(context.toString()).block();
        } catch (Exception e) {
            log.error("Failed to generate personal insights", e);
            return "Could not generate insights at this time.";
        }
    }
}
