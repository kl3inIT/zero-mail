---
phase: 08-admin-console-operator-tooling
plan: 8F
type: execute
wave: 2
depends_on:
  - 08-8A
  - 08-8E
files_modified:
  - backend/core/src/main/java/com/zeromail/core/admin/spend/usecases/SpendAggregateQueryService.java
  - backend/core/src/main/java/com/zeromail/core/admin/spend/usecases/SpendCsvExporter.java
  - backend/core/src/main/java/com/zeromail/core/admin/spend/projection/SpendKpis.java
  - backend/core/src/main/java/com/zeromail/core/admin/spend/projection/ProviderStackBarRow.java
  - backend/core/src/main/java/com/zeromail/core/admin/spend/projection/FeatureDonutSlice.java
  - backend/core/src/main/java/com/zeromail/core/admin/spend/projection/TopTenantRow.java
  - backend/core/src/main/java/com/zeromail/core/admin/spend/projection/SpendQuery.java
  - backend/core/src/main/java/com/zeromail/core/admin/spend/projection/SpendDashboardSnapshot.java
  - backend/core/src/main/java/com/zeromail/core/admin/spend/package-info.java
  - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminSpendController.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/SpendDashboardResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/SpendKpiResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/ProviderStackBarRowResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/FeatureDonutSliceResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/TopTenantRowResponse.java
  - backend/core/src/test/java/com/zeromail/core/admin/arch/AdminSpendPromptAccessorBanTest.java
  - apps/admin/src/routes/spend.tsx
  - apps/admin/src/features/spend/spend-api.ts
  - apps/admin/src/features/spend/query-keys.ts
  - apps/admin/src/features/spend/use-spend-dashboard.ts
  - apps/admin/e2e/spend.spec.ts
  - backend/core/src/main/resources/db/changelog/changes/079-llm-call-audit-credential-source.yaml
  - backend/core/src/test/java/com/zeromail/core/admin/arch/LlmCallAuditCredentialSourceCoverageTest.java

autonomous: true
requirements:
  - OPS-SPEND-01
  - OPS-SPEND-02

must_haves:
  truths:
    - "Operator can view /spend dashboard aggregating llm_call_audit rows: today / 7d / 30d totals split platform-vs-BYOK on KPI cards."
    - "Stacked bar chart by provider, donut chart by feature, top-20 tenants table — all metadata-only."
    - "Date range picker max 90 days; range > 90d returns HTTP 400 error.admin.spend_range_too_wide."
    - "K-anonymity on deleted tenants: buckets with fewer than 5 entries return aggregated rollup only (no per-tenant figures); UI footer surfaces k-anonymity note per UI-SPEC line 213."
    - "No per-prompt drill-down endpoint exists; ArchUnit AdminSpendPromptAccessorBanTest forbids spend controllers/services from reading LlmCallAudit.prompt* / .completion* accessors."
    - "AdminResponseBodyBanFilter never trips on production spend response (verified end-to-end)."
    - "Auto-refresh every 60s (slower than /queue per UI-SPEC §Interaction Patterns 2)."
    - "CSV export (date-bound spend snapshot) streams aggregate rows; max 10k rows."
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/admin/spend/usecases/SpendAggregateQueryService.java"
      provides: "Aggregator over llm_call_audit; SUM/COUNT only; never SELECT prompt/completion columns; k-anonymity enforcement."
    - path: "backend/core/src/main/java/com/zeromail/core/admin/spend/projection/SpendDashboardSnapshot.java"
      provides: "Full dashboard data shape: KPI cards + stacked-bar rows + donut slices + top-20 tenants."
    - path: "backend/core/src/test/java/com/zeromail/core/admin/arch/AdminSpendPromptAccessorBanTest.java"
      provides: "ArchUnit forbids spend code from calling LlmCallAuditEntity.getPrompt* / getCompletion* accessors."
  key_links:
    - from: "AdminSpendController#dashboard"
      to: "SpendAggregateQueryService#snapshot"
      via: "aggregate-only SQL"
      pattern: "SpendAggregateQueryService"
    - from: "apps/admin/src/routes/spend.tsx"
      to: "useQuery refetchInterval 60_000"
      via: "TanStack Query slower refresh"
      pattern: "refetchInterval.*60"
---

