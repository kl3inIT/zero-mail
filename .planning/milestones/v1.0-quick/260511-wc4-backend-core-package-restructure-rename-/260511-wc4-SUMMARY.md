---
quick_task: 260511-wc4
title: Backend core package restructure — rename application/→usecases/, dissolve service/, framework-free domain/ rule
status: complete
must_haves:
  truths:
    - status: pass
      truth: "`./gradlew check` passes: all-module compileJava + compileTestJava, the updated ArchUnit suite, the NEW DomainPurityArchTest, Spring Modulith verification (ZeroMailApiApplicationModulesTest), and the full backend test suite."
    - status: pass
      truth: "No `com.zeromail.core.*.service` package exists under `backend/core/src/main` (find returns nothing)."
    - status: pass
      truth: "No `com.zeromail.core.*.service` package exists under `backend/core/src/test` (rules/service arch test relocated; all behavioural service tests re-homed)."
    - status: pass
      truth: "No empty `com.zeromail.core.*.model` package directory exists under `backend/core` (done in Task 1, commit 2928065)."
    - status: pass
      truth: "`git grep 'com\\.zeromail\\.core\\.\\(rules\\|llm\\|triage\\)\\.application' -- backend` returns nothing."
    - status: pass
      truth: "No class in any `com.zeromail.core.<context>.domain..` package depends on `org.springframework..`, `tools.jackson..`, `jakarta.persistence..`, or `org.hibernate..` — DomainPurityArchTest (no whitelist, no allowEmptyShould) passes; teeth-check confirmed it FAILS when a framework import is added."
    - status: pass
      truth: "`lint-staged.config.mjs` exists at repo root with a function-based `backend/**/*.java` entry; `package.json` has no top-level `lint-staged` block; `.husky/pre-commit` (`pnpm exec lint-staged`) picks up the config — verified empirically: the hook ran `gradlew.bat spotlessApply -q` on the Task 3 and Task 4 commits on Windows."
    - status: pass
      truth: "CLAUDE.md Conventions §2 and CONVENTIONS.md §2 describe `usecases/` (not `application/`), omit `service/` from the valid list, keep `model/` in the anti-pattern list, list `gateway/` for `llm` and `gmail`, and carry the verbatim layering-doctrine sentence."
  artifacts:
    - path: lint-staged.config.mjs
      status: present
      note: "Created in Task 1 (commit 2928065). Function-based backend/**/*.java entry resolves the Gradle wrapper from the config file's own location and quotes the path — works on Windows; web glob entries preserved."
    - path: backend/core/src/test/java/com/zeromail/core/arch/DomainPurityArchTest.java
      status: present
    - path: backend/core/src/main/java/com/zeromail/core/rules/usecases/package-info.java
      status: present
    - path: backend/core/src/main/java/com/zeromail/core/llm/usecases/package-info.java
      status: present
    - path: backend/core/src/main/java/com/zeromail/core/triage/usecases/package-info.java
      status: present
    - path: backend/core/src/main/java/com/zeromail/core/config/package-info.java
      status: present
      note: "added in Task 1 (commit 2928065)"
    - path: backend/core/src/main/java/com/zeromail/core/tenant/concurrency/package-info.java
      status: present
      note: "added in Task 1 (commit 2928065)"
    - path: backend/core/src/main/java/com/zeromail/core/billing/persistence/package-info.java
      status: present
      note: "added in Task 1 (commit 2928065)"
    - path: backend/core/src/main/java/com/zeromail/core/llm/byok/package-info.java
      status: present
      note: "added in Task 1 (commit 2928065)"
    - path: backend/core/src/main/java/com/zeromail/core/gmail/gateway/package-info.java
      status: present
    - path: backend/core/src/test/java/com/zeromail/core/rules/usecases/RulePreviewWriteBoundaryTest.java
      status: present
      note: "relocated from core.rules.service; package + GmailPreviewReadService import + the two ArchUnit package literals updated to usecases"
    - path: CLAUDE.md
      status: updated
    - path: CONVENTIONS.md
      status: updated
