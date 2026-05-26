# Phase 9: User Settings UI on Curated Catalog — Research

**Researched:** 2026-05-26 (refreshed after D-17 revised round 2)
**Domain:** Java 25 / Spring Boot 4 / Spring AI M7 backend REST + Next.js 16 / shadcn / next-intl frontend; Gmail API in-memory read; AES-GCM BYOK reuse; Postgres + Liquibase YAML migrations
**Confidence:** HIGH — every claim is grounded in code grep (`backend/core`, `backend/api`, `apps/web`) or in the locked design contract at `09-CONTEXT.md` + `09-UI-SPEC.md`.

## Summary

Phase 9 is **integration, not invention**. Every backend ingredient already exists: `AssistantSettingsEntity` and `AssistantKnowledgeMemoryEntity` (extend, do not create); `PersonalizationSanitizer` (reuse); `ModelsProbeClient.probeConnection` + `MasterKeyTestResult` enum `{OK, INVALID_KEY, RATE_LIMITED, NETWORK_ERROR, TIMEOUT}` (extract `ProviderConnectionTester` per D-14); `SenderSafetyNetController` + `TenantProtectedSenderObservationEntity` (extend with `pattern_kind`/`created_by_user` + DELETE); `RefreshTokenCipher` AES-GCM (already at-rest for OAuth refresh tokens — Phase 9 BYOK reuses it); `GmailApiClientFactory`; `SpringAiProviderChatClientFactory.openAiCompatibleModel(...)` which already takes `credential.baseUrl()` as a runtime per-call argument (so swapping BYOK base URL is a one-liner at the resolver layer, not a Spring AI starter reconfiguration).

The phase work decomposes into:
1. **Liquibase changeset 094-XXX** adding 5 columns to `assistant_settings` (`email_signature`, `tone_preset`, `auto_draft_replies`, `draft_confidence`, `sensitive_data_protection`). **NO `ai_provider_mode` column** — D-17 revised round 2 retired it; the `active` flag on the new BYOK row IS the on/off switch.
2. **Changeset 095** adding `UNIQUE(tenant_id, title)` to `assistant_knowledge_snippet` (`updated_at` column already exists from changeset 046).
3. **Changeset 096** adding `pattern_kind`, `created_by_user` to `tenant_protected_sender_observation` AND `blocked_by_safety_net_pattern VARCHAR(320)` to `triage_audit`.
4. **Changeset 097** creating a NEW `user_byok_key` table: `tenant_id PK, provider VARCHAR(16), base_url VARCHAR(255), api_key_ciphertext BYTEA, api_key_iv BYTEA, model_id VARCHAR(64) NULL, active BOOLEAN NOT NULL DEFAULT FALSE, last_test_result VARCHAR(16) NULL, last_tested_at TIMESTAMPTZ NULL`. Exactly one row per tenant.
5. **`ProviderConnectionTester` extraction (D-14)** in `core.llm.gateway.springai` from `ModelsProbeClient.probeConnection` + `fetchModelCatalog`. Returns a value object `ConnectionTestResult(result, models?)`. `MasterKeyAdminService` MKEY-03 refactored to delegate. New `UserByokController.testConnection` delegates after a per-tenant 10/hour rate-limit check.
6. **`ByokProviderResolver`** (new in `core.chat.byok` or `core.llm.byok`) — reads the `user_byok_key` row and gates: if `active = TRUE AND model_id IS NOT NULL AND last_test_result = 'OK'` → return a `ResolvedLlmProviderCredential`; else return `Optional.empty()` → caller falls back to the admin-catalog platform default.
7. **Composition with existing `TenantByokProviderCredentialResolver`.** The legacy resolver reads `tenant_byok_credentials` (legacy table). The new `ByokProviderResolver` reads `user_byok_key` (Phase 9 table). The two coexist or replace; the planner must pick. **Recommendation:** the new `ByokProviderResolver` REPLACES the legacy resolver — both `LlmGatewayImpl` and `SpringAiChatModelFactory` already inject `TenantByokProviderCredentialResolver`, so swap the implementation behind the same Spring bean type OR migrate call sites to `ByokProviderResolver`. The legacy `tenant_byok_credentials` table + `TenantByokCredentialsEntity` + `ByokService` + `ByokController` are deleted alongside their changelogs 018/019/020 left in place (data migrated forward in Wave 0 — single user, single row, easy migration).
8. **New REST surface** (replaces legacy `/api/llm/byok`):
   - `GET /api/byok`
   - `POST /api/byok` (save provider + baseUrl + apiKey + optional modelId)
   - `PUT /api/byok/active`
   - `PUT /api/byok/model`
   - `DELETE /api/byok`
   - `POST /api/byok/test-connection` (`{}` for stored OR `{provider, baseUrl, apiKey}` for inline pre-save)
   - `GET /api/settings/ai/cost?window=7d` → `{usd: number}` single tenant-wide SUM
   - `POST /api/settings/voice/generate-from-sent` (SET-VOICE-07)
9. **Frontend refactor** of `apps/web/features/ai/components/AiConfigPage.tsx` into five `<SectionHeader>` groups. The `AiProviderSection` is ONE `SettingCard` (provider · base URL · key · model · active switch · test · save), NOT three feature rows. `ByokForm` moves from `SettingsClient.tsx` and is rebuilt inline in `AiProviderSection.tsx`.
10. **2 ArchUnit invariants** — single `PersonalizationSanitizer` call site + single `AssistantKnowledgeService.append` call site — plus a sentinel-leak integration test for the SET-VOICE-07 privacy invariant AND a `ProviderConnectionTesterSingleBindingTest` proving both admin MKEY-03 and user `POST /api/byok/test-connection` reach the same `ProviderConnectionTester.probeConnection` method.

**Primary recommendation:** Plan a 3-wave build. **Wave 0** = Liquibase + entity scaffolding + DTO + test scaffolding + data migration of any existing legacy `tenant_byok_credentials` rows into the new `user_byok_key` shape. **Wave 1** = backend services + controllers + `ProviderConnectionTester` extraction + `ByokProviderResolver` + ArchUnit. **Wave 2** = frontend section refactor + Knowledge CRUD + AI Provider section + OpenAPI regen + Playwright e2e. SET-VOICE-07 generate-from-sent is the highest-risk surface (privacy invariant + LLM call + Gmail API in-memory) — give it a dedicated plan and an integration test that seeds sentinel content into Gmail-API stubs and greps captured logs + audit + DB for leaks.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01.** `/ai` page uses **flat `<SectionHeader>` groups** on a single `/ai/page.tsx` — NOT shadcn `<Tabs>`, NOT query-param tab routing. Section order: `Your voice`, `Behavior`, `Updates`, `Safety net`, `AI Provider`.

**D-02.** Every multi-field setting uses the `SettingCard` (title + description + Edit/Set button) → shadcn `Dialog` edit pattern. NOT inline edit.

**D-03.** Short toggles (`auto_draft_replies`, `daily_digest`, `sensitive_data_protection`, `shadow_mode`) render INLINE as shadcn `<Switch>` on the `SettingCard` body. No Dialog needed for boolean fields.

**D-04.** Knowledge snippets render as a shadcn `<Table>` (Title | Last Updated | Edit | Delete) with `+ Add` button opening a Dialog containing `KnowledgeForm`. Edit on a row opens the same Dialog prefilled. Delete uses `ConfirmDialog`.

**D-05.** Backend adds `UNIQUE(tenant_id, title)` constraint on `assistant_knowledge_snippet` + `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` (already exists since changelog 046).

**D-06.** BYOK stays on `/ai` inside `AI Provider` — NOT split to `/settings`. (Updated 2026-05-26 — the original wording mentioned a per-feature model picker; that picker has since been removed by D-08 revised + D-17 revised.)

**D-07.** `SET-BEHV-02` exposes draft confidence as `LOW | MEDIUM | HIGH` enum via shadcn `<Select>`. Backend stores enum in `assistant_settings.draft_confidence VARCHAR(8)` and maps to internal numeric thresholds (`LOW=0.50, MEDIUM=0.70, HIGH=0.85`) when calling the draft worker.

**D-08 (revised 2026-05-26 round 2).** `PUT /api/settings/ai` is REMOVED. Backend exposes `PUT /api/settings/voice`, `PUT /api/settings/behavior`, and a separate BYOK resource at `/api/byok/*` (GET, POST, PUT `/active`, PUT `/model`, DELETE, POST `/test-connection`). Cost endpoint: `GET /api/settings/ai/cost?window=7d` → `{usd: number}`. Voice generate: `POST /api/settings/voice/generate-from-sent`. Knowledge CRUD: `GET/POST/PUT/DELETE /api/knowledge-snippets`. Safety net: existing `/api/triage/sender-safety-net/*` family extended with DELETE.

**D-09.** When `tone_preset = 'CUSTOM'`, system prompt uses ONLY `writing_style`. For any other preset, both preset and writing_style are passed to prompt assembler. NO new `custom_tone_description` column.

**D-10..D-12.** SET-VOICE-07: `POST /api/settings/voice/generate-from-sent` (`{sampleSize}` default 20, max 50) returns `{generatedStyle}` ≤ 500 words. Rate-limit 3/hour/tenant.

**D-11 PRIVACY INVARIANT (LOCKED):** raw email bodies, LLM prompt, LLM completion MUST be in-memory-only. Only user-reviewed style guide persists via subsequent `PUT /api/settings/voice`.

**D-13.** Phase 9 code stays inside `core.chat` module. New sub-package `core.chat.settings` for REST surface. Safety net stays inside `core.triage`. NO new top-level module. Modulith named-interface `core.chat::settings-api`.

**D-14.** Extract a shared `ProviderConnectionTester` service in `core.llm.gateway.springai` from `ModelsProbeClient.probeConnection` + `fetchModelCatalog`. Admin MKEY-03 endpoint refactored to delegate. User-side `POST /api/byok/test-connection` is a thin wrapper that enforces per-tenant 10/hour rate-limit before delegating. **The actual code enum is `{OK, INVALID_KEY, RATE_LIMITED, NETWORK_ERROR, TIMEOUT}`** — the CONTEXT D-14 wording says `UNSUPPORTED`, but the actual `MasterKeyTestResult.java` source has `TIMEOUT`, not `UNSUPPORTED`. Plans MUST use the real enum from `MasterKeyTestResult`.

**D-15.** Daily-digest toggle (`SET-BEHV-03`) reuses existing v1.0 `/api/me/notifications`. Shadow-mode toggle (`SET-BEHV-05`) reuses existing v1.0 `PUT /api/tenant/triage-pause`. NO new column on `assistant_settings`.

> **Heads-up to planner:** `tenants.triage_shadow_mode` was DROPPED in changelog `039-drop-triage-shadow-mode.yaml`. The surviving toggle is the triage **pause** flag (`TriagePauseController.PUT /api/tenant/triage-pause`). Plan should rename UI string from "Shadow mode" to "Pause triage" and keep backend unchanged.

**D-16.** `ByokForm` removed from `apps/web/app/(protected)/(app)/settings/SettingsClient.tsx` and rendered exactly once inside the `AI Provider` section on `/ai`. AES-GCM cipher reused. Legacy `/settings` keeps all other cards untouched.

**D-17 (revised 2026-05-26 round 2).** The `AI Provider` section is ONE BYOK card. There is NO separate `Platform default ↔ Use my key` mode card and NO per-feature picker. The card contains, top-to-bottom: Provider `<Select>` · Base URL `<Input>` (auto-filled per provider, user-editable) · API key `<Input type="password">` · Model `<Select>` (populated from `/v1/models` after Test connection) · Active `<Switch>` (default OFF, disabled until model picked AND last test OK) · `Kiểm tra kết nối` button · `Lưu` button. Persistence: NEW table `user_byok_key (tenant_id PK, provider, base_url, api_key_ciphertext, api_key_iv, model_id NULL, active DEFAULT FALSE, last_test_result NULL, last_tested_at NULL)`. Saving a new provider replaces the previous row AND resets `active=false, last_test_result=NULL, last_tested_at=NULL`. NO `assistant_settings.ai_provider_mode` column — the `active` flag IS the on/off switch. Provider allow-list: OpenAI / Anthropic / Google / DeepSeek (server-side reject for `openrouter` / `router_9r`). Resolution rule: row exists AND `active=TRUE` AND `model_id IS NOT NULL` AND `last_test_result='OK'` → use BYOK; otherwise platform default.

