# Phase 2A: Mail Ingestion - Context

**Gathered:** 2026-04-28
**Status:** Ready for research + planning

<domain>
## Phase Boundary

Phase 2A wires the Gmail ingress pipeline that every later phase depends on: register `users.watch` per connected Gmail account, receive Pub/Sub push notifications via plain HTTP on the VPS, verify Google OIDC tokens, dedupe deliveries idempotently, fetch Gmail history with bounded recovery, and append a privacy-safe `mail_message_observed` audit row per new INBOX message. Plus a tenant-visible "globally pause triage" toggle that blocks downstream write actions while still receiving + observing events.

**In scope:**

*Backend — Pub/Sub push receiver:*
- New `GmailPubSubController` `@PostMapping("/internal/pubsub/gmail")` in `backend/api`, bypassing the user OAuth filter chain (its own SecurityConfig matcher with `permitAll` + custom OIDC verification filter).
- Verify Google OIDC token on every request: `Authorization: Bearer <token>` → `google-auth-library-oauth2-http` `TokenVerifier` checks (a) signature against Google's JWKS, (b) `aud` claim equals configured public push endpoint URL, (c) `email` claim equals configured Pub/Sub service-account principal. Reject with 401 + opaque log; never reach business logic. (Closes deferred ceremony from Phase 01.5 D-D5.)
- Parse base64url `message.data` JSON → `{emailAddress, historyId}`. Lookup `gmail_connections` by `LOWER(google_email)` → bind `TenantContext.TENANT` ScopedValue.
- INSERT into `pubsub_delivery` with `ON CONFLICT (tenant_id, pubsub_message_id) DO NOTHING` (Pub/Sub redelivery becomes atomic no-op). Return `200 OK` immediately. Target controller p99 < 300ms (no Gmail API call inside controller).
- Unknown email address (no matching `gmail_connections` row) → 200 OK + log `event=pubsub_unknown_email_dropped` (no tenant binding, no PII). Dropping is correct (orphaned Pub/Sub messages from deleted accounts).

*Backend — Worker pipeline (history fan-out):*
- New `GmailHistoryProcessor` Spring `@Scheduled(fixedDelay)` job in `backend/worker` (1–2s tick on idle).
- Query: `SELECT ... FROM pubsub_delivery WHERE status='PENDING' FOR UPDATE SKIP LOCKED LIMIT N`.
- Per row: bind `TenantContext.TENANT`, refresh access token via `RefreshTokenCipher` decrypt + Google OAuth refresh, call `gmail.users().history().list(startHistoryId=last_synced_history_id, historyTypes=[messageAdded], maxResults=500)`.
- Bounded gap: if `webhook_history_id - last_synced_history_id > 500`, start at `webhook_history_id - 500` and log `event=gmail_history_gap_truncated tenantId={} skipped={delta}` (Inbox-zero parity, prevents runaway processing).
- For each `messagesAdded` entry where `labelIds` includes `INBOX`: `INSERT INTO mail_message_observed (tenant_id, gmail_message_id, gmail_thread_id, history_id, label_ids, observed_at) ON CONFLICT (tenant_id, gmail_message_id) DO NOTHING`. Privacy: NO subject, NO from, NO body, NO snippet — only IDs + label snapshot.
- Monotonic `last_synced_history_id` advance: `UPDATE gmail_connections SET last_synced_history_id={new} WHERE tenant_id={t} AND (last_synced_history_id IS NULL OR last_synced_history_id < {new})` to prevent regression under concurrent processors (Inbox-zero monotonic update pattern).
- After successful fan-out, `UPDATE pubsub_delivery SET status='PROCESSED'`. On retryable failure increment `attempts`; after 3 failures → `status='DEAD'` + log alert event.

