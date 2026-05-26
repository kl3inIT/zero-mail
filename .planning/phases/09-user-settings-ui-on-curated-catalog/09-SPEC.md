# Phase 9: User Settings UI on Curated Catalog — Specification

**Created:** 2026-05-26
**Updated:** 2026-05-26 (Inbox Zero pattern alignment during discuss-phase)
**Ambiguity score:** 0.169 (gate: ≤ 0.20)
**Requirements:** 17 locked (SET-VOICE-01..06, SET-BEHV-01..05, SET-SAFE-01, SET-SAFE-04, SET-AI-01..04). **SET-SAFE-02 and SET-SAFE-03 deferred to v1.3 per round-1 scope decision.**

## Goal

A user can open `/ai` and configure writing voice, assistant behavior, sender safety net, and per-feature AI provider/model across **flat sections grouped by `<SectionHeader>`** (Inbox Zero pattern) — backed by the admin-curated catalog and BYOK only for the four user-allowed providers (OpenAI, Anthropic, Google, DeepSeek).

## Background

Phase 8 shipped admin master-keys + curated catalog (`MKEY-*`, `CAT-*`) and the `GET /api/settings/catalog` endpoint. Today the user-facing `/settings` route (`apps/web/app/(protected)/(app)/settings/page.tsx` → `SettingsClient.tsx`) is a flat one-page surface mixing Account, Language, Gmail connection, BYOK, Notifications, triage pause toggle, and Delete account; there is no tabbed structure and no per-feature provider/model picker fed by the admin catalog. The `/ai` route (`apps/web/app/(protected)/(app)/ai/page.tsx` → `features/ai/components/AiConfigPage.tsx`) already exists but only renders the `Auto-send rules` toggle plus the existing `SenderSafetyNetList` and an add-sender form.

Backend reality scouted before this spec:

- `assistant_settings` table already has `personal_instructions`, `writing_style`, `provider_id`, `chat_model_id`, `triage_model_id`, `draft_model_id`, `ai_output_language`. **Missing columns** for Phase 9: `email_signature`, `tone_preset`, `auto_draft_replies`, `draft_confidence_threshold`, `sensitive_data_protection`.
- `assistant_knowledge_snippet` table + `AssistantKnowledgeMemoryEntity` exist (currently only written via the `ADD_TO_KNOWLEDGE_BASE` chat tool) — **reused by SET-VOICE-04 instead of creating a new table.**
- `PersonalizationSanitizer` (XML-fence + prompt-injection sanitization, 2000-char cap) already exists for `personal_instructions` and is reused as-is.
- `SettingsCatalogController` at `GET /api/settings/catalog` returns the admin-curated per-feature × per-provider matrix with ETag caching.
- `ByokController` + `ByokForm.tsx` exist but currently live on the legacy `/settings` page; Phase 9 moves the canonical surface to `/ai?tab=provider`.
- `SenderSafetyNetController` exposes `GET /api/triage/sender-safety-net` + `POST .../{senderEmail}/opt-in` against the existing `tenant_protected_sender_observation` table. **Missing**: DELETE endpoint and domain-pattern support. **Out of scope**: `mode` column (`protect` vs `escalate`) and paste-import — both deferred per round-1 scope decision.
- `RuleAutomationSettingsService` already drives the `Auto-send rules` toggle on the existing `/ai` page; Phase 9 keeps that toggle in Tab C (Safety Net).
- v1.0 daily-digest (`ANL-03`) and shadow-mode (`TRG-07`) toggles have their own endpoints. **Phase 9 reuses those endpoints unchanged** — no migration into `assistant_settings`.
- `llm_call_audit` table already records per-call cost — `SET-AI-03` cost estimate is a SUM over the last 7 days grouped by feature.

The phase delivers the two-page split confirmed during the spec interview: `/ai` becomes the canonical AI-configuration page (this phase); `/settings` keeps the legacy Account / Language / Gmail / Notifications / Delete cards untouched, except that `ByokForm` is removed from `/settings` and now lives canonically inside `/ai`.

