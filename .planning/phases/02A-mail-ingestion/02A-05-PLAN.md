---
phase: 02A-mail-ingestion
plan: "05"
type: execute
wave: 4
depends_on:
  - "02A-02"
  - "02A-03"
  - "02A-04"
files_modified:
  - .planning/phases/02A-mail-ingestion/02A-VALIDATION.md
  - .planning/ROADMAP.md
  - .planning/STATE.md
autonomous: true
requirements:
  - MAIL-01
  - MAIL-02
  - MAIL-03
  - MAIL-04
  - MAIL-05
  - MAIL-06

must_haves:
  truths:
    - "Full backend test suite passes (./gradlew clean check)"
    - "All 10 backend Wave 0 test classes are enabled and GREEN"
    - "All 4 frontend Wave 0 test files are enabled and GREEN"
    - "No Phase 2A Wave 0 Java test class contains class-level @Disabled and no Phase 2A frontend test contains it.skip"
    - "pnpm -F web run typecheck + lint + i18n:check all exit 0"
    - "ApplicationModulesTest passes (no new Modulith boundary violations)"
    - "DomainBoundaryArchTests passes (no cross-domain persistence access)"
    - "02A-VALIDATION.md flipped nyquist_compliant: true + wave_0_complete: true"
    - "STATE.md no longer contains the blocker bullet `- **Pub/Sub OIDC verification ceremony**`"
  artifacts:
    - path: ".planning/phases/02A-mail-ingestion/02A-VALIDATION.md"
      provides: "Flipped nyquist_compliant + wave_0_complete"
      contains: "nyquist_compliant: true"
    - path: ".planning/STATE.md"
      provides: "Phase 01.5 Pub/Sub push-token blocker removed"
  key_links:
    - from: "ApplicationModulesTest"
      to: "core.gmail module"
      via: "Spring Modulith verification"
      pattern: "ApplicationModulesTest"
    - from: "DomainBoundaryArchTests"
      to: "new entities in core.gmail.persistence"
      via: "ArchUnit cross-domain access rules"
      pattern: "DomainBoundaryArchTests"
---

<objective>
Run the full verification suite, flip VALIDATION.md flags, and close the Phase 01.5 D-D5 Pub/Sub OIDC ceremony blocker in STATE.md.

Purpose: Nyquist compliance verification — every acceptance criterion verified; phase declared complete.

Output: All tests GREEN, 02A-VALIDATION.md updated, STATE.md blocker removed.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/ROADMAP.md
@.planning/phases/02A-mail-ingestion/02A-VALIDATION.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Full verification sweep — all tests + ApplicationModulesTest + ArchUnit</name>
  <files>
    .planning/phases/02A-mail-ingestion/02A-VALIDATION.md
  </files>

  <read_first>
    - .planning/phases/02A-mail-ingestion/02A-VALIDATION.md (full file — update after tests pass)
    - backend/core/src/test/java/com/zeromail/core/ApplicationModulesTest.java (find it — run it)
    - backend/core/src/test/java/com/zeromail/core/architecture/DomainBoundaryArchTests.java (run it)
  </read_first>

  <action>
Run the full verification suite in sequence:

**Step 1 — Backend full suite:**
```bash
./gradlew clean check 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL. If failures, fix before proceeding.

**Step 2 — Targeted Wave 0 backend tests:**
```bash
./gradlew :backend:api:test --tests "*PubSubOidcAuthFilterTest*" \
  --tests "*GmailPubSubControllerIntegrationTest*" \
  --tests "*MeControllerTest*" \
  --tests "*TriagePauseControllerTest*" \
  --tests "*PubSubIdempotencyTest*" 2>&1 | grep -E "PASSED|FAILED|SKIPPED"

./gradlew :backend:core:test --tests "*PubSubDeliveryEntityTest*" \
  --tests "*MailMessageObservedEntityTest*" \
  --tests "*GmailIngestionHealthTest*" 2>&1 | grep -E "PASSED|FAILED"

./gradlew :backend:worker:test --tests "*GmailWatchSchedulerTest*" \
  --tests "*GmailHistoryProcessorTest*" 2>&1 | grep -E "PASSED|FAILED"
```

Then verify skipped API scaffolds are gone:
```bash
grep -R "@Disabled" backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java \
  backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java \
  backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java
