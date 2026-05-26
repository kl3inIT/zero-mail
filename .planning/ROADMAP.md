# Roadmap: Zero Mail

## Milestones

- ✅ **v1.0 MVP** — Phases 1, 1.1-1.6, 2A-2C, 3, 4, 5A-5C, 6 (shipped 2026-05-15) — see [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)
- ✅ **v1.1 Email assistant chat** — Phase 7 only (shipped 2026-05-19; Phase 8 deferred to v1.2) — see [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)
- 🚧 **v1.2 Admin Console + User Settings UI** — in planning (3 phases, 73 requirements, started 2026-05-19; Phase 8 + former Phase 9 merged 2026-05-19 during spec-phase; Phase 8 admin auth pivoted to WebAuthn + separate frontend during discuss-phase 2026-05-19, +2 reqs ADMIN-09/10; Phase 08.1 inserted 2026-05-23 for Inbox Zero-style rule actions and examples)

## Phases

<details>
<summary>✅ v1.0 MVP (shipped 2026-05-15) — 17 phases, 123 plans</summary>

Full details: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)

</details>

<details>
<summary>✅ v1.1 Email assistant chat (shipped 2026-05-19) — Phase 7 only</summary>

- [x] Phase 7: Chat Email Assistant (Backend + Frontend + Send Executor + ArchUnit flip 0→1) — 6/6 plans, completed 2026-05-18

Phase 8 (Settings + Hardening + Eval + GA) **deferred to v1.2** during spec-phase 2026-05-19. 19 unchecked v1.1 reqs (SET-AI/VOICE/BEHV/SAFE-*) carried forward to v1.2 candidates.

Full details: [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)

</details>

### 🚧 v1.2 — Admin Console + User Settings UI (in planning)

**Phase numbering continues from v1.1.** v1.2 begins at Phase 8 (no reset to 1).

- [x] **Phase 8: Admin Console & Operator Tooling** — Operators can log in to a hardened `/admin/*` console with RBAC + append-only audit, configure 6 LLM providers with master keys, curate the per-feature model catalog, inspect tenant health / worker queue / platform LLM spend, and deploy v1.2 infrastructure via the re-platformed reverse proxy (completed 2026-05-20)
- [ ] **Phase 08.1: Inbox Zero-style Rule Actions & Admin-managed Examples Catalog** — Users can build rules from Inbox Zero-style examples/personas and enable expanded actions including send replies, forward, and send email behind one default-ON global auto-send setting, runtime safety gates, fallback-to-draft behavior, and auditable send boundaries
- [ ] **Phase 9: User Settings UI on Curated Catalog** — Users can configure voice, behavior, safety net, and AI provider/model across four tabs backed by the admin-curated catalog

## Phase Details

### Phase 8: Admin Console & Operator Tooling

**Goal**: An operator (admin user) can deploy v1.2 infrastructure, sign in via the existing bundled Google OAuth flow, reach a `/admin/*` console gated by `ROLE_ADMIN` with full append-only audit, configure all 6 LLM providers with master keys, curate the per-feature model catalog via Sync-from-`/models`, and inspect tenant health / worker queue / platform LLM spend — with zero tenant-content leakage and zero master-key byte leakage.

**Merged 2026-05-19:** This phase consolidates original v1.2 Phase 8 (foundation: auth/audit/AdminContext/route group/OPS-INFRA) and original Phase 9 (operator surface: master keys/catalog/tenant inspection/queue/spend) into a single coherent admin console deliverable. Planning structure inside the phase follows research SUMMARY sub-phases 8A→8F (8A foundation must complete before 8B/8C/8D/8E/8F callers).

**Depends on**: v1.1 Phase 7 (chat send call-site invariant, Spring Modulith spine, Spring Session Redis, bundled OAuth, AES-GCM `RefreshTokenCipher`)