**Inbox Zero pattern lock (added during discuss-phase 2026-05-26).** After scouting `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/settings/SettingsTab.tsx` and related files, the page layout and edit pattern are pivoted onto the Inbox Zero shape: flat `<SectionHeader>` groups instead of shadcn `<Tabs>`; `SettingCard` (title + description + Edit/Set button) opening a shadcn `Dialog` for each setting instead of inline edit; Knowledge as a `<Table>` (Title | Last Updated | Edit/Delete) with `+ Add` opening a Dialog containing `KnowledgeForm` (mirrors `KnowledgeBase.tsx` / `KnowledgeForm.tsx`); `SET-BEHV-02` confidence exposed as an enum `LOW | MEDIUM | HIGH` `<Select>` instead of a 0.0–1.0 slider, with the backend mapping the enum to internal numeric thresholds (`LOW=0.50, MEDIUM=0.70, HIGH=0.85`). One deviation from Inbox Zero is locked: BYOK + per-feature model picker stays on `/ai` (not split to `/settings`) because Zero Mail is single-tenant-per-user and BYOK + model picker are configured in one flow.

## Requirements

### Section `Your voice` (SET-VOICE-01..06)

1. **Writing style**: User can edit a free-text writing-style description (200–500 words) that the AI uses to shape draft tone.
   - Current: `assistant_settings.writing_style` column exists; no REST endpoint exposes it; no UI surface
   - Target: `PUT /api/settings/voice` accepts `writingStyle` field; section renders a `SettingCard` with an Edit button opening a Dialog with a textarea + live word counter; 200-word minimum + 500-word maximum enforced server-side
   - Acceptance: a save below 200 words returns HTTP 400 with `code=voice.writing_style.too_short`; a save above 500 words returns HTTP 400 with `code=voice.writing_style.too_long`; a save inside the range returns 200 and persists the text

2. **Personal instructions ("About me")**: User can edit free-text personal instructions injected into the system prompt for chat/triage/draft.
   - Current: `assistant_settings.personal_instructions` column exists; `UPDATE_PERSONAL_INSTRUCTIONS` chat tool exists; no REST endpoint for direct UI edit; `PersonalizationSanitizer` already enforces XML-fence + prompt-injection sentinel removal + 2000-char cap
   - Target: `PUT /api/settings/voice` accepts `personalInstructions` field; the same `PersonalizationSanitizer` is invoked before persistence (no duplicate sanitizer); `SettingCard` opens a Dialog with a textarea + 2000-char counter
   - Acceptance: a save exceeding 2000 chars after sanitization returns HTTP 400 with `code=voice.personal_instructions.too_long`; a save containing a known injection sentinel is sanitized and the persisted value contains no sentinel; chat tool path and REST path both call the same sanitizer (verified by ArchUnit/test)

3. **Email signature**: User can edit a free-text signature that the AI appends to drafts.
   - Current: no `email_signature` column on `assistant_settings`; no UI
   - Target: Liquibase changelog adds `email_signature TEXT` to `assistant_settings`; `PUT /api/settings/voice` accepts `emailSignature`; `SettingCard` opens a Dialog with a textarea + 500-char cap
   - Acceptance: after saving a signature, the next AI-generated draft contains the signature verbatim at the end (integration test against a stubbed `ChatModel`); a save exceeding 500 chars returns 400

4. **Knowledge-base snippets**: User can manage titled knowledge snippets that the AI consults when drafting.
   - Current: `assistant_knowledge_snippet` table exists (`title VARCHAR(120)`, `content TEXT`); only written via `ADD_TO_KNOWLEDGE_BASE` chat tool; no list/edit/delete API; no UI; no uniqueness constraint on title
   - Target: Liquibase changelog adds `UNIQUE (tenant_id, title)` constraint and `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` column to `assistant_knowledge_snippet` (with auto-update trigger or service-layer touch); `GET /api/knowledge-snippets`, `POST /api/knowledge-snippets`, `PUT /api/knowledge-snippets/{id}`, `DELETE /api/knowledge-snippets/{id}` REST endpoints using the existing entity + repo; the `Knowledge` section renders a shadcn `<Table>` (Title | Last Updated | Edit | Delete) with a `+ Add` button opening a Dialog containing `KnowledgeForm`; click Edit on a row opens the same Dialog prefilled; delete uses `ConfirmDialog`; ordering is `updatedAt DESC`; `ADD_TO_KNOWLEDGE_BASE` chat tool and REST POST share the same persistence path
   - Acceptance: a `POST` with title > 120 chars returns 400; a `POST` with a duplicate `(tenant_id, title)` returns HTTP 409 with `code=knowledge.title.duplicate`; a `DELETE {id}` for another tenant's snippet returns 404 (tenant isolation check); listing returns only the current tenant's snippets ordered by `updated_at DESC`

