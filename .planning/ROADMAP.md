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
- [x] **Phase 1.2.1: Shared base entity + IdentifiedEnum standard + DTO group-by-domain (INSERTED)** _(completed 2026-04-26)_ - Introduce `core.shared.persistence` abstract entity hierarchy (`AbstractEntity`, `AbstractAuditableEntity`, `AbstractTenantOwnedEntity`); introduce `core.shared.lang.IdentifiedEnum` interface (id/weight/labelKey) and apply to `OnboardingStep` + `GmailConnectionStatus`; reorganize `backend/api/dto/` group-by-domain (account/, gmail/, onboarding/) and rename `TenantStatusResponse` → `GmailConnectionStatusResponse`; close code review WR-01 (Pitfall 5 real persistence test), WR-02 (replace ordinal() with weight()), WR-03 (bulk delete query)
- [x] **Phase 1.3: Frontend Architecture Refactor and Public Content Foundation (INSERTED)** _(completed 2026-04-26)_ - Reorganize `apps/web` around route groups, feature folders, typed OpenAPI boundaries, frontend quality gates, and public landing/docs scaffolding without implementing the final landing/docs design yet
- [x] **Phase 1.4: Gmail Identity Semantics, Permission UX, and UI Consistency (INSERTED)** _(completed 2026-04-27 — closed without ship; remaining value superseded by Phase 1.5)_ - Align v1 auth so the Google login account IS the first managed Gmail account; treat initial Gmail access as incremental consent for that same account; reject mismatched initial Gmail OAuth callbacks; keep multi-account management as a later workspace-level capability (users add more Gmail accounts to a workspace); sweep UI consistency, visual polish, layout quality, copy, states, and reusable frontend patterns across the current app via the `frontend-design` skill
- [x] **Phase 1.5: Inbox-Zero Alignment: Bundled OAuth + UX Polish + Cleanup Sweep (INSERTED)** _(completed 2026-04-28)_ - Remaining heavy Phase 1.5 work: single Google OAuth upfront Gmail scope, remove google-gmail mismatch architecture, merge Gmail token provisioning, simplify onboarding/consent UX, deflate frontend primitives, polish landing/login/onboarding/settings/ReconnectPrompt, and close surviving REVIEW cleanup; excludes quick tasks already completed
- [x] **Phase 2A: Mail Ingestion** _(completed 2026-04-29)_ - Gmail `users.watch` + Pub/Sub push + OIDC verification + idempotent history processing + global pause
- [x] **Phase 2B: Billing (Prepaid Credits)** _(completed 2026-05-06)_ - Double-entry Postgres ledger, reserve/settle/release, credit-hold watchdog, SePay/VietQR top-up intent + webhook, balance API hooks
- [ ] **Phase 2C: LLM Gateway** - Spring AI 2.0.0-M6 `LlmGateway` with sanitize → Unicode strip → structured tool-call + allow-list → BYOK per-request options → daily spend cap → drift detection
- [x] **Phase 3: Rules Engine** _(completed 2026-05-10)_ - NL → structured matcher AST via Spring AI tool-call, deterministic evaluator, live preview, CRUD + reorder, template gallery
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

### Phase 01.6: Brand Identity, Design Tokens, and Landing Page (INSERTED)

