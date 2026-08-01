# BEAT — Workflow 2: Auth, UI/UX Polish & Deployment

> **INSTRUCTIONS FOR THE CODING AGENT — READ BEFORE TOUCHING ANYTHING**
> 1. Work on **exactly one phase per session**. Do not jump ahead, do not "helpfully" start a later phase while doing an earlier one. Finish the phase's checkboxes, stop, wait for the next session.
> 2. Do not invent your own architecture, libraries, table names, or endpoints that aren't specified here. If something is genuinely ambiguous, ask — don't guess and don't improvise a "creative" solution.
> 3. Do not delete, rewrite, or "clean up" code outside the scope of the current phase. No unrelated refactors. No renaming things you weren't asked to rename.
> 4. Every phase has a Checkpoint at the end — actually run it and confirm it works before marking checkboxes done. A checked box you didn't verify is a lie, not progress.
> 5. This is a $0-budget project. Never add a paid service, paid tier, or anything requiring a credit card without flagging it first.
> 6. Keep commits/changes scoped and boring. No mess. No half-finished stubs left behind "for later" unless a checkbox explicitly says so.

---

## Context

This file picks up after `work.md` (Phases 1-9, feature-complete and audited on the dev server — see `audit.md`). Beat currently works for a single user only. This workflow adds real multi-user auth so friends can use it too, then closes out with UI/UX polish and final deployment.

## Research Findings — Why This Architecture

- **Better Auth cannot be used on the Spring Boot backend.** Better Auth is a TypeScript/Node-only authentication framework — there is no Java/Spring port or SDK. It is not an option for this stack's backend, full stop.
- **The correct pattern for a Next.js + separate Spring Boot backend pair is: Auth.js (formerly NextAuth) on the frontend issues sessions/JWTs, Spring Security validates JWTs on the backend.** This is a well-established, current (2026) pattern — Auth.js v5 is distributed as `next-auth@beta` and is the standard auth library for Next.js App Router apps.
- **Email as the login method**: Auth.js has a **built-in Resend provider** (`next-auth/providers/resend`) that implements magic-link (passwordless) email login — exactly what you asked for, and it reuses the same Resend account already set up in `work.md` Phase 6. No separate email-auth service needed.
- **Important constraint**: Auth.js's email/magic-link provider **requires a database adapter** — verification tokens must be persisted somewhere, they can't be stateless JWT-only for this flow. This means Auth.js needs its own small schema (`users`, `accounts`, `sessions`, `verification_token` tables) living in the same Neon Postgres database, via the official Auth.js Postgres/Drizzle or Prisma adapter (your choice at build time, Prisma has the more mature Auth.js adapter as of 2026 — default to Prisma unless you have a reason not to).
- **Token handoff**: after Auth.js signs a user in on the frontend, the frontend must attach a signed JWT (Auth.js can mint one via its JWT callback) to every request to the Spring Boot backend. The backend validates it with Spring Security's JWT support (a `OncePerRequestFilter`-based filter, same pattern as any standard Spring Security JWT setup) — the backend does NOT talk to Auth.js or Resend directly, it only verifies signatures.

---

## Phase 10 — Auth Foundations (Database + Auth.js Setup)

**Goal:** Get Auth.js fully working on the frontend in isolation — real magic-link email sign-in, real sessions — before touching the Spring Boot backend or any existing app data at all.

