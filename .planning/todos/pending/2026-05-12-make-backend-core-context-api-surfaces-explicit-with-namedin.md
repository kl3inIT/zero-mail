---
created: 2026-05-12T00:30:00Z
title: Make backend/core context API surfaces explicit with @NamedInterface
area: architecture
files:
  - backend/core/src/main/java/com/zeromail/core/*/package-info.java
  - backend/core/src/main/java/com/zeromail/core/*/usecases/
  - backend/core/src/main/java/com/zeromail/core/*/projection/
  - backend/api/src/test/java/com/zeromail/api/ZeroMailApiApplicationModulesTest.java
  - build.gradle.kts
---

## Problem

Spring Modulith treats each `backend/core` bounded context's **root package** as its only public
API; every sub-package (`domain/`, `usecases/`, `persistence/`, `projection/`, `exception/`,
`gateway/`) is "internal" and other modules are not supposed to reach into it. But `backend/api`
controllers (and `backend/worker`) call `core.<ctx>.usecases.*` types directly — i.e. they reach
into an "internal" sub-package. This compiles only because `backend/api` is a separate `@Modulithic`
application that does not verify `core`'s internal package boundaries. The boundary is therefore
documented-by-convention, not enforced. For a 3-person team this means accidental cross-context
reaching-into-internals (e.g. `backend/api` importing a `core.rules.persistence.RuleEntity` or a
`core.triage.domain.*` type) would pass review/build silently.

Deferred from quick task `260511-wc4` (the `application/` → `usecases/` rename + `service/`
dissolution + `DomainPurityArchTest`). That task was mechanical; this one is design work
(deciding the surface), so it was split out. **Not urgent.**

## Solution

1. **Decide the real public API surface per context** — typically: the use-case service interfaces
   (and the `@Service` classes when no interface) + the `Command`/`Result` records in `usecases/`
   + the read-side types in `projection/`. Entities, repositories, `domain/` internals, and
   `gateway/` adapters stay hidden.
2. **Mark those as named interfaces** — put `@NamedInterface("api")` on the relevant sub-package's
   `package-info.java` (e.g. a new `core.rules.api` package, or annotate `core.rules.usecases`
   directly), or annotate the individual exported types. Convention: `::api` (and `::spi` if a
   second surface is ever needed).
3. **Update `@ApplicationModule(allowedDependencies = { ... })`** strings to the `"<ctx>::api"`
   form so a context may only depend on another context's *named* API, not its internals.
4. **Keep `domain/` and `persistence/` truly internal** — no `@NamedInterface` on them; verify
   nothing outside the owning context imports them.
5. **Verify** `ZeroMailApiApplicationModulesTest` (and any other Modulith verification test) still
   passes; consider tightening it / adding one in `backend/core` itself so `core`'s internal
   boundaries are actually verified, not just `backend/api`'s.

### Related low-priority follow-ups (capture here so they aren't lost)

- **Spotless `ratchetFrom("origin/main")`** in root `build.gradle.kts` — the repo is already fully
  google-java-format AOSP-formatted (quick task `260511-vok`), so this is purely a CI-speed
  optimization (`spotlessCheck` only re-checks changed files). One line. Do whenever.
- **Vertical-slice split of `rules/` and (maybe) `llm/`** — only *reactively*, if those contexts'
  layer folders (`rules/usecases/`, `rules/domain/`) grow unwieldy to scan. Split by capability
  (`rules/compile/`, `rules/preview/`, `rules/template/`, `rules/management/`) rather than by
  technical layer. Within-context change, not a global rewrite. Don't do preemptively.