5. **Tone preset**: User can pick a tone preset (professional / friendly / casual / formal / custom).
   - Current: no `tone_preset` column on `assistant_settings`; no UI
   - Target: Liquibase changelog adds `tone_preset VARCHAR(16)` with CHECK constraint to `('PROFESSIONAL','FRIENDLY','CASUAL','FORMAL','CUSTOM')`; `PUT /api/settings/voice` accepts `tonePreset`; `SettingCard` opens a Dialog with a shadcn `<Select>` of the five options
   - Acceptance: a save with a value outside the enum returns 400 with `code=voice.tone_preset.invalid`; the preset is reflected in the drafts pipeline (system prompt contains the chosen tone descriptor)

6. **AI output language**: User can pick AI output language independent of UI language.
   - Current: `assistant_settings.ai_output_language` column exists; no UI surface
   - Target: `PUT /api/settings/voice` accepts `aiOutputLanguage` (`'vi'` default, `'en'`); `SettingCard` opens a Dialog with a radio group; allowed values enforced server-side
   - Acceptance: a save with value not in `('vi','en')` returns 400; chat completions issued after the change use the chosen language even when UI language differs (integration test)

### Section `Behavior` (SET-BEHV-01..05)

7. **Auto-draft replies toggle**: User can toggle the master switch for v1.0 background draft replies (`DRFT-01..04`).
   - Current: no `auto_draft_replies` column; `DRFT-*` workers always run when feature enabled at platform level
   - Target: Liquibase changelog adds `auto_draft_replies BOOLEAN NOT NULL DEFAULT TRUE` to `assistant_settings`; `PUT /api/settings/behavior` accepts the toggle; `SettingCard` renders an inline shadcn `<Switch>` (toggle is short enough to skip the Dialog pattern); v1.0 draft worker reads this flag and short-circuits when `FALSE`
   - Acceptance: when toggled OFF, no new `draft` rows are written for incoming messages during a triage run (integration test against the worker); when toggled ON, drafts resume

8. **Draft confidence (enum)**: User can pick draft confidence as one of `LOW | MEDIUM | HIGH`; AI only saves drafts at or above the mapped internal threshold.
   - Current: no threshold column; draft worker has a hard-coded internal default
   - Target: Liquibase changelog adds `draft_confidence VARCHAR(8) NOT NULL DEFAULT 'MEDIUM' CHECK (draft_confidence IN ('LOW','MEDIUM','HIGH'))` to `assistant_settings`; `PUT /api/settings/behavior` accepts `draftConfidence` enum; `SettingCard` opens a Dialog with a shadcn `<Select>` of the three options + a one-line explanation per option; backend maps the enum to internal numeric thresholds (`LOW=0.50`, `MEDIUM=0.70`, `HIGH=0.85`) when calling the draft worker
   - Acceptance: a save with a value outside the enum returns 400 with `code=behavior.draft_confidence.invalid`; the worker reads the per-tenant enum, resolves to the mapped threshold, and skips draft persistence when `confidence < threshold` (integration test asserts the threshold for each enum value)

9. **Daily digest toggle**: User can toggle daily digest from the `Updates` section (reusing v1.0 `ANL-03` config).
   - Current: `ANL-03` toggle exists with its own endpoint and table; no surface on `/ai`
   - Target: `SettingCard` renders an inline shadcn `<Switch>` bound to the existing `ANL-03` endpoint; NO new column on `assistant_settings`
   - Acceptance: toggling the switch persists through the existing `ANL-03` endpoint and reflects on the v1.0 analytics page (round-trip verification test)

10. **Sensitive-data protection toggle**: User can toggle PII redaction (default ON).
    - Current: v1.0 `LLM-05` PII redaction runs unconditionally; no per-tenant override
    - Target: Liquibase changelog adds `sensitive_data_protection BOOLEAN NOT NULL DEFAULT TRUE` to `assistant_settings`; `PUT /api/settings/behavior` accepts the toggle; `SettingCard` renders an inline `<Switch>`; `LLM-05` redactor reads the flag and skips redaction only when explicitly OFF
    - Acceptance: a tenant with the toggle ON has PII tokens stripped in outbound LLM prompts (verified by snapshot test); a tenant with it OFF retains tokens; default for new tenants is TRUE

