---
quick_id: 260525-krs
slug: tool-results-ui
date: 2026-05-25
status: in-progress
---

# Quick Task: Per-tool UI components for chat tool results

## Problem

`apps/web/features/chat/components/conversation-pane.tsx:124-139` renders any tool that is NOT a write-action body slot as a generic AI Elements `<Tool>` card dumping raw JSON in `<ToolInput>` and `<ToolOutput>`. That's 15 of our 24 chat tools:

- **Read (8):** `searchInbox`, `getMessage`, `listLabels`, `getThread`, `getRule`, `listRules`, `getSenderSafetyEntry`, `searchMemories`
- **WriteReversible (7):** `applyLabel`, `removeLabel`, `archiveThread`, `updateRule`, `disableRule`, `saveDraft`, `addToKnowledgeBase`

Compare inbox-zero (`apps/web/components/assistant-chat/tools.tsx` + `message-part.tsx`) which has a dedicated React component per tool, routed via a switch on `part.type === "tool-<name>"`. Each component renders a `SubtleToolCollapsible` with structured rows / email list / rule cards instead of JSON.

## Fix

New file: `apps/web/features/chat/components/tool-results.tsx`

Ports the inbox-zero pattern to Zero Mail's tool catalog:

**Shared helpers** (mirroring inbox-zero `tools.tsx`):
- `SubtleToolCollapsible` — `<Collapsible>` wrapper with chevron + title
- `ToolDetailRow` — fixed-width label + value
- `EmailRow` — single email line (subject / from / date / unread)
- `getOutputField<T>` / `getInputField<T>` — safe JSON field extraction
- `formatRelativeDate` — short relative date for timestamps

**Per-tool components** (15 total):
- Read: `SearchInboxResult`, `GetMessageResult`, `ListLabelsResult`, `GetThreadResult`, `GetRuleResult`, `ListRulesResult`, `GetSenderSafetyEntryResult`, `SearchMemoriesResult`
- WriteReversible: `ApplyLabelResult`, `RemoveLabelResult`, `ArchiveThreadResult`, `UpdateRuleResult`, `DisableRuleResult`, `SaveDraftResult`, `AddToKnowledgeBaseResult`

Each component uses the actual backend output shape (e.g., `SearchInboxOutput.messages[].subject` etc., grepped from `backend/.../tools/*ToolHandler.java`).

**Dispatcher** — exported function `renderToolResult({ toolName, input, output, state, errorText })` that switches on `toolName` and returns the matching component, or `null` for unknown tools (caller renders a fallback).

`conversation-pane.tsx`:
- Replace the generic `<Tool>` fallback block with a call to `renderToolResult(...)`.
- If the dispatcher returns null, keep the existing AI Elements `<Tool>` JSON fallback (defense in depth for new backend tools).

## Verification

- `pnpm --filter web typecheck` — pass.
- `pnpm --filter web lint` — pass.
- Existing Playwright tests should still pass; defer to CI.

## Out of scope

- Inline email card with one-click archive/mark-read (inbox-zero `inline-email-card.tsx`) — that's a separate UX investment requiring backend write-action wiring per email row. Keep tool-result email rows display-only for this quick task.
- Backend tool output changes — read backend records as-is.
- I18n strings — use VI as the default labels (consistent with our existing `apps/web/features/chat` strings).
