# Phase 7: Analytics Enhancement - Context

**Gathered:** 2026-05-15
**Status:** Ready for planning

<domain>
## Phase Boundary

`apps/web` analytics screen at `/analytics` gains 5 new data dimensions — trend chart, Δ% comparison badges, action breakdown, rule precision/trust score, noise reduction, and credits panel — by extending the existing `GET /api/analytics/summary` endpoint and `AnalyticsSummaryQueryService` with new SQL queries. `TopSendersPanel` grows to top-10 with a sender/domain toggle. All new data derives strictly from `mail_message_observed`, `triage_audit`, `credit_ledger_entry`, and `credit_reservation` metadata — no email bodies, subjects, LLM prompts or completions.

Dependency: Phase 5C must be complete (endpoint `/api/analytics/summary`, 4-panel UI, and database indexes must exist).

</domain>

<decisions>
## Implementation Decisions

### Chart library
- **D-01:** Chart library = **shadcn/ui `chart` component** (Recharts-based). Install via `pnpm dlx shadcn@latest add chart`. Consistent with the project's shadcn-first convention (Card, Table, Tooltip already use shadcn primitives). Teal/Paper-warm CSS variable tokens auto-map through the shadcn theming layer. Mirrors the reference inbox-zero repo's use of Recharts.
- **D-02:** Chart type mix — **donut chart** for Action Breakdown (ANL-06: label / archive / save_draft proportion), **line chart** for Trend (ANL-04: day-by-day observed vs applied). Rationale: donut for proportional composition; line for time series.
- **D-03:** TrendPanel layout = **full-width `md:col-span-2` at the top of the analytics grid**, before the existing 4 panels. Users see trend immediately on load; consistent with dashboard convention of primary temporal chart leading.

### API evolution
- **D-04:** Strategy = **extend existing `AnalyticsSummaryResponse`**. Single `GET /api/analytics/summary?window=...` call. No new endpoint. Add new fields: `trendPoints`, `actionBreakdown`, `deltas`, `topSendersByDomain`, `creditSpend` to the response record. Frontend one HTTP call, one TanStack Query key, atomic window context. Extend `AnalyticsSummaryProjection` and `AnalyticsSummaryQueryService` with new queries.
- **D-05:** Delta % (ANL-05) = **backend computes** by running current-window queries + prior-window queries (same duration, immediately preceding). Returns `deltas: { volumeObservedDeltaPct, volumeAppliedDeltaPct, timeSavedDeltaPct }` as `Long` nullable fields (null when prior window has zero data — frontend renders "—" instead of NaN). Prior window = `[start - duration, start)`.
- **D-06:** Trend SQL = **`generate_series` + LEFT JOIN** to zero-fill days with no events. Pattern:
  ```sql
  SELECT s.day::date AS date,
         count(DISTINCT m.gmail_message_id) FILTER (WHERE m.gmail_message_id IS NOT NULL) AS observed,
         count(DISTINCT a.gmail_message_id) FILTER (WHERE a.applied_at IS NOT NULL AND a.reverted_at IS NULL) AS applied
  FROM generate_series(?, ?, interval '1 day') AS s(day)
  LEFT JOIN mail_message_observed m ON m.tenant_id = ? AND date_trunc('day', m.observed_at) = s.day
  LEFT JOIN triage_audit a ON a.tenant_id = ? AND date_trunc('day', a.applied_at) = s.day
  GROUP BY s.day ORDER BY s.day ASC
  ```
  Guarantees every day in window has a row (observed=0 / applied=0 for quiet days) — critical for chart continuity.
- **D-07:** Action Breakdown (ANL-06) = expose the existing `appliedByActionType` map (already computed in `queryAppliedByActionType`) as a new `actionBreakdown: { labelCount, archiveCount, saveDraftCount }` record in the projection. No new SQL needed — just expose what's already computed.

### Credits panel data
- **D-08:** Cost-per-action scope = **TRIAGE / DRAFT / PREVIEW grouped** (not the internal TRIAGE_PLATFORM_LLM / TRIAGE_DETERMINISTIC sub-sites). User-facing grouping: "AI Triage", "AI Draft Reply", "Rule Preview". JOIN query:
  ```sql
  SELECT cr.call_site, sum(abs(cle.amount_credits)) AS credits_spent
  FROM credit_ledger_entry cle
  JOIN credit_reservation cr
    ON cle.ref_id = cr.id::varchar
   AND cle.ref_type = 'RESERVATION'
   AND cle.kind = 'SETTLE'
  WHERE cle.tenant_id = ?
    AND cle.created_at >= ?
    AND cle.created_at < ?
    AND cr.call_site IN ('TRIAGE', 'DRAFT', 'PREVIEW')
  GROUP BY cr.call_site
  ```
