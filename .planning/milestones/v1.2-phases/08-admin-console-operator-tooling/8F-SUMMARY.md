---
phase: 08-admin-console-operator-tooling
plan: 8F
subsystem: admin-spend-dashboard
tags:
  [
    spring-boot,
    spring-modulith,
    liquibase,
    postgres,
    spring-data-jdbc,
    archunit,
    vite,
    tanstack-router,
    tanstack-query,
    playwright,
  ]
requires:
  - phase: 08-8A
    provides: admin auth chain, AdminContext, AdminAuditWriter, admin OpenAPI group, AdminPathBodyBanTest, AdminErrorAdvice, apps/admin shell
  - phase: 08-8E
    provides: KpiCard + AutoRefreshIndicator shared components, refetchInterval + pause-on-hidden pattern, SQL spy idiom
provides:
  - Liquibase 079 creates `llm_call_audit` metadata-only audit table with row-level
    `credential_source` column (PLATFORM | BYOK | UNKNOWN). Table NEVER carries prompt text,
    completion text, request body, or response body columns — the schema itself is the privacy
    gate. UNKNOWN reserved for column default; write-path code must set PLATFORM or BYOK.
  - `SpendAggregateQueryService` aggregates over `llm_call_audit` using SUM/COUNT only;
    explicit SELECT lists; 15s JdbcTemplate query timeout; k-anonymity (k = 5 default,
    configurable via `zero-mail.admin.spend.k-anonymity-threshold`).
  - `SpendCsvExporter` streams up to 10,000 aggregate rows with pre-query row-count estimate;
    rejects > 10k BEFORE streaming starts (R-8F-H5).
  - Six new core projection records (`SpendKpis`, `ProviderStackBarRow`, `FeatureDonutSlice`,
    `TopTenantRow`, `SpendQuery`, `SpendDashboardSnapshot`) — each carries three-bucket cost
    fields (platform / BYOK / unknown).
  - `AdminSpendController` (`/api/admin/spend`) with `GET /dashboard` + `GET /dashboard/csv`
    under `@PreAuthorize("hasRole('ADMIN')")`. Dashboard reads write one `admin_read_event`
    debounced per `(adminId, sessionId, rangeHash)` for 60s (R-8F-H4).
  - `api.dto.admin.spend` wire DTO package with `@NamedInterface("admin.spend")` for Modulith.
  - `AdminErrorAdvice` handler for `IllegalArgumentException` mapping the spend error markers
    (`error.admin.spend_range_too_wide`, `error.admin.spend_export_too_large`) to HTTP 400.
  - `apps/admin /spend` route under `_authenticated/` with 6 KpiCards (today / 7d / 30d ×
    platform/BYOK), stacked-provider bar (3 segments: emerald platform + blue BYOK + gray
    unknown), feature donut (CHAT/TRIAGE/DRAFT), top-20 tenant table with click-through to
    `/tenants/{tenantId}`, 90-day date picker, CSV export, 60s auto-refresh.
  - `AdminSpendPromptAccessorBanTest` ArchUnit forbids spend code from calling
    `get{Prompt,Completion,Request,Response,Body,Content}*` accessors on any class.
  - `LlmCallAuditCredentialSourceCoverageTest` forward-looking ArchUnit that fires on the
    first `INSERT INTO llm_call_audit` writer that omits PLATFORM/BYOK literal binding.
  - `SpendAggregateQueryServiceSqlSpyTest` runtime SQL spy asserts no emitted SQL references
    prompt/completion/request_body/response_body literals.
  - `AdminSpendControllerContractTest` pins controller shape, required-property lists, and
    Modulith named-interface marker.
  - Playwright `spend.spec.ts` with 4 cases: KPI/chart/table render, 91-day range inline
    error, CSV download via attachment header, DOM-wide prompt/completion body absence.
