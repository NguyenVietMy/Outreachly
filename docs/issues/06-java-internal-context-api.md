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

  Landed as `identity/infrastructure/security/InternalApiSecurityConfig` — a second
  `SecurityFilterChain` at `@Order(1)` with `securityMatcher("/internal/**")`, stateless and with
  anonymous auth disabled, running `InternalTokenFilter` ahead of the OAuth2 chain. The cookie
  carries no authority on this chain, so a logged-in browser session gets 401, not the app's usual
  302 to Google.
- Route path `/internal/**` must be excluded from the public ALB listener rule (enforced in issue 11).
- Keep responses lean: the GitHub README truncation to 2000 chars currently done in
  `ChatService.java:145` moves here.

## Security requirement

This endpoint exposes full resume text and profile data by `userId` with no user session. A leaked
or missing token is a full data breach. It must be:

- [x] A high-entropy secret from AWS Secrets Manager in prod, never a default value —
      `pulse.internal.token=${INTERNAL_TOKEN:}`, and a blank expected token rejects every request
      (fail closed). 256-bit value in `pulse/dev/INTERNAL_TOKEN`, wired into the api task's
      `secret_arns`.
- [x] Rejected (401) when absent, empty, or wrong — verified by test
- [x] Constant-time compared — `MessageDigest.isEqual` on the UTF-8 bytes
- [x] Not logged, not included in trace attributes — the rejection log records method and path only

Also landed here, though the issue defers it to 11: the public HTTPS listener now answers
`/internal/*` with a fixed 404 (`aws_lb_listener_rule.block_internal`, priority 1), so the endpoint
is not internet-reachable even before the agent is deployed. **Needs a manual `./spinup.ps1`** —
CI deploys the image, not Terraform.

## Acceptance criteria

- [x] Three endpoints return correct data for a seeded user
- [x] 401 without/with wrong token (test)
- [x] No session-cookie path reaches these routes (test)
- [x] `ChatService` still compiles and behaves identically — this issue **adds** the API, it does not
      yet rewire chat (that is issue 08)
- [x] `mvn test` green

## Verify

```bash
cd api && ./mvnw test -Dtest=InternalContextControllerTest
curl -H "X-Internal-Token: $TOKEN" "localhost:8080/internal/context/profile?userId=1"
curl "localhost:8080/internal/context/profile?userId=1"   # expect 401
```

### Result (2026-07-31)

`./mvnw test -Dtest=InternalContextControllerTest` — 7 tests, 0 failures. Full `./mvnw test` — 78
tests, 0 failures.

Live against the running app (port 8081; 8080 was occupied), token `dev`, `userId=5` (the seeded
account — ids 1–4 are empty):

| Request | Result |
|---|---|
| no `X-Internal-Token` | 401 `{"error":"Missing or invalid X-Internal-Token"}` |
| wrong token | 401 |
| empty token header | 401 |
| `/profile` with token | 200 — profileMarkdown 3494 chars, targetRole, graduationYear 2029, 5 axis scores, leetcodeStats, resumeText 3099 chars, `resumeChars: 3099` |
| `/items` with token | 200 — 0 goals, 108 uncompleted tasks, 5 roadmap items |
| `/github` with token | 200 — 2 repos with READMEs, truncated to ≤2000 chars |