**Goal:** `apps/web` adopts the Zero Mail brand identity (Teal accent + Paper-warm neutrals + Earnest trust copy), exposes a demo-ready landing page at `/` with hero + how-it-works + features + trust-pillars sections, and reskins Phase 1.5 sign-in + onboarding surfaces to match the reference visual tone — without changing any backend integration, OAuth flow, or route contract.
**Depends on:** Phase 1.5
**Requirements**: see `.planning/phases/01.6-brand-identity-design-tokens-and-landing-page/01.6-SPEC.md` (9 phase-internal requirements: design tokens, typography stack, landing page sections, public layout shell, ZMLogoMark, Phase 1.5 reskin, theme cookie persistence, sign-in TrustPanel + legal footer, i18n VI/EN coverage)
**Success Criteria** (what must be TRUE):
  1. `/` renders 4 sections (Hero, HowItWorks 3 steps, Features, TrustPillars) on Teal-locked Paper-warm tokens; Lighthouse mobile + desktop ≥ 90 for Performance/Accessibility/Best-Practices; responsive from 320px viewport upward without horizontal scroll.
  2. Geist Sans + Geist Mono + Be Vietnam Pro (Vietnamese subset) + Instrument Serif load via `next/font`; `font-sans`/`font-mono`/`font-serif` Tailwind utilities resolve correctly; Vietnamese diacritics render with correct glyphs.
  3. `(auth)/login` shows TrustPanel + 2-column shell at desktop (≥768px), single-column form at mobile; legal footer (Terms / Privacy / Google API Limited Use disclosure) renders at every viewport beneath the OAuth button; bundled Google OAuth flow from Phase 1.5 D-A1 is unchanged.
  4. Onboarding 3 screens (`gmail-connect`, `template-select`, `complete`) render with `<AuthTopBar>` + named-step pill `<StepIndicator>`; `OnboardingStep` enum + state machine + route paths unchanged.
  5. Theme toggle persists via `zm-theme` cookie set by a Next 16 Server Action; first-paint HTML respects cookie value with no flash; no `localStorage` usage anywhere.
  6. i18n parity: every visible string flows through `next-intl` keys; `apps/web/i18n/messages/{vi,en}.json` lock-step on new namespaces (`nav.*`, `trust.*`, `how.*`, `feat.*`, `legal.*`, `footer.*`, `onboarding.steps.*`); `pnpm i18n:check` passes.
  7. `01.6-VISUAL-SWEEP.md` checklist proves Phase 1.5 components (Alert variant=warning, ReconnectPrompt, Login form, 3 onboarding cards) survive token swap — every row PASS for contrast WCAG AA, layout intact at 320/768/1024 px, focus ring visible, dark-mode renders, OAuth click path still ends at `/welcome` or correct onboarding step.
**Plans:** 7/8 plans executed
**Research flag**: COMPLETE — Tailwind 4 `@theme inline`, Next.js 16 `next/font` Vietnamese subset, Server Action cookie write, shadcn primitive token-rebind verified in 01.6-RESEARCH.md.

Plans:
- [x] 01.6-00-PLAN.md — Wave 0 test scaffolding (red specs + i18n scanner extension)
- [x] 01.6-01-PLAN.md — Wave 1 design tokens (Teal + paper-warm + supplemental tokens + retuned radii/warning/destructive)
- [x] 01.6-02-PLAN.md — Wave 1 typography stack (Be Vietnam Pro vietnamese subset + Instrument Serif)
- [x] 01.6-03-PLAN.md — Wave 2 public layout shell (TopBar + Footer + ZMLogoMark + ThemeToggle + setTheme + cookie read)
- [x] 01.6-04-PLAN.md — Wave 2 landing sections (Hero + HowItWorks + Features + TrustPillars + landing/how/feat/trust i18n)
- [x] 01.6-05-PLAN.md — Wave 3 sign-in 2-col reskin (AuthTopBar + TrustPanel + LegalFooter) + /terms /privacy stubs
- [x] 01.6-06-PLAN.md — Wave 3 onboarding 3-route split + StepIndicator + onboarding.steps.* i18n
- [ ] 01.6-07-PLAN.md — Wave 4 visual sweep + Lighthouse audit + Phase 1.5 regression checklist