**Requirements (42)**: OPS-INFRA-01, OPS-INFRA-02, OPS-INFRA-03, ADMIN-01, ADMIN-02, ADMIN-03, ADMIN-04, ADMIN-05, ADMIN-06, ADMIN-07, ADMIN-08, ADMIN-09, ADMIN-10, ARCH-08, ARCH-09, ARCH-10, ARCH-11, ARCH-12, MKEY-01, MKEY-02, MKEY-03, MKEY-04, MKEY-05, MKEY-06, MKEY-07, MKEY-08, CAT-01, CAT-02, CAT-03, CAT-04, CAT-05, CAT-06, CAT-07, OPS-TENANT-01, OPS-TENANT-02, OPS-TENANT-03, OPS-TENANT-04, OPS-TENANT-05, OPS-QUEUE-01, OPS-QUEUE-02, OPS-SPEND-01, OPS-SPEND-02
(ADMIN-09 = admin_users table schema; ADMIN-10 = WebAuthn enrollment + assertion ceremonies — both added during discuss-phase pivot 2026-05-19.)

**Success Criteria** (what must be TRUE):

1. Operator can run `docker compose up` on the VPS and reach `apps/web` + `/api/*` + `9router-dashboard` through a single `jc21/nginx-proxy-manager` reverse proxy, with Let's Encrypt auto-renewal and Google OAuth callback URLs unchanged from v1.1 (live VPS migration itself is a deploy step, gated on compose + runbook deliverables landing in the merged phase)
2. A bootstrapped admin user can sign in at `admin.zeromail.com` via WebAuthn passkey ceremony (hardware-bound, `userVerificationRequirement=REQUIRED`) and access `/api/admin/*` routes; a request without a valid admin WebAuthn session returns HTTP 401 at the chain level. Two separate `SecurityFilterChain` beans isolate admin and user auth: `@Order(1) adminChain` uses `securityMatcher("/api/admin/**")` + `.webAuthn(...)`; `@Order(2) userChain` retains Google OAuth bundled flow unchanged. Chain-level enforcement plus explicit `@PreAuthorize("hasRole('ADMIN')")` per admin controller (defense in depth) + ArchUnit rules `every_admin_controller_must_have_preauthorize` and `admin_chain_does_not_use_oauth2login`. The user-facing `users` table gains NO `role` column — admin identity lives entirely in a separate `admin_users` table.
3. Every admin state mutation (role grants, catalog edits, master-key set/rotate, tenant pause/disconnect/delete) writes one row to `admin_audit_event` in the same transaction with HMAC-chained hash; the application DB user cannot `UPDATE` or `DELETE` that table, and a Postgres trigger raises `EXCEPTION` on any attempt regardless of role
4. The admin frontend ships as a NEW `apps/admin` Vite + React 19 SPA (no SSR, no SEO, no Next.js) served at `admin.zeromail.com` via NPM proxy with its own Let's Encrypt cert and its own generated TypeScript client from `admin-schema.d.ts` (codegenned from `springdoc-openapi` `GroupedOpenApi` admin spec); the public `apps/web` Next.js bundle ships zero admin schema types and zero admin route code; `zeromail.com/admin` returns 404. Persistent "ADMIN MODE — actions affect tenants" banner inside `apps/admin` chrome covers the "alt-tab between admin tabs" failure mode (DNS subdomain handles the user-vs-admin cognitive cue).
5. Inside any admin request, `TenantContext.currentOrThrow()` throws and `AdminContext.currentOrThrow()` resolves; cross-tenant admin reads can only happen through `AdminTenantAccess.readOnly(tenantId, supplier)`, which writes an `admin_read_event` row before invoking the supplier, enforced by an ArchUnit rule banning admin packages from referencing `TenantContext` directly
6. ArchUnit `AdminPathBodyBanTest` is green: admin packages cannot reference `GmailClient` body-exposing methods, `ChatMessageRepository.findContent*`, `LlmCallAudit.prompt*` / `.completion*` accessors, or any field named per the regex `body|bodyHtml|snippet|payload|prompt|completion|content`; the repo-wide grep gate still asserts exactly 1 Gmail send call site; admin packages are additionally forbidden by ArchUnit from referencing Gmail send methods entirely
7. Operator can set/test/rotate the master key for all 6 providers (OpenAI, Anthropic, Google, DeepSeek, OpenRouter, 9Router) through a unified `/admin/master-keys/<provider>` form: keys are AES-GCM-encrypted via the existing `RefreshTokenCipher`, displayed masked-only (`sk-****abc1`), test-connection returns an enum (`OK | INVALID_KEY | RATE_LIMITED | NETWORK_ERROR | TIMEOUT`) with no provider error body, and rotation evicts every cached `ChatModel` instance for that provider across all tenants on success while preserving the old key on test failure
8. The 9Router master-key entry toggles between `OPENAI_FORMAT` (Spring AI OpenAI adapter at the configured `base_url`) and `ANTHROPIC_FORMAT` (Spring AI Anthropic adapter at the same `base_url`) without changing the adapter type for the other 5 single-mode providers; admin can pick a per-feature default provider for `chat`, `triage`, and `draft` (v1.0 default `OpenRouter` preserved at launch)
9. Operator can run the 3-step Sync-from-`/models` flow per provider (Fetch via `processing_job` SKIP LOCKED with 60s Redis debounce lease → Diff review → Confirm); auto-apply is forbidden, model IDs are validated against `^[a-zA-Z0-9._:/\-]{1,128}$` and per-provider JSON Schema, Anthropic's Sync button is disabled with a manual-entry tooltip (Liquibase data seed for initial Claude family), and disabling a model with pinned tenants requires confirm-twice + reason
10. Operator can browse `/admin/tenants` (list) and `/admin/tenants/<tenantId>` (5 tabs: Overview, Health, Billing, Spend, Activity) showing metadata only — no email body, no chat content, no prompts/completions, chat-session inspection limited to metadata (count, last activity, model selection) — and can pause/disconnect/delete a tenant with confirm-twice + reason; an `AdminResponseBodyBanFilter` rejects with HTTP 500 + audit row any admin response containing a string field >200 chars whose key matches the forbidden regex
11. Operator can view at `/admin/queue` real-time read-only aggregates over `outbox` + `processing_job` (depth by type, oldest-unleased age, retry distribution, failure rate, dead-letter count) with 10s auto-refresh, and re-queue a dead-letter row without viewing its payload or editing its fields
12. Operator can view at `/admin/spend` a metadata-only dashboard aggregating `llm_call_audit` (today / 7d / 30d totals split platform-vs-BYOK, stacked bar by provider, donut by feature, top-20 tenants, max 90-day picker) with k-anonymity on deleted tenants and no per-prompt drill-down; the CI `MasterKeySentinelLeakTest` (ARCH-11) is green — no log line, response body, exception, YAML, or audit row contains `sk-`, `sk-ant-`, `AIza`, or `sk-or-` sentinels (or masked-encoded forms)

