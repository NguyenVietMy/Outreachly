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

**As built.** `AgentClient` owns the fallback and defines its own wire records, so `platform` does
not import `personal`; `ChatService` maps them onto the four contract records. The traceparent
header is *not* set by hand — the injected `WebClient.Builder` is the auto-configured one, which
carries client observations and with them the propagation issue 10 continues the trace from.
Two new properties: `pulse.agent.url` (`AGENT_URL`, default `http://localhost:8001`) and
`pulse.agent.timeout-seconds` (`AGENT_TIMEOUT_SECONDS`, default 60 — the graph's worst case is two
retrieval rounds and three model calls). `docker-compose.yml` sets `AGENT_URL: http://agent:8001`
on the api service; **ECS does not have it yet — that is issue 11.**

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

**Done: deleted here.** Issue 04 had already removed the parity harness that was its other caller.
`QdrantVectorStore.hybridSearch` / `HybridQuery` are now unreachable from Java too — left in place,
since deleting them is a second-order cleanup this issue did not ask for.

## Acceptance criteria

- [ ] Frontend chat works with **no changes to `app/`** — verify by running the real UI.
      `app/` is untouched (the diff contains no `app/` file), but the browser run was **not
      done** — it needs a Google sign-in and was skipped by request. See *How this was verified*.
- [ ] Routing-decision panel still renders, now showing agent trajectory — same reason
- [ ] Source citations still render with scores — same reason
- [x] Agent down → graceful degraded response, not a 500 (test)
- [x] `pulse.rag.chat` observation still emitted with the same tags
- [x] `mvn test` green — 83 tests, 0 failures

## How this was verified

The browser check was skipped, so the contract was pinned at the wire instead:

1. `POST localhost:8001/chat` against the running agent, as the real user, returned one
   `obsidian_diff` citation (`score 0.033333335`) and a four-step trajectory.
2. That payload is the fixture in `AgentClientTest.decodesTheAgentResponseAndSendsTheInternalToken`,
   served by a stub `HttpServer`: it asserts the decode into `Citation`/`TrajectoryStep` and that
   `X-Internal-Token` is on the request.
3. `ChatServiceTest` asserts the mapping onto `SourceCitation`/`RoutingDecision`, that history is
   forwarded, and that `pulse.rag.chat` still carries `user.id` / `message.length`.
4. `ChatController` is unchanged, and `ChatService`'s four records are byte-identical, so the JSON
   `app/` consumes cannot have moved.

What that chain does *not* cover: the rendered panel itself. Run the UI before closing issue 11.

**Both services booted clean** (`./mvnw spring-boot:run` + `uv run uvicorn`, Docker Desktop was
down so compose was not used) — which is what proves the `AgentClient` bean wiring and the new
`pulse.agent.*` properties resolve.

## Verify

```bash
docker compose up
# open localhost:3000, use chat, inspect the routing panel
cd api && ./mvnw test
```
