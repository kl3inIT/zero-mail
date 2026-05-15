# Phase 7: Analytics Enhancement — Discussion Log

**Date:** 2026-05-15
**Session duration:** ~30 min
**Status:** All 4 areas discussed, CONTEXT.md written

---

## Areas Covered

### Area 1: Chart library

| Question | Options | Selection | Notes |
|----------|---------|-----------|-------|
| Chart library? | shadcn/ui chart / Recharts direct / SVG manual | **shadcn/ui chart (Recharts)** | Consistent with shadcn-first convention, Inbox Zero reference repo also uses Recharts |
| Action Breakdown chart type? | Donut / Horizontal bar / Claude decides | **Mix — chart by data fit** | Donut for proportional (ANL-06), Line for time series (ANL-04) |
| TrendPanel layout? | Full-width at top / Inside VolumePanel | **Full-width (md:col-span-2) at top** | Users see trend on load, leads the dashboard |

### Area 2: API evolution

| Question | Options | Selection | Notes |
|----------|---------|-----------|-------|
| Endpoint strategy? | Extend existing / New /trend endpoint / Hybrid | **Extend AnalyticsSummaryResponse** | 1 HTTP call, atomic window, no new query key |
| Delta % computation? | Backend / Frontend (2 calls) | **Backend computes** | Returns delta % fields; null when prior window is zero data |
| Trend SQL? | generate_series + LEFT JOIN / Simple GROUP BY | **generate_series + LEFT JOIN** | Zero-fills quiet days — critical for chart continuity |

### Area 3: Credits panel

| Question | Options | Selection | Notes |
|----------|---------|-----------|-------|
| Cost breakdown granularity? | TRIAGE/DRAFT/PREVIEW grouped / All 5 CallSites / Total only | **TRIAGE/DRAFT/PREVIEW grouped** | User-facing labels: "AI Triage", "AI Draft Reply", "Rule Preview" |
| Projected spend formula? | Linear extrapolation / No projection | **Linear extrapolation** | (consumed / days) × 30, labeled "estimated" on UI |
| Module placement? | Extend analytics query service / Billing facade | **Extend AnalyticsSummaryQueryService** | Core.analytics gains billing dependency (or raw JdbcTemplate — planner picks) |

### Area 4: Top Senders + domain grouping

| Question | Options | Selection | Notes |
|----------|---------|-----------|-------|
| Domain grouping location? | SQL-level GROUP BY / Client-side | **SQL-level SUBSTRING domain** | More accurate — sees all senders, not just top-10 raw |
| Views: sender + domain? | 2 views with toggle / Domain only / Sender only | **2 views with chip toggle** | By Sender (top-10 raw) + By Domain (top-10 domain) |

---

## Competitive Research Findings

Researched Inbox Zero (reference repo), Superhuman, and Shortwave analytics.

- **Inbox Zero (reference)**: Uses Recharts + Tinybird for day-by-day analytics. Our Postgres-backed approach achieves the same shape with `generate_series`. Validated Recharts as the right library.
- **Superhuman**: Analytics focused on team read-receipts and CRM integration (acquired by Grammarly Oct 2025 ~$825M). Not directly relevant to v1 solo-user triage analytics.
- **Shortwave**: Team analytics functional but "lacks customization flexibility." Low competitive bar.

---

## Claude's Discretion Items

Planner has discretion on:
- Package placement for new projection types inside `core.analytics.projection`
- Whether `generate_series` trend query is a separate private method or inline
- Whether `core.analytics` module explicitly lists `billing` in allowedDependencies or uses raw JdbcTemplate
- Recharts chart configuration details (CartesianGrid, Tooltip, Legend)
- Whether new queries fan out inside `summarize()` or are split into helper classes

---

## Deferred Ideas

None — all discussion stayed within the defined scope of ANL-04 through ANL-09 plus top-10/domain-grouping.
