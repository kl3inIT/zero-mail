# Phase 7: Analytics Enhancement - Research

**Researched:** 2026-05-15
**Domain:** shadcn/ui Chart (Recharts), PostgreSQL generate_series, Spring Data JDBC analytics queries
**Confidence:** HIGH (core stack verified via official docs + codebase inspection)

---

<user_constraints>
## Ràng Buộc Người Dùng (từ CONTEXT.md)

### Quyết Định Đã Khóa
- **D-01:** Chart library = shadcn/ui `chart` (Recharts-based). Install: `pnpm dlx shadcn@latest add chart`.
- **D-02:** Donut cho Action Breakdown (ANL-06), Line cho Trend (ANL-04).
- **D-03:** TrendPanel layout = `md:col-span-2` full-width, đặt trên cùng grid trước 4 panel hiện tại.
- **D-04:** Extend `AnalyticsSummaryResponse` — không tạo endpoint mới.
- **D-05:** Backend tính delta % (prior window = `[start - duration, start)`). Trả `Long` nullable (null khi prior window có zero data).
- **D-06:** Trend SQL = `generate_series` + LEFT JOIN để zero-fill ngày không có event.
- **D-07:** Action Breakdown = expose `appliedByActionType` map hiện có dưới dạng `actionBreakdown` record.
- **D-08:** Credit scope = TRIAGE / DRAFT / PREVIEW grouped. JOIN `credit_ledger_entry` ↔ `credit_reservation`.
- **D-09:** Projected monthly = `(totalConsumed / windowDays) × 30`, trả `projectedMonthlyCredits: Long`.
- **D-10:** Extend `AnalyticsSummaryQueryService` với `queryCreditSpend(...)`.
- **D-11:** Top Senders: `LIMIT 3` → `LIMIT 10`.
- **D-12:** Domain grouping = SQL `SUBSTRING(sender_email FROM '@(.+)$')`. Hai trường riêng: `topSenders` + `topSenderDomains`.
- **D-13:** Frontend toggle "By Sender" / "By Domain" — local component state, chip pattern.
- **D-14:** Precision Rate = frontend-only: `applied / decisions × 100`.
- **D-15:** Trust Score badge: ≥90% green, 70–89% amber, <70% destructive.
- **D-16:** `decisions === 0` → Precision Rate = "—", Trust Score badge ẩn.
- **D-17:** Noise Reduction = frontend-only: `(volumeApplied / volumeObserved × 100)%`.
- **D-18:** i18n keys: `analytics.trend.*`, `analytics.actionBreakdown.*`, `analytics.noiseReduction.*`, `analytics.credits.*`.
- **D-19:** Privacy logging: `event=<name> tenantId={}` format. Không log `sender_email`.
- **D-20:** Credit balance từ `GET /api/billing/balance` — reuse TanStack Query cache, không endpoint mới.

### Claude's Discretion
- Package placement cho projection types mới (`trendPoints`, `creditSpend`) — bên trong `core.analytics.projection`.
- `trendPoints` dùng Java record `TrendPointProjection(LocalDate date, long observed, long applied)`.
- Liquibase changeset numbering cho index mới.
- `core.analytics` dùng raw JdbcTemplate (tránh import billing domain types) hoặc liệt kê `billing` trong `allowedDependencies`.
- Recharts CartesianGrid / Tooltip / Legend config chi tiết.
- `AnalyticsSummaryQueryService` fan out 7-8 query inline hoặc tách `CreditSpendQueryService` helper.

### Ý Tưởng Hoãn (NGOÀI SCOPE)
Không có item nào bị hoãn — tất cả 6 yêu cầu ANL-04 đến ANL-09 đều trong scope.
</user_constraints>

<phase_requirements>
## Yêu Cầu Phase

| ID | Mô tả | Research Support |
|----|-------|-----------------|
| ANL-04 | Trend chart: day-by-day observed vs applied trong window | generate_series SQL + shadcn LineChart |
| ANL-05 | Delta % badges: so sánh current vs prior window | Backend dual-query pattern + nullable Long |
| ANL-06 | Action Breakdown donut: label / archive / save_draft proportion | Expose `appliedByActionType`; shadcn PieChart/donut |
| ANL-07 | Rule Precision Rate + Trust Score badge | Frontend-only computed từ existing `decisions`/`applied` |
| ANL-08 | Noise Reduction panel | Frontend-only từ `volumeApplied / volumeObserved` |
| ANL-09 | Credits panel: consumed by call_site + projected monthly | JOIN credit_ledger_entry ↔ credit_reservation |
| ANL-EXT | Top Senders top-10 + domain grouping toggle | LIMIT 10 + SUBSTRING domain SQL + chip toggle |
</phase_requirements>

---

## Tóm Tắt

Phase 7 mở rộng analytics dashboard bằng cách thêm 5 data dimension mới (trend chart, delta badges, action breakdown, noise reduction, credits) và nâng cấp Top Senders lên top-10 với domain toggle. Toàn bộ data mới được tính từ metadata đã có (`mail_message_observed`, `triage_audit`, `credit_ledger_entry`, `credit_reservation`) — không có table mới, không có endpoint mới.

