# Standardized Resume Scoring Rubric — SWE (Backend / Fullstack)

**Purpose:** Provide deterministic, reproducible scoring criteria for LLM-based evaluation of software engineering resumes for backend and fullstack roles **without a specific job description.** Skills are scored against market demand (what the majority of backend/fullstack JDs ask for in 2024–2026). Every criterion has a concrete definition, point value, and examples of pass/fail so scores are consistent across runs.

---

## Scoring Overview

| Section | Max Points | Weight |
|---------|-----------|--------|
| 1. Technical Skills Match | 25 | Core gate |
| 2. Professional Experience | 30 | Highest signal |
| 3. Impact & Quantification | 15 | Differentiator |
| 4. Education & Credentials | 10 | Baseline |
| 5. Project Complexity | 10 | Depth signal |
| 6. Resume Quality & Parsability | 10 | ATS compatibility |
| **Total** | **100** | |

**Pass threshold:** 60/100 to advance to human review.
**Auto-advance:** 80+ triggers priority routing to hiring manager.

---

## Section 1 — Technical Skills Match (25 pts)

Since no specific job description is available, score against **market demand** — the technologies that appear most frequently in backend/fullstack job postings. The tiers below are based on aggregate job market data and should be updated periodically.

### Language & Framework Market Tiers

**Tier 1 (High demand):** Python, Java, TypeScript/JavaScript, Go, C#
**Tier 2 (Solid demand):** Kotlin, Rust, Ruby, C++, Scala, Swift (server-side)
**Tier 3 (Niche/declining):** PHP, Perl, R, Lua, Elixir, Clojure, Haskell, COBOL, Fortran

**Framework Tier 1:** Spring/Spring Boot, Django/Flask/FastAPI, Express/NestJS/Next.js, ASP.NET, Rails, Gin/Fiber
**Framework Tier 2:** Laravel, Phoenix, Actix, Ktor, Play, Micronaut

**Data Tier 1:** PostgreSQL, MySQL, MongoDB, Redis, Kafka, DynamoDB, Elasticsearch
**Data Tier 2:** Cassandra, Neo4j, CockroachDB, RabbitMQ, Pulsar, ClickHouse

**Infra Tier 1:** AWS, GCP, Azure, Docker, Kubernetes, Terraform, GitHub Actions, Jenkins, Datadog
**Infra Tier 2:** Pulumi, Nomad, ArgoCD, Prometheus/Grafana, CloudFormation

### 1A. Primary Language Proficiency (10 pts)

Evaluate whether the candidate demonstrates working-level proficiency in market-relevant languages.

| Score | Criteria |
|-------|----------|
| 10 | ≥1 Tier 1 language appears in ≥2 professional experience bullet points **and** is listed in skills section. |
| 7 | ≥1 Tier 1 language appears in ≥1 professional bullet **or** a Tier 2 language appears in ≥2 bullets with concrete detail. |
| 4 | Only Tier 1/2 languages listed in skills section with no supporting context, **or** a Tier 3 language demonstrated in depth. |
| 2 | Only Tier 3 languages demonstrated with context. |
| 0 | No recognizable programming language evidence. |

**What counts as "appears in a professional bullet":** The language is named explicitly in connection with a delivered feature, system, or responsibility — not just listed alongside a job title. Example pass: "Rewrote the payment service in Go, reducing p99 latency from 800ms to 120ms." Example fail: "Technologies: Java, Python, Go" in a standalone skills block with no further mention.

**Polyglot bonus:** If the candidate demonstrates proficiency (bullet-level evidence) in ≥3 Tier 1/2 languages, add +2 to the raw score (capped at 10). Rationale: language breadth signals adaptability, which matters when there is no specific JD to match against.

### 1B. Backend Frameworks & Runtime (5 pts)

| Score | Criteria |
|-------|----------|
| 5 | ≥1 Tier 1 framework used in a described production system with at least one concrete detail (scale, purpose, or outcome). |
| 3 | Tier 1 framework named in bullets without detail, **or** Tier 2 framework with concrete detail. |
| 1 | Any framework listed in skills section only, or only Tier 2 frameworks without context. |
| 0 | No backend framework evidence. |

### 1C. Data Layer (5 pts)

Evidence of working with databases, caches, queues, or data pipelines.

| Score | Criteria |
|-------|----------|
| 5 | Resume describes designing, optimizing, or operating a data system (e.g., schema design, query optimization, migration, replication, partitioning) using Tier 1 data technologies with specifics. |
| 3 | Names ≥2 data technologies (Tier 1 or 2) with some usage context. |
| 1 | Data technologies listed in skills only, or only Tier 2/unlisted data tech with no context. |
| 0 | No data layer evidence. |

