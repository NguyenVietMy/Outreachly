# Outreachly

Outreachly is a lead workflow product for importing contacts, enriching data, generating outreach copy, and connecting external tools through lightweight integrations.

## Current product shape

- Workspace is focused on `Leads` and `Import`
- Gmail is exposed as a Google OAuth integration on the Integrations page
- GitHub, Slack, Linear, and Obsidian integrations power the dashboard and activity views
- Hunter is used for enrichment and OpenAI is used for copy generation

## Stack

- Frontend: Next.js, TypeScript, Tailwind, shadcn/ui
- Backend: Spring Boot, Spring Security OAuth2, Spring Data JPA, Flyway
- Data: PostgreSQL on Supabase
- Infra: AWS ECS Fargate, ALB, Route53, Secrets Manager

## Notes

- Gmail uses a separate OAuth registration so sensitive Google consent is requested only when the user explicitly connects Gmail.
- Historical migrations still contain older campaign and provider tables, but the current application code no longer uses those retired feature paths.
