# Phase 8: Admin Console & Operator Tooling — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-19
**Phase:** 08-admin-console-operator-tooling
**Areas discussed:** Spring Modulith module structure, Admin UI visual identity (superseded by architectural pivot), Tenant detail 5-tab routing, Architectural shape pivot (admin auth method + frontend topology)

---

## Spring Modulith module structure

| Option | Description | Selected |
|--------|-------------|----------|
| Single `core.admin` module + vertical sub-packages (auth/audit/mkey/cat/tenant/queue/spend) | Match existing core.chat/core.llm convention; ArchUnit handles internal boundaries; simpler Modulith config; defer @NamedInterface to Phase 11+ rule-of-three | ✓ |
| Hybrid: foundation module + 5 vertical sub-modules | core.admin (auth + audit) as 1 module; mkey/cat/tenant/queue/spend as 5 sibling modules with @NamedInterface; explicit Modulith boundaries | |
| 7 top-level modules (auth + audit + mkey + cat + tenant + queue + spend) | Each vertical = top-level Modulith module sibling of core.chat; maximum explicit cross-module API surfaces | |

**User's choice:** Single core.admin module + vertical sub-packages (Recommended)
**Notes:** Matches `core.chat`, `core.llm`, `core.gmail` shape. AdminContext + audit primitive shared heavily across verticals justifies single module. Rule-of-three for @NamedInterface extraction not yet met.

---

## @NamedInterface annotations on sub-packages (follow-up to Modulith decision)

| Option | Description | Selected |
|--------|-------------|----------|
| No — internal sub-packages free reference | Default per existing convention; ArchUnit controls specific boundaries; todo #2026-05-12 stays open for Phase 11+ | ✓ |
| Yes — every sub-package package-info.java + @NamedInterface | Mature explicit boundaries; closes todo #2026-05-12 immediately; trades +7 ceremony files | |
| Only foundation exposes @NamedInterface | Compromise: auth + audit @NamedInterface; 5 verticals stay internal | |

**User's choice:** No — internal sub-packages free reference (Recommended)
**Notes:** Defer abstraction until rule-of-three; matches existing project convention.

---

## Method-security expression (pre-pivot context)

| Option | Description | Selected |
|--------|-------------|----------|
| Plain `@PreAuthorize("hasRole('ADMIN')")` per class + ArchUnit gate | Explicit, discoverable, stock Spring annotations; same CI safety via ArchUnit; rule of three not met (Phase 8 has 2-3 admin controllers) | ✓ |
| Custom `@AdminController` meta-annotation bundling `@RestController + @PreAuthorize` | Single source of truth for role expression; ArchUnit enforces presence; reduces repetition | |
| Separate `SecurityFilterChain` only, no method-level | Zero `@PreAuthorize`; loses defense in depth | |
| `AuthorizationManager` bean for path-based rules | More flexible but overkill for class-level admin gating | |

**User's choice:** Plain `@PreAuthorize("hasRole('ADMIN')")` per class + ArchUnit gate
**Notes:** User pushed back on custom `@AdminController` annotation citing "raw shadcn primitives first" / rule-of-three principle applied to backend abstractions. Defer meta-annotation extraction until Phase 9+ when admin controller count grows.

---

## Role elevation mechanism (pre-pivot — superseded by pivot below)

| Option | Description | Selected (pre-pivot) |
|--------|-------------|----------|
| `GrantedAuthoritiesMapper` bean reading `users.role` | Idiomatic Spring Security pattern; single source of truth for DB → authority; testable in isolation | ✓ (pre-pivot) |
| Custom `OidcUserService` decorator | Replace `DefaultOidcUserService`; can inject custom `OAuth2User`; heavier | |
| `AuthenticationSuccessHandler` rewriting authorities | Modify `GoogleOAuthSuccessHandler` post-OAuth; mixes concerns | |
| `OncePerRequestFilter` re-loading authorities every request | Hot-revoke admin in real-time; overkill for rare admin grants | |
| Modify `GoogleAuthorizationRequestResolver` | Wrong file — that's outgoing OAuth request shape, not authority handling | (rejected) |

