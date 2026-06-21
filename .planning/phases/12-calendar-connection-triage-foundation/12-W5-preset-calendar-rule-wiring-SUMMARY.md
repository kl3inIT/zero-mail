---
phase: 12-calendar-connection-triage-foundation
plan: W5
subsystem: preset-calendar-rule-wiring
status: complete
tags: [cal-triage, rule-engine, matcher-ast, liquibase-migration, no-llm-call, modulith-boundaries]
requirements_completed: [CAL-TRIAGE-03]
requires:
  - MessageClass IdentifiedEnum (W4 Task 1) — already shipped
  - gmail_inbox_projection.message_class column (W0 changeset 134) — already shipped
  - GmailInboxProjectionEntity.getMessageClassOptional() (W4) — already shipped
  - findByTenantConnectionAndMessage finder (W4) — already shipped
  - 113-default-rule-templates-seed.yaml (Phase 8) — already shipped, NOT edited
  - 114-default-rule-templates-vi-seed.yaml (Phase 8) — already shipped, NOT edited
provides:
  - com.zeromail.core.rules.domain.MatcherType.PRESET_CALENDAR (new enum value)
  - com.zeromail.core.rules.domain.MatcherNode.PresetCalendarMatcher (new sealed-interface
    permit + record, framework-free, requiresBodyEvidence=false)
  - RuleEvaluationInput.messageClass() Optional<MessageClass> accessor — back-compat
    constructor preserves all 9 existing call sites
  - RuleEvaluator.evaluate(...) new switch arm — terminal MATCHED when messageClass is
    present, terminal NOT_MATCHED otherwise; diagnostic="preset_calendar"; no DEFERRED state
  - InboxProjectionReadService.findMessageClass(tenantId, gmailConnectionId, gmailMessageId)
    — exposes the W4 column to cross-context callers via the inbox::usecases named interface
  - TriageRuleEvaluationInputFactory now reads the projection's messageClass at fetch time
    and threads it through the RuleEvaluationInput sent to the evaluator
  - RuleAstJsonValidator + RuleCompileResultValidator + TriageOrchestratorService.parseMatcherNode
    + RulePreviewService.parseMatcherNode — all 4 JSON→MatcherNode codecs now handle the
    PRESET_CALENDAR shape (nodeId is the only carrier; no extra arguments)
  - Liquibase 136-system-calendar-template-preset-matcher.yaml — roll-forward data
    migration rewriting matcher_ast on rule_template_catalog (system-calendar + system-calendar-vi)
    AND on already-materialized rule rows for both keys, from SEMANTIC_INTENT to PRESET_CALENDAR.
    Customized rules (nodeId differs from seed) are PRESERVED. Idempotent by WHERE.
affects:
  - rules + triage Modulith modules gain `inbox :: domain` dependency
  - triage module gains `inbox :: usecases` dependency (added by W4, used here for read service)
  - Existing dev/prod tenants whose system-calendar rule was materialized from the SEMANTIC_INTENT
    seed are silently migrated to the PRESET shape on the next backend boot (the moment Liquibase
    applies changeset 136)
  - Future fresh-database deployments end up identical: 113 seeds SEMANTIC_INTENT then 136
    immediately rewrites to PRESET_CALENDAR in the same Liquibase apply
tech_stack_added:
  - none — ical4j, MessageClass enum, projection columns all shipped in earlier waves
patterns_followed:
  - Sealed-interface permits + nested record (CONVENTIONS.md §3, mirrors SemanticIntentMatcher)
  - Back-compat non-canonical record constructor delegating to canonical (preserves 9 call sites,
    no test churn outside the migration test + factory test)
  - IdentifiedEnum + fail-loud fromId on MatcherType (CONVENTIONS.md §4) — PRESET_CALENDAR
    follows the existing fromId contract automatically
  - Liquibase 113/114 are immutable (CLAUDE.md §10); 136 is a forward-only fix-via-new-changeset
    that mirrors the precedent set by 023-fix-pin-calendar-category
  - Idempotency-by-WHERE on the migration UPDATEs — no runOnChange / runAlways /
    clear-checksums (forbidden per CLAUDE.md §10)
  - Cross-module read access via the inbox::usecases NamedInterface, not via the persistence
    repository — keeps the Modulith boundary green
  - Privacy: no body / sender email / intent text in logs; the matcher diagnostic is the
    constant string "preset_calendar"