**Plans:** 6/6 plans complete

Plans:
**Wave 1**

- [x] 8A-PLAN.md — Foundation: docker-compose + runbook; SecurityFilterChain admin/user split; admin_users + WebAuthn ceremonies; append-only audit (HMAC chain + trigger); AdminContext mutex; GroupedOpenApi split; AdminAudit + RoleGrants controllers; apps/admin Vite SPA scaffold + login/enroll/dashboard/audit/role-grants routes (ADMIN-01..10, ARCH-08/09/10/12, OPS-INFRA-01..03)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 8B-PLAN.md — Master Keys: llm_provider_master_key + PlatformSecretCipher + MasterKeyAdminService (set/test/rotate) + edit-session + rate-limit + ChatModel cache eviction + ProviderMasterKeyResolver + 9Router dual-mode + MasterKeySentinelLeakTest; /master-keys list + per-provider edit (MKEY-01..08, ARCH-11)
- [x] 8C-PLAN.md — Tenant Inspection: AdminTenantAccess.readOnly + 5-tab projections + AdminResponseBodyBanFilter + TenantOAuthRevocationGateway + pause/disconnect/delete; /tenants list + /tenants/:id 5-tab detail (OPS-TENANT-01..05)
- [x] 8E-PLAN.md — Queue Health: QueueHealthQueryService + DeadLetterRequeueService + KpiCard + AutoRefreshIndicator + /queue page with 10s auto-refresh (OPS-QUEUE-01/02)
- [x] 8F-PLAN.md — Spend Dashboard: SpendAggregateQueryService + k-anonymity + AdminSpendPromptAccessorBanTest + /spend page with 90d picker + stacked bar + donut + top-20 (OPS-SPEND-01/02)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 8D-PLAN.md — Catalog: provider_catalog + model_catalog + feature_binding + 3-step Sync (Fetch/Diff/Confirm) + Anthropic seed + GET /api/settings/catalog + CatalogChangedEvent; /catalog browser + Sync wizard (CAT-01..07)

