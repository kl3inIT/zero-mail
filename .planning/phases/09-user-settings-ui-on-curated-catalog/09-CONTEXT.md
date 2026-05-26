# Phase 9: User Settings UI on Curated Catalog — Context

**Gathered:** 2026-05-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 9 delivers the user-facing `/ai` page: a single-route, flat-section layout (Inbox Zero pattern) where a tenant configures their writing voice, assistant behavior, sender safety net, and a single tenant-wide BYOK card (provider + base URL + API key + model + Active switch) per D-17. Backend exposes typed REST endpoints over the existing `assistant_settings` table (extended with `email_signature`, `tone_preset`, `auto_draft_replies`, `draft_confidence` enum, `sensitive_data_protection`), the existing `assistant_knowledge_snippet` table (extended with `UNIQUE(tenant_id, title)` + `updated_at`), the existing `tenant_protected_sender_observation` table (extended with `pattern_kind` + `created_by_user`), and a new triage-audit column `blocked_by_safety_net_pattern`. SET-VOICE-07 pulls forward the "Generate writing style from recent sent emails" capability from `SET-VOICE-FUT-03` with a strict in-memory privacy invariant.

The phase consumes admin artifacts shipped by Phase 8 (`MKEY` master keys, `CAT` curated catalog, `GET /api/settings/catalog` endpoint) without modifying them. `ByokForm` moves canonically from the legacy `/settings` page to the `AI Provider` section on `/ai`. The legacy `/settings` page is otherwise untouched.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**18 requirements are locked.** Source: `09-SPEC.md` for full Current/Target/Acceptance per requirement; `.planning/REQUIREMENTS.md` for canonical IDs.

Downstream agents (`gsd-phase-researcher`, `gsd-planner`, `gsd-executor`) MUST read `09-SPEC.md` before planning or implementing. Requirements, boundaries, and acceptance criteria are NOT duplicated here.

**Active requirement IDs:** SET-VOICE-01..07, SET-BEHV-01..05, SET-SAFE-01, SET-SAFE-04, SET-AI-01..04.

**Deferred during spec-phase round 1 (2026-05-26):** SET-SAFE-02 (paste-import) and SET-SAFE-03 (per-entry `protect`/`escalate` mode) — every user-added safety-net entry behaves as `protect`.

**Pulled into Phase 9 during discuss-phase (2026-05-26):** SET-VOICE-07 (Generate writing style from recent sent emails) — moved from `SET-VOICE-FUT-03` in REQUIREMENTS.md.

</spec_lock>

<decisions>
## Implementation Decisions

### UI Layout & Edit Pattern (D-01..06) — Inbox Zero alignment

