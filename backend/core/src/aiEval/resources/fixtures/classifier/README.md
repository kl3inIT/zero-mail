# Classifier Eval Fixtures

Synthetic held-out reply-status fixtures for Phase 5B eval dimension 7.

Rules:

- Fixtures are synthetic only. Do not commit real Gmail subjects, addresses, snippets, bodies, prompts, completions, or token bytes.
- The deterministic classifier score is gated by `./gradlew :backend:core:aiEval -PdeterministicOnly`.
- Labels are for the v1 event-bounded heuristic: Zero Mail classifies only already-observed thread state and never enumerates the mailbox.

Heuristic blind spots included:

- `classifier-06-multi-participant-counterparty` probes multi-participant threads where the user still owes a reply.
- `classifier-07-auto-reply-last-message` probes vacation/auto-reply messages that should not flip to awaiting.
- `classifier-08-sent-label-lag` probes observed self-authored mail before the SENT label is visible.
- `classifier-09-group-several-parties` probes group threads where several counterparties are waiting on the user.
- `classifier-10-dsn-bounce` probes delivery-status/bounce-style messages that can look like sent mail.
- `classifier-20-clarification-answered` probes the "clarifying answer received, user still owns deliverable" residue.
- `classifier-21-owner-took-action` probes the user taking ownership without promising another message.

Fixture fields:

- `expectedBucket`: `TO_REPLY` or `AWAITING_THEIR_REPLY`.
- `lastMessageFromTenant`: whether the latest observed message is authored by the tenant.
- `threadHasSentLabel`: whether the latest observed thread state carries Gmail `SENT`.
- `hasZeroMailDraft`: whether Zero Mail has a draft recorded on the thread.
- `lastMessageIsAutoReply`: vacation responder / DSN-style auto-reply marker.
- `edgeCase`: whether the fixture belongs to the non-trivial edge-case subset above.