```
Expected: no matches.

**Step 3 — Modulith + ArchUnit:**
```bash
./gradlew :backend:core:test --tests "*ApplicationModulesTest*" \
  --tests "*DomainBoundaryArchTests*" 2>&1 | grep -E "PASSED|FAILED|error:"
```

If ApplicationModulesTest fails because new packages (`core.gmail.persistence` has new entities, `core.gmail.service` has new services) trigger boundary violations:
- Check if `core.gmail` module's `package-info.java` needs updating for `allowedDependencies`
- The new entities/services are IN the `core.gmail` module — no boundary violation expected
- If `GmailHistoryProcessor` or `GmailWatchScheduler` in `backend/worker` import from `core.gmail.persistence` — this is correct (worker depends on core)
- If any new class imports from a different domain's `persistence` package — fix that violation

**Step 4 — Frontend full suite:**
```bash
pnpm -F web run test:run 2>&1 | tail -20
pnpm -F web run typecheck 2>&1 | tail -10
pnpm -F web run lint 2>&1 | tail -10
pnpm -F web run i18n:check 2>&1 | tail -10
grep -R "it.skip" apps/web/features/gmail/components/ReconnectPrompt.test.tsx
```
Expected for the `grep -R "it.skip"` command: no matches.

**Step 5 — Update 02A-VALIDATION.md:**
READ the file. Change frontmatter:
```yaml
nyquist_compliant: true
wave_0_complete: true
```

Update the Per-Task Verification Map table with actual task IDs and GREEN/RED status for each task.

Update the Wave 0 Requirements checklist — flip each `[ ]` to `[x]` for completed tests.

Add manual verification instructions summary at the bottom (the 4 manual-only items from VALIDATION.md §Manual-Only Verifications remain pending for staging environment).
  </action>

  <verify>
    <automated>./gradlew clean check 2>&1 | grep -E "BUILD|tests were" | tail -5</automated>
  </verify>

  <acceptance_criteria>
    - `./gradlew clean check` exits 0 (BUILD SUCCESSFUL)
    - All 10 backend Wave 0 test classes are GREEN (PubSubOidcAuthFilterTest x7, GmailPubSubControllerIntegrationTest x6, MeControllerTest x3, TriagePauseControllerTest x2, PubSubIdempotencyTest x2, PubSubDeliveryEntityTest x4, MailMessageObservedEntityTest x4, GmailIngestionHealthTest x4, GmailHistoryProcessorTest x6, GmailWatchSchedulerTest x6)
    - `grep -R "@Disabled" backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java` returns no matches
    - `ApplicationModulesTest` passes GREEN
    - `DomainBoundaryArchTests` passes GREEN
    - `pnpm -F web run test:run` — all Wave 0 tests GREEN (PauseBanner.test.tsx, useToggleTriagePause.test.tsx, ReconnectPrompt.test.tsx, phase-02a-files.test.ts)
    - `grep -R "it.skip" apps/web/features/gmail/components/ReconnectPrompt.test.tsx` returns no matches
    - `pnpm -F web run typecheck` exits 0
    - `pnpm -F web run lint` exits 0
    - `pnpm -F web run i18n:check` exits 0
    - `02A-VALIDATION.md` frontmatter contains `nyquist_compliant: true` and `wave_0_complete: true`
    - All 12 Wave 0 checkboxes in 02A-VALIDATION.md are `[x]`
  </acceptance_criteria>

  <done>All tests GREEN; ApplicationModulesTest + DomainBoundaryArchTests pass; VALIDATION.md updated with nyquist_compliant: true</done>
</task>

<task type="auto">
  <name>Task 2: Close STATE.md Pub/Sub OIDC blocker + update ROADMAP.md Phase 2A plans count</name>
  <files>
    .planning/STATE.md
  </files>

  <read_first>
    - .planning/STATE.md (full file — READ BEFORE editing)
    - .planning/ROADMAP.md (Phase 2A section — update plans count)
  </read_first>

  <action>
**`STATE.md`** — READ the full file. Find the Blockers/Concerns section. Locate the blocker bullet:
```
- **Pub/Sub OIDC verification ceremony** (Phase 2A push-receiver) ...
```

Remove this entire bullet point. This blocker is now closed by Phase 2A Plan 03 (`PubSubOidcAuthFilter` + verification protocol covering aud, email, signature, expiry).

In the Accumulated Context → Decisions section, ADD a new entry documenting the Phase 2A closure:
```
- [Phase 2A]: Pub/Sub push-token validation closed. PubSubOidcAuthFilter uses TokenVerifier.newBuilder().setAudience().setIssuer().setCertificatesLocation() from google-auth-library-oauth2-http. PubSubSecurityConfig contributes a SecurityFilterChain bean with @Order(1) and remains active under the test profile; user-session SecurityConfig's SecurityFilterChain bean is @Order(2). 7-case test validates: valid passes, wrong aud/email/issuer/exp/sig all return 401, and non-Pub/Sub paths skip the filter. Phase 01.5 D-D5 deferred blocker retired.
```

Also update Current Position to reflect Phase 2A complete.

**`ROADMAP.md`** — READ the Phase 2A section. Update:
```
**Plans**: 6 plans
```
And update the Plans list:
```
Plans:
- [x] 02A-00-PLAN.md — Wave 0 RED test scaffolds (10 backend test classes + 2 fixtures + 4 frontend test files)
- [x] 02A-01-PLAN.md — Schema (Liquibase 010-013) + entities + enum
- [x] 02A-02-PLAN.md — Worker schedulers (GmailWatchScheduler + GmailHistoryProcessor)
- [x] 02A-03-PLAN.md — API layer (PubSubOidcAuthFilter + push receiver + triage-pause controller)
- [x] 02A-04-PLAN.md — Frontend (PauseBanner + settings toggle + ReconnectPrompt gate + i18n)
- [x] 02A-05-PLAN.md — Full verification sweep + closure
```
  </action>

  <verify>
    <automated>grep -c -- "- \\*\\*Pub/Sub OIDC verification ceremony\\*\\*" .planning/STATE.md</automated>
  </verify>

  <acceptance_criteria>
    - `grep -c -- "- \\*\\*Pub/Sub OIDC verification ceremony\\*\\*" .planning/STATE.md` returns 0 (blocker bullet removed)
    - STATE.md Accumulated Context → Decisions section contains "Phase 2A" entry about Pub/Sub push-token validation closure
    - ROADMAP.md Phase 2A section shows `**Plans**: 6 plans`
    - ROADMAP.md Phase 2A plans list shows all 6 plans with `[x]` checkboxes
  </acceptance_criteria>

  <done>STATE.md Pub/Sub OIDC blocker removed; Phase 2A marked complete in ROADMAP.md; decisions log updated</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Verification sweep | No new trust boundaries introduced — this plan is verification only |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01 | Spoofing | Final gate: PubSubOidcAuthFilterTest all 7 cases GREEN | mitigate | Acceptance criterion requires all 7 test cases pass: valid + wrong aud + wrong email + wrong issuer + expired + bad signature + non-Pub/Sub path guard — all OIDC failures must return 401, and non-Pub/Sub paths must skip the filter |
| T-05 | Information Disclosure | DomainBoundaryArchTests covers new entities | mitigate | ArchUnit test must pass GREEN confirming no cross-domain persistence access in new code |
</threat_model>

<verification>
After this plan:
- `./gradlew clean check` BUILD SUCCESSFUL (entire backend suite)
- `pnpm -F web run test:run && pnpm -F web run typecheck && pnpm -F web run lint && pnpm -F web run i18n:check` all exit 0
- `grep -c -- "- \\*\\*Pub/Sub OIDC verification ceremony\\*\\*" .planning/STATE.md` = 0
- `grep "nyquist_compliant" .planning/phases/02A-mail-ingestion/02A-VALIDATION.md` shows `true`
</verification>

<success_criteria>
Full test suite GREEN (backend + frontend). All 10 backend Wave 0 test classes and all 4 frontend Wave 0 test files are enabled and GREEN. ApplicationModulesTest + DomainBoundaryArchTests pass. VALIDATION.md nyquist_compliant: true. STATE.md Pub/Sub OIDC blocker bullet removed. Phase 2A declared complete.
</success_criteria>

<output>
After completion, create `.planning/phases/02A-mail-ingestion/02A-05-SUMMARY.md`
</output>