Backend pattern cốt lõi: extend `AnalyticsSummaryQueryService.summarize()` với 4 query mới (trend, delta, credit spend, top domains), extend `AnalyticsSummaryProjection` và `AnalyticsSummaryResponse` với field mới. Frontend: 4 component panel mới + 2 frontend-only computed panels, tất cả đọc từ `summaryQuery.data` đã có.

**Khuyến nghị chính:** Dùng raw JdbcTemplate trực tiếp với tên bảng `credit_ledger_entry`/`credit_reservation` trong `AnalyticsSummaryQueryService` để tránh vi phạm Modulith boundary với `core.billing` — đây là lựa chọn ít blast radius nhất.

---

## Bản Đồ Trách Nhiệm Kiến Trúc

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Trend time-series query | API / Backend | — | generate_series SQL requires DB engine; zero-fill logic backend-only |
| Delta % computation | API / Backend | — | Cần 2 DB query; frontend không có prior-window data |
| Action Breakdown aggregation | API / Backend | — | Đã tính trong `appliedByActionType`; chỉ cần expose |
| Credit spend query | API / Backend | — | JOIN giữa 2 table; không expose raw ledger ra frontend |
| Top Domain grouping | API / Backend | — | SUBSTRING GROUP BY thuộc về SQL layer |
| Precision Rate / Trust Score | Browser / Client | — | Công thức đơn giản từ existing response fields |
| Noise Reduction % | Browser / Client | — | Công thức đơn giản từ `volumeApplied / volumeObserved` |
| Sender/Domain toggle UI state | Browser / Client | — | Local component state, không persist |
| Chart rendering | Browser / Client | — | Recharts là client-side rendering library |

---

## Stack Chuẩn

### Core

| Library | Version | Mục Đích | Ghi Chú |
|---------|---------|---------|---------|
| shadcn/ui chart | (copied source) | ChartContainer + Tooltip + Legend wrappers | Install: `pnpm dlx shadcn@latest add chart` [VERIFIED: ui.shadcn.com/docs/components/chart] |
| recharts | ~2.15.x | LineChart, PieChart, Line, Pie, Cell, CartesianGrid, XAxis, YAxis | Peer dep của shadcn chart; không import trực tiếp ngoài component |
| Spring Data JDBC / JdbcTemplate | (Boot 4 managed) | Thực thi tất cả analytics SQL | Pattern hiện tại trong `AnalyticsSummaryQueryService` |
| PostgreSQL `generate_series` | built-in | Zero-fill day buckets cho trend query | Không cần extension |

### Supporting

| Library | Version | Mục Đích | Khi Dùng |
|---------|---------|---------|---------|
| TanStack Query | 5.100.1 | Cache `summaryQuery.data`; reuse `analyticsKeys.summary(window)` | Hook `useAnalyticsSummary` không thay đổi |
| next-intl | project version | i18n cho tất cả text label trong panel mới | Thêm key vào `vi.json` / `en.json` |
| lucide-react | project version | Icon `Info` cho tooltip trong panel mới | Pattern từ VolumePanel |

### Cài Đặt

```bash
# Từ apps/web
pnpm dlx shadcn@latest add chart
```

Lệnh này copy `components/ui/chart.tsx` vào project. Recharts được install tự động như peer dependency.

---

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | Disposition |
|---------|----------|-----|-----------|-------------|-------------|
| recharts | npm | ~9 yrs | ~5M/wk | github.com/recharts/recharts | Approved — shadcn peer dep [VERIFIED: ui.shadcn.com] |

shadcn/ui chart là **copied source** (không phải npm package riêng) — không cần audit riêng.

---

## API shadcn/ui Chart — LineChart và PieChart/Donut

### ChartConfig và CSS Variables

```typescript
// Source: ui.shadcn.com/docs/components/chart [VERIFIED]
import { type ChartConfig } from "@/components/ui/chart"

const trendChartConfig = {
  observed: {
    label: "Observed",
    // Dùng CSS variable token của project
    color: "hsl(var(--muted-foreground))",
  },
  applied: {
    label: "Applied",
    // Teal token = primary
    color: "hsl(var(--primary))",
  },
} satisfies ChartConfig
```

**Cơ chế màu:** `ChartContainer` inject CSS variables `--color-<key>` vào DOM scope dựa trên `config[key].color`. Trong JSX dùng `"var(--color-observed)"` và `"var(--color-applied)"` — không hardcode hex/hsl trực tiếp vào prop.

### LineChart — TrendPanel

