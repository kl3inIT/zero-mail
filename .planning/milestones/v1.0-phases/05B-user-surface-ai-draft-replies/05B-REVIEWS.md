---
phase: 5B
reviewers: [codex, opencode]
reviewed_at: 2026-05-12T20:15:39Z
plans_reviewed: [05B-00-PLAN.md, 05B-01-PLAN.md, 05B-02-PLAN.md, 05B-03-PLAN.md, 05B-04-PLAN.md, 05B-05-PLAN.md, 05B-06-PLAN.md, 05B-07-PLAN.md]
---

# Cross-AI Plan Review — Phase 5B

## Codex Review

## Summary

Overall plan quality is strong, but I would not treat this plan set as execution-ready yet. The main blockers are call-path ownership, module dependency cycles, bucket semantics, and parallelization assumptions. The plans are thorough on privacy, no-auto-send, Gmail threading, UI states, and eval coverage, but several details conflict with the actual code shape and with each other.

Risk assessment: HIGH until the blocking concerns below are resolved.

## Cross-Plan Strengths

- Correctly prioritizes Gmail threading headers. Google’s current Gmail docs still require `threadId`, RFC-compliant `References` / `In-Reply-To`, and matching subject for drafts/messages to join threads.
- Strong privacy posture: metadata-only projections, no draft body in API responses, no prompt/completion persistence, log-scrub tests, and eval fixtures as synthetic-only.
- Good safety posture: no `drafts.send`, no `drafts.update`, `save_draft` tool remains `{ body }`, and Spring AI usage stays adapter-confined.
- UI scope is disciplined: review/edit/send stays in Gmail, not in-app.
- Evaluation plan is unusually strong for a product phase: deterministic safety, threading, token-budget, privacy, and classifier checks.

## Blocking Concerns

| Severity | Concern |
|---|---|
| HIGH | Plan 01 assumes `TriageOrchestratorService` directly calls `TriageGmailWriter.saveDraft`, but current code routes draft writes through `TriageAuditSaga.gmailWritePhase` at [TriageAuditSaga.java](<D:/study-materials-summer-2026/EXE202/zero-mail/backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageAuditSaga.java:119>). The plan must include `TriageAuditSaga`, `TriageAuditCommand`, and triage input/header propagation. |
| HIGH | Current triage input does not carry `Message-ID`, `References`, or `Reply-To`; `GmailPreviewReadService` metadata headers omit them. Plan 01 needs to extend `GmailPreviewMessage` / `TriageRuleEvaluationInput`, not only the writer. |
| HIGH | Plan 03 does not clearly make automatic triage `save_draft` use the new tone-matched LLM generation path. The phase goal says automatic drafts are primary; leaving triage drafts as “instruction body” misses DRFT-03 for the main path. |
| HIGH | `LlmGateway` taking `ToneContext` from `core.draft` would create a likely `llm -> draft -> llm` module cycle. Keep the gateway interface independent of `core.draft`. |
| HIGH | Plan 04 runs parallel with Plan 03 but assumes `core/triage/package-info.java` and `core/thread/package-info.java` already include `shared.pagination`. That is not safe in parallel. Make 04 depend on 03, or let 04 own those edges. |
| HIGH | Bucket semantics are inconsistent: some text says “Zero-Mail draft means awaiting,” while plans classify drafts as `TO_REPLY` with `Draft ready`. The latter is more product-correct, but the spec/acceptance text must be reconciled before tests are written. |
| HIGH | Awaiting-reply depends on outbound/sent observation, but the current event surface appears to have only `MailMessageObserved`. Add explicit SENT watch/outbound event scope, or downgrade awaiting accuracy claims. |
| HIGH | Public bucket naming is inconsistent: `to-reply`, `to_reply`, and enum `TO_REPLY` appear. This will break API/UI integration unless a public slug converter is specified. |

## Plan-by-Plan Review

