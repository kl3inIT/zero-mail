---
quick_id: 260525-krs
slug: tool-results-ui
date: 2026-05-25
status: complete
---

# Summary: Per-tool UI components for chat tool results

## What changed

New file: `apps/web/features/chat/components/tool-results.tsx` (~420 lines).
Modified: `apps/web/features/chat/components/conversation-pane.tsx`.

Replaces the generic AI Elements `<Tool>` JSON dump for 15 of 24 chat tools (the 8 read + 7 writeReversible tools — write-confirm tools keep using `<PreviewCard>`).

**Shared helpers (ported from inbox-zero `tools.tsx`):**
- `SubtleToolCollapsible` — chevron + title collapsible wrapper.
- `ToolDetailRow` — fixed-width label + value row.
- `EmailRowsList` — deduped (by threadId) email row list with subject / from / snippet / date / unread badge.
- `formatRelativeDate` — Vietnamese relative date.
- `getField` / `asString` / `asArray` / `asBool` — safe field extraction.

**Per-tool components (15):**

Read (8): `SearchInboxResult`, `GetMessageResult`, `ListLabelsResult`, `GetThreadResult`, `GetRuleResult`, `ListRulesResult`, `GetSenderSafetyEntryResult`, `SearchMemoriesResult`.

WriteReversible (7): `ApplyLabelResult`, `RemoveLabelResult`, `ArchiveThreadResult`, `UpdateRuleResult`, `DisableRuleResult`, `SaveDraftResult`, `AddToKnowledgeBaseResult`.

Each maps to the actual backend output record (e.g., `SearchInboxOutput.messages[].subject/from/date/...` verified via `backend/.../tools/SearchInboxToolHandler.java`).

**Dispatcher:**
- `renderToolResult({ toolName, input, output })` — switch on toolName, returns matching component or `null`.
- `hasToolResultRenderer(toolName)` — predicate used by `conversation-pane.tsx` to decide whether to use the dispatcher or fall back to the existing AI Elements `<Tool>` JSON card.

The JSON fallback stays as defensive UX so newly added backend tools render something rather than nothing.

## Verification

- `pnpm --filter web typecheck` — pass.
- `pnpm --filter web lint` — pass (one unused-disable warning in `coverage/`, not touched).
- `pnpm --filter web test -- __tests__/chat/tool-catalog-contract.test.ts --run` — 51 test files / 265 tests pass.
- Manual browser verification deferred to user.

## Out of scope

- Inline email card with one-click archive/mark-read (inbox-zero pattern that wires write actions per email row). Email rows here are display-only.
- I18n keys for new tool-result strings — kept Vietnamese inline; can be hoisted to `apps/web/features/chat/messages.ts` later if EN parity becomes required.
- New backend tools — read existing tool record shapes as-is.