### 1D. Infrastructure & DevOps Literacy (5 pts)

Evidence of deployment, CI/CD, containerization, cloud, or observability.

| Score | Criteria |
|-------|----------|
| 5 | Describes owning or building infrastructure using Tier 1 infra tools with outcome detail (e.g., "designed CI/CD pipeline with GitHub Actions deploying to EKS," "set up Datadog dashboards reducing MTTR by 40%"). |
| 3 | Mentions ≥2 infra tools (Tier 1 or 2) with light context. |
| 1 | Infra tools in skills list only. |
| 0 | No infrastructure or DevOps evidence. |

---

## Section 2 — Professional Experience (30 pts)

### 2A. Years of Relevant Experience (10 pts)

Calculate from employment dates on the resume. Only count roles where the title or description indicates software engineering, development, or a closely adjacent function (SRE, data engineering, ML engineering). Do not count unrelated roles. Overlapping roles count once.

| Score | Criteria |
|-------|----------|
| 10 | ≥5 years of relevant engineering experience. |
| 7 | 3–4 years of relevant engineering experience. |
| 4 | 1–2 years of relevant engineering experience. |
| 2 | <1 year professional experience but has internships or substantial project work. |
| 0 | No dateable engineering experience. |

**Date calculation rules:** If only years are provided (e.g., "2020–2023"), assume January start and December end. "Present" or "Current" = scoring date. Round to nearest half-year.

### 2B. Role Progression & Scope (10 pts)

| Score | Criteria |
|-------|----------|
| 10 | Clear upward trajectory (e.g., SWE → Senior → Staff/Lead) **or** consistently held senior-scope responsibilities: system design ownership, mentoring, cross-team coordination, technical decision-making. |
| 7 | Lateral moves at consistent level with increasing scope or complexity evident from bullet content. |
| 4 | Flat trajectory, single role, but responsibilities are appropriate for the target level. |
| 0 | Titles and descriptions suggest the candidate is significantly below the target seniority, or experience is too vague to evaluate scope. |

### 2C. Relevance of Most Recent Role (10 pts)

The most recent (or current) role carries outsized signal.

| Score | Criteria |
|-------|----------|
| 10 | Current/most recent role is a backend or fullstack engineering position at a company building software as a primary business function, with bullet points describing backend systems work. |
| 7 | Most recent role is engineering-adjacent (frontend-heavy fullstack, data engineering, DevOps/SRE, ML) with some backend overlap. |
| 4 | Most recent role is in tech but not engineering (PM, QA, technical writing) or is engineering in a non-software domain with transferable skills. |
| 0 | Most recent role is non-technical, or there is a gap >2 years since last engineering role with no explanation (freelance, OSS, education). |

---

## Section 3 — Impact & Quantification (15 pts)

### 3A. Quantified Outcomes (10 pts)

Count the number of bullet points across the resume that contain a **specific, measurable result** tied to the candidate's work.

**What qualifies:** A number or percentage attached to a business or system outcome the candidate influenced. Examples: "reduced API latency by 35%," "processed 2M events/day," "cut deployment time from 45 min to 8 min," "grew DAU from 50K to 200K," "eliminated $120K/yr in infra costs."