11. **Shadow-mode toggle**: User can surface and toggle the v1.0 `TRG-07` shadow-mode flag.
    - Current: shadow-mode toggle exists in v1.0 `TRG-07` with its own endpoint and triage-pause-state path (`useToggleTriagePause` / `useTriagePauseState` hooks already exist)
    - Target: `SettingCard` renders an inline shadcn `<Switch>` bound to the existing v1.0 toggle endpoint; NO new column on `assistant_settings`
    - Acceptance: toggling persists through the existing v1.0 endpoint and the triage worker enters shadow mode on the next message (integration test against the worker)

### Section `Safety net` (SET-SAFE-01, SET-SAFE-04 only; SAFE-02 and SAFE-03 deferred)

12. **Sender safety net CRUD with domain pattern**: User can view, add, and remove sender entries (single email OR domain pattern like `@acme.com`).
    - Current: `tenant_protected_sender_observation` table holds `sender_email` + observation counters; `GET /api/triage/sender-safety-net` and `POST .../{senderEmail}/opt-in` exist; no DELETE endpoint; no domain-pattern support
    - Target: Liquibase changelog adds `pattern_kind VARCHAR(8) NOT NULL DEFAULT 'EMAIL' CHECK (pattern_kind IN ('EMAIL','DOMAIN'))` and `created_by_user BOOLEAN NOT NULL DEFAULT FALSE`; backfill `pattern_kind='EMAIL'` for existing rows; new `DELETE /api/triage/sender-safety-net/{id}` endpoint (only entries with `created_by_user=TRUE` are user-deletable); existing POST opt-in accepts both `ceo@acme.com` (EMAIL) and `@acme.com` (DOMAIN) and sets `created_by_user=TRUE`; the section renders a shadcn `<Table>` (Pattern | Added | Delete) with an inline add input + `+ Add` button (no Dialog needed — single-field input)
    - Acceptance: a POST with `@acme.com` persists `pattern_kind='DOMAIN'`; a DELETE of an observation-created entry (`created_by_user=FALSE`) returns 403 with `code=safety_net.observation_not_deletable`; the triage matcher matches a DOMAIN entry against any sender whose email ends with that domain (integration test)

13. **Audit-log indicator for safety-net block**: User sees a visual indicator in the triage audit log when a rule was blocked by the safety net.
    - Current: triage audit rows show action + rule + outcome; no field distinguishes "blocked by safety net"
    - Target: the existing audit row carries a `blocked_by_safety_net_pattern VARCHAR(320) NULL` column (Liquibase changelog); when set, `AuditRow.tsx` / `AuditCardList.tsx` renders a `<Badge variant="warning">` reading "Blocked by safety net for {pattern}" with the v1.0 `useTriageAuditLog` payload already exposing the field
    - Acceptance: an audit row whose execution was blocked by a `protect` entry has the field populated; the UI renders the badge with the pattern; rows without the field render unchanged; integration test against the triage worker confirms the field is set when a matching `protect` entry exists

### Section `AI Provider` (SET-AI-01..04)

14. **Per-feature provider+model picker from curated catalog**: User can pick provider + model per feature (chat / triage / draft) from `GET /api/settings/catalog`.
    - Current: `GET /api/settings/catalog` exists; `assistant_settings.{chat,triage,draft}_model_id` columns exist; UI has no picker
    - Target: the section renders three rows (Chat / Triage / Draft); each row shows provider `<Select>` + model `<Select>` populated from the catalog filtered by feature; the provider list shows the platform default + only the 4 BYOK-eligible providers (OpenAI, Anthropic, Google, DeepSeek) when the user has a valid BYOK; OpenRouter and 9Router appear only as platform-default labels and never as user-selectable BYOK; `PUT /api/settings/ai` accepts `{feature, providerId, modelId, useBYOK}` per feature
    - Acceptance: catalog returns 4 providers and a chat picker shows exactly those 4 + platform default; selecting a model not in the catalog returns HTTP 400 with `code=ai.model.not_in_catalog`; OpenRouter and 9Router are never present in the `useBYOK=true` payload (response-shape test)