**UI hint**: yes

**Planning-time decisions locked during spec-phase 2026-05-19 + discuss-phase pivot 2026-05-19** (from research SUMMARY + spec interview + WebSearch + Spring Security 7 Context7):

- **Decision 1 (admin auth method, POST-PIVOT 2026-05-19):** WebAuthn passkey via Spring Security 7 `.webAuthn(...)` DSL on `admin.zeromail.com` with `userVerificationRequirement=REQUIRED`. NOT Google OAuth (decouple admin from Google IdP), NOT HTTP Basic (OWASP ASVS deprecated), NOT password.
- **Decision 1.1 (frontend shape, POST-PIVOT 2026-05-19):** Separate `apps/admin` Vite + React 19 SPA on `admin.zeromail.com`. NOT a Next.js route group inside `apps/web`. Admin doesn't need SEO/SSR; DNS subdomain provides cognitive cue; admin schema types stay out of public Next.js bundle.
- **Decision 1.2 (chain isolation, POST-PIVOT 2026-05-19):** Two `SecurityFilterChain` beans via `securityMatcher`: `@Order(1)` admin chain with `.webAuthn(...)`; `@Order(2)` user chain unchanged `.oauth2Login(...)`. ArchUnit enforces non-overlap. `AdminContext` ScopedValue mutex with `TenantContext` remains locked as codepath-level defense in depth.
- **Decision 1.3 (user-side RBAC, POST-PIVOT 2026-05-19):** Removed entirely. `users` table gains NO `role` column; no `GrantedAuthoritiesMapper`. Admin authority sourced from separate `admin_users` table via `AdminUserDetailsService` on admin chain only.
- **Decision 1.5 (method-security expression):** Explicit `@PreAuthorize("hasRole('ADMIN')")` per controller (no `@AdminController` meta-annotation until rule-of-three triggers in Phase 9+); ArchUnit rule enforces every `@RestController` in `..controllers.admin..` has `@PreAuthorize`.
- **Decision 2 (first-admin bootstrap, POST-PIVOT 2026-05-19):** Liquibase seed of `admin_users` row(s) from `zeromail.admin.bootstrap-emails` config + Spring Boot startup runner prints 10-min one-time enrollment URL to STDOUT (never log file or DB). Admin uses URL to complete WebAuthn registration ceremony. NOT env-var `ZEROMAIL_BOOTSTRAP_ADMIN_EMAIL` (pre-pivot).
- **Decision 3 (audit retention):** Indefinite for `admin_audit_event`, 30 days for `admin_read_event`.
- **Decision 4 (chat-session inspection scope):** Session metadata only (count, last activity, model selection). "Show details" disabled with tooltip referring to a future v1.3+ tenant-bound support ticket flow.
- **Decision 5 (Anthropic catalog seeding):** Liquibase data seed for initial Claude family at v1.2 launch; manual admin entry for new models; Sync button disabled on Anthropic provider page.
- **Decision 6 (OPS-INFRA gating):** Phase 8 merge gate ships docker-compose changes + NPM subdomain config for `admin.zeromail.com` + `docs/ops/v1.2-deploy.md` runbook. Live VPS migration from hand-managed nginx → NPM + 9Router sidecar boot + admin subdomain DNS is a deploy step (tracked separately, not blocking merge).