### Phase 1.1: Vietnamese-first i18n and error-handling foundation (INSERTED)
**Goal**: Establish a Vietnamese-default, English-secondary i18n architecture across `backend/api`, `backend/core`, and `apps/web`, with a user-facing language switcher and a stable, frontend-localizable API error contract — referencing the local JHipster project's proven patterns where they fit Spring Boot 4 / Next.js 16. All Phase 1 privacy/safety constraints (no body/prompt/completion in logs, no PII in error payloads, tenant isolation via Scoped Values, ArchUnit bans) must remain intact.
**Depends on**: Phase 1 (needs OpenAPI skeleton, Spring Security session cookie, log-scrub contract, and `apps/web` scaffold to land first)
**Requirements**: REQ-1, REQ-2, REQ-3, REQ-4, REQ-5, REQ-6, REQ-7 (locked in 01.1-SPEC.md)
**Success Criteria** (what must be TRUE):
  1. Default UI language is Vietnamese; English is selectable via a persistent in-product language switcher; preference is stored per user and survives session.
  2. Backend error responses follow a stable contract (machine-readable code + parameters) that the frontend localizes — no human-readable Vietnamese/English strings are constructed server-side for user-facing errors.
  3. Both Vietnamese and English message bundles cover every user-facing string in scope (auth, onboarding, settings, errors); a CI check fails the build on missing keys.
  4. ArchUnit and log-scrub guarantees from Phase 1 still pass; no localized error message contains PII, email body, prompt, or completion content.
  5. The JHipster reference patterns adopted are documented in CONTEXT.md with a clear "what we kept / what we adapted / what we rejected" note (Spring Boot 4 / Spring AI 2.0.0-M6 / Next.js 16 fit).
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
- [x] 01.3-06-PLAN.md — MDX docs pipeline: install next-mdx-remote@6.0.0 + gray-matter; (public)/docs/page.tsx + [slug]/page.tsx + loading.tsx; 4 sample MDX files _(completed 2026-04-26)_
- [x] 01.3-07-PLAN.md — Locale dictionary additions: landing.* + docs.* + common.{nav,loading} mirrored to vi.json + en.json; update EN_SCAN_FILES in scripts/check-i18n.ts; flip lint-staged i18n:check to STRICT mode (REVIEWS Revision 3 closure) _(completed 2026-04-26)_
- [x] 01.3-08-PLAN.md — Final verification: full automated suite GREEN (tsc + vitest 80/80 + i18n:check 87 keys STRICT + ESLint); 6 Wave 0 files GREEN 56/56 assertions; proxy.ts `as unknown as` cast preserved (3 occurrences); schema-diff REVIEWS Revision 7 gate PASSED via source-control proof (zero commits to schema.d.ts in 01.3 range); VALIDATION.md flipped `nyquist_compliant: true` + `wave_0_complete: true`; Playwright e2e + live `generate:api` round-trip deferred to user with replay commands _(completed 2026-04-26)_
**UI hint**: yes

### Phase 1.4: Gmail Identity Semantics, Permission UX, and UI Consistency (INSERTED)
**Goal**: Align v1 Gmail identity semantics so the Google login account is the first managed Gmail account, convert the incremental `gmail.modify` OAuth grant into the durable Gmail connection record, and run a bounded frontend UI consistency sweep across the current app without finalizing the Phase 5 brand identity.
**Depends on**: Phase 1.3 (keeps the frontend route-group and feature-folder structure stable before the consistency sweep)
**Requirements**: _(no new product requirements; tightens AUTH-01/AUTH-02 behavior and closes frontend polish/error-boundary gaps)_
**Success Criteria** (what must be TRUE):
  1. The second OAuth leg for `google-gmail` rejects any Google account whose OIDC subject does not match the logged-in user's stored Google subject; no Gmail connection is persisted on mismatch.
  2. A matching Gmail grant idempotently upserts the tenant's single `GmailConnectionEntity`, encrypts the refresh token with the existing AES-GCM envelope, stores granted scopes, marks status `CONNECTED`, and advances onboarding from `SIGNED_IN` to `GMAIL_CONNECTED`.
  3. Gmail permission UX uses `login_hint` for the current user email, distinguishes identity mismatch from consent denied, redirects back to `/onboarding` with machine-readable error codes, and localizes both states in Vietnamese and English without exposing subjects, tokens, or stack traces.
  4. Current frontend surfaces share a Tailwind 4 token layer and five reusable primitives (`PageShell`, `SectionCard`, `StatusAlert`, `EmptyState`, `LoadingState`), with hard-coded ad-hoc styling replaced across login, onboarding, settings, landing, docs, and existing shared components.
  5. Next.js App Router error fallbacks exist for root, public, auth, protected, and 404 states; they use the new primitives where provider context allows, never render raw exception details, and pass the existing frontend i18n/type/lint checks.