```typescript
// Source: ui.shadcn.com/docs/components/chart [VERIFIED]
import { Line, LineChart, CartesianGrid, XAxis, YAxis } from "recharts"
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  ChartLegend,
  ChartLegendContent,
  type ChartConfig,
} from "@/components/ui/chart"

// Data shape từ backend TrendPointResponse[]
type TrendPointResponse = {
  date: string   // "2026-05-08" (ISO date, từ LocalDate)
  observed: number
  applied: number
}

const trendChartConfig = {
  observed: { label: "Observed",  color: "hsl(var(--muted-foreground))" },
  applied:  { label: "Applied",   color: "hsl(var(--primary))" },
} satisfies ChartConfig

export function TrendChart({ data }: { data: TrendPointResponse[] }) {
  return (
    <ChartContainer config={trendChartConfig} className="min-h-[220px] w-full">
      <LineChart data={data} accessibilityLayer>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis
          dataKey="date"
          tickLine={false}
          axisLine={false}
          tickMargin={8}
          // Rút gọn "2026-05-08" → "May 8"
          tickFormatter={(value: string) =>
            new Date(value).toLocaleDateString("en-US", { month: "short", day: "numeric" })
          }
        />
        <YAxis tickLine={false} axisLine={false} width={32} />
        <ChartTooltip content={<ChartTooltipContent indicator="line" />} />
        <ChartLegend content={<ChartLegendContent />} />
        <Line
          dataKey="observed"
          stroke="var(--color-observed)"
          strokeWidth={2}
          dot={false}
          type="monotone"
        />
        <Line
          dataKey="applied"
          stroke="var(--color-applied)"
          strokeWidth={2}
          dot={false}
          type="monotone"
        />
      </LineChart>
    </ChartContainer>
  )
}
```

**Quan trọng:** `type="monotone"` cho đường mượt. `dot={false}` khi có nhiều điểm data (30+ ngày) để tránh cluttered UI.

### PieChart/Donut — ActionBreakdownPanel

```typescript
// Source: ui.shadcn.com/docs/components/chart [VERIFIED]; innerRadius từ Recharts Pie props
import { Pie, PieChart, Cell } from "recharts"
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"

type ActionBreakdownData = {
  action: string  // "label" | "archive" | "save_draft"
  count: number
}

const actionChartConfig = {
  label:     { label: "Label",      color: "hsl(var(--primary))" },
  archive:   { label: "Archive",    color: "hsl(var(--chart-2))" },
  save_draft:{ label: "Save Draft", color: "hsl(var(--chart-3))" },
} satisfies ChartConfig

export function ActionBreakdownChart({ data }: { data: ActionBreakdownData[] }) {
  return (
    <ChartContainer config={actionChartConfig} className="min-h-[200px] w-full aspect-square">
      <PieChart>
        <ChartTooltip content={<ChartTooltipContent nameKey="action" hideLabel />} />
        <Pie
          data={data}
          dataKey="count"
          nameKey="action"
          innerRadius={60}   // Donut hole — innerRadius > 0
          outerRadius={90}
          strokeWidth={2}
        >
          {data.map((entry) => (
            <Cell
              key={entry.action}
              fill={`var(--color-${entry.action})`}
            />
          ))}
        </Pie>
      </PieChart>
    </ChartContainer>
  )
}
```

**Donut = Pie với `innerRadius > 0`.** Không có component riêng — chỉ cần thêm prop `innerRadius` vào `<Pie>`. [VERIFIED: Recharts docs, ui.shadcn.com]

**Lưu ý `save_draft` trong CSS var:** CSS variable name không được có underscore trong một số browser. Safe approach: map action key sang CSS-safe variant:

```typescript
// Utility trong component
function toCssKey(action: string): string {
  return action.replace(/_/g, "-") // "save_draft" → "save-draft"
}

const actionChartConfig = {
  "save-draft": { label: "Save Draft", color: "hsl(var(--chart-3))" },
  // ...
} satisfies ChartConfig

// Trong Cell:
fill={`var(--color-${toCssKey(entry.action)})`}
// Khi pass data: map action field cũng dùng toCssKey
```

---

## SQL Patterns

### generate_series — Zero-filled Trend Query (D-06)

```sql
-- Source: D-06 trong CONTEXT.md + PostgreSQL docs [VERIFIED]
-- Params: ?, ?, interval '1 day', ?, ?
-- Thứ tự: windowStart, windowEnd, tenantId (mail), tenantId (triage)
SELECT
    s.day::date                                                        AS date,
    count(DISTINCT m.gmail_message_id)
        FILTER (WHERE m.gmail_message_id IS NOT NULL)                  AS observed,
    count(DISTINCT a.gmail_message_id)
        FILTER (WHERE a.applied_at IS NOT NULL AND a.reverted_at IS NULL) AS applied
FROM generate_series(
    ?::timestamptz,            -- windowStartInclusive
    ? - interval '1 day',      -- windowEndExclusive - 1 day (end inclusive for series)
    interval '1 day'
) AS s(day)
LEFT JOIN mail_message_observed m
    ON m.tenant_id = ?::uuid
   AND date_trunc('day', m.observed_at AT TIME ZONE 'UTC') = s.day
   AND 'INBOX' = ANY(m.label_ids)
LEFT JOIN triage_audit a
    ON a.tenant_id = ?::uuid
   AND date_trunc('day', a.applied_at AT TIME ZONE 'UTC') = s.day
GROUP BY s.day
ORDER BY s.day ASC
```

**Java implementation pattern:**

```java
private static final String TREND_SQL =
    """
    SELECT
        s.day::date AS date,
        count(DISTINCT m.gmail_message_id)
            FILTER (WHERE m.gmail_message_id IS NOT NULL) AS observed,
        count(DISTINCT a.gmail_message_id)
            FILTER (WHERE a.applied_at IS NOT NULL AND a.reverted_at IS NULL) AS applied
    FROM generate_series(?::timestamptz, ? - interval '1 day', interval '1 day') AS s(day)
    LEFT JOIN mail_message_observed m
        ON m.tenant_id = ?::uuid
       AND date_trunc('day', m.observed_at AT TIME ZONE 'UTC') = s.day
       AND 'INBOX' = ANY(m.label_ids)
    LEFT JOIN triage_audit a
        ON a.tenant_id = ?::uuid
       AND date_trunc('day', a.applied_at AT TIME ZONE 'UTC') = s.day
    GROUP BY s.day
    ORDER BY s.day ASC
    """;

// New projection record (in core.analytics.projection package):
// public record TrendPointProjection(LocalDate date, long observed, long applied) {}

private List<TrendPointProjection> queryTrendPoints(
        UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
    return jdbcTemplate.query(
            TREND_SQL,
            (resultSet, rowNumber) -> new TrendPointProjection(
                    resultSet.getDate("date").toLocalDate(),
                    resultSet.getLong("observed"),
                    resultSet.getLong("applied")),
            windowStartInclusive,
            windowEndExclusive,
            tenantId,
            tenantId);
}
```

**Caveat quan trọng về generate_series upper bound:** `generate_series(start, end, step)` bao gồm `end`. Vì `windowEndExclusive` là exclusive, truyền `windowEndExclusive - interval '1 day'` để tránh tạo thêm ngày thừa. Hoặc dùng `WHERE s.day < ?` thay vì điều chỉnh parameter.

### Credit Spend JOIN Query (D-08)

```sql
-- Source: D-08 trong CONTEXT.md + schema verify từ 014/015 changelogs [VERIFIED]
SELECT
    cr.call_site                              AS call_site,
    sum(abs(cle.amount_credits))              AS credits_spent
FROM credit_ledger_entry cle
JOIN credit_reservation cr
    ON cle.ref_id  = cr.id::varchar
   AND cle.ref_type = 'RESERVATION'
   AND cle.kind    = 'SETTLE'
WHERE cle.tenant_id  = ?
  AND cle.created_at >= ?
  AND cle.created_at  < ?
  AND cr.call_site IN ('TRIAGE', 'DRAFT', 'PREVIEW')
GROUP BY cr.call_site
```

**Java implementation:**

```java
private static final String CREDIT_SPEND_SQL =
    """
    SELECT cr.call_site AS call_site, sum(abs(cle.amount_credits)) AS credits_spent
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
    """;

// New projection record:
// public record CreditSpendProjection(String callSite, long creditsSpent) {}

private List<CreditSpendProjection> queryCreditSpend(
        UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
    return jdbcTemplate.query(
            CREDIT_SPEND_SQL,
            (resultSet, rowNumber) -> new CreditSpendProjection(
                    resultSet.getString("call_site"),
                    resultSet.getLong("credits_spent")),
            tenantId,
            windowStartInclusive,
            windowEndExclusive);
}
```

**Lý do dùng `abs()`:** `amount_credits` trong `credit_ledger_entry` có thể lưu âm (debit). `abs()` đảm bảo luôn dương cho display.

**Không cần import `CallSite` enum:** Query dùng raw string IN clause — tránh vi phạm Modulith boundary `core.analytics` → `core.billing`.

### Domain Grouping SQL (D-12)

```sql
-- Source: D-12 trong CONTEXT.md [VERIFIED - PostgreSQL SUBSTRING regex]
SELECT
    SUBSTRING(sender_email FROM '@(.+)$') AS domain,
    count(*)                               AS message_count
FROM mail_message_observed
WHERE tenant_id = ?
  AND observed_at >= ?
  AND observed_at < ?
  AND sender_email IS NOT NULL
  AND 'INBOX' = ANY(label_ids)
  AND SUBSTRING(sender_email FROM '@(.+)$') IS NOT NULL
GROUP BY domain
ORDER BY message_count DESC, domain ASC
LIMIT 10
```

**Java implementation:**

```java
private static final String TOP_SENDER_DOMAINS_SQL =
    """
    SELECT SUBSTRING(sender_email FROM '@(.+)$') AS domain, count(*) AS message_count
    FROM mail_message_observed
    WHERE tenant_id = ?
      AND observed_at >= ?
      AND observed_at < ?
      AND sender_email IS NOT NULL
      AND 'INBOX' = ANY(label_ids)
      AND SUBSTRING(sender_email FROM '@(.+)$') IS NOT NULL
    GROUP BY domain
    ORDER BY message_count DESC, domain ASC
    LIMIT 10
    """;

// New projection record:
// public record TopSenderDomainProjection(String domain, long count) {}

private List<TopSenderDomainProjection> queryTopSenderDomains(
        UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
    return jdbcTemplate.query(
            TOP_SENDER_DOMAINS_SQL,
            (resultSet, rowNumber) -> new TopSenderDomainProjection(
                    resultSet.getString("domain"),
                    resultSet.getLong("message_count")),
            tenantId,
            windowStartInclusive,
            windowEndExclusive);
}
```

