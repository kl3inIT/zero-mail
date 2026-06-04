// Time-window spec passed to the bulk-unsubscribe candidates query, the
// stats timeline chart, and the stats messages list. A preset matches the
// legacy `7d`/`30d`/`90d` chips; a custom range carries two ISO `yyyy-MM-dd`
// dates from the calendar picker.
//
// Encoded into URLSearchParams by `appendDateRangeSpec`, which keeps the
// backend contract in one spot — every endpoint accepts either
// `window=7d|30d|90d|windowDays=N` OR `startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`.

export type DateRangeSpec =
  | { kind: 'window'; windowDays: number }
  | { kind: 'range'; startDate: string; endDate: string };

export function appendDateRangeSpec(
  searchParams: URLSearchParams,
  spec: DateRangeSpec,
  options: { windowParamName?: 'window' | 'windowDays' } = {},
): void {
  if (spec.kind === 'window') {
    const paramName = options.windowParamName ?? 'windowDays';
    if (paramName === 'window') {
      searchParams.set('window', `${spec.windowDays}d`);
    } else {
      searchParams.set('windowDays', String(spec.windowDays));
    }
    return;
  }
  searchParams.set('startDate', spec.startDate);
  searchParams.set('endDate', spec.endDate);
}

// Stable key fragment for TanStack queryKey deduplication. Returning a
// primitive string keeps cache lookups O(1) and avoids spec-object identity
// surprises across renders.
export function dateRangeSpecCacheKey(spec: DateRangeSpec): string {
  return spec.kind === 'window'
    ? `window:${spec.windowDays}`
    : `range:${spec.startDate}:${spec.endDate}`;
}
