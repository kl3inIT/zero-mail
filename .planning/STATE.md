---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 02C-01-PLAN.md
last_updated: "2026-05-07T12:27:34.292Z"
last_activity: 2026-05-07
progress:
  total_phases: 15
  completed_phases: 10
  total_plans: 80
  completed_plans: 72
  percent: 90
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-28)

**Core value:** AI auto-triage that users trust with their real Gmail inbox — triage quality, safety (no destructive or silently-sent actions), and reliability are non-negotiable.
**Current focus:** Phase 02C — llm-gateway

## Current Position

Phase: 02C (llm-gateway) — EXECUTING
Plan: 2 of 9
Status: Ready to execute
Last activity: 2026-05-07

Progress: [█████████░] 90%

## Performance Metrics

**Velocity:**

- Total plans completed: 24
- Average duration: —
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01.5 | 9 | - | - |
| 02B | 7 | - | - |

**Recent Trend:**

- Last 5 plans: (none yet)
- Trend: —

*Updated after each plan completion*
| Phase 01.1 P04 | 30min | 2 tasks | 5 files |
| Phase 01.1 P05 | 30min | 3 tasks | 13 files |
| Phase 1.1 P06 | 24min | 2 tasks | 13 files |
| Phase 1.1 P07 | 45min | 2 tasks | 11 files |
| Phase 1.1 P8 | 14min | 4 tasks | 5 files |
| Phase 1.2 P01 | 9min | 3 tasks | 11 files |
| Phase 1.2 P02 | 17min | 2 tasks | 16 files |
| Phase 1.2 PP03 | 9min | 3 tasks | 27 files |
| Phase 1.2 P04 | 6min | 3 tasks | 18 files |
| Phase 1.2 P05 | 11min | 3 tasks | 30 files |
| Phase 01.2 P06 | 25min | 3 tasks | 9 files |
| Phase 01.3 P01 | 6min | 2 tasks | 6 files |
| Phase 01.3 P02 | 6min | 2 tasks | 7 files |
| Phase 01.2.1 P01 | 14min | 3 tasks | 15 files |
| Phase 01.2.1 P02 | 5min | 1 task | 3 files |
| Phase 01.3 P03 | 5min | 2 tasks (T2 deferred) | 3 files |
| Phase 01.2.1 P03 | 18 | 3 tasks | 10 files |
| Phase 01.3 P04 | 10min | 4 tasks | 22 created + 9 modified + 5 relocated |
| Phase 01.3 P05 | 12min | 5 tasks | 5 created + 1 modified + 3 relocated + 5 deleted |
| Phase 01.2.1 P04 | 38min | 5 tasks | 17 files |
| Phase 01.3 P06 | 9min | 2 tasks | 8 created + 3 modified |
| Phase 01.3 P07 | 5min | 3 tasks | 1 created + 8 modified |
| Phase 01.3 P08 | 15min | 1 task (Task 2 = checkpoint:human-verify deferred per autonomous=false) | 0 code + 3 .planning artifacts |
| Phase 01.4 P01 | 23m | - tasks | - files |
| Phase 01.4 P02 | 10min | 3 tasks | 10 files |
| Phase 01.4 P03 | 15m | 3 tasks | 4 files |
| Phase 01.4 P04 | 25min | 2 tasks | 5 files |
| Phase 01.4 P05 | 38min | 2 tasks | 5 created + 5 modified |
| Phase 01.4 P06 | 28min | 2 tasks | 9 modified |
| Phase 01.5 P01 | 120 | - tasks | - files |
| Phase 01.5 P02 | 78min | 2 tasks | 28 files |
| Phase 01.5 P03 | 25m | 2 tasks | 4 files |
| Phase 01.5 P04 | 35min | 3 tasks | 7 files |
| Phase 01.5 P06 | 25min | 2 tasks | 2 files |
| Phase 01.5 P07 | 11 | 2 tasks | 4 files |
| Phase 01.5 P08 | 12min | 2 tasks | 4 files |
| Phase 01.5 P09 | 24min | 2 tasks | 5 files |
| Phase 01.6 P01 | 9min | 1 tasks | 2 files |
| Phase 01.6 P02 | 16min | 1 tasks | 2 files |
| Phase 01.6 P03 | 6min | 3 tasks | 10 files |
| Phase 01.6 P05 | 18min | 3 tasks | 8 files |
| Phase 01.6 P06 | 35min | 3 tasks | 12 files |
| Phase 02A P00 | 14min | 2 tasks | 17 files |
| Phase 02A P01 | 11min | 2 tasks | 18 files |
| Phase 02A P02 | 12min | 2 tasks | 15 files |
| Phase 02A P03 | 20min | 2 tasks | 26 files |
| Phase 02A P04 | 16min | 2 tasks | 15 files |
| Phase 02A P05 | 20min | 2 tasks | 11 files |
| Phase 02B P00 | 13min | 4 tasks | 20 files |
| Phase 02B P03 | 14min | 3 tasks | 25 files |
| Phase 02C P01 | 45min | 1 tasks | 42 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Roadmap: 8-phase topology with parallel sub-phases 2A/2B/2C (standard granularity, research-aligned)
- Roadmap: Phase 2C (LLM Gateway) hard-gated by Phase 1 safety infrastructure — prompt injection + log bleed are product-killing
- Roadmap: Phase 4 (Triage) hard-gated by Phase 2C — no triage without sanitization, Unicode strip, allow-list
- Roadmap: CASA restricted-scope verification tracked as external parallel track, initiated in Phase 1 (FND-07), closed before Phase 6 launch
- [Phase ?]: Use RestClient + LocalServerPort (not MockMvc.webAppContextSetup) for backend tests requiring TenantContext ScopedValue — MockMvc skips servlet filters and the test auth filter never binds the ScopedValue
- [Phase ?]: Phase 1.1 P06: Vitest dedupes react/react-dom + LanguageSwitcher inlines SVG/native button to escape pnpm's duplicate React install
- [Phase ?]: Phase 1.1 P07: Playwright must live at workspace root because Next.js declares @playwright/test as an optional peer dep — installing under apps/web doubles the next install on disk and breaks tsc at the next-intl/middleware boundary
- [Phase 1.2]: CL-3 Spring Modulith naming form locked = `"shared.privacy"` (dotted-nested literal). Probe A passed first try in `core.tenant/package-info.java`; Probes B (`"privacy"`) and C (`"shared :: privacy"`) not run. Plans 02-05 MUST use this exact literal in `allowedDependencies`. Documented in `core/shared/privacy/package-info.java` JavaDoc.
- [Phase 1.2]: Pitfall 1 closure protocol confirmed in practice — every string-typed FQN reference (logback XML `<filter class="..."/>`, ArchUnit literal constants, build-script `approvedPkg`, integration-test imports) MUST update in the same plan as Java class moves. Surface scan via `grep -rn 'old.fqn' backend/ apps/ buildSrc/` is the verification gate.
- [Phase 1.2]: Plan 02 confirmed the per-domain `persistence/` + `persistence/lowlevel/` shape (intra-domain marker, no `@ApplicationModule`); Plans 03/04/05 reuse this exact shape for account/onboarding/gmail. The proactive `lowlevel/` package-info marker prevents Plan 06's regex-update from silent-no-oping.
- [Phase 1.2]: Plan 02 sweep folded 10 stale-import sites (2 production, 8 test) into the same commit as the `git mv` — no fix-up commit needed (vs Plan 01's `9769dd7`). Discipline: Edit the working tree of renamed files BEFORE staging, never after the first commit.
- [Phase ?]: [Phase 1.2]: Plan 03 confirmed CL-2 reshape pattern — AccountService dropped 4 cross-domain repos, kept only UserRepository; new deleteCurrentUser is single-domain. Multi-domain orchestration moves to AccountDeletionController as transitional bridge across Plans 03→06.
- [Phase ?]: [Phase 1.2]: Forward-decl deferral protocol locked: never declare a Modulith allowedDependencies edge to a non-existent module. core.account/package-info.java declares {tenant, shared.privacy} now; Plan 04 amends to add 'onboarding' once that module exists on disk.
- [Phase 1.2]: Plan 04 closed Pitfall 5 (enum-name persistence drift) via OnboardingStepEnumPersistenceTest — pure-JVM unit test asserting OnboardingStep.{...}.name() match stable strings. Pattern: when relocating @Enumerated(EnumType.STRING) enums, ship a name() literal-assert test in the same plan.
- [Phase 1.2]: Plan 04 confirmed atomic bidirectional Modulith edge protocol: when introducing a new module that an existing module already depends on, declare BOTH edges in the same commit as the new package-info.java (account ↔ onboarding both landed in commit 2f25214).
- [Phase 1.2]: Plan 05 completed CL-2 single-domain delete pattern across all 4 domains: GmailConnectionService.deleteForCurrentTenant + new TenantService.deleteCurrentTenant (first occupant of core.tenant.service). AccountDeletionController bridge now FK-safe 4-call orchestration with zero direct repo injections.
- [Phase 1.2]: Plan 05 collapsed @EntityScan to single root "com.zeromail.core" in both Application.java + CoreTestApplication.java per RESEARCH.md primary recommendation. Forward-compatible for Phase 2A/2B/2C/3/4 — no further entity-scan edits needed.
- [Phase 1.2]: Plan 05 honored D-D4 explicitly: gmail/package-info.java allowedDependencies = {tenant, shared.privacy} — NO account edge. Verified by ApplicationModulesTest.
- [Phase 1.2]: Plan 05 deviation captured: concurrent user activity on STATE.md/01.3 docs auto-staged executor's pending Java edits into commits 03b5652 + eabbdca. No functional impact (all tests pass), but Task 3's Java work appears in those commits rather than a dedicated executor commit. Documented in 01.2-05-SUMMARY.md.
- [Phase ?]: [Phase 1.2]: Plan 06 closed Wave 0 ArchUnit gap with DomainBoundaryArchTests (4 explicit per-domain rules). Predicate composition pattern locked at DescribedPredicate level (DSL-level .and() chain unsupported in ArchUnit 1.4.x — confirmed via sources jar). Pattern documented in class-level JavaDoc for future-domain authors.
- [Phase ?]: [Phase 1.2]: Plan 06 caught + decomposed 2 lingering cross-domain repo injections that Plans 03/04 silently shipped (T-01.2-H regression). Pattern: extend CL-2 service-to-service primitive shape to non-delete writes — TenantService.createTenant + AccountService.advanceOnboardingStep mirror the delete-cascade primitives. ArchUnit threat-test-as-defect-finder confirmed working on first execution.
- [Phase ?]: [Phase 1.2]: Phase 1.2 structurally complete. Source tree contains exactly {account, gmail, onboarding, shared, tenant} per RESEARCH.md target. Full ./gradlew clean check green: 115 tests / 30 classes / 0 failures. Byte-identical contract preserved (zero diff in db/changelog, libs.versions.toml, apps/web/openapi).
- [Phase ?]: [Phase 1.3]: Plan 01 — Wave 0 architecture/cleanup test spine landed (6 vitest files, 56 assertions: 45 RED + 10 GREEN + 1 SKIP). Static existsSync predicate at module load (REVIEWS Revision 4) replaces broken beforeAll+skipIf permanent-skip pattern. Non-literal dynamic-import spec defers Vite import-analysis to runtime — fixes ERR_MODULE_NOT_FOUND that @vite-ignore alone does not solve. Pattern locked: pure-fs/regex Wave 0 tests with no @/features/ runtime imports survive every refactor wave.
- [Phase ?]: [Phase 1.3]: Plan 02 — pre-commit gate live (Husky 9 + lint-staged + Prettier 3 root-level). Empirically verified: hooks fired on Task 1 + Task 2 commits. lint-staged in WARN-ONLY i18n:check mode (`|| true`) until Plan 07 final task; `!(messages)` extglob de-duplicates Prettier across overlapping globs (REVIEWS Revisions 3 + 5).
- [Phase ?]: [Phase 1.3]: Plan 02 — `transpilePackages: ['next-mdx-remote']` pre-declared in apps/web/next.config.ts. Pattern locked: pre-declare Turbopack-transpile entries when the runtime dep lands in a future plan; entry is a no-op until first import (Pitfall 6 mitigation, Plan 06 activates).
- [Phase 01.2.1]: Plan 01 — 3-tier @MappedSuperclass hierarchy (AbstractEntity → AbstractAuditableEntity → AbstractTenantOwnedEntity) landed in core.shared.persistence with @EntityListeners(AuditingEntityListener.class) on Tier 2. JpaAuditingConfig wires Clock-bean → DateTimeProvider → @EnableJpaAuditing. FND-05 MultiTenantLeakIntegrationTest still passes (PROVES @TenantId binding survives @MappedSuperclass relocation in Hibernate 7).
- [Phase 01.2.1]: Plan 01 — Liquibase changeset 007 deviation captured: gmail_connections table (created in changeset 003) was missing created_at column. Fix prepended to changeset 007: addColumn created_at + backfill from COALESCE(connected_at, NOW()) + promote NOT NULL. Pattern locked: defaultValueComputed: now() on all new audit columns (created_at, updated_at) so raw-SQL inserts in tests still pass NOT NULL constraint; AuditingEntityListener still wins for entity-managed paths.
- [Phase 01.2.1]: Plan 01 — UserEntity.advanceTo() retains ordinal()-based body. Plan 03 swaps to weight() once OrderedEnum lands in Plan 02 (per CONTEXT D-B5 + WR-02).
- [Phase 01.2.1]: Plan 02 — core.shared.lang Modulith leaf module landed (IdentifiedEnum + OrderedEnum interfaces + package-info @ApplicationModule(displayName="Lang", allowedDependencies={})). Pure interface introduction, zero consumers; Plan 03 wires OnboardingStep (implements OrderedEnum, weights 10/20/30/40) and GmailConnectionStatus (implements IdentifiedEnum, no weight per D-B5). Locks D-B1 two-interface split, D-B2 String id type, D-B3 labelKey default = `<ClassSimpleName>.<id>`, D-B4 fromId fail-loud (NoSuchElementException not IllegalArgumentException). Documents D-C2 id()==name() invariant (enforced via convention + @Enumerated(STRING) + Plan 03 OnboardingStepPersistenceTest; ArchTest enforcement OUT of scope) and D-C3 AttributeConverter migration trigger (deferred to first per-enum decoupling need).
- [Phase 01.2.1]: Plan 02 — JetBrains MCP file-problem check unavailable in sequential-executor agent tool set (upstream issue anthropics/claude-code#13898). Fallback: `./gradlew :backend:core:check :backend:api:check` BUILD SUCCESSFUL is a strict superset for pure-interface files (javac + ArchUnit + ApplicationModulesTest covers correctness). Documented in 01.2.1-02-SUMMARY.md as tooling deviation; no code deviation.
- [Phase ?]: [Phase 01.2.1]: Plan 03 — WR-01/WR-02/WR-03 closure complete. OnboardingStep implements OrderedEnum (10/20/30/40); GmailConnectionStatus implements IdentifiedEnum (unordered per D-B5); UserEntity.advanceTo uses weight() not ordinal(); OnboardingSelectionRepository.deleteByTenantId is bulk @Modifying @Query with explicit WHERE :tenantId (T-01.2.1-07 mitigation); OnboardingStepPersistenceTest extends PostgresContainerTest with @ParameterizedTest @EnumSource — 4 real-DB round-trips + raw column data_type assert. Modulith allowedDependencies for account/onboarding/gmail gained literal 'shared.lang'.
- [Phase ?]: [Phase 01.2.1]: Plan 03 — Rule 3 fix: TestJpaAuditingConfig added to backend/core test sources. Production JpaAuditingConfig (com.zeromail.api.config) is outside CoreTestApplication scan base; @CreatedDate/@LastModifiedDate fields were bound to NULL by Hibernate (defaultValueComputed: now() on DB column does NOT apply when INSERT binds NULL). Test-side mirror is the smallest correct surface — same wiring seen only by tests.
- [Phase 1.3]: Plan 04 — Server-safe lib/api/client.ts split landed (REVIEWS Revision 1, Codex HIGH #2). Re-export of ./errors removed; client-only callers import directly from @/lib/api/errors. RSC + edge code paths no longer pull use-client + next-intl hook code through the import graph. Wave 0 server-safe-client.test.ts is the durable guard.
- [Phase 1.3]: Plan 04 — Five feature folders fully populated: features/{auth,account,onboarding,gmail,i18n}/{api,components,hooks}. 5 components relocated via git mv (LanguageSwitcher → i18n, ConnectionHealthBadge + ReconnectPrompt → gmail, DeleteAccountDialog → account, TemplateCard → onboarding). EN_SCAN_FILES in scripts/check-i18n.ts updated in same commit so scanner does not silently lose coverage (Phase 1.1 D-D3).
- [Phase 1.3]: Plan 04 — Isomorphic getCurrentUser({ fetcher?, signal?, headers? }) at features/account/api/me.ts is the single source of truth for /me. proxy.ts:reconcileLocaleCookie + app/layout.tsx:reassertServerLocale + features/account/hooks/useCurrentUser all consume it. as unknown as cast in proxy.ts preserved (D-E5).
- [Phase 1.3]: Plan 04 — All endpoint-specific calls moved to feature/api/ + hooks (REVIEWS Revision 1, Codex HIGH #1). 7 new feature/api/ modules + 5 new hooks (useTenantStatus + useDisconnectGmail + useSelectTemplate + useCompleteOnboarding + useDeleteAccount). Settings + onboarding pages call feature hooks; zero inline api.GET/POST/DELETE for moved endpoints. ROADMAP success criterion #6 fully satisfied.
- [Phase 1.3]: Plan 04 — Pattern locked: per-feature TanStack Query key factories (accountKeys/gmailKeys/onboardingKeys); explicit cross-feature invalidation via queryClient.invalidateQueries({ queryKey: featureKeys.X() }); NO barrel index.ts at any features/<name>/ root; deep imports only.
- [Phase 1.3]: Plan 05 — Wave 3 route topology landed: app/(public)/, app/(auth)/, app/(protected)/ each with their own layout.tsx; Light skeleton landing at (public)/page.tsx replaces app/page.tsx → redirect('/login'); 4 dead app/[locale]/* re-exports deleted; Wave 0 route-groups vitest spec fully GREEN (6/6).
- [Phase 1.3]: Plan 05 — Chrome ownership decided exactly once (REVIEWS Rev 2 #4): (public)/layout.tsx owns header+main+footer; (auth)/layout.tsx is minimal passthrough (login page keeps inline <main> + LanguageSwitcher); (protected)/layout.tsx is bare passthrough (ProtectedHeader lazy until Phase 5). No nested <main>.
- [Phase 1.3]: Plan 05 — Landing CTA pattern locked: <Link className={buttonVariants()}> NOT <Button asChild> (REVIEWS Rev 2 #3). Local Button wraps @base-ui/react/button which does not support asChild. buttonVariants exported from @/components/ui/button. Auth-aware CTA via getCurrentUser({ headers: { cookie } }) silent-fallback to /login.
- [Phase 1.3]: Plan 05 — next-intl typed-namespace bypass via cast-to-never: (public)/{layout,page}.tsx use `t("namespace.key" as never)` until Plan 07 lands the keys. Runtime returns key path as fallback. Pattern locked for any future "ship UI before its i18n namespace" plan.
- [Phase 1.3]: Plan 05 — Playwright route-smoke env-blocked in sandbox (port 3000 held by stale process returning 500). Spec committed as durable gate; CI / fresh dev runs cleanly. Route-smoke pattern locked for future [locale]-style mirror-tree deletions.
- [Phase ?]: Phase 01.2.1 Plan 04 — DTO group-by-domain reorg (4 DTOs into 3 sub-packages, 4 root files DELETED) + GmailConnectionStatusResponse rename across Java + URL + @Tag(name=gmail) + frontend; @NamedInterface re-exposure pattern locked for nested sub-packages of auto-detected modulith modules; springdoc-openapi-gradle-plugin 1.9.0 wired for hermetic spec emit (port 58080, dummy creds) — replaces bootRun&+kill per W3 closure.
- [Phase ?]: Phase 01.2.1 Plan 04 deviation: concurrent user activity (commits 3e13e05 / e367d67) auto-staged executor's pending Java edits + a phase-01.3 e2e scaffold file into commits with mixed phase prefixes — same race condition documented for Phase 1.2 P05 commits 03b5652+eabbdca. Documented in 01.2.1-04-SUMMARY.md §Deviations §4+§5. No code-quality or scope-creep impact; only commit-subject attribution drift.
- [Phase 1.3]: Plan 06 — Wave 4 docs/MDX pipeline landed. apps/web/lib/docs/loader.ts is the deterministic resolver + zod FrontmatterSchema (REVIEWS Revision 6, OpenCode MEDIUM): anchored on path.dirname(fileURLToPath(import.meta.url)) (or __dirname when defined), never the caller's working directory. Index page parses content/docs/*.mdx with gray-matter + safeParse and silently skips malformed entries; dynamic [slug] page uses compileMDX from next-mdx-remote/rsc with await params + SLUG_RE BEFORE path.join + safeParse + slug/locale consistency check (fm.data.slug !== slug || fm.data.locale !== locale → notFound()). 4 sample MDX files (vi + en, getting-started + privacy). next-mdx-remote v6 security defaults preserved (no override of blockJS / blockDangerousJS). Wave 0 mdx-pipeline.test.ts FULLY GREEN (10/10, 0 skipped).
- [Phase 1.3]: Plan 06 — Pattern locked: when a Wave 0 negative-substring regex (e.g. /process\.cwd\(\)/) tests source bytes for forbidden tokens, prose comments must paraphrase the token rather than mention it literally. Useful guard, but conflates source bytes with semantics — name the trade-off in the comment when paraphrasing.
- [Phase 1.3]: Plan 06 — Pattern locked: Wave 0 availability gates that read existsSync('node_modules/<pkg>/package.json') under pnpm workspaces MUST OR-check workspace-root node_modules (pnpm hoists shared deps to root); the Plan 01 mdx-pipeline gate was widened in this plan and the inline note explains the pnpm-hoist intent for future agents.
- [Phase 1.3]: Plan 07 — Wave 5 i18n closure landed: 11 new leaf keys (87 total) mirrored across vi/en bundles (common.nav.{docs,signIn} + landing.{heading,tagline,primaryCta,continueSetupCta} + docs.{indexHeading,backToList,empty.{heading,body},notFound.body}); EN_SCAN_FILES expanded 10 → 14 entries (added (public)/docs/page.tsx + [slug]/page.tsx + [slug]/loading.tsx + (auth)/layout.tsx); 8 `t(... as never)` cast bypasses across 4 source files removed (next-intl typed-key check re-engaged for Plan 04/05/06 surfaces). UI-SPEC §Copywriting Contract copy used verbatim.
- [Phase 1.3]: Plan 07 — REVIEWS Revision 3 closed: lint-staged i18n:check flipped from warn-only (`|| true` since Plan 02) to STRICT. Empirically verified on disposable temp branch — deliberate vi.json key delete → `git commit` → husky exit 1 + lint-staged auto-revert + `[parity] vi.json and en.json leaf-key sets differ: en-only: landing.continueSetupCta` in stderr. Pattern locked: flip strict BEFORE temp-branch verification so the temp branch inherits strict config.
- [Phase 1.3]: Plan 08 — Phase 01.3 closure-ready. All 4 automated gates GREEN (tsc, vitest 80/80 across 9 files, i18n:check 87 keys, ESLint). All 6 Wave 0 files GREEN with 56/56 assertions zero-RED zero-SKIP. proxy.ts `as unknown as` cast preserved (3 occurrences ≥ 2). Schema-diff REVIEWS Revision 7 gate PASSED via source-control proof: `git log e367d67..HEAD -- apps/web/lib/api/schema.d.ts` returns 0 commits — Phase 1.3 is frontend-only by definition, schema.d.ts byte-identical to Phase 01.2.1 baseline. VALIDATION.md flipped `nyquist_compliant: true` + `wave_0_complete: true`. Two manual gates deferred to user with replay commands: Playwright e2e (port 3000 held by stale `next dev` PID 24616 — same Plan 03/05 env-block) and live `generate:api` round-trip (backend not running on localhost:8080).
- [Phase ?]: Wave 0 RED-by-design test scaffolds locked: 5 backend tests + 1 fixture + 9 frontend tests reference future production classes/components/files; compile and import errors form the acceptance contract Waves 1-2 must satisfy
- [Phase 01.4]: Plan 01.4-02: GmailIdentityMismatchException extends OAuth2AuthenticationException — Spring Security route automatic qua failureHandler thay vì non-OAuth RuntimeException bypass thành 500 — RESEARCH Q2 recommendation; native Spring routing eliminates custom mapping
- [Phase 01.4]: Plan 01.4-02: Single AuthorizedClient load tại đầu callback (Issue 3 mitigation) — captured accessTokenForRevoke local tái sử dụng ở 2 throw-site mismatch để failure handler revoke được sau khi removeAuthorizedClient — Pitfall 2 + Issue 3 từ checker; one OAuth callback => one AuthorizedClient lookup
- [Phase 01.4]: Plan 01.4-02: Forward-decl GmailConnectionService.upsert signature throws UnsupportedOperationException trong Plan 02 — Plan 03 fill body. Pattern locked cho cross-plan compilation seam — Giữ ./gradlew compileJava GREEN xuyên suốt wave thay vì để Plan 02 ship intentional compile-RED
- [Phase 01.4]: Plan 01.4-02: Dispatcher failure-side route theo thrown-exception type (không phải registration id — AuthenticationException không carry registration reliably). GmailIdentityMismatchException + OAuth2AuthenticationException(access_denied) cả hai route tới GmailOAuthFailureHandler — Spring AuthenticationException surface không expose source registration; type-based dispatch là Spring-idiomatic
- [Phase ?]: Plan 03: GmailConnectionService.upsert KHÔNG ghi googleEmail trên path UPDATE — subject check ở Plan 02 đã guarantee equality (D-A4 + RESEARCH Q4)
- [Phase ?]: Plan 03: login_hint inject pattern matching qua OAuth2AuthenticationToken→OidcUser→email; silent omit cho mọi mismatch (D-A2 graceful-degrade)
- [Phase ?]: Plan 04: Inline SVG icons + plain DOM elements (a/button) thay lucide-react/next-link/Button vì vitest React-dedupe boundary không xuyên qua transitive imports từ next/lucide/@base-ui
- [Phase ?]: Plan 04: 5 UI primitives compose existing shadcn — không cài primitive mới, không sửa globals.css (tokens layer đã đủ taxonomy)
- [Phase 01.4]: Plan 05: 5 boundary files (global-error + not-found + 3 segment error.tsx) — `unstable_retry` thay `reset` (RESEARCH §Pitfall 3 — `reset` chỉ re-render stale data, `unstable_retry` re-fetch). Option A safety net: try { unstable_retry() } catch { window.location.reload() } chỉ trong global-error.tsx. Static-source assertion cho jsdom-stripped <html>/<body>; vi.stubEnv thay Object.defineProperty (Node 24 reject)
- [Phase 01.4]: Plan 06: Closed-enum `?error=` mapping với type-guard `isKnownError` — Threat T-error-param-tamper mitigated; arbitrary URL values resolve `null` baseKey + render no alert. handleRetry fire cả `router.replace('/onboarding')` (URL hygiene trước) lẫn `window.location.href` (re-trigger OAuth full-page nav). Wave 0 onboarding-error.test.tsx 5/5 GREEN
- [Phase 01.4]: Plan 06: Plain `<button className={cn(buttonVariants())}>` thay `<Button>` trong onboarding — vitest @base-ui/react useRef null-dispatcher (cùng root cause Plan 04 StatusAlert/EmptyState). Pattern locked cho mọi page client-render-tested under vitest
- [Phase 01.4]: Plan 06: Settings narrows max-w-4xl → max-w-3xl — UI-SPEC §Spacing locks app variant; design contract takes precedence over historical width
- [Phase 01.4]: Plan 06: ReconnectPrompt collapse từ 18-line hand-crafted Alert (border-amber-500 bg-amber-50 + Button) → 12-line StatusAlert variant=warn wrapper. Pattern: bất kỳ existing Alert ad-hoc nào trong repo có thể refactor cùng cách miễn là single i18n key resolves to one-line string
- [Phase 01.4]: Plan 06: TemplateCard ring-blue-600 → ring-ring (KHÔNG ring-primary). UI-SPEC Color reservation list không name selection-ring là accent slot; ring-ring là structural focus token, đúng choice cho selected-state visual
- [Phase ?]: Single bundled google OAuth2 registration replaces two-leg google+google-gmail pattern (Inbox Zero alignment)
- [Phase ?]: OAuthProvisioningService.provisionBundledOAuth uses PROPAGATION_REQUIRED (HIGH-1 atomicity fix): user+tenant+gmail all roll back together on failure
- [Phase ?]: Null refresh token on first login throws OAuth2AuthenticationException(consent_denied) before any DB write (MED-3)
- [Phase ?]: gmail.settings.basic missing allows provisioning with opaque warning log — not a hard failure (INFO-7 policy)
- [Phase ?]: Phase 01.5 Plan 02: D-D3 — TemplateCard ring-ring -> ring-primary so Phase 5 brand swap propagates automatically
- [Phase ?]: Phase 01.5 Plan 02: global-error.tsx left inline-styled English — next-intl getTranslations cannot run in global error boundary
- [Phase ?]: Phase 01.5 Plan 02: Loose translator cast (as unknown as string-to-string fn) for dynamic template-literal error key lookups under next-intl 4 strict bundle
- [Phase ?]: Phase 01.5 Plan 02: Plain <a> with eslint-disable in not-found.tsx — next/link triggers vitest React-dedupe useContext null; mirrors lucide-react inline-SVG boundary
- [Phase ?]: HIGH-2 fix: getCurrentUserCached uses primitive cookie header string as React cache() key for real RSC dedupe
- [Phase 01.5]: Plan 04: frontend-design skill là sole invocation site trong Phase 01.5 (MED-5 review fix) — Plan 02 deflation không invoke skill; Plan 04 polish IS the visual-design pass
- [Phase 01.5]: Plan 04: danger-zone settings dùng border-destructive token trên Card thay solid background fill — keeps visual hierarchy mà không alarmist
- [Phase ?]: Race-loser drops second bundledTx entirely: winner thread committed atomically; re-encrypting loser token overwrites winner envelope (privacy + atomicity violation)
- [Phase ?]: GET for /tenant/connect-gmail: idempotent OAuth redirect trigger; token rotation on callback; no CSRF needed per Spring Security safe-method defaults (CR-03)
- [Phase ?]: token.getName() not req.getUserPrincipal() for removeAuthorizedClient: principal not in SecurityContext at throw sites; CR-02 fix
- [Phase ?]: Plan 01.5-08: spring-cloud-gcp-starter-secretmanager + BOM removed -- CLAUDE.md No GCP hosting baseline lock now honored in build artifacts
- [Phase ?]: Plan 01.5-08: REFRESH_TOKEN_KEY_BASE64 uses :? fail-fast -- missing env var gives clear startup error, no sm:// fallback
- [Phase 01.6]: Plan 01: Theme tokens stay Teal-only for v1; no [data-accent] or multi-palette scope was added.
- [Phase 01.6]: Plan 01: Chrome helper tokens remain direct CSS variables, not --color-* utilities, to avoid misleading gradient/overlay utility classes.
- [Phase 01.6]: Plan 01: Prettier ignore markers are limited to :root and .dark token value blocks because the Wave 0 spec requires exact uppercase hex literals.
- [Phase 01.6]: Plan 02: Keep production next/font/google loaders as direct module-scope const calls; Vitest specs mock next/font/google locally because Next/Turbopack rejects conditional font-loader wrappers.
- [Phase 01.6]: Plan 02: Be Vietnam Pro ships with vietnamese+latin subsets and weights 400/500/600; Instrument Serif remains latin-only with normal+italic style.
- [Phase 01.6]: 01.6-03: Theme persistence uses Server Action + zm-theme cookie only; no localStorage or client-side cookie writes.
- [Phase 01.6]: 01.6-03: LanguageSwitcher variant compact was reused directly in public TopBar; no fork was needed.
- [Phase ?]: 02A-00: Worker RED tests use a package-local SpringBootTest scaffold because backend/core test sources are not on the worker test classpath. — Keeps Wave 0 worker verification RED on future production classes instead of failing on cross-module test-source visibility.
- [Phase ?]: 02A-00: Vitest includes features/**/*.{test,spec}.{ts,tsx} for feature-owned Wave 0 tests. — Without this include glob, PauseBanner and useToggleTriagePause tests are not collected by Vitest and the frontend RED spine is partially invisible.
- [Phase 02A]: 02A-01: Use Yasson JSON-B at runtime for Hibernate JSONB mapping under Spring Boot 4/Jackson 3 instead of adding Jackson 2.
- [Phase 02A]: 02A-01: Keep MailMessageObservedId as a top-level record to satisfy the committed Wave 0 test contract while still using Hibernate @IdClass.
- [Phase 02A]: 02A-01: Explicitly tenant-scope one-argument PubSubDeliveryRepository claims because native SQL does not inherit Hibernate @TenantId filtering.
- [Phase 02A]: 02A-02: GmailConnectionService.markDisconnected uses TransactionTemplate for a DB-only durable state update before best-effort users.stop cleanup.
- [Phase 02A]: 02A-02: GmailHistoryProcessor remains a thin scheduled loop; GmailDeliveryProcessingService owns the public @Transactional per-delivery boundary.
- [Phase 02A]: 02A-02: WorkerApplication mirrors API entity/repository scanning because the worker directly consumes backend/core repositories.
- [Phase 02A]: 02A-02: Worker REFRESH_TOKEN_KEY_BASE64 is fail-fast with no sm:// fallback, honoring the no-GCP-hosting baseline.
- [Phase 02A]: 02A-03: Pub/Sub OIDC verification is isolated in an @Order(1) SecurityFilterChain that remains active under test profile.
- [Phase 02A]: 02A-03: PubSubIngestionService performs Gmail email lookup with unscoped JdbcTemplate, then binds TenantContext before inserting delivery rows.
- [Phase 02A]: 02A-03: GmailPubSubController returns void for ack paths to avoid existing controller-boundary ArchUnit false positives on ResponseEntity.
- [Phase 02A]: 02A-03: /me composes tenant pause state and Gmail ingestion health from services; googleEmail is response-only and not logged.
- [Phase 02A]: Plan 04: Use a plain accessible toggle button because apps/web has no shadcn Switch primitive installed. — No local Switch primitive exists and the plan forbids installing new shadcn primitives in this plan.
- [Phase 02A]: Plan 04: generate-api.ts defaults to openapi/openapi.json for schema generation. — The Gradle OpenAPI task writes a local artifact and stops its forked server, so frontend codegen must not require localhost:8080 by default.
- [Phase 2A]: Pub/Sub push-token validation closed. PubSubOidcAuthFilter uses TokenVerifier.newBuilder().setAudience().setIssuer().setCertificatesLocation() from google-auth-library-oauth2-http. PubSubSecurityConfig contributes a SecurityFilterChain bean with @Order(1) and remains active under the test profile; user-session SecurityConfig's SecurityFilterChain bean is @Order(2). 7-case test validates: valid passes, wrong aud/email/issuer/exp/sig all return 401, and non-Pub/Sub paths skip the filter. Phase 01.5 D-D5 deferred blocker retired.
- [Phase 02B]: Plan 00 accepted a Phase 02B-only Wave 0 compile-RED contract-test window; no production stubs were added. — The plan explicitly scopes this exception to prepaid-credit billing tests until Plans 03, 04, and 05 land the referenced symbols.
- [Phase 02B]: SepayWebhookMismatchAuditEventTest uses valid Crockford code ABCD2345 for the amount-mismatch audit path. — This guards the cycle-3 review fix so future implementation resolves by payload code instead of referenceCode or an invalid test fixture.
- [Phase 02B]: Top-up code uniqueness uses tenant-bypassing lookup — billing_topup_intent.code is globally unique while standard JPA findByCode is tenant-filtered.
- [Phase 02B]: BillingProperties masks SePay secret in toString — The configuration record carries zero-mail.billing.sepay.webhook-api-key and must not expose the API key through accidental bean logging.
- [Phase 02C]: [Phase 02C Plan 01] Keep RefreshTokenCipher at core.gmail.persistence.crypto and declare core.llm -> gmail.persistence.crypto Modulith dependency for BYOK encryption reuse. — Matches D-A5 and avoids relocating Gmail token crypto in Plan 01.
- [Phase 02C]: [Phase 02C Plan 01] Use pure-Java LlmModelClient seam and records so Spring AI imports stay confined to core.llm.gateway.springai. — Satisfies the strict ArchUnit no-exemption import boundary for LLM-01.
- [Phase 02C]: [Phase 02C Plan 01] Add test-only Spring AI placeholder keys to SpringBootTest contexts because starters auto-configure model beans even when gateway behavior is not exercised. — Required to keep core/API/worker tests booting after adding Spring AI starters.

### Roadmap Evolution

- Phase 1.1 inserted after Phase 1: Vietnamese-first i18n and error-handling foundation — default Vietnamese / secondary English, language switcher, stable frontend-localizable API error contract; references local JHipster patterns; preserves all Phase 1 privacy/safety constraints (URGENT)
- Phase 1.2 inserted after Phase 1.1: Domain-owned persistence restructuring — refactor `backend/core` into domain-owned service/persistence/model packages; add a small shared package for stable cross-cutting infrastructure; preserve schema and safety constraints; enforce boundaries with Modulith or ArchUnit (URGENT)
- Phase 1.3 inserted after Phase 1.2: Frontend Architecture Refactor and Public Content Foundation — reorganize `apps/web` with Next.js route groups, feature folders (`api/`, `components/`, `hooks/`), typed OpenAPI boundaries, workspace cleanup, Prettier/Husky/lint-staged gates, and landing/docs scaffolding without final content design (URGENT)
- Phase 1.2.1 inserted after Phase 1.2: Shared base entity hierarchy (`AbstractEntity`/`AbstractAuditableEntity`/`AbstractTenantOwnedEntity` in `core.shared.persistence`) + `IdentifiedEnum` standard (id/weight/labelKey, applied to `OnboardingStep` + `GmailConnectionStatus`) + `backend/api/dto/` group-by-domain (with `TenantStatusResponse` → `GmailConnectionStatusResponse` rename) + close code-review WR-01/WR-02/WR-03; closes structural-cleanup gaps Phase 1.2 intentionally deferred (URGENT)
- Phase 1.4 inserted after Phase 1.3: Gmail Identity Semantics, Permission UX, and UI Consistency — align v1 auth so the Google login account IS the first managed Gmail account; treat initial Gmail access as incremental consent for that same account; reject mismatched initial Gmail OAuth callbacks; keep multi-account management as a later workspace-level capability (users add more Gmail accounts to a workspace); sweep UI consistency, visual polish, layout quality, copy, states, and reusable frontend patterns via the `frontend-design` skill (URGENT)
- Phase 1.4 marked complete without `/gsd-ship` on 2026-04-27 because Phase 1.5 supersedes much of the remaining OAuth/UX cleanup value.
- Phase 01.5 inserted after Phase 1.4: Inbox-Zero Alignment: Bundled OAuth + UX Polish + Cleanup Sweep - Remaining heavy Phase 1.5 work: single Google OAuth upfront Gmail scope, remove google-gmail mismatch architecture, merge Gmail token provisioning, simplify onboarding/consent UX, deflate frontend primitives, polish landing/login/onboarding/settings/ReconnectPrompt, and close surviving REVIEW cleanup; excludes quick tasks already completed. (URGENT)
- Phase 01.6 inserted after Phase 1: Brand Identity, Design Tokens, and Landing Page (URGENT)

### Pending Todos

[From .planning/todos/pending/ — ideas captured during sessions]

- WR-06: dedicated test-profile SecurityConfig slice (so OAuth filter chain is exercised under integration tests) — `.planning/todos/pending/2026-04-28-wr-06-test-profile-securityconfig-slice.md`
- Apply `:?` fail-fast to `backend/worker/src/main/resources/application.yml:10` (CR-04 parity with api module) — `.planning/todos/pending/2026-04-28-worker-application-yml-fail-fast-parity.md`

### Blockers/Concerns

[Issues that affect future work]

- Phase 2A and Phase 2C are both flagged for `/gsd-research-phase` before planning — do not skip (Gmail watch/history + OIDC verification for 2A; Spring AI 2.0.0-M4 BYOK builder API + tokenizer for 2C).
- CASA verification is a 4–12 week external clock — must be initiated during Phase 1 execution, not deferred.
- Open decisions deferred to phase execution: credit unit economics (Phase 2B), tokenizer choice (Phase 2C), payment provider Stripe vs LemonSqueezy (Phase 2B), observability vendor (any), CASA tier (Phase 1/6).
- **Refresh-token key rotation drill** (Phase 2C or dedicated security-ceremony phase) — verification protocol: deploy v2 key alongside v1 in the deployment secret source (current VPS baseline: Docker secrets / systemd credentials / locked-down env files; future production options may include GCP Secret Manager, AWS Secrets Manager, or HashiCorp Vault); verify multi-version decrypt path reads `key_version` byte from envelope and selects correct key; rotate v1 → v2 + re-encrypt all rows; verify v1 envelopes still decrypt during overlap window. Per CLAUDE.md TL;DR ("No GCP hosting baseline; do not add spring-cloud-gcp starters by default"), the drill must be deployment-source-agnostic.
- **Production cookie `secure: true` profile override + `REFRESH_TOKEN_KEY_BASE64` deployment secret resolution** (Phase 6 launch hardening) — verification protocol: assert `application-prod.yml` overrides `server.servlet.session.cookie.secure: true`; assert `REFRESH_TOKEN_KEY_BASE64` resolves successfully from the configured deployment secret source in prod profile (Docker secret / systemd credential / env file mounted via the VPS deployment pipeline; possible future production options: GCP Secret Manager, AWS Secrets Manager, HashiCorp Vault); assert app fails-fast at boot if the secret is missing (no fallback to plain env-var in prod). Per CLAUDE.md TL;DR, no GCP-specific resolution is required by default.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260507-4lb | Refactor frontend feature API, hooks, query keys, and tests | 2026-05-07 | a3c2966 | [260507-4lb-refactor-frontend-feature-api-hooks-quer](./quick/260507-4lb-refactor-frontend-feature-api-hooks-quer/) |
| 260428-0hx | Rename core view records to projections | 2026-04-28 | 3ff9025 | [260428-0hx-rename-core-view-records-to-projections-](./quick/260428-0hx-rename-core-view-records-to-projections-/) |
| 260506-n2x | Move billing CreditLedger interface into service package | 2026-05-06 | b2a97d5 | [260506-n2x-move-billing-creditledger-interface-into](./quick/260506-n2x-move-billing-creditledger-interface-into/) |
| 260427-9n3 | cài Dependabot cho tôi | 2026-04-27 | 600fef4 | [260427-9n3-c-i-dependabot-cho-t-i](./quick/260427-9n3-c-i-dependabot-cho-t-i/) |
| 260427-02m | Refactor @Value application properties into @ConfigurationProperties | 2026-04-27 | fec9201 | [260427-02m-refactor-value-application-properties-in](./quick/260427-02m-refactor-value-application-properties-in/) |
| 260427-8qe | Phase 1.5 quick cleanup: font fix and low-risk frontend/backend review findings | 2026-04-27 | 91117fd | [260427-8qe-phase-1-5-quick-cleanup-font-fix-and-low](./quick/260427-8qe-phase-1-5-quick-cleanup-font-fix-and-low/) |
| 260427-8sw | Phase 1.5 folder restructure for web docs and i18n messages | 2026-04-27 | 576f671 | [260427-8sw-phase-1-5-folder-restructure-for-web-doc](./quick/260427-8sw-phase-1-5-folder-restructure-for-web-doc/) |
| 260427-8xs | Project-wide JetBrains problem sweep for backend and frontend | 2026-04-27 | 8944e0c | [260427-8xs-project-wide-problem-sweep-for-backend-a](./quick/260427-8xs-project-wide-problem-sweep-for-backend-a/) |
| 260426-a5s | Add Spring Boot Docker Compose support to backend/api so dev startup auto-launches Postgres + Redis from docker-compose.yml | 2026-04-26 | 1219ec8 | [260426-a5s-add-spring-boot-docker-compose-support-t](./quick/260426-a5s-add-spring-boot-docker-compose-support-t/) |

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| *(none — project init)* | | | |

## Session Continuity

Last session: 2026-05-07T12:27:34.267Z
Stopped at: Completed 02C-01-PLAN.md
Resume file: None
