# Beat

Personalized multi-channel news research digest. Define topics, set a schedule per topic, get a deduplicated, ranked, AI-summarized digest by email — no generic RSS slop.

**Example:** "AI/ML — 20 articles — 8:00 AM daily", "AI Agents — 10 articles — 1:00 PM daily", "Full-Stack Job Market — 15 articles — 5:00 PM daily" — each runs independently.

## Stack

| Layer | Tech |
|---|---|
| Backend | Java 21, Spring Boot 3.4 |
| Frontend | Next.js (App Router), TypeScript, Tailwind |
| Database | PostgreSQL (Neon, free tier) |
| Research | TinyFish (search + fetch) |
| LLM | Groq — `llama-3.3-70b-versatile` |
| Email | Resend |
| Hosting | Oracle Cloud Always Free VM (backend) + Vercel (frontend) |

$0 budget. Every service above is free tier.

## How it works

```
Channel (topic + schedule) 
  → TinyFish news search (multi-query expansion)
  → Fetch full article text (TinyFish + Jina Reader fallback)
  → Dedupe + cluster
  → Groq: rank & trim to N articles      (1 call)
  → Groq: synthesize "why it matters"    (1 call)
  → Persist digest_run + news_item
  → Email via Resend
```

2 Groq calls per channel run — designed to stay well under the free tier's 1,000 requests/day.

## Setup

```bash
git clone <repo>
cd beat
```

**Backend**
```bash
cd backend
cp .env.example .env   # fill in DATABASE_URL, TINYFISH_API_KEY, GROQ_API_KEY, RESEND_API_KEY, DIGEST_RECIPIENT_EMAIL, RESEND_FROM_EMAIL
./mvnw spring-boot:run
```

**Frontend**
```bash
cd frontend
cp .env.local.example .env.local   # NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
npm install && npm run dev
```

## Project status

Build follows [`work.md`](./work.md) — phased, one phase per session. See it for the full architecture rationale and current progress.

## Deployment

- Backend → Oracle Cloud Always Free VM, run as a `systemd` service
- Frontend → Vercel, connected to GitHub
- Set `NEXT_PUBLIC_API_BASE_URL` on Vercel to the VM's public address

## Roadmap

- [x] v1 — single-user, multi-channel, scheduled digests
- [ ] v2 — auth (multi-user), per-user recipient + channel isolation

## License

Personal project — no license specified.