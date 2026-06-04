import { getApiUrl } from '@/lib/api/base-url';
import { xsrfHeader } from '@/lib/api/client';

// TODO: Switch this feature to the generated OpenAPI client after
// :backend:api:generateOpenApiDocs can run under the project JDK 25 locally.
export type GmailInboxMessageResponse = {
  gmailMessageId?: string;
  gmailThreadId?: string;
  subject?: string;
  snippet?: string;
  from?: string;
  to?: string[];
  cc?: string[];
  receivedAt?: string;
  labelIds?: string[];
  labels?: GmailInboxLabelResponse[];
  unread?: boolean;
  hasAttachment?: boolean;
  openInGmailUrl?: string;
};

export type GmailInboxLabelResponse = {
  id?: string;
  name?: string;
};

export type InboxDataSource = 'PROJECTION' | 'LIVE_GMAIL' | 'SYNCING';

export type GmailInboxPageResponse = {
  items?: GmailInboxMessageResponse[];
  nextCursor?: string | null;
  loadedCount?: number;
  maxMessages?: number;
  dataSource?: InboxDataSource;
};

export type GmailInboxMessageDetailResponse = {
  message?: GmailInboxMessageResponse;
  renderedText?: string;
  renderedHtml?: string;
};

export type InboxMessage = {
  gmailMessageId: string;
  gmailThreadId: string;
  subject: string;
  snippet: string;
  from: string;
  to: string[];
  cc: string[];
  receivedAt: string;
  labelIds: string[];
  labels: InboxLabel[];
  unread: boolean;
  hasAttachment: boolean;
  openInGmailUrl: string;
};

export type InboxLabel = {
  id: string;
  name: string;
};

export type InboxPage = {
  items: InboxMessage[];
  nextCursor: string | null;
  loadedCount: number;
  maxMessages: number;
  dataSource: InboxDataSource;
};

export type InboxMessageDetail = {
  message: InboxMessage;
  renderedText: string;
  renderedHtml: string;
};

export type GmailThreadDetailResponse = {
  gmailThreadId?: string;
  subject?: string;
  messages?: GmailInboxMessageDetailResponse[];
};

/** A full conversation: every message in the thread (received + the user's own sent replies). */
export type InboxThreadDetail = {
  gmailThreadId: string;
  subject: string;
  messages: InboxMessageDetail[];
};

export class InboxApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'InboxApiError';
    this.status = status;
  }
}

function normalizeMessage(message: GmailInboxMessageResponse): InboxMessage | null {
  if (!message.gmailMessageId || !message.gmailThreadId) return null;
  const labelIds = message.labelIds ?? [];
  return {
    gmailMessageId: message.gmailMessageId,
    gmailThreadId: message.gmailThreadId,
    subject: message.subject ?? '',
    snippet: message.snippet ?? '',
    from: message.from ?? '',
    to: message.to ?? [],
    cc: message.cc ?? [],
    receivedAt: message.receivedAt ?? new Date(0).toISOString(),
    labelIds,
    labels: normalizeLabels(labelIds, message.labels),
    unread: Boolean(message.unread),
    hasAttachment: Boolean(message.hasAttachment),
    openInGmailUrl:
      message.openInGmailUrl ??
      `https://mail.google.com/mail/u/0/#inbox/${encodeURIComponent(message.gmailThreadId)}`,
  };
}

function normalizeLabels(
  labelIds: string[],
  labels: GmailInboxLabelResponse[] | undefined,
): InboxLabel[] {
  if (labels?.length) {
    return labels
      .filter((label): label is { id: string; name?: string } => Boolean(label.id))
      .map((label) => ({ id: label.id, name: label.name ?? label.id }));
  }
  return labelIds.map((labelId) => ({ id: labelId, name: labelId }));
}

function normalizePage(response: GmailInboxPageResponse): InboxPage {
  return {
    items: (response.items ?? [])
      .map(normalizeMessage)
      .filter((message): message is InboxMessage => message !== null),
    nextCursor: response.nextCursor ?? null,
    loadedCount: response.loadedCount ?? 0,
    maxMessages: response.maxMessages ?? 100,
    // Backend < Wave 3 omitted dataSource; legacy responses are effectively live Gmail.
    dataSource: response.dataSource ?? 'LIVE_GMAIL',
  };
}

function normalizeDetail(response: GmailInboxMessageDetailResponse): InboxMessageDetail {
  const message = response.message ? normalizeMessage(response.message) : null;
  if (!message) {
    throw new InboxApiError('Malformed inbox message detail', 500);
  }
  return {
    message,
    renderedText: response.renderedText ?? '',
    renderedHtml: response.renderedHtml ?? '',
  };
}

function throwApiError(result: { response: Response }, fallbackMessage: string): never {
  throw new InboxApiError(fallbackMessage, result.response.status);
}

async function getJson<TResponse>(path: string): Promise<{ response: Response; data?: TResponse }> {
  const response = await fetch(getApiUrl(path as `/${string}`), {
    credentials: 'include',
  });
  if (!response.ok) {
    return { response };
  }
  return { response, data: (await response.json()) as TResponse };
}

async function postNoContent(path: string): Promise<Response> {
  return fetch(getApiUrl(path as `/${string}`), {
    method: 'POST',
    credentials: 'include',
    headers: xsrfHeader(),
  });
}

export async function getInboxPage({
  cursor,
  limit = 20,
}: {
  cursor?: string | null;
  limit?: number;
} = {}): Promise<InboxPage> {
  const searchParams = new URLSearchParams({ limit: String(limit) });
  if (cursor) searchParams.set('cursor', cursor);
  const result = await getJson<GmailInboxPageResponse>(
    `/api/gmail/inbox?${searchParams.toString()}`,
  );

  if (!result.response.ok || result.data === undefined) {
    throwApiError(result, `/api/gmail/inbox failed: ${result.response.status}`);
  }
  return normalizePage(result.data);
}

export async function getInboxMessageDetail(gmailMessageId: string): Promise<InboxMessageDetail> {
  const result = await getJson<GmailInboxMessageDetailResponse>(
    `/api/gmail/inbox/${encodeURIComponent(gmailMessageId)}`,
  );

  if (!result.response.ok || result.data === undefined) {
    throwApiError(result, `/api/gmail/inbox/${gmailMessageId} failed: ${result.response.status}`);
  }
  return normalizeDetail(result.data);
}

/**
 * Fetch the rendered body of a saved Gmail reply draft. The draft body is user-authored draft data
 * (owned/reviewed/sent by the user), rendered live and never persisted — distinct from extracted
 * inbox email content. Used by the needs-reply reader to preview "your draft reply" in-place.
 */
export async function getGmailDraftDetail(gmailDraftId: string): Promise<InboxMessageDetail> {
  const result = await getJson<GmailInboxMessageDetailResponse>(
    `/api/gmail/inbox/drafts/${encodeURIComponent(gmailDraftId)}`,
  );

  if (!result.response.ok || result.data === undefined) {
    throwApiError(
      result,
      `/api/gmail/inbox/drafts/${gmailDraftId} failed: ${result.response.status}`,
    );
  }
  return normalizeDetail(result.data);
}

function normalizeThreadDetail(response: GmailThreadDetailResponse): InboxThreadDetail {
  const messages = (response.messages ?? [])
    .map((entry): InboxMessageDetail | null => {
      const message = entry.message ? normalizeMessage(entry.message) : null;
      if (!message) return null;
      return {
        message,
        renderedText: entry.renderedText ?? '',
        renderedHtml: entry.renderedHtml ?? '',
      };
    })
    .filter((entry): entry is InboxMessageDetail => entry !== null);
  return {
    gmailThreadId: response.gmailThreadId ?? '',
    subject: response.subject ?? '',
    messages,
  };
}

/**
 * Fetch a whole Gmail conversation (received messages + the user's own sent replies) for the inbox
 * reader, so a user can see at a glance that they already replied — including AI-composed messages
 * sent immediately, which never enter the needs-reply queue. Rendered live, never persisted.
 */
export async function getInboxThreadDetail(gmailThreadId: string): Promise<InboxThreadDetail> {
  const result = await getJson<GmailThreadDetailResponse>(
    `/api/gmail/inbox/threads/${encodeURIComponent(gmailThreadId)}`,
  );

  if (!result.response.ok || result.data === undefined) {
    throwApiError(
      result,
      `/api/gmail/inbox/threads/${gmailThreadId} failed: ${result.response.status}`,
    );
  }
  return normalizeThreadDetail(result.data);
}

export async function markInboxMessageRead(gmailMessageId: string): Promise<void> {
  const response = await postNoContent(
    `/api/gmail/inbox/${encodeURIComponent(gmailMessageId)}/read`,
  );
  if (!response.ok) {
    throw new InboxApiError(
      `/api/gmail/inbox/${gmailMessageId}/read failed: ${response.status}`,
      response.status,
    );
  }
}
