# 08 — `ChatService` → HTTP client, contract preserved

**Track:** B · Agent  ·  **Blocked by:** 07  ·  **Blocks:** 11
**Estimate:** ~3h

## Goal

`ChatService` stops assembling context and calling Anthropic. It calls the agent and translates the
response — with the frontend contract byte-identical.

## Changes

`ChatService.chat(...)` shrinks to roughly:

```java
public ChatResponse chat(Long userId, String message, List<ChatMessage> history) {
    return observability.observe("pulse.rag.chat", obs -> { /* same tags */ }, () -> {
        AgentChatResponse r = agentClient.chat(userId, message, history);   // WebClient, traceparent propagated
        return new ChatResponse(r.message(), r.sources(), r.trajectory());  // trajectory -> RoutingDecision
    });
}
```

**Must survive verbatim** (PRD §6.3):

```java
public record ChatMessage(String role, String content) {}
public record SourceCitation(String sourceType, String sourceKey, double score) {}
public record RoutingDecision(String sourceType, String decision, String reason) {}
public record ChatResponse(String message, List<SourceCitation> sources, List<RoutingDecision> routingDecisions) {}
```

- `getIndexStatus(...)` stays in Java unchanged — it reads `chunkRepo.countGroupedBySourceType`.
- New `platform/agent/AgentClient.java` — `WebClient`, `X-Internal-Token`, `traceparent` header,
  timeout, and a fallback.
- **Fallback:** if the agent is unreachable or times out, return a graceful degraded response rather
  than a 500. Simplest honest option: a `ChatResponse` explaining the assistant is temporarily
  unavailable, with an empty sources list. Do not silently resurrect the old inline path — two
  divergent code paths is exactly what this issue removes.

## Orphans this creates (remove — they are ours)

- The four threshold constants (`RESUME_INLINE_THRESHOLD`, `GITHUB_INLINE_THRESHOLD`,
  `OBSIDIAN_INLINE_THRESHOLD`, `ITEMS_INLINE_THRESHOLD`) and `MAX_HISTORY_TURNS`, `RAG_TOP_K`
- Repository/reader dependencies now unused by `ChatService`: `profileRepo`, `goalRepo`, `aiTaskRepo`,
  `roadmapRepo`, `githubRepo`, `retrievalService`, `anthropicService` — **check each** against
  `getIndexStatus` before deleting; `chunkRepo` must stay
- `formatSourceLabel(...)` moves to the agent (it builds display labels for citations)

## Pre-existing dead code — do NOT delete

- `ChatService.java:17` — `import java.util.stream.Collectors;` appears unused *today*, before any of
  our changes. Per `CLAUDE.md`, mention it, leave it. (Noted here so it isn't re-litigated.)

## Once this lands

`HybridRetrievalService` has **zero** callers in Java (it had exactly one: `ChatService`). Decide:
delete it, or keep it for the issue 03 parity harness. Recommendation: keep until issue 04 closes,
then delete in a separate commit.

## Acceptance criteria

- [ ] Frontend chat works with **no changes to `app/`** — verify by running the real UI
- [ ] Routing-decision panel still renders, now showing agent trajectory
- [ ] Source citations still render with scores
- [ ] Agent down → graceful degraded response, not a 500 (test)
- [ ] `pulse.rag.chat` observation still emitted with the same tags
- [ ] `mvn test` green

## Verify

```bash
docker compose up
# open localhost:3000, use chat, inspect the routing panel
cd api && ./mvnw test
```