metrics:
  duration: ~90min
  tasks: 4
  commits: 4
---

# Quick Task 260511-wc4: Backend core package restructure — Summary

Restructured `backend/core` to the locked DDD + Clean Architecture + Hexagonal layout: deleted leftover empty `model/` packages, backfilled missing Spring Modulith `package-info.java`, renamed `application/` → `usecases/` in the three contexts that had it, dissolved every `service/` package into `usecases/` (Spring beans) / `domain/` (pure logic) / `gmail/gateway/` (external-I/O adapter), relocated the stray `core.rules.service` arch test, added a bright-line `DomainPurityArchTest` banning framework imports from `domain/`, synced `CLAUDE.md` + `CONVENTIONS.md`, and (Task 1) replaced the Windows-broken `lint-staged` `package.json` block with a cross-platform `lint-staged.config.mjs`. Delivered in four commits; each compiles. `./gradlew check` is green.

## Commit SHAs

| Task | SHA | Subject |
|------|-----|---------|
| 1 (cleanup + lint-staged) | `2928065` | `chore(core): remove empty model/ packages, add missing package-info, fix cross-platform lint-staged java config` |
| 2 (application→usecases rename) | `8fd53d3` | `refactor(core): rename application/ to usecases/ in llm, rules, triage` |
| 3 (dissolve service/) | `81fba52` | `refactor(core): dissolve service/ into usecases+domain; relocate rule-preview arch test` |
| 4 (new ArchUnit rule + docs) | `e7cc431` | `arch(core): enforce framework-free domain/; document layering doctrine` |

