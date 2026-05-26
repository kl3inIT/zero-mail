# Requirements: Zero Mail v1.2

**Defined:** 2026-05-19
**Milestone:** v1.2 — Admin Console Foundation + Settings UI on Curated Catalog
**Core Value:** AI auto-triage that users trust with their real Gmail inbox. v1.2 adds an operator admin console + curated LLM catalog + Inbox Zero-style rule examples/actions + 4-tab user Settings **without weakening the trust posture** (explicit user opt-in for outbound automation, no email-body persistence, send execution behind one audited gateway boundary, tenant isolation) and **without leaking new content surfaces** (no admin can read tenant email body, chat content, prompts, or completions).

> v1.0 requirements (AUTH, FND, MAIL, BILL, LLM, RULE, TRG, DRFT, ANL, WEB) and v1.1 chat requirements (CHAT, ARCH-01..07, SET-SAFE-05) are SHIPPED and archived at `.planning/milestones/v1.0-REQUIREMENTS.md` and `.planning/milestones/v1.1-REQUIREMENTS.md`. This document covers v1.2 net-new requirements + the 19 v1.1-deferred SET-* requirements carried forward.

---

## v1 Requirements

### Operations / Infrastructure (NEW — Phase 8B.0 prerequisite)

- [x] **OPS-INFRA-01**: Operator can deploy `decolua/9router:latest` as a sidecar Docker container in the existing `docker-compose.yml`, bound to loopback (`127.0.0.1:20128`), with `REQUIRE_API_KEY=true`, persistent SQLite volume at `/opt/zeromail/9router-data`, `JWT_SECRET` + `INITIAL_PASSWORD` overrides, and `AUTH_COOKIE_SECURE=true`
- [x] **OPS-INFRA-02**: Operator can migrate the existing VPS reverse-proxy (currently hand-managed nginx serving `apps/web` + `/api/*`) to a single `jc21/nginx-proxy-manager` container, manage routes (`web`, `api`, `9router-dashboard`) through NPM admin UI, and auto-renew Let's Encrypt certs through NPM. Existing OAuth callback URLs (Google) MUST remain bit-for-bit identical after migration
- [x] **OPS-INFRA-03**: Operator has a written runbook at `docs/ops/v1.2-deploy.md` covering: (a) zero-downtime migration steps from manual nginx → NPM, (b) 9Router sidecar first-run setup (default password reset, API-key generation, provider account connection), (c) rollback procedure if NPM/9Router fail, (d) backup of NPM `/data` + 9Router SQLite volumes

### Admin Foundation — Auth, Routing, Audit (NEW)

