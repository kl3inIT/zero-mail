# Roadmap: Zero Mail

## Milestones

- ✅ **v1.0 MVP** — Phases 1, 1.1-1.6, 2A-2C, 3, 4, 5A-5C, 6 (shipped 2026-05-15) — see [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)
- ✅ **v1.1 Email assistant chat** — Phase 7 only (shipped 2026-05-19; Phase 8 deferred to v1.2) — see [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)
- 🚧 **v1.2 Admin Console + User Settings UI** — in planning (2 phases, 61 requirements, started 2026-05-19; Phase 8 + former Phase 9 merged 2026-05-19 during spec-phase; Phase 8 admin auth pivoted to WebAuthn + separate frontend during discuss-phase 2026-05-19, +2 reqs ADMIN-09/10)

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

- [ ] **Phase 8: Admin Console & Operator Tooling** — Operators can log in to a hardened `/admin/*` console with RBAC + append-only audit, configure 6 LLM providers with master keys, curate the per-feature model catalog, inspect tenant health / worker queue / platform LLM spend, and deploy v1.2 infrastructure via the re-platformed reverse proxy
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

**Plans**: TBD (planning structure: 8A foundation → 8B master keys → 8C tenant inspection → 8D catalog Sync → 8E queue health → 8F spend dashboard; 8A is hard gate, 8B/8C/8D/8E/8F can wave-parallelize after 8A)
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

### Phase 9: User Settings UI on Curated Catalog

**Goal**: A user can open `/settings` and configure their writing voice, assistant behavior, sender safety net, and per-feature AI provider/model across four tabs — with the AI tab pulling exclusively from the admin-curated catalog and BYOK only for the four user-allowed providers.

**Depends on**: Phase 8 (`MKEY` master keys + `CAT` curated catalog + `GET /api/settings/catalog` endpoint). SET-VOICE / SET-BEHV / SET-SAFE work is independent of MKEY/CAT and may begin earlier in implementation.

**Requirements**: SET-VOICE-01, SET-VOICE-02, SET-VOICE-03, SET-VOICE-04, SET-VOICE-05, SET-VOICE-06, SET-BEHV-01, SET-BEHV-02, SET-BEHV-03, SET-BEHV-04, SET-BEHV-05, SET-SAFE-01, SET-SAFE-02, SET-SAFE-03, SET-SAFE-04, SET-AI-01, SET-AI-02, SET-AI-03, SET-AI-04

**Success Criteria** (what must be TRUE):
1. User can open `/settings` and switch between four shadcn `<Tabs>` (Personalization, Behavior, Safety Net, AI Provider/Model) via query-param-driven active tab on a single flat-folder `/settings/page.tsx` route
2. In Personalization, user can edit free-text writing style (200–500 words), personal instructions (XML-fenced, prompt-injection-sentinel-sanitized, 2000-char cap), email signature, titled knowledge-base snippets, a tone preset (professional/friendly/casual/formal/custom), and pick AI output language (VI default, EN secondary) independent of UI language
3. In Behavior, user can toggle auto-draft replies, set a draft confidence threshold slider (0.0–1.0), toggle daily digest (reuses v1.0 ANL-03), toggle sensitive-data protection (default ON), and surface the shadow-mode toggle from v1.0 TRG-07
4. In Safety Net, user can view, add, paste-import (with parsed preview), and remove sender entries; pick per-entry mode (`protect` vs `escalate`); and see a visual indicator in the audit log when a rule was blocked by the safety net
5. In AI Provider/Model, user can pick provider + model per feature (chat/triage/draft) from `GET /api/settings/catalog` (showing platform default + the 4 BYOK-eligible providers configured), enter their BYOK key (OpenAI / Anthropic / Google / DeepSeek only — never OpenRouter or 9Router) with AES-GCM encryption and no plaintext echo, toggle "Use platform default" vs "Use my key" independently per feature, see the resolved provider+model in helper text and last-7d cost estimate, and test the BYOK connection with the same enum-only response shape as MKEY-03

**Plans**: TBD
**UI hint**: yes

---

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-6 (collapsed) | v1.0 | 123/123 | Complete | 2026-05-15 |
| 7. Chat Email Assistant | v1.1 | 6/6 | Complete | 2026-05-18 |
| 8. Admin Console & Operator Tooling | v1.2 | 0/0 | Not started | — |
| 9. User Settings UI on Curated Catalog | v1.2 | 0/0 | Not started | — |

---

*v1.0 archived 2026-05-15. v1.1 archived 2026-05-19 (Phase 7 only). v1.2 roadmap drafted 2026-05-19 — initially 3 phases; Phase 8 + former Phase 9 merged into single Phase 8 (40 reqs) on 2026-05-19 during spec-phase; Phase 8 admin auth pivoted to WebAuthn passkey + separate `apps/admin` Vite frontend during discuss-phase 2026-05-19, adding ADMIN-09 (admin_users schema) + ADMIN-10 (WebAuthn ceremonies). Final shape: 2 phases, 61 requirements, 100% coverage.*
