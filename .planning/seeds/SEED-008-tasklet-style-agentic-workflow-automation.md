---
id: SEED-008
status: dormant
planted: 2026-05-14
planted_during: Phase 6 launch-readiness discussion
trigger_when: "when planning autonomous workflows, webhooks, MCP, or third-party SaaS integrations"
scope: large
---

# SEED-008: Tasklet-Style Agentic Workflow Automation

## Why This Matters

Shortwave's Tasklet direction is bigger than email: background agents triggered by new email, schedules, labels, or webhooks; actions across Slack, Notion, Google Drive, CRMs, databases, and custom APIs; plain-English automation setup.

Inbox Zero has API/webhook and assistant concepts, but a Tasklet-style automation engine could become a major platform direction for Zero Mail if we decide to move beyond inbox triage.

## When to Surface

**Trigger:** when planning autonomous workflows, webhooks, MCP, or third-party SaaS integrations.

## Scope Estimate

**Large**. This is likely a separate product surface or a major milestone.

## Candidate Product Shape

- Automation builder in plain English.
- Triggers: new email, label applied, scheduled time, webhook, manual command.
- Actions: draft reply, add internal comment, create todo, send Slack/Zalo notification, save attachment, update CRM, call webhook.
- MCP connector registry for Notion, Slack, Asana, Linear, HubSpot, GitHub, Google Drive.
- Approval policies: always review, auto-run safe action, never auto-send.
- Run history with step-by-step evidence and rollback where possible.

## Security Rules

- Build SSRF-safe webhook URL validation from day one.
- Per-connector scopes and secret storage.
- Rate limits and spend caps per tenant.
- Explicit tool allow-list per automation.
- Human approval required for high-impact actions.

## Breadcrumbs

- Shortwave Tasklet intro: https://www.shortwave.com/blog/introducing-tasklet-ai-automation/
- Shortwave + Tasklet integration: https://www.shortwave.com/blog/shortwave-tasklet-integration/
- Shortwave AI assistant Tasklet section: https://www.shortwave.com/docs/guides/ai-assistant/
- Inbox Zero local webhook validation references: `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/webhook-validation.ts`

## Notes

This is a "do not accidentally build it inside rules" seed. If accepted, it needs a first-class architecture.
