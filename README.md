# Outreachly

**A cold outreach SaaS that combines lead enrichment, AI-generated copy, and multi-provider email campaigns — deployed and live.**

---

## Project Overview

### The Problem

Cold outreach is broken across three problems that most teams solve with three separate tools: finding valid email addresses, generating personalized copy, and scheduling follow-up sequences. The result is manual, fragmented, and slow — CSV exports, copy-pasting into a writing tool, manual sends from Gmail. Nothing talks to each other, and deliverability suffers when everything routes through a shared sending domain.

### What Outreachly Does

Outreachly is a production SaaS that closes that gap. Users import leads via CSV, enrich missing emails through Hunter.io (find + verify), write or AI-generate campaign templates, schedule automated email sequences as campaign checkpoints with timezone-aware delivery, and track opens, clicks, bounces, and complaints — all in one platform. Users can send through their own Gmail account, AWS SES, or Resend, depending on their volume and deliverability needs.

### Goals

- Ship a real, production-deployed product (live at outreach-ly.com)
- Build a pluggable multi-provider email architecture so sending strategy isn't locked in
- Integrate a real enrichment pipeline with local caching to stay inside API rate limits
- Use AI for copy generation and improvement without building or hosting a model
- Deploy on serverless-friendly infrastructure with minimal operational overhead

---

## System Architecture

```
                        ┌──────────────────────────────────────┐
                        │          NEXT.JS FRONTEND            │
                        │  Vercel · outreach-ly.com            │
                        │                                      │
                        │  Leads · Campaigns · Templates       │
                        │  Import · Dashboard · Settings       │
                        └──────────────────┬───────────────────┘
                                           │ HTTPS / session cookie
                                           ▼
                        ┌──────────────────────────────────────┐
                        │         SPRING BOOT API              │
                        │  AWS ECS Fargate · api.outreach-ly.com│
                        │  ALB (HTTP → HTTPS redirect)         │
                        │                                      │
                        │  ┌──────────┐  ┌──────────────────┐  │
                        │  │  Auth    │  │  Checkpoint      │  │
                        │  │  Google  │  │  Scheduler       │  │
                        │  │  OAuth2  │  │  (@Scheduled,    │  │
                        │  └──────────┘  │  every 60s)      │  │
                        │                └──────────────────┘  │
                        │  ┌──────────┐  ┌──────────────────┐  │
                        │  │Enrichment│  │  Email Provider  │  │
                        │  │ Service  │  │  Factory         │  │
                        │  │(5s poll) │  │  (strategy)      │  │
                        │  └──────────┘  └──────────────────┘  │
                        └────────────┬─────────────────────────┘
                                     │
           ┌─────────────────────────┼──────────────────────┐
           ▼                         ▼                      ▼
┌──────────────────┐   ┌─────────────────────┐  ┌──────────────────┐
│   PostgreSQL     │   │   EMAIL PROVIDERS   │  │  EXTERNAL APIs   │
│   (Supabase)     │   │                     │  │                  │
│  • users         │   │  Gmail API          │  │  Hunter.io       │
│  • organizations │   │  (user's own acct,  │  │  email finder +  │
│  • leads         │   │   OAuth token)      │  │  verifier        │
│  • campaigns     │   │                     │  │                  │
│  • checkpoints   │   │  AWS SES            │  │  OpenAI GPT-4o   │
│  • email_events  │   │  (bulk transact.)   │  │  template gen +  │
│  • enrichment_   │   │                     │  │  improvement     │
│    cache         │   │  Resend             │  │                  │
│  • templates     │   │  (alt provider)     │  │  Google OAuth2   │
│  • activity_feed │   └─────────────────────┘  └──────────────────┘
└──────────────────┘
        ▲
        │ SNS webhooks (DELIVERY, BOUNCE, COMPLAINT)
┌──────────────────┐
│   AWS SES        │
└──────────────────┘
```

### Key Components

| Component | Role |
|---|---|
| **API Service** | REST API for all entities: leads, campaigns, templates, CSV import, enrichment, email sending, webhooks, activity feed |
| **Checkpoint Scheduler** | `@Scheduled` job (every 60s) — finds active checkpoints for today, resolves campaign creator timezone, fires `EmailDeliveryService`, updates checkpoint status |
| **Enrichment Service** | Polls pending jobs every 5s — calls Hunter.io finder + verifier, caches results by SHA-256 key, applies email + verification status back to lead |
| **Email Provider Factory** | Strategy pattern — routes sends to Gmail (OAuth token), AWS SES, or Resend by config; same `EmailProvider` interface for all three |
| **Rate Limit Service** | Daily quota (100 emails/day per user/org) backed by `email_events` DELIVERY count; checked before every send |
| **Delivery Tracking** | SES → SNS → `EmailWebhookController` → stores `DELIVERY / BOUNCE / COMPLAINT` events; feeds dashboard KPIs and campaign stats |
| **AI Template Service** | Spring WebFlux client to OpenAI GPT-4o — generate cold email/LinkedIn templates and iteratively improve them (shorter, longer, more professional, higher conversion) |
| **Link Tracking** | Short URL generation + redirect endpoint to track click-through rates per campaign |

---

## Tech Stack

### Frontend
- **Next.js** (TypeScript, App Router)
- **Tailwind CSS** + **shadcn/ui** — full component library (tables, modals, dialogs, rich text editor, date pickers, toasts)
- Custom hooks per domain: `useCampaigns`, `useLeads`, `useCampaignCheckpoints`, `useDeliveryMetrics`, `useActivityFeed`
- Deployed on **Vercel**

