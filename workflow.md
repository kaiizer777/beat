# Beat — News Digest Pipeline Improvement: Execution Workflow

> **Instructions**: Work through each phase sequentially. After completing a phase (code written, tested, and committed), mark its checkbox as done: change `- [ ]` to `- [x]`.

---

## Phase 1 — Robust Date Parsing in `RawArticle`

- [x] **Completed**

**Goal**: Ensure `publishedAt` is always reliably resolved to a `java.time.Instant`, including for relative-date strings like "3 hours ago".

**What to do**:

1. Locate the class responsible for parsing `publishedAt` from the TinyFish/Jina response (likely a mapper or deserializer around `RawArticle`).
2. Replace the existing `parseInstantOrNull` (or equivalent) with a robust date parser that handles:
   - ISO-8601 strings (`2026-08-10T14:00:00Z`)
   - Human-relative strings (`"X hours ago"`, `"yesterday"`) — use a small NLP utility (e.g., Apache Natty, `date-fns`, or a custom regex → `Instant.now().minus(X, unit)` mapper)
   - Varied locale formats (`Aug 10, 2026`, `10/08/2026`)
3. If parsing fails entirely, set `publishedAt` to `null` — **never** silently default to `Instant.EPOCH` or `now()`.
4. Add a unit test covering all three format categories plus the null-fallback case.

---

## Phase 2 — Freshness Filter in `DigestPipelineService`

- [x] **Completed**

**Goal**: Discard stale articles before they reach the LLM, keeping the context window clean and temporally relevant.

**What to do**:

1. Open `DigestPipelineService.java`, locate the point where `rawCandidateList` is assembled (before it is passed to `ArticleDeduplicationService`).
2. Add a configurable threshold property — e.g., `digest.freshness.max-age-hours=168` (7 days) in `application.properties`, injected via `@Value`.
3. Implement the filter:
   ```java
   Instant cutoff = Instant.now().minus(maxAgeHours, ChronoUnit.HOURS);
   rawCandidateList = rawCandidateList.stream()
       .filter(a -> a.getPublishedAt() != null && a.getPublishedAt().isAfter(cutoff))
       .collect(Collectors.toList());
   ```
4. Log how many articles were dropped at `DEBUG` level.
5. Verify with an integration test: feed a mix of fresh and stale articles, assert only fresh ones pass.

---

## Phase 3 — Pre-Deduplication Sort by Date

- [ ] **Completed**

**Goal**: When deduplication collapses near-duplicate stories into one, ensure the **newest** version of the story survives.

**What to do**:

1. Immediately after the freshness filter (Phase 2) and **before** calling `ArticleDeduplicationService.deduplicate()`, sort `rawCandidateList`:
   ```java
   rawCandidateList.sort(Comparator.comparing(
       RawArticle::getPublishedAt,
       Comparator.nullsLast(Comparator.reverseOrder())
   ));
   ```
2. Confirm `ArticleDeduplicationService.deduplicate()` preserves list order (picks the first occurrence when collapsing duplicates). If it doesn't, adjust deduplication logic to do so.
3. No new test needed if Phase 2's integration test is extended to assert the ordering invariant.

---

## Phase 4 — Temporal Anchoring in LLM Prompts (`LlmDigestService`)

- [ ] **Completed**

**Goal**: Ground every LLM call in the current date so the model cannot confuse "new in May" with "new today".

**What to do**:

1. Update the method signatures of `clusterAndRank` and `synthesizeBlurbs` (and any other Groq-calling methods) to accept an `Instant currentInstant` parameter.
2. Format it for injection: `String currentDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(currentInstant.atZone(ZoneOffset.UTC));`
3. Prepend the following line to every **system prompt** sent to Groq:
   ```
   Current Date: {currentDate}. Evaluate all claims of recency (e.g., "new", "just announced", "latest") strictly relative to this date.
   ```
4. In `DigestPipelineService`, pass `Instant.now()` at the start of the pipeline run and thread it through to every `LlmDigestService` call. Capture it once — do not call `Instant.now()` multiple times across a single run.
5. Update the `clusterAndRank` prompt body to include: *"Rank the top unique articles by relevance and RECENCY. Penalize or discard articles that appear outdated relative to the Current Date."*

---

## Phase 5 — Fact-Checking / Verification Stage (`verifyAndRefine`)

- [ ] **Completed**

**Goal**: Eliminate hallucinated model versions, fabricated numbers, and blurbs that contradict their source text before anything is persisted.

**What to do**:

1. Add a new method `verifyAndRefine(RawArticle article, String generatedBlurb, Instant currentInstant): Optional<String>` to `LlmDigestService`.
2. Build the verification prompt:
   - **System**: `"You are a strict fact-checker. Current Date: {currentDate}."`
   - **User**: Pass `article.getFullText()` and `generatedBlurb`. Instruct the model to check:
     1. No numbers, model versions, or named entities appear in the blurb that are absent from the source text.
     2. Timeline claims are consistent with the Current Date.
   - **Required output**: JSON — `{ "isValid": boolean, "refinedBlurb": "corrected text or null", "rejectionReason": "..." }`
3. Parse the JSON response. If `isValid` is `false` and `refinedBlurb` is null → return `Optional.empty()` (article is dropped). If `refinedBlurb` is provided → return it as the corrected blurb.
4. In `DigestPipelineService`, call `verifyAndRefine` for each article **after** `synthesizeBlurbs`. Drop any article where the result is `Optional.empty()`. Log rejections at `WARN` level with the `rejectionReason`.
5. Add an error budget: if more than 50% of articles are rejected in a single run, log an `ERROR` and surface it in the `digest_run.error_message` field — signals a systemic prompt or data problem.

---

## Phase 6 — Anti-Hallucination Prompt Guardrails

- [ ] **Completed**

**Goal**: Harden the `synthesizeBlurbs` prompt itself as a first line of defense, reducing the verification stage's rejection rate.

**What to do**:

1. Open the `synthesizeBlurbs` prompt string in `LlmDigestService`.
2. Add the following block verbatim to the prompt (in the system or instruction section):
   ```
   CRITICAL RULES:
   - Do NOT extrapolate, infer, or invent model versions, software names, release dates, or statistics.
   - Every claim in the blurb MUST be directly derivable from the provided source text.
   - The blurb must be 100% derivative of the provided text. No external knowledge.
   ```
3. Review all other Groq prompt strings in the service for similar risks and apply the same constraint language where appropriate.
4. After deploying Phase 5, monitor the `rejectionReason` logs over 2–3 pipeline runs to confirm the rejection rate drops.

---

## Phase 7 — Integration Smoke Test & Cleanup

- [ ] **Completed**

**Goal**: Confirm the full pipeline works end-to-end with all phases active before merging.

**What to do**:

1. Trigger a manual pipeline run locally against a real channel (or a stub channel with pre-loaded fixture articles including stale, fresh, duplicate, and hallucination-prone inputs).
2. Verify in logs:
   - Stale articles are dropped in Phase 2 (count logged at DEBUG).
   - Sorted order is maintained (newest first, visible in debug output).
   - LLM prompts contain the `Current Date:` anchor line.
   - At least one verification call completes and returns valid JSON.
3. Check the `news_item` table: only verified, fresh articles should be persisted.
4. Check `digest_run.error_message`: should be null for a clean run.
5. Remove any debug scaffolding, TODOs, or temporary logging added during development.
6. Open a PR. Reference the phases in the PR description.