<objective>
Deliver `/spend` platform LLM spend dashboard: today/7d/30d KPI totals split platform-vs-BYOK, stacked bar by provider, donut by feature, top-20 tenants (k-anonymized), max-90-day date picker, no per-prompt drill-down. ArchUnit gate forbids prompt/completion accessor reads.

Output: Operator gets metadata-only spend visibility with k-anonymity on deleted-tenant buckets; sentinel-leak / body-ban filters remain green throughout.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@CONVENTIONS.md
@.planning/phases/08-admin-console-operator-tooling/08-SPEC.md
@.planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md
@.planning/phases/08-admin-console-operator-tooling/08-UI-SPEC.md
@.planning/phases/08-admin-console-operator-tooling/08-PROTOTYPE.html
@.planning/phases/08-admin-console-operator-tooling/08-8A-SUMMARY.md
@.planning/phases/08-admin-console-operator-tooling/08-8E-SUMMARY.md
@backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogPage.java
</context>

<reviews_addendum_8F>
## Reviews-pass replan addendum — 2026-05-19 (Codex + OpenCode HIGHs incorporated)

### R-8F-H1 — Platform-vs-BYOK row-level classification, not tenant-level (Codex HIGH)
**Decision (critical):** The original plan splits platform-vs-BYOK by joining `byok_credential.tenant_id` — this is WRONG. A tenant with BYOK pinned for some features may still consume platform spend for OTHER features (e.g., user has BYOK Anthropic for chat but uses platform OpenRouter for triage). Tenant-level classification corrupts the dashboard. Required fix:
- **Schema:** add `credential_source VARCHAR(16) NOT NULL CHECK IN ('PLATFORM','BYOK')` column to `llm_call_audit`. Liquibase 079 (new file, added to Task 8F-01 files: `backend/core/src/main/resources/db/changelog/changes/079-llm-call-audit-credential-source.yaml`) adds the column with a backfill `<sql>UPDATE llm_call_audit SET credential_source = CASE WHEN tenant_id IN (SELECT tenant_id FROM byok_credential WHERE provider = llm_call_audit.provider) THEN 'BYOK' ELSE 'PLATFORM' END WHERE credential_source IS NULL;</sql>` (best-effort backfill on historical rows; the column is the source of truth going forward).
- **Write path:** every LLM call site (`SpringAiChatModelFactory` resolution path + any direct ChatModel callers) MUST set `credential_source` based on which key the call actually used at request time. Add `LlmCallAuditCredentialSourceCoverageTest` ArchUnit (added to Task 8F-01 files) that fails CI if any class constructing `LlmCallAuditEntity` does not set `credential_source` — scans constructor + setter call sites.
- **Read path:** `SpendAggregateQueryService` aggregates SUM(cost) GROUP BY credential_source — TWO sums per bucket (`platformCost`, `byokCost`) come directly from this column, not from a join. Acceptance criterion 8F-01 amended.

### R-8F-H2 — 8F formally depends on 8E for KpiCard + AutoRefreshIndicator (OpenCode MEDIUM→HIGH for dependency correctness)
**Decision:** Update 8F frontmatter `depends_on: [08-8A, 08-8E]`. The two shared components MUST be available before 8F renders. If 8E waveplan changes, 8F replan accordingly. (Original `depends_on: [08-8A]` is corrected.)

### R-8F-H3 — Active tenant emails surfaced; `[deleted]` reserved for deleted tenants (Codex MEDIUM)
**Decision:** `TopTenantRow` is updated:
- For ACTIVE tenants: show `gmailAccountEmail` (per OPS-SPEND-02 acceptance — "top-20 tenants" must be operationally useful; emails are admin-only data already accessible via 8C tenant inspection).
- For DELETED tenants: show literal `[deleted]` placeholder + roll up below-k-threshold counts into a single "Deleted (k aggregated)" entry per OPS-SPEND-02.
The `tenantLabelHash` HMAC field is REMOVED from `TopTenantRow` (it was over-engineered hiding; cross-screen correlation is a non-goal). Update record signature: `TopTenantRow(UUID tenantId, String gmailAccountEmailOrPlaceholder, BigDecimal totalCost, int callCount, boolean isKAnonymized)`. `tenantId` is included so the admin can click through to `/tenants/{tenantId}` (8C) directly.

