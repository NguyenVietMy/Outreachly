# Pulse

Pulse is a personal CS career development platform — SWE readiness assessments, resume scoring, goal tracking, and productivity integrations (GitHub, Obsidian, Slack, Linear).

## Current product shape

- Dashboard aggregates activity from connected integrations
- Personal section for SWE profile, goals, resume scoring, and AI-driven study tasks
- GitHub, Slack, Linear, and Obsidian integrations power the dashboard and activity views
- OpenAI powers AI digest, insights, resume scoring, and task generation

## Stack

- Frontend: Next.js, TypeScript, Tailwind, shadcn/ui
- Backend: Spring Boot, Spring Security OAuth2, Spring Data JPA, Flyway
- Data: PostgreSQL on Supabase
- Infra: AWS ECS Fargate, ALB, Route53, Secrets Manager

## Notes

- Historical migrations still contain older campaign and provider tables, but the current application code no longer uses those retired feature paths.