- [x] 10.1 Install `next-auth@beta` (Auth.js v5) in the frontend. Choose Prisma as the database adapter (`@auth/prisma-adapter`) and set up Prisma pointed at the same Neon connection string already used by the backend — but Auth.js's tables are separate from Beat's existing tables, don't touch `channel`/`digest_run`/`news_item`.
- [x] 10.2 Run Auth.js's official Prisma schema migration to create its required tables (`User`, `Account`, `Session`, `VerificationToken`) in Neon. Confirm these tables exist and are empty via psql/GUI — do not manually design this schema, use Auth.js's documented schema as-is.
- [x] 10.3 Configure the Resend provider in Auth.js (`next-auth/providers/resend`), reusing the existing `RESEND_API_KEY`. Set a clear `from` address (can reuse `RESEND_FROM_EMAIL` from Phase 6). Add `AUTH_SECRET` (generate via `openssl rand -base64 32`) and `AUTH_RESEND_KEY` (or reference the existing key name, your choice, just be consistent) to the frontend's env vars.
- [x] 10.4 Build the sign-in page: an email input + "Sign in" button hitting Auth.js's email sign-in flow. No custom UI polish yet — this phase is about the flow working, not looking good.
- [x] 10.5 Test the full magic-link loop manually: enter your email, receive the email via Resend, click the link, confirm you land back on the app signed in, and confirm a real row now exists in the `User` table in Neon.
- [x] 10.6 Add a basic "signed in as X / sign out" indicator somewhere visible on the frontend, and confirm sign-out actually clears the session (protected content, if any yet, becomes inaccessible after sign-out).

**Checkpoint:** You can sign in with your email via a real magic link end-to-end, see yourself as signed in, sign out, and sign back in again — all without touching the Spring Boot backend or any existing Beat data.

---

## Phase 11 — Backend JWT Verification & Data Scoping

**Goal:** Make the Spring Boot backend actually enforce auth, and scope all existing data (channels, digest runs, news items) to the signed-in user instead of being global.

- [x] 11.1 Configure Auth.js to mint a signed JWT (via its `jwt` callback) containing at minimum the user's Auth.js `id` and email, using the same `AUTH_SECRET` — this is the token the frontend will send to the backend.
- [x] 11.2 Add `spring-boot-starter-oauth2-resource-server` (or an equivalent JWT-validation dependency, your call, but it must validate signatures against `AUTH_SECRET`, not just decode blindly) to the backend. Build a JWT validation filter (`OncePerRequestFilter`) that checks the `Authorization: Bearer <token>` header on every request.
- [x] 11.3 Add a `user_id` (string, matching Auth.js's user id) column to the `channel` table via a proper migration — NOT a manual schema edit outside your migration tool. Existing rows created during single-user testing can be backfilled with your own user id or wiped, your choice, just don't leave orphaned rows with a null `user_id` silently ignored.
- [x] 11.4 Update every channel REST endpoint (`work.md` Phase 2.3) to be scoped to the authenticated user: `GET /api/channels` returns only the caller's channels, `POST` sets `user_id` from the validated JWT (never trust a client-supplied `user_id`), `PUT`/`DELETE` must verify the target channel belongs to the caller before acting (403 if not, don't just 404 — decide and be consistent, but don't leak existence of other users' channels either way).
- [x] 11.5 Propagate the same scoping to `digest_run`/`news_item` read endpoints (`work.md` Phase 8.1) — a user must only ever see their own digest history.
- [x] 11.6 Update the frontend's API calls to attach the JWT to every request to the backend (e.g. via a fetch wrapper/interceptor), and handle a 401 response by redirecting to sign-in.
- [x] 11.7 Confirm the Phase 5 dynamic scheduler still works correctly with per-user channels — nothing about the trigger logic itself needs to change (it was already per-channel), just confirm multiple different users' channels all keep firing independently and correctly.


**Checkpoint:** Sign in as two different email addresses (yourself + a friend/test address), create a channel under each, confirm each account only ever sees its own channels/history via both the API directly (should 403/empty-list on cross-user access attempts) and the UI, and confirm both users' schedules still fire independently.

---

## Phase 12 — Onboarding & Multi-User Polish

**Goal:** Make the multi-user experience actually usable by someone who isn't you — first-run experience, per-user email delivery, sane defaults.

