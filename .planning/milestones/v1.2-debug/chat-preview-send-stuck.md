---
status: investigating
trigger: "User reports chat assistant preview card flickers when clicking Gửi (Send) and email never sends; UI stuck on 'Đang gửi email...' indefinitely. Screenshot shows To=nhuxuanviet27102004@gmail.com, Subject='Re: Heloo', Vietnamese body, Send/AI Write/Language toggle/Attachment buttons visible."
created: 2026-05-25
updated: 2026-05-25
note_2026-05-25_continuation: "Resumed. Code has evolved since the original write-up — AutoConfirmSendAction (apps/web/features/inbox/components/InboxPageClient.tsx:1214-1262) was added to handle the autoConfirm path with a one-shot confirmStartedRef guard and a local 4-state machine (waiting/sending/sent/failed). This neutralizes original root causes #1 (retry-loop) and #2 (parent-spinner desync) FOR THE INBOX COMPOSER PATH. Inbox composer always uses autoConfirm=true (InboxPageClient.tsx:1084 + handleConfirmDialogSend setAutoConfirmRequested(true) line 786). So the user's current symptom likely has a different proximate cause — see Refined Hypotheses below."
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

- hypothesis (2026-05-25 continuation): autoConfirm useEffect retry-loop in `preview-card.tsx:148-156` is the inbox-composer root cause.
  reason: Inbox composer renders the autoConfirm path through `AutoConfirmSendAction` (InboxPageClient.tsx:1214), NOT `PreviewCard`. `AutoConfirmSendAction` already has a one-shot `confirmStartedRef.current = true` set before the mutation fires (line 1222) and never resets — so the loop hypothesis does not apply to this user flow. PreviewCard's loop risk still exists for the chat-page non-autoConfirm flow but is not what the user is hitting from Inbox.

- hypothesis (2026-05-25 continuation): parent-spinner desync via `actionStatus(part.state)`.
  reason: AutoConfirmSendAction now drives its own `sendState` local machine ('waiting'/'sending'/'sent'/'failed'), not the chat part state. The mapping bug described in original root cause #2 no longer gates the inbox flow.

## Refined Hypotheses (2026-05-25)

Inbox composer end-to-end flow (confirmed by reading code):
1. User fills composer in `InboxReplyComposer` (InboxPageClient.tsx:605).
2. Submit → `handleSubmit` (line 777) opens `AlertDialog` (line 1091).
3. User confirms → `handleConfirmDialogSend` (line 783):
   - `setPreviewSubmitted(true)` + `setAutoConfirmRequested(true)`
   - `await assistantPreview.sendMessage({ text: composerConfirmationPrompt(...) })` — opens SSE stream to `/api/chat`.
4. `useChat({ chatId, initialMessages })` (use-chat.ts:60) wraps Vercel AI SDK's `useVercelChat`. Backend streams parts. When a `data-persistence`/`persistence` data part arrives, `setPersistenceAckCount(n+1)` (use-chat.ts:91-95).
5. `InlineAssistantPreview` renders with `autoConfirm={true}` (InboxPageClient.tsx:1084). Picks latest send-class tool call → renders `AutoConfirmSendAction` (line 1164).
6. `AutoConfirmSendAction` effect: gated by `!action.persistenceConfirmed` (line 1221). `persistenceConfirmed = isPersistedMessage(message) || persistenceAckCount > 0` (line 1155).
7. Once persistenceConfirmed flips true → mutation `useConfirmAction` fires POST `/api/chat/{chatId}/confirm` → backend `ConfirmController` executes Gmail send tool.

Candidate failure modes (in decreasing prior, given user's "Send disable / spinner stuck" symptom):

- **H-A: Send Preview button disabled because chat stream never ends** —
  `previewDisabled = assistantBusy || !toText.trim() || ...` (line 651). `assistantBusy = status === 'submitted' || 'streaming'` (line 647). If backend SSE never closes (provider error mid-stream, swallowed exception in stream handler, dropped network), Send Preview button stays disabled AND the inline preview spinner stays — both observable as "nút Send disable mãi".

- **H-B: SSE never emits a `data-persistence` part for autoConfirm flow** —
  `persistenceAckCount` stays 0 → `persistenceConfirmed=false` forever → `AutoConfirmSendAction` early-returns from the effect → never fires confirm mutation → spinner stuck on `inbox.composer.sendingNow`. No error toast (no exception path).

- **H-C: SSE never emits a send-class `tool-` part** —
  Assistant interprets the prompt as plain text and never calls `sendEmail`/`replyEmail`. `latestSendAction === undefined` → render falls into `busy ? <Spinner sendingNow /> : null` branch (line 1165-1170). If `busy` is true forever (stream hangs), spinner stuck.

- **H-D: AutoConfirmSendAction fires confirm but backend returns 4xx/5xx** —
  `setSendState('failed')` → small "sendFailed" red text shown but **no toast** (useConfirmAction has no `meta.errorMessage`). User says "không có phản hồi", so the failure text might be off-screen (preview area scrolled) or too subtle.

- **H-E: User canceled XSRF or session expired mid-stream** —
  401 redirect handled at fetch layer for normal requests, but Vercel AI SDK transport bypasses openapi-fetch — uses raw transport. Backend rejects with 401, stream errors silently.

## Next Action (2026-05-25)

Cannot disambiguate H-A through H-E without browser+backend evidence. Need:
1. Reproduce in browser with DevTools Network tab open.
2. Inspect the SSE response stream for `/api/chat`: does it close? What parts does it emit? Any 4xx/5xx?
3. Inspect backend application log around the failure timestamp: any exception in `ChatStreamController`, `AssistantToolExecutor`, or Gmail OAuth?

User-facing defenses we should add regardless (these are cheap, prevent the silent-stuck symptom even if root cause is elsewhere):

- **D-1 (high-value, low-risk)**: Add `meta.errorMessage` to `useConfirmAction` so any confirm failure surfaces a toast (currently silent on AutoConfirmSendAction's failed path).
- **D-2 (high-value, low-risk)**: Wrap `AutoConfirmSendAction`'s `waiting`/`sending` state in a soft timeout (e.g. 45-60s). On timeout, flip to `failed` and toast. Prevents the indefinite spinner regardless of which sub-failure occurred.
- **D-3 (medium)**: Surface chat-stream errors in `assistantPreview` — if `assistantPreview.status === 'error'` or stream rejected, show toast + reset `previewSubmitted` so the user can retry without reload.
- **D-4 (medium)**: Add `meta.errorMessage` to `useConfirmAction` AND `useCancelAction` (still silent in the chat-page non-autoConfirm flow too). Original root cause #3 still applies to the chat page even though it doesn't gate the inbox bug.
