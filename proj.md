# Beat: Project Architecture and Implementation Details

Beat is a self-hosted, multi-user web application designed to automatically research, synthesize, and deliver personalized news digests on customizable schedules. This document provides a deep dive into the project's architecture, technical decisions, data model, and core workflows.

---

## 1. High-Level Architecture

Beat relies on a decoupled architecture, optimized for $0 cost execution across multiple free-tier cloud providers. 

- **Frontend Application**: Next.js App Router written in TypeScript, styled with Tailwind CSS. Responsible for the user interface, reading digests, managing channels, and handling user authentication via Auth.js (NextAuth v5).
- **Backend Service**: Java 21 Spring Boot 3.4 application. Serves as the core engine for LLM orchestration, external API interactions, and data persistence. Exposes REST APIs secured by JWT validation.
- **Database**: Single PostgreSQL instance hosted on Neon. Shared between the frontend (Auth.js tables via Prisma) and backend (Domain tables via Spring Data JPA).
- **Orchestrator**: GitHub Actions operates as the heartbeat of the application, running a cron job every 5 minutes to trigger the backend's digest processing engine.

---

## 2. Authentication Flow & Security (Phase 10-11)

Beat supports multi-user functionality using passwordless magic links.

### The Auth Loop
1. **Login Request**: User enters their email in the Next.js frontend.
2. **Email Dispatch**: Auth.js uses the Resend adapter to email a secure, time-limited magic link.
3. **Session Creation**: Clicking the link verifies the token, and Auth.js persists a session and user record into the database via the Prisma adapter.
4. **JWT Minting**: The frontend Auth.js configuration uses the `jwt` callback to mint a JSON Web Token signed with `AUTH_SECRET`, containing the user's `id` and `email`.
5. **Backend Validation**: The frontend attaches this JWT as a `Bearer` token on every API request. The Spring Boot backend uses `spring-boot-starter-oauth2-resource-server` and a `OncePerRequestFilter` to cryptographically verify the JWT signature using the same `AUTH_SECRET`. 

**Data Scoping**: Once authenticated, the backend extracts the `user_id` from the JWT. All database queries for channels, runs, and items are strictly scoped to `WHERE user_id = ?`, ensuring isolation between accounts.

---

## 3. Data Model

The PostgreSQL database houses two separate table categories:

**Auth Tables (Managed by Prisma / Auth.js):**
- `User`: Standard user data (id, name, email).
- `Account`, `Session`, `VerificationToken`: Oauth and magic-link internal state.

**Domain Tables (Managed by Spring Data JPA / Hibernate):**
- `channel`: Represents a user's subscription (topic, target count, cron time, timezone, `user_id`, `last_run_at`).
- `digest_run`: A specific execution event for a channel (status, started_at, completed_at, error_message).
- `news_item`: Individual synthesized articles linked to a specific `digest_run` (title, original_url, content_blurb, source_name).

---

## 4. The Research and AI Pipeline

When a channel is due for execution, Beat runs a deterministic, multi-step pipeline powered by TinyFish and Groq.

### 4.1 Query Expansion
Instead of running a single raw query, the LLM expands the user's topic into 3-5 hyper-specific sub-queries to maximize coverage.

### 4.2 Candidate Search & Fetching
- **Search**: The expanded queries are fed into the TinyFish API (`domain_type=news`). TinyFish returns a wide net of candidate URLs.
- **Deduplication**: A normalization pass groups candidates by URL and semantic title similarity to remove duplicates.
- **Fetching**: The backend fetches the raw markdown content of the candidate URLs using TinyFish, falling back to Jina AI Reader (`r.jina.ai`) if TinyFish fails.

### 4.3 LLM Pass 1: Clustering & Ranking
Because the search step gathers more articles than requested, the Groq API (`llama-3.3-70b-versatile`) analyzes the collected markdown. It clusters related stories and trims the list down to the user's exact requested `article_count`, optimizing for diversity and relevance.

### 4.4 LLM Pass 2: Synthesis
For the finalized list of articles, Groq generates a concise, grounded "Why It Matters" summary for each item. This prevents hallucinations by strictly constraining the model to the fetched markdown context.

### 4.5 Delivery
The final digest is saved to the DB and dispatched as a heavily styled HTML email via the Resend API to the user's Auth.js email address.

---

## 5. Stateless Scheduling (GitHub Actions)

Previous iterations used stateful in-memory triggers (`ThreadPoolTaskScheduler`) which mandated an always-on server. The current model shifts to a **stateless poll pattern**:
- A GitHub Action (`.github/workflows/run-digests.yml`) fires every 5 minutes.
- It hits `POST /api/internal/run-due-channels` on the backend, authenticated via a shared `INTERNAL_CRON_SECRET`.
- The backend queries all active channels, evaluates their `cron_time` and `timezone` against the current UTC time, and processes any channel due within the last 5 minutes.
- Overlaps are prevented by tracking `last_run_at` on the `channel` table.

---

## 6. Hosting Strategy

- **Backend**: Render Free Web Service. Sleeps after 15 minutes of inactivity, but wakes up immediately when GitHub Actions hits it every 5 minutes, effectively keeping it warm.
- **Frontend**: Vercel. Provides edge caching, simple Next.js integration, and free SSL.
- **Database**: Neon serverless Postgres. Scales to zero when completely inactive but stays responsive.

This stack delivers an enterprise-grade multi-user SaaS experience without incurring any monthly server costs.
