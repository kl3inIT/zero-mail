---
phase: 05B-user-surface-ai-draft-replies
plan: 07
type: execute
wave: 7
depends_on: ["05B-01", "05B-02", "05B-03", "05B-04", "05B-05", "05B-06"]
files_modified:
  - backend/core/build.gradle.kts
  - backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftThreadingEvalTest.java
  - backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftSafetyEvalTest.java
  - backend/core/src/aiEval/java/com/zeromail/core/aiEval/ClassifierAccuracyEvalTest.java
  - backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftTokenBudgetEvalTest.java
  - backend/core/src/aiEval/resources/fixtures/draft/README.md
  - backend/core/src/aiEval/resources/fixtures/classifier/README.md
  - .github/workflows/ci.yml
  - .planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md
  - backend/core/src/test/java/com/zeromail/core/draft/DraftPrivacySweepTest.java
  - .planning/phases/05B-user-surface-ai-draft-replies/05B-VALIDATION.md
  - .planning/phases/05B-user-surface-ai-draft-replies/05B-UAT.md
  - .planning/REQUIREMENTS.md
  - .planning/ROADMAP.md
autonomous: true
requirements: [DRFT-01, DRFT-02, DRFT-03, DRFT-04]
must_haves:
  truths:
    - "Deterministic eval dims run via `:backend:core:aiEval -PdeterministicOnly` (its own CI job, not part of `check`): dims 4 (safety/allow-list), 6 (threading headers), 8 (token budget) pass unconditionally; dim 7 (classifier ≥85% TO_REPLY/AWAITING, ≥5 edge-case fixtures, no skew) GATES the merge and routes DRFT-04's status — it is never disabled and the bar is never lowered"
    - "DraftPrivacySweepTest (FND-03-analogous) proves no email body / sent-mail tone context / prompt / completion / draft body reaches logs, exceptions, or persistence across the draft + classify + list paths — on success AND on forced-failure paths"
    - "05B-AI-SPEC.md uses CallSite.DRAFT everywhere (no DRAFT_REPLY)"
    - "`./gradlew clean check` (backend) is green; `apps/web` tsc/lint/vitest/i18n:check are green"
    - "REQUIREMENTS.md flips DRFT-01..03 to Complete; DRFT-04 to Complete if the dim-7 classifier gate passed, else to Partially complete with an LLM-hybrid follow-up note (the bar is never lowered, the test never disabled); updates the WEB-02 row; ROADMAP.md marks Phase 5B complete"
  artifacts:
    - path: "backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftThreadingEvalTest.java"
      provides: "eval dim 6: parse the built MIME back with jakarta.mail, assert headers against fixture expectations incl. Vietnamese subject / missing Message-ID / already-Re:-prefixed / no prior References / cross-thread-bleed pair"
    - path: "backend/core/src/aiEval/java/com/zeromail/core/aiEval/ClassifierAccuracyEvalTest.java"
      provides: "eval dim 7: heuristic classifier scored on the held-out fixture set; >= ~85% on the TO_REPLY/AWAITING split, no one-direction skew"
    - path: ".planning/phases/05B-user-surface-ai-draft-replies/05B-VALIDATION.md"
      provides: "filled validation strategy + nyquist_compliant/wave_0_complete flags + the eval sign-off"
  key_links:
    - from: "backend/core/build.gradle.kts"
      to: "src/aiEval source set"
      via: "a tagged `aiEval` test source set + `aiEval` Gradle task (deterministicOnly + judgeOnly props)"
      pattern: "aiEval"
    - from: ".planning/REQUIREMENTS.md"
      to: "DRFT-01..04 + WEB-02"
      via: "status flip to Complete / 5B-portion-done"
      pattern: "DRFT-0[1-4]"
---

