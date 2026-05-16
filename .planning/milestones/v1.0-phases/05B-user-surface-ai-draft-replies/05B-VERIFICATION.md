---
status: passed
phase: 05B-user-surface-ai-draft-replies
verified_at: 2026-05-13
requirements: [DRFT-01, DRFT-02, DRFT-03, DRFT-04]
score: 4/4 requirements
automated_checks:
  backend_clean_check: passed
  backend_ai_eval_deterministic: passed
  draft_privacy_sweep: passed
  web_typecheck: passed
  web_lint: passed
  web_vitest: passed
  web_i18n_check: passed
  schema_drift: passed
  codebase_drift: skipped
code_review: skipped
---

# Phase 05B Verification - User Surface AI Draft Replies

## Verdict

Passed. Phase 05B delivers AI draft replies through the existing `LlmGateway`, saves them as Gmail drafts with correct threading headers, keeps tone context in request without persistence, blocks every auto-send/update path, and surfaces draft review/regeneration through the needs-reply and live triage audit web surfaces.

`WEB-02` remains globally unchecked because analytics belongs to Phase 5C. The Phase 5B draft-review portion is complete, and the `GET /api/triage/audit` backend gap from 5A is now closed.

## Requirement Coverage

| Requirement | Result | Evidence |
|-------------|--------|----------|
| DRFT-01 | PASS | `ThreadDraftController`, `GenerateThreadDraftService`, and `apps/web/features/needs-reply` provide manual draft generation/regeneration for a thread. `ThreadDraftControllerContractTest`, needs-reply Vitest, and 05B-06 Playwright coverage passed. |
| DRFT-02 | PASS | `ReplyMimeBuilder`, `ReplyHeaders`, and `ThreadingHeaderValidator` route both triage and on-demand drafts through valid `In-Reply-To`/`References` MIME. `DraftThreadingEvalTest`, `ReplyMimeBuildTest`, and `TriageAuditSagaDraftThreadingTest` passed. |
| DRFT-03 | PASS | `ToneContextBuilder` fetches recent sent-mail snippets in request, strips/sanitizes them, degrades on token/Gmail failure, and persists no tone content. `ToneContextBuilderTest`, `DraftTokenBudgetEvalTest`, and `DraftPrivacySweepTest` passed. |
| DRFT-04 | PASS | No auto-send path exists; deterministic safety eval gates no `drafts.send`, `drafts.update`, or `messages.send`, and the UI exposes only review in Gmail. `DraftSafetyEvalTest`, `DraftPathArchUnitTest`, grep gates, and web tests passed. |

## Must-Have Checks

| Check | Result | Evidence |
|-------|--------|----------|
| Deterministic eval dims 4/6/7/8 gate | PASS | `./gradlew.bat :backend:core:aiEval -PdeterministicOnly --console=plain` passed. |
| Classifier accuracy threshold | PASS | `ClassifierAccuracyEvalTest` scored 22/22 correct (100%) over 22 fixtures with 7 edge cases and no one-direction skew; DRFT-04 remains Complete. |
| Fixture privacy | PASS | 15 draft fixtures and 22 classifier fixtures are synthetic; grep for common real mail domains returned no matches. |
| Draft privacy sweep | PASS | `DraftPrivacySweepTest` passed success, `SafetyViolationException`, and Gmail-fetch failure paths with no forbidden sentinels in logs, exception chains, projections, `triage_audit`, or `thread_reply_status`. |
| Full backend gate | PASS | `./gradlew.bat clean check --console=plain` passed after Spotless formatting. |
| Full frontend gates | PASS | `pnpm -C apps/web typecheck`, `lint`, `test` (39 files / 236 tests), and `i18n:check` passed. |
| Validation and UAT artifacts | PASS | `05B-VALIDATION.md` has `status: approved`, `nyquist_compliant: true`, and `wave_0_complete: true`; `05B-UAT.md` has 11/11 passed scenarios. |
| Requirement traceability | PASS | `.planning/REQUIREMENTS.md` marks DRFT-01..DRFT-04 Complete and updates WEB-02 to show 5B draft-review done, analytics remaining in 5C. |
| Roadmap traceability | PASS | `.planning/ROADMAP.md` marks Phase 5B complete with 8/8 plans. |
| Schema drift gate | PASS | `gsd-sdk query verify.schema-drift "05B"` returned `drift_detected: false`. |

## Quality Gates

- Code review: skipped because this Codex runtime cannot auto-spawn `gsd-code-reviewer` without explicit sub-agent authorization. Non-blocking per execute-phase. Recommended before merge: `$gsd-code-review 05B --depth=standard`.
- Codebase drift: skipped because `.planning/codebase/STRUCTURE.md` does not exist. Non-blocking per execute-phase.
- JetBrains file-problem check: attempted for `DraftPrivacySweepTest.java`, but JetBrains MCP timed out after 120 seconds. Gradle compilation, focused test, full `clean check`, and deterministic eval passed.

## Residual Risks

- LLM-judge dimensions 1/2/3/5 remain report-only until calibrated against human labels at the AI-SPEC threshold.
- Real Gmail UI threading and human tone judgment still benefit from launch-readiness manual review, because CI uses mocks and synthetic fixtures.
- Vietnamese safety/error copy added in 05B should receive native-speaker review before launch.
- Needs-reply display metadata uses live Gmail batched reads; if it becomes hot, add the short-TTL metadata cache noted in 05B-05/06 summaries.

## Conclusion

Phase 05B is verified as complete against its phase goal, requirement IDs, deterministic AI eval gates, and privacy constraints. Proceed to Phase 5C or final launch-hardening planning without reopening 5B.