- **D-09:** Projected monthly spend = **linear extrapolation**: `(totalConsumed / windowDays) × 30`. Returned as `projectedMonthlyCredits: Long`. UI shows "~X credits/month (estimated)" with a note tooltip clarifying it is a projection, not a guarantee.
- **D-10:** Module placement = extend **`AnalyticsSummaryQueryService`** with a `queryCreditSpend(tenantId, windowStart, windowEnd)` method. `core.analytics` allowed dependencies gain `billing` (for `CreditReservation` entity access). If the Modulith boundary rule makes this too invasive, planner may use JdbcTemplate directly against `credit_reservation` + `credit_ledger_entry` tables without importing billing domain types — same result, different dependency route (planner chooses).

### Top Senders expansion
- **D-11:** Top-3 → top-10: change SQL `LIMIT 3` to `LIMIT 10` in `TOP_SENDERS_SQL`.
- **D-12:** Domain grouping = **SQL-level** `SUBSTRING(sender_email FROM '@(.+)$')` GROUP BY domain. Backend returns two fields: `topSenders` (top-10 raw email, as before) + `topSenderDomains` (top-10 domain aggregates `[{domain, count}]`). Two separate queries inside the same `@Transactional(readOnly=true)` service call.
- **D-13:** Frontend toggle = **chip/tab toggle inside `TopSendersPanel`** with two states: "By Sender" and "By Domain". No URL state — local component state. Uses existing `WindowChips` design pattern (chip row with selected state) adapted as an inline toggle.

### Rule Hits enhancement
- **D-14:** Precision Rate (ANL-07) = computed **frontend-side** from existing data: `applied / decisions × 100`. No backend change. New column `Precision Rate` in the rule hits table.
- **D-15:** Trust Score badge thresholds (ANL-07) = **≥90% = green (`text-teal-600`), 70–89% = amber (`text-amber-600`), <70% = red (`text-destructive`)**. Matches the Teal brand token and existing destructive/warning token usage.
- **D-16:** When `decisions === 0`, Precision Rate = "—" (not NaN, not 0%). Trust Score badge hidden when no decisions. Consistent with `safeCount` pattern already in `RuleHitsPanel`.

### Noise Reduction panel
- **D-17:** Noise Reduction (ANL-08) = **frontend-side computation** from existing `volumeObserved` and `volumeApplied`: `(volumeApplied / volumeObserved × 100)%` + raw count `volumeApplied`. No new backend query needed. New `NoiseReductionPanel` component: big percentage number + "of {observed} emails filtered" supplementary text. Empty state when `volumeObserved === 0`.

### Small locks
- **D-18:** i18n keys for new panels follow existing `analytics.*` namespace: `analytics.trend.*`, `analytics.actionBreakdown.*`, `analytics.noiseReduction.*`, `analytics.credits.*`, plus new columns in `analytics.ruleHits.column.*`. Planner aligns with `apps/web/i18n/messages/{vi,en}.json` lock-step requirement.
- **D-19:** Privacy logging — all new backend log lines follow `event=<name> tenantId={}` format. `sender_email` stays out of server logs (owner-visible in UI panel by design per Phase 5C D-25). `call_site` field in credit query is not PII — safe to log.
- **D-20:** The credits panel shows current-balance alongside consumed credits, sourced from the existing `GET /api/billing/balance` call already made by the persistent chrome. No new billing endpoint needed for balance display — reuse TanStack Query cache.

### Claude's Discretion
- Exact package placement for new projection types (`trendPoints`, `creditSpend`) — inside `core.analytics.projection` following existing `AnalyticsSummaryProjection` pattern.
- Whether `trendPoints` uses a Java record `TrendPointProjection(LocalDate date, long observed, long applied)` or a `Map.Entry` — planner picks the cleaner option.
- Exact Liquibase changeset numbering for any new indexes (sequence continues from last 5C changeset).
- Whether the `core.analytics` Modulith `allowedDependencies` explicitly lists `billing` or the credit query uses raw JdbcTemplate against table names without importing billing domain classes (planner picks based on blast radius).
- Recharts `CartesianGrid`, `Tooltip`, `Legend` configuration details inside `TrendChart`.
- Whether `AnalyticsSummaryQueryService` fans out to 7-8 sequential queries (all inline) or spawns a private `CreditSpendQueryService` helper inside `core.analytics` (planner picks based on class size).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 5C foundation (MUST read — Phase 7 builds on top of this)
- `.planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md` — All Phase 5C implementation decisions. D-18 through D-25 especially (aggregation SQL shape, endpoint shape, privacy logging, frontend pattern).
- `.planning/phases/05C-user-surface-analytics-daily-digest/05C-SPEC.md` — Phase 5C locked requirements and acceptance criteria (defines the foundation Phase 7 extends).