- [x] 12.1 Change email delivery (`work.md` Phase 6) from the single hardcoded `DIGEST_RECIPIENT_EMAIL` env var to using each user's own Auth.js account email as the delivery address for their channels — this was flagged as a possible future need back in Phase 6.3, this is where it actually happens. Keep `DIGEST_RECIPIENT_EMAIL` as a fallback/override field if you want, but the default must be the signed-in user's own email.
- [x] 12.2 Add a simple first-run empty state for a brand-new user with zero channels — a clear "create your first channel" prompt, not a blank confusing page.
- [x] 12.3 Add a basic account/settings area showing the signed-in email and a sign-out control, reachable from the main nav.
- [x] 12.4 Sanity-check and, if needed, fix Groq's shared 1,000-req/day budget awareness (`work.md` Phase 9.3) now that usage is genuinely shared across multiple real users, not just you — the daily-counter warning should reflect total usage across all users' channel runs combined.
- [x] 12.5 Invite one real friend to actually sign up and create a channel, end to end, with zero help from you — this is your real usability test, not a simulated second account.

**Checkpoint:** A friend, unassisted, can land on the site, sign in with their own email via magic link, create a channel, and receive their own digest at their own email address — with zero manual setup or intervention from you.

---

## Phase 13 — UI/UX Enhancement

**Goal:** Beat currently works. This phase makes it actually pleasant to use and worth showing off — this is the second-to-last phase, done once all functionality (including auth) is proven solid.

- [x] 13.1 Establish a clear visual identity: pick a simple, deliberate color palette and typography (not default Tailwind gray-on-white) — reference `work.md`'s frontend-design principles if your coding agent has access to them, otherwise keep it clean, high-contrast, and consistent across every page.
- [x] 13.2 Polish the channel list/dashboard view: better visual hierarchy for active vs inactive channels, clearer "next run" time display (human-readable, e.g. "Today at 8:00 AM IST", not a raw timestamp), status indicators (last run success/failed) that are actually scannable at a glance.
- [x] 13.3 Polish the digest reading view (`work.md` Phase 8.2) — this is the core "payoff" screen, treat it like a real reading product: comfortable line length, clear source attribution, good spacing between articles, obvious clickable targets.
- [x] 13.4 Polish the channel create/edit form — better timezone picker UX (searchable, not a giant raw dropdown), clearer article-count input, inline validation feedback instead of only on-submit errors.
- [x] 13.5 Add loading/empty/error states everywhere they're currently missing or bare (e.g. a channel with zero runs yet, a failed run, a slow "run now" trigger) — every state a real user could land on should look intentional, not broken.
- [x] 13.6 Basic responsive pass — confirm the dashboard, forms, and reading view are usable on a phone-width screen, since friends will likely check this on mobile at some point.
- [x] 13.7 Add simple micro-copy throughout (button labels, empty states, confirmation dialogs) — replace anything that still reads like a dev placeholder ("Submit", "Item deleted") with clear, human copy.

**Checkpoint:** Walk through the entire app fresh — sign-in, empty state, create a channel, view a digest, edit settings, sign out — and confirm every screen looks and reads like a finished product, not a working prototype.

---
## Phase 14 — Final Deployment (No-Card, GitHub Actions-Based)

**Goal:** Take the feature-complete, audited, multi-user, polished app live for real — without an always-on VM and without entering any card details anywhere. This replaces the original Oracle VM-based plan from `work.md` Phase 1 (deferred) entirely.

**Why this shape:** Every major always-on VM provider (Oracle, GCP, AWS, Azure) requires a card for identity verification, even on their free tiers. Render's free tier has no free cron at all. Cloudflare Workers can't run a JVM and caps free CPU time at 10ms per invocation — too short for a real TinyFish→Groq pipeline. GitHub Actions is the one path that needs no card, runs a real JVM, and has no CPU-time ceiling for the work itself.

