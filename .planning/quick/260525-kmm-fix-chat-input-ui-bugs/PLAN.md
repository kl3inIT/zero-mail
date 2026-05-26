---
quick_id: 260525-kmm
slug: fix-chat-input-ui-bugs
date: 2026-05-25
status: in-progress
---

# Quick Task: Fix three chat conversation pane UI bugs

## Problems observed

1. **Input does not clear until response starts.** `handleSubmit` in `conversation-pane.tsx:194-200` does `await chat.sendMessage(...)` then `setInput('')`. User typed text stays in the textarea until the model returns the first stream token.
2. **Send button has unreadable disabled state.** `PromptInputSubmit` uses only `bg-primary text-primary-foreground` (purple-on-purple-ish). When disabled (empty input, ready) it just looks like a solid purple square with no visible icon. When streaming, the icon also disappears because `{!isBusy && <ArrowUp />}` removes children but AI Elements doesn't supply its own.
3. **`writeReversible` tools render a dead "Confirm" button.** Lines 139-145 in `conversation-pane.tsx` render `<Button>Confirm</Button>` with no `onClick`. `writeReversible` tools (`applyLabel` / `archiveThread` / `saveDraft` / ...) are auto-executed server-side; there's nothing for the user to confirm. The button is misleading and broken.

## Fix

`apps/web/features/chat/components/conversation-pane.tsx`:

1. Reorder `handleSubmit`: call `setInput('')` BEFORE `await chat.sendMessage(...)` so the textarea clears immediately (mirrors inbox-zero `chat.tsx:184-196` which calls `handleSubmit()` then `setLocalStorageInput("")`, both synchronous).
2. Rewrite the `<PromptInputSubmit>` per status, copying inbox-zero `chat.tsx:258-286`:
   - Render `<Loader2 spinner />` when submitted.
   - Render `<Square />` (stop icon) when streaming.
   - Render `<ArrowUp />` when ready.
   - `disabled={!isBusy ? !input.trim() : false}` — disabled only when ready+empty, never when busy (user must be able to stop).
   - `onClick`: if busy, call `handleStop()` and preventDefault.
   - Add disabled visual: `disabled:bg-muted disabled:text-muted-foreground disabled:cursor-not-allowed` so disabled state is visually distinct from primary.
3. Remove the dead Confirm button block (lines 139-145) entirely. `writeReversible` tools just need their generic Tool card; no action button.

## Verification

- `pnpm --filter web typecheck` — pass.
- `pnpm --filter web lint` — pass.
- Manual: type then submit → input clears immediately. Idle send button shows arrow, disabled when empty, stops on click during streaming.
- Existing Playwright `e2e/chat/stream-happy-path.spec.ts` should still pass; deferred to CI.

## Out of scope

- Per-tool UI components (read + writeReversible JSON dump) → QT3.
- Any backend changes.