**User's choice (pre-pivot):** `GrantedAuthoritiesMapper` bean
**Notes:** This decision was superseded entirely by the architectural pivot below — admin authority no longer comes from `users.role`, it comes from a separate `admin_users` table on a dedicated chain.

---

## Architectural shape pivot (admin auth method + frontend topology)

| Option | Description | Selected |
|--------|-------------|----------|
| A. Single app, role-based (Google OAuth + users.role + (admin) Next.js route group) — original SPEC.md lock | Zero new infra; reuses existing OAuth + session; AdminContext mutex + ArchUnit + audit defend in depth; matches solo-operator single-VPS scale | |
| B. Separate frontend + Basic Auth (user's initial proposal) | apps/web + apps/admin separate; admin uses HTTP Basic + admin accounts seeded from DB | (rejected after research — OWASP ASVS deprecates Basic Auth) |
| C. Fully separate backend/admin-api + apps/admin (process isolation) | Add Gradle module backend/admin-api with own SecurityConfig; cross-process eventing for MasterKeyRotated/CatalogChanged; +250MB JVM; +10 reqs | (rejected — cross-process eventing pain, single-VPS process isolation theatrical) |
| **WebAuthn passkey + separate apps/admin Vite+React + 2 SecurityFilterChain (1 backend)** | Spring Security 7 `.webAuthn(...)` DSL on admin.zeromail.com; admin_users table; admin chain @Order(1) + user chain @Order(2); user-side RBAC removed entirely; cross-vertical events stay in-JVM via Spring Modulith | ✓ |

**User's choice:** WebAuthn passkey + separate apps/admin Vite+React + 2 SecurityFilterChain
**Notes:** Pivot driven by WebSearch + Context7 research:
- 2026 industry standard for admin = passkeys (phishing-resistant, hardware-bound, public-key-only storage); see c-sharpcorner Authentication Trends 2026, WorkOS user authentication best practices.
- HTTP Basic Auth = OWASP ASVS deprecated for admin (credentials on every request, no MFA, no logout, no revocation without password change).
- Spring Security 7 ships `.webAuthn(...)` DSL natively (6.4+ feature) — zero new dependency for `backend/api`.
- Decoupling admin auth from Google OAuth removes single-vendor (Google IdP) compromise surface.
- Admin doesn't need SSR/SEO — Vite + React 19 SPA is lighter than Next.js route group.
- DNS subdomain `admin.zeromail.com` provides strongest cognitive cue (replaces UI visual differentiation via dark sidebar).
- User-side `users.role` column removed entirely → simpler user codepath (no RBAC concept).
- SPEC.md ADMIN-01/02/03/06 + ARCH-08 rewritten inline; ADMIN-09 (admin_users schema) + ADMIN-10 (WebAuthn ceremonies) added. Spec delta committed `5213927a` mid-discuss-phase.
- Memory note `project_v12_admin_webauthn_pivot` saved with lesson-learned about spec-phase one-way-door discipline.

---

## Admin UI visual identity (superseded by pivot)

| Option | Description | Selected |
|--------|-------------|----------|
| Differentiate — dark sidebar + amber ADMIN MODE banner | Strongest within-app cognitive cue; brand consistency preserved | |
| Reuse user palette + banner only | Minimal design work; one banner easy to overlook | |
| Full dark theme — ops console aesthetic | Brand fragmentation; admin token set separate | |
| (Superseded by pivot — DNS subdomain handles cognitive cue) | After pivot: `apps/admin` on `admin.zeromail.com` makes UI palette decision moot; ADMIN MODE banner kept for destructive-action context within admin tabs | ✓ |

**User's choice:** N/A (superseded by architectural pivot)
**Notes:** Initially user asked for ASCII mockup preview; then questioned the underlying architectural assumption that admin is "1 frontend route group". Discussion shifted to the architectural pivot above. Post-pivot, DNS-level separation makes intra-app dark/light differentiation unnecessary — only the ADMIN MODE banner inside `apps/admin` is kept for the "alt-tab between admin tabs" failure mode within the admin app itself.

---

## Tenant detail 5-tab routing

| Option | Description | Selected |
|--------|-------------|----------|
| Single React Router route + shadcn `<Tabs>` + `?tab=` query param + TanStack Query lazy per-tab + 1 admin_read_event per tab visit | URL shareable; shadcn primitive reused; audit granularity per tab visit (5 rows/session max); balanced data-fetch | ✓ (Claude's discretion) |
| Nested route segments `/tenants/:id/<tab>` | URL cleaner per tab; per-route useQuery automatic unmount; verbose router config; redirect `/tenants/:id/` → `/overview` needed | |
| Single route + all tabs preloaded + tab state local in component | Fast tab switch (instant); URL not shareable; audit granularity page-level only; network heavy | |

**User's choice:** "vụ này bạn decide" (Claude's discretion)
**Notes:** Claude locked Option A (query param) per recommended-with-rationale pattern. shadcn `<Tabs>` already in `apps/web/components/ui/tabs.tsx` (copyable to `apps/admin`). `admin_read_event` row per tab visit gives useful per-action audit granularity.

---

## Claude's Discretion

- Tenant detail 5-tab routing (D-11 in CONTEXT.md): query-param + shadcn Tabs.
- PLAN.md structure inside Phase 8 (single PLAN.md vs split 8A.PLAN through 8F.PLAN): deferred to plan-phase.
- Liquibase changelog grouping (per-domain vs one-big): match existing per-feature convention (047 priors).
- Admin enrollment URL out-of-band delivery channel: documented options without prescribing in runbook.
- `(admin)` URL prefix within `apps/admin`: skip — routes start at root since admin lives on own subdomain.
- Audit log row presentation (before/after JSON diff layout): UI concern for ui-phase or plan-phase.
- Catalog Sync Diff page layout (table vs side-by-side vs accordion + pinned-tenant count display): UI concern for ui-phase.
- Master-key resolver placement architectural decision (in `core.llm.gateway.springai`) — locked by research SUMMARY, not asked.
- Spring Modulith event cross-vertical pattern (`@ApplicationModuleListener` in-JVM) — locked by memory note + project convention, not asked.

## Deferred Ideas

- **`@AdminController` meta-annotation** — defer to Phase 11+ when admin controller count ≥6 (rule-of-three).
- **Two-cookie session split** — current chain split + DNS subdomain reduces need; revisit if real CSRF/impersonation vector surfaces.
- **Self-service "I lost my passkey" recovery UI** — invites social-engineering surface; v1.3+ with second-admin co-sign.
- **Multiple passkey enrollment per admin** (primary + backup) — v1.3+ enhancement.
- **TOTP fallback for admin** — explicitly out of scope; revisit only if hardware key supply chain becomes accessibility issue.
- **Admin-side IP allowlist configuration UI** — Phase 8 documents NPM-level option; UI control is v1.3+.
- **Audit log forensic export with cryptographic chain proof** — future ADR for legal hold scenarios.
- **Cross-process admin events** (LISTEN/NOTIFY etc.) — only relevant if backend/admin-api split happens later (v1.3+).
- **Spring AI starter exclusion in admin path** — currently discipline only; enforced at dependency level only if JVM split.
- **`backend/admin-api` Gradle module extraction** — v1.3+ if compliance/team-scaling driver emerges.
- **Spring Modulith @NamedInterface explicit API surfaces** (todo `2026-05-12-make-backend-core-context-api-surfaces-explicit-with-namedin.md`) — Phase 11+ when rule-of-three triggers.
- **Rules UX structured When/Then builder** (todo `2026-05-15-rules-ux-structured-builder-next-milestone.md`) — out of Phase 8 scope; v1.3+ user-facing settings.
- **WR-06 test-profile SecurityConfig slice** — plan-phase reference if touching SecurityConfig test slicing; otherwise carry to future security-hardening phase.

---

*Phase: 08-admin-console-operator-tooling*
*Log written: 2026-05-19*
