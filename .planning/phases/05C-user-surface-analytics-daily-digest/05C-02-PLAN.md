---
phase: 05C
plan: 02
type: execute
wave: 2
depends_on:
  - 05C-01
files_modified:
  - backend/core/src/main/java/com/zeromail/core/analytics/package-info.java
  - backend/core/src/main/java/com/zeromail/core/analytics/domain/TimeSavedWeights.java
  - backend/core/src/main/java/com/zeromail/core/analytics/domain/TimeWindow.java
  - backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryProjection.java
  - backend/core/src/main/java/com/zeromail/core/analytics/projection/TopSenderProjection.java
  - backend/core/src/main/java/com/zeromail/core/analytics/projection/RuleHitProjection.java
  - backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryQueryService.java
  - backend/api/src/main/java/com/zeromail/api/controllers/analytics/AnalyticsController.java
  - backend/api/src/main/java/com/zeromail/api/dto/analytics/AnalyticsSummaryResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/analytics/AnalyticsWindow.java
  - backend/core/src/test/java/com/zeromail/core/arch/AnalyticsRepositoryContentBanTest.java
  - backend/core/src/test/java/com/zeromail/core/analytics/AnalyticsSummaryQueryServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/analytics/AnalyticsPrivacySweepTest.java
  - backend/core/src/test/java/com/zeromail/core/analytics/TimeSavedWeightsTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/analytics/AnalyticsControllerContractTest.java
autonomous: true
requirements:
  - ANL-01
  - ANL-02
threat_refs:
  - T-05C-04
  - T-05C-05
  - T-05C-06
  - T-05C-07
must_haves:
  truths:
    - "Authenticated tenant calling GET /api/analytics/summary?window=7d|30d|90d gets a single JSON response with volume + timeSaved + topSenders + ruleHits"
    - "AnalyticsSummaryQueryService.summarize accepts an explicit TimeWindow(startInclusive, endExclusive) — NOT a Duration; the controller computes `TimeWindow.endingAt(Instant.now(), window.duration())` and Plan 03's digest pipeline computes `TimeWindow.endingAt(sendMoment, Duration.ofHours(24))` so the closed prior-24h window is expressible — REVIEW FIX (Codex C5)"
    - "Service NEVER reads `TenantContext` internally; controller extracts `tenantId = TenantContext.currentOrThrow()` and passes it as the first positional parameter; Plan 03 worker passes the bound tenantId from its ScopedValue.where scope — REVIEW FIX (OpenCode M3 / S5)"
    - "Time-saved math equals appliedLabel*10 + appliedArchive*30 + appliedSaveDraft*180 in seconds; reverted rows excluded"
    - "Q1 (volume) and Q3 (top senders) count ONLY rows whose `label_ids` array contains `'INBOX'` — SENT mail (composed/reply-drafted) is excluded from analytics — REVIEW FIX (Codex MEDIUM: top-sender/volume queries may include SENT mail)"
    - "Q3 top-3 senders skips rows with NULL sender_email (pre-§0-fix rows do not bias the result)"
    - "Tenant A cannot see tenant B's data — every aggregation query joins WHERE tenant_id = ? (first positional param, sourced from the service caller — not via TenantContext)"
    - "No sender_email, no gmail_message_id, no body, no prompts appear in server logs during an analytics endpoint hit (privacy sweep test)"
    - "Analytics service does not depend on any column other than those on triage_audit + mail_message_observed — ArchUnit boundary test enforces"
    - "Invalid window param (e.g. window=8d, window=10y, window=) returns 400 — empty-string window (`?window=`) is treated as missing and falls through to default 7d ONLY when the parameter is truly absent or empty by Spring's `required=false` semantics; ambiguous empty strings are explicitly rejected with 400 — REVIEW FIX (OpenCode L1)"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryQueryService.java"
      provides: "4 JDBC queries Q1–Q4 with @Transactional(readOnly=true)"
      min_lines: 100
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/analytics/AnalyticsController.java"
      provides: "GET /api/analytics/summary?window= endpoint"
      exports: ["summary"]
    - path: "backend/api/src/main/java/com/zeromail/api/dto/analytics/AnalyticsSummaryResponse.java"
      provides: "typed response record with from(projection, window) factory"
    - path: "backend/core/src/main/java/com/zeromail/core/analytics/domain/TimeSavedWeights.java"
      provides: "label=10, archive=30, save_draft=180 constants"
      contains: "180"
  key_links:
    - from: "backend/api/src/main/java/com/zeromail/api/controllers/analytics/AnalyticsController.java"
      to: "core.analytics.projection.AnalyticsSummaryQueryService"
      via: "summarize(tenantId, TimeWindow.endingAt(Instant.now(), window.duration()))"
      pattern: "analyticsSummaryQueryService\\.summarize.*TimeWindow"
    - from: "backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryQueryService.java"
      to: "Postgres triage_audit + mail_message_observed"
      via: "JdbcTemplate 4 queries Q1..Q4 with WHERE observed_at >= ? AND observed_at < ? AND 'INBOX' = ANY(label_ids)"
      pattern: "jdbcTemplate\\.(query|queryForObject)"
