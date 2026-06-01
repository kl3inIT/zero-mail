---
slug: 260601-gmail-inbox-cqrs-projection-phase-a
title: Gmail Inbox CQRS-lite Projection — Phase A
status: pending-review
created_at: 2026-06-01
owner: claude
---

# Gmail Inbox CQRS-lite Projection — Phase A (foundation only)

## Goal

Introduce a Postgres-backed inbox projection so future phases can stop hitting Gmail on every inbox-list call.

**Phase A delivers ONLY:** schema (2 tables), AES-GCM crypto for sensitive metadata, write path from Pub/Sub observed listener, backfill plumbing (eager + lazy enqueue via `processing_job`), and ArchUnit boundary tests.

**Phase A explicitly does NOT touch:** `RecentInboxReadService` read swap (defer Phase B), sent-message projection (defer), thread view (own phase later), `history.list` incremental sync (Phase B/C), cron purge job (defer).

## Why a separate table

`mail_message_observed` is append-once (`ON CONFLICT (tenant_id, gmail_message_id) DO NOTHING`) — it is the ingestion/triage seed, not a mutable display state. Inbox projection must mutate continuously (unread changes, label changes, archive→OUT_OF_INBOX, tombstone on delete, refreshed_at, source_history_id). Mixing the two would break ingestion semantics and force a different index strategy on the same table.

`thread_reply_status` is per-thread; inbox is per-message — different grain, different lifecycle. Do not conflate.

## Schema

### `gmail_inbox_projection` (per-message, mutable display state)

```sql
tenant_id                       UUID         NOT NULL  -- FK tenants(id) ON DELETE CASCADE
gmail_message_id                TEXT         NOT NULL
gmail_thread_id                 TEXT         NOT NULL

-- AES-GCM encrypted sensitive metadata
sender_email_hash               BYTEA        NOT NULL  -- HMAC-SHA256(lowercase normalized)
sender_email_ciphertext         BYTEA        NOT NULL
sender_display_name_ciphertext  BYTEA                  -- nullable
subject_ciphertext              BYTEA                  -- nullable
snippet_ciphertext              BYTEA                  -- nullable

-- Non-sensitive
has_attachment                  BOOLEAN      NOT NULL DEFAULT FALSE
received_at                     TIMESTAMPTZ  NOT NULL  -- from Gmail internalDate
label_ids                       TEXT[]       NOT NULL DEFAULT '{}'
inbox_state                     VARCHAR(16)  NOT NULL DEFAULT 'INBOX'
unread                          BOOLEAN      NOT NULL DEFAULT FALSE  -- denormalized

-- Sync metadata
source_history_id               BIGINT       NOT NULL
refreshed_at                    TIMESTAMPTZ  NOT NULL DEFAULT now()
expires_at                      TIMESTAMPTZ  NOT NULL  -- refreshed_at + 90 days

-- Concurrency
version                         INT          NOT NULL DEFAULT 0

PRIMARY KEY (tenant_id, gmail_message_id)
```

`inbox_state` enum (3 values, IdentifiedEnum + fail-loud fromId):
- `INBOX` — currently has Gmail INBOX label
- `OUT_OF_INBOX` — was seen, now not in inbox (archived / trashed / moved); kept for reconcile + idempotency
- `TOMBSTONED` — Gmail 404 / messageDeleted; kept short-term then purged

Indices:
- Partial inbox list index: `(tenant_id, received_at DESC, gmail_message_id DESC) WHERE inbox_state = 'INBOX'`
- Purge support: `(expires_at)` plain btree
- Thread group (cheap, helps future thread view): `(tenant_id, gmail_thread_id, received_at DESC)`

### `gmail_inbox_sync_state` (per-tenant cursor)

```sql
tenant_id            UUID         NOT NULL PRIMARY KEY  -- FK tenants(id) ON DELETE CASCADE
last_history_id      BIGINT                             -- nullable until first sync
last_full_sync_at    TIMESTAMPTZ                        -- nullable
last_incremental_at  TIMESTAMPTZ                        -- nullable, reserved Phase C
status               VARCHAR(24)  NOT NULL DEFAULT 'IDLE'
consecutive_errors   INT          NOT NULL DEFAULT 0
last_error_at        TIMESTAMPTZ
last_error_code      VARCHAR(64)
version              INT          NOT NULL DEFAULT 0
```

`status` enum (3 values, IdentifiedEnum + fail-loud fromId): `IDLE | BACKFILLING | ERROR`

