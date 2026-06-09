// TODO: regenerate admin-schema after backend deployment and replace with typed api client
// (GET /api/admin/feedback will appear in admin-schema.d.ts after regen)
import { getAdminApiBase } from '@/lib/api/admin-base-url';

export type FeedbackType = 'BUG_REPORT' | 'FEATURE_REQUEST' | 'GENERAL';
export type FeedbackStatus = 'OPEN' | 'RESOLVED';

export type FeedbackRow = {
  id: string;
  tenantId: string | null;
  type: FeedbackType;
  subject: string;
  message: string;
  contactEmail: string;
  status: FeedbackStatus;
  adminNotes: string | null;
  resolvedAt: string | null;
  createdAt: string;
};

export type FeedbackListResponse = {
  rows: FeedbackRow[];
  openCount: number;
};

export type FeedbackStatusFilter = FeedbackStatus | 'ALL';

function readXsrfCookie(): string | undefined {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

function xsrfHeaders(): HeadersInit {
  const token = readXsrfCookie();
  return token ? { 'X-XSRF-TOKEN': token } : {};
}

export async function fetchFeedbackList(
  status: FeedbackStatusFilter,
  limit = 50,
): Promise<FeedbackListResponse> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (status !== 'ALL') params.set('status', status);
  const response = await fetch(`${getAdminApiBase()}/api/admin/feedback?${params}`, {
    credentials: 'include',
  });
  if (!response.ok) throw new Error(`Không thể tải danh sách feedback: ${response.status}`);
  return response.json() as Promise<FeedbackListResponse>;
}

export async function resolveFeedback(id: string, adminNotes?: string): Promise<void> {
  const response = await fetch(`${getAdminApiBase()}/api/admin/feedback/${id}/resolve`, {
    method: 'PATCH',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...xsrfHeaders() },
    body: JSON.stringify({ adminNotes: adminNotes ?? null }),
  });
  if (!response.ok) throw new Error(`Không thể resolve feedback: ${response.status}`);
}

export async function reopenFeedback(id: string): Promise<void> {
  const response = await fetch(`${getAdminApiBase()}/api/admin/feedback/${id}/reopen`, {
    method: 'PATCH',
    credentials: 'include',
    headers: { ...xsrfHeaders() },
  });
  if (!response.ok) throw new Error(`Không thể reopen feedback: ${response.status}`);
}