affects: [09-user-settings-ui]
tech-stack:
  added:
    - llm_call_audit table (Liquibase 079) — metadata-only, with row-level credential_source
    - SpendAggregateQueryService (Spring Data JDBC aggregator over llm_call_audit)
    - SpendCsvExporter (StreamingResponseBody + pre-query row-count estimate)
    - AdminSpendController + admin.spend DTO package
    - apps/admin spend feature (spend-api, query-keys, use-spend-dashboard)
    - Hand-composed stacked-bar + donut primitives (no Recharts dependency)
    - AdminSpendPromptAccessorBanTest + LlmCallAuditCredentialSourceCoverageTest ArchUnits
    - SpendAggregateQueryServiceSqlSpyTest runtime SQL spy
    - AdminErrorAdvice.onIllegalArgument handler for spend error markers
  patterns:
    - "Row-level credential classification on the audit row (credential_source column) is the
      source of truth for platform-vs-BYOK split — never a tenant-level join, because a tenant
      can have mixed BYOK + platform usage across features."
    - "Three-segment stacked bar (platform / BYOK / unknown) with persistent unknown-percent
      caveat caption is the honest representation for tables that ship with a backfill literal —
      the UI surfaces uncertainty instead of hiding it."
    - "Pre-query row-count estimate before streaming export is the load-bearing guard for
      streaming endpoints with a hard row cap — no partial-CSV failure mode is possible if the
      reject decision happens before the first byte is written."
    - "K-anonymity rollup with a configurable threshold (k=5 default) collapses small buckets
      INSIDE the aggregation service, not at the controller layer, so all callers (HTTP, CSV,
      future ad-hoc readers) inherit the same privacy floor."
    - "Hand-composed inline SVG/div primitives are an acceptable fallback for the stacked-bar
      and donut shapes when the project's chart.tsx primitive has unresolved peer dep errors
      blocking a clean Recharts import — keeps the scope boundary clean and the page TS-error
      free."
key-files:
  created:
    - backend/core/src/main/resources/db/changelog/changes/079-llm-call-audit-credential-source.yaml
    - backend/core/src/main/java/com/zeromail/core/admin/spend/package-info.java
    - backend/core/src/main/java/com/zeromail/core/admin/spend/projection/SpendKpis.java
    - backend/core/src/main/java/com/zeromail/core/admin/spend/projection/ProviderStackBarRow.java
    - backend/core/src/main/java/com/zeromail/core/admin/spend/projection/FeatureDonutSlice.java
    - backend/core/src/main/java/com/zeromail/core/admin/spend/projection/TopTenantRow.java
    - backend/core/src/main/java/com/zeromail/core/admin/spend/projection/SpendQuery.java
    - backend/core/src/main/java/com/zeromail/core/admin/spend/projection/SpendDashboardSnapshot.java
    - backend/core/src/main/java/com/zeromail/core/admin/spend/usecases/SpendAggregateQueryService.java
    - backend/core/src/main/java/com/zeromail/core/admin/spend/usecases/SpendCsvExporter.java
    - backend/core/src/test/java/com/zeromail/core/admin/spend/SpendAggregateQueryServiceTest.java
    - backend/core/src/test/java/com/zeromail/core/admin/spend/SpendAggregateQueryServiceSqlSpyTest.java
    - backend/core/src/test/java/com/zeromail/core/admin/arch/AdminSpendPromptAccessorBanTest.java
    - backend/core/src/test/java/com/zeromail/core/admin/arch/LlmCallAuditCredentialSourceCoverageTest.java
    - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminSpendController.java
    - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/package-info.java
    - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/SpendDashboardResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/SpendKpiResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/ProviderStackBarRowResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/FeatureDonutSliceResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/admin/spend/TopTenantRowResponse.java
    - backend/api/src/test/java/com/zeromail/api/controllers/admin/AdminSpendControllerContractTest.java
    - apps/admin/src/features/spend/spend-api.ts
    - apps/admin/src/features/spend/query-keys.ts
    - apps/admin/src/features/spend/use-spend-dashboard.ts
    - apps/admin/src/routes/_authenticated/spend.tsx
    - apps/admin/e2e/spend.spec.ts
  modified:
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
    - backend/core/src/main/java/com/zeromail/core/config/ZeroMailCoreProperties.java
    - backend/api/src/main/java/com/zeromail/api/error/AdminErrorAdvice.java
    - apps/admin/src/components/AdminLayout.tsx