**Plans**: 6 plans
- [x] 01.4-01-PLAN.md — [BLOCKING] Wave 0 test scaffolds (5 backend + 9 frontend Vitest, MockGoogleRevocationServer fixture) _(completed 2026-04-26)_
- [x] 01.4-02-PLAN.md — Backend OAuth2 dispatcher: GmailIdentityMismatchException + Success/Failure handlers + Dispatching beans + GoogleTokenRevocationClient + ErrorCodes + SecurityConfig wiring _(completed 2026-04-26)_
- [x] 01.4-03-PLAN.md — Backend extensions: GmailConnectionService.upsert(...) idempotent + GmailScopeRequestResolver login_hint graceful-degrade _(completed 2026-04-27)_
- [x] 01.4-04-PLAN.md — Frontend primitives: PageShell + SectionCard + StatusAlert + EmptyState + LoadingState (compose existing shadcn, no new installs) _(completed 2026-04-27)_
- [x] 01.4-05-PLAN.md — Next.js error-boundary baseline: global-error.tsx + not-found.tsx + 3 segment error.tsx + i18n key bundle (vi+en) _(completed 2026-04-27)_
- [x] 01.4-06-PLAN.md — Token sweep across 12 surfaces + onboarding mismatch alert wiring + settings singleAccountNote + final phase gates _(completed 2026-04-27 — Phase 01.4 closed without ship; follow-up cleanup moved to Phase 01.5)_
**UI hint**: yes

### Phase 1.5: Inbox-Zero Alignment: Bundled OAuth + UX Polish + Cleanup Sweep (INSERTED)

**Goal**: Finish the remaining heavy Phase 1.5 work after the quick-task sweep: align with Inbox Zero's bundled Google OAuth model, remove the now-unnecessary two-registration Gmail mismatch architecture, simplify onboarding and consent-denied UX, deflate custom frontend primitives back to raw shadcn/token-aware composition, polish the current landing/login/onboarding/settings/reconnect surfaces, and close surviving REVIEW cleanup that still applies.
**Requirements**: _(no new product requirements; pivots AUTH-01/AUTH-02 behavior and frontend architecture after Phase 1.4 review)_
**Depends on:** Phase 1.4
**Success Criteria** (what must be TRUE):
  1. The primary Google OAuth registration requests `openid profile email https://www.googleapis.com/auth/gmail.modify` upfront, persists Gmail connection tokens during Google login provisioning, and removes the `google-gmail` registration plus mismatch/revocation handler architecture.
  2. Gmail provisioning and token persistence run in the same transaction as user provisioning, so a successful login creates or updates the tenant's Gmail connection without a separate onboarding "connect Gmail" step.
  3. Onboarding routes directly to template selection after provisioning; consent-denied UX uses a simple `/login?error=...` path, while reconnect still uses the single `google` registration with `prompt=consent`.
  4. Phase 1.4 frontend wrapper primitives are deflated where they add no value; retained primitives must justify real composition, and affected surfaces use raw shadcn components plus token-aware `className`.
  5. Surviving REVIEW cleanup is closed or explicitly documented: `/me` fetch dedupe/cache strategy, `TemplateCard` `cn()`, retained `ReconnectPrompt` token variant, CLAUDE.md conventions, and deferred verification ceremonies.
**Plans**: 5 plans
- [x] 01.5-01-PLAN.md — Backend bundled OAuth collapse + onboarding state machine simplification + 2-leg flow deletion
- [x] 01.5-02-PLAN.md — Atomic frontend deflation + Alert warning variant + RSC login error rendering + i18n key swap
- [x] 01.5-03-PLAN.md — /me fetch React cache() per-request dedupe
- [x] 01.5-04-PLAN.md — frontend-design skill polish pass across 5 surfaces (landing/login/onboarding/settings/ReconnectPrompt)
- [x] 01.5-05-PLAN.md — CLAUDE.md Conventions promotion + STATE.md deferred verification ceremonies
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
**Plans**: 6 plans

