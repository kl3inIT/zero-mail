---
quick_task: 260511-wc4
verified: 2026-05-12T00:00:00Z
status: passed
score: 8/8 must-haves verified
re_verification: false
---

# Quick Task 260511-wc4: Backend core package restructure — Verification Report

**Task Goal:** Rename `application/`→`usecases/` (llm/rules/triage), dissolve `service/` across all contexts, add a no-whitelist `DomainPurityArchTest`, delete empty `model/` packages, add missing `package-info.java`, relocate `RulePreviewWriteBoundaryTest`, update ArchUnit/Modulith tests, sync CLAUDE.md §2 + CONVENTIONS.md §2, fix the lint-staged config.
**Verified:** 2026-05-12
**Status:** passed

## Goal Achievement — Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `./gradlew check` passes (compile + ArchUnit incl. new `DomainPurityArchTest` + Spring Modulith + full backend test suite) | ✓ VERIFIED | `./gradlew check` → BUILD SUCCESSFUL. Force-reran `:backend:core:test --tests 'com.zeromail.core.arch.*' --tests 'com.zeromail.core.billing.BillingDomainBoundaryArchTest' --tests 'com.zeromail.core.rules.usecases.RulePreviewWriteBoundaryTest' --rerun-tasks` → BUILD SUCCESSFUL. Force-reran `:backend:api:test --tests '*ApplicationModulesTest*' --rerun-tasks` → BUILD SUCCESSFUL. No OOM. |
| 2 | No `com.zeromail.core.*.service` package under `backend/core/src/main` | ✓ VERIFIED | `find backend/core/src/main -type d -name service` → empty. `git grep -nE 'package com\.zeromail\.core\.[a-z]+\.service;' -- backend` → empty. `git grep -nE 'com\.zeromail\.core\.[a-z]+\.service\.' -- backend` → empty. |
| 3 | No `com.zeromail.core.*.service` package under `backend/core/src/test` | ✓ VERIFIED | `find backend/core/src/test -type d -name service` → empty. |
| 4 | No empty `com.zeromail.core.*.model` package directory under `backend/core` | ✓ VERIFIED | `find backend/core/src/main -type d -name model` → empty. |
| 5 | `git grep 'com.zeromail.core.(rules\|llm\|triage).application' -- backend` returns nothing | ✓ VERIFIED | `git grep -nE 'com\.zeromail\.core\.(rules\|llm\|triage)\.application' -- backend` → empty. `find backend/core/src/{main,test} -type d -name application` → empty. |
| 6 | No class in any `core.<ctx>.domain..` package depends on `org.springframework..`, `tools.jackson..`, `jakarta.persistence..`, or `org.hibernate..`; `DomainPurityArchTest` has NO whitelist, NO `allowEmptyShould(true)` | ✓ VERIFIED | `git grep -rnE 'import (org\.springframework\.\|tools\.jackson\.\|jakarta\.persistence\.\|org\.hibernate\.)'` across all 6 `core/*/domain/` dirs → empty. `DomainPurityArchTest.java` read directly: targets `..com.zeromail.core.*.domain..`, bans the 4 roots, no `.allowEmptyShould`, no whitelist `.and()` exclusions. Test passes under `--rerun-tasks`. |
| 7 | `lint-staged.config.mjs` at repo root with function-form `backend/**/*.java` entry + verbatim web glob arrays; `package.json` has no top-level `lint-staged` block (devDep `"lint-staged": "16.4.0"` retained); `.husky/pre-commit` runs `pnpm exec lint-staged` | ✓ VERIFIED | `lint-staged.config.mjs` read: `'backend/**/*.java': (_files) => \`"${gradlew}" spotlessApply -q\`` (Windows→`gradlew.bat`, else `gradlew`, path resolved from config location); `'apps/web/**/*.{ts,tsx,js,jsx}'` array `["pnpm --filter web exec eslint --fix","pnpm --filter web exec prettier --write"]` plus the 3 other web entries verbatim. `node -e "require('./package.json')"` → `'lint-staged' in p` = false; `p.devDependencies['lint-staged']` = `16.4.0`. `grep -n lint-staged package.json` → only line 25 (devDep). `.husky/pre-commit` = `pnpm exec lint-staged`. |
| 8 | CLAUDE.md Conventions §2 and CONVENTIONS.md §2 describe `usecases/` (not `application/`), omit `service/` from the valid list, keep `model/` in the anti-pattern list, list `gateway/`, carry the layering-doctrine sentence | ✓ VERIFIED | CLAUDE.md item 2: contains "Zero Mail's backend follows DDD strategic design ... Clean Architecture use-case layer in `usecases/` + Hexagonal ports/adapters ..."; lists `domain/ usecases/ projection/ exception/ persistence/ gateway/`; "Do not add ambiguous `core.<domain>.model.*` and do not add a `core.<domain>.service.*` catch-all". No `core.<ctx>.application` package-layout refs in CLAUDE.md. CONVENTIONS.md §2: same doctrine sentence (lines 28–32), `usecases/` folder list, `gateway/` listed (line 49), `model`/`service` catch-all in anti-pattern list (line 62), example path `core.rules.usecases.RuleCompileResult` (line 53), `domain/` noted as framework-free `DomainPurityArchTest`-enforced (line 43). `grep -c 'DDD strategic design'` → 1 each (per executor; consistent with reads). |

