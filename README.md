# Beat — Personalized Multi-Cron News Research Digest

Beat is a self-hosted, multi-user personalized news research digest. Define distinct topics with custom schedule times and article target counts (e.g. "AI/ML — 20 articles — 8:00 AM daily", "AI Agents — 10 articles — 1:00 PM daily"). Beat will automatically run multi-step AI research pipelines for these topics and deliver clean, synthesized digests to your email and web UI.

---

## 🛠️ Stack & Architecture

| Layer | Technology | Cost Tier |
|---|---|---|
| **Backend** | Java 21, Spring Boot 3.4 (Spring Web, Spring Data JPA, Spring Security) | Free (Render) |
| **Frontend** | Next.js (App Router), TypeScript, Tailwind CSS, Auth.js (NextAuth v5) | Free (Vercel) |
| **Database** | PostgreSQL (Neon serverless DB) with Prisma (Auth) & Hibernate (Core) | Free |
| **Authentication** | Magic Link Email login via Resend (Auth.js) | Free |
| **Research API** | TinyFish (`domain_type=news` search + markdown fetch) + Jina AI Reader | Free |
| **LLM Engine** | Groq API (`llama-3.3-70b-versatile`) — two-pass clustering & synthesis | Free |
| **Email Delivery** | Resend API (Primary) / Brevo API (Fallback) | Free |
| **Cron System** | GitHub Actions (Polling backend every 5 minutes) | Free |

---

## ⚡ Pipeline Flow

Beat replaces heavy, always-on task schedulers with a lightweight, stateless poll model driven by GitHub Actions:

```
GitHub Actions (Every 5 minutes) -> POST /api/internal/run-due-channels
  │
  ├── 1. Find all due channels based on user-configured time & timezone
  ├── 2. Query Expansion (3-5 targeted sub-queries per channel)
  ├── 3. TinyFish News Search (Multi-query candidate collection)
  ├── 4. Title Normalization & Deduplication Pass
  ├── 5. Content Fetching (TinyFish Fetch + Jina AI Reader fallback)
  ├── 6. Groq LLM Call 1: Cluster & Rank (trims candidates to target count)
  ├── 7. Groq LLM Call 2: Synthesize "Why It Matters" blurbs
  ├── 8. Persist `digest_run` and `news_item` records
  └── 9. Send formatted HTML digest email to the user's inbox
```

---

## 🔑 Environment Variables

All secret keys and credentials are set as environment variables on the backend (Render) and frontend (Vercel).

**Backend (Render / Local):**
| Key Variable | Purpose |
|---|---|
| `DATABASE_URL` | Neon PostgreSQL connection string |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | DB Credentials |
| `TINYFISH_API_KEY` | TinyFish Search & Fetch API key |
| `GROQ_API_KEY` | Groq LLM inference key |
| `RESEND_API_KEY` | Resend email sending key |
| `RESEND_FROM_EMAIL` | Sender address (e.g., `onboarding@resend.dev`) |
| `AUTH_SECRET` | Secret used to validate JWTs from frontend |
| `INTERNAL_CRON_SECRET` | Shared secret for GitHub Actions cron trigger |

**Frontend (Vercel / Local):**
| Key Variable | Purpose |
|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | Backend URL (e.g., `https://beat-fuvv.onrender.com`) |
| `AUTH_SECRET` | NextAuth encryption secret (Must match backend) |
| `AUTH_RESEND_KEY` | Resend key for Magic Links |
| `DATABASE_URL` | Neon DB for Prisma Auth.js adapter |

---

## ➕ How to Use

1. **Sign In**: Navigate to the frontend URL and enter your email address. Check your inbox for the magic login link.
2. **Create a Channel**: Once logged in, click "Create Channel".
   - **Name**: e.g., `DevOps & Cloud`
   - **Topic Query**: e.g., `Kubernetes serverless docker developments`
   - **Article Count**: `10`
   - **Delivery Time**: `09:00`
   - **Timezone**: `Asia/Kolkata`
3. **Wait for the Digest**: Based on your delivery time, GitHub Actions will trigger the pipeline, and you'll receive your digest via email (and can view it in the app). Note: Because GitHub Actions polls every 5 minutes, digests might arrive a few minutes after the exact set time.

---

## 🚀 Deployment

This stack is designed to run 100% on free tiers without requiring a credit card.

1. **Backend (Render)**: Deploy the Spring Boot application as a Web Service on Render's free tier. Add all backend environment variables.
2. **Frontend (Vercel)**: Connect your GitHub repository to Vercel and deploy the `frontend` folder. Add all frontend environment variables.
3. **Cron (GitHub Actions)**: Add the `INTERNAL_CRON_SECRET` to your GitHub repo secrets. The `.github/workflows/run-digests.yml` file will automatically hit your Render backend every 5 minutes.

---

## 💻 Local Development Setup

1. **Clone Repository**:
   ```bash
   git clone https://github.com/kaiizer777/beat.git
   cd beat
   ```

2. **Backend**:
   ```bash
   cd backend
   cp .env.example .env  # Configure your credentials
   ./mvnw spring-boot:run
   ```

3. **Frontend**:
   ```bash
   cd frontend
   cp .env.example .env.local  # Set NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
   npm install
   npm run dev
   ```

---

## 📊 Reliability & Limits

- **Groq API Limit**: Groq free tier imposes a strict ceiling of **1,000 Requests Per Day (RPD)**. Beat uses a dedicated usage tracker to alert when the limit approaches.
- **Stateless Cron**: Transient network errors during GitHub Actions polling are safe; the backend uses a `last_run_at` timestamp to prevent duplicate runs on overlapping polls.