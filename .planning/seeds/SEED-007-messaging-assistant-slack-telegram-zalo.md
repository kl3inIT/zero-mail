---
id: SEED-007
status: dormant
planted: 2026-05-14
planted_during: Phase 6 launch-readiness discussion
trigger_when: "when planning assistant access outside the web app or Vietnam-market integrations"
scope: medium
---

# SEED-007: Messaging Assistant via Slack, Telegram, and Zalo

## Why This Matters

Inbox Zero highlights Slack and Telegram integration so users can chat with the AI assistant without leaving their existing tools. For Zero Mail's Vietnam context, Zalo should be evaluated as a local-market equivalent or complement. This is especially useful for founders who live in chat apps more than dashboards.

This is a product distribution feature as much as a technical integration.

## When to Surface

**Trigger:** when planning assistant access outside the web app or Vietnam-market integrations.

## Scope Estimate

**Medium** for notification-only and command-lite flows. **Large** for full assistant parity across channels.

## Candidate Product Shape

- Slack bot: notify high-priority mail, daily digest, rule actions needing review, draft-ready alerts.
- Telegram bot: mobile-first assistant commands for founders.
- Zalo OA integration: Vietnam-market notifications and lightweight commands through Zalo Official Account.
- Command examples: "pause triage", "show urgent mail", "draft reply", "mark done", "top up credits", "open this thread".
- Proactive updates: scheduled check-ins, important-sender alerts, digest delivery.
- Deep links back to Zero Mail web for sensitive review/confirmation.

## Safety Rules

- Do not send raw email body into chat channels by default.
- Use snippets/metadata plus secure deep links unless the user explicitly enables body previews.
- All write actions require confirmation or redirect to app for approval.
- Store channel credentials separately from Gmail credentials.
- Treat Zalo OA rate limits, webhook signatures/MAC validation, and verified OA requirements as research gates.

## Breadcrumbs

- Inbox Zero README Slack & Telegram feature: `D:/study-materials-summer-2026/EXE202/inbox-zero/README.md`
- Inbox Zero local connected apps UI: `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/app/(app)/[emailAccountId]/settings/ConnectedAppsSection.tsx`
- Zalo OA OpenAPI: https://oa.zaloapp.com/home/function/extension?type=open-api
- Zalo OA API/webhook research from 2026-05-14 discussion.

## Notes

Zalo is not in Inbox Zero's README feature list and could be a local differentiation play for Vietnamese users.
