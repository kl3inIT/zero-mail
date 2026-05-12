---
phase: 05B-user-surface-ai-draft-replies
plan: 00
type: execute
wave: 1
depends_on: []
files_modified:
  - gradle/libs.versions.toml
  - backend/core/build.gradle.kts
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
  - backend/core/src/main/resources/db/changelog/changes/030-thread-reply-status.yaml
  - backend/core/src/test/java/com/zeromail/core/draft/ReplyMimeBuildTest.java
  - backend/core/src/test/java/com/zeromail/core/draft/ThreadingHeaderValidatorTest.java
  - backend/core/src/test/java/com/zeromail/core/draft/GenerateThreadDraftServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/draft/ToneContextBuilderTest.java
  - backend/core/src/test/java/com/zeromail/core/draft/DraftPrivacyLogScrubTest.java
  - backend/core/src/test/java/com/zeromail/core/draft/DraftPathArchUnitTest.java
  - backend/core/src/test/java/com/zeromail/core/thread/ClassifyThreadReplyStatusServiceTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/triage/TriageAuditControllerContractTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/triage/AuditLogPaginationTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/triage/AuditLogMultiTenantLeakTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/thread/ThreadDraftControllerContractTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/thread/DraftLockContentionTest.java
  - apps/web/features/needs-reply/components/NeedsReplyTable.test.tsx
  - apps/web/e2e/needs-reply.spec.ts
autonomous: true
requirements: [DRFT-01, DRFT-02, DRFT-03, DRFT-04]
must_haves:
  truths:
    - "jakarta.mail (Angus Mail) is on backend/core runtime classpath; MimeMessage class loads"
    - "thread_reply_status Liquibase changelog applies cleanly against a Testcontainers Postgres"
    - "Wave 0 backend test classes compile but fail (RED) on missing production classes"
    - "Wave 0 frontend test files exist and reference the future needs-reply feature"
  artifacts:
    - path: "backend/core/src/main/resources/db/changelog/changes/030-thread-reply-status.yaml"
      provides: "thread_reply_status table + unique index + partial TO_REPLY index + rollback"
      contains: "createTable"
    - path: "gradle/libs.versions.toml"
      provides: "angus-mail + jakarta-mail-api version catalog entries"
      contains: "jakarta.mail"
    - path: "backend/core/src/test/java/com/zeromail/core/draft/ReplyMimeBuildTest.java"
      provides: "RED test scaffold for the reply-MIME build path"
  key_links:
    - from: "backend/core/build.gradle.kts"
      to: "gradle/libs.versions.toml"
      via: "implementation(libs.jakarta.mail.api) + runtimeOnly(libs.angus.mail)"
      pattern: "libs\\.(jakarta\\.mail\\.api|angus\\.mail)"
    - from: "backend/core/src/main/resources/db/changelog/db.changelog-master.yaml"
      to: "changes/030-thread-reply-status.yaml"
      via: "include directive"
      pattern: "030-thread-reply-status"
---

<objective>
[BLOCKING] Lay the dependency, schema, and test foundation for Phase 5B before any production code lands: add the `jakarta.mail` (Angus Mail) dependency to the version catalog, create the `030-thread-reply-status` Liquibase changelog and wire it into the master changelog, and create the RED-by-design Wave 0 test spine (backend + frontend) that the later plans must turn GREEN.

