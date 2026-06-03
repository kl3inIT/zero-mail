import { api, xsrfHeader } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type UnsubscribeCandidateResponse = components['schemas']['UnsubscribeCandidateResponse'];
export type UnsubscribeCandidateListResponse =
  components['schemas']['UnsubscribeCandidateListResponse'];
export type CampaignExecuteRequest = components['schemas']['CampaignExecuteRequest'];
export type CampaignExecuteResponse = components['schemas']['CampaignExecuteResponse'];

export type CleanupSenderAction =
  | 'APPROVE'
  | 'UNAPPROVE'
  | 'MARK_UNSUBSCRIBED'
  | 'AUTO_ARCHIVE'
  | 'ARCHIVE'
  | 'DELETE'
  | 'LABEL_FUTURE';

export type CleanupSenderActionRequest = {
  action: CleanupSenderAction;
  senderEmails: string[];
  labelName?: string;
};

export type CleanupSenderActionResponse = {
  senderCount: number;
  affectedMessageCount: number;
  failedMessageCount: number;
};

function jsonHeaders(): HeadersInit {
  return { 'Content-Type': 'application/json', ...xsrfHeader() };
}

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export async function fetchCandidates(
  window: string = '30d',
  limit: number = 25,
): Promise<UnsubscribeCandidateResponse[]> {
  const result = await api.GET('/api/unsubscribe/candidates', {
    params: { query: { window, limit } },
  });
  const data = unwrap(result, `/api/unsubscribe/candidates failed: ${result.response.status}`);
  // Defensively unwrap either { items: [...] } or bare array (Playwright fixtures use the bare
  // array form).
  if (Array.isArray(data)) {
    return data as UnsubscribeCandidateResponse[];
  }
  return data.items ?? [];
}

export async function executeCampaign(
  body: CampaignExecuteRequest,
): Promise<CampaignExecuteResponse> {
  const result = await api.POST('/api/unsubscribe/campaigns/execute', {
    body,
    headers: jsonHeaders(),
  });
  return unwrap(result, `/api/unsubscribe/campaigns/execute failed: ${result.response.status}`);
}

export async function runSenderAction(
  body: CleanupSenderActionRequest,
): Promise<CleanupSenderActionResponse> {
  // TODO(openapi): switch back to the typed api.POST once /api/unsubscribe/senders/action lands
  // in lib/api/schema.d.ts. The backend CleanupSenderActionController already exposes this
  // endpoint; the committed OpenAPI spec is just stale. Fix: boot backend + run
  // `pnpm --filter web run generate:api`, then restore api.POST and drop this raw fetch.
  const response = await fetch('/api/unsubscribe/senders/action', {
    method: 'POST',
    credentials: 'include',
    headers: jsonHeaders(),
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`/api/unsubscribe/senders/action failed: ${response.status}`);
  }
  return (await response.json()) as CleanupSenderActionResponse;
}

// Stats dialog endpoints (UNS-stats-01/02/03). Types are hand-written here until the
// OpenAPI regen sweeps them into `lib/api/schema.d.ts`.

export type SenderTimelineEntry = {
  date: string;
  count: number;
};

export type SenderMessageSummary = {
  gmailMessageId: string;
  gmailThreadId: string;
  subject: string;
  snippet: string;
  internalDate: string;
  archived: boolean;
  unread: boolean;
};

export type SenderMessageBody = {
  gmailMessageId: string;
  subject: string;
  fromHeader: string;
  internalDate: string;
  htmlBody?: string | null;
  plainBody?: string | null;
};

export async function fetchSenderTimeline(
  senderEmail: string,
  windowDays: number,
): Promise<SenderTimelineEntry[]> {
  const searchParams = new URLSearchParams({
    senderEmail,
    windowDays: String(windowDays),
  });
  const response = await fetch(`/api/unsubscribe/stats/timeline?${searchParams.toString()}`, {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`/api/unsubscribe/stats/timeline failed: ${response.status}`);
  }
  return (await response.json()) as SenderTimelineEntry[];
}

export async function fetchSenderMessages(
  senderEmail: string,
  archivedOnly: boolean,
  limit: number,
): Promise<SenderMessageSummary[]> {
  const searchParams = new URLSearchParams({
    senderEmail,
    archivedOnly: String(archivedOnly),
    limit: String(limit),
  });
  const response = await fetch(`/api/unsubscribe/stats/messages?${searchParams.toString()}`, {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`/api/unsubscribe/stats/messages failed: ${response.status}`);
  }
  return (await response.json()) as SenderMessageSummary[];
}

export async function fetchSenderMessageBody(
  gmailMessageId: string,
): Promise<SenderMessageBody | null> {
  const response = await fetch(
    `/api/unsubscribe/stats/messages/${encodeURIComponent(gmailMessageId)}/body`,
    { credentials: 'include' },
  );
  if (response.status === 404) return null;
  if (!response.ok) {
    throw new Error(`/api/unsubscribe/stats/messages body failed: ${response.status}`);
  }
  return (await response.json()) as SenderMessageBody;
}