---

<objective>
Ship the read-side analytics aggregation: a new `core.analytics` Modulith module hosting `AnalyticsSummaryQueryService` (4 JDBC queries Q1–Q4 over `triage_audit` + `mail_message_observed`, `@Transactional(readOnly=true)`, mirroring `AuditLogQueryService` shape per D-18) and a new `AnalyticsController` in `backend/api/controllers/analytics/` serving `GET /api/analytics/summary?window=7d|30d|90d` with a single typed `AnalyticsSummaryResponse` (D-24). Ship the time-saved constants as `core.analytics.domain.TimeSavedWeights` (D-23). Plant the boundary tests: `AnalyticsRepositoryContentBanTest` (ArchUnit ban on body/prompt/completion column reads), `AnalyticsPrivacySweepTest` (no sender_email in logs), and the `AnalyticsControllerContractTest` (Spring MVC test for response shape + window validation + tenant scoping).

Purpose: Plan 03 (digest dispatcher) reuses `AnalyticsSummaryQueryService.summarize(tenantId, Duration.ofHours(24))` with the same 4 queries — different window bounds — so the read-side MUST be production-ready before digest composition lands. Plan 04 (frontend) calls this endpoint via the OpenAPI-generated typed client.

Output: 1 Modulith module (`core.analytics`), 4 projection records + 1 service + 1 weights constants class, 1 controller + 1 response DTO + 1 window enum, 5 backend tests.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-SPEC.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-RESEARCH.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-VALIDATION.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-01-PLAN.md
@CLAUDE.md
@CONVENTIONS.md

<!-- Code templates (RESEARCH.md §Code Examples) -->
@backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogQueryService.java
@backend/core/src/test/java/com/zeromail/core/triage/TriagePrivacySweepTest.java
@backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java
@backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java
@backend/api/src/main/java/com/zeromail/api/dto/triage/AuditLogPageResponse.java
@backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java
@backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml
</context>

<interfaces>
<!-- Critical contracts the executor needs without re-exploring -->

`AuditLogQueryService` shape (template): `@Service` + constructor injection of `JdbcTemplate` + Objects.requireNonNull guard + `@Transactional(readOnly = true)` on the public method. Mirror exactly for `AnalyticsSummaryQueryService`.

`TriageAuditEntity` columns (from changeset 025): `tenant_id`, `gmail_message_id`, `gmail_thread_id`, `rule_id`, `rule_name_snapshot`, `action_type` (label/archive/save_draft), `decision`, `decided_at`, `applied_at`, `reverted_at`, `attempt_count`. Q1 + Q2 + Q4 query this.

`mail_message_observed` columns (Plan 01 added `sender_email`): `tenant_id`, `gmail_message_id`, `gmail_thread_id`, `sender_email` (nullable, NEW from Plan 01 §0 fix), `history_id`, `label_ids`, `internal_date`, `observed_at`. Q1 + Q3 query this.

Endpoint shape (D-24):
- URL: `GET /api/analytics/summary?window=7d|30d|90d`
- Default when `window` param missing OR empty (`?window=` with no value): `7d` — handled at controller via `@RequestParam(value="window", required=false) String windowId` then `windowId = (windowId == null || windowId.isBlank()) ? "7d" : windowId.trim()` BEFORE calling `AnalyticsWindow.fromId(...)` — REVIEW FIX (OpenCode L1)
- Invalid value (e.g. `?window=8d`, `?window=10y`, `?window=abc`): `400 Bad Request`
- Response record `AnalyticsSummaryResponse(window: String, volumeObserved: long, volumeApplied: long, timeSavedSeconds: long, topSenders: List<TopSenderResponse>, ruleHits: List<RuleHitResponse>)` where `TopSenderResponse(senderEmail: String, count: long)` and `RuleHitResponse(ruleName: String, decisions: long, applied: long, reverted: long)`