| Plan | Strengths | Concerns | Suggestions | Risk |
|---|---|---|---|---|
| 05B-00 | Good foundation: dependency, metadata-only schema, early RED tests, ArchUnit intent. | RED scaffolds may intentionally fail `compileTestJava`, which can block all later verification. Schema lacks indexes for the actual inbox keyset ordering. | Prefer reflection/disabled tests so test sources compile. Add `(tenant_id, bucket, resolved, last_classified_at desc, gmail_thread_id desc)` style index. | MEDIUM |
| 05B-01 | Correct Gmail threading direction; `jakarta.mail` over string MIME; fail-closed validator. | Misses actual save-draft call path through `TriageAuditSaga`; misses existing metadata gaps. | Thread `ReplyHeaders` through triage input and `TriageAuditCommand`; update `GmailPreviewReadService` headers; use strict address parsing. | HIGH |
| 05B-02 | Solid metadata-only projection and idempotency idea. | Semantics conflict on drafts vs awaiting; outbound/draft events not owned; tenant-scoped repo methods are too implicit. | Reconcile bucket contract; add event classes/publishers and SENT observation plan; use explicit tenant predicates. | HIGH |
| 05B-03 | Strong privacy, lock, LLM gateway-only, no tool schema widening. | Does not clearly satisfy automatic triage draft generation; likely module cycle via `ToneContext`; threadId-only command lacks reliable reply target; transaction/external-call boundaries are fuzzy. | Decouple gateway inputs, wire triage `save_draft` to generation, store/use last inbound message id and headers, avoid DB tx across Gmail/LLM calls. | HIGH |
| 05B-04 | Good CQRS-lite read side; keyset instead of offset; metadata-only rows. | Parallel dependency issue with Plan 03; cursor uses epoch millis, risking timestamp precision bugs; counts/resolve rely on implicit tenant context. | Serialize after 03 or move package edges; encode full `Instant`; explicit tenant SQL/repository methods. | MEDIUM-HIGH |
| 05B-05 | Thin REST shape, good error mapping, no draft body returned. | Bucket slug mismatch; sidebar count needs a cheap endpoint; live Gmail display fetch can become expensive or flaky. | Define API slugs (`to-reply`, `awaiting-their-reply`) and converters; add counts endpoint or `includeItems=false`; batch Gmail metadata with timeout/degraded fields. | MEDIUM |
| 05B-06 | UI states, responsive behavior, no in-app draft body/send/edit are well covered. | Sidebar badge may trigger full inbox + Gmail fetch on every route; e2e may be unrealistic without mocks/auth harness. | Use a lightweight count hook; rely on MSW/API fixtures for component/e2e tests; require confirm for regenerate. | MEDIUM |
| 05B-07 | Strong closure discipline: evals, privacy sweep, UAT, requirements flip. | “Classifier eval gates” but also “or document gap” is contradictory; closure plan is very large; AI-SPEC still references `DRAFT_REPLY` in places. | Split eval harness from closure; only mark complete if deterministic gates pass or explicitly relax acceptance; update AI-SPEC to match `CallSite.DRAFT`. | MEDIUM-HIGH |

## Key Suggestions

1. Fix the draft call path first: `TriageAuditSaga` owns Gmail writes today, so Plan 01/03 must route headers and generated bodies through that saga.
2. Make Plan 03 the actual automatic draft-generation integration, not just the manual endpoint.
3. Resolve `TO_REPLY` vs `AWAITING_THEIR_REPLY` semantics before writing Wave 0 tests.
4. Remove the Plan 03/04 parallel hazard by adding `05B-03` as a dependency of `05B-04`.
5. Define stable public bucket slugs and keep enum IDs internal.
6. Keep `core.llm` independent of `core.draft`; pass neutral gateway inputs.
7. Add explicit outbound/SENT observation work or narrow awaiting-reply claims.
8. Do not allow classifier accuracy to “fail but close”; either gate it or mark the requirement partially complete.

Sources checked: Google Gmail threading docs, Gmail drafts docs, and Spring AI 2.0.0-M6 Context7 docs for ChatClient/tool-calling behavior.

---

## OpenCode Review

# Cross-AI Plan Review: Phase 5B — User Surface AI Draft Replies

## Overall Phase Assessment

**8 plans | 4 waves + closure | ~70+ files modified | well-scoped to DRFT-01..04**

The phase is well-structured with clear dependency ordering, thorough threat models, and good security/privacy discipline. The Modulith boundary coordination between Plans 03 and 04 is carefully (if awkwardly) handled. Major risk: Gmail API quota consumption from repeated sent-mail + thread-metadata fetches under real usage.

---

## Plan-by-Plan Review

### 05B-00 — Wave 0: Dependency + Schema + RED Tests

**Summary**: BLOCKING foundation — jakarta.mail dependency, `thread_reply_status` Liquibase changelog, ~14 RED test scaffolds.

**Strengths**:
- Clean separation of infrastructure from production code
- Threat model T-05B-00-01 correctly flags activation-jar skew risk
- RED tests as executable acceptance contracts is a strong TDD pattern
- Schema is metadata-only by construction with proper CHECK constraints and FK cascade