15. **BYOK key entry for 4 providers**: User can save a BYOK key per allowed provider (OpenAI / Anthropic / Google / DeepSeek).
    - Current: `ByokController` + `ByokForm.tsx` exist on legacy `/settings`; AES-GCM at-rest encryption via v1.0 `LLM-04` works
    - Target: `ByokForm` moves canonically to the `AI Provider` section on `/ai`; the form is removed from `SettingsClient.tsx`; provider list locked to the 4 allowed (OpenRouter and 9Router are NOT shown as BYOK options); the existing AES-GCM cipher is reused; no plaintext echo to frontend after save
    - Acceptance: rendering `SettingsClient.tsx` after this phase contains no `ByokForm` reference; rendering the `AI Provider` section on `/ai` contains exactly one `ByokForm`; a BYOK save for `openrouter` or `9router` is rejected server-side with HTTP 400 `code=ai.byok.provider_not_allowed`; the response payload never contains the plaintext key

16. **Per-feature "Use platform default" toggle + cost estimate helper**: User can toggle "Use platform default" vs "Use my key" independently per feature, with last-7d cost estimate visible next to each picker.
    - Current: no per-feature toggle; no cost helper
    - Target: each of the three feature rows (Chat / Triage / Draft) has a switch `Use platform default ↔ Use my key`; `useBYOK` is persisted per feature in `assistant_settings`; `GET /api/settings/ai/cost?window=7d` returns `{chat: usd, triage: usd, draft: usd}` aggregated via `SUM(llm_call_audit.cost_usd) WHERE tenant_id=? AND feature=? AND created_at >= now()-interval '7 days'`; UI shows "Currently using: {provider}/{model}" + "$X.XX last 7d"
    - Acceptance: the helper text updates within one query refetch when the picker changes; tenants with zero calls in 7 days see `$0.00`; the endpoint returns 0 for unknown features; the toggle is independent per feature (verified by saving Chat=BYOK + Triage=default + Draft=BYOK round-trip)

17. **BYOK test-connection with enum-only response**: User can test the BYOK before relying on the key.
    - Current: no test-connection endpoint for user BYOK; admin `MKEY-03` test-connection exists for master keys with the enum-only contract
    - Target: `POST /api/settings/ai/test-connection` accepts `{providerId}` and uses the user's stored BYOK to call the provider's `/v1/models` (or per-provider equivalent); response is exactly `{result: 'OK' | 'INVALID_KEY' | 'RATE_LIMITED' | 'NETWORK_ERROR' | 'TIMEOUT'}`; rate-limited to 10/hour per user; never returns the provider error body
    - Acceptance: response shape contains exactly one field `result` from the closed enum; no provider error string ever appears in the response body (snapshot test with stubbed provider returning a 401 body); the 11th call in an hour returns HTTP 429 with `code=ai.test_connection.rate_limited`

## Boundaries

**In scope:**

- Canonical `/ai` page restructured to flat `<SectionHeader>` groups (Inbox Zero pattern): `Your voice`, `Behavior`, `Updates`, `Safety net`, `AI Provider`. No shadcn `<Tabs>`, no query-param tab state.
- Every setting uses the `SettingCard` (title + description + Edit/Set button) → shadcn `Dialog` edit pattern, except short toggles (`<Switch>`) which render inline on the card.
- Backend: `GET/PUT /api/settings/voice`, `GET/PUT /api/settings/behavior`, `GET/PUT /api/settings/ai`, `GET /api/settings/ai/cost?window=7d`, `POST /api/settings/ai/test-connection`
- Backend: `GET/POST/PUT/DELETE /api/knowledge-snippets` (reusing `assistant_knowledge_snippet` table) with `UNIQUE(tenant_id, title)` + `updated_at` column
- Backend: `DELETE /api/triage/sender-safety-net/{id}` + domain-pattern support on the existing POST opt-in
- Liquibase changelog adding `email_signature`, `tone_preset`, `auto_draft_replies`, `draft_confidence` (enum), `sensitive_data_protection` to `assistant_settings`
- Liquibase changelog adding `UNIQUE(tenant_id, title)` + `updated_at` to `assistant_knowledge_snippet`
- Liquibase changelog adding `pattern_kind`, `created_by_user` to `tenant_protected_sender_observation` and `blocked_by_safety_net_pattern` to the triage audit row
- Move `ByokForm` from `/settings` to the `AI Provider` section of `/ai`
- ArchUnit / integration tests proving: shared sanitizer path between chat tool and REST, shared knowledge-snippet repo path between chat tool and REST, OpenRouter+9Router never shown as BYOK options
- Reuse v1.0 `ANL-03` daily-digest endpoint and v1.0 `TRG-07` shadow-mode endpoint (no migration); both render as inline `<Switch>` in their sections

