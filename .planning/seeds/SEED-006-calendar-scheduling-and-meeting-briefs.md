---
id: SEED-006
status: dormant
planted: 2026-05-14
planted_during: Phase 6 launch-readiness discussion
trigger_when: "when planning Google Calendar, meeting briefs, scheduling replies, or calendar-aware triage"
scope: medium
---

# SEED-006: Calendar Scheduling and Meeting Briefs

## Why This Matters

Both Inbox Zero and Shortwave treat calendar as a natural extension of email. Inbox Zero lists meeting briefs that pull context from email and calendar. Shortwave goes further: AI can check availability, create events, write scheduling emails, extract events from emails, and surface calendar context inside the assistant.

Zero Mail should not request Calendar scopes in Phase 6 unless the feature exists, but calendar-aware email workflows are high-value for founders and busy professionals.

## When to Surface

**Trigger:** when planning Google Calendar, meeting briefs, scheduling replies, or calendar-aware triage.

## Scope Estimate

**Medium** for free/busy and meeting briefs. **Large** for full event creation/editing and assistant scheduling.

## Candidate Product Shape

- Optional "Connect Google Calendar" via incremental OAuth.
- Free/busy only mode for scheduling reply drafts.
- Meeting briefs before external meetings: attendees, recent threads, open action items, relevant attachments.
- "Schedule with AI" from dates/times highlighted in an email.
- Draft scheduling replies with proposed times.
- Create calendar holds/invites from email content after user confirmation.
- Calendar-aware triage: cancellations/reschedules always stay visible.

## Scope Strategy

- Do not add Calendar scopes to v1 Phase 6.
- Start with the narrowest feasible scope, likely free/busy for availability.
- Add event read/create scopes only when the UI flow exists and Google verification materials are updated.
- Use incremental authorization so only users who enable calendar features grant calendar access.

## Breadcrumbs

- Shortwave AI assistant calendar docs: https://www.shortwave.com/docs/guides/ai-assistant/
- Inbox Zero README meeting briefs: `D:/study-materials-summer-2026/EXE202/inbox-zero/README.md`
- Prior discussion on 2026-05-14: adding Calendar later requires scope verification for the new scope and user re-consent via incremental authorization.

## Notes

This is likely a strong Milestone 1.1 or 1.2 feature if the team wants visible user value after CASA submission.
