# Phase 9: User Settings UI on Curated Catalog — Research

**Researched:** 2026-05-26
**Domain:** Java 25 / Spring Boot 4 / Spring AI M7 backend REST + Next.js 16 / shadcn / next-intl frontend; Gmail API in-memory read; AES-GCM BYOK reuse; Postgres + Liquibase YAML migrations
**Confidence:** HIGH (every external claim is grounded in code grep on `D:\study-materials-summer-2026\EXE202\zero-mail` or in the locked design contract at `09-UI-SPEC.md`)

## Summary

Phase 9 is overwhelmingly **integration, not invention**. Every backend ingredient already exists in the repo: `AssistantSettingsEntity` and `AssistantKnowledgeMemoryEntity` (extend, do not create); `PersonalizationSanitizer` (reuse); `ModelsProbeClient` returning the locked `MasterKeyTestResult` enum `{OK, INVALID_KEY, RATE_LIMITED, NETWORK_ERROR, TIMEOUT}` (extract `ProviderConnectionTester` per D-14); `SenderSafetyNetController` + `TenantProtectedSenderObservationEntity` (extend with `pattern_kind`/`created_by_user` + DELETE); `SettingsCatalogController` shipping `GET /api/settings/catalog` with ETag; `ByokController` + `AssistantSettingsJpaRepository`; `RefreshTokenCipher` / `PlatformSecretCipher` for AES-GCM; `Gmail` API client via `GmailApiClientFactory`. Frontend has every shadcn primitive already installed under `apps/web/components/ui/` (verified by `Glob` — `dialog`, `table`, `select`, `radio-group`, `switch`, `badge`, `separator`, `spinner`, `alert-dialog` all present). next-intl bundle locations are `apps/web/i18n/messages/{vi,en}.json`.

The phase work decomposes into: (1) **Liquibase changeset 094-XXX adding 5 columns to `assistant_settings`** (email_signature, tone_preset, auto_draft_replies, draft_confidence, sensitive_data_protection); (2) **changeset adding `UNIQUE(tenant_id,title) + updated_at` to `assistant_knowledge_snippet`**; (3) **changeset adding `pattern_kind`, `created_by_user` to `tenant_protected_sender_observation` and `blocked_by_safety_net_pattern VARCHAR(320)` to `triage_audit`**; (4) **3 new service+controller pairs** under `core.chat.settings` (Voice, Behavior, AI) + extended Safety-Net + Knowledge CRUD + `POST /api/settings/voice/generate-from-sent` (SET-VOICE-07); (5) **`ProviderConnectionTester` extraction** in `core.llm.gateway.springai` (D-14); (6) **frontend refactor** of `apps/web/features/ai/components/AiConfigPage.tsx` into five `<SectionHeader>` groups with `SettingCard`+`Dialog` per UI-SPEC; (7) **OpenAPI regeneration** via `pnpm --filter web run generate:api`; (8) **2 ArchUnit invariants** — single sanitizer call site + single knowledge-snippet write call site — plus a sentinel-leak test for the generate-from-sent privacy invariant.

**Primary recommendation:** Plan a 3-wave build (Wave 0 = Liquibase + entity + DTO scaffolding; Wave 1 = backend services + controllers + ArchUnit + ProviderConnectionTester extraction; Wave 2 = frontend section refactor + Knowledge CRUD + AI Provider section). SET-VOICE-07 generate-from-sent is the highest-risk surface (privacy invariant + LLM call + Gmail API in-memory) — give it a dedicated plan and an integration test that seeds sentinel-content into Gmail-API stubs and greps the captured log + audit + DB for leaks.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01.** `/ai` page uses **flat `<SectionHeader>` groups** on a single `/ai/page.tsx` — NOT shadcn `<Tabs>`, NOT query-param tab routing. Section order: `Your voice`, `Behavior`, `Updates`, `Safety net`, `AI Provider`. Supersedes original SPEC v1 four-tabs directive.

**D-02.** Every multi-field setting uses the `SettingCard` (title + description + Edit/Set button) → shadcn `Dialog` edit pattern. Mirrors Inbox Zero `WritingStyleSetting.tsx` / `AboutSetting.tsx`. NOT inline edit.

**D-03.** Short toggles (`auto_draft_replies`, `daily_digest`, `sensitive_data_protection`, `shadow_mode`) render INLINE as shadcn `<Switch>` on the `SettingCard` body. No Dialog needed for boolean fields.

**D-04.** Knowledge snippets render as a shadcn `<Table>` (Title | Last Updated | Edit | Delete) with `+ Add` button opening a Dialog containing `KnowledgeForm`. Edit on a row opens the same Dialog prefilled. Delete uses `ConfirmDialog`. Mirrors Inbox Zero `KnowledgeBase.tsx`.

**D-05.** Backend adds `UNIQUE(tenant_id, title)` constraint on `assistant_knowledge_snippet` + `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` column with auto-touch on update. List ordering is `updated_at DESC`. Mirrors Inbox Zero `Knowledge` Prisma `@@unique([emailAccountId, title])`.

**D-06.** BYOK + per-feature model picker stay on `/ai` inside `AI Provider` — NOT split to `/settings`. Reason: Zero Mail is single-tenant-per-user.

**D-07.** `SET-BEHV-02` exposes draft confidence as `LOW | MEDIUM | HIGH` enum via shadcn `<Select>`, NOT a 0.0–1.0 slider. Backend stores enum in `assistant_settings.draft_confidence VARCHAR(8)` and maps to internal numeric thresholds (`LOW=0.50, MEDIUM=0.70, HIGH=0.85`) when calling the draft worker.

**D-08.** Backend exposes three feature-scoped PUTs: `PUT /api/settings/voice`, `PUT /api/settings/behavior`, `PUT /api/settings/ai`. NOT one mega `PUT /api/settings`; NOT `PATCH` per-field. Sub-resources separate: `GET/POST/PUT/DELETE /api/knowledge-snippets`, extended `/api/triage/sender-safety-net/*` with DELETE, new `POST /api/settings/ai/test-connection` + `GET /api/settings/ai/cost?window=7d` + `POST /api/settings/voice/generate-from-sent`.

**D-09.** When `tone_preset = 'CUSTOM'`, system prompt uses ONLY `writing_style` (no preset descriptor). For any other preset value (PROFESSIONAL/FRIENDLY/CASUAL/FORMAL), both preset and writing_style are passed to prompt assembler. NO new `custom_tone_description` column.

**D-10..D-12.** SET-VOICE-07: `POST /api/settings/voice/generate-from-sent` (`{sampleSize: number}` default 20, max 50) returns `{generatedStyle: string}` ≤ 500 words. Gmail `users.messages.list` filter `in:sent` + `users.messages.get` in-memory only; LLM via existing Spring AI gateway; rate-limit 3/hour/tenant; on 0 sent → 200 + empty string; on LLM error → existing writing_style unchanged + inline error toast.

**D-11 PRIVACY INVARIANT (LOCKED):** raw email bodies, the LLM prompt, and the LLM completion exchange MUST be in-memory-only — no DB row, no log line, no audit entry. Only user-reviewed-and-saved style guide is persisted via subsequent `PUT /api/settings/voice`. ArchUnit / integration test asserts no `prompt`/`completion`/`body` field is written by the generate path.

**D-13.** Phase 9 code stays inside `core.chat` module. New sub-package `core.chat.settings` (REST DTOs/controllers/use-case services). `AssistantSettingsEntity`, `AssistantKnowledgeMemoryEntity`, `AssistantMemoryService` remain at current `core.chat.persistence` / `core.chat.usecases` location. Safety net stays inside `core.triage`. NO new top-level module. Modulith named-interface `core.chat::settings-api` declares boundary.

**D-14.** Extract a shared `ProviderConnectionTester` service in `core.llm.gateway.springai` from Phase 8 admin MKEY-03 logic. Admin MKEY endpoint refactored to delegate. User-side `POST /api/settings/ai/test-connection` is a thin wrapper enforcing per-tenant 10/hour rate-limit before delegating. Single source of truth for the enum response shape `{OK | INVALID_KEY | RATE_LIMITED | NETWORK_ERROR | TIMEOUT}`.

**D-15.** Daily-digest toggle (`SET-BEHV-03`) reuses existing v1.0 `/api/me/notifications` (`NotificationPreferencesController.PATCH` with `digestEnabled`). Shadow-mode toggle (`SET-BEHV-05`) reuses existing v1.0 `PUT /api/tenant/triage-pause`. NO new column on `assistant_settings` for either.

