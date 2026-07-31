# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

IMPORTANT: Always read `PLAN.md` at the start of a session. It tracks the active evals/cost-observability workstream — current phase, locked-in decisions, and task status. Update its checkboxes as work lands.

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

Tradeoff: These guidelines bias toward caution over speed. For trivial tasks, use judgment.

1. Think Before Coding
Don't assume. Don't hide confusion. Surface tradeoffs.

Before implementing:

State your assumptions explicitly. If uncertain, ask.
If multiple interpretations exist, present them - don't pick silently.
If a simpler approach exists, say so. Push back when warranted.
If something is unclear, stop. Name what's confusing. Ask.
2. Simplicity First
Minimum code that solves the problem. Nothing speculative.

No features beyond what was asked.
No abstractions for single-use code.
No "flexibility" or "configurability" that wasn't requested.
No error handling for impossible scenarios.
If you write 200 lines and it could be 50, rewrite it.
Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

3. Surgical Changes
Touch only what you must. Clean up only your own mess.

When editing existing code:

Don't "improve" adjacent code, comments, or formatting.
Don't refactor things that aren't broken.
Match existing style, even if you'd do it differently.
If you notice unrelated dead code, mention it - don't delete it.
When your changes create orphans:

Remove imports/variables/functions that YOUR changes made unused.
Don't remove pre-existing dead code unless asked.
The test: Every changed line should trace directly to the user's request.

4. Goal-Driven Execution
Define success criteria. Loop until verified.

Transform tasks into verifiable goals:

"Add validation" → "Write tests for invalid inputs, then make them pass"
"Fix the bug" → "Write a test that reproduces it, then make it pass"
"Refactor X" → "Ensure tests pass before and after"
For multi-step tasks, state a brief plan:

1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

These guidelines are working if: fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

## Project Overview

Pulse is a personal CS career development platform — SWE readiness assessments, resume scoring, goal tracking, and productivity integrations (GitHub, Obsidian, Slack, Linear).

## Repository Structure

Monorepo with three top-level directories:

- **`api/`** — Spring Boot 3.5.5 backend (Java 17, Maven). Runs on port 8080.
- **`app/`** — Next.js 14 frontend (TypeScript, App Router, Tailwind + shadcn/ui). Runs on port 3000.
- **`infra/`** — Terraform modules for AWS (VPC, ECS Fargate, SES).

## Build & Run Commands

### Backend (api/)
```bash
cd api
./mvnw spring-boot:run          # starts API on localhost:8080
./mvnw clean package             # build JAR (output in target/)
./mvnw clean package -DskipTests # build without tests
./mvnw test                      # run tests
```
Config is loaded from `application.properties` which imports `optional:file:.env.local.properties` for local secrets. The `.env.local.properties` and `.env.prod.properties` files are gitignored.

### Frontend (app/)
```bash
cd app
npm install
npm run dev    # starts Next.js dev server on localhost:3000
npm run build  # production build
npm start      # serve production build
```
The API URL defaults to `http://localhost:8080` in development (set via `NEXT_PUBLIC_API_URL`).

### Docker (api/)
```bash
cd api
docker build -t pulse-api .
docker run -p 8080:8080 --env-file .env.local.properties pulse-api
```

### Infrastructure (infra/)
```bash
cd infra/environments/dev
terraform init
terraform plan
terraform apply
```
Modules: `network` (VPC/subnets), `ecs_api` (ECS Fargate cluster/task/ALB).

## Architecture

### Backend Package Structure
All Java code under `com.pulse.pulse`:
- `controller/` — REST endpoints (dashboard, personal, integrations, organizations, auth, settings)
- `service/` — Business logic. Key services:
  - `DashboardService` — Aggregates integration activity metrics, trends, and AI digest generation
  - `IntegrationService` — Manages user integrations (GitHub, Obsidian, Slack, Linear) with OAuth and API key flows
  - `IntegrationSyncScheduler` — Background auto-sync for connected integrations
  - `AnthropicService` — Anthropic Java SDK client (Claude Sonnet 5 for resume scoring + eval judge, Haiku 4.5 for digest, insights, chat, and task generation); `EmbeddingService` remains on OpenAI (`text-embedding-3-small`) for pgvector embeddings
  - `PersonalService` — SWE profile, goals, resume scoring, and AI-driven study tasks
  - `ResumeService` — PDF parsing and resume text extraction
