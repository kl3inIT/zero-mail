---
id: SEED-003
status: dormant
planted: 2026-05-14
planted_during: Phase 6 launch-readiness discussion
trigger_when: "when planning chat UI, assistant UX, command palette, or rule-management improvements"
scope: medium
---

# SEED-003: Screen-Aware AI Assistant and Command Center

## Why This Matters

Shortwave's AI assistant is context-aware: users can refer to "this email", "these contacts", selected threads, or visible search results, and the assistant adapts to what is on screen. Inbox Zero has an AI personal assistant and assistant chat for rule/action workflows, but Zero Mail can design this more cleanly from the beginning around explicit command previews, confirmations, and privacy boundaries.

This is a strong Milestone 1.1 candidate because it can improve UX without necessarily changing Google scopes or long-term data retention.

## When to Surface

**Trigger:** when planning chat UI, assistant UX, command palette, or rule-management improvements.

## Scope Estimate

**Medium** for a v1.1 assistant that operates on current screen/thread/rule state. **Large** if it becomes a full mailbox agent with persistent memory.

## Candidate Product Shape

- Right-side AI assistant panel in protected app shell.
- Screen-aware context packs: current route, selected thread/audit row/rule/analytics window.
- Assistant-created rule drafts with explicit user confirmation.
- Assistant explanations for why a rule matched or why triage was skipped.
- "Organize my inbox" suggestions that generate a reviewable action plan, not silent writes.
- Command palette for common actions: pause, undo, create rule, draft reply, top up, open analytics, reconnect Gmail.
- Saved prompts/snippets for common user workflows.

## Safety Rules

- Never let chat directly execute destructive actions without a structured preview and confirmation.
- Keep all Gmail writes inside existing allow-listed action services.
- Do not log assistant prompt/completion content.
- Prefer short-lived context packs over persistent memory for v1.1.

## Breadcrumbs

- Shortwave AI assistant docs: https://www.shortwave.com/docs/guides/ai-assistant/
- Shortwave keyboard shortcuts / command palette: https://www.shortwave.com/docs/references/shortcuts/
- Inbox Zero local reference: `D:/study-materials-summer-2026/EXE202/inbox-zero/ARCHITECTURE.md` section "AI Personal Assistant".
- Inbox Zero local assistant actions: `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/actions/assistant-chat*.ts`

## Notes

This should be separated from full mailbox search. A useful assistant can ship while preserving the current Phase 6 privacy story.
