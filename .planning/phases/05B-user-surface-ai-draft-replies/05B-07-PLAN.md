---
phase: 05B-user-surface-ai-draft-replies
plan: 07
type: execute
wave: 6
depends_on: ["05B-01", "05B-02", "05B-03", "05B-04", "05B-05", "05B-06"]
files_modified:
  - backend/core/build.gradle.kts
  - backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftThreadingEvalTest.java
  - backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftSafetyEvalTest.java
  - backend/core/src/aiEval/java/com/zeromail/core/aiEval/ClassifierAccuracyEvalTest.java
  - backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftTokenBudgetEvalTest.java
  - backend/core/src/aiEval/resources/fixtures/draft/README.md
  - backend/core/src/aiEval/resources/fixtures/classifier/README.md
  - backend/core/src/test/java/com/zeromail/core/draft/DraftPrivacySweepTest.java
  - .planning/phases/05B-user-surface-ai-draft-replies/05B-VALIDATION.md
  - .planning/phases/05B-user-surface-ai-draft-replies/05B-UAT.md
  - .planning/REQUIREMENTS.md
  - .planning/ROADMAP.md
autonomous: true
requirements: [DRFT-01, DRFT-02, DRFT-03, DRFT-04]
must_haves:
  truths:
    - "Deterministic eval dims (4 safety/allow-list, 6 threading-header correctness, 7 classifier accuracy, 8 token budget) run via `:backend:core:aiEval -PdeterministicOnly` and pass"
    - "DraftPrivacySweepTest (FND-03-analogous) proves no email body / sent-mail tone context / prompt / completion / draft body reaches logs or persistence across the draft + classify + list paths"
    - "`./gradlew clean check` (backend) is green; `apps/web` tsc/lint/vitest/i18n:check are green"
    - "REQUIREMENTS.md flips DRFT-01..04 to Complete and updates the WEB-02 row; ROADMAP.md marks Phase 5B complete"
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
Close Phase 5B: stand up the `:backend:core:aiEval` tagged source set + Gradle task (reuse any existing eval-harness scaffolding from Phase 4), land the deterministic eval dimensions (dim 4 safety/allow-list, dim 6 threading-header correctness, dim 7 classifier accuracy on the held-out fixture set, dim 8 token budget) plus synthetic-fixture seeds (and `@Tag("judge")` skeletons for the report-only LLM-judge dims), add a `DraftPrivacySweepTest` (FND-03-analogous), run the full backend `check` + `apps/web` gates + the deterministic eval, fill `05B-VALIDATION.md` + write `05B-UAT.md`, and flip `REQUIREMENTS.md` DRFT-01..04 to Complete + update the WEB-02 row + mark Phase 5B complete in `ROADMAP.md`.