**REVIEW FIX (Codex C5) — Service API signature:**
`AnalyticsSummaryQueryService.summarize(UUID tenantId, TimeWindow window) → AnalyticsSummaryProjection` where `TimeWindow(Instant startInclusive, Instant endExclusive)` is a new record in `core.analytics.domain.TimeWindow` with static factories `endingAt(Instant endExclusive, Duration duration)` and `between(Instant startInclusive, Instant endExclusive)`. Controller: `service.summarize(tenantId, TimeWindow.endingAt(Instant.now(), window.duration()))`. Plan 03 digest: `service.summarize(tenantId, TimeWindow.between(sendMoment.minus(Duration.ofHours(24)), sendMoment))` — closed `[sendMoment-24h, sendMoment)` window per CONTEXT D-19.

Q1 SQL (volume — observed messages and applied audits) — UPDATED with INBOX filter and closed window:
- Observed: `SELECT count(*) FROM mail_message_observed WHERE tenant_id = ? AND observed_at >= ? AND observed_at < ? AND 'INBOX' = ANY(label_ids)` — REVIEW FIX (Codex MEDIUM INBOX filter)
- Applied: `SELECT count(*) FROM triage_audit WHERE tenant_id = ? AND applied_at >= ? AND applied_at < ? AND reverted_at IS NULL`

Q2 SQL (time saved — closed window): `SELECT action_type, count(*) FROM triage_audit WHERE tenant_id = ? AND applied_at >= ? AND applied_at < ? AND reverted_at IS NULL GROUP BY action_type` — app layer multiplies by TimeSavedWeights constants

Q3 SQL (top-3 senders — INBOX filter + NULL skip + closed window) — UPDATED: `SELECT sender_email, count(*) AS c FROM mail_message_observed WHERE tenant_id = ? AND observed_at >= ? AND observed_at < ? AND sender_email IS NOT NULL AND 'INBOX' = ANY(label_ids) GROUP BY sender_email ORDER BY c DESC, sender_email ASC LIMIT 3` — REVIEW FIX (Codex MEDIUM INBOX filter); the `IS NOT NULL` is the §0 fix gate; pre-fix rows skipped; the `'INBOX' = ANY(label_ids)` filter excludes SENT (composed reply) mail

Q4 SQL (rule hits — closed window): `SELECT rule_name_snapshot, count(*) AS decisions, count(*) FILTER (WHERE applied_at IS NOT NULL AND reverted_at IS NULL) AS applied, count(*) FILTER (WHERE reverted_at IS NOT NULL) AS reverted FROM triage_audit WHERE tenant_id = ? AND decided_at >= ? AND decided_at < ? GROUP BY rule_name_snapshot ORDER BY decisions DESC, rule_name_snapshot ASC`

**REVIEW FIX (OpenCode M3 / S5)** — `TenantContext.currentOrThrow()` is read ONLY at the controller boundary (HTTP context); the service signature takes `UUID tenantId` as an explicit parameter and NEVER reads `TenantContext` internally. This is load-bearing because Plan 03's worker has no HTTP context — it uses its own `ScopedValue.where(TenantContext.TENANT, ...)` scope and passes the bound tenantId explicitly into the service.