**Research mandate:** Plan-phase MUST pull Spring Security 7 docs via Context7 (`/websites/spring_io_spring-security_reference_7_0` or `/spring-projects/spring-security`) before coding decisions — especially the `.webAuthn(...)` DSL (Spring Security 6.4+ feature; training data may not cover it), multiple `SecurityFilterChain` + `securityMatcher` patterns, and OAuth2 OIDC userinfo path unchanged. See memory notes `project_phase8_spring_security_7_research` and `project_v12_admin_webauthn_pivot`.

---

### Phase 08.1: Inbox Zero-style rule actions and admin-managed examples catalog (INSERTED)

**Goal:** Bring the Inbox Zero rule-authoring UX into Zero Mail: copy the example/persona catalog as a seed, show an Available Actions panel, let admins manage examples/actions, and allow real automated outbound rule actions (`send_reply`, `forward_email`, `send_email`) behind one default-ON global auto-send setting, backend safety gates, fallback-to-draft behavior, and a shared outbound gateway boundary.

**Requirements (12)**: RACT-01, RACT-02, RACT-03, RACT-04, RACT-05, RACT-06, RACT-07, RACT-08, RACT-09, RACT-10, RACT-11, RACT-12
**Depends on:** Phase 8
**Plans:** 5/6 plans executed

**Source artifacts:**

- Inbox Zero example seed copied verbatim from `../inbox-zero/apps/web/app/(app)/[emailAccountId]/assistant/examples.ts` at commit `6044fde9f`: `.planning/phases/08.1-inbox-zero-style-rule-actions-and-admin-managed-examples-cat/inbox-zero-examples.ts`
- Reference UX/actions researched from Inbox Zero `AvailableActionsPanel.tsx`, `action-availability.ts`, `actions.ts`, `static-from-risk.ts`, and `rule.ts`

**Success Criteria** (what must be TRUE):

1. Rule creation offers three entry paths matching the Inbox Zero mental model: `Create rules`, `Choose from examples`, and `Add manually`
2. The examples UI includes the copied Inbox Zero persona set (`Founder`, `Influencer`, `Realtor`, `Investor`, `Assistant`, `Developer`, `Designer`, `Sales`, `Marketer`, `Support`, `Recruiter`, `Student`, `Outreach`, `Other`) and the example prompt grid seeded from the copied source artifact
3. Admin can create, edit, disable, reorder, and localize examples/personas/action descriptors without code changes; disabled examples do not appear in the user rule builder
4. User-facing Available Actions includes `Label`, `Archive`, `Save draft`, `Mark read/unread`, `Star/unstar`, `Add to digest`, `Mark spam`, `Send reply`, `Forward`, and `Send email`, with unavailable actions visibly disabled and explained
5. Settings expose one account-level `Auto-send rules` toggle for automated outbound rule actions; it defaults ON and there are no individual outbound action toggles
6. Manual editor and AI compiler both persist the same structured `When/Then` schema; natural language remains only `sourceText`/audit metadata
7. Rule-triggered outbound actions execute only when the global setting, sender-risk guard, safety net, cap/rate-limit, idempotency, OAuth scope, tenant checks, and audit reservation all pass
8. If an outbound gate fails or the global setting is OFF, the rule result falls back to Gmail `save_draft` with an audit reason; it must not silently drop or send the email
9. All Gmail send execution goes through one shared outbound gateway/send executor; ArchUnit/grep tests are updated to allow that boundary and fail any direct Gmail send call site elsewhere
10. Privacy constraints remain intact: no long-term storage of Gmail-read email bodies, LLM prompts/completions, or embeddings; persisted draft bodies are allowed only when they are user-authored/action arguments under the existing draft-body carve-out
11. Low-trust/static sender protections equivalent to Inbox Zero's example-risk guard prevent users from saving demo examples that would send to real people by accident
12. UAT covers examples import, admin catalog management, outbound setting gates, downgrade-to-draft behavior, and the no-bypass architecture tests

Plans:
**Wave 1**

