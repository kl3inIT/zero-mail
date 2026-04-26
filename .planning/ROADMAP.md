# Roadmap: Zero Mail

## Overview

Zero Mail is an AI Gmail triage SaaS where trust is the product. This roadmap walks from a safety-first foundation (Scoped Values, log scrubbers, OAuth, CASA kickoff) through three parallel infrastructure tracks (mail ingestion, billing ledger, LLM gateway) that converge on the rules engine and then the hero triage orchestrator. After triage lands, the user-facing surface (drafts, analytics, web UI) ships together, followed by a polish + CASA-verified launch phase. Phase 2C (LLM Gateway) is hard-gated by Phase 1 safety infrastructure, and Phase 4 (Triage) is hard-gated by Phase 2C. CASA restricted-scope verification (4–12 weeks, external) is kicked off in parallel at Phase 1 OAuth wiring and completes before Phase 6 launch.

## Phases

**Phase Numbering:**
- Integer phases (1, 3, 4, 6): Planned milestone work
- Sub-phases (2A, 2B, 2C): Parallel tracks that must all complete before Phase 3 — executable concurrently post-Phase 1
- Decimal phases (1.1, 1.2, 2.1, 2.2): Urgent insertions (marked with INSERTED)

- [x] **Phase 1: Foundation & Safety Infrastructure** - Scoped Values, `@Sensitive`, Logback scrub, ArchUnit bans, multi-tenant leak test, Google OAuth, skeleton OpenAPI, CASA kickoff (CASA external filing pending — tracked outside the phase as a parallel external dependency)
- [x] **Phase 1.1: Vietnamese-first i18n and error-handling foundation (INSERTED)** _(completed 2026-04-26)_ - Default language Vietnamese, secondary English, user-facing language switcher; stable API error contracts that are frontend-localizable; reference local JHipster project patterns where appropriate; preserve all Phase 1 privacy/safety constraints
- [x] **Phase 1.2: Domain-owned persistence restructuring (INSERTED)** _(completed 2026-04-26)_ - Refactor `backend/core` into domain-owned service/persistence/model packages, add a small shared package for stable cross-cutting infrastructure, preserve schema and safety constraints, and enforce boundaries with Modulith or ArchUnit
- [ ] **Phase 1.2.1: Shared base entity + IdentifiedEnum standard + DTO group-by-domain (INSERTED)** - Introduce `core.shared.persistence` abstract entity hierarchy (`AbstractEntity`, `AbstractAuditableEntity`, `AbstractTenantOwnedEntity`); introduce `core.shared.lang.IdentifiedEnum` interface (id/weight/labelKey) and apply to `OnboardingStep` + `GmailConnectionStatus`; reorganize `backend/api/dto/` group-by-domain (account/, gmail/, onboarding/) and rename `TenantStatusResponse` → `GmailConnectionStatusResponse`; close code review WR-01 (Pitfall 5 real persistence test), WR-02 (replace ordinal() with weight()), WR-03 (bulk delete query)
- [ ] **Phase 1.3: Frontend Architecture Refactor and Public Content Foundation (INSERTED)** - Reorganize `apps/web` around route groups, feature folders, typed OpenAPI boundaries, frontend quality gates, and public landing/docs scaffolding without implementing the final landing/docs design yet
- [ ] **Phase 2A: Mail Ingestion** - Gmail `users.watch` + Pub/Sub push + OIDC verification + idempotent history processing + global pause
- [ ] **Phase 2B: Billing (Prepaid Credits)** - Double-entry Postgres ledger, reserve/settle/release, credit-hold watchdog, balance UI hooks
- [ ] **Phase 2C: LLM Gateway** - Spring AI 2.0.0-M4 `LlmGateway` with sanitize → Unicode strip → structured tool-call + allow-list → BYOK per-request options → daily spend cap → drift detection
- [ ] **Phase 3: Rules Engine** - NL → structured matcher AST via Spring AI tool-call, deterministic evaluator, live preview, CRUD + reorder, template gallery
- [ ] **Phase 4: Triage Convergence (Hero)** - Orchestrator, safety policy layer, audit + undo, shadow mode for new tenants, sender safety net
- [ ] **Phase 5: User Surface (Drafts, Analytics, Web UI)** - AI-drafted replies, metadata-only analytics + daily digest, Next.js 16 / React 19 frontend covering all flows
- [ ] **Phase 6: Polish & CASA-Verified Launch** - End-to-end integration hardening, CASA Tier verification sign-off, launch readiness

