---
slug: 260601-gmail-inbox-cqrs-projection-phase-b
title: Gmail Inbox CQRS-lite Projection — Phase B (read swap)
status: pending-review
created_at: 2026-06-01
owner: claude
depends_on:
  - 260601-gmail-inbox-cqrs-projection-phase-a
---

# Phase B — Gmail Inbox Read Swap

## Goal

Switch the inbox list read path from "every request hits Gmail metadata + per-message get" to
"projection table first, fall back to live Gmail only when the projection is empty / stale / not
yet synced." Frontend gets fast page loads (DB query ~10ms vs Gmail round-trip ~1-2s) without
ever exposing stale data as if it were fresh.

Phase A already shipped the write side: schema, cipher, Pub/Sub-driven UPSERTs, backfill on
connect / first fetch. Phase B is purely the read swap + UX signal for the in-flight backfill.

## Locked decisions (do NOT re-debate)

### Read priority

1. **Projection first**: query `gmail_inbox_projection` for the partial index window
   `(tenant_id, received_at DESC, gmail_message_id DESC) WHERE inbox_state = 'INBOX'`.
2. **Decrypt at the use-case boundary**: `InboxProjectionReadService` calls
   `InboxProjectionCipher.decrypt(envelope, tenantId, gmailMessageId, EncryptedField.*)` for each
   row and returns plaintext field DTOs. Cipher boundary stays single-entry.
3. **Live Gmail fallback** when:
   - No row exists for the tenant (cold start before backfill finishes), OR
   - `sync_state.last_full_sync_at IS NULL` (never synced), OR
   - The page is empty AND `sync_state.status = BACKFILLING` (UI shows "syncing").
4. **Per-row expires_at gate**: a row past `expires_at` is treated as missing; the fallback
   handler kicks in. Wave 0 just enforces the gate; the cron purge stays deferred.

### Mark-read invariant

`triageGmailWriter.markRead(tenantId, gmailMessageId)` must:
1. Issue the Gmail `users.messages.modify` (`removeLabelIds=['UNREAD']`) as today, AND
2. UPDATE `gmail_inbox_projection.unread = false` + remove `UNREAD` from `label_ids` for the row.

Both happen in the same `@Transactional` boundary so a Gmail success + DB failure (or vice versa)
does not leave a divergent state. Frontend stops invalidating `inboxKeys.pages()` — the optimistic
update already mirrors the new state, and the projection write makes the next refetch return the
same value.

### Cursor / pagination

DB cursor is opaque-encoded by `InboxCursorCodec` (already exists in `core.gmail.usecases`).
Phase B reuses it for the projection path: encode `(received_at, gmail_message_id)` as the
"after" cursor; the partial index already orders by that pair so the next page query is a single
index range scan.

### Data source signal

`GmailInboxPageResponse` gains a `dataSource` discriminator field:
- `PROJECTION` — happy path; data is from `gmail_inbox_projection`
- `LIVE_GMAIL` — fallback; row count < page size from projection AND `sync_state` says ready, so
  we filled the gap from Gmail (logging: `event=inbox_read_fallback reason=...`)
- `SYNCING` — projection empty AND backfill is in flight; FE renders "Đang đồng bộ hộp thư..."
  banner + spinner, no list

Frontend `InboxPageClient` reads the discriminator and renders accordingly. No mixed-source pages
in Phase B — fallback returns 100% Gmail-derived items (kept simple to avoid expires_at
correctness pitfalls when stitching).

## Wave breakdown

### Wave 0 — projection read API + decrypt
- `InboxProjectionReadService` with `fetchInboxPage(tenantId, cursor, limit)` returning
  `InboxProjectionPage(items, nextCursor, dataSource=PROJECTION)`.
- `GmailInboxProjectionRepository.findInboxPage(tenantId, beforeReceivedAt, beforeMessageId, limit)`
  — uses partial index, paginates by `(received_at, gmail_message_id)` tuple.
- New DB cursor codec method or reuse: encode `(received_at, gmail_message_id)` instead of
  the existing Gmail `pageToken`.
- Item DTO mirrors `RecentInboxMessage` shape so the controller layer can serve either source.

**Commit:** `feat(inbox): wave 0 — DB read + cipher decrypt`