- [x] 08.1-01-PLAN.md — Contract and architecture boundary: reconcile stale docs/requirements, define shared outbound gateway contract, add Spring Modulith named interfaces, regenerate Modulith docs
- [x] 08.1-02-PLAN.md — DB-backed examples/personas/action descriptors: Liquibase seed EN+VI from Inbox Zero, user read APIs, admin CRUD APIs, OpenAPI codegen

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 08.1-03-PLAN.md — Admin rule catalog UI: bilingual persona/example/action descriptor management in `apps/admin`
- [x] 08.1-04-PLAN.md — User rules UI: persona examples inside existing `RuleComposer`, Available Actions panel, default-ON global auto-send setting UI
- [x] 08.1-05-PLAN.md — Rule action schema/compiler/manual builder: expanded structured action intents for mark/read/star/digest/spam/reply/forward/send

**Wave 3** *(blocked on Wave 2 completion)*

- [ ] 08.1-06-PLAN.md — Runtime outbound execution: shared `OutboundSendGateway`, fallback-to-draft gates, ArchUnit/privacy tests, safe Gmail UAT

### Phase 9: User Settings UI on Curated Catalog

**Goal**: A user can open `/settings` and configure their writing voice, assistant behavior, sender safety net, and per-feature AI provider/model across four tabs — with the AI tab pulling exclusively from the admin-curated catalog and BYOK only for the four user-allowed providers.

**Depends on**: Phase 8 (`MKEY` master keys + `CAT` curated catalog + `GET /api/settings/catalog` endpoint). SET-VOICE / SET-BEHV / SET-SAFE work is independent of MKEY/CAT and may begin earlier in implementation.

**Requirements**: SET-VOICE-01, SET-VOICE-02, SET-VOICE-03, SET-VOICE-04, SET-VOICE-05, SET-VOICE-06, SET-BEHV-01, SET-BEHV-02, SET-BEHV-03, SET-BEHV-04, SET-BEHV-05, SET-SAFE-01, SET-SAFE-02, SET-SAFE-03, SET-SAFE-04, SET-AI-01, SET-AI-02, SET-AI-03, SET-AI-04

**Success Criteria** (what must be TRUE):

