package com.pulse.pulse.activity.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulse.pulse.activity.domain.GitHubRepository;
import com.pulse.pulse.activity.infrastructure.persistence.GitHubRepositoryRepository;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GitHubProjectSyncService {

    private final WebClient gitHubWebClient;
    private final GitHubRepositoryRepository repoRepository;
    private final PulseObservability observability;

    private static final int MAX_ACTIVE_REPOS = 5;
    private static final int MAX_COMMITS_PER_REPO = 20;
    private static final int MAX_FILES_PER_COMMIT = 5;
    private static final int README_MAX_LENGTH = 1500;
    private static final long README_REFRESH_HOURS = 72;
    private static final long LANGUAGES_REFRESH_HOURS = 168;
    private static final long PROJECT_SYNC_COOLDOWN_HOURS = 6;

    public GitHubProjectSyncService(@Qualifier("gitHubWebClient") WebClient gitHubWebClient,
                                    GitHubRepositoryRepository repoRepository,
                                    PulseObservability observability) {
        this.gitHubWebClient = gitHubWebClient;
        this.repoRepository = repoRepository;
        this.observability = observability;
    }

    public boolean shouldSync(GitHubProjectSyncRequest request) {
        Map<String, Object> meta = request.metadata();
        if (meta == null) {
            return true;
        }
        Object lastRun = meta.get("projectSyncLastRun");
        if (lastRun == null) {
            return true;
        }
        LocalDateTime lastRunTime = LocalDateTime.parse(lastRun.toString());
        return lastRunTime.plusHours(PROJECT_SYNC_COOLDOWN_HOURS).isBefore(LocalDateTime.now());
    }

    @Transactional
    public int syncProjects(GitHubProjectSyncRequest request) {
        Timer.Sample sample = observability.timerSample();
        String result = "error";
        Observation observation = observability.start("pulse.github.project_sync");
        observability.low(observation, "operation", "sync_projects");
        observability.high(observation, "user.id", request.userId());
        observability.high(observation, "selected_repo_count",
                request.selectedRepositories() != null ? request.selectedRepositories().size() : 0);

        try {
            int synced = observability.scoped(observation, () ->
                    syncSelectedRepositories(request.userId(), request.accessToken(), request.selectedRepositories()));
            result = "success";
            return synced;
        } catch (RuntimeException e) {
            observation.error(e);
            throw e;
        } finally {
            observability.low(observation, "result", result);
            observability.stopTimer(sample, "pulse.github.project_sync.duration", "result", result);
            observation.stop();
        }
    }

    @Transactional
    public void clearRepositories(Long userId) {
        repoRepository.deleteByUserId(userId);
    }

    @Transactional
    public int syncSelectedRepositories(Long userId, String token, List<String> selectedRepositories) {
        return observability.observe("pulse.github.project_sync", observation -> {
            observability.low(observation, "operation", "sync_selected_repositories");
            observability.high(observation, "user.id", userId);
            observability.high(observation, "selected_repo_count",
                    selectedRepositories != null ? selectedRepositories.size() : 0);
        }, () -> {
            if (selectedRepositories == null || selectedRepositories.isEmpty()) {
                repoRepository.deleteByUserId(userId);
                return 0;
            }

            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            List<String> trackedRepos = new ArrayList<>();
            int synced = 0;

            for (String fullName : selectedRepositories) {
                String[] parts = fullName.split("/");
                if (parts.length != 2) {
                    continue;
                }

                JsonNode repoNode = fetchRepository(token, parts[0], parts[1]);
                if (repoNode == null) {
                    continue;
                }

                trackedRepos.add(fullName);
                GitHubRepository repo = repoRepository.findByUserIdAndRepoFullName(userId, fullName)
                        .orElse(GitHubRepository.builder()
                                .userId(userId)
                                .repoFullName(fullName)
                                .build());

                populateRepositorySummary(repo, repoNode);

                LocalDateTime pushedAt = repo.getPushedAt();
                boolean isRecentlyActive = pushedAt != null && pushedAt.isAfter(sevenDaysAgo);
                if (isRecentlyActive && synced < MAX_ACTIVE_REPOS) {
                    syncRepoDetails(repo, token, parts[0], parts[1]);
                    synced++;
                }

                repo.setLastSyncedAt(LocalDateTime.now());
                repoRepository.save(repo);
            }

            if (trackedRepos.isEmpty()) {
                repoRepository.deleteByUserId(userId);
                return 0;
            }

            repoRepository.deleteStaleRepos(userId, trackedRepos);
            log.info("Project sync for userId={}: {} repos tracked, {} with full details", userId, trackedRepos.size(), synced);
            return trackedRepos.size();
        });
    }

    private JsonNode fetchRepository(String token, String owner, String repoName) {
        return observability.observe("pulse.github.project_sync", observation -> {
            observability.low(observation, "operation", "fetch_repo_summary");
            observability.high(observation, "repo.full_name", owner + "/" + repoName);
        }, () -> {
            try {
                return gitHubWebClient.get()
                        .uri("/repos/{owner}/{repo}", owner, repoName)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();
            } catch (Exception e) {
                log.warn("Failed to fetch repo {}/{}: {}", owner, repoName, e.getMessage());
                return null;
            }
        });
    }

    private void populateRepositorySummary(GitHubRepository repo, JsonNode repoNode) {
        repo.setRepoName(repoNode.path("name").asText(repo.getRepoName()));
        repo.setDescription(repoNode.path("description").asText(null));
        repo.setPrimaryLanguage(repoNode.path("language").asText(null));

        String pushedAtStr = repoNode.path("pushed_at").asText("");
        repo.setPushedAt(pushedAtStr.isBlank() ? null : parseGitHubTimestamp(pushedAtStr));
        repo.setOpenIssuesCount(repoNode.path("open_issues_count").asInt(0));
        repo.setFork(repoNode.path("fork").asBoolean(false));
        repo.setDefaultBranch(repoNode.path("default_branch").asText("main"));

        List<String> topics = new ArrayList<>();
        JsonNode topicsNode = repoNode.path("topics");
        if (topicsNode.isArray()) {
            for (JsonNode t : topicsNode) {
                topics.add(t.asText());
            }
        }
        repo.setTopics(topics);
    }

    private void syncRepoDetails(GitHubRepository repo, String token, String owner, String repoName) {
        fetchCommits(repo, token, owner, repoName);
        fetchOpenPrs(repo, token, owner, repoName);
        fetchReadmeIfStale(repo, token, owner, repoName);
        fetchLanguagesIfStale(repo, token, owner, repoName);
    }

    private void fetchCommits(GitHubRepository repo, String token, String owner, String repoName) {
        observability.observe("pulse.github.project_sync", observation -> {
            observability.low(observation, "operation", "fetch_commits");
            observability.high(observation, "repo.full_name", owner + "/" + repoName);
        }, () -> {
            try {
                JsonNode commits = gitHubWebClient.get()
                        .uri("/repos/{owner}/{repo}/commits?per_page={limit}", owner, repoName, MAX_COMMITS_PER_REPO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();

                if (commits == null || !commits.isArray()) {
                    return;
                }

                List<Map<String, Object>> commitList = new ArrayList<>();
                boolean firstCommit = true;

                for (JsonNode commit : commits) {
                    String sha = commit.path("sha").asText();
                    String message = commit.path("commit").path("message").asText("");
                    String date = commit.path("commit").path("author").path("date").asText("");

                    Map<String, Object> commitData = new LinkedHashMap<>();
                    commitData.put("sha", sha.substring(0, Math.min(7, sha.length())));
                    commitData.put("message", message.length() > 200 ? message.substring(0, 200) : message);
                    commitData.put("date", date);

                    if (firstCommit) {
                        List<Map<String, Object>> filesChanged = fetchCommitFiles(token, owner, repoName, sha);
                        if (!filesChanged.isEmpty()) {
                            commitData.put("filesChanged", filesChanged);
                        }
                        firstCommit = false;
                    }

                    commitList.add(commitData);
                }
                repo.setRecentCommits(commitList);
            } catch (Exception e) {
                log.warn("Failed to fetch commits for {}/{}: {}", owner, repoName, e.getMessage());
            }
        });
    }

    private List<Map<String, Object>> fetchCommitFiles(String token, String owner, String repoName, String sha) {
        try {
            JsonNode detail = gitHubWebClient.get()
                    .uri("/repos/{owner}/{repo}/commits/{sha}", owner, repoName, sha)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (detail == null) {
                return Collections.emptyList();
            }

            JsonNode files = detail.path("files");
            if (!files.isArray()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> result = new ArrayList<>();
            int count = 0;
            for (JsonNode file : files) {
                if (count >= MAX_FILES_PER_COMMIT) {
                    break;
                }
                result.add(Map.of(
                        "filename", file.path("filename").asText(),
                        "additions", file.path("additions").asInt(0),
                        "deletions", file.path("deletions").asInt(0)
                ));
                count++;
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch commit detail {}: {}", sha, e.getMessage());
            return Collections.emptyList();
        }
    }

    private void fetchOpenPrs(GitHubRepository repo, String token, String owner, String repoName) {
        observability.observe("pulse.github.project_sync", observation -> {
            observability.low(observation, "operation", "fetch_open_prs");
            observability.high(observation, "repo.full_name", owner + "/" + repoName);
        }, () -> {
            try {
                JsonNode prs = gitHubWebClient.get()
                        .uri("/repos/{owner}/{repo}/pulls?state=open&per_page=10", owner, repoName)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();

                if (prs == null || !prs.isArray()) {
                    return;
                }

                List<Map<String, Object>> prList = new ArrayList<>();
                for (JsonNode pr : prs) {
                    String body = pr.path("body").asText("");
                    prList.add(Map.of(
                            "number", pr.path("number").asInt(),
                            "title", pr.path("title").asText(""),
                            "body", body.length() > 500 ? body.substring(0, 500) : body,
                            "draft", pr.path("draft").asBoolean(false),
                            "createdAt", pr.path("created_at").asText(""),
                            "reviewComments", pr.path("review_comments").asInt(0)
                    ));
                }
                repo.setOpenPrs(prList);
            } catch (Exception e) {
                log.warn("Failed to fetch PRs for {}/{}: {}", owner, repoName, e.getMessage());
            }
        });
    }

    private void fetchReadmeIfStale(GitHubRepository repo, String token, String owner, String repoName) {
        if (repo.getReadmeFetchedAt() != null &&
                repo.getReadmeFetchedAt().plusHours(README_REFRESH_HOURS).isAfter(LocalDateTime.now())) {
            return;
        }

        observability.observe("pulse.github.project_sync", observation -> {
            observability.low(observation, "operation", "fetch_readme");
            observability.high(observation, "repo.full_name", owner + "/" + repoName);
        }, () -> {
            try {
                JsonNode readme = gitHubWebClient.get()
                        .uri("/repos/{owner}/{repo}/readme", owner, repoName)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();

                if (readme == null) {
                    return;
                }

                String content = readme.path("content").asText("");
                String encoding = readme.path("encoding").asText("");

                if ("base64".equals(encoding) && !content.isEmpty()) {
                    String decoded = new String(Base64.getMimeDecoder().decode(content), StandardCharsets.UTF_8);
                    repo.setReadmeContent(decoded.length() > README_MAX_LENGTH
                            ? decoded.substring(0, README_MAX_LENGTH)
                            : decoded);
                }
                repo.setReadmeFetchedAt(LocalDateTime.now());
            } catch (WebClientResponseException.NotFound e) {
                repo.setReadmeContent(null);
                repo.setReadmeFetchedAt(LocalDateTime.now());
            } catch (Exception e) {
                log.warn("Failed to fetch README for {}/{}: {}", owner, repoName, e.getMessage());
            }
        });
    }

    private void fetchLanguagesIfStale(GitHubRepository repo, String token, String owner, String repoName) {
        if (repo.getLastSyncedAt() != null &&
                repo.getLastSyncedAt().plusHours(LANGUAGES_REFRESH_HOURS).isAfter(LocalDateTime.now()) &&
                repo.getLanguages() != null && !repo.getLanguages().isEmpty()) {
            return;
        }

        observability.observe("pulse.github.project_sync", observation -> {
            observability.low(observation, "operation", "fetch_languages");
            observability.high(observation, "repo.full_name", owner + "/" + repoName);
        }, () -> {
            try {
                JsonNode langs = gitHubWebClient.get()
                        .uri("/repos/{owner}/{repo}/languages", owner, repoName)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();

                if (langs == null) {
                    return;
                }

                Map<String, Object> langMap = new LinkedHashMap<>();
                langs.fields().forEachRemaining(entry -> langMap.put(entry.getKey(), entry.getValue().asLong()));
                repo.setLanguages(langMap);
            } catch (Exception e) {
                log.warn("Failed to fetch languages for {}/{}: {}", owner, repoName, e.getMessage());
            }
        });
    }

    private LocalDateTime parseGitHubTimestamp(String timestamp) {
        return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
    }
}
