---
slug: fix-codeql-alerts
date: 2026-05-26
status: in-progress
---

# Fix all open CodeQL code-scanning alerts

## Goal

Close all 63 open code-scanning alerts on https://github.com/kl3inIT/zero-mail/security
(Dependabot already clean, secret scanning clean).

## Triage

| Alert(s) | Rule | Verdict | Action |
|---|---|---|---|
| #223-#281 (59) | `java/local-variable-is-never-read` | False positive — Java 25 sealed-switch `case T ignored ->` where binding is syntactically required but unused | Replace `ignored` → `_` (unnamed pattern, matches CLAUDE.md convention) |
| #284 | `java/unused-parameter` | Real — `baseUrl` param of `PlatformLlmRuntimeRouter.fallbackKeyFormat` not used | Drop param |
| #285, #286 | `java/log-injection` (medium) | Defensive — dynamic strings (`gmailMessageId`, `senderDomain`) in log statements; CRLF tainting theoretically possible | Add `stripCrlf` helper and apply to dynamic args |
| #287 | `java/missing-case-in-switch` | False positive — `UnsubscribeAttemptState` has 4 values, switch covers all 4 via comma-separated case labels | Dismiss via gh api |

## Files touched

- `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java`
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageUndoService.java`
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageAuditSaga.java`
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageActionResultJsonValidator.java`
- `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditWriter.java`
- `backend/core/src/main/java/com/zeromail/core/rules/usecases/RulePreviewService.java`
- `backend/core/src/main/java/com/zeromail/core/llm/usecases/PlatformLlmRuntimeRouter.java`
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java`
- `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/CampaignRetryService.java`

## Verification

- `./gradlew :backend:core:compileJava :backend:worker:compileJava` → BUILD SUCCESSFUL
- `./gradlew :backend:core:test :backend:worker:test` → BUILD SUCCESSFUL
- After push: wait for CodeQL re-scan; alerts 223–286 should auto-close. #287 dismissed manually.
