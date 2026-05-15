---
id: SEED-009
status: dormant
planted: 2026-05-14
planted_during: Phase 6 launch-readiness discussion
trigger_when: "when planning cleanup, unsubscribe, cold-email defense, or attachment workflows"
scope: medium
---

# SEED-009: Bulk Cleanup, Cold Email Blocker, and Smart Filing

## Why This Matters

Inbox Zero has practical cleanup features that are easy for users to understand: bulk unsubscriber, bulk archiver, cold email blocker, analytics, and smart filing of attachments. These features produce immediate visible value without requiring a full Shortwave-style mailbox rewrite.

Zero Mail's v1 triage engine can become the foundation for these workflows.

## When to Surface

**Trigger:** when planning cleanup, unsubscribe, cold-email defense, or attachment workflows.

## Scope Estimate

**Medium** if built on existing Gmail labels, metadata, and triage audit. **Large** if adding Drive/OneDrive attachment filing.

## Candidate Product Shape

- Bulk unsubscriber: identify senders the user never opens/replies to, unsubscribe or archive.
- Bulk archiver: clean old low-value mail by sender/category/date.
- Cold email blocker: classify first-time senders and route obvious outreach away from inbox.
- Attachment filing: save invoices/contracts/receipts to Drive later, only after a separate scope decision.
- Cleanup campaign UI: preview counts, sample messages, reversible plan, dry-run first.
- Suppression list: never touch protected senders/domains.

## Safety Rules

- Always show sample messages before bulk action.
- Never permanently delete in early versions.
- Prefer label/archive over destructive actions.
- Keep unsubscribe action conservative; some unsubscribe links are tracking or unsafe.
- Any Drive/OneDrive integration is a separate scope/provider decision.

## Breadcrumbs

- Inbox Zero README cleanup features: `D:/study-materials-summer-2026/EXE202/inbox-zero/README.md`
- Inbox Zero architecture bulk unsubscriber / cold email blocker sections: `D:/study-materials-summer-2026/EXE202/inbox-zero/ARCHITECTURE.md`
- Shortwave homepage block/unsubscribe and delivery controls: https://www.shortwave.com/

## Notes

This is a strong post-Phase-6 practical roadmap track because it extends current triage/audit capabilities without requiring mailbox embeddings.