Plans:
- [x] 02A-00-PLAN.md — Wave 0 RED test scaffolds (10 backend test classes + 2 fixtures + 4 frontend test files)
- [x] 02A-01-PLAN.md — Schema (Liquibase 010-013) + entities + enum
- [x] 02A-02-PLAN.md — Worker schedulers (GmailWatchScheduler + GmailHistoryProcessor)
- [x] 02A-03-PLAN.md — API layer (PubSubOidcAuthFilter + push receiver + triage-pause controller)
- [x] 02A-04-PLAN.md — Frontend (PauseBanner + settings toggle + ReconnectPrompt gate + i18n)
- [x] 02A-05-PLAN.md — Full verification sweep + closure

Cross-cutting constraints:
- Pub/Sub OIDC security is active under the test profile; missing or invalid tokens return 401 before business logic.
- TenantContext is bound before tenant-scoped persistence transactions open; unscoped Gmail email lookup uses parameterized JdbcTemplate only.
- Delivery and observed-message idempotency use native INSERT ... ON CONFLICT DO NOTHING, not caught JPA DataIntegrityViolationException paths.
- No raw email content, token values, or Google email addresses are logged or persisted outside the explicitly allowed owner-visible response field.

### Phase 2B: Billing (Prepaid Credits)
**Goal**: Stand up a double-entry Postgres credit ledger with reserve/settle/release semantics and a watchdog, so that every billable action in later phases can charge credits safely under concurrency.
**Depends on**: Phase 1
**Requirements**: BILL-01, BILL-02, BILL-03, BILL-04, BILL-05, BILL-06, BILL-07
**Success Criteria** (what must be TRUE):
  1. A user can create a SePay/VietQR top-up intent, complete the Vietnam-beta bank transfer flow, and have the signed webhook idempotently credit the ledger; UI rendering remains Phase 5.
  2. Running the billable-action flow concurrently for the same tenant never double-charges and never loses credits — the ledger always reconciles.
  3. Orphaned credit holds (reserved but neither settled nor released) are swept back to the available balance by a scheduled watchdog within its interval.
  4. When a tenant balance is insufficient, any billable action is blocked at the gateway with a clear UI prompt to top up — no partial debit.
  5. Actions performed under a BYOK key do not consume platform credits, and both balance and per-action cost are visible in the UI.
**Plans**: 7 plans

Plans:
- [x] 02B-00-wave0-tests-PLAN.md — Wave 0 RED test scaffolds (7 core + 8 api + 2 worker = 17 files; flip VALIDATION nyquist_compliant true)
- [x] 02B-01-schema-and-deps-PLAN.md — Liquibase 014 (credit_ledger_entry) + 015 (credit_reservation) + 016 (billing_topup_intent) + 017 (shedlock); ShedLock 7.7.0 + worker build wiring
- [x] 02B-02-domain-model-PLAN.md — core.billing Modulith leaf + billing model/service contract package (CreditLedger interface + 3 enums + 2 records + 2 exceptions; BYOK Javadoc clause)
- [x] 02B-03-credit-ledger-service-PLAN.md — 3 entities + 3 repositories + AdvisoryLockJdbcHelper + nested ZeroMailCoreProperties billing settings + SepayApiKeyVerifier + TopupCodeGenerator + CreditLedgerService (REQUIRES_NEW + advisory lock) + BillingTopupService
- [x] 02B-04-api-surface-PLAN.md — 4 DTOs + BillingController + SepayWebhookController + @Order(2) BillingWebhookSecurityConfig + SepayApiKeyAuthFilter + ErrorCodes + GlobalExceptionHandler 402/500 + i18n vi/en + schema.d.ts regen
- [x] 02B-05-worker-schedulers-PLAN.md — CreditReserveWatchdog (60s + ShedLock + ScopedValue tenant binding) + BillingIntentExpirySweeper (1h) + worker application.yml :? fail-fast (close CR-04 carryover)
- [x] 02B-06-verification-closure-PLAN.md — DomainBoundaryArchTests (5th billing rule) + BillingDomainBoundaryArchTest GREEN + CallSiteEnumMembershipArchTest GREEN + REQUIREMENTS.md BILL-01..BILL-07 flip + ./gradlew clean check
**UI hint**: yes