### Wave 1 — fallback + freshness gate
- `RecentInboxReadService.fetchPage` becomes a thin orchestrator:
  1. Check `sync_state` — if `last_full_sync_at IS NULL` → return `SYNCING` empty page.
  2. Else query projection. If `items.size() == limit` → return `PROJECTION`.
  3. Else fall back to live Gmail (existing code path) → return `LIVE_GMAIL`.
- Per-row `expires_at` filter: `WHERE expires_at > NOW()` on the projection query so stale rows
  drop out and trigger fallback.
- Lazy backfill enqueue (already wired in Phase A) keeps firing on the fallback path so the next
  fetch can be served from DB.

**Commit:** `feat(inbox): wave 1 — fallback + freshness gate`

### Wave 2 — mark-read local update
- `TriageGmailWriter.markRead` extended to also UPDATE the projection row (or call
  `InboxProjectionWriteService.markRead(tenantId, gmailMessageId)` if a dedicated entry point
  keeps the cipher boundary cleaner — wave-0 decision).
- Frontend `useMarkInboxMessageRead`: drop the `invalidateQueries({queryKey: inboxKeys.pages()})`
  side-effect on success — the optimistic update already matches the DB state. Keep the
  invalidation on the message detail key so the open message reflects the unread label removal.

**Commit:** `feat(inbox): wave 2 — mark-read writes projection`

### Wave 3 — FE syncing indicator
- `GmailInboxPageResponse` exposes `dataSource: 'PROJECTION' | 'LIVE_GMAIL' | 'SYNCING'`.
- `@Schema(allowableValues = {"PROJECTION", "LIVE_GMAIL", "SYNCING"})` + required.
- Regenerate `apps/web/lib/api/schema.d.ts` via the documented codegen flow.
- `InboxPageClient` reads `dataSource` from the latest page; when `SYNCING` renders the banner
  ("Đang đồng bộ hộp thư từ Gmail. Mục mới sẽ hiện trong giây lát.") + a Loader2 spinner; when
  `LIVE_GMAIL` quietly logs but UI is unchanged.

**Commit:** `feat(inbox): wave 3 — dataSource discriminator + syncing banner`

### Wave 4 — tests + verification
- `InboxProjectionReadServiceTest`: seed projection rows + verify decrypt + paginate.
- `RecentInboxReadServiceFallbackTest`: cover three branches (PROJECTION happy / LIVE_GMAIL gap
  fill / SYNCING empty).
- Privacy log test: `InboxProjectionReadService` never logs plaintext (`InboxProjectionPrivacyLogTest`
  glob already covers `core.inbox.**`; ensure no new violations).
- Tenant isolation: seed tenant A + tenant B projections; tenant A's `fetchInboxPage` MUST NOT
  return tenant B rows (rely on `@TenantId` filter OR explicit `WHERE tenant_id = ?`).
- ArchUnit (extend `InboxProjectionArchTest`): `InboxProjectionReadService` is the ONLY caller
  of `cipher.decrypt(...)` outside the cipher's own tests.

**Commit:** `test(inbox): wave 4 — read path coverage + tenant isolation`

## Conventions (strict)

Same as Phase A: explicit names, records for DTOs / classes for entities, no Lombok, no plaintext
in logs, Liquibase append-only, atomic per-wave commits.

## Explicitly NOT in Phase B

- Sent / draft / trash / spam states + thread view — defer.
- Gmail `history.list` incremental sync — Phase C (or never; the Pub/Sub-driven UPSERTs may be
  enough).
- Cron purge of expired rows — defer; per-row `expires_at` filter in the read query is sufficient
  for correctness until volume forces hand.
- Backfill enqueue logic — already in Phase A wave 3; no changes.
- Mixed-source page stitching (some rows from DB, some from Gmail in the same response) — deferred
  to avoid `expires_at` correctness pitfalls.

## When PLAN.md is created, surface for review

Wave 0 starts with: `RecentInboxReadService.fetchPage` is currently a single method that calls
`gmailForTenant + messages.list + fetchMessageMetadata` synchronously. Wave 0 extracts the Gmail
path into a `liveGmailFetcher` helper and inlines the projection path beside it; Wave 1 makes the
fetchPage method the orchestrator. Keep the public method signature so existing API + tests stay
green during the refactor.