<objective>
Close Phase 5B: stand up the `:backend:core:aiEval` tagged source set + Gradle task (reuse any existing eval-harness scaffolding from Phase 4) + a CI job invoking it, land the deterministic eval dimensions (dim 4 safety/allow-list, dim 6 threading-header correctness, dim 7 classifier accuracy on the held-out fixture set — GATED, never "measure and document", dim 8 token budget) plus synthetic-fixture seeds (≥15 draft; 20-30 classifier with ≥5 non-trivial edge cases) and `@Tag("judge")` skeletons for the report-only LLM-judge dims, update `05B-AI-SPEC.md` to use `CallSite.DRAFT` (no `DRAFT_REPLY` anywhere), add a `DraftPrivacySweepTest` (FND-03-analogous, asserting no content leak via logs OR exceptions on success AND failure paths), run the full backend `check` + `apps/web` gates + the deterministic eval, fill `05B-VALIDATION.md` + write `05B-UAT.md`, flip `REQUIREMENTS.md` DRFT-01..03 to Complete and DRFT-04 to Complete (dim-7 passed) or Partially complete + LLM-hybrid follow-up (dim-7 failed), update the WEB-02 row, mark Phase 5B complete in `ROADMAP.md`.

Purpose: The phase-gate sign-off. Per AI-SPEC §5: deterministic dims 4/6/8 gate the merge unconditionally; dim 7 (classifier) gates the merge AND routes DRFT-04's status; LLM-judge dims 1/2/3/5 ride along report-only until calibrated ≥ 0.7. There is NO path where the phase closes with the classifier bar quietly unmet and DRFT-04 still marked Complete.
Output: `aiEval` source set + 4 deterministic eval tests + fixture READMEs/seeds, `DraftPrivacySweepTest`, `05B-VALIDATION.md`, `05B-UAT.md`, REQUIREMENTS/ROADMAP edits.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/REQUIREMENTS.md
@CLAUDE.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-RESEARCH.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-VALIDATION.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-SPEC.md
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: aiEval tagged source set + deterministic eval dims 4/6/7/8 + fixture seeds</name>
  <files>backend/core/build.gradle.kts, backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftThreadingEvalTest.java, backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftSafetyEvalTest.java, backend/core/src/aiEval/java/com/zeromail/core/aiEval/ClassifierAccuracyEvalTest.java, backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftTokenBudgetEvalTest.java, backend/core/src/aiEval/resources/fixtures/draft/README.md, backend/core/src/aiEval/resources/fixtures/classifier/README.md</files>
  <read_first>
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md §5 (the dimension rubrics, the eval-tooling/CI section, the reference-dataset composition for the draft suite and the classifier held-out set, the calibration gate)
    - backend/core/build.gradle.kts (existing test source sets — check if a prior phase already created an `aiEval` / `eval-harness` tagged source set or a `semanticIntentEval` task; reuse/extend rather than re-create)
    - .planning/phases/04-triage-convergence-hero/04-08-PLAN.md (or its SUMMARY) — how Phase 4 wired `semanticIntentEval` + the `eval-harness` dir marker; mirror the source-set/task wiring
    - backend/core/src/main/java/com/zeromail/core/draft/usecases/GenerateThreadDraftService.java + ToneContextBuilder.java + the Plan-01 ReplyMimeBuilder/ThreadingHeaderValidator + the Plan-02 ClassifyThreadReplyStatusService (the services the eval tests drive)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-RESEARCH.md §"Validation Architecture" (the test map)
    - inbox-zero's `apps/web/utils/ai/reply/determine-thread-status.test.ts` at `D:\study materials summer 2026\EXE202\inbox-zero` (the classifier fixture composition to mirror)
  </read_first>
  <behavior>
    - `backend/core/build.gradle.kts`: add (or reuse) a tagged `aiEval` source set under `src/aiEval/java` + `src/aiEval/resources`, a `tasks.register("aiEval")` JUnit task honoring `-PdeterministicOnly` (excludes `@Tag("judge")` tests) and `-PjudgeOnly` (only the judge-tagged tests); `aiEval` depends on the test classpath. Do NOT wire `aiEval` into `check` — it's its own task / a separate CI job.
    - `DraftThreadingEvalTest` (dim 6, deterministic, no model calls): for >= 6 fixture inbound messages (a normal thread; one with no prior `References`; one whose subject is already `Re:`-prefixed; one with a non-ASCII / Vietnamese subject; one missing a `Message-ID`; an adversarial pair with overlapping participants) build the reply MIME via the production builder, parse it back with `jakarta.mail`, assert `In-Reply-To` == inbound `Message-ID`, `References` == prior chain + that id, exactly one `Re:` prefix, correct `To`, `threadId` set, base64url-no-padding; missing `Message-ID` -> fails closed; the adversarial pair -> thread A's MIME contains zero content from thread B.
    - `DraftSafetyEvalTest` (dim 4, deterministic): `ActionValidator` accepts exactly `{LABEL, ARCHIVE, SAVE_DRAFT}` and rejects everything else; the `save_draft` tool schema is exactly `{ body: string }`; a stubbed model response with a non-`save_draft` tool call (or no tool call) -> `SafetyViolationException` + zero Gmail writes; an ArchUnit assertion (or a reference to `DraftPathArchUnitTest`) that no `drafts.send`/`drafts.update`/`messages.send` is reachable from `core.draft`/`core.triage`.
    - `ClassifierAccuracyEvalTest` (dim 7, deterministic, GATED): load the held-out classifier fixture set (20-30 labeled threads mirroring inbox-zero's `determine-thread-status.test.ts` cases — see the fixture README; AT LEAST 5 of them MUST exercise a non-trivial edge case: a multi-participant thread, an auto-reply / vacation-responder last message, a SENT-label-lag scenario, a group thread where several parties are expected to reply, a DSN/bounce message that looks like sent mail — a fixture set of "all plainly distinguishable cases" fails this criterion), run `ClassifyThreadReplyStatusService` (heuristic v1) over each, compute accuracy on the TO_REPLY ⇄ AWAITING_THEIR_REPLY split; **assert ≥ 85% AND no one-direction skew — this test GATES `:backend:core:aiEval -PdeterministicOnly`.** FYI/ACTIONED reported, not gated. **If the heuristic misses the bar the test FAILS and STAYS FAILED — do NOT lower the bar, do NOT mark it `@Disabled`.** The phase still closes (the heuristic is the SPEC-flagged assumption and the LLM-hybrid is the planned remediation) BUT then `REQUIREMENTS.md` records DRFT-04's reply-status-classifier portion as **Partially complete** (not Complete) with an explicit "heuristic accuracy {X}% on the held-out set — LLM-hybrid follow-up required" note, and `05B-VALIDATION.md` records the same. There is no "gate it OR document the gap and call it done" path — it is "gate passes → Complete" or "gate fails → Partially complete + follow-up". (Eval CI keeps running the test, so the follow-up's progress is visible.)
    - `DraftTokenBudgetEvalTest` (dim 8, deterministic): a gateway-level assertion that a draft call without an explicit `max_tokens` is refused; an oversized tone-context build raises `TokenBudgetExceededException` and the draft still proceeds with descriptors-only; ANY simulated Gmail-API failure during the tone fetch degrades to empty tone and the draft still proceeds (no hard fail); recorded `promptTokens`/`completionTokens` within bounds on a fixture run (or assert the configured `max_tokens` cap is applied).
    - Fixture READMEs (`fixtures/draft/README.md`, `fixtures/classifier/README.md`): document the composition (the draft suite's critical-path / failure-mode / threading-edge / content-edge / adversarial-pair cases per AI-SPEC §5; the classifier set's labeled cases INCLUDING a "heuristic blind spots" section listing the ≥5 edge-case fixtures and what each probes) and the "synthetic/anonymized only — no real mail" rule; seed at least the minimum count of fixture files (≥ 15 draft, 20-30 classifier with ≥5 edge cases) — synthetic JSON, no real bodies. The LLM-judge dims 1/2/3/5 are left as `@Tag("judge")` skeletons that read the same fixtures and call `LlmGateway` — report-only, not required to pass in this plan.
  </behavior>
  <action>
    Wire/extend the `aiEval` source set + Gradle task in `backend/core/build.gradle.kts` (reuse any existing eval-harness scaffolding). Add a CI step in `.github/workflows/ci.yml` (or the project's actual CI config) that runs `./gradlew :backend:core:aiEval -PdeterministicOnly` as its own job — `aiEval` is NOT part of `check`, so CI must invoke it explicitly or the deterministic dims never gate a PR. Create the four deterministic eval test classes + the two fixture READMEs + the synthetic fixture files (≥15 draft; 20-30 classifier with ≥5 edge cases) + `@Tag("judge")` skeletons for dims 1/2/3/5. Update `05B-AI-SPEC.md` so EVERY reference to a `DRAFT_REPLY` `CallSite` is changed to `CallSite.DRAFT` (the phase reuses the existing enum value — `grep -n "DRAFT_REPLY" .planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md` must return nothing afterward). Run `./gradlew :backend:core:aiEval -PdeterministicOnly` — dims 4/6/8 must pass; dim 7 either passes (→ DRFT-04 Complete in Task 2) or fails (→ DRFT-04 reply-status portion Partially complete + follow-up in Task 2). The bar is never lowered and the test is never disabled.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:aiEval -PdeterministicOnly 2>&1 | tail -15</automated>
  </verify>
  <acceptance_criteria>
    - `:backend:core:aiEval` task exists with `-PdeterministicOnly` / `-PjudgeOnly` modes; `aiEval` is NOT part of `check`; CI invokes `./gradlew :backend:core:aiEval -PdeterministicOnly` as its own job (asserted: the CI config references it)
    - `./gradlew :backend:core:aiEval -PdeterministicOnly`: dim 4 (allow-list/no-auto-send), dim 6 (threading headers incl. Vietnamese/missing-Message-ID/already-Re:/no-prior-References + cross-thread-bleed), and dim 8 (token budget incl. the Gmail-failure-degrades path) PASS; dim 7 (classifier ≥85% TO_REPLY/AWAITING, no skew) PASSES or FAILS (it gates — never disabled, bar never lowered; a fail routes DRFT-04's classifier portion to Partially complete in Task 2)
    - `fixtures/draft/` has ≥15 synthetic fixture files; `fixtures/classifier/` has 20-30 labeled synthetic fixtures of which ≥5 exercise a non-trivial edge case (multi-participant / auto-reply / SENT-lag / group thread / DSN-bounce), enumerated in the README's "heuristic blind spots" section
    - `grep -n "DRAFT_REPLY" .planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md` returns nothing (AI-SPEC updated to `CallSite.DRAFT`)
    - No fixture contains a real email body / address / Google subject (synthetic only)
    - `mcp__jetbrains__get_file_problems` on the new eval Java files clean
  </acceptance_criteria>
  <done>The AI eval suite's deterministic dimensions gate; fixtures seeded; the classifier accuracy bar measured.</done>
</task>

<task type="auto">
  <name>Task 2: DraftPrivacySweepTest + full gates + VALIDATION/UAT + REQUIREMENTS/ROADMAP flip</name>
  <files>backend/core/src/test/java/com/zeromail/core/draft/DraftPrivacySweepTest.java, .planning/phases/05B-user-surface-ai-draft-replies/05B-VALIDATION.md, .planning/phases/05B-user-surface-ai-draft-replies/05B-UAT.md, .planning/REQUIREMENTS.md, .planning/ROADMAP.md</files>
  <read_first>
    - backend/core/src/test/java/.../TriagePrivacySweepTest.java (the Phase 4 FND-03-analogous privacy sweep — mirror it for the draft path) and BillingPrivacyLogScrubTest.java
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-VALIDATION.md (the template to fill — the validation strategy table, the nyquist/wave-0 flags, the eval sign-off section)
    - .planning/phases/04-triage-convergence-hero/04-UAT.md (the UAT format to mirror)
    - .planning/REQUIREMENTS.md (the DRFT-01..04 rows + the WEB-02 row + the Traceability table — flip statuses)
    - .planning/ROADMAP.md (the Phase 5B entry + the Progress table — mark complete)
    - all 05B-*-SUMMARY.md files written by Plans 00-06 (roll up the actual outcomes / deviations into VALIDATION + UAT)
  </read_first>
  <action>
    Create `DraftPrivacySweepTest` (FND-03-analogous): drive a draft generation (including a forced failure path — a stubbed LLM `SafetyViolationException` and a stubbed Gmail-fetch failure) + a reply-status classification + a `GET /api/triage/audit` + a `GET /api/threads` page under synthetic load with log capture; assert zero email body bytes, zero sent-mail tone-context bytes, zero prompt bytes, zero completion bytes, zero draft body bytes, zero Google subject, zero token bytes in any captured log line AND in any thrown exception's message/cause chain (the failure paths must not leak content via an exception either); assert no `thread_reply_status` row, no `triage_audit` row, and no other persistence holds any of that content after the requests complete (success or failure). Run `./gradlew clean check` (backend — must be green), `pnpm -C apps/web tsc --noEmit && pnpm -C apps/web lint && pnpm -C apps/web vitest run && pnpm -C apps/web i18n:check` (must be green), and `./gradlew :backend:core:aiEval -PdeterministicOnly` (dims 4/6/8 must be green; dim 7 either green → DRFT-04 Complete, or red → DRFT-04 reply-status portion Partially complete). Fill `05B-VALIDATION.md` (validation strategy table per the test map; flip `nyquist_compliant: true` + `wave_0_complete: true`; record the eval sign-off — deterministic dims 4/6/8 green, dim 7 pass/fail status, LLM-judge dims 1/2/3/5 report-only pending calibration; record the classifier-accuracy result and, if <85%, the LLM-hybrid follow-up). Write `05B-UAT.md` (the SPEC §Acceptance Criteria as a manual UAT checklist + the automated-coverage map + any env-blocked items with replay commands, mirroring 04-UAT.md). Flip `REQUIREMENTS.md`: DRFT-01, DRFT-02, DRFT-03 → Complete in the checklist and the Traceability table; DRFT-04 → Complete IF the dim-7 gate passed, else → **Partially complete** with the note "no-auto-send + one-draft-per-thread + threading-headers complete; reply-status classifier accuracy {X}% < 85% on the held-out set — LLM-hybrid follow-up tracked"; update the WEB-02 row to note the draft-review portion is done (onboarding, rules+live-preview, triage audit log+undo, billing, draft-review — analytics still → 5C) and that `GET /api/triage/audit` is now built; update the coverage footer date. Update `ROADMAP.md`: mark "Phase 5B: User Surface — AI Draft Replies" `[x]`, fill its "Plans:" count (8), update the Progress table row + the execution-order/parallelization notes (Wave 3 = `05B-03` alone; `05B-04` depends on `05B-03`, not parallel with it).
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew clean check 2>&1 | tail -10 && ./gradlew :backend:core:aiEval -PdeterministicOnly 2>&1 | tail -5 && cd apps/web && pnpm tsc --noEmit 2>&1 | tail -3 && pnpm vitest run 2>&1 | tail -5 && pnpm i18n:check</automated>
  </verify>
  <acceptance_criteria>
    - `DraftPrivacySweepTest` passes: no email body / sent-mail tone-context / prompt / completion / draft body / Google subject / token bytes in any captured log line OR in any thrown exception's message/cause chain (success AND forced-failure paths); no persistence holds that content after the requests
    - `./gradlew clean check` (backend) green; `./gradlew :backend:core:aiEval -PdeterministicOnly` — dims 4/6/8 green (dim 7 pass or documented fail); `apps/web` `tsc --noEmit` + `lint` + `vitest run` + `i18n:check` all green
    - `05B-VALIDATION.md` filled with the validation strategy table, `nyquist_compliant: true`, `wave_0_complete: true`, the eval sign-off (dims 4/6/8 + dim-7 status), and the classifier-accuracy result (+ LLM-hybrid follow-up if <85%)
    - `05B-UAT.md` exists with the SPEC acceptance criteria as a checklist + automated-coverage map + replay commands for any env-blocked items
    - `REQUIREMENTS.md`: DRFT-01, DRFT-02, DRFT-03 marked Complete; DRFT-04 Complete (dim-7 passed) or Partially complete with the LLM-hybrid follow-up note (dim-7 failed); WEB-02 row updated; footer date updated
    - `ROADMAP.md`: Phase 5B `[x]`, "Plans: 8 plans", Progress table row + execution-order notes updated (03 then 04, not parallel)
    - `grep -n "DRAFT_REPLY" .planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md` returns nothing
  </acceptance_criteria>
  <done>Phase 5B closed: all gates green, eval signed off, requirements + roadmap flipped.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| eval fixtures (committed to the repo) | must be synthetic — never real user mail (Google Limited Use + the no-persistence privacy lock) |
| log/persistence sweep under synthetic load | the FND-03-analogous proof that content never leaks |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05B-07-01 | Information Disclosure | a committed eval fixture containing a real email body / address / Google subject | mitigate | Fixture READMEs mandate synthetic/anonymized only; a fixture-content lint (or code review) checks no fixture carries a real address pattern / a real Google subject; this is the same posture as the Phase 4 eval fixtures |
| T-05B-07-02 | Information Disclosure | the draft / classify / list paths leaking email content into logs or persistence | mitigate | `DraftPrivacySweepTest` (FND-03-analogous) — synthetic-load log capture asserting zero body/tone/prompt/completion/draft-body/subject/token bytes + zero such content in `thread_reply_status` / `triage_audit` / any persistence after the requests |
| T-05B-07-03 | Repudiation | the no-auto-send / allow-list invariant silently regressing | mitigate | `DraftSafetyEvalTest` (dim 4) + `DraftPathArchUnitTest` (Plan 03) run in CI; the no-`drafts.send`/`drafts.update`/`messages.send` ArchUnit rule fails the build on regression |
| T-05B-07-04 | Tampering | the threading-correctness contract regressing (mojibake subjects, double `Re:`, missing headers, mis-thread) | mitigate | `DraftThreadingEvalTest` (dim 6) parses the built MIME back with `jakarta.mail` and asserts every header against fixture expectations across all edge cases + the cross-thread-bleed pair |
| T-05B-07-05 | (quality, flagged assumption) | the heuristic reply-status classifier missing the >= 85% bar in production | mitigate (best-effort + documented) | `ClassifierAccuracyEvalTest` (dim 7) measures it against the held-out fixture set every CI run; a miss surfaces loudly; the offline flywheel re-scores it periodically; the designed-for LLM-hybrid fallback is the documented remediation if it can't reach the bar — not a v1 blocker per the SPEC Ambiguity Report |
</threat_model>

<verification>
- `./gradlew clean check` (backend) green
- `./gradlew :backend:core:aiEval -PdeterministicOnly`: dims 4/6/8 green; dim 7 green (→ DRFT-04 Complete) or red (→ DRFT-04 Partially complete + follow-up); CI invokes this task as its own job; the test is never `@Disabled` and the 85% bar is never lowered
- `pnpm -C apps/web tsc --noEmit && pnpm -C apps/web lint && pnpm -C apps/web vitest run && pnpm -C apps/web i18n:check` all green
- `05B-VALIDATION.md` flipped (`nyquist_compliant: true`, `wave_0_complete: true`, dim-7 result recorded); `05B-UAT.md` written; `REQUIREMENTS.md` DRFT-01..03 Complete, DRFT-04 Complete-or-Partially-complete per dim-7, WEB-02 updated; `ROADMAP.md` Phase 5B complete; `05B-AI-SPEC.md` has no `DRAFT_REPLY`
- `mcp__jetbrains__get_file_problems` on `DraftPrivacySweepTest.java` + the eval files — no problems
</verification>

<success_criteria>
Phase 5B closed: deterministic AI-eval dimensions (4 safety, 6 threading, 8 token budget) gate and pass unconditionally; dim 7 (classifier accuracy ≥85% TO_REPLY/AWAITING on a held-out set with ≥5 non-trivial edge cases) gates the merge AND routes DRFT-04's status — never "measure and document with the bar intact"; `DraftPrivacySweepTest` proves no content leakage via logs OR exceptions on success and failure paths; CI runs `aiEval` as its own job; `05B-AI-SPEC.md` reuses `CallSite.DRAFT`; full backend `check` + `apps/web` gates green; `05B-VALIDATION.md` + `05B-UAT.md` complete; DRFT-01..03 → Complete, DRFT-04 → Complete (dim-7 passed) or Partially complete + LLM-hybrid follow-up (dim-7 failed); WEB-02 row updated; Phase 5B marked complete in the roadmap.
</success_criteria>

<output>
After completion, create `.planning/phases/05B-user-surface-ai-draft-replies/05B-07-SUMMARY.md`
</output>