- `service/integration/` — Per-provider sync implementations:
  - `IntegrationProvider` interface → `GitHubIntegrationProvider`, `ObsidianIntegrationProvider`, `SlackIntegrationProvider`, `LinearIntegrationProvider`
- `entity/` — JPA entities (Lombok throughout)
- `config/` — CORS, OAuth2, integration config
- `security/` — OAuth2 success/failure handlers
- `dto/` — Request/response DTOs
- `repository/` — Spring Data JPA repositories

### Database
PostgreSQL on Supabase. Schema managed by **Flyway** migrations (`api/src/main/resources/db/migration/`, V1–V54). `ddl-auto=none` — always use migrations for schema changes.

### Frontend Structure
- `src/app/` — Next.js App Router pages: dashboard, personal, integrations, settings, onboarding, privacy, terms
- `src/components/` — UI components organized by domain (assessment/, icons/, personal/) plus shared/ and ui/ (shadcn)
- `src/hooks/` — Domain-specific data hooks (useDashboard, useIntegrations, usePersonal)
- `src/lib/` — Utilities (config, utils)
- `src/contexts/AuthContext.tsx` — Google OAuth2 auth state via session cookie (`credentials: "include"` on all API calls)

### Auth Flow
Google OAuth2 with one registration: `google` (login: openid,profile,email). Backend manages sessions; frontend reads auth state from `GET /api/auth/user` and sends cookies with every request.

### Key Design Patterns
- **Integration provider strategy**: `IntegrationProvider` interface per provider. Adding a provider = one class implementing the interface.
- **Frontend hooks pattern**: Each domain entity has a custom hook that encapsulates fetch logic, loading/error state, and CRUD operations.

## Deployment

- **Frontend**: Vercel (auto-deploys from main)
- **Backend**: Docker → AWS ECR → ECS Fargate (0.25 vCPU / 512MB), behind ALB with HTTPS
- **Secrets**: AWS Secrets Manager, injected at ECS task startup
- **CI**: `.github/workflows/deploy-api.yml` builds and deploys on push to `main`. It does *not* run
  Terraform — infrastructure changes are applied manually via the scripts below.

### Spin up and tear down the dev environment

`infra/environments/dev/` holds three **gitignored, local-only** files. They will not exist in a
fresh clone, and a new machine has to recreate them before it can deploy:

| File | Purpose |
|---|---|
| `spinup.ps1` | `terraform apply`, then push every secret value into Secrets Manager |
| `teardown.ps1` | `terraform destroy` (one line, no confirmation of its own beyond Terraform's) |
| `secrets.json` | Flat `{"pulse/dev/KEY": "value"}` map — the actual secret values |
| `github-app-key.pem` | GitHub App private key, handled separately (multi-line PEM) |

```powershell
cd infra/environments/dev
./spinup.ps1     # terraform apply + populate secrets, then push to main to deploy
./teardown.ps1   # terraform destroy
```

Both use AWS profile `pulse` in `us-east-1`. `spinup.ps1` skips any key whose value is blank and
skips `GITHUB_APP_PRIVATE_KEY` in the JSON loop, reading it from `github-app-key.pem` instead — so
an empty entry is silently *not* an error. Check its per-key `OK:` / `SKIPPED:` output rather than
assuming a clean exit means every secret landed.

**Terraform is the source of truth for which env vars the task gets, not `secrets.json`.** In
`main.tf`, `secret_arns` maps a name to a Secrets Manager ARN (value injected at task start) and
`extra_environment` sets plain literals. Adding a secret means three edits: a
`aws_secretsmanager_secret` resource, an entry in `secret_arns`, and a key in `secrets.json`.
A key present in `secrets.json` but absent from `secret_arns` is written to Secrets Manager and
never reaches the container.

**`teardown.ps1` destroys real infrastructure** — VPC, ECS, ALB, and the Secrets Manager entries.
It does not touch Supabase or Qdrant, which are external and hold all the data. Re-running
`spinup.ps1` afterwards repopulates secrets from `secrets.json`, so that file is the thing whose
loss would actually hurt; it is gitignored and exists on one machine.