**Out of scope:**

- `SET-SAFE-02` paste-import — deferred to v1.3 per round-1 scope decision (user does not need bulk import yet)
- `SET-SAFE-03` per-entry mode toggle (`protect` vs `escalate`) — deferred to v1.3 per round-1 scope decision; every user-added entry behaves as `protect`
- Refactoring or reshaping the legacy `/settings` page (Account / Language / Gmail / Notifications / Delete cards) — those stay as-is; only `ByokForm` is removed
- shadcn `<Tabs>` layout or query-param tab routing — superseded by IZ flat-section pattern during discuss-phase 2026-05-26. ROADMAP success criterion 1 ("query-param-driven active tab on a single flat-folder `/settings/page.tsx`") is replaced by "flat sections grouped with `<SectionHeader>` on `/ai`".
- 0.0–1.0 confidence slider — replaced by enum `LOW | MEDIUM | HIGH` `<Select>` per IZ pattern; backend maps the enum to numeric thresholds internally so the worker logic does not change
- File-based route shape (`/ai/personalization`, `/ai/behavior`, etc.) — single `/ai/page.tsx`, no nested routes, no query-param tab state
- Splitting BYOK + model picker onto `/settings` (the IZ pattern) — Zero Mail is single-tenant-per-user; BYOK and model picker stay together on `/ai`
- New table for knowledge snippets — `assistant_knowledge_snippet` is reused
- New behavior_settings or voice_settings table — best-practice consolidation into existing `assistant_settings` per round-1 decision
- Exposing `assistant_memory` (generic memory used by `SAVE_MEMORY` chat tool) in the UI — out of scope; remains chat-only
- Migrating daily digest or shadow-mode into `assistant_settings` — both reuse existing v1.0 endpoints
- Hostile-corpus eval suite, Grafana dashboards, visual brand refresh — deferred to v1.3 (`EVAL-*`, `OPS-DASH-*`, `VISUAL-REFRESH-*`)
- Provider expansion beyond the 4 BYOK-eligible — deferred to v1.3 (`SET-AI-EXP-*`)
- Free-text model ID textbox in the picker — out of scope (typos → silent failures; curated catalog list only, per `REQUIREMENTS` Out-of-Scope row)

## Constraints

- All voice-related text inputs that feed system prompts (personal instructions especially) MUST flow through the existing `PersonalizationSanitizer` exactly once; chat-tool and REST paths share the sanitizer instance (ArchUnit / unit test enforces single-call)
- `assistant_knowledge_snippet` writes from both `ADD_TO_KNOWLEDGE_BASE` chat tool and `POST /api/knowledge-snippets` REST MUST go through the same repository call site (verified by ArchUnit / unit test)
- OpenRouter and 9Router MUST NEVER appear as BYOK-eligible providers in `GET /api/settings/catalog` response or `PUT /api/settings/ai` request (server-side rejection + frontend filter as defense in depth)
- BYOK keys MUST be AES-GCM-encrypted at rest via the existing v1.0 `LLM-04` cipher; plaintext key MUST NOT appear in any log, exception message, or API response after the initial save
- `POST /api/settings/ai/test-connection` MUST return only the enum `{OK, INVALID_KEY, RATE_LIMITED, NETWORK_ERROR, TIMEOUT}` (same contract as `MKEY-03`); rate-limited to 10/hour per user
- `GET /api/settings/catalog` ETag caching (already implemented) MUST be respected by the Tab D client (TanStack Query default `staleTime` is acceptable; explicit invalidation on BYOK save)
- Page layout MUST use flat `<SectionHeader>` groups on a single `/ai/page.tsx` — no shadcn `<Tabs>`, no query-param tab state, no nested file-based routes
- Every multi-field setting MUST use the `SettingCard` + shadcn `Dialog` edit pattern; short toggles MAY render inline as `<Switch>` on the card (per Inbox Zero reference)
- Knowledge snippets MUST persist `UNIQUE(tenant_id, title)`; the user is shown a duplicate error before save attempts on existing titles
- No hardcoded color hex anywhere in Tab A/B/C/D — design tokens only (per `apps/web/AGENTS.md`)
- Backend DTO records MUST use Jakarta Bean Validation + `@Schema(requiredProperties = {...})` so the regenerated `apps/web/lib/api/schema.d.ts` carries accurate required/nullable info (per project convention 10); FE MUST NOT hand-edit `schema.d.ts`
- TanStack Query mutation toasts MUST flow through `meta.successMessage` / `meta.errorMessage` (per project convention 11); no local `toast.success/error` calls in feature hooks
- HTML prototype at `.planning/phases/09-user-settings-ui-on-curated-catalog/09-PROTOTYPE.html` MUST be produced during `/gsd-ui-phase` (per project CLAUDE.md UI Phase Prototype Rule)

