---
status: passed
phase: 05B-user-surface-ai-draft-replies
source: [05B-SPEC.md, 05B-VALIDATION.md]
started: 2026-05-13
updated: 2026-05-13
---

# Phase 5B UAT - AI Draft Replies

Phase 5B is covered by green backend, frontend, and deterministic AI eval gates. Real Gmail UI inspection and human tone review remain optional launch-readiness checks, not blockers for phase closure, because CI uses mocked Gmail clients and synthetic fixture mail.

## Scenarios

| # | Scenario | Steps | Expected | Coverage | Status |
|---|----------|-------|----------|----------|--------|
| 1 | Threaded Gmail draft headers | Generate a triage or on-demand draft for a fixture thread. | Draft MIME carries `In-Reply-To`, `References`, one `Re:` prefix, `To`, base64url raw without padding, and the intended Gmail `threadId`. | automated: YES / manual: OPTIONAL - `ReplyMimeBuildTest`, `ThreadingHeaderValidatorTest`, `DraftThreadingEvalTest`, `./gradlew.bat :backend:core:aiEval -PdeterministicOnly --console=plain` | pass |
| 2 | Existing triage `save_draft` path is threaded | Run the automatic triage save-draft path. | The existing `TriageOrchestratorService -> TriageAuditSaga -> TriageGmailWriter.saveDraft` route passes `ReplyHeaders`; not only the new on-demand path is fixed. | automated: YES / manual: NO - `TriageAuditSagaDraftThreadingTest`, `AutomaticTriageDraftUsesToneGenerationTest`, `./gradlew.bat clean check --console=plain` | pass |
| 3 | Draft generation uses the LLM gateway | Run draft generation for a synthetic thread. | Draft body is non-empty, produced by `LlmGateway.chatForDraft(CallSite.DRAFT, ...)`, and Spring AI imports remain confined to the adapter package. | automated: YES / manual: NO - `GenerateThreadDraftServiceTest`, `DraftPathArchUnitTest`, `./gradlew.bat clean check --console=plain` | pass |
| 4 | Tone context is in-request and not persisted | Build a draft with recent sent-mail snippets available. | Tone context is fetched in request, stripped/sanitized/truncated, degrades to descriptors-only on token/Gmail failure, and is not stored. | automated: YES / manual: OPTIONAL - `ToneContextBuilderTest`, `DraftTokenBudgetEvalTest`, `DraftPrivacySweepTest` | pass |
| 5 | Privacy sweep catches draft content bleed | Run success, safety failure, and Gmail-fetch failure draft paths plus classify/list reads. | No email body, sent-mail tone context, prompt, completion, draft body, Google subject, raw address, or token sentinel appears in logs, exception chains, `triage_audit`, or `thread_reply_status`. | automated: YES / manual: NO - `DraftPrivacySweepTest`, `./gradlew.bat :backend:core:test --tests com.zeromail.core.draft.DraftPrivacySweepTest --console=plain` | pass |
| 6 | No auto-send or in-app send/edit | Search backend and inspect the needs-reply UI. | No 5B path calls `users.drafts.send`, `users.drafts.update`, or `users.messages.send`; UI exposes only Gmail deep link plus draft/regenerate/resolve controls. | automated: YES / manual: OPTIONAL - `DraftSafetyEvalTest`, `DraftPathArchUnitTest`, `pnpm -C apps/web test`, Playwright checks from 05B-06 | pass |
| 7 | Audit list endpoint is live | Request `GET /api/triage/audit` with cursor/action filters. | Paginated rows include audit/thread/message/draft identifiers; the 5A audit-list unavailable sentinel is gone. | automated: YES / manual: NO - `AuditLogQueryServiceTest`, `TriageAuditControllerContractTest`, `AuditLogPaginationTest`, `pnpm -C apps/web test` | pass |
| 8 | Draft action works from audit and needs-reply rows | Trigger "Draft reply" or "Regenerate draft" from a triage audit row and a needs-reply row. | Backend creates/regenerates one Gmail draft per thread, handles 409 in-flight contention, and the web row shows the non-destructive result state. | automated: YES / manual: OPTIONAL - `ThreadDraftControllerContractTest`, `DraftLockContentionTest`, `GenerateDraftButton`/needs-reply Vitest, `needs-reply.spec.ts` | pass |
| 9 | Needs-reply two-bucket inbox | Load `/needs-reply` with empty, one-row, and many-row mocked states. | Both `to-reply` and `awaiting-their-reply` buckets render without horizontal overflow; Zero-Mail drafts show draft status and Gmail links. | automated: YES / manual: OPTIONAL - `NeedsReplyInboxQueryServiceTest`, `NeedsReplyTable.test.tsx`, `pnpm -C apps/web test`, `pnpm -C apps/web e2e -- needs-reply.spec.ts` from 05B-06 | pass |
| 10 | Classifier accuracy gate | Run deterministic AI eval. | Held-out classifier set scores at least 85% with at least five non-trivial edge cases and no one-direction skew. | automated: YES / manual: NO - `ClassifierAccuracyEvalTest`, observed 22/22 correct, 7 edge cases | pass |
| 11 | Full phase gate | Run backend, frontend, and deterministic eval gates. | All required commands succeed. | automated: YES / manual: NO - `./gradlew.bat clean check --console=plain`, `./gradlew.bat :backend:core:aiEval -PdeterministicOnly --console=plain`, `pnpm -C apps/web typecheck`, `pnpm -C apps/web lint`, `pnpm -C apps/web test`, `pnpm -C apps/web i18n:check` | pass |

## Replay Commands

```powershell
.\gradlew.bat clean check --console=plain
.\gradlew.bat :backend:core:aiEval -PdeterministicOnly --console=plain
pnpm -C apps/web typecheck
pnpm -C apps/web lint
pnpm -C apps/web test
pnpm -C apps/web i18n:check
```

Optional browser/Gmail follow-up:

```powershell
pnpm -C apps/web e2e -- needs-reply.spec.ts
pnpm -C apps/web e2e -- triage-audit.spec.ts --workers=1
```

## Summary

total: 11
passed: 11
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

None for Phase 5B closure. Analytics remains Phase 5C, and human tone/judge calibration remains a launch-readiness follow-up before promoting judge dims 1/2/3/5 to required CI.
