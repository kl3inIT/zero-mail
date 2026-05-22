import { api } from '@/lib/api/admin-client';
import type { components } from '@/lib/api/admin-schema';

export type QueueHealth = components['schemas']['QueueHealthResponse'];
export type QueueDepthByType = NonNullable<QueueHealth['depthByType']>[number];
export type RetryDistributionBucket = NonNullable<QueueHealth['retryHistogram']>[number];
export type DeadLetterRow = components['schemas']['DeadLetterRowResponse'];
export type DeadLetterPage = components['schemas']['DeadLetterPageResponse'];

export type RequeueInput = {
  jobId: string;
  reason: string;
};

export type RequeueResult = {
  auditId?: string | null;
};

export async function fetchQueueHealth(): Promise<QueueHealth> {
  const { data, error } = await api.GET('/api/admin/queue/health');
  if (error || !data) {
    throw new Error('Không thể tải tình trạng hàng đợi.');
  }
  return data;
}

export async function fetchDeadLetters(
  cursor: string | null,
  limit = 25,
): Promise<DeadLetterPage> {
  const { data, error } = await api.GET('/api/admin/queue/dead-letters', {
    params: {
      query: {
        cursor: cursor ?? undefined,
        limit,
      },
    },
  });
  if (error || !data) {
    throw new Error('Không thể tải danh sách dead-letter.');
  }
  return data;
}

export async function requeueDeadLetter(input: RequeueInput): Promise<RequeueResult> {
  const { error } = await api.POST('/api/admin/queue/dead-letters/{jobId}/requeue', {
    params: { path: { jobId: input.jobId } },
    body: { reason: input.reason },
  });
  if (error) {
    throw new Error('Không thể requeue dead-letter.');
  }
  // Backend currently returns 204 No Content. A future change will surface the
  // admin_audit_event id (Location header or 201 body) — pass it through then.
  return {};
}

export function shortJobToken(jobId: string): string {
  return jobId.replace(/-/g, '').slice(0, 8);
}