**Concerns**:
- **HIGH**: 14 RED test scaffolds in parallel files means the "RED" state may manifest as either compilation errors (literal class references) or reflection-based skips (FQN style). The plan acknowledges both approaches but doesn't mandate one. If mixed inconsistently across the 14 files, Waves 1-5 need to handle both patterns — increasing cognitive load. Prefer FQN/reflection style across all 14 to keep `compileTestJava` green during the RED phase.
- **MEDIUM**: Version mismatch between `jakarta.mail-api:2.1.3` and `angus-mail:2.0.4` — the Angus 2.0.x line implements the Jakarta Mail 2.1.x spec, but the major version numbers are confusing. Need to verify with `gradle dependencyInsight` (which the plan does) and also check Angus Mail's exact compat matrix — some 2.0.x releases only target Jakarta Mail 2.1.0-M1.
- **LOW**: `030-thread-reply-status.yaml` uses both `deleteCascade: true` on the FK and the plan later adds an explicit repository delete — duplicate cleanup. The plan says "or rely on cascade and document the choice" — pick one and be consistent across the project. FK cascade is sufficient; the explicit delete is an unnecessary second mechanism.

**Suggestion**: Standardize on FQN/reflection style for all 14 RED tests to keep `compileTestJava` passable during the RED phase. This prevents the Wave-0 state from blocking unrelated parallel work.

**Risk**: LOW — standard infrastructure setup, rolled back cleanly if wrong.

---

### 05B-01 — Threading Headers Retrofit

**Summary**: Widens `TriageGmailWriter.saveDraft()` to build RFC-2822-compliant MIME via jakarta.mail, adds `ReplyHeaders`, `ThreadingHeaderValidator`, fail-closed on missing `Message-ID`.

**Strengths**:
- `prefixReIfAbsent` correctly uses `regionMatches(true, …)` for case-insensitive prefix detection — avoids double `Re:` without breaking `RE:` vs `re: `
- `buildReferences` correctly handles the "no prior References" edge case
- Threat model covers encoded-word/CRLF/folding via `MimeMessage` library (T-05B-01-06) — using the library instead of hand-rolling headers is correct
- Validator fails closed on missing `Message-ID` — never produces a mis-threaded draft

**Concerns**:
- **LOW**: Non-English reply prefixes (German `AW:`, French `RE:`, Swedish `SV:`, Spanish `RV:`, etc.) are not handled. Gmail UI normalizes these, but the raw RFC subject may carry localized prefixes. A thread whose subject is already `AW: Meeting notes` would become `Re: AW: Meeting notes` — visually awkward. Mitigation: either strip common known prefixes or use a heuristic — `subject matches ^[A-Za-z]{1,4}:\s` is simple. Not a blocker for v1, but should be documented as a known cosmetic issue.
- **MEDIUM (quality)**: The `ReplyHeaders.of(...)` compact ctor requires `Objects.requireNonNull` on `gmailThreadId` but allows `inboundMessageId` to be null (the caller decides). This means the validator is the only barrier to producing a draft with no `In-Reply-To`. If the caller bypasses the validator somehow, the draft would be mis-threaded. Consider making `inboundMessageId` non-optional and always requiring the caller to decide "fail closed or produce without threading" explicitly.

**Suggestion**: Add a test for the exact Angular Mail/Angus Activation version compatibility before committing the dependency change. Document the non-English reply-prefix cosmetic issue in a TODO.

**Risk**: LOW — well-scoped, clear security invariants, good edge-case handling.

---

### 05B-02 — Thread Reply-Status Package

**Summary**: Creates `core.thread` package with `ThreadReplyBucket` IdentifiedEnum, JPA entity, heuristic-only `ClassifyThreadReplyStatusService`, Modulith reaction, tenant cleanup.

**Strengths**:
- Correctly scoped to NOT touch `TriageOrchestratorService.java` or add the `triage → thread` Modulith edge — Plan 03 owns that. Good dependency coordination.
- Heuristic-only v1 with documented LLM-hybrid fallback is pragmatic — no prompt-injection surface, zero LLM cost
- Idempotency key `(tenantId, gmailThreadId, lastClassifiedMessageId)` prevents re-upsert churn
- Auto-reply detection (`Auto-Submitted`, `Precedence: bulk`) correctly keeps vacation responders in `TO_REPLY`

