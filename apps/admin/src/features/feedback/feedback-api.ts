import { api } from '@/lib/api/admin-client';
import type { components } from '@/lib/api/admin-schema';

export type FeedbackRow = components['schemas']['FeedbackRowResponse'];
export type FeedbackListResponse = components['schemas']['FeedbackListResponse'];
export type FeedbackType = FeedbackRow['type'];
export type FeedbackStatus = FeedbackRow['status'];
export type FeedbackStatusFilter = FeedbackStatus | 'ALL';

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  message: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(message);
  }
  return result.data;
}

export async function fetchFeedbackList(
  status: FeedbackStatusFilter,
  limit = 50,
): Promise<FeedbackListResponse> {
  const result = await api.GET('/api/admin/feedback', {
    params: {
      query: {
        status: status === 'ALL' ? undefined : status,
        limit,
      },
    },
  });
  return unwrap(result, `Không thể tải danh sách feedback: ${result.response.status}`);
}

export async function resolveFeedback(id: string, adminNotes?: string): Promise<void> {
  const result = await api.PATCH('/api/admin/feedback/{id}/resolve', {
    params: { path: { id } },
    body: { adminNotes: adminNotes ?? undefined },
  });
  if (!result.response.ok) {
    throw result.error ?? new Error(`Không thể resolve feedback: ${result.response.status}`);
  }
}

export async function reopenFeedback(id: string): Promise<void> {
  const result = await api.PATCH('/api/admin/feedback/{id}/reopen', {
    params: { path: { id } },
  });
  if (!result.response.ok) {
    throw result.error ?? new Error(`Không thể reopen feedback: ${result.response.status}`);
  }
}