## Phase Details

### Phase 1: Foundation & Safety Infrastructure
**Goal**: Ship the tenant-isolation and log-scrubbing infrastructure that makes every later phase safe, wire Google OAuth, publish the skeleton OpenAPI contract, and kick off the external CASA restricted-scope verification clock.
**Depends on**: Nothing (first phase)
**Requirements**: FND-01, FND-02, FND-03, FND-04, FND-05, FND-06, FND-07, AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06
**Success Criteria** (what must be TRUE):
  1. A new user can sign in with Google, connect one Gmail account, complete the guided onboarding through the template-rule step, and revoke access + delete all data from a settings screen.
  2. A concurrent multi-tenant integration test using virtual threads proves that tenant context never leaks across requests (Scoped Values only, ArchUnit fails any `ThreadLocal` reference in request/worker paths).
  3. Attempting to log an email body, LLM prompt, or LLM completion is blocked at build time by ArchUnit and at runtime by the Logback scrub filter — a reviewer can grep application logs during a synthetic traffic run and find zero body/prompt/completion content.
  4. A tenant whose OAuth grant is revoked externally is surfaced as `DISCONNECTED` in the UI with a reconnect prompt on the next request.
  5. The skeleton OpenAPI spec is published from `backend/api` and the `apps/web` module successfully generates its typed client via `openapi-typescript`, and a CASA restricted-scope verification submission has been filed with the external lab.
**Plans**: 9 plans
- [ ] 01-01-PLAN.md — Gradle multi-project scaffold, buildSrc conventions, runnable Spring Boot shells
- [ ] 01-02-PLAN.md — Tenant isolation primitives (ScopedValue, Hibernate resolver, TenantAwareTaskScope, Modulith packages, ArchUnit ThreadLocal/virtual-thread/native-SQL bans)
- [ ] 01-03-PLAN.md — Log safety contract (Sensitive<T> wrapper, Jackson module, Logback scrub filter, ArchUnit rules d + e)
- [ ] 01-04-PLAN.md — Liquibase YAML baseline + JPA entities + Testcontainers schema push (BLOCKING)
- [ ] 01-05-PLAN.md — Spring Security OAuth2 dual registration, Spring Session Redis, TenantBindingFilter, invalid_grant detection, FND-05 leak test
- [ ] 01-06-PLAN.md — AES-GCM refresh-token envelope cipher + GCP Secret Manager wiring
- [ ] 01-07-PLAN.md — backend/api skeleton OpenAPI + Phase 1 controllers + delete-cascade + onboarding state machine
- [ ] 01-08-PLAN.md — apps/web Next.js 16 scaffold + typed client codegen + /login, /onboarding, /settings routes
- [ ] 01-09-PLAN.md — FND-03 log-scrub synthetic-traffic test + CASA submission package + actuator probes
**UI hint**: yes

### Phase 1.1: Vietnamese-first i18n and error-handling foundation (INSERTED)
**Goal**: Establish a Vietnamese-default, English-secondary i18n architecture across `backend/api`, `backend/core`, and `apps/web`, with a user-facing language switcher and a stable, frontend-localizable API error contract — referencing the local JHipster project's proven patterns where they fit Spring Boot 4 / Next.js 16. All Phase 1 privacy/safety constraints (no body/prompt/completion in logs, no PII in error payloads, tenant isolation via Scoped Values, ArchUnit bans) must remain intact.
**Depends on**: Phase 1 (needs OpenAPI skeleton, Spring Security session cookie, log-scrub contract, and `apps/web` scaffold to land first)
**Requirements**: REQ-1, REQ-2, REQ-3, REQ-4, REQ-5, REQ-6, REQ-7 (locked in 01.1-SPEC.md)
**Success Criteria** (what must be TRUE):
  1. Default UI language is Vietnamese; English is selectable via a persistent in-product language switcher; preference is stored per user and survives session.
  2. Backend error responses follow a stable contract (machine-readable code + parameters) that the frontend localizes — no human-readable Vietnamese/English strings are constructed server-side for user-facing errors.
  3. Both Vietnamese and English message bundles cover every user-facing string in scope (auth, onboarding, settings, errors); a CI check fails the build on missing keys.
  4. ArchUnit and log-scrub guarantees from Phase 1 still pass; no localized error message contains PII, email body, prompt, or completion content.
  5. The JHipster reference patterns adopted are documented in CONTEXT.md with a clear "what we kept / what we adapted / what we rejected" note (Spring Boot 4 / Spring AI 2.0.0-M4 / Next.js 16 fit).