key_files_created:
  - backend/core/src/main/resources/db/changelog/changes/136-system-calendar-template-preset-matcher.yaml
  - backend/core/src/test/java/com/zeromail/core/rules/domain/RuleEvaluatorCalendarPresetTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/usecases/SystemCalendarTemplateMigrationTest.java
key_files_modified:
  - backend/core/src/main/java/com/zeromail/core/rules/domain/MatcherType.java
  - backend/core/src/main/java/com/zeromail/core/rules/domain/MatcherNode.java
  - backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluationInput.java
  - backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluator.java
  - backend/core/src/main/java/com/zeromail/core/rules/package-info.java
  - backend/core/src/main/java/com/zeromail/core/rules/usecases/RuleAstJsonValidator.java
  - backend/core/src/main/java/com/zeromail/core/rules/usecases/RuleCompileResultValidator.java
  - backend/core/src/main/java/com/zeromail/core/rules/usecases/RulePreviewService.java
  - backend/core/src/main/java/com/zeromail/core/triage/package-info.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageRuleEvaluationInputFactory.java
  - backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionReadService.java
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
  - backend/core/src/test/java/com/zeromail/core/rules/domain/RuleAstContractTest.java
  - backend/core/src/test/java/com/zeromail/core/triage/usecases/TriageRuleEvaluationInputFactoryTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/usecases/RuleTemplateMaterializationServiceTest.java
decisions:
  - "Changeset id renumbered from the plan's `135-...` to `136-...`. Plan PLAN.md was authored
    before W2's changeset 135-calendar-tables-version-column.yaml landed in the master changelog;
    the next free id is 136. The semantics are unchanged — Liquibase ordering is solely
    determined by the master include order, and 136 is appended after 135. Plan acceptance
    criteria still match: `grep -c 'PRESET_CALENDAR' 136-system-calendar-template-preset-matcher.yaml`
    returns 8 (well above the required minimum of 2)."
  - "Threading messageClass into the rule evaluation path: chose option (A) — add a new
    read method `InboxProjectionReadService.findMessageClass(...)` and call it from
    `TriageRuleEvaluationInputFactory.fetch(...)`. Rejected option (B) of widening the
    `MailMessageObserved` event payload (would violate the privacy invariant that the
    integration event bus carries IDs only — body/sender email never traverse it).
    Rejected option (C) of pushing the read into `GmailPreviewReadService.fetchTriageInput`
    (would conflate the live-Gmail fetch with the projection read; the existing service
    is intentionally Gmail-API-only). The new read is a single-row, single-column lookup
    keyed by the existing (tenant_id, gmail_connection_id, gmail_message_id) finder W4 added."
  - "Backward compatibility on `RuleEvaluationInput`: added a non-canonical record constructor
    that delegates to the canonical constructor with `Optional.empty()` for `messageClass`.
    This preserves the 9 existing call sites (RuleEvaluatorTest, ActionProposalMergerTest,
    SemanticEvalContentBuilderTest, TriageOutboundRuntimeGateTest, RulesControllerPrivacyTest,
    RulesControllerIntegrationTest, RulePreviewService, RulePreviewDataService, plus the
    factory we did update). The PRESET matcher returns NOT_MATCHED for any input that did
    not opt-in to passing a messageClass — which is the correct semantic for legacy code
    paths that pre-date Phase 12 W4."
  - "AllowListedTools (LLM rule-builder tool catalog) is DELIBERATELY not changed. The new
    PRESET_CALENDAR matcher must NOT be exposable to the LLM rule-builder — otherwise an
    arbitrary user-typed natural-language prompt could generate a preset matcher rule. The
    preset matcher is a system-managed matcher seeded only via the 113/114 + 136 changeset
    pair. RuleAstJsonValidator (the user-facing validator) does accept PRESET_CALENDAR in
    case a future admin tool needs to write it, but the LLM cannot."
  - "Module boundaries: had to add `inbox :: domain` to rules's `allowedDependencies` (so
    `RuleEvaluationInput` can carry `MessageClass`), and add `inbox :: domain` to triage's
    `allowedDependencies` (so the factory can read `MessageClass` from the projection read
    service). The Modulith `ZeroMailApiApplicationModulesTest` verification stays green."
  - "RuleTemplateMaterializationServiceTest's invariant 'every default rule's matcher_ast
    contains SEMANTIC_INTENT' is loosened to `containsAnyOf(SEMANTIC_INTENT, PRESET_CALENDAR)`.
    This is correct: after 136 applies, the calendar rule is structurally PRESET, but every
    OTHER seeded rule remains SEMANTIC_INTENT. The original assertion's intent — 'matchers
    are non-trivial' — still holds."
