---
phase: 5B
slug: user-surface-ai-draft-replies
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-13
updated: 2026-05-13
---

# Phase 5B — Validation Strategy

Per-phase validation contract for the AI draft reply surface.

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend framework** | JUnit 5, AssertJ, Mockito, Testcontainers PostgreSQL, ArchUnit, Spring Modulith |
| **Frontend framework** | Vitest, Testing Library, Playwright |
| **AI eval harness** | `backend/core/src/aiEval` tagged source set with `:backend:core:aiEval` |
| **Quick run command** | `./gradlew.bat :backend:core:test --tests com.zeromail.core.draft.DraftPrivacySweepTest --console=plain` |
| **Full backend command** | `./gradlew.bat clean check --console=plain` |
| **Deterministic eval command** | `./gradlew.bat :backend:core:aiEval -PdeterministicOnly --console=plain` |
| **Frontend command set** | `pnpm -C apps/web typecheck`, `pnpm -C apps/web lint`, `pnpm -C apps/web test`, `pnpm -C apps/web i18n:check` |
| **Estimated runtime** | ~6 minutes for backend `clean check`; ~1 minute for web gates; ~20 seconds for deterministic eval |

## Sampling Rate

- **After every task commit:** focused backend or frontend task slice plus acceptance greps.
- **After every plan wave:** focused slices from the owning plan; Wave 7 ran full backend, web, and eval gates.
- **Before phase closure:** full suite must be green.
- **Max feedback latency:** under 8 minutes for the complete automated gate set on this workstation.

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 05B-00-01 | 00 | 0 | DRFT-02/04 | T-05B-07-03/04 | Jakarta Mail dependency and thread status schema exist; RED contracts compile | dependency/schema/test scaffold | `./gradlew.bat :backend:core:test --tests "*LiquibaseMigrationTest"` | yes | green |
| 05B-01-01 | 01 | 2 | DRFT-02 | T-05B-07-04 | Gmail draft MIME has correct `In-Reply-To`/`References` and no send path | unit/integration | `./gradlew.bat :backend:core:test --tests "*ReplyMimeBuild*" --tests "*ThreadingHeaderValidator*"` | yes | green |
| 05B-02-01 | 02 | 2 | DRFT-04 | T-05B-07-05 | Reply-status rows persist metadata only and classifier is tenant-scoped | unit/integration | `./gradlew.bat :backend:core:test --tests "*ClassifyThreadReplyStatus*"` | yes | green |
| 05B-03-01 | 03 | 3 | DRFT-01/03/04 | T-05B-07-02/03 | Draft generation uses `LlmGateway.chatForDraft`, no raw Spring AI in `core.draft`, no body returned | unit/ArchUnit | `./gradlew.bat :backend:core:test --tests "*GenerateThreadDraft*" --tests "*DraftPathArchUnit*"` | yes | green |
| 05B-04-01 | 04 | 4 | DRFT-04/WEB-02 | T-05B-07-02 | Audit and needs-reply read sides return metadata-only keyset pages | integration | `./gradlew.bat :backend:core:test --tests "*AuditLogQuery*" --tests "*NeedsReplyInboxQuery*"` | yes | green |
| 05B-05-01 | 05 | 5 | DRFT-01/04/WEB-02 | T-05B-07-02/03 | REST endpoints are thin, tenant scoped, and expose no draft body | API contract | `./gradlew.bat :backend:api:test --tests "*ThreadDraftController*" --tests "*TriageAuditController*"` | yes | green |
| 05B-06-01 | 06 | 6 | WEB-02 | T-05B-07-03 | Needs-reply UI offers draft/regenerate/open-in-Gmail only; no send/edit control | Vitest/Playwright | `pnpm -C apps/web test`, `pnpm -C apps/web e2e -- needs-reply.spec.ts` | yes | green |
| 05B-07-01 | 07 | 7 | DRFT-01..04 | T-05B-07-01/03/04/05 | Deterministic AI eval dims 4/6/7/8 gate in CI; fixtures are synthetic | aiEval | `./gradlew.bat :backend:core:aiEval -PdeterministicOnly --console=plain` | yes | green |
| 05B-07-02 | 07 | 7 | DRFT-03/04 | T-05B-07-02 | Draft/classify/list paths leak no body, tone, prompt, completion, subject, address, or token bytes to logs, exceptions, or persistence | integration privacy sweep | `./gradlew.bat :backend:core:test --tests com.zeromail.core.draft.DraftPrivacySweepTest --console=plain` | yes | green |

