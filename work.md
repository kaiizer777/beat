# BEAT — Personalized Multi-Cron News Research Digest

> **INSTRUCTIONS FOR THE CODING AGENT — READ BEFORE TOUCHING ANYTHING**
> 1. Work on **exactly one phase per session**. Do not jump ahead, do not "helpfully" start Phase 4 while doing Phase 2. Finish the phase's checkboxes, stop, wait for the next session.
> 2. Do not invent your own architecture, libraries, table names, or endpoints that aren't specified here. If something is genuinely ambiguous, ask — don't guess and don't improvise a "creative" solution.
> 3. Do not delete, rewrite, or "clean up" code outside the scope of the current phase. No unrelated refactors. No renaming things you weren't asked to rename.
> 4. Every phase has a Checkpoint at the end — actually run it and confirm it works before marking checkboxes done. A checked box you didn't verify is a lie, not progress.
> 5. This is a $0-budget project. Never add a paid service, paid tier, or anything requiring a credit card without flagging it first.
> 6. Keep commits/changes scoped and boring. No mess. No half-finished stubs left behind "for later" unless a checkbox explicitly says so.

---

## Project Summary

**Beat** is a self-hosted, multi-topic news research digest. The user defines multiple independent "channels" (e.g. "AI/ML — 20 news — 8:00 AM daily", "AI Agents — 10 news — 1:00 PM daily", "Full-Stack Dev Market — 15 news — 5:00 PM daily"), each with its own topic, article count, and delivery time. Each channel runs on its own cron schedule, does real multi-query research (not flat RSS aggregation), deduplicates and ranks results, writes an LLM-synthesized "why it matters" blurb per story, and delivers the digest by email. This is a fun/personal-use project — not built to serve external users, no auth-wall SaaS concerns, but built properly end-to-end.

## Locked Architecture Decisions

- **Backend**: Java 21 + Spring Boot 3.x (Spring Web, Spring Data JPA, Spring Scheduling)
- **Frontend**: Next.js (App Router), deployed on **Vercel** (free)
- **Database**: PostgreSQL via **Neon** free tier (100 CU-hrs/mo, 0.5GB — plenty for this)
- **Backend hosting**: **Oracle Cloud Always Free VM** (Ampere A1 or AMD Micro shape — see Phase 1 for capacity fallback). Real always-on Linux box, so Spring's native `@Scheduled`/`TaskScheduler` handles cron directly — no external cron-ping workarounds needed.
- **Search/research**: **TinyFish** (`docs.tinyfish.ai`) — free Search (`domain_type=news`) and Fetch endpoints, 30 req/min search, 150 req/min fetch, no credit card
- **Full article text extraction**: **Jina AI Reader** (`r.jina.ai/<url>`) — free, no key required — as fallback/supplement to TinyFish Fetch
- **LLM (synthesis + ranking)**: **Groq API — `llama-3.3-70b-versatile`** — free tier: **1,000 requests/day, 30 RPM, 12,000 TPM**. This is the binding constraint — the pipeline is designed around batching, not per-article calls.
- **Embeddings (dedup similarity)**: done WITHOUT a separate embeddings API — see Phase 4, uses title/content token-overlap + LLM-assisted clustering in the same Groq call to avoid needing another free-tier account. (Alternative noted in Phase 4 if you want true embeddings later.)
- **Email delivery**: **Resend** free tier (3,000 emails/month, no card) — primary. Brevo (300/day) noted as backup in Phase 6.
- **Scheduling engine**: Spring's `TaskScheduler` with **dynamic per-channel triggers** (`Trigger` interface, not static `@Scheduled(cron=...)`) — because each user channel has its own independently-configured time, and channels can be added/edited at runtime without redeploying.

---

## Phase 1 — Infrastructure Setup & Skeleton Boot

**Goal:** Get empty-but-real Spring Boot backend and Next.js frontend both running locally, Neon DB connected, and Oracle VM provisioned and reachable. Nothing about news/cron logic yet — this phase is pure plumbing.