- [x] **ADMIN-01**: Admin authenticates at `admin.zeromail.com` via Spring Security 7 `.webAuthn(...)` DSL (hardware-bound passkey, `userVerificationRequirement=REQUIRED`) on a dedicated `@Order(1) SecurityFilterChain` with `securityMatcher("/api/admin/**")`. NOT Google OAuth, NOT password, NOT HTTP Basic. Admin identity stored in `admin_users` table (separate from `users`). User-facing `users` table gains NO `role` column. Pivoted from Google-OAuth-bundled design 2026-05-19 during discuss-phase research.
- [x] **ADMIN-02**: Admin chain (`@Order(1)`) and user chain (`@Order(2)`) never share auth method or authority. Request to `/api/admin/*` without WebAuthn session returns 401 at chain level; admin with valid session returns 200. Explicit `@PreAuthorize("hasRole('ADMIN')")` per `@RestController` in `controllers/admin/` for defense in depth. ArchUnit rules `every_admin_controller_must_have_preauthorize` and `admin_chain_does_not_use_oauth2login` enforced in CI.
- [x] **ADMIN-03**: First-admin bootstrap via Liquibase seed of `admin_users` row(s) from `zeromail.admin.bootstrap-emails` config + Spring Boot startup runner that prints one 10-min one-time enrollment URL per PENDING_ENROLLMENT row to STDOUT (never log file, never DB). Admin uses URL to complete WebAuthn registration ceremony → row status `PENDING_ENROLLMENT` → `ACTIVE`. Operator can later grant admin via audited `POST /api/admin/grant-admin` (admin-only) returning fresh one-time URL communicated out-of-band to target.
- [x] **ADMIN-04**: Every admin action (catalog edits, master-key set/rotate, tenant pause/disconnect/delete, role grants/revokes) writes one row to `admin_audit_event` in the SAME transaction as the state mutation. Row contains: `actor_user_id`, `actor_email`, `action`, `target_kind`, `target_id`, `before_state_json`, `after_state_json`, `reason VARCHAR(500)`, `request_ip`, `request_id`, `created_at`, `hmac_chain_hash`
- [x] **ADMIN-05**: Every admin READ that touches tenant data (Tenant detail view, audit log query) writes one row to `admin_read_event` (separate from `admin_audit_event`); 30-day retention for reads, indefinite retention for actions
- [x] **ADMIN-06**: Admin frontend is a NEW separate `apps/admin` Vite + React 19 SPA (no SSR, no SEO, no Next.js) served at `admin.zeromail.com` via NPM proxy with its own Let's Encrypt cert; admin-schema TypeScript client lives only in `apps/admin/src/lib/api/`. Public `apps/web` Next.js bundle ships ZERO admin schema types and zero admin route code. Persistent "ADMIN MODE" banner inside `apps/admin` chrome for destructive-action context. Pivoted from `(admin)` Next.js sibling route group design 2026-05-19 during discuss-phase.
- [x] **ADMIN-07**: Admin can view paginated `admin_audit_event` log filtered by actor / action / target / date range, with CSV export (max 10k rows per export). Each row shows actor email + action + target + before/after diff
- [x] **ADMIN-08**: Destructive admin actions (tenant delete, master-key rotate, catalog disable-with-active-pins) require an in-modal confirm-twice + free-text "reason" (min 8 chars), which is recorded in the audit row
- [x] **ADMIN-09**: `admin_users` table schema (Liquibase changelog) with columns: `id UUID PRIMARY KEY`, `email VARCHAR(320) UNIQUE NOT NULL`, `display_name VARCHAR(200)`, `user_handle BYTEA NOT NULL UNIQUE`, `status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_ENROLLMENT','ACTIVE','REVOKED'))`, `credential_id BYTEA UNIQUE`, `public_key_cose BYTEA`, `signature_counter BIGINT DEFAULT 0`, `aaguid UUID`, `attestation_format VARCHAR(50)`, `last_used_at TIMESTAMPTZ`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `revoked_at TIMESTAMPTZ`, `revoked_reason VARCHAR(500)`. App DB user has INSERT + SELECT + UPDATE (last_used_at, signature_counter, status) only; DELETE forbidden — revocation via `status='REVOKED'` for audit trail.
- [x] **ADMIN-10**: WebAuthn enrollment + assertion ceremonies wired to Spring Security 7 `.webAuthn(...)` DSL. Enrollment: `EnrollmentTokenGate` filter validates one-time token + PENDING row → triggers `POST /webauthn/register/options` + `POST /webauthn/register` → row transitions ACTIVE + credential stored. Assertion: `POST /login/webauthn/options` + `POST /login/webauthn` → session issued + `signature_counter` incremented; downgraded `signCount` rejected + `WEBAUTHN_REPLAY_SUSPECTED` audit row written; REVOKED row returns 401; `userVerificationRequirement=REQUIRED` enforced. Lost-passkey recovery via shell access only (out of scope v1.2 UI).

### Master Keys / Platform Provider Config (NEW)

- [x] **MKEY-01**: Admin can edit the 6 platform provider entries through one unified form at `/admin/master-keys/<provider>`: `OpenAI, Anthropic, Google, DeepSeek, OpenRouter, 9Router`. Form fields per provider: `{api_key, base_url, enabled, notes}` — same shape as the user-facing BYOK form, with `api_key` AES-GCM-encrypted at rest via the existing `RefreshTokenCipher`, never returned to the frontend after save, never logged
- [x] **MKEY-02**: `base_url` is editable for ALL 6 providers (not just 9Router) — defaulting to each provider's official URL, but admin can override to point at a corporate proxy / Vertex AI / Bedrock-compatible endpoint / self-hosted gateway
- [x] **MKEY-03**: Admin can test-connection per provider — backend calls the provider's lightweight discovery endpoint (`/v1/models` for OpenAI-shape; provider-specific for Anthropic/Google) using the saved master key. Response strips provider error bodies; returns only an enum: `OK | INVALID_KEY | RATE_LIMITED | NETWORK_ERROR | TIMEOUT`. Rate-limited to 10 test-connections per admin per hour
- [x] **MKEY-04**: Admin can rotate a master key: enter new key → backend test-connects → on success, encrypts new + stores old in `previous_encrypted_key` for grace window → emits `MasterKeyRotatedEvent` → eviction of every cached `ChatModel` instance for that provider across ALL tenants. On test-connect failure, rotation aborts, old key stays ACTIVE, audit row records the failed attempt
- [x] **MKEY-05**: Admin can pick a **per-feature default provider** for `chat`, `triage`, and `draft` features from the 6 providers. Default-provider selection is what platform users fall back to when they have NOT set their own BYOK. v1.0 hard-coded default `OpenRouter` is preserved for all three features at v1.2 launch and admin can rebind per-feature after
- [x] **MKEY-06**: 9Router-specific dual-mode — admin can toggle the 9Router master-key entry between `OPENAI_FORMAT` (default; calls `/v1/chat/completions` via Spring AI OpenAI adapter at the configured base-url) and `ANTHROPIC_FORMAT` (calls `/v1/messages` via Spring AI Anthropic adapter at the configured base-url with `anthropic-version: 2023-06-01`). All other providers are single-mode
- [x] **MKEY-07**: Admin sees masked display only (`sk-****abc1`) for every saved key. There is no "reveal key once" affordance, no copy-to-clipboard for the plaintext key, no `GET /api/admin/master-keys/<provider>?reveal=true`
- [x] **MKEY-08**: Admin sees a Dependents count per master-key entry: how many tenants are currently configured to use this provider as platform default for chat/triage/draft. Disabling a provider that has Dependents > 0 requires confirm-twice + a written reason