## Wave 0 Requirements

- [x] `backend/core/src/test/java/com/zeromail/core/draft/ReplyMimeBuildTest.java`
- [x] `backend/core/src/test/java/com/zeromail/core/draft/ThreadingHeaderValidatorTest.java`
- [x] `backend/core/src/test/java/com/zeromail/core/draft/GenerateThreadDraftServiceTest.java`
- [x] `backend/core/src/test/java/com/zeromail/core/draft/ToneContextBuilderTest.java`
- [x] `backend/core/src/test/java/com/zeromail/core/draft/DraftPrivacyLogScrubTest.java`
- [x] `backend/core/src/test/java/com/zeromail/core/draft/DraftPathArchUnitTest.java`
- [x] `backend/core/src/test/java/com/zeromail/core/thread/ClassifyThreadReplyStatusServiceTest.java`
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/thread/ThreadDraftControllerContractTest.java`
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/triage/TriageAuditControllerContractTest.java`
- [x] `apps/web/features/needs-reply/components/NeedsReplyTable.test.tsx`
- [x] `apps/web/e2e/needs-reply.spec.ts`

## AI Eval Sign-Off

| Dimension | Gate | Result | Evidence |
|-----------|------|--------|----------|
| Dim 1 voice fidelity | report-only LLM judge | pending calibration | `@Tag("judge")` skeletons stay excluded by `-PdeterministicOnly` until judge/human correlation >= 0.7 |
| Dim 2 relevance/no hallucinated commitments | report-only LLM judge | pending calibration | same judge-only path |
| Dim 3 brevity/actionability | report-only LLM judge | pending calibration | same judge-only path |
| Dim 4 safety/no-auto-send | deterministic required | pass | `DraftSafetyEvalTest`; CI job runs `:backend:core:aiEval -PdeterministicOnly` |
| Dim 5 prompt-injection resistance | report-only LLM judge | pending calibration | same judge-only path |
| Dim 6 threading headers | deterministic required | pass | `DraftThreadingEvalTest` parses MIME and validates headers/thread/cross-thread bleed |
| Dim 7 classifier accuracy | deterministic required | pass | `ClassifierAccuracyEvalTest`: 22/22 correct (100%), 7 edge-case fixtures, no one-direction skew |
| Dim 8 token budget | deterministic required | pass | `DraftTokenBudgetEvalTest` enforces explicit draft max tokens and tone-context degradation |

Fixture sign-off:

- Draft fixtures: 15 synthetic JSON fixtures.
- Classifier fixtures: 22 synthetic JSON fixtures.
- Classifier edge cases: 7 non-trivial edge cases.
- Fixture privacy lint: no common real email domains found; synthetic/anonymized-only READMEs present.

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real Gmail draft appears in original Gmail UI thread | DRFT-02 | CI uses mocked Gmail clients and MIME parsing; a real Gmail account is required to inspect the Gmail UI thread placement | Use a connected test tenant, trigger draft from `/needs-reply`, then open the Gmail deep link and confirm the draft is in the original thread |
| Human tone judgment | DRFT-03 | Automated deterministic tests prove tone context is fetched/sanitized and judge scaffolding exists, but "sounds like me" needs calibrated human labels | Review the synthetic judge fixtures and a connected test-tenant draft; record human labels before promoting judge dims |
| Vietnamese draft/error copy nuance | WEB-02 | Automated i18n parity cannot validate copy quality | Native speaker review of `apps/web/i18n/messages/vi.json` keys touched in 05B |

## Validation Sign-Off

- [x] All tasks have automated verify commands or Wave 0 dependencies.
- [x] Sampling continuity maintained; no 3 consecutive tasks lacked automated verification.
- [x] Wave 0 covered all missing references and future contracts.
- [x] No watch-mode flags in required gates.
- [x] Feedback latency stayed under the phase threshold.
- [x] `nyquist_compliant: true` set in frontmatter.
- [x] `wave_0_complete: true` set in frontmatter.

**Approval:** approved 2026-05-13