*Backend — History-404 (HISTORY_LOST recovery):*
- Worker catches Gmail 404 from `history.list` (historyId expired beyond Gmail's ~7-day window).
- Set `gmail_connections.last_synced_history_id = webhook_history_id` (advance pointer to current; drop the gap, no full mailbox rescan per ROADMAP success #5).
- Set `gmail_connections.ingestion_health = HISTORY_LOST`.
- Log `event=gmail_history_lost tenantId={} expired_history_id={} new_pointer={}`.
- Future webhooks process forward from new pointer normally. UI surfaces `ReconnectPrompt`. User clicks reconnect → `/tenant/connect-gmail` (Phase 01.5 D-A5 reconnect path with `prompt=consent`) → success handler clears `watch_expires_at`+`watch_history_id` to NULL → `GmailWatchScheduler` re-issues watch within 30s → `ingestion_health` flips back to `HEALTHY` upon successful watch.

*Backend — `users.watch` lifecycle (unified register + renew):*
- New `GmailWatchScheduler` `@Scheduled(cron = "0 * * * * *")` (every minute; shorter than hourly to keep first-connect latency low) in `backend/worker`.
- Single query handles initial registration AND renewal: `SELECT ... FROM gmail_connections WHERE status='CONNECTED' AND (watch_expires_at IS NULL OR watch_expires_at < NOW() + INTERVAL '24 hours') FOR UPDATE SKIP LOCKED LIMIT N`.
- Per row: bind tenant context, refresh token, call `gmail.users().watch(userId='me', requestBody={labelIds: ['INBOX'], labelFilterBehavior: 'include', topicName: env.GOOGLE_PUBSUB_TOPIC_NAME})`.
- Persist response: `watch_history_id` (returned baseline), `watch_expires_at` (returned `expiration` ms epoch), `watch_renewed_at = NOW()`. Set `ingestion_health = HEALTHY` if previously `WATCH_UNHEALTHY` and watch succeeded.
- Failure path: increment retry counter (in-memory or column); after 3 consecutive failures → `ingestion_health = WATCH_UNHEALTHY` + log alert event. Next renewal attempt next tick; success resets counter.
- INBOX-only subscription: `labelIds=['INBOX']` (NOT INBOX+SENT — v1 has no sent-side feature; cuts ~50% Pub/Sub volume). Defer SENT to Phase 4+ if rule engine needs it (cheap migration: re-call `users.watch` with new labelIds).
- On user disconnect (`GmailConnectionService.disconnect`) and account deletion: call `gmail.users().stop()` to cancel the watch + null out `watch_*` columns. Best-effort (don't fail disconnect if stop() fails — token may already be revoked).

*Backend — Schema additions (Liquibase):*
- New changeset `010-gmail-ingestion-state.yaml`:
  - `ALTER TABLE gmail_connections ADD COLUMN last_synced_history_id BIGINT, watch_history_id BIGINT, watch_expires_at TIMESTAMPTZ, watch_renewed_at TIMESTAMPTZ, ingestion_health VARCHAR(32) NOT NULL DEFAULT 'HEALTHY'`.
- New changeset `011-pubsub-delivery-table.yaml`:
  - `CREATE TABLE pubsub_delivery (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, pubsub_message_id TEXT NOT NULL, history_id BIGINT NOT NULL, payload JSONB NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING', attempts INT NOT NULL DEFAULT 0, locked_until TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ, UNIQUE(tenant_id, pubsub_message_id))`. Index `(status, locked_until)` for the SKIP LOCKED scan.
- New changeset `012-mail-message-observed-table.yaml`:
  - `CREATE TABLE mail_message_observed (tenant_id UUID NOT NULL, gmail_message_id TEXT NOT NULL, gmail_thread_id TEXT NOT NULL, history_id BIGINT NOT NULL, label_ids TEXT[] NOT NULL, observed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY (tenant_id, gmail_message_id))`. BRIN index on `observed_at`.
- New changeset `013-tenants-triage-paused.yaml`:
  - `ALTER TABLE tenants ADD COLUMN triage_paused BOOLEAN NOT NULL DEFAULT false`.
- New `IdentifiedEnum` `GmailIngestionHealth` in `core.gmail.model` with values `HEALTHY`, `WATCH_UNHEALTHY`, `HISTORY_LOST` (per Phase 01.2.1 D-B5 unordered convention).

*Backend — Pause API + status surface:*
- New `PUT /tenant/triage-pause` endpoint in `backend/api`, body `{"paused": true|false}`. Service: `TenantService.setTriagePaused(boolean)` updates `tenants.triage_paused` under TenantContext.
- Extend existing `MeResponse` (or sibling endpoint) to include `triagePaused: boolean` and `gmailConnectionStatus: {status, ingestionHealth, googleEmail}` so frontend can render banner + reconnect prompt without N+1 calls.
- Audit log: `event=triage_pause_toggled tenantId={} paused={true|false}`. No body, no PII.
- Phase 2A scope: persist + UI. Phase 4 scope: read flag at triage_job enqueue time. Phase 2A's verification of MAIL-06 is "toggle works + persists + reflects in UI"; end-to-end "no write actions" verification deferred to Phase 4.

*Frontend — Pause toggle + banner:*
- `apps/web/app/(protected)/settings/page.tsx` adds a "Pause automated triage" toggle in a new Card section (raw shadcn primitives + token-aware className per Phase 01.5 D-C2).
- New `apps/web/features/triage/components/PauseBanner.tsx` (or `apps/web/features/tenant/components/PauseBanner.tsx`): conditional render in `(protected)/layout.tsx` when `tenant.triagePaused === true`. Non-dismissible, persistent, with an inline "Unpause" button. Uses `<Alert variant="warning">` (Phase 01.5 D-C3 warning variant) — same primitive family as `ReconnectPrompt`.
- TanStack Query hook `useToggleTriagePause` mutation invalidates the `me` query key after success.

*Frontend — ReconnectPrompt extension:*
- `apps/web/features/gmail/components/ReconnectPrompt.tsx` gate now reads `gmailConnectionStatus.ingestionHealth` in addition to `status`. Shown when `status !== 'CONNECTED' OR ingestionHealth !== 'HEALTHY'`. One copy + one CTA — root cause is for telemetry/admin only. CTA still points at `/tenant/connect-gmail` (Phase 01.5 D-A5).
- i18n keys preserved from Phase 01.5; no new copy strings unless `frontend-design` skill recommends during plan-phase polish review.

**Out of scope (enforced):**
- Phase 4 triage-job fan-out: Phase 2A's worker writes to `mail_message_observed` and stops there. Enqueueing `processing_job` rows for triage / rules / write-actions is Phase 4.
- Reading `tenants.triage_paused` to gate triage actions: Phase 4 owns the read; Phase 2A only persists + exposes the flag.
- Per-tenant Pub/Sub topics or per-tenant subscriptions. Single shared topic + subscription, all tenants → controller looks up tenant by `emailAddress`.
- Server-Sent Events / WebSocket "live message" stream to UI. UI relies on TanStack Query refetch / future polling-list endpoint (own phase).
- Multi-account / workspace concept (still deferred per Phase 01.5).
- Backend rename `tenant → workspace`.
- Sent-side detection / Reply-Tracker patterns (Inbox-zero feature, not v1).
- Spam / `IMPORTANT` / category filtering at the watch layer. Watch only INBOX; downstream rules engine in Phase 3 handles label-based filtering.
- Dead-letter topic / GCP-side DLQ wiring. App-layer `pubsub_delivery.status='DEAD'` is the v1 dead-letter; GCP DLQ tuning deferred.
- LLM / triage logic of any kind. Phase 2A is observation-only.
- New shadcn primitives beyond `<Alert variant="warning">` (already shipped 01.5).
- Visual regression tests / Storybook / Chromatic.
- Sentry / Datadog / OTel browser SDK frontend wiring.
- BYOK key handling, billing credits — orthogonal phases (2C, 2B).

</domain>

<decisions>
## Implementation Decisions

### A. Push pipeline shape

- **D-A1: Ack-fast + Postgres handoff (Inbox-zero semantic on top of CLAUDE.md SKIP LOCKED queue).** Controller does only OIDC verify + tenant lookup + dedup INSERT + 200 OK return. All Gmail API calls (history.list, watch.users, users.stop), token refresh, retry, rate-limit handling live in `backend/worker`. Why: (a) Pub/Sub default ack deadline is ~10s — sync inline would risk timeout on history backlogs; (b) controller stays sub-300ms p99 + trivially testable (pure OIDC + DB unit); (c) worker reusable for Phase 4 fan-out; (d) aligns with CLAUDE.md TL;DR ("Postgres-backed SKIP LOCKED queue, no Kafka/RabbitMQ in v1"); (e) `backend/worker` separation enables vertical scale tuning later. Trade-off: 0–2s poll latency from delivery to `MessageObserved`. ROADMAP success #1 "within seconds" satisfied with `fixedDelay=1000ms` on idle.

- **D-A2: Single shared Pub/Sub topic + subscription for all tenants.** Mirrors Inbox-zero. Gmail `users.watch` everywhere uses the same `topicName`; controller looks up tenant by `LOWER(emailAddress)` from the decoded payload. Single OIDC `aud` claim, single SA principal `email` claim — both env-config'd. Rejected per-tenant topology: multiplies GCP setup, requires provisioning automation, no v1 isolation benefit at single-VPS scale.

- **D-A3: Push endpoint security.** `/internal/pubsub/gmail` is `permitAll` for the existing OAuth/session filter chain (Pub/Sub doesn't carry a user session) but is gated by a custom `PubSubOidcAuthFilter` that runs `TokenVerifier` from `google-auth-library-oauth2-http`. Verifies signature, `iss=https://accounts.google.com`, `aud=<configured push URL>`, `email=<configured SA principal>`. Mismatch → 401 + opaque log + no business logic reached. Reject paths NEVER bind `TenantContext` (no PII in log even on rejection).

- **D-A4: Tenant lookup by `LOWER(google_email)`.** Pub/Sub payload `emailAddress` is the canonical join key (Inbox-zero pattern). Address normalization to lowercase for case-insensitive matching. Unknown email → 200 OK + drop with `event=pubsub_unknown_email_dropped` event (orphaned message from deleted account is correct behavior, not an error). NEVER 4xx — Pub/Sub will redeliver indefinitely on non-200.

- **D-A5: Idempotency keys per layer.**
  - Pub/Sub-level dedup: `pubsub_delivery.UNIQUE (tenant_id, pubsub_message_id)`. Pub/Sub `messageId` is Google's monotonic delivery ID — canonical Pub/Sub redelivery dedup key.
  - Gmail-message-level dedup: `mail_message_observed.PRIMARY KEY (tenant_id, gmail_message_id)`. Gmail message IDs can recur across multiple history entries (label changes, gap re-fetches); single PK gates downstream MAIL-04 compliance + Phase 4 audit reuse.
  - Both INSERTs use `ON CONFLICT DO NOTHING` for atomic dedup without read-then-write race.

### B. Sync state + dedup schema

- **D-B1: Extend `gmail_connections` with 4 ingestion-state columns** (single-row v1, single-account-per-tenant locked from Phase 01.4 D-B1): `last_synced_history_id BIGINT NULL`, `watch_history_id BIGINT NULL` (baseline returned by `users.watch`), `watch_expires_at TIMESTAMPTZ NULL`, `watch_renewed_at TIMESTAMPTZ NULL`. Adding a parallel `gmail_sync_state` 1:1 join table is over-engineering for v1; if multi-account ever ships, refactor lives in its dedicated phase.

- **D-B2: Two dedup tables, two distinct invariants.** `pubsub_delivery` is the ingress queue + Pub/Sub redelivery guard (UNIQUE on `pubsub_message_id`). `mail_message_observed` is the per-Gmail-message audit log (UNIQUE on `gmail_message_id`) — Phase 4 verifies success #3 "duplicate deliveries safe" by replaying and asserting no second observation row. Merging into a single table with nullable columns (one-row-per-delivery + one-row-per-message in same table) couples 2 semantics into 1 UNIQUE constraint and is harder to reason about. Two tables, two purposes — explicit beats clever.

- **D-B3: `mail_message_observed` is privacy-floor.** Columns: `tenant_id, gmail_message_id, gmail_thread_id, history_id, label_ids TEXT[], observed_at`. NO subject, NO from, NO body, NO snippet, NO sender domain, NO recipient. Even storing label IDs is a (small) signal — but labels are user-defined classifiers, not message content, and Phase 4 needs them to evaluate rules without re-fetching. PostgreSQL TEXT[] (not JSONB) for label_ids — simpler ops, same storage cost. BRIN index on `observed_at` per CLAUDE.md time-series pattern.

- **D-B4: MessageObserved emit policy = `messagesAdded` only, INBOX-filtered.** Worker iterates `history.history[*].messagesAdded`; emits `mail_message_observed` ONLY when the message's `labelIds` includes `INBOX`. Skips `labelsAdded` / `labelsRemoved` events (Inbox-zero processes these for label-change reactions; v1 doesn't need them). Defer label-change observation to Phase 4 if rule engine surfaces a need (cheap forward addition: same table, new code path).

- **D-B5: `last_synced_history_id` monotonic-conditional update** (Inbox-zero pattern): `UPDATE gmail_connections SET last_synced_history_id = :new WHERE tenant_id = :t AND (last_synced_history_id IS NULL OR last_synced_history_id < :new)`. Prevents concurrent processors with older history IDs from regressing the pointer. The worker grabs rows via `FOR UPDATE SKIP LOCKED` so concurrency should be bounded, but the conditional UPDATE is a belt-and-suspenders invariant.

- **D-B6: Bounded history window.** Match Inbox-zero: if `webhook_history_id - last_synced_history_id > 500`, start at `webhook_history_id - 500` and log `event=gmail_history_gap_truncated`. Prevents runaway processing if user reconnects after a long disconnect. ROADMAP success #5 "no full mailbox rescan" satisfied. `maxResults: 500` per `history.list` call. If `nextPageToken` returned, log `event=gmail_history_pagination_dropped` and accept the truncation (rare; only happens when 500 messages arrived in single delivery window).

### C. Watch lifecycle

- **D-C1: Async-via-worker, NOT in OAuth success handler.** Bundled-OAuth handler (Phase 01.5 D-A1) commits user+tenant+gmail_connections atomically and exits. NO `gmail.users().watch()` call inside the OAuth critical path. Worker `GmailWatchScheduler` polls every minute; picks up rows where `status='CONNECTED' AND (watch_expires_at IS NULL OR watch_expires_at < NOW() + INTERVAL '24 hours')`. Single query handles BOTH initial registration (NULL expiry) AND renewal (<24h to expiry). Rationale: (a) OAuth handler stays trivial — no external Gmail call in login path; (b) one code path for register + renew = easier reasoning; (c) Gmail API failure during initial connect doesn't break login flow — user lands in app, watch arrives within 60s; (d) per-tenant scatter is natural since `watch_renewed_at` updates differently per row. Trade-off: 0–60s latency from "Gmail connected" to "watch active", during which no Pub/Sub events arrive. Acceptable: user is on `/onboarding` template selector during this window.

- **D-C2: Renewal cadence = `@Scheduled(cron = "0 * * * * *")` (every minute), 24h margin.** Every minute is finer-grained than strictly needed for renewal, but it doubles as the initial-registration trigger so first-connect latency stays sub-60s. Daily-fixed cron would create a thundering herd against Gmail quota (250 quota units/user/sec but global quota is shared) and would push initial-register latency to up to 24h. Per-tenant scatter on `watch_renewed_at` ensures renewals naturally distribute across the 6-day inner window. `LIMIT N` (e.g., 50) per tick to bound per-tick work; multiple ticks drain a backlog without saturating Gmail API.

- **D-C3: `labelIds = ['INBOX']` only, `labelFilterBehavior = 'include'`.** v1 Zero Mail has no sent-side feature, no Reply-Tracker, no thread-detection. Subscribing INBOX+SENT (Inbox-zero default) doubles Pub/Sub volume for no v1 value. Future Phase 4+ adding sent-thread features is a cheap migration: `users.watch` is idempotent — re-call with new labelIds on existing connections. NOT subscribing all labels (wildcard) because that pulls user actions like "manually labeled message", which v1 doesn't react to.

- **D-C4: Failure model = 3 consecutive failures → `ingestion_health = WATCH_UNHEALTHY`.** Per-row attempt counter (column `watch_consecutive_failures INT NOT NULL DEFAULT 0` on `gmail_connections`, incremented on failure, reset to 0 on success). After 3 fails: flip `ingestion_health` (independent of `status` — token may still be valid). Log `event=gmail_watch_unhealthy_threshold tenantId={}`. Next renewal tick continues retrying; success resets counter + flips `ingestion_health` back to `HEALTHY`. No exponential backoff needed at v1 scale; constant 1-minute interval is acceptable for the 3-attempt window.

- **D-C5: `users.stop()` on disconnect.** `GmailConnectionService.disconnect` (Phase 01.5 owned) extended to call `gmail.users().stop()` best-effort + null out `watch_*` columns + set `ingestion_health = HEALTHY` (clears any prior degraded state). Failure to call `stop()` doesn't fail the disconnect operation (token may already be revoked — Gmail returns 400). Same for account deletion path (`AccountService.deleteCurrentUser`).

### D. History-404 + DISCONNECTED model

- **D-D1: New `IdentifiedEnum` `GmailIngestionHealth` (single column, three values).** Values: `HEALTHY` (default), `WATCH_UNHEALTHY` (D-C4), `HISTORY_LOST` (history-404 detected). Stored on `gmail_connections.ingestion_health VARCHAR(32) NOT NULL DEFAULT 'HEALTHY'`. Implements `IdentifiedEnum` per Phase 01.2.1 D-B5 (unordered, no weight, static `fromId` fail-loud per D-B4).

  **`status` enum (`GmailConnectionStatus`) UNCHANGED** — keeps the 4 values `NOT_CONNECTED, PENDING, CONNECTED, DISCONNECTED`, semantically locked to "token/identity health". `ingestion_health` is orthogonal (token can be valid while data stream is broken). Combining the 3 axes (token / scheduler / data) into a single status enum forces consumers to reason about every cross-product; orthogonal columns are cleaner.

- **D-D2: Bounded history-404 recovery.** Worker catches Gmail 404 from `history.list`. Action: (a) `UPDATE gmail_connections SET last_synced_history_id = :webhook_history_id, ingestion_health = 'HISTORY_LOST'`; (b) log `event=gmail_history_lost tenantId={} expired_history_id={} new_pointer={webhook_history_id}`; (c) mark current `pubsub_delivery` row `status='PROCESSED'` (we successfully advanced state, even though we dropped the gap). Future webhooks process forward from the new pointer normally. NO full mailbox rescan, NO `messages.list` call. ROADMAP success #5 satisfied.

- **D-D3: ReconnectPrompt unified gate, single copy + single CTA.** Frontend logic: `shouldShowReconnect = status !== 'CONNECTED' || ingestionHealth !== 'HEALTHY'`. One `<Alert variant="warning">` with the existing copy from Phase 01.5 (no new i18n keys). One CTA → `/tenant/connect-gmail` (Phase 01.5 D-A5 reconnect entry with `prompt=consent`). End-user doesn't need to distinguish "token revoked" vs "watch retry failed" vs "history expired" — the action is the same OAuth re-grant. `ingestion_health` value is for telemetry / admin debugging only.

- **D-D4: Reconnect handler clears `watch_*` columns to force re-register.** After a successful reconnect via `/tenant/connect-gmail`, the bundled-OAuth success handler (or its post-success cleanup) sets `watch_expires_at = NULL`, `watch_history_id = NULL`, `watch_consecutive_failures = 0`, `ingestion_health = HEALTHY`. Worker `GmailWatchScheduler` picks up the row on its next minute-tick (NULL expiry matches the initial-register condition) → re-issues `users.watch` → persists fresh baseline → ingress resumes normally.

### E. Global pause toggle (MAIL-06)

- **D-E1: Single boolean `tenants.triage_paused BOOLEAN NOT NULL DEFAULT false`.** Not a `tenant_settings` table. v1 invariant 1-tenant-1-user-1-Gmail (Phase 01.4 D-B1) is locked; one boolean doesn't justify a settings entity. When/if multi-flag settings accumulate (theme, timezone, notification prefs), migrate to `tenant_settings(tenant_id PK, ...)` in a dedicated phase.

- **D-E2: Pause gate semantic = "events received, write actions blocked" — Phase 4 reads, Phase 2A persists.** Phase 2A's `pubsub_delivery` insertion + `mail_message_observed` append run unconditionally. Phase 4's triage_job enqueue (which doesn't exist in 2A) will read `tenants.triage_paused` and skip the queue insert when true. ROADMAP success #5 wording ("events still received but no write actions queued") aligns with this split. Phase 2A's verifiable behavior: toggle persists, UI reflects it, audit event fires.

- **D-E3: Toggle endpoint `PUT /tenant/triage-pause`.** Body `{"paused": boolean}`. Returns updated `MeResponse`-like payload. Service: `TenantService.setTriagePaused(boolean)` under TenantContext. Audit log: `event=triage_pause_toggled tenantId={} paused={true|false}`. NO request/response body fields beyond the boolean.

- **D-E4: Status surface = extended `MeResponse` (or sibling endpoint).** Frontend reads `triagePaused: boolean` and `gmailConnectionStatus: {status, ingestionHealth, googleEmail}` from a single GET (avoids N+1 on layout render). Existing `/me` endpoint (consumed by `app/layout.tsx` per Phase 01.5 D-D2 React `cache()` wrapper) is the natural extension point. OpenAPI schema regen via `springdoc-openapi-gradle-plugin` (Phase 01.2.1 P04 hermetic emit pattern) → frontend `apps/web/lib/api/schema.d.ts` auto-typed.

- **D-E5: UI = Settings toggle + persistent banner when paused.** Settings page (`apps/web/app/(protected)/settings/page.tsx`) gets a new Card section with the toggle (raw shadcn `<Switch>` or `<Button>` toggle — `frontend-design` skill picks during plan-phase polish review). `(protected)/layout.tsx` renders a `<PauseBanner>` non-dismissible component when `tenant.triagePaused`. Banner uses `<Alert variant="warning">` (Phase 01.5 D-C3) + inline "Unpause" button. TanStack Query hook `useToggleTriagePause` invalidates the `me` key after success — same dedupe strategy as `useDisconnectGmail` (Phase 01.3).

### Claude's Discretion

The researcher/planner/executor have flexibility within CLAUDE.md and the decisions above on:

- **`pubsub_delivery.payload JSONB` exact shape** — store the full Pub/Sub envelope (`{message: {data, messageId, publishTime, attributes}, subscription}`) as-is, or just the decoded `{emailAddress, historyId, messageId, publishTime}`? Recommend full envelope for replay/debug capability; payload is bounded (~few KB).
- **Worker module shape inside `backend/worker`** — single combined module `mail-ingestion` housing `GmailWatchScheduler` + `GmailHistoryProcessor`, or split into two? Recommend single module since they share `RefreshTokenCipher` + Gmail client builder + retry/rate-limit infrastructure. Don't pre-split.
- **Spring MVC OIDC verification filter** — implement as Spring `OncePerRequestFilter` mounted on `/internal/pubsub/**` prefix, vs a Spring Security `SecurityFilterChain` with custom matcher + custom `AuthenticationProvider`. Researcher to verify which is idiomatic on Spring Security 7.0.5 — likely a separate `SecurityFilterChain` with `@Order` so it runs before the user-OAuth chain and uses `permitAll` after the Pub/Sub OIDC filter authenticates the request.
- **`GmailHistoryProcessor` retry classification** — which Gmail error codes are retryable (5xx, 429, network)? Which terminal (401 invalid_grant → flip `status=DISCONNECTED`, 403 quota → backoff)? Researcher to extract from `google-api-services-gmail` v1-rev20250331-2.0.0 + Inbox-zero's `withGmailRetry` helper.
- **Token refresh integration** — reuse `OAuth2AuthorizedClientService` from Spring Security 7 for refresh, or call Google's token endpoint directly with the decrypted refresh token? Recommend the latter for the worker context (no `Authentication` principal in scheduler thread); document the pattern.
- **`watch_consecutive_failures` storage** — column on `gmail_connections` (durable) vs in-memory cache (rebuild on restart). Recommend column for resilience across restarts; tiny cost.
- **Pub/Sub topic + subscription provisioning automation** — IaC (Terraform / `gcloud`) script in `infra/`, or manual GCP console setup documented in `RUNBOOK.md`? Pre-launch, manual + RUNBOOK is fine; revisit in Phase 6 launch hardening.
- **Audience claim configuration** — env var `PUBSUB_PUSH_AUDIENCE_URL` + `PUBSUB_SA_PRINCIPAL_EMAIL` (matching Phase 01.5 D-D5 protocol). Both required, fail-fast at boot if missing (Phase 01.5 P08 `:?` pattern).
- **`mail_message_observed.label_ids` storage type** — TEXT[] (PostgreSQL native array) vs JSONB array. Recommend TEXT[] for simpler GIN index + smaller footprint; no nested fields needed.
- **Whether to also drop `nextPageToken` truncation events into a dead-letter table** — likely overkill v1; just log + accept. Revisit if retention/SLA concerns surface.
- **First-connect UX feedback** — does the onboarding template-selector page show "Setting up your inbox..." for the 0–60s watch-register window? Recommend yes via lightweight banner reading `watch_history_id IS NULL` from the extended status. Consult `frontend-design` skill during plan polish.
- **Pause toggle copy** — "Pause automated triage" vs "Pause Zero Mail" vs "Don't apply rules to new mail". Pick during `frontend-design` skill polish; v1 i18n key naming = `settings.triage.pause.{title,body,toggleLabel}`.
- **Whether `mail_message_observed` row should also store `internal_date` (Gmail-side timestamp)** — useful for Phase 4 ordering, doesn't violate privacy. Recommend yes.
- **Granularity of `event=` logging** — emit per-message-observed event (high volume) vs per-history-batch event (low volume). Recommend per-batch with count: `event=gmail_history_processed tenantId={} batch_size={} new_observations={}`. Per-message events would flood logs at busy mailboxes.
- **Endpoint base path** — `/internal/pubsub/gmail` vs `/api/v1/internal/pubsub/gmail` vs `/webhook/gmail`. Match existing controller convention; consult `apps/web/proxy.ts` for which prefixes are forwarded vs internal-only. Internal-only path should be reachable from Pub/Sub but NOT from the Next.js proxy (firewalled at reverse proxy level).
- **Whether `mail_message_observed` triggers an outbound application event** for Phase 4 to subscribe to. Recommend NO at Phase 2A — Phase 4 will poll `mail_message_observed WHERE NOT EXISTS (matching processing_job)` or similar. Don't pre-bind a Spring application event API that may not match Phase 4's needs.

### Folded Todos

_No todos folded — `gsd-sdk query todo.match-phase 2A` not run (init blocked by existing .planning/); no pending todos in `.planning/todos/pending/` matched Phase 2A scope on manual scan. The two active pending todos (`wr-06-test-profile-securityconfig-slice`, `worker-application-yml-fail-fast-parity`) are infrastructure-quality items unrelated to mail ingestion._

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before researching, planning, or implementing.**

### Project-level (in-repo, locked)
- `CLAUDE.md` — Locked stack (Java 25, Spring Boot 4.0.6, Spring Security 7.0.5, Spring AI 2.0.0-M4, Gmail API v1-rev20250331-2.0.0, google-auth-library-oauth2-http 1.35.0, Liquibase 5.0.2 YAML, PostgreSQL 17.6 self-hosted, Redis 7.2 self-hosted, no `spring-cloud-gcp` baseline). MUST follow Conventions section (thin controllers + service-owned `@Transactional`, records-for-DTOs / classes-for-entities Lombok-free, OrderedEnum/IdentifiedEnum + static `fromId` fail-loud, privacy-logging format `event=opaque_name tenantId={}` never inline subject/email/token).
- `.planning/PROJECT.md` — Privacy posture, "trust is the product", Gmail-only v1, write-action allow-list (label/archive/save-draft, NEVER auto-send).
- `.planning/REQUIREMENTS.md` — MAIL-01, MAIL-02, MAIL-03, MAIL-04, MAIL-05, MAIL-06 (this phase's full requirement list).
- `.planning/ROADMAP.md` §Phase 2A — goal + 5 success criteria + research flag (Gmail watch/history edge cases + OIDC push-token verification).
- `.planning/STATE.md` — current position, blockers section: **(a) Pub/Sub OIDC verification ceremony is THIS PHASE's deliverable**, watch other deferred ceremonies (refresh-token rotation owned by 2C, prod cookie/secret resolution owned by Phase 6).
- `.planning/research/STACK.md` — Postgres-backed queue with SKIP LOCKED, Gmail integration libs, observability OTel, no GCP-specific starters baseline.

### Prior-phase context (decisive for this phase)
- `.planning/phases/01-foundation-safety-infrastructure/01-CONTEXT.md` — D-A1 (module pattern), D-B1/B2 (`OncePerRequestFilter` + `TenantContext` ScopedValue + `@TenantId` discriminator), D-E1/E2 (privacy contract, Logback scrub), D-G1/G2 (refresh-token AES-GCM envelope + key versioning) — **`RefreshTokenCipher` is consumed verbatim by the worker for token refresh**.
- `.planning/phases/01.2-domain-owned-persistence-restructuring/01.2-CONTEXT.md` — Modulith module boundaries: `gmail/`, `tenant/`, `shared/` allowedDependencies. Worker will live in `core.gmail.service` package + new `core.gmail.ingestion` (or stay in service); ingestion enums in `core.gmail.model`.
- `.planning/phases/01.2.1-shared-base-entity-and-enum-standard/01.2.1-CONTEXT.md` — `AbstractTenantOwnedEntity` (extended by `GmailConnectionEntity`), `IdentifiedEnum` contract (used by new `GmailIngestionHealth`), `PostgresContainerTest` harness pattern (used by every integration test in this phase).
- `.planning/phases/01.5-inbox-zero-alignment-bundled-oauth-ux-polish-cleanup-sweep-r/01.5-CONTEXT.md` — **D-A1 bundled scopes** (`gmail.modify` provides `users.watch` permission), **D-A5 reconnect URL `/tenant/connect-gmail`** (this phase's history-404 reconnect path), **D-D5 deferred ceremonies (Pub/Sub OIDC verification owned by 2A)** — close it. D-D3 `<Alert variant="warning">` (PauseBanner reuses).
- `.planning/phases/01.4-gmail-identity-semantics-permission-ux-and-ui-consistency/01.4-CONTEXT.md` — D-B1/B4 (single-account-per-tenant invariant — drives D-B1 / D-E1 in this phase: extend gmail_connections + tenants column instead of new tables).

### In-code anchors (current state to extend)
- `backend/api/src/main/java/com/zeromail/api/controllers/` — new `GmailPubSubController.java` (push receiver) + extend `TenantStatusController.java` or sibling for `PUT /tenant/triage-pause`.
- `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` — add new `SecurityFilterChain @Bean` for `/internal/pubsub/**` matcher with `permitAll` + custom OIDC verification filter, `@Order` before user OAuth chain.
- `backend/api/src/main/java/com/zeromail/api/security/` — new `PubSubOidcAuthFilter.java` (OncePerRequestFilter style or filter-chain style — researcher decides).
- `backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java` — extend with `triagePaused: boolean` and `gmailConnectionStatus: {status, ingestionHealth, googleEmail}` (or add sibling `TenantStatusResponse` projection).
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java` — ADD 5 columns (`last_synced_history_id BIGINT`, `watch_history_id BIGINT`, `watch_expires_at TIMESTAMPTZ`, `watch_renewed_at TIMESTAMPTZ`, `watch_consecutive_failures INT NOT NULL DEFAULT 0`, `ingestion_health VARCHAR(32) NOT NULL DEFAULT 'HEALTHY'`).
- `backend/core/src/main/java/com/zeromail/core/gmail/model/GmailConnectionStatus.java` — UNCHANGED (D-D1 keeps the 4-value enum).
- `backend/core/src/main/java/com/zeromail/core/gmail/model/` — ADD `GmailIngestionHealth.java` (new IdentifiedEnum: HEALTHY, WATCH_UNHEALTHY, HISTORY_LOST + static `fromId` fail-loud).
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/` — ADD `PubSubDeliveryEntity.java` + `PubSubDeliveryRepository.java`; ADD `MailMessageObservedEntity.java` + `MailMessageObservedRepository.java`. `PubSubDeliveryEntity` extends `AbstractTenantOwnedEntity`; `MailMessageObservedEntity` uses a composite `(tenant_id, gmail_message_id)` key with explicit `@TenantId` on `tenant_id` because it cannot inherit the surrogate-id base class.
- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java` — extend `disconnect(...)` to call `users.stop()` and null watch_* columns; add `markHistoryLost(...)`, `markWatchUnhealthy(...)`, `clearForReconnect(...)` methods.
- `backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java` — ADD `triage_paused BOOLEAN NOT NULL DEFAULT false` column.
- `backend/core/src/main/java/com/zeromail/core/tenant/service/TenantService.java` — ADD `setTriagePaused(boolean)` method.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java` — CONSUMED VERBATIM by worker for token refresh.
- `backend/worker/src/main/java/com/zeromail/worker/` — ADD `GmailWatchScheduler.java` (cron 1min, register+renew unified) + `GmailHistoryProcessor.java` (fixedDelay ~1s, fan-out from `pubsub_delivery` → `mail_message_observed`). Existing `HealthcheckScheduler.java` is the pattern reference.
- `backend/worker/src/main/java/com/zeromail/worker/WorkerApplication.java` — ensure `@EnableScheduling` + `@EntityScan` covers gmail + tenant entities.
- `backend/worker/src/main/resources/application.yml` — env vars for `GOOGLE_PUBSUB_TOPIC_NAME`, `PUBSUB_PUSH_AUDIENCE_URL`, `PUBSUB_SA_PRINCIPAL_EMAIL` with `:?` fail-fast (Phase 01.5 P08 pattern).
- `backend/api/src/main/resources/application.yml` — same env vars (controller filter consumes them).
- `backend/core/src/main/resources/db/changelog/changes/` — last is `009-drop-signed-in-onboarding-step.yaml`. ADD `010-gmail-ingestion-state.yaml`, `011-pubsub-delivery-table.yaml`, `012-mail-message-observed-table.yaml`, `013-tenants-triage-paused.yaml`. `db.changelog-master.yaml` `includeAll` auto-picks.
- `apps/web/app/(protected)/settings/page.tsx` — ADD Card section "Pause automated triage" with `<Switch>`-style toggle. Polish target.
- `apps/web/app/(protected)/layout.tsx` — ADD conditional `<PauseBanner />` render when `tenant.triagePaused`.
- `apps/web/features/triage/` (NEW) or `apps/web/features/tenant/` — ADD `components/PauseBanner.tsx`, `hooks/useToggleTriagePause.ts`, `api/triagePause.ts`. Follows Phase 01.3 feature-folder convention (deep imports, no barrel index).
- `apps/web/features/gmail/components/ReconnectPrompt.tsx` — extend gate logic to read `ingestionHealth !== 'HEALTHY'` in addition to `status !== 'CONNECTED'`. No new copy needed (D-D3).
- `apps/web/features/account/api/me.ts` (`getCurrentUser`) — extend response schema to include `triagePaused` + `gmailConnectionStatus`. Re-runs through Phase 01.5 D-D2 `cache()` wrapper.
- `apps/web/lib/api/schema.d.ts` — auto-regenerated via `pnpm generate:api` after backend OpenAPI emits new fields/endpoint.
- `apps/web/i18n/messages/{vi,en}.json` — ADD `settings.triage.pause.{title,body,toggleLabel,banner.heading,banner.unpause}` (exact key set picked during plan phase). `EN_SCAN_FILES` in `apps/web/scripts/check-i18n.ts` updated if new files added.

### External specs (re-fetch via Context7 at implementation time)
- **Google Gmail API v1** — `users.watch`, `users.stop`, `users.history.list` (params: `startHistoryId`, `historyTypes=['messageAdded']`, `maxResults=500`, `labelId=INBOX`), `users.messages.get` (probably NOT needed in 2A — Phase 4 fetches body for triage), error codes (404 historyId expired, 401 invalid_grant, 403 quota).
- **Google Cloud Pub/Sub push delivery** — push payload format (`{message: {data: base64, messageId, publishTime, attributes}, subscription}`), OIDC token signing semantics (`aud` = push endpoint URL, `email` = SA principal, `iss=https://accounts.google.com`).
- **`google-auth-library-oauth2-http` 1.35.0** — `TokenVerifier` setup (custom JWKS fetcher for Google's keys, audience pinning, expiry), `IdToken` verification flow.
- **Spring Security 7.0.5** — multiple `SecurityFilterChain @Bean` with `@Order`, custom `AuthenticationProvider`, `permitAll()` matcher composition, integration with custom `OncePerRequestFilter`.
- **Spring MVC + Spring Boot 4.0.6** — controller mapping, virtual threads (`spring.threads.virtual.enabled=true`), `@PostMapping` body binding for Pub/Sub envelope.
- **Spring Scheduling** — `@Scheduled(cron=...)` vs `@Scheduled(fixedDelay=...)`, ScopedValue propagation across scheduled threads (likely needs explicit `ScopedValue.where(...).run(...)` per row, scheduler doesn't auto-bind).
- **PostgreSQL** — `SELECT ... FOR UPDATE SKIP LOCKED` semantics, `ON CONFLICT (cols) DO NOTHING` atomic upsert, BRIN indexes for time-series, `TEXT[]` array storage + GIN index.
- **Liquibase 5.0.2 YAML** — `addColumn` + default values, `createTable` with composite UNIQUE constraint and PK, idempotent `onlyIf` pre-conditions.
- **Spring Modulith verification** — adding `core.gmail.ingestion` (or co-located) module declaration if a new sub-package emerges; otherwise existing `core.gmail` module shape stays per Phase 01.2 D-D4.

### Local references
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/gmail/watch.ts` — reference for `users.watch` request body shape (labelIds + topicName).
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/app/api/google/webhook/route.ts` — reference for ack-fast pattern + `decodeHistoryId` base64 parse.
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/app/api/google/webhook/process-history.ts` — reference for `fetchGmailHistoryResilient` (500-item gap cap, 404→expired branch, monotonic `lastSyncedHistoryId` UPDATE).
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/gmail/history.ts` — `getHistory` wrapper.
- Inbox-zero is Node + NextAuth + Prisma — stack-translate to Java 25 + Spring + JPA, don't copy-paste. Patterns map; library APIs differ.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable assets (preserved by Phase 2A)

- **`GmailConnectionEntity`** (Phase 01.2.1 P03) — extended with 6 ingestion-state columns. Existing fields (`googleEmail`, `status`, `refreshTokenEncrypted`, `scopesGranted`, `connectedAt`, `disconnectedAt`) preserved. AbstractTenantOwnedEntity extension preserved.
- **`GmailConnectionStatus` enum** (Phase 01.2.1) — UNCHANGED. NOT_CONNECTED / PENDING / CONNECTED / DISCONNECTED preserved as token/identity health axis.
- **`RefreshTokenCipher`** (Phase 1 P06 + Phase 01.5 P08) — AES-GCM envelope encrypt/decrypt with `key_version` byte. Worker consumes verbatim for token refresh.
- **`GmailConnectionService`** (Phase 01.5 P03) — `upsert(...)`, `currentStatus(...)`, `disconnect(...)`, `deleteForCurrentTenant(...)` preserved; `disconnect(...)` extended to call `users.stop()`. New methods added: `markHistoryLost`, `markWatchUnhealthy`, `clearForReconnect`, `recordWatchSuccess`.
- **`OAuthProvisioningService`** (Phase 01.5) — bundled-OAuth provisioning preserved verbatim. Watch registration is async post-provisioning (D-C1) — handler doesn't change.
- **`TenantContext` ScopedValue + `OncePerRequestFilter` pattern** (Phase 1 D-B1/D-B2) — Pub/Sub controller's filter binds `TENANT` after tenant lookup; worker schedulers bind explicitly per row.
- **`@TenantId` discriminator** (Phase 1 D-B2) — automatically applied by Hibernate to `pubsub_delivery` via `AbstractTenantOwnedEntity` and to `mail_message_observed` via an explicit `@TenantId` field on its composite-key entity.
- **`AbstractTenantOwnedEntity`** (Phase 01.2.1) — `PubSubDeliveryEntity` extends it. `MailMessageObservedEntity` cannot extend it because of its composite key, so it must carry an explicit `@TenantId` tenant field instead.
- **`IdentifiedEnum` + `OrderedEnum` contract** (Phase 01.2.1 P02) — `GmailIngestionHealth` implements `IdentifiedEnum` (unordered, `fromId` fail-loud).
- **`PostgresContainerTest`** (Phase 01.2.1 P03) — integration test harness for ingestion controller + worker schedulers.
- **`MultiTenantLeakIntegrationTest`** (Phase 1 FND-05) — pattern reference for ScopedValue + `@TenantId` test wiring.
- **`AccountDeletionController`** (Phase 01.2 P05) — reference for orchestration pattern; pause toggle endpoint uses simpler single-service call.
- **`HealthcheckScheduler`** (Phase 1 worker scaffold) — `@Scheduled` pattern reference for `GmailWatchScheduler` + `GmailHistoryProcessor`.
- **shadcn `<Alert variant="warning">`** (Phase 01.5 D-C3) — `PauseBanner` reuses; existing `--warning` token from `globals.css`.
- **`features/<name>/{api,components,hooks}/`** (Phase 01.3) — feature folder structure preserved; new `features/triage/` (or co-located in `features/tenant/` — planner picks).
- **TanStack Query `me` key + `cache()` wrapper** (Phase 01.5 D-D2) — extended with `triagePaused` + `gmailConnectionStatus`; same dedupe semantics.
- **`ReconnectPrompt.tsx`** (Phase 01.4 / 01.5) — gate condition extended; copy unchanged (D-D3).
- **OpenAPI codegen pipeline** (`apps/web/lib/api/schema.d.ts` via `springdoc-openapi-gradle-plugin`, Phase 01.2.1 P04) — used to type the new `PUT /tenant/triage-pause` endpoint + extended `MeResponse`.

### Established patterns (preserved as constraints)

- **Postgres-backed SKIP LOCKED queue, NO Kafka/RabbitMQ** (CLAUDE.md TL;DR) — `pubsub_delivery` is the v1 ingress queue.
- **Plain HTTP push receiver, NO `spring-cloud-gcp` starters** (CLAUDE.md + Phase 01.5 P08 confirmed) — controller is bare `@PostMapping`.
- **Refresh-token envelope `[key_version|nonce|ciphertext]`** (Phase 1 D-G2) — preserved verbatim.
- **AES-GCM at app layer, key from VPS deployment secret** (Phase 1 D-G1, Phase 01.5 P08 `:?` fail-fast) — `REFRESH_TOKEN_KEY_BASE64` env var pattern.
- **ScopedValue binding before persistence** (Phase 1 D-B1) — controller filter + worker scheduler must `ScopedValue.where(TENANT, tenantId).run(...)` before ANY DB call.
- **Privacy contract** (Phase 1 D-E1, D-E2) — never log subject/email/from/refresh-token/access-token bytes; opaque `event=` names + `tenantId` UUID. Logback scrub filter remains active.
- **`@TenantId` + Hibernate filter** (Phase 1 D-B2) — automatic per-tenant scoping on every query.
- **Onboarding state machine forward-only** (Phase 01.2.1 D-B5) — Phase 2A doesn't touch onboarding.
- **`OnboardingStep` and `GmailConnectionStatus` orthogonal** (Phase 01.5 D-B4) — `ingestion_health` is a third orthogonal axis.
- **Liquibase YAML changelogs, defaultValueComputed: now() on audit columns** (Phase 01.2.1 P01) — `mail_message_observed.observed_at` follows.
- **All-or-nothing OAuth provisioning** (Phase 01.5 D-A2) — UNCHANGED; watch registration is async-post-provisioning, doesn't break atomicity.
- **`/tenant/connect-gmail` reconnect URL** (Phase 01.5 D-A5) — Phase 2A's reconnect-CTA target.
- **Single bundled `google` OAuth registration** (Phase 01.5 D-A1) — UNCHANGED; `gmail.modify` scope already grants `users.watch` permission.
- **Liquibase changeset numbering** — last is `009`; this phase adds `010-013`.
- **CLAUDE.md Conventions section** (Phase 01.5 D-D4) — `GmailPubSubController` follows thin-controller pattern; entities stay class + Lombok-free; new `IdentifiedEnum` follows `fromId` fail-loud convention; all log statements follow `event=opaque_name tenantId={}` format.
- **`frontend-design` skill mandatory before JSX changes** (Phase 01.5 D-D1, persistent feedback memory) — applies to `PauseBanner` + Settings toggle render.
- **Raw shadcn first** (Phase 01.5 D-C1) — no new wrapper primitives in 2A.
- **No barrel `index.ts` in `features/<name>/`** (Phase 01.3 D-A5) — deep imports.

### Integration points

- **Pub/Sub controller ↔ `PubSubOidcAuthFilter`** — filter authenticates request, stashes verified `email` claim into request attribute; controller reads it for sanity check (`request.email == configured SA email`).
- **Pub/Sub controller ↔ `gmail_connections` lookup** — `LOWER(google_email)` query; bind `TenantContext.TENANT` from result.
- **Pub/Sub controller ↔ `pubsub_delivery` table** — INSERT with ON CONFLICT DO NOTHING; respond 200 immediately.
- **`GmailHistoryProcessor` ↔ `pubsub_delivery`** — SKIP LOCKED scan; fan-out to `mail_message_observed`; `status` advance.
- **`GmailHistoryProcessor` ↔ `RefreshTokenCipher`** — decrypt refresh-token envelope, refresh access token via Google OAuth token endpoint, build Gmail client.
- **`GmailHistoryProcessor` ↔ `gmail.users().history().list(...)`** — bounded query (`maxResults=500`, `historyTypes=['messageAdded']`, `labelId='INBOX'`); 404 → HISTORY_LOST flow.
- **`GmailHistoryProcessor` ↔ `mail_message_observed`** — INSERT ON CONFLICT DO NOTHING per messagesAdded entry where labels include INBOX.
- **`GmailHistoryProcessor` ↔ `gmail_connections.last_synced_history_id`** — monotonic-conditional UPDATE.
- **`GmailWatchScheduler` ↔ `gmail_connections`** — SKIP LOCKED scan with NULL-or-near-expiry WHERE clause; UPDATE `watch_*` columns + `ingestion_health` on success/failure.
- **`GmailWatchScheduler` ↔ `gmail.users().watch(...)`** — INBOX-only; topicName from env var.
- **`GmailConnectionService.disconnect` ↔ `gmail.users().stop(...)`** — best-effort cleanup.
- **`TenantService.setTriagePaused` ↔ `tenants.triage_paused`** — single-column UPDATE under TenantContext.
- **`/me` endpoint ↔ extended response** — single read covers triagePaused + gmailConnectionStatus (no N+1).
- **`PauseBanner` ↔ TanStack Query `me` cache** — read `triagePaused`; `useToggleTriagePause` invalidates `me` key on success.
- **`ReconnectPrompt` ↔ TanStack Query `me` cache** — gate logic now reads `gmailConnectionStatus.ingestionHealth`.
- **OpenAPI codegen pipeline** — backend exposes `PUT /tenant/triage-pause` + extended `MeResponse`; `pnpm generate:api` regenerates `schema.d.ts`.

</code_context>

<specifics>
## Specific Ideas

- **Pub/Sub OIDC ceremony is THIS PHASE's deliverable.** Phase 01.5 D-D5 deferred it; Phase 2A closes it with the `PubSubOidcAuthFilter` + verification protocol. After Phase 2A ships, the STATE.md blocker entry for "Pub/Sub OIDC verification ceremony" is removed.
- **One worker, one schedule cadence target — minute-tick for register/renew, second-tick for history fan-out.** Don't conflate; they read/write different tables with different SLA windows.
- **`pubsub_delivery` IS the v1 ingress queue.** Don't introduce a separate "outbox" table for ingress. CLAUDE.md mentions `outbox` + `processing_job` — `outbox` is the egress (write-actions) for Phase 4; `processing_job` is the triage worklist for Phase 4. `pubsub_delivery` is its own ingress concern, keep separate.
- **`mail_message_observed` is the audit trail Phase 4 will verify.** ROADMAP success #3 ("Replaying the same Pub/Sub delivery a second time produces no duplicate downstream effects — verifiable via audit trail in Phase 4") = Phase 4 reads this table. Don't blur with triage state — keep it append-only + privacy-floor.
- **Privacy non-negotiable on `mail_message_observed`.** No subject, no from, no body, no snippet, no recipient. Zero email content of any kind. Just IDs + label snapshot. ArchUnit rule (Phase 1 FND-04) and Logback scrub (FND-03) catch leakage at code-review time; the schema itself is the first line of defense.
- **History 404 advances pointer, drops the gap — by design.** "no full mailbox rescan" means we accept that messages in the gap window between last_synced and current are lost to triage. This is correct behavior — Zero Mail is real-time triage, not batch import. Surface the loss via ReconnectPrompt; let the user re-grant.
- **Watch register inside OAuth handler is rejected even though it's the lowest-latency path.** The handler stays trivial + login critical-path stays fast. 0–60s latency is acceptable; user is on the template-selector during this window.
- **Single OAuth flow handles both first-connect AND reconnect after history-loss.** `/tenant/connect-gmail` (Phase 01.5 D-A5 with `prompt=consent`) is the universal recovery entry. Don't introduce a new endpoint for "refresh watch only" — adds API surface for a corner case the OAuth re-grant already handles.
- **Pause flag is one bit; Phase 4 reads it.** Phase 2A doesn't gate anything except providing the toggle. Resist scope creep into "implement the pause gate end-to-end" — the gate point doesn't exist yet (no triage_job table, no triage worker). Phase 4 will read this flag at its own enqueue time.
- **PauseBanner non-dismissible by design.** "Globally paused" is a state the user explicitly opted into; an automatically-dismissed banner risks them forgetting and assuming triage is running.
- **Single shared Pub/Sub topic + subscription scales with the v1 user base.** At <1000 active tenants and <50 msg/s ceiling (CLAUDE.md), one subscription handles it. Per-tenant topology is a Phase 6+ scaling concern.
- **`labelIds: ['INBOX']` not `INBOX+SENT`.** v1 doesn't have Reply-Tracker; subscribing SENT doubles Pub/Sub volume + worker load for zero v1 value.
- **Bounded history window = 500 items.** Inbox-zero default. Catches the "user reconnects after a week of disconnect" tail case without runaway processing.
- **Test strategy mirrors Phase 01.2.1 P03**: `PostgresContainerTest` for entity round-trip, `RestClient + LocalServerPort` for controller integration (NOT MockMvc — the OIDC verification filter needs the full filter chain).
- **OIDC verification test fixture must be hermetic.** Mock Google's JWKS endpoint + sign synthetic ID tokens with a generated keypair; assert verification accepts valid + rejects (a) wrong audience, (b) wrong email, (c) expired, (d) bad signature, (e) wrong issuer. This is the contract Phase 01.5 D-D5 said must be drilled.
- **Worker idempotency under crashes**: `pubsub_delivery.status` advances PENDING → PROCESSED only after `mail_message_observed` writes commit + `gmail_connections.last_synced_history_id` updates commit. Single transaction. If worker crashes mid-fan-out, restart re-locks the row (lock released on connection close), `mail_message_observed` ON CONFLICT skips already-written rows, `last_synced_history_id` monotonic-conditional UPDATE skips no-op. Net: at-least-once worker semantics + idempotent observation = exactly-once observation.
- **Audit log emit policy**: per-batch (`event=gmail_history_processed tenantId={} batch_size={N} new_observations={M}`) for normal traffic; per-event for state changes (`event=gmail_history_lost`, `event=gmail_watch_unhealthy_threshold`, `event=triage_pause_toggled`). Don't flood logs with per-message events.

</specifics>

<deferred>
## Deferred Ideas

These surfaced in scope or thinking but belong to other phases:

- **Phase 4 triage_job enqueue + write-action gate.** Phase 4 reads `tenants.triage_paused` and `mail_message_observed`. Out of scope for 2A.
- **LLM gateway / triage logic / rules engine.** Phases 2C, 3, 4.
- **BYOK key handling, billing credit deduction on triage.** Phases 2B, 2C.
- **Sent-side detection / Reply-Tracker / thread reply-status.** Inbox-zero feature; deferred until Phase 4+ confirms need.
- **Label-change observation (`labelsAdded` / `labelsRemoved` history events).** Defer until rule engine surfaces a need.
- **Per-tenant Pub/Sub topic + subscription topology.** Multi-tenant isolation feature; revisit at Phase 6 scale-hardening.
- **GCP-side dead-letter topic / DLQ tuning.** v1 uses app-layer `pubsub_delivery.status='DEAD'` after 3 retries; revisit if SLA concerns surface.
- **Pub/Sub topic + subscription IaC (Terraform / `gcloud` script).** Manual setup + RUNBOOK.md for v1; automate at Phase 6 launch hardening.
- **Multi-account / workspace support.** Phase-bound; deferred to dedicated phase post-v1.
- **`tenant_settings` table** (when multi-flag settings accumulate beyond `triage_paused`).
- **Per-message `event=` log entries** for triage debugging. v1 uses per-batch summary; reconsider with observability phase.
- **Sentry/Datadog/OTel browser SDK.** Observability phase.
- **Frontend UI for "view recent observations" / inbox-message list.** Phase 5 (web UI / analytics + audit dashboard).
- **`messages.list` full-mailbox import on first connect / reconnect.** Explicitly rejected by ROADMAP success #5 (no full mailbox rescan). v1 is real-time triage starting from the moment of grant.
- **History pagination beyond first 500.** Drop with log; revisit if observed in production.
- **Per-tenant Gmail API quota tracking + adaptive backoff.** Worker uses simple constant retry; observability phase + load patterns inform finer tuning.
- **Webhook signing secret rotation drill** (separate from refresh-token rotation). Phase 6.
- **Real-time SSE / WebSocket "new mail" stream to UI.** Future polling-list endpoint phase or Phase 5 dashboard.
- **`SearchUsers.watch` finer label filters** (only un-read, only INBOX without `IMPORTANT`, etc.). Defer to rule engine in Phase 3.
- **Watch-renewal failure escalation to email/SMS/push.** v1 surfaces via UI banner only; out-of-band channels in observability phase.
- **Soft-delete vs hard-delete of `pubsub_delivery` rows after retention window.** v1 keeps everything; retention policy in Phase 6.
- **`mail_message_observed` partitioning by month / time-bucket.** BRIN index on `observed_at` is enough at v1 scale.
- **`OAuth2AuthorizedClientService` migration to Spring Session Redis** (out-of-band from current JDBC). Phase 6.
- **CASA-side write-action approval workflow extension.** External CASA verification track (Phase 1 → Phase 6 timeline).

### Reviewed Todos (not folded)

_No todos reviewed — manual scan of `.planning/todos/pending/` found no matches for Phase 2A scope. The two pending items (`wr-06-test-profile-securityconfig-slice`, `worker-application-yml-fail-fast-parity`) are infra-quality items unrelated to mail ingestion._

</deferred>

---

*Phase: 02A-mail-ingestion*
*Context gathered: 2026-04-28*