### Curated LLM Catalog (NEW)

- [ ] **CAT-01**: Admin can view the catalog at `/admin/catalog` as a per-provider × per-feature matrix. Each cell shows: model display name, model wire ID, enabled toggle (default/recommended badge), cost per 1k input/output tokens (if known), deprecation tag (if applicable), last-synced-at
- [ ] **CAT-02**: Admin can trigger Sync-from-`/models` per provider — a 3-step flow: (1) Fetch (async via existing `processing_job` SKIP LOCKED queue, debounced by Redis lease `admin:catalog:sync:<provider>` 60s TTL), (2) Diff (admin reviews additions / removals / metadata changes against the live catalog), (3) Confirm. Auto-apply is forbidden
- [ ] **CAT-03**: Catalog Sync uses provider-specific `/models` endpoints (OpenAI-shape: `GET /v1/models`; OpenRouter: `GET /api/v1/models`; DeepSeek: `GET /v1/models`; Google: `GET /v1beta/models`; 9Router: `GET /v1/models`). Anthropic-direct has NO `/models` endpoint — admin enters Anthropic models manually + a Liquibase data seed pre-populates current Claude family. Sync button on the Anthropic-direct provider page is disabled with a tooltip explaining the manual workflow
- [ ] **CAT-04**: Sync flow validates every fetched model against a strict per-provider JSON Schema; rejects rows that fail validation; logs rejections to the admin audit. Model IDs must match `^[a-zA-Z0-9._:/\-]{1,128}$` allow-list regex
- [ ] **CAT-05**: Admin can soft-delete a model (sets `deprecated_at`, hides from user pickers, keeps existing tenant pins functional); a separate hard-delete flow exists for models with zero dependents only
- [ ] **CAT-06**: 9Router-specific combos (entries with `owned_by:"combo"` in `/v1/models`) appear as standalone catalog entries with a "combo" badge; admin curates them like any other model
- [ ] **CAT-07**: Admin can bind a model to features (chat / triage / draft) via `feature_binding` table — each (model × feature) pair is independently enabled/disabled/recommended. Foreign key + unique constraint prevent stale pins from `assistant_settings.{chat|triage|draft}_model_id`

### Tenant Read-Only Views (NEW)

- [x] **OPS-TENANT-01**: Admin can browse a paginated list of tenants at `/admin/tenants` with columns: connected email, status (CONNECTED/DISCONNECTED/PAUSED/DELETED), last Gmail event, watch expiry, credit balance, total spend last-30d, member-since. List excludes raw email content
- [x] **OPS-TENANT-02**: Admin can drill into a tenant at `/admin/tenants/<tenantId>` with 5 tabs: **Overview** (connection state, watch state, pause toggle), **Health** (Pub/Sub delivery, last events count, error rate), **Billing** (ledger balance, holds, recent top-ups), **Spend** (per-feature LLM cost over selectable window), **Activity** (chat session count, last activity timestamp, model selection — NO chat content, NO email body, NO prompts/completions)
- [x] **OPS-TENANT-03**: Admin can pause, disconnect, or delete a tenant from the Tenant detail view; each action requires confirm-twice + reason and writes to `admin_audit_event`. Delete shows preview counts (messages, rules, audit rows that will be removed) before final confirm
- [x] **OPS-TENANT-04**: All admin tenant-projection DTOs are served by Spring Data JDBC `Repository<...>` interfaces (NOT `CrudRepository`, NOT JPA). Field names matching `body|bodyHtml|snippet|payload|prompt|completion|content` are forbidden in admin projection DTOs at compile time via ArchUnit
- [x] **OPS-TENANT-05**: An `AdminResponseBodyBanFilter` (Spring MVC filter, runs after admin controllers) scans the outbound JSON for any string field >200 chars whose key matches the forbidden regex AND rejects the response with HTTP 500 + an audit row, as a defense-in-depth failsafe against future leaks

### Worker Queue Health (NEW)