### Phase 2C: LLM Gateway
**Goal**: Ship the single `LlmGateway` abstraction on Spring AI 2.0.0-M6 that all LLM traffic must traverse, with full prompt-injection hardening, BYOK routing, metadata-only observability, per-tenant spend caps, and drift detection — the contract that Phase 4 triage will be built on.
**Depends on**: Phase 1 (hard gate — safety infrastructure must ship first)
**Requirements**: LLM-01, LLM-02, LLM-03, LLM-04, LLM-05, LLM-06, LLM-07, LLM-08, LLM-09, LLM-10, LLM-11
**Success Criteria** (what must be TRUE):
  1. Every LLM call in the codebase goes through `LlmGateway`; an ArchUnit test fails any direct `ChatClient`/vendor SDK usage outside the gateway.
  2. A message body containing HTML, hidden Unicode tag characters (U+E0000–U+E007F), or an injected "ignore previous instructions" payload is sanitized (Jsoup), NFC-normalized, tag-stripped, wrapped in the structured tool-call schema, and truncated to ≤4k tokens before any model call — verifiable via a prompt-injection test corpus.
  3. A tool call returning an action outside the per-action allow-list is rejected before it can be executed.
  4. A user-provided BYOK key (OpenAI, Anthropic, OpenRouter) is used for that user's calls without any server-side persistence of the key beyond the request scope, and BYOK calls bypass platform credit deduction.
  5. Per-tenant daily LLM spend cap blocks further billable calls when exceeded, no raw body/prompt/completion is persisted beyond the short-lived in-memory cache, and the golden-set drift job flags any regression on the fixed sample on schedule.
**Plans**: 8 plans
**Research flag**: COMPLETE — Spring AI 2.0.0-M6 BYOK seam (`OpenAiChatModel.builder().options(...)` for platform/OpenAI-compatible, `ChatClient.prompt().options(builder)` for runtime deltas, `AnthropicChatOptions.builder().apiKey().baseUrl().model()` for Anthropic), jtokkit 1.1.0 + cl100k_base, Liquibase floor 019, all verified in 02C-RESEARCH.md and implementation.

Plans:
- [x] 02C-01-PLAN.md — Wave 1 foundation: package skeleton + libs.versions.toml (Spring AI BOM + jtokkit) + Liquibase 018 BYOK schema + ArchUnit boundary test + Wave 0 RED scaffolds
- [x] 02C-02-PLAN.md — Wave 1 sanitization pipeline: Jsoup -> NFC -> Unicode-tag-strip -> jtokkit truncate(3896) + corpus test (5 prompt-injection fixtures)
- [x] 02C-03-PLAN.md — Wave 2 gateway core: LlmGateway interface + LlmGatewayImpl skeleton + PlatformApiKey + PlatformChatClientConfig + ZeroMailLlmProperties + application.yml fail-fast + observation pins + multi-tenant leak test
- [x] 02C-04-PLAN.md — Wave 3 tool-call allow-list: ActionValidator + SafetyViolationException + Layer 1 (toolChoice=required) + Layer 2 (validator) wired into LlmGatewayImpl
- [x] 02C-05a/05b-PLAN.md — Wave 4 BYOK: BYOKChatModelFactory + 2 asymmetric impls + ByokService + ByokController + 5 DTOs + 4 ErrorCodes + 3 GlobalExceptionHandler mappings
- [x] 02C-06-PLAN.md — Wave 5 credit cap: CreditLedger reserve/settle/release wrapping platform path; BYOK + driftCheck skip ledger; 100-call concurrent test
- [x] 02C-07-PLAN.md — Wave 6 drift detection: DriftDetectionJob + golden-set.json (~20 synthetic fixtures) + golden-baseline.json + 2 CI mock tests; cron defaults disabled
- [x] 02C-08-PLAN.md — Wave 6 frontend BYOK form: features/llm/ triplet + ByokForm.tsx (frontend-design skill) + i18n vi/en + mounted on /settings

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
**Plans**: 10 plans
**Research flag**: COMPLETE — Phase 3 research, AI-SPEC, UI-SPEC, validation strategy, and implementation patterns are available in `.planning/phases/03-rules-engine/`.

