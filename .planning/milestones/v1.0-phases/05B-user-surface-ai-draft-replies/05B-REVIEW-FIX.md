---
phase: 05B-user-surface-ai-draft-replies
review_path: .planning/phases/05B-user-surface-ai-draft-replies/05B-REVIEW.md
fixed_at: 2026-05-13T12:05:00+07:00
fix_scope: all
findings_in_scope: 13
fixed: 13
skipped: 0
iteration: 1
status: all_fixed
---

# Phase 05B Code Review Fix Report

## Summary

All findings from `05B-REVIEW.md` were fixed under `--fix --all`.

## Fixes Applied

- CR-01: Reserved and leased on-demand draft audit rows before the non-idempotent Gmail draft write, with a regression test that unreclaimed pending audit rows skip Gmail writes.
- WR-01: Removed the duplicate `ThreadDraftSaved` classification path that stored draft ids as message ids.
- WR-02: Split Redis lock contention from Redis backend unavailability and mapped backend outage to a retryable unavailable draft-generation error.
- WR-03: Added backend-owned audit `undoableUntil` and switched the web audit mapper away from a hard-coded 30-day guess.
- WR-04: Removed stale local draft-status state from needs-reply rows.
- WR-05: Added a lightweight `to-reply-count` endpoint and web hook path instead of fetching a Gmail-backed inbox row for the sidebar count.
- WR-06: Added Google API Client request timeout support and used the tone fetch budget for tone-context Gmail clients.
- WR-07: Marked missing, blank, or undecryptable Gmail refresh-token envelopes as non-retryable `DEAD` deliveries and logged failure class names for retryable failures.
- WR-08: Added a resolved-only `thread_reply_status` keyset index and migration assertion.
- IN-01: Consolidated duplicate draft-generation message copy.
- IN-02: Added an accessible label for the sidebar needs-reply badge.
- IN-03: Added an authoritative shadow-mode GET endpoint and removed the frontend fallback state/copy.
- IN-04: Avoided repeated `toReplyCount` work on non-initial needs-reply page loads.

## Fix Commits

- `b5d7a4e` - `fix(05B): CR-01 reserve draft audit before Gmail write`
- `2f545fb` - `fix(05B): WR-01 remove duplicate draft classification listener`
- `52eb0b6` - `fix(05B): WR-02 surface draft lock backend outage`
- `52c5f40` - `fix(05B): WR-03 use backend audit undo deadline`
- `a21d39b` - `fix(05B): WR-04 render draft status from refreshed row`
- `35bd742` - `fix(05B): WR-05 add lightweight to-reply count endpoint`
- `ee38708` - `fix(05B): IN-04 count only first needs-reply page`
- `9698b51` - `fix(05B): IN-02 label needs-reply sidebar badge`
- `1adcad2` - `fix(05B): IN-01 consolidate draft generation copy`
- `f4fb7c0` - `fix(05B): lease draft audit before Gmail write`
- `81b0380` - `fix(05B): bound tone Gmail fetch timeouts`
- `1bf102b` - `fix(05B): fail dead on invalid Gmail refresh token`
- `d83562a` - `fix(05B): add resolved reply-status index`
- `ee9e673` - `fix(05B): add shadow-mode read endpoint`

## Verification

- `pnpm -C apps/web generate:api` - PASS
- `./gradlew.bat :backend:core:test --tests com.zeromail.core.draft.GenerateThreadDraftServiceTest --tests com.zeromail.core.thread.ClassifyThreadReplyStatusServiceTest --tests com.zeromail.core.triage.AuditLogQueryServiceTest --tests com.zeromail.core.gmail.GmailDeliveryProcessingServiceTest --tests com.zeromail.core.support.LiquibaseMigrationTest :backend:api:test --tests com.zeromail.api.controllers.thread.ThreadDraftControllerContractTest --tests com.zeromail.api.controllers.triage.TriageTenantControllerContractTest --tests com.zeromail.api.controllers.triage.TriageAuditControllerContractTest --console=plain` - PASS
- `./gradlew.bat :backend:core:spotlessCheck :backend:api:spotlessCheck --console=plain` - PASS
- `pnpm -C apps/web typecheck` - PASS
- `pnpm -C apps/web lint` - PASS
- `pnpm -C apps/web i18n:check` - PASS
- `pnpm -C apps/web test -- features/triage features/needs-reply` - PASS (5 files, 20 tests)
- `git diff --check` - PASS

## Notes

JetBrains `get_file_problems` timed out during diagnostics, so verification used Gradle compile/test,
Spotless, TypeScript, ESLint, i18n, and focused Vitest checks.
