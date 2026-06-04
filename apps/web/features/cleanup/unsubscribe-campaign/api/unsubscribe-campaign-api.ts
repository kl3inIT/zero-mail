import { getApiUrl } from '@/lib/api/base-url';
import { api, xsrfHeader } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';
import {
  appendDateRangeSpec,
  type DateRangeSpec,
} from '@/features/cleanup/unsubscribe-campaign/date-range-spec';

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
  spec: DateRangeSpec,
  limit: number = 25,
): Promise<UnsubscribeCandidateResponse[]> {
  // The typed openapi-fetch client only knows about the legacy `window=7d|30d|90d` query
  // shape because the schema has not been regenerated since the date-range params were added.
  // Drop down to manual URLSearchParams so the request carries `startDate=/endDate=` when the
  // user picks a custom calendar range, and fall back to the same `window=...` shape the
  // typed client used to emit for the preset case.
  const searchParams = new URLSearchParams({ limit: String(limit) });
  appendDateRangeSpec(searchParams, spec, { windowParamName: 'window' });
  const response = await fetch(
    getApiUrl(`/api/unsubscribe/candidates?${searchParams.toString()}`),
    { credentials: 'include' },
  );
  if (!response.ok) {
    throw new Error(`/api/unsubscribe/candidates failed: ${response.status}`);
  }
  const data = (await response.json()) as
    | UnsubscribeCandidateListResponse
    | UnsubscribeCandidateResponse[];
  if (Array.isArray(data)) return data;
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
  const response = await fetch(getApiUrl('/api/unsubscribe/senders/action'), {
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
  spec: DateRangeSpec,
): Promise<SenderTimelineEntry[]> {
  const searchParams = new URLSearchParams({ senderEmail });
  appendDateRangeSpec(searchParams, spec);
  const response = await fetch(
    getApiUrl(`/api/unsubscribe/stats/timeline?${searchParams.toString()}`),
    { credentials: 'include' },
  );
  if (!response.ok) {
    throw new Error(`/api/unsubscribe/stats/timeline failed: ${response.status}`);
  }
  return (await response.json()) as SenderTimelineEntry[];
}

export async function fetchSenderMessages(
  senderEmail: string,
  archivedOnly: boolean,
  limit: number,
  spec: DateRangeSpec,
): Promise<SenderMessageSummary[]> {
  const searchParams = new URLSearchParams({
    senderEmail,
    archivedOnly: String(archivedOnly),
    limit: String(limit),
  });
  appendDateRangeSpec(searchParams, spec);
  const response = await fetch(
    getApiUrl(`/api/unsubscribe/stats/messages?${searchParams.toString()}`),
    { credentials: 'include' },
  );
  if (!response.ok) {
    throw new Error(`/api/unsubscribe/stats/messages failed: ${response.status}`);
  }
  return (await response.json()) as SenderMessageSummary[];
}

export async function fetchSenderMessageBody(
  gmailMessageId: string,
): Promise<SenderMessageBody | null> {
  const response = await fetch(
    getApiUrl(`/api/unsubscribe/stats/messages/${encodeURIComponent(gmailMessageId)}/body`),
    { credentials: 'include' },
  );
  if (response.status === 404) return null;
  if (!response.ok) {
    throw new Error(`/api/unsubscribe/stats/messages body failed: ${response.status}`);
  }
  return (await response.json()) as SenderMessageBody;
}