Plans:
**Wave 0 — validation spine**
- [x] 03-00-PLAN.md — Wave 0 contract tests for AST, persistence, compiler, evaluator, preview, templates, API, UI, and boundary rules

**Wave 1 — foundations**
- [x] 03-01-PLAN.md — Rules Modulith package, model vocabulary, Liquibase/JPA persistence, template catalog seed data, and D-D1 boundary extension
- [x] 03-02-PLAN.md — Gateway-owned `RULE_COMPILE` tool path, dedicated compile result, and prompt fixture for structured rule compilation

**Wave 2 — core behavior**
- [x] 03-03-PLAN.md — Rule compiler, result validation, CRUD, reorder, enable/disable, and preview-before-enable state transitions
- [x] 03-04-PLAN.md — Deterministic tri-state evaluator and action proposal merge/conflict handling

**Wave 3 — preview and templates**
- [x] 03-05-PLAN.md — Side-effect-free recent-message preview with transient Gmail reads and privacy assertions
- [x] 03-06-PLAN.md — DB-backed template catalog and idempotent onboarding-template materialization through `OnboardingService`

**Wave 4 — API**
- [x] 03-07-PLAN.md — Thin rules controller, DTO/error mapping, tenant/privacy tests, and regenerated OpenAPI/schema artifacts

**Wave 5 — frontend**
- [x] 03-08-PLAN.md — Protected `/rules` page, typed feature API/hooks, i18n, Vitest contracts, and Playwright desktop/mobile flow

**Wave 6 — closure**
- [x] 03-09-PLAN.md — Full verification, privacy/architecture audit, requirement traceability, UAT, and Phase 4 handoff

Cross-cutting constraints:
- `core.rules` must not import Spring AI/vendor SDKs; all model interaction stays behind `LlmGateway`.
- No raw Gmail headers, snippets, bodies, prompts, completions, tool args, or token bytes may be persisted, logged, or returned.
- Rules stay disabled until a successful preview for the exact saved rule version.
- Edited rules clear preview eligibility and require a fresh preview for the current entity version before enablement.
- Rule reordering uses tenant-qualified optimistic version checks and fails all-or-nothing on conflicts.
- Cross-domain reads use owning services, not another domain's repositories; onboarding template selections are exposed through `OnboardingService`.
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

  Note (interview round 1, 2026-05-11): shadow mode is reframed to an opt-in tenant-wide toggle (default OFF), not a count-based auto-unlock; all triage UI (audit log, undo button, shadow toggle, sender-safety-net management) is deferred to Phase 5 — Phase 4 ships backend + REST only.
**Plans**: 9 plans
**Research flag**: COMPLETE — `04-SPEC.md`, `04-CONTEXT.md`, `04-AI-SPEC.md`, `04-RESEARCH.md`, `04-PATTERNS.md`, `04-VALIDATION.md` in `.planning/phases/04-triage-convergence-hero/`.

Plans:
**Wave 1**
- [x] 04-00-PLAN.md — [BLOCKING] Wave 0: spring-modulith-starter-jdbc dependency + RED test spine (core/api/worker scaffolds, 4 ArchUnit guards, CallSite membership 3->5, eval-harness dir marker)
- [x] 04-01-PLAN.md — Modulith JDBC event spine: MailMessageObserved event + publish site + Liquibase 024 (event_publication) + core.triage package skeleton + TenantContext.runWith

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 04-02-PLAN.md — Triage persistence + domain: Liquibase 025-027 + TriageActionResult sealed type + validator + canonicalizer + TriageDecision + TriageAuditEntity/Repository + TenantSenderOptInEntity/Repository + 5 exceptions + CallSite extension + TenantEntity.triageShadowMode + TenantService accessors