### R-8F-H4 — `admin_read_event` debounce per range AND session (Codex MEDIUM)
**Decision:** `AdminSpendController.getDashboard` debounce key becomes `(adminId, sessionId, hash(from, to, providers, features))` not just `(adminId, hash)`. This prevents auto-refresh every 60s from writing 1440 read rows/day per admin × N admins. Debounce TTL = 60s; same range within 60s in same session = no audit write. Implement via Redis SETNX on key `zeromail:admin:spend:read-debounce:{sessionId}:{rangeHash}` with TTL 60s. Acceptance criterion 8F-02 amended: 100 sequential refresh ticks (over 100 minutes) of identical range write only ~100 read events, not 1440.

### R-8F-H5 — CSV row estimate pre-query (OpenCode LOW)
**Decision:** `SpendCsvExporter.streamCsv` runs a pre-query `SELECT COUNT(DISTINCT (bucket_date, provider, feature)) FROM llm_call_audit WHERE created_at BETWEEN :from AND :to`. If count > 10,000 returns HTTP 400 `error.admin.spend_export_too_large` BEFORE streaming starts. No partial-CSV failure.

### R-8F-H6 — `kAnonymityThreshold` configurable (OpenCode LOW)
**Decision:** Add property `zeromail.admin.spend.k-anonymity-threshold` (default 5) to `ZeroMailCoreProperties`. `SpendAggregateQueryService` reads from property. Default unchanged; configurable per environment for future tightening. Acceptance: setting threshold to 10 in test profile → buckets with 6-9 entries roll up.

### R-8F-H7 — Query timeout guard (OpenCode LOW)
**Decision:** `SpendAggregateQueryService.snapshot()` queries set `.queryTimeout(15, TimeUnit.SECONDS)` on `JdbcTemplate`. `llm_call_audit` could grow to millions of rows over months; 90-day range queries cap at 15s.

### R-8F-H8 — Liquibase numbering (cross-plan from 8A R-H10)
**Decision:** `079-llm-call-audit-credential-source.yaml` lives in the 078+ band reserved for 8E/8F additions. Append to db.changelog-master.yaml in numeric order.

</reviews_addendum_8F>

<tasks>