**Plans**: 8 plans
- [x] 01.1-01-PLAN.md — [BLOCKING] Wave 0 test scaffolding + Liquibase changelog 006-users-preferred-language + JPA field + Vitest config
- [x] 01.1-02-PLAN.md — Backend error contract: ErrorCodes, ApiError, FieldErrorDto, AllowedParamScalars, GlobalExceptionHandler upgrade (extends ResponseEntityExceptionHandler)
- [x] 01.1-03-PLAN.md — springdoc GlobalOpenApiCustomizer registering ApiError schema + default 4xx/5xx responses
- [x] 01.1-04-PLAN.md — Backend locale endpoint: PATCH /me/language with @Valid + tenant-scoped service + integration tests
- [x] 01.1-05-PLAN.md — Frontend i18n bootstrap: next-intl 4.x, routing/request/middleware, vi+en bundles, async layout, regenerated typed client
- [x] 01.1-06-PLAN.md — LanguageSwitcher + useLocalizedApiError hook + replace hard-coded English in Phase 1 pages/components
- [x] 01.1-07-PLAN.md — CI key-coverage gate (parity + EN scanner + backend code coverage) + Playwright switcher persistence smoke
- [x] 01.1-08-PLAN.md — ArchUnit hardening + sentinel-sweep safety tests + JHipster keep/adapt/reject verification
**UI hint**: yes

### Phase 1.2: Domain-owned persistence restructuring (INSERTED)
**Goal**: Refactor `backend/core` away from a global persistence bucket into domain-owned `service`, `persistence`, and `model` packages, with a small `shared` package reserved only for stable cross-cutting infrastructure. Preserve the existing database schema and Phase 1 privacy/safety constraints while making module boundaries explicit and enforceable.
**Depends on**: Phase 1.1 (keeps the i18n/error-contract additions in the final package layout)
**Requirements**: _(architecture restructuring; no new product requirements yet)_
**Success Criteria** (what must be TRUE):
  1. Account, onboarding, Gmail connection, tenant, privacy, and crypto code are organized by domain ownership rather than a single global `core.persistence` package.
  2. Entities and repositories live under their owning domain's `persistence` subpackage unless they are genuinely shared infrastructure.
  3. A small `shared` package exists only for stable cross-cutting infrastructure; it does not become a business-logic dumping ground.
  4. Existing table names, Liquibase changelog semantics, tenant isolation, log-scrub rules, and API behavior are preserved.
  5. Modulith and/or ArchUnit tests enforce the new boundaries, including restrictions on cross-domain access to persistence internals.
**Plans**: 6 plans
- [x] 01.2-01-PLAN.md — Move privacy module to core.shared.privacy + CL-3 Modulith naming probe + logback FQN update _(completed 2026-04-26)_
- [x] 01.2-02-PLAN.md — Move TenantEntity/Repository to core.tenant.persistence; create per-domain lowlevel/ marker _(completed 2026-04-26)_
- [x] 01.2-03-PLAN.md — Move User entity/repo + 2 model types + 2 services to core.account; reshape AccountService (CL-2 deleteCurrentUser); transitional AccountDeletionController bridge _(completed 2026-04-26)_
- [x] 01.2-04-PLAN.md — Move onboarding domain (entity/repo/enum/service); add OnboardingService.deleteSelectionsForCurrentTenant; flip UserEntity OnboardingStep import; Wave 0 OnboardingStepEnumPersistenceTest _(completed 2026-04-26)_
- [x] 01.2-05-PLAN.md — Move gmail domain + crypto; rename TenantConnectionService→GmailConnectionService; add TenantService.deleteCurrentTenant; collapse @EntityScan to single root; delete core.crypto + core.persistence packages _(completed 2026-04-26)_
- [x] 01.2-06-PLAN.md — DomainBoundaryArchTests (4 rules); TenantIsolationArchTests regex update; TenantStatusController toResponse(view) helper (D-B5); finalize AccountDeletionController Pattern 8; ./gradlew clean check