**Wave 3** *(blocked on Wave 2 completion)*
- [ ] 04-03-PLAN.md — LlmGateway.evaluateSemanticIntents (strict-JSON-Schema classifier) + SemanticIntentEvaluator/Response + SemanticIntentRequest + 2 gateway exceptions + worker model pin gpt-5.4-nano + semanticIntentEval Gradle task

**Wave 4** *(blocked on Wave 3 completion)*
- [ ] 04-04-PLAN.md — Triage services: TriageSafetyPolicy (allow-list gate) + TriageGmailWriter (single Gmail-write call site, send-free) + SenderSafetyNetService (sent-history heuristic + Redis 24h cache + opt-in override) + core.gmail Gmail-client facade + Redis bean wiring

**Wave 5** *(blocked on Wave 4 completion)*
- [ ] 04-05-PLAN.md — TriageOrchestratorService (@ApplicationModuleListener hero: tenant rebind -> rules -> inline SEMANTIC_INTENT via LlmGateway -> safety gate -> sender net -> two-phase PENDING->APPLIED audit loop -> Gmail/shadow) + metadata-only input facade + worker.triage package-info
- [ ] 04-06-PLAN.md — TriageUndoService (compute-inverse, 30d window, exhaustive switch) + 3 thin triage controllers (undo / shadow-mode / sender-safety-net) + DTOs + ErrorCodes + GlobalExceptionHandler + vi/en i18n + schema.d.ts regen

**Wave 6** *(blocked on Wave 5 completion)*
- [ ] 04-07-PLAN.md — worker.triage jobs: TriageEventRetryJob + TriageEventCleanupJob + TriageAuditPurgeJob/Batch (30d retention) + TriagePendingReaperJob/Batch (PENDING never lives forever) - all ShedLock-coordinated

**Wave 7** *(blocked on Wave 6 completion)*
- [ ] 04-08-PLAN.md — Closure: TriagePrivacySweepTest (FND-03-analogous) + ./gradlew clean check green + TRG-01..TRG-08 -> Complete + 04-VALIDATION.md sign-off + 04-UAT.md

**UI hint**: yes (Phase 5)

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
Phase 1 → Phase 1.1 → Phase 1.2 → Phase 1.2.1 → Phase 1.3 → Phase 1.4 → Phase 1.5 → {Phase 2A ∥ Phase 2B ∥ Phase 2C} → Phase 3 → Phase 4 → Phase 5 → Phase 6

Parallelization: Phases 2A, 2B, and 2C can run concurrently once Phase 1 completes. Phase 5 starts after Phase 4, but `apps/web` scaffolding may start immediately after Phase 1 once the OpenAPI stub is stable.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation & Safety Infrastructure | 9/9 | Complete (CASA filing pending external) | 2026-04-25 |
| 1.1. Vietnamese-first i18n and error-handling foundation (INSERTED) | 8/8 | Complete | 2026-04-26 |
| 1.2. Domain-owned persistence restructuring (INSERTED) | 6/6 | Complete | 2026-04-26 |
| 1.2.1. Shared base entity + IdentifiedEnum standard + DTO group-by-domain (INSERTED) | 4/4 | Complete | 2026-04-26 |
| 1.3. Frontend Architecture Refactor and Public Content Foundation (INSERTED) | 8/8 | Complete | 2026-04-26 |
| 1.4. Gmail Identity Semantics, Permission UX, and UI Consistency (INSERTED) | 6/6 | Complete without ship; superseded by 1.5 | 2026-04-27 |
| 1.5. Inbox-Zero Alignment: Bundled OAuth + UX Polish + Cleanup Sweep (INSERTED) | 7/8 | In Progress|  |
| 2A. Mail Ingestion | 6/6 | Complete | 2026-04-29 |
| 2B. Billing (Prepaid Credits) | 7/7 | Complete | 2026-05-06 |
| 2C. LLM Gateway | 0/8 | Not started | - |
| 3. Rules Engine | 10/10 | Complete | 2026-05-10 |
| 4. Triage Convergence (Hero) | 2/9 | In Progress | - |
| 5. User Surface — Drafts, Analytics, Web UI | 0/TBD | Not started | - |
| 6. Polish & CASA-Verified Launch | 0/TBD | Not started | - |