**Source of truth for "is a backfill currently queued/running"** is the existing `processing_job` table (job_type = `INBOX_PROJECTION_BACKFILL`, idempotency_key = tenantId). `sync_state.status` is informational write-through for observability; the dedup-on-enqueue logic queries `processing_job`.

## Crypto

`CryptoProperties.inboxProjectionKeyBase64()` — SEPARATE from `refreshTokenKeyBase64()`. OAuth refresh-token and inbox metadata are different data classes; reusing the key widens blast radius.

- Key: 256-bit random, base64-encoded. Startup validator: base64-decoded length must equal 32 bytes (fail-fast bean construction)
- Cipher envelope binary layout: `[version:u8 (=1)][iv:12B][ciphertext:N][tag:16B]` stored as BYTEA
- AAD (Additional Authenticated Data): `tenantId.toString() + ":" + gmailMessageId + ":" + fieldName` UTF-8 bytes, where fieldName ∈ {`sender_email`, `sender_display_name`, `subject`, `snippet`}
- AAD purpose: prevents copying ciphertext from one row/field to another while still decrypting; tamper-evident at row+field granularity
- Hash key derivation: HKDF-Expand from `inboxProjectionKeyBase64` with info = `"inbox-projection-sender-hash"` if HKDF infra exists in repo; otherwise document fallback (e.g. separate `senderHashKeyBase64` property — to decide during Wave 1)
- `InboxProjectionCipher` service: `encrypt(plaintext, tenantId, gmailMessageId, fieldName)` and `decrypt(envelope, tenantId, gmailMessageId, fieldName)`

## Privacy / logging

- NEVER log subject, snippet, sender_email, sender_display_name, or any plaintext field
- All log lines follow project convention: `event=<name> tenantId={} gmailMessageId={}` only
- ArchUnit test enforces: no `log.*(...)` call passes a `*plaintext*`, `*subject*`, `*snippet*`, `*senderEmail*`, `*senderDisplayName*` symbol or string-concat-of-such

## Backfill flow

`InboxBackfillService` fetches the 100 latest INBOX messages from Gmail and bulk-upserts into `gmail_inbox_projection`.

Two trigger points (both enqueue an idempotent job; both async; neither blocks user UX):

1. **Eager on Gmail connect success** — in OAuth success handler / GmailConnectionService, after persisting the connection, enqueue `INBOX_PROJECTION_BACKFILL`
2. **Lazy on first inbox fetch** — in `RecentInboxReadService.fetchPage` (or a thin wrapper called from there), check `sync_state.last_full_sync_at IS NULL` for this tenant; if so, enqueue the same job. Live Gmail fetch still serves the response — read swap is Phase B

Enqueue logic:
```
if (processingJobRepository.findOpen(tenantId, INBOX_PROJECTION_BACKFILL).isEmpty()) {
    processingJobRepository.enqueue(tenantId, INBOX_PROJECTION_BACKFILL, ...);
    syncStateRepository.upsertStatus(tenantId, BACKFILLING);
}
```

Job worker (existing `processing_job` drain with `SKIP LOCKED`): on success → `status=IDLE`, `last_full_sync_at=now()`, `last_history_id=max(historyId observed)`. Terminal failure → `status=ERROR`, `consecutive_errors++`, `last_error_*`.

## ArchUnit boundaries (Wave 4)

- Only `core.inbox.usecases.InboxProjectionWriteService` may call `GmailInboxProjectionRepository.save*` or invoke the native UPSERT
- `InboxProjectionWriteService.upsert(...)` may be called only by `InboxBackfillService` and the Pub/Sub mail-observed listener (and the future Phase B read-swap path)
- `GmailInboxProjectionRepository` + `GmailInboxSyncStateRepository` may not be injected outside `core.inbox.**` and the listener package
- No log statement passes a plaintext field symbol or string concatenation thereof

## Wave breakdown

### Wave 0 — schema + config (parallelizable)
- Liquibase changeset `108-gmail-inbox-projection.yaml` (2 tables + 3 indices, explicit rollback)
- `CryptoProperties.inboxProjectionKeyBase64()` + startup length validator
- `.env.example`: `INBOX_PROJECTION_KEY_BASE64`
- Module skeleton: `core/inbox/{domain,persistence,usecases,exception}/package-info.java` (Spring Modulith boundaries)

**Commit:** `feat(inbox): wave 0 — schema + crypto config skeleton`