---

## Pattern Delta % (D-05)

### Backend Dual-Query Pattern

```java
// Trong AnalyticsSummaryQueryService.summarize()
// Prior window = [start - duration, start)

@Transactional(readOnly = true)
public AnalyticsSummaryProjection summarize(UUID tenantId, TimeWindow window) {
    // ... existing queries (current window) ...

    // --- Delta computation ---
    Duration windowDuration = Duration.between(
            requestedWindow.startInclusive(), requestedWindow.endExclusive());
    Instant priorWindowStart = requestedWindow.startInclusive().minus(windowDuration);
    Instant priorWindowEnd   = requestedWindow.startInclusive(); // exclusive
    Timestamp priorWindowStartTs = Timestamp.from(priorWindowStart);
    Timestamp priorWindowEndTs   = Timestamp.from(priorWindowEnd);

    long priorVolumeObserved = queryCount(OBSERVED_VOLUME_SQL, tenantId,
            priorWindowStartTs, priorWindowEndTs);
    long priorVolumeApplied  = queryCount(APPLIED_VOLUME_SQL, tenantId,
            priorWindowStartTs, priorWindowEndTs);

    Long volumeObservedDeltaPct = computeDeltaPct(volumeObserved, priorVolumeObserved);
    Long volumeAppliedDeltaPct  = computeDeltaPct(volumeApplied,  priorVolumeApplied);
    // timeSaved delta: compute priorTimeSaved similarly using priorAppliedByActionType
    // ...

    return new AnalyticsSummaryProjection(
            volumeObserved, volumeApplied, timeSavedSeconds,
            topSenders, topSenderDomains, ruleHits, trendPoints,
            actionBreakdown, creditSpend,
            volumeObservedDeltaPct, volumeAppliedDeltaPct, timeSavedDeltaPct);
}

/**
 * Returns null when priorValue is 0 (division by zero → frontend renders "—").
 * Returns Long (signed %) otherwise.
 */
private static Long computeDeltaPct(long currentValue, long priorValue) {
    if (priorValue == 0L) {
        return null;  // Frontend renders "—" instead of NaN/infinity
    }
    return Math.round(((double)(currentValue - priorValue) / priorValue) * 100.0);
}
```

**Frontend rendering:**

```typescript
function formatDelta(deltaPct: number | null | undefined): string {
  if (deltaPct == null || !Number.isFinite(deltaPct)) return "—"
  const sign = deltaPct >= 0 ? "+" : ""
  return `${sign}${deltaPct}%`
}

// Badge color:
function deltaVariant(deltaPct: number | null | undefined): "positive" | "negative" | "neutral" {
  if (deltaPct == null) return "neutral"
  if (deltaPct > 0) return "positive"
  if (deltaPct < 0) return "negative"
  return "neutral"
}
```

---

## Placement Projection Types Mới

```
backend/core/src/main/java/com/zeromail/core/analytics/projection/
├── AnalyticsSummaryProjection.java      ← extend với fields mới
├── AnalyticsSummaryQueryService.java    ← extend với 4 query mới
├── TrendPointProjection.java            ← NEW record
├── CreditSpendProjection.java           ← NEW record
├── TopSenderDomainProjection.java       ← NEW record
├── RuleHitProjection.java               ← existing (unchanged)
└── TopSenderProjection.java             ← existing (unchanged)
```

Tất cả nằm trong package `com.zeromail.core.analytics.projection` — nhất quán với pattern hiện tại.

**DTO response tương ứng trong `backend/api`:**

```
backend/api/src/main/java/com/zeromail/api/dto/analytics/
├── AnalyticsSummaryResponse.java        ← extend với nested records mới
├── TrendPointResponse.java              ← NEW nested record (hoặc nội bộ trong AnalyticsSummaryResponse)
├── ActionBreakdownResponse.java         ← NEW nested record
├── CreditSpendResponse.java             ← NEW nested record
├── TopSenderDomainResponse.java         ← NEW nested record
└── DeltasResponse.java                  ← NEW nested record (hoặc field Long riêng)
```

**Khuyến nghị:** Dùng **nested record bên trong `AnalyticsSummaryResponse`** giống pattern `TopSenderResponse` và `RuleHitResponse` hiện tại — tránh tạo file DTO rải rác.

---

## Pattern Kiến Trúc

### Mở Rộng `AnalyticsSummaryProjection`

