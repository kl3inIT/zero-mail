'use client';

import { useInfiniteQuery, type InfiniteData } from '@tanstack/react-query';

import { getAuditLog, type AuditEntry, type AuditLogPage } from '@/features/triage/api/triage-api';
import { triageKeys } from '@/features/triage/query-keys';

// GAP: no backend triage-audit list endpoint as of 05A - see
// 05A-RESEARCH.md A4. The first page is an explicit unavailable sentinel,
// not an empty list, so the UI can render a distinct degradation state.
export function useTriageAuditLog() {
  return useInfiniteQuery({
    queryKey: triageKeys.auditLog(),
    queryFn: ({ pageParam }) => getAuditLog({ cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) =>
      lastPage.unavailable ? undefined : (lastPage.nextCursor ?? undefined),
  });
}

export function isAuditLogUnavailable(data: InfiniteData<AuditLogPage> | undefined): boolean {
  return Boolean(data?.pages.some((page) => page.unavailable));
}

export function flattenAuditEntries(data: InfiniteData<AuditLogPage> | undefined): AuditEntry[] {
  return data?.pages.flatMap((page) => page.entries) ?? [];
}