1. User can open `/ai` and configure AI settings via flat `<SectionHeader>` groups (`Your voice`, `Behavior`, `Updates`, `Safety net`, `AI Provider`) on a single `/ai/page.tsx` route — Inbox Zero pattern. Every multi-field setting uses a `SettingCard` (title + description + Edit/Set button) opening a shadcn `Dialog`; short toggles render inline as `<Switch>` on the card. **Updated 2026-05-26 during discuss-phase** — superseded prior "four shadcn `<Tabs>` with query-param-driven active tab on `/settings`" wording.
2. In `Your voice`, user can edit free-text writing style (200–500 words), personal instructions (XML-fenced, prompt-injection-sentinel-sanitized, 2000-char cap), email signature, titled knowledge-base snippets, a tone preset (professional/friendly/casual/formal/custom), and pick AI output language (VI default, EN secondary) independent of UI language. Knowledge snippets render as a `<Table>` (Title | Last Updated | Edit | Delete) with `+ Add` button; backend enforces `UNIQUE(tenant_id, title)` and orders by `updated_at DESC`.
3. In `Behavior` + `Updates`, user can toggle auto-draft replies, pick draft confidence as an enum `LOW | MEDIUM | HIGH` (backend maps to internal thresholds 0.50 / 0.70 / 0.85), toggle daily digest (reuses v1.0 ANL-03), toggle sensitive-data protection (default ON), and surface the shadow-mode toggle from v1.0 TRG-07. **Updated 2026-05-26** — confidence pivoted from 0.0–1.0 slider to LOW/MEDIUM/HIGH enum per Inbox Zero pattern.
4. In `Safety net`, user can view, add, and remove sender entries (single email or domain pattern), and see a visual indicator in the audit log when a rule was blocked by the safety net. **Updated 2026-05-26** — paste-import (SET-SAFE-02) and `protect`-vs-`escalate` mode (SET-SAFE-03) deferred to v1.3 per spec-phase round-1 scope decision; every user-added entry behaves as `protect`.
5. In `AI Provider`, user fills a single BYOK card with: provider `<Select>` (OpenAI / Anthropic / Google / DeepSeek — never OpenRouter or 9Router), base URL `<Input>` (auto-filled per provider, user-editable for OpenAI-compatible / Anthropic-compatible endpoints), API key (AES-GCM encrypted, no plaintext echo, masked display on re-render), model `<Select>` (populated from the provider's `/v1/models` response returned by Test connection), an Active `<Switch>` (default OFF; disabled until a model is picked AND the last Test result is `OK`), `Kiểm tra kết nối`, and `Lưu`. When the row is `active=true` AND has a tested model, every AI feature (chat, triage, draft, voice-generate) runs through that BYOK row; otherwise the admin-curated catalog default applies. Test connection uses the SAME enum-only response (`OK / INVALID_KEY / RATE_LIMITED / NETWORK_ERROR / TIMEOUT`) as admin MKEY-03 via the shared `ProviderConnectionTester`, plus a `models[]` list on `OK` so the user can pick a model. A single tenant-wide last-7d cost figure renders below the card. BYOK lives on `/ai` because Zero Mail is single-tenant-per-user. **Updated 2026-05-26 during plan-phase round 2** — per-feature picker, per-feature `Platform default ↔ Use my key` toggle, AND the tenant-wide mode card all removed; replaced by a single BYOK card with an Active switch.

**Plans** (7 plans across 4 waves):
- [x] 09-01-PLAN.md — Wave 0: Liquibase changesets 094..097 + JPA entity scaffolding + 33 Wave-0 test stubs
- [x] 09-02-PLAN.md — Wave 1: Voice + Behavior + Knowledge backend (services/controllers/DTOs) + DraftReplyWorker + SensitiveDataRedactor wiring
- [x] 09-03-PLAN.md — Wave 1: Safety Net DELETE + DOMAIN pattern + triage audit blocked_by_safety_net_pattern badge
- [x] 09-04-PLAN.md — Wave 1: ProviderConnectionTester extraction + UserByokService + ByokProviderResolver + UserByokController + AiCostQueryService (D-17)
- [x] 09-05-PLAN.md — Wave 1: SET-VOICE-07 generate-from-sent (in-memory privacy invariant + Spring AI observation hardening)
- [x] 09-06-PLAN.md — Wave 2: OpenAPI regen + FE sections + Knowledge feature + AiProviderSection + ByokForm removal from /settings
- [ ] 09-07-PLAN.md — Wave 3: Playwright e2e ai-settings.spec.ts + Phase9ArchitectureTest aggregate + manual UX checkpoint

**UI hint**: yes

---

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-6 (collapsed) | v1.0 | 123/123 | Complete | 2026-05-15 |
| 7. Chat Email Assistant | v1.1 | 6/6 | Complete | 2026-05-18 |
| 8. Admin Console & Operator Tooling | v1.2 | 6/6 | Complete   | 2026-05-20 |
| 08.1. Inbox Zero-style Rule Actions & Admin-managed Examples Catalog | v1.2 | 5/6 | In Progress|  |
| 9. User Settings UI on Curated Catalog | v1.2 | 6/7 | In Progress|  |

---

*v1.0 archived 2026-05-15. v1.1 archived 2026-05-19 (Phase 7 only). v1.2 roadmap drafted 2026-05-19 — initially 3 phases; Phase 8 + former Phase 9 merged into single Phase 8 (40 reqs) on 2026-05-19 during spec-phase; Phase 8 admin auth pivoted to WebAuthn passkey + separate `apps/admin` Vite frontend during discuss-phase 2026-05-19, adding ADMIN-09 (admin_users schema) + ADMIN-10 (WebAuthn ceremonies). Phase 08.1 inserted 2026-05-23 to adopt Inbox Zero-style rule actions/examples and user-enabled outbound automation. Current shape: 3 phases, 73 requirements.*
