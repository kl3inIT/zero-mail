import { api } from '@/lib/api/admin-client';
import type { components } from '@/lib/api/admin-schema';

export type QueueHealth = components['schemas']['QueueHealthResponse'];
export type QueueDepthByType = NonNullable<QueueHealth['depthByType']>[number];
export type RetryDistributionBucket = NonNullable<QueueHealth['retryHistogram']>[number];
export type JobRow = components['schemas']['JobRowResponse'];
export type JobPage = components['schemas']['JobPageResponse'];
export type JobDetail = components['schemas']['JobDetailResponse'];

export type JobsFilter = {
  status?: string | null;
  jobType?: string | null;
};

export type JobActionInput = {
  jobId: string;
  reason: string;
};

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

export async function fetchJobs(
  filter: JobsFilter,
  cursor: string | null,
  limit = 25,
): Promise<JobPage> {
  const { data, error } = await api.GET('/api/admin/queue/jobs', {
    params: {
      query: {
        status: filter.status ?? undefined,
        jobType: filter.jobType ?? undefined,
        cursor: cursor ?? undefined,
        limit,
      },
    },
  });
  if (error || !data) {
    throw new Error('Không thể tải danh sách công việc.');
  }
  return data;
}

export async function fetchJobDetail(jobId: string): Promise<JobDetail> {
  const { data, error } = await api.GET('/api/admin/queue/jobs/{jobId}', {
    params: { path: { jobId } },
  });
  if (error || !data) {
    throw new Error('Không thể tải chi tiết công việc.');
  }
  return data;
}

export async function forceRetryJob(input: JobActionInput): Promise<RequeueResult> {
  const { error } = await api.POST('/api/admin/queue/jobs/{jobId}/force-retry', {
    params: { path: { jobId: input.jobId } },
    body: { reason: input.reason },
  });
  if (error) {
    throw new Error('Không thể chạy lại công việc.');
  }
  return {};
}

export async function cancelJob(input: JobActionInput): Promise<RequeueResult> {
  const { error } = await api.POST('/api/admin/queue/jobs/{jobId}/cancel', {
    params: { path: { jobId: input.jobId } },
    body: { reason: input.reason },
  });
  if (error) {
    throw new Error('Không thể hủy công việc.');
  }
  return {};
}

export function shortJobToken(jobId: string): string {
  return jobId.replace(/-/g, '').slice(0, 8);
}