**Known trade-off, not a concern for this project:** GitHub's `schedule:` trigger is best-effort — runs can be delayed under load, and GitHub enforces a 5-minute minimum interval between scheduled triggers. Phase 5's original design (a live `TaskScheduler` firing at each channel's exact time) is replaced with a **poll pattern**: one GitHub Actions workflow runs every 5 minutes, asks the backend "which channels are due right now," and runs the pipeline for each one that is. Digests may land a few minutes after their configured time. Since this is a personal/fun project with no strict delivery-time requirement, this is a non-issue — documented here only so it's understood, not treated as a bug to fix.

- [x] 14.1 Convert the Phase 5 scheduling logic: replace the live `ThreadPoolTaskScheduler`/dynamic `Trigger` design with a single stateless endpoint, e.g. `POST /api/internal/run-due-channels`, protected by a shared secret header (not JWT — this is service-to-service, called by GitHub Actions, not a logged-in user). This endpoint queries all `is_active=true` channels, checks each against its `cron_time`+`timezone` to see if it's due within the last 5-minute window, and runs the Phase 3-4 research pipeline for each due channel synchronously or fire-and-forget (your call, but log clearly either way).
- [x] 14.2 Add a `last_run_at` timestamp to the `channel` table (migration, not manual edit) so the due-check can avoid re-firing the same channel twice if a poll overlaps with a delayed previous run — same overlap-protection intent as the original Phase 5.6, adapted to the poll model.
- [x] 14.3 Decide and implement backend hosting: Selected and deployed Spring Boot backend to Render's free web service tier (no credit card required, simpler dashboard). Free instances scale to zero after 15 minutes of inactivity with cold-start on wake, matching the 5-minute GitHub Actions poll model. Live URL: https://beat-fuvv.onrender.com
- [x] 14.4 Write the GitHub Actions workflow (`.github/workflows/run-digests.yml`): `on.schedule.cron: '*/5 * * * *'`, one job that does a simple authenticated `curl`/HTTP call to the `run-due-channels` endpoint from 14.1, passing the shared secret from a GitHub repo secret (never hardcoded in the YAML). (Verified with GitHub Actions logs: cleanly evaluates and triggers correctly).
- [x] 14.5 Add all required secrets to the GitHub repo under Settings → Secrets and variables → Actions: the shared internal secret from 14.1, and confirm the backend host from 14.3 already has its own env vars (`DATABASE_URL`, `TINYFISH_API_KEY`, `GROQ_API_KEY`, `RESEND_API_KEY`, `RESEND_FROM_EMAIL`, `AUTH_SECRET`) set on its own platform, not duplicated into GitHub.
- [ ] 14.6 Deploy the frontend to Vercel (connect the GitHub repo). Set frontend env vars: `NEXT_PUBLIC_API_BASE_URL` (pointing at the 14.3 backend host's public URL), `AUTH_SECRET`, `AUTH_RESEND_KEY`, `DATABASE_URL` (for the Prisma/Auth.js adapter).
- [ ] 14.7 Update CORS on the Spring Boot backend to allow only the real production Vercel domain.
- [ ] 14.8 Full production smoke test: from the live Vercel URL, sign in with a real email, create a channel with a near-future time, wait for the next poll window, confirm the digest email arrives and the reading view works — same full-loop test as `audit.md` Phase C, against production infrastructure.
- [ ] 14.9 Update the project `README.md` with the real production URLs, a short "for friends: how to sign up and use it" section, and a one-line note that digests may arrive a few minutes after their configured time.

**Checkpoint:** Beat is live at a real public URL, a friend can sign up and use it without your involvement, no card was entered anywhere in the stack, and a channel created with a near-future time actually delivers its digest within a poll cycle or two.

---

## Explicitly Out of Scope (do not build unless separately asked)

- Payment/subscription anything
- Mobile app / push notifications
- True vector embeddings for dedup (unchanged from `work.md`)
- Admin dashboard / user management UI beyond the basic account area in Phase 12.3
- Rate-limiting individual users against each other's Groq usage (shared budget awareness only, per 12.4 — per-user quotas are a future concern, not this workflow's scope)
- Any paid tier of any service listed in `work.md` or here