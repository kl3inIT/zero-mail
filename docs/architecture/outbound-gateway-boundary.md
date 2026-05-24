# Outbound Gateway Boundary

**Status:** Phase 08.1 contract  
**Last updated:** 2026-05-24

## Purpose

Zero Mail allows rule-triggered outbound actions after Phase 08.1, but actual Gmail sends must stay behind one shared boundary. The boundary exists so chat confirmations and rule automation share the same safety, idempotency, tenant, audit, and logging behavior.

## User-Facing Contract

- Rules can produce `send_reply`, `forward_email`, and `send_email` actions.
- One global `Auto-send rules` setting controls automated outbound rule sends.
- The global setting defaults ON for new users/tenants.
- There are no individual outbound action toggles.
- There is no per-rule acknowledgement checkbox or modal.
- Rule review/list UI may show `Will auto-send` copy or badges, but that copy must not block saving.
- If the global setting is OFF or any runtime gate fails, the runtime saves a Gmail draft instead of sending.

## Runtime Gate Contract

Outbound rule sends execute only when all of these pass:

- global `Auto-send rules` setting,
- sender-risk/static-example guard,
- sender safety net,
- daily/rate cap,
- idempotency key,
- OAuth scope check,
- tenant context check,
- audit reservation.

Any gate failure must produce an auditable fallback result. The fallback is Gmail `save_draft`; a separate review inbox is out of scope for Phase 08.1.

## Code Boundary

The shared API should be domain-neutral, for example:

- `com.zeromail.core.outbound.usecases.OutboundSendGateway`
- `OutboundSendCommand`
- `OutboundSendResult`

Allowed callers:

- chat confirmation flow after explicit user click,
- rule/triage runtime after all automated outbound gates pass.

Forbidden direct callers:

- API controllers,
- admin packages,
- worker orchestration,
- rule compiler,
- rule management,
- triage orchestration outside the gateway call,
- frontend-generated DTO or schema code.

## Gmail API Ownership

The only production class allowed to call `gmail.users().messages().send(...)` or Gmail draft-send equivalents is the shared outbound gateway implementation: `com.zeromail.core.outbound.usecases.GmailOutboundSendGateway`.

Architecture tests must reject any additional direct Gmail send call site outside:

- the shared outbound gateway package, and
- classes annotated with `@AllowedSendCallSite`.

## Privacy Contract

Do not persist Gmail-read email bodies, snippets, prompts, completions, or embeddings as part of outbound automation. Persisted draft/send action arguments are allowed only when they are user-authored or rule-action draft data under the existing draft-body carve-out.

Logs must use event names, tenant IDs, audit IDs, hashes, and bounded enum reasons. Logs must not include recipient body text, Gmail-read bodies, model prompts/completions, or draft body content.

## Verification

Required gates:

- ArchUnit/grep: exactly one direct Gmail send call site.
- ArchUnit: forbidden packages cannot call Gmail send directly.
- Runtime tests: global setting OFF falls back to draft.
- Runtime tests: sender safety net and low-trust/static guards fall back to draft.
- Privacy tests: Gmail-read content remains banned from persisted chat/rule/audit content.
- UAT: controlled Gmail test account proves send and fallback behavior.