- **D-01:** `/ai` page uses **flat `<SectionHeader>` groups** on a single `/ai/page.tsx` — NOT shadcn `<Tabs>`, NOT query-param tab routing. Section order: `Your voice`, `Behavior`, `Updates`, `Safety net`, `AI Provider`. This supersedes the original SPEC v1 directive of "four query-param-driven tabs" — ROADMAP success criterion 1 also updated.
- **D-02:** Every multi-field setting uses the `SettingCard` (title + description + Edit/Set button) → shadcn `Dialog` edit pattern. Mirrors Inbox Zero's `WritingStyleSetting.tsx` / `AboutSetting.tsx`. NOT inline edit.
- **D-03:** Short toggles (`auto_draft_replies`, `daily_digest`, `sensitive_data_protection`, `shadow_mode`) render INLINE as shadcn `<Switch>` on the `SettingCard` body. No Dialog needed for boolean fields.
- **D-04:** Knowledge snippets render as a shadcn `<Table>` (Title | Last Updated | Edit | Delete) with `+ Add` button opening a Dialog containing `KnowledgeForm`. Edit on a row opens the same Dialog prefilled. Delete uses `ConfirmDialog`. Mirrors Inbox Zero's `KnowledgeBase.tsx`.
- **D-05:** Backend adds `UNIQUE(tenant_id, title)` constraint on `assistant_knowledge_snippet` + `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` column with auto-touch on update. List ordering is `updated_at DESC` (matches Inbox Zero's `formatDateSimple(updatedAt)` column). Mirrors Inbox Zero's `Knowledge` Prisma model `@@unique([emailAccountId, title])`.
- **D-06:** **Deviation from Inbox Zero (LOCKED):** BYOK stays on `/ai` inside the `AI Provider` section — NOT split to `/settings` as Inbox Zero does. Reason: Zero Mail is single-tenant-per-user; Inbox Zero split because they have multi-email-account-per-user where BYOK is shared across accounts. We don't have that dilemma. Keep "config AI provider" as one flow. **Updated 2026-05-26 during plan-phase** — the original wording mentioned "per-feature model picker"; that picker has since been removed (see D-08 + D-17). The deviation now applies to BYOK only.

### Draft Confidence Encoding (D-07)

- **D-07:** `SET-BEHV-02` exposes draft confidence as a `LOW | MEDIUM | HIGH` enum via shadcn `<Select>`, NOT a 0.0–1.0 slider. Backend stores enum in `assistant_settings.draft_confidence VARCHAR(8)` and maps to internal numeric thresholds (`LOW=0.50, MEDIUM=0.70, HIGH=0.85`) when calling the draft worker. Reason: matches Inbox Zero's `DraftReplyConfidence` enum; user-friendlier than picking 0.62 vs 0.68; future tuning means changing one constant, not migrating user values.

### REST Endpoint Granularity (D-08)

- **D-08 (revised 2026-05-26 round 2):** Backend exposes feature-scoped PUTs: `PUT /api/settings/voice`, `PUT /api/settings/behavior`. **`PUT /api/settings/ai` is REMOVED** — the AI Provider section has no `assistant_settings`-bound fields. BYOK is its own resource: `GET /api/byok` (load row), `POST /api/byok` (save provider + base_url + api_key + optional model_id), `PUT /api/byok/active` (`{active: bool}` toggle), `DELETE /api/byok` (remove row entirely), `POST /api/byok/test-connection` (test stored OR inline-payload key — see D-17). Sub-resources stay separate: `GET/POST/PUT/DELETE /api/knowledge-snippets`, the existing `/api/triage/sender-safety-net/*` family extended with DELETE, `GET /api/settings/ai/cost?window=7d` (returns `{usd: number}` single value), `POST /api/settings/voice/generate-from-sent`. Reason: matches resource boundary (BYOK ≠ assistant settings — different table, different lifecycle); idiomatic for Spring controllers + Jakarta Bean Validation + openapi-typescript codegen; clean TanStack Query keys per section; consistent with Phase 8 `MasterKeyAdminService` shape.

### Tone Preset CUSTOM Semantics (D-09)

- **D-09:** When `tone_preset = 'CUSTOM'`, the system prompt for downstream chat/triage/draft uses ONLY `writing_style` (no preset descriptor). For any other preset value (PROFESSIONAL/FRIENDLY/CASUAL/FORMAL), both preset and writing_style are passed to the prompt assembler. NO new `custom_tone_description` column — avoid duplication; `writing_style` is the single source of truth for free-text tone customization.

### Writing-style "Generate from sent emails" (D-10..D-12) — SET-VOICE-07

- **D-10:** New endpoint `POST /api/settings/voice/generate-from-sent` (request: `{ sampleSize: number }` default 20, max 50) returns `{ generatedStyle: string }` (≤ 500 words). Implementation: Gmail API `users.messages.list` filter `in:sent` + `users.messages.get` for body extraction, all in-memory; LLM call via existing Spring AI gateway with a style-extraction prompt; result is the user-reviewable style guide that pre-populates the textarea.
- **D-11:** **Privacy invariant (LOCKED):** raw email bodies, the LLM prompt, and the LLM completion exchange MUST be in-memory-only — no DB row, no log line, no audit entry. Only the user-reviewed-and-saved style guide is persisted (via a subsequent `PUT /api/settings/voice` write to `assistant_settings.writing_style`). ArchUnit / integration test asserts no `prompt`/`completion`/`body` field is written by the generate path. Aligns with the existing project privacy constraint that bans long-term storage of raw email bodies and LLM exchanges for the email-content pipeline.
- **D-12:** Rate-limited to 3 generations per hour per tenant. UI Dialog button shows loading state; on success the textarea is populated with the LLM result but Save is NOT auto-clicked (user must review and click Save explicitly). On 0 sent messages → HTTP 200 + `{ generatedStyle: "" }` + empty-state UI message. On LLM error → existing writing_style unchanged + inline error toast.

### Spring Modulith Boundary (D-13)

- **D-13:** Phase 9 settings, voice, knowledge, and behavior code stays inside `core.chat` module. New sub-package `core.chat.settings` (REST surface DTOs/controllers/use-case services) is added; `AssistantSettingsEntity`, `AssistantKnowledgeMemoryEntity`, `AssistantMemoryService` remain at their current `core.chat.persistence` / `core.chat.usecases` location. Safety net (`tenant_protected_sender_observation` + triage audit column) stays inside `core.triage`. NO new top-level module (`core.tenant.settings`, `core.usersettings`) — those would force migrating four entities + updating draft/rule worker imports for zero net architectural value in v1.2. Modulith named-interface `core.chat::settings-api` declares the boundary for outside callers.

### Test-Connection Reuse (D-14)

- **D-14:** Extract a shared `ProviderConnectionTester` service in `core.llm.gateway.springai` from Phase 8 admin MKEY-03 logic. Admin MKEY endpoint is refactored to delegate to the service (Phase 8 sentinel-leak test ARCH-11 remains green via the shared scrub). User-side `POST /api/settings/ai/test-connection` is a thin wrapper that (1) loads the tenant's single stored BYOK from `user_byok_key`, (2) enforces a per-tenant 10/hour rate-limit, then (3) delegates. Reason: avoid sentinel-leak drift between two paths; single source of truth for the enum response shape `{OK | INVALID_KEY | RATE_LIMITED | NETWORK_ERROR | TIMEOUT}` (verified 2026-05-26 round 2 against the actual `MasterKeyTestResult.java` source — earlier draft wording mentioning `UNSUPPORTED` was incorrect and is retracted); per-tenant vs per-admin rate-limit isolation handled at the wrapper layer. ArchUnit test asserts both endpoints reach `ProviderConnectionTester.probeConnection`.

### Reused v1.0 Endpoints (D-15)

- **D-15:** Daily-digest toggle (`SET-BEHV-03`) and shadow-mode toggle (`SET-BEHV-05`) reuse the existing v1.0 `ANL-03` and `TRG-07` endpoints respectively. NO new column added to `assistant_settings` for either. Phase 9 only renders shadcn `<Switch>` bound to the existing endpoints via existing hooks (`useToggleTriagePause` / `useTriagePauseState` for TRG-07; equivalent for ANL-03).

### ByokForm Migration (D-16)

- **D-16:** `ByokForm` is removed from `apps/web/app/(protected)/(app)/settings/SettingsClient.tsx` and rendered exactly once inside the `AI Provider` section on `/ai`. Existing `ByokController` + AES-GCM cipher are reused. The legacy `/settings` page keeps all other cards (Account / Language / Gmail / Notifications / Delete) untouched.

### AI Provider Shape — single BYOK card with Active switch + base URL + model picker (D-17, revised 2026-05-26 during plan-phase round 2)

- **D-17 (revised):** The `AI Provider` section is ONE BYOK card. There is NO separate `Platform default ↔ Use my key` mode card and NO per-feature picker for Chat/Triage/Draft. The card contains, top-to-bottom: Provider `<Select>` · Base URL `<Input>` (OpenAI-compatible mode — auto-filled per provider, user-editable) · API key `<Input type="password">` · Model `<Select>` (populated from the provider's `/v1/models` response after Test connection) · Active `<Switch>` (default OFF) · `Kiểm tra kết nối` button · `Lưu` button. Persistence: ONE new table `user_byok_key (tenant_id PK, provider VARCHAR(16), base_url VARCHAR(255), api_key_ciphertext BYTEA, api_key_iv BYTEA, model_id VARCHAR(64) NULL, active BOOLEAN NOT NULL DEFAULT FALSE, last_test_result VARCHAR(16) NULL, last_tested_at TIMESTAMPTZ NULL)`. NO `assistant_settings.ai_provider_mode` column — the `active` flag on the BYOK row IS the on/off switch. Exactly one row per tenant — saving a new provider replaces the previous row.

  **Resolution rule (used by every feature: chat, triage, draft, voice-generate):**
  1. If `user_byok_key` row exists AND `active = TRUE` AND `model_id IS NOT NULL` → use the BYOK row (decrypt key, call `{base_url}` with `model_id`).
  2. Otherwise → use the admin-curated catalog's platform default for that feature.

  **Provider allow-list (unchanged):** OpenAI / Anthropic / Google / DeepSeek only. Server-side reject for `openrouter` / `9router` with `code=ai.byok.provider_not_allowed`.

  **Activating without a tested model:** `PUT /api/byok/active {active: true}` rejects with HTTP 400 `code=ai.byok.no_model_picked` if `model_id IS NULL` or `last_test_result <> 'OK'`. UI mirrors this — the Active `<Switch>` is disabled until both conditions are satisfied; tooltip reads `Hãy chọn model và kiểm tra kết nối trước` / `Pick a model and test the connection first`.

  **Test connection contract:** `POST /api/byok/test-connection` (no body — uses the stored row OR an in-memory not-yet-saved row passed inline as `{provider, baseUrl, apiKey}` for first-test-before-save). Response: `{result: 'OK' | 'INVALID_KEY' | 'RATE_LIMITED' | 'NETWORK_ERROR' | 'TIMEOUT', models?: string[]}`. When `result='OK'`, `models[]` lists the provider's `/v1/models` IDs filtered to chat-completion-capable models. Other results omit `models`. Same enum and same `ProviderConnectionTester` as admin MKEY-03 (D-14). Rate-limited to 10 tests/hour per tenant.

  **Reason for the revised shape:** user directive 2026-05-26 round 2 ("k cần cái card này chỉ cần bật tắt key … do người dùng chọn model từ key và cho người dùng test kiểu compatible openai anthropic theo url"). Mirrors admin's MKEY shape (provider + base URL + key + model + active flag) so the `ProviderConnectionTester` extraction in D-14 stays a clean shared dependency. Eliminates the `Chế độ` card, eliminates `assistant_settings.ai_provider_mode`, eliminates the resolved-provider helper list, and eliminates any per-feature surface. Helper text under the card simply reads `Khi bật, tất cả tính năng AI (Chat, Triage, Draft) sẽ chạy bằng key của bạn.` / `When enabled, all AI features (Chat, Triage, Draft) run on your key.`

</decisions>

<canonical_refs>
## Canonical References (downstream agents MUST read)

| Path | Why it matters |
|---|---|
| `.planning/phases/09-user-settings-ui-on-curated-catalog/09-SPEC.md` | Locked requirements — MUST read before planning. 18 requirements with Current/Target/Acceptance each. |
| `.planning/REQUIREMENTS.md` | Canonical `SET-VOICE-*`, `SET-BEHV-*`, `SET-SAFE-*`, `SET-AI-*` requirement IDs and the Out-of-Scope row. |
| `.planning/ROADMAP.md` (Phase 9 entry) | Updated success criteria reflecting Inbox Zero alignment. |
| `.planning/phases/08-admin-console-operator-tooling/08-CONTEXT.md` | Phase 8 catalog schema (`provider_catalog`, `model_catalog`, `feature_binding`), `GET /api/settings/catalog` ETag contract, `RefreshTokenCipher` AES-GCM reuse, `ProviderMasterKeyResolver` confinement, `MasterKeySentinelLeakTest` ARCH-11. |
| `.planning/phases/08.1-inbox-zero-style-rule-actions-and-admin-managed-examples-cat/08.1-CONTEXT.md` | `Auto-send rules` toggle that already lives on `/ai` (stays untouched by Phase 9). |
| `CLAUDE.md` | Privacy invariant for the email-content pipeline; backend code-style rules; outbound-gateway boundary. |
| `apps/web/CLAUDE.md` (`@AGENTS.md`) | `schema.d.ts` is generated — NEVER hand-edit; shadcn primitive rule; TanStack Query callback meta pattern; no hardcoded color hex. |
| `apps/web/app/(protected)/(app)/ai/page.tsx` + `apps/web/features/ai/components/AiConfigPage.tsx` | Current `/ai` page (Auto-send toggle + sender input + SenderSafetyNetList) — Phase 9 restructures around these without removing them. |
| `apps/web/app/(protected)/(app)/settings/SettingsClient.tsx` | Legacy `/settings` page from which `ByokForm` is removed in Phase 9. |
| `backend/core/src/main/java/com/zeromail/core/chat/persistence/AssistantSettingsEntity.java` | Existing entity to extend with `email_signature`, `tone_preset`, `auto_draft_replies`, `draft_confidence`, `sensitive_data_protection`. |
| `backend/core/src/main/java/com/zeromail/core/chat/persistence/AssistantKnowledgeMemoryEntity.java` + repo | Existing entity to extend with `UNIQUE(tenant_id, title)` + `updated_at`. |
| `backend/core/src/main/java/com/zeromail/core/chat/sanitize/PersonalizationSanitizer.java` | Existing sanitizer for `personal_instructions` — REST and chat-tool paths MUST share this single instance. |
| `backend/api/src/main/java/com/zeromail/api/controllers/settings/SettingsCatalogController.java` | Existing `GET /api/settings/catalog` ETag pattern that Phase 9 client must consume. |
| `backend/api/src/main/java/com/zeromail/api/controllers/triage/SenderSafetyNetController.java` + `SenderSafetyNetService` | Existing safety net endpoints to extend with DELETE + domain-pattern support. |
| `backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java` + `apps/web/features/llm/components/ByokForm.tsx` | Existing BYOK surface that moves canonically to `/ai`. |
| `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/settings/SettingsTab.tsx` | Inbox Zero flat-section layout reference (LOCKED pattern). |
| `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/settings/WritingStyleSetting.tsx` | Inbox Zero `SettingCard` + Dialog edit reference (LOCKED pattern). |
| `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/knowledge/KnowledgeBase.tsx` + `KnowledgeForm.tsx` | Inbox Zero Knowledge Table + Dialog reference (LOCKED pattern). |
| `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/settings/DraftConfidenceSetting.tsx` | Inbox Zero enum-select for `DraftReplyConfidence` (LOCKED pattern). |
| `../inbox-zero/apps/web/prisma/schema.prisma` (`model Knowledge`) | Inbox Zero `@@unique([emailAccountId, title])` shape — Phase 9 mirrors with `UNIQUE(tenant_id, title)`. |

</canonical_refs>

<code_context>
## Reusable Assets & Patterns

**Backend:**
- `AssistantSettingsEntity` already has `personal_instructions`, `writing_style`, `provider_id`, `chat_model_id`, `triage_model_id`, `draft_model_id`, `ai_output_language`. Phase 9 adds 5 columns + a Liquibase changelog.
- `AssistantKnowledgeMemoryEntity` already exists; Phase 9 adds `UNIQUE(tenant_id, title)` + `updated_at` + REST CRUD endpoints.
- `AssistantMemoryService.requireBoundedText` is reusable for length/control-char normalization.
- `PersonalizationSanitizer` already enforces XML-fence + injection sentinel removal + 2000-char cap.
- `RefreshTokenCipher` (AES-GCM at-rest) reused by Phase 9 BYOK saves — already in scope.
- `ByokController` works today; Phase 9 only moves the FE component.
- `SenderSafetyNetController` exposes GET list + POST opt-in; Phase 9 extends with DELETE + domain-pattern parsing in `SenderEmailCanonicalizer`.
- `RuleAutomationSettingsService` drives the existing `Auto-send rules` toggle on `/ai` — Phase 9 leaves this untouched and renders it inside one of the IZ-pattern sections.
- `SettingsCatalogController` `GET /api/settings/catalog` ETag contract is the Phase 9 client's source for the Provider/Model picker.

**Frontend:**
- `apps/web/features/ai/` already exists with `messages.ts` + `components/AiConfigPage.tsx`. Phase 9 grows this folder: `components/{YourVoiceSection,BehaviorSection,UpdatesSection,SafetyNetSection,AiProviderSection}.tsx`, plus per-setting Dialog forms (`WritingStyleDialog`, `PersonalInstructionsDialog`, etc.). Knowledge subfeature lives at `apps/web/features/knowledge/` (new), mirroring Inbox Zero folder split.
- `apps/web/features/llm/` (ByokForm + hooks) is reused; only the import location changes.
- `apps/web/features/triage/components/SenderSafetyNetList.tsx` is the current safety-net UI; Phase 9 lifts it into the `Safety net` section + adds delete + domain-pattern support.
- TanStack Query callback patterns: every new mutation uses `meta.successMessage`/`meta.errorMessage` per `apps/web/CLAUDE.md`; no local `toast.success/error` calls.
- shadcn primitives required: `Card`, `Dialog`, `Form`, `Input`, `Textarea`, `Select`, `Switch`, `Slider` (NOT used — replaced by Select for confidence), `Table`, `Badge`, `RadioGroup`, `ConfirmDialog` (composed from shadcn `Dialog`), `Button`. Install any missing via `pnpm dlx shadcn@latest add <component>` from `apps/web`.
- HTML prototype: Phase 9's `/gsd-ui-phase` run MUST emit `.planning/phases/09-user-settings-ui-on-curated-catalog/09-PROTOTYPE.html` (per project CLAUDE.md UI Phase Prototype Rule).

**OpenAPI codegen flow:**
- After backend DTO additions/changes, boot the backend → run `pnpm --filter web run generate:api` → commit the regenerated `apps/web/lib/api/schema.d.ts` and `apps/web/openapi/zero-mail-spec.json`. NEVER hand-edit.

</code_context>

<deferred>
## Deferred Ideas (captured during discuss-phase 2026-05-26)

None this round. The "Generate from recent sent emails" feature was originally on the deferred list (`SET-VOICE-FUT-03`) but was pulled into scope as `SET-VOICE-07` during discuss-phase. No new deferred items were surfaced.

`SET-SAFE-02` (paste-import) and `SET-SAFE-03` (per-entry `protect`/`escalate` mode) remain deferred to v1.3 per the spec-phase round-1 scope decision.

</deferred>

---

## Next Steps

`/clear` then:

`/gsd:plan-phase 9`

The phase has an explicit UI surface (Inbox Zero pattern alignment, 5 sections, knowledge Table+Dialog) — strongly consider:

`/gsd:ui-phase 9`

to generate `09-UI-SPEC.md` + `09-PROTOTYPE.html` before planning. UI phase will produce the design contract that the planner uses to budget UI-build tasks.

---

*Phase: 09-user-settings-ui-on-curated-catalog*
*Context gathered: 2026-05-26*
*Decisions locked: 16 (D-01..D-16)*
*Next: `/gsd:ui-phase 9` (recommended) → `/gsd:plan-phase 9`*