## Acceptance Criteria

- [ ] Opening `/ai` renders flat `<SectionHeader>` groups (`Your voice`, `Behavior`, `Updates`, `Safety net`, `AI Provider`) in a single scrollable page — no shadcn `<Tabs>`, no `?tab=` query param
- [ ] Every multi-field setting opens an edit Dialog via its `SettingCard` Edit/Set button; short toggles render inline as `<Switch>` on the card
- [ ] `PUT /api/settings/voice` round-trips writingStyle, personalInstructions (sanitized), emailSignature, tonePreset, aiOutputLanguage; values exceeding limits return HTTP 400 with the documented `code=voice.*` strings
- [ ] `GET/POST/PUT/DELETE /api/knowledge-snippets` work end-to-end; tenant isolation verified by integration test (cross-tenant access returns 404 not 403); duplicate `(tenant_id, title)` returns 409 `code=knowledge.title.duplicate`; list orders by `updated_at DESC`
- [ ] Knowledge section renders `<Table>` (Title | Last Updated | Edit | Delete) with `+ Add` button; clicking Edit opens the same Dialog prefilled; delete uses `ConfirmDialog`
- [ ] `ADD_TO_KNOWLEDGE_BASE` chat tool and `POST /api/knowledge-snippets` go through the same persistence call site (ArchUnit / unit test green)
- [ ] `PUT /api/settings/behavior` persists auto-draft toggle, `draft_confidence` enum, sensitive-data toggle; daily-digest and shadow-mode toggles persist via the existing v1.0 ANL-03 / TRG-07 endpoints (no new column)
- [ ] Draft worker reads `draft_confidence` enum, resolves to the internal threshold (LOW=0.50 / MEDIUM=0.70 / HIGH=0.85), and skips persistence when confidence < threshold (integration test asserts threshold per enum value)
- [ ] Safety net `DELETE /api/triage/sender-safety-net/{id}` returns 403 for observation-created entries and 200 for user-created entries
- [ ] Safety net `POST /api/triage/sender-safety-net/{pattern}/opt-in` accepts both `ceo@acme.com` and `@acme.com` and persists `pattern_kind` correctly
- [ ] Triage audit row carries `blocked_by_safety_net_pattern` when applicable and Tab C renders the badge with the pattern; rows without the field render unchanged
- [ ] `AI Provider` section renders only OpenAI / Anthropic / Google / DeepSeek as BYOK options; OpenRouter and 9Router appear only as platform-default labels
- [ ] BYOK save for `openrouter` or `9router` is rejected server-side with `code=ai.byok.provider_not_allowed`
- [ ] `ByokForm` no longer appears in `SettingsClient.tsx`; it renders once inside the `AI Provider` section on `/ai`
- [ ] `GET /api/settings/ai/cost?window=7d` returns per-feature USD totals; helper text shows "Currently using: {provider}/{model}" + "$X.XX last 7d"
- [ ] `POST /api/settings/ai/test-connection` returns exactly one of `{OK, INVALID_KEY, RATE_LIMITED, NETWORK_ERROR, TIMEOUT}`; provider error bodies never leak; 11th call/hour returns 429
- [ ] `apps/web/lib/api/schema.d.ts` is regenerated from the running backend after Phase 9 DTO additions; no hand-edits
- [ ] Playwright e2e covers the flat-section golden path: edit voice (Dialog) → save → reload → values persist; toggle behavior (`<Switch>`) → reload → persist; add+edit+delete knowledge snippet → reload → persist; add+delete safety-net entry → reload → persist; pick BYOK provider+model + test connection → state persists
- [ ] No hardcoded color hex in any Tab A/B/C/D component (Prettier / ESLint / repo grep gate)

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                                                    |
|--------------------|-------|------|--------|--------------------------------------------------------------------------|
| Goal Clarity       | 0.90  | 0.75 | ✓      | 4-tab layout on `/ai`, 17 requirements with explicit current/target      |
| Boundary Clarity   | 0.85  | 0.70 | ✓      | `/settings` legacy untouched; SAFE-02/03 deferred; ByokForm moved fully |
| Constraint Clarity | 0.78  | 0.65 | ✓      | Sanitizer single-call, BYOK provider allow-list, query-param routing    |
| Acceptance Criteria| 0.74  | 0.70 | ✓      | Most criteria are testable; some depend on Phase 9 plan to detail        |
| **Ambiguity**      | 0.169 | ≤0.20| ✓      | Gate passed after round 2                                                |