**Concerns**:
- **MEDIUM**: Heuristic accuracy ≥85% on TO_REPLY/AWAITING split is untested against real-world patterns. Edge cases that will fail:
  - User replied via a different Gmail client (mobile app) — SENT label may not have synced yet
  - Thread with multiple participants — "the last message" may be from another non-tenant participant
  - Group threads where multiple parties are expected to reply
  - Bounced messages looking like sent mail (DSN/bounce messages have `From: <>` or MAILER-DAEMON)
  - User drafted but hasn't sent yet — `hasDraft=true` → `TO_REPLY` which is correct, but the heuristic produces no differentiator for "draft already written vs draft just started"
  - The plan acknowledges this (T-05B-02-03, fixture evaluation in Plan 07) but the mitigation is "document the gap" — if the heuristic is significantly below 85% on the held-out set, the feature ships with a known-weak inbox.
- **LOW**: `lastMessageIsAutoReply` detection is boolean-only, but auto-reply detection is hard to get right. Gmail vacation responses carry `Auto-Submitted: auto-replied` in most cases, but not all email systems use this header. Some out-of-office messages go through a user's SENT folder (Gmail's "vacation responder" does), so `lastMessageFromIsTenant && threadHasSentLabel` would already be true for them. The auto-reply check is critical for correctness but has false-negative risk.