Purpose: The phase-gate sign-off. Per AI-SPEC §5: deterministic dims 4/6/7/8 gate the merge; LLM-judge dims 1/2/3/5 ride along report-only until calibrated >= 0.7.
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
    - `ClassifierAccuracyEvalTest` (dim 7, deterministic): load the held-out classifier fixture set (20-30 labeled threads mirroring inbox-zero's `determine-thread-status.test.ts` cases — see the fixture README), run `ClassifyThreadReplyStatusService` (heuristic v1) over each, compute accuracy on the TO_REPLY <-> AWAITING_THEIR_REPLY split; assert >= ~85% and no one-direction skew; FYI/ACTIONED reported, not gated. If the heuristic misses the bar the test FAILS — surface it (it's the SPEC-flagged assumption; the fallback is the LLM hybrid, a deferred follow-up, not a v1 fix).
    - `DraftTokenBudgetEvalTest` (dim 8, deterministic): a gateway-level assertion that a draft call without an explicit `max_tokens` is refused; an oversized tone-context build raises `TokenBudgetExceededException` and the draft still proceeds with descriptors-only; recorded `promptTokens`/`completionTokens` within bounds on a fixture run (or assert the configured `max_tokens` cap is applied).
    - Fixture READMEs (`fixtures/draft/README.md`, `fixtures/classifier/README.md`): document the composition (the draft suite's critical-path / failure-mode / threading-edge / content-edge / adversarial-pair cases per AI-SPEC §5; the classifier set's labeled cases) and the "synthetic/anonymized only — no real mail" rule; seed at least the minimum count of fixture files (>= 15 draft, 20-30 classifier) — synthetic JSON, no real bodies. The LLM-judge dims 1/2/3/5 can be left as `@Tag("judge")` skeletons that read the same fixtures and call `LlmGateway` — report-only, not required to pass in this plan.
  </behavior>
  <action>
    Wire/extend the `aiEval` source set + Gradle task in `backend/core/build.gradle.kts` (reuse any existing eval-harness scaffolding). Create the four deterministic eval test classes + the two fixture READMEs + the minimum synthetic fixture files + `@Tag("judge")` skeletons for dims 1/2/3/5. Run `./gradlew :backend:core:aiEval -PdeterministicOnly` — dims 4/6/7/8 must pass. If dim 7's heuristic misses >= 85% on the fixtures you author, do NOT lower the bar — document the gap in the SUMMARY + `05B-VALIDATION.md` as the flagged assumption coming due (with the LLM-hybrid follow-up noted); the phase can still close per the SPEC Ambiguity Report, but flag it loudly.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:aiEval -PdeterministicOnly 2>&1 | tail -15</automated>
  </verify>
  <acceptance_criteria>
    - `:backend:core:aiEval` task exists with `-PdeterministicOnly` / `-PjudgeOnly` modes; `aiEval` is NOT part of `check`
    - `./gradlew :backend:core:aiEval -PdeterministicOnly` passes: dim 4 (allow-list/no-auto-send), dim 6 (threading headers incl. all edge cases + cross-thread-bleed), dim 7 (classifier >= 85% TO_REPLY/AWAITING — or the gap documented), dim 8 (token budget)
    - `fixtures/draft/` has >= 15 synthetic fixture files; `fixtures/classifier/` has 20-30 labeled synthetic fixtures; the READMEs document composition + the no-real-mail rule
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
    Create `DraftPrivacySweepTest` (FND-03-analogous): drive a draft generation + a reply-status classification + a `GET /api/triage/audit` + a `GET /api/threads` page under synthetic load with log capture; assert zero email body bytes, zero sent-mail tone-context bytes, zero prompt bytes, zero completion bytes, zero draft body bytes, zero Google subject, zero token bytes in any captured log line; assert no `thread_reply_status` row, no `triage_audit` row, and no other persistence holds any of that content after the requests complete. Run `./gradlew clean check` (backend — must be green), `pnpm -C apps/web tsc --noEmit && pnpm -C apps/web lint && pnpm -C apps/web vitest run && pnpm -C apps/web i18n:check` (must be green), and `./gradlew :backend:core:aiEval -PdeterministicOnly` (must be green). Fill `05B-VALIDATION.md` (validation strategy table per the test map; flip `nyquist_compliant: true` + `wave_0_complete: true`; record the eval sign-off — deterministic dims 4/6/7/8 green, LLM-judge dims 1/2/3/5 report-only pending calibration; note the classifier-accuracy assumption status). Write `05B-UAT.md` (the SPEC §Acceptance Criteria as a manual UAT checklist + the automated-coverage map + any env-blocked items with replay commands, mirroring 04-UAT.md). Flip `REQUIREMENTS.md`: DRFT-01..04 -> Complete in both the checklist and the Traceability table; update the WEB-02 row to note the draft-review portion is done (onboarding, rules+live-preview, triage audit log+undo, billing, draft-review — analytics still -> 5C) and that `GET /api/triage/audit` is now built; update the coverage footer date. Update `ROADMAP.md`: mark "Phase 5B: User Surface — AI Draft Replies" `[x]`, fill its "Plans:" count (8), update the Progress table row + the execution-order/parallelization notes.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew clean check 2>&1 | tail -10 && ./gradlew :backend:core:aiEval -PdeterministicOnly 2>&1 | tail -5 && cd apps/web && pnpm tsc --noEmit 2>&1 | tail -3 && pnpm vitest run 2>&1 | tail -5 && pnpm i18n:check</automated>
  </verify>
  <acceptance_criteria>
    - `DraftPrivacySweepTest` passes: no email body / sent-mail tone-context / prompt / completion / draft body / Google subject / token bytes in any captured log line; no persistence holds that content after the requests
    - `./gradlew clean check` (backend) green; `./gradlew :backend:core:aiEval -PdeterministicOnly` green; `apps/web` `tsc --noEmit` + `lint` + `vitest run` + `i18n:check` all green
    - `05B-VALIDATION.md` filled with the validation strategy table, `nyquist_compliant: true`, `wave_0_complete: true`, the eval sign-off, and the classifier-accuracy assumption status
    - `05B-UAT.md` exists with the SPEC acceptance criteria as a checklist + automated-coverage map + replay commands for any env-blocked items
    - `REQUIREMENTS.md`: DRFT-01, DRFT-02, DRFT-03, DRFT-04 marked Complete (checklist + Traceability table); WEB-02 row updated; footer date updated
    - `ROADMAP.md`: Phase 5B `[x]`, "Plans: 8 plans", Progress table row updated
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
- `./gradlew :backend:core:aiEval -PdeterministicOnly` green (dims 4/6/7/8)
- `pnpm -C apps/web tsc --noEmit && pnpm -C apps/web lint && pnpm -C apps/web vitest run && pnpm -C apps/web i18n:check` all green
- `05B-VALIDATION.md` flipped (`nyquist_compliant: true`, `wave_0_complete: true`); `05B-UAT.md` written; `REQUIREMENTS.md` DRFT-01..04 Complete + WEB-02 updated; `ROADMAP.md` Phase 5B complete
- `mcp__jetbrains__get_file_problems` on `DraftPrivacySweepTest.java` + the eval files — no problems
</verification>

<success_criteria>
Phase 5B closed: deterministic AI-eval dimensions (4 safety, 6 threading, 7 classifier accuracy, 8 token budget) gate and pass; `DraftPrivacySweepTest` proves no content leakage; full backend `check` + `apps/web` gates green; `05B-VALIDATION.md` + `05B-UAT.md` complete; DRFT-01..04 flipped to Complete + the WEB-02 row updated; Phase 5B marked complete in the roadmap. (Classifier-accuracy is a flagged assumption — if the heuristic misses ≥85% on the fixtures, the gap is documented with the LLM-hybrid follow-up, per the SPEC Ambiguity Report.)
</success_criteria>

<output>
After completion, create `.planning/phases/05B-user-surface-ai-draft-replies/05B-07-SUMMARY.md`
</output>