```java
// backend/core/.../analytics/projection/AnalyticsSummaryProjection.java
public record AnalyticsSummaryProjection(
        long volumeObserved,
        long volumeApplied,
        long timeSavedSeconds,
        List<TopSenderProjection> topSenders,
        List<TopSenderDomainProjection> topSenderDomains,    // NEW
        List<RuleHitProjection> ruleHits,
        List<TrendPointProjection> trendPoints,              // NEW
        ActionBreakdownProjection actionBreakdown,           // NEW
        List<CreditSpendProjection> creditSpend,             // NEW
        Long volumeObservedDeltaPct,                         // NEW nullable
        Long volumeAppliedDeltaPct,                          // NEW nullable
        Long timeSavedDeltaPct) {                            // NEW nullable

    public AnalyticsSummaryProjection {
        topSenders = List.copyOf(topSenders);
        topSenderDomains = List.copyOf(topSenderDomains);
        ruleHits = List.copyOf(ruleHits);
        trendPoints = List.copyOf(trendPoints);
        creditSpend = List.copyOf(creditSpend);
    }
}
```

### Mở Rộng `AnalyticsSummaryResponse`

```java
// backend/api/.../dto/analytics/AnalyticsSummaryResponse.java
public record AnalyticsSummaryResponse(
        String window,
        long volumeObserved,
        long volumeApplied,
        long timeSavedSeconds,
        List<TopSenderResponse> topSenders,
        List<TopSenderDomainResponse> topSenderDomains,     // NEW
        List<RuleHitResponse> ruleHits,
        List<TrendPointResponse> trendPoints,               // NEW
        ActionBreakdownResponse actionBreakdown,             // NEW
        List<CreditSpendResponse> creditSpend,              // NEW
        Long volumeObservedDeltaPct,                         // NEW nullable
        Long volumeAppliedDeltaPct,                          // NEW nullable
        Long timeSavedDeltaPct,                              // NEW nullable
        Long projectedMonthlyCredits) {                      // NEW

    // Canonical compact constructor + List.copyOf

    public static AnalyticsSummaryResponse from(
            AnalyticsSummaryProjection projection, AnalyticsWindow window) {
        long totalCredits = projection.creditSpend().stream()
                .mapToLong(CreditSpendProjection::creditsSpent).sum();
        long windowDays = window.days();  // 7, 30, or 90
        long projectedMonthly = windowDays > 0 ? Math.round((double) totalCredits / windowDays * 30) : 0L;

        return new AnalyticsSummaryResponse(
                window.id(),
                projection.volumeObserved(),
                projection.volumeApplied(),
                projection.timeSavedSeconds(),
                projection.topSenders().stream().map(TopSenderResponse::from).toList(),
                projection.topSenderDomains().stream().map(TopSenderDomainResponse::from).toList(),
                projection.ruleHits().stream().map(RuleHitResponse::from).toList(),
                projection.trendPoints().stream().map(TrendPointResponse::from).toList(),
                ActionBreakdownResponse.from(projection.actionBreakdown()),
                projection.creditSpend().stream().map(CreditSpendResponse::from).toList(),
                projection.volumeObservedDeltaPct(),
                projection.volumeAppliedDeltaPct(),
                projection.timeSavedDeltaPct(),
                projectedMonthly);
    }

    // --- Nested response records ---

    public record TrendPointResponse(String date, long observed, long applied) {
        static TrendPointResponse from(TrendPointProjection p) {
            return new TrendPointResponse(p.date().toString(), p.observed(), p.applied());
        }
    }

    public record TopSenderDomainResponse(String domain, long count) {
        static TopSenderDomainResponse from(TopSenderDomainProjection p) {
            return new TopSenderDomainResponse(p.domain(), p.count());
        }
    }

    public record ActionBreakdownResponse(long labelCount, long archiveCount, long saveDraftCount) {
        static ActionBreakdownResponse from(ActionBreakdownProjection p) {
            return new ActionBreakdownResponse(p.labelCount(), p.archiveCount(), p.saveDraftCount());
        }
    }

    public record CreditSpendResponse(String callSite, long creditsSpent) {
        static CreditSpendResponse from(CreditSpendProjection p) {
            return new CreditSpendResponse(p.callSite(), p.creditsSpent());
        }
    }
    // TopSenderResponse và RuleHitResponse giữ nguyên
}
```

### Frontend Type Update

Sau khi backend sinh lại OpenAPI spec, `openapi-typescript` sẽ cập nhật `@/lib/api/schema.ts` tự động. Frontend chỉ cần:

```typescript
// analytics-api.ts — KHÔNG thay đổi logic, chỉ type mở rộng tự động
export type AnalyticsSummaryResponse = components['schemas']['AnalyticsSummaryResponse']
// Các nested type mới:
export type TrendPointResponse = components['schemas']['TrendPointResponse']
export type CreditSpendResponse = components['schemas']['CreditSpendResponse']
```

---

## Những Thứ Không Nên Tự Xây

| Vấn Đề | Đừng Tự Xây | Dùng Thay Thế | Lý Do |
|---------|------------|---------------|-------|
| Chart responsive container | Custom div | `ChartContainer` (shadcn) | Handles ResizeObserver, min-height, aria |
| Tooltip formatting | Custom overlay | `ChartTooltipContent` với props `indicator` | Handles mouse position, portal, styling |
| Donut chart | Custom SVG arc | `<Pie innerRadius={N}>` từ Recharts | Edge cases: animation, a11y |
| Zero-fill time series | Application-side loop | `generate_series` SQL | Consistent với DB timezone; O(1) vs O(n) |
| Delta % NaN guard | Thresholding | `return null` khi `priorValue === 0` | Frontend render "—" nhất quán |