### Phase 1.2.1: Shared base entity + IdentifiedEnum standard + DTO group-by-domain (INSERTED)
**Goal**: Close out the structural-cleanup gaps that Phase 1.2 intentionally deferred so domain entities, enums, and HTTP DTOs all follow project-wide standards. Specifically: introduce `core.shared.persistence.*` abstract entity hierarchy to DRY the four entity declarations and enforce multi-tenant ownership at the type level; introduce `core.shared.lang.IdentifiedEnum` interface (id + weight + labelKey) so domain enums no longer rely on `name()` for storage or `ordinal()` for ordering; reorganize `backend/api/dto/` group-by-domain to align with the Phase 1.2 domain-owned invariant; and close the three Phase 1.2 code-review warnings (WR-01 enum persistence test must hit real DB; WR-02 forward-only invariant must use explicit weight; WR-03 bulk delete must avoid N+1).
**Depends on**: Phase 1.2 (the shared `core.shared.*` parent package and per-domain `model/` + `persistence/` tiers must already exist)
**Requirements**: _(architecture polish; no new product requirements yet)_
**Success Criteria** (what must be TRUE):
  1. `core.shared.persistence.AbstractEntity`, `AbstractAuditableEntity`, and `AbstractTenantOwnedEntity` exist; the 4 concrete entities (`TenantEntity`, `UserEntity`, `OnboardingSelectionEntity`, `GmailConnectionEntity`) extend the appropriate base; `MultiTenantLeakIntegrationTest` (FND-05) still passes after the refactor.
  2. `core.shared.lang.IdentifiedEnum` exists; `OnboardingStep` and `GmailConnectionStatus` implement it with stable `id()` + explicit `weight()`; `UserEntity.advanceTo` uses `weight()`-based comparison instead of `ordinal()`.
  3. `OnboardingStepPersistenceTest` is upgraded to a `PostgresContainerTest`-based round-trip that persists via Hibernate and asserts the storage form is the id string via `JdbcTemplate`, defending Pitfall 5 against an `EnumType.STRING → ORDINAL` switch.
  4. `backend/api/dto/` is reorganized into `account/`, `gmail/`, `onboarding/` sub-packages mirroring the `core.<domain>` layout; `TenantStatusResponse` is renamed to `GmailConnectionStatusResponse` (D-A4 follow-through).
  5. `OnboardingService.deleteSelectionsForCurrentTenant` uses a bulk JPQL `@Modifying @Query` instead of find-then-delete (closes WR-03).
  6. Database schema additions limited to audit columns (`updated_at`, `version`); no destructive or semantic-changing migrations. Full `./gradlew clean check` stays green; `ApplicationModulesTest` + `DomainBoundaryArchTests` + `AccountDeletionE2ETest` + `OnboardingStepPersistenceTest` all pass.