Modulith `core.analytics/package-info.java` declares `@ApplicationModule(displayName="Analytics", allowedDependencies={"triage", "gmail", "shared.persistence", "shared.lang"})` (per RESEARCH §12 #3). Plan 03's `core.notification` module declares `"analytics"` in its `allowedDependencies` so `DigestComposer` can import `AnalyticsSummaryQueryService` (review M1 / S4).

`AuthorizedClient`-style controller auth inherits the existing Phase 5A/5B security filter chain — no new auth surface; tenant scoping is via `TenantContext`.

`springdoc-openapi 3.0.3` auto-discovers `@RestController`; the new controller appears in `/v3/api-docs` after first build. Plan 04 runs `pnpm generate:api` to refresh the typed client.
</interfaces>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: AnalyticsSummaryQueryService (summarize(tenantId, TimeWindow)) + TimeWindow + TimeSavedWeights + projection records, with ArchUnit content-ban + privacy-sweep tests; INBOX-filter Q1/Q3 — REVIEW FIXES Codex C5 + Codex MEDIUM (INBOX filter) + OpenCode M3</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/analytics/package-info.java,
    backend/core/src/main/java/com/zeromail/core/analytics/domain/TimeSavedWeights.java,
    backend/core/src/main/java/com/zeromail/core/analytics/domain/TimeWindow.java,
    backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryProjection.java,
    backend/core/src/main/java/com/zeromail/core/analytics/projection/TopSenderProjection.java,
    backend/core/src/main/java/com/zeromail/core/analytics/projection/RuleHitProjection.java,
    backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryQueryService.java,
    backend/core/src/test/java/com/zeromail/core/analytics/TimeSavedWeightsTest.java,
    backend/core/src/test/java/com/zeromail/core/analytics/AnalyticsSummaryQueryServiceTest.java,
    backend/core/src/test/java/com/zeromail/core/arch/AnalyticsRepositoryContentBanTest.java,
    backend/core/src/test/java/com/zeromail/core/analytics/AnalyticsPrivacySweepTest.java
  </files>
  <read_first>
    backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogQueryService.java,
    backend/core/src/test/java/com/zeromail/core/triage/TriagePrivacySweepTest.java,
    backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java,
    backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java,
    backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml,
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md (D-18 D-19 D-20 D-22 D-23 D-25),
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-RESEARCH.md (§5 JDBC read-side and §11 test infrastructure)
  </read_first>
  <behavior>
    - **REVIEW FIX (Codex C5)** — `TimeWindow` record in `core.analytics.domain`: `record TimeWindow(Instant startInclusive, Instant endExclusive)` with compact constructor that validates `endExclusive.isAfter(startInclusive)` (throws `IllegalArgumentException` otherwise); static factories `TimeWindow.endingAt(Instant endExclusive, Duration duration) → new TimeWindow(endExclusive.minus(duration), endExclusive)` and `TimeWindow.between(Instant startInclusive, Instant endExclusive) → new TimeWindow(startInclusive, endExclusive)`. This is the load-bearing fix for Codex C5 — the closed `[start, end)` interval is now expressible (Plan 03 digest needs `[sendMoment-24h, sendMoment)`).
    - `TimeSavedWeights`: final class (or record) with `public static final int LABEL_SECONDS = 10`, `ARCHIVE_SECONDS = 30`, `SAVE_DRAFT_SECONDS = 180`; static method `computeSeconds(Map<String,Long> appliedByActionType)` returning the weighted sum. Action-type ids match the existing `triage_audit.action_type` literal strings (`"label"`, `"archive"`, `"save_draft"`).
    - `AnalyticsSummaryProjection` record fields: `long volumeObserved, long volumeApplied, long timeSavedSeconds, List<TopSenderProjection> topSenders, List<RuleHitProjection> ruleHits`
    - `TopSenderProjection` record: `String senderEmail, long count`
    - `RuleHitProjection` record: `String ruleName, long decisions, long applied, long reverted`
    - **REVIEW FIX (Codex C5 + OpenCode M3)** — `AnalyticsSummaryQueryService` constructor injection of `JdbcTemplate`; single public method `@Transactional(readOnly = true) public AnalyticsSummaryProjection summarize(UUID tenantId, TimeWindow window)` — explicit `UUID tenantId` parameter, NOT read from `TenantContext` inside the service (M3/S5 — service has no HTTP context dependency; works equally well for web controller and worker scheduler); runs Q1, Q2, Q3, Q4 in sequence; binds JDBC params in order `tenantId, window.startInclusive(), window.endExclusive()` (and `decided_at` analogues for Q2/Q4). Service is allowed to log `event=analytics_summary_computed tenantId={} windowStart={} windowEnd={}` only — NO row counts in logs (counts go in the returned projection).
    - Q1, Q2, Q3, Q4 SQL matches the spec in `<interfaces>` exactly (closed `>= start AND < end` form); Q1 and Q3 additionally include `AND 'INBOX' = ANY(label_ids)` (Codex MEDIUM — exclude SENT mail from analytics); Q3 includes `AND sender_email IS NOT NULL` (§0 graceful-skip). Q2 and Q4 do NOT need the INBOX filter because `triage_audit` rows only exist for incoming triage decisions; verify by reading `TriageAuditEntity` and Phase 4 ingestion — if any code path writes `triage_audit` rows for SENT messages, this assumption is wrong and Q2/Q4 also need a join to filter, document the deviation in the summary.
    - `TimeSavedWeightsTest`: pure unit test, no Spring; given `{label: 5, archive: 2, save_draft: 1}` returns `5*10 + 2*30 + 1*180 = 290`; unknown action_type ids ignored (zero contribution); empty map returns 0
    - `AnalyticsSummaryQueryServiceTest`: extends `PostgresContainerTest`, seeded fixture (N tenants, M audit rows, K observed rows with the §0 gating). Asserts (a) Q1 volume correct (b) Q2 time-saved formula correct with reverted rows excluded (c) Q3 top-3 deterministic alphabetical tie-break (d) Q3 skips NULL sender_email rows (e) Q4 applied/reverted FILTER counts correct (f) tenant B's data is never visible to tenant A's summarize call **(g) — NEW — INBOX-only filter:** seed 5 `mail_message_observed` rows for tenant A — 3 with `label_ids = ARRAY['INBOX']` and 2 with `label_ids = ARRAY['SENT']`; assert Q1 volume = 3 (not 5) and Q3 top-3 omits the SENT senders **(h) — NEW — closed-window:** call `summarize(tenantA, TimeWindow.between(t0, t1))` with a sentinel row at `observed_at = t1` (the endExclusive boundary); assert it is EXCLUDED from counts; a sentinel at `observed_at = t0` is INCLUDED.
    - `AnalyticsRepositoryContentBanTest`: ArchUnit test — `noClasses().that().resideInAPackage("..core.analytics..").should().dependOnClassesThat().resideInAnyPackage("..draft..", "..thread.. (only the body-bearing entities, not the read-only thread reply status — refine via specific class names)")` AND a `noMethods().that().areDeclaredInClassesThat().resideInAPackage("..core.analytics..").should().callMethodWhere(...)` rule banning JDBC queries that mention forbidden column substrings — implement as a method that scans `AnalyticsSummaryQueryService` source/bytecode for forbidden tokens (`body`, `prompt`, `completion`, `embedding`)
    - `AnalyticsPrivacySweepTest`: copy `TriagePrivacySweepTest` shape — `PostgresContainerTest` base + Logback `ListAppender` + `SensitiveMarkerScrubFilter`; seeded tenant has sender_email `audit-sentinel-05C-01@example.com`; call `summarize(tenantId, TimeWindow.endingAt(Instant.now(), Duration.ofDays(7)))`; assert that across all captured log lines NONE contain the sentinel string. The captured response itself IS allowed to contain the sender (owner-visible by D-25).
    - Modulith `package-info.java` declares the module with `allowedDependencies = {"triage", "gmail", "shared.persistence", "shared.lang"}` and exposes the service via the default public package (or `@NamedInterface("api")` on `projection` sub-package) so `core.notification` (Plan 03) can import — Plan 03's notification module declares `"analytics"` as an allowedDependency to complete the link (review M1).
  </behavior>
  <action>Create `core.analytics` Modulith module mirroring `core.triage.projection` shape — read `AuditLogQueryService` carefully for the constructor + `JdbcTemplate` + `@Transactional(readOnly = true)` + Objects.requireNonNull guard pattern. Use enterprise-readable names throughout: `analyticsSummaryQueryService` not `analyticsSvc`, `windowStartInclusive` / `windowEndExclusive` (not `start` / `end`), `appliedByActionType` not `appliedByType`. The 4 Q1–Q4 queries are verbatim from `<interfaces>` — do NOT paraphrase the SQL; the alphabetical tie-break in Q3/Q4 is load-bearing per D-22. Q1 and Q3 MUST include `AND 'INBOX' = ANY(label_ids)` (the SENT-mail exclusion per Codex MEDIUM). Q3 MUST include `AND sender_email IS NOT NULL` (the §0 graceful-skip). The SQL uses `observed_at >= ? AND observed_at < ?` form (closed-open per Codex C5 TimeWindow) — verify the EXPLAIN plan still uses the `idx_mail_message_observed_tenant_observed_at` index from Plan 01 changeset 036 (closed `>=` + `<` typically still uses a btree range scan). Constants in `TimeSavedWeights` MUST be `static final int` and named `LABEL_SECONDS`, `ARCHIVE_SECONDS`, `SAVE_DRAFT_SECONDS`. `AnalyticsSummaryQueryServiceTest` seeds two tenants A + B; A's call to `summarize` MUST never see B's rows — assert by including 5 sentinel rows for B in every table and zero in A's response. Add new case (g) for INBOX-vs-SENT filter and case (h) for closed-window boundary. `AnalyticsRepositoryContentBanTest` mirrors `LlmGatewayBoundaryTest` shape: `noClasses()...resideOutsideOfPackage("..core.analytics..").should()...` PLUS a complementary `noClasses().that().resideInAPackage("..core.analytics..").should().dependOnClassesThat().haveSimpleName("DraftEntity").orShould().dependOnClassesThat().haveSimpleName("ThreadEntity")` (or the equivalent class names — read the codebase to confirm). After Java edits, run `mcp__jetbrains__get_file_problems` on every touched file. Implements D-18 + D-22 + D-23 + D-25 + REVIEWS Codex C5 + Codex MEDIUM (INBOX filter) + OpenCode M3.</action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "TimeSavedWeightsTest" --tests "AnalyticsSummaryQueryServiceTest" --tests "AnalyticsRepositoryContentBanTest" --tests "AnalyticsPrivacySweepTest" -x checkstyleMain -x spotlessCheck</automated>
  </verify>
  <done>
    All 4 listed tests run green. `TimeSavedWeights.SAVE_DRAFT_SECONDS == 180`. `AnalyticsSummaryQueryService.summarize` returns correctly-shaped projection for the seeded fixture. ArchUnit boundary test passes (analytics service has no forbidden imports). Privacy sweep test passes (sentinel sender_email never reaches captured logs). `mcp__jetbrains__get_file_problems` on every touched Java file reports 0 errors.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: AnalyticsController (GET /api/analytics/summary) + AnalyticsSummaryResponse + AnalyticsWindow enum + MVC contract test</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/controllers/analytics/AnalyticsController.java,
    backend/api/src/main/java/com/zeromail/api/dto/analytics/AnalyticsSummaryResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/analytics/AnalyticsWindow.java,
    backend/api/src/test/java/com/zeromail/api/controllers/analytics/AnalyticsControllerContractTest.java
  </files>
  <read_first>
    backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java,
    backend/api/src/main/java/com/zeromail/api/dto/triage/AuditLogPageResponse.java,
    backend/api/src/main/java/com/zeromail/api/config/WebSecurityConfig.java (or equivalent — to confirm controller path is under authenticated paths),
    backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryQueryService.java,
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md (D-24 endpoint shape, D-25 privacy logging)
  </read_first>
  <behavior>
    - `AnalyticsWindow` enum implements `IdentifiedEnum` with 3 members `SEVEN_DAYS (id="7d", duration=Duration.ofDays(7))`, `THIRTY_DAYS (id="30d", duration=Duration.ofDays(30))`, `NINETY_DAYS (id="90d", duration=Duration.ofDays(90))`; `fromId` fail-loud
    - `AnalyticsSummaryResponse` record: `String window, long volumeObserved, long volumeApplied, long timeSavedSeconds, List<TopSenderResponse> topSenders, List<RuleHitResponse> ruleHits`; with nested records `TopSenderResponse(String senderEmail, long count)` and `RuleHitResponse(String ruleName, long decisions, long applied, long reverted)`; static `from(AnalyticsSummaryProjection projection, AnalyticsWindow window)` factory
    - **REVIEW FIX (Codex C5 + OpenCode L1)** — `AnalyticsController`: `@RestController` `@RequestMapping("/api/analytics")`; single endpoint `@GetMapping("/summary")` with parameter `@RequestParam(value="window", required=false) String rawWindow`. Body normalizes: `String windowId = (rawWindow == null || rawWindow.isBlank()) ? "7d" : rawWindow.trim();` then `AnalyticsWindow window = AnalyticsWindow.fromId(windowId);` (catches `NoSuchElementException` → 400). Then `UUID tenantId = TenantContext.currentOrThrow();` (controller is the ONLY place TenantContext is read for this flow), then `TimeWindow timeWindow = TimeWindow.endingAt(Instant.now(), window.duration());` and `service.summarize(tenantId, timeWindow);`, then return `AnalyticsSummaryResponse.from(projection, window)`.
    - Privacy logging: one structured log line `event=analytics_summary_requested tenantId={} window={}` only — NO sender_email, NO row counts, NO window timestamps (counts and timestamps both leak no value but counts go in response, timestamps are not user-visible — keep logs minimal)
    - `AnalyticsControllerContractTest`: `@WebMvcTest(AnalyticsController.class)` + MockMvc + `@MockitoBean` `AnalyticsSummaryQueryService`; assert (a) `GET /api/analytics/summary` (no window param) returns 200 with `window: "7d"`, (b) `GET /api/analytics/summary?window=30d` returns 200 with `window: "30d"`, (c) `GET /api/analytics/summary?window=bogus` returns 400 (d) **NEW (OpenCode L1) — `GET /api/analytics/summary?window=` (empty value) returns 200 with `window: "7d"` (treated as missing per the controller's blank-normalization)**, (e) **NEW (OpenCode L1) — `GET /api/analytics/summary?window=%20%20` (whitespace only) returns 200 with `window: "7d"` (trim then blank-check)**, (f) response JSON shape matches the record fields exactly (jsonPath asserts on volumeObserved, timeSavedSeconds, topSenders[0].senderEmail, ruleHits[0].applied), (g) unauthenticated request returns 401, **(h) — NEW — argument captor asserts the mocked `summarize(...)` call received an explicit `UUID tenantId` parameter and a `TimeWindow` whose `endExclusive` is approximately `Instant.now()` and `startInclusive == endExclusive - window.duration()` (proves Codex C5 + M3 wiring)**
    - OpenAPI: the controller MUST appear in `/v3/api-docs` — verified manually post-build (Plan 04 depends on this for typed-client generation)
  </behavior>
  <action>Read `TriageAuditController` to confirm the auth pattern + the `TenantContext.currentOrThrow()` extraction location + the response-DTO `from(...)` factory convention. `AnalyticsController` is a thin shim — NO business logic, NO direct repo injection (Convention 1). **REVIEW FIX (Codex C5 + OpenCode M3)**: controller is the SOLE caller of `TenantContext.currentOrThrow()` for this endpoint; the service signature is `summarize(UUID tenantId, TimeWindow window)` — never reads `TenantContext` itself. Controller builds the `TimeWindow` via `TimeWindow.endingAt(Instant.now(), window.duration())` (closed `[now-duration, now)` interval). **REVIEW FIX (OpenCode L1)**: parameter is `@RequestParam(value="window", required=false) String rawWindow`; controller normalizes `(rawWindow == null || rawWindow.isBlank()) ? "7d" : rawWindow.trim()` BEFORE calling `AnalyticsWindow.fromId(...)`. This guarantees `?window=` and `?window=%20` both fall through to the 7d default cleanly instead of producing an ambiguous 200-with-fallback vs 400. `AnalyticsWindow.fromId` is the validation gate for non-empty non-known values; controller wraps the `NoSuchElementException` with `@ExceptionHandler` returning 400 (verify whether the existing project has a global `@ControllerAdvice` that already handles `NoSuchElementException` → 400; if it does, reuse; if not, the controller-local handler is sufficient). Privacy log line uses the project's structured logging idiom exactly — NO `sender_email`, NO body, NO row counts in logs. `AnalyticsControllerContractTest` uses `@WebMvcTest` path (sufficient for window-validation + shape + argument-captor assertions); if `TenantContext` ScopedValue requires `RestClient + LocalServerPort` per the documented STATE.md decision, use that for the auth case (g) only. After Java edits, run `mcp__jetbrains__get_file_problems` on every touched file. Implements D-24 + D-25 + REVIEWS Codex C5 + OpenCode M3 + OpenCode L1.</action>
  <verify>
    <automated>./gradlew :backend:api:test --tests "AnalyticsControllerContractTest" -x checkstyleMain -x spotlessCheck</automated>
  </verify>
  <done>
    `AnalyticsControllerContractTest` runs green (5 cases above). `AnalyticsController` + `AnalyticsSummaryResponse` + `AnalyticsWindow` compile and pass `./gradlew :backend:api:check`. Running `./gradlew :backend:api:bootRun` briefly then `curl http://localhost:8080/v3/api-docs | jq '.paths["/api/analytics/summary"]'` shows the path exists with the documented query parameter + 200 + 400 + 401 responses. `mcp__jetbrains__get_file_problems` reports 0 errors on touched files.
  </done>
</task>

</tasks>

<threat_model>

## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| client → `/api/analytics/summary` | Untrusted `window` query param crosses here — must be validated against the closed enum {7d, 30d, 90d} |
| `AnalyticsController` → `AnalyticsSummaryQueryService` | TenantContext crosses here; all downstream queries MUST be tenant-scoped |
| `AnalyticsSummaryQueryService` → Postgres | JDBC must use parameterized queries (no string concat); every WHERE includes tenant_id = ? as the first param |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05C-04 | Information disclosure (cross-tenant leak) | `AnalyticsSummaryQueryService` 4 JDBC queries | mitigate | Every query's first JDBC param is `TenantContext.currentOrThrow()`; tested in `AnalyticsSummaryQueryServiceTest` by seeding tenant B and asserting tenant A's response contains zero of B's rows. Existing `TenantIsolationArchTests` (project-wide) covers JDBC WHERE pattern compliance |
| T-05C-05 | Tampering (input validation) | `window` query param | mitigate | `AnalyticsWindow.fromId` is a closed-enum lookup with fail-loud `NoSuchElementException`; controller wraps to 400; test case in `AnalyticsControllerContractTest` for `?window=bogus` |
| T-05C-06 | Information disclosure (logs) | Controller + service logging | mitigate | Structured `event=` + `tenantId={}` only; NO sender_email or row counts in logs; `AnalyticsPrivacySweepTest` asserts sentinel sender never reaches log lines |
| T-05C-07 | Tampering (SQL injection) | 4 JDBC queries | mitigate | All queries use `JdbcTemplate.queryForObject(sql, args)` / `query(sql, args, rowMapper)` parameterized form — no string concatenation; window comes from a closed enum, not user input; tenant id is a UUID from session |

</threat_model>

<verification>
- `./gradlew :backend:core:test --tests "TimeSavedWeightsTest" --tests "AnalyticsSummaryQueryServiceTest" --tests "AnalyticsRepositoryContentBanTest" --tests "AnalyticsPrivacySweepTest"` exits 0
- `./gradlew :backend:api:test --tests "AnalyticsControllerContractTest"` exits 0
- `./gradlew :backend:core:check :backend:api:check` BUILD SUCCESSFUL
- `mcp__jetbrains__get_file_problems` on every touched Java file reports 0 errors
- Manual: `./gradlew :backend:api:bootRun` then `curl http://localhost:8080/v3/api-docs` shows `/api/analytics/summary` registered with the documented schema
</verification>

<success_criteria>
- Q1, Q2, Q3, Q4 produce the expected counts on a seeded fixture
- Q1 + Q3 exclude rows whose `label_ids` lacks `'INBOX'` (Codex MEDIUM INBOX filter) — verified by `AnalyticsSummaryQueryServiceTest` case (g)
- Service signature is `summarize(UUID tenantId, TimeWindow window)` — NOT `Duration`, NOT TenantContext-internal (Codex C5 + OpenCode M3/S5)
- Closed-window semantics: `[start, end)` — `observed_at == endExclusive` is excluded (case h)
- Time-saved formula `applied_label*10 + applied_archive*30 + applied_save_draft*180` (seconds) verified by `TimeSavedWeightsTest` + `AnalyticsSummaryQueryServiceTest`
- Reverted rows are excluded from the time-saved aggregation
- Q3 skips NULL sender_email rows (§0 graceful-skip)
- Top-3 ties resolve alphabetically on the secondary key (sender_email or rule_name_snapshot) — fixture-stable
- Endpoint returns 400 on invalid window, 401 unauthenticated, 200 with typed shape for valid authenticated request
- Empty-string `?window=` and whitespace-only `?window=%20` fall through to default 7d (200) — NOT 400 (OpenCode L1)
- No sender_email or row content in server logs (privacy sweep green)
- Cross-tenant isolation verified by seeded test
- ArchUnit content-ban prevents future code from joining body/prompt/completion columns into analytics
</success_criteria>

<output>
After completion, create `.planning/phases/05C-user-surface-analytics-daily-digest/05C-02-SUMMARY.md` capturing:
- Whether `core.analytics` ended up as a new Modulith module or was inlined into `core.triage.projection`
- Exact SQL used for Q1–Q4 (final, after any tuning vs the documented form)
- Indexes verified via `EXPLAIN ANALYZE` on the seeded fixture
- Any deviation in the global `NoSuchElementException → 400` exception handling
- Tests' runtime and assertions made
</output>