### Wave 1 — entities + cipher + enums
- `GmailInboxProjectionEntity`, `GmailInboxSyncStateEntity` (classes for Hibernate, NOT records; no Lombok)
- `GmailInboxProjectionRepository`, `GmailInboxSyncStateRepository` (native UPSERT queries)
- `InboxState`, `InboxSyncStatus` enums (IdentifiedEnum + fail-loud `fromId`)
- `InboxProjectionCipher` service (AES-GCM encrypt/decrypt + AAD)
- Tests: cipher round-trip; AAD tamper detection (modifying fieldName/tenantId/gmailMessageId fails decrypt); enum fromId throws NoSuchElementException on unknown id; repository UPSERT idempotency (slice test)

**Commit:** `feat(inbox): wave 1 — entities, repositories, cipher, enums`

### Wave 2 — write service + Pub/Sub hook
- `InboxProjectionWriteService.upsert(tenantId, GmailMessage payload, sourceHistoryId)` — encrypts fields, computes sender hash, sets `expires_at = refreshed_at + INTERVAL '90 days'`, UPSERTs via `ON CONFLICT DO UPDATE SET ... version = version + 1`
- Hook into existing Pub/Sub observed listener / `GmailDeliveryProcessingService`: AFTER existing `MailMessageObservedRepository.insertObservedIfAbsent(...)`, call `inboxProjectionWriteService.upsert(...)` with the same metadata, same `@Transactional` boundary if possible
- Test: end-to-end Pub/Sub message → both `mail_message_observed` AND `gmail_inbox_projection` rows present, encrypted fields decryptable round-trip

**Commit:** `feat(inbox): wave 2 — projection writer + Pub/Sub hook`

### Wave 3 — backfill plumbing
- `InboxBackfillService` (Gmail metadata fetch + bulk upsert)
- Register `INBOX_PROJECTION_BACKFILL` job type in processing_job
- Eager trigger in OAuth/GmailConnectionService success handler
- Lazy trigger in `RecentInboxReadService.fetchPage` (or thin wrapper) — non-blocking enqueue
- Tests: connect → job enqueued; second connect → no duplicate (idempotent); first fetch (no sync_state row) → enqueued; second fetch → no duplicate; backfill worker run → projection populated, sync_state advanced to IDLE with last_full_sync_at + last_history_id

**Commit:** `feat(inbox): wave 3 — backfill service + eager/lazy triggers`

### Wave 4 — ArchUnit + verification
- ArchUnit tests as described above (`InboxProjectionArchTest`)
- Privacy log test: regex scan over `core.inbox.**` source for forbidden patterns
- Run `./gradlew :backend:core:test :backend:api:test` — must be green
- Run `pnpm encoding:check` — no BOM in any new YAML/Java file
- Verify Liquibase startup applies the changeset on a fresh dev DB

**Commit:** `test(inbox): wave 4 — ArchUnit boundaries + privacy lint`

## Conventions (strict)

- Backend code style: explicit domain-revealing names; NO `req`/`res`/`repo`/`svc`/`cfg`/`ctx`/`msg`/`err`/`ex`/`e`/`conn`/`tx`/1-letter vars. Use `gmailInboxProjectionRepository`, `inboxProjectionWriteService`, `tenantContext`, `gmailMessage`, etc.
- Records for DTOs, classes for entities, NO Lombok anywhere
- Liquibase: append-only; explicit rollback block; preConditions where useful; comment field
- Spring Modulith: clean boundaries in `core.inbox`; expose only the public API surface via package-info.java
- Enums: IdentifiedEnum + static `fromId` throws `NoSuchElementException` on unknown id (per Convention 4)
- Logging: structured `event=<name> tenantId={}` format; never plaintext content

## Commit hygiene

- Atomic conventional commits per wave (`feat`, `test`, `chore`, `docs` prefixes)
- DO NOT skip pre-commit hooks unless the documented Windows cross-spawn issue blocks; if so, mention `--no-verify` in commit body with verification ran manually
- DO NOT edit `BundledGoogleOAuthIntegrationTest.java` or `GoogleOAuthSuccessHandlerTest.java` even if Spotless reformatter tries

## Out of scope for Phase A (defer; do not implement)

- Outbound send gateway → projection upsert (sent messages)
- SENT / DRAFT / TRASH / SPAM inbox_state values
- Thread view (own phase)
- Read swap: `RecentInboxReadService.fetchPage` → DB (Phase B)
- Gmail `history.list` incremental sync (Phase B/C)
- Cron purge job for expired rows (Phase A only adds column + index)
