---
id: SEED-004
status: dormant
planted: 2026-05-14
planted_during: Phase 6 launch-readiness discussion
trigger_when: "when planning inbox UX beyond triage audit and analytics"
scope: medium
---

# SEED-004: Inbox Splits, Bundles, Todos, Snooze, and Delivery Schedules

## Why This Matters

Shortwave's product strength is not only AI. It gives users inbox primitives that reduce cognitive load: split inboxes, sender/label bundles, email-to-todo, snooze, delivery schedules, keyboard-first triage, saved searches, and granular push controls. These are durable productivity features that make AI automation feel controllable.

Inbox Zero has Reply Zero, bulk cleanup, analytics, and AI assistant features, but Shortwave's split/bundle/delivery-schedule model is a richer inbox UX direction.

## When to Surface

**Trigger:** when planning inbox UX beyond triage audit and analytics.

## Scope Estimate

**Medium** if implemented as UI over existing metadata and Gmail labels. **Large** if it becomes a full replacement email client.

## Candidate Product Shape

- Splits: focused tabs based on labels, senders, rule results, or saved queries.
- Bundles: group newsletters/promotions/receipts by sender or label for bulk action.
- Delivery schedules: hold non-urgent categories until chosen times.
- Email-to-todo: convert selected threads into task rows with due date and priority.
- Snooze: defer threads until a natural-language time.
- Keyboard-first triage: shortcuts for archive, undo, draft, open assistant, pause.
- Saved searches in sidebar.
- Granular notifications: only alert for high-priority splits or protected senders.

## Compliance and Architecture Notes

- Prefer metadata/label-backed UX first.
- Avoid full body indexing unless SEED-002 is intentionally accepted.
- Delivery schedules may require Gmail label/archive behavior plus worker jobs to re-surface mail.
- Todo state is app-owned and should not require new Google scopes.

## Breadcrumbs

- Shortwave customization docs: https://www.shortwave.com/docs/guides/customize-your-shortwave-settings/
- Shortwave method docs: https://www.shortwave.com/docs/guides/method/
- Shortwave homepage email productivity section: https://www.shortwave.com/
- Inbox Zero README: `D:/study-materials-summer-2026/EXE202/inbox-zero/README.md`

## Notes

This is a strong differentiation track that does not necessarily complicate CASA if implemented with metadata and labels.
