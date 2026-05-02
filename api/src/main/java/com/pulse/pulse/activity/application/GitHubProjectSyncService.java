package com.pulse.pulse.activity.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulse.pulse.activity.domain.GitHubRepository;
import com.pulse.pulse.activity.infrastructure.persistence.GitHubRepositoryRepository;
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
import java.util.*;

@Service
@Slf4j
public class GitHubProjectSyncService {

    private final WebClient gitHubWebClient;
    private final GitHubRepositoryRepository repoRepository;

    private static final int MAX_ACTIVE_REPOS = 5;
    private static final int MAX_COMMITS_PER_REPO = 20;
    private static final int MAX_FILES_PER_COMMIT = 5;
    private static final int README_MAX_LENGTH = 1500;
    private static final long README_REFRESH_HOURS = 72;
    private static final long LANGUAGES_REFRESH_HOURS = 168;
    private static final long PROJECT_SYNC_COOLDOWN_HOURS = 6;

    public GitHubProjectSyncService(@Qualifier("gitHubWebClient") WebClient gitHubWebClient,
                                     GitHubRepositoryRepository repoRepository) {
        this.gitHubWebClient = gitHubWebClient;
        this.repoRepository = repoRepository;
    }

    public boolean shouldSync(GitHubProjectSyncRequest request) {
        Map<String, Object> meta = request.metadata();
        if (meta == null) return true;
        Object lastRun = meta.get("projectSyncLastRun");
        if (lastRun == null) return true;
        LocalDateTime lastRunTime = LocalDateTime.parse(lastRun.toString());
        return lastRunTime.plusHours(PROJECT_SYNC_COOLDOWN_HOURS).isBefore(LocalDateTime.now());
    }

    @Transactional
    public int syncProjects(GitHubProjectSyncRequest request) {
        String token = request.accessToken();
        Long userId = request.userId();

        List<JsonNode> repos = fetchUserRepos(token);
        if (repos.isEmpty()) {
            log.debug("No active repos found for userId={}", userId);
            return 0;
        }

        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<String> activeRepoNames = new ArrayList<>();
        int synced = 0;

        for (JsonNode repoNode : repos) {
            String fullName = repoNode.path("full_name").asText();
            String pushedAtStr = repoNode.path("pushed_at").asText("");
            if (pushedAtStr.isEmpty()) continue;

            LocalDateTime pushedAt = parseGitHubTimestamp(pushedAtStr);
            if (pushedAt.isBefore(ninetyDaysAgo)) continue;
            if (repoNode.path("fork").asBoolean(false)) continue;

            activeRepoNames.add(fullName);

            GitHubRepository repo = repoRepository.findByUserIdAndRepoFullName(userId, fullName)
                    .orElse(GitHubRepository.builder()
                            .userId(userId)
                            .repoFullName(fullName)
                            .build());

            repo.setRepoName(repoNode.path("name").asText());
            repo.setDescription(repoNode.path("description").asText(null));
            repo.setPrimaryLanguage(repoNode.path("language").asText(null));
            repo.setPushedAt(pushedAt);
            repo.setOpenIssuesCount(repoNode.path("open_issues_count").asInt(0));
            repo.setFork(false);
            repo.setDefaultBranch(repoNode.path("default_branch").asText("main"));

            List<String> topics = new ArrayList<>();
            JsonNode topicsNode = repoNode.path("topics");
            if (topicsNode.isArray()) {
                for (JsonNode t : topicsNode) topics.add(t.asText());
            }
            repo.setTopics(topics);

            boolean isRecentlyActive = pushedAt.isAfter(sevenDaysAgo);
            if (isRecentlyActive && synced < MAX_ACTIVE_REPOS) {
                syncRepoDetails(repo, token);
                synced++;
            }

            repo.setLastSyncedAt(LocalDateTime.now());
            repoRepository.save(repo);
        }

        if (!activeRepoNames.isEmpty()) {
            repoRepository.deleteStaleRepos(userId, activeRepoNames);
        }

        fetchAndDistributeAssignedIssues(userId, token, activeRepoNames);

        log.info("Project sync for userId={}: {} repos tracked, {} with full details", userId, activeRepoNames.size(), synced);
        return activeRepoNames.size();
    }

    private void syncRepoDetails(GitHubRepository repo, String token) {
        String fullName = repo.getRepoFullName();
        String[] parts = fullName.split("/");
        if (parts.length != 2) return;

        fetchCommits(repo, token, parts[0], parts[1]);
        fetchOpenPrs(repo, token, parts[0], parts[1]);
        fetchReadmeIfStale(repo, token, parts[0], parts[1]);
        fetchLanguagesIfStale(repo, token, parts[0], parts[1]);
    }