<task type="auto" tdd="true">
  <name>Task 8F-01: SpendAggregateQueryService + projection records + k-anonymity + AdminSpendPromptAccessorBan ArchUnit</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/admin/spend/usecases/SpendAggregateQueryService.java,
    backend/core/src/main/java/com/zeromail/core/admin/spend/usecases/SpendCsvExporter.java,
    backend/core/src/main/java/com/zeromail/core/admin/spend/projection/SpendKpis.java,
    backend/core/src/main/java/com/zeromail/core/admin/spend/projection/ProviderStackBarRow.java,
    backend/core/src/main/java/com/zeromail/core/admin/spend/projection/FeatureDonutSlice.java,
    backend/core/src/main/java/com/zeromail/core/admin/spend/projection/TopTenantRow.java,
    backend/core/src/main/java/com/zeromail/core/admin/spend/projection/SpendQuery.java,
    backend/core/src/main/java/com/zeromail/core/admin/spend/projection/SpendDashboardSnapshot.java,
    backend/core/src/main/java/com/zeromail/core/admin/spend/package-info.java,
    backend/core/src/test/java/com/zeromail/core/admin/arch/AdminSpendPromptAccessorBanTest.java
  </files>
  <read_first>
    backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogPage.java + AuditLogRow.java (page projection idiom),
    backend/core/src/test/java/com/zeromail/core/arch/TriageAuditRepositoryBoundaryArchTest.java (repo-confinement pattern),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C13, §C17,
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §OPS-SPEND-01/02,
    .planning/phases/08-admin-console-operator-tooling/08-UI-SPEC.md (§/spend + §Microcopy k-anonymity note),
    (existing llm_call_audit schema — verify via mcp__postgres__list_objects "llm_call_audit" to confirm columns)
  </read_first>
  <behavior>
    - SpendKpis record: (BigDecimal todayPlatformCost, BigDecimal todayByokCost, BigDecimal sevenDayPlatformCost, BigDecimal sevenDayByokCost, BigDecimal thirtyDayPlatformCost, BigDecimal thirtyDayByokCost, int todayCallCount, int sevenDayCallCount, int thirtyDayCallCount).
    - ProviderStackBarRow record: (Instant bucketDate, String provider, BigDecimal platformCost, BigDecimal byokCost, int callCount).
    - FeatureDonutSlice record: (String feature, BigDecimal totalCost, int callCount, double percentOfTotal).
    - TopTenantRow record: (UUID tenantId, String tenantLabelHash, BigDecimal totalCost, int callCount, String kAnonymityNote) — tenantLabelHash is a stable short HMAC of tenantId (so admin can correlate without exposing UUID); kAnonymityNote is set to "K-anonymized aggregate" when bucket has fewer than 5 entries.
    - SpendQuery record: (Instant from, Instant to, Optional[Set[LlmProvider]] providers, Optional[Set[Feature]] features) — from-to validated server-side: range must be 90 days or less.
    - SpendDashboardSnapshot record: (SpendKpis kpis, List[ProviderStackBarRow] stackBar, List[FeatureDonutSlice] donut, List[TopTenantRow] topTenants, Instant snapshotAt, String kAnonymityFooterNote).
    - SpendAggregateQueryService.snapshot(SpendQuery) returns SpendDashboardSnapshot:
      1. Validate from-to range LTE 90 days; throw IllegalArgumentException -> HTTP 400 error.admin.spend_range_too_wide.
      2. Aggregate over llm_call_audit using SUM(cost), COUNT(*), GROUP BY provider/feature/date_trunc/tenantId.
      3. Split platform-vs-BYOK by joining on byok_credential.tenant_id (rows where tenant has BYOK pin = BYOK; else platform).
      4. For top-tenants: rank by SUM(cost) DESC LIMIT 20; for each, count rows in same bucket — if fewer than 5 distinct tenants in the bucket, replace exact cost with "K-anonymized aggregate" label + aggregated rollup.
      5. Apply k-anonymity to deleted-tenant rows (tenantId in tombstone table — if exists — or NULL FK) by combining into single "Deleted (k aggregated)" row when fewer than 5 deleted tenants in the bucket.
    - SpendAggregateQueryService never SELECTs prompt_text / completion_text / prompt_token_text / any string column from llm_call_audit; only metadata columns (cost, tokens, provider, feature, model_id, tenant_id, created_at).
    - SpendCsvExporter.streamCsv(SpendQuery, OutputStream) streams up to 10,000 aggregate rows (bucketDate, provider, feature, platformCost, byokCost, callCount); rejects if range causes > 10k row estimate.
    - AdminSpendPromptAccessorBanTest: ArchUnit noClasses().that().resideInAnyPackage("..core.admin.spend..","..controllers.admin.spend..").should(...) — forbids calls to methods on `LlmCallAudit*` entity matching getter regex `(?i)get(Prompt|Completion|Request|Response|Body|Content).*` — extend DraftPathArchUnitTest condition shape.
  </behavior>
  <action>
    Implement per PATTERNS §C13/§C17. SpendAggregateQueryService uses Spring Data JDBC NamedParameterJdbcTemplate for SUM/COUNT aggregates — JPA is overkill. K-anonymity threshold (k=5) is hardcoded per OPS-SPEND-01 acceptance + UI-SPEC line 213 footer note. tenantLabelHash uses HMAC-SHA256 with the same KEK as admin_audit_event (already configured in ZeroMailCoreProperties from 8A) — short 8-char hex prefix for display; admin can correlate same hash across runs but cannot reverse to tenantId. Per OPS-SPEND-02 acceptance: SQL queries explicitly enumerated; integration test verifies the actual SQL emitted (capture via `@Sql` script + JdbcTemplate spy in test) — assert query string contains no "prompt" / "completion" / "request_body" tokens. AdminSpendPromptAccessorBanTest extends ArchUnit condition shape from DraftPathArchUnitTest lines 31-73 (custom ArchCondition with javaClass.getMethodCallsFromSelf() inspection). 90-day range cap throws IllegalArgumentException mapped to HTTP 400 by AdminErrorAdvice (from 8A). Privacy logging: `event=spend_snapshot fromEpoch={} toEpoch={} totalCallCount={}` — never log per-tenant cost.
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "com.zeromail.core.admin.spend.*" --tests "com.zeromail.core.admin.arch.AdminSpendPromptAccessorBanTest"</automated>
  </verify>
  <done>
    SpendDashboardSnapshot returns correct aggregates; k-anonymity applied to small buckets; range > 90d rejected; ArchUnit forbids prompt/completion accessor reads; sentinel-leak gate (from 8B) still green over spend code paths.
  </done>
  <acceptance_criteria>
    - Fixture: 100 llm_call_audit rows across 6 providers, 3 features, 50 tenants, 30-day span -> SpendDashboardSnapshot returns 6 stack-bar series + 3 donut slices + top-20 tenants.
    - SpendQuery(from=2026-01-01, to=2026-04-15) range > 90d -> service throws IllegalArgumentException; controller in 8F-02 maps to HTTP 400.
    - Top-tenants fixture with only 3 tenants in a bucket -> k-anonymity rollup row returned with kAnonymityNote="K-anonymized aggregate" + no per-tenant cost.
    - AdminSpendPromptAccessorBanTest: fixture spend service injecting `LlmCallAuditEntity.getPrompt()` makes test red; removing makes it green.
    - JdbcTemplate query spy: no captured query string contains "prompt" / "completion" / "request_body" tokens (case-insensitive).
    - MasterKeySentinelLeakTest (from 8B) still green after running spend tests.
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8F-02: AdminSpendController + DTOs + apps/admin /spend route with KpiCards + stacked bar + donut + top-20 table + 90d date picker + 60s auto-refresh + CSV export</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminSpendController.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/spend/SpendDashboardResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/spend/SpendKpiResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/spend/ProviderStackBarRowResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/spend/FeatureDonutSliceResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/spend/TopTenantRowResponse.java,
    apps/admin/src/routes/spend.tsx,
    apps/admin/src/features/spend/spend-api.ts,
    apps/admin/src/features/spend/query-keys.ts,
    apps/admin/src/features/spend/use-spend-dashboard.ts,
    apps/admin/e2e/spend.spec.ts
  </files>
  <read_first>
    backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java (controller idiom),
    apps/web/components/ui/chart.tsx + card.tsx + table.tsx + popover.tsx + button.tsx (primitives copied in 8A),
    apps/admin/src/components/KpiCard.tsx + AutoRefreshIndicator.tsx (from 8E — reused here),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C13, §C14,
    .planning/phases/08-admin-console-operator-tooling/08-UI-SPEC.md (§/spend + §Microcopy + §Interaction Patterns),
    .planning/phases/08-admin-console-operator-tooling/08-PROTOTYPE.html (spend screen visual reference)
  </read_first>
  <behavior>
    - AdminSpendController @PreAuthorize("hasRole('ADMIN')") @RequestMapping("/api/admin/spend"):
      - GET /dashboard?from=&to=&providers=&features= returns SpendDashboardResponse. Writes 1 admin_read_event row per distinct range (debounced 60s) with target_kind=SPEND_DASHBOARD.
      - GET /dashboard/csv?from=&to=&providers=&features= returns CSV stream (StreamingResponseBody, Content-Disposition `attachment; filename="spend-{from}-{to}.csv"`). Max 10k rows.
    - DTOs are records with @Schema; explicit allowlist; no body-ban field-name collisions.
    - apps/admin /spend route:
      - Top row: 6 KpiCard instances (Today platform, Today BYOK, 7d platform, 7d BYOK, 30d platform, 30d BYOK) using tabular-num.
      - Stacked bar (recharts via chart.tsx) showing daily cost by provider (6 series) across selected range.
      - Donut (recharts) showing cost split by feature (CHAT/TRIAGE/DRAFT, 3 slices).
      - Top-20 tenants table: tenant hash (mono first 8 chars) + cost + call count + k-anonymity badge if applicable.
      - Date-range popover (popover.tsx + button.tsx): presets `today`, `7d`, `30d`, custom (max 90d); client-side guard rejects > 90d before request fires.
      - AutoRefreshIndicator top-right with 60s interval (slower than /queue per UI-SPEC §Interaction Patterns 2).
      - Footer note (UI-SPEC line 213): `Per-tenant spend bucketed by 7-day k-anonymity (k≥5). Exact per-tenant cost is not exposed.`
      - Export CSV button (UI-SPEC line 148) right-aligned on filter bar; spinner during streaming; auto-downloads file.
    - TanStack Query: refetchInterval 60_000; refetchOnWindowFocus false; refetchIntervalInBackground false.
    - Per UI-SPEC §Interaction Patterns 7: filter changes reset "Updated Ns ago" counter + immediate refetch.
    - Playwright spend.spec.ts: login -> /spend -> 6 KpiCards render with mocked fixture -> stacked bar shows 6 series -> donut shows 3 slices -> top-20 table renders -> change date range to 31d-90d works -> change date range to 91d returns inline validation error before request fires -> click Export CSV streams file with proper Content-Disposition.
  </behavior>
  <action>
    Implement per PATTERNS §C13/§C14 + UI-SPEC. Controllers/DTOs straightforward. Recharts via chart.tsx primitive copied in 8A. Date-range popover composes popover + button + calendar primitives raw (no new wrapper). Per UI-SPEC §Color: chart series colors map to --blue / --violet / --primary tokens (line 101 — chart series 2-3 use blue/violet; series 1 uses accent purple). 90-day client-side guard: when picker delta > 90d show inline error `Date range maximum is 90 days. Choose a narrower window or use 7d/30d presets.` (UI-SPEC §Microcopy). CSV export: same StreamingResponseBody pattern as 8A audit CSV. Per UI-SPEC §Accessibility: charts have `aria-label` + visible legend; tabular-nums on all monetary values; KPI cards use tabular-nums per UI-SPEC §Typography. Playwright spec stubs `/api/admin/spend/dashboard` with deterministic 6-provider 3-feature fixture; export endpoint mocked to return small CSV blob.
  </action>
  <verify>
    <automated>./gradlew :backend:api:test --tests "com.zeromail.api.controllers.admin.AdminSpendController*" && pnpm --filter @zeromail/admin test:unit && pnpm --filter @zeromail/admin e2e -- --grep "spend"</automated>
  </verify>
  <done>
    KpiCards + stacked bar + donut + top-20 render under mocked data; date-range guard rejects > 90d both client + server; CSV export streams; AutoRefreshIndicator shows 60s interval; AdminPathBodyBanTest + AdminSpendPromptAccessorBanTest green over spend DTOs/services; full sentinel-leak / body-ban gates remain green end-to-end.
  </done>
  <acceptance_criteria>
    - GET /api/admin/spend/dashboard?from=2026-04-19T00:00:00Z&to=2026-05-19T00:00:00Z returns SpendDashboardResponse with kpis + stackBar + donut + topTenants; 1 admin_read_event row inserted.
    - GET /api/admin/spend/dashboard?from=2026-01-01T00:00:00Z&to=2026-05-19T00:00:00Z returns HTTP 400 with error code error.admin.spend_range_too_wide.
    - GET /api/admin/spend/dashboard/csv?from=&to= returns 200 with Content-Type text/csv + Content-Disposition attachment; CSV body has bucketDate, provider, feature, platformCost, byokCost, callCount columns.
    - Playwright spend.spec.ts: 6 KpiCards render with fixture values; date picker delta 91 days shows inline error and disables Apply button; bar+donut chart aria-labels present; Export CSV button triggers file download in headless browser.
    - Network panel inspection: no /api/admin/spend response field name matches body-ban regex.
    - AdminResponseBodyBanFilter NOT tripped on production spend response (zero ADMIN_RESPONSE_BODY_BAN_TRIPPED rows after e2e run).
  </acceptance_criteria>
