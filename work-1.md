Before `DigestPipelineService` passes candidates to `LlmDigestService.clusterAndRank`, implement a hard filter.
*   **Action (Parsing):** Replace `parseInstantOrNull` with a robust date parser (using NLP or a small utility library) to handle relative dates ("X hours ago") and varied formats.
*   **Action (Filtering):** Parse `candidate.getPublishedAt()`. Discard any article older than a configurable threshold (e.g., 7 days for weekly channels, 24 hours for daily channels).
*   **Action (Deduplication):** Sort the `rawCandidateList` by parsed date (newest first) *before* passing it to `ArticleDeduplicationService.deduplicate()`.
*   **Benefit:** Keeps the newest version of a story, removes stale news from the LLM's context window, and prevents temporal confusion.

### Step 2.2: Inject Temporal Anchors into LLM Prompts
Update `LlmDigestService` methods to accept the current date as a parameter and inject it prominently into the system prompts.
*   **Action:** Prepend the system prompt with: `Current Date: {Formatted Current Date}. Evaluate all claims of recency (e.g., "new", "just announced") relative to this date.`
*   **Benefit:** Forces the LLM to ground its summaries in the present reality, preventing it from calling a May 2026 release "new" in August 2026.

### Step 2.3: Introduce a Strict Fact-Checking Stage (The Critic)
Add a new LLM stage after `synthesizeBlurbs`, or modify the synthesis prompt to use Chain-of-Thought (CoT) verification. The most robust approach is a dedicated verification pass: `LlmDigestService.verifyAndRefine()`.
*   **Action:** Pass the original `fullText` and the generated `summaryBlurb` to the LLM with a strict Fact-Checker prompt.
*   **Prompt Constraints:** 
    1. "Verify that no numbers, model versions (e.g., '4.8'), or entities exist in the blurb that are missing from the source text."
    2. "Verify that the timeline makes sense relative to the Current Date."
    3. Output must be a JSON object: `{ "isValid": boolean, "refinedBlurb": "corrected text or null", "rejectionReason": "..." }`
*   **Action in Pipeline:** Drop any `RawArticle` that fails this verification step before persisting to `NewsItemRepository`.

### Step 2.4: Strengthen Prompt Guardrails
Update the `clusterAndRank` prompt to explicitly value recency:
*   *"Rank the top unique articles by relevance and RECENCY. Penalize or discard articles that appear outdated relative to the Current Date."*

Update the `synthesizeBlurbs` prompt to include strict anti-hallucination language:
*   *"CRITICAL: Do not extrapolate model versions, software names, or dates. The blurb must be 100% derivative of the provided text."*

## Summary of Implementation Path
1. **Modify `RawArticle` / Parsing:** Ensure `publishedAt` is reliably parsed to `Instant`.
2. **Update `DigestPipelineService.java`:** Add the freshness filter before Stage 2. Add the verification loop after Stage 3.
3. **Update `LlmDigestService.java`:** Modify method signatures to accept `Instant.now()`. Implement the new `verifyAndRefine` method. Update existing Groq prompts.