### Claude's Discretion

(None — every gap was resolved during discuss-phase + plan-phase round 2.)

### Deferred Ideas (OUT OF SCOPE)

- **SET-SAFE-02** paste-import — deferred to v1.3.
- **SET-SAFE-03** per-entry `protect`/`escalate` mode toggle — deferred to v1.3; every user-added entry behaves as `protect`.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SET-VOICE-01 | Writing-style free-text (200–500 words) edit | `AssistantSettingsEntity.writingStyle` exists; add `PUT /api/settings/voice` + Bean Validation `@Size` + service-layer word-count check |
| SET-VOICE-02 | Personal instructions edit (XML-fenced, injection-sanitized, 2000 cap) | `PersonalizationSanitizer` exists; ArchUnit proves single call site (chat-tool + REST) |
| SET-VOICE-03 | Email signature edit (500 cap) | New column `assistant_settings.email_signature TEXT` |
| SET-VOICE-04 | Knowledge-snippets CRUD with title uniqueness | `AssistantKnowledgeMemoryEntity` exists; add `UNIQUE(tenant_id, title)` + REST CRUD; chat tool `ADD_TO_KNOWLEDGE_BASE` and REST POST share `AssistantKnowledgeService.append` (ArchUnit) |
| SET-VOICE-05 | Tone preset enum (PROFESSIONAL/FRIENDLY/CASUAL/FORMAL/CUSTOM) | New column `assistant_settings.tone_preset VARCHAR(16) + CHECK` |
| SET-VOICE-06 | AI output language radio (vi/en) | `assistant_settings.ai_output_language` exists; PUT validation `@Pattern("^(vi|en)$")` |
| SET-VOICE-07 | Generate writing style from recent sent emails (LLM + Gmail API) | `POST /api/settings/voice/generate-from-sent`; Gmail `users.messages.list filter in:sent` via `GmailApiClientFactory`; LLM via `LlmGateway`; rate-limit 3/hour/tenant; in-memory-only privacy invariant |
| SET-BEHV-01 | Auto-draft replies toggle | New column `assistant_settings.auto_draft_replies BOOLEAN DEFAULT TRUE`; draft worker reads flag |
| SET-BEHV-02 | Draft confidence enum LOW/MEDIUM/HIGH | New column `assistant_settings.draft_confidence VARCHAR(8) DEFAULT 'MEDIUM' CHECK`; threshold mapping in draft worker |
| SET-BEHV-03 | Daily digest toggle | Reuse `NotificationPreferencesController.PATCH /api/me/notifications` `digestEnabled` — no backend changes |
| SET-BEHV-04 | Sensitive-data-protection toggle | New column `assistant_settings.sensitive_data_protection BOOLEAN DEFAULT TRUE`; LLM-05 redactor reads flag |
| SET-BEHV-05 | Shadow-mode toggle | Reuse `TriagePauseController.PUT /api/tenant/triage-pause` (UI label renamed to "Pause triage"; backend column is `tenants.triage_paused`, not `triage_shadow_mode`) |
| SET-SAFE-01 | Sender safety net CRUD with email + domain pattern | Extend `tenant_protected_sender_observation` with `pattern_kind VARCHAR(8) DEFAULT 'EMAIL' CHECK IN ('EMAIL','DOMAIN')` + `created_by_user BOOLEAN DEFAULT FALSE`; add `DELETE /api/triage/sender-safety-net/{id}`; extend POST opt-in to accept `@acme.com`; extend `SenderEmailCanonicalizer` |
| SET-SAFE-04 | Audit-log indicator for safety-net block | New column `triage_audit.blocked_by_safety_net_pattern VARCHAR(320) NULL`; triage worker sets when `decision = REJECTED_BY_SAFETY_NET`; `AuditRow.tsx` renders Badge |
| SET-AI-01 | BYOK active-switch gate (no per-feature picker, no mode card) | NEW table `user_byok_key`; `ByokProviderResolver` decides per-call between BYOK and admin-curated platform default based on `active && model_id IS NOT NULL && last_test_result='OK'`. `PUT /api/byok/active {active:true}` rejects with 400 `code=ai.byok.no_model_picked` when the gate fails. |
| SET-AI-02 | Single BYOK row with Provider + Base URL + API key + Model | `user_byok_key` shape: `tenant_id PK, provider VARCHAR(16), base_url VARCHAR(255), api_key_ciphertext BYTEA, api_key_iv BYTEA, model_id VARCHAR(64) NULL, active BOOLEAN DEFAULT FALSE, last_test_result VARCHAR(16) NULL, last_tested_at TIMESTAMPTZ NULL`. `POST /api/byok` accepts `{provider, baseUrl, apiKey, modelId?}`. AES-GCM via existing `RefreshTokenCipher`. Provider allow-list rejects `OPENROUTER`/`ROUTER_9R` with `code=ai.byok.provider_not_allowed`. Base URL must be `https://` (or `http://localhost*` for dev) with `code=ai.byok.base_url_not_https` rejection otherwise. |
| SET-AI-03 | Single tenant-wide last-7d cost | New `GET /api/settings/ai/cost?window=7d` returns `{usd: number}` via `SUM(llm_call_audit.total_cost_usd) WHERE tenant_id = ? AND created_at >= now() - interval '7 days'`. **No `call_site=CHAT` enum addition required** — aggregation is tenant-scoped, not feature-scoped. The existing `ck_llm_call_audit_call_site` CHECK constraint is UNTOUCHED. |
| SET-AI-04 | BYOK test-connection enum + model list (shared with admin MKEY-03) | Extract `ProviderConnectionTester.probeConnection(provider, baseUrl, ciphertext) -> ConnectionTestResult(result, models?)` from `ModelsProbeClient`. Admin MKEY-03 refactored to delegate. New `POST /api/byok/test-connection` accepts `{}` (stored row) OR `{provider, baseUrl, apiKey}` (inline pre-save); persists `last_test_result + last_tested_at` on stored-row path. Rate-limit 10/hour/tenant. ArchUnit `ProviderConnectionTesterSingleBindingTest` asserts both paths reach `ProviderConnectionTester.probeConnection`. |

</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Writing-style / personal-instructions / signature / tone / language CRUD | API / Backend | DB | Persisted in `assistant_settings`; sanitization is backend invariant |
| Knowledge-snippet CRUD | API / Backend | DB | Persisted in `assistant_knowledge_snippet`; uniqueness enforced at Postgres |
| Tone-preset → prompt assembler glue | API / Backend (Spring AI adapter) | — | D-09 logic lives where the system prompt is built, not in UI |
| Draft confidence threshold mapping | API / Backend (draft worker) | — | Enum→numeric translation runs server-side; UI never sees thresholds |
| Auto-draft / sensitive-data toggles | API / Backend | DB | Boolean column read by draft + LLM-05 redactor workers |
| Daily digest toggle | API / Backend (existing notifications module) | — | Reuses `NotificationPreferenceService` |
| Triage pause ("shadow mode" UI label) | API / Backend (existing tenant module) | — | Reuses `TenantService.setTriagePaused` |
| Safety-net CRUD with pattern_kind | API / Backend (triage module) | DB | Stays inside `core.triage` per D-13 |
| Safety-net block badge | API / Backend (`triage_audit` column) | Frontend (Badge render) | Backend sets `blocked_by_safety_net_pattern`; FE renders Badge |
| **BYOK active-switch gate** | API / Backend (`ByokProviderResolver`) | — | The single source of truth that decides BYOK vs platform default per chat/triage/draft/voice-generate call. Replaces D-17-round-1's `ai_provider_mode` column and the per-feature picker entirely. |
| **`ByokProviderResolver` resolution** | API / Backend (`core.chat.byok` or `core.llm.byok`) | DB (`user_byok_key`) | Reads `user_byok_key`; if `active && model_id IS NOT NULL && last_test_result='OK'` → returns `ResolvedLlmProviderCredential` with decrypted key, base URL, model id; else `Optional.empty()`. Replaces `TenantByokProviderCredentialResolver` as the single resolver bean injected into `LlmGatewayImpl` + `SpringAiChatModelFactory`. |
| **`ProviderConnectionTester` extraction** | API / Backend (`core.llm.gateway.springai`) | — | Shared service called by both admin `MasterKeyAdminService` MKEY-03 path AND new `UserByokController.testConnection`. Single sentinel-leak scrub (ARCH-11), single enum response shape. |
| BYOK key entry | API / Backend (cipher) | Frontend (form) | AES-GCM at rest; FE never re-renders plaintext |
| BYOK test connection | API / Backend (`ProviderConnectionTester`) | — | Provider HTTP call confined to backend; FE only sees enum result + (when OK) model list |
| BYOK model select | API / Backend (`PUT /api/byok/model`) | Frontend (Select populated from latest test response) | Model list lives in client state until Save; persistence on PUT |
| Last-7d cost helper | API / Backend (`SUM llm_call_audit`) | Frontend (helper text) | Tenant-wide SUM in SQL; FE renders dollar string |
| Generate-from-sent (SET-VOICE-07) | API / Backend (Gmail + LLM in-memory) | Frontend (Dialog button) | Privacy invariant requires all bytes to live in backend memory only |

## Standard Stack

### Core (already in project — no installs needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.6 | App framework | Locked by `CLAUDE.md`. `[VERIFIED: STACK.md + libs.versions.toml]` |
| Spring AI | 2.0.0-M7 | LLM orchestration via `SpringAiProviderChatClientFactory.openAiCompatibleModel(...)` which already takes `credential.baseUrl()` as a per-call runtime argument | Locked. `[VERIFIED: SpringAiProviderChatClientFactory.java:128–135]` |
| Spring Data JPA (Hibernate 7) | Boot-managed | `AssistantSettingsEntity` + new `UserByokKeyEntity` writes | Existing convention |
| Spring Data JDBC | Boot-managed | `AiCostQueryService` SUM aggregation (mirrors `SpendAggregateReadRepository`) | Existing convention |
| Liquibase | 5.0.2 | YAML changesets for new columns + table | Locked; next free integer is `094`. `[VERIFIED: db.changelog-master.yaml]` |
| `jakarta.validation` (Bean Validation) | Jakarta 3.x | `@Size`, `@NotBlank`, `@Pattern` on request DTOs | Required by convention §3 for accurate OpenAPI codegen |
| `io.swagger.v3.oas.annotations.media.Schema` | springdoc-openapi | `@Schema(requiredProperties = {...})` on response DTOs | Required by convention §3 |
| `google-api-services-gmail` | Boot-managed via `GmailApiClientFactory` | SET-VOICE-07 in-memory read | Already wired in `core.gmail.gateway` |
| Spring `RestClient` (with cleartext + HTTPS qualifiers) | Boot-managed | Reused inside extracted `ProviderConnectionTester` (HTTP/2 fallback for cleartext targets) | Already configured in `ModelsProbeClient` |
| `RefreshTokenCipher` | Existing | AES-GCM for `api_key_ciphertext` in `user_byok_key` | ARCH-11 friendly; no new cipher |

### Frontend (already in project — every shadcn primitive confirmed by `Glob apps/web/components/ui/*.tsx`)

