# Feature Research — v1.2: Admin Console Foundation + Settings UI

**Domain:** Internal admin/support/compliance console + curated LLM catalog + Settings UI rebuilt on the curated catalog (trust-first multi-tenant SaaS, solo-operator early stage)
**Researched:** 2026-05-19
**Overall confidence:** HIGH for admin RBAC + audit log + tenant inspection patterns (well-trodden SaaS territory; multiple independent sources agree); MEDIUM for curated LLM catalog UX (newer pattern — primary reference LiteLLM proxy admin UI + OpenClaw/OpenRouter ergonomics + internal v1.0 `LlmGateway` shape); HIGH for Settings UI deferred reqs (already specified in v1.1 deferred list).
**Scope:** **Only the v1.2 milestone delta.** v1.0 + v1.1 features (auth, mail ingestion, billing, LLM gateway, rules engine, triage convergence, draft replies, analytics, chat assistant + confirmation cards, send executor) are described in `PROJECT.md` "Validated" and the prior `.planning/research/FEATURES.md` revisions — **not re-researched here.**

> Inbox Zero reference: their admin (`apps/web/app/(app)/admin/page.tsx`) is a single page gated by `isAdmin({ email })` checking `env.ADMINS` (an env-var allowlist of emails). Tools shown: `AdminUpgradeUserForm`, `AdminUserControls`, `AdminUserInfo` (email-keyed user lookup), `AdminHashEmail`, `GmailUrlConverter`, `DebugLabels`, `RegisterSSOModal`, `AdminSyncStripe`, `AdminTopSpenders`. **No** model catalog UI, **no** master-key UX, **no** tenant-level read-only inspection, **no** worker-queue panel — Inbox Zero ships those as "log into Vercel / Stripe / Postgres directly". Zero Mail's admin must go further because we're VPS-self-hosted, BYOK-by-default, and own our own LLM gateway abstraction.

---

## Executive Summary

v1.2 adds **two surfaces** stacked on top of the shipped v1.0 + v1.1 backend:

1. **Admin Console Foundation (Phase 8)** — a new `/admin/*` route tree gated by `ROLE_ADMIN`, with an append-only `admin_audit_event` log of every write the admin performs. The console exposes seven feature surfaces: (a) admin RBAC + audit; (b) curated LLM catalog with per-provider Sync-from-`/models` review flow; (c) AES-GCM-encrypted master-key management for the 4 platform providers; (d) tenant read-only inspection (connection state, watch expiry, pause, ledger holds/balance, spend timeline) **without any PII surface from email bodies / chat content / token bytes**; (e) worker queue health (Postgres outbox + `processing_job` stats); (f) promoted global LLM spend dashboard aggregating metadata-only across all tenants; (g) catalog/master-key plumbing that the user Settings AI tab consumes.

2. **User Settings UI on curated catalog (Phase 9)** — `/settings` with 4 tabs (Personalization, Behavior, Safety Net, AI Provider/Model) carrying forward the 19 deferred v1.1 reqs (SET-AI-01..04, SET-VOICE-01..06, SET-BEHV-01..05, SET-SAFE-01..04). The AI Provider/Model tab is the dependency hinge: it can only list models the admin has approved in the catalog, scoped per feature (chat / triage / draft).

**Highest-risk feature** is (c) master-key management: an AES-GCM key for OpenAI/Anthropic/Google/DeepSeek lives in app config (or a sealed `master_key` row); rotation must re-encrypt every dependent encrypted record (BYOK secrets, future webhook secrets); a botched rotate locks every BYOK user out of LLM calls. Inbox Zero does not own this problem (no app-layer encryption beyond Auth.js sessions); we own it because of v1.0 LLM-04 (`AES-GCM BYOK encryption + per-call zeroing`).

**Highest-trust risk** is (d) tenant inspection: the temptation is to ship a "view-as-user" flow because it's quick. Don't. v1 trust posture forbids admin reads of email bodies, chat content, prompts/completions, token bytes, and OAuth refresh tokens. Tenant inspection must be **read-only metadata projections only** — connection state, watch expiry, pause flag, credit balance, spend buckets, audit row counts — never the underlying PII rows.

**Lowest-risk feature** is (e) worker queue health: read-only SQL aggregates over the existing `outbox` + `processing_job` tables. Reuse Postgres MCP introspection patterns; no new schema.

**Curated catalog** is the architectural keystone of v1.2: it inverts the v1.0/v1.1 default of "user types a model ID, LLM gateway accepts it if Spring AI does". Phase 8 introduces `llm_catalog_model` (per-provider, per-feature, admin-toggled) and `llm_catalog_sync_run` (record of each Sync-from-`/models` invocation + diff). Phase 9's AI Provider/Model picker reads `llm_catalog_model` filtered to `{provider, feature, enabled=true}`.

**For roadmap:** Phase 8 has clear internal ordering — RBAC + audit framework first (everything else depends on it) → master-key + catalog schema → Sync-from-`/models` UI → tenant inspection → queue health → spend dashboard. Phase 9 can begin once `llm_catalog_model` queryable endpoints exist (does not need Sync UI complete).

---

## Feature Categories Overview (v1.2 only)

| Category | What it covers | Backend dep | Frontend dep | Risk |
|----------|---------------|-------------|--------------|------|
| **ADMIN** | `/admin/*` route gate, `ROLE_ADMIN`, admin action audit log, admin navigation chrome | New `admin_audit_event` table; `user_account.role` column (enum {USER, ADMIN}); Spring Security `hasRole('ADMIN')` on `/admin/**` | New `/admin/*` layout + sidebar; admin-only nav surfaced only when role present | MEDIUM (well-trodden but role bootstrap on a fresh VPS is fiddly) |
| **CAT** | Curated LLM catalog (per-provider × per-feature), Sync-from-`/models` flow with diff + approve, enable/disable toggles, default-model selection per feature | New `llm_catalog_model`, `llm_catalog_sync_run`, `llm_catalog_sync_diff_entry` tables; provider `/models` adapter inside the existing `LlmGateway` package | Catalog table UI per provider, model row toggle, Sync button → diff modal with checkboxes, "Set default for chat/triage/draft" per row | MEDIUM (Sync diff UX is the hard part) |
| **MKEY** | AES-GCM master-key management for the 4 platform providers — set, rotate, test-connection, last-rotated-at; also surfaces "users on this key" count | New `provider_master_key` table (encrypted), key-rotation job (re-encrypts dependent BYOK rows under new master), `/admin/keys/{provider}/test` endpoint | Per-provider card: status pill, rotate button (with confirm dialog), test-connection button → result toast, last-rotated-at timestamp | HIGH (rotation is destructive; mistakes lock out BYOK callers) |
| **OPS-TENANT** | Tenant read-only inspection: list/search tenants, view per-tenant connection state + watch expiry + pause + ledger balance/holds + spend-over-time + recent admin-relevant audit events | Read-side projections over existing v1.0 tables (`email_account`, `gmail_connection`, `credit_ledger_entry`, `triage_audit`, `chat_session` *counts only*); no new write tables | Tenant list table with filter, tenant detail page with health/billing/spend tabs (no email-body, no chat-content, no prompt-completion surfaces) | MEDIUM (must avoid leaking PII through analytics joins) |
| **OPS-QUEUE** | Worker queue health: outbox lag, processing_job depth, oldest unleased age, retry distribution, failure-rate-by-job-type, dead-letter inspection | Read aggregates over existing `outbox` + `processing_job` tables (already exist from v1.0 Phase 4) | Stat cards + small charts; "oldest unleased" age in seconds; retry histogram; dead-letter table with re-queue button (with confirm) | LOW |
| **OPS-SPEND** | Global LLM spend dashboard: aggregate metadata-only spend across all tenants, broken down by provider, feature (chat/triage/draft), and platform-vs-BYOK | Read aggregates over existing `llm_call_audit` (metadata only — never prompt/completion content) | Top-line spend cards (today / 7d / 30d), provider breakdown stacked bar, feature breakdown donut, top-N tenants table | LOW |
| **SET-AI** | User Settings AI Provider/Model tab — picks chat / triage / draft model from admin-curated catalog; manages BYOK keys per provider; shows per-provider status | New `assistant_settings.chat_model_id / triage_model_id / draft_model_id` FK to `llm_catalog_model`; reuses v1.0 `byok_credential` | shadcn `<Select>` per feature (chat/triage/draft) populated from `/api/catalog/models?feature=...`; BYOK key management cards per provider | MEDIUM (depends on CAT) |
| **SET-VOICE** | Personalization tab — writing style, personal instructions, signature, knowledge base CRUD, tone preset, AI output language | New columns on `assistant_settings`; new `assistant_knowledge_snippet` table (already specified in v1.1 deferred list) | Textareas + language toggle + knowledge-snippet CRUD list | LOW |
| **SET-BEHV** | Behavior tab — auto-draft toggle, draft confidence threshold, follow-up reminders, daily digest opt-in, sensitive-data protection | 3 booleans + 1 numeric on `assistant_settings`; reuses existing daily-digest config | Switches + threshold slider | LOW |
| **SET-SAFE** | Safety Net tab — sender VIP allow-list / never-archive / never-trash list management | Reuses existing v1.0 TRG-07..08 tables (`sender_safety_entry`) — no new schema | List, add by email/domain, remove, search | LOW |