- [x] **OPS-QUEUE-01**: Admin can view at `/admin/queue` real-time read-only aggregates over existing `outbox` + `processing_job` tables: depth by job type, oldest-unleased age, retry-count distribution, failure rate (last 1h / 24h), dead-letter row count. UI auto-refreshes every 10s
- [x] **OPS-QUEUE-02**: Admin can re-queue a dead-letter row from the dead-letter list view (sets state back to PENDING, increments retry counter, writes audit row). Cannot view the row payload (which may contain tenant-scoped context); cannot manually edit row fields

### Global LLM Spend Dashboard (NEW)

- [x] **OPS-SPEND-01**: Admin can view at `/admin/spend` a metadata-only spend dashboard aggregating existing `llm_call_audit` rows (which already store no prompt/completion content). Top-line cards: today / last 7d / last 30d total cost split platform-vs-BYOK. Charts: stacked bar by provider, donut by feature (chat/triage/draft), top-N tenants table (N=20)
- [x] **OPS-SPEND-02**: Spend dashboard supports a date-range picker (max 90 days) and respects k-anonymity: the top-N tenants table hides rows where the tenant's email cannot be resolved (deleted tenants show as "[deleted]"). No drill-down to per-prompt detail, no WebSocket streaming, no public sharing URL

### Settings Page — Personalization (carried from v1.1)

- [ ] **SET-VOICE-01**: User can edit free-text writing style description (200–500 words) that influences AI draft tone
- [ ] **SET-VOICE-02**: User can edit free-text personal instructions ("About me") that gets injected into the system prompt for chat/triage/draft (XML-fenced + sanitized for prompt-injection sentinels + length cap 2000 chars)
- [ ] **SET-VOICE-03**: User can edit free-text email signature appended to AI drafts
- [ ] **SET-VOICE-04**: User can manage a list of titled knowledge-base snippets the AI consults when drafting
- [ ] **SET-VOICE-05**: User can pick a tone preset (professional / friendly / casual / formal / custom) as a quick baseline
- [ ] **SET-VOICE-06**: User can pick AI output language (VI / EN, default VI) — separate from UI language
- [ ] **SET-VOICE-07**: User can trigger a "Generate from recent sent emails" action inside the writing-style edit dialog. The action fetches the most recent N sent emails (default N=20, max 50), feeds them transiently to the LLM along with a style-extraction prompt, and returns a concise style guide (≤500 words) that pre-populates the writing-style textarea for the user to review and edit before saving. Privacy invariant: raw email bodies and the LLM prompt/completion exchange MUST be in-memory-only (no DB, no log file, no audit row); only the user-reviewed-and-saved style guide is persisted (into the existing `assistant_settings.writing_style` column). Pulled into v1.2 Phase 9 scope from `SET-VOICE-FUT-03` on 2026-05-26 during discuss-phase.

### Settings Page — Behavior Toggles (carried from v1.1)

- [ ] **SET-BEHV-01**: User can toggle auto-draft replies (master switch for v1.0 DRFT-01..04 background drafts)
- [ ] **SET-BEHV-02**: User can set a draft confidence threshold (0.0–1.0); AI only saves drafts ≥ threshold
- [ ] **SET-BEHV-03**: User can toggle daily digest (reuses v1.0 ANL-03 config)
- [ ] **SET-BEHV-04**: User can toggle sensitive-data protection (controls v1.0 LLM-05 PII redaction behavior; default ON)
- [ ] **SET-BEHV-05**: User can surface the shadow-mode toggle (reuses v1.0 TRG-07) from the assistant Settings page

### Settings Page — Sender Safety Net (carried from v1.1)