</task>

</tasks>

<threat_model>

## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Admin browser to /api/admin/spend/** | Aggregate-only metadata; no per-prompt drill-down |
| backend/api to llm_call_audit | SUM/COUNT-only queries; never SELECT prompt/completion columns |
| Aggregate to admin DTO | k-anonymity threshold k>=5 applied at projection layer |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-08-50 | Information Disclosure | Per-prompt text leaks via spend endpoint | mitigate | AdminSpendPromptAccessorBanTest ArchUnit forbids prompt/completion accessor reads; SQL query spy verifies no prompt/completion columns selected |
| T-08-51 | Information Disclosure | Per-tenant cost leaks for small buckets | mitigate | k-anonymity threshold k>=5; smaller buckets returned as "K-anonymized aggregate" rollup |
| T-08-52 | Information Disclosure | Deleted tenant identification via residual spend | mitigate | Deleted-tenant rows combined into single "Deleted (k aggregated)" entry when bucket < 5 |
| T-08-53 | Denial of Service | Range > 90 days causes expensive query | mitigate | Server-side 90-day range cap + client-side guard before request fires |
| T-08-54 | Information Disclosure | tenantLabelHash reversible | mitigate | HMAC-SHA256 with KEK; not reversible without key; 8-char prefix is collision-tolerant but not enumerable |
| T-08-55 | Repudiation | Spend dashboard reads without audit | mitigate | 1 admin_read_event row per distinct range (debounced 60s); target_kind=SPEND_DASHBOARD |
| T-08-56 | Information Disclosure | CSV export contains forbidden fields | mitigate | CSV streamer enumerates allowed columns only (bucketDate, provider, feature, platformCost, byokCost, callCount); no tenant ID, no prompt content |
| T-08-SC | Tampering | No new npm/pip/cargo installs in 8F | accept | Reuses recharts via chart.tsx primitive already copied in 8A; KpiCard + AutoRefreshIndicator from 8E |

