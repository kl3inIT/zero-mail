---
status: completed
completed: 2026-05-24
---

# Summary

Implemented cleanup unsubscribe UX wording and recent-inbox working-set alignment.

## Changes

- Reworded cleanup unsubscribe UI away from "campaign", "RFC 8058", and "suppression" language.
- Moved sender skip/protection out of the primary row action so "Preview" remains the main action.
- Defaulted cleanup candidate filtering to actionable senders.
- Added shared cleanup recent Inbox working-set service and reused it for candidate list, preview, and worker execution.
- Hardened cleanup privacy by removing Gmail message IDs from undo logs/exceptions and avoiding raw unsubscribe URI parse details in exceptions.
- Made React Query Devtools opt-in via `NEXT_PUBLIC_ENABLE_QUERY_DEVTOOLS=true` to reduce Next dev/e2e client-navigation stalls and large dev chunks.

## Verification

- `pnpm --dir apps/web test:e2e cleanup-unsubscribe-campaign.spec.ts --reporter=list`
- `pnpm --dir apps/web typecheck`
- `pnpm --dir apps/web i18n:check`
- `./gradlew.bat :backend:core:test --tests "com.zeromail.core.cleanup.http.UnsubscribeHttpClientTest" --tests "com.zeromail.core.cleanup.UnsubscribeMailtoSenderRecipientGuardTest" --tests "com.zeromail.core.cleanup.usecases.CandidateQueryServiceTest" --tests "com.zeromail.core.cleanup.usecases.CampaignPreviewServiceTest" --tests "com.zeromail.core.cleanup.usecases.CampaignUndoServiceTest"`
- `./gradlew.bat :backend:worker:compileJava`
- `./gradlew.bat :backend:core:test --tests "com.zeromail.core.cleanup.CleanupPrivacySweepTest" --tests "com.zeromail.core.arch.UnsubscribeHttpClientBoundaryTest" --tests "com.zeromail.core.arch.GmailWriteBoundaryTest" --tests "com.zeromail.core.arch.OnlyOneGmailSendCallSiteTest" --tests "com.zeromail.core.arch.ChatPersistenceContentBanTest"`

