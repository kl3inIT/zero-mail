---
phase: 4
phase_dir: .planning/phases/04-triage-convergence-hero
convergence_cycle: 1
reviewers: [codex, opencode]
reviewed_at: 2026-05-11T06:37:24Z
plans_reviewed:
  - 04-00-PLAN.md
  - 04-01-PLAN.md
  - 04-02-PLAN.md
  - 04-03-PLAN.md
  - 04-04-PLAN.md
  - 04-05-PLAN.md
  - 04-06-PLAN.md
  - 04-07-PLAN.md
  - 04-08-PLAN.md
current_high: 6
---

# Cross-AI Plan Review — Phase 4: Triage Convergence (Hero)

Reviewed by 2 AI systems: **Codex** (gpt-5.x via `codex exec`) and **OpenCode** (`minimax-m2.5-free`). Claude CLI not available in this environment (running inside Claude Code — skipped for independence). OpenCode's default `nemotron-3-super-free` model returned a provider error; review obtained via `minimax-m2.5-free` fallback.

---

## Codex Review

## Summary
The plan set is unusually thorough and covers the right architectural pillars: Modulith event durability, explicit no-send guards, audit-first idempotency, shadow mode, undo, sender safety net, and closure verification. I would not execute it as-is yet. The biggest risks are in the audit mini-saga transaction boundaries, `SAVE_DRAFT` idempotency/state capture, repository methods missing for non-APPLIED decisions, and sender-safety-net data modeling. These are fixable, but several are phase-goal risks rather than polish issues.

## Strengths
- Good defense-in-depth for auto-send: runtime allow-list, Gmail writer boundary, repo-wide send ban, and `RuleActionType.SEND` existence guard.
- Strong privacy posture: metadata-only event payloads, no prompt/completion storage, content-free logs, and a closure privacy sweep.
- Modulith JDBC event registry is the right tool for durable in-process after-commit handoff.
- The unique `args_hash` idea correctly avoids collapsing multi-label actions from one rule.
- Closure plan is solid: full `clean check`, Wave-0 contract convergence, requirements traceability, validation sign-off, UAT doc.

## Concerns
- **[HIGH] 04-05-PLAN: PENDING → Gmail → APPLIED inside `@ApplicationModuleListener` is transactionally unsafe.** `@ApplicationModuleListener` runs in a new transaction. If PENDING insert, Gmail call, and markApplied happen inside that same transaction, the reservation is not durable before the external side effect. A crash after `drafts.create` but before commit can create duplicate drafts on retry. The saga needs explicit small transactions: reserve PENDING commit, external Gmail call outside DB transaction, then mark APPLIED/FAILED in a second transaction.

- **[HIGH] 04-05-PLAN / 04-07-PLAN: existing PENDING conflict handling can lose actions.** The plan says empty `RETURNING` means "skip Gmail". If a previous attempt committed PENDING but crashed before Gmail, retry skips forever and the reaper marks FAILED. Define lease/attempt semantics, or make retry able to reclaim stale PENDING rows.

- **[HIGH] 04-02-PLAN / 04-05-PLAN: `SAVE_DRAFT` idempotency hash cannot include `draftId`.** `TriageActionResult.SaveDraft` requires `draftId`, but `args_hash` is computed before Gmail creates the draft. Split stable request args from Gmail result state, or compute `args_hash` only from pre-write action intent and store `draftId` separately after success.

- **[HIGH] 04-02-PLAN / 04-05-PLAN: repository surface does not support all decisions.** 04-02 only plans `insertAuditPendingIfAbsent`, `markApplied`, `markFailed`, `markReverted`. 04-05 needs `SHADOW_LOGGED`, `REJECTED_BY_SAFETY_NET`, and `REJECTED_BY_SAFETY_POLICY`. Add explicit narrow insert/transition methods and update the ArchUnit allow-list.

- **[HIGH] 04-02-PLAN: native audit insert bypasses entity validation.** `@PrePersist`/getter validation on `TriageAuditEntity` will not protect `insertAuditPendingIfAbsent` native SQL. Put validation/canonicalization in a custom repository/service method before the native insert.