**Score:** 8/8 truths verified.

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `lint-staged.config.mjs` | Cross-platform config, function `backend/**/*.java` entry | ✓ VERIFIED | Present at repo root; function entry confirmed; web globs verbatim; parses (`import()` returns object with function-typed backend key). |
| `backend/core/src/test/java/com/zeromail/core/arch/DomainPurityArchTest.java` | New bright-line framework-free-domain rule | ✓ VERIFIED | Present; `noClasses().that().resideInAPackage("..com.zeromail.core.*.domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..","tools.jackson..","jakarta.persistence..","org.hibernate..")` with verbatim `because(...)`; `DoNotIncludeTests` importer; no `allowEmptyShould`. |
| `backend/core/.../rules/usecases/package-info.java` | Renamed rules use-case marker | ✓ VERIFIED | Present: `/** Use-case services, commands, and operation results for the rules context. */`. |
| `backend/core/.../llm/usecases/package-info.java` | Renamed llm use-case marker | ✓ VERIFIED | Present, analogous text. |
| `backend/core/.../triage/usecases/package-info.java` | Renamed triage use-case marker | ✓ VERIFIED | Present, analogous text. |
| `backend/core/.../config/package-info.java` | Modulith marker for core.config | ✓ VERIFIED | Present (Task 1, commit 2928065). |
| `backend/core/.../tenant/concurrency/package-info.java` | Modulith marker | ✓ VERIFIED | Present. |
| `backend/core/.../billing/persistence/package-info.java` | Modulith marker | ✓ VERIFIED | Present. |
| `backend/core/.../llm/byok/package-info.java` | Modulith marker | ✓ VERIFIED | Present. |
| `backend/core/.../gmail/gateway/package-info.java` | New gmail external-I/O adapter marker | ✓ VERIFIED | Present: `/** Gmail external-I/O adapters: OAuth token refresh and Gmail API client construction. */`. |
| `backend/core/src/test/.../rules/usecases/RulePreviewWriteBoundaryTest.java` | Relocated arch test (was core.rules.service) | ✓ VERIFIED | `find backend/core/src/test -name RulePreviewWriteBoundaryTest.java` → only `.../rules/usecases/RulePreviewWriteBoundaryTest.java`. No `rules/service` test dir remains. Test passes under `--rerun-tasks`. |
| `CLAUDE.md` | §2 updated to usecases/ + doctrine | ✓ VERIFIED | See truth 8. |
| `CONVENTIONS.md` | §2 updated to usecases/ + doctrine | ✓ VERIFIED | See truth 8. |
| (also) `backend/core/.../{account,gmail,onboarding,tenant,billing}/usecases/package-info.java` | New use-case markers for contexts that gained `usecases/` | ✓ VERIFIED | All 5 present (find check). |

## Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `.husky/pre-commit` | `lint-staged.config.mjs` | `pnpm exec lint-staged` auto-discovers config | ✓ WIRED | `.husky/pre-commit` = `pnpm exec lint-staged`; config file at repo root (auto-discovery default). |
| `ZeroMailApiApplicationModulesTest` | renamed `core.<ctx>.usecases` + dissolved `service/` + no empty `model/` | `ApplicationModules.of(...).verify()` | ✓ WIRED | `:backend:api:test --tests '*ApplicationModulesTest*' --rerun-tasks` → BUILD SUCCESSFUL. |
| `DomainPurityArchTest` | every `core.<ctx>.domain..` class | ArchUnit `noClasses().that().resideInAPackage("..com.zeromail.core.*.domain..")...` | ✓ WIRED | Test exists, passes under `--rerun-tasks`; domain dirs (billing/gmail/llm/onboarding/rules/triage) all have classes — rule has subjects. |

