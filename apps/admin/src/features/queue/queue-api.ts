// Queue feature uses raw fetch because /api/admin/queue/** is not yet in the regenerated
// openapi-typescript schema (`apps/admin/src/lib/api/admin-schema.d.ts`). Per CLAUDE.md
// Convention 8, raw fetch is allowed "temporarily missing schema with an explicit TODO".
// TODO(08-8E follow-up): run `pnpm --filter @zeromail/admin generate-api` against a running
// backend at localhost:8080 and migrate this file to the typed `api.GET / api.POST` client.

import { getAdminApiUrl } from '@/lib/api/admin-base-url';

export type QueueDepthByType = {
  jobType: string;
  pendingCount: number;
  processingCount: number;
};

export type RetryDistributionBucket = {
  attemptsBucket: number;
  rowCount: number;
};

export type QueueHealth = {
  depthByType: QueueDepthByType[];
  oldestUnleasedJobAgeSeconds: number;
  retryHistogram: RetryDistributionBucket[];
  failureRateLast24h: number;
  deadLetterCount: number;
  adminRequeuedLast24h: number;
  snapshotAt: string;
};

export type DeadLetterRow = {
  jobId: string;
  jobType: string;
  lastFailureReason: string | null;
  retryCount: number;
  adminRequeueCount: number;
  lastFailedAt: string | null;
  createdAt: string;
};

export type DeadLetterPage = {
  rows: DeadLetterRow[];
  nextCursor: string | null;
  totalEstimate: number;
  hasNextPage: boolean;
};

export type RequeueInput = {
  jobId: string;
  reason: string;
};

export type RequeueResult = {
  auditId?: string | null;
};

async function fetchJson<T>(path: `/${string}`, init?: RequestInit): Promise<T> {
  const response = await fetch(getAdminApiUrl(path), {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!response.ok) {
    throw new Error(`Yêu cầu thất bại: ${response.status} ${response.statusText}`);
  }
  if (response.status === 204) {
    // Caller is expected to coerce a no-content response into a non-data result.
    return undefined as T;
  }
  return (await response.json()) as T;
}

export async function fetchQueueHealth(): Promise<QueueHealth> {
  return fetchJson<QueueHealth>('/api/admin/queue/health');
}

export async function fetchDeadLetters(
  cursor: string | null,
  limit = 25,
): Promise<DeadLetterPage> {
  const params = new URLSearchParams();
  if (cursor) params.set('cursor', cursor);
  params.set('limit', String(limit));
  return fetchJson<DeadLetterPage>(`/api/admin/queue/dead-letters?${params.toString()}`);
}

export async function requeueDeadLetter(input: RequeueInput): Promise<RequeueResult> {
  await fetchJson<void>(`/api/admin/queue/dead-letters/${input.jobId}/requeue`, {
    method: 'POST',
    body: JSON.stringify({ reason: input.reason }),
  });
  // Backend currently returns 204 No Content with no body. WR-10: do not fabricate an
  // audit id — let the dialog render "Action recorded." A future backend change will
  // surface the real admin_audit_event.id (Location header or 201 body) and we'll pass
  // it through here.
  return {};
}

export function shortJobToken(jobId: string): string {
  return jobId.replace(/-/g, '').slice(0, 8);
}