**Suggestion**: Add a "heuristic blind spots" section to the fixture README documenting the known failure modes. Consider adding a `messages.list(q="in:sent")` call in the heuristic for ONLY the case where `lastMessageFromIsTenant && threadHasSentLabel` (to fetch the sent message's headers and check for auto-reply patterns) — but weigh against the no-mailbox-enumeration invariant. Current approach (boolean from caller metadata) is acceptable for v1.

**Risk**: MEDIUM — classifier quality is a product-trust risk, not a security/safety risk. The phase correctly flags it.

---

### 05B-03 — Draft Generation Service

**Summary**: Core of the phase — `ToneContextBuilder`, `RedisDistributedLock`, `GenerateThreadDraftService`, `LlmGateway` draft seam, triage orchestration sub-step, Modulith boundary coordination.

**Strengths**:
- Delete-then-recreate regeneration (D-15) is the correct approach — guarantees ≤1 Zero-Mail draft per thread
- Redis lock with token-compare release prevents double-click races
- Tone context is prompt-only (not tool schema) per D-08 — `save_draft` tool stays `{ body: string }`
- `SAVE_DRAFT_ONLY` profile isolates the narrow tool surface
- Degrade-to-descriptors on `TokenBudgetExceededException` prevents hard failures from noisy sent mail
- The `shared.pagination` edge addition to both parent `package-info.java` files is a necessary (if awkward) coordination with Plan 04
- `CallSite.DRAFT` is reused (not a new `DRAFT_REPLY`) — avoids billing complexity

**Concerns**:
- **HIGH (data loss, not safety)**: Regenerate does delete-then-recreate. If `deleteDraft` succeeds but `saveDraft` fails (Gmail API transient error, quota exceeded, rate limit), the user's existing draft is destroyed with no replacement. The plan mentions "one automatic retry on a transient transport error only" — but after that, the old draft is unrecoverable. **Mitigation**: Either (a) save the new draft FIRST, then delete the old one (reorder the operations), or (b) snapshot the old draft body in a short-lived cache before deleting with a restore-on-failure mechanism. Option (a) is simpler and should be the default: `saveDraft` new → if success → `deleteDraft` old → if delete fails, log but don't fail (the old draft id is stale, but Gmail still has it).
- **MEDIUM**: The `ToneContextBuilder` fetches sent mail in-request every time a draft is generated. For power users who regenerate drafts frequently on the same thread, this is repeated Gmail API calls for the same ~5-8 messages. The plan explicitly defers caching (deferred "Cached LLM style summary per tenant" in deferred section), which is fine for v1, but the repeated API calls are real QUOTA cost. A simple in-memory cache keyed by `tenantId` with a 5-minute TTL for the descriptor portion alone (not snippets) would reduce this to near-zero cost.
- **MEDIUM**: The `ToneContextBuilder` batch-fetches sent mail with a `Duration` fetch budget. If some fetches succeed and some fail mid-batch, the behavior isn't fully specified: return whatever snippets were fetched? Drop all and use descriptors-only? The plan says "degrade to descriptors-only on TokenBudgetExceededException" but doesn't address partial fetch failure. Define: on any Gmail API failure during tone-context building, degrade to descriptors-only — never block the draft.
- **LOW**: The `chatForDraft(...)` method adds a new interface method on `LlmGateway`. The plan correctly keeps Spring AI types in the adapter. But the interface should be designed so non-draft callers can't accidentally use it — maybe a package-private or restricted visibility if Java module system or ArchUnit enforces it.

**Suggestion**: Reorder regenerate to `saveDraft` first, then `deleteDraft` old. Add a 5-minute in-memory cache for tone descriptors (not snippets) keyed by `tenantId`. Define explicit fallback on partial tone fetch failure: drop tone context entirely, proceed with an instructions-only prompt.

**Risk**: MEDIUM-HIGH (data loss during regenerate is the highest-risk item in this phase) — mitigated by the reorder suggestion.

---

### 05B-04 — Read-Side Queries

**Summary**: `KeysetCursor` codec, `AuditLogQueryService`, `NeedsReplyInboxQueryService` (NULLS-LAST keyset), `MarkThreadResolvedService`.

**Strengths**:
- Correct NULLS-LAST keyset design with two-region cursor logic (sentinel for null tail, compound predicate for mixed region)
- `AuditLogQueryService` uses `(created_at desc, audit_id desc)` — appropriate for an append-only audit table
- No `OFFSET`/`COUNT(*)` for paging — correct per project convention
- Correctly does NOT edit parent `package-info.java` files (delegates to Plan 03)
- `NeedsReplyRow` is metadata-only — no subject/participant/body columns

**Concerns**:
- **MEDIUM**: The `KeysetCursor.nullsLast()` sentinel uses `Instant.EPOCH` as a magic value. If real data ever has `last_classified_at = Instant.EPOCH` (only possible in synthetic tests or buggy initial inserts), the sentinel would collide. Use a different representation: encode a sentinel string (e.g., `__NULLS_LAST__`) in the cursor and check for it in `decode`, rather than using a valid `Instant` value. The cursor is opaque anyway.
- **MEDIUM**: The `NeedsReplyInboxQueryService` keyset predicate for the non-null region includes `(last_classified_at is null)` as an OR branch. This means every page query in the non-null region also scans the null tail. On a table where most rows have null `last_classified_at` (e.g., during initial backfill), this forces a sequential scan of the entire null region on every page. **Mitigation**: The partial index covers TO_REPLY + NOT resolved, so the query is already bounded by bucket. But the `last_classified_at IS NULL` branch still forces a filter. Consider a composite index on `(bucket, resolved, last_classified_at desc nulls last, gmail_thread_id desc)` for the hot path.
- **LOW**: The `AuditLogQueryService` uses `(created_at, audit_id) < (?, ?)`. If `triage_audit` has many rows with the same `created_at` timestamp (millisecond precision — unlikely under real load but possible in tests), the ordering tie-breaker by `audit_id` may not be monotonic between pages because UUIDs are not ordered by insertion. Consider `ORDER BY created_at DESC, audit_id DESC` — the `audit_id` is a UUID. UUIDv7 (time-ordered) would be ideal for keyset pagination. If the project uses standard UUIDv4, this is fine for correctness but may produce surprising page splits under high concurrency. Not a v1 blocker.

**Suggestion**: Change sentinel representation from `Instant.EPOCH` to a distinct string prefix in the base64-encoded cursor. Consider adding a composite index `(bucket, resolved, last_classified_at DESC NULLS LAST, gmail_thread_id DESC)` for the needs-reply query.

**Risk**: LOW — correct design, minor index-tuning concern.

---

### 05B-05 — REST Endpoints

**Summary**: `GET /api/triage/audit`, `POST /api/threads/{id}/draft`, `POST .../resolve`, `GET /api/threads?bucket=...`, error mapping, OpenAPI regen.

**Strengths**:
- Two disjoint `/api/threads` controllers with clear method-path separation — works correctly in Spring MVC
- No draft body in any response — privacy-compliant by API contract
- `InvalidCursorException` wraps `KeysetCursor.decode` failures without broadening the generic `IllegalArgumentException` handler — correct error isolation
- Vi/en i18n copy for new error codes
- `DraftGenerationInFlightException → 409` is the correct HTTP semantic for resource-level contention

**Concerns**:
- **HIGH**: `NeedsReplyInboxController` fires one `threads.get(format=METADATA)` per row for live display fields. At `limit=50`, this is 50 Gmail API calls per page load. Gmail API quota is ~1M queries/day (free tier) or ~10M/day (paid). For a user checking the inbox 5x/day, this is 250 calls/day just for inbox loads — plus draft-generation calls (another 5-8 per draft). For 500 active users, that's ~125K calls/day — 12.5% of the free quota. **This is manageable but must be monitored.** The plan mentions "BatchRequest pattern + Duration fetch budget" — confirm that `BatchRequest` is used (reduces to 1 HTTP call with ~50 sub-requests, still counts as 50 quota units).
- **MEDIUM**: `SafetyViolationException` mapped to 500. Returning 500 means the client can't distinguish "LLM did something unsafe" from "server is having a bad day." A 422 (Unprocessable Entity) or 400 (Bad Request) would be more semantically correct since the issue is with the model's response, not the server infrastructure. A 500 with `LLM_SAFETY_VIOLATION` code means any client-side retry logic would re-attempt a request guaranteed to fail again. Consider 422 with the same error code — still no retry, but semantically accurate.
- **LOW**: The `ThreadDraftController` and `NeedsReplyInboxController` both use `@Tag(name="thread")` for springdoc. This groups all thread endpoints under one OpenAPI tag — acceptable but loses the distinction between write operations (draft/resolve) and read operations (inbox). Consider two tags: `thread-draft` and `thread-inbox`.

**Suggestion**: Add Gmail API quota monitoring to this phase (or at least file a follow-up issue). Change `SafetyViolationException` to 422. Add a note to the SUMMARY about the potential per-row Gmail fetch quota.

**Risk**: MEDIUM — Gmail API quota is the primary concern; per-row fetches are within budget for v1 but need monitoring.

---

### 05B-06 — Frontend

**Summary**: `features/needs-reply/` (API/keys/hooks/components), `/needs-reply` route, sidebar nav item, draft action on audit rows, 5A GAP sentinel removal, all states covered, Playwright e2e.

**Strengths**:
- Raw shadcn primitives only — no custom wrapper components (follows project convention)
- All UI states explicitly specified: loading (Skeleton), empty (TO_REPLY "Inbox zero" / AWAITING "Nothing awaiting"), error (destructive Alert + retry), 320px responsive, classifying banner
- Draft action on BOTH needs-reply rows AND audit rows — consistent UX
- No draft body fetched or rendered anywhere
- `sonner` toast for success/failure, inline amber notice for 409
- `Regenerate draft` gated behind `alert-dialog` confirm per UI-SPEC §Destructive

**Concerns**:
- **MEDIUM**: The 5A GAP sentinel removal means `useTriageAuditLog` switches from returning `{ unavailable: true }` to calling a real API. If the `GET /api/triage/audit` endpoint has any downtime/deployment skew, the `/triage` page shows an error state that didn't exist before. The error state is handled (destructive Alert + retry), but this is a regression in UX if the backend isn't fully deployed yet. **Mitigation**: ensure the `GET /api/triage/audit` endpoint is deployed and tested (Plan 05) before the frontend change ships (Plan 06). The dependency ordering (`05B-05 → 05B-06`) already enforces this. Just make sure deploy order follows the same sequence.
- **LOW**: The `GenerateDraftButton` shows "Generating…"/"Regenerating…" during loading but the plan says to "re-enable after a moment" on 409. The UX of a disabled-then-auto-reenabled button is slightly jarring. Consider showing the inline notice immediately on 409 without re-enabling, and let the user re-click explicitly. Or keep both states so the user isn't stuck.
- **LOW**: Vietnamese is the default rendering language. All new i18n keys must be added lock-step to both `en.json` and `vi.json`. The plan explicitly requires this. Make sure the vi translations are reviewed by a Vietnamese speaker — auto-translated UI copy for safety-related error messages is a risk.

**Suggestion**: In `useGenerateDraft`, on 409, show the inline notice AND keep the button disabled for a brief cooldown (3-5s) before re-enabling. This prevents rapid re-click loops.

**Risk**: LOW-MEDIUM — standard UI plan with good state coverage. Deploy-order dependency is the main concern.

---

### 05B-07 — Closure

**Summary**: `aiEval` source set + deterministic evals (dims 4/6/7/8), `DraftPrivacySweepTest`, full gates, VALIDATION.md + UAT.md, REQUIREMENTS/ROADMAP flip.

**Strengths**:
- Deterministic-only mode for CI gating with LLM-judge dims report-only — pragmatic
- `DraftPrivacySweepTest` covers the cross-cutting content-leakage invariant
- Correct approach: measure classifier accuracy, don't lower the bar, document the gap
- Fixture READMEs mandate synthetic/anonymized-only content
- 20-30 labeled classifier fixtures with reference to inbox-zero's test cases is well-grounded

**Concerns**:
- **MEDIUM**: Creating 20-30 high-quality labeled classifier fixtures is non-trivial. If the planner shortcuts this (e.g., "all plainly distinguishable cases"), the evals lose diagnostic value and the 85% bar becomes meaningless. The fixtures need to include the edge cases documented in Plan 02's concerns (multi-participant threads, auto-reply variants, SENT-label-lag scenarios, group threads, DSN/bounce). This is labor but essential for the eval to be meaningful.
- **LOW**: The `DraftPrivacySweepTest` should also assert that the `ToneContextBuilder` doesn't accidentally leak sent-mail bodies via an uncaught exception's message or cause. The plan says "assert zero email body bytes in any captured log line" — also assert in the exception path.
- **LOW**: The plan says `aiEval` is NOT part of `check` but has its own CI task. Must ensure CI runs it.

**Suggestion**: Add fixture quality review to the acceptance criteria — "at least 5 of the 20-30 classifier fixtures test a non-trivial edge case (multi-participant, auto-reply, group thread, SENT-lag)". This prevents a degenerate fixture set.

**Risk**: LOW — standard closure plan. Fixture quality is the main execution risk.

---

## Cross-Cutting Concerns

| Concern | Severity | Scope | Mitigation |
|---------|----------|-------|------------|
| Gmail API quota consumption from repeated per-request sent-mail fetches + per-row thread metadata fetches | HIGH | 05B-03, 05B-05, 05B-06 | Batch requests, Duration fetch budget, 1-5 min in-memory descriptor cache (suggestion), quota monitoring |
| Delete-then-recreate data loss during regenerate failure | HIGH | 05B-03 | Reorder to save-then-delete (suggestion) |
| Heuristic classifier accuracy unknown against real-world patterns | MEDIUM | 05B-02, 05B-07 | Held-out fixture evaluation, documented gap, LLM-hybrid deferred follow-up |
| Mixed RED test convention (literal-ref vs FQN-ref) across 14 files | MEDIUM | 05B-00 | Standardize on FQN/reflection for all RED tests |
| Modulith boundary coordination (Plan 03 adds `shared.pagination` edge for Plan 04's benefit) | LOW | 05B-03, 05B-04 | Works correctly but is architecturally awkward; document in SUMMARY |
| Non-English reply prefixes causing cosmetic `Re:` doubling | LOW | 05B-01 | Document as known cosmetic issue |
| `SafetyViolationException` → 500 vs 422 | LOW | 05B-05 | Change to 422 per suggestion |
| NULLS-LAST sentinel using Instant.EPOCH (collision risk) | LOW | 05B-04 | Use distinct sentinel encoding |

---

## Risk Assessment Summary

| Plan | Risk | Justification |
|------|------|---------------|
| 05B-00 | LOW | Standard infrastructure; rollback-safe |
| 05B-01 | LOW | Clear scope, well-specified invariants |
| 05B-02 | MEDIUM | Heuristic classifier quality is hard to predict; accuracy bar may not be met |
| 05B-03 | MEDIUM-HIGH | Delete-then-recreate data loss is the highest single-risk item; Gmail API quota concern |
| 05B-04 | LOW | Correct design; index tuning is optional |
| 05B-05 | MEDIUM | Per-row Gmail fetches under real load; API quota monitoring needed |
| 05B-06 | LOW-MEDIUM | Deploy-order dependency on Plan 05; standard UI plan |
| 05B-07 | LOW | Fixture quality is main execution risk |
| **Phase Total** | **MEDIUM** | No showstoppers. Highest risks: draft-regenerate data loss (mitigate by reordering) and Gmail API quota (monitor post-launch). |

---

## Does This Phase Achieve Its Goals?

**Yes — DRFT-01..04 are fully covered:**

- **DRFT-01** (request draft): `POST /api/threads/{id}/draft` + UI action ✅
- **DRFT-02** (correct threading headers): MimeMessage with `In-Reply-To`/`References`/`Re:`/`To` + `ThreadingHeaderValidator` fails closed ✅
- **DRFT-03** (tone matching): In-request sent-mail fetch + quote/signature strip + SanitizationPipeline + descriptors + ≤3 snippets in prompt ✅
- **DRFT-04** (never auto-send): No `drafts.send`/`drafts.update` in codebase (ArchUnit-gated); no auto-send endpoint exists; regenerate = delete-then-recreate; UI has no Send control ✅

**Gaps (all correctly scoped):**
- Classifier accuracy: heuristic-only v1, documented gap, LLM-hybrid follow-up deferred
- Gmail API quota monitoring: not in plan scope — file a follow-up issue
- Non-English reply prefix cosmetic issue: documented, not fixable without i18n table

**Strongest aspects**: Threat model rigor, privacy compliance, Modulith boundary discipline, security invariants (ArchUnit + eval + privacy sweep).

**Weakest aspect**: Gmail API quota planning — the per-request sent-mail fetch in ToneContextBuilder plus per-row thread metadata in the inbox controller is the most likely operational surprise in production.

---

## Consensus Summary

Both reviewers rate the plan set as well-structured with strong privacy/safety discipline (metadata-only projections, no auto-send, ArchUnit + eval + privacy sweep, Gmail threading via `jakarta.mail`). They diverge on overall risk: Codex says **HIGH — not execution-ready** due to call-path/module/semantics mismatches with the actual codebase; OpenCode says **MEDIUM — no showstoppers** but flags two HIGH operational risks (regenerate data loss, Gmail API quota).

### Agreed Strengths
- Correct Gmail threading approach (`threadId` + RFC `In-Reply-To`/`References` + `Re:` + `MimeMessage`, fail-closed validator).
- Strong privacy posture: metadata-only read models, no draft body in API responses, no prompt/completion persistence, log-scrub + privacy-sweep tests, synthetic-only eval fixtures.
- Strong safety posture: no `drafts.send`/`drafts.update`, `save_draft` tool stays `{ body }`, `SAVE_DRAFT_ONLY` profile, Spring AI confined to adapter, `CallSite.DRAFT` reused (no billing churn).
- Disciplined UI scope (review/edit/send stays in Gmail).
- Unusually strong evaluation/closure plan (deterministic safety/threading/token/privacy/classifier dims).
- Careful (if awkward) Modulith boundary coordination between Plans 03 and 04.

### Agreed Concerns
- **HIGH — Gmail API quota / repeated fetches** (OpenCode HIGH; Codex flags as fuzzy external-call boundary in Plan 03/05): `ToneContextBuilder` fetches sent mail in-request on every generation; `NeedsReplyInboxController` fires one `threads.get(METADATA)` per row. Needs `BatchRequest`, fetch-time budget, possible short-TTL descriptor cache, and quota monitoring.
- **HIGH — RED test convention** (both, severity MEDIUM each): the 14 Wave-0 RED scaffolds may fail `compileTestJava` if literal class refs are used, blocking all later verification. Standardize on FQN/reflection style.
- **HIGH — classifier accuracy "fail but close"** (both): `ClassifyThreadReplyStatusService` heuristic ≥85% bar is untested against real patterns (multi-participant threads, auto-reply variants, SENT-label lag, group threads, DSN/bounce); both object to "measure then document the gap" as the only mitigation — either gate it or mark the requirement partially complete.
- **HIGH — `core.llm` ↔ `core.draft` module cycle risk** (Codex HIGH; OpenCode LOW): `LlmGateway` accepting `ToneContext` from `core.draft` likely creates an `llm → draft → llm` cycle; keep the gateway interface on neutral inputs.
- **MEDIUM — Plan 03/04 parallelization hazard** (Codex HIGH; OpenCode LOW): Plan 04 assumes the `shared.pagination` edge already exists in `core/triage` and `core/thread` `package-info.java`, but Plan 03 adds it in parallel. Make 04 depend on 03, or have 04 own those edges.
- **MEDIUM — keyset cursor precision** (both): cursor encodes epoch millis / uses `Instant.EPOCH` as a NULLS-LAST sentinel — risk of precision loss and sentinel collision; encode full `Instant` and use a distinct sentinel token.
- **LOW — non-English `Re:` prefixes** (OpenCode); **LOW — `SafetyViolationException` → 500 vs 422** (OpenCode); **LOW — implicit tenant scoping in repo methods** (Codex).

### Divergent Views
- **Execution readiness:** Codex — HIGH risk, several plan details conflict with the real code shape (esp. `TriageAuditSaga` owning Gmail writes today, triage input not carrying `Message-ID`/`References`/`Reply-To`, automatic-triage drafts not clearly routed through the new tone-matched path) → not ready. OpenCode — MEDIUM, DRFT-01..04 fully covered, no showstoppers, ship with monitoring.
- **Draft call path:** Codex asserts current code routes draft writes through `TriageAuditSaga.gmailWritePhase`, so Plans 01/03 must include the saga + `TriageAuditCommand` + header propagation; OpenCode does not raise this (reviewed plans as-written without cross-checking the codebase).
- **Bucket semantics:** Codex flags an inconsistency (spec text "Zero-Mail draft ⇒ awaiting" vs plans classifying drafts as `TO_REPLY` / "Draft ready") plus public slug naming drift (`to-reply` / `to_reply` / `TO_REPLY`) that must be reconciled before Wave 0 tests; OpenCode treats the `TO_REPLY` + "Draft ready" behavior as fine.
- **Regenerate failure mode:** OpenCode raises delete-then-recreate as a HIGH data-loss risk (recommends save-then-delete reorder); Codex mentions delete-then-recreate only as a strength (guarantees ≤1 draft) and does not flag the failure window.
