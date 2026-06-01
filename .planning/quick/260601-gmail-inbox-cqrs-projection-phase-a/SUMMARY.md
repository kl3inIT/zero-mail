---
slug: 260601-gmail-inbox-cqrs-projection-phase-a
title: Gmail Inbox CQRS-lite Projection — Phase A
status: complete
completed_at: 2026-06-01
commits:
  - 4648ef28 feat(inbox): wave 0 — schema + crypto config skeleton
  - c67e4b71 feat(inbox): wave 1 — entities, repositories, cipher, enums
  - a15b5572 feat(inbox): wave 2 — projection writer + Pub/Sub hook
  - ad0a0cae feat(inbox): wave 3 — backfill service + eager/lazy triggers
  - (wave 4) test(inbox): wave 4 — ArchUnit boundaries + privacy log lint
---

# Phase A — Gmail Inbox CQRS-lite Projection (complete)

## Result

Shipped the foundation for the inbox CQRS-lite read model. Pub/Sub-driven and backfill-driven
writes populate `gmail_inbox_projection` (mutable display state) and `gmail_inbox_sync_state`
(per-tenant cursor) with AES-GCM encrypted sensitive metadata. Read swap intentionally deferred
to Phase B; live Gmail behavior unchanged.

## What ships

| Wave | Deliverable | Tests |
|---|---|---|
| 0 | Liquibase `108-gmail-inbox-projection.yaml` (2 tables + 3 indices + rollback); `CryptoProperties.inboxProjectionKeyBase64` + `inboxProjectionSenderHashKeyBase64` (separate from refresh-token KEK); env + 4 application.yml configs + build.gradle.kts OpenAPI props; `core.inbox.{domain,persistence,usecases,exception}` Modulith skeleton | compile-clean |
| 1 | `InboxState` / `InboxSyncStatus` enums (IdentifiedEnum + fail-loud `fromId`); `EncryptedField` enum; `InboxProjectionCipher` wrapping `PlatformSecretCipher` with per-(tenant, gmailMessageId, fieldName) AAD + HMAC-SHA256 sender hash; `GmailInboxProjectionEntity` + `GmailInboxSyncStateEntity` (Hibernate classes, no records, no Lombok); repositories with native UPSERT only | 12 unit + slice tests: enum fromId, cipher round-trip, AAD tamper detection for tenantId / gmailMessageId / fieldName, repo UPSERT idempotency, sync state status transitions |
| 2 | `InboxProjectionWriteService.upsert(InboxProjectionUpsertCommand)` — encrypts, hashes, derives `expires_at = refreshed_at + 90 days`, runs in `REQUIRES_NEW` transaction; hooks into `GmailDeliveryProcessingService.insertObservationAndPublishEvents` so every Pub/Sub observation refreshes the projection (label / unread / archived transitions) without disturbing the append-once observed row | 2 integration tests: end-to-end UPSERT + cipher round-trip; INBOX→OUT_OF_INBOX flip on re-observation with INBOX dropped |
| 3 | `InboxBackfillEnqueuer` (idempotent processing_job dedup via JdbcTemplate; bypasses Hibernate `@TenantId` resolver so callers don't need bound `TenantContext`); `InboxBackfillService` (Gmail `users.messages.list(INBOX, 100)` + per-message metadata fetch with From/Subject headers + snippet + attachment detection); `InboxBackfillJobHandler` + `ProcessingJobWorker` dispatch case `INBOX_PROJECTION_BACKFILL`; eager trigger in `OAuthProvisioningService.provisionBundledOAuth` (both first-login + reconnect); lazy trigger in `RecentInboxReadService.fetchPage` (no `sync_state.last_full_sync_at`) | 3 dedup contract tests: first call inserts + flips status; repeated PENDING calls dedup to 1 row; post-COMPLETED call inserts fresh row |
| 4 | `InboxProjectionArchTest` (4 boundaries: only `InboxProjectionWriteService` may call native UPSERT; only listener + backfill may call `InboxProjectionWriteService.upsert`; neither repository may be injected outside `core.inbox.*` or listener package); `InboxProjectionPrivacyLogTest` (source-file regex lint over `core.inbox.**` — bans bare identifiers `subject` / `snippet` / `senderEmail` / `senderDisplayName` / `plaintext` in `log.*(...)` calls, with double-quoted strings stripped first to avoid false positives on log message templates) | 5 architecture + privacy tests |

## Locked architectural decisions (encoded in code + tests)

- **Separate table not extension of `mail_message_observed`**: observed is append-once
  (`ON CONFLICT DO NOTHING`); projection is mutable (`ON CONFLICT DO UPDATE`). Mixing would break
  ingestion semantics + force diverging index strategies.
- **inbox_state enum = INBOX | OUT_OF_INBOX | TOMBSTONED** (3 values). Gmail-archive becomes
  `OUT_OF_INBOX` to keep the semantic precise; Gmail 404 / messageDeleted → `TOMBSTONED`.
- **TTL refresh-based**: `expires_at = refreshed_at + 90 days`. Receiver-based would expire
  backfilled old mail immediately.
- **2 separate AES-GCM keys**: `inboxProjectionKeyBase64` (field cipher) +
  `inboxProjectionSenderHashKeyBase64` (HMAC key). Blast-radius isolation: key leak of one does
  not allow recomputing the other.
- **AAD = `tenantId:gmailMessageId:fieldName`**: ciphertext copied from one (row, field) tuple to
  another fails to decrypt — tamper-evident at row+field granularity.
- **Idempotent enqueue source-of-truth = `processing_job`**: `sync_state.status` is an informational
  write-through. Dedup logic counts open (PENDING / PROCESSING) rows.
- **JdbcTemplate INSERT in the enqueuer**: bypasses Hibernate `@TenantId` resolver so OAuth /
  fetchPage triggers don't need bound TenantContext at the call site.

## Explicitly NOT shipped (defer to later phases)

- Read swap (`RecentInboxReadService.fetchPage` → DB query) — Phase B
- Sent / draft / trash / spam states + outbound send hook
- Thread view (own phase)
- Gmail `history.list` incremental sync — Phase B/C
- Cron purge job (only `expires_at` column + index are in place)

## Test summary

20 new tests across `com.zeromail.core.inbox.*` and `com.zeromail.core.arch.InboxProjection*`;
all green. Existing tests touched (constructor signatures): `OAuthProvisioningDefaultsTest`,
`RecentInboxReadServiceTest`, `GmailDeliveryProcessingServiceTest`,
`GmailDeliveryProcessingSenderEmailTest` — all updated and green.

## Notes for Phase B picker

- `gmail_inbox_projection` rows are populated by Pub/Sub observed listener (label changes /
  unread flips) AND by `InboxBackfillService` (initial 100-message backfill on connect or first
  fetch). Both flows go through the single `InboxProjectionWriteService.upsert` entry point.
- For the read swap: query partial index
  `idx_gmail_inbox_projection_list (tenant_id, received_at DESC, gmail_message_id DESC) WHERE inbox_state = 'INBOX'`
  — already in place.
- Ciphertext fields are nullable; expect to decrypt-on-read at the use-case boundary via
  `InboxProjectionCipher.decrypt(envelope, tenantId, gmailMessageId, EncryptedField.*)`.
- Decryption failure on a stale ciphertext (e.g. AAD mismatch from a future schema change) should
  cause the read path to fall back to live Gmail for that single row, not 500 the whole page.

## Followups

- Cron purge job for `expires_at < now()` rows (low priority; manual `DELETE` works for now).
- Backfill error handling: terminal error → `ERROR` status + retry policy decision (Phase B).
- E2E test: Pub/Sub stub → projection row materialized + decryptable round-trip (already partly
  covered by `InboxProjectionWriteServiceTest`; full pipeline test belongs in `:backend:api`).