metrics:
  duration: "~50 minutes"
  tasks_completed: 2
  files_created: 3
  files_modified: 15
  tests_added: 8  # RuleEvaluatorCalendarPresetTest x5 (incl. parameterized 4 enum values) + SystemCalendarTemplateMigrationTest x3
  commits: 2
  completed_date: 2026-06-22
---

# Phase 12 Plan W5: Preset Calendar Rule Wiring — Summary

**One-liner:** Land CAL-TRIAGE-03 via a new sealed-interface matcher
permit `MatcherNode.PresetCalendarMatcher` that fires deterministically
whenever the W4 `message_class` column is set, plus a roll-forward
Liquibase changeset (136) that migrates existing tenants' seeded
calendar rule from SEMANTIC_INTENT to PRESET_CALENDAR. Zero LLM cost
on inbound calendar messages; user-authored rules retain full
authority — no backend downgrade, no CalendarAwareGuard, no audit
reason.

## Tasks Executed

| Task | Name                                                                                      | Commit     | Files                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| ---- | ----------------------------------------------------------------------------------------- | ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | PresetCalendarMatcher + RuleEvaluationInput.messageClass plumbing + 5-case unit test       | `b25dc3d5` | `MatcherType.java` (+PRESET_CALENDAR), `MatcherNode.java` (+permit + record), `RuleEvaluationInput.java` (+messageClass + back-compat constructor), `RuleEvaluator.java` (+switch arm), `InboxProjectionReadService.java` (+findMessageClass), `TriageRuleEvaluationInputFactory.java` (threads messageClass), `RuleAstJsonValidator.java` + `RuleCompileResultValidator.java` + `TriageOrchestratorService.java` + `RulePreviewService.java` (parser switches), 2 package-info Modulith updates, 2 test updates |
| 2    | Changeset 136 + master include + 3-case migration test                                     | `e377b331` | `136-system-calendar-template-preset-matcher.yaml`, `db.changelog-master.yaml`, `SystemCalendarTemplateMigrationTest.java`, `RuleTemplateMaterializationServiceTest.java` (loosen matcher assertion)                                                                                                                                                                                                                                                                                                            |

## Output Contract (from PLAN §output)

### (a) `git diff` against 113 / 114 is empty

```bash
$ git diff backend/core/src/main/resources/db/changelog/changes/113-default-rule-templates-seed.yaml backend/core/src/main/resources/db/changelog/changes/114-default-rule-templates-vi-seed.yaml
# (empty — confirmed)
```

113 and 114 are byte-identical pre/post. The W5 migration lives only in
the new 136 changeset, per CLAUDE.md §10 (applied changesets are
immutable).

### (b) Exact JSON shape PRESET_CALENDAR uses

After 136 applies:

```json
{
  "schemaVersion": "rules.v1",
  "type": "PRESET_CALENDAR",
  "nodeId": "system-calendar"
}
```

(`system-calendar-vi` for the Vietnamese template.) The `intent` field
present in the SEMANTIC_INTENT shape is intentionally dropped — the
preset matcher carries no LLM intent. The test
`SystemCalendarTemplateMigrationTest.changeset_136_migrates_template_catalog_to_preset_calendar`
asserts `jsonb_exists(matcher_ast, 'intent') = false` on both rows.

### (c) Uncustomized vs customized counts in the test fixture