**What does not qualify:** Team size ("team of 5"), vague scale ("large-scale system"), technology counts ("used 10+ microservices"), or unattributed company metrics ("company revenue grew 20%" with no link to candidate's work).

| Score | Criteria |
|-------|----------|
| 10 | ≥4 quantified outcome bullets. |
| 7 | 2–3 quantified outcome bullets. |
| 4 | 1 quantified outcome bullet. |
| 0 | Zero quantified outcomes anywhere on the resume. |

### 3B. Scope of Impact (5 pts)

Evaluate the **broadest credible scope** described across all experience entries.

| Score | Criteria |
|-------|----------|
| 5 | Org-wide or company-wide impact described: platform/infrastructure used by multiple teams, company-wide migration, customer-facing system serving the majority of users. |
| 3 | Team-level impact: owned a service, led a feature end-to-end, improved a team workflow. |
| 1 | Task-level contributions only: fixed bugs, implemented tickets, assisted with features. |
| 0 | No impact or scope can be inferred from the resume text. |

---

## Section 4 — Education & Credentials (10 pts)

### 4A. Degree (6 pts)

| Score | Criteria |
|-------|----------|
| 6 | BS/BA or higher in Computer Science, Software Engineering, or closely related field (CE, EE, Math, Physics, Data Science). |
| 4 | BS/BA in an unrelated field **plus** a coding bootcamp, relevant certificate program, or demonstrable self-taught trajectory (significant OSS contributions, shipped products). |
| 2 | Coding bootcamp or certificate program as highest credential. |
| 0 | No degree or formal training mentioned. |

**Important:** A missing degree should never be a hard disqualifier if Sections 1–3 score ≥45/70. Override rule: if the candidate has ≥5 years of professional SWE experience, set the floor for this subsection at 4 regardless of educational background.

### 4B. Certifications & Continued Learning (4 pts)

| Score | Criteria |
|-------|----------|
| 4 | Holds ≥1 industry-recognized certification relevant to the role: AWS Solutions Architect, GCP Professional Cloud Architect, Kubernetes (CKA/CKAD), or equivalent. |
| 2 | Relevant coursework, MOOCs, or non-proctored certifications (e.g., Coursera specializations) mentioned. |
| 0 | No certifications or continuing education mentioned. |

---

## Section 5 — Project Complexity (10 pts)

Evaluate the **single most complex system or project** described on the resume.

### Complexity Indicators (check all that apply, then score)

- **Distributed systems:** microservices, event-driven architecture, service mesh, multi-region deployment.
- **Scale:** explicit mention of high throughput (>10K RPS), large data volumes (>1TB), high availability (99.9%+ SLA), or large user bases (>100K).
- **Architectural ownership:** the candidate designed, proposed, or led the architecture — not just implemented within an existing one.
- **Hard problems:** concurrency, consistency guarantees, real-time processing, data pipeline correctness, performance optimization, security-critical systems.
- **Cross-system integration:** coordinating across ≥3 services, teams, or external APIs.

| Score | Criteria |
|-------|----------|
| 10 | ≥4 complexity indicators present in a single described project/system. |
| 7 | 2–3 complexity indicators present. |
| 4 | 1 complexity indicator present. |
| 2 | Projects described but no complexity indicators — CRUD apps, tutorial-level work, or descriptions too vague to assess. |
| 0 | No projects or systems described beyond job title. |

---

## Section 6 — Resume Quality & Parsability (10 pts)

These criteria ensure the resume can be reliably processed by automated systems and read quickly by humans.

### 6A. Structural Parsability (5 pts)

| Score | Criteria |
|-------|----------|
| 5 | Resume has clearly delimited sections (Experience, Skills, Education at minimum), uses standard date formats (MM/YYYY, YYYY, or Month YYYY), each role has company name + title + dates, and bullet points are discrete statements (not run-on paragraphs). |
| 3 | Minor structural issues: one missing date, inconsistent formatting, sections present but poorly labeled. |
| 1 | Major structural issues: no clear section boundaries, narrative paragraphs instead of parsable entries, missing company names or titles. |
| 0 | Resume is essentially unparsable: single block of text, image-only, or heavily stylized with no extractable structure. |

### 6B. Conciseness & Signal Density (5 pts)

| Score | Criteria |
|-------|----------|
| 5 | Resume is ≤2 pages, every bullet contains either a technical detail or a measurable outcome, no filler phrases ("responsible for," "participated in," "helped with" without specifics). |
| 3 | Resume is ≤3 pages, most bullets are substantive, minor filler present. |
| 1 | Resume is >3 pages **or** majority of bullets are duties-based ("responsible for X") with no outcomes or specifics. |
| 0 | Resume is a wall of text with no actionable information, or is ≤1 substantive sentence. |

---

## Scoring Output Format

When an LLM evaluates a resume using this rubric, it must return the following structured output:

```json
{
  "candidate_id": "<filename or identifier>",
  "scoring_date": "<ISO 8601>",
  "sections": {
    "technical_skills": {
      "primary_language": { "score": 0, "max": 10, "rationale": "" },
      "backend_frameworks": { "score": 0, "max": 5, "rationale": "" },
      "data_layer": { "score": 0, "max": 5, "rationale": "" },
      "infra_devops": { "score": 0, "max": 5, "rationale": "" }
    },
    "experience": {
      "years": { "score": 0, "max": 10, "rationale": "" },
      "progression": { "score": 0, "max": 10, "rationale": "" },
      "recency": { "score": 0, "max": 10, "rationale": "" }
    },
    "impact": {
      "quantified_outcomes": { "score": 0, "max": 10, "rationale": "" },
      "scope": { "score": 0, "max": 5, "rationale": "" }
    },
    "education": {
      "degree": { "score": 0, "max": 6, "rationale": "" },
      "certifications": { "score": 0, "max": 4, "rationale": "" }
    },
    "project_complexity": { "score": 0, "max": 10, "rationale": "" },
    "resume_quality": {
      "parsability": { "score": 0, "max": 5, "rationale": "" },
      "conciseness": { "score": 0, "max": 5, "rationale": "" }
    }
  },
  "total_score": 0,
  "max_score": 100,
  "decision": "ADVANCE | HOLD | REJECT",
  "flags": [],
  "summary": ""
}
```

### Decision Logic

- **ADVANCE** (≥60): Route to hiring manager for human review.
- **HOLD** (45–59): Route to recruiter for manual triage — may have potential but gaps need human judgment.
- **REJECT** (<45): Auto-reject with generated rationale.
- **Auto-priority** (≥80): Flag for expedited review.

### Flag Definitions

The `flags` array should contain any applicable flags from this list:

| Flag | Trigger |
|------|---------|
| `OVERQUALIFIED` | Total score ≥85 — likely a strong senior/staff candidate. |
| `CAREER_SWITCH` | Most recent role is non-engineering but candidate has prior engineering experience. |
| `GAP_>2Y` | Employment gap exceeding 2 years with no explanation. |
| `NO_QUANTIFICATION` | Section 3A score = 0. |
| `SKILLS_LIST_ONLY` | Sections 1A–1D all scored ≤1 (skills listed but never demonstrated). |
| `EXPERIENCE_OVERRIDE` | Section 4A floor override triggered (≥5 yrs experience, no degree). |
| `NICHE_STACK_ONLY` | All demonstrated languages/frameworks are Tier 3 — limits job market fit. |
| `POLYGLOT` | Polyglot bonus applied in Section 1A. |

---

## LLM Prompt Template

The following system prompt can be appended to an LLM call to invoke this rubric:

```
You are a resume evaluator for a general CS career stats platform.
You will receive a candidate's resume. There is NO specific job
description. Score the resume using the SWE Backend/Fullstack Scoring
Rubric provided below, which evaluates general market readiness
against the most commonly demanded backend/fullstack skills.

Rules:
1. Score each subsection independently. Write a 1-2 sentence rationale
   for every score explaining what evidence you found (or did not find).
2. Quote or paraphrase the specific resume text that justifies each
   score. If no evidence exists, state "No evidence found."
3. Apply override rules (e.g., experience-based education floor,
   polyglot bonus) after initial scoring.
4. Return ONLY the JSON output object. No preamble, no markdown fences.
5. Be conservative: when evidence is ambiguous, score one tier lower
   and note the ambiguity in the rationale.
6. Do not infer technologies from company reputation. Score only what
   the resume explicitly states.
7. Use the Market Tiers provided in the rubric to classify languages,
   frameworks, data technologies, and infra tools. If a technology
   does not appear in any tier, treat it as Tier 3 (niche).

[INSERT RUBRIC HERE]
```

---

## Calibration Notes

To maintain scoring consistency across LLM runs:

- **Anchor on evidence, not inference.** If the resume says "built microservices," that is 1 complexity indicator. Do not infer Kubernetes, Docker, or CI/CD unless stated.
- **Company prestige is not a signal.** A FAANG company name does not add points. Only the described work matters.
- **Penalize vagueness, not brevity.** A short resume with specific, quantified bullets should outscore a long resume full of generic responsibilities.
- **Recency dominates.** When the resume shows growth, weight the most recent 2 roles more heavily in Sections 2 and 5. Older roles provide context but should not override recent trajectory.
- **No partial credit between tiers.** If a candidate falls between two scoring tiers, choose the lower tier and explain in the rationale. This prevents score inflation across runs.
- **Unlisted technologies default to Tier 3.** If a language, framework, or tool does not appear in any tier list, treat it as Tier 3 (niche). The rationale should name the technology and note it is untiered.
- **Tier lists are snapshots.** The market tiers in Section 1 reflect 2024–2026 demand. If this rubric is used beyond that window, the tiers should be refreshed against current job posting data before scoring.
- **Niche does not mean bad.** A Tier 3 language demonstrated with depth and impact still earns points through Sections 2, 3, and 5. The tier system only affects Section 1 (skills match), ensuring the score reflects general employability without punishing specialists across every dimension.
