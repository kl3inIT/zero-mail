# Phase 08.1 Rule Actions UAT

This runbook verifies Inbox Zero-style rule examples, expanded rule actions, admin-managed
examples, the global `Auto-send rules` toggle, fallback-to-draft behavior, and the shared outbound
Gmail send boundary.

## Safety Setup

- Use a controlled Gmail test tenant only.
- Use safe recipient addresses that the tester owns.
- Do not run real-send checks against a personal inbox or unapproved recipients.
- Keep application logs open during the test and confirm they contain IDs, hashes, and enum reasons,
  not raw recipients, Gmail bodies, prompts, completions, or draft body text.

## Local Commands

```powershell
./gradlew :backend:core:test --tests "*AssistantSend*" --tests "*Outbound*" --tests "*Triage*" --tests "*Arch*" --tests "*Privacy*"
./gradlew :backend:api:test
pnpm --filter @zeromail/web typecheck
pnpm --filter @zeromail/web e2e -- --grep "rules examples"
rg -n "gmail\\.users\\(\\)\\.messages\\(\\)\\.send|messages\\(\\)\\.send\\(" backend/core/src/main/java
```

Expected grep result: exactly one production call site, in
`backend/core/src/main/java/com/zeromail/core/outbound/usecases/GmailOutboundSendGateway.java`.

## Automated Gate Checklist

1. Run `OutboundGmailSendBoundaryTest`, `OnlyOneGmailSendCallSiteTest`, and
   `NoGmailSendAllowedTest`.
2. Confirm direct Gmail send is allowed only in the shared outbound gateway package.
3. Confirm admin, API controller, rule compiler, rule runtime, worker, and triage packages cannot
   call Gmail send directly.
4. Run `TriageOutboundRuntimeGateTest`.
5. Confirm `AUTO_SEND_DISABLED`, sender safety-net, low-trust/static sender, OAuth/send failure,
   idempotency skip, and tenant mismatch cases are covered.
6. Run privacy tests and confirm Gmail-read content remains banned while user-authored draft/send
   action data is allowed.

## Browser Smoke

1. Sign in to the test tenant.
2. Open Rules.
3. Click `Choose from examples`.
4. Select a persona such as Founder or Developer.
5. Confirm localized examples render below the prompt box.
6. Click an example and confirm it replaces the prompt text.
7. Convert to a structured rule.
8. Confirm review/edit shows expanded actions such as `send_reply`, `forward_email`, or
   `send_email`.
9. Save the rule.
10. Open Settings and confirm `Auto-send rules` is ON by default.

## Admin Smoke

1. Open the admin rule catalog.
2. Confirm personas and examples are loaded from DB, not hard-coded frontend chips.
3. Edit English and Vietnamese example text.
4. Disable one example and confirm it disappears from the user example list.
5. Re-enable it and confirm display order is respected.

## Controlled Gmail UAT

1. Start API, worker, and web with the controlled Gmail test tenant.
2. Confirm the test Gmail account has draft and send scopes.
3. Confirm `Auto-send rules` is ON.
4. Create a sender-anchored outbound rule, for example sender domain equals the safe test sender and
   action is `send_reply`.
5. Send a matching inbound test email from the safe sender.
6. Confirm Gmail Sent contains exactly one sent reply.
7. Confirm triage audit records an applied outbound action through the gateway.
8. Turn `Auto-send rules` OFF.
9. Send another matching inbound test email.
10. Confirm no sent email is created.
11. Confirm a Gmail draft exists and audit reason includes `AUTO_SEND_DISABLED`.
12. Add the sender to the safety-net/protected list.
13. Turn `Auto-send rules` ON.
14. Send another matching inbound test email.
15. Confirm no sent email is created and a Gmail draft is saved.
16. Confirm audit reason includes the sender safety-net fallback.
17. Create an outbound rule without an email/domain sender condition, for example subject-only.
18. Send a matching inbound test email.
19. Confirm no sent email is created and a Gmail draft is saved.
20. Confirm audit reason includes low-trust/static sender fallback.

## Pass Criteria

- Expanded actions are available in rule authoring and persist as structured `rules.v1` JSON.
- Global `Auto-send rules` defaults ON.
- `AUTO_SEND_DISABLED` creates a Gmail draft and does not send.
- Sender safety-net and low-trust/static sender gates create Gmail drafts and do not send.
- Tenant mismatch fails before any Gmail send or draft call.
- Exactly one direct Gmail send call site exists.
- No logs or persisted audit fields contain Gmail-read body/snippet/prompt/completion content.