| Library | Version | Purpose |
|---------|---------|---------|
| Next.js | 16.2.4 (App Router) | `/ai/page.tsx` server component shell |
| React | 19.2.5 | Client components |
| shadcn primitives (verified present: `dialog`, `alert-dialog`, `card`, `button`, `input`, `textarea`, `switch`, `select`, `radio-group`, `table`, `badge`, `separator`, `label`, `tooltip`, `spinner`, `sonner`) | latest | All Phase 9 sections — including the new AI Provider card (Select + Input + Switch + Button) — covered without `pnpm dlx shadcn add` |
| TanStack Query | 5.100.1 | One hook file per use-case; `meta.successMessage`/`meta.errorMessage` for toasts |
| openapi-typescript + openapi-fetch | 7.13.0 / 0.17.0 | Typed client via `apps/web/lib/api/schema.d.ts` (regenerated by `pnpm --filter web run generate:api`) |
| next-intl | existing | VI default + EN; bundles at `apps/web/i18n/messages/{vi,en}.json` |
| lucide-react | existing | Section + action icons (`Settings`, `Bot`, `Shield`, `Bell`, `Sparkles`) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff (why we don't) |
|------------|-----------|----------|
| `SettingCard` + `Dialog` edit | Inline edit on Card | D-02 locks Dialog pattern (Inbox Zero parity) |
| `<Select>` LOW/MEDIUM/HIGH | shadcn `<Slider>` 0.0–1.0 | D-07 locks enum (Inbox Zero parity + future-tuning ergonomics) |
| One mega `PUT /api/settings` | Per-resource PUTs | D-08 locks split (clean TanStack Query keys, OpenAPI codegen, Spring controller boundaries) |
| `assistant_settings.ai_provider_mode` enum + per-feature picker | Single `user_byok_key.active` flag + single model picker on the BYOK row | D-17 revised — eliminates the mode card, the resolved-provider helper list, and any per-feature surface. Active flag is the on/off switch. |
| Per-feature cost aggregation (`{chat, triage, draft}` USD) | Single tenant-wide cost (`{usd}`) | D-17 revised — no `call_site=CHAT` schema change required |
| Adding `active` + `last_test_result` to legacy `tenant_byok_credentials` table | NEW table `user_byok_key` | CONTEXT D-17 locks a new table. The legacy table + `TenantByokCredentialsEntity` + `ByokService` + `ByokController` are deleted in Wave 0; existing rows (single user, single tenant) migrated forward. |

**No new package installs required for Phase 9 — every library is already a project dependency.**

## Package Legitimacy Audit

> **Skipped — no new external packages installed in this phase.** All capabilities use existing project dependencies. slopcheck not applicable.

## Architecture Patterns

### System Architecture Diagram

```
User browser
    │
    ▼ HTTPS (Next.js SSR + client hydration)
/ai/page.tsx  ──►  <AiConfigPage> client component
                   │
                   ├─ <YourVoiceSection>   ──► useVoiceSettings (Query) ──► GET /api/settings/voice
                   │      └─ SettingCard ──► Dialog ──► useUpdateVoiceSettings ──► PUT /api/settings/voice
                   │      └─ <KnowledgeTable> ──► useKnowledgeSnippets ──► GET /api/knowledge-snippets
                   │                                          POST/PUT/DELETE /api/knowledge-snippets[/{id}]
                   │      └─ <WritingStyleDialog>            ──► POST /api/settings/voice/generate-from-sent
                   │
                   ├─ <BehaviorSection>    ──► useBehaviorSettings  ──► GET/PUT /api/settings/behavior
                   ├─ <UpdatesSection>     ──► useNotificationPrefs ──► GET/PATCH /api/me/notifications (existing)
                   │                          useTriagePauseState  ──► PUT /api/tenant/triage-pause (existing — UI labeled "Pause triage")
                   ├─ <SafetyNetSection>   ──► useProtectedSenders ──► GET /api/triage/sender-safety-net
                   │                                                   POST .../{pattern}/opt-in
                   │                                                   DELETE .../{id}  (NEW)
                   │                          (Auto-send rules toggle stays — reuses RuleAutomationSettings)
                   └─ <AiProviderSection>  ──► ONE SettingCard:
                                              useByok               ──► GET /api/byok
                                              useSaveByok           ──► POST /api/byok      (resets active=false, last_test_*=NULL)
                                              useTestByokConnection ──► POST /api/byok/test-connection
                                              useSelectByokModel    ──► PUT /api/byok/model
                                              useActivateByok       ──► PUT /api/byok/active  (rejects when model missing or last test ≠ OK)
                                              useDeleteByok         ──► DELETE /api/byok
                                              useAiCost             ──► GET /api/settings/ai/cost?window=7d

Backend (Spring MVC controllers in backend/api)
    │
    ├─ controllers/settings/  (NEW)
    │     ├─ SettingsVoiceController       → SettingsVoiceService          → AssistantSettingsJpaRepository
    │     │                                   └─ PersonalizationSanitizer (existing — single instance)
    │     ├─ SettingsBehaviorController    → SettingsBehaviorService       → AssistantSettingsJpaRepository
    │     ├─ SettingsAiCostController      → AiCostQueryService (NEW — JDBC over llm_call_audit; tenant-wide SUM)
    │     ├─ KnowledgeSnippetController    → AssistantKnowledgeService     (extended for update/delete)
    │     └─ VoiceGenerateController       → VoiceGenerationService (NEW)
    │                                          ├─ GmailSentMessagesReader (NEW — in-memory only)
    │                                          ├─ LlmGateway (existing)
    │                                          └─ RateLimiter (Redis Lettuce)
    │
    ├─ controllers/byok/  (NEW)
    │     └─ UserByokController            → UserByokService (NEW)
    │                                          ├─ UserByokKeyRepository (NEW)
    │                                          ├─ RefreshTokenCipher (existing AES-GCM)
    │                                          ├─ BaseUrlValidator (NEW — https-only with localhost dev allow)
    │                                          ├─ ProviderAllowList (NEW — rejects OPENROUTER/ROUTER_9R)
    │                                          └─ ProviderConnectionTester (NEW — extracted from ModelsProbeClient)
    │
    ├─ controllers/triage/SenderSafetyNetController (extended)
    │     └─ SenderSafetyNetService (extended) → TenantProtectedSenderObservationRepository (extended entity)
    │
    └─ controllers/admin/AdminMasterKeyController (existing — refactored to delegate to ProviderConnectionTester for MKEY-03)

ChatModel resolution (per-call):
    LlmGatewayImpl.chat / SpringAiChatModelFactory.resolveForChat
        └─► ByokProviderResolver.resolve(tenantId, fallbackModel)        [NEW — replaces TenantByokProviderCredentialResolver]
              └─► reads user_byok_key WHERE tenant_id = ?
              ├─ active && model_id IS NOT NULL && last_test_result = 'OK'
              │     → return Optional<ResolvedLlmProviderCredential>(
              │           providerId, model_id, decrypted key, base_url, source=BYOK)
              └─ otherwise
                    → return Optional.empty()  → caller uses admin-catalog platform default
        └─► passes to SpringAiProviderChatClientFactory.openAiCompatibleModel(...)
              .baseUrl(credential.baseUrl())  ← key insight: Spring AI accepts per-call baseUrl
              .apiKey(plaintextApiKey)
              .options(...model_id...)

Postgres
    ├─ assistant_settings           (+5 columns via Liquibase 094 — NO ai_provider_mode)
    ├─ assistant_knowledge_snippet  (+UNIQUE(tenant_id,title) via Liquibase 095; updated_at exists since 046)
    ├─ tenant_protected_sender_observation  (+pattern_kind, +created_by_user via Liquibase 096)
    ├─ triage_audit                 (+blocked_by_safety_net_pattern via Liquibase 096)
    ├─ llm_call_audit               (UNCHANGED — D-17 retired the CHAT enum addition)
    ├─ tenant_byok_credentials      (LEGACY — deleted/migrated forward in Wave 0)
    └─ user_byok_key                (NEW via Liquibase 097 — tenant_id PK + provider/base_url/api_key_ciphertext/api_key_iv/model_id/active/last_test_result/last_tested_at)
```

### Recommended Project Structure

```
backend/core/src/main/java/com/zeromail/core/chat/
├── persistence/
│   ├── AssistantSettingsEntity.java                 [EXTEND with 5 columns — voice/behavior fields ONLY; NO `ai_provider_mode`]
│   ├── AssistantSettingsJpaRepository.java          [Existing findByTenantId reused]
│   └── AssistantKnowledgeMemoryEntity.java          [No change — already tenant-scoped]
├── usecases/
│   ├── AssistantKnowledgeService.java               [EXTEND: list/update/delete; keep append() for chat-tool reuse]
│   ├── settings/                                    [NEW sub-package per D-13]
│   │   ├── SettingsVoiceService.java
│   │   ├── SettingsBehaviorService.java
│   │   ├── VoiceGenerationService.java              [SET-VOICE-07; calls LlmGateway + Gmail in-memory]
│   │   └── AiCostQueryService.java                  [JDBC SUM over llm_call_audit — tenant-wide]
│   └── package-info.java                            [Modulith named-interface settings-api]
└── sanitize/PersonalizationSanitizer.java           [No change — REUSED by SettingsVoiceService AND chat-tool]

backend/core/src/main/java/com/zeromail/core/llm/byok/         [NEW package OR core.chat.byok — planner picks based on Modulith boundary]
├── UserByokKeyEntity.java                           [NEW JPA entity: tenant_id PK, provider, base_url, api_key_ciphertext, api_key_iv, model_id, active, last_test_result, last_tested_at]
├── UserByokKeyRepository.java                       [NEW Spring Data JPA repo]
├── UserByokService.java                             [save/load/activate/setModel/delete; uses RefreshTokenCipher]
├── ByokProviderResolver.java                        [NEW — replaces TenantByokProviderCredentialResolver as the single resolver bean]
├── BaseUrlValidator.java                            [NEW — https-only with localhost dev allow]
└── ProviderAllowList.java                           [NEW — rejects OPENROUTER/ROUTER_9R]

backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/
└── ProviderConnectionTester.java                    [NEW per D-14; extracted from ModelsProbeClient.probeConnection + fetchModelCatalog; returns ConnectionTestResult(result, models?)]

backend/core/src/main/java/com/zeromail/core/triage/
├── persistence/TenantProtectedSenderObservationEntity.java   [EXTEND: pattern_kind, created_by_user]
└── usecases/
    ├── SenderSafetyNetService.java                  [EXTEND: deleteByIdAndTenant, accept domain patterns]
    └── SenderEmailCanonicalizer.java                [EXTEND: canonicalize `@acme.com` as DOMAIN pattern]

backend/api/src/main/java/com/zeromail/api/
├── controllers/settings/
│   ├── SettingsVoiceController.java                 [NEW]
│   ├── SettingsBehaviorController.java              [NEW]
│   ├── SettingsAiCostController.java                [NEW — single GET /api/settings/ai/cost?window=7d]
│   ├── KnowledgeSnippetController.java              [NEW]
│   └── VoiceGenerateController.java                 [NEW]
├── controllers/byok/                                [NEW per D-17]
│   └── UserByokController.java                      [GET/POST/DELETE /api/byok, PUT /api/byok/active, PUT /api/byok/model, POST /api/byok/test-connection]
├── controllers/triage/SenderSafetyNetController.java [EXTEND: DELETE + accept domain pattern]
├── controllers/admin/AdminMasterKeyController.java   [REFACTOR MKEY-03 endpoint to delegate to ProviderConnectionTester]
└── dto/
    ├── settings/
    │   ├── VoiceSettingsResponse.java / VoiceSettingsUpdateRequest.java
    │   ├── BehaviorSettingsResponse.java / BehaviorSettingsUpdateRequest.java
    │   ├── AiCostResponse.java                      [{usd: number}]
    │   ├── GenerateFromSentRequest.java / GenerateFromSentResponse.java
    │   └── KnowledgeSnippetRequest.java / Response.java / ListResponse.java
    └── byok/                                        [NEW]
        ├── ByokResponse.java                        [{provider, baseUrl, lastFourChars, modelId, active, lastTestResult, lastTestedAt} — NEVER plaintext]
        ├── ByokSaveRequest.java                     [{provider, baseUrl, apiKey, modelId?}]
        ├── ByokActivateRequest.java                 [{active: bool}]
        ├── ByokModelRequest.java                    [{modelId: string}]
        ├── ByokTestConnectionRequest.java           [optional {provider, baseUrl, apiKey} for inline test]
        └── ByokTestConnectionResponse.java          [{result, models?: string[]}]

backend/core/src/main/resources/db/changelog/changes/
├── 094-assistant-settings-phase9-columns.yaml       [NEW — voice/behavior columns ONLY; NO ai_provider_mode]
├── 095-assistant-knowledge-snippet-unique-title.yaml [NEW]
├── 096-safety-net-pattern-kind-and-audit-badge.yaml [NEW]
└── 097-user-byok-key-table.yaml                     [NEW per D-17 — creates user_byok_key]

apps/web/features/ai/
├── components/
│   ├── AiConfigPage.tsx                             [REFACTOR — five sections wrapper]
│   ├── SectionHeader.tsx                            [NEW composed (justified — 5 callsites)]
│   ├── SettingCard.tsx                              [NEW composed (justified — 12+ callsites)]
│   ├── ConfirmDialog.tsx                            [NEW composed (justified — 3 callsites)]
│   ├── YourVoiceSection.tsx                         [NEW]
│   ├── BehaviorSection.tsx                          [NEW]
│   ├── UpdatesSection.tsx                           [NEW]
│   ├── SafetyNetSection.tsx                         [NEW — wraps existing SenderSafetyNetList]
│   ├── AiProviderSection.tsx                        [NEW — ONE SettingCard with Provider/BaseURL/Key/Model/Active/Test/Save + cost footer]
│   ├── WritingStyleDialog.tsx                       [NEW — includes Generate-from-sent button]
│   ├── PersonalInstructionsDialog.tsx               [NEW]
│   ├── EmailSignatureDialog.tsx                     [NEW]
│   ├── TonePresetDialog.tsx                         [NEW]
│   ├── AiOutputLanguageDialog.tsx                   [NEW]
│   └── DraftConfidenceDialog.tsx                    [NEW]
├── api/
│   ├── ai-settings-api.ts                           [NEW — voice + behavior; typed via generated schema.d.ts]
│   └── byok-api.ts                                  [NEW — byok endpoints]
├── hooks/
│   ├── useVoiceSettings.ts / useUpdateVoiceSettings.ts
│   ├── useBehaviorSettings.ts / useUpdateBehaviorSettings.ts
│   ├── useByok.ts / useSaveByok.ts / useTestByokConnection.ts / useSelectByokModel.ts / useActivateByok.ts / useDeleteByok.ts
│   ├── useAiCost.ts
│   └── useGenerateVoiceFromSent.ts
├── query-keys.ts                                    [NEW — voice, behavior, byok, cost keys]
└── messages.ts                                      [EXTEND]

apps/web/features/knowledge/                         [NEW feature — mirrors Inbox Zero folder split]
├── components/{KnowledgeTable,KnowledgeDialog,KnowledgeRow}.tsx
├── api/knowledge-api.ts
├── hooks/{useKnowledge,useCreateKnowledge,useUpdateKnowledge,useDeleteKnowledge}.ts
└── query-keys.ts

apps/web/app/(protected)/(app)/settings/SettingsClient.tsx  [EDIT — remove ByokForm import and render]
apps/web/features/llm/                                       [DELETE — ByokForm and legacy hooks removed alongside legacy table migration]
```

### Pattern 1: Service-owned `@Transactional` (per CONVENTIONS.md §1)

```java
// backend/core/src/main/java/com/zeromail/core/chat/usecases/settings/SettingsVoiceService.java
@Service
public class SettingsVoiceService {
    private final AssistantSettingsJpaRepository assistantSettingsRepository;
    private final PersonalizationSanitizer personalizationSanitizer;

    @Transactional
    public VoiceSettingsResult update(UUID tenantId, VoiceSettingsCommand command) {
        AssistantSettingsEntity assistantSettings =
                assistantSettingsRepository.findByTenantId(tenantId)
                        .orElseGet(() -> AssistantSettingsEntity.defaults(tenantId));
        assistantSettings.applyVoice(
                command.writingStyle(),
                personalizationSanitizer.sanitize(command.personalInstructions()),
                command.emailSignature(),
                command.tonePreset(),
                command.aiOutputLanguage());
        return VoiceSettingsResult.from(assistantSettingsRepository.saveAndFlush(assistantSettings));
    }
}
```

### Pattern 2: `ByokProviderResolver` — the heart of D-17

```java
// backend/core/src/main/java/com/zeromail/core/llm/byok/ByokProviderResolver.java
@Service
public class ByokProviderResolver {
    private final UserByokKeyRepository userByokKeyRepository;
    private final RefreshTokenCipher refreshTokenCipher;

    @Transactional(readOnly = true)
    public Optional<ResolvedLlmProviderCredential> resolve(UUID tenantId, String fallbackModel) {
        return userByokKeyRepository.findByTenantId(tenantId)
                .filter(this::isUsable)
                .map(row -> toResolvedCredential(tenantId, row));
    }

    private boolean isUsable(UserByokKeyEntity row) {
        return row.isActive()
                && row.getModelId() != null
                && !row.getModelId().isBlank()
                && "OK".equals(row.getLastTestResult());
    }

    private ResolvedLlmProviderCredential toResolvedCredential(
            UUID tenantId, UserByokKeyEntity row) {
        byte[] plaintextKey =
                refreshTokenCipher.decrypt(row.getApiKeyCiphertext(), tenantId.toString());
        try {
            return new ResolvedLlmProviderCredential(
                    row.getProvider(),                       // "OPENAI" | "ANTHROPIC" | "GOOGLE" | "DEEPSEEK"
                    row.getModelId(),
                    new LlmProviderCredential(
                            row.getProvider(),
                            keyFormatFor(row.getProvider()),
                            row.getBaseUrl(),                // ← per-call base URL passed to SpringAiProviderChatClientFactory
                            plaintextKey,
                            LlmCredentialSource.BYOK),
                    /* keyVersion = */ (short) 1,
                    /* schemaVersion = */ 1);
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);             // scrub plaintext after handoff
        }
    }
}
```

The existing `SpringAiProviderChatClientFactory.openAiCompatibleModel(credential, model, ...)` at `SpringAiProviderChatClientFactory.java:122–135` already invokes `OpenAiChatModel.builder()...baseUrl(credential.baseUrl())...build()`. No Spring AI starter reconfiguration needed — the per-call `baseUrl` is already the runtime contract. Same is true for `anthropicModel(...)` and `deepSeekModel(...)`. Google uses `com.google.genai.Client.builder().apiKey(...)` which inherits its endpoint; base_url override for Google is not supported by the Spring AI google-genai adapter today (open question O-3 below).

### Pattern 3: `ProviderConnectionTester` extraction (D-14)

```java
// backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/ProviderConnectionTester.java
@Component
public class ProviderConnectionTester {
    private final ModelsProbeClient modelsProbeClient;      // existing — keep as low-level HTTP

    public ConnectionTestResult probeConnection(
            LlmProvider provider, String baseUrl, byte[] plaintextKey) {
        KeyFormat keyFormat = LlmProvider.defaultKeyFormat(provider);
        MasterKeyTestResult testResult =
                modelsProbeClient.probeConnection(provider, keyFormat, baseUrl, plaintextKey);
        if (testResult != MasterKeyTestResult.OK) {
            return new ConnectionTestResult(testResult, List.of());
        }
        try {
            List<ModelsProbeClient.RawModel> models =
                    modelsProbeClient.fetchModelCatalog(provider, keyFormat, baseUrl, plaintextKey);
            List<String> chatCompletionIds =
                    models.stream()
                            .map(ModelsProbeClient.RawModel::modelId)
                            .filter(this::isChatCompletionCapable)
                            .limit(100)
                            .toList();
            return new ConnectionTestResult(MasterKeyTestResult.OK, chatCompletionIds);
        } catch (ModelsProbeClient.ProbeFailedException exception) {
            return new ConnectionTestResult(exception.reason(), List.of());
        }
    }

    private boolean isChatCompletionCapable(String modelId) {
        // exclude embeddings/whisper/tts; provider-specific patterns
        String lower = modelId.toLowerCase(Locale.ROOT);
        return !lower.contains("embedding")
                && !lower.contains("whisper")
                && !lower.contains("tts")
                && !lower.contains("dall-e")
                && !lower.contains("text-embedding")
                && !lower.contains("aqa");          // Google text-bison-001 retired; exclude AQA models
    }

    public record ConnectionTestResult(MasterKeyTestResult result, List<String> models) {}
}
```

Admin `MasterKeyAdminService.testConnection(...)` and `MasterKeyAdminService.testKey(...)` are refactored to call `providerConnectionTester.probeConnection(...)`. Sentinel-leak scrub (`MasterKeySentinelLeakTest` ARCH-11) stays green because the response shape never includes provider error bodies — only the enum.

### Pattern 4: Bean-Validation DTOs that drive OpenAPI codegen (per CONVENTIONS.md §3)

```java
// backend/api/src/main/java/com/zeromail/api/dto/byok/ByokSaveRequest.java
public record ByokSaveRequest(
        @Schema(requiredMode = REQUIRED, allowableValues = {"OPENAI", "ANTHROPIC", "GOOGLE", "DEEPSEEK"})
        @Pattern(regexp = "^(OPENAI|ANTHROPIC|GOOGLE|DEEPSEEK)$") String provider,

        @Schema(requiredMode = REQUIRED, description = "Base URL — must be https:// (or http://localhost* in dev)")
        @Size(min = 8, max = 255) String baseUrl,

        @Schema(requiredMode = REQUIRED) @Size(min = 8, max = 256) String apiKey,

        @Schema(description = "Optional pre-tested model id; null forces user to test+pick before activation")
        @Size(max = 64) String modelId) {}
```

After this DTO ships: **boot backend → `pnpm --filter web run generate:api` → commit `apps/web/lib/api/schema.d.ts` + `apps/web/openapi/zero-mail-spec.json`**. Plan MUST include the regen step for every wave that mutates a backend DTO.

### Pattern 5: TanStack Query mutation with global toast meta (per `apps/web/AGENTS.md`)

```ts
// apps/web/features/ai/hooks/useActivateByok.ts
export function useActivateByok() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { active: boolean }) =>
      api.PUT('/api/byok/active', { body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: byokKeys.row() }),
    meta: {
      successMessage: 'ai.byok.activeSaved',
      errorMessage: 'ai.byok.activeFailed', // localized via apps/web/lib/api/errors.ts — code=ai.byok.no_model_picked maps to "Hãy chọn model và kiểm tra kết nối trước"
    },
  });
}
```

### Pattern 6: SET-VOICE-07 in-memory generate path (privacy invariant)

```java
// backend/core/src/main/java/com/zeromail/core/chat/usecases/settings/VoiceGenerationService.java
@Service
public class VoiceGenerationService {
    @Transactional(propagation = NOT_SUPPORTED)  // no DB write
    public GenerateFromSentResult generate(UUID tenantId, int sampleSize) {
        rateLimiter.requireAllowance(tenantId, "voice.generate", 3, Duration.ofHours(1));

        List<SentMessageSummary> samples =
                gmailSentMessagesReader.readRecentSent(tenantId, Math.min(sampleSize, 50));
        if (samples.isEmpty()) return GenerateFromSentResult.empty();

        ChatResponse response = llmGateway.chat(
                LlmChatRequest.forVoiceGenerate(tenantId, samples),
                CallSite.PREVIEW);                        // reuse existing PREVIEW enum value
        return GenerateFromSentResult.of(truncateToWords(response.getResult().getOutput().getContent(), 500));
    }
}
```

**Critical:** every variable holding email body bytes is method-local. Integration test seeds Gmail-API stub with sentinel `LEAK_SENTINEL_AB12CD34` and greps captured log + audit + DB for the sentinel after invocation — assert zero matches.

### Anti-Patterns to Avoid

- **Inline `toast.success/error` in feature hooks.** Use `meta.successMessage`.
- **Hand-edit `apps/web/lib/api/schema.d.ts`.** Always boot backend + regen.
- **Hardcoded color hex** (`bg-[#867AEB]`). Use tokens (`bg-primary/10`, `border-border`).
- **Custom sanitizer for `personal_instructions` in REST path.** Must reuse `PersonalizationSanitizer` — ArchUnit invariant.
- **Adding `ai_provider_mode` column to `assistant_settings`.** D-17 revised retired this — the `active` flag on `user_byok_key` IS the on/off switch.
- **Adding per-feature pickers (Chat/Triage/Draft rows).** D-17 revised retired this — one BYOK row applies to all features.
- **Adding `CallSite.CHAT` to `ck_llm_call_audit_call_site` CHECK constraint.** D-17 revised — cost is tenant-wide, not feature-scoped.
- **Polling provider `/v1/models` from the FE.** Test-connection always goes through backend `ProviderConnectionTester` (avoids exposing the BYOK key client-side).
- **Activating BYOK without a tested model.** Both UI Switch disabled state AND server-side `PUT /api/byok/active` reject with `code=ai.byok.no_model_picked`.
- **Storing the LLM prompt/completion from the generate-from-sent path** — privacy invariant D-11.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Sanitizing personal_instructions | New `Phase9Sanitizer` | Existing `PersonalizationSanitizer` | Single source of truth — ArchUnit verifies single call site |
| AES-GCM encryption for BYOK | New cipher | Existing `RefreshTokenCipher` | Already key-managed; reuse is ARCH-11 friendly |
| Provider HTTP probe | Custom `RestClient` per provider | Extracted `ProviderConnectionTester` (D-14) | Single sentinel-leak scrub (ARCH-11); enum-only response unified |
| Per-call Spring AI baseUrl override | New starter wiring | Existing `SpringAiProviderChatClientFactory.openAiCompatibleModel(credential, ...)` which already accepts `credential.baseUrl()` | Phase 9 only swaps the credential resolver bean; the factory unchanged |
| BYOK resolution gate | Inline if-else in `LlmGatewayImpl` | New `ByokProviderResolver` that returns `Optional<ResolvedLlmProviderCredential>` | Single bean, single decision rule, easy to unit-test |
| Gmail OAuth refresh / token plumbing | Custom Gmail call | `GmailApiClientFactory` | Already handles token refresh + `RefreshTokenCipher` decrypt |
| Rate limiting (3/hour voice generate, 10/hour test-connection) | Custom in-memory counter | Redis Lettuce + existing `MasterKeyRateLimiter` pattern under `core.admin.mkey.usecases` | Same lease/eviction shape; portable |
| Tenant-wide cost SUM | Per-feature denormalization | `SUM(total_cost_usd) WHERE tenant_id = ?` over existing `llm_call_audit` | Phase 8 already paid the indexing cost |
| Knowledge-snippet `updated_at` touch | DB trigger | Hibernate `@PreUpdate` OR Spring Data `@LastModifiedDate` | Stays at app layer; no Liquibase trigger ceremony |
| ConfirmDialog primitive | New `AlertDialog` wrapper | shadcn `Dialog` with destructive button | UI-SPEC standardizes Dialog reuse |
| Field-level error localization | Hand-coded VI/EN per field | Existing `useLocalizedFieldError` in `apps/web/lib/api/errors.ts` | Switch on `err.code` |

**Key insight:** Phase 9 builds ZERO net-new core capabilities. Every layer (cipher, sanitizer, repository pattern, rate-limiter, Gmail client, Spring AI gateway with per-call baseUrl, ETag, OpenAPI codegen, next-intl, shadcn primitives, TanStack Query meta) is already in the project. Net work is: 4 Liquibase changesets, 1 entity + 1 repo + 1 resolver + 1 tester extraction, 5 service classes, 7 controllers, 1 frontend feature folder + 1 new feature, OpenAPI regen, ArchUnit + sentinel-leak tests, Playwright e2e.

## Runtime State Inventory

> Phase 9 includes a small migration of the legacy `tenant_byok_credentials` table into the new `user_byok_key` shape — list it explicitly.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | (1) `tenant_byok_credentials` row(s) — solo-tenant: 0 or 1 rows of `(tenant_id, provider, endpoint, model, encrypted_key, key_version)`. (2) `assistant_settings` rows with no Phase 9 columns yet. | Liquibase changeset 097 includes a `<sql>` block migrating any existing `tenant_byok_credentials` row → `user_byok_key` with `active=false, last_test_result=NULL, last_tested_at=NULL` (force user to re-test + re-activate). Existing AES-GCM ciphertext copied unchanged (same cipher version). Liquibase 094 ALTER TABLE on `assistant_settings` defaults all new BOOLEAN columns. |
| Live service config | None — Phase 9 is purely in-process REST. No n8n / external workflow embeds. | — |
| OS-registered state | None | — |
| Secrets / env vars | `SPRING_AI_*` API keys for admin master keys (Phase 8) — unchanged. User BYOK keys live in `user_byok_key.api_key_ciphertext` (NOT env vars). | None |
| Build artifacts | `apps/web/lib/api/schema.d.ts` + `apps/web/openapi/zero-mail-spec.json` — regenerated automatically by `pnpm --filter web run generate:api` after backend DTO changes. | Plan includes regen step explicitly per wave that touches DTOs. |

## Common Pitfalls

### Pitfall 1: SET-VOICE-07 silent log leak via Spring AI default tracing

**What goes wrong:** Spring AI M7 has a `chat.client.observation` instrumentation hook that may capture prompt + completion content into spans by default.
**Why it happens:** Easy to enable for debugging and forget to turn off.
**How to avoid:** Verify `application.yml` for both `backend/api` and `backend/worker` has prompt/completion capture disabled. Add a property-binding test in `core.config` that asserts these are FALSE.
**Warning signs:** Tempo trace span contains `gen_ai.prompt.0.content` attribute. `[ASSUMED]` — verify property names via Context7 (`/spring-projects/spring-ai`) before lock.

### Pitfall 2: `assistant_settings` upsert race on first save

**What goes wrong:** Two simultaneous `PUT /api/settings/voice` requests both `findByTenantId` → empty → both `new AssistantSettingsEntity.defaults` → both `saveAndFlush` → Postgres unique index throws on second.
**How to avoid:** Use `INSERT ... ON CONFLICT (tenant_id) DO UPDATE` via JDBC, OR catch `DataIntegrityViolationException` and retry the find path, OR `@Transactional(isolation = REPEATABLE_READ)` + `SELECT FOR UPDATE`. Recommendation: ON CONFLICT.

### Pitfall 3: Knowledge snippet 409 conflict UX

**What goes wrong:** User opens KnowledgeDialog, types title that already exists, clicks Save → backend returns 409 → toast says generic "Couldn't save" → user confused.
**How to avoid:** Map `code=knowledge.title.duplicate` in `apps/web/lib/api/errors.ts`; Dialog form renders the localized message inline above the title field via `useLocalizedFieldError`. UI-SPEC already has the VI/EN strings.

### Pitfall 4 (RETIRED 2026-05-26 round 2)

~~`call_site` enum missing `CHAT`~~ — Obsolete under D-17 revised (single tenant-wide cost — no per-feature breakdown). The cost endpoint returns `{usd: number}` aggregated as `SUM(total_cost_usd) WHERE tenant_id = ?` — no `GROUP BY call_site`. The existing `ck_llm_call_audit_call_site` CHECK constraint is untouched. **Plans MUST NOT include a `CallSite.CHAT` addition or a `ck_llm_call_audit_call_site` Liquibase changeset for this phase.**

### Pitfall 5: Triage-pause renamed to "shadow mode" in UI confuses real shadow-mode semantics

**What goes wrong:** UI calls the toggle "Shadow mode" but backend semantics are full triage pause. Real shadow mode (rules run but Gmail side-effects suppressed) was DROPPED in changelog 039.
**How to avoid:** Rename UI string to "Tạm dừng triage" / "Pause triage" — keep existing backend endpoint unchanged. Document this rename in the plan; SPEC + CONTEXT both assume this.

### Pitfall 6: BYOK provider allow-list drift between frontend filter and backend reject

**What goes wrong:** Frontend `<Select>` filters out OpenRouter / Router_9R. Backend `POST /api/byok` doesn't enforce the same allow-list. Attacker (or curl user) saves `provider=OPENROUTER` → silent success → confusing state.
**How to avoid:** Server-side validation in `UserByokService.save(...)` rejects `provider IN ('OPENROUTER','ROUTER_9R')` with HTTP 400 `code=ai.byok.provider_not_allowed`. ArchUnit / controller test asserts the rejection path AND the FE `<Select>` does not include these options. Defense in depth.

### Pitfall 7: Liquibase YAML changelog numbering collision

**What goes wrong:** The changelog directory already has two `086-*.yaml` files. Phase 9 changesets must not reuse a number already in `db.changelog-master.yaml`.
**How to avoid:** Pick next free integer ≥ `094`. Highest verified existing is `093-billing-package-presentation-fields.yaml`. Use `094` / `095` / `096` / `097`.

### Pitfall 8 (NEW): Activating BYOK before testing connection

**What goes wrong:** User opens AI Provider section, types provider + key + base URL → toggles `Active` switch before clicking `Kiểm tra kết nối`. If the FE doesn't gate the switch, an untested BYOK becomes the source of truth for chat/triage/draft — pipelines hit a stale base URL with a bad key.
**Why it happens:** Optimistic UX where the switch is treated as a "save and use" shortcut.
**How to avoid:** Frontend disables the `Active` switch while `model_id IS NULL || lastTestResult !== 'OK'`, with tooltip `Hãy chọn model và kiểm tra kết nối trước` / `Pick a model and test the connection first`. Backend `PUT /api/byok/active {active: true}` rejects with HTTP 400 `code=ai.byok.no_model_picked` if either gate fails. Both layers required (defense in depth). MVC slice test `ByokActivateGateTest` asserts the rejection paths for both empty `model_id` and non-`OK` `last_test_result`.

### Pitfall 9 (NEW): Provider canonical base URL vs user-edited base URL drift

**What goes wrong:** UI auto-fills `https://api.openai.com/v1` when user picks `OPENAI`. User then switches to `ANTHROPIC` — the input still shows the OpenAI URL because the FE didn't sync. User saves; backend stores `provider=ANTHROPIC, base_url=https://api.openai.com/v1`. Test connection fails with `INVALID_KEY` because `/v1/models` on api.openai.com rejects an Anthropic key. User confused — picks `OPENAI` back but their key was Anthropic. Cycle continues.
**Why it happens:** Auto-fill on provider change is a one-shot effect that gets overwritten by user edits; without explicit sync logic the form drifts.
**How to avoid:**
- FE: on provider `<Select>` change, set `baseUrl` field to `LlmProvider.defaultBaseUrl(provider)` AND clear `apiKey`, `modelId`. Show a helper tooltip "Base URL được tự động điền cho {provider}. Sửa nếu bạn dùng endpoint OpenAI-compatible / Anthropic-compatible / Azure / self-hosted." / "Base URL is auto-filled for {provider}. Edit if you use an OpenAI-compatible / Anthropic-compatible / Azure / self-hosted endpoint."
- BE: `BaseUrlValidator` only checks `https://` (or `http://localhost*` dev allow) and length ≤ 255 — does NOT enforce provider-canonical URL. This preserves OpenAI-compatible / Anthropic-compatible / Azure / self-hosted endpoint usage which IS valid.
- `ProviderConnectionTester` uses the user-supplied `base_url` verbatim (`ModelsProbeClient.baseUrlFor(provider, baseUrl)` already does this). If the URL is wrong, the probe returns `INVALID_KEY` or `NETWORK_ERROR` and user sees it in the test-connection response — fail loud, no silent acceptance.
**Warning signs:** Test connection returns `OK` but no models in the dropdown match what the user expected (e.g., `gpt-4o` listed under `provider=ANTHROPIC, base_url=…openai…` because the user accidentally mismatched).

### Pitfall 10 (NEW): Test-connection inline payload bypasses rate-limit

**What goes wrong:** `POST /api/byok/test-connection` accepts EITHER `{}` (use stored row) OR `{provider, baseUrl, apiKey}` (inline, no save). If rate-limit only applies to the stored-row path, an attacker spam-tests the inline path with rotated guess keys.
**How to avoid:** Per-tenant 10/hour rate-limit MUST apply to BOTH paths in `UserByokService.testConnection(...)` — the Redis key is `byok.test_connection:{tenantId}` regardless of payload shape. ArchUnit test `ByokTestConnectionRateLimitAppliesToBothPathsTest` asserts the rate-limiter is invoked before the branch.

## Code Examples

### Liquibase changeset 094 (assistant_settings columns)

```yaml
# backend/core/src/main/resources/db/changelog/changes/094-assistant-settings-phase9-columns.yaml
databaseChangeLog:
  - changeSet:
      id: 094-assistant-settings-phase9-columns
      author: zeromail
      comment: Phase 9 — add email_signature, tone_preset, auto_draft_replies, draft_confidence, sensitive_data_protection. NO ai_provider_mode (D-17 revised).
      changes:
        - addColumn:
            tableName: assistant_settings
            columns:
              - column: { name: email_signature,           type: varchar(500) }
              - column: { name: tone_preset,                type: varchar(16) }
              - column: { name: auto_draft_replies,         type: boolean, defaultValueBoolean: true, constraints: { nullable: false } }
              - column: { name: draft_confidence,           type: varchar(8), defaultValue: MEDIUM, constraints: { nullable: false } }
              - column: { name: sensitive_data_protection, type: boolean, defaultValueBoolean: true, constraints: { nullable: false } }
        - sql:
            comment: tone_preset closed enum (D-09 — CUSTOM = use writing_style only).
            sql: >-
              ALTER TABLE assistant_settings ADD CONSTRAINT ck_assistant_settings_tone_preset
              CHECK (tone_preset IS NULL OR tone_preset IN ('PROFESSIONAL','FRIENDLY','CASUAL','FORMAL','CUSTOM'))
        - sql:
            comment: draft_confidence enum (D-07).
            sql: >-
              ALTER TABLE assistant_settings ADD CONSTRAINT ck_assistant_settings_draft_confidence
              CHECK (draft_confidence IN ('LOW','MEDIUM','HIGH'))
      rollback:
        - sql: { sql: ALTER TABLE assistant_settings DROP CONSTRAINT ck_assistant_settings_draft_confidence }
        - sql: { sql: ALTER TABLE assistant_settings DROP CONSTRAINT ck_assistant_settings_tone_preset }
        - dropColumn: { tableName: assistant_settings, columnName: sensitive_data_protection }
        - dropColumn: { tableName: assistant_settings, columnName: draft_confidence }
        - dropColumn: { tableName: assistant_settings, columnName: auto_draft_replies }
        - dropColumn: { tableName: assistant_settings, columnName: tone_preset }
        - dropColumn: { tableName: assistant_settings, columnName: email_signature }
```

### Liquibase changeset 097 (NEW `user_byok_key` table + migration)

```yaml
# backend/core/src/main/resources/db/changelog/changes/097-user-byok-key-table.yaml
databaseChangeLog:
  - changeSet:
      id: 097-user-byok-key-table
      author: zeromail
      comment: Phase 9 D-17 — create user_byok_key (single tenant-scoped BYOK row); migrate forward any existing tenant_byok_credentials row.
      changes:
        - createTable:
            tableName: user_byok_key
            columns:
              - column: { name: tenant_id,            type: uuid,         constraints: { primaryKey: true, nullable: false } }
              - column: { name: provider,             type: varchar(16),  constraints: { nullable: false } }
              - column: { name: base_url,             type: varchar(255), constraints: { nullable: false } }
              - column: { name: api_key_ciphertext,  type: bytea,        constraints: { nullable: false } }
              - column: { name: api_key_iv,           type: bytea,        constraints: { nullable: false } }
              - column: { name: model_id,             type: varchar(64) }
              - column: { name: active,               type: boolean,      defaultValueBoolean: false, constraints: { nullable: false } }
              - column: { name: last_test_result,     type: varchar(16) }
              - column: { name: last_tested_at,       type: timestamptz }
              - column: { name: created_at,           type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
              - column: { name: updated_at,           type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
        - addCheckConstraint:
            tableName: user_byok_key
            constraintName: ck_user_byok_key_provider
            constraintBody: provider IN ('OPENAI','ANTHROPIC','GOOGLE','DEEPSEEK')
        - addCheckConstraint:
            tableName: user_byok_key
            constraintName: ck_user_byok_key_last_test_result
            constraintBody: last_test_result IS NULL OR last_test_result IN ('OK','INVALID_KEY','RATE_LIMITED','NETWORK_ERROR','TIMEOUT')
        - sql:
            comment: Forward-migrate any legacy tenant_byok_credentials row.
            sql: >-
              INSERT INTO user_byok_key (
                  tenant_id, provider, base_url, api_key_ciphertext, api_key_iv,
                  model_id, active, last_test_result, last_tested_at)
              SELECT tenant_id,
                     CASE provider WHEN 'GOOGLE_GENAI' THEN 'GOOGLE' ELSE provider END,
                     COALESCE(endpoint, ''),
                     encrypted_key,
                     ''::bytea,           -- legacy table stored IV inside the ciphertext envelope; new schema may need a parser
                     model,
                     false,               -- force re-test + re-activate after rename
                     NULL,
                     NULL
              FROM tenant_byok_credentials
              ON CONFLICT (tenant_id) DO NOTHING;
        - sql:
            comment: Drop legacy table once migration runs.
            sql: DROP TABLE IF EXISTS tenant_byok_credentials CASCADE;
      rollback:
        - sql: { sql: DROP TABLE IF EXISTS user_byok_key }
```

> **Planner note on `api_key_iv`:** the existing `RefreshTokenCipher` envelope format may already store `iv || ciphertext || tag` in a single blob. If so, drop the `api_key_iv` column from the new table and keep just `api_key_ciphertext`. Read `RefreshTokenCipher.encrypt(...)` / `.decrypt(...)` signatures in Wave 0 — if it returns `byte[] envelope` (single blob), use one column; if `record EncryptedEnvelope(byte[] ciphertext, byte[] iv)`, use two. Assume single-blob until verified.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Shadow-mode dry-run logging | Triage pause (full stop) | Changelog 039 | UI label says "Pause" |
| Slider 0.0–1.0 draft confidence | LOW/MEDIUM/HIGH enum | D-07 | One column + threshold map |
| 4 shadcn `<Tabs>` on `/ai?tab=...` | Flat `<SectionHeader>` groups | D-01 | One flat page, no query-param sync |
| `KnowledgeMemory` chat-tool-only | REST CRUD + chat-tool share `AssistantKnowledgeService` | D-04 | One ArchUnit guard for single call site |
| Per-feature provider+model picker (`PUT /api/settings/ai {feature, providerId, modelId, useBYOK}`) | Single BYOK row with active flag (`POST /api/byok` + `PUT /api/byok/active`) | D-17 revised 2026-05-26 round 2 | One card, one resolver, one DB row; deletes `assistant_settings.ai_provider_mode` plan and 3-row picker UI |
| Separate `Platform default ↔ Use my key` mode card | `active` flag on the BYOK row IS the on/off switch | D-17 revised 2026-05-26 round 2 | Eliminates the mode card entirely; `AiProviderSection` is one `<SettingCard>` |
| Per-feature cost rows (`{chat, triage, draft}` USD) | Single tenant-wide `{usd}` SUM | D-17 revised 2026-05-26 round 2 | No `call_site=CHAT` enum addition; no `ck_llm_call_audit_call_site` changeset |
| Legacy `tenant_byok_credentials` table + `ByokService` + `ByokController` + `TenantByokProviderCredentialResolver` | NEW `user_byok_key` table + `UserByokService` + `UserByokController` + `ByokProviderResolver` | Phase 9 D-17 | Migration forward in changelog 097; legacy code deleted |

**Deprecated/outdated:**

- `tenants.triage_shadow_mode` column — dropped in changelog 039; UI must not reference it.
- `TenantByokProviderCredentialResolver` + `TenantByokCredentialsEntity` + `tenant_byok_credentials` table + `ByokService` + `ByokController` + `apps/web/features/llm/components/ByokForm.tsx` — all deleted in Phase 9 Wave 0 / Wave 2 (legacy table data migrated forward to `user_byok_key`).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring AI 2.0.0-M7 has `spring.ai.chat.client.observations.log-prompt` (or equivalent) property | Pitfall 1 | If property name differs → privacy invariant not enforced via property; planner verifies via Context7 before locking config |
| A2 | "Shadow mode" UI label maps to existing `triage_paused` flag (no new column) | D-15 heads-up | If user wants a true shadow-log mode, Phase 9 needs an extra column + worker codepath |
| A3 | `assistant_settings.updated_at` auto-touches via JPA `@PreUpdate` lifecycle | Pattern 1 | If JPA listener silently fails, `updated_at DESC` ordering regresses. Mitigation: integration test |
| A4 | (RETIRED) `llm_call_audit.call_site` enum must add `CHAT` | Pitfall 4 | Obsolete under D-17 revised |
| A5 | Existing `AssistantSettingsJpaRepository.findByTenantId` exists (verified by grep in `AssistantPersonalInstructionsService`) | Pattern 1 | If method signature differs, service code adapts trivially |
| A6 | `apps/web/i18n/messages/{vi,en}.json` is the next-intl bundle path | Validation Architecture | If bundle path differs in 16.2.4, hooks throw at runtime; verified by `apps/web/i18n/request.ts` |
| A7 | `RefreshTokenCipher.encrypt(...)` returns a single `byte[] envelope` (iv inlined) | Code Examples | If it returns a record with separate iv, `user_byok_key` needs two columns instead of one; planner verifies in Wave 0 |
| A8 | `MasterKeyTestResult` enum is the canonical 5-value enum `{OK, INVALID_KEY, RATE_LIMITED, NETWORK_ERROR, TIMEOUT}` (CONTEXT D-14 wording "UNSUPPORTED" is incorrect) | D-14 | If a future code change adds `UNSUPPORTED`, response shape must include it; current planning uses the verified enum |
| A9 | `SpringAiProviderChatClientFactory.openAiCompatibleModel(...)` accepts per-call `baseUrl` via the credential argument | Pattern 2 | Verified in `SpringAiProviderChatClientFactory.java:128–135` |
| A10 | The `google-genai` Spring AI adapter does NOT support per-call `baseUrl` override (Client builder takes `apiKey` only) | Pattern 2 | If user picks GOOGLE provider with a non-default base URL, the runtime may silently use Google's canonical endpoint. Either reject non-default `base_url` for GOOGLE at save time with `code=ai.byok.base_url_not_supported_for_provider`, or document this as a known limitation. Planner picks. |
| A11 | Anthropic exposes a `/v1/models` endpoint reachable with `x-api-key` + `anthropic-version` headers | Pattern 3 | If Anthropic does NOT expose `/v1/models` (historically they didn't), `ProviderConnectionTester` for ANTHROPIC must either (a) probe via a tiny `POST /v1/messages` with `max_tokens=1` OR (b) fall back to a hard-coded model list. The existing `ModelsProbeClient.probeConnection(...)` already hits `{baseUrl}/models` for Anthropic; if it works today against Phase 8 admin master keys, it works for user BYOK. Planner verifies via integration test against the Anthropic live endpoint in Wave 1. See Open Question O-1. |

## Open Questions

1. **(NEW) Does Anthropic expose `/v1/models` reachable with `x-api-key` headers as of 2026-05?**
   - What we know: existing `ModelsProbeClient.probeConnection(...)` issues `GET {baseUrl}/models` for Anthropic with `x-api-key` + `anthropic-version: 2023-06-01` headers. Phase 8 admin MKEY-03 has been live for some time and apparently works for Anthropic master keys.
   - What's unclear: whether Anthropic shipped `/v1/models` as a public endpoint, or whether the current code path returns `404 → NETWORK_ERROR` silently for Anthropic. If the latter, `ProviderConnectionTester.probeConnection(provider=ANTHROPIC, ...)` returns `NETWORK_ERROR` even with a valid key — user-side BYOK Anthropic test-connection will appear broken.
   - Recommendation: Wave 1 includes an integration test against Anthropic's live `/v1/models` with a real key (run only via `@Tag("llm-eval")` so CI doesn't burn credits). If it returns 404, add a fallback to `POST /v1/messages` with `max_tokens=1` in the `ProviderConnectionTester` for Anthropic. Context7 `/anthropics/anthropic-sdk-python` or `/anthropics/api-docs` queried for `/v1/models` should resolve this in 5 minutes.

2. **(NEW) Is `TenantByokProviderCredentialResolver` admin-bound or generic enough to be composed with `ByokProviderResolver`?**
   - What we know: `TenantByokProviderCredentialResolver` reads `tenant_byok_credentials` (legacy) and returns `Optional<ResolvedLlmProviderCredential>`. `ByokProviderResolver` (Phase 9) reads `user_byok_key` (new) and returns the same shape.
   - What's unclear: whether to (a) DELETE the legacy resolver + table after migration, or (b) compose `ByokProviderResolver` to call the legacy resolver as a fallback for any orphaned rows.
   - Recommendation: (a) — clean delete. Solo-tenant deployment has 0 or 1 legacy rows. Wave 0 changelog 097 migrates the row forward; Wave 1 deletes the legacy classes. Composition adds complexity for zero net safety. Planner picks during plan-phase if user disagrees.

3. **(NEW) Does Spring AI M7 google-genai adapter support runtime `baseUrl` per call?**
   - What we know: `SpringAiProviderChatClientFactory.googleGenAiModel(...)` builds `com.google.genai.Client.builder().apiKey(plaintextApiKey)...build()` — no `baseUrl` setter in the visible Client builder. OpenAI / Anthropic / DeepSeek adapters DO accept `.baseUrl(credential.baseUrl())`.
   - What's unclear: whether the google-genai Java client exposes a base URL override on the Client builder.
   - Recommendation: Wave 1 spike — try `Client.builder().apiKey(...).clientOptions(ClientOptions.builder().baseUrl(...).build())` or equivalent. If unsupported, EITHER reject non-default `base_url` for GOOGLE at the `UserByokService.save(...)` layer with `code=ai.byok.base_url_not_supported_for_provider`, OR document as "Google BYOK ignores `base_url`; uses canonical endpoint." Planner picks. Context7 `/google/genai-sdk-java` query for "base url override" resolves in 5 minutes.

4. **(NEW) Provider's own rate limit on `/v1/models` during user-side test-connection beyond our 10/hour cap**
   - What we know: our per-tenant 10/hour rate-limit prevents user spam. But the provider's own `/v1/models` endpoint has its own rate limit (e.g., OpenAI returns 429 if a single account fires too fast).
   - What's unclear: whether OpenAI/Anthropic/Google/DeepSeek throttle `/v1/models` aggressively enough that our 10/hour cap is not sufficient.
   - Recommendation: defensive — `ProviderConnectionTester` already maps provider 429 → `RATE_LIMITED` enum value. UI shows "Provider returned rate limit, please wait and try again." No code change needed; tested by the existing MKEY-03 path.

5. **`@LastModifiedDate` Spring Data auditing wired?**
   - What we know: `AssistantKnowledgeMemoryEntity` extends `AbstractTenantOwnedEntity`. Whether `AbstractTenantOwnedEntity` has `updatedAt` + `@LastModifiedDate` is unverified.
   - Recommendation: planner reads `backend/core/.../shared/persistence/AbstractTenantOwnedEntity.java` during plan-phase to confirm. If no auditing wiring, do the touch manually in service.

6. **Rate-limit primitive — Redis Lettuce vs in-memory `MasterKeyRateLimiter`**
   - What we know: Phase 8 ships `core.admin.mkey.usecases.MasterKeyRateLimiter`.
   - Recommendation: planner inspects; if generic, extract to `core.shared.ratelimit`; if admin-bound, write a thin per-tenant variant covering both `voice.generate (3/hr)` and `byok.test_connection (10/hr)`.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL 18.4 (testcontainers) | All persistence tests | ✓ | per STACK.md | — |
| Spring Boot 4.0.6 | App | ✓ | locked | — |
| Spring AI 2.0.0-M7 | Generate-from-sent + BYOK chat | ✓ | locked | If google-genai per-call baseUrl unsupported → reject at save (see O-3) |
| Gmail API client | SET-VOICE-07 | ✓ | via `GmailApiClientFactory` | — |
| Redis Lettuce | Rate-limit + cache | ✓ | locked | — |
| `pnpm --filter web run generate:api` | OpenAPI codegen | ✓ | per AGENTS.md | Manual hand-edit FORBIDDEN |
| shadcn primitives (every required primitive) | All UI | ✓ | every needed primitive present in `apps/web/components/ui/` | — |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none.

## Validation Architecture

> `workflow.nyquist_validation` is `true` in `.planning/config.json`. Section included.

### Test Framework

| Property | Value |
|----------|-------|
| Backend test framework | JUnit 5 (Jupiter) + AssertJ + Mockito + Testcontainers Postgres (per TESTING.md §3). Base test classes: `PostgresContainerTest`, `ApiPostgresTestBase`, `TestSessionSupport.TestSessionMinter` |
| Frontend unit framework | Vitest 4 (existing in `apps/web/__tests__/**`) |
| Frontend e2e framework | Playwright 1.60 in `apps/web/e2e/**` |
| Backend quick run | `./gradlew :backend:core:test :backend:api:test --tests "*Settings*" --tests "*Knowledge*" --tests "*VoiceGeneration*" --tests "*Byok*"` |
| Backend full suite | `./gradlew test` |
| Frontend quick run | `pnpm --filter web test --run features/ai features/knowledge` |
| E2E run | `pnpm --filter web e2e -- ai-settings.spec.ts` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SET-VOICE-01 | writing_style 200–500 word bounds enforced | unit (validator) | `./gradlew :backend:core:test --tests SettingsVoiceServiceWordBoundsTest` | ❌ Wave 0 |
| SET-VOICE-01 | PUT /api/settings/voice returns 200 + persists | mvc slice | `./gradlew :backend:api:test --tests SettingsVoiceControllerTest` | ❌ Wave 0 |
| SET-VOICE-02 | sanitizer single-call invariant | ArchUnit | `./gradlew :backend:core:test --tests PersonalizationSanitizerSingleCallSiteTest` | ❌ Wave 0 |
| SET-VOICE-02 | sentinel `[SYSTEM]` removed from persisted value | unit | `./gradlew :backend:core:test --tests PersonalizationSanitizerCorpusTest` | ✅ existing |
| SET-VOICE-03 | signature appears verbatim in next draft | integration | `./gradlew :backend:core:test --tests DraftSignatureIntegrationTest` | ❌ Wave 0 |
| SET-VOICE-04 | UNIQUE(tenant_id,title) returns 409 | `@DataJpaTest` | `./gradlew :backend:core:test --tests AssistantKnowledgeMemoryUniqueTitleTest` | ❌ Wave 0 |
| SET-VOICE-04 | cross-tenant delete returns 404 | mvc slice | `./gradlew :backend:api:test --tests KnowledgeSnippetControllerTenantIsolationTest` | ❌ Wave 0 |
| SET-VOICE-04 | chat-tool + REST share `AssistantKnowledgeService.append` | ArchUnit | `./gradlew :backend:core:test --tests KnowledgeSnippetSingleWriteSiteTest` | ❌ Wave 0 |
| SET-VOICE-05 | tone_preset enum CHECK rejects bad value | `@DataJpaTest` | `./gradlew :backend:core:test --tests AssistantSettingsTonePresetCheckTest` | ❌ Wave 0 |
| SET-VOICE-06 | non-`vi`/`en` ai_output_language returns 400 | mvc slice | `./gradlew :backend:api:test --tests SettingsVoiceLanguageValidationTest` | ❌ Wave 0 |
| SET-VOICE-07 | sentinel content never reaches DB/log/audit | integration | `./gradlew :backend:core:test --tests VoiceGenerationFromSentLeakTest` | ❌ Wave 0 (critical privacy test) |
| SET-VOICE-07 | 4th call/hour returns 429 | unit | `./gradlew :backend:core:test --tests VoiceGenerationRateLimitTest` | ❌ Wave 0 |
| SET-BEHV-01 | toggle OFF → draft worker writes no rows | integration | `./gradlew :backend:worker:test --tests DraftAutoToggleIntegrationTest` | ❌ Wave 0 |
| SET-BEHV-02 | draft worker resolves enum → threshold and skips below | integration | `./gradlew :backend:worker:test --tests DraftConfidenceThresholdTest` | ❌ Wave 0 |
| SET-BEHV-03 | reuses existing notification-preferences endpoint (no new column) | smoke | Playwright e2e | (Playwright) ❌ |
| SET-BEHV-04 | LLM-05 redactor toggle-aware | unit | `./gradlew :backend:core:test --tests SensitiveDataRedactionToggleTest` | ❌ Wave 0 |
| SET-BEHV-05 | reuses triage-pause endpoint (UI labeled "Pause triage") | smoke | Playwright e2e | (Playwright) ❌ |
| SET-SAFE-01 | DELETE observation-created entry → 403 | mvc slice | `./gradlew :backend:api:test --tests SenderSafetyNetDeleteAuthorityTest` | ❌ Wave 0 |
| SET-SAFE-01 | `@acme.com` POST persists as DOMAIN | mvc slice | `./gradlew :backend:api:test --tests SenderSafetyNetDomainPatternTest` | ❌ Wave 0 |
| SET-SAFE-01 | DOMAIN entry blocks matching sender in triage worker | integration | `./gradlew :backend:worker:test --tests TriageSafetyNetDomainMatchTest` | ❌ Wave 0 |
| SET-SAFE-04 | `blocked_by_safety_net_pattern` populated when REJECTED_BY_SAFETY_NET | integration | `./gradlew :backend:worker:test --tests TriageAuditSafetyNetBadgeTest` | ❌ Wave 0 |
| SET-AI-01 | **Resolution rule end-to-end:** active+tested+model → chat/triage/draft pipelines call `{base_url}` with `model_id`; inactive → catalog default | integration | `./gradlew :backend:core:test --tests ByokResolutionIntegrationTest` (stubs OpenAI URL + asserts request body `model` field equals `model_id`; toggles `active` and asserts fallback to catalog default) | ❌ Wave 0 |
| SET-AI-01 | Activate gate: `PUT /api/byok/active {active:true}` rejects when `model_id IS NULL` | mvc slice | `./gradlew :backend:api:test --tests ByokActivateGateModelMissingTest` | ❌ Wave 0 |
| SET-AI-01 | Activate gate: rejects when `last_test_result <> 'OK'` | mvc slice | `./gradlew :backend:api:test --tests ByokActivateGateNotTestedTest` | ❌ Wave 0 |
| SET-AI-02 | save BYOK for `OPENROUTER` → 400 `code=ai.byok.provider_not_allowed` | mvc slice | `./gradlew :backend:api:test --tests ByokSaveProviderAllowListTest` | ❌ Wave 0 |
| SET-AI-02 | base URL validation: `http://attacker.com` → 400 `code=ai.byok.base_url_not_https` | mvc slice | `./gradlew :backend:api:test --tests ByokSaveBaseUrlValidationTest` | ❌ Wave 0 |
| SET-AI-02 | saving a row resets `active=false`, `last_test_result=NULL`, `last_tested_at=NULL` | `@DataJpaTest` | `./gradlew :backend:core:test --tests ByokSaveResetsStateTest` | ❌ Wave 0 |
| SET-AI-02 | plaintext key never echoed in response (regex assertion) | mvc slice (snapshot) | `./gradlew :backend:api:test --tests ByokResponseNeverEchoesPlaintextTest` | ❌ Wave 0 |
| SET-AI-02 | replace-row-on-new-provider semantics (exactly one row per tenant) | `@DataJpaTest` | `./gradlew :backend:core:test --tests UserByokKeySingleRowPerTenantTest` | ❌ Wave 0 |
| SET-AI-03 | tenant-wide cost SUM returns exactly `{usd}` (no per-feature keys) | `@DataJpaTest` | `./gradlew :backend:core:test --tests AiCostQueryService7DayTest` | ❌ Wave 0 |
| SET-AI-04 | enum-only response; 401 provider body never leaks; `OK` carries `models[]` capped 100 | mvc slice | `./gradlew :backend:api:test --tests ByokTestConnectionEnumOnlyTest` | ❌ Wave 0 |
| SET-AI-04 | 11th test/hour returns 429 `code=ai.byok.test_connection.rate_limited` (applies to BOTH stored and inline payload paths) | unit | `./gradlew :backend:core:test --tests ByokTestConnectionRateLimitTest` | ❌ Wave 0 |
| SET-AI-04 | admin MKEY-03 + user `POST /api/byok/test-connection` both reach `ProviderConnectionTester.probeConnection` | ArchUnit | `./gradlew :backend:core:test --tests ProviderConnectionTesterSingleBindingTest` | ❌ Wave 0 |
| SET-AI-04 | sentinel-leak scrub stays green for user path (no provider error body in response) | unit | `./gradlew :backend:core:test --tests UserByokTestConnectionSentinelLeakTest` | ❌ Wave 0 |
| Whole page | flat-section golden path | Playwright e2e | `pnpm --filter web e2e -- ai-settings.spec.ts` | ❌ Wave 0 |
| Whole page | no hardcoded color hex | repo grep gate | `apps/web` existing lint task | ✅ |

### Sampling Rate

- **Per task commit:** smallest matching slice command from the table above.
- **Per wave merge:** `./gradlew :backend:core:test :backend:api:test :backend:worker:test --tests "*Settings*" --tests "*Knowledge*" --tests "*SafetyNet*" --tests "*VoiceGeneration*" --tests "*Byok*"` + `pnpm --filter web test --run features/ai features/knowledge`.
- **Phase gate:** `./gradlew test` + `pnpm --filter web e2e -- ai-settings.spec.ts` + manual VI/EN locale check + manual `npm view`-style spot check on D-17 BYOK flow (provider select → base URL auto-fill → test → model pick → activate → chat call hits BYOK URL).

### Wave 0 Gaps

- [ ] `backend/core/src/test/java/com/zeromail/core/chat/usecases/settings/*` directory
- [ ] `backend/core/src/test/java/com/zeromail/core/chat/usecases/AssistantKnowledgeServiceCrudTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/llm/byok/*` directory (resolver + service tests + ArchUnit)
- [ ] `backend/core/src/test/java/com/zeromail/core/llm/gateway/springai/ProviderConnectionTesterTest.java` (extraction parity test)
- [ ] `backend/core/src/test/java/com/zeromail/core/llm/byok/ByokResolutionIntegrationTest.java` (D-17 critical end-to-end test)
- [ ] `backend/core/src/test/java/com/zeromail/core/chat/usecases/settings/VoiceGenerationFromSentLeakTest.java` (D-11 privacy invariant)
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/settings/*` directory
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/byok/UserByokControllerTest.java` (full surface)
- [ ] `apps/web/__tests__/features/ai/` Vitest specs (especially `AiProviderSection.test.tsx` — Active switch disabled gate)
- [ ] `apps/web/e2e/ai-settings.spec.ts` Playwright spec

*(All test infrastructure — `PostgresContainerTest`, `ApiPostgresTestBase`, `TestSessionSupport`, Playwright config, Vitest config, lint gates — exists. Only the new test files are missing.)*

## Security Domain

> `security_enforcement: true` in config; ASVS L1. Section included.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Existing Spring Security 7 cookie-session + WebAuthn admin (Phase 8). Phase 9 only adds user-scoped endpoints behind `isAuthenticated()` |
| V3 Session Management | yes | Existing Spring Session Redis + `HttpOnly,SameSite=Lax,Secure` cookie. No change |
| V4 Access Control | yes | `TenantContext.currentTenantUuid()` mandatory in every new controller; cross-tenant access returns 404 |
| V5 Input Validation | yes | Bean Validation on every request DTO; `@Pattern` + `@Size` + closed-enum `@Schema(allowableValues)`; `PersonalizationSanitizer` for prompt-fenced text; `BaseUrlValidator` for BYOK base URLs |
| V6 Cryptography | yes | Reuse `RefreshTokenCipher` AES-GCM. No hand-rolled crypto. Plaintext BYOK keys never logged |
| V7 Error Handling | yes | Existing `ProblemDetail` mapper; translate domain errors to closed `code=*` strings; never echo provider error bodies (ARCH-11) |
| V8 Data Protection | yes | Privacy invariant D-11 (in-memory only for generate-from-sent); never serialize raw bodies |
| V12 Files/Resources | no | Phase 9 uploads no files |
| V13 API/Web Service | yes | OpenAPI codegen pipeline; rate-limit on test-connection + generate-from-sent; CSRF already handled |

### Known Threat Patterns for {Java 25 / Spring Boot 4 / Spring AI M7 / Postgres / Next.js 16}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-tenant data leak (SET-VOICE-04, SET-SAFE-01, SET-AI-02) | Information Disclosure | Every repository query filters by `tenant_id`; controllers pull tenant from `TenantContext`; cross-tenant access returns 404 |
| Prompt injection via `personal_instructions` or `writing_style` | Tampering / Elevation | `PersonalizationSanitizer` enforces XML-fence + sentinel removal + length cap; ArchUnit asserts single call site |
| LLM prompt/completion leakage (SET-VOICE-07) | Information Disclosure | Privacy invariant D-11 + sentinel-leak integration test + Spring AI observation property disabled |
| BYOK key disclosure via response body or log | Information Disclosure | AES-GCM at rest; response DTO has no `apiKey` field, only `lastFourChars`; ARCH-11 sentinel test |
| Provider allow-list bypass | Tampering | `ProviderAllowList` rejects `OPENROUTER`/`ROUTER_9R` at server-side; FE filter is defense in depth |
| BYOK base URL as SSRF probe | Tampering / Information Disclosure | `BaseUrlValidator` requires `https://` (or `http://localhost*` dev allow) — but provider URL is otherwise unrestricted. **Open risk:** an attacker tenant could point BYOK at an internal service URL to probe response timing. Mitigation: `ProviderConnectionTester` only emits enum + model list, never response body bytes; rate-limit 10/hour caps oracle volume. Accepted for v1.2. |
| Test-connection used as oracle (free `/v1/models` call on attacker URL) | Spoofing / Resource Exhaustion | Per-tenant 10/hour rate-limit on BOTH stored and inline payload paths; enum response shape never includes provider error body |
| Rate-limit bypass via clock skew | Denial of Service | Redis-backed rate-limiter (single source of time) |
| Activate-before-test gate bypass | Tampering | Backend `PUT /api/byok/active` rejects with `code=ai.byok.no_model_picked` when `model_id IS NULL || last_test_result <> 'OK'`; FE Switch also disabled (defense in depth) |
| Knowledge-snippet content injection into draft prompt | Tampering | Same sanitizer pattern; cap 8000 chars; XML-fence in prompt assembler |
| 409 → enumeration of other tenants' titles | Information Disclosure | UNIQUE constraint scoped `(tenant_id, title)` — duplicate detection only sees same tenant |
| OAuth scope expansion required by Gmail send actions | Spoofing | Bundled OAuth from v1.1; no new scopes added in Phase 9 (SET-VOICE-07 uses existing `gmail.readonly`) — verify scope IS granted; otherwise tenant must re-consent |
| Audit-log injection via safety-net pattern | Tampering | `blocked_by_safety_net_pattern VARCHAR(320)` is canonicalized before write |
| Migration race during legacy `tenant_byok_credentials` drop | Data Loss | Liquibase 097 INSERT-then-DROP runs in a single transaction; rollback restores; solo-tenant impact is at most 1 row that user can re-enter |

## Sources

### Primary (HIGH confidence)

- Project files read directly during this refresh:
  - `.planning/phases/09-user-settings-ui-on-curated-catalog/09-CONTEXT.md` (full read)
  - `.planning/phases/09-user-settings-ui-on-curated-catalog/09-SPEC.md` (full read)
  - `.planning/phases/09-user-settings-ui-on-curated-catalog/09-RESEARCH.md` (prior version — full read)
  - `backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/ModelsProbeClient.java` (full read — verified enum, `RawModel`, `probeConnection`, `fetchModelCatalog`, header logic)
  - `backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyTestResult.java` (full read — verified enum `{OK, INVALID_KEY, RATE_LIMITED, NETWORK_ERROR, TIMEOUT}`)
  - `backend/core/src/main/java/com/zeromail/core/admin/mkey/domain/LlmProvider.java` (full read — verified provider IDs and default base URLs)
  - `backend/core/src/main/java/com/zeromail/core/llm/usecases/TenantByokProviderCredentialResolver.java` (full read — verified existing resolver shape)
  - `backend/core/src/main/java/com/zeromail/core/llm/persistence/TenantByokCredentialsEntity.java` (full read — verified legacy table shape)
  - `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SpringAiProviderChatClientFactory.java` (partial read — verified per-call `baseUrl` setter for OpenAI/Anthropic/DeepSeek; Google uses Client builder)
  - `backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java` (full read — verified legacy controller shape to be replaced)
  - Grep on `MasterKeyAdminService.java` confirming `MasterKeyTestResult.OK` usage at lines 180, 267, 392, 542, 554, 580, 615, 737, 774
  - Grep on `LlmGatewayImpl.java` + `SpringAiChatModelFactory.java` confirming `TenantByokProviderCredentialResolver` is the single resolver bean called at chat-resolution time
  - `apps/web/components/ui/*.tsx` directory listing — verified all required shadcn primitives present
  - Liquibase changelog directory — verified latest existing is `093-billing-package-presentation-fields.yaml`; legacy BYOK lives in changelogs 018/019/020

### Secondary (MEDIUM confidence)

- Inbox Zero local reference at `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/` (settings + knowledge files) — used only to confirm Inbox Zero pattern parity for sections + Knowledge Table; Inbox Zero's BYOK split-to-/settings pattern is explicitly NOT followed (D-06).

### Tertiary (LOW confidence)

- Anthropic `/v1/models` reachability with `x-api-key` headers (Open Question O-1) — needs Wave 1 integration test against live Anthropic endpoint or Context7 query for `/anthropics/api-docs`.
- Spring AI 2.0.0-M7 google-genai adapter per-call `baseUrl` support (Open Question O-3) — needs Wave 1 spike or Context7 query for `/spring-projects/spring-ai`.
- Spring AI M7 observation-property names that disable prompt/completion capture (Pitfall 1 / A1) — needs Context7 verification before locking `application.yml`.

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — every library verified in project code, no installs needed.
- Architecture: HIGH — `ByokProviderResolver` + `ProviderConnectionTester` shapes verified against `ModelsProbeClient`, `TenantByokProviderCredentialResolver`, and `SpringAiProviderChatClientFactory` source.
- Pitfalls: HIGH — every pitfall corresponds to a verified surface or a locked decision in CONTEXT.
- Privacy invariants: HIGH — D-11 ban surfaces listed exhaustively; sentinel-grep test approach concrete.
- SET-AI-01 resolution rule: HIGH — Spring AI per-call `baseUrl` confirmed in source; the only open question is Google's adapter (O-3) which is a known + bounded risk.
- SET-AI-04 enum response: HIGH for OpenAI/DeepSeek/Google paths (existing admin MKEY-03 works), MEDIUM for Anthropic (O-1).

**Research date:** 2026-05-26 (refresh after D-17 revised round 2)
**Valid until:** 2026-06-25 (30 days — stack is stable; only Spring AI M7 → GA is fast-moving)

## Ready for Planning

Yes. The phase is fully scoped against the locked D-17 revised design:

- Backend surface (4 changelogs, 1 entity, 1 repo, 1 resolver, 1 service, 1 cost service, 1 voice-gen service, 1 knowledge service extension, 1 tester extraction, 6 new controllers + 1 refactored admin controller) is well-bounded.
- Frontend surface (1 refactored `AiConfigPage`, 5 new sections, 6 new dialogs, 9 new hooks, 2 new feature folders) is well-bounded.
- Test ladder (32 backend tests + 1 Playwright e2e) maps 1:1 to requirements.
- 3 open questions (O-1 Anthropic `/v1/models`, O-2 legacy resolver disposition, O-3 Google per-call baseUrl) are surfaceable in a 30-minute Wave 1 spike and do not block planning.
- 1 assumption flagged for verification (A1 — Spring AI observation property names) can be resolved during Wave 0 via Context7 in 5 minutes.

Planner can proceed with `/gsd:plan-phase 9` to break this into Wave 0 / Wave 1 / Wave 2 with `checkpoint:human-verify` gates before:
1. The Liquibase 097 migration drops the legacy `tenant_byok_credentials` table (data-loss risk).
2. The `ProviderConnectionTester` extraction touches `MasterKeyAdminService` MKEY-03 path (Phase 8 ARCH-11 invariant).
3. The chat/triage/draft pipelines swap `TenantByokProviderCredentialResolver` for `ByokProviderResolver` (runtime BYOK resolution change).
