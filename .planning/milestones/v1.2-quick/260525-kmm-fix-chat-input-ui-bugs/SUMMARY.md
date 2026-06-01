---
quick_id: 260525-kmm
slug: fix-chat-input-ui-bugs
date: 2026-05-25
status: complete
---

# Summary: Fix three chat conversation pane UI bugs

## What changed

`apps/web/features/chat/components/conversation-pane.tsx`:

1. `handleSubmit`: moved `setInput('')` to BEFORE `await chat.sendMessage(...)`. Textarea now clears immediately on submit instead of waiting for the first stream token.
2. `PromptInputSubmit`: copied inbox-zero's per-status icon pattern (`chat.tsx:258-286`). Submit button now renders `<Loader2 spinner />` on `submitted`, `<Square />` on `streaming`, `<ArrowUp />` on `ready`. Added explicit `disabled:bg-muted disabled:text-muted-foreground disabled:cursor-not-allowed` so the disabled state is visually distinct from the primary purple. `onClick` calls `handleStop()` and prevents default when busy.
3. Removed dead "Confirm" button block on writeReversible tool cards. `writeReversible` tools auto-execute server-side; the button had no `onClick` and was misleading UI. Cleaned up unused `Button` import and `isWriteReversibleToolName` import.

## Verification

- `pnpm --filter web typecheck` — pass.
- `pnpm --filter web lint` — pass (the one unused-disable warning is in `coverage/lcov-report`, not touched).
- Manual browser verification deferred to user.
- Playwright `e2e/chat/stream-happy-path.spec.ts` deferred to CI.

## Not done in this task

- Per-tool UI components (read + writeReversible JSON dump) → QT3.