## Documented Deviations (accepted per plan)

The plan explicitly states: if a class cannot satisfy the no-whitelist domain-purity rule, re-home it to `usecases/` keeping its annotation — do NOT weaken the rule. The following deviations from the locked re-homing table are therefore PASS, not gaps:

- `RuleCompileResultValidator` → `rules/usecases/` (not `rules/domain/`) — imports `tools.jackson.*`. Confirmed at `backend/core/src/main/java/com/zeromail/core/rules/usecases/RuleCompileResultValidator.java`.
- Five pre-existing framework-coupled `domain/` classes (`rules.domain.ActionIntentJsonValidator`, `rules.domain.RuleAstJsonValidator`, `triage.domain.SenderEmailCanonicalizer`, `triage.domain.TriageActionArgsCanonicalizer`, `triage.domain.TriageActionResultJsonValidator`) → moved to `usecases/`. Confirmed: no banned imports remain in any `core/*/domain/` package, and `DomainPurityArchTest` passes.
- `GmailApiClientFactory` → `gmail/gateway/`. Confirmed: `gmail/gateway/package-info.java` present; no `gmail/service/` dir.

All accepted because `./gradlew check` is green and no `domain/` class imports a banned package.

## Commits

| Commit | Subject | Status |
|--------|---------|--------|
| `2928065` | `chore(core): remove empty model/ packages, add missing package-info, fix cross-platform lint-staged java config` | ✓ Present in `git log` |
| `8fd53d3` | `refactor(core): rename application/ to usecases/ in llm, rules, triage` | ✓ Present |
| `81fba52` | `refactor(core): dissolve service/ into usecases+domain; relocate rule-preview arch test` | ✓ Present |
| `e7cc431` | `arch(core): enforce framework-free domain/; document layering doctrine` | ✓ Present |

Branch `gsd/phase-04-triage-convergence-hero`; `git status --porcelain` → only the untracked SUMMARY.md (working tree otherwise clean).

## Anti-Patterns Found

None. No `TODO`/`FIXME`/`XXX`/`HACK` markers introduced; no stubbed implementations; `model/`, `service/`, `application/` package dirs all absent under `backend/core`.

## Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full backend build + all tests | `./gradlew check` | BUILD SUCCESSFUL | ✓ PASS |
| ArchUnit suite incl. new rule re-run from scratch | `./gradlew :backend:core:test --tests 'com.zeromail.core.arch.*' --tests '...BillingDomainBoundaryArchTest' --tests '...rules.usecases.RulePreviewWriteBoundaryTest' --rerun-tasks` | BUILD SUCCESSFUL | ✓ PASS |
| Spring Modulith verification re-run from scratch | `./gradlew :backend:api:test --tests '*ApplicationModulesTest*' --rerun-tasks` | BUILD SUCCESSFUL | ✓ PASS |
| lint-staged config parses, backend entry is a function | `node -e "import('./lint-staged.config.mjs')..."` | `backend entry type: function` | ✓ PASS |
| package.json has no top-level lint-staged block | `node -e "require('./package.json')..."` | `has top-level lint-staged key: false`; devDep = `16.4.0` | ✓ PASS |

## Human Verification Required

None. All must-haves verified programmatically (build + grep + file reads). The teeth-check on `DomainPurityArchTest` (executor temporarily added a framework import to a `domain/` class, confirmed BUILD FAILED, reverted) was not re-performed by the verifier since the rule's structure (no whitelist, real subjects, banned-package list correct) is directly observable in source and the rule passes against the real tree.

## Gaps Summary

No gaps. The phase goal is achieved: `application/` renamed to `usecases/` in llm/rules/triage; all `service/` packages dissolved (main and test, including the relocated arch test); `DomainPurityArchTest` exists with no whitelist / no `allowEmptyShould`, runs as part of `./gradlew check`, and passes; empty `model/` dirs deleted; missing `package-info.java` added; CLAUDE.md §2 + CONVENTIONS.md §2 synced with `usecases/` + the layering-doctrine sentence; `lint-staged.config.mjs` in place with function-form backend entry and verbatim web globs; `package.json` top-level `lint-staged` block removed (devDep retained); four expected commits present; `./gradlew check` BUILD SUCCESSFUL.

---

_Verified: 2026-05-12_
_Verifier: Claude (gsd-verifier)_