> **Heads-up to planner:** `tenants.triage_shadow_mode` was DROPPED in changelog `039-drop-triage-shadow-mode.yaml` (2026 historical). The only surviving toggle that maps to "shadow mode" is the triage **pause** flag (`TriagePauseController.PUT /api/tenant/triage-pause`). Either (a) rename UI string from "Shadow mode" to "Pause triage" so backend stays unchanged, or (b) add a separate dry-run column. The CONTEXT D-15 wording assumes (a). Plan should pick one and stop using "shadow mode" terminology if (a). `[ASSUMED]`

**D-16.** `ByokForm` removed from `apps/web/app/(protected)/(app)/settings/SettingsClient.tsx` and rendered exactly once inside the `AI Provider` section on `/ai`. Existing `ByokController` + AES-GCM cipher reused. Legacy `/settings` keeps all other cards untouched.

### Claude's Discretion

(None — every gap was resolved in discuss-phase.)

### Deferred Ideas (OUT OF SCOPE)

- **SET-SAFE-02** paste-import — deferred to v1.3.
- **SET-SAFE-03** per-entry `protect`/`escalate` mode toggle — deferred to v1.3; every user-added entry behaves as `protect`.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SET-VOICE-01 | Writing-style free-text (200–500 words) edit | `AssistantSettingsEntity.writingStyle` column exists; need `PUT /api/settings/voice` + Bean Validation `@Size` |
| SET-VOICE-02 | Personal instructions edit (XML-fenced, injection-sanitized, 2000 cap) | `PersonalizationSanitizer` (`backend/core/.../chat/sanitize/PersonalizationSanitizer.java`) already enforces sentinels + cap; ArchUnit must prove single call site |
| SET-VOICE-03 | Email signature edit (500 cap) | Need new column `assistant_settings.email_signature TEXT` + Liquibase changeset |
| SET-VOICE-04 | Knowledge-snippets CRUD with title uniqueness | `AssistantKnowledgeMemoryEntity` + `AssistantKnowledgeService.append` exist; add `UNIQUE(tenant_id,title)` + `updated_at` + REST CRUD + extend service to update/delete; chat tool `ADD_TO_KNOWLEDGE_BASE` and REST POST share `AssistantKnowledgeService` (ArchUnit) |
| SET-VOICE-05 | Tone preset enum (PROFESSIONAL/FRIENDLY/CASUAL/FORMAL/CUSTOM) | New column `assistant_settings.tone_preset VARCHAR(16) + CHECK` |
| SET-VOICE-06 | AI output language radio (vi/en) | `assistant_settings.ai_output_language` column exists; just need PUT validation |
| SET-VOICE-07 | Generate writing style from recent sent emails (LLM + Gmail API) | Need new endpoint + Gmail `users.messages.list filter in:sent` via existing `GmailApiClientFactory`; LLM via existing `LlmGateway`; rate-limit; in-memory-only privacy invariant |
| SET-BEHV-01 | Auto-draft replies toggle | New column `assistant_settings.auto_draft_replies BOOLEAN NOT NULL DEFAULT TRUE`; draft worker reads flag and short-circuits when FALSE |
| SET-BEHV-02 | Draft confidence enum LOW/MEDIUM/HIGH | New column `assistant_settings.draft_confidence VARCHAR(8) NOT NULL DEFAULT 'MEDIUM' CHECK (...)`; threshold mapping in draft worker |
| SET-BEHV-03 | Daily digest toggle | Reuse `NotificationPreferencesController.PATCH /api/me/notifications` `digestEnabled` field — no backend changes |
| SET-BEHV-04 | Sensitive-data-protection toggle | New column `assistant_settings.sensitive_data_protection BOOLEAN NOT NULL DEFAULT TRUE`; LLM-05 redactor reads flag |
| SET-BEHV-05 | Shadow-mode toggle | Reuse `TriagePauseController.PUT /api/tenant/triage-pause` (note: actual column is `tenants.triage_paused`, not `triage_shadow_mode` — see D-15 heads-up above) |
| SET-SAFE-01 | Sender safety net CRUD with email + domain pattern | Extend `tenant_protected_sender_observation` with `pattern_kind VARCHAR(8) DEFAULT 'EMAIL' CHECK IN ('EMAIL','DOMAIN')` + `created_by_user BOOLEAN DEFAULT FALSE`; add `DELETE /api/triage/sender-safety-net/{id}`; extend POST opt-in to accept `@acme.com`; extend `SenderEmailCanonicalizer` |
| SET-SAFE-04 | Audit-log indicator for safety-net block | New column `triage_audit.blocked_by_safety_net_pattern VARCHAR(320) NULL`; triage worker sets when `decision = REJECTED_BY_SAFETY_NET`; `AuditRow.tsx` / `AuditCardList.tsx` render Badge |
| SET-AI-01 | Single tenant-wide `Platform default ↔ Use my key` mode (NO per-feature picker) | New column `assistant_settings.ai_provider_mode VARCHAR(16) NOT NULL DEFAULT 'PLATFORM_DEFAULT' CHECK IN ('PLATFORM_DEFAULT','USER_BYOK')`; `PUT /api/settings/ai` body is `{mode}` only; helper text reads catalog `defaults` block server-side per feature (read-only). **Updated 2026-05-26** — per-feature provider+model picker removed by user directive |
| SET-AI-02 | Single active BYOK provider + key (4 allowed) | New table `user_byok_key (tenant_id PK, provider VARCHAR(16), ciphertext BYTEA, iv BYTEA, last_test_result VARCHAR(16) NULL, last_tested_at TIMESTAMPTZ NULL)`; one row per tenant — saving a new provider replaces the previous row. Existing `ByokController` is rewritten to this shape (or a new `UserByokController` replaces it). AES-GCM via `RefreshTokenCipher`; allow-list rejects `openrouter`/`9router` with `code=ai.byok.provider_not_allowed` |
| SET-AI-03 | Single tenant-wide last-7d cost (NO per-feature breakdown) | New `GET /api/settings/ai/cost?window=7d` returns `{usd: number}` (single value) via `SUM(llm_call_audit.total_cost_usd) WHERE tenant_id=? AND created_at >= now() - interval '7 days'`. **No `CHAT` enum addition required** — aggregation is tenant-scoped, not feature-scoped. Plan can skip the `ck_llm_call_audit_call_site` Liquibase changeset and the `ChatOrchestrator` audit-write change entirely. **Updated 2026-05-26** — per-feature cost rows removed by user directive |
| SET-AI-04 | BYOK test-connection enum-only response (shared with admin MKEY-03) | Extract `ProviderConnectionTester.probeConnection(provider, ciphertext) -> ConnectionTestResult` from existing `ModelsProbeClient.probeConnection`; admin MKEY-03 endpoint refactored to delegate; new `POST /api/settings/ai/test-connection` (no body — loads tenant's `user_byok_key` row) wraps with per-tenant 10/hour rate-limit; ArchUnit asserts both paths reach `ProviderConnectionTester.probeConnection` |

</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Writing-style / personal-instructions / signature / tone / language CRUD | API / Backend | DB | Persisted in `assistant_settings`; sanitization is backend invariant |
| Knowledge-snippet CRUD | API / Backend | DB | Persisted in `assistant_knowledge_snippet`; uniqueness enforced at Postgres |
| Tone-preset → prompt assembler glue | API / Backend (Spring AI adapter) | — | D-09 logic lives where the system-prompt is built, not in UI |
| Draft confidence threshold mapping | API / Backend (draft worker) | — | Enum→numeric translation runs server-side; UI never sees thresholds |
| Auto-draft / sensitive-data toggles | API / Backend | DB | Boolean column read by draft + LLM-05 redactor workers |
| Daily digest toggle | API / Backend (existing notifications module) | — | Reuses `NotificationPreferenceService.updatePreference` |
| Triage pause ("shadow mode") | API / Backend (existing tenant module) | — | Reuses `TenantService.setTriagePaused` |
| Safety-net CRUD with pattern_kind | API / Backend (triage module) | DB | Stays inside `core.triage` per D-13 |
| Safety-net block badge | API / Backend (triage_audit column) | Frontend (Badge render) | Backend sets `blocked_by_safety_net_pattern`; FE renders Badge |
| Provider + model picker | Frontend SSR (Next.js) | API / Backend (`GET /api/settings/catalog`) | Catalog data delivered by backend; selection is client-state |
| BYOK key entry | API / Backend (cipher) | Frontend (form) | AES-GCM at rest; FE never re-renders plaintext |
| BYOK test connection | API / Backend (`ProviderConnectionTester`) | — | Provider HTTP call confined to backend; FE only sees enum result |
| Last-7d cost helper | API / Backend (`SUM llm_call_audit`) | Frontend (helper text) | Aggregation in SQL; FE renders dollar string |
| Generate-from-sent (SET-VOICE-07) | API / Backend (Gmail + LLM in-memory) | Frontend (Dialog button) | Privacy invariant requires all bytes to live in backend memory only |

## Standard Stack

### Core (already in project — no installs needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.6 | App framework | Locked by `CLAUDE.md`. `[VERIFIED: project STACK.md + pom]` |
| Spring AI | 2.0.0-M7 | LLM orchestration (used by `LlmGateway` for SET-VOICE-07 generate path) | Locked. `[VERIFIED: STACK.md]` |
| Spring Data JPA (Hibernate 7) | Boot-managed | `AssistantSettingsEntity` write path | Existing convention. `[VERIFIED: grep on `@Entity`]` |
| Spring Data JDBC | Boot-managed | `llm_call_audit` aggregation for SET-AI-03 (already used by `SpendAggregateReadRepository`) | Existing convention — JPA writes / JDBC reads CQRS-lite. `[VERIFIED: SpendAggregateReadRepository.java]` |
| Liquibase | 5.0.2 | YAML changesets for new columns | Locked; existing 094+ changeset numbers free. `[VERIFIED: db.changelog-master.yaml]` |
| `jakarta.validation` (Bean Validation) | Jakarta 3.x | `@Size`, `@NotBlank`, `@Pattern` on request DTOs | Required by convention §3 for accurate OpenAPI codegen. `[VERIFIED]` |
| `io.swagger.v3.oas.annotations.media.Schema` | springdoc-openapi | `@Schema(requiredProperties = {...})` on response DTOs | Required by convention §3. `[VERIFIED: existing controllers]` |
| `google-api-services-gmail` | Boot-managed via `GmailApiClientFactory` | SET-VOICE-07 `users.messages.list` + `users.messages.get` | Already wired in `core.gmail.gateway`. `[VERIFIED: GmailPreviewReadService.java imports `com.google.api.services.gmail.Gmail`]` |
| Spring `RestClient` | Boot-managed | Reused inside extracted `ProviderConnectionTester` | Already used by `ModelsProbeClient`. `[VERIFIED]` |

### Frontend (already in project — every shadcn primitive confirmed by `Glob apps/web/components/ui/*.tsx`)

| Library | Version | Purpose |
|---------|---------|---------|
| Next.js | 16.2.4 (App Router) | `/ai/page.tsx` server component shell |
| React | 19.2.5 | Client components |
| shadcn primitives (already installed) | latest | `card`, `dialog`, `button`, `input`, `textarea`, `switch`, `select`, `radio-group`, `table`, `badge`, `separator`, `label`, `tooltip`, `spinner`, `alert-dialog`, `sonner` |
| TanStack Query | 5.100.1 | One hook file per use-case; `meta.successMessage`/`meta.errorMessage` for toasts |
| openapi-typescript + openapi-fetch | 7.13.0 / 0.17.0 | Typed client via `apps/web/lib/api/schema.d.ts` (regenerated by `pnpm --filter web run generate:api`) |
| next-intl | existing | VI default + EN; bundles at `apps/web/i18n/messages/{vi,en}.json` |
| lucide-react | existing | Section + action icons |

### Alternatives Considered

| Instead of | Could Use | Tradeoff (why we don't) |
|------------|-----------|----------|
| `SettingCard` + `Dialog` edit | Inline edit on Card | UI-SPEC D-02 locks Dialog pattern (Inbox Zero parity) |
| `<Select>` LOW/MEDIUM/HIGH | shadcn `<Slider>` 0.0–1.0 | D-07 locks enum (Inbox Zero parity + future-tuning ergonomics) |
| One mega `PUT /api/settings` | Three feature PUTs | D-08 locks split (clean TanStack Query keys per section) |
| Per-feature cost aggregation (`{chat, triage, draft}` USD) | Single tenant-wide cost (`{usd}`) | D-17 locked single-figure footer; no `call_site=CHAT` schema change required |

**No new package installs required for Phase 9 — every library is already a project dependency.**

## Package Legitimacy Audit

> **Skipped — no new external packages installed in this phase.** All capabilities use existing project dependencies (Spring Boot, Spring AI, Spring Data JPA/JDBC, google-api-services-gmail, Liquibase YAML, shadcn primitives, TanStack Query, next-intl, openapi-typescript). slopcheck and registry-verification are not applicable.

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
                   │                          useTriagePauseState  ──► PUT /api/tenant/triage-pause (existing)
                   ├─ <SafetyNetSection>   ──► useProtectedSenders ──► GET /api/triage/sender-safety-net
                   │                                                   POST .../{pattern}/opt-in
                   │                                                   DELETE .../{id}  (NEW)
                   │                          (Auto-send rules toggle stays here — reuses existing RuleAutomationSettings)
                   └─ <AiProviderSection>  ──► useAiSettings  ──► GET/PUT /api/settings/ai
                                              useAiCost      ──► GET /api/settings/ai/cost?window=7d
                                              useTestConn    ──► POST /api/settings/ai/test-connection
                                              <ByokForm>     ──► existing /api/llm/byok

Backend (Spring MVC controllers in backend/api)
    │
    ├─ controllers/settings/  (NEW)
    │     ├─ SettingsVoiceController       → SettingsVoiceService          → AssistantSettingsJpaRepository
    │     │                                   └─ PersonalizationSanitizer (existing — single instance)
    │     ├─ SettingsBehaviorController    → SettingsBehaviorService       → AssistantSettingsJpaRepository
    │     ├─ SettingsAiController          → SettingsAiService             → AssistantSettingsJpaRepository
    │     │                                   ├─ ProviderConnectionTester (NEW — extracted from ModelsProbeClient)
    │     │                                   └─ AiCostQueryService (NEW — JDBC over llm_call_audit)
    │     ├─ KnowledgeSnippetController    → AssistantKnowledgeService     (extended for update/delete)
    │     └─ VoiceGenerateController       → VoiceGenerationService (NEW)
    │                                          ├─ GmailSentMessagesReader (NEW — in-memory only)
    │                                          ├─ LlmGateway (existing)
    │                                          └─ RateLimiter (Redis Lettuce — existing pattern)
    │
    ├─ controllers/triage/SenderSafetyNetController (extended)
    │     └─ SenderSafetyNetService (extended) → TenantProtectedSenderObservationRepository (extended entity)
    │
    └─ controllers/llm/ByokController (unchanged)

Postgres
    ├─ assistant_settings           (+5 columns via Liquibase 094)
    ├─ assistant_knowledge_snippet  (+UNIQUE(tenant_id,title), service-layer updated_at touch via Liquibase 095)
    ├─ tenant_protected_sender_observation  (+pattern_kind, +created_by_user via Liquibase 096)
    ├─ triage_audit                 (+blocked_by_safety_net_pattern via Liquibase 096)
    ├─ llm_call_audit               (existing; UNCHANGED — D-17 retired the CHAT enum addition)
    └─ user_byok_key                (NEW table via Liquibase 097 — tenant_id PK, provider, base_url, ciphertext, iv, model_id, active, last_test_result, last_tested_at)
```

### Recommended Project Structure

```
backend/core/src/main/java/com/zeromail/core/chat/
├── persistence/
│   ├── AssistantSettingsEntity.java                 [EXTEND with 5 columns — voice/behavior fields ONLY; NO `ai_provider_mode` column per D-17]
│   ├── AssistantSettingsJpaRepository.java          [EXTEND with findByTenantId or use existing if present]
│   ├── AssistantKnowledgeMemoryEntity.java          [no change — already has tenant scoping]
│   ├── UserByokKeyEntity.java                       [NEW — single tenant-scoped BYOK row per D-17]
│   └── UserByokKeyJpaRepository.java                [NEW — findByTenantId / save / delete]
├── usecases/
│   ├── AssistantKnowledgeService.java               [EXTEND: list/update/delete; keep append for chat-tool reuse]
│   ├── settings/                                    [NEW sub-package per D-13]
│   │   ├── SettingsVoiceService.java
│   │   ├── SettingsBehaviorService.java
│   │   ├── VoiceGenerationService.java              [SET-VOICE-07; calls LlmGateway + Gmail in-memory]
│   │   └── AiCostQueryService.java                  [JDBC SUM over llm_call_audit — tenant-wide]
│   └── byok/                                        [NEW sub-package — D-17]
│       ├── UserByokService.java                     [save/load/activate/delete; uses RefreshTokenCipher]
│       └── ByokProviderResolver.java                [resolves chat/triage/draft model → BYOK row IF active+tested ELSE catalog default]
└── sanitize/PersonalizationSanitizer.java           [no change — REUSED by SettingsVoiceService AND chat-tool]

backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/
└── ProviderConnectionTester.java                    [NEW per D-14; extracted from ModelsProbeClient]

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
│   └── UserByokController.java                      [POST /api/byok, GET /api/byok, PUT /api/byok/active, PUT /api/byok/model, DELETE /api/byok, POST /api/byok/test-connection]
├── controllers/triage/SenderSafetyNetController.java [EXTEND: DELETE + accept domain pattern]
└── dto/settings/                                    [NEW]
    ├── VoiceSettingsResponse.java
    ├── VoiceSettingsUpdateRequest.java
    ├── BehaviorSettingsResponse.java
    ├── BehaviorSettingsUpdateRequest.java
    ├── AiCostResponse.java                          [{usd: number}]
    ├── ByokSaveRequest.java / ByokResponse.java     [provider, baseUrl, lastFourChars, modelId, active, lastTestResult, lastTestedAt — NEVER plaintext]
    ├── ByokActivateRequest.java                     [{active: bool}]
    ├── ByokModelRequest.java                        [{modelId: string}]
    ├── ByokTestConnectionRequest.java               [optional {provider, baseUrl, apiKey} for inline test]
    ├── ByokTestConnectionResponse.java              [{result, models?: string[]}]
    ├── GenerateFromSentRequest.java / Response.java
    └── KnowledgeSnippetRequest.java / Response.java / ListResponse.java

backend/core/src/main/resources/db/changelog/changes/
├── 094-assistant-settings-phase9-columns.yaml       [NEW — voice/behavior columns ONLY; NO ai_provider_mode]
├── 095-assistant-knowledge-snippet-unique-title-and-updated-at.yaml  [NEW]
├── 096-safety-net-pattern-kind-and-audit-badge.yaml [NEW]
└── 097-user-byok-key-table.yaml                     [NEW per D-17 — creates user_byok_key table]

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
│   ├── AiProviderSection.tsx                        [NEW — single BYOK card with Provider/BaseURL/Key/Model/Active switch + cost footer]
│   ├── WritingStyleDialog.tsx                       [NEW — includes Generate-from-sent button]
│   ├── PersonalInstructionsDialog.tsx               [NEW]
│   ├── EmailSignatureDialog.tsx                     [NEW]
│   ├── TonePresetDialog.tsx                         [NEW]
│   ├── AiOutputLanguageDialog.tsx                   [NEW]
│   └── DraftConfidenceDialog.tsx                    [NEW]
├── api/
│   └── ai-settings-api.ts                           [NEW — typed via generated schema.d.ts]
├── hooks/
│   ├── useVoiceSettings.ts                          [NEW]
│   ├── useUpdateVoiceSettings.ts                    [NEW; meta.successMessage]
│   ├── useBehaviorSettings.ts                       [NEW]
│   ├── useUpdateBehaviorSettings.ts                 [NEW]
│   ├── useAiSettings.ts                             [NEW]
│   ├── useUpdateAiSettings.ts                       [NEW]
│   ├── useAiCost.ts                                 [NEW]
│   ├── useTestConnection.ts                         [NEW]
│   └── useGenerateVoiceFromSent.ts                  [NEW]
├── query-keys.ts                                    [NEW]
└── messages.ts                                      [EXTEND]

apps/web/features/knowledge/                         [NEW feature — mirrors Inbox Zero folder split]
├── components/
│   ├── KnowledgeTable.tsx
│   ├── KnowledgeDialog.tsx
│   └── KnowledgeRow.tsx
├── api/knowledge-api.ts
├── hooks/{useKnowledge,useCreateKnowledge,useUpdateKnowledge,useDeleteKnowledge}.ts
└── query-keys.ts

apps/web/app/(protected)/(app)/settings/SettingsClient.tsx  [EDIT — remove ByokForm import and render]
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
        // length validation thrown as BusinessException → maps to HTTP 400 with code=voice.*
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

### Pattern 2: Bean-Validation DTOs that drive OpenAPI codegen (per CONVENTIONS.md §3)

```java
// backend/api/src/main/java/com/zeromail/api/dto/settings/VoiceSettingsUpdateRequest.java
public record VoiceSettingsUpdateRequest(
        @Schema(description = "200..500 word free-text writing style guide")
        @Size(min = 1, max = 5000) String writingStyle,            // word count enforced in service

        @Schema(description = "Personal instructions, max 2000 chars after sanitization")
        @Size(max = 2000) String personalInstructions,

        @Schema(description = "Email signature, max 500 chars")
        @Size(max = 500) String emailSignature,

        @Schema(allowableValues = {"PROFESSIONAL", "FRIENDLY", "CASUAL", "FORMAL", "CUSTOM"})
        @Pattern(regexp = "^(PROFESSIONAL|FRIENDLY|CASUAL|FORMAL|CUSTOM)$") String tonePreset,

        @Schema(allowableValues = {"vi", "en"})
        @Pattern(regexp = "^(vi|en)$") String aiOutputLanguage) {}
```

After this DTO ships: **boot backend → `pnpm --filter web run generate:api` → commit `apps/web/lib/api/schema.d.ts` + `apps/web/openapi/zero-mail-spec.json`**. The plan MUST include this regen step explicitly for every wave that mutates a backend DTO.

### Pattern 3: TanStack Query mutation with global toast meta (per `apps/web/AGENTS.md`)

```ts
// apps/web/features/ai/hooks/useUpdateVoiceSettings.ts
export function useUpdateVoiceSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: VoiceSettingsUpdateRequest) =>
      api.PUT('/api/settings/voice', { body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: aiKeys.voice() }),
    meta: {
      successMessage: 'ai.voice.saved',
      errorMessage: 'ai.voice.saveFailed',
    },
  });
}
```

No local `toast.success/error`. Global handler in `apps/web/lib/query-client.tsx` reads `meta` and toasts via Sonner.

### Pattern 4: SET-VOICE-07 in-memory generate path (privacy invariant)

```java
// backend/core/src/main/java/com/zeromail/core/chat/usecases/settings/VoiceGenerationService.java
@Service
public class VoiceGenerationService {
    @Transactional(propagation = NOT_SUPPORTED)  // no DB write
    public GenerateFromSentResult generate(UUID tenantId, int sampleSize) {
        // Step 1: rate-limit check (Redis 3/hour)
        rateLimiter.requireAllowance(tenantId, "voice.generate", 3, Duration.ofHours(1));

        // Step 2: read sent messages (METADATA + bodies in-memory only — never logged)
        List<SentMessageSummary> samples =
                gmailSentMessagesReader.readRecentSent(tenantId, Math.min(sampleSize, 50));
        if (samples.isEmpty()) {
            return GenerateFromSentResult.empty();
        }

        // Step 3: build prompt + call LLM via existing Spring AI gateway
        //   - call_site = VOICE_GENERATE (must be added to ck_llm_call_audit_call_site
        //     CHECK — or use existing PREVIEW value; planner picks)
        //   - prompt/completion text NEVER logged (ChatResponse.toString ArchUnit gate)
        ChatResponse response = llmGateway.chat(
                LlmChatRequest.forVoiceGenerate(tenantId, samples),
                CallSite.PREVIEW);

        String generated = response.getResult().getOutput().getContent();
        // Step 4: truncate at 500 words (server-side cap)
        return GenerateFromSentResult.of(truncateToWords(generated, 500));
    }
}
```

**Critical:** every variable holding email body bytes is method-local. No `gmailMessage.body` is written to any field, repository, or log line. Integration test seeds Gmail-API stub with sentinel content like `LEAK_SENTINEL_AB12CD34` and greps the captured log + audit + DB rows for the sentinel after invocation — assert zero matches.

### Anti-Patterns to Avoid

- **Inline `toast.success/error` in feature hooks.** Use `meta.successMessage`. The global handler is opt-in and will not double-fire on existing local toasts but new code must use meta.
- **Hand-edit `apps/web/lib/api/schema.d.ts`.** Always boot backend + regen. (`apps/web/AGENTS.md` says next regen will silently overwrite.)
- **Hardcoded color hex** (`bg-[#867AEB]`). Use tokens (`bg-primary/10`, `border-border`).
- **Custom sanitizer for `personal_instructions` in REST path.** Must reuse `PersonalizationSanitizer` — ArchUnit invariant.
- **Direct call to repository from controller.** Convention §1 — controllers translate HTTP only.
- **New `assistant_settings` row inserted by REST when one already exists.** Use upsert pattern (`findByTenantId.orElseGet(defaults)` then `saveAndFlush`).
- **`@SpringBootTest` for unit tests** — use plain JUnit + Mockito for sanitization, validators, enum mapping (TESTING.md §3).
- **Polling provider `/v1/models` from the FE.** Test-connection always goes through backend `ProviderConnectionTester` (avoids exposing the BYOK key client-side).
- **Storing the LLM prompt/completion from the generate-from-sent path** — privacy invariant D-11.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Sanitizing personal_instructions | New `Phase9Sanitizer` | Existing `PersonalizationSanitizer` | Single source of truth — ArchUnit verifies single call site |
| AES-GCM encryption for BYOK | New cipher | Existing `RefreshTokenCipher` / `PlatformSecretCipher` | Already key-managed; reuse is ARCH-11 friendly |
| Provider HTTP probe | Custom `RestClient` per provider | Extracted `ProviderConnectionTester` (D-14) | Single sentinel-leak scrub (ARCH-11); enum-only response shape unified |
| Gmail OAuth refresh / token plumbing | Custom Gmail call | `GmailApiClientFactory` (`backend/core/.../gmail/gateway/GmailApiClientFactory.java`) | Already handles token refresh + `RefreshTokenCipher` decrypt |
| Rate limiting (3/hour voice generate, 10/hour test-connection) | Custom in-memory counter | Redis Lettuce + existing `MasterKeyRateLimiter` pattern under `core.admin.mkey.usecases` | Same lease/eviction shape; portable |
| Per-feature cost aggregation | New denormalized table | `SUM(total_cost_usd) GROUP BY call_site` on existing `llm_call_audit` (mirror `SpendAggregateReadRepository`) | Phase 8 already paid the indexing cost (`idx_llm_call_audit_call_site_created`) |
| Knowledge-snippet `updated_at` touch | DB trigger | Hibernate `@PreUpdate` on entity OR JPA `@LastModifiedDate` from Spring Data auditing | Stays at app layer; no Liquibase trigger DSL ceremony |
| ConfirmDialog primitive | New `AlertDialog` wrapper | UI-SPEC §"Destructive actions" says use shadcn `Dialog` with destructive button | Project hasn't standardized AlertDialog yet; UI-SPEC says reuse Dialog |
| Field-level error localization | Hand-coded VI/EN strings per field | Existing `useLocalizedFieldError` in `apps/web/lib/api/errors.ts` | Switch on `err.code`; matches T-1.1.06 threat model |

**Key insight:** Phase 9 builds ZERO net-new core capabilities. Every layer (cipher, sanitizer, repository pattern, rate-limiter, Gmail client, Spring AI gateway, ETag, OpenAPI codegen, next-intl, shadcn primitives, TanStack Query meta) is already in the project. The work is composition + Liquibase column adds + UI wiring + tests.

## Runtime State Inventory

> Phase 9 is greenfield additions + extensions of existing schemas. No rename/refactor of stored data. **Section omitted intentionally.**

## Common Pitfalls

### Pitfall 1: SET-VOICE-07 silent log leak via Spring AI default tracing

**What goes wrong:** Spring AI M7 has a `chat.client.observation` instrumentation hook that captures prompt + completion content into spans by default if `spring.ai.chat.client.observations.log-prompt=true` (or equivalent).
**Why it happens:** Easy to enable for debugging and forget to turn off.
**How to avoid:** Verify `application.yml` for both `backend/api` and `backend/worker` has `spring.ai.chat.client.observations.log-prompt: false` and `spring.ai.chat.client.observations.log-completion: false` (or equivalent — verify exact key against Spring AI 2.0.0-M7 docs via Context7). Add a property-binding test in `core.config` that asserts these are FALSE.
**Warning signs:** Tempo trace span contains `gen_ai.prompt.0.content` attribute. `[ASSUMED]` — verify property names via Context7 (`/spring-projects/spring-ai`) before lock.

### Pitfall 2: `assistant_settings` upsert race on first save

**What goes wrong:** Two simultaneous `PUT /api/settings/voice` requests (FE retry + user double-click) both `findByTenantId` → empty → both `new AssistantSettingsEntity.defaults` → both `saveAndFlush` → Postgres `ux_assistant_settings_tenant` unique index throws on second.
**Why it happens:** Optimistic check-then-insert is not atomic.
**How to avoid:** Either use `INSERT ... ON CONFLICT (tenant_id) DO UPDATE` SQL via JDBC, OR catch `DataIntegrityViolationException` and retry the `findByTenantId` path (the row exists now), OR wrap in `@Transactional(isolation = REPEATABLE_READ)` and acquire row lock via `SELECT FOR UPDATE`. Recommendation: ON CONFLICT — Postgres-native, one trip, no retry loop.
**Warning signs:** Sporadic 500s in CI integration tests under load.

### Pitfall 3: Knowledge snippet 409 conflict UX

**What goes wrong:** User opens KnowledgeDialog, types title "VIP Customers" (which already exists), clicks Save → backend returns 409 → toast says generic "Couldn't save" → user confused.
**Why it happens:** Generic error toast fires, doesn't translate `code=knowledge.title.duplicate` into a field-level inline error.
**How to avoid:** Map `code=knowledge.title.duplicate` in `apps/web/lib/api/errors.ts`; have the Dialog form render the localized message inline above the title field via `useLocalizedFieldError`. UI-SPEC already has the VI/EN strings.
**Warning signs:** Toast says generic message instead of "A snippet with this title already exists" / "Đã có đoạn kiến thức với tiêu đề này".

### Pitfall 4: ~~`call_site` enum missing `CHAT`~~ — **RETIRED 2026-05-26**

**Status:** Obsolete under D-17 (single tenant-wide cost — no per-feature breakdown). The cost endpoint `GET /api/settings/ai/cost?window=7d` now returns `{usd: number}` aggregated as `SUM(total_cost_usd) WHERE tenant_id = ?` — no `GROUP BY call_site`, no `WHERE call_site IN (...)`. The existing `ck_llm_call_audit_call_site` CHECK constraint is untouched; `ChatOrchestrator` keeps emitting whatever value it emits today. Plans MUST NOT include a `CallSite.CHAT` addition or a Liquibase changeset 097 against this constraint.

### Pitfall 5: Triage-pause renamed to "shadow mode" in UI confuses real shadow-mode semantics

**What goes wrong:** UI calls the toggle "Shadow mode" but backend semantics are full triage pause (no rules execute at all). Real shadow mode (rules run but Gmail side-effects suppressed) was DROPPED in changelog 039.
**Why it happens:** Naming carry-over from REQUIREMENTS.md which still references SET-BEHV-05 as "shadow mode".
**How to avoid:** Pick one and document in plan: (a) rename UI string to "Tạm dừng triage" / "Pause triage" and keep the existing backend endpoint, OR (b) re-introduce a dedicated `shadow_mode` column + worker codepath. (a) is cheaper and matches what users actually expect from a kill-switch. SPEC + CONTEXT both say (a) implicitly — confirm.

### Pitfall 6: BYOK provider allow-list drift between frontend filter and backend reject

**What goes wrong:** Frontend `<Select>` filters out OpenRouter+9Router from BYOK options. Backend `PUT /api/settings/ai` doesn't enforce the same allow-list. Attacker (or curl user) saves `providerId=openrouter, useBYOK=true` → silent success → confusing state.
**Why it happens:** Defense-in-depth lapse.
**How to avoid:** Server-side validation in `SettingsAiService` rejects `useBYOK=true` for `providerId IN ('openrouter','9router')` with HTTP 400 `code=ai.byok.provider_not_allowed`. ArchUnit / controller test asserts the rejection path.
**Warning signs:** Manual curl POST with `useBYOK=true, providerId='openrouter'` succeeds with 200.

### Pitfall 7: Liquibase YAML changelog numbering collision

**What goes wrong:** The changelog directory has two `086-*.yaml` files (`086-create-waitlist-email.yaml` and `086-rule-personas-examples-actions.yaml`) — both compiled, both ran. Phase 9 changesets must not reuse a number that's already in `db.changelog-master.yaml` includeAll order.
**Why it happens:** Parallel feature branches landed without coordinating numbers.
**How to avoid:** Pick next free integer ≥ 094 (highest verified existing changeset is `093-billing-package-presentation-fields.yaml`). Don't rely on Liquibase tolerating duplicate numeric prefixes — it does, but it hurts readability.

## Code Examples

### Liquibase changeset 094 (assistant_settings columns)

```yaml
# backend/core/src/main/resources/db/changelog/changes/094-assistant-settings-phase9-columns.yaml
databaseChangeLog:
  - changeSet:
      id: 094-assistant-settings-phase9-columns
      author: zeromail
      comment: Phase 9 — add email_signature, tone_preset, auto_draft_replies, draft_confidence, sensitive_data_protection.
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

### Liquibase changeset 095 (knowledge UNIQUE + updated_at-touch — `updated_at` column already exists from 046)

```yaml
databaseChangeLog:
  - changeSet:
      id: 095-assistant-knowledge-snippet-unique-title
      author: zeromail
      comment: Phase 9 D-05 — enforce unique title per tenant; updated_at column already present (changeset 046).
      changes:
        - addUniqueConstraint:
            tableName: assistant_knowledge_snippet
            columnNames: tenant_id, title
            constraintName: uq_assistant_knowledge_snippet_tenant_title
      rollback:
        - dropUniqueConstraint:
            tableName: assistant_knowledge_snippet
            constraintName: uq_assistant_knowledge_snippet_tenant_title
```

**Note:** `assistant_knowledge_snippet.updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` already exists from changeset 046 (verified by reading the file). The plan does NOT need to add it. JPA service-layer touch (set `updatedAt = Instant.now()` before `save`) is enough.

### Knowledge CRUD service signatures (extends existing)

```java
// backend/core/src/main/java/com/zeromail/core/chat/usecases/AssistantKnowledgeService.java
@Transactional
public List<KnowledgeSnippetProjection> list(UUID tenantId) { /* ORDER BY updated_at DESC */ }

@Transactional
public KnowledgeSnippetProjection update(UUID tenantId, UUID snippetId, String title, String content) {
    AssistantKnowledgeMemoryEntity entity =
        repo.findByIdAndTenantId(snippetId, tenantId)
            .orElseThrow(() -> new KnowledgeSnippetNotFoundException(snippetId));
    entity.rename(requireBoundedText(title, "title", 120));
    entity.replaceContent(requireBoundedText(content, "content", 8_000));
    // updated_at = Instant.now() via @PreUpdate or explicit setter
    return KnowledgeSnippetProjection.from(repo.saveAndFlush(entity));
}

@Transactional
public void delete(UUID tenantId, UUID snippetId) {
    int deletedRows = repo.deleteByIdAndTenantId(snippetId, tenantId);
    if (deletedRows == 0) throw new KnowledgeSnippetNotFoundException(snippetId);  // → HTTP 404
}
```

The existing `append(tenantId, title, content)` method stays unchanged so `ADD_TO_KNOWLEDGE_BASE` chat-tool continues to work — but the duplicate-title path now throws `DataIntegrityViolationException` from the new unique constraint and must be caught and rethrown as `KnowledgeTitleDuplicateException`. Adjust `append` accordingly.

### REST DTO with closed enum (drives typed FE codegen)

```java
// backend/api/src/main/java/com/zeromail/api/dto/settings/BehaviorSettingsUpdateRequest.java
public record BehaviorSettingsUpdateRequest(
        @Schema(requiredMode = REQUIRED) Boolean autoDraftReplies,

        @Schema(requiredMode = REQUIRED, allowableValues = {"LOW","MEDIUM","HIGH"})
        @Pattern(regexp = "^(LOW|MEDIUM|HIGH)$") String draftConfidence,

        @Schema(requiredMode = REQUIRED) Boolean sensitiveDataProtection) {}
```

After generate-api regen, FE imports as `components['schemas']['BehaviorSettingsUpdateRequest']` with `draftConfidence: 'LOW' | 'MEDIUM' | 'HIGH'` literal union.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Shadow-mode dry-run logging | Triage pause (full stop) | Changelog 039 (2026 historical) | UI label must say "Pause" — D-15 heads-up |
| Slider 0.0–1.0 draft confidence (original SPEC) | LOW/MEDIUM/HIGH enum | D-07 (discuss-phase 2026-05-26) | One backend column + service-layer threshold map |
| 4 shadcn `<Tabs>` on `/ai?tab=...` (original SPEC) | Flat `<SectionHeader>` groups (Inbox Zero pattern) | D-01 (discuss-phase 2026-05-26) | One flat `AiConfigPage.tsx`; no query-param sync; no nested routes |
| `KnowledgeMemory` chat-tool-only | REST CRUD + chat-tool both go through `AssistantKnowledgeService` | Phase 9 D-04 | One ArchUnit guard for single call site; UI gets Table editor |

**Deprecated/outdated:**

- `tenants.triage_shadow_mode` column — dropped 2026 historical; UI must not reference it.
- Original "Phase 8" admin scope referenced 6 BYOK providers; v1.2 user-side BYOK is locked to 4 (OpenAI/Anthropic/Google/DeepSeek). Confirmed by SPEC + REQUIREMENTS Out-of-Scope row.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring AI 2.0.0-M7 has `spring.ai.chat.client.observations.log-prompt` property; default may already be FALSE | Pitfall 1 | If wrong property name → privacy invariant not enforced for generate-from-sent; planner must verify via Context7 before locking config |
| A2 | "Shadow mode" UI label maps to existing `triage_paused` flag (no new column) | D-15 heads-up | If user wants a true shadow-log mode, Phase 9 needs an extra column + worker codepath — adds 2–3 tasks |
| A3 | `assistant_settings.updated_at` auto-touches via JPA `@PreUpdate` lifecycle (not via DB trigger) | Pattern 1 | If JPA listener silently fails on detach, ordering by `updated_at DESC` regresses. Mitigation: integration test |
| A4 | ~~`llm_call_audit.call_site` enum must add `CHAT`~~ — RETIRED (D-17) | Pitfall 4 | Obsolete: SET-AI-03 now returns a single tenant-wide `usd` figure, not per-feature. No `CHAT` enum addition, no `call_site` CHECK changeset. Plans that touch this enum should be rejected by the plan-checker |
| A5 | Existing `AssistantSettingsJpaRepository` has `findByTenantId` (it's referenced by `AssistantPersonalInstructionsService`) | Pattern 1 | If method signature differs, service code adapts trivially — no schema impact |
| A6 | `apps/web/i18n/messages/{vi,en}.json` is the next-intl bundle; new keys added without `[locale]` route segment break (per request.ts comment) | RESEARCH "Internationalization" | If bundle path differs in 16.2.4, hooks throw at runtime; verified by reading `apps/web/i18n/request.ts` |

## Open Questions

1. **~~`call_site = 'CHAT'` enum: emit-from-chat vs reuse-existing~~ — RETIRED 2026-05-26 (D-17)**
   - SET-AI-03 now returns a single tenant-scoped USD figure (`SUM(total_cost_usd) WHERE tenant_id = ?`). No `GROUP BY call_site`, no `WHERE call_site = 'CHAT'`. The existing CHECK constraint is untouched. Plans MUST NOT add `CallSite.CHAT` or a `ck_llm_call_audit_call_site` Liquibase changeset for this phase.

2. **`@LastModifiedDate` Spring Data auditing wired?**
   - What we know: `AssistantKnowledgeMemoryEntity` extends `AbstractTenantOwnedEntity`. Whether `AbstractTenantOwnedEntity` already has `updatedAt` field with `@LastModifiedDate` is unverified in this research.
   - What's unclear: whether explicit `entity.touchUpdatedAt()` calls are needed.
   - Recommendation: planner reads `backend/core/.../shared/persistence/AbstractTenantOwnedEntity.java` during plan-phase to confirm. If no auditing wiring, add `@EnableJpaAuditing` + `@EntityListeners(AuditingEntityListener.class)` once or do the touch manually in service.

3. **Rate-limit primitive — Redis Lettuce vs in-memory `MasterKeyRateLimiter` shape**
   - What we know: Phase 8 ships `core.admin.mkey.usecases.MasterKeyRateLimiter` for the 10/hour admin test-connection cap.
   - What's unclear: whether that class is admin-only or generally reusable.
   - Recommendation: planner inspects the class; if generic, extract to `core.shared.ratelimit`; if admin-bound, write a thin per-tenant variant (2 cases: voice.generate 3/hour, ai.test-connection 10/hour).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL 18.4 (testcontainers) | All persistence tests | ✓ | per STACK.md | — |
| Spring Boot 4.0.6 | App | ✓ | locked | — |
| Spring AI 2.0.0-M7 | Generate-from-sent | ✓ | locked | If `LlmGateway` chat call surface differs in M7, adapt at adapter boundary |
| Gmail API client | SET-VOICE-07 | ✓ | via `GmailApiClientFactory` | — |
| Redis Lettuce | Rate-limit + cache | ✓ | locked | — |
| `pnpm --filter web run generate:api` | OpenAPI codegen | ✓ | per AGENTS.md | Manual hand-edit is FORBIDDEN — must boot backend |
| shadcn primitives | All UI | ✓ | every needed primitive already in `apps/web/components/ui/` (Glob-verified) | If a primitive turns out missing later: `pnpm dlx shadcn@latest add <component>` from `apps/web` |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none.

## Validation Architecture

> `workflow.nyquist_validation` is `true` in `.planning/config.json`. Section included.

### Test Framework

| Property | Value |
|----------|-------|
| Backend test framework | JUnit 5 (Jupiter) + AssertJ + Mockito + Testcontainers Postgres (per TESTING.md §3). Base test classes: `PostgresContainerTest`, `ApiPostgresTestBase`, `TestSessionSupport.TestSessionMinter` for real session cookies |
| Frontend unit framework | Vitest 4 (existing in `apps/web/__tests__/**`) |
| Frontend e2e framework | Playwright 1.60 in `apps/web/e2e/**` |
| Backend quick run | `./gradlew :backend:core:test :backend:api:test --tests "*Settings*" --tests "*Knowledge*" --tests "*VoiceGeneration*"` |
| Backend full suite | `./gradlew test` |
| Frontend quick run | `pnpm --filter web test --run features/ai features/knowledge` |
| E2E run | `pnpm --filter web e2e -- ai-settings.spec.ts` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SET-VOICE-01 | writing_style 200–500 word bounds enforced | unit (validator) | `./gradlew :backend:core:test --tests SettingsVoiceServiceWordBoundsTest` | ❌ Wave 0 |
| SET-VOICE-01 | PUT /api/settings/voice returns 200 + persists | mvc slice | `./gradlew :backend:api:test --tests SettingsVoiceControllerTest` | ❌ Wave 0 |
| SET-VOICE-02 | sanitizer single-call invariant | ArchUnit | `./gradlew :backend:core:test --tests PersonalizationSanitizerSingleCallSiteTest` | ❌ Wave 0 |
| SET-VOICE-02 | sentinel `[SYSTEM]` removed from persisted value | unit | `./gradlew :backend:core:test --tests PersonalizationSanitizerCorpusTest` | (existing) ✅ |
| SET-VOICE-03 | signature appears verbatim in next draft | integration | `./gradlew :backend:core:test --tests DraftSignatureIntegrationTest` | ❌ Wave 0 |
| SET-VOICE-04 | UNIQUE(tenant_id,title) returns 409 | `@DataJpaTest` | `./gradlew :backend:core:test --tests AssistantKnowledgeMemoryUniqueTitleTest` | ❌ Wave 0 |
| SET-VOICE-04 | cross-tenant delete returns 404 | mvc slice | `./gradlew :backend:api:test --tests KnowledgeSnippetControllerTenantIsolationTest` | ❌ Wave 0 |
| SET-VOICE-04 | chat-tool + REST share `AssistantKnowledgeService.append` | ArchUnit | `./gradlew :backend:core:test --tests KnowledgeSnippetSingleWriteSiteTest` | ❌ Wave 0 |
| SET-VOICE-05 | tone_preset enum CHECK rejects bad value | `@DataJpaTest` | `./gradlew :backend:core:test --tests AssistantSettingsTonePresetCheckTest` | ❌ Wave 0 |
| SET-VOICE-06 | non-`vi`/`en` ai_output_language returns 400 | mvc slice | `./gradlew :backend:api:test --tests SettingsVoiceLanguageValidationTest` | ❌ Wave 0 |
| SET-VOICE-07 | sentinel content never reaches DB/log | integration | `./gradlew :backend:core:test --tests VoiceGenerationFromSentLeakTest` | ❌ Wave 0 (critical privacy test) |
| SET-VOICE-07 | 4th call/hour returns 429 | unit | `./gradlew :backend:core:test --tests VoiceGenerationRateLimitTest` | ❌ Wave 0 |
| SET-BEHV-01 | toggle OFF → draft worker writes no rows | integration | `./gradlew :backend:worker:test --tests DraftAutoToggleIntegrationTest` | ❌ Wave 0 |
| SET-BEHV-02 | draft worker resolves enum → threshold and skips below | integration | `./gradlew :backend:worker:test --tests DraftConfidenceThresholdTest` | ❌ Wave 0 |
| SET-BEHV-03 | reuses existing ANL-03 endpoint (no new column) | smoke | manual click in Playwright e2e | (Playwright) ❌ |
| SET-BEHV-04 | LLM-05 redactor toggle-aware | unit | `./gradlew :backend:core:test --tests SensitiveDataRedactionToggleTest` | ❌ Wave 0 |
| SET-BEHV-05 | reuses TRG-07 / triage-pause endpoint | smoke | Playwright e2e | (Playwright) ❌ |
| SET-SAFE-01 | DELETE observation-created entry → 403 | mvc slice | `./gradlew :backend:api:test --tests SenderSafetyNetDeleteAuthorityTest` | ❌ Wave 0 |
| SET-SAFE-01 | `@acme.com` POST persists as DOMAIN | mvc slice | `./gradlew :backend:api:test --tests SenderSafetyNetDomainPatternTest` | ❌ Wave 0 |
| SET-SAFE-01 | DOMAIN entry blocks matching sender in triage worker | integration | `./gradlew :backend:worker:test --tests TriageSafetyNetDomainMatchTest` | ❌ Wave 0 |
| SET-SAFE-04 | `blocked_by_safety_net_pattern` populated when REJECTED_BY_SAFETY_NET | integration | `./gradlew :backend:worker:test --tests TriageAuditSafetyNetBadgeTest` | ❌ Wave 0 |
| SET-AI-01 | resolution rule: active row + tested + model → calls `{base_url}` with `model_id`; otherwise calls catalog default | integration | `./gradlew :backend:core:test --tests ByokResolutionIntegrationTest` | ❌ Wave 0 |
| SET-AI-01 | Active switch gate: `PUT /api/byok/active {active:true}` rejects when `model_id IS NULL` or `last_test_result <> 'OK'` | mvc slice | `./gradlew :backend:api:test --tests ByokActivateGateTest` | ❌ Wave 0 |
| SET-AI-02 | save BYOK for `openrouter` → 400 `code=ai.byok.provider_not_allowed` | mvc slice | `./gradlew :backend:api:test --tests ByokSaveProviderAllowListTest` | ❌ Wave 0 |
| SET-AI-02 | base URL validation: `http://attacker.com` → 400 `code=ai.byok.base_url_not_https` | mvc slice | `./gradlew :backend:api:test --tests ByokSaveBaseUrlValidationTest` | ❌ Wave 0 |
| SET-AI-02 | saving a row clears `active`, `last_test_result`, `last_tested_at` | `@DataJpaTest` | `./gradlew :backend:core:test --tests ByokSaveResetsStateTest` | ❌ Wave 0 |
| SET-AI-02 | plaintext key never echoed in response (regex assertion) | mvc slice (snapshot) | `./gradlew :backend:api:test --tests ByokResponseNeverEchoesPlaintextTest` | ❌ Wave 0 |
| SET-AI-03 | tenant-wide cost SUM returns exactly `{usd}` | `@DataJpaTest` | `./gradlew :backend:core:test --tests AiCostQueryService7DayTest` | ❌ Wave 0 |
| SET-AI-04 | enum-only response shape; 401 body never leaks; `OK` carries `models[]` | mvc slice | `./gradlew :backend:api:test --tests ByokTestConnectionEnumOnlyTest` | ❌ Wave 0 |
| SET-AI-04 | 11th test/hour returns 429 `code=ai.byok.test_connection.rate_limited` | unit | `./gradlew :backend:core:test --tests ByokTestConnectionRateLimitTest` | ❌ Wave 0 |
| SET-AI-04 | admin MKEY-03 + user `/api/byok/test-connection` both reach `ProviderConnectionTester.probeConnection` | ArchUnit | `./gradlew :backend:core:test --tests ProviderConnectionTesterSingleBindingTest` | ❌ Wave 0 |
| Whole page | flat-section golden path | Playwright e2e | `pnpm --filter web e2e -- ai-settings.spec.ts` | ❌ Wave 0 |
| Whole page | no hardcoded color hex | repo grep gate | `apps/web` existing lint task | ✅ |

### Sampling Rate

- **Per task commit:** the smallest matching slice command from the table above (e.g. backend service tasks run `./gradlew :backend:core:test --tests <new test class>` only).
- **Per wave merge:** `./gradlew :backend:core:test :backend:api:test :backend:worker:test --tests "*Settings*" --tests "*Knowledge*" --tests "*SafetyNet*" --tests "*VoiceGeneration*"` + `pnpm --filter web test --run features/ai features/knowledge`.
- **Phase gate:** `./gradlew test` + `pnpm --filter web e2e -- ai-settings.spec.ts` + manual VI/EN locale check in browser.

### Wave 0 Gaps

- [ ] `backend/core/src/test/java/com/zeromail/core/chat/usecases/settings/*` directory — none exist yet
- [ ] `backend/core/src/test/java/com/zeromail/core/chat/usecases/AssistantKnowledgeServiceCrudTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/chat/arch/*Phase9*Test.java` (single-call-site ArchUnit suite)
- [ ] `backend/core/src/test/java/com/zeromail/core/chat/usecases/settings/VoiceGenerationFromSentLeakTest.java` — privacy invariant test (the most important new test)
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/settings/*` directory
- [ ] `apps/web/__tests__/features/ai/` (or beside-feature) Vitest specs for the new hooks
- [ ] `apps/web/e2e/ai-settings.spec.ts` Playwright spec

*(All test infrastructure — `PostgresContainerTest`, `ApiPostgresTestBase`, `TestSessionSupport`, Playwright config, Vitest config, lint gates — exists. Only the new test files are missing.)*

## Security Domain

> `security_enforcement: true` in config; ASVS L1. Section included.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Existing Spring Security 7 cookie-session + WebAuthn for admin (Phase 8). Phase 9 only adds user-scoped endpoints behind `isAuthenticated()` |
| V3 Session Management | yes | Existing Spring Session Redis + `HttpOnly,SameSite=Lax,Secure` cookie. No change |
| V4 Access Control | yes | `TenantContext.currentTenantUuid()` mandatory in every new controller; cross-tenant access returns 404 (not 403 — avoids tenant enumeration) |
| V5 Input Validation | yes | Bean Validation on every request DTO; `@Pattern` + `@Size` + closed-enum `@Schema(allowableValues)`; `PersonalizationSanitizer` for prompt-fenced text |
| V6 Cryptography | yes | Reuse `RefreshTokenCipher` / `PlatformSecretCipher` AES-GCM (Phase 8 patterns). No hand-rolled crypto. Never log plaintext BYOK keys |
| V7 Error Handling | yes | Existing `ProblemDetail` mapper in `backend/api/.../error/`. Translate domain errors to closed `code=*` strings; never echo provider error bodies (ARCH-11 for SET-AI-04) |
| V8 Data Protection | yes | Privacy invariant D-11 (in-memory only for generate-from-sent); existing `AdminBodyBanRegex` not applicable to user routes but same spirit applies — never serialize raw bodies |
| V12 Files/Resources | no | Phase 9 uploads no files |
| V13 API/Web Service | yes | OpenAPI codegen pipeline; rate-limit on test-connection + generate-from-sent; CSRF already handled by spring-security |

### Known Threat Patterns for {Java 25 / Spring Boot 4 / Spring AI M7 / Postgres / Next.js 16}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-tenant data leak (SET-VOICE-04, SET-SAFE-01) | Information Disclosure | Every repository query MUST filter by `tenant_id`; every controller pulls tenant from `TenantContext`; cross-tenant access returns 404 (not 403). MultiTenantLeakTest per resource |
| Prompt injection via `personal_instructions` or `writing_style` | Tampering / Elevation | `PersonalizationSanitizer` enforces XML-fence + sentinel removal + length cap; ArchUnit asserts single call site |
| LLM prompt/completion leakage (SET-VOICE-07) | Information Disclosure | Privacy invariant D-11 + sentinel-leak integration test + Spring AI observation property disabled |
| BYOK key disclosure via response body or log | Information Disclosure | AES-GCM at rest; response DTO has no `apiKey` field; no `log.info("...{}", request)` with full request; ARCH-11-style sentinel test |
| Provider allow-list bypass | Tampering | Server-side validation in `SettingsAiService` rejects `useBYOK=true` for openrouter/9router; ArchUnit gate (defense in depth) |
| Test-connection used as port-scan oracle (free /v1/models call on attacker URL) | Spoofing / Resource Exhaustion | `ProviderConnectionTester` only accepts whitelisted base URLs from `master_key.base_url`; doesn't proxy arbitrary URL — verified |
| Rate-limit bypass via clock skew | Denial of Service | Redis-backed rate-limiter (single source of time); not per-instance counter |
| Knowledge-snippet content injection into draft prompt | Tampering | Same sanitizer pattern; cap 8000 chars; XML-fence in prompt assembler |
| 409 → enumeration of other tenants' titles | Information Disclosure | UNIQUE constraint scoped `(tenant_id, title)` — duplicate detection only sees same tenant. SAFE by design |
| OAuth scope expansion required by Gmail send actions | Spoofing | Bundled OAuth from v1.1; no new scopes added in Phase 9 (SET-VOICE-07 uses existing `gmail.readonly`) — verify scope IS already granted; otherwise tenant must re-consent |
| Audit-log injection via safety-net pattern | Tampering | `blocked_by_safety_net_pattern VARCHAR(320)` is sanitized to canonical form before write; log line uses structured fields, not string-concatenated SQL |

## Sources

### Primary (HIGH confidence)

- Project files read directly:
  - `.planning/phases/09-user-settings-ui-on-curated-catalog/09-CONTEXT.md`
  - `.planning/phases/09-user-settings-ui-on-curated-catalog/09-SPEC.md`
  - `.planning/phases/09-user-settings-ui-on-curated-catalog/09-UI-SPEC.md`
  - `.planning/REQUIREMENTS.md`
  - `.planning/ROADMAP.md`
  - `.planning/config.json`
  - `CLAUDE.md`, `apps/web/CLAUDE.md`, `apps/web/AGENTS.md`
  - `CONVENTIONS.md`, `TESTING.md`
  - `backend/core/.../chat/persistence/AssistantSettingsEntity.java`
  - `backend/core/.../chat/persistence/AssistantKnowledgeMemoryEntity.java`
  - `backend/core/.../chat/sanitize/PersonalizationSanitizer.java`
  - `backend/core/.../chat/usecases/AssistantKnowledgeService.java`
  - `backend/core/.../chat/usecases/AssistantMemoryService.java`
  - `backend/core/.../chat/package-info.java`
  - `backend/core/.../triage/persistence/TenantProtectedSenderObservationEntity.java`
  - `backend/core/.../admin/mkey/usecases/MasterKeyAdminService.java`
  - `backend/core/.../admin/mkey/usecases/ModelsProbeClient.java`
  - `backend/core/.../admin/spend/persistence/lowlevel/SpendAggregateReadRepository.java`
  - `backend/core/.../llm/usecases/LlmUsageRecord.java`
  - `backend/api/.../controllers/llm/ByokController.java`
  - `backend/api/.../controllers/triage/SenderSafetyNetController.java`
  - `backend/api/.../controllers/settings/SettingsCatalogController.java`
  - `backend/api/.../controllers/notifications/NotificationPreferencesController.java`
  - `backend/api/.../controllers/tenant/TriagePauseController.java`
  - Liquibase changesets `025`, `028`, `039`, `045`, `046`, `085`
  - `apps/web/app/(protected)/(app)/ai/page.tsx`
  - `apps/web/features/ai/components/AiConfigPage.tsx`
  - `apps/web/features/llm/components/ByokForm.tsx`
  - `apps/web/features/triage/components/SenderSafetyNetList.tsx`
  - `apps/web/app/(protected)/(app)/settings/SettingsClient.tsx`
  - `apps/web/i18n/request.ts`
  - `apps/web/components/ui/*.tsx` (Glob)

### Secondary (MEDIUM confidence)

- Inbox Zero local reference at `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/{settings,knowledge}/` (listed files: `WritingStyleSetting.tsx`, `AboutSetting.tsx`, `DraftConfidenceSetting.tsx`, `DraftKnowledgeSetting.tsx`, `KnowledgeBase.tsx`, `KnowledgeForm.tsx`, `SettingsTab.tsx`). Used to confirm Inbox Zero pattern parity locked in D-01..D-07.

### Tertiary (LOW confidence)

- Spring AI 2.0.0-M7 observation property names (Pitfall 1, Assumption A1) — needs Context7 verification before code lock.

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — every library verified in project code, no installs needed.
- Architecture: HIGH — every reusable asset and call site grounded in grep + file reads.
- Pitfalls: HIGH — every pitfall corresponds to a verified surface (changelog 085 `call_site` CHECK, changelog 039 dropped `triage_shadow_mode`, BYOK allow-list, two `086-*.yaml` files demonstrating the numbering collision).
- Privacy invariants: HIGH — D-11 ban surfaces are listed exhaustively (DB, log, audit) and the test approach is concrete (sentinel-grep).
- SET-AI-03 cost aggregation: MEDIUM — depends on whether `CHAT` value exists in `call_site` enum; planner must verify and add a Liquibase changeset if not.

**Research date:** 2026-05-26
**Valid until:** 2026-06-25 (30 days — stack is stable; only Spring AI M7 → GA is fast-moving)