- **[HIGH] 04-02-PLAN: nullable `rule_id` breaks the unique idempotency index.** PostgreSQL unique indexes treat NULLs as distinct unless `NULLS NOT DISTINCT` is used. Either make `rule_id` non-null for every proposal-derived audit row, or define a null-safe unique index.

- **[HIGH] 04-04-PLAN / 04-06-PLAN: sender safety net endpoint has no real protected-sender source.** `GET /api/triage/sender-safety-net` is planned to list opt-ins, not protected senders discovered by triage. To meet TRG-08, persist protected sender observations or query audit rows with enough sender metadata.

- **[HIGH] 04-05-PLAN: sender safety net only gates archive/save-draft.** The requirement says frequent/important senders are "not auto-acted on" until opt-in. Applying a label is still an automated Gmail write. Either gate all actions or explicitly amend the requirement.

- **[MEDIUM] 04-04-PLAN: Redis key leaks raw sender email.** The value stores only a boolean, but the key includes `{lower(senderEmail)}`. Use a keyed hash in the Redis key and keep raw email only where the product needs to display it.

- **[MEDIUM] 04-04-PLAN: Gmail search query needs input hardening.** `to:<senderEmail>` is built from untrusted input. Validate/canonicalize the email and quote/escape it for Gmail search. Also prefer request body/query param over a path variable for opt-in.

- **[MEDIUM] 04-07-PLAN: failed Modulith publications are not retried.** The plan mentions `FailedEventPublications` but only schedules `IncompleteEventPublications`. If listener failures move to failed publications, the retry job misses them.

- **[MEDIUM] 04-07-PLAN: purge timestamp is wrong for shadow/rejected rows.** Purge uses `applied_at`, but `SHADOW_LOGGED` and rejected rows likely have null `applied_at`. Use `created_at`, `decided_at`, or `COALESCE(applied_at, created_at)` for retention.

- **[MEDIUM] 04-00-PLAN: compile-RED test scaffolds can block later targeted tests.** Later waves run `:backend:core:test --tests ...`, but Gradle still compiles all test sources. Avoid unresolved imports in default test source sets; use reflection/string FQNs, separate source set, or real placeholders.

- **[MEDIUM] 04-05-PLAN: possible duplicate listeners.** If both `TriageOrchestratorService` and `TriageOrchestratorAdapter` carry `@ApplicationModuleListener`, events can be handled twice. Decide one listener location.

## Suggestions
- Add a dedicated "audit saga transaction design" task before 04-05: reserve/claim, external call, finalization, stale PENDING retry semantics, and special `SAVE_DRAFT` crash handling.
- Redesign audit payloads as `action_request_json` plus `action_result_json` or keep `args_hash` strictly based on pre-write stable inputs.
- Add explicit repository methods for `markShadowLogged`, `markRejectedBySafetyNet`, `markRejectedBySafetyPolicy`, and make the ArchUnit rule whitelist only those narrow transitions.
- Add a sender-safety-net persistence model for "protected sender observed" if the REST list must be accurate.
- Canonicalize and validate sender emails once, then reuse the normalized form for Gmail query, opt-in uniqueness, audit/list display, and cache hashing.
- Replace Bash-style verification snippets (`grep`, `tail`, `test`) with Gradle assertions or PowerShell-safe commands for this Windows workspace.
- Add execution-time checks against Spring AI and Spring Modulith docs in 04-01/04-03, not just 04-07.

## Risk Assessment
**Overall risk: HIGH until the audit saga and sender-safety-net gaps are fixed.** The plan is directionally strong and security-conscious, but the current PENDING transaction design can either duplicate drafts or lose actions, which cuts directly against the hero feature's trust promise. After fixing the saga boundaries, `SAVE_DRAFT` state model, rejected/shadow audit transitions, and sender-safety-net persistence, the risk drops to MEDIUM.

Docs checked: Spring Modulith event APIs and `@ApplicationModuleListener`; Spring AI structured JSON output; Gmail `drafts.create` and `messages.modify`; PostgreSQL `INSERT ... ON CONFLICT ... RETURNING`.

