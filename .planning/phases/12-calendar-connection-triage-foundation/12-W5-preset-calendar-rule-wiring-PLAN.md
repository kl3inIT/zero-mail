---
phase: 12-calendar-connection-triage-foundation
plan: 06
type: execute
wave: 5
depends_on:
  - 12-05
files_modified:
  - backend/core/src/main/java/com/zeromail/core/rules/domain/MatcherNode.java
  - backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluator.java
  - backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluationInput.java
  - backend/core/src/main/resources/db/changelog/changes/135-system-calendar-template-preset-matcher.yaml
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
  - backend/core/src/test/java/com/zeromail/core/rules/domain/RuleEvaluatorCalendarPresetTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/usecases/SystemCalendarTemplateMigrationTest.java
autonomous: true
requirements:
  - CAL-TRIAGE-03
must_haves:
  truths:
    - "RuleEvaluator pushes a PRESET match for the seeded system-calendar rule whenever messageClass is non-null on the inbox message, BEFORE any SEMANTIC_INTENT/AI matcher runs"
    - "The existing 113-default-rule-templates-seed.yaml system-calendar matcher_ast is migrated by changeset 135 from SEMANTIC_INTENT to the new PRESET_CALENDAR shape — both for the rule_template_catalog row AND for already-materialized rule rows where (template_key='system-calendar' AND matcher_ast.type='SEMANTIC_INTENT')"
    - "User-customized rules (those whose matcher_ast no longer equals the seeded value) are NOT migrated — Custom rules retain their original matcher shape"
    - "User-authored rules continue evaluating normally even when an inbox message is a calendar invite — no backend downgrade, no audit reason"
    - "After this plan, a new tenant first-login gets a seeded Calendar rule that labels invites via PRESET match (no AI call)"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/rules/domain/MatcherNode.java"
      provides: "New permits entry: PresetCalendarMatcher record with nodeId only (no other fields)"
      contains: "PresetCalendarMatcher"
    - path: "backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluator.java"
      provides: "New case branch in evaluate(...) switch — when matcherNode is PresetCalendarMatcher and message.messageClass() is non-null, return terminal MATCHED with a 'preset_calendar' diagnostic"
    - path: "backend/core/src/main/resources/db/changelog/changes/135-system-calendar-template-preset-matcher.yaml"
      provides: "Liquibase data changeset rewriting matcher_ast JSONB for the system-calendar template + uncustomized materialized rules"
  key_links:
    - from: "RuleEvaluator.evaluate (PresetCalendarMatcher case)"
      to: "RuleEvaluationInput.messageClass()"
      via: "Read-side input enriched in W4 with the new message_class column from the inbox projection"
      pattern: "messageClass"
    - from: "Changeset 135"
      to: "system-calendar template_key in rule_template_catalog AND rule.matcher_ast"
      via: "Idempotent UPDATE on (template_key='system-calendar' AND template_version=1) for both tables; SEMANTIC_INTENT → PRESET_CALENDAR rewrite"
      pattern: "PRESET_CALENDAR"
---

<objective>
Land CAL-TRIAGE-03 per D-09's Inbox-Zero pattern WITHOUT the previously-considered backend `CalendarAwareGuard`:

1. **New `MatcherNode` permit `PresetCalendarMatcher`** — a sealed-interface record with a single `nodeId` field (per the existing `MatcherNode` shape verified at line 8-28 of `MatcherNode.java`). Represents the calendar PRESET match — terminal MATCHED whenever the inbox message has a non-null `messageClass` (from W4's projection columns). No AI call, no SEMANTIC_INTENT involvement.

2. **`RuleEvaluator.evaluate(...)` switch branch** — add the new `case MatcherNode.PresetCalendarMatcher presetCalendarMatcher → ...` arm that returns MATCHED when `ruleEvaluationInput.messageClass()` is non-null; returns NOT_MATCHED when null. NO Deferred (AI call) path for PRESET — it is structurally deterministic.

3. **`RuleEvaluationInput` carries the new `messageClass` field** — passed through from the read-side projection so the evaluator can see the new column from W4. This is the data-flow seam that lets the rule engine know "this message is a calendar invite" without any new repository call.

4. **Liquibase changeset 135 migrates existing tenants** per `<open_questions_from_research>` Q4 = MIGRATE. The seeded `system-calendar` row in `113-default-rule-templates-seed.yaml` was set with `matcher_ast = {type: SEMANTIC_INTENT, nodeId: "system-calendar", intent: "Calendar: ..."}`. Changeset 135 rewrites that JSONB to `{type: PRESET_CALENDAR, nodeId: "system-calendar"}` for BOTH `rule_template_catalog` AND each already-materialized `rule` row that still carries the seeded matcher (i.e. `rule.template_key='system-calendar' AND rule.template_version=1 AND rule.matcher_ast = <original seeded JSON>` — idempotent UPDATE keyed by exact JSON match so a user who customized the matcher is NOT migrated). New tenants get the PRESET shape directly via `113-default-rule-templates-seed.yaml`'s post-135 state.

CRITICAL: Per Liquibase discipline (CLAUDE.md §10), the existing `113-default-rule-templates-seed.yaml` MUST NOT be edited — it is already applied to production tenants and editing it would mutate the historical changeset checksum. Instead, changeset 135 is a roll-forward data migration. Future fresh-database deployments apply `113-...` then immediately `135-...` so the end state is identical to a tenant migrated from prior state.

5. **No new audit reason, no CalendarAwareGuard, no badge UI change** — per D-09 explicitly. The W4 inbox UI already shows the Cancellation / Time changed badges for CANCEL/RESCHEDULE classes (purely message-side, decoupled from rule evaluation). User rules retain full action authority.

Purpose: Complete CAL-TRIAGE-03 — new tenants get the seeded Calendar label rule that fires via PRESET (no LLM cost) whenever a message classified by W4 lands.
Output: 1 sealed-interface permit + 1 evaluator branch + 1 evaluation-input field + 1 changeset + 1 master-include edit + 2 tests.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md
@.planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md
@.planning/phases/12-calendar-connection-triage-foundation/12-PATTERNS.md
@.planning/phases/12-calendar-connection-triage-foundation/12-05-SUMMARY.md
</context>

<artifacts_this_phase_produces>
- `MatcherNode.PresetCalendarMatcher(String nodeId)` record — new sealed-interface permit.
- `RuleEvaluator.evaluate(...)` new switch case.
- `RuleEvaluationInput.messageClass()` accessor (new field).
- `135-system-calendar-template-preset-matcher.yaml` data migration.
- Master changelog include.
- `RuleEvaluatorCalendarPresetTest` — PRESET MATCHES when messageClass is non-null; PRESET NOT_MATCHED when null; user-authored SEMANTIC_INTENT rules unaffected.
- `SystemCalendarTemplateMigrationTest` — applies 135 to a seeded DB and asserts both template + materialized-row JSONB rewrites with idempotency (re-running the changeset is a no-op) AND custom-rule preservation.

NOT in this plan:
- Frontend rule-builder UI changes for PRESET_CALENDAR — the seeded rule is system-managed and the UI already renders system-* rules via the existing pattern (W3 UI does not touch rule-builder).
- Reading `messageClass` into the read-side pipeline — for Phase 12 the projection-read endpoint already exposes the field per W4 Task 1; the rule engine queries it from `RuleEvaluationInput` which is built per-message at the rule-evaluation orchestration layer (verify the existing builder feeds `messageClass` into the input; if not, add the field plumbing in Task 1).
- RESCHEDULE handling at the rule level — W4 ships INVITE-only for METHOD:REQUEST; the PRESET match still fires for INVITE/CANCEL/RSVP whenever `messageClass != null`.
</artifacts_this_phase_produces>

<tasks>

<task type="auto">
  <name>Task 1: PresetCalendarMatcher MatcherNode permit + RuleEvaluationInput.messageClass + RuleEvaluator branch + unit test</name>
  <files>backend/core/src/main/java/com/zeromail/core/rules/domain/MatcherNode.java, backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluator.java, backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluationInput.java, backend/core/src/test/java/com/zeromail/core/rules/domain/RuleEvaluatorCalendarPresetTest.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/rules/domain/MatcherNode.java (full file — the existing sealed-interface permits list, including `SemanticIntentMatcher` at line 28; PresetCalendarMatcher is added to this permits list)
    - backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluator.java (full file — the switch statement in `evaluate(...)`; new case branch is added alongside the existing `case MatcherNode.SemanticIntentMatcher ...` arm and the `case MatcherNode.SenderEmailMatcher ...` etc. arms at lines 33-100)
    - backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluationInput.java (the existing record/class — verify shape; if record, add a new `Optional<MessageClass> messageClass` accessor at the right position; if class, add a getter+field)
    - backend/core/src/main/java/com/zeromail/core/inbox/domain/MessageClass.java (W4 — the enum the input carries)
    - .planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md (D-09 — PRESET match-before-AI, no downgrade, no audit reason, no badge)
    - .planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md (§Pattern 5 lines 619-648 — RuleEvaluator early-return sketch; §"insertion point investigation needed" notes the planner should verify by reading RuleEvaluator)
    - CONVENTIONS.md §4 (IdentifiedEnum convention applicable to the messageClass field marshaling at the input layer)
    - Find the existing `RuleEvaluationInput` builder/factory — typically a `usecases` orchestration class that translates the inbox projection row into the input record. Verify the field is plumbed end-to-end (projection → builder → input → evaluator).
  </read_first>
  <action>
    Read `MatcherNode.java` first to confirm the sealed-interface permits list shape (it has `permits MatcherNode.SenderEmailMatcher, ..., SemanticIntentMatcher`). Add `PresetCalendarMatcher` to the permits list. Define the inner record:
    ```java
    record PresetCalendarMatcher(String nodeId) implements MatcherNode {}
    ```
    Per CONVENTIONS §3 + existing record style. `nodeId` is the matcher's identifier carried in the persisted `matcher_ast.nodeId` JSON field — for the seeded calendar rule this stays `"system-calendar"` matching the seed key.

    Edit `RuleEvaluationInput.java`. Verify the existing shape — if a record, add a new field `Optional<MessageClass> messageClass` (or `MessageClass messageClass` nullable — pick the shape that matches the existing fields' null-handling convention). If a class, add a field + getter. Ensure the constructor / static factory accepts the new field.

    Locate the `RuleEvaluationInput` builder/factory that translates a `GmailInboxProjectionEntity` into a `RuleEvaluationInput`. The W4 `GmailInboxProjectionEntity` exposes `getMessageClassOptional()` — wire that into the builder.

    Edit `RuleEvaluator.evaluate(...)` switch. Add a new arm BEFORE the catch-all default per RESEARCH.md §Pattern 5 lines 631-648:
    ```java
    case MatcherNode.PresetCalendarMatcher presetCalendarMatcher ->
            terminal(
                    presetCalendarMatcher.nodeId(),
                    ruleEvaluationInput.messageClass().isPresent(),
                    "preset_calendar");
    ```
    Use the existing `terminal(nodeId, matched, diagnostic)` helper at lines 67-71 (the same helper used for `GmailLabelPresentMatcher` and similar terminal matchers). This produces a deterministic MATCHED or NOT_MATCHED result with a diagnostic string "preset_calendar" — no Deferred state, no AI involvement, no audit reason per D-09.

    Per CONVENTIONS §4 + project memory `feedback_modulith_listener_scope.md`: this evaluator already lives in `core.rules.domain` and has no Spring annotations (verified at line 13 — `public class RuleEvaluator` with no `@Service`). Keep it framework-free.

    Privacy: the evaluator does not log; the `diagnostic` string is "preset_calendar" — no tenant/message data leaks via the result.

    Create `RuleEvaluatorCalendarPresetTest.java` per VALIDATION.md TBD-w5-01:
    - Plain JUnit 5 (Layer 1 unit test).
    - Helper to build a `RuleEvaluationInput` with controllable `messageClass`.
    - Helper to build a `MatcherNode.PresetCalendarMatcher("system-calendar")` and the existing `MatcherNode.SemanticIntentMatcher(...)` for user-rule scenarios.
    - Cases:
      - `presetCalendarMatcher_matchesWhenMessageClassIsPresent` — input has `messageClass=Optional.of(INVITE)`; assert evaluator returns terminal MATCHED with diagnostic `"preset_calendar"`.
      - `presetCalendarMatcher_doesNotMatchWhenMessageClassIsAbsent` — input has `messageClass=Optional.empty()`; assert NOT_MATCHED.
      - `presetCalendarMatcher_matchesForEveryMessageClassEnumValue` — parameterized test across INVITE / CANCEL / RESCHEDULE / RSVP; all four match.
      - `semanticIntentMatcher_unchangedByPresetSibling` — feed a SEMANTIC_INTENT matcher (existing); assert it returns DEFERRED as before (no override-by-PRESET — user rules retain full authority per D-09).
      - `presetCalendarMatcher_doesNotEmitAuditReason` — assert `EvaluationResult.diagnostic()` is `"preset_calendar"` and that there is NO `auditReason` field carrying a Calendar-aware-guard-style override (the field does NOT exist; this test guards against a future regression).
    - Per TESTING.md §1, this test exists because it pins the user-trust contract (PRESET match before AI; user rules retain authority).
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.rules.domain.RuleEvaluatorCalendarPresetTest"</automated>
  </verify>
  <acceptance_criteria>
    - `MatcherNode.java` `permits` line carries `PresetCalendarMatcher`; the record is defined inside `MatcherNode` per the existing nested-record pattern.
    - `grep -c 'PresetCalendarMatcher' backend/core/src/main/java/com/zeromail/core/rules/domain/MatcherNode.java` returns at least 2 (one in permits list, one in record definition).
    - `RuleEvaluator.java` carries a new `case MatcherNode.PresetCalendarMatcher` arm; `grep -c 'PresetCalendarMatcher' backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluator.java` returns at least 1.
    - `grep -c 'CalendarAwareGuard\|auditReason' backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluator.java | grep -v '^#'` returns 0 — D-09 invariant (no backend downgrade).
    - `RuleEvaluationInput.java` carries the new messageClass accessor.
    - All 5 cases in `RuleEvaluatorCalendarPresetTest` green.
    - `cd backend && ./gradlew :backend:core:check` green (DomainPurityArchTest must NOT fail — the new `PresetCalendarMatcher` has no framework dependency).
    - JetBrains `get_file_problems` returns no errors on the modified files.
  </acceptance_criteria>
  <done>The evaluator structurally matches calendar messages against the PRESET shape before any AI matcher runs; user-authored rules retain full action authority.</done>
</task>

<task type="auto">
  <name>Task 2: Changeset 135 + master include + SystemCalendarTemplateMigrationTest</name>
  <files>backend/core/src/main/resources/db/changelog/changes/135-system-calendar-template-preset-matcher.yaml, backend/core/src/main/resources/db/changelog/db.changelog-master.yaml, backend/core/src/test/java/com/zeromail/core/rules/usecases/SystemCalendarTemplateMigrationTest.java</files>
  <read_first>
    - backend/core/src/main/resources/db/changelog/changes/113-default-rule-templates-seed.yaml (lines 95-107 — the system-calendar template row's matcher_ast JSONB literal to rewrite from; the exact seeded JSON is `{"schemaVersion":"rules.v1","type":"SEMANTIC_INTENT","nodeId":"system-calendar","intent":"Calendar: ...","deferred":true}`)
    - backend/core/src/main/resources/db/changelog/changes/114-default-rule-templates-vi-seed.yaml (verify whether the Vietnamese seed `system-calendar-vi` matcher_ast also uses SEMANTIC_INTENT; if so, the migration must rewrite BOTH `system-calendar` AND `system-calendar-vi` for matched template_keys)
    - backend/core/src/main/resources/db/changelog/changes/023-fix-pin-calendar-category.yaml (precedent for a fix-via-new-changeset on a rule template — the data migration shape this plan mirrors; verified per RESEARCH.md "023 precedent" reference)
    - backend/core/src/main/resources/db/changelog/changes/124-processing-job-mailbox.yaml (representative recent data-migration changeset for raw-SQL UPDATE pattern with explicit rollback)
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml (the master include — appends 135 after 134-inbox-projection-calendar-columns.yaml from W0)
    - CLAUDE.md §10 Liquibase changelog discipline — append-only, explicit rollback, preConditions for destructive/data-dependent changes, do NOT edit applied changesets (113 is sacred)
    - .planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md (§Common Pitfalls + §"Runtime State Inventory" lines 677-681 — recommendation MIGRATE for uncustomized rule rows)
    - .planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md (D-09 IZ pattern; the seeded rule becomes PRESET_CALENDAR with the same nodeId)
  </read_first>
  <action>
    Verify `114-default-rule-templates-vi-seed.yaml` — confirm whether `system-calendar-vi` also uses `SEMANTIC_INTENT`. If yes, changeset 135 migrates both `system-calendar` AND `system-calendar-vi` template_keys.

    Create `backend/core/src/main/resources/db/changelog/changes/135-system-calendar-template-preset-matcher.yaml`:
    ```yaml
    databaseChangeLog:
      - changeSet:
          id: 135-system-calendar-template-preset-matcher
          author: zeromail
          comment: >-
            Migrate the seeded system-calendar(-vi) rule template's matcher_ast from
            SEMANTIC_INTENT (LLM-driven) to PRESET_CALENDAR (deterministic, message-side)
            so the calendar label rule fires WITHOUT an LLM call whenever the W4
            ical4j classifier sets gmail_inbox_projection.message_class. Also rewrites
            uncustomized materialized rule rows for existing tenants so they pick up
            the new behavior immediately (CONTEXT D-09; open_questions_from_research Q4).
            Customized rules — those whose matcher_ast no longer equals the seeded
            JSON for (template_key, template_version) — are PRESERVED.
          preConditions:
            - onFail: HALT
            - sqlCheck:
                expectedResult: 1
                sql: |
                  SELECT count(*) FROM rule_template_catalog
                  WHERE template_key = 'system-calendar'
                    AND template_version = 1
                    AND matcher_ast ->> 'type' = 'SEMANTIC_INTENT';
          changes:
            - sql:
                splitStatements: false
                sql: |
                  -- 1. Migrate the template catalog rows (system-calendar + system-calendar-vi if present).
                  UPDATE rule_template_catalog
                  SET matcher_ast = jsonb_build_object(
                        'schemaVersion', 'rules.v1',
                        'type', 'PRESET_CALENDAR',
                        'nodeId', 'system-calendar')
                  WHERE template_key = 'system-calendar'
                    AND template_version = 1
                    AND matcher_ast ->> 'type' = 'SEMANTIC_INTENT';

                  UPDATE rule_template_catalog
                  SET matcher_ast = jsonb_build_object(
                        'schemaVersion', 'rules.v1',
                        'type', 'PRESET_CALENDAR',
                        'nodeId', 'system-calendar-vi')
                  WHERE template_key = 'system-calendar-vi'
                    AND template_version = 1
                    AND matcher_ast ->> 'type' = 'SEMANTIC_INTENT';

                  -- 2. Migrate uncustomized materialized rules.
                  -- A rule row is "uncustomized" when its matcher_ast still equals the original seeded
                  -- SEMANTIC_INTENT shape (we match on matcher_ast ->> 'type' AND matcher_ast ->> 'nodeId').
                  -- We do NOT match on the full intent string because the seeded intent text might
                  -- have been edited cosmetically; the type+nodeId pair is the durable identifier.
                  UPDATE rule
                  SET matcher_ast = jsonb_build_object(
                        'schemaVersion', 'rules.v1',
                        'type', 'PRESET_CALENDAR',
                        'nodeId', 'system-calendar')
                  WHERE template_key = 'system-calendar'
                    AND template_version = 1
                    AND matcher_ast ->> 'type' = 'SEMANTIC_INTENT'
                    AND matcher_ast ->> 'nodeId' = 'system-calendar';

                  UPDATE rule
                  SET matcher_ast = jsonb_build_object(
                        'schemaVersion', 'rules.v1',
                        'type', 'PRESET_CALENDAR',
                        'nodeId', 'system-calendar-vi')
                  WHERE template_key = 'system-calendar-vi'
                    AND template_version = 1
                    AND matcher_ast ->> 'type' = 'SEMANTIC_INTENT'
                    AND matcher_ast ->> 'nodeId' = 'system-calendar-vi';
          rollback:
            - sql:
                splitStatements: false
                sql: |
                  -- Reverse migration is best-effort: we cannot restore the original intent text
                  -- because we did not store it in this changeset. Roll back only the type marker;
                  -- the AI matcher will work but the intent string carried in matcher_ast.intent
                  -- will be missing. Operators should re-seed via 113-default-rule-templates-seed
                  -- if they need the full original SEMANTIC_INTENT behavior back.
                  UPDATE rule_template_catalog
                  SET matcher_ast = jsonb_set(matcher_ast, '{type}', '"SEMANTIC_INTENT"'::jsonb)
                  WHERE template_key IN ('system-calendar','system-calendar-vi')
                    AND template_version = 1
                    AND matcher_ast ->> 'type' = 'PRESET_CALENDAR';

                  UPDATE rule
                  SET matcher_ast = jsonb_set(matcher_ast, '{type}', '"SEMANTIC_INTENT"'::jsonb)
                  WHERE template_key IN ('system-calendar','system-calendar-vi')
                    AND template_version = 1
                    AND matcher_ast ->> 'type' = 'PRESET_CALENDAR';
    ```

    Verify: the preCondition checks the EN template first; if that's already migrated (re-run after a partial apply), `expectedResult: 1` would fail because the row would carry `PRESET_CALENDAR` already. Tighten the preCondition by checking both EN + VI rows summed (e.g. `>= 1` via `sqlCheck` returning the count). Actually Liquibase `sqlCheck` matches a single integer expected — change shape to a `runOnce` semantic OR use `onFail: MARK_RAN` so a re-applied changeset becomes a no-op silently. Per CLAUDE.md §10 — `runOnChange` and `runAlways` are forbidden; the right shape here is the preCondition fails with `onFail: CONTINUE` and the UPDATEs become no-ops because the WHERE clauses won't match. Use `onFail: CONTINUE` + log a comment that "if the precondition fails, no rows match, no harm done" — this matches the idempotency-by-WHERE pattern (the UPDATEs are themselves idempotent because the WHERE filters on `'SEMANTIC_INTENT'`).

    Final precondition shape: drop the explicit `sqlCheck` and rely on idempotency-by-WHERE — the changeset can be safely re-applied because the SQL only UPDATEs rows still carrying `SEMANTIC_INTENT`. Add a `comment:` explaining the idempotency. Edit the changeset accordingly.

    Edit `db.changelog-master.yaml`: append a single `include:` for `changes/135-system-calendar-template-preset-matcher.yaml` after the W0 include for `134-inbox-projection-calendar-columns.yaml`.

    Create `SystemCalendarTemplateMigrationTest.java` extending `PostgresContainerTest`:
    - Boot the test container with the changelog applied up to changeset 113 (seeded `system-calendar` SEMANTIC_INTENT) + 114 (seeded `system-calendar-vi` SEMANTIC_INTENT) + the rest of the changelog through 134 (W0).
    - Insert two `rule` rows simulating already-materialized tenants:
      1. Tenant A: `(template_key='system-calendar', template_version=1, matcher_ast = <original seeded JSON>)` — uncustomized.
      2. Tenant B: `(template_key='system-calendar', template_version=1, matcher_ast = jsonb {'schemaVersion':'rules.v1', 'type': 'SEMANTIC_INTENT', 'nodeId': 'custom-cal', 'intent': 'My custom intent', 'deferred': true})` — customized (nodeId differs from the seed).
    - Apply changeset 135.
    - Assertions:
      - Tenant A's `matcher_ast` now equals `{"schemaVersion":"rules.v1","type":"PRESET_CALENDAR","nodeId":"system-calendar"}`.
      - Tenant B's `matcher_ast` is UNCHANGED — still SEMANTIC_INTENT with `nodeId='custom-cal'`.
      - `rule_template_catalog` row for `system-calendar` (and `system-calendar-vi`) now has type PRESET_CALENDAR.
    - Re-apply the changeset (simulate Liquibase's no-op rerun) — assert no rows changed (idempotency-by-WHERE).
    - Apply the `rollback` and assert Tenant A's `matcher_ast.type` is back to `SEMANTIC_INTENT` (intent string remains the simplified form documented in the rollback comment; this is "best effort" per the changeset comment).
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.rules.usecases.SystemCalendarTemplateMigrationTest"</automated>
  </verify>
  <acceptance_criteria>
    - `135-system-calendar-template-preset-matcher.yaml` exists; `grep -c 'PRESET_CALENDAR' backend/core/src/main/resources/db/changelog/changes/135-system-calendar-template-preset-matcher.yaml` returns at least 2.
    - `grep -c 'rollback:' backend/core/src/main/resources/db/changelog/changes/135-system-calendar-template-preset-matcher.yaml` returns at least 1 (explicit rollback per CLAUDE.md §10).
    - `grep -c 'runOnChange\|runAlways\|clear-checksums' backend/core/src/main/resources/db/changelog/changes/135-system-calendar-template-preset-matcher.yaml | grep -v '^#'` returns 0 (forbidden per CLAUDE.md §10).
    - `db.changelog-master.yaml` includes `135-system-calendar-template-preset-matcher.yaml` AFTER `134-inbox-projection-calendar-columns.yaml`.
    - `113-default-rule-templates-seed.yaml` and `114-default-rule-templates-vi-seed.yaml` are byte-identical pre- and post-task (`git diff` returns empty).
    - All assertions in `SystemCalendarTemplateMigrationTest` green: tenant A migrated, tenant B preserved, template_catalog rewritten, idempotent re-apply, rollback works.
    - `./gradlew :backend:api:check :backend:core:check` green.
  </acceptance_criteria>
  <done>Pre-Phase-12 tenants with the seeded Calendar rule are silently migrated to the PRESET behavior on the next Liquibase apply. Customized rules are preserved. New tenants get the PRESET shape directly from the (113 + 135) post-migration state.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| matcher_ast JSONB (rule + rule_template_catalog) | Stored shape consumed by RuleEvaluator; SEMANTIC_INTENT carries a free-text intent that goes to the LLM. |
| RuleEvaluator switch over MatcherNode sealed interface | Structural pattern match; new permit forces exhaustive handling. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-12-12 | Tampering | Customized rule row inadvertently migrated to PRESET (silently breaks user expectation) | mitigate | Changeset 135 WHERE clauses guard on `matcher_ast ->> 'nodeId' = 'system-calendar'` AND `matcher_ast ->> 'type' = 'SEMANTIC_INTENT'` — the original seeded shape only. A user who customized the nodeId (e.g. to `'custom-cal'`) is preserved. Test `SystemCalendarTemplateMigrationTest` asserts tenant B (customized) is unchanged. |
| T-12-13 | Denial of Service | LLM cost regression — preset rule still calls SEMANTIC_INTENT after migration (e.g. cache poison) | mitigate | After 135 applies, `matcher_ast.type='PRESET_CALENDAR'` and the new RuleEvaluator switch arm short-circuits to terminal MATCHED. No Deferred state, no LLM call. Test `RuleEvaluatorCalendarPresetTest.presetCalendarMatcher_matchesWhenMessageClassIsPresent` proves the structural shortcut. |
| T-12-14 | Elevation of Privilege | New `PresetCalendarMatcher` permit bypasses some existing rule-action gate | accept | The PRESET matcher only RESULTS IN a match — action execution still flows through the existing outbound gates (Auto-send setting, safety net, rate cap, idempotency, audit). The seeded rule's action is `label "Calendar"` — pure label, no destructive action. CLAUDE.md `Write actions allowed` rules still apply. |
| T-12-15 | Information Disclosure | matcher_ast UPDATE leaks across tenants | mitigate | The WHERE clauses on the rule UPDATE do NOT filter by `tenant_id` because the migration is intentionally tenant-scoped via the WHERE on `(template_key, template_version, matcher_ast)`. Cross-tenant exposure is irrelevant here (UPDATEs are issued by Liquibase as the application user; no per-tenant secret leaks via the rewrite). |
</threat_model>

<verification>
- `cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.rules.domain.RuleEvaluatorCalendarPresetTest" --tests "com.zeromail.core.rules.usecases.SystemCalendarTemplateMigrationTest"` — both green.
- `cd backend && ./gradlew :backend:core:check` — full check green (DomainPurityArchTest, ApplicationModulesTest).
- `cd backend && ./gradlew :backend:core:test --tests "*RuleEvaluator*"` — full RuleEvaluator test suite green (no regression on existing matcher types).
- Manual: spin up the test container with all migrations applied; INSERT a `rule` row with PRESET_CALENDAR matcher_ast; INSERT an inbox projection row with `message_class='INVITE'`; trigger the existing rule-evaluation pipeline; observe the rule MATCHED via the PRESET path with no LLM call (verify via the existing LLM-call-audit table — zero rows added for this evaluation).
- Confirm CAL-TRIAGE-03 wording in REQUIREMENTS.md (line 54) matches the shipped behavior: "Calendar rule runs as a PRESET match before AI matching; user-authored rules retain full action authority — no backend downgrade, no CalendarAwareGuard."
</verification>

<success_criteria>
- `MatcherNode` sealed interface gains the `PresetCalendarMatcher` permit + record.
- `RuleEvaluator.evaluate(...)` switch carries the new PRESET branch; returns terminal MATCHED when `RuleEvaluationInput.messageClass()` is present.
- `RuleEvaluationInput.messageClass()` accessor is wired through the inbox-projection → input builder so the evaluator sees W4's `message_class` column.
- Liquibase 135 migrates the `system-calendar` (+ `system-calendar-vi`) template and uncustomized materialized rules; customized rules preserved.
- Idempotent: 135 re-apply is a no-op; rollback is explicit (best-effort restore of type marker).
- New tenants seeded after 113 + 135 apply land on PRESET shape directly.
- Existing user rules (SEMANTIC_INTENT, sender_email, subject_regex, etc.) unaffected by the new permit — all existing RuleEvaluator tests green.
- No backend downgrade, no CalendarAwareGuard, no audit reason added per D-09.
- Full Phase 12 closure: INFRA-01 + CAL-CONN-01..08 + CAL-TRIAGE-01..04 implemented and tested.
</success_criteria>

<output>
Create `.planning/phases/12-calendar-connection-triage-foundation/12-06-SUMMARY.md` listing: (a) confirmation that `113-default-rule-templates-seed.yaml` and `114-default-rule-templates-vi-seed.yaml` are byte-identical pre/post (`git diff` empty), (b) the exact JSON shape PRESET_CALENDAR uses (`{"schemaVersion":"rules.v1","type":"PRESET_CALENDAR","nodeId":"system-calendar"}`), (c) the count of uncustomized vs customized rule rows touched by 135 in the test fixture (1 vs 1), (d) confirmation that no LLM call audit row is written when the PRESET rule matches (verify in the test by mocking the LLM gateway and asserting `verify(llmGateway, never()).call(...)`).

PHASE 12 CLOSURE: After this plan ships, the phase is implementation-complete. Recommend `/gsd-verify-work` next to run UAT against the 5 ROADMAP Success Criteria.
</output>
