---
status: investigating
trigger: "User reports chat assistant preview card flickers when clicking Gửi (Send) and email never sends; UI stuck on 'Đang gửi email...' indefinitely. Screenshot shows To=nhuxuanviet27102004@gmail.com, Subject='Re: Heloo', Vietnamese body, Send/AI Write/Language toggle/Attachment buttons visible."
created: 2026-05-25
updated: 2026-05-25
---

# Debug Session: Chat Assistant Preview Card — Send Stuck + Flicker

## Symptoms

- Expected behavior: User reviews AI-drafted reply on chat assistant preview card → clicks Gửi → mutation fires → backend Gmail send tool executes → preview card resolves to a sent confirmation state.
- Actual behavior: Clicking Gửi causes the preview card to flicker (components re-render inconsistently) and the loading indicator `Đang gửi email...` stays forever; no apparent network success/failure feedback in UI.
- Error messages (browser console):
  - `Blocked script execution in 'about:srcdoc' because the document's frame is sandboxed and the 'allow-scripts' permission is not set.`
  - `The resource http://localhost:3000/_next/static/css/app/layout.css?v=... was preloaded using link preload but not used within a few seconds from the window's load event.` (CSS preload warning — likely unrelated)
- Timeline: Unknown ("Không chắc") — may have started recently after a chat assistant / preview card phase; needs git-log confirmation.
- Reproduction: Open inbox, trigger chat assistant action that drafts a reply (subject becomes "Re: Heloo"), click Gửi on the rendered preview card.

## Context

- Compose pathway: **Chat assistant preview card** (not standalone Compose modal, not Reply within thread).
- Project pathway: per CLAUDE.md, chat preview card renders `assistant_pending_action` for `sendEmail`/`replyEmail`/`forwardEmail` tool calls; user-confirmed send must fire on explicit per-message click (auto-send forbidden).
- Backend tool: `sendEmail` / `replyEmail` in Spring AI tool layer; the actual Gmail send goes through `core.llm.gateway.springai` adapter.
- Suspect surface: `apps/web/features/chat-assistant/**` (preview card component, send mutation hook, pending-action state) and the corresponding `/api/chat/...` or `/api/messages/send` endpoint.

## Current Focus

- hypothesis (refined after code inspection): **autoConfirm useEffect retry loop on confirm failure** —
  In `apps/web/features/chat/components/preview-card/preview-card.tsx:148-156`, the effect runs `handleConfirm()` whenever `(action.autoConfirm && computed.status === 'pending' && computed.sendEnabled && !confirmInFlightRef.current)`. After a failed mutation, `confirmAction.isPending` flips back to false → `computed.sendEnabled` returns to true → effect re-runs → fires another POST `/api/chat/{chatId}/confirm` → fails again → loops. This produces both observable symptoms: continuous re-renders (flicker) and the parent spinner never clears.
- root_cause_summary:
  1. **Retry loop** in PreviewCard's autoConfirm effect — no "attempted" flag, so any error restarts the cycle.
  2. **Parent spinner desync** — `InlineAssistantPreview` derives `sendStatus` from the chat message `part.state` (which is `'input-available'` post-stream and never updates because the confirm response is not piped back into the AI SDK message store). Even if confirm succeeds once, the parent spinner stays.
  3. **Silent error swallowing** — `void handleConfirm()` discards the rejection; `useConfirmAction` has no `meta.errorMessage`; when `autoConfirm` is true and status is `'pending'`, PreviewCard returns `null`, so the inline error text inside `CardContent` never renders. User sees no error, only the spinner.
- next_action: Propose fix to user covering (a) one-shot autoConfirm guard, (b) surface confirm error via toast, (c) propagate confirm result up to InlineAssistantPreview so the spinner clears.

## Evidence

- timestamp: 2026-05-25
  observation: Console shows `Blocked script execution in 'about:srcdoc' because the document's frame is sandboxed and the 'allow-scripts' permission is not set.` — strongly implies the chat assistant preview uses a sandboxed iframe (likely to safely render HTML email body without XSS), and something in there tries to execute a script (could be a CSS-in-JS injection or an editor bootstrap). Whether the Send action lives inside or outside this iframe needs confirmation.
- timestamp: 2026-05-25
  observation: Screenshot shows the loading indicator `Đang gửi email...` rendered as a small spinner-line below the action buttons inside the same preview card — consistent with `isPending` being driven by the preview card's own mutation state, not a global toast.
- timestamp: 2026-05-25
  observation: Current branch is `gsd/phase-08-bulk-unsubscribe-campaign`; the chat assistant preview card was implemented in an earlier phase. Git log within `apps/web/features/chat-assistant/**` will date the regression if one occurred.

## Evidence (continued)

- timestamp: 2026-05-25
  observation: `apps/web/features/chat/components/preview-card/preview-card.tsx:148-156` — the autoConfirm useEffect has guards on `confirmInFlightRef`, `computed.status === 'pending'`, and `computed.sendEnabled`, but no "attempted" flag. When the mutation errors, `confirmInFlightRef` resets in `finally`, `submitting` flips false, `sendEnabled` flips true → effect re-fires.
- timestamp: 2026-05-25
  observation: `apps/web/features/inbox/components/InboxPageClient.tsx:1152-1154` — `latestSendAction` is built from the chat message parts; `sendStatus = actionStatus(latestSendAction)` only inspects `action.state`/`action.output?.state`/`action.confirmation?.state`. After stream closes with `finish("awaiting-confirmation")`, the part state stays `'input-available'`, which `actionStatus` returns as `'pending'`. `isSending = busy || !latestSendAction || sendStatus === 'pending'` is therefore stuck at true.
- timestamp: 2026-05-25
  observation: Backend `AssistantSendExecutor.execute(...)` (line 130) returns `state="CONFIRMED"`. `actionStatus` correctly maps `'confirmed'` to `'sent'` for send-class tools, BUT only when fed via `localState` inside PreviewCard. The parent has no path to receive `localState`.
- timestamp: 2026-05-25
  observation: `apps/web/features/chat/hooks/use-confirm-action.ts:17-27` — the `useConfirmAction` mutation defines no `meta.successMessage` / `meta.errorMessage`, so neither success nor failure surfaces a toast via the global `MutationCache` handler in `apps/web/lib/query-client.tsx`.
- timestamp: 2026-05-25
  observation: `preview-card.tsx:158-160` — when `autoConfirm && computed.status === 'pending'`, the entire card returns `null`. Therefore the inline `confirmAction.isError` text inside `CardContent` is unreachable on the autoConfirm path.
- timestamp: 2026-05-25
  observation: Backend `ConfirmController.java:78-81` maps `GmailSendFailedException` to 502 and `ConfirmationLeaseConflictException` / `StaleToolCallException` / `VipAcknowledgmentMissingException` to 409. None of these are surfaced to the user in the autoConfirm flow.

## Eliminated

- hypothesis: The "Blocked script execution in 'about:srcdoc'" console error is the root cause.
  reason: That message originates from an unrelated sandboxed iframe (likely an email-body render elsewhere). The compose form Send button is a normal React onClick in `handleSubmit` / `handleConfirmDialogSend` running in the top-level document; it is not inside a sandboxed iframe.