Status: ✓ = met minimum

## Interview Log

| Round | Perspective       | Question summary                                            | Decision locked                                                                   |
|-------|-------------------|-------------------------------------------------------------|-----------------------------------------------------------------------------------|
| 1     | Researcher        | What does the existing `/settings` page contain today?      | Flat cards: Account / Language / Gmail / BYOK / Notifications / Delete           |
| 1     | Researcher        | Where is `/ai` route? What is on it?                        | Exists; contains Auto-send toggle + SenderSafetyNetList + add-sender form        |
| 1     | Researcher        | Do `assistant_knowledge_snippet` / `assistant_memory` exist?| Yes — both tables exist; reuse `assistant_knowledge_snippet` for SET-VOICE-04   |
| 1     | Boundary Keeper   | Two-page split: `/ai` for AI config, `/settings` for legacy | Confirmed by user. Voice tab lives on `/ai` because giọng văn liên quan tới AI  |
| 1     | Boundary Keeper   | Safety Net — how far to extend the backend?                 | MINIMAL: add DELETE + domain pattern; DROP paste-import; DROP mode toggle       |
| 1     | Simplifier        | DB strategy for new behavior + voice fields?                | Add all 5 missing columns to `assistant_settings` (single GET/PUT)              |
| 1     | Researcher        | Where does cost estimate (SET-AI-03) come from?             | Aggregate `llm_call_audit` SUM per feature, last 7d                              |
| 2     | Boundary Keeper   | Tab routing on `/ai` — query param or file route?           | Query param `/ai?tab=...` (matches ROADMAP success criterion 1)                  |
| 2     | Boundary Keeper   | Reuse v1.0 daily-digest + shadow-mode endpoints?            | YES reuse — no migration into `assistant_settings`                               |
| 2     | Boundary Keeper   | ByokForm — keep on `/settings` or move fully to `/ai`?      | Move fully to `/ai` `AI Provider` section; remove import from `SettingsClient.tsx` |
| 3     | Researcher (discuss-phase) | Check Inbox Zero AI config pattern before locking gray areas | Pivot Phase 9 layout to IZ shape: flat `<SectionHeader>` groups (not Tabs), `SettingCard`+Dialog edit (not inline), Knowledge as Table+Dialog with `UNIQUE(tenant_id, title)` + `updated_at DESC`, `SET-BEHV-02` confidence enum LOW/MEDIUM/HIGH (not slider). Single deviation from IZ: BYOK + model picker stay on `/ai` because Zero Mail is single-tenant-per-user. |

---

*Phase: 09-user-settings-ui-on-curated-catalog*
*Spec created: 2026-05-26*
*Next step: /gsd:discuss-phase 9 — implementation decisions (Liquibase ordering, sanitizer wiring, FE feature folders, test-slice ladder)*
