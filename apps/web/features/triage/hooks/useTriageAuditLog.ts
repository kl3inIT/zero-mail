'use client';

import { useInfiniteQuery, type InfiniteData } from '@tanstack/react-query';

import {
  getAuditLog,
  type AuditEntry,
  type AuditLogFilters,
  type AuditLogPage,
} from '@/features/triage/api/triage-api';
import { triageKeys } from '@/features/triage/query-keys';

export function useTriageAuditLog(filters: AuditLogFilters = {}) {
  return useInfiniteQuery({
    queryKey: triageKeys.auditLog(filters),
    queryFn: ({ pageParam }) => getAuditLog({ ...filters, cursor: pageParam, limit: 50 }),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  });
}

export function flattenAuditEntries(data: InfiniteData<AuditLogPage> | undefined): AuditEntry[] {
  return data?.pages.flatMap((page) => page.entries) ?? [];
}