---

## ADMIN Category — RBAC + Audit Log

### Table Stakes (Users — i.e., the admin operator — Expect These)

| # | Feature | Why Expected | Complexity | Notes |
|---|---------|--------------|------------|-------|
| ADMIN-T1 | Separate `/admin/*` route tree, hidden from non-admins (no nav link, route returns 403 not 404) | Standard internal-tool pattern; surfacing admin UI to normal users invites probing | **S** | Spring Security `.requestMatchers("/admin/**").hasRole("ADMIN")`; Next.js layout returns 403 page if `role !== 'ADMIN'` |
| ADMIN-T2 | `ROLE_ADMIN` stored on the user, not derived from an env-var email allowlist | Inbox Zero uses an env-var allowlist (`env.ADMINS`); fine for a 1-developer hobby project, but blocks adding a non-developer support operator and breaks SOC2 evidence later | **S** | New `user_account.role ENUM('USER','ADMIN')` column; default `USER`; first admin bootstrapped via Liquibase changelog targeting the founder's email |
| ADMIN-T3 | Admin action audit log — every write the admin does (rotate key, sync catalog, disable model, re-queue job, mark tenant paused-by-admin) writes an append-only `admin_audit_event` row with `(actor_user_id, action, target_tenant_id?, target_resource, before_value, after_value, ts)` | Audit immutability is the linchpin of trust + future SOC2/CASA evidence; every admin write must be reconstructable | **M** | New `admin_audit_event` table; `INSERT`-only (no `UPDATE`/`DELETE` grants); Postgres trigger refuses non-INSERT; 90+ day retention |
| ADMIN-T4 | Tenant-aware audit — every audit event with a tenant target carries `target_tenant_id` so we can later answer "show me every admin action on tenant X" | Multi-tenant SaaS audit baseline; needed before a tenant says "did you touch our account?" | **S** | Foreign-keyed column on `admin_audit_event`; nullable for global actions like "rotate OpenAI master key" |
| ADMIN-T5 | Read-action visibility — every admin **read** of a tenant page (tenant detail, ledger view, spend timeline) writes a `admin_read_event` row with `(actor, target_tenant_id, surface, ts)` | Reads of tenant data are not free in a trust-first SaaS; "who looked at my account?" matters | **S** | New `admin_read_event` table; lighter retention (30 days) than write audit |
| ADMIN-T6 | Admin can see their own recent audit log (last 50 actions) on the `/admin` landing | Self-check before doing something destructive | **S** | Read from `admin_audit_event WHERE actor_user_id = current_admin` |
| ADMIN-T7 | Admin session is the same Spring Session Redis cookie as user session — no separate admin login | Reduces credential surface; one auth path | **S** | Already in place from v1.0 AUTH-04 |
| ADMIN-T8 | Admin nav lives in a separate `/admin` layout, not bolted into user chrome | Visual separation reduces "I thought I was in my own account" mistakes | **S** | Next.js parallel route segment |

### Differentiators

| # | Feature | Value Proposition | Complexity | Notes |
|---|---------|-------------------|------------|-------|
| ADMIN-D1 | Audit diff view — for any audit row with `before_value` / `after_value`, render a side-by-side JSON diff in the audit log UI | Lets admin verify "what exactly did I change?" without `psql` | **M** | Reuses any small JSON-diff React component |
| ADMIN-D2 | Audit-event filter chips (action type, tenant, date range) on `/admin/audit` | Once the table is non-trivial size, ungrouped scroll is unusable | **S** | Standard table-filter UI |
| ADMIN-D3 | Audit-event CSV export (admin-only, audit-logged itself — exporting the log is an auditable action) | SOC2/CASA evidence; auditor wants the rows on disk | **S** | Stream CSV via `/admin/audit/export.csv`; emits `audit.exported` audit row |
| ADMIN-D4 | "Confirm twice" pattern for destructive admin actions (rotate key, re-queue dead-letter batch, pause tenant) — typed-confirm dialog like GitHub's `Type the repo name to delete` | Prevents accidental destructive clicks in a console used rarely | **S** | Standard pattern; use shadcn `<AlertDialog>` |

### Anti-Features