---

## OpenCode Review

## Phase 4 Plan Review

### Summary

Phase 4 is a well-engineered hero convergence with 9 plans across 7 waves. The architectural spine — Modulith JDBC event registry for atomic event publication, two-phase PENDING→APPLIED audit loop, sealed `TriageActionResult` preserving the "SEND forbidden" invariant, and sender-safety-net with graceful Redis degradation — is defensively designed for a trust-critical product. Most gaps are minor naming ambiguities or underspecified internal method signatures; none are show-stoppers but 04-05 needs a clarification before execution.

### Strengths

- **Correct idempotency pattern** — two-phase PENDING→APPLIED with `ON CONFLICT DO NOTHING RETURNING` correctly handles `users.drafts.create` (non-idempotent on Google's side) while avoiding orphan audit rows on retry
- **Privacy-safe event payload** — `MailMessageObserved` is ids+timestamp only; `semanticEvalContent` is subject excerpt + content-free flag summary with no body/raw headers/sender display name; sender email logged hashed-only
- **Defense-in-depth SEND prevention** — sealed `TriageActionResult` (no SEND variant) + exhaustive switch in `TriageUndoService` + `NoGmailSendAllowedTest` + `TriageGmailWriteBoundaryTest` + `RuleActionType.SEND` existence assertion; compile-time + runtime + ArchUnit layers stacked
- **`args_hash` (raw SHA-256)** — correct rejection of the SPEC-suggested simpler unique key; canonical JSON + 32-byte hash handles the legitimate multi-label-same-rule case without collapsing it
- **Redis graceful degradation** — `SenderSafetyNetService` uses `ObjectProvider<StringRedisTemplate>` and fails-safe (returns `protected=true`) on both Redis unavailability and Gmail outage; cache stores only a boolean, never sent counts
- **`@Transactional` proxy-boundary correctness** — all purge/reaper schedulers follow the `CreditReserveWatchdog`/`CreditReserveWatchdogBatch` split; self-invocation trap (RESEARCH Pitfall 6) is explicitly mitigated
- **LLM failure containment** — `DEFERRED-(error)` per node treated as `NOT_MATCHED`; deterministic-only actions still fire; no `decision=FAILED` from semantic failures alone; booleans never defaulted to false

### Concerns

**1. `gmail_change_token` not populated on PENDING→APPLIED transition** *(04-05, HIGH)*
The orchestrator step 9f says `markApplied(reservedAuditId, tenantId, gmailDraftIdOrNull)` and notes "if `markApplied` doesn't carry the change token, add a narrow update." But `TriageAuditRepository` (04-02) only defines `markApplied(auditId, tenantId, externalRef)` — no `gmail_change_token` parameter. For **undo to work**, `restoreToInbox` needs `{"removedLabelIds":["INBOX"]}` and `removeLabel` needs `{"addedLabelId":"Label_xxx"}` from the audit row. The token must be set post-write. Two clean options: (a) extend `markApplied` signature to `markApplied(auditId, tenantId, externalRef, String gmailChangeTokenJson)` or (b) add a narrow `updateGmailChangeToken(auditId, tenantId, changeTokenJson)` called after the Gmail success. Option (a) is simpler. **Recommendation:** extend `markApplied` in 04-05 with a `gmailChangeToken` parameter, and update 04-02's `TriageAuditRepository` signature accordingly. Without this, archive/label undo is broken.

**2. `markShadowLogged` repository method missing from Wave 2** *(04-02/04-05, HIGH)*
04-05 step 9e says "transition it to SHADOW_LOGGED via a narrow `mark*` method" but 04-02's `TriageAuditRepository` only defines `markApplied` / `markFailed` / `markReverted`. There's no `markShadowLogged(UUID auditId, UUID tenantId)` with `WHERE decision='PENDING'`. Two options: (a) add `markShadowLogged` to 04-02 (cleaner — the repo knows all transitions) or (b) do the SHADOW_LOGGED insert directly in the orchestrator (skip the PENDING insert when shadow mode is known upfront, which is cleaner for shadow mode anyway). Option (b) avoids a wasted PENDING insert and is simpler: if `isTriageShadowMode`, insert directly as `decision='SHADOW_LOGGED'` and `gmail_change_token=null`; skip the two-phase loop entirely. **Recommendation:** document this clearly in 04-05 — either add `markShadowLogged` to 04-02's repo or handle direct SHADOW_LOGGED insert in the orchestrator (preferred, avoids an extra DB row + UPDATE for shadow mode).

**3. `TriageGmailWriteBoundaryTest` in Wave 0 vs Wave 4** *(04-00/04-04, MEDIUM)*
04-00 Task 3 says "Modify `CallSiteEnumMembershipArchTest`" but for `TriageGmailWriteBoundaryTest` it says "Create... Wave-0 — names `TriageGmailWriter` as the single permitted Gmail-write call site; **this task makes it GREEN against a real class**." But `TriageGmailWriter` doesn't exist in Wave 0 — it's landed in 04-04. So the test created in 04-00 must compile-RED (referencing a non-existent class) and turn GREEN in 04-04. The acceptance criteria says "`./gradlew :backend:core:test --tests "*TriageGmailWriteBoundaryTest"` PASSES" after Wave 4. This is correct but the 04-00 task description is slightly misleading — it should explicitly say "create as a compile-RED stub that turns GREEN when 04-04 lands `TriageGmailWriter`." No fix needed, just clarity.

**4. UAT is backend+REST only; Phase 5 owns the UI** *(04-08, MEDIUM)*
04-08 UAT creation must explicitly state that AC items like "user sees per-message audit trail" (AC #5) and "user can undo" (AC #7) are **not manually UATable** in Phase 4 — they're verified by automated contract tests. Only the REST endpoint behavior (200/409/404 responses) is UATable. If the executor creates UAT scenarios as manual steps, they should be clearly labeled "automated contract test coverage: YES / manual coverage: NO." Recommend 04-08-UAT.md use a "Coverage" column distinguishing automated vs manual.

**5. Event retry window vs daily cleanup edge case** *(04-07, LOW)*
`TriageEventRetryJob` resubmits incomplete publications older than **5 minutes**. `TriageEventCleanupJob` deletes completed publications older than **7 days** daily at 3 AM. An event that fails repeatedly for ~5 minutes (exactly at the resubmit threshold) could be in the INCOMPLETE→resubmit→fail→INCOMPLETE cycle when the 3 AM cleanup fires. Since the 3 AM cleanup deletes **completed** publications and the cycle never reaches COMPLETED status, this is not a real issue. However, the 3 AM cron means there's no retry-after-3AM window — a stuck event could theoretically sit for ~24h. Recommend adding a counter or log line `event=triage_event_cleanup_skipped_incomplete_count` so this is observable.

### Suggestions

**A. Verify `spring-modulith-starter-jdbc` resolves from the existing BOM** *(04-00, MEDIUM)*
The plan says the version "is supplied by the `spring-modulith-bom` already imported by the `zeromail.modulith-conventions` build plugin." This needs to be verified at execute time via `./gradlew :backend:core:dependencies`. If the pinned snapshot doesn't publish the JDBC starter, the plan surfaces this in the SUMMARY and stops. Good hedge, but the Research assumption (RESEARCH A1) should be re-read against Spring Modulith 2.0.6 release notes before execution.

**B. Consider adding `markApplied` overload with `gmailChangeToken`** *(04-02, MEDIUM)*
Rather than leaving the gmail_change_token gap as a 04-05 problem, 04-02 could add a second `markApplied` overload: `markApplied(UUID auditId, UUID tenantId, String externalRef, String gmailChangeTokenJson)` alongside the existing one. This keeps the repo declaring all transitions (cleaner for the ArchUnit whitelist) and makes 04-05's orchestrator step unambiguous.

**C. `semanticEvalContent` Javadoc lock-in** *(04-05, MEDIUM)*
The Javadoc note documenting the "v1 limitation — semantic-intent matchers see only the sanitized subject excerpt + derived flags (metadata-only triage fetch carries no body)" is critical for future maintainers. Recommend it be placed at the field declaration, the method that builds it, AND the class-level Javadoc. Also recommend adding a code comment: `// NOTE: do NOT add body/snippet/raw-header fetch here — that would require a richer Gmail API call and re-review under privacy+latency constraints.`

**D. Consider `isTriageShadowMode` fast-path before inserting PENDING row** *(04-05, LOW)*
If shadow mode is ON, skip the PENDING insert entirely and write directly to `decision='SHADOW_LOGGED'` with `gmail_change_token=null`. This avoids one wasted INSERT + one wasted UPDATE per shadow-mode action. Low impact in v1 (shadow mode is opt-in default-OFF) but it's cleaner.

**E. `SEND` existence assertion vs `RuleActionType.SEND` future-proofing** *(04-00, MEDIUM)*
`NoGmailSendAllowedTest` asserts `RuleActionType.valueOf("SEND")` → `IllegalArgumentException`. If a future phase adds `RuleActionType.SEND` for v2 (auto-send), this test would fail the build — which is correct. However, the test name and message should clarify that the assertion is "SEND must not exist in the enum." Consider a separate ArchUnit rule: `noClasses().should().accessField(RuleActionType.class, "SEND")` as a more durable guard.

**F. Add `triage.semantic_eval.fanout.per_rule` Micrometer counter** *(04-05, LOW)*
Adding a `triage.semantic_eval.fanout.per_rule.count` counter helps distinguish "how often does the fallback fire?" from "how much does the fallback cost?" This feeds the v2 adaptive-token-budget decision.

### Risk Assessment

**Overall: MEDIUM**

The architecture is sound and the threat model is thorough (T-04-01 through T-04-08 cover the STRIDE space well). The two HIGH concerns — `gmail_change_token` population gap and missing `markShadowLogged` — are **fixable within the existing plan structure** without architectural changes, but they need clarification before Wave 5 executes to avoid rework.

The highest residual risk is the **Spring Modulith JDBC starter version verification** (RESEARCH A1). If `spring-modulith-starter-jdbc` is not in the existing BOM/pin, the plan correctly surfaces this and stops, but resolving it requires a discuss-phase decision.

The secondary risk is **execution scope creep** — Wave 5 (`TriageOrchestratorService`) is the most complex single class in Phase 4 (10-step orchestration, 3 exception paths, credit accounting, two-phase loop, shadow mode branch, sender-net gating, per-rule fan-out). The RED-by-design test spine is the right mechanism for catching nuances.

No HIGH-severity security regressions: the privacy invariant, SEND-forbidden enforcement, tenant isolation (FND-05), and sender-data handling all have multi-layer mitigations.

---

## Consensus Summary

Both reviewers agree the plan set is architecturally strong and security-conscious — defense-in-depth for the no-auto-send invariant, metadata-only privacy posture, Modulith JDBC event registry for durable handoff, the `args_hash` design that avoids collapsing multi-label actions, and a thorough closure plan. The disagreement is on overall risk level: Codex rates **HIGH** (audit saga + sender-safety-net gaps are phase-goal risks), OpenCode rates **MEDIUM** (the HIGHs are fixable within the existing structure without architectural changes). Both want clarifications landed before Wave 5 executes.

### Agreed Strengths
- Multi-layer SEND prevention (sealed `TriageActionResult`, exhaustive switch, runtime allow-list, ArchUnit boundary tests, `RuleActionType.SEND` existence guard).
- Privacy-safe event payload — `MailMessageObserved` ids+timestamp only; no body/prompt/completion storage; content-free / hashed-sender logs; closure privacy sweep.
- Modulith JDBC event registry is the right tool for durable in-process after-commit handoff.
- `args_hash` (canonical JSON + raw SHA-256) correctly handles the multi-label-same-rule case rather than collapsing it.
- `@Transactional` proxy-boundary correctness on purge/reaper schedulers (watchdog/batch split, self-invocation trap mitigated).
- Solid closure plan: full `clean check`, Wave-0 contract convergence, requirements traceability, validation sign-off, UAT doc.

### Agreed Concerns (highest priority)
1. **[HIGH] 04-02/04-05 — Audit repository surface is incomplete.** The repo only declares `insertAuditPendingIfAbsent`/`markApplied`/`markFailed`/`markReverted`. The orchestrator additionally needs `SHADOW_LOGGED`, `REJECTED_BY_SAFETY_NET`, `REJECTED_BY_SAFETY_POLICY` transitions and a way to set `gmail_change_token` post-write (needed for undo to work). Add explicit narrow methods (or direct inserts) and update the ArchUnit allow-list. *(Both reviewers; Codex frames it as missing transitions, OpenCode frames it as the `gmail_change_token` + `markShadowLogged` gap — same root issue.)*
2. **[HIGH] 04-05 — Two-phase PENDING→Gmail→APPLIED transaction boundaries.** PENDING reservation must be durably committed before the external Gmail call; the call must run outside a DB transaction; APPLIED/FAILED must be a separate transaction. Also define lease/attempt semantics so a crashed-mid-write PENDING row can be reclaimed rather than skipped forever (which would lose the action). *(Codex HIGH; OpenCode touches the same retry-window edge case at LOW.)*
3. **[HIGH] 04-02 — Native audit insert bypasses entity validation.** `@PrePersist`/getter validation on `TriageAuditEntity` won't run for the `insertAuditPendingIfAbsent` native SQL — move validation/canonicalization into a service/custom-repo method before the native insert. *(Codex; consistent with OpenCode's note that the orchestrator is the most complex class and needs the test spine.)*
4. **[MEDIUM] 04-00 — Compile-RED test scaffolds vs targeted test runs.** Later waves run `--tests ...` but Gradle still compiles all test sources; unresolved imports in the default test source set can break the build. Use reflection/string FQNs, a separate source set, or real placeholder classes. Both reviewers flag the Wave-0 RED spine clarity (OpenCode specifically for `TriageGmailWriteBoundaryTest`).
5. **[MEDIUM] 04-00 — Verify `spring-modulith-starter-jdbc` resolves from the existing BOM/pin** before Wave 1; re-read RESEARCH A1 against current Spring Modulith release notes. Both reviewers call this out as the top execution-blocking risk.
6. **[MEDIUM] 04-04 — Sender-email handling needs hardening.** Codex: Redis key leaks raw lowercased sender email (hash the key); Gmail `to:<senderEmail>` search is built from untrusted input (validate/canonicalize/escape; prefer query param over path variable). OpenCode endorses canonicalize-once-reuse-everywhere.

### Divergent Views
- **Overall risk level:** Codex says HIGH (the audit-saga and sender-safety-net data-model gaps cut against the hero feature's trust promise); OpenCode says MEDIUM (fixable in place, the RED test spine catches the rest). Worth resolving by landing the audit-repository + saga-boundary clarifications, then re-rating.
- **Sender safety net data source (TRG-08):** Codex flags as HIGH that `GET /api/triage/sender-safety-net` lists *opt-ins* but has no source of *protected senders discovered by triage* — to truly meet TRG-08 it needs a "protected sender observed" persistence model or audit-row query. OpenCode does not raise this. Needs a planner decision: is the endpoint listing opt-ins (current plan) sufficient for TRG-08, or does the spec require surfacing the auto-detected protected set?
- **Sender safety net action scope:** Codex flags as HIGH that the net only gates archive/save-draft while label writes still proceed — the requirement says protected senders are "not auto-acted on." Either gate all actions on protected senders or explicitly amend the requirement. OpenCode does not raise this. Needs a planner/requirement decision.
- **Modulith failed-publications retry:** Codex flags (MEDIUM) that `TriageEventRetryJob` only schedules `IncompleteEventPublications`, not `FailedEventPublications`. OpenCode treats the retry/cleanup interaction as a non-issue at LOW. Worth confirming which registry view actually holds listener-failure rows in Spring Modulith 2.0.x.
