# Outreachly

<div align="center">

**Lead Enrichment & Cold Outreach SaaS Platform**

_AI-powered, multi-tenant SaaS solution for lead enrichment, email verification, and automated outreach campaigns_

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-14.2-000000.svg)](https://nextjs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.0-blue.svg)](https://www.typescriptlang.org/)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)
[![AWS](https://img.shields.io/badge/AWS-Infrastructure-orange.svg)](https://aws.amazon.com/)

</div>

---

## 📋 Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Documentation](#api-documentation)
- [Database Schema](#database-schema)
- [Deployment](#deployment)
- [Development Guidelines](#development-guidelines)
- [Contributing](#contributing)

---

## 🎯 Overview

**Outreachly** is a comprehensive, production-ready SaaS platform designed to help businesses automate their lead enrichment and cold outreach processes. The platform combines AI-powered personalization, email verification, campaign management, and multi-provider email delivery to create a robust solution for sales and marketing teams.

### What Makes Outreachly Special?

- 🤖 **AI-Powered Personalization**: Leverages OpenAI to generate personalized email templates and improve outreach messaging
- 📊 **Multi-Provider Email Delivery**: Abstracted email provider system supporting AWS SES, Resend, and Gmail with seamless switching
- 🔍 **Advanced Lead Enrichment**: Integrates with Hunter.io to automatically enrich lead data with verified contact information
- 🎯 **Campaign Management**: Multi-step campaign workflows with checkpoints, scheduling, and automation
- 📈 **Real-Time Analytics**: Comprehensive dashboard with engagement metrics, delivery tracking, and performance insights
- 🔐 **Enterprise Security**: OAuth2 authentication, Row-Level Security (RLS), multi-tenancy support
- 🚀 **Cloud-Native**: Deployed on AWS ECS Fargate with auto-scaling, load balancing, and infrastructure as code

---

## ✨ Key Features

### Lead Management

- **CSV Import**: Bulk import leads with intelligent column mapping and validation
- **Lead Enrichment**: Automatic enrichment using Hunter.io API with caching for cost optimization
- **Email Verification**: Real-time email validation before sending campaigns
- **Lead Organization**: Organize leads into custom lists with filtering and search capabilities
- **Deduplication**: Automatic detection and handling of duplicate leads

### Campaign Management

- **Multi-Step Campaigns**: Create complex outreach sequences with multiple checkpoints
- **Campaign Checkpoints**: Define stages with different templates and timing
- **Automated Scheduling**: Intelligent scheduling with rate limiting and deliverability optimization
- **Campaign Analytics**: Track open rates, replies, clicks, and conversions per campaign
- **Status Management**: Pause, resume, and manage campaign lifecycles

### Email Features

- **Provider Abstraction**: Switch between AWS SES, Resend, and Gmail without code changes
- **Template Editor**: Rich text editor with personalization variables (`{{firstName}}`, `{{companyName}}`, etc.)
- **AI Template Generation**: Generate email templates using OpenAI
- **Link Tracking**: Track click-through rates with automatic link shortening
- **Click Tracking Toggle**: Optional per-campaign click tracking
- **Delivery Metrics**: Comprehensive tracking of bounces, complaints, and delivery status

### Analytics & Reporting

- **Dashboard Overview**: Real-time KPIs including active campaigns, total leads, sent emails, and engagement rates
- **Activity Feed**: Real-time feed of all platform activities
- **Engagement Trends**: Visual charts showing email performance over time
- **Delivery Health**: Monitor sending profile health and reputation
- **Compliance Metrics**: Track unsubscribe rates and spam complaints

### Security & Authentication

- **OAuth2 Support**: Google, GitHub, and Microsoft authentication
- **Multi-Tenancy**: Organization-level isolation with Row-Level Security
- **Session Management**: Secure session handling with HTTP-only cookies
- **API Security**: JWT-based authentication for API endpoints

---

## 🛠 Technology Stack

### Frontend

- **Framework**: Next.js 14.2 (React 18)
- **Language**: TypeScript 5.0
- **Styling**: Tailwind CSS with custom design system
- **UI Components**: Radix UI primitives
- **State Management**: React Context API + Custom Hooks
- **Forms**: React Hook Form
- **HTTP Client**: Native Fetch API
- **Authentication**: Supabase SSR with OAuth2

### Backend

- **Framework**: Spring Boot 3.5.5
- **Language**: Java 17
- **Build Tool**: Maven
- **Database**: PostgreSQL (Supabase)
- **ORM**: Spring Data JPA with Hibernate
- **Migrations**: Flyway
- **Security**: Spring Security with OAuth2
- **Async Processing**: Spring AMQP (RabbitMQ)
- **API Documentation**: Spring Actuator

### Infrastructure

- **Cloud Provider**: AWS
- **Container Orchestration**: Amazon ECS Fargate
- **Load Balancer**: Application Load Balancer (ALB)
- **Infrastructure as Code**: Terraform
- **Secrets Management**: AWS Secrets Manager
- **Email Services**: AWS SES, Resend API
- **Monitoring**: CloudWatch Logs
- **Database**: Supabase (PostgreSQL with RLS)

### External Services

- **Lead Enrichment**: Hunter.io API
- **AI Services**: OpenAI API (GPT-4)
- **Email Providers**:
  - AWS Simple Email Service (SES)
  - Resend
  - Gmail API (OAuth2)

---

## 🏗 Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (Next.js)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │   Landing    │  │  Dashboard   │  │  Campaigns   │    │
│  │    Page      │  │              │  │              │    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
└─────────────────────────────────────────────────────────────┘
                           │ HTTPS
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              Application Load Balancer (ALB)                 │
│                    SSL Termination                            │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              Spring Boot API (ECS Fargate)                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │   REST API   │  │   Services   │  │  Schedulers   │    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │ OAuth2 Auth  │  │ Email Service│  │ Enrichment   │    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
└─────────────────────────────────────────────────────────────┘
         │              │              │              │
         ▼              ▼              ▼              ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  PostgreSQL │  │  RabbitMQ   │  │   AWS SES   │  │  Hunter.io  │
│  (Supabase) │  │   (Future)  │  │   Resend    │  │   OpenAI    │
└─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘
```

### Backend Architecture Patterns

1. **Layered Architecture**

   - **Controller Layer**: REST API endpoints with request/response handling
   - **Service Layer**: Business logic and orchestration
   - **Repository Layer**: Data access with JPA repositories
   - **Entity Layer**: Domain models with JPA annotations

2. **Email Provider Abstraction**

   - Unified interface (`EmailProvider`) for all email services
   - Factory pattern for provider instantiation
   - Strategy pattern for provider switching
   - Health monitoring and automatic failover

3. **Multi-Tenancy**

   - Organization-level isolation using `org_id` foreign keys
   - Row-Level Security (RLS) policies in PostgreSQL
   - User-to-organization mapping for access control

4. **Async Processing**
   - Background job processing for lead enrichment
   - Scheduled checkpoint execution for campaigns
   - Async email sending with rate limiting

---

## 📁 Project Structure

```
outreachly/
├── api/                          # Spring Boot Backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/outreachly/outreachly/
│   │   │   │   ├── config/       # Configuration classes
│   │   │   │   │   ├── CorsConfig.java
│   │   │   │   │   ├── EmailProviderConfig.java
│   │   │   │   │   └── OAuth2Config.java
│   │   │   │   ├── controller/  # REST Controllers
│   │   │   │   │   ├── CampaignController.java
│   │   │   │   │   ├── EmailController.java
│   │   │   │   │   ├── LeadEnrichmentController.java
│   │   │   │   │   ├── TemplateController.java
│   │   │   │   │   └── ...
│   │   │   │   ├── service/     # Business Logic
│   │   │   │   │   ├── CampaignService.java
│   │   │   │   │   ├── EnrichmentService.java
│   │   │   │   │   ├── EmailDeliveryService.java
│   │   │   │   │   ├── email/   # Email provider implementations
│   │   │   │   │   │   ├── UnifiedEmailService.java
│   │   │   │   │   │   ├── SesEmailService.java
│   │   │   │   │   │   ├── ResendEmailService.java
│   │   │   │   │   │   └── GmailService.java
│   │   │   │   │   └── ...
│   │   │   │   ├── entity/      # JPA Entities
│   │   │   │   │   ├── Campaign.java
│   │   │   │   │   ├── Lead.java
│   │   │   │   │   ├── EmailEvent.java
│   │   │   │   │   └── ...
│   │   │   │   ├── repository/  # Data Access
│   │   │   │   │   ├── CampaignRepository.java
│   │   │   │   │   ├── LeadRepository.java
│   │   │   │   │   └── ...
│   │   │   │   └── security/   # Security Configuration
│   │   │   │       ├── OAuth2AuthenticationSuccessHandler.java
│   │   │   │       └── OAuth2AuthenticationFailureHandler.java
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── db/migration/  # Flyway migrations
│   │   └── test/
│   ├── pom.xml
│   └── Dockerfile
│
├── app/                          # Next.js Frontend
│   ├── src/
│   │   ├── app/                  # Next.js App Router pages
│   │   │   ├── page.tsx          # Landing page
│   │   │   ├── auth/             # Authentication pages
│   │   │   ├── dashboard/        # Dashboard page
│   │   │   ├── campaigns/        # Campaigns page
│   │   │   ├── leads/           # Leads management
│   │   │   ├── templates/       # Email templates
│   │   │   └── ...
│   │   ├── components/           # React Components
│   │   │   ├── ui/              # shadcn/ui components
│   │   │   ├── campaigns/       # Campaign-specific components
│   │   │   ├── dashboard/       # Dashboard components
│   │   │   ├── email/          # Email-related components
│   │   │   └── ...
│   │   ├── hooks/               # Custom React Hooks
│   │   │   ├── useCampaigns.ts
│   │   │   ├── useLeads.ts
│   │   │   └── ...
│   │   ├── lib/                 # Utilities and helpers
│   │   │   ├── aiService.ts     # OpenAI integration
│   │   │   ├── emailValidation.ts
│   │   │   └── ...
│   │   └── contexts/            # React Contexts
│   │       └── AuthContext.tsx
│   ├── package.json
│   ├── tailwind.config.ts
│   └── tsconfig.json
│
├── infra/                        # Infrastructure as Code
│   ├── environments/
│   │   └── dev/                 # Development environment
│   │       ├── main.tf
│   │       └── outputs.tf
│   └── modules/
│       ├── ecs_api/             # ECS API module
│       ├── network/             # VPC/Networking module
│       └── ses/                 # SES configuration
│
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

- **Java 17** or higher
- **Node.js 18+** and npm/yarn
- **PostgreSQL 15+** (or Supabase account)
- **Maven 3.8+**
- **Docker** (optional, for containerization)
- **AWS CLI** (for deployment)
- **Terraform 1.0+** (for infrastructure)

### Local Development Setup

#### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/outreachly.git
cd outreachly
```

#### 2. Backend Setup

```bash
cd api

# Copy environment configuration
cp src/main/resources/application.properties.example src/main/resources/application.properties

# Edit application.properties with your configuration
# Set database URL, OAuth credentials, API keys, etc.

# Build the project
./mvnw clean install

# Run the application
./mvnw spring-boot:run

# Or use Docker
docker build -t outreachly-api .
docker run -p 8080:8080 outreachly-api
```

**Required Environment Variables:**

- `SUPABASE_SESSION_POOLER`: PostgreSQL connection URL
- `DB_USER`: Database username
- `DB_PASSWORD`: Database password
- `GOOGLE_CLIENT_ID`: OAuth2 client ID
- `GOOGLE_CLIENT_SECRET`: OAuth2 client secret
- `OPENAI_API_KEY`: OpenAI API key
- `HUNTER_ACC_1`: Hunter.io API key
- `AWS_FROM_EMAIL`: AWS SES sender email

#### 3. Frontend Setup

```bash
cd app

# Install dependencies
npm install

# Set up environment variables
cp .env.example .env.local

# Edit .env.local
# NEXT_PUBLIC_API_URL=http://localhost:8080

# Run development server
npm run dev

# Build for production
npm run build
npm start
```

**Frontend Environment Variables:**

- `NEXT_PUBLIC_API_URL`: Backend API URL (default: `http://localhost:8080`)

#### 4. Database Setup

The application uses Flyway for database migrations. Migrations are automatically applied on startup.

**Manual migration (if needed):**

```bash
cd api
./mvnw flyway:migrate
```

**Initial Setup:**

1. Create a PostgreSQL database
2. Update connection string in `application.properties`
3. Run the application - migrations will execute automatically

### OAuth2 Setup

See [OAUTH_SETUP.md](./OAUTH_SETUP.md) for detailed instructions on configuring OAuth2 providers.

### Email Provider Configuration

See [api/EMAIL_PROVIDERS.md](./api/EMAIL_PROVIDERS.md) for email provider setup and switching.

---

## ⚙️ Configuration

### Application Properties

Key configuration options in `api/src/main/resources/application.properties`:

```properties
# Server Configuration
server.port=8080
spring.application.name=outreachly

# Database Configuration
spring.datasource.url=${SUPABASE_SESSION_POOLER}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=false

# OAuth2 Configuration
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}

# Enrichment Configuration
ENRICH_ENABLED=true
ENRICH_RATE_PER_MIN=30
ENRICH_MAX_RETRIES=3
ENRICH_CONFIDENCE_MIN=0.6

# Email Provider Configuration
email.provider=resend
resend.api-key=${RESEND_API_KEY}
aws.ses.region=us-east-1
```

### Feature Flags

- `ENRICH_ENABLED`: Enable/disable lead enrichment
- `WEBHOOK_ENABLED`: Enable webhook notifications
- Email provider selection via `email.provider`

---

## 📡 API Documentation

### Base URL

- **Development**: `http://localhost:8080/api`
- **Production**: `https://api.outreach-ly.com/api`

### Authentication

All API endpoints (except `/api/public/**`) require authentication via OAuth2 session cookie or JWT token.

### Key Endpoints

#### Campaigns

- `GET /api/campaigns` - List all campaigns
- `POST /api/campaigns` - Create a new campaign
- `GET /api/campaigns/{id}` - Get campaign details
- `PUT /api/campaigns/{id}` - Update campaign
- `DELETE /api/campaigns/{id}` - Delete campaign
- `POST /api/campaigns/{id}/checkpoints` - Add checkpoint

#### Leads

- `GET /api/leads` - List leads with filtering
- `POST /api/leads` - Create a lead
- `GET /api/leads/{id}` - Get lead details
- `PUT /api/leads/{id}` - Update lead
- `DELETE /api/leads/{id}` - Delete lead
- `POST /api/leads/enrich` - Enrich leads
- `GET /api/leads/check-email` - Check if email exists

#### Email

- `POST /api/email/send` - Send email (uses configured provider)
- `POST /api/email/send/ses` - Send via AWS SES
- `POST /api/email/send/resend` - Send via Resend
- `GET /api/email/providers` - List available providers
- `POST /api/email/validate` - Validate email template

#### Templates

- `GET /api/templates` - List templates
- `POST /api/templates` - Create template
- `POST /api/templates/{id}/generate` - Generate with AI
- `POST /api/templates/{id}/improve` - Improve with AI

#### Import

- `POST /api/import/csv` - Import CSV file
- `GET /api/import/jobs` - List import jobs
- `GET /api/import/jobs/{id}` - Get import job status

### Response Format

All endpoints return JSON:

**Success Response:**

```json
{
  "data": { ... },
  "message": "Success"
}
```

**Error Response:**

```json
{
  "error": "Error message",
  "details": { ... }
}
```

---

## 🗄 Database Schema

### Core Entities

#### Users

- **Table**: `users`
- **Purpose**: OAuth2 authenticated users
- **Key Fields**: `id`, `email`, `org_id`, `oauth_provider`, `oauth_provider_id`

#### Organizations

- **Table**: `organizations`
- **Purpose**: Multi-tenant organization management
- **Key Fields**: `id`, `name`, `created_at`

#### Campaigns

- **Table**: `campaigns`
- **Purpose**: Outreach campaigns
- **Key Fields**: `id`, `org_id`, `name`, `description`, `status`
- **Status**: `active`, `paused`, `completed`, `inactive`

#### Leads

- **Table**: `leads`
- **Purpose**: Lead contact information
- **Key Fields**: `id`, `email`, `first_name`, `last_name`, `domain`, `company_name`
- **Enrichment**: `phone`, `linkedin_url`, `twitter_url`, `enrichment_data`

#### Campaign Checkpoints

- **Table**: `campaign_checkpoints`
- **Purpose**: Multi-step campaign stages
- **Key Fields**: `id`, `campaign_id`, `template_id`, `delay_days`, `order_index`

#### Email Events

- **Table**: `email_events`
- **Purpose**: Email delivery tracking
- **Key Fields**: `id`, `lead_id`, `campaign_id`, `event_type`, `timestamp`
- **Event Types**: `sent`, `delivered`, `opened`, `clicked`, `replied`, `bounced`, `complained`

#### Templates

- **Table**: `templates`
- **Purpose**: Email templates with personalization
- **Key Fields**: `id`, `org_id`, `name`, `subject`, `content`, `is_html`

#### Enrichment Cache

- **Table**: `enrichment_cache`
- **Purpose**: Cache enrichment results to reduce API costs
- **Key Fields**: `id`, `key_hash`, `provider`, `json`, `created_at`

### Relationships

```
Organizations (1) ──→ (N) Users
Organizations (1) ──→ (N) Campaigns
Campaigns (N) ──→ (N) Leads [via campaign_lead]
Campaigns (1) ──→ (N) Campaign Checkpoints
Campaign Checkpoints (N) ──→ (N) Leads [via campaign_checkpoint_leads]
Templates (1) ──→ (N) Campaign Checkpoints
Leads (1) ──→ (N) Email Events
```

### Indexes

Key indexes for performance:

- `leads.email` (unique)
- `leads.org_id`
- `campaigns.org_id`
- `email_events.lead_id`, `email_events.campaign_id`
- `enrichment_cache.key_hash`, `provider`

---

## 🚢 Deployment

### Infrastructure Setup

The project uses Terraform for infrastructure as code.

#### Prerequisites

1. AWS Account with appropriate permissions
2. Terraform installed
3. AWS CLI configured

#### Deploy Infrastructure

```bash
cd infra/environments/dev

# Initialize Terraform
terraform init

# Plan deployment
terraform plan

# Apply configuration
terraform apply
```

**Infrastructure Components:**

- VPC with public/private subnets
- Application Load Balancer (ALB)
- ECS Fargate cluster
- RDS/PostgreSQL database (or Supabase)
- CloudWatch log groups
- IAM roles and policies
- Secrets Manager for sensitive data

### Application Deployment

#### Docker Build

```bash
# Build backend image
cd api
docker build -t outreachly-api:latest .

# Tag for ECR
docker tag outreachly-api:latest <aws-account-id>.dkr.ecr.<region>.amazonaws.com/outreachly-api:latest

# Push to ECR
aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin <aws-account-id>.dkr.ecr.<region>.amazonaws.com
docker push <aws-account-id>.dkr.ecr.<region>.amazonaws.com/outreachly-api:latest
```

#### ECS Deployment

Update the ECS task definition with the new image URI and deploy:

```bash
aws ecs update-service \
  --cluster <cluster-name> \
  --service <service-name> \
  --force-new-deployment \
  --region <region>
```

#### Frontend Deployment

The Next.js frontend can be deployed to:

- **Vercel** (recommended for Next.js)
- **AWS Amplify**
- **CloudFront + S3**

**Vercel Deployment:**

```bash
cd app
npm install -g vercel
vercel
```

### Environment Variables

Ensure all environment variables are set in:

- ECS task definition (for backend)
- Vercel/Amplify dashboard (for frontend)
- AWS Secrets Manager (for sensitive values)

### Health Checks

The application exposes health check endpoints:

- `/actuator/health` - Spring Boot Actuator health check
- `/health` - Application health check

---

## 💻 Development Guidelines

### Code Style

#### Backend (Java)

- Follow Spring Boot conventions
- Use Lombok for boilerplate reduction
- Package structure: `com.outreachly.outreachly.{layer}.{feature}`
- Use `@Slf4j` for logging
- Implement comprehensive exception handling

#### Frontend (TypeScript)

- Use TypeScript strict mode
- Follow React hooks best practices
- Component structure: feature-based folders
- Use custom hooks for reusable logic
- Implement error boundaries

### Testing

#### Backend Tests

```bash
cd api
./mvnw test
```

#### Frontend Tests

```bash
cd app
npm test
```

### Database Migrations

1. Create migration file: `V{version}__{description}.sql`
2. Place in `api/src/main/resources/db/migration/`
3. Test locally before committing
4. Migrations run automatically on application startup

### Git Workflow

1. Create feature branch: `git checkout -b feature/your-feature`
2. Make changes and commit
3. Push and create pull request
4. Code review before merge

### Code Review Checklist

- [ ] Code follows style guidelines
- [ ] Tests pass locally
- [ ] Database migrations tested
- [ ] API documentation updated
- [ ] Environment variables documented
- [ ] No hardcoded secrets
- [ ] Error handling implemented
- [ ] Logging appropriate

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests if applicable
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

### Contribution Areas

- 🐛 Bug fixes
- ✨ New features
- 📖 Documentation improvements
- 🎨 UI/UX enhancements
- ⚡ Performance optimizations
- 🔒 Security improvements
- 🧪 Test coverage

---

## 👤 Author

**Your Name**

- GitHub: [@MiloNguyen](https://github.com/NguyenVietMy)
- Email: vietmynguyen@umass.edu
- LinkedIn: [Viet My Nguyen](https://www.linkedin.com/in/viet-my-nguyen-92b2b5324/)

---

## 🙏 Acknowledgments

- [Spring Boot](https://spring.io/projects/spring-boot) - Backend framework
- [Next.js](https://nextjs.org/) - Frontend framework
- [shadcn/ui](https://ui.shadcn.com/) - UI component library
- [Hunter.io](https://hunter.io/) - Lead enrichment API
- [OpenAI](https://openai.com/) - AI services
- [Supabase](https://supabase.com/) - Database and authentication

---

## 📚 Additional Documentation

- [OAuth Setup Guide](./OAUTH_SETUP.md)
- [Email Provider Configuration](./api/EMAIL_PROVIDERS.md)
- [Gmail Setup Guide](./GMAIL_EMAIL_SETUP.md)
- [API Help Documentation](./api/HELP.md)

---

<div align="center">

</div>