    private List<JsonNode> fetchUserRepos(String token) {
        try {
            JsonNode response = gitHubWebClient.get()
                    .uri("/user/repos?sort=pushed&per_page=30&affiliation=owner,collaborator")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.isArray()) return Collections.emptyList();

            List<JsonNode> repos = new ArrayList<>();
            for (JsonNode r : response) repos.add(r);
            return repos;
        } catch (Exception e) {
            log.error("Failed to fetch repos: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void fetchCommits(GitHubRepository repo, String token, String owner, String repoName) {
        try {
            JsonNode commits = gitHubWebClient.get()
                    .uri("/repos/{owner}/{repo}/commits?per_page={limit}", owner, repoName, MAX_COMMITS_PER_REPO)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (commits == null || !commits.isArray()) return;

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
    }

    private List<Map<String, Object>> fetchCommitFiles(String token, String owner, String repoName, String sha) {
        try {
            JsonNode detail = gitHubWebClient.get()
                    .uri("/repos/{owner}/{repo}/commits/{sha}", owner, repoName, sha)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (detail == null) return Collections.emptyList();

            JsonNode files = detail.path("files");
            if (!files.isArray()) return Collections.emptyList();

            List<Map<String, Object>> result = new ArrayList<>();
            int count = 0;
            for (JsonNode file : files) {
                if (count >= MAX_FILES_PER_COMMIT) break;
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
        try {
            JsonNode prs = gitHubWebClient.get()
                    .uri("/repos/{owner}/{repo}/pulls?state=open&per_page=10", owner, repoName)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (prs == null || !prs.isArray()) return;

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
    }

    private void fetchReadmeIfStale(GitHubRepository repo, String token, String owner, String repoName) {
        if (repo.getReadmeFetchedAt() != null &&
                repo.getReadmeFetchedAt().plusHours(README_REFRESH_HOURS).isAfter(LocalDateTime.now())) {
            return;
        }

        try {
            JsonNode readme = gitHubWebClient.get()
                    .uri("/repos/{owner}/{repo}/readme", owner, repoName)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (readme == null) return;

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
    }

    private void fetchLanguagesIfStale(GitHubRepository repo, String token, String owner, String repoName) {
        if (repo.getLastSyncedAt() != null &&
                repo.getLastSyncedAt().plusHours(LANGUAGES_REFRESH_HOURS).isAfter(LocalDateTime.now()) &&
                repo.getLanguages() != null && !repo.getLanguages().isEmpty()) {
            return;
        }

        try {
            JsonNode langs = gitHubWebClient.get()
                    .uri("/repos/{owner}/{repo}/languages", owner, repoName)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (langs == null) return;

            Map<String, Object> langMap = new LinkedHashMap<>();
            langs.fields().forEachRemaining(entry -> langMap.put(entry.getKey(), entry.getValue().asLong()));
            repo.setLanguages(langMap);
        } catch (Exception e) {
            log.warn("Failed to fetch languages for {}/{}: {}", owner, repoName, e.getMessage());
        }
    }

    private void fetchAndDistributeAssignedIssues(Long userId, String token, List<String> activeRepoNames) {
        try {
            JsonNode issues = gitHubWebClient.get()
                    .uri("/issues?filter=assigned&state=open&per_page=30")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (issues == null || !issues.isArray()) return;

            Map<String, List<Map<String, Object>>> issuesByRepo = new HashMap<>();

            for (JsonNode issue : issues) {
                if (issue.has("pull_request")) continue;

                String repoFullName = issue.path("repository").path("full_name").asText("");
                if (repoFullName.isEmpty() || !activeRepoNames.contains(repoFullName)) continue;

                List<String> labels = new ArrayList<>();
                JsonNode labelsNode = issue.path("labels");
                if (labelsNode.isArray()) {
                    for (JsonNode l : labelsNode) labels.add(l.path("name").asText());
                }

                Map<String, Object> issueData = Map.of(
                        "number", issue.path("number").asInt(),
                        "title", issue.path("title").asText(""),
                        "labels", labels,
                        "createdAt", issue.path("created_at").asText(""),
                        "repoFullName", repoFullName
                );

                issuesByRepo.computeIfAbsent(repoFullName, k -> new ArrayList<>()).add(issueData);
            }

            for (Map.Entry<String, List<Map<String, Object>>> entry : issuesByRepo.entrySet()) {
                repoRepository.findByUserIdAndRepoFullName(userId, entry.getKey())
                        .ifPresent(repo -> {
                            repo.setAssignedIssues(entry.getValue());
                            repoRepository.save(repo);
                        });
            }
        } catch (Exception e) {
            log.warn("Failed to fetch assigned issues: {}", e.getMessage());
        }
    }

    private LocalDateTime parseGitHubTimestamp(String timestamp) {
        return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
    }
}
