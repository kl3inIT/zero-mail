---
id: SEED-010
status: dormant
planted: 2026-05-14
planted_during: Phase 6 launch-readiness discussion
trigger_when: "when planning founder/sales workflows, CRM integration, or outbound email productivity"
scope: medium
---

# SEED-010: Sales Engagement, CRM Sync, Read Receipts, and Link Tracking

## Why This Matters

Shortwave includes sales-oriented productivity features such as read receipts, link tracking, recent opens, CRM BCC integration, follow-up reminders, and shared thread links. Inbox Zero's public README does not emphasize this sales-engagement layer.

For founders and operators, knowing when a prospect/investor/customer opened an email and having follow-up reminders can be more valuable than generic inbox cleanup.

## When to Surface

**Trigger:** when planning founder/sales workflows, CRM integration, or outbound email productivity.

## Scope Estimate

**Medium** for CRM BCC and follow-up reminders. **Large** for reliable read/link tracking with privacy controls and deliverability safeguards.

## Candidate Product Shape

- Automatic BCC to CRM addresses for outgoing customer mail.
- Follow-up reminders if no reply by a chosen date.
- Recent opens feed for sent mail, if tracking is enabled.
- Link click tracking for user-created links, if tracking is enabled.
- Shared thread links to CRM/support tools.
- Investor/customer mode dashboard: waiting replies, hot threads, stale follow-ups.

## Product and Privacy Rules

- Tracking pixels/link tracking must be explicit and user-controlled.
- Do not enable tracking by default.
- Make recipient privacy and deliverability risks visible.
- Do not mix this into the trust-first Phase 6 story.

## Breadcrumbs

- Shortwave CRM integration docs: https://www.shortwave.com/docs/how-tos/crm-integration/
- Shortwave homepage "read receipts", "link tracking", "recent opens": https://www.shortwave.com/
- Inbox Zero README does not foreground sales engagement features.

## Notes

This is a possible premium feature set, but should be handled carefully because tracking features can damage trust if presented poorly.