- [ ] **SET-SAFE-01**: User can view, add, and remove sender safety net entries (email or domain pattern) via the Settings page (exposes existing v1.0 TRG-07..08 tables to end users for the first time)
- [ ] **SET-SAFE-02**: User can paste-import multiple entries at once with a parsed preview before save
- [ ] **SET-SAFE-03**: User can pick per-entry mode (`protect` = never auto-act, `escalate` = notify but don't act)
- [ ] **SET-SAFE-04**: User sees a visual indicator in the audit log when a rule was blocked by the safety net ("Was going to archive, blocked by VIP rule for ceo@acme.com")

### Settings Page — AI Provider/Model (carried from v1.1, rewired onto curated catalog)

- [ ] **SET-AI-01**: User has ONE BYOK card with an `Active` switch as the only on/off control. When the row is `active=true` AND has a tested model, every AI feature (chat / triage / draft / voice-generate) runs through that key+URL+model. When `active=false` (or no row), every feature falls back to the admin-curated catalog default. **Updated 2026-05-26 round 2** — no per-feature picker, no separate `Platform default ↔ Use my key` mode card; the `active` flag on the BYOK row replaces both
- [ ] **SET-AI-02**: BYOK row holds provider (OpenAI / Anthropic / Google / DeepSeek only — NEVER OpenRouter, NEVER 9Router), base URL (auto-filled per provider, user-editable to support OpenAI-compatible / Anthropic-compatible endpoints; validated as `https://` except `http://localhost*` for dev), API key (AES-GCM encrypted via v1.0 LLM-04 / `RefreshTokenCipher`, never logged, never returned to the frontend after save — only `lastFourChars`), and a user-picked model from the Test-connection response. Saving any field clears `last_test_result` and `last_tested_at` and forces `active=false`. Switching providers/URLs/keys replaces the single tenant row
- [ ] **SET-AI-03**: User sees a single tenant-wide last-7d AI cost figure below the BYOK card (e.g. `Chi phí AI 7 ngày qua: $2.43`). **Updated 2026-05-26** — per-feature cost rows removed; aggregation is a single tenant-scoped sum from existing `llm_call_audit` rows, no `call_site=CHAT` schema change required
- [ ] **SET-AI-04**: User can test the BYOK connection (either against the stored row OR an inline-payload pre-save) using the same `/v1/models` probe and enum-only response shape (`OK / INVALID_KEY / RATE_LIMITED / NETWORK_ERROR / TIMEOUT`) as admin MKEY-03. On `OK` the response additionally carries `models[]` (provider's chat-completion-capable model IDs, capped at 100) so the user can pick a model from the result. Both admin and user paths delegate to a shared `ProviderConnectionTester` (D-14). Rate-limited to 10 tests/hour per tenant. Activating the BYOK row requires the last test to be `OK` AND a model to be picked, otherwise `PUT /api/byok/active` returns HTTP 400 `code=ai.byok.no_model_picked`

### Rule Actions and Examples Catalog (NEW — Phase 08.1)

- [x] **RACT-01**: Rule creation offers three entry paths matching the Inbox Zero mental model: `Create rules`, `Choose from examples`, and `Add manually`
- [x] **RACT-02**: User examples UI includes the copied Inbox Zero persona set (`Founder`, `Influencer`, `Realtor`, `Investor`, `Assistant`, `Developer`, `Designer`, `Sales`, `Marketer`, `Support`, `Recruiter`, `Student`, `Outreach`, `Other`) and the example prompt grid seeded from `.planning/phases/08.1-inbox-zero-style-rule-actions-and-admin-managed-examples-cat/inbox-zero-examples.ts`
- [x] **RACT-03**: Admin can create, edit, disable, reorder, and localize examples/personas/action descriptors without code changes; disabled examples do not appear in the user rule builder
- [x] **RACT-04**: User-facing Available Actions panel includes `Label`, `Archive`, `Save draft`, `Mark read/unread`, `Star/unstar`, `Add to digest`, `Mark spam`, `Send reply`, `Forward`, and `Send email`, with unavailable actions visibly disabled and explained
- [x] **RACT-05**: Settings expose one account-level `Auto-send rules` toggle for automated outbound rule actions; it defaults ON for new users/tenants and there are no individual outbound action toggles
- [x] **RACT-06**: Manual editor and AI compiler both persist the same structured `When/Then` schema; natural language remains only `sourceText`/audit metadata
- [x] **RACT-07**: Rule-triggered outbound actions execute only when the global auto-send setting, sender-risk guard, safety net, cap/rate-limit, idempotency, OAuth scope, tenant checks, and audit reservation all pass
- [ ] **RACT-08**: If an outbound gate fails or the global auto-send setting is OFF, the rule result falls back to Gmail `save_draft` with an audit reason; the system must not silently drop or send the email
- [x] **RACT-09**: All Gmail send execution goes through one shared outbound gateway/send executor; ArchUnit/grep tests are updated to allow that boundary and fail any direct Gmail send call site elsewhere
- [x] **RACT-10**: Privacy constraints remain intact: no long-term storage of Gmail-read email bodies, LLM prompts/completions, or embeddings; persisted draft bodies are allowed only when they are user-authored/action arguments under the existing draft-body carve-out
- [ ] **RACT-11**: Low-trust/static sender protections equivalent to Inbox Zero's example-risk guard prevent users from saving demo examples that would send to real people by accident
- [x] **RACT-12**: UAT covers examples import, admin catalog management, outbound setting gates, downgrade-to-draft behavior, and no-bypass architecture tests

### Architecture Invariants — Phase 8 baseline, extended by Phase 08.1

- [x] **ARCH-08**: `AdminContext` is a `ScopedValue` mutually exclusive with `TenantContext` — entering admin scope clears the tenant binding and vice versa. Cross-tenant admin reads route through `AdminTenantAccess.readOnly(tenantId, supplier)` which writes one `admin_read_event` row before invoking the supplier. ArchUnit rule forbids admin packages from reading `TenantContext` directly
- [x] **ARCH-09**: ArchUnit `AdminPathBodyBanTest` enforces that classes under `..controllers.admin..` and `..core.admin..projection..` cannot reference `GmailClient` body-exposing methods, `ChatMessageRepository.findContent*`, `LlmCallAudit.prompt*` / `.completion*` field accessors, or any field named per the forbidden regex `body|bodyHtml|snippet|payload|prompt|completion|content`. Test runs in CI
- [x] **ARCH-10**: Phase 8 baseline single Gmail send call-site invariant from v1.1 ARCH-01 holds until Phase 08.1 replaces it with the stricter gateway-boundary rule in RACT-09; admin packages are forbidden by ArchUnit from referencing Gmail send methods entirely. Master-key test-connection uses `GET /v1/models` (or per-provider equivalent), never a send method
- [x] **ARCH-11**: A `MasterKeySentinelLeakTest` runs in CI and asserts that no log line, no admin response body, no exception message, no `application*.yml`, and no audit row contains any of the sentinel prefixes `sk-`, `sk-ant-`, `AIza`, `sk-or-` (or their masked-encoded forms). The test seeds dummy sentinel-prefixed master keys, exercises every admin endpoint that touches them, and greps the captured logs + responses
- [x] **ARCH-12**: `admin_audit_event` is append-only at the database level — the application DB user has no `UPDATE` or `DELETE` privilege on the table; a Postgres `BEFORE UPDATE OR DELETE` trigger raises `EXCEPTION` regardless of role; per-row `hmac_chain_hash` chains to the previous row's hash; a nightly verification job re-derives the chain and alerts on mismatch

---

## Future Requirements (v1.3+ candidates)

### Deferred from v1.2 scope decision (2026-05-19)

- **VISUAL-REFRESH-01..06**: Audit + align Phase 7 chat UI, Settings, Triage, Rules, Analytics pages with PR #40 purple brand palette (teal → purple); fix hardcoded color/visual-hierarchy regressions across the user-facing surface
- **EVAL-01..05**: Hostile-corpus `aiEval` suite — 15 hostile emails + 10 hostile personal_instructions + VIP send refusal + VI/EN fidelity + tool-call safety against jailbreaks
- **OPS-DASH-01..04**: Grafana dashboards — lease residuals, audit-vs-state mismatch, ordering violations, leak counters, BUDGET_EXHAUSTED rate
- **CASA-01**: CASA evidence refresh for chat surface (extends v1.0 FND-07)
- **LAUNCH-01**: LAUNCH-GO-NOGO checklist for v1.2 GA
- **GA-01**: Formal v1.2 GA tag

### 9Router Expansion

- **NR-OAUTH-01**: OAuth-provider connection runbook (Claude Code Pro, Codex Pro, GitHub Copilot subscriptions) routed through 9Router for platform-cost savings
- **NR-COMBO-01**: First-class combo authoring UX (vs. catalog row representation) — admin builds combos directly in Zero Mail admin UI rather than 9Router dashboard

### Provider Expansion (carried from v1.1 deferred list)

- **SET-AI-EXP-01**: Bedrock provider via `@ai-sdk/amazon-bedrock`
- **SET-AI-EXP-02**: Azure OpenAI provider via `@ai-sdk/azure`
- **SET-AI-EXP-03**: Groq, Perplexity, OpenAI-compatible (self-hosted), Google Vertex (Workspace orgs) — expand from 6 to ~11 platform providers
- **OPS-03**: CASA production verification (SEED-012) — unblock OAuth Testing-mode 100-user cap and 7-day re-consent expiration

### Operational Surfaces

- **OPS-04**: Waitlist + semi-automated OAuth test-user provisioning (`/waitlist` form, admin paste-to-Google-Console flow) — deferred from v1.1
- **OPS-05**: Tenant chat-session content inspection via explicit time-boxed tenant-grant flow (support escalation)

### Chat Enhancements (carried from v1.1)

- **CHAT-FUT-01**: Image attachments — multimodal via Spring AI `Media` API
- **CHAT-FUT-02**: Reasoning blocks — for Anthropic/select OpenRouter routes
- **CHAT-FUT-03**: Context-pack injection — inbox stats + rule snapshot in system prompt
- **CHAT-FUT-04**: Stale-rules detection
- **CHAT-FUT-05**: 30-second soft undo on send

### Personalization Differentiators (carried from v1.1)

- **SET-VOICE-FUT-01**: Knowledge snippet auto-tagging (suggested when to apply)
- **SET-VOICE-FUT-02**: Per-recipient tone (formal for boss, casual for friends)
- **SET-VOICE-FUT-03**: ~~Voice import from past sent mail (in-memory only, immediate discard)~~ — **Pulled into v1.2 Phase 9 as `SET-VOICE-07` on 2026-05-26.**

### Tool Extensions (carried from v1.1)

- **TOOL-FUT-01**: Learned patterns (requires learning loop + persisted derived features — privacy review needed)
- **TOOL-FUT-02**: Multi-rule selection (advanced — allow AI to apply multiple rules per email)
- **TOOL-FUT-03**: Browser extension sync (Inbox Zero Tabs extension)
- **TOOL-FUT-04**: Sender categorization tools
- **TOOL-FUT-05**: Calendar tools (require new Google Calendar scope)
- **TOOL-FUT-06**: Attachment reading tools (require Drive scope or attachment storage)

---

## Out of Scope

Explicit exclusions for v1.2. Each row carries the reason so we don't silently re-add later.

| Feature | Reason |
|---------|--------|
| Ungated outbound automation | Hard ban — rule-triggered send/reply/forward is allowed only behind Phase 08.1 global auto-send setting, sender-risk guard, safety net, cap/rate-limit, idempotency, OAuth, tenant, and audit gates; blocked sends fall back to Gmail draft |
| Long-term persistence of raw email body, email-content LLM prompts/completions, or embeddings | Permanent privacy invariant from v1.0. Admin views are explicitly metadata-only (OPS-TENANT-04..05 enforces) |
| Admin impersonation of a user (act-as-tenant) | Hard ban — violates trust posture; tenant authority cannot be borrowed by admin. ARCH-08 enforces |
| Admin SQL console / arbitrary query UI on `/admin/*` | Hard ban — no ad-hoc DB access through the web; ops queries go through Postgres MCP / psql via SSH |
| Arbitrary webhook execution from rules | Deferred — needs separate admin/user allow-list, secret management, retry, and data-leak review |
| Destructive delete automation from rules | Deferred — spam is allowed in Phase 08.1; permanent delete requires a separate safety review |
| Reveal-master-key-once affordance | Hard ban — MKEY-07; rotation is the only path to recover from a lost key |
| Free-text model-ID textbox in Settings AI tab | Typos → silent failures; curated catalog list only |
| User BYOK for OpenRouter or 9Router | Admin-only platform tier; users cannot set their own NINEROUTER_KEY or OpenRouter sk-or-key |
| 9Router image / TTS / STT / embeddings / web-search / web-fetch capabilities | Out of v1.2 scope — embeddings forbidden by privacy stance; chat-only ingress for v1.2 |
| 9Router OAuth-provider connections (Claude Code Pro, Codex Pro, GitHub Copilot subscriptions) | Deferred to NR-OAUTH-01; v1.2 ships API-key providers only |
| Auto-approve Sync-from-`/models` | Anti-feature; providers ship deprecated/preview models that need admin review |
| Hostile-corpus aiEval suite | Deferred to v1.3+ EVAL-01..05 |
| Grafana ops dashboards | Deferred to v1.3+ OPS-DASH-01..04 |
| CASA evidence refresh + LAUNCH-GO-NOGO + formal GA tag | Deferred to v1.3+ — v1.2 ships without GA tag |
| Visual refresh of user pages (purple brand palette alignment) | Deferred to v1.3+ VISUAL-REFRESH-01..06 |
| Replacing Postgres as primary datastore for catalog/audit | Locked — same VPS Postgres 17, no separate auth DB, no Vault, no GCP/AWS KMS |
| Stateless JWT for admin session | Locked NO — cookie + Spring Session Redis is the v1.0 auth model; admin RBAC layers on top, doesn't fork it |

---

## Traceability

Phase-to-requirement mapping (populated by gsd-roadmapper 2026-05-19).

| Requirement | Phase | Status |
|-------------|-------|--------|
| OPS-INFRA-01 | Phase 8 | Complete |
| OPS-INFRA-02 | Phase 8 | Complete |
| OPS-INFRA-03 | Phase 8 | Complete |
| ADMIN-01 | Phase 8 | Complete |
| ADMIN-02 | Phase 8 | Complete |
| ADMIN-03 | Phase 8 | Complete |
| ADMIN-04 | Phase 8 | Complete |
| ADMIN-05 | Phase 8 | Complete |
| ADMIN-06 | Phase 8 | Complete |
| ADMIN-07 | Phase 8 | Complete |
| ADMIN-08 | Phase 8 | Complete |
| ADMIN-09 | Phase 8 | Complete |
| ADMIN-10 | Phase 8 | Complete |
| MKEY-01 | Phase 8 | Complete |
| MKEY-02 | Phase 8 | Complete |
| MKEY-03 | Phase 8 | Complete |
| MKEY-04 | Phase 8 | Complete |
| MKEY-05 | Phase 8 | Complete |
| MKEY-06 | Phase 8 | Complete |
| MKEY-07 | Phase 8 | Complete |
| MKEY-08 | Phase 8 | Complete |
| CAT-01 | Phase 8 | Pending |
| CAT-02 | Phase 8 | Pending |
| CAT-03 | Phase 8 | Pending |
| CAT-04 | Phase 8 | Pending |
| CAT-05 | Phase 8 | Pending |
| CAT-06 | Phase 8 | Pending |
| CAT-07 | Phase 8 | Pending |
| OPS-TENANT-01 | Phase 8 | Complete |
| OPS-TENANT-02 | Phase 8 | Complete |
| OPS-TENANT-03 | Phase 8 | Complete |
| OPS-TENANT-04 | Phase 8 | Complete |
| OPS-TENANT-05 | Phase 8 | Complete |
| OPS-QUEUE-01 | Phase 8 | Complete |
| OPS-QUEUE-02 | Phase 8 | Complete |
| OPS-SPEND-01 | Phase 8 | Complete |
| OPS-SPEND-02 | Phase 8 | Complete |
| RACT-01 | Phase 08.1 | Complete |
| RACT-02 | Phase 08.1 | Complete |
| RACT-03 | Phase 08.1 | Complete |
| RACT-04 | Phase 08.1 | Complete |
| RACT-05 | Phase 08.1 | Complete |
| RACT-06 | Phase 08.1 | Complete |
| RACT-07 | Phase 08.1 | Complete |
| RACT-08 | Phase 08.1 | Pending |
| RACT-09 | Phase 08.1 | Complete |
| RACT-10 | Phase 08.1 | Complete |
| RACT-11 | Phase 08.1 | Pending |
| RACT-12 | Phase 08.1 | Complete |
| SET-VOICE-01 | Phase 9 | Pending |
| SET-VOICE-02 | Phase 9 | Pending |
| SET-VOICE-03 | Phase 9 | Pending |
| SET-VOICE-04 | Phase 9 | Pending |
| SET-VOICE-05 | Phase 9 | Pending |
| SET-VOICE-06 | Phase 9 | Pending |
| SET-VOICE-07 | Phase 9 | Pending |
| SET-BEHV-01 | Phase 9 | Pending |
| SET-BEHV-02 | Phase 9 | Pending |
| SET-BEHV-03 | Phase 9 | Pending |
| SET-BEHV-04 | Phase 9 | Pending |
| SET-BEHV-05 | Phase 9 | Pending |
| SET-SAFE-01 | Phase 9 | Pending |
| SET-SAFE-02 | Phase 9 | Pending |
| SET-SAFE-03 | Phase 9 | Pending |
| SET-SAFE-04 | Phase 9 | Pending |
| SET-AI-01 | Phase 9 | Pending |
| SET-AI-02 | Phase 9 | Pending |
| SET-AI-03 | Phase 9 | Pending |
| SET-AI-04 | Phase 9 | Pending |
| ARCH-08 | Phase 8 | Complete |
| ARCH-09 | Phase 8 | Complete |
| ARCH-10 | Phase 8 | Complete |
| ARCH-11 | Phase 8 | Complete |
| ARCH-12 | Phase 8 | Complete |

**Coverage (post-roadmap, post-pivot):**
- v1.2 requirements: **73 total** (mapping confirmed 100% coverage, zero orphans)
  - Phase 8 (Admin Console & Operator Tooling, merged 2026-05-19 + WebAuthn pivot 2026-05-19): 42 reqs — 3 OPS-INFRA + 10 ADMIN (01-10) + 5 ARCH (08/09/10/11/12) + 8 MKEY + 7 CAT + 5 OPS-TENANT + 2 OPS-QUEUE + 2 OPS-SPEND
  - Phase 08.1 (Inbox Zero-style Rule Actions & Admin-managed Examples Catalog, inserted 2026-05-23): 12 reqs — RACT-01..12
  - Phase 9 (User Settings UI on Curated Catalog): 20 reqs — 7 SET-VOICE + 5 SET-BEHV + 4 SET-SAFE + 4 SET-AI (SET-VOICE-07 pulled from `SET-VOICE-FUT-03` on 2026-05-26 during discuss-phase)
- Phase mapping: ✓ Complete
- Merge note: original Phase 8 (foundation, 15 reqs) and original Phase 9 (operator surface, 25 reqs) merged into single Phase 8 mega (40 reqs) during spec-phase 2026-05-19; former Phase 10 renumbered → Phase 9. Phase 8 then gained ADMIN-09 (admin_users schema) + ADMIN-10 (WebAuthn ceremonies) during discuss-phase pivot 2026-05-19 — pre-pivot ADMIN-01/02/03/06 + ARCH-08 also rewritten to reflect the WebAuthn + separate-frontend shape.

> **Note on pre-roadmap "57 total" tally:** the original pre-roadmap summary undercounted by 2; the actual REQ-ID inventory is 3 + 8 + 8 + 7 + 5 + 2 + 2 + 19 + 5 = 59 (the "5 ARCH" line was not added to the prior subtotal). Counted again during roadmapping; all 59 IDs above are explicit and mapped.

---

*Requirements defined: 2026-05-19*
*Last updated: 2026-05-23 — Phase 08.1 inserted and RACT-01..12 mapped (3 phases, 100% coverage)*