---

## Các Rủi Ro và Caveats

### Rủi Ro 1: CSS Variable `--color-<key>` với underscore

**Vấn đề:** `var(--color-save_draft)` không hoạt động — CSS custom property names không được chứa underscore theo spec của một số browser parser. Recharts Cell sẽ nhận `undefined` màu.

**Cách tránh:** Map action type sang CSS-safe key (`save_draft` → `save-draft`) trước khi build `chartConfig` và trong data array. Utility `toCssKey` trong file chart component.

### Rủi Ro 2: generate_series timezone mismatch

**Vấn đề:** `generate_series(start, end, '1 day')` sinh các timestamp UTC midnight. Nếu `mail_message_observed.observed_at` được store theo UTC nhưng user timezone khác, `date_trunc('day', ...)` có thể lệch ngày.

**Cách tránh:** Tất cả timestamp trong project store UTC (verify qua `timestamptz` trong changelogs — đúng). Dùng `AT TIME ZONE 'UTC'` trong `date_trunc` để explicit. Frontend format ngày dùng user locale nhưng backend luôn UTC.

### Rủi Ro 3: generate_series parameter type mismatch

**Vấn đề:** JdbcTemplate truyền `java.sql.Timestamp` nhưng PostgreSQL `generate_series` cần `timestamptz`. JDBC driver của PostgreSQL thường handle tự động nhưng một số version cần cast.

**Cách tránh:** Dùng `?::timestamptz` cast inline trong SQL (đã có trong template).

### Rủi Ro 4: credit_reservation không có tenant_id column

**Vấn đề:** Schema `015-credit-reservation.yaml` có `tenant_id` column nhưng JOIN đi qua `credit_ledger_entry.tenant_id` (đã indexed). Nếu filter chỉ trên `cle.tenant_id`, query vẫn correct vì `cle.ref_id = cr.id::varchar` là unique key trên `credit_reservation`.

**Xác nhận:** Schema 015 có `tenant_id` trên `credit_reservation` với FK về `tenants(id)`. Có thể thêm `AND cr.tenant_id = ?` vào WHERE clause để double-filter nếu cần index push-down — nhưng không bắt buộc về tính đúng đắn.

### Rủi Ro 5: Recharts version compatibility với React 19

**Vấn đề:** Recharts ~2.x sử dụng legacy React APIs. React 19 có breaking changes (Concurrent Mode strict, `act()` changes).

**Cách tránh:** shadcn/ui chart component đã tested với React 18/19 peer. Không import Recharts trực tiếp ngoài shadcn wrapper — để wrapper absorb bất kỳ compatibility shim nào. [ASSUMED — React 19 + Recharts 2 compatibility cụ thể chưa verify từ release notes]

### Rủi Ro 6: `AnalyticsSummaryResponse` là Java record — compact constructor cần update

**Vấn đề:** Java record có compact constructor `public AnalyticsSummaryResponse { topSenders = List.copyOf(topSenders); ... }`. Khi thêm field `List` mới (e.g. `trendPoints`, `creditSpend`), compact constructor PHẢI có `List.copyOf` cho mỗi List field mới. Quên → `UnsupportedOperationException` tại runtime.

**Cách tránh:** Planner thêm `List.copyOf` cho mỗi `List<?>` field trong compact constructor.

---

## Kiến Trúc Validation

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (Boot 4) |
| Config file | `backend/core/src/test/resources/application.yml` |
| Quick run | `./gradlew :backend:core:test --tests "*Analytics*"` |
| Full suite | `./gradlew :backend:core:test` |

### Mapping Yêu Cầu → Test

| Req ID | Hành Vi | Test Type | Command |
|--------|---------|-----------|---------|
| ANL-04 trend | generate_series trả đúng số ngày, zero-fill | unit (JdbcTemplate + H2 hoặc Testcontainers Postgres) | `./gradlew :backend:core:test --tests "*TrendQuery*"` |
| ANL-05 delta | `computeDeltaPct` trả null khi prior=0, đúng % | unit (pure Java) | `./gradlew :backend:core:test --tests "*DeltaPct*"` |
| ANL-09 credits | JOIN query aggregate đúng per call_site | unit (Testcontainers) | `./gradlew :backend:core:test --tests "*CreditSpend*"` |
| Privacy | New queries không leak sender_email vào log | `Analytics*PrivacySweepTest` extend | `./gradlew :backend:core:test --tests "*PrivacySweep*"` |
| ANL-07 precision | Frontend `applied/decisions*100` | Vitest unit | `pnpm --filter web test` |

### Wave 0 Gaps