### Backend
- **Spring Boot 3.5.5** on **Java 17**
- Spring Security OAuth2 Client — two Google registrations: standard login (`openid,profile,email`) and Gmail send scope (`gmail.send`) via incremental authorization
- Spring Data JPA + **Flyway** (`ddl-auto=none`, fully migration-controlled schema)
- Spring WebFlux (reactive HTTP client for OpenAI API calls)
- Google Gmail API SDK + Jakarta Mail / Angus Mail (MIME message construction for Gmail sends)
- AWS SDK v2 SES (bulk transactional sending with bounce/complaint handling)
- OpenCSV (CSV import with configurable column mapping modal)
- Lombok throughout

### Database
- **PostgreSQL** hosted on **Supabase** (session pooler connection string)
- Flyway versioned migrations — schema is reproducible
- Key tables: `users`, `organizations`, `leads`, `lead_lists`, `org_leads`, `campaigns`, `campaign_leads`, `campaign_checkpoints`, `campaign_checkpoint_leads`, `templates`, `email_events`, `enrichment_jobs`, `enrichment_cache`, `import_jobs`, `activity_feed`

### Infrastructure
- **AWS ECS Fargate** — Spring Boot container, 0.25 vCPU / 512MB, ALB with HTTP→HTTPS redirect
- **AWS SES** — transactional email, bounce/complaint handling via SNS webhooks
- **AWS Secrets Manager** — all credentials injected at ECS task startup (Supabase URL, DB creds, OpenAI key, Hunter keys, Google OAuth)
- **AWS VPC** — public subnets, separate security groups for ALB and ECS task
- **Terraform** — modular IaC: `network` (VPC), `ecs_api` (ECS cluster + task + service + IAM), `ses` (sending config)
- **CloudWatch Logs** — ECS task logs, 14-day retention
- **Vercel** — frontend, zero-config Next.js deployment

### AI / External Services
- **OpenAI GPT-4o** — cold email and LinkedIn message generation, iterative improvement
- **Hunter.io** — email finder (first name + last name + domain → email) and verifier (deliverability: valid / invalid / risky)
- **Google OAuth2 / Gmail API** — users authenticate with Google; platform requests `gmail.send` scope separately to send from their own account

---

## Engineering Decisions

**Multi-provider email via strategy pattern, not a hardcoded provider.**
Different users have different needs: sales reps want Gmail (higher reply rates, no shared domain reputation risk), product teams want SES for cost-efficient bulk sends, developers use Resend for its DX. `EmailProvider` is an interface; `EmailProviderFactory` selects the implementation by a single config property. Adding a new provider is one class and one enum entry — nothing else changes.

**Two separate Google OAuth2 registrations (login vs. Gmail send).**
Login uses `openid,profile,email`. Gmail sending requires `gmail.send` — a much more sensitive scope. Requesting it at login would scare users away. Instead, the app registers a second OAuth client that requests `gmail.send` only when the user explicitly connects Gmail. Incremental authorization keeps the trust surface minimal at sign-up.

**Hunter.io enrichment with SHA-256 content-addressed cache.**
Hunter has rate limits and per-call costs. The enrichment cache stores results permanently keyed by SHA-256 of `provider:email` (or `provider:firstname:lastname:domain`). The next enrichment request for the same lead is a DB read — zero API cost. The scheduler also processes one job at a time with a minimum 1-second inter-call delay, staying within Hunter's rate limits without a dedicated library.

**Supabase over self-hosted RDS.**
Managed connection pooling (session pooler), automated backups, and dashboard visibility are handled without ops work. Flyway migrations ensure the schema remains version-controlled and reproducible regardless of where Postgres runs. For a solo developer, eliminating database ops overhead keeps focus on product.

**Checkpoint scheduler as `@Scheduled`, not a message queue.**
Fast to ship, no additional infrastructure. The `CheckpointScheduler` class has an explicit `TODO` comment in the source: upgrade to RabbitMQ or Redis for reliability. The current implementation prevents duplicate runs via checkpoint status checks rather than message visibility timeouts. It's a known limitation, not an oversight — the right call for an early-stage product that can be migrated when the need arises.

---

## Demo

> Screenshots and video coming soon.

**Live**: [outreach-ly.com](https://www.outreach-ly.com)

Gmail connect flow step-by-step screenshots are in `app/public/connect-gmail/` (Step1–Step3).

---

## Future Improvements

**Upgrade checkpoint scheduler to queue-based delivery.** The `CheckpointScheduler` source has a `TODO` calling this out. The current `@Scheduled` approach works at one ECS instance but would double-send at two. Migrating to RabbitMQ (already declared as a dependency) with visibility timeouts would make delivery idempotent across replicas. The enrichment service has the same structural issue.

**LinkedIn outreach.** The AI service already exposes `platform: "LINKEDIN"` in the template generation request schema — the type definitions and backend API are already wired. The missing piece is a LinkedIn OAuth integration and send API for actually delivering messages.

**Per-user sending reputation tracking.** Rate limits are currently coarse (100 emails/day). A production-grade system would track bounce and complaint rates per user/domain and throttle proactively — or warn before a SES account gets flagged. AWS SES provides per-identity statistics via `GetSendStatistics`; wiring that into a health score on the dashboard would give users actionable deliverability feedback.

**ECS auto-scaling.** The service runs at `desired_count=1`. Adding a target tracking policy on ALB request count or CPU would let the API scale horizontally — useful when multiple users trigger checkpoint sends simultaneously at the same scheduled time.

**Reply detection.** Delivery, bounces, and clicks are tracked. Replies are not. Polling Gmail API for replies (or using a webhook-capable inbox) would close the outreach loop — and enable automatic sequence stopping when a lead responds, which is the most-requested feature in any cold outreach tool.
