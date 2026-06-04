---
quick_id: 260602-cgj
status: in_progress
---

# Make bulk unsubscribe match Inbox Zero UX

Goal: replace the visible unsubscribe campaign workflow with an Inbox Zero-style sender table:
checkbox selection, sender display name + avatar, history/readability cues, one-click row action,
and a selected-items toolbar that directly executes unsubscribe/block without exposing campaign
terminology, preview dialog, or progress/undo pages.

Implementation checklist:

- [x] Inspect existing Zero Mail campaign UI/API and reuse backend execution/status where possible.
- [x] Rename user-facing copy and route surfaces away from campaign language.
- [x] Simplify candidate list table to a sender-centric action table with direct
      `Unsubscribe`/`Block` behavior.
- [x] Replace preview-first flow with direct execute plus toast.

Follow-up cleanup (this PLAN, in-flight):

- [ ] **FE delete** — remove `/cleanup/.../[jobId]/`, `CampaignStatusPage`, `PerSenderStateTable`,
      `PerSenderStateBadge`, `UndoBanner`, `UndoConfirmDialog`, `PreviewCampaignDialog`,
      `RiskBadge`, `MethodBadge`, hooks `useCampaignStatus`/`useUndoCampaign`/`useRetrySender`/
      `usePreviewCampaign` (+ test). Drop their query-key + api functions (`fetchCampaignStatus`,
      `retrySender`, `undoCampaign`, `previewCampaign`).
- [ ] **FE useExecuteCampaign** — drop the `Xem tiến độ` toast action and move the three
      `preview.err*` toast keys to `action.err*`.
- [ ] **FE messages.ts** — remove `status.*`, `undo.*`, `retry.*`, `preview.*`, and
      `errors.cleanup.campaign.not_found / undo_window_expired / retry_conflict`. Add the missing
      keys actually used by the new flow: `filter.*` (unhandled/all/unsubscribed/autoArchived/
      approved), `window.7d/30d/90d`, `list.loadMore`, `list.action.{approve,unapprove,autoArchive,
      archive,delete,archiveAll,deleteAll,labelFuture,menu,viewStats,viewGmail,unsubscribeBlock,
      block}`, `list.col.{from,read}`, `statusLabel.{unhandled,approved,unsubscribed,autoArchived}`,
      `stats.{title,emails,read,method,status}`, `confirm.{cancel,deleteOne,archiveTitle,archiveBody,
      deleteTitle,deleteBody}`, `labelFuture.{title,body,placeholder}`, `action.{mailAffected,
      genericError,approveOk,unapproveOk,markUnsubscribedOk,autoArchiveOk,archiveOk,deleteOk,
      labelFutureOk}`.
- [ ] **FE e2e** — rewrite `cleanup-unsubscribe-campaign.spec.ts` to cover the new golden path
      (unhandled filter, search, primary unsubscribe row action, bulk archive dialog). Drop the
      preview/execute/status/undo steps.
- [ ] **BE delete** — drop `CampaignStatusController` and the `/preview /retry /{jobId}/undo`
      endpoints inside `UnsubscribeCampaignController` plus their service classes
      (`CampaignRetryService`, `CampaignUndoService`, `CampaignStatusQueryService`) and DTOs
      (`CampaignStatusResponse`, `PerSenderStateResponse`, `CampaignPreviewRequest`,
      `CampaignPreviewResponse`, `PerSenderPreviewResponse`). Keep the Java class
      `CampaignPreviewService` because `CampaignExecuteService` still uses it as
      defense-in-depth before INSERT (see `CampaignExecuteService.java:32-36`).
- [ ] **BE exceptions / projection** — drop `CampaignNotFoundException`,
      `UndoWindowExpiredException`, `CampaignRetryConflictException`, and
      `CampaignStatusProjection` if unreferenced after the service cull.
- [ ] **BE tests** — drop `CampaignStatusControllerTest`, `CampaignUndoServiceTest`. Trim
      `UnsubscribeCampaignControllerTest` to the execute-only surface.
- [ ] **Regen OpenAPI** — boot backend, run `pnpm --filter web run generate:api`, replace the
      hand-written `CleanupSenderAction{Request,Response}` types in `unsubscribe-campaign-api.ts`
      with `components['schemas'][...]`.

Sender name + avatar (Inbox-Zero parity):

- [x] **BE schema** — Liquibase changeset 110-mail-message-observed-sender-name.yaml adds
      `sender_name varchar(320)` (nullable; existing rows stay NULL).
