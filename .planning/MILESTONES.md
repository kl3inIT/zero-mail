# Milestones

## v1.0 MVP (Shipped: 2026-05-15)

**Phases completed:** 17 phases, 123 plans, 221 tasks

**Key accomplishments:**

- Security package (`backend/api/.../security/`):
- Configuration:
- One-liner:
- One-liner:
- One-liner:
- One-liner:
- One-liner:
- One-liner:
- One-liner:
- One-liner:
- Created:
- Created:
- Created (4 package-info markers):
- Created (5 package-info markers + 1 net-new test):
- 1. [Rule 3 - Blocking] Pitfall 1 sweep — TenantConnectionService import flip + intra-package crypto test moves folded into Task 1 commit
- Net-new `DomainBoundaryArchTests` (4 rules, one per domain) closes Phase 1.2's last architectural-enforcement gap; `TenantIsolationArchTests` regex updated to span all 4 domains; `TenantStatusController` adopts the `toResponse(view)` helper pattern matching `MeController`; `AccountDeletionController` finalized in canonical Pattern 8 form. As a high-value side-effect, `DomainBoundaryArchTests` caught two cross-domain repo injections that Plans 03/04 silently missed — both decomposed in the same commit using the existing CL-2 service-to-service primitive pattern. Full `./gradlew clean check` green: 115 tests / 30 classes / 0 failures. Byte-identical contract preserved. Phase 1.2 is structurally complete.
- Before (line 100):
- Rule:
- 1. [Rule 3 - Blocking] CoreTestApplication missing `@EnableJpaAuditing` → Hibernate INSERT bound NULL on `@CreatedDate` / `@LastModifiedDate` → NOT NULL constraint on `users.created_at` failed
- Six vitest guard files (56 assertions) codify ROADMAP success criteria #1/#2/#4/#5/#6/#7; intentionally RED at Plan 01 close, transition to GREEN incrementally as Plans 02-07 land their targets.
- Monorepo-root Husky 9 + lint-staged + Prettier 3 pre-commit gate is live; ESLint flat-config integrates Prettier; next.config.ts pre-declares `next-mdx-remote` in transpilePackages so Plan 06's MDX install drops in cleanly.
- Single root pnpm-workspace.yaml + single root pnpm-lock.yaml; apps/web duplicates deleted; proxy.ts cast verified intact via tsc; Wave 0 workspace-cleanup test now flips GREEN on the lockfile/workspace-yaml/ignoredBuiltDependencies assertions.
- Wave 2 architecture lock landed: server-safe `lib/api/client.ts` split, five feature folders fully populated (api/components/hooks), isomorphic `getCurrentUser` consolidating /me across proxy.ts + app/layout.tsx + CSR hooks, and ALL endpoint-specific calls (`/tenant/status`, `/tenant/disconnect`, `/me/account`, `/onboarding/select-template`, `/onboarding/complete`) moved out of route pages into feature/api/ modules with TanStack Query hooks.
- Wave 3 route topology landed: three App Router route groups (public/auth/protected) with per-group layouts, auth-aware Light skeleton landing replacing the redirect-to-login root, and the dead app/[locale]/ mirror tree fully deleted. Chrome ownership decided exactly once per REVIEWS Revision 2 — Codex HIGH #4 (no nested <main>). All ROADMAP success criterion #1 + #7-partial unblocked.
- Wave 4 docs/MDX pipeline landed: deterministic loader + zod runtime frontmatter validation + slug/locale consistency check + 4 sample MDX files. Closes ROADMAP success criterion #7 with the visual design intentionally deferred to Phase 5.
- Wave 5 closed: vi/en bundles now mirror 87 leaf keys (added 11 across `common.nav`, `landing`, `docs`); EN-prose scanner covers 14 Phase 1 files (added 4 docs + auth-layout entries); 8 `as never` cast bypasses across 4 source files removed; root lint-staged i18n:check is in STRICT mode and was empirically verified to BLOCK a deliberate parity-drift commit on a disposable temp branch. Plan 02's warn-only window is closed.
- Result:

---