- [x] 1.1 Create Neon project, get connection string, confirm you can connect via `psql` or a GUI client. Note the free tier: 0.5GB storage, 100 CU-hrs/month, auto-suspends when idle (cold start ~500ms, this is fine and expected).
- [x] 1.2 Scaffold Spring Boot 3.x project (Java 21, Maven or Gradle — pick Maven for simplicity) with starters: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `postgresql` driver, `spring-boot-starter-validation`. Confirm `application.yml` connects to Neon and the app boots with zero entities yet.
- [x] 1.3 Scaffold Next.js app (App Router, TypeScript, Tailwind). Confirm `npm run dev` boots a blank homepage.
- [ ] 1.4 Provision the Oracle Cloud Always Free VM:
  - Sign up for Oracle Cloud (card required for identity verification only, no charge on Always Free resources).
  - Attempt to create an **Ampere A1 Flex** instance (up to 2 OCPU / 12GB RAM under current Always Free limits as of mid-2026 — this was recently reduced from 4 OCPU/24GB, that's expected and fine).
  - **If you hit "Out of host capacity"**: this is common and NOT a bug. Do the following in order: (a) try a different Availability Domain in the same region, (b) wait and retry — Oracle cycles capacity, (c) if still stuck after a few tries, fall back to the **VM.Standard.E2.1.Micro (AMD, always free, 1GB RAM)** shape instead — it's smaller but sufficient for this project's actual load (a Spring Boot app doing scheduled batch jobs, not serving high traffic).
  - Install Java 21 (e.g. via `sdkman` or the distro package manager) and confirm `java -version` works on the VM.
  - Open port 8080 (or your chosen port) in both the Oracle Security List/NSG AND the VM's local firewall (`iptables`/`firewalld`) — this trips up almost everyone, both layers must be opened.
- [ ] 1.5 Deploy the empty Spring Boot skeleton to the VM manually once (build the JAR, `scp` it over, run it with `nohup java -jar app.jar &` or a basic `systemd` service file). Confirm you can hit a health-check endpoint from your own machine's browser using the VM's public IP.
- [ ] 1.6 Deploy the empty Next.js skeleton to Vercel (connect GitHub repo, default settings). Confirm it's live at a `*.vercel.app` URL.
- [x] 1.7 Sign up for and collect API keys for: TinyFish (`agent.tinyfish.ai`), Groq (`console.groq.com`), Resend (`resend.com`). Store all keys as environment variables on the VM (never hardcoded) and in Vercel's environment variable settings for anything the frontend needs directly (should be none in this phase — frontend talks to your backend only, never directly to these third-party APIs).

**Checkpoint:** You have a live public backend URL (Oracle VM) returning a 200 on a health endpoint, a live frontend URL (Vercel) showing a blank page, a Neon DB you can connect to, and all three third-party API keys saved as env vars. Nothing talks to each other yet — that's fine, that's Phase 2+.

---

## Phase 2 — Data Model & Channel CRUD (Backend Only)

**Goal:** Model "channels" (the user's news topics) in Postgres and build plain REST CRUD for them. No cron, no research, no LLM yet — just being able to create/read/update/delete a channel definition.

- [ ] 2.1 Design and create the `channel` entity/table with these fields: `id`, `name` (e.g. "AI/ML"), `topic_query` (free text describing what the channel is about, e.g. "artificial intelligence and machine learning developments"), `article_count` (int, e.g. 10-20), `cron_time` (store as `LocalTime` + a simple daily-recurrence for now — timezone handling is explicitly Phase 2.2, don't skip it), `timezone` (IANA string, e.g. `Asia/Kolkata`), `is_active` (boolean), `created_at`, `updated_at`.
- [ ] 2.2 Handle timezone correctly from the start: store `cron_time` as local time + `timezone` string, not as a raw cron expression — this makes the dynamic-trigger logic in Phase 5 much cleaner. Do not hardcode IST or any single timezone as default in the entity — default should be explicit and configurable.
- [ ] 2.3 Build REST endpoints: `POST /api/channels`, `GET /api/channels`, `GET /api/channels/{id}`, `PUT /api/channels/{id}`, `DELETE /api/channels/{id}`. Add basic request validation (name required, article_count between 5-25, valid IANA timezone string).
- [ ] 2.4 Create a `digest_run` table to later hold the output of each cron execution: `id`, `channel_id` (FK), `run_at`, `status` (`pending`/`success`/`failed`), `error_message` (nullable). Just the table + entity for now — nothing writes to it yet.
- [ ] 2.5 Create a `news_item` table: `id`, `digest_run_id` (FK), `title`, `url`, `source_name`, `published_at`, `summary_blurb` (text, the LLM-written "why it matters"), `rank_position` (int). Just the table + entity for now.

**Checkpoint:** Using Postman/curl/Bruno, you can create a channel (e.g. name="AI/ML", topic_query="artificial intelligence and machine learning news", article_count=20, cron_time=08:00, timezone=Asia/Kolkata), list it, update it, delete it. Data persists correctly in Neon. `digest_run` and `news_item` tables exist but are empty.

---

## Phase 3 — TinyFish Research Layer (Search + Fetch)

**Goal:** Given a single channel's topic, produce a deduplicated pool of ~30-50 candidate raw articles with full text — no LLM involved yet, no ranking yet, just reliable fetching.

- [ ] 3.1 Build a `TinyFishClient` service wrapping the TinyFish REST API. Implement the **Search** call using `domain_type=news` (this is TinyFish's dedicated news search mode — returns publisher + date metadata, use it instead of generic web search). Handle the 30 req/min rate limit with basic backoff on 429s.
- [ ] 3.2 Build query expansion: given a channel's `topic_query`, generate 3-5 sub-queries covering different angles before hitting TinyFish. For this phase, do this expansion with a simple deterministic template (e.g. `"{topic} latest news"`, `"{topic} this week"`, `"{topic} analysis"`) — do NOT call the LLM for query expansion yet, that's wasteful of the 1,000/day Groq budget. LLM-based query expansion is explicitly out of scope; keep it templated.
- [ ] 3.3 Run all sub-queries for one channel through TinyFish Search in sequence (respecting rate limit), collect raw results (title, url, snippet, publisher, date) into one pool per channel run.
- [ ] 3.4 Build a `TinyFishFetchClient` wrapping TinyFish's **Fetch** endpoint to pull full clean markdown/text for each candidate URL (150 req/min limit — comfortably enough for 30-50 URLs per channel run). Add a fallback: if TinyFish Fetch fails or returns near-empty content for a URL, retry that single URL via **Jina AI Reader** (`https://r.jina.ai/<the-url>`, plain GET, no auth needed) before giving up on that article entirely.
- [ ] 3.5 Basic dedup pass (cheap, no LLM): normalize titles (lowercase, strip punctuation) and drop near-identical titles across sources, and drop exact duplicate URLs. This is a coarse first pass — the smarter semantic dedup happens in Phase 4.
- [ ] 3.6 Write a manual test harness (a temporary controller endpoint or a `CommandLineRunner` you can trigger, your choice) that takes a topic string and prints the final deduped pool of articles with full text to logs, so you can visually sanity-check quality before wiring anything else to it.

**Checkpoint:** Calling your test harness with topic "AI agents" produces ~20-40 real, deduplicated, full-text articles from the last few days, logged clearly. No garbage/broken fetches silently included — failed fetches should be dropped, not passed downstream as empty content.

---

## Phase 4 — Ranking, Clustering & LLM Synthesis (Groq)

**Goal:** Turn the raw article pool from Phase 3 into the final ranked list of N articles (N = channel's `article_count`) with a synthesized "why it matters" blurb each — designed to fit Groq's 1,000 req/day budget.

- [ ] 4.1 Build a `GroqClient` service using the OpenAI-compatible endpoint (`https://api.groq.com/openai/v1/chat/completions`), model = **`llama-3.3-70b-versatile`**. Implement basic 429 handling with exponential backoff (respect the `x-ratelimit-*` response headers rather than guessing).
- [ ] 4.2 **Budget math — do this before writing code, not after**: with ~5-10 channels running daily at different times, you have roughly 1,000 Groq requests/day to spend across ALL of them combined. Design the pipeline to use **exactly 2 Groq calls per channel run**, not one call per article:
  - **Call 1 — Cluster + Rank**: send the full pool of deduped articles (title + short snippet only, not full text, to stay under 12K TPM) in ONE prompt, ask the model to return JSON: which articles are duplicate/same-story clusters, and a ranked order by relevance+recency+source-diversity, trimmed to the channel's `article_count`.
  - **Call 2 — Synthesize blurbs**: for the final trimmed list only (e.g. top 20, not the full 40-article pool), send full article text in one batched prompt and ask for a JSON array of 2-3 sentence "why it matters" blurbs, one per article, grounded strictly in the provided text (explicitly instruct the model not to add outside claims).
- [ ] 4.3 Enforce strict JSON-only responses from Groq (system prompt instructs JSON-only output, no preamble) and write defensive parsing — if the model wraps the JSON in markdown fences or adds commentary, strip that before `JSON.parse`/Jackson deserialize. Log and gracefully skip (not crash) on a malformed response for a single channel run.
- [ ] 4.4 Wire Phase 3's output directly into Call 1 → Call 2, producing a final ordered list of `{title, url, source, published_at, blurb}` ready to persist.
- [ ] 4.5 Persist the result: create a `digest_run` row (status=success) and the corresponding `news_item` rows (with `rank_position` set) for that channel. On any failure in the pipeline (TinyFish or Groq), persist a `digest_run` row with status=failed and a useful `error_message` instead of silently losing the run.

**Checkpoint:** Running the full pipeline end-to-end for one channel (topic → TinyFish → dedupe → Groq cluster/rank → Groq synthesize → persisted `digest_run` + `news_item` rows) uses exactly 2 Groq calls and produces N well-ranked, non-duplicate articles with grounded blurbs, visible in the DB.

---

## Phase 5 — Dynamic Multi-Channel Cron Scheduling

**Goal:** Every active channel fires automatically at its own configured time, independently, without redeploying the app when channels are added/edited/deleted.

- [ ] 5.1 Implement Spring's `TaskScheduler` (via `ThreadPoolTaskScheduler` bean, not `@Scheduled` — you need dynamic, not static, schedules) to hold one scheduled trigger per active channel.
- [ ] 5.2 Implement a `Trigger` per channel that computes next-execution-time from the channel's stored `cron_time` + `timezone`, so it correctly fires at, e.g., 08:00 Asia/Kolkata daily regardless of what timezone the VM itself runs in.
- [ ] 5.3 On application startup, load all `is_active=true` channels from the DB and register a trigger for each.
- [ ] 5.4 When a channel is created/updated/deleted via the Phase 2 REST endpoints, update the live scheduler in-memory too (cancel + re-register the trigger) — don't require a restart for schedule changes to take effect.
- [ ] 5.5 Each trigger firing calls the full Phase 3+4 pipeline for that channel's `topic_query` and `article_count`, and persists the resulting `digest_run`/`news_item` rows as designed.
- [ ] 5.6 Add basic overlap protection: if a channel's previous run is somehow still in progress when its next trigger fires (shouldn't normally happen with daily schedules, but be defensive), skip the new trigger and log a warning rather than running two pipelines concurrently for the same channel.

**Checkpoint:** Create two test channels with cron times a few minutes apart in the near future, leave the app running, and watch both fire independently at their correct times without any manual intervention, each producing its own `digest_run`.

---

## Phase 6 — Email Delivery

**Goal:** After each successful `digest_run`, send a clean digest email for that channel.

- [ ] 6.1 Sign up for Resend, verify a sending domain (or use their default onboarding domain for personal-use testing if a custom domain isn't set up yet — note in comments that a verified custom domain improves deliverability but isn't required to get started).
- [ ] 6.2 Build an `EmailService` using Resend's Java-compatible REST API (plain HTTP POST is fine, no need for a heavy SDK). Design one clean HTML email template: channel name + date in the subject line, then each article as title (linked) + source + blurb, in `rank_position` order.
- [ ] 6.3 Store the destination email address as a simple app-level config (env var) for now — this is single-user/personal, not multi-tenant, so don't over-engineer per-channel recipient lists unless you actually want that; if you do, add a `recipient_email` field to the `channel` entity instead.
- [ ] 6.4 Hook email sending into the end of the Phase 5 pipeline: after a `digest_run` is marked `success`, send the email; if sending fails, log it clearly but don't mark the whole `digest_run` as failed (research succeeded even if delivery hiccuped) — add a separate `email_sent` boolean on `digest_run` instead.
- [ ] 6.5 Note Brevo (300 emails/day free, no card) as a documented fallback in code comments/README if Resend's domain verification becomes a blocker — don't implement both, just document the swap path.

**Checkpoint:** A real end-to-end scheduled run (from Phase 5) results in an actual email landing in your inbox with correctly ranked, correctly formatted news items and working links.

---

## Phase 7 — Frontend: Channel Management UI

**Goal:** Replace manual curl/Postman channel management with a real UI on the Next.js app, talking to your Spring Boot backend.

- [ ] 7.1 Build a channel list page: table/card view of all channels showing name, topic, article count, schedule time+timezone, active/inactive toggle, last run status.
- [ ] 7.2 Build a create/edit channel form: name, topic_query (textarea, encourage a descriptive sentence not just a keyword), article_count (5-25 slider or number input), time picker, timezone selector (searchable IANA list, not a raw text field).
- [ ] 7.3 Wire the form to your backend's Phase 2 REST endpoints. Handle validation errors from the backend gracefully in the UI (don't just show a raw 400 with no message).
- [ ] 7.4 Add a delete/deactivate action per channel with a confirmation step.
- [ ] 7.5 Set the backend's public Oracle VM URL as the frontend's API base URL via a Vercel environment variable (`NEXT_PUBLIC_API_BASE_URL` or similar) — never hardcode the IP in source.
- [ ] 7.6 Enable CORS on the Spring Boot backend for your specific Vercel domain only (not `*`), since this is a real deployed pair of services now.

**Checkpoint:** From the live Vercel URL, you can create a new channel, see it appear in the list, edit its schedule time, and deactivate it — all reflected correctly in Neon and picked up by the Phase 5 live scheduler without a backend restart.

---

## Phase 8 — Frontend: Digest History & Reading View

**Goal:** Let the user actually browse past digests in the web UI too, not just receive them by email — this is the "fun to use daily" payoff.

- [ ] 8.1 Build `GET /api/channels/{id}/runs` and `GET /api/runs/{id}/items` backend endpoints exposing `digest_run` history and each run's `news_item` list in rank order.
- [ ] 8.2 Build a per-channel history page on the frontend: list of past runs by date, click into one to see the ranked article list with blurbs and outbound links, styled cleanly (this is a personal reading tool — make it pleasant, not a raw table).
- [ ] 8.3 Add a "run now" manual-trigger button per channel for convenience (calls a new backend endpoint that runs the Phase 3-5 pipeline immediately instead of waiting for the scheduled time) — useful for testing and for catching up on a topic on demand. Add basic protection so this can't be spammed (e.g. disable the button for N seconds after a click, or check that no run for this channel is already `pending`).
- [ ] 8.4 Show a simple loading/pending state while a manually-triggered run is in progress (poll the run's status endpoint every few seconds until it's `success`/`failed`).

**Checkpoint:** You can open the Vercel site, click into any channel, browse its digest history, click "run now" on a channel and watch it go from pending to a fresh set of ranked articles in the UI within roughly a minute or two.

---

## Phase 9 — Reliability, Logging & Polish

**Goal:** Make the always-on VM process something you can trust unattended for weeks, since this now runs your daily information diet.

- [ ] 9.1 Set up the Spring Boot app as a proper `systemd` service on the Oracle VM (auto-restart on crash, starts on VM reboot) instead of a manual `nohup` process — this was a shortcut in Phase 1, fix it properly now.
- [ ] 9.2 Add structured logging for every pipeline stage (TinyFish search/fetch counts, Groq calls used, dedup counts, final article count, email send result) per `digest_run`, so a failed run is debuggable from logs alone without re-running anything.
- [ ] 9.3 Add a simple daily Groq-usage counter (in-memory or a small DB counter reset at UTC midnight) and log a warning if a day's total calls approach the 1,000 RPD ceiling — this is your only hard constraint, make it visible before it silently breaks a run.
- [ ] 9.4 Add basic retry-once logic around the TinyFish and Groq calls specifically for transient network failures (not for genuine bad-response cases) so a flaky moment doesn't fail an entire channel's run.
- [ ] 9.5 Write a short `README.md` covering: how to add a new channel, where all API keys live, how to redeploy after a code change (build JAR → scp → restart systemd service), and the documented Resend→Brevo fallback path from Phase 6.

**Checkpoint:** Reboot the Oracle VM manually and confirm the Spring Boot service comes back up on its own and channels keep firing on schedule afterward. Read back a `digest_run`'s logs and confirm you can tell exactly what happened at every stage without guessing.

---

## Explicitly Out of Scope (do not build unless separately asked)

- Multi-user auth/accounts — this is single-user personal use
- Payment/subscription anything
- Mobile app / push notifications
- True vector embeddings for dedup (template+LLM-cluster approach in Phase 4 is the deliberate free-tier-friendly choice)
- Any paid tier of any service listed above