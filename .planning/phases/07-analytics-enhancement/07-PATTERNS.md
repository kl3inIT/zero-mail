# Phase 7: Analytics Enhancement - Pattern Map

**Mapped:** 2026-05-15
**Files analyzed:** 18 (backend + frontend — mới và cần sửa đổi)
**Analogs found:** 17 / 18

---

## Phân loại File

| File mới / sửa đổi | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/core/.../projection/AnalyticsSummaryQueryService.java` | service | CRUD read | chính nó (mở rộng) | exact |
| `backend/core/.../projection/AnalyticsSummaryProjection.java` | model | transform | chính nó (mở rộng) | exact |
| `backend/core/.../projection/TrendPointProjection.java` | model | transform | `TopSenderProjection.java` | exact |
| `backend/core/.../projection/ActionBreakdownProjection.java` | model | transform | `RuleHitProjection.java` | exact |
| `backend/core/.../projection/DeltasProjection.java` | model | transform | `RuleHitProjection.java` | exact |
| `backend/core/.../projection/CreditSpendProjection.java` | model | transform | `TopSenderProjection.java` | exact |
| `backend/core/.../projection/TopSenderDomainProjection.java` | model | transform | `TopSenderProjection.java` | exact |
| `backend/api/.../dto/analytics/AnalyticsSummaryResponse.java` | DTO | transform | chính nó (mở rộng) | exact |
| `apps/web/features/analytics/components/TrendPanel.tsx` | component | request-response | `VolumePanel.tsx` + `TimeSavedPanel.tsx` | exact |
| `apps/web/features/analytics/components/ActionBreakdownPanel.tsx` | component | request-response | `TopSendersPanel.tsx` | exact |
| `apps/web/features/analytics/components/NoiseReductionPanel.tsx` | component | request-response | `VolumePanel.tsx` | exact |
| `apps/web/features/analytics/components/CreditsPanel.tsx` | component | request-response | `TimeSavedPanel.tsx` | exact |
| `apps/web/features/analytics/components/TopSendersPanel.tsx` | component | request-response | chính nó (mở rộng) | exact |
| `apps/web/features/analytics/components/RuleHitsPanel.tsx` | component | request-response | chính nó (mở rộng) | exact |
| `apps/web/features/analytics/components/AnalyticsPageClient.tsx` | component | request-response | chính nó (mở rộng) | exact |
| `apps/web/features/analytics/components/AnalyticsSkeleton.tsx` | component | request-response | chính nó (mở rộng) | exact |
| `apps/web/features/analytics/messages.ts` | config | transform | chính nó (mở rộng) | exact |
| `apps/web/components/ui/chart.tsx` | ui-primitive | - | không có analog (cài mới) | none |

---

## Pattern Assignments

### Backend: Projection Records mới

**Analog:** `backend/core/src/main/java/com/zeromail/core/analytics/projection/TopSenderProjection.java`

Mỗi record mới chỉ có field + compact constructor (nếu cần defensive copy). Không có logic, không có method.

**Pattern (lines 1-3):**
```java
package com.zeromail.core.analytics.projection;

public record TrendPointProjection(java.time.LocalDate date, long observed, long applied) {}
```

```java
public record ActionBreakdownProjection(long labelCount, long archiveCount, long saveDraftCount) {}
```

```java
// nullable Long dùng để biểu diễn "không có dữ liệu prior window" — null khác với 0
public record DeltasProjection(Long volumeObservedDeltaPct, Long volumeAppliedDeltaPct, Long timeSavedDeltaPct) {}
```

```java
public record CreditSpendProjection(long triageCredits, long draftCredits, long previewCredits, long projectedMonthlyCredits) {}
```

```java
public record TopSenderDomainProjection(String domain, long count) {}
```

---

### `AnalyticsSummaryProjection.java` (mở rộng)

**Analog:** Chính nó (lines 1-16 hiện tại).

**Pattern hiện tại — copy compact constructor + `List.copyOf`:**
```java
public record AnalyticsSummaryProjection(
        long volumeObserved,
        long volumeApplied,
        long timeSavedSeconds,
        List<TopSenderProjection> topSenders,
        List<RuleHitProjection> ruleHits) {

    public AnalyticsSummaryProjection {
        topSenders = List.copyOf(topSenders);
        ruleHits = List.copyOf(ruleHits);
    }
}
```

**Pattern mở rộng — thêm field mới, giữ compact constructor:**
```java
public record AnalyticsSummaryProjection(
        long volumeObserved,
        long volumeApplied,
        long timeSavedSeconds,
        List<TopSenderProjection> topSenders,
        List<RuleHitProjection> ruleHits,
        // Mới — Phase 7
        List<TrendPointProjection> trendPoints,
        ActionBreakdownProjection actionBreakdown,
        DeltasProjection deltas,
        List<TopSenderDomainProjection> topSenderDomains,
        CreditSpendProjection creditSpend) {

    public AnalyticsSummaryProjection {
        topSenders = List.copyOf(topSenders);
        ruleHits = List.copyOf(ruleHits);
        trendPoints = List.copyOf(trendPoints);
        topSenderDomains = List.copyOf(topSenderDomains);
        // actionBreakdown, deltas, creditSpend là records đơn — không cần copy
    }
}
```

---

### `AnalyticsSummaryQueryService.java` (mở rộng)

**Analog:** Chính nó — `backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryQueryService.java`

**Pattern khai báo SQL constant (lines 22-78):**
```java
private static final String TREND_SQL =
        """
        SELECT s.day::date AS day_date,
               count(DISTINCT m.gmail_message_id)
                   FILTER (WHERE m.gmail_message_id IS NOT NULL) AS observed,
               count(DISTINCT a.gmail_message_id)
                   FILTER (WHERE a.applied_at IS NOT NULL AND a.reverted_at IS NULL) AS applied
        FROM generate_series(?, ?, interval '1 day') AS s(day)
        LEFT JOIN mail_message_observed m
               ON m.tenant_id = ? AND date_trunc('day', m.observed_at AT TIME ZONE 'UTC') = s.day
        LEFT JOIN triage_audit a
               ON a.tenant_id = ? AND date_trunc('day', a.applied_at AT TIME ZONE 'UTC') = s.day
        GROUP BY s.day
        ORDER BY s.day ASC
        """;