| # | Feature | Why Requested | Why Problematic for Zero Mail | Alternative |
|---|---------|---------------|-------------------------------|-------------|
| ADMIN-A1 | **Admin impersonates user / "Sign in as user"** | Quick way to debug "their UI is broken" reports | **Trust violation** — impersonation grants the admin a session with the user's Gmail scope. Even if we audit it, the admin can read every email body. Cross-tenant impersonation is a recognized SaaS anti-pattern; the right pattern is to ship enough read-only diagnostics that impersonation is unnecessary | Tenant read-only inspection (OPS-TENANT-*) + better client-side error logs; revisit only if a real support load justifies it |
| ADMIN-A2 | **Admin views tenant email bodies / chat content / prompts / completions** | "I can't help them debug their rule without seeing what email triggered it" | **Architecturally banned** by v1.0 privacy constraint (`@Sensitive` + Logback scrub + ArchUnit content-ban tests) and v1.1 chat body-ban (3-layer enforcement); any admin surface that breaks this collapses the whole privacy posture | Show metadata only — message-id, sender-domain-hashed, label transitions, audit row IDs; the user can opt-in to share a redacted reproducer separately |
| ADMIN-A3 | **Free-form SQL console / `psql` proxy in admin UI** | "Just let me query directly when something weird happens" | Bypasses the audit log + the content-ban surface entirely; any read of the audit table itself bypasses `admin_read_event` enforcement; one accidental `SELECT body FROM ...` violates the privacy contract | Postgres MCP via JetBrains direct DB access for the solo operator (already in the dev workflow) — keeps the boundary tight |
| ADMIN-A4 | **Editable env-var allowlist for admin emails** | Inbox Zero pattern | Tied to environment file reloads; doesn't survive multi-instance; no audit row when an admin is granted/revoked | Database-backed `user_account.role` + a separate "promote user to admin" admin action that itself emits an audit row |
| ADMIN-A5 | **Separate admin password / 2FA layer** | "Admin should be harder to log into than user" | Adds friction without measurable gain when the solo operator's Google account already protects via 2FA; revisit when first non-developer joins | Rely on Google's 2FA; **enforce** admin role only via DB column; log admin session start |
| ADMIN-A6 | **Generic "user management" surface (edit any tenant's name/email/etc.)** | Common in mature SaaS admin tools | We don't actually mutate tenant identity fields in v1.2 — Gmail is the source of truth for email + profile; mutating it would break sync | Keep tenant fields read-only; the only writes are admin-flagged pause / unpause / mark-for-deletion |

---

## CAT Category — Curated LLM Catalog + Sync-from-/models

### Table Stakes

| # | Feature | Why Expected | Complexity | Notes |
|---|---------|--------------|------------|-------|
| CAT-T1 | Per-provider catalog table view (OpenAI, Anthropic, Google, DeepSeek as four tabs) listing every model the admin has curated, with `{name, providerModelId, enabled, allowedFeatures: [chat?, triage?, draft?], inputCostPer1k, outputCostPer1k, contextWindow, lastSyncedAt}` | Without a curated list, the Settings UI either hardcodes models (stale) or shows every model the provider has ever published (overwhelming + paid-tier-only models break BYOK) | **M** | New `llm_catalog_model` table (PK `(providerId, providerModelId)`); seeded with the existing v1.0 default list per provider |
| CAT-T2 | Per-feature toggle per model — a single model row has 3 independent checkboxes (chat / triage / draft) so admin can allow `gpt-5-mini` for triage but not chat | Different features have different latency / cost / quality budgets; one global toggle is too coarse | **S** | 3 boolean columns or a `allowed_features` JSONB array on `llm_catalog_model` |
| CAT-T3 | Per-feature default model — exactly one catalog row per `(provider, feature)` is the default; the Settings UI uses this default if the user has not picked one | Users land on Settings without an opinion; we need a sane default | **S** | `is_default_for_chat / is_default_for_triage / is_default_for_draft` booleans with a partial unique index |
| CAT-T4 | "Sync from /models" button per provider — admin clicks, backend fetches that provider's `/models` endpoint with the master key, computes a diff against the current catalog, opens a modal listing `{added, removed, changed}` model entries with per-row checkboxes to approve | Manual entry of every new GPT/Claude/Gemini release is operator toil; auto-importing without admin review is bad (vendors ship preview / deprecated / unsuitable models constantly) | **M** | New `llm_catalog_sync_run` row per click; new `llm_catalog_sync_diff_entry` rows per diff line; admin approval commits selected entries to `llm_catalog_model` |
| CAT-T5 | Sync run history — list past Sync runs per provider with `{startedAt, actor, addedCount, removedCount, approvedCount, skippedCount}` | Audit + "did we already sync GPT-5.1?" lookup | **S** | Query `llm_catalog_sync_run` ordered desc |
| CAT-T6 | "Disable model" emits `model.disabled` audit row and surfaces "N users currently have this as their chat/triage/draft selection" before disabling | Prevents disabling a model 200 users depend on without realizing | **M** | Join count from `assistant_settings.{chat,triage,draft}_model_id`; require admin to acknowledge before disabling |
| CAT-T7 | Provider status pill (green/amber/red) based on last successful `/models` fetch + last successful test-connection within 24h | At-a-glance ops signal | **S** | Derived from `llm_catalog_sync_run.lastSuccessAt` and `provider_master_key.lastTestConnectionAt` |

### Differentiators

| # | Feature | Value Proposition | Complexity | Notes |
|---|---------|-------------------|------------|-------|
| CAT-D1 | Cost-per-1k tokens stored on each catalog row and shown in Settings AI tab next to model name (`gpt-5-mini · $0.25 / $2.00 per 1M tok`) | Users picking a model want to know cost before flipping; otherwise BYOK users overspend on Opus by accident | **M** | Pricing data is provider-published; we cache on Sync. Acknowledge it may go stale until next Sync (acceptable — pricing changes monthly at most) |
| CAT-D2 | "Recommended for {chat/triage/draft}" badge on one model per feature per provider, distinct from "default" | Default is what we ship; recommended is editorial guidance ("for triage, we recommend gpt-5-mini for cost") | **S** | `recommended_for_{chat,triage,draft}` boolean column; admin curated |
| CAT-D3 | Deprecation tag — if a Sync detects a model has been removed by the provider but tenants still reference it, surface as a banner "12 users on a deprecated model — pick a migration target" | Vendor model retirement causes silent failures; surfacing the count gives the admin time to migrate users | **M** | Diff entry of type `removed` + dependent count from `assistant_settings` |
| CAT-D4 | Catalog state snapshot exposed as `/api/catalog/models?feature=chat&provider=openai` (and equivalent for triage/draft) — the **only** thing the Settings UI queries | Decouples admin curation from frontend; Settings doesn't need to know about disabled / deprecated / non-feature models | **S** | Read endpoint filtered to `enabled=true AND allowed_features ? :feature` |

### Anti-Features

| # | Feature | Why Requested | Why Problematic | Alternative |
|---|---------|---------------|-----------------|-------------|
| CAT-A1 | **Free-form "type any model ID" override in Settings UI** | "What if a user wants to try a brand-new model before admin syncs?" | Breaks the curated-catalog invariant; routes load through unknown-cost models; defeats the per-feature catalog | Users wait for admin Sync, or run BYOK with their own OpenRouter key (BYOK already supports per-call model pin via existing v1.0 LLM-02) |
| CAT-A2 | **Auto-approve Sync diff (no admin review)** | "Save the operator a click" | Vendors ship deprecated, preview, internal-only, or paid-tier-only models in `/models` — auto-approve = silent breakage | Always require explicit admin approval of each added entry |
| CAT-A3 | **Embedding-model curation in v1.2 catalog** | Symmetric with chat/triage/draft | Privacy constraint forbids embeddings of user mail in v1; no embedding feature exists to power | Defer to whenever embedding feature is approved (post v2) |
| CAT-A4 | **Per-tenant model allowlist overrides ("tenant X can use Opus, tenant Y cannot")** | Enterprise-y feature | Adds a tenant-scoped layer over the catalog; multiplies UI complexity; we have no use case in v1 (solo prosumers, no enterprise sales) | Global catalog only; revisit when first enterprise customer signs |
| CAT-A5 | **Live `/models` polling on every Settings page load** | "Always show the latest models" | Each tenant's Settings load → 4 outbound `/models` calls → rate-limit + cost; defeats catalog purpose | Catalog is the cache; admin-triggered Sync is the refresh |

---

## MKEY Category — Master-Key Management

### Table Stakes

| # | Feature | Why Expected | Complexity | Notes |
|---|---------|--------------|------------|-------|
| MKEY-T1 | Per-provider master key set/view UI: 4 cards (OpenAI, Anthropic, Google, DeepSeek), each showing `{status: SET/UNSET, lastRotatedAt, lastTestedAt, encryptedKeyVersion}` — never showing the key bytes | Admin needs to verify "is OpenAI configured" without exposing the key | **S** | New `provider_master_key` table with encrypted-at-rest key blob + metadata columns |
| MKEY-T2 | "Set / replace master key" form with masked input, submit encrypts under app-layer AES-GCM (reusing v1.0 LLM-04 crypto), writes encrypted blob + emits `master_key.set` audit row | Same crypto pattern as v1.0 BYOK; reuse don't duplicate | **M** | Same `AesGcmEncryptor` used for BYOK; new column `provider_master_key.encrypted_key BYTEA NOT NULL` |
| MKEY-T3 | "Test connection" button per provider — backend issues a minimal `/models` GET (or `/v1/messages` ping) using the current master key, returns `{ok, latencyMs, modelsReturnedCount}`, updates `provider_master_key.lastTestedAt` | First sanity check before relying on a key; also surfaces "provider is down" vs "our key is wrong" | **S** | Already partially exists from CAT-T4 Sync flow; share the adapter |
| MKEY-T4 | "Rotate master key" workflow: admin pastes new key → backend test-connects under new key → if pass, re-encrypts dependent rows (any platform-encrypted BYOK secrets if applicable) → swaps active key → marks old key version `RETIRED` → emits `master_key.rotated` audit row with old/new version | Rotation is the whole point of key management; without it, set-once-forever | **L** | Multi-step transactional flow; old key kept for read-only decrypt of historical encrypted columns until full re-encrypt sweep completes |
| MKEY-T5 | Confirm-twice destructive-action dialog on rotate (typed-confirm pattern) | Botched rotate breaks every dependent LLM call | **S** | shadcn `<AlertDialog>` |
| MKEY-T6 | Failed-rotation rollback — if any step of T4 fails (test-connect failed, re-encrypt failed mid-sweep), rollback to old active key, surface error toast, write `master_key.rotation_failed` audit row | Rotate is destructive; partial failure must not lock everyone out | **M** | Transactional rotation with explicit `RETIRED → ACTIVE` reversal step on error |

### Differentiators

| # | Feature | Value Proposition | Complexity | Notes |
|---|---------|-------------------|------------|-------|
| MKEY-D1 | "Dependents" count per master key — "12 BYOK rows / 4 webhook secrets encrypted under this key" surfaced before rotation | Tells admin the blast radius of rotation | **M** | Count via FK or per-table version column |
| MKEY-D2 | Rotation cadence reminder — banner appears when `lastRotatedAt` > 90 days ago | Aligns with PCI/SOC2 90-day rotation guidance; opt-in not enforced in v1.2 | **S** | Frontend check on `provider_master_key.lastRotatedAt` |
| MKEY-D3 | "Key history" mini-list per provider — last 5 versions with `{version, rotatedAt, actor}` (not key bytes) | Audit/forensics support | **S** | Reads `admin_audit_event WHERE action LIKE 'master_key.%'` |

### Anti-Features

| # | Feature | Why Requested | Why Problematic | Alternative |
|---|---------|---------------|-----------------|-------------|
| MKEY-A1 | **Show the master key bytes in any admin UI surface ("reveal once")** | "I forgot what I pasted, let me check" | A revealed-once secret in a long-running page session leaks via DOM, screenshot, accidental screen share; never worth the convenience | Admin stores key in their own password manager; UI shows only "set / unset" + `lastRotatedAt` |
| MKEY-A2 | **Automatic key rotation on a cron** | Best practice in PCI-aligned shops | v1.2 has no key-management infrastructure (no KMS, no HSM, no Vault); automatic rotation when re-encrypt sweep can partially fail = silent prod incident at 3am | Manual rotation with banner reminder (MKEY-D2); revisit when there's actual KMS infra |
| MKEY-A3 | **Per-tenant master key (tenant brings their own platform key)** | Symmetric with BYOK | This conflates platform credentials with tenant credentials; BYOK already covers the "user brings their own" case | Master key is platform-scoped; BYOK is tenant-scoped; keep separate |
| MKEY-A4 | **Storing keys in DB encrypted only with the DB-level pgcrypto** | "Simpler than app-layer AES-GCM" | Already explicitly banned in CLAUDE.md ("Hard do not use" list); key-in-DB → key-leak on DB leak | App-layer AES-GCM with master from env / sealed config (already the v1.0 pattern from LLM-04) |

---

## OPS-TENANT Category — Tenant Read-Only Inspection

### Table Stakes

| # | Feature | Why Expected | Complexity | Notes |
|---|---------|--------------|------------|-------|
| OPS-TENANT-T1 | Tenant list page at `/admin/tenants` with search-by-email + filter chips (state: connected/disconnected/paused, has-balance, recently-active) + pagination | "Find tenant X" is the most common operator task | **M** | Read projection over `email_account` + `gmail_connection`; debounced search |
| OPS-TENANT-T2 | Tenant detail page at `/admin/tenants/{tenantId}` with tabs: Overview, Health, Billing, Spend, Activity | Container for everything else | **S** | Tab layout reuses shadcn `<Tabs>` |
| OPS-TENANT-T3 | Overview tab — tenant email (hashed for display? — decide), creation date, last sign-in, role, pause flag, watch expiry, watch renewal cadence; no identity-related write controls in v1.2 | The "is this account healthy" snapshot | **S** | Read from `user_account` + `gmail_connection` |
| OPS-TENANT-T4 | Health tab — gmail connection state, last Pub/Sub event timestamp, watch expiry countdown, recent reconnect attempts, last sync errors (codes only, no payload bodies) | Most "my mail isn't being triaged" reports trace to connection/watch issues | **M** | Read from existing v1.0 connection-health tables |
| OPS-TENANT-T5 | Billing tab — current credit balance, recent ledger holds (reservation ids + amounts only, never the underlying message ids), recent top-ups, stale-reservation count | Resolves "I topped up but balance didn't update" reports | **M** | Read from `credit_ledger_entry` + `credit_reservation` |
| OPS-TENANT-T6 | Spend tab — LLM spend over the last 30 days, broken down by feature (chat/triage/draft) and provider; **metadata only** (token counts + dollar amounts; never prompts/completions) | Resolves "why did I burn $X this week" reports | **M** | Read aggregates over `llm_call_audit` (the v1.0 metadata-only audit) |
| OPS-TENANT-T7 | Activity tab — last 50 admin actions on this tenant + last 100 triage audit row counts (counts by action: labeled/archived/drafted/sent-via-chat) | "What's been going on here recently" without exposing message contents | **M** | Read from `admin_audit_event WHERE target_tenant_id = ?` + counts from `triage_audit` |
| OPS-TENANT-T8 | "Pause tenant" action — sets the existing v1.0 pause flag (`MAIL-06`) via an admin-side write; emits `tenant.paused_by_admin` audit row | Sometimes you need to pause a tenant whose mailbox is causing a runaway loop without waiting for them | **S** | Reuses existing pause infra; admin-action variant |
| OPS-TENANT-T9 | "Disconnect Gmail" action — surfaces the user-side disconnect (existing AUTH-03/05 flow) on admin's behalf; explicit double-confirm | Emergency containment | **S** | Reuses existing disconnect; admin-action variant |
| OPS-TENANT-T10 | "Delete tenant + all data" action — wraps the existing AUTH-03 cascade-delete with an admin-typed-confirm dialog + audit row | GDPR-like deletion request handled by support; reuses validated cascade | **M** | Reuses AUTH-03 cascade; admin-action variant |

### Differentiators

| # | Feature | Value Proposition | Complexity | Notes |
|---|---------|-------------------|------------|-------|
| OPS-TENANT-D1 | Spend-over-time sparkline on tenant Overview tab, plus comparison to that tenant's 30-day median | Visual anomaly detection | **S** | Reuses Spend tab data |
| OPS-TENANT-D2 | "Replay last watch renewal" — admin-triggered retry of `users.watch` for a tenant whose watch expired without auto-renewal | Saves a support cycle; renewal is the common stuck state | **M** | Trigger via existing v1.0 worker job; emit audit row |
| OPS-TENANT-D3 | Tenant deletion preview ("this will delete N rules, M audit rows, K ledger entries") before final confirm | Reduces "I clicked delete and didn't realize what would happen" | **S** | Count query before delete |

### Anti-Features

| # | Feature | Why Requested | Why Problematic | Alternative |
|---|---------|---------------|-----------------|-------------|
| OPS-TENANT-A1 | **"View tenant inbox" / message-body access** | Quickest debugging path | Hard violation of v1 privacy posture (no body access) and CLAUDE.md "Privacy" constraint | Tenant exports their own redacted reproducer; admin works from metadata |
| OPS-TENANT-A2 | **"View tenant chat history" / chat message content** | Symmetric with above | Hard violation of v1.1 chat body-ban (3-layer enforcement); even chat is user PII | Show counts only (chat sessions count, messages count); never content |
| OPS-TENANT-A3 | **"View tenant prompts/completions"** | "I want to see what we sent to OpenAI on their behalf" | LLM-09 explicitly bans persistence beyond short-lived cache; nothing to view | Metadata-only LLM call audit (provider, model, tokens, latency, dollar cost) |
| OPS-TENANT-A4 | **"View tenant OAuth refresh token / Google subject"** | "Their token must be expired" | AUTH-04 + FND-03 ban token-byte logging; refresh token is encrypted at rest under master key | Show only `{connectionState, lastSuccessfulRefreshAt, errorCode}` |
| OPS-TENANT-A5 | **"Edit tenant settings on their behalf"** | "User asked me to change their personalization for them" | Admin writing to user-owned config is impersonation-adjacent; if needed, the user does it themselves over a support call | Read-only view + screen-share over support call |
| OPS-TENANT-A6 | **Bulk tenant operations (bulk pause, bulk delete)** | Convenient | Multiplies blast radius; encourages "mass action" thinking | One tenant at a time in v1.2; revisit if a real ops scenario justifies |

---

## OPS-QUEUE Category — Worker Queue Health (Read-Only)

### Table Stakes

| # | Feature | Why Expected | Complexity | Notes |
|---|---------|--------------|------------|-------|
| OPS-QUEUE-T1 | Outbox lag stat — count of `outbox` rows with `processed_at IS NULL` + max age of oldest unprocessed | First indicator of "is the worker keeping up?" | **S** | Single SQL aggregate; PlanetScale postgres-queue article confirms "oldest unprocessed age" is the canonical lag metric |
| OPS-QUEUE-T2 | `processing_job` queue depth by job type (mail-ingest, triage, draft, chat-tool, etc.) + max age of oldest unleased per type | Lets admin see which job type is starving | **S** | Group-by-type aggregate |
| OPS-QUEUE-T3 | Retry distribution histogram per job type — "how many jobs at retry count 0/1/2/3+?" | Locates a job class stuck in a retry loop | **S** | Group-by-`retry_count` aggregate |
| OPS-QUEUE-T4 | Failure rate per job type over the last 1h / 24h | Anomaly signal | **S** | Aggregate over `processing_job.completed_at` + `last_error_code` |
| OPS-QUEUE-T5 | Dead-letter view — jobs at `status=FAILED_PERMANENT` (or retry exhausted) with `{jobId, type, tenantId, lastErrorCode, lastErrorAt, retryCount}` (no payload bodies) | Surfaces "we gave up on these" so admin can investigate or re-queue | **M** | Read from `processing_job` filtered to terminal failure |
| OPS-QUEUE-T6 | Re-queue action on dead-letter row — admin-triggered reset of `retry_count` + `status` back to `READY`; emits `job.requeued` audit row; confirm-twice dialog | The only mutating action in the queue surface; gated by audit | **S** | Single `UPDATE` wrapped in audit |
| OPS-QUEUE-T7 | Auto-refresh on the queue health page (every 10s) with a "live" indicator | The admin sits on this page during an incident; manual refresh defeats the purpose | **S** | SWR or TanStack Query `refetchInterval` |

### Differentiators

| # | Feature | Value Proposition | Complexity | Notes |
|---|---------|-------------------|------------|-------|
| OPS-QUEUE-D1 | Worker heartbeat — which worker processes are leasing jobs in the last 60s | Confirms the worker is alive vs just empty queue | **S** | Read distinct `leased_by_worker` from `processing_job` in last 60s |
| OPS-QUEUE-D2 | Backpressure alert — banner appears at top of admin pages when oldest unleased > 5min | Surface the incident before the operator navigates to the queue page | **S** | Frontend check; can be enriched into Grafana alert in v1.3+ |

### Anti-Features

| # | Feature | Why Requested | Why Problematic | Alternative |
|---|---------|---------------|-----------------|-------------|
| OPS-QUEUE-A1 | **Worker stop/start/restart controls from the UI** | "Just let me restart the worker without SSH" | Tightly couples admin UI to deployment topology; one wrong click in prod kills ingest | Deployment surface is the right place; admin UI is read+limited-write |
| OPS-QUEUE-A2 | **"View job payload" on a dead-letter row** | Debug a specific stuck job | Payloads carry message IDs / Gmail message refs / sometimes redacted bodies — opens the privacy can | Show error code + tenant + job type only; ask tenant for a reproducer if needed |
| OPS-QUEUE-A3 | **Manually edit a job row** | Surgically fix a job that's stuck because of bad data | Audit + correctness nightmare; row schema is internal contract | Re-queue or delete (audit-logged); fix data via a proper migration |

---

## OPS-SPEND Category — Global LLM Spend Dashboard

### Table Stakes

| # | Feature | Why Expected | Complexity | Notes |
|---|---------|--------------|------------|-------|
| OPS-SPEND-T1 | Top-line spend cards: total $ today, 7d, 30d (across all tenants, all providers) | The "are we burning money" snapshot | **S** | Read aggregate over `llm_call_audit.cost_usd` |
| OPS-SPEND-T2 | Stacked bar by provider — daily spend × {openai, anthropic, google, deepseek, openrouter} over last 30 days | Quickly tells admin "which provider is the cost center" | **M** | Aggregate group-by-day, group-by-provider |
| OPS-SPEND-T3 | Donut by feature — spend split across {chat, triage, draft} over last 30 days | Surfaces "is chat eating the budget" vs "is triage" | **S** | Aggregate group-by-feature |
| OPS-SPEND-T4 | Platform vs BYOK split — what % of LLM calls are paid by platform credits vs paid by user BYOK key | Unit-economics signal | **S** | Boolean column on `llm_call_audit` already exists per v1.0 BILL-07 |
| OPS-SPEND-T5 | Top-N tenants by spend (last 7d / 30d) with each row click-through to tenant detail Spend tab | Identifies outlier accounts | **S** | Aggregate + join to email_account for display name |
| OPS-SPEND-T6 | Date-range picker (last 24h / 7d / 30d / custom) | Standard analytics affordance | **S** | Use existing shadcn date-picker primitive |

### Differentiators

| # | Feature | Value Proposition | Complexity | Notes |
|---|---------|-------------------|------------|-------|
| OPS-SPEND-D1 | Spend forecast — "if today's burn continues, you'll spend $X this month" | Quick decision aid for whether to raise alarm | **S** | Simple linear extrapolation from MTD |
| OPS-SPEND-D2 | Per-model cost-per-call median + p95 — surfaces "this model has a long-tail latency cost" | Helps decide which models to deprecate from catalog | **S** | Aggregate over `llm_call_audit.{tokens_in, tokens_out, cost_usd}` |
| OPS-SPEND-D3 | Cap-vs-actual chart — shows the configured global daily cap (from v1.0 LLM-10) vs actual daily spend | Validates the cap is reasonable | **S** | Two-line chart |

### Anti-Features

| # | Feature | Why Requested | Why Problematic | Alternative |
|---|---------|---------------|-----------------|-------------|
| OPS-SPEND-A1 | **Drill-down to individual prompts/completions on the spend dashboard** | "I want to know what they were asking that cost $5" | Same v1 privacy ban as everywhere else | Stop at metadata (tokens, model, latency, feature, cost); the user can self-explain via their own activity |
| OPS-SPEND-A2 | **Real-time spend streaming (websocket)** | Cool UI | Adds infra without value; 10s refetch is more than fast enough | TanStack Query `refetchInterval` |
| OPS-SPEND-A3 | **Spend dashboard as a public marketing page ("see how much our users save")** | Growth surface | Even aggregated, exposes our cost structure to competitors and our top tenants' relative usage to each other | Internal admin only |

---

## SET-AI Category — Settings AI Provider/Model Tab (carries forward 4 deferred v1.1 reqs)

> Reference: SET-AI-01..04 from v1.1 deferred list. All 4 carry forward unchanged in intent; v1.2 only adds the catalog dependency.

### Table Stakes

| # | Feature | Why Expected | Complexity | Notes |
|---|---------|--------------|------------|-------|
| SET-AI-T1 | (SET-AI-01) Per-feature provider+model picker — 3 separate selects: "Chat assistant model", "Triage model", "Draft replies model" | Each feature has different cost/quality tradeoff; one global model is too coarse | **S** | shadcn `<Select>` × 3, options from `/api/catalog/models?feature=...` |
| SET-AI-T2 | (SET-AI-02) Provider picker drives the model dropdown — user picks "OpenAI" → model dropdown shows only OpenAI catalog models enabled for that feature | Reduces dropdown size; matches the BYOK keying model | **S** | Two-stage cascading select |
| SET-AI-T3 | (SET-AI-03) BYOK key management cards per provider — paste/save/clear key, "Test connection" button, last-used timestamp, "currently in use for: [chat, triage]" | The "I want to pay with my own OpenAI key" path; already in v1.0 LLM-02..04, surface here | **M** | Reuses v1.0 `byok_credential` table + AES-GCM crypto |
| SET-AI-T4 | (SET-AI-04) Per-feature "Use BYOK if available, otherwise platform credits" toggle — default `ON` when a BYOK key exists | The cost-control affordance users expect | **S** | Boolean on `assistant_settings` |
| SET-AI-T5 | Cost-per-1k display next to each model name in dropdown | Pulled forward from CAT-D1 — users need cost signal at point of choice | **S** | Read from `llm_catalog_model.input_cost / output_cost` |
| SET-AI-T6 | "Reset to recommended" button per feature — sets the picker back to the catalog's `recommended_for_{feature}` model | Lets users back out of a bad choice | **S** | Reads from CAT-D2 |

### Differentiators

| # | Feature | Value Proposition | Complexity | Notes |
|---|---------|-------------------|------------|-------|
| SET-AI-D1 | "Last 7d cost" hint under each picker showing what they actually spent on the current model | Real cost feedback beats theoretical pricing | **M** | Join `assistant_settings.{feature}_model_id` to per-feature spend from spend audit |
| SET-AI-D2 | Inline deprecation banner — if the user's picked model was deprecated in CAT-D3 sync, show "this model is deprecated by the provider — please pick a new one" | Surface CAT-D3 deprecation to the user | **S** | Compare `assistant_settings.{feature}_model_id` to `llm_catalog_model.is_deprecated` |
| SET-AI-D3 | Per-feature spend cap (daily) per user — independent of LLM-10 global cap | Cost-conscious users want to bound their own spend | **M** | New nullable column `daily_spend_cap_usd_{feature}` on `assistant_settings` |

### Anti-Features

| # | Feature | Why Requested | Why Problematic | Alternative |
|---|---------|---------------|-----------------|-------------|
| SET-AI-A1 | **Free-form model ID textbox** | "Let me type `gpt-5-pro-experimental` myself" | Defeats catalog (CAT-A1); routes unknown-cost calls | Wait for admin Sync |
| SET-AI-A2 | **Per-rule model override in the rule editor UI** | Power-user feature | Multiplies UX surface; per-feature granularity already covers 95% of need | Defer to v2 |
| SET-AI-A3 | **Show users the master-key bytes (or any provider-side credentials)** | "How do I know what key you're using?" | Same anti-pattern as MKEY-A1, at user surface | Show only "platform credits" vs "your BYOK key" |
| SET-AI-A4 | **Allow user to "Sync" provider models from their own Settings page** | Symmetry with admin Sync | Outbound rate-limit + cost + every user can trigger Sync = DoS our master key | Admin-only Sync (CAT-T4) |

---

## SET-VOICE Category — Personalization Tab (carries forward 6 deferred v1.1 reqs)

> Reference: SET-VOICE-01..06 from v1.1 deferred list. Specified in prior v1.1 FEATURES.md research; carries forward unchanged. Brief restatement below.

### Table Stakes

| # | Feature | Why Expected | Complexity | Notes |
|---|---------|--------------|------------|-------|
| SET-VOICE-T1 | (SET-VOICE-01) Writing-style free-text input ("formal, concise, prefer bullet points") | Drives draft + chat tone | **S** | Textarea, persisted to `assistant_settings.writing_style` |
| SET-VOICE-T2 | (SET-VOICE-02) Personal instructions free-text input ("always sign off as 'best, Q.'") — explicit prompt-injection-hardened slot | Drives every assistant response | **M** | Textarea; sanitized + injection-hardened in the LLM gateway as v1.1 already requires |
| SET-VOICE-T3 | (SET-VOICE-03) Email signature field with rich-text (or markdown) preview | Appended to AI drafts + chat-confirmed send | **S** | Textarea + preview |
| SET-VOICE-T4 | (SET-VOICE-04) Knowledge-base snippet CRUD list — short user-authored facts the assistant may quote ("my work address is X", "my Zoom link is Y") | The "tell the assistant about me once, never again" affordance | **M** | New `assistant_knowledge_snippet` table; per-user list with add/edit/delete |
| SET-VOICE-T5 | (SET-VOICE-05) Tone preset dropdown — Professional / Friendly / Direct / Custom | Quick mode-switch | **S** | Enum column |
| SET-VOICE-T6 | (SET-VOICE-06) AI output language toggle — Vietnamese / English (Auto-detect from incoming email is the default) | Locked in v1.0 i18n direction | **S** | Enum column; respected in the LLM gateway prompt assembly |

### Anti-Features

| # | Feature | Why Requested | Why Problematic | Alternative |
|---|---------|---------------|-----------------|-------------|
| SET-VOICE-A1 | **Two-way sync between personalization fields and rule-builder system prompt** | Inbox Zero pattern (their ARCHITECTURE.md flags this as messy) | Already in v1.1 anti-feature list (CHAT-A10); structured rule AST is the source of truth | One-way: chat → settings only |
| SET-VOICE-A2 | **AI-learned writing style from sent mail** | Better tone matching | Would require persisting derived features over user mail — privacy ban | In-request tone matching only (already v1.0 DRFT-03) |

---

## SET-BEHV Category — Behavior Tab (carries forward 5 deferred v1.1 reqs)

> Reference: SET-BEHV-01..05 from v1.1 deferred list. Carries forward unchanged.

### Table Stakes

| # | Feature | Why Expected | Complexity | Notes |
|---|---------|--------------|------------|-------|
| SET-BEHV-T1 | (SET-BEHV-01) Auto-draft toggle — when on, draft is saved to Gmail automatically on incoming mail matching draft-eligible rules; when off, draft is only generated on-demand | Already a v1.0 capability surfaced as a setting | **S** | Boolean on `assistant_settings` |
| SET-BEHV-T2 | (SET-BEHV-02) Draft confidence threshold slider — only generate draft if internal confidence > threshold | Cost-control + quality-control | **S** | Numeric `[0.0..1.0]` on `assistant_settings` |
| SET-BEHV-T3 | (SET-BEHV-03) Follow-up reminders toggle — daily-digest mode reminds about un-replied threads | Engagement loop, opt-in | **S** | Boolean on `assistant_settings` |
| SET-BEHV-T4 | (SET-BEHV-04) Daily digest opt-in toggle — reuses v1.0 ANL-03 | Already-shipped capability, surfaced as a setting | **S** | Reuses existing daily-digest config |
| SET-BEHV-T5 | (SET-BEHV-05) Sensitive-data protection toggle — refuses to draft replies on messages flagged sensitive (e.g., legal/health), forces user to compose manually | Trust-aware default for high-stakes mail | **M** | Boolean on `assistant_settings`; classifier hook in v1.0 LLM-05 pipeline |

### Anti-Features

| # | Feature | Why Requested | Why Problematic | Alternative |
|---|---------|---------------|-----------------|-------------|
| SET-BEHV-A1 | **"Always auto-send on confidence > X" toggle** | Logical extension of the threshold slider | Hard ban — TRG-03 + ArchUnit + grep gate enforce zero auto-send; surfacing this toggle in the UI invites trust violation | Auto-send remains forbidden; chat-preview-confirm send is the only send path |

---

## SET-SAFE Category — Safety Net Tab (carries forward 4 deferred v1.1 reqs)

> Reference: SET-SAFE-01..04 from v1.1 deferred list. Carries forward unchanged. All read/write existing v1.0 TRG-07..08 tables.

### Table Stakes

| # | Feature | Why Expected | Complexity | Notes |
|---|---------|--------------|------------|-------|
| SET-SAFE-T1 | (SET-SAFE-01) VIP allow-list — email/domain entries never archived/labeled by rules | Trust-saving "panic button" against an over-aggressive rule | **S** | Reuses `sender_safety_entry` table |
| SET-SAFE-T2 | (SET-SAFE-02) Never-archive list — domain or address; even if a rule says archive, won't | Inbox Zero pattern; users want explicit safety overrides | **S** | Reuses safety table; type=NEVER_ARCHIVE |
| SET-SAFE-T3 | (SET-SAFE-03) Never-trash list (n/a in v1 since rules can't trash, but ships future-proof) | Future-proofing | **S** | Reuses safety table; type=NEVER_TRASH (rule-engine guard) |
| SET-SAFE-T4 | (SET-SAFE-04) Quick-add from triage audit — every audit row gets a "Add sender to VIP" inline action | Saves the trip back to Settings | **S** | Inline action in audit row UI |

### Anti-Features

| # | Feature | Why Requested | Why Problematic | Alternative |
|---|---------|---------------|-----------------|-------------|
| SET-SAFE-A1 | **Block-list (rules force-archive any sender matching)** | Symmetric with allow-list | Already expressible via a normal rule; first-class block-list adds a second UI surface for the same outcome | Use a rule |

---

## Feature Dependencies

```
ADMIN (RBAC + audit framework)
    └──required-by──> CAT (audit + admin-gated mutations)
                        └──required-by──> SET-AI (Settings AI tab reads catalog)
    └──required-by──> MKEY (audit + admin-gated mutations)
                        └──required-by──> CAT Sync flow (uses master key)
    └──required-by──> OPS-TENANT (admin-only routes + read-audit)
    └──required-by──> OPS-QUEUE (admin-only routes + audit for re-queue)
    └──required-by──> OPS-SPEND (admin-only routes)

CAT
    └──required-by──> SET-AI (model picker reads catalog)

MKEY
    └──required-by──> CAT (Sync calls /models with master key)

SET-VOICE / SET-BEHV / SET-SAFE
    └── independent of CAT/MKEY/ADMIN — can ship in parallel once Settings page chrome exists

SET-AI ──conflicts──> "user-types-any-model" anti-pattern (CAT-A1, SET-AI-A1) — must keep catalog as the only source

OPS-TENANT.Spend tab ──reuses──> OPS-SPEND aggregate queries (one tenant filter)
```

### Dependency Notes

- **ADMIN → everything else in v1.2:** Every other v1.2 feature lives behind `/admin/*` (catalog, master key, tenant inspection, queue, spend). RBAC + audit framework must be in place before any of those write surfaces ship — otherwise audit gaps appear in the most security-sensitive code we have.
- **MKEY → CAT:** Sync-from-`/models` calls each provider's API using the master key. Without master-key UX, Sync is admin-config-by-environment-variable, which works but doesn't match the curated-catalog story.
- **CAT → SET-AI:** The AI Provider/Model tab can only list models the admin has approved. SET-AI ships any time after `/api/catalog/models?feature=...` exists; doesn't need Sync UI complete.
- **OPS-TENANT.Spend ↔ OPS-SPEND:** Same aggregate query, just with/without a tenant filter — share the read-side service.
- **SET-VOICE / SET-BEHV / SET-SAFE are independent:** Can ship without any Phase 8 work; they're pure user-Settings additions on existing v1.0 schema (+ a few new columns on `assistant_settings` + the existing safety table). Phase 9 sequencing can in principle start these in parallel with Phase 8.

---

## MVP Definition (v1.2 launch scope)

### Launch With (v1.2 Phase 8 + Phase 9)

Minimum viable admin + Settings for trust-first ops on a single-VPS deployment.

**Phase 8 (Admin Console Foundation):**
- [ ] **ADMIN-T1..T8** — RBAC + admin route gate + admin_audit_event + admin_read_event + first-admin bootstrap migration (essential foundation; everything depends on it)
- [ ] **ADMIN-D4** — Confirm-twice pattern (cheap, hugely reduces accidental destructive clicks)
- [ ] **CAT-T1..T7** — Curated catalog tables, per-provider × per-feature toggles, Sync-from-`/models` flow with diff modal, sync run history, disable-with-dependent-count, provider status pill
- [ ] **MKEY-T1..T6** — Per-provider master-key set/view/test/rotate with full transactional rollback on failed rotation
- [ ] **OPS-TENANT-T1..T10** — Tenant list + detail (Overview/Health/Billing/Spend/Activity) + pause/disconnect/delete admin actions, all read-only on PII (metadata only)
- [ ] **OPS-QUEUE-T1..T7** — Read-only queue health stats + dead-letter view + re-queue action with confirm + 10s auto-refresh
- [ ] **OPS-SPEND-T1..T6** — Global spend dashboard (cards + stacked bar + donut + platform/BYOK split + top-N tenants)

**Phase 9 (Settings UI on curated catalog):**
- [ ] **SET-AI-T1..T6** — Per-feature provider+model picker reading from catalog + BYOK key cards + use-BYOK-if-available toggle + cost display + reset-to-recommended
- [ ] **SET-VOICE-T1..T6** — All 6 personalization fields incl. knowledge-base CRUD
- [ ] **SET-BEHV-T1..T5** — All 5 behavior toggles
- [ ] **SET-SAFE-T1..T4** — All 4 safety net surfaces wired against existing TRG-07..08

### Add After Validation (v1.2.x patch milestones)

- [ ] **ADMIN-D1** Audit diff JSON view — once table is non-trivial
- [ ] **ADMIN-D2** Audit-event filter chips — once volume justifies
- [ ] **ADMIN-D3** Audit CSV export — first time SOC2/CASA evidence is requested
- [ ] **CAT-D1** Cost-per-1k in catalog — first user-reported "I didn't know Opus was that expensive"
- [ ] **CAT-D2** "Recommended for" badge — first time default ≠ recommended
- [ ] **CAT-D3** Deprecation tag — first vendor model retirement
- [ ] **MKEY-D1** Dependents count
- [ ] **MKEY-D2** Rotation cadence reminder banner
- [ ] **OPS-TENANT-D1** Spend sparkline
- [ ] **OPS-TENANT-D2** Replay last watch renewal
- [ ] **OPS-QUEUE-D2** Backpressure global banner
- [ ] **OPS-SPEND-D1..D3** Forecast / per-model cost stats / cap-vs-actual chart
- [ ] **SET-AI-D1..D3** Last-7d-cost hint / deprecation banner / per-feature spend cap

### Future Consideration (v1.3+)

- [ ] Grafana dashboards (separate observability surface — already deferred per PROJECT.md)
- [ ] CASA refresh evidence pipeline (SEED-012 closure)
- [ ] Per-tenant model allowlist overrides (CAT-A4) — wait for first enterprise customer
- [ ] Automatic key rotation on cron (MKEY-A2) — wait for KMS/Vault infra
- [ ] Bulk tenant operations (OPS-TENANT-A6) — wait for an ops scenario that requires them
- [ ] Real-time spend streaming (OPS-SPEND-A2) — never; 10s refetch is enough
- [ ] Auto-approve Sync (CAT-A2) — never; admin review is the point

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| ADMIN-T1..T8 (RBAC + audit) | HIGH (everything else depends) | MEDIUM | **P1** |
| MKEY-T1..T6 (master keys) | HIGH (platform credits depend) | HIGH (rotation rollback) | **P1** |
| CAT-T1..T7 (curated catalog + Sync) | HIGH (Settings AI tab depends) | MEDIUM | **P1** |
| OPS-TENANT-T1..T10 (tenant inspection) | HIGH (first support load arrives) | MEDIUM | **P1** |
| OPS-QUEUE-T1..T7 (queue health) | MEDIUM (incidents are rare but expensive) | LOW | **P1** |
| OPS-SPEND-T1..T6 (global spend) | HIGH (unit economics visibility) | LOW | **P1** |
| SET-AI-T1..T6 (AI Provider/Model tab) | HIGH (carries forward deferred req) | LOW | **P1** |
| SET-VOICE-T1..T6 (Personalization) | HIGH (carries forward deferred req) | LOW | **P1** |
| SET-BEHV-T1..T5 (Behavior) | MEDIUM (carries forward deferred req) | LOW | **P1** |
| SET-SAFE-T1..T4 (Safety Net) | HIGH (trust posture surface) | LOW | **P1** |
| ADMIN-D1..D4 (audit polish) | MEDIUM | LOW | **P2** |
| CAT-D1..D4 (catalog polish) | MEDIUM | LOW | **P2** |
| MKEY-D1..D3 (key UX polish) | MEDIUM | LOW | **P2** |
| OPS-TENANT-D1..D3 (tenant polish) | LOW | LOW | **P2** |
| OPS-QUEUE-D1..D2 (queue polish) | LOW | LOW | **P2** |
| OPS-SPEND-D1..D3 (spend polish) | LOW | LOW | **P2** |
| SET-AI-D1..D3 (Settings AI polish) | MEDIUM | MEDIUM | **P2** |
| Grafana / CASA / impersonation | — | HIGH | **P3** (deferred per PROJECT.md) |

---

## Competitor Feature Analysis

| Feature | Inbox Zero (`../inbox-zero`) | LiteLLM Proxy Admin UI | Zero Mail v1.2 Approach |
|---------|------------------------------|------------------------|--------------------------|
| Admin auth | Env-var email allowlist (`env.ADMINS`) | Master-key login + virtual-key system | DB-backed `user_account.role = ADMIN`; first admin bootstrapped via Liquibase changelog |
| Audit log | Implicit (Vercel + Stripe + Postgres logs) | Per-key + per-team usage logs | Explicit `admin_audit_event` (append-only, Postgres-triggered) + `admin_read_event` |
| User lookup | `AdminUserInfo` email lookup form, returns user JSON | Per-user/team detail page | Tenant list + detail page (Overview / Health / Billing / Spend / Activity) — strictly metadata-only |
| LLM catalog curation | None (every user picks from a hardcoded list) | Yes — model allowlist + per-team enabled set | Per-provider × per-feature catalog with Sync-from-`/models` diff review |
| Master-key management | None visible | Server-managed; UI shows key fingerprint + virtual keys | Per-provider master-key set/test/rotate with transactional rollback |
| Tenant impersonation | Not visible | Not surfaced in OSS | **Not built** — explicitly anti-feature for trust posture |
| Spend dashboard | `AdminTopSpenders` (single sortable table) | Top spenders + per-team budgets + cap | Top-line cards + provider/feature breakdowns + top-N tenants |
| Worker queue panel | None (Vercel handles execution) | Health panel for proxy itself | Postgres outbox + processing_job stats + dead-letter re-queue |
| Settings model picker | Free-form provider + model with curated suggestions | Models filtered by team allowlist | Picker reads `/api/catalog/models?feature=...` — no free-form ID |

---

## Sources

- **Local Inbox Zero clone:** `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/app/(app)/admin/*` (admin page + components); `apps/web/utils/admin.ts` (`isAdmin` allowlist pattern). HIGH confidence — direct source inspection.
- **Zero Mail prior research:** `.planning/research/FEATURES.md` revision for v1.1 (deferred Settings reqs SET-*); `.planning/PROJECT.md` (validated v1.0/v1.1 features, Active v1.2 scope, "Out of Scope" privacy constraints); `CLAUDE.md` Privacy + write-action constraints; `.planning/seeds/SEED-011-admin-support-and-compliance-console.md`. HIGH confidence — internal repository.
- **[WorkOS — How to design RBAC for multi-tenant SaaS](https://workos.com/blog/how-to-design-multi-tenant-rbac-saas)** — tenant-aware authorization decisions, role change auditability. MEDIUM confidence (one secondary source; principles are well-trodden).
- **[Agnite Studio — Audit Logging Design in SaaS](https://agnitestudio.com/blog/audit-logging-saas/)** — immutability, append-only design, tenant filtering, retention strategies. MEDIUM confidence.
- **[Microsoft Learn — Multitenant identity approaches](https://learn.microsoft.com/en-us/azure/architecture/guide/multitenant/approaches/identity)** — impersonation audit requirements (log both impersonator + impersonated user). HIGH confidence (vendor docs).
- **[Idee — Cross-tenant impersonation](https://www.getidee.com/blog/what-is-cross-tenant-impersonation)** — impersonation as recognized attack surface in multi-tenant SaaS. MEDIUM confidence.
- **[Google Cloud KMS — Key rotation](https://cloud.google.com/kms/docs/key-rotation)** — rotate-then-retire pattern, old key kept for decrypt of historical ciphertexts. HIGH confidence (vendor docs).
- **[Ubiq Security — Key wrapping best practices](https://dev.ubiqsecurity.com/docs/key-mgmt-best-practices)** — envelope encryption (DEK + KEK); rotate KEK without re-encrypting all data. MEDIUM confidence.
- **[NIST SP 800-38D / AES-GCM rotation limits](https://www.crypteron.com/blog/pci-dss-key-rotations-simplified/)** (secondary citation) — ~2^32 encryption operations safety limit per key; PCI 90-day rotation cadence. MEDIUM confidence.
- **[PlanetScale — Keeping a Postgres queue healthy](https://planetscale.com/blog/keeping-a-postgres-queue-healthy)** — oldest-unprocessed-age as canonical lag metric; vacuum/bloat concerns for high-churn queues. HIGH confidence.
- **[Neon — Queue System using SKIP LOCKED](https://neon.com/guides/queue-system)** — `FOR UPDATE SKIP LOCKED` semantics; worker death + lock release pattern. HIGH confidence.
- **[LiteLLM — Model Management + Proxy UI](https://docs.litellm.ai/docs/proxy/model_management)** — closest peer for curated-catalog + per-team allowlist + virtual-key admin pattern. MEDIUM confidence (vendor docs; their UI is more enterprise-y than our v1.2 needs).
- **[LiteLLM Enterprise — model allowlists, budgets](https://docs.litellm.ai/docs/enterprise)** — per-project budgets + rate limits + model allowlists as canonical features. MEDIUM confidence.

---

*Feature research for: v1.2 — Admin Console Foundation + Settings UI on curated catalog*
*Researched: 2026-05-19*