Purpose: Every later plan depends on this. The `jakarta.mail` classpath change touches `backend/core` (transitively `backend/api`/`backend/worker`), the `thread_reply_status` table is needed by the classifier and inbox plans, and the Wave 0 tests are the executable acceptance contract for the whole phase.
Output: Version catalog + build script edits, the Liquibase changelog, ~14 RED test scaffolds.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@CLAUDE.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-SPEC.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-PATTERNS.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md
@.planning/phases/05B-user-surface-ai-draft-replies/05B-RESEARCH.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add jakarta.mail dependency + thread_reply_status Liquibase changelog</name>
  <files>gradle/libs.versions.toml, backend/core/build.gradle.kts, backend/core/src/main/resources/db/changelog/changes/030-thread-reply-status.yaml, backend/core/src/main/resources/db/changelog/db.changelog-master.yaml</files>
  <read_first>
    - gradle/libs.versions.toml (current version catalog format — `[versions]` / `[libraries]` blocks)
    - backend/core/build.gradle.kts (how `implementation(libs.*)` / `runtimeOnly(libs.*)` are declared)
    - backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml (createTable + createIndex + `sql:` for unique/partial indexes + `rollback: dropTable` analog)
    - backend/core/src/main/resources/db/changelog/changes/028-tenant-protected-sender-observation.yaml (recent tenant-FK changelog shape)
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml (include ordering — append `030` after `029`)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-RESEARCH.md §"Standard Stack — Supporting NEW dependency to add" and §"Common Pitfalls — Pitfall 1"
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-PATTERNS.md §"core/thread/persistence... + Liquibase changelog — NEW" (the full changelog skeleton)
  </read_first>
  <action>
    Add to `gradle/libs.versions.toml`: `[versions]` `jakartaMail = "2.0.4"` and `jakartaMailApi = "2.1.3"`; `[libraries]` `angus-mail = { module = "org.eclipse.angus:angus-mail", version.ref = "jakartaMail" }` and `jakarta-mail-api = { module = "jakarta.mail:jakarta.mail-api", version.ref = "jakartaMailApi" }`. In `backend/core/build.gradle.kts` add `implementation(libs.jakarta.mail.api)` and `runtimeOnly(libs.angus.mail)`.
    Create `backend/core/src/main/resources/db/changelog/changes/030-thread-reply-status.yaml` with changeSet id `030-thread-reply-status`, author `zeromail`, comment noting "Metadata-only thread reply-status projection. No email bodies, prompts, or completions." Columns: `id` uuid PK `defaultValueComputed: gen_random_uuid()`; `tenant_id` uuid not-null FK `fk_thread_reply_status_tenant` references `tenants(id)` `deleteCascade: true`; `gmail_thread_id` varchar(255) not-null; `bucket` varchar(32) not-null; `last_classified_message_id` varchar(255) nullable; `last_classified_at` timestamptz nullable; `has_draft` boolean not-null `defaultValueBoolean: false`; `draft_id` varchar(255) nullable; `resolved` boolean not-null `defaultValueBoolean: false`; plus `created_at` timestamptz `defaultValueComputed: now()` not-null, `updated_at` timestamptz `defaultValueComputed: now()` not-null, `version` bigint `defaultValueNumeric: 0` not-null (mirror `025-triage-audit.yaml`'s audit-column shape exactly). Then `sql:` blocks: CHECK constraint `ck_thread_reply_status_bucket CHECK (bucket IN ('TO_REPLY','AWAITING_THEIR_REPLY','FYI','ACTIONED'))`; `CREATE UNIQUE INDEX ux_thread_reply_status_tenant_thread ON thread_reply_status (tenant_id, gmail_thread_id)`; `CREATE INDEX idx_thread_reply_status_to_reply ON thread_reply_status (tenant_id) WHERE bucket = 'TO_REPLY' AND NOT resolved` (with a `comment:` for the partial index). `rollback: dropTable: { tableName: thread_reply_status }`.
    Append an `include` entry for `changes/030-thread-reply-status.yaml` in `db.changelog-master.yaml` after the `029` entry, matching the existing include syntax exactly.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:dependencies --configuration runtimeClasspath | grep -E "angus-mail|jakarta.mail" && ./gradlew :backend:core:test --tests "*Liquibase*" --tests "*SchemaPush*" 2>&1 | tail -5</automated>
  </verify>
  <acceptance_criteria>
    - `./gradlew :backend:core:dependencies --configuration runtimeClasspath` lists `org.eclipse.angus:angus-mail:2.0.4` and `jakarta.mail:jakarta.mail-api:2.1.3`
    - `Class.forName("jakarta.mail.internet.MimeMessage")` succeeds at test runtime (asserted by a one-line test or `gradle dependencyInsight --dependency jakarta.mail`)
    - `db.changelog-master.yaml` contains an `include` for `changes/030-thread-reply-status.yaml`
    - A Liquibase-update against the Testcontainers Postgres (existing schema-push test) succeeds with the new changeset; `thread_reply_status` table exists with the unique index `ux_thread_reply_status_tenant_thread` and partial index `idx_thread_reply_status_to_reply`
    - `mcp__jetbrains__get_file_problems` on `backend/core/build.gradle.kts` reports no new problems
  </acceptance_criteria>
  <done>jakarta.mail on the classpath, `030-thread-reply-status.yaml` created and wired into the master changelog, schema-push test green.</done>
</task>

<task type="auto">
  <name>Task 2: Backend Wave 0 RED test scaffolds + ArchUnit guards</name>
  <files>backend/core/src/test/java/com/zeromail/core/draft/ReplyMimeBuildTest.java, backend/core/src/test/java/com/zeromail/core/draft/ThreadingHeaderValidatorTest.java, backend/core/src/test/java/com/zeromail/core/draft/GenerateThreadDraftServiceTest.java, backend/core/src/test/java/com/zeromail/core/draft/ToneContextBuilderTest.java, backend/core/src/test/java/com/zeromail/core/draft/DraftPrivacyLogScrubTest.java, backend/core/src/test/java/com/zeromail/core/draft/DraftPathArchUnitTest.java, backend/core/src/test/java/com/zeromail/core/thread/ClassifyThreadReplyStatusServiceTest.java, backend/api/src/test/java/com/zeromail/api/controllers/triage/TriageAuditControllerContractTest.java, backend/api/src/test/java/com/zeromail/api/controllers/triage/AuditLogPaginationTest.java, backend/api/src/test/java/com/zeromail/api/controllers/triage/AuditLogMultiTenantLeakTest.java, backend/api/src/test/java/com/zeromail/api/controllers/thread/ThreadDraftControllerContractTest.java, backend/api/src/test/java/com/zeromail/api/controllers/thread/DraftLockContentionTest.java</files>
  <read_first>
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md §5 (Evaluation Strategy — dims 4, 6, 7, 8 are deterministic; the rubrics define what each test asserts) and §6 (Guardrails)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-RESEARCH.md §"Validation Architecture — Phase Requirements → Test Map" and §"Wave 0 Gaps"
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md (D-01..D-17 — the behaviors under test)
    - backend/core/src/test/java/com/zeromail/core/llm/.../*ArchUnit*.java and the existing Spring-AI import-confinement ArchUnit rule (find it via grep `org.springframework.ai` in test sources) — DraftPathArchUnitTest mirrors its shape
    - backend/api/src/test/java/.../TriageUndoControllerContractTest.java (controller contract test pattern; uses RestClient + LocalServerPort per the project test convention, not MockMvc) — find under backend/api/src/test
    - backend/api/src/test/java/.../BillingBalanceMultiTenantLeakTest.java (multi-tenant leak test pattern) — find under backend/api/src/test
    - backend/core/src/test/java/.../BillingPrivacyLogScrubTest.java (log-capture privacy assertion pattern) — find under backend/core/src/test
    - backend/core/src/test/java/.../NoGmailSendAllowedTest.java + TriageGmailWriteBoundaryTest.java (existing Gmail-write boundary tests to extend)
  </read_first>
  <action>
    Create RED-by-design JUnit 5 + AssertJ test scaffolds that reference the future production classes by name so `compileTestJava` stays green only once those classes exist (or via reflection/FQN-string where a compile dependency on a not-yet-existing class would block other tests — match whichever the existing Wave 0 convention in this repo uses; check 04-00 / 02C-01 plans' approach):
    - `ReplyMimeBuildTest`: builds a reply MIME via the future `draft.ReplyMimeBuilder` (or `TriageGmailWriter` widened path), parses it back with `jakarta.mail.internet.MimeMessage`, asserts `In-Reply-To` == inbound `Message-ID`, `References` == prior chain + that id, exactly one `Re:` prefix (case-insensitive), correct `To`, `threadId` set, base64url-no-padding. Regression cases: no prior `References`; subject already `Re:`-prefixed; non-ASCII / Vietnamese subject; missing `Message-ID` → builder fails closed.
    - `ThreadingHeaderValidatorTest`: the deterministic validator that runs before `drafts.create` rejects a MIME with missing/malformed headers or a `threadId` mismatch.
    - `GenerateThreadDraftServiceTest`: with a stubbed `LlmGateway` returning `ToolCallResult{action=save_draft, args{body}}`, the service produces a non-empty body, calls `deleteDraft` then `saveDraft` (delete-then-recreate, D-15), persists the new `draftId`, upserts `thread_reply_status`.
    - `ToneContextBuilderTest`: fetches sent mail (stubbed Gmail), strips quotes (`On … wrote:` / leading `>`) and signatures (`-- `), runs each snippet through `SanitizationPipeline`; on `TokenBudgetExceededException` degrades to descriptors-only and the build still returns a `ToneContext`; no persistence layer holds snippet content after the call.
    - `DraftPrivacyLogScrubTest`: capture logs during a draft generation; assert no sent-mail body bytes, no draft body, no prompt, no completion in any log line.
    - `DraftPathArchUnitTest`: no reference to `users.drafts.send` / `users.drafts.update` / `users.messages.send` from `com.zeromail.core.draft..` or `com.zeromail.core.triage..`; `jakarta.mail..` is importable only from the package that owns the MIME builder; `org.springframework.ai..` not importable from `com.zeromail.core.draft..` (it goes through `LlmGateway`).
    - `ClassifyThreadReplyStatusServiceTest`: heuristic — last message `From` == tenant address (+ `SENT` label) → `AWAITING_THEIR_REPLY`; counterparty last + no draft → `TO_REPLY`; thread with a Zero-Mail draft → still `TO_REPLY` with `hasDraft=true`; idempotency: unchanged `(tenantId, gmailThreadId, lastClassifiedMessageId)` → no re-upsert.
    - `TriageAuditControllerContractTest` + `AuditLogPaginationTest` + `AuditLogMultiTenantLeakTest`: `GET /api/triage/audit` returns `{ items, nextCursor }`; items carry `auditId`, `gmailThreadId`, `gmailMessageId`, `ruleName`, `action`, `reason`, `decisionState`, `createdAt`, `draftId`; `nextCursor` round-trips for the next page; tenant A cannot see tenant B's rows.
    - `ThreadDraftControllerContractTest` + `DraftLockContentionTest`: `POST /api/threads/{gmailThreadId}/draft` returns `{ draftId, gmailThreadId, status, openInGmailUrl }` (no body field) and produces a Gmail draft for that thread; a second concurrent call while the Redis lock is held returns HTTP 409.
    Each test that exercises a not-yet-existing class must be visibly RED (compile error or assertion failure), and that RED state IS the acceptance contract for the later plans.
  </action>
  <verify>
    <automated>cd "$REPO" && ./gradlew :backend:core:compileTestJava :backend:api:compileTestJava 2>&1 | tail -5</automated>
  </verify>
  <acceptance_criteria>
    - All 12 backend test files exist under the listed paths
    - `compileTestJava` for `:backend:core` and `:backend:api` either succeeds (FQN/reflection style) or fails only on the intentionally-referenced future production classes (literal-reference style) — matching the repo's established Wave 0 convention
    - `DraftPathArchUnitTest` is structured to fail today (the `core.draft` package doesn't exist yet) and will pass once the package is created without forbidden imports
    - No test references an email body, address, Google subject, token bytes, prompt, or completion in an assertion against a log line except to assert its ABSENCE
  </acceptance_criteria>
  <done>12 backend Wave 0 test scaffolds committed; the RED set is the acceptance contract for Plans 01–05.</done>
</task>

<task type="auto">
  <name>Task 3: Frontend Wave 0 test scaffolds + EN_SCAN_FILES update</name>
  <files>apps/web/features/needs-reply/components/NeedsReplyTable.test.tsx, apps/web/e2e/needs-reply.spec.ts, apps/web/scripts/check-i18n.ts</files>
  <read_first>
    - apps/web/features/triage/components/AuditTable.tsx + its Vitest test (find `apps/web/features/triage/**/*.test.tsx` or `apps/web/__tests__/**`) — the table-component test shape
    - apps/web/e2e/*.spec.ts (an existing Playwright spec, e.g. the triage or rules one) — the e2e golden-path shape
    - apps/web/scripts/check-i18n.ts (the `EN_SCAN_FILES` list — add the new needs-reply page + components so the i18n scanner does not lose coverage)
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-UI-SPEC.md §"Key Screens & States" (the states the component test must cover: populated / loading / classifying banner / empty TO_REPLY / empty AWAITING / error / 320px)
  </read_first>
  <action>
    Create `apps/web/features/needs-reply/components/NeedsReplyTable.test.tsx` (Vitest) asserting: renders both buckets at 0 / 1 / many threads; loading shows `Skeleton` rows; the "classifying…" amber banner renders above a (possibly stale) list; the TO_REPLY empty state shows "Inbox zero 🎉"; the AWAITING empty state shows "Nothing awaiting"; the error state shows the destructive `Alert` + "Try again"; a row exposes the `Draft reply` / `Regenerate draft` action, the "Open in Gmail" link to `https://mail.google.com/mail/u/0/#all/<threadId>`, the draft-status badge (`No draft` / `Draft ready` / `Draft sent`), and `Mark resolved`. RED-by-design: it imports the future `NeedsReplyTable` component which doesn't exist yet.
    Create `apps/web/e2e/needs-reply.spec.ts` (Playwright) with a skipped-or-RED golden path: navigate to `/needs-reply`, see the two-bucket Tabs, click `Draft reply` on a row, see the success toast "Draft saved in Gmail — review and send it there.", see the draft-status badge flip to `Draft ready`. Mark `test.fixme(...)` until the feature lands (mirror how prior phases' e2e specs handle env-blocked routes).
    Add the new files (`app/(protected)/(app)/needs-reply/page.tsx` and `features/needs-reply/components/*.tsx`) to the `EN_SCAN_FILES` list in `apps/web/scripts/check-i18n.ts` so `pnpm i18n:check` keeps scanning them.
  </action>
  <verify>
    <automated>cd "$REPO/apps/web" && pnpm vitest run features/needs-reply 2>&1 | tail -10; pnpm tsc --noEmit 2>&1 | tail -5</automated>
  </verify>
  <acceptance_criteria>
    - `apps/web/features/needs-reply/components/NeedsReplyTable.test.tsx` and `apps/web/e2e/needs-reply.spec.ts` exist
    - `NeedsReplyTable.test.tsx` is RED today (imports a non-existent component) and asserts each state from UI-SPEC §Key Screens
    - `apps/web/scripts/check-i18n.ts` `EN_SCAN_FILES` includes `app/(protected)/(app)/needs-reply/page.tsx` and the needs-reply component files
    - `pnpm i18n:check` still passes (no new keys yet, just file-list extension)
  </acceptance_criteria>
  <done>Frontend Wave 0 RED spine committed; i18n scanner aware of the new feature.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| build/dependency supply chain | new `jakarta.mail` artifact enters the classpath of `backend/core` and transitively `api`/`worker` |
| Liquibase changelog → production DB | `030-thread-reply-status.yaml` will run against the real Postgres on deploy |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05B-00-01 | Tampering | `jakarta.mail` dependency (wrong/poisoned artifact, milestone vs stable) | mitigate | Pin `org.eclipse.angus:angus-mail:2.0.4` (stable, never the `2.1.0-M1` milestone per the no-pre-release policy); `gradle dependencyInsight` after adding to confirm no `jakarta.activation` skew with the present `angus-activation:2.0.3` |
| T-05B-00-02 | Denial of Service | `030-thread-reply-status` migration | mitigate | `createTable` on a fresh table only — no data backfill, no destructive change; `rollback: dropTable` provided; partial index is cheap on an empty table |
| T-05B-00-03 | Information Disclosure | `thread_reply_status` schema design | mitigate | Schema is metadata-only by construction — no body/subject/participant columns; CHECK constraint pins `bucket` to the enum id set; FK `deleteCascade: true` so account deletion purges rows |
| T-05B-00-04 | Repudiation | Wave 0 ArchUnit guards absent | mitigate | `DraftPathArchUnitTest` lands now (RED) so the no-`drafts.send`/`drafts.update` and Spring-AI/jakarta.mail import-confinement invariants are enforced from the first production commit, not retrofitted |
</threat_model>

<verification>
- `./gradlew :backend:core:dependencies --configuration runtimeClasspath` shows `angus-mail:2.0.4` + `jakarta.mail-api:2.1.3`
- `./gradlew :backend:core:test --tests "*SchemaPush*"` (or the existing Testcontainers Liquibase test) green with `thread_reply_status` present
- `./gradlew :backend:core:compileTestJava :backend:api:compileTestJava` behaves per the repo's Wave 0 convention (green for FQN-style, or RED only on intentional future-class references)
- `pnpm -C apps/web i18n:check` passes; `pnpm -C apps/web vitest run features/needs-reply` is RED-by-design
</verification>

<success_criteria>
jakarta.mail on the classpath; `030-thread-reply-status.yaml` created and master-wired; ~14 RED Wave 0 test scaffolds committed; `EN_SCAN_FILES` extended. This plan unblocks Plans 01–05.
</success_criteria>

<output>
After completion, create `.planning/phases/05B-user-surface-ai-draft-replies/05B-00-SUMMARY.md`
</output>