</threat_model>

<verification>

```bash
./gradlew :backend:core:test :backend:api:test --tests "*Spend*"
pnpm --filter @zeromail/admin test:unit
pnpm --filter @zeromail/admin e2e -- --grep "spend"

# Verify no prompt/completion column selects
grep -rEi "(getPrompt|getCompletion|prompt_text|completion_text)" backend/core/src/main/java/com/zeromail/core/admin/spend/  # expect 0
grep -rEi "(getPrompt|getCompletion|prompt_text|completion_text)" backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminSpendController.java  # expect 0

# Verify final sentinel-leak gate (full Phase 8 build artifacts)
./gradlew :backend:core:test --tests "*MasterKeySentinelLeakTest*"
grep -rE '(sk-[a-zA-Z0-9]{8,}|sk-ant-[a-zA-Z0-9]{8,}|AIza[a-zA-Z0-9]{8,}|sk-or-[a-zA-Z0-9]{8,})' build/reports/ build/test-results/ build/logs/ 2>/dev/null | wc -l  # expect 0

# AdminPathBodyBanTest covers spend
./gradlew :backend:core:test --tests "*AdminPathBodyBanTest*"
```

</verification>

<success_criteria>
- [ ] SpendAggregateQueryService returns metadata-only aggregates (no prompt/completion selects)
- [ ] 90-day range cap enforced server-side + client-side
- [ ] k-anonymity threshold k>=5 applied to top-tenants buckets + deleted tenants
- [ ] AdminSpendPromptAccessorBanTest green: ArchUnit forbids prompt/completion accessor reads
- [ ] /spend dashboard renders 6 KPI cards + stacked bar (provider) + donut (feature) + top-20 table
- [ ] Date-range picker max 90d with client-side guard
- [ ] AutoRefreshIndicator at 60s interval
- [ ] Footer k-anonymity note rendered per UI-SPEC line 213
- [ ] CSV export streams aggregate rows, max 10k
- [ ] Playwright spend spec green
- [ ] MasterKeySentinelLeakTest still green after full Phase 8 build
- [ ] AdminResponseBodyBanFilter never trips on production spend response
- [ ] (reviews-pass) Platform-vs-BYOK classified by `llm_call_audit.credential_source` column (row-level), NOT tenant-level join
- [ ] (reviews-pass) Liquibase 079 adds `credential_source VARCHAR(16) NOT NULL` + backfill; `LlmCallAuditCredentialSourceCoverageTest` ArchUnit forces every audit write to set it
- [ ] (reviews-pass) `depends_on` includes `08-8E` (KpiCard + AutoRefreshIndicator dependency)
- [ ] (reviews-pass) `TopTenantRow` surfaces `gmailAccountEmail` for active tenants + `[deleted]` placeholder for deleted; `tenantLabelHash` HMAC field removed
- [ ] (reviews-pass) Read-audit debounced per `(sessionId, rangeHash)` with 60s TTL — auto-refresh ticks do not flood admin_read_event
- [ ] (reviews-pass) CSV export estimates row count pre-query; rejects >10k BEFORE streaming starts
- [ ] (reviews-pass) `zeromail.admin.spend.k-anonymity-threshold` configurable (default 5)
- [ ] (reviews-pass) `JdbcTemplate.queryTimeout=15s` on spend aggregate queries
</success_criteria>

<output>
Create `.planning/phases/08-admin-console-operator-tooling/08-8F-SUMMARY.md` when done.
</output>
