---
status: resolved
trigger: "Inbox UI has double scrollbars, Gmail images are wrong, read/unread does not sync with Gmail, reply/reply all/forward/generate actions are missing, rules/analytics/unsubscribe are not consistently based on the 100-message Gmail working set."
created: 2026-05-24
updated: 2026-05-24
---

# Debug Session: Inbox Working Set Regressions

## Symptoms

- Expected behavior: Inbox uses one full-page scrollbar, no sticky message header, Gmail-rendered images appear correctly, read/unread state stays aligned with Gmail, and message detail exposes reply/reply all/forward/generate actions.
- Expected behavior: The app lazy-loads the inbox UI in pages while all dependent workflows use the same latest-100 Gmail working set.
- Expected behavior: Rule preview must not apply labels; labels are applied only by an explicit apply action, and analytics/unsubscribe screens should produce results from the same 100-message base when available.
- Actual behavior: UI shows double scrollbars, images are not rendered like Gmail, read status differs between Gmail and the app, actions are missing, labels appear before an explicit test/apply expectation, analytics is empty after 3/100 rule matches, and unsubscribe is empty.
- Error messages: None supplied for this report.
- Reproduction: Connect Gmail, open `/inbox`, inspect message list/detail, open/read messages, compare Gmail state, run rule test against the 100 samples, inspect analytics and unsubscribe.

## Current Focus

- hypothesis: The remaining UX gap is local to the inbox detail UI: list lazy loading needs a dedicated inbox scrollbar, while Reply/Reply all/Forward still navigate to chat instead of opening an inline Gmail-style composer.
- test: Compare the Zero reference composer flow, then update the inbox component, messages, and e2e assertions.
- expecting: The message list scroll container fetches the next 20 messages, and each action opens an inline composer with recipients/subject initialized like the Zero reference without creating a new unsafe send call site.
- next_action: implement inline composer and update regression tests
- reasoning_checkpoint:
- tdd_checkpoint:

## Evidence

- Inbox UI used `lg:h-[calc(100vh-170px)]`, a scrollable detail section, and a fixed-height iframe, producing nested scrollbars and making the message header feel pinned.
- Inbox TanStack queries kept pages fresh for 10 minutes and did not refetch on mount/focus, so Gmail read/unread changes made elsewhere could remain stale in the UI.
- Inline Gmail images only used raw string replacement for `cid:<contentId>` and one URL-encoded variant. Encoded/bracketed Content-ID values were easy to miss.
- Rule preview displayed `INBOX` as a Gmail label, which made preview rows look already modified even before the explicit apply action.
- Rule test apply wrote Gmail labels but did not write `triage_audit` rows, so analytics rule hits/applied counts stayed empty after a successful manual label apply.
- Cleanup unsubscribe candidate query was DB-only against `mail_message_observed`; when Gmail had recent List-Unsubscribe headers but the observed table was empty/stale, cleanup showed no candidates.
- The Zero reference opens an inline `ReplyCompose` from `reply` / `replyAll` / `forward` actions and delegates actual send to its `EmailComposer`; Zero Mail must keep the same visible interaction while preserving its stricter confirmed-send boundary.
- Existing Zero Mail confirmed-send tool schemas for `replyEmail` / `forwardEmail` lacked `subject`, `cc`, and `gmailThreadId` fields even though `ConfirmedSendToolHandlers` consumes them. Without the schema fix, an inline preview could be generated with insufficient send-executor input.

## Eliminated

## Resolution

- root_cause: the recent-100 Gmail working set was implemented for the inbox/rule preview path, but downstream screens still depended on stale client cache or DB-only audit/observed rows.
- fix: inbox now uses one page scroll with a dedicated message-list scroll container for lazy-load, refetches on mount/focus while keeping query cache, auto mark-read is idempotent per selected message, Gmail `cid:` images are rewritten via parsed HTML, rule preview hides system labels, rule test label applies write APPLIED triage audit rows, analytics/cleanup/inbox caches invalidate after label apply, unsubscribe candidates prefer the latest 100 Gmail inbox messages before DB fallback, and Reply/Reply all/Forward/Generate now open an inline Inbox composer modeled after Zero's flow. Confirmed-send schemas now expose `subject`, `cc`, and `gmailThreadId` for reply/forward preview safety.
- verification: `pnpm --filter web i18n:build`; `pnpm --filter web typecheck`; `pnpm --filter web lint`; `pnpm --filter web i18n:check`; `pnpm --filter web test:e2e e2e/inbox.spec.ts --reporter=list`; `pnpm --filter web test:e2e e2e/analytics.spec.ts --reporter=list`; `pnpm --filter web test:e2e e2e/needs-reply.spec.ts --reporter=list`; `pnpm --filter web test:e2e e2e/cleanup-unsubscribe-campaign.spec.ts --reporter=list`; chat confirmation Playwright specs: `stream-happy-path`, `confirmation-race`, `confirmation-replay`, `outside-source-thread`, `vip-banner`, `csrf-parity`; `./gradlew.bat :backend:core:test --tests com.zeromail.core.chat.llm.springai.ToolCallbackTranslatorTest --tests com.zeromail.core.arch.OnlyOneGmailSendCallSiteTest --tests com.zeromail.core.arch.GmailWriteBoundaryTest --tests com.zeromail.core.chat.sanitize.ToolOutputSanitizerTest --tests com.zeromail.core.arch.ChatPersistenceContentBanTest`; `./gradlew.bat :backend:core:test --tests com.zeromail.core.cleanup.UnsubscribeMailtoSenderRecipientGuardTest`; `./gradlew.bat :backend:core:compileJava :backend:api:compileJava`; JetBrains build of touched Java files.
- files_changed: inbox UI/hook/messages/e2e, chat confirmed-send arg schemas, rules preview/hooks, Gmail recent read, Gmail preview working-set DTO, rule test apply/audit writer/repository, cleanup candidate query/tests, generated i18n messages.