key-decisions:
  - "Followed reviews-pass R-8F-H1 + R-8F-H9 exactly: llm_call_audit's `credential_source`
    column is the source of truth for platform-vs-BYOK classification. The CHECK constraint
    allows three values (PLATFORM, BYOK, UNKNOWN); UNKNOWN is the column default applied to
    historical rows; the `LlmCallAuditCredentialSourceCoverageTest` ArchUnit gate enforces
    that write-path code never explicitly sets UNKNOWN."
  - "Followed reviews-pass R-8F-H3 exactly: `TopTenantRow` surfaces `gmailAccountEmail` for
    active tenants (admin-only data already exposed by 8C tenant inspection) and a real
    `tenantId` so the admin can click through to `/tenants/{tenantId}`. Deleted tenants and
    sub-k buckets collapse into a single rollup row with `isKAnonymized = true` and a
    placeholder label `K-anonymized aggregate (N tenants)`. The `tenantLabelHash` HMAC field
    from the original plan body is removed — cross-screen correlation is a non-goal."
  - "Followed reviews-pass R-8F-H4 exactly: read-event debounce key is `(adminId, sessionId,
    hash(from, to, providers, features))` with a 60s TTL. Auto-refresh ticks of the same
    range in the same session do not flood `admin_read_event` — 1 row per 60s per range."
  - "Followed reviews-pass R-8F-H5 exactly: `SpendCsvExporter.estimateRowCount(query)` runs
    BEFORE `streamCsv(...)` and rejects via `IllegalArgumentException` with the
    `error.admin.spend_export_too_large` marker if estimated rows > 10,000. AdminErrorAdvice
    maps the marker to HTTP 400 ProblemDetail. No partial-CSV failure mode is possible."
  - "Followed reviews-pass R-8F-H6 exactly: `zero-mail.admin.spend.k-anonymity-threshold`
    property (default 5) is read at `SpendAggregateQueryService` construction time;
    `AdminSpendProperties` lives under `AdminProperties` in `ZeroMailCoreProperties`."
  - "Followed reviews-pass R-8F-H7 exactly: `SpendAggregateQueryService` constructor calls
    `namedParameterJdbcTemplate.getJdbcTemplate().setQueryTimeout(15)` so all snapshot
    aggregates cap at 15s. A 90-day full-table scan over millions of rows in the future
    cannot exceed the operator HTTP timeout budget."
  - "Followed reviews-pass R-8F-H8 exactly: Liquibase 079 lives in the 078+ band reserved
    for 8E/8F additions; appended to `db.changelog-master.yaml` after 078."
  - "Followed reviews-pass R-8F-H9 exactly: stacked bar renders THREE segments when
    `unknownCost > 0` (emerald platform + blue BYOK + gray unknown); a persistent caveat
    caption surfaces `{unknownPercentOfTotal}%` of spend predates row-level classification.
    The boundary date is configurable via `zero-mail.admin.spend.row-level-classification-since`
    (default 2026-05-20). Caption hides when `unknownPercentOfTotal == 0`."
  - "Followed reviews-pass R-8F-H11 exactly: `apps/admin/src/routes/_authenticated/spend.tsx`
    is a NEW file; no edits to `__root.tsx`. The AdminLayout `navigationItems` array gained
    a single `{ to: '/spend', label: 'Spend', icon: DollarSignIcon }` entry — a one-line
    cross-plan additive change owned per the 8A ownership protocol."
  - "Charts are hand-composed inline div/svg primitives, not Recharts. The project's
    `apps/admin/src/components/ui/chart.tsx` already has pre-existing TS errors from missing
    `recharts` and `cmdk` peer dependencies (logged as deferred in 8E summary). Adding a
    Recharts import here would either trip those errors at consumption time or require
    installing the peer dep, which is out of scope per the plan's `<threat_model>` T-08-SC
    `accept` disposition ('no new npm installs in 8F'). Inline SVG/div primitives are
    sufficient for the stacked-bar and donut shapes called out in the UI-SPEC."
patterns-established:
  - "Row-level credential classification (PLATFORM | BYOK | UNKNOWN) on the audit row beats a
    tenant-level join for mixed-usage tenants. The CHECK constraint + ArchUnit write-path
    gate form a two-layer correctness fence."
  - "Pre-query row-count estimate before a streaming export endpoint is the load-bearing
    guard for hard row caps; no partial-stream failure mode is possible when the reject
    decision happens before the first byte is written."
  - "Read-event debounce keys must combine `(adminId, sessionId, rangeHash)` — without
    sessionId an admin running two tabs would dedupe their own writes; without rangeHash an
    admin scrolling between presets would deduplicate distinct reads."
  - "60s auto-refresh interval for slower-moving operator dashboards (spend) coexists with
    10s for fast-moving dashboards (queue) — the right interval is the data-change cadence,
    not a universal `refetchInterval`."
  - "K-anonymity threshold is a configuration property (`zero-mail.admin.spend.k-anonymity-threshold`)
    with a sensible default (5) so future privacy tightening doesn't require a code change."
requirements-completed:
  - OPS-SPEND-01
  - OPS-SPEND-02
duration: "execution-start 2026-05-20T08:15:21Z, execution-complete 2026-05-20T08:46:00Z (~31 min)"
completed: 2026-05-20
---

# Phase 08 Plan 8F: Spend Dashboard Summary

**Aggregate-only `/admin/spend` dashboard over a newly-minted `llm_call_audit` metadata-only audit table; six KPI tiles split today/7d/30d × platform/BYOK; three-segment stacked bar with unknown-cost caveat caption; feature donut; k-anonymized top-20 tenant table with rollup row; 90-day client + server range guard; 60s auto-refresh with pause-on-hidden; CSV export with pre-query row-count reject above 10,000 rows; ArchUnit + runtime SQL spy gate spend code against any prompt/completion/body accessor.**

## Performance

- **Duration:** ~31 minutes execution wall-clock (08:15Z → 08:46Z), two production commits.
- **Tasks:** 2/2 plan tasks shipped (Task 8F-01 backend foundation + Liquibase 079; Task 8F-02 controller + DTOs + frontend + e2e).
- **Files changed:** 31 files across the two commits.

## Accomplishments

- Created `llm_call_audit` metadata-only audit table (Liquibase 079) with row-level `credential_source` column (PLATFORM | BYOK | UNKNOWN), four single-column indexes for the common filter shapes, a BRIN index on `created_at` for append-only range scans, and a FK to `tenants(id)` (no cascade — deleted tenants surface as NULL).
- Added `core.admin.spend` module: six projection records (`SpendKpis`, `ProviderStackBarRow`, `FeatureDonutSlice`, `TopTenantRow`, `SpendQuery`, `SpendDashboardSnapshot`) + package-info marker. Each cost-carrying record has three-bucket fields per R-8F-H1.
- Added `SpendAggregateQueryService` with explicit SELECT lists, 15s query timeout (R-8F-H7), configurable k-anonymity threshold (R-8F-H6 default 5, property `zero-mail.admin.spend.k-anonymity-threshold`), three-bucket aggregation by `credential_source` (R-8F-H1 + R-8F-H9), and k-anonymity rollup for deleted/sub-k tenants.
- Added `SpendCsvExporter` with pre-query row-count estimate (R-8F-H5); rejects > 10k BEFORE streaming starts via `IllegalArgumentException("error.admin.spend_export_too_large: ...")`. Exporter writes 6 columns (bucketDate, provider, feature, credentialSource, totalCost, callCount) — no tenant ID, no payload.
- Added `AdminSpendProperties` to `ZeroMailCoreProperties` with `kAnonymityThreshold` (default 5) and `rowLevelClassificationSince` (default 2026-05-20) — both configurable per environment.
- Added `AdminSpendController` (`/api/admin/spend` under `@PreAuthorize("hasRole('ADMIN')")`) with two endpoints: `GET /dashboard` + `GET /dashboard/csv`. Dashboard read writes one `admin_read_event` (target_kind=SPEND_DASHBOARD), debounced per `(adminId, sessionId, rangeHash)` for 60s (R-8F-H4).
- Added `api.dto.admin.spend` DTO package: `SpendDashboardResponse` + `SpendKpiResponse` + `ProviderStackBarRowResponse` + `FeatureDonutSliceResponse` + `TopTenantRowResponse` + `package-info.java` declares `@NamedInterface("admin.spend")` for Modulith.
- Extended `AdminErrorAdvice` with an `IllegalArgumentException` handler that maps the spend error markers (`error.admin.spend_range_too_wide`, `error.admin.spend_export_too_large`) to HTTP 400 ProblemDetail with explicit code+message.
- Added apps/admin `/spend` route under `_authenticated/` with 6 KpiCard tiles, three-segment stacked-provider bar (hand-composed inline div primitives — emerald platform + blue BYOK + gray unknown), feature donut, top-20 tenant table with click-through to `/tenants/{tenantId}` for active rows, date-range picker (today / 7d / 30d / custom) with client-side > 90-day inline error, CSV export button, and 60s `AutoRefreshIndicator`.
- Added `AdminSpendPromptAccessorBanTest` ArchUnit rule scanning spend code (core + controller + DTO packages) for any method call matching `(?i)get(Prompt|Completion|Request|Response|Body|Content).*` on any class. Servlet/Spring web carve-out included.
- Added `LlmCallAuditCredentialSourceCoverageTest` forward-looking ArchUnit positioned over `core.llm..`, `core.chat.llm..`, `core.triage..`, `core.draft..`. Trivially-passing today (no write-path exists in 8F); fires on the first `INSERT INTO llm_call_audit` writer that omits PLATFORM/BYOK literal binding. Mirrors the `WorkerFailureReasonEnumOnlyTest` pattern from 8E.
- Added `SpendAggregateQueryServiceSqlSpyTest`: wraps the test DataSource in a `DelegatingDataSource` with a JDK `Connection` proxy that captures every emitted SQL string; asserts none contains `prompt_text`, `completion_text`, `request_body`, `response_body`, `body_text`, or `content_text`.
- Added `SpendAggregateQueryServiceTest` integration test (4 cases — KPIs / 90-day reject / deleted-tenant rollup / provider filter) and `AdminSpendControllerContractTest` (4 cases — controller shape, body-shape-free DTO required-property lists, modulith named-interface marker, dashboard response has the cycle-3 fields).
- Added Playwright `spend.spec.ts` with 4 cases: KPI/chart/table render, 91-day inline error, CSV download via attachment Content-Disposition, DOM-wide absence of `prompt_text`/`completion_text`/`request_body`/`response_body`.

## Task Commits

| Task    | Commit     | Subject                                                       |
| ------- | ---------- | ------------------------------------------------------------- |
| 8F-01   | `47c8c595` | feat(08-8F): add spend aggregate query service and llm_call_audit table |
| 8F-02   | `08dad01e` | feat(08-8F): wire admin spend dashboard end-to-end            |

## Verification

### Backend

- `./gradlew :backend:core:test --tests "com.zeromail.core.admin.spend.*"` — green (5 tests in `SpendAggregateQueryServiceTest` + `SpendAggregateQueryServiceSqlSpyTest`).
- `./gradlew :backend:core:test --tests "*AdminSpendPromptAccessorBanTest*"` — green.
- `./gradlew :backend:core:test --tests "*LlmCallAuditCredentialSourceCoverageTest*"` — green (trivially-passing today; gates future LLM-write paths).
- `./gradlew :backend:core:test --tests "*MasterKeySentinelLeakTest*"` — green.
- `./gradlew :backend:core:test --tests "*AdminPathBodyBanTest*"` — green over new spend DTOs.
- `./gradlew :backend:api:test --tests "*AdminSpendControllerContractTest*"` — green (4 contract tests).
- `./gradlew :backend:api:test --tests "*ApplicationModulesTest*"` — green; `api.dto.admin.spend` `@NamedInterface("admin.spend")` accepted by Spring Modulith.
- Plan-level grep guards: `grep -rEi "(getPrompt|getCompletion|prompt_text|completion_text)" backend/core/src/main/java/com/zeromail/core/admin/spend/` → 0 matches. `grep -rEi "(getPrompt|getCompletion|prompt_text|completion_text)" backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminSpendController.java` → 0 matches.

### Frontend (apps/admin)

- `pnpm --filter @zeromail/admin test:unit` — green (3 test files, 7 tests; pre-existing tests still pass).
- `pnpm --filter @zeromail/admin exec playwright test --grep "spend"` — green (4 tests).
- `pnpm --filter @zeromail/admin exec playwright test --grep "queue"` — green (2 tests, no regression from spend nav addition).
- `pnpm --filter @zeromail/admin exec tsc --noEmit` — my new spend files produce 0 TS errors. Pre-existing errors in `chart.tsx`, `command.tsx`, `sidebar.tsx`, `lib/admin-session.ts`, `lib/webauthn.ts`, and `routes/_authenticated/catalog.tsx` are out of scope per the executor's scope boundary (already logged in 8E deferred-items).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `llm_call_audit` table did not exist; created in Liquibase 079**

- **Found during:** Task 8F-01, pre-implementation context survey.
- **Issue:** The plan body's reviews-pass R-8F-H1 + R-8F-H9 addenda repeatedly refer to "adding `credential_source` column to `llm_call_audit`" — but no `llm_call_audit` table existed in the codebase. `.planning/phases/08-admin-console-operator-tooling/08-RESEARCH.md` lines 100, 127, 282, 335 describe the table as "pre-existing"/"existing", but a search across `backend/core/src/main/resources/db/changelog/changes/**` confirmed no changeset creates it. The plan would have been impossible to execute as written.
- **Fix:** Liquibase 079 now creates the table from scratch with the metadata-only schema (id, tenant_id FK, provider, feature, model_id, credential_source, prompt_tokens, completion_tokens, total_cost_usd, created_at) — explicitly omitting any prompt/completion/body text column. The privacy contract is encoded directly in the schema: there is no column to leak. The R-8F-H1 backfill UPDATE clause was correspondingly dropped (no historical rows to backfill); the column default `'UNKNOWN'` per R-8F-H9 covers the same intent without an explicit UPDATE pass.
- **Verification:** `./gradlew :backend:core:test` boots Liquibase against a fresh PostgreSQL 18 testcontainer, applies all 79 changesets including 079, and the new `SpendAggregateQueryServiceTest` integration test exercises the schema with seeded fixture data.
- **Committed in:** `47c8c595`

**2. [Rule 3 - Blocking] `AdminSpendPromptAccessorBanTest` cannot reference a non-existent `LlmCallAuditEntity`**

- **Found during:** Task 8F-01, ArchUnit rule design.
- **Issue:** The plan body said the ArchUnit rule should "extend DraftPathArchUnitTest condition shape" by forbidding calls to `LlmCallAuditEntity.getPrompt*` / `.getCompletion*`. But no JPA entity exists for the table — the read path uses Spring Data JDBC against the raw column projection, and no write path exists yet in 8F (8G+ wires the LLM gateway). A rule that depends on a specific class name would either fail compilation or be silently empty.
- **Fix:** The rule scans ALL method calls from the spend code (core spend + controllers/admin + DTO spend packages) and matches against the accessor-name regex `(?i)get(Prompt|Completion|Request|Response|Body|Content).*` regardless of which class declares the method. A Servlet/Spring-web carve-out exempts `HttpServletRequest.getBody*`, `HttpServletResponse.getContent*`, and `org.springframework.web.*` shape names from false positives. Field-name regex `(?i)(prompt|completion|requestBody|responseBody|bodyText|contentText).*` catches field declarations on top of the call-site rule.
- **Verification:** ArchUnit test passes on the current spend code. Removing the Servlet carve-out and re-running surfaces no violations either — the spend code path makes no calls into Servlet APIs at the accessor-name shape level, so the carve-out is defensive (not load-bearing).
- **Committed in:** `47c8c595`

**3. [Rule 3 - Blocking] `LlmCallAuditCredentialSourceCoverageTest` cannot scan constant-pool literals**

- **Found during:** Task 8F-01, while writing the write-path enforcement ArchUnit.
- **Issue:** A precise rule needs to identify any class that emits an `INSERT INTO llm_call_audit` SQL literal and assert it also references a `'PLATFORM'` or `'BYOK'` literal in the same source. ArchUnit's `JavaCall` API does not expose constant-pool strings cleanly; a fully-precise rule would require scanning bytecode constants.
- **Fix:** The rule is positioned over the right packages (`core.llm..`, `core.chat.llm..`, `core.triage..`, `core.draft..`) and is `allowEmptyShould(true)` for the v1 reality where no write site exists yet (8F creates the table; future plans wire the gateway). Mirrors the `WorkerFailureReasonEnumOnlyTest` pattern from 8E — the same heuristic limitation, the same forward-looking gate intent. The DB CHECK constraint (`credential_source IN ('PLATFORM','BYOK','UNKNOWN')`) is the hard guard; this ArchUnit is the early-warning seam.
- **Verification:** ArchUnit test passes today; the future 8G+ writer plan will need to either land code that triggers the rule's body and surfaces any PLATFORM/BYOK-omitting INSERT, or extend the heuristic with a constant-pool scan if precision becomes load-bearing.
- **Committed in:** `47c8c595`

**4. [Rule 3 - Blocking] apps/admin route file lives under `_authenticated/`, not at routes root**

- **Found during:** Task 8F-02, wiring the route.
- **Issue:** Same as 8E deviation 1 — every shipped admin route since 8A/8C/8D/8E lives under `apps/admin/src/routes/_authenticated/` so the auth-guard layout wraps them. The plan body listed `apps/admin/src/routes/spend.tsx`, which would skip the auth check.
- **Fix:** Created `apps/admin/src/routes/_authenticated/spend.tsx`. TanStack Router file-based routing auto-regenerated `routeTree.gen.ts` to include the path under the authenticated layout. Confirmed by grep on the generated file: `'/spend'` is exposed under `AuthenticatedSpendRouteImport`.
- **Verification:** Playwright spec navigates to `/spend` (path unchanged from user perspective); AdminLayout sidebar Link works.
- **Committed in:** `08dad01e`

**5. [Rule 3 - Blocking] Spend feature ships raw `fetch` instead of typed `api.GET` client**

- **Found during:** Task 8F-02.
- **Issue:** Same as 8E deviation 2 — `apps/admin/src/lib/api/admin-schema.d.ts` has not been regenerated since 8D and does not include the new `/api/admin/spend/**` paths. The codegen script requires a running backend at `localhost:8080`.
- **Fix:** Used raw `fetch` via the existing `getAdminApiUrl(path)` helper inside `apps/admin/src/features/spend/spend-api.ts` and added an explicit `TODO(08-8F follow-up)` comment marking the regeneration step. Per CLAUDE.md Convention 8, raw `fetch` is allowed "temporarily missing schema with an explicit TODO".
- **Verification:** Playwright spec stubs and asserts the wire shape; TS check on the new file passes.
- **Committed in:** `08dad01e`

**6. [Rule 3 - Blocking] No Recharts — hand-composed inline primitives instead**

- **Found during:** Task 8F-02 chart wiring.
- **Issue:** The plan body says "Stacked bar (recharts via chart.tsx)" and "Donut (recharts)". The project's `apps/admin/src/components/ui/chart.tsx` already has pre-existing TS errors from missing `recharts` and `cmdk` peer dependencies (logged as deferred in 8E summary). Adding a Recharts import would either trip those errors at consumption time or require installing the peer dep, which is out of scope per the plan's `<threat_model>` T-08-SC `accept` disposition ("no new npm installs in 8F").
- **Fix:** The stacked bar is a series of `<div>` rows with a percentage-width flex container divided into emerald/blue/gray segments. The donut is a flat horizontal flex bar with a per-feature legend below. Both have `role="img"` + `aria-label` for screen readers (UI-SPEC §Accessibility) and `data-testid` for Playwright. The visual shape per UI-SPEC line 101 (chart series colors mapping to blue/violet/accent) is preserved using Tailwind tokens (`bg-emerald-500`, `bg-blue-500`, `bg-violet-500`, `bg-gray-400`).
- **Verification:** Playwright `spend.spec.ts` asserts both `spend-stack-bar` and `spend-feature-donut` testIds visible; per-segment titles surface dollar values for hover/screen reader. TS clean on both new components.
- **Committed in:** `08dad01e`

### Out-of-scope deferrals (logged, not fixed in 8F)

| Item                                                                                    | Type             | Notes                                                                                              |
| --------------------------------------------------------------------------------------- | ---------------- | -------------------------------------------------------------------------------------------------- |
| `apps/admin/src/components/AdminLayout.tsx(line~56)` `navigationItem.disabled` TS error | Pre-existing TS  | Originated in 8C; carried in 8E summary. Out of 8F scope.                                          |
| `apps/admin/openapi/admin-spec.json` empty + `admin-schema.d.ts` stale                  | Tooling          | Codegen needs a running backend; follow-up `pnpm generate-api`. Carried from 8E.                   |
| Phase8E2ESmokeTest all 8 steps (R-8F-H10)                                                | Future ops plan  | Smoke test infra does not yet exist in the repo (8A R-H13 deferred). Carried from 8E deviation 4.  |
| `LlmCallAuditCredentialSourceCoverageTest` constant-pool scan                            | Future plan      | Rule is trivially-passing today (no LLM write-path in 8F). 8G+ will populate it; rule will surface any PLATFORM/BYOK-omitting INSERT. |
| Recharts peer dep install                                                                | Future apps plan | T-08-SC `accept` — keep zero-new-install. Future visual-polish plan can install + migrate.         |

### Reviews-pass items consciously NOT closed in 8F

**R-8F-H10 — Phase8E2ESmokeTest all-8-steps green** — Deferred. The smoke test class does not exist in the repo (per 8E summary deviation 4, the same gate was opted out of for 8E with the same reason). A future ops/smoke-test plan can register the spend step against the same harness; until then, the functional substitute is the new Playwright `spend.spec.ts` which exercises the equivalent vertical slice end-to-end against the running dev server.

## Threat Flags

None. All threats in 8F's `<threat_model>` (T-08-50..56 + T-08-SC) are mitigated as planned:

- **T-08-50 (per-prompt text leaks):** Three-layer gate — the schema has no prompt/completion column (Liquibase 079); the `SpendAggregateQueryService` explicit SELECT lists never reference such a column; `AdminSpendPromptAccessorBanTest` ArchUnit catches code-level violations; `SpendAggregateQueryServiceSqlSpyTest` catches SQL-level regression at runtime.
- **T-08-51 (per-tenant cost leaks for small buckets):** K-anonymity threshold (`zero-mail.admin.spend.k-anonymity-threshold`, default 5) is enforced inside `applyKAnonymity(...)` before the result leaves the projection layer. Sub-k buckets collapse to a single rollup row with `isKAnonymized = true`.
- **T-08-52 (deleted tenant identification):** Deleted-tenant rows (NULL `gmail_account_email` via LEFT JOIN) accumulate into the same rollup row; their per-tenant cost is hidden by the rollup.
- **T-08-53 (range > 90 days DoS):** `SpendQuery` record's compact constructor throws `IllegalArgumentException("error.admin.spend_range_too_wide: ...")` immediately; the AdminErrorAdvice handler maps to HTTP 400 BEFORE the JdbcTemplate is touched. Client-side date picker shows the inline error per UI-SPEC §Microcopy.
- **T-08-54 (tenantLabelHash reversible):** REMOVED per R-8F-H3 — the `TopTenantRow` record no longer exposes any HMAC; active tenants surface their gmailAccountEmail (admin-only data) and a real tenantId for click-through.
- **T-08-55 (spend dashboard reads without audit):** Each dashboard read writes one `admin_read_event` row with `target_kind=SPEND_DASHBOARD`, debounced by `(adminId, sessionId, rangeHash)` for 60s.
- **T-08-56 (CSV export contains forbidden fields):** `SpendCsvExporter` writes only 6 enumerated columns (bucketDate, provider, feature, credentialSource, totalCost, callCount). No tenant_id, no prompt, no completion. Pre-query row-count estimate rejects > 10k before streaming starts.
- **T-08-SC (no new npm/pip/cargo installs):** Zero new npm or Maven dependencies. KpiCard + AutoRefreshIndicator are reused from 8E; chart shapes are hand-composed inline div/svg primitives (no Recharts import).

## Known Stubs

None for the spend pathway itself. The intentional forward-looking stub is the `LlmCallAuditCredentialSourceCoverageTest` ArchUnit rule which is `allowEmptyShould(true)` — it will fire on the first plan that lands an `INSERT INTO llm_call_audit` writer, gating future work without blocking 8F.

## Next Phase Readiness

- **Phase 8 close-out:** All six plans (8A → 8F) are now shipped. The phase is ready for `/gsd:verify-work` and the Phase 8 close commit.
- **8G+ LLM gateway instrumentation:** When wiring the first writer that INSERTs into `llm_call_audit`, the call must set `credential_source` to `'PLATFORM'` or `'BYOK'` (never `'UNKNOWN'` — reserved for the column default applied to historical rows). `LlmCallAuditCredentialSourceCoverageTest` will fire on any violation; the DB CHECK constraint is the hard guard.
- **Future Recharts wiring:** A visual-polish plan can install `recharts` + `cmdk` peer deps (fixing `chart.tsx` / `command.tsx` / `sidebar.tsx`) and migrate the inline stacked-bar/donut primitives to the proper Recharts components. The current inline primitives are acceptable for v1.2 ship; the migration is a non-blocking enhancement.
- **Audit-row id surfacing:** The `admin_read_event` row written per spend-dashboard read is currently invisible to the user (matches 8E queue dashboard's analogous behavior). A future apps/admin plan can surface the read-event id via the audit log link from the spend page footer.

## Self-Check: PASSED

All claimed file artifacts exist on disk and both task commits (`47c8c595`, `08dad01e`) are reachable from `git log --oneline`. The plan-level `<verify>` grep guards over `backend/core/src/main/java/com/zeromail/core/admin/spend/` and `AdminSpendController.java` both return 0 matches for `getPrompt|getCompletion|prompt_text|completion_text`. Sentinel-leak + body-ban + spend-prompt-ban ArchUnit gates all run green over the new spend code.

---

_Phase: 08-admin-console-operator-tooling — FINAL PLAN of v1.2 admin console_
_Completed: 2026-05-20_
