package com.pulse.pulse.personal.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.activity.domain.GitHubRepository;
import com.pulse.pulse.activity.domain.GitHubRepositoryReader;
import com.pulse.pulse.personal.domain.AiTask;
import com.pulse.pulse.personal.domain.RoadmapItem;
import com.pulse.pulse.personal.domain.UserGoal;
import com.pulse.pulse.personal.domain.UserProfile;
import com.pulse.pulse.personal.infrastructure.persistence.*;
import com.pulse.pulse.platform.ai.EmbeddingService;
import com.pulse.pulse.platform.observability.PulseObservability;
import com.pulse.pulse.platform.vector.QdrantVectorStore;
import com.pulse.pulse.platform.vector.QdrantVectorStore.ChunkPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeIndexingService {

    private static final Pattern RESUME_SECTION_PATTERN = Pattern.compile(
            "^\\s*(education|experience|work\\s+experience|employment|skills|technical\\s+skills|projects|summary|objective|certifications|awards|activities|publications|interests|languages|volunteer)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private final KnowledgeChunkRepository chunkRepo;
    private final GitHubRepositoryReader githubRepo;
    private final UserProfileRepository profileRepo;
    private final UserGoalRepository goalRepo;
    private final AiTaskRepository aiTaskRepo;
    private final RoadmapItemRepository roadmapRepo;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final PulseObservability observability;
    /** Empty when {@code qdrant.enabled=false}; Postgres stays authoritative either way. */
    private final Optional<QdrantVectorStore> qdrantStore;

    /**
     * Whether to mirror writes into Qdrant. Held here rather than on the store since issue 03, which
     * made the store the read path too — reads must not be gated on a write-side flag. Spring
     * overwrites the initializer; the default keeps manually constructed instances mirroring.
     */
    @Value("${pulse.vector.dual-write:true}")
    private boolean dualWrite = true;

    @Transactional
    public void indexGitHubReadme(Long userId, String repoFullName, String repoName,
                                  String primaryLanguage, String readmeContent) {
        if (readmeContent == null || readmeContent.trim().length() < 50) return;

        observability.observe("pulse.rag.index", obs -> {
            observability.low(obs, "source_type", "github_readme");
            observability.high(obs, "user.id", userId);
        }, () -> {
            String content = "Repository: " + repoFullName + "\n" +
                    (primaryLanguage != null ? "Primary Language: " + primaryLanguage + "\n" : "") +
                    "\n" + readmeContent.trim();

            float[] embedding = embeddingService.embed(content).block();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("repoName", repoName);
            if (primaryLanguage != null) metadata.put("language", primaryLanguage);

            upsertChunk(userId, "github_readme", repoFullName, 0, content, embedding, metadata);
            return null;
        });
    }

    @Transactional
    public void indexObsidianDiff(Long userId, String date, String diffContent) {
        if (diffContent == null || diffContent.isBlank()) return;

        observability.observe("pulse.rag.index", obs -> {
            observability.low(obs, "source_type", "obsidian_diff");
            observability.high(obs, "user.id", userId);
        }, () -> {
            String content = "Study notes for " + date + ":\n\n" + diffContent.trim();
            float[] embedding = embeddingService.embed(content).block();

            upsertChunk(userId, "obsidian_diff", date, 0, content, embedding, Map.of("date", date));

            pruneOldObsidianDiffs(userId);
            return null;
        });
    }

    @Transactional
    public void indexResumeSections(Long userId, String resumeText) {
        if (resumeText == null || resumeText.isBlank()) return;

        observability.observe("pulse.rag.index", obs -> {
            observability.low(obs, "source_type", "resume_section");
            observability.high(obs, "user.id", userId);
        }, () -> {
            deleteBySourceType(userId, "resume_section");

            List<Map.Entry<String, String>> sections = parseResumeSections(resumeText);
            List<String> texts = sections.stream().map(Map.Entry::getValue).toList();

            if (texts.isEmpty()) return null;

            List<float[]> embeddings = embeddingService.embedBatch(texts).block();

            List<ChunkPoint> chunks = new ArrayList<>();
            for (int i = 0; i < sections.size(); i++) {
                Map.Entry<String, String> section = sections.get(i);
                Map<String, Object> metadata = Map.of("sectionName", section.getKey());
                chunks.add(new ChunkPoint(userId, "resume_section", section.getKey(), i,
                        section.getValue(), embeddings.get(i), metadata));
            }
            upsertChunks(chunks);
            return null;
        });
    }

    @Transactional
    public void indexGoalsAndTasks(Long userId) {
        observability.observe("pulse.rag.index", obs -> {
            observability.low(obs, "source_type", "goals_tasks");
            observability.high(obs, "user.id", userId);
        }, () -> {
            deleteBySourceType(userId, "goal");
            deleteBySourceType(userId, "task");
            deleteBySourceType(userId, "roadmap");

            List<UserGoal> goals = goalRepo.findByUserId(userId);
            List<AiTask> tasks = aiTaskRepo.findByUserIdOrderByAxisAscOrderIndexAsc(userId).stream()
                    .filter(t -> !t.isCompleted()).toList();
            List<RoadmapItem> roadmapItems = roadmapRepo.findByUserIdOrderByFocusRankAsc(userId);

            List<String> allTexts = new ArrayList<>();
            List<ChunkMeta> allMeta = new ArrayList<>();

            for (UserGoal goal : goals) {
                String text = "Goal: " + goal.getTitle() +
                        " | Category: " + goal.getCategory() +
                        " | Progress: " + goal.getCurrentValue() + "/" + goal.getTargetValue() + " " + goal.getUnit() +
                        (goal.getDeadline() != null ? " | Deadline: " + goal.getDeadline() : "") +
                        " | Status: " + goal.getStatus();
                allTexts.add(text);
                allMeta.add(new ChunkMeta("goal", goal.getId().toString(), 0, Map.of("category", goal.getCategory())));
            }

            for (AiTask task : tasks) {
                String text = "Study Task [" + task.getAxis() + "]: " + task.getTitle() +
                        (task.getDescription() != null ? " — " + task.getDescription() : "") +
                        " | Priority: " + task.getPriority() +
                        " | Source: " + task.getSource();
                allTexts.add(text);
                allMeta.add(new ChunkMeta("task", task.getId().toString(), 0, Map.of("axis", task.getAxis())));
            }

            for (RoadmapItem item : roadmapItems) {
                String text = "Roadmap: " + item.getTitle() +
                        (item.getDescription() != null ? " — " + item.getDescription() : "") +
                        " | Phase: " + item.getPhase() +
                        (item.getDeadline() != null ? " | Deadline: " + item.getDeadline() : "") +
                        " | Status: " + item.getStatus();
                allTexts.add(text);
                allMeta.add(new ChunkMeta("roadmap", item.getId().toString(), 0, Map.of("phase", item.getPhase())));
            }

            if (allTexts.isEmpty()) return null;

            List<float[]> embeddings = embeddingService.embedBatch(allTexts).block();

            List<ChunkPoint> chunks = new ArrayList<>();
            for (int i = 0; i < allTexts.size(); i++) {
                ChunkMeta meta = allMeta.get(i);
                chunks.add(new ChunkPoint(userId, meta.sourceType, meta.sourceKey, meta.chunkIndex,
                        allTexts.get(i), embeddings.get(i), meta.metadata));
            }
            upsertChunks(chunks);
            return null;
        });
    }

    @Transactional
    public void reindexAll(Long userId) {
        log.info("Starting full reindex for user {}", userId);
        deleteAllForUser(userId);

        UserProfile profile = profileRepo.findByUserId(userId).orElse(null);
        if (profile != null && profile.getResumeText() != null && !profile.getResumeText().isBlank()) {
            indexResumeSections(userId, profile.getResumeText());
        }

        List<GitHubRepository> repos = githubRepo.findByUserIdOrderByPushedAtDesc(userId);
        for (GitHubRepository repo : repos) {
            if (repo.getReadmeContent() != null && repo.getReadmeContent().trim().length() >= 50) {
                indexGitHubReadme(userId, repo.getRepoFullName(), repo.getRepoName(),
                        repo.getPrimaryLanguage(), repo.getReadmeContent());
            }
        }

        indexGoalsAndTasks(userId);
        log.info("Full reindex completed for user {}", userId);
    }

    List<Map.Entry<String, String>> parseResumeSections(String resumeText) {
        List<Map.Entry<String, String>> sections = new ArrayList<>();
        Matcher matcher = RESUME_SECTION_PATTERN.matcher(resumeText);

        List<int[]> headerPositions = new ArrayList<>();
        List<String> headerNames = new ArrayList<>();

        while (matcher.find()) {
            headerPositions.add(new int[]{matcher.start(), matcher.end()});
            headerNames.add(matcher.group(1).trim());
        }

        if (headerPositions.isEmpty()) {
            sections.add(Map.entry("full_resume", resumeText.trim()));
            return sections;
        }

        if (headerPositions.get(0)[0] > 0) {
            String preamble = resumeText.substring(0, headerPositions.get(0)[0]).trim();
            if (!preamble.isBlank()) {
                sections.add(Map.entry("header", preamble));
            }
        }

        for (int i = 0; i < headerPositions.size(); i++) {
            int contentStart = headerPositions.get(i)[1];
            int contentEnd = (i + 1 < headerPositions.size()) ? headerPositions.get(i + 1)[0] : resumeText.length();
            String content = resumeText.substring(contentStart, contentEnd).trim();
            if (!content.isBlank()) {
                String sectionContent = headerNames.get(i) + "\n" + content;
                sections.add(Map.entry(headerNames.get(i).toLowerCase(), sectionContent));
            }
        }

        return sections;
    }

    private void upsertChunk(Long userId, String sourceType, String sourceKey, int chunkIndex,
                             String content, float[] embedding, Map<String, Object> metadata) {
        upsertChunks(List.of(new ChunkPoint(userId, sourceType, sourceKey, chunkIndex, content,
                embedding, metadata)));
    }

    /** The single write seam: Postgres is authoritative, Qdrant is mirrored in one batched call. */
    private void upsertChunks(List<ChunkPoint> chunks) {
        for (ChunkPoint chunk : chunks) {
            String metadataStr;
            try {
                metadataStr = objectMapper.writeValueAsString(chunk.metadata());
            } catch (JsonProcessingException e) {
                metadataStr = "{}";
            }
            chunkRepo.upsertChunk(chunk.userId(), chunk.sourceType(), chunk.sourceKey(), chunk.chunkIndex(),
                    chunk.content(), EmbeddingService.toVectorString(chunk.denseVector()), metadataStr);
        }
        mirror("upsert of " + chunks.size() + " chunk(s)", store -> store.upsertBatch(chunks));
    }

    private void deleteBySourceType(Long userId, String sourceType) {
        chunkRepo.deleteByUserIdAndSourceType(userId, sourceType);
        mirror("delete of source type " + sourceType, store -> store.deleteBySource(userId, sourceType));
    }

    private void deleteBySourceKey(Long userId, String sourceType, String sourceKey) {
        chunkRepo.deleteByUserIdAndSourceTypeAndSourceKey(userId, sourceType, sourceKey);
        mirror("delete of " + sourceType + "/" + sourceKey,
                store -> store.deleteBySourceKey(userId, sourceType, sourceKey));
    }

    private void deleteAllForUser(Long userId) {
        chunkRepo.deleteByUserId(userId);
        mirror("delete of all chunks", store -> store.deleteByUser(userId));
    }

    /** A failing mirror must never fail the Postgres write, which stays authoritative. */
    private void mirror(String operation, Consumer<QdrantVectorStore> write) {
        if (!dualWrite) {
            return;
        }
        qdrantStore.ifPresent(store -> {
            try {
                write.accept(store);
            } catch (RuntimeException e) {
                log.error("Qdrant dual-write failed ({}); Postgres write kept", operation, e);
            }
        });
    }

    private void pruneOldObsidianDiffs(Long userId) {
        String cutoffDate = LocalDate.now().minusDays(14).toString();
        chunkRepo.findByUserIdAndSourceType(userId, "obsidian_diff").stream()
                .filter(c -> c.getSourceKey().compareTo(cutoffDate) < 0)
                .forEach(c -> deleteBySourceKey(userId, "obsidian_diff", c.getSourceKey()));
    }

    private record ChunkMeta(String sourceType, String sourceKey, int chunkIndex, Map<String, Object> metadata) {}
}