### Existing implementation files
- `backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryQueryService.java` — Extend this class with new queries (D-04 to D-12).
- `backend/api/src/main/java/com/zeromail/api/dto/analytics/AnalyticsSummaryResponse.java` — Extend this record with new fields.
- `apps/web/features/analytics/components/AnalyticsPageClient.tsx` — Main orchestrator component to update with new panels.
- `backend/core/src/main/java/com/zeromail/core/billing/domain/CallSite.java` — TRIAGE/DRAFT/PREVIEW cost constants (D-08).
- `backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml` — `credit_ledger_entry` schema.
- `backend/core/src/main/resources/db/changelog/changes/015-credit-reservation.yaml` — `credit_reservation` schema with `call_site` column.

### Project conventions
- `CLAUDE.md` — Java 25, Spring Boot 4, Gradle 9, privacy constraints, backend code style (no abbreviations), `@Transactional(readOnly=true)` JdbcTemplate pattern.
- `CONVENTIONS.md` §4 (records for DTOs), §5 (privacy logging format), §7 (shadcn/ui primitive selection: `pnpm dlx shadcn@latest add <component>`), §8 (feature API/hooks/query keys layout).
- `TESTING.md` — Phase 5C privacy sweep test pattern (`Analytics*PrivacySweepTest`) MUST be extended for new query methods.

### Reference design
- Inbox Zero reference repo uses Recharts for analytics charts (confirmed via search). Our shadcn/ui chart wraps Recharts — same underlying library.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `VolumePanel`, `TimeSavedPanel`, `TopSendersPanel`, `RuleHitsPanel` — existing panel components at `apps/web/features/analytics/components/`. Extend or adapt rather than replace.
- `safeCount(value)` utility function defined in multiple panel files — consolidate into a shared `analyticsUtils.ts` if needed (planner's call).
- `useAnalyticsSummary(window)` hook at `apps/web/features/analytics/hooks/` — extend type signature when `AnalyticsSummaryResponse` gains new fields; hook itself stays the same.
- `WindowChips` component — chip design pattern reusable for the sender/domain toggle (D-13).
- `analyticsKeys` query key factory — add new key variants if a second endpoint is ever introduced.

### Established Patterns
- `@Transactional(readOnly=true)` multi-query service with sequential JdbcTemplate queries (see `AnalyticsSummaryQueryService`) — add new queries to same method or refactor into private helpers.
- `safeCount` / null-safe result handling in both backend (`count == null ? 0L : count`) and frontend (`Number.isFinite(value) ? ... : 0`).
- `Card/CardHeader/CardContent/CardDescription` shadcn layout — all new panels follow same shell.
- Privacy sweep test pattern: `Analytics*PrivacySweepTest` in `backend/core/src/test/` — extend to cover new queries.
- `from(...)` static factory on response records — `AnalyticsSummaryResponse.from(projection, window)` maps projection to DTO.

### Integration Points
- `AnalyticsPageClient.tsx` — add `TrendPanel`, `NoiseReductionPanel`, `ActionBreakdownPanel`, `CreditsPanel` imports and render them from `summaryQuery.data`.
- `AnalyticsSkeleton.tsx` — update skeleton to match new layout (add TrendPanel skeleton row).
- `AnalyticsSummaryProjection` record — add new fields; `AnalyticsSummaryQueryService.summarize()` populates them.
- `AnalyticsSummaryResponse` record — add new fields; `from()` factory maps them from projection.
- `apps/web/i18n/messages/vi.json` and `en.json` — add `analytics.trend.*`, `analytics.actionBreakdown.*`, `analytics.noiseReduction.*`, `analytics.credits.*` namespaces lock-step.

</code_context>

<specifics>
## Specific Ideas

- User referenced **Inbox Zero**, **Superhuman**, and **Shortwave** as inspiration. Inbox Zero (reference repo) is the most relevant — it uses Recharts for email analytics charts, has a per-day stats view, and the codebase directly inspired this project. Superhuman focuses on team read-receipts analytics (not relevant to v1). Shortwave analytics are functional but limited — low bar to exceed.
- shadcn/ui chart component (Recharts) was confirmed as correct library by user — consistent with reference repo pattern.
- User wants chart types chosen by data fit, not uniformity — donut for proportional composition, line for time series. This "mix by fit" principle applies to future panels too.

</specifics>

<deferred>
## Deferred Ideas

No scope creep items arose during discussion. All 6 requirements (ANL-04 through ANL-09) and the top-10/domain-grouping extension are in scope per the workstream definition.

</deferred>
