# 06 — Java internal context API + shared-secret auth

**Track:** B · Agent  ·  **Blocked by:** —  ·  **Blocks:** 07
**Estimate:** ~3h

## Goal

The agent can read the non-vector context that `ChatService` assembles today, over HTTP, without
duplicating JPA logic in Python.

## Changes

New `personal/api/InternalContextController.java`:

```
GET /internal/context/profile?userId=
    -> { profileMarkdown, targetRole, graduationYear, axisScores,
         leetcodeStats, resumeText, resumeChars }
GET /internal/context/items?userId=
    -> { goals[], tasks[], roadmap[] }        # tasks = uncompleted only
GET /internal/context/github?userId=
    -> { repos[] }                            # name, description, primaryLanguage, readmeContent
```

- Extract the assembly logic out of `ChatService` **as-is**. The formatting strings
  (`=== GOALS ===`, the goal/task/roadmap line formats) move to the agent side in issue 07; these
  endpoints return structured JSON, not pre-formatted blobs.
- Auth: `X-Internal-Token` header checked against `pulse.internal.token`, a Spring Security filter or
  `@PreAuthorize` — whichever fits the existing `security/` setup. These routes must **not** be
  reachable via the session cookie or from the internet.
- Route path `/internal/**` must be excluded from the public ALB listener rule (enforced in issue 11).
- Keep responses lean: the GitHub README truncation to 2000 chars currently done in
  `ChatService.java:145` moves here.

## Security requirement

This endpoint exposes full resume text and profile data by `userId` with no user session. A leaked
or missing token is a full data breach. It must be:

- [ ] A high-entropy secret from AWS Secrets Manager in prod, never a default value
- [ ] Rejected (401) when absent, empty, or wrong — verified by test
- [ ] Constant-time compared
- [ ] Not logged, not included in trace attributes

## Acceptance criteria

- [ ] Three endpoints return correct data for a seeded user
- [ ] 401 without/with wrong token (test)
- [ ] No session-cookie path reaches these routes (test)
- [ ] `ChatService` still compiles and behaves identically — this issue **adds** the API, it does not
      yet rewire chat (that is issue 08)
- [ ] `mvn test` green

## Verify

```bash
cd api && ./mvnw test -Dtest=InternalContextControllerTest
curl -H "X-Internal-Token: $TOKEN" "localhost:8080/internal/context/profile?userId=1"
curl "localhost:8080/internal/context/profile?userId=1"   # expect 401
```
