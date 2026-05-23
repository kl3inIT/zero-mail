import { getAdminApiUrl } from '@/lib/api/admin-base-url';

// TODO: regenerate apps/admin/src/lib/api/admin-schema.d.ts via
// `pnpm --filter @zeromail/admin run generate-api` once the backend boots locally,
// then swap these raw-fetch helpers for the typed `api.GET` / `api.POST` from
// `@/lib/api/admin-client`. AGENTS.md explicitly allows raw fetch only for
// "temporarily missing schema with an explicit TODO".

export type WaitlistStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'INVITED'
  | 'INVITE_FAILED';

export const WAITLIST_STATUSES: ReadonlyArray<WaitlistStatus> = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'INVITED',
  'INVITE_FAILED',
];

export type WaitlistEntry = {
  id: string;
  email: string;
  status: WaitlistStatus;
  source: string | null;
  createdAt: string;
  approvedAt: string | null;
  approvedByAdminId: string | null;
  inviteSentAt: string | null;
  inviteNextAttemptAt: string | null;
  inviteFailureReason: string | null;
};

export type WaitlistListResponse = {
  items: WaitlistEntry[];
  totalElements: number;
  page: number;
  size: number;
};

export type WaitlistListQuery = {
  status?: WaitlistStatus;
  page?: number;
  size?: number;
};

function readXsrfCookie(): string | undefined {
  if (typeof document === 'undefined') return undefined;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

function mutatingHeaders(): HeadersInit {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = readXsrfCookie();
  if (token) headers['X-XSRF-TOKEN'] = token;
  return headers;
}

async function parseOrThrow<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (response.status === 401 && typeof window !== 'undefined') {
    window.location.assign('/login');
    throw new Error('Phiên đăng nhập đã hết hạn.');
  }
  if (!response.ok) {
    let problemDetail: { detail?: string; title?: string } | null = null;
    try {
      problemDetail = (await response.json()) as { detail?: string; title?: string };
    } catch {
      // ignore parse failure
    }
    throw new Error(problemDetail?.title ?? problemDetail?.detail ?? fallbackMessage);
  }
  return (await response.json()) as T;
}

export async function fetchWaitlistList(
  query: WaitlistListQuery,
): Promise<WaitlistListResponse> {
  const searchParams = new URLSearchParams();
  if (query.status) searchParams.set('status', query.status);
  if (query.page !== undefined) searchParams.set('page', String(query.page));
  if (query.size !== undefined) searchParams.set('size', String(query.size));
  const qs = searchParams.toString();
  const response = await fetch(
    getAdminApiUrl(`/api/admin/waitlist${qs ? `?${qs}` : ''}`),
    { credentials: 'include' },
  );
  return parseOrThrow<WaitlistListResponse>(
    response,
    'Không tải được danh sách đăng ký chờ.',
  );
}

export async function approveWaitlistEntry(waitlistId: string): Promise<WaitlistEntry> {
  const response = await fetch(
    getAdminApiUrl(`/api/admin/waitlist/${waitlistId}/approve`),
    {
      method: 'POST',
      headers: mutatingHeaders(),
      credentials: 'include',
    },
  );
  return parseOrThrow<WaitlistEntry>(response, 'Không duyệt được đăng ký.');
}

export async function rejectWaitlistEntry(waitlistId: string): Promise<WaitlistEntry> {
  const response = await fetch(
    getAdminApiUrl(`/api/admin/waitlist/${waitlistId}/reject`),
    {
      method: 'POST',
      headers: mutatingHeaders(),
      credentials: 'include',
    },
  );
  return parseOrThrow<WaitlistEntry>(response, 'Không từ chối được đăng ký.');
}
