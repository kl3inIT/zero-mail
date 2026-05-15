---
status: passed
phase: 05B-user-surface-ai-draft-replies
source: [05B-SPEC.md, 05B-VALIDATION.md, manual UI walk]
started: 2026-05-13
updated: 2026-05-13
manual_ui_verified: 2026-05-13
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

## Manual UI Verification (2026-05-13)

Driven via Playwright MCP against live Next dev (`apps/web`) + Spring `:backend:api:bootRun`. Two regressions surfaced and were fixed inline.

### Walked

| Surface | Verified | Notes |
|---------|----------|-------|
| `05B-PROTOTYPE.html` (all 10 UI-SPEC states) | pass | Sidebar + topbar chrome, 5-row populated table with `Draft reply`/`Regenerate draft`/`Generating…`, 409 amber inline notice, `No draft`/`Draft ready`/`Draft sent` badges, recompute amber banner, 5-row skeleton, To-reply/Awaiting empty states, destructive error `Alert` + `Try again`, success/destructive Sonner toasts, regenerate `alert-dialog`, `/triage` audit table with new draft action + mono `AI` marker, 320px single-column rows. Tokens (teal accent, paper-warm neutrals, 4-step type scale, 8pt spacing) align with UI-SPEC. |
| `/needs-reply` live (unauthenticated, before Fix #1) | issue → fixed | Page rendered chrome + heading + tabs without session — proxy.ts gate gap (24× ERR_CONNECTION_REFUSED to `:8080` confirmed call paths but not the leak). |
| `/needs-reply` live (unauthenticated, after Fix #1) | pass | Redirects to `/login` (HTTP 200, Vietnamese login page with `Tiếp tục với Google` and 3 commitment pillars). |
| Backend `:backend:api:bootRun` (before Fix #2) | issue → fixed | Liquibase validation failed: `030-thread-reply-status` checksum mismatch from amend in commit `d83562a`. |
| Backend `:backend:api:bootRun` (after Fix #2) | pass | Started in 24.6s, `Tomcat started on port 8080`. DB shows 030 unchanged + 031 newly applied; all 5 expected indexes on `thread_reply_status` present including `idx_thread_reply_status_resolved`. |

### Findings & fixes (closed in this UAT pass)

| # | Finding | Severity | Root cause | Fix commit |
|---|---------|----------|------------|------------|
| MUI-1 | `/needs-reply` accessible without `ZEROMAIL_SESSION` cookie; full app shell + page heading + tabs render to anonymous visitors. | major (access-control) | `apps/web/proxy.ts` PROTECTED list missing `/needs-reply` (regression — new authenticated route added in 5B-06 not registered with the gate). | `9e5270f fix(05B): gate /needs-reply behind auth in proxy` — adds route to PROTECTED + extends `route-smoke.spec.ts` regression coverage. |
| MUI-2 | `:backend:api:bootRun` fails on any DB with 030 already applied: Liquibase checksum mismatch on `030-thread-reply-status`. `clearChecksums` would silently drop the new resolved-only index. | blocker (data/migration) | Commit `d83562a fix(05B): add resolved reply-status index` amended an applied changeset instead of creating a new one. | `d16f4c8 fix(05B): split resolved-index into changeset 031` — reverts 030 to its original five SQL changes; new `031-thread-reply-status-resolved-index.yaml` owns the index; master changelog updated. |

### Not walked (deliberately deferred)

- **Authenticated `/needs-reply` with real Gmail data** — requires interactive Google OAuth (Playwright MCP cannot drive Google's bot-protected login). Already covered in CI by `apps/web/e2e/needs-reply.spec.ts` + `triage-audit.spec.ts` against `chrome-test-utils.ts` mocks. Real-Gmail walk stays in the launch-readiness checklist as the original UAT note documented.
- **Dark mode** of every state — no token regression risk; tokens are CSS variables shared with 5A which `gsd-ui-review` already audited.