**Plans**:
- [x] 01.2.1-01-PLAN.md — Shared base entity hierarchy (AbstractEntity → AbstractAuditableEntity → AbstractTenantOwnedEntity) + JpaAuditingConfig + Liquibase 007 audit-columns + 4 entities refactored _(completed 2026-04-26)_
- [x] 01.2.1-02-PLAN.md — core.shared.lang Modulith leaf module (IdentifiedEnum + OrderedEnum interfaces, package-info) _(completed 2026-04-26)_
- [x] 01.2.1-03-PLAN.md — Apply enum interfaces to OnboardingStep + GmailConnectionStatus; UserEntity.advanceTo weight() swap (WR-02); OnboardingSelectionRepository bulk JPQL deleteByTenantId (WR-03); OnboardingStepPersistenceTest real-DB round-trip (WR-01) + TestJpaAuditingConfig fix; Modulith allowedDependencies updates _(completed 2026-04-26)_
- [x] 01.2.1-04-PLAN.md — DTO group-by-domain reorg (4 DTOs into account/gmail/onboarding sub-packages, 4 root files DELETED, no transitional aliases); TenantStatusResponse → GmailConnectionStatusResponse Java + URL `/gmail/connection/status` + @Tag(name=gmail) + frontend; springdoc-openapi-gradle-plugin 1.9.0 hermetic spec emit replaces bootRun&+kill (W3); @NamedInterface re-exposure on each new sub-package; frontend regen via openapi-typescript; full `./gradlew clean check` BUILD SUCCESSFUL (#6 closure) _(completed 2026-04-26)_

### Phase 1.3: Frontend Architecture Refactor and Public Content Foundation (INSERTED)
**Goal**: Refactor `apps/web` into a scalable Next.js App Router structure using route groups `app/(public)`, `app/(auth)`, and `app/(protected)`; introduce feature folders with `api/`, `components/`, and `hooks/` subfolders for TanStack Query hooks and feature-specific UI; keep shared primitives in `components/ui` and shared infrastructure in `lib/`; clean duplicate workspace artifacts; add Prettier, Husky, and lint-staged quality gates; clarify typed OpenAPI client boundaries; and scaffold public landing plus multi-page docs architecture without implementing the final landing/docs content design yet.
**Depends on**: Phase 1.2 (keeps the backend/domain restructuring and current frontend surface stable before reshaping frontend architecture)
**Requirements**: _(frontend architecture/tooling; no new product requirements yet)_
**Success Criteria** (what must be TRUE):
  1. `apps/web/app` is organized with route groups for public, auth, and protected surfaces while preserving clean URLs (`/`, `/login`, `/onboarding`, `/settings`, `/docs`, `/docs/[slug]`).
  2. Feature folders exist for current frontend domains, with explicit `api/`, `components/`, and `hooks/` subfolders where TanStack Query hooks and feature-specific UI live.
  3. Shared UI primitives remain in `components/ui`, and shared infrastructure remains in `lib/` rather than being duplicated across features.
  4. Duplicate workspace artifacts under `apps/web` are reviewed and removed or justified so the root pnpm workspace remains the source of truth.
  5. Prettier, Husky, and lint-staged are configured with scripts that fit the existing pnpm/Turbo workspace.
  6. The base `openapi-fetch` client stays in `lib/api`, while endpoint-specific calls move into feature `api` modules without regressing typed OpenAPI generation.
  7. Public landing and multi-page docs scaffolding exists at `app/(public)/page.tsx`, `app/(public)/docs/page.tsx`, `app/(public)/docs/[slug]/page.tsx`, and `content/docs/*.mdx`, with final visual design/content explicitly deferred.
**Plans**: 8 plans
- [x] 01.3-01-PLAN.md — [BLOCKING] Wave 0 architecture/cleanup test scaffolding (6 vitest files / 56 assertions; RED-by-design verification spine for Plans 02-07) _(completed 2026-04-26)_
- [x] 01.3-02-PLAN.md — Tooling foundation: Husky 9 + lint-staged + Prettier 3 + tailwindcss plugin at root; ESLint flat-config Prettier integration; next.config.ts transpilePackages _(completed 2026-04-26)_
- [x] 01.3-03-PLAN.md — Workspace cleanup: delete apps/web/pnpm-lock.yaml + apps/web/pnpm-workspace.yaml; migrate ignoredBuiltDependencies to root; verify proxy.ts cast still compiles _(completed 2026-04-26 — Playwright e2e gate Task 2 deferred to user)_
- [x] 01.3-04-PLAN.md — Feature folders skeleton + 5 component relocations + isomorphic features/account/api/me.ts + accountKeys factory + hooks; refactor proxy.ts + app/layout.tsx to import getCurrentUser; REVIEWS Revision 1 expansion (lib/api/client.ts split + ALL endpoint-specific calls moved to feature/api/ + hooks; settings + onboarding pages use feature hooks) _(completed 2026-04-26)_
- [x] 01.3-05-PLAN.md — Route group migration (public/auth/protected) + 3 group layouts + Light skeleton landing + delete app/[locale]/ mirror tree _(completed 2026-04-26)_
- [ ] 01.3-06-PLAN.md — MDX docs pipeline: install next-mdx-remote@6.0.0 + gray-matter; (public)/docs/page.tsx + [slug]/page.tsx + loading.tsx; 4 sample MDX files
- [ ] 01.3-07-PLAN.md — Locale dictionary additions: landing.* + docs.* + common.{nav,loading} mirrored to vi.json + en.json; update EN_SCAN_FILES in scripts/check-i18n.ts
- [ ] 01.3-08-PLAN.md — Final verification: full automated suite + manual checkpoints (Husky hook, Tailwind sort, visual); flip VALIDATION.md nyquist_compliant + wave_0_complete
**UI hint**: yes

### Phase 2A: Mail Ingestion
**Goal**: Receive Gmail push notifications reliably, keep `users.watch` alive, and process every history delivery idempotently with a tenant-visible global pause.
**Depends on**: Phase 1
**Requirements**: MAIL-01, MAIL-02, MAIL-03, MAIL-04, MAIL-05, MAIL-06
**Success Criteria** (what must be TRUE):
  1. When a new message arrives in a connected Gmail account, the system logs a corresponding `MessageObserved` event within seconds, attributable to the correct tenant.
  2. `users.watch` is renewed daily before its 7-day expiry, and a per-tenant health alert fires if any renewal fails.
  3. Replaying the same Pub/Sub delivery (same `historyId` + `messageId`) a second time produces no duplicate downstream effects — verifiable via audit trail in Phase 4.
  4. A Pub/Sub push request with a missing, expired, or wrong-audience Google OIDC token is rejected with 401 and never reaches business logic.
  5. A user can flip a "pause all automated triage" toggle and observe that new-message events are still received but no write actions are queued; after a history-404, the user sees a visible reconnect prompt instead of a full mailbox rescan.
**Plans**: TBD
**Research flag**: This phase should run through `/gsd-research-phase` before planning — Gmail `watch`/history edge cases and OIDC push-token verification need current-library lookup (see SUMMARY.md research flags).

### Phase 2B: Billing (Prepaid Credits)
**Goal**: Stand up a double-entry Postgres credit ledger with reserve/settle/release semantics and a watchdog, so that every billable action in later phases can charge credits safely under concurrency.
**Depends on**: Phase 1
**Requirements**: BILL-01, BILL-02, BILL-03, BILL-04, BILL-05, BILL-06, BILL-07
**Success Criteria** (what must be TRUE):
  1. A user can purchase prepaid credits via the selected payment provider (Stripe or LemonSqueezy) and see the new balance reflected in real time in the UI.
  2. Running the billable-action flow concurrently for the same tenant never double-charges and never loses credits — the ledger always reconciles.
  3. Orphaned credit holds (reserved but neither settled nor released) are swept back to the available balance by a scheduled watchdog within its interval.
  4. When a tenant's balance is insufficient, any billable action is blocked at the gateway with a clear UI prompt to top up — no partial debit.
  5. Actions performed under a BYOK key do not consume platform credits, and both balance and per-action cost are visible in the UI.
**Plans**: TBD
**UI hint**: yes

### Phase 2C: LLM Gateway
**Goal**: Ship the single `LlmGateway` abstraction on Spring AI 2.0.0-M4 that all LLM traffic must traverse, with full prompt-injection hardening, BYOK routing, metadata-only observability, per-tenant spend caps, and drift detection — the contract that Phase 4 triage will be built on.
**Depends on**: Phase 1 (hard gate — safety infrastructure must ship first)
**Requirements**: LLM-01, LLM-02, LLM-03, LLM-04, LLM-05, LLM-06, LLM-07, LLM-08, LLM-09, LLM-10, LLM-11
**Success Criteria** (what must be TRUE):
  1. Every LLM call in the codebase goes through `LlmGateway`; an ArchUnit test fails any direct `ChatClient`/vendor SDK usage outside the gateway.
  2. A message body containing HTML, hidden Unicode tag characters (U+E0000–U+E007F), or an injected "ignore previous instructions" payload is sanitized (Jsoup), NFC-normalized, tag-stripped, wrapped in the structured tool-call schema, and truncated to ≤4k tokens before any model call — verifiable via a prompt-injection test corpus.
  3. A tool call returning an action outside the per-action allow-list is rejected before it can be executed.
  4. A user-provided BYOK key (OpenAI, Anthropic, OpenRouter) is used for that user's calls without any server-side persistence of the key beyond the request scope, and BYOK calls bypass platform credit deduction.
  5. Per-tenant daily LLM spend cap blocks further billable calls when exceeded, no raw body/prompt/completion is persisted beyond the short-lived in-memory cache, and the golden-set drift job flags any regression on the fixed sample on schedule.
**Plans**: TBD
**Research flag**: This phase should run through `/gsd-research-phase` before planning — Spring AI 2.0.0-M4 per-request BYOK builder API and tokenizer choice must be verified in code, not from memory (see SUMMARY.md research flags).

### Phase 3: Rules Engine
**Goal**: Let users author, preview, and manage natural-language rules that compile to a deterministic matcher AST — with `SEMANTIC_INTENT` matchers deferred to Phase 4 for batched LLM evaluation.
**Depends on**: Phase 2C (needs `LlmGateway` for compilation), Phase 2A (preview runs against recent messages), Phase 2B (compile + preview are billable)
**Requirements**: RULE-01, RULE-02, RULE-03, RULE-04, RULE-05, RULE-06, RULE-07
**Success Criteria** (what must be TRUE):
  1. A user types "Archive receipts from Stripe and label them Finance" and the system compiles it into a structured matcher AST via a Spring AI tool-call — no free-form LLM output reaches runtime.
  2. The evaluator runs deterministic matchers against messages with no LLM call involved, and rules containing `SEMANTIC_INTENT` clauses are correctly deferred with a clear marker for batched evaluation.
  3. A user can preview a rule against the last N recent messages and see exactly which would match, before enabling the rule.
  4. A user can enable, disable, reorder, edit, and delete rules, and the changes take effect on the next message processed.
  5. A new user sees a template gallery of common v1 rules (receipts, newsletters, calendar invites) and can enable one with a single click.
**Plans**: TBD
**UI hint**: yes

### Phase 4: Triage Convergence (Hero)
**Goal**: The product. Orchestrate per-message triage: evaluate matchers in order, apply the safety policy layer, execute only allow-listed Gmail writes (label / archive / save-draft — never send), and leave an immutable audit trail with user-visible undo, shadow mode, and sender safety net.
**Depends on**: Phase 3 (rules), Phase 2C (LLM gateway — hard gate; no triage without sanitization + allow-list)
**Requirements**: TRG-01, TRG-02, TRG-03, TRG-04, TRG-05, TRG-06, TRG-07, TRG-08
**Success Criteria** (what must be TRUE):
  1. When a new message arrives in a connected Gmail account with active rules, the orchestrator evaluates matchers in rule order, applies at most the resulting allow-listed actions to Gmail, and the message visibly changes (label / archive / draft saved) within a few seconds.
  2. No code path can send mail on behalf of the user — an attempt to return a `SEND` action from any layer is rejected at the gateway and logged as a safety violation.
  3. Every automated action has an audit entry (message reference, rule, action, reason, timestamp) visible to the user, and the user can undo any action within the retention window and see the inverse Gmail change.
  4. A brand-new tenant's first N triage decisions are logged as would-apply but never written to Gmail until the user explicitly exits shadow mode.
  5. Messages from senders identified as frequent/important are not auto-acted on until the user opts that sender into automation, visible in a sender-safety-net UI.
**Plans**: TBD
**UI hint**: yes

### Phase 5: User Surface — Drafts, Analytics, Web UI
**Goal**: Deliver the complete user-facing surface: AI-drafted replies in Gmail, metadata-only analytics with a daily digest, and the Next.js 16 / React 19 frontend that ties every flow (onboarding, rules, audit, drafts, analytics, billing, privacy) together.
**Depends on**: Phase 4 (audit log + triage to surface), Phase 2B (billing UI), Phase 2A (pause toggle + connection health), Phase 1 (OpenAPI contract — note that `apps/web` scaffolding can begin in parallel right after Phase 1 once the OpenAPI stub is stable)
**Requirements**: DRFT-01, DRFT-02, DRFT-03, DRFT-04, ANL-01, ANL-02, ANL-03, WEB-01, WEB-02, WEB-03, WEB-04
**Success Criteria** (what must be TRUE):
  1. A user can request an AI draft reply for a thread, find it in Gmail as a normal draft with correct `In-Reply-To` and `References` headers, recognizable tone-matched phrasing, and must review before sending — no code path auto-sends.
  2. The analytics screen shows volume triaged, estimated time saved, top senders, and rule hits over a user-selectable window, derived from per-message metadata only (no bodies, prompts, or completions stored or queried).
  3. Each day, a connected tenant receives a digest email summarizing triage activity for the prior day.
  4. The `apps/web` Next.js 16 / React 19 frontend covers onboarding, rule CRUD with live preview, triage audit log with undo, draft review, analytics, billing, and an in-product privacy page explaining no-stored-bodies, no-auto-send, and the BYOK option.
  5. A persistent UI region (header or sidebar) surfaces the global pause toggle, real-time credit balance, and tenant connection health on every authenticated screen.
**Plans**: TBD
**UI hint**: yes

### Phase 6: Polish & CASA-Verified Launch
**Goal**: Harden end-to-end flows, close CASA restricted-scope verification (initiated in Phase 1), and produce a launch-ready build. No new REQ-IDs — this phase validates everything prior.
**Depends on**: Phase 5, plus external CASA lab sign-off
**Requirements**: _(none — integration, hardening, external verification close-out)_
**Success Criteria** (what must be TRUE):
  1. A fresh end-to-end run (sign up → connect Gmail → enable template rule → receive message → triage → undo → draft → analytics) completes without errors on a clean environment.
  2. Load/concurrency tests with N concurrent tenants sustain triage throughput without cross-tenant leakage, log bleed, or ledger inconsistency.
  3. CASA restricted-scope verification for Gmail scopes is completed (Tier decision logged), and the Google OAuth consent screen is moved from Testing to Production.
  4. Prompt-injection regression suite, ArchUnit suite, and golden-set drift check all pass on the release candidate commit.
  5. Production runbook exists (on-call, Pub/Sub backlog recovery, `users.watch` renewal incidents, ledger reconciliation) and a launch go/no-go has been signed off against the trust story (never auto-sends, no stored bodies, undoable actions).
**Plans**: TBD

## External Track (not a phase)

**CASA restricted-scope verification** is a 4–12 week external dependency executed in parallel with Phases 1 → 6. It is initiated during Phase 1 OAuth wiring (FND-07) and must be closed before Phase 6 launch go/no-go. It is tracked as a project risk, not as a phase, because no engineering work is gated on it until launch.

## Progress

**Execution Order:**
Phase 1 → Phase 1.1 → Phase 1.2 → Phase 1.3 → {Phase 2A ∥ Phase 2B ∥ Phase 2C} → Phase 3 → Phase 4 → Phase 5 → Phase 6

Parallelization: Phases 2A, 2B, and 2C can run concurrently once Phase 1 completes. Phase 5 starts after Phase 4, but `apps/web` scaffolding may start immediately after Phase 1 once the OpenAPI stub is stable.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation & Safety Infrastructure | 9/9 | Complete (CASA filing pending external) | 2026-04-25 |
| 1.1. Vietnamese-first i18n and error-handling foundation (INSERTED) | 8/8 | Complete | 2026-04-26 |
| 1.2. Domain-owned persistence restructuring (INSERTED) | 6/6 | Complete | 2026-04-26 |
| 1.2.1. Shared base entity + IdentifiedEnum standard + DTO group-by-domain (INSERTED) | 2/4 | Executing | - |
| 1.3. Frontend Architecture Refactor and Public Content Foundation (INSERTED) | 0/TBD | Not started | - |
| 2A. Mail Ingestion | 0/TBD | Not started | - |
| 2B. Billing (Prepaid Credits) | 0/TBD | Not started | - |
| 2C. LLM Gateway | 0/TBD | Not started | - |
| 3. Rules Engine | 0/TBD | Not started | - |
| 4. Triage Convergence (Hero) | 0/TBD | Not started | - |
| 5. User Surface — Drafts, Analytics, Web UI | 0/TBD | Not started | - |
| 6. Polish & CASA-Verified Launch | 0/TBD | Not started | - |