- [ ] `TrendPointProjectionQueryTest.java` — covers ANL-04 generate_series zero-fill
- [ ] `CreditSpendQueryTest.java` — covers ANL-09 JOIN logic
- [ ] `AnalyticsDeltaComputationTest.java` — covers ANL-05 null-safe delta
- [ ] Extend `AnalyticsPrivacySweepTest` cho 3 query mới
- [ ] `TrendChart.test.tsx` — Vitest, kiểm tra render với empty data
- [ ] `ActionBreakdownChart.test.tsx` — Vitest, donut render

---

## Security Domain

| ASVS Category | Áp Dụng | Control |
|---------------|---------|---------|
| V5 Input Validation | yes | `window` param validated ở controller (existing) |
| V4 Access Control | yes | `tenant_id` param luôn từ authenticated session, không từ user input |
| V3 Session Management | no | Không thay đổi session handling |
| V6 Cryptography | no | Không encrypt analytics data |

**Privacy invariant (D-19):** Tất cả log line mới theo format `event=<name> tenantId={}`. `sender_email` KHÔNG được xuất hiện trong server log (domain grouping chỉ log count, không log domain string).

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | React 19 + Recharts 2.x compatible qua shadcn wrapper | Rủi Ro 5 | Chart không render — fallback: pin Recharts version |
| A2 | `generate_series` với `Timestamp` JDBC param không cần explicit cast nếu dùng `::timestamptz` trong SQL | generate_series SQL | Query fail với type error — fix: dùng `setTimestamp` với Calendar UTC |
| A3 | `amount_credits` trong `credit_ledger_entry` cho SETTLE entries là dương (khi charge) hoặc âm | Credit query | `abs()` mask logic error — verify với billing team |

---

## Câu Hỏi Còn Mở

1. **`AnalyticsWindow.days()` method tồn tại chưa?**
   - Biết: `AnalyticsWindow` là enum/sealed type trong `backend/api`
   - Không rõ: Có method `days()` không hay cần tính từ `TimeWindow`
   - Khuyến nghị: Planner kiểm tra `AnalyticsWindow` source; nếu không có `days()`, thêm helper trong `from()` factory

2. **`ActionBreakdownProjection` — record hay inline trong Projection?**
   - Biết: D-07 nói expose `appliedByActionType` map hiện có
   - Không rõ: Map `{LABEL: 5, ARCHIVE: 10, SAVE_DRAFT: 3}` hay record riêng
   - Khuyến nghị: Planner tạo `record ActionBreakdownProjection(long labelCount, long archiveCount, long saveDraftCount)` được build từ map sau khi `queryAppliedByActionType` chạy

3. **Index cho `generate_series` JOIN?**
   - Biết: Phase 5C đã tạo index trên `mail_message_observed(tenant_id, observed_at)` và `triage_audit(tenant_id, applied_at)`
   - Không rõ: `date_trunc` expression trên `observed_at` có được cover bởi index hiện tại không
   - Khuyến nghị: Nếu EXPLAIN ANALYZE chỉ ra Seq Scan, thêm partial index `(date_trunc('day', observed_at))` trong Liquibase changeset tiếp theo

---

## Nguồn

### Primary (HIGH confidence)
- [ui.shadcn.com/docs/components/chart](https://ui.shadcn.com/docs/components/chart) — ChartContainer, ChartTooltip, ChartConfig, Line chart, Pie chart API [VERIFIED]
- `AnalyticsSummaryQueryService.java` — JdbcTemplate pattern, SQL shape hiện tại [VERIFIED: codebase read]
- `014-credit-ledger-entry.yaml` + `015-credit-reservation.yaml` — schema columns, constraints, indexes [VERIFIED: codebase read]
- `CallSite.java` — TRIAGE/DRAFT/PREVIEW enum members [VERIFIED: codebase read]
- `AnalyticsSummaryResponse.java` + `AnalyticsSummaryProjection.java` — record structure hiện tại [VERIFIED: codebase read]
- `07-CONTEXT.md` — 20 locked decisions [VERIFIED: codebase read]
- PostgreSQL docs — `generate_series`, `SUBSTRING FROM` regex syntax [CITED: postgresql.org/docs]

### Secondary (MEDIUM confidence)
- TopSendersPanel.tsx, VolumePanel.tsx, AnalyticsPageClient.tsx — frontend pattern [VERIFIED: codebase read]
- Recharts Pie `innerRadius` prop — donut pattern [CITED: recharts.org/api/Pie]

### Tertiary (LOW confidence)
- React 19 + Recharts 2.x compatibility via shadcn wrapper [ASSUMED — A1]

---

## Metadata

**Confidence breakdown:**
- shadcn/ui chart API: HIGH — verified từ official docs
- SQL patterns (generate_series, credit JOIN, domain SUBSTRING): HIGH — verified từ CONTEXT.md decisions + schema changelogs
- Delta % pattern: HIGH — straightforward math, verified constraint design
- React 19 + Recharts compatibility: LOW — không có official statement
- Package placement: HIGH — follows existing codebase conventions

**Research date:** 2026-05-15
**Valid until:** 2026-06-15 (shadcn/ui chart API stable; Spring Boot 4 API stable)
