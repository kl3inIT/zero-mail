---
slug: fix-codeql-alerts
date: 2026-05-26
status: complete
---

# Summary — Close all open CodeQL alerts

## Outcome

| Alert(s) | Verdict | Resolution |
|---|---|---|
| 59 × `java/local-variable-is-never-read` (#223–#281) | False positive (sealed switch binding) | Replaced `case T ignored ->` with `case T _ ->` (Java 25 unnamed pattern). Will auto-close on next CodeQL scan. |
| #284 `java/unused-parameter` | Real | Dropped `baseUrl` param of `PlatformLlmRuntimeRouter.fallbackKeyFormat`. Will auto-close. |
| #285, #286 `java/log-injection` (medium) | Defensive fix | Added `stripCrlf` helper in `TriageGmailWriter` and `CampaignRetryService`; applied to dynamic args (`gmailMessageId`, `gmailThreadId`, `senderDomain`). Will auto-close. |
| #287 `java/missing-case-in-switch` | False positive | Dismissed via gh api — switch is exhaustive over all 4 `UnsubscribeAttemptState` values via comma-separated case labels; CodeQL appears not to recognize this Java 21+ pattern. |

Dependabot: 8 alerts, all already FIXED before this session. Secret scanning: 0 alerts.

## Commit

`6ed0ba71` (rebased onto `98bd4055`) — chore(security): close 62 CodeQL alerts (sealed-switch _, CRLF log sanitize, unused param). Pushed to `origin/main`.

## Verification

- `./gradlew :backend:core:compileJava :backend:worker:compileJava` → BUILD SUCCESSFUL
- `./gradlew :backend:core:test :backend:worker:test` → BUILD SUCCESSFUL
- spotless reformat ran cleanly as part of pre-commit lint-staged.