`SystemCalendarTemplateMigrationTest.changeset_136_migrates_uncustomized_rule_rows_and_preserves_customized_rules`
inserts exactly **1 uncustomized + 1 customized** rule row, runs the
migration SQL, asserts the uncustomized row is rewritten and the
customized row is unchanged. The customized row's distinguishing
feature is `matcher_ast.nodeId = 'custom-cal'` (vs the seed's
`system-calendar`); the WHERE clause's `AND matcher_ast ->> 'nodeId'
= 'system-calendar'` excludes it.

### (d) LLM-call audit row is NOT written when the PRESET rule matches

The `RuleEvaluator.evaluate(...)` PRESET branch returns
`RuleEvaluationResult.matched(nodeId, "preset_calendar")` directly —
**no `LlmGateway` is invoked, no `llm_call_audit` row is written**.
Confirmed by reading the new switch arm at
`RuleEvaluator.java:115-119`: the branch calls only the existing
`terminal(...)` helper, which writes no audit, makes no I/O, and returns
synchronously. The `RuleEvaluatorCalendarPresetTest` cases all run
without a `LlmGateway` mock — there is no LLM dependency to mock —
which is the structural proof. (A future end-to-end test with a real
`TriageOrchestratorService` and a mocked `LlmGateway` is captured in
the Phase 12 verifier slate; for the W5 scope the structural-proof
read is sufficient.)

## Verification

```bash
./gradlew :backend:core:test --tests "com.zeromail.core.rules.domain.RuleEvaluatorCalendarPresetTest"
./gradlew :backend:core:test --tests "com.zeromail.core.rules.usecases.SystemCalendarTemplateMigrationTest"
./gradlew :backend:core:test --tests "com.zeromail.core.triage.usecases.TriageRuleEvaluationInputFactoryTest"
./gradlew :backend:core:test --tests "com.zeromail.core.rules.*"
./gradlew :backend:api:test --tests "com.zeromail.api.ZeroMailApiApplicationModulesTest"
```

| Test class                                       | Tests | Failed | Notes                                                                                                          |
| ------------------------------------------------ | ----- | ------ | -------------------------------------------------------------------------------------------------------------- |
| `RuleEvaluatorCalendarPresetTest`                | 5     | 0      | preset MATCHES on every MessageClass value; user SEMANTIC_INTENT unaffected; no auditReason regression          |
| `SystemCalendarTemplateMigrationTest`            | 3     | 0      | template catalog migrated; uncustomized rule migrated; customized preserved; idempotent WHERE-clause no-op      |
| `TriageRuleEvaluationInputFactoryTest`           | 1     | 0      | factory now constructed with `(GmailPreviewReadService, InboxProjectionReadService)`; observed-event path green |
| `RuleAstContractTest`                            | 2     | 0      | matcher-type vocabulary pin now includes `PRESET_CALENDAR`                                                      |
| `RuleTemplateMaterializationServiceTest`         | 4     | 0      | calendar rule now PRESET; assertion loosened to `containsAnyOf(SEMANTIC_INTENT, PRESET_CALENDAR)`                |
| `ZeroMailApiApplicationModulesTest`              | 30    | 0      | rules + triage modules' allowedDependencies updated cleanly; no boundary violations                             |

Total: **8 new tests added, all green** (plus all pre-existing
adjacent tests verified green).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Plan referenced changeset id 135 but 135 was already taken by W2**

- **Found during:** Task 2 file creation.
- **Issue:** PLAN.md was authored before W2 landed
  `135-calendar-tables-version-column.yaml` in the master changelog.
  The next free id is 136.
- **Fix:** Created the migration as
  `136-system-calendar-template-preset-matcher.yaml`. The Liquibase
  ordering is solely the master-include order, and 136 is appended
  after 135. Semantics unchanged.
- **Files modified:** `136-system-calendar-template-preset-matcher.yaml`
  (new), `db.changelog-master.yaml` (append).
- **Commit:** `e377b331`.

**2. [Rule 3 — Blocking] Rules table `gmail_connection_id` NOT NULL constraint (W4)**

- **Found during:** Task 2 initial test run.
- **Issue:** The migration test's `insertRule` helper did not provide
  `gmail_connection_id`, which is NOT NULL after changeset 126
  (rules-mailbox-ownership). Insert failed.
- **Fix:** Added `insertGmailConnection(...)` helper and threaded the
  new column through `insertRule(...)`. Mirrors the pattern used by
  `TenantInspectionReadRepositoryTest`.
- **Files modified:** `SystemCalendarTemplateMigrationTest.java`.
- **Commit:** Folded into Task 2's commit `e377b331`.

**3. [Rule 3 — Blocking] JDBC binding clash with Postgres JSONB `?` exists operator**

- **Found during:** Task 2 second test run.
- **Issue:** Used `matcher_ast ? 'intent'` (Postgres JSONB key-exists)
  inside a `jdbcTemplate.query(...)` — JDBC saw `?` as a parameter
  placeholder and threw "No value specified for parameter 2".
- **Fix:** Replaced with the function form `jsonb_exists(matcher_ast,
  'intent')`, which is semantically identical and JDBC-safe.
- **Files modified:** `SystemCalendarTemplateMigrationTest.java`.
- **Commit:** Folded into Task 2's commit `e377b331`.

**4. [Rule 2 — Missing Critical Functionality] Plan did not call out the
RuleTemplateMaterializationServiceTest assertion**

- **Found during:** Task 2 broader regression run.
- **Issue:** That test asserts every materialized rule's matcher_ast
  contains "SEMANTIC_INTENT". After 136, the calendar rule no longer
  contains that string — it contains "PRESET_CALENDAR".
- **Fix:** Loosened the assertion to `containsAnyOf("SEMANTIC_INTENT",
  "PRESET_CALENDAR")`. The original assertion's intent — "matchers are
  non-trivial" — still holds; the loosening correctly reflects the new
  reality.
- **Files modified:**
  `RuleTemplateMaterializationServiceTest.java`.
- **Commit:** Folded into Task 2's commit `e377b331`.

### Authentication Gates

None. W5 is a pure code/data/test delivery — no OAuth flow, no Gmail
grant exercised.

### Pre-existing Issues (Out of Scope, NOT Fixed)

**SafetyContractArchTests** flags
`CalendarApiClientFactory$CachedAccessToken.accessToken` as a
deny-listed field name that should be `Sensitive<String>`. This is a
W0/W1 issue, not a W5 regression. Logged here as a Phase 12 follow-up.

### Scope Boundaries Respected

- No change to `MailMessageObserved` event payload (still id-only).
- No change to `AllowListedTools` (LLM rule-builder must not be able
  to author preset matchers).
- No new frontend UI work — system-* rules are rendered via the
  existing path; the W4 UI badge still drives the visible calendar
  affordance.
- No CalendarAwareGuard, no audit reason, no backend downgrade —
  D-09 invariant preserved.
- 113 / 114 seeded changesets are byte-identical pre/post.

## Threat Surface

All W5 threats in `<threat_model>` are mitigated as planned:

| Threat ID | Mitigation Status                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| T-12-12   | `SystemCalendarTemplateMigrationTest.changeset_136_migrates_uncustomized_rule_rows_and_preserves_customized_rules` asserts the customized tenant's rule (matcher_ast.nodeId='custom-cal') is unchanged after the migration WHERE clause runs. The WHERE filters on `matcher_ast ->> 'nodeId' = 'system-calendar'` (and the VI variant), so a custom nodeId is structurally excluded.                                                                                                                                                                              |
| T-12-13   | `RuleEvaluatorCalendarPresetTest.presetCalendarMatcher_matchesWhenMessageClassIsPresent` proves the PRESET arm returns terminal MATCHED structurally — there is no DEFERRED path, so no LLM call can be issued by this matcher. The `RuleEvaluator.java` switch arm calls only the existing `terminal(...)` helper.                                                                                                                                                                                                                                              |
| T-12-14   | The PRESET matcher returns a `RuleEvaluationResult`; action execution still flows through the existing outbound gates (Auto-send setting, safety net, rate cap, idempotency, audit). The seeded rule's action is `label "Calendar"` — pure label, no destructive action. `RuleEvaluator` has no special-case for PRESET in action handoff — the result is consumed by the same `TriageOrchestratorService` machinery as every other matcher type.                                                                                                                |
| T-12-15   | Migration WHERE clauses do not filter by `tenant_id` because the migration is a global data fix; the per-rule `template_key` + `template_version` + `matcher_ast.type` + `matcher_ast.nodeId` quadruple is the durable identifier. Cross-tenant exposure is irrelevant for this rewrite — Liquibase runs as the application user, and the rewrite carries no per-tenant secret. The test fixture's two-tenant case implicitly proves the WHERE-clause selectivity. |

## Known Stubs

**None.** This wave is the final implementation slice of Phase 12 —
the seeded calendar rule now fires deterministically on the W4
classifier's output, no follow-up wiring required.

## Self-Check: PASSED

Files exist on disk:

- `backend/core/src/main/java/com/zeromail/core/rules/domain/MatcherType.java` — MODIFIED (+PRESET_CALENDAR)
- `backend/core/src/main/java/com/zeromail/core/rules/domain/MatcherNode.java` — MODIFIED (+permit + record)
- `backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluationInput.java` — MODIFIED (+messageClass)
- `backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluator.java` — MODIFIED (+switch arm)
- `backend/core/src/main/resources/db/changelog/changes/136-system-calendar-template-preset-matcher.yaml` — FOUND
- `backend/core/src/test/java/com/zeromail/core/rules/domain/RuleEvaluatorCalendarPresetTest.java` — FOUND
- `backend/core/src/test/java/com/zeromail/core/rules/usecases/SystemCalendarTemplateMigrationTest.java` — FOUND

Commits exist in `git log --oneline`:

- `b25dc3d5` — FOUND (Task 1: matcher + plumbing + unit test)
- `e377b331` — FOUND (Task 2: changeset 136 + master include + migration test)

All 8 new tests + adjacent regression tests pass. Modulith verification
green. Phase 12 implementation-complete.