(Tasks 1 & 2 were already committed by an earlier executor run; the orchestrator's pre-dispatch commit is `33b1e0b`. This run executed Tasks 3 and 4 and re-verified everything.)

## Final re-homing of every former `service/` class

### account/service → all to `account/usecases/` (keep `@Service`)
- `AccountService`, `OAuthProvisioningService` — `usecases/`, `@Service` kept. `package-info.java` deleted; added `account/usecases/package-info.java`.

### billing/service → mix
- `BillingTopupService` → `billing/usecases/` (`@Service` kept). **Call-site change:** dropped the `TopupCodeGenerator topupCodeGenerator` constructor parameter and replaced it with `private final TopupCodeGenerator topupCodeGenerator = new TopupCodeGenerator();` (it is no longer a Spring bean).
- `CreditLedger` (interface) → `billing/usecases/`.
- `CreditLedgerService` → `billing/usecases/` (`@Service` kept; package-private impl of `CreditLedger`).
- `SepayApiKeyVerifier` → `billing/usecases/` (`@Component` kept; HMAC compare only, no HTTP → `usecases/`, not `gateway/`).
- `TopupCodeGenerator` → `billing/domain/` (**removed `@Component`** + its import; only field is `new SecureRandom()` → pure domain). Caller `BillingTopupService` swapped to `new TopupCodeGenerator()` (see above).
- `billing/service` had no `package-info.java`; added `billing/usecases/package-info.java`.

### gmail/service → mix
- `GmailApiClientFactory` → `gmail/gateway/` (`@Component` kept; external Gmail OAuth/token I/O adapter). Created `gmail/gateway/package-info.java`. **Within-context import added** to `GmailConnectionService`, `GmailDeliveryProcessingService`, `GmailPreviewReadService` (all now `gmail/usecases/`) and to `GmailApiClientFactory` itself (`import com.zeromail.core.gmail.usecases.InvalidGrantException;`).
- `GmailConnectionService`, `GmailDeliveryProcessingService`, `GmailPreviewReadService`, `PubSubIngestionService` → `gmail/usecases/` (`@Service` kept).
- `IngestResult` (enum), `InvalidGrantException` → `gmail/usecases/` (per the locked table — `GmailApiClientFactory` in `gateway/` imports `InvalidGrantException` from `usecases/`; an intra-context dependency, accepted).
- `package-info.java` deleted; added `gmail/usecases/package-info.java` + `gmail/gateway/package-info.java`.

### llm/service → mix
- `LlmGateway` (interface), `LlmGatewayImpl` (`@Service` kept), `ByokService` (`@Service` kept), `ByokLlmModelClient`, `LlmModelClient`, `SemanticIntentEvaluator` (the seam interface; the Spring AI impl already lives in `llm/gateway/springai/`) → `llm/usecases/`.
- `ActionValidator`, `AllowListedTools`, `RuleCompileToolValidator` → `llm/domain/` (**removed `@Component`** + imports; no injected fields → pure domain). **Call-site change:** `LlmGatewayImpl`'s `@Autowired` constructor dropped these three parameters and now passes `new AllowListedTools()`, `new ActionValidator()`, `new RuleCompileToolValidator()`; the test-facing (non-`@Autowired`) constructors still accept them and tests already pass `new …()` instances, so no test churn. Imports for the three `llm/domain` classes added to `LlmGatewayImpl` and to the six `llm/usecases` test classes that construct them by simple name.
- `package-info.java` deleted (the renamed `application/`→`usecases/` package-info from Task 2 already covers `llm/usecases`). `llm/package-info.java` JavaDoc already mentioned `domain`/`usecases`/`gateway` (updated in Task 2).

### onboarding/service → `onboarding/usecases/`
- `OnboardingService` → `usecases/` (`@Service` kept). `package-info.java` deleted; added `onboarding/usecases/package-info.java`.

### rules/service → mix
- `RuleCompilerService`, `RuleManagementService`, `RulePreviewService`, `RulePreviewDataService`, `RuleTemplateCatalogService`, `RuleTemplateMaterializationService` → `rules/usecases/` (`@Service` kept).
- `RuleEvaluator` → `rules/domain/` (plain class, no annotation). Callers already use `new RuleEvaluator()`.
- `ActionProposalMerger` → `rules/domain/` (plain class; default ctor does `this(new RuleEvaluator())` → pure domain). Caller `RulePreviewService` already uses `new ActionProposalMerger()`. **Within-context imports added** to `RulePreviewService` for `RuleEvaluator` and `ActionProposalMerger`.
- **`RuleCompileResultValidator` → `rules/usecases/` (DEVIATION from the locked table, which said `rules/domain/` + remove `@Component`).** Rationale: this class imports `tools.jackson.databind.ObjectMapper`/`JsonMapper`/`JacksonException` and the new `DomainPurityArchTest` bans `tools.jackson..` from any `domain/` package with no exemption. Per the plan's explicit instruction ("if a class in a domain/ package can't satisfy this rule, that class was mis-homed — move it to usecases/, keeping its annotation"), it landed in `rules/usecases/` and kept `@Component`. `RuleCompilerService` still `@Autowired`-injects it (same package, still a bean) — no call-site change.
- `package-info.java` deleted (the renamed `application/`→`usecases/` package-info covers `rules/usecases`).

### tenant/service → `tenant/usecases/`
- `TenantService` → `usecases/` (`@Service` kept). `package-info.java` deleted; added `tenant/usecases/package-info.java`. Fixed a stale `{@code core.tenant.service.*}` JavaDoc reference.

### triage/service → mix
- `SenderSafetyNetService`, `TriageGmailWriter` → `triage/usecases/` (`@Component` kept).
- `TriageSafetyPolicy` → `triage/domain/` (**removed `@Component`** + its import; pure gate logic, only slf4j + core imports remain — passes the rule). **Call-site change:** `TriageOrchestratorService`'s constructor dropped the `TriageSafetyPolicy triageSafetyPolicy` parameter and now does `this.triageSafetyPolicy = new TriageSafetyPolicy();` (only Spring constructs `TriageOrchestratorService`; no test churn).
- `package-info.java` deleted (the renamed `application/`→`usecases/` package-info covers `triage/usecases`).

### Additional pre-existing `domain/` violators re-homed in Task 4 (not in the plan's locked table — `DomainPurityArchTest` surfaced them)
The new rule failed against the pre-existing tree because these classes were already framework-coupled. Per the plan ("if a class can't satisfy the rule, it was mis-homed — move it to usecases/, keeping its annotation. Do NOT weaken the rule"):
- `rules.domain.ActionIntentJsonValidator`, `rules.domain.RuleAstJsonValidator` (both use `tools.jackson`) → `rules/usecases/`. Within-context imports added (`RuleActionType`, `MatcherNode`, `MatcherType`, `RuleSchemaVersion`); `RuleEntity`/`RuleTemplateEntity` (in `rules/persistence`) now import them from `rules/usecases` (an intra-context persistence→usecases reference; not blocked by any arch test); `RuleModelTest` (kept in `rules/domain`) now imports them from `rules/usecases`.
- `triage.domain.SenderEmailCanonicalizer` (`@Component`), `triage.domain.TriageActionArgsCanonicalizer` (`@Component` + `tools.jackson`), `triage.domain.TriageActionResultJsonValidator` (`@Component` + `tools.jackson`) → `triage/usecases/`. Annotations kept; within-context `TriageActionResult` import added to the two that need it; callers in `triage/persistence`/`triage/usecases`/`backend/api` had their imports rewritten by the bulk FQN pass.

## Relocation of `RulePreviewWriteBoundaryTest`
`git mv backend/core/src/test/.../rules/service/RulePreviewWriteBoundaryTest.java → .../rules/usecases/RulePreviewWriteBoundaryTest.java`; package declaration → `com.zeromail.core.rules.usecases`; `import com.zeromail.core.gmail.service.GmailPreviewReadService;` → `…gmail.usecases.GmailPreviewReadService;`; the two ArchUnit string literals `"..core.rules.service.."` / `"..core.gmail.service.."` → `"..core.rules.usecases.."` / `"..core.gmail.usecases.."`; old empty `rules/service` test dir removed. Behavioural service tests were `git mv`'d to follow their production targets (e.g. `billing/service/*Test` → `billing/usecases/`, `TopupCodeGeneratorTest` → `billing/domain/`, `llm/service/*Test` → `llm/usecases/` or `llm/domain/`, `rules/service/*Test` → `rules/usecases/` or `rules/domain/`, etc.).

## Other arch / Modulith test edits
- `TriageGmailWriteBoundaryTest`: `"com.zeromail.core.triage.service.TriageGmailWriter"` → `"…triage.usecases.TriageGmailWriter"` (caught by the FQN pass).
- `BillingDomainBoundaryArchTest`: `"..core.billing.service.."` → `"..core.billing.usecases.."`; FQN literal rewritten; test method renamed `credit_ledger_service_not_instantiated_outside_billing_service` → `…_outside_billing_usecases`.
- Fixed stale **source-file path string literals** that the dotted-FQN pass cannot catch: `RuleEvaluatorTest` (`src/main/java/com/zeromail/core/rules/service/RuleEvaluator.java` → `…/rules/domain/RuleEvaluator.java`), `RulePreviewServiceWave0Test` (`com/zeromail/core/rules/service` → `…/rules/usecases`), `SenderSafetyNetServiceContractTest` (`…/triage/service/SenderSafetyNetService.java` → `…/triage/usecases/SenderSafetyNetService.java`), and stale `triage/application/` path strings in six worker/core triage contract tests (Task 2 leftovers — `triage/application/` → `triage/usecases/`). `Class.forName(...)` reflection literals in Wave0/contract tests were caught by the FQN pass.

## New ArchUnit rule + teeth check
`backend/core/src/test/java/com/zeromail/core/arch/DomainPurityArchTest.java` — `noClasses().that().resideInAPackage("..com.zeromail.core.*.domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..","tools.jackson..","jakarta.persistence..","org.hibernate..")`, with the verbatim `because(...)` clause, **no whitelist, no `allowEmptyShould`**. `ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.zeromail")` style. Teeth check: temporarily added `@org.springframework.stereotype.Service` to `rules/domain/RuleEvaluator.java`, ran `:backend:core:test --tests 'com.zeromail.core.arch.DomainPurityArchTest'` → **BUILD FAILED** (rule violated), then reverted (no diff). Test passes against the post-Task-3+4 tree and runs as part of `./gradlew check`.

## Docs edits
- `CLAUDE.md` Conventions §2: rewritten — `usecases/` (not `application/`), `service/` removed from the valid list, `model/` kept in the anti-pattern list, `gateway/` listed for `llm`+`gmail`, plus the verbatim DDD-strategic + tactical-domain + Clean-Architecture-usecases + Hexagonal-ports/adapters layering-doctrine sentence (incl. the CQRS-lite and repository-per-entity documented deviations).
- `CONVENTIONS.md` §2: same updates — `usecases/` not `application/`; `service/` removed; `model/` and a new `core.<domain>.service.*` catch-all both in the anti-pattern list; `domain/` now explicitly noted as framework-free with `DomainPurityArchTest` enforcement; `gateway/` added to the folder list; example path `core.rules.application.RuleCompileResult` → `core.rules.usecases.RuleCompileResult`; same doctrine sentence near the top of §2.

## lint-staged.config.mjs content (Task 1, commit 2928065)
Function-based `'backend/**/*.java': (_files) => '"<repoRoot>/gradlew[.bat]" spotlessApply -q'` where the wrapper path is resolved from the config file's own location (`fileURLToPath(import.meta.url)`) and quoted, with forward slashes (string-argv strips backslashes); `process.platform === 'win32' ? 'gradlew.bat' : 'gradlew'`. The four `apps/web/...` array entries kept verbatim. `package.json`'s top-level `"lint-staged"` block removed; the `"lint-staged"` devDependency line untouched. `.husky/pre-commit` (`pnpm exec lint-staged`) auto-discovers the config — empirically confirmed: the hook ran `gradlew.bat spotlessApply -q` on this run's Task 3 and Task 4 commits on Windows.

## Tooling deviation
JetBrains MCP `get_file_problems` was unavailable to the executor (upstream anthropics/claude-code#13898). Substituted `./gradlew compileJava compileTestJava` (all modules) as the parse/type gate after Java edits, plus targeted `./gradlew :backend:core:test --tests …` for the arch tests, and the full `./gradlew check` as the backstop.

## OOM retries
None needed — `./gradlew check` completed cleanly on the default daemon (no test-worker OOM observed this run).

## Final verification (all pass)
- `./gradlew check` — BUILD SUCCESSFUL (all-module compile, full ArchUnit suite incl. new `DomainPurityArchTest`, `BillingDomainBoundaryArchTest`, relocated `RulePreviewWriteBoundaryTest`, Spring Modulith `ZeroMailApiApplicationModulesTest`, full backend test suite).
- `find backend/core/src/main -type d \( -name service -o -name model -o -name application \)` → nothing.
- `find backend/core/src/test -type d \( -name service -o -name application \)` → nothing.
- `git grep 'com\.zeromail\.core\.\(rules\|llm\|triage\)\.application' -- backend` → nothing.
- `git grep 'com\.zeromail\.core\.[a-z]*\.service\.' -- backend` → nothing.
- `git grep -E 'package com\.zeromail\.core\.[a-z]+\.service;' -- backend` → nothing.
- `node -e "import('./lint-staged.config.mjs')…"` → `backend/**/*.java` is a function.
- `grep -c '"lint-staged"' package.json` → 1 (devDependency line only).
- `grep -c 'DDD strategic design' CLAUDE.md CONVENTIONS.md` → 1 each.
- `git status` → clean.

## Self-Check: PASSED
- `backend/core/src/test/java/com/zeromail/core/arch/DomainPurityArchTest.java` — FOUND.
- `backend/core/src/main/java/com/zeromail/core/gmail/gateway/package-info.java` — FOUND.
- `backend/core/src/main/java/com/zeromail/core/rules/usecases/RuleCompileResultValidator.java` — FOUND.
- `backend/core/src/test/java/com/zeromail/core/rules/usecases/RulePreviewWriteBoundaryTest.java` — FOUND.
- `lint-staged.config.mjs` — FOUND.
- Commits `2928065`, `8fd53d3`, `81fba52`, `e7cc431` — all FOUND in `git log`.