- [x] **BE observer** — `EmailAddressCanonicalizer.extractDisplayName` parses display-name out of
      the `From` header (e.g. `"John Doe" <john@x>` → "John Doe"), sanitizes + truncates ≤320, and
      `GmailDeliveryProcessingService` feeds it to `MailMessageObservedRepository.insertObservedIfAbsent`
      alongside the existing `sender_email`.
- [x] **BE candidate query** — `CandidateQueryService` aggregates `MAX(sender_name) FILTER` per
      group (same sender almost always carries the same display name); pipe through
      `UnsubscribeCandidateProjection` + `UnsubscribeCandidateResponse` (Jackson `NON_NULL` so
      nullability rides over the wire).
- [x] **BE tests** — `CandidateQueryServiceTest` adds `senderNameAggregatesIntoCandidateRow` and
      `senderRowWithoutDisplayNameReturnsNull`. `EmailAddressCanonicalizerTest` adds 4 cases for
      the new `extractDisplayName` helper. `GmailDeliveryProcessingSenderEmailTest` asserts the
      display-name flows through to the repo mock; sibling mocks updated for the 11-arg signature.
- [x] **FE consume** — `CandidateListTable` renders `candidate.senderName ?? localPartFallback`
      and `StatsDialog` title prefers `senderName`. Optional FE field typed via local intersection
      so the page compiles before the OpenAPI regen lands.
- [x] **FE avatar** — `SenderAvatar` renders `https://www.google.com/s2/favicons?domain={domain}
      &sz=64` (Inbox Zero pattern) with `onError` fallback to the upgraded initials helper
      (initials from the senderName first, local-part second) and the MailIcon if both are blank.

Stats dialog v1 (Inbox Zero parity — ảnh 2 + 3):

- [x] **BE timeline** — `SenderTimelineQueryService` + `SenderTimelineEntry` projection +
      `GET /api/unsubscribe/stats/timeline?senderEmail=...&windowDays=30`. SQL `DATE_TRUNC('day',
      observed_at)` over `mail_message_observed`. Privacy log: senderDomain + bucket count only.
- [x] **BE messages** — `SenderMessageReadService.fetchMessagesFromSender` calls Gmail live with
      `q=from:<senderEmail>` (Inbox Zero parity — they use the same Gmail query inside
      `/api/threads?fromEmail=`). `GET /api/unsubscribe/stats/messages?senderEmail=...&
      archivedOnly=false&limit=50`.
- [x] **BE body preview** — `SenderMessageReadService.fetchMessageBody` calls Gmail
      `messages.get format=full`, walks `payload.parts`, decodes base64url for text/html and
      text/plain. Never persisted. `GET /api/unsubscribe/stats/messages/{gmailMessageId}/body`.
      404 → FE shows empty preview.
- [x] **FE hooks + API client** — `useSenderTimeline`, `useSenderMessages`,
      `useSenderMessageBody` (TanStack Query, 5-min staleTime for body). Plain `fetch` in
      `unsubscribe-campaign-api.ts` (no schema.d.ts regen yet — types hand-written).
- [x] **FE SenderStatsDialog** — recharts bar chart over 30-day window, tabs Unarchived/All,
      message list with click-to-preview, inline preview pane on the right with sandboxed iframe
      (`<iframe sandbox="" srcDoc={htmlBody}>`) for HTML body. Plain-text fallback. Header
      buttons: Unsubscribe (or Block when method=NONE) + Auto Archive.
- [x] **FE i18n** — added `stats.titleWith`, `stats.chartLoading/Error/Empty`, `stats.tabs.*`,
      `stats.messagesLoading/Error/Empty`, `stats.previewHint/Loading/Error/Empty`,
      `stats.closePreview`. Removed obsolete `stats.title/emails/read/method/status` (old small
      dialog). Merged JSON, parity 1782 keys.
- [x] **Wire-up** — `CandidateListPage` swaps `<StatsDialog>` for `<SenderStatsDialog>` with
      onUnsubscribe/onAutoArchive callbacks that reuse the existing `useExecuteCampaign` /
      `useSenderAction` paths. No new mutations needed.

Followups before commit:

- [ ] Boot backend → `pnpm --filter web run generate:api` to regen `apps/web/lib/api/schema.d.ts`
      so `senderName` + new stats DTOs become part of the generated types; the hand-written
      types in `unsubscribe-campaign-api.ts` collapse to `components['schemas'][...]`.
- [ ] Backend test for `SenderTimelineQueryService` (Postgres-container) + integration test for
      `SenderStatsController` mocking Gmail. Skipped in v1 to fit scope; add before merge.