private static final String TOP_SENDER_DOMAINS_SQL =
        """
        SELECT SUBSTRING(sender_email FROM '@(.+)$') AS domain,
               count(*) AS domain_count
        FROM mail_message_observed
        WHERE tenant_id = ?
          AND observed_at >= ?
          AND observed_at < ?
          AND sender_email IS NOT NULL
          AND 'INBOX' = ANY(label_ids)
        GROUP BY domain
        ORDER BY domain_count DESC, domain ASC
        LIMIT 10
        """;

private static final String CREDIT_SPEND_SQL =
        """
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
        """;
```

**Pattern `queryCount` — null guard (lines 115-123):**
```java
private long queryCount(
        String sql,
        UUID tenantId,
        Timestamp windowStartInclusive,
        Timestamp windowEndExclusive) {
    Long count =
            jdbcTemplate.queryForObject(
                    sql, Long.class, tenantId, windowStartInclusive, windowEndExclusive);
    return count == null ? 0L : count;
}
```

**Pattern query trả về List (lines 150-160):**
```java
private List<TrendPointProjection> queryTrendPoints(
        UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
    return jdbcTemplate.query(
            TREND_SQL,
            (resultSet, rowNumber) ->
                    new TrendPointProjection(
                            resultSet.getDate("day_date").toLocalDate(),
                            resultSet.getLong("observed"),
                            resultSet.getLong("applied")),
            windowStartInclusive,   // s.day param 1
            windowEndExclusive,     // s.day param 2
            tenantId,               // LEFT JOIN mail_message_observed
            tenantId);              // LEFT JOIN triage_audit
}
```

**Pattern query map (lines 126-148) — dùng cho credit spend:**
```java
private CreditSpendProjection queryCreditSpend(
        UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
    Map<String, Long> spendByCallSite = new HashMap<>();
    jdbcTemplate.query(
            CREDIT_SPEND_SQL,
            resultSet -> {
                String callSite = resultSet.getString("call_site");
                if (callSite == null) {
                    return;
                }
                spendByCallSite.merge(
                        callSite,
                        resultSet.getLong("credits_spent"),
                        (previousCount, duplicateCount) -> {
                            throw new IllegalStateException(
                                    "Duplicate call_site in credit-spend query: " + callSite);
                        });
            },
            tenantId,
            windowStartInclusive,
            windowEndExclusive);
    // ... build CreditSpendProjection từ map
}
```

**Pattern delta computation — prior window (không có analog, pattern mới):**
```java
// Prior window = [start - duration, start)
private DeltasProjection queryDeltas(
        UUID tenantId, TimeWindow currentWindow) {
    Timestamp priorStart = Timestamp.from(currentWindow.startInclusive().minus(currentWindow.duration()));
    Timestamp priorEnd   = Timestamp.from(currentWindow.startInclusive());
    long priorObserved = queryCount(OBSERVED_VOLUME_SQL, tenantId, priorStart, priorEnd);
    long priorApplied  = queryCount(APPLIED_VOLUME_SQL,  tenantId, priorStart, priorEnd);
    // timeSaved prior cũng cần queryAppliedByActionType riêng cho prior window
    return new DeltasProjection(
            deltaPct(currentObserved, priorObserved),
            deltaPct(currentApplied,  priorApplied),
            deltaPct(currentTimeSaved, priorTimeSaved));
}

/** Trả null khi prior = 0 (tránh chia cho 0 → frontend render "—"). */
private static Long deltaPct(long current, long prior) {
    if (prior == 0) return null;
    return Math.round((current - prior) * 100.0 / prior);
}
```

**Pattern ActionBreakdown — lấy từ `appliedByActionType` đã compute (D-07):**
```java
// Không cần SQL mới — chỉ wrap map đã có thành record
private static ActionBreakdownProjection toActionBreakdown(Map<String, Long> appliedByActionType) {
    return new ActionBreakdownProjection(
            appliedByActionType.getOrDefault("label", 0L),
            appliedByActionType.getOrDefault("archive", 0L),
            appliedByActionType.getOrDefault("save_draft", 0L));
}
```

**Pattern logging (lines 106-112):**
```java
log.info(
        "event=analytics_summary_computed tenantId={} windowStart={} windowEnd={}",
        tenantId,
        requestedWindow.startInclusive(),
        requestedWindow.endExclusive());
```

---

### `AnalyticsSummaryResponse.java` (mở rộng)

**Analog:** Chính nó — `backend/api/src/main/java/com/zeromail/api/dto/analytics/AnalyticsSummaryResponse.java`

**Pattern record ngoài cùng + compact constructor + `from()` factory (lines 8-46):**
```java
public record AnalyticsSummaryResponse(
        String window,
        long volumeObserved,
        long volumeApplied,
        long timeSavedSeconds,
        List<TopSenderResponse> topSenders,
        List<RuleHitResponse> ruleHits,
        // Mới — Phase 7
        List<TrendPointResponse> trendPoints,
        ActionBreakdownResponse actionBreakdown,
        DeltasResponse deltas,
        List<TopSenderDomainResponse> topSenderDomains,
        CreditSpendResponse creditSpend) {

    public AnalyticsSummaryResponse {
        topSenders = List.copyOf(topSenders);
        ruleHits = List.copyOf(ruleHits);
        trendPoints = List.copyOf(trendPoints);
        topSenderDomains = List.copyOf(topSenderDomains);
    }

    public static AnalyticsSummaryResponse from(
            AnalyticsSummaryProjection projection, AnalyticsWindow window) {
        return new AnalyticsSummaryResponse(
                window.id(),
                projection.volumeObserved(),
                projection.volumeApplied(),
                projection.timeSavedSeconds(),
                projection.topSenders().stream().map(TopSenderResponse::from).toList(),
                projection.ruleHits().stream().map(RuleHitResponse::from).toList(),
                projection.trendPoints().stream().map(TrendPointResponse::from).toList(),
                ActionBreakdownResponse.from(projection.actionBreakdown()),
                DeltasResponse.from(projection.deltas()),
                projection.topSenderDomains().stream().map(TopSenderDomainResponse::from).toList(),
                CreditSpendResponse.from(projection.creditSpend()));
    }
    // ... nested records
}
```

**Pattern nested record với private `from()` (lines 32-46):**
```java
public record TrendPointResponse(String date, long observed, long applied) {
    private static TrendPointResponse from(TrendPointProjection projection) {
        return new TrendPointResponse(
                projection.date().toString(),   // LocalDate → ISO-8601 string cho JSON
                projection.observed(),
                projection.applied());
    }
}

// Nullable Long fields cho deltas — null → serialized as JSON null → frontend renders "—"
public record DeltasResponse(Long volumeObservedDeltaPct, Long volumeAppliedDeltaPct, Long timeSavedDeltaPct) {
    private static DeltasResponse from(DeltasProjection projection) {
        return new DeltasResponse(
                projection.volumeObservedDeltaPct(),
                projection.volumeAppliedDeltaPct(),
                projection.timeSavedDeltaPct());
    }
}

public record TopSenderDomainResponse(String domain, long count) {
    private static TopSenderDomainResponse from(TopSenderDomainProjection projection) {
        return new TopSenderDomainResponse(projection.domain(), projection.count());
    }
}
```

---

### `TrendPanel.tsx` (component mới)

**Analog:** `apps/web/features/analytics/components/VolumePanel.tsx` (Card shell + big metric + Info tooltip) + `TimeSavedPanel.tsx` (CardAction với Info button)

**Pattern Card shell với CardAction tooltip (VolumePanel lines 1-72):**
```tsx
'use client';

import { Info } from 'lucide-react';
import { useTranslations } from 'next-intl';
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
// Chart import từ shadcn chart primitive
import { ChartContainer, ChartTooltip, ChartTooltipContent } from '@/components/ui/chart';

type TrendPanelProps = {
  trendPoints?: TrendPointResponse[];
};

export function TrendPanel({ trendPoints = [] }: TrendPanelProps) {
  const t = useTranslations();
  const empty = trendPoints.length === 0;

  return (
    <Card data-testid="analytics-trend-panel">
      <CardHeader className="has-data-[slot=card-action]:grid-cols-[1fr_auto]">
        <CardDescription className="font-mono text-[11px] font-medium tracking-[0.08em]">
          {t('analytics.trend.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-base leading-snug font-medium">{t('analytics.trend.title')}</h3>
        </CardTitle>
        <CardAction>
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  className="text-muted-foreground hover:text-foreground focus-visible:ring-ring grid size-8 place-items-center rounded-md outline-none focus-visible:ring-2"
                  aria-label={t('analytics.trend.tooltip')}
                />
              }
            >
              <Info className="size-4" aria-hidden="true" />
            </TooltipTrigger>
            <TooltipContent>{t('analytics.trend.tooltip')}</TooltipContent>
          </Tooltip>
        </CardAction>
      </CardHeader>
      <CardContent>
        {empty ? (
          <p className="text-muted-foreground text-sm">{t('analytics.trend.empty')}</p>
        ) : (
          <ChartContainer config={chartConfig} className="h-[180px] w-full">
            {/* ResponsiveContainer + LineChart từ Recharts qua shadcn ChartContainer */}
          </ChartContainer>
        )}
      </CardContent>
    </Card>
  );
}
```

**Layout: `md:col-span-2` full-width (D-03) — áp dụng trong `AnalyticsPageClient.tsx`:**
```tsx
<div className="md:col-span-2">
  <TrendPanel trendPoints={summaryQuery.data.trendPoints} />
</div>
```

---

### `NoiseReductionPanel.tsx` (component mới)

**Analog:** `apps/web/features/analytics/components/VolumePanel.tsx` — cùng cấu trúc big metric + supplementary text + empty state

**Pattern (sao chép sát VolumePanel):**
```tsx
'use client';

import { useTranslations } from 'next-intl';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';

type NoiseReductionPanelProps = {
  volumeObserved?: number;
  volumeApplied?: number;
};

function safeCount(value: number | undefined): number {
  return Number.isFinite(value) ? Math.max(0, Math.trunc(value ?? 0)) : 0;
}

export function NoiseReductionPanel({ volumeObserved, volumeApplied }: NoiseReductionPanelProps) {
  const t = useTranslations();
  const observed = safeCount(volumeObserved);
  const applied = safeCount(volumeApplied);
  const empty = observed === 0;
  const pct = empty ? 0 : Math.round((applied / observed) * 100);

  return (
    <Card data-testid="analytics-noise-reduction-panel">
      <CardHeader>
        <CardDescription className="font-mono text-[11px] font-medium tracking-[0.08em]">
          {t('analytics.noiseReduction.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-base leading-snug font-medium">
            {t('analytics.noiseReduction.title')}
          </h3>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <p className="text-foreground font-mono text-[28px] leading-none font-semibold tabular-nums">
          {empty ? '—' : `${pct}%`}
        </p>
        <p className="text-muted-foreground text-sm">
          {empty
            ? t('analytics.noiseReduction.empty')
            : t('analytics.noiseReduction.supplementary', {
                applied,
                observed,
              })}
        </p>
      </CardContent>
    </Card>
  );
}
```

---

### `ActionBreakdownPanel.tsx` (component mới)

**Analog:** `apps/web/features/analytics/components/TopSendersPanel.tsx` — danh sách ngắn + Badge + empty state; kết hợp với donut chart từ `chart.tsx`

**Pattern Card shell (TopSendersPanel lines 1-68):**
```tsx
'use client';

import { useTranslations } from 'next-intl';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ChartContainer, ChartTooltip, ChartTooltipContent } from '@/components/ui/chart';

type ActionBreakdownPanelProps = {
  actionBreakdown?: {
    labelCount: number;
    archiveCount: number;
    saveDraftCount: number;
  };
};

export function ActionBreakdownPanel({ actionBreakdown }: ActionBreakdownPanelProps) {
  const t = useTranslations();
  const total = (actionBreakdown?.labelCount ?? 0)
      + (actionBreakdown?.archiveCount ?? 0)
      + (actionBreakdown?.saveDraftCount ?? 0);
  const empty = total === 0;

  return (
    <Card data-testid="analytics-action-breakdown-panel">
      <CardHeader>
        <CardDescription className="font-mono text-[11px] font-medium tracking-[0.08em]">
          {t('analytics.actionBreakdown.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-base leading-snug font-medium">
            {t('analytics.actionBreakdown.title')}
          </h3>
        </CardTitle>
      </CardHeader>
      <CardContent>
        {empty ? (
          <p className="text-muted-foreground text-sm">{t('analytics.actionBreakdown.empty')}</p>
        ) : (
          <ChartContainer config={chartConfig} className="h-[160px] w-full">
            {/* PieChart (donut) via Recharts */}
          </ChartContainer>
        )}
      </CardContent>
    </Card>
  );
}
```

---

### `CreditsPanel.tsx` (component mới)

**Analog:** `apps/web/features/analytics/components/TimeSavedPanel.tsx` — big metric display + CardAction Info tooltip + empty state

**Pattern (sao chép sát TimeSavedPanel):**
```tsx
'use client';

import { Info } from 'lucide-react';
import { useTranslations } from 'next-intl';
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';

type CreditsPanelProps = {
  creditSpend?: {
    triageCredits: number;
    draftCredits: number;
    previewCredits: number;
    projectedMonthlyCredits: number;
  };
};

export function CreditsPanel({ creditSpend }: CreditsPanelProps) {
  const t = useTranslations();
  const total = (creditSpend?.triageCredits ?? 0)
      + (creditSpend?.draftCredits ?? 0)
      + (creditSpend?.previewCredits ?? 0);
  const empty = total === 0;
  const projected = creditSpend?.projectedMonthlyCredits ?? 0;

  return (
    <Card data-testid="analytics-credits-panel">
      <CardHeader className="has-data-[slot=card-action]:grid-cols-[1fr_auto]">
        <CardDescription className="font-mono text-[11px] font-medium tracking-[0.08em]">
          {t('analytics.credits.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-base leading-snug font-medium">{t('analytics.credits.title')}</h3>
        </CardTitle>
        <CardAction>
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  className="text-muted-foreground hover:text-foreground focus-visible:ring-ring grid size-8 place-items-center rounded-md outline-none focus-visible:ring-2"
                  aria-label={t('analytics.credits.tooltip')}
                />
              }
            >
              <Info className="size-4" aria-hidden="true" />
            </TooltipTrigger>
            <TooltipContent>{t('analytics.credits.tooltip')}</TooltipContent>
          </Tooltip>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-2">
        <p className="text-foreground font-mono text-[28px] leading-none font-semibold tabular-nums">
          {total}
        </p>
        {empty ? (
          <p className="text-muted-foreground text-sm">{t('analytics.credits.empty')}</p>
        ) : (
          <p className="text-muted-foreground text-sm">
            {t('analytics.credits.projected', { credits: projected })}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
```

---

### `TopSendersPanel.tsx` (mở rộng hiện tại)

**Analog:** Chính nó — `apps/web/features/analytics/components/TopSendersPanel.tsx`

**Pattern toggle Sender/Domain — dùng `WindowChips` design (local state, không có URL):**
```tsx
// Thêm import tabs/toggle và local state
import { useState } from 'react';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';

// Trong component:
const [mode, setMode] = useState<'sender' | 'domain'>('sender');

// Toggle chip — bên trong CardHeader hoặc trên danh sách:
<Tabs value={mode} onValueChange={(v) => setMode(v as 'sender' | 'domain')}>
  <TabsList className="h-8 gap-1 p-1">
    <TabsTrigger value="sender" className="h-6 px-2 text-xs">
      {t('analytics.topSenders.bySender')}
    </TabsTrigger>
    <TabsTrigger value="domain" className="h-6 px-2 text-xs">
      {t('analytics.topSenders.byDomain')}
    </TabsTrigger>
  </TabsList>
</Tabs>
```

**Pattern danh sách (limit tăng 3 → 10, giữ cấu trúc `<ol>`):**
```tsx
// Thay: const visibleSenders = senders.slice(0, 3);
const visibleSenders = senders.slice(0, 10);
// Tương tự cho domains
const visibleDomains = senderDomains.slice(0, 10);
```

---

### `RuleHitsPanel.tsx` (mở rộng hiện tại)

**Analog:** Chính nó — `apps/web/features/analytics/components/RuleHitsPanel.tsx`

**Pattern Precision Rate column — frontend computation (D-14):**
```tsx
// Tính precision từ existing data — không cần API field mới
function precisionRate(decisions: number, applied: number): string {
  const safeDecisions = safeCount(decisions);
  if (safeDecisions === 0) return '—';   // D-16: không NaN, không 0%
  return `${Math.round((safeCount(applied) / safeDecisions) * 100)}%`;
}

// Trust Score badge — D-15 thresholds
function trustVariant(decisions: number, applied: number): 'default' | 'secondary' | 'destructive' {
  if (safeCount(decisions) === 0) return 'secondary';  // ẩn hoặc neutral
  const pct = (safeCount(applied) / safeCount(decisions)) * 100;
  if (pct >= 90) return 'default';     // teal — text-teal-600
  if (pct >= 70) return 'secondary';   // amber
  return 'destructive';                // đỏ
}
```

**Pattern thêm column vào table (lines 43-57):**
```tsx
// Thêm vào <TableHeader>
<TableHead className="text-right">
  {t('analytics.ruleHits.column.precision')}
</TableHead>

// Thêm vào mỗi <TableRow>
<TableCell className="text-right py-2">
  {/* Badge hiển thị precision rate */}
  {safeCount(ruleHit.decisions) > 0 && (
    <Badge variant={trustVariant(ruleHit.decisions, ruleHit.applied)}>
      {precisionRate(ruleHit.decisions, ruleHit.applied)}
    </Badge>
  )}
</TableCell>
```

---

### `AnalyticsPageClient.tsx` (mở rộng hiện tại)

**Analog:** Chính nó — `apps/web/features/analytics/components/AnalyticsPageClient.tsx`

**Pattern import + render mới panel (lines 44-72):**
```tsx
// Thêm imports:
import { TrendPanel } from '@/features/analytics/components/TrendPanel';
import { ActionBreakdownPanel } from '@/features/analytics/components/ActionBreakdownPanel';
import { NoiseReductionPanel } from '@/features/analytics/components/NoiseReductionPanel';
import { CreditsPanel } from '@/features/analytics/components/CreditsPanel';

// Cập nhật grid — TrendPanel lên top full-width:
<div className="grid grid-cols-1 gap-4 md:grid-cols-2">
  <div className="md:col-span-2">
    <TrendPanel trendPoints={summaryQuery.data.trendPoints} />
  </div>
  <VolumePanel
    observed={summaryQuery.data.volumeObserved}
    applied={summaryQuery.data.volumeApplied}
  />
  <TimeSavedPanel seconds={summaryQuery.data.timeSavedSeconds} />
  <NoiseReductionPanel
    volumeObserved={summaryQuery.data.volumeObserved}
    volumeApplied={summaryQuery.data.volumeApplied}
  />
  <ActionBreakdownPanel actionBreakdown={summaryQuery.data.actionBreakdown} />
  <TopSendersPanel
    senders={summaryQuery.data.topSenders}
    senderDomains={summaryQuery.data.topSenderDomains}
  />
  <CreditsPanel creditSpend={summaryQuery.data.creditSpend} />
  <div className="md:col-span-2">
    <RuleHitsPanel ruleHits={summaryQuery.data.ruleHits} />
  </div>
</div>
```

---

### `useAnalyticsSummary.ts` (không đổi logic)

**Analog:** Chính nó — `apps/web/features/analytics/hooks/useAnalyticsSummary.ts`

Hook không cần thay đổi. Chỉ cần `AnalyticsSummaryResponse` type (từ `schema.d.ts`) có field mới sau khi chạy OpenAPI codegen. Hook tự động pick up.

```typescript
// Không thay đổi — hook vẫn là:
export function useAnalyticsSummary(window: AnalyticsWindow) {
  return useQuery({
    queryKey: analyticsKeys.summary(window),
    queryFn: () => fetchAnalyticsSummary(window),
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });
}
// Type AnalyticsSummaryResponse expand tự động từ OpenAPI codegen
```

---

### `analytics-api.ts` (thêm type exports)

**Analog:** Chính nó — `apps/web/features/analytics/api/analytics-api.ts`

**Pattern type export từ schema (lines 4-7):**
```typescript
// Thêm exports cho type mới — chỉ re-export từ schema, không có logic
export type TrendPointResponse = components['schemas']['TrendPointResponse'];
export type ActionBreakdownResponse = components['schemas']['ActionBreakdownResponse'];
export type DeltasResponse = components['schemas']['DeltasResponse'];
export type TopSenderDomainResponse = components['schemas']['TopSenderDomainResponse'];
export type CreditSpendResponse = components['schemas']['CreditSpendResponse'];
// AnalyticsSummaryResponse tự động expand (đã export)
```

---

### `messages.ts` (mở rộng i18n)

**Analog:** Chính nó — `apps/web/features/analytics/messages.ts`

**Pattern key format (lines 1-122) — key flat với namespace `analytics.*`:**
```typescript
// Thêm vào analyticsMessages object — giữ cấu trúc {vi, en} cho mỗi key:

// analytics.trend.*
'analytics.trend.eyebrow': { vi: 'XU HƯỚNG', en: 'TREND' },
'analytics.trend.title': { vi: 'Xu hướng email theo ngày', en: 'Daily email trend' },
'analytics.trend.tooltip': {
  vi: 'Số email Zero Mail quan sát và đã phân loại theo từng ngày trong khoảng thời gian đã chọn.',
  en: 'Emails Zero Mail observed and triaged per day in the selected window.',
},
'analytics.trend.empty': { vi: 'Chưa có dữ liệu xu hướng trong khoảng này.', en: 'No trend data in this window.' },
'analytics.trend.observed': { vi: 'Quan sát', en: 'Observed' },
'analytics.trend.applied': { vi: 'Đã phân loại', en: 'Triaged' },

// analytics.actionBreakdown.*
'analytics.actionBreakdown.eyebrow': { vi: 'PHÂN TÍCH HÀNH ĐỘNG', en: 'ACTION BREAKDOWN' },
'analytics.actionBreakdown.title': { vi: 'Zero Mail đã làm gì', en: 'What Zero Mail did' },
'analytics.actionBreakdown.empty': { vi: 'Chưa có hành động trong khoảng này.', en: 'No actions in this window.' },
'analytics.actionBreakdown.label': { vi: 'Gắn nhãn', en: 'Label' },
'analytics.actionBreakdown.archive': { vi: 'Lưu trữ', en: 'Archive' },
'analytics.actionBreakdown.saveDraft': { vi: 'Soạn nháp', en: 'Save draft' },

// analytics.noiseReduction.*
'analytics.noiseReduction.eyebrow': { vi: 'LỌC NHIỄU', en: 'NOISE REDUCTION' },
'analytics.noiseReduction.title': { vi: 'Email đã lọc', en: 'Emails filtered' },
'analytics.noiseReduction.supplementary': {
  vi: '{applied} trên {observed} email đã lọc',
  en: '{applied} of {observed} emails filtered',
},
'analytics.noiseReduction.empty': { vi: 'Chưa có email nào bị lọc trong khoảng này.', en: 'No emails filtered in this window.' },

// analytics.credits.*
'analytics.credits.eyebrow': { vi: 'CREDIT ĐÃ DÙNG', en: 'CREDITS USED' },
'analytics.credits.title': { vi: 'Credit AI đã tiêu thụ', en: 'AI credits consumed' },
'analytics.credits.tooltip': {
  vi: 'Credit tiêu thụ cho Phân loại AI, Soạn nháp, và Xem trước quy tắc trong khoảng thời gian đã chọn. Dự kiến hàng tháng là ước tính tuyến tính.',
  en: 'Credits consumed for AI Triage, Draft Reply, and Rule Preview in the selected window. Monthly projection is a linear estimate.',
},
'analytics.credits.empty': { vi: 'Chưa có credit nào được tiêu thụ trong khoảng này.', en: 'No credits consumed in this window.' },
'analytics.credits.projected': { vi: '~{credits} credits/tháng (ước tính)', en: '~{credits} credits/month (estimated)' },
'analytics.credits.triage': { vi: 'Phân loại AI', en: 'AI Triage' },
'analytics.credits.draft': { vi: 'Soạn nháp AI', en: 'AI Draft Reply' },
'analytics.credits.preview': { vi: 'Xem trước quy tắc', en: 'Rule Preview' },

// analytics.topSenders.* — mở rộng existing namespace
'analytics.topSenders.bySender': { vi: 'Theo người gửi', en: 'By sender' },
'analytics.topSenders.byDomain': { vi: 'Theo tên miền', en: 'By domain' },

// analytics.ruleHits.column.* — mở rộng existing namespace
'analytics.ruleHits.column.precision': { vi: 'Tỷ lệ chính xác', en: 'Precision' },
```

---

### shadcn `chart.tsx` (cài mới)

**Không có analog trong codebase.** Đây là shadcn primitive chưa được cài.

**Cách cài (từ `apps/web`):**
```bash
pnpm dlx shadcn@latest add chart
```

Primitive sẽ xuất hiện tại `apps/web/components/ui/chart.tsx`. Không chỉnh sửa trực tiếp file này (shadcn rule — IN-04 từ Phase 5C review). Wrap Recharts components bên trong `ChartContainer` trong feature component.

**Danh sách shadcn primitives hiện có (không cần cài lại):**
`badge`, `button`, `card`, `checkbox`, `dialog`, `dropdown-menu`, `input`, `label`, `progress`, `radio-group`, `scroll-area`, `select`, `separator`, `sheet`, `sidebar`, `skeleton`, `switch`, `table`, `tabs`, `textarea`, `toggle`, `toggle-group`, `tooltip`, `alert`, `alert-dialog`, `avatar`, `command`, `input-group`, `popover`, `sonner`

**Chưa có (cần cài trước khi dùng):** `chart`

---

## Shared Patterns

### safeCount — Hàm chống NaN

**Source:** `VolumePanel.tsx` (line 21-23), `RuleHitsPanel.tsx` (line 21-23), `TopSendersPanel.tsx` (line 15-17)
**Áp dụng cho:** Tất cả panel component mới

```typescript
function safeCount(value: number | undefined): number {
  return Number.isFinite(value) ? Math.max(0, Math.trunc(value ?? 0)) : 0;
}
```

**Lưu ý:** `safeCount` hiện bị duplicate ở mỗi file panel. CONTEXT.md ghi nhận có thể consolidate vào `analyticsUtils.ts` — planner chọn. Nếu tạo shared util, đặt tại `apps/web/features/analytics/utils/analyticsUtils.ts`.

---

### Card Shell Pattern

**Source:** Tất cả panel hiện có
**Áp dụng cho:** Tất cả panel component mới

```tsx
<Card data-testid="analytics-{name}-panel">
  <CardHeader>                   {/* hoặc với CardAction nếu có Info tooltip */}
    <CardDescription className="font-mono text-[11px] font-medium tracking-[0.08em]">
      {t('analytics.{name}.eyebrow')}    {/* UPPERCASE MONOSPACE label */}
    </CardDescription>
    <CardTitle>
      <h3 className="text-base leading-snug font-medium">{t('analytics.{name}.title')}</h3>
    </CardTitle>
  </CardHeader>
  <CardContent>
    {empty ? (
      <p className="text-muted-foreground text-sm">{t('analytics.{name}.empty')}</p>
    ) : (
      {/* nội dung */}
    )}
  </CardContent>
</Card>
```

---

### Big Metric Display Pattern

**Source:** `VolumePanel.tsx` (line 58), `TimeSavedPanel.tsx` (line 72)
**Áp dụng cho:** `NoiseReductionPanel`, `CreditsPanel`

```tsx
<p className="text-foreground font-mono text-[28px] leading-none font-semibold tabular-nums">
  {value}
</p>
```

---

### Supplementary Text Pattern

**Source:** `VolumePanel.tsx` (lines 61-67)
**Áp dụng cho:** `NoiseReductionPanel`, `CreditsPanel`

```tsx
<p className="text-muted-foreground text-sm">
  {empty
    ? t('analytics.{name}.empty')
    : t('analytics.{name}.supplementary', { param1, param2 })}
</p>
```

---

### Info Tooltip (CardAction) Pattern

**Source:** `VolumePanel.tsx` (lines 40-55), `TimeSavedPanel.tsx` (lines 53-68)
**Áp dụng cho:** `TrendPanel`, `CreditsPanel` (các panel có khái niệm cần giải thích)

```tsx
<CardHeader className="has-data-[slot=card-action]:grid-cols-[1fr_auto]">
  {/* ... */}
  <CardAction>
    <Tooltip>
      <TooltipTrigger
        render={
          <button
            type="button"
            className="text-muted-foreground hover:text-foreground focus-visible:ring-ring grid size-8 place-items-center rounded-md outline-none focus-visible:ring-2"
            aria-label={t('analytics.{name}.tooltip')}
          />
        }
      >
        <Info className="size-4" aria-hidden="true" />
      </TooltipTrigger>
      <TooltipContent>{t('analytics.{name}.tooltip')}</TooltipContent>
    </Tooltip>
  </CardAction>
</CardHeader>
```

---

### Privacy Logging Pattern

**Source:** `AnalyticsSummaryQueryService.java` (lines 106-112), `AnalyticsController.java` (lines 49-52)
**Áp dụng cho:** Tất cả method mới trong `AnalyticsSummaryQueryService`

```java
// Format: event=<name> tenantId={}  + structured fields
// KHÔNG log: sender_email, message body, LLM prompt/completion
log.info(
        "event=analytics_summary_computed tenantId={} windowStart={} windowEnd={}",
        tenantId,
        requestedWindow.startInclusive(),
        requestedWindow.endExclusive());
```

---

### `@Transactional(readOnly = true)` Query Service Pattern

**Source:** `AnalyticsSummaryQueryService.java` (line 87)
**Áp dụng cho:** Tất cả method query mới trong cùng service

Tất cả query mới được gọi bên trong `summarize()` — method này đã có `@Transactional(readOnly = true)`. Không cần annotate lại từng private helper.

---

### Backend Null Guard Pattern

**Source:** `AnalyticsSummaryQueryService.java` (lines 115-123) + Phase 5C WR-07 fix
**Áp dụng cho:** Tất cả `queryForObject` và `query` callback mới

```java
// Với queryForObject — null guard:
Long count = jdbcTemplate.queryForObject(sql, Long.class, ...);
return count == null ? 0L : count;

// Với query callback — null-key skip + fail-loud duplicate:
String key = resultSet.getString("column_name");
if (key == null) {
    return;  // skip null keys defensively
}
map.merge(key, resultSet.getLong("count_col"), (previous, duplicate) -> {
    throw new IllegalStateException("Duplicate key in query: " + key);
});
```

---

### `IdentifiedEnum + fromId` Pattern

**Source:** `AnalyticsWindow.java` (lines 8-36), `CallSite.java` (lines 21-49)
**Áp dụng cho:** Bất kỳ enum mới nào cần serialize/deserialize theo id string

```java
public enum SomeEnum implements IdentifiedEnum {
    VALUE_A("value_a");

    private final String id;
    SomeEnum(String id) { this.id = id; }

    @Override public String id() { return id; }

    public static SomeEnum fromId(String id) {
        return Stream.of(values())
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown SomeEnum id: " + id));
    }
}
```

---

## Anti-Patterns (từ Phase 5C Code Review)

### Phải tránh: Empty body trên error handler

**Source:** WR-01 trong Phase 5C review — `AnalyticsController.invalidWindow` ban đầu trả 400 không có body.

```java
// SAI — đã fix ở Phase 5C, đừng lặp lại:
@ExceptionHandler(SomeException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
void invalidWindow() {}

// ĐÚNG — dùng ProblemDetail:
@ExceptionHandler(InvalidAnalyticsWindowException.class)
ResponseEntity<ProblemDetail> invalidWindow(InvalidAnalyticsWindowException exception) {
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problemDetail.setTitle("...");
    problemDetail.setDetail("...");
    problemDetail.setProperty("code", ErrorCodes.BAD_REQUEST);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
}
```

### Phải tránh: Silent duplicate key trong query map

**Source:** WR-07 trong Phase 5C review — `queryAppliedByActionType` ban đầu dùng `put()` thay vì `merge()`.

```java
// SAI:
map.put(key, value);  // silently overwrites

// ĐÚNG (đã chuẩn hóa trong Phase 5C):
map.merge(key, value, (previous, duplicate) -> {
    throw new IllegalStateException("Duplicate key: " + key);
});
```

### Phải tránh: Chỉnh sửa shadcn primitive source trực tiếp

**Source:** IN-04 trong Phase 5C review

Không chỉnh sửa `apps/web/components/ui/chart.tsx` sau khi cài. Tạo wrapper trong `features/analytics/components/` nếu cần customization.

### Phải tránh: `safeCount` duplicate không có shared util

`safeCount` hiện tại được copy vào từng panel file. Nếu tạo panel thứ 5+, xem xét extract ra `analyticsUtils.ts`. Không tạo biến thể khác nhau của hàm này.

### Phải tránh: Null thay vì "—" cho metrics không có dữ liệu

**Source:** D-16 trong CONTEXT.md + `VolumePanel.tsx` empty state pattern.

```tsx
// SAI — render NaN hoặc null:
<p>{pct}%</p>  // NaN% khi observed === 0

// ĐÚNG:
<p>{empty ? '—' : `${pct}%`}</p>
```

### Phải tránh: `REQUIRES_NEW` propagation trên cleanup methods

**Source:** CR-02 trong Phase 5C review.

```java
// SAI — method bị cô lập khỏi transaction cha:
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void deleteForTenant(UUID tenantId) { ... }

// ĐÚNG — join outer transaction:
@Transactional
public void deleteForTenant(UUID tenantId) { ... }
```

---

## Files Không Có Analog (cần tham khảo RESEARCH.md / docs)

| File | Role | Reason |
|------|------|---------|
| `apps/web/components/ui/chart.tsx` | ui-primitive | shadcn chart primitive chưa tồn tại trong project — cài qua `pnpm dlx shadcn@latest add chart` |
| Recharts config (chartConfig, CartesianGrid, etc.) trong TrendPanel / ActionBreakdownPanel | component config | Không có Recharts usage nào trong codebase — tham khảo shadcn/ui chart docs và Recharts docs |

---

## Metadata

**Analog search scope:** `apps/web/features/analytics/`, `backend/core/src/main/java/com/zeromail/core/analytics/`, `backend/api/src/main/java/com/zeromail/api/dto/analytics/`, `apps/web/components/ui/`, `apps/web/i18n/messages/`
**Files scanned:** ~20 source files + 2 review files (Phase 5C)
**Pattern extraction date:** 2026-05-15
