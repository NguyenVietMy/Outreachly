package com.pulse.pulse.personal.application;

import com.pulse.pulse.activity.domain.GitHubRepositoryReader;
import com.pulse.pulse.personal.domain.UserProfile;
import com.pulse.pulse.personal.infrastructure.persistence.AiTaskRepository;
import com.pulse.pulse.personal.infrastructure.persistence.RoadmapItemRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserGoalRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The non-vector context {@link ChatService} assembles today, exposed as structured JSON for the
 * Python agent. Formatting stays on the agent side; this only reads.
 */
@Service
@RequiredArgsConstructor
public class InternalContextService {

    private static final int README_MAX_CHARS = 2000;

    private final UserProfileRepository profileRepo;
    private final UserGoalRepository goalRepo;
    private final AiTaskRepository aiTaskRepo;
    private final RoadmapItemRepository roadmapRepo;
    private final GitHubRepositoryReader githubRepo;

    public ProfileContext getProfile(Long userId) {
        UserProfile profile = profileRepo.findByUserId(userId).orElse(null);
        if (profile == null) {
            return new ProfileContext(null, null, null, Map.of(), Map.of(), null, 0);
        }
        String resumeText = profile.getResumeText();
        return new ProfileContext(
                profile.getProfileMarkdown(),
                profile.getTargetRole(),
                profile.getGraduationYear(),
                profile.getAxisScores(),
                profile.getLeetcodeStats(),
                resumeText,
                resumeText != null ? resumeText.length() : 0);
    }

    public ItemsContext getItems(Long userId) {
        List<Goal> goals = goalRepo.findByUserId(userId).stream()
                .map(g -> new Goal(g.getTitle(), g.getCurrentValue(), g.getTargetValue(), g.getUnit(),
                        g.getDeadline(), g.getStatus()))
                .toList();
        List<Task> tasks = aiTaskRepo.findByUserIdOrderByAxisAscOrderIndexAsc(userId).stream()
                .filter(t -> !t.isCompleted())
                .map(t -> new Task(t.getAxis(), t.getTitle()))
                .toList();
        List<RoadmapEntry> roadmap = roadmapRepo.findByUserIdOrderByFocusRankAsc(userId).stream()
                .map(r -> new RoadmapEntry(r.getTitle(), r.getPhase(), r.getDeadline(), r.getStatus()))
                .toList();
        return new ItemsContext(goals, tasks, roadmap);
    }

    public GithubContext getGithub(Long userId) {
        List<Repo> repos = githubRepo.findByUserIdOrderByPushedAtDesc(userId).stream()
                .filter(r -> r.getReadmeContent() != null && !r.getReadmeContent().isBlank())
                .map(r -> new Repo(
                        r.getRepoFullName(),
                        r.getDescription(),
                        r.getPrimaryLanguage(),
                        r.getReadmeContent().substring(0, Math.min(r.getReadmeContent().length(), README_MAX_CHARS))))
                .toList();
        return new GithubContext(repos);
    }

    public record ProfileContext(
            String profileMarkdown,
            String targetRole,
            Integer graduationYear,
            Map<String, Object> axisScores,
            Map<String, Object> leetcodeStats,
            String resumeText,
            int resumeChars) {}

    public record ItemsContext(List<Goal> goals, List<Task> tasks, List<RoadmapEntry> roadmap) {}

    public record Goal(String title, Integer currentValue, Integer targetValue, String unit,
                       LocalDate deadline, String status) {}

    public record Task(String axis, String title) {}

    public record RoadmapEntry(String title, String phase, LocalDate deadline, String status) {}

    public record GithubContext(List<Repo> repos) {}

    public record Repo(String name, String description, String primaryLanguage, String readmeContent) {}
}
