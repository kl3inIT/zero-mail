import { api } from '@/lib/api/client';
import { ErrorCode } from '@/lib/api/error-codes';
import type { components } from '@/lib/api/schema';

export type ApiError = components['schemas']['ApiError'];
export type NeedsReplyRowResponse = components['schemas']['NeedsReplyRowResponse'];
export type NeedsReplyListResponse = components['schemas']['NeedsReplyListResponse'];
export type ThreadDraftResponse = components['schemas']['ThreadDraftResponse'];
export type ToReplyCountResponse = components['schemas']['ToReplyCountResponse'];

export type ServerBucket = 'to-reply' | 'awaiting-their-reply';
export type NeedsReplyBucket = ServerBucket | 'drafted';
export type DraftStatus = 'NO_DRAFT' | 'DRAFT_READY' | 'DRAFT_SENT';

/**
 * Map a UI bucket to the bucket the backend understands. The synthetic 'drafted' bucket is a
 * client-side filter over the to-reply server bucket — backend has no concept of it.
 */
export function toServerBucket(bucket: NeedsReplyBucket): ServerBucket {
  return bucket === 'drafted' ? 'to-reply' : bucket;
}

export type NeedsReplyRow = {
  gmailThreadId: string;
  subject: string;
  otherParty: string;
  lastActivityAt: string;
  draftStatus: DraftStatus;
  resolved: boolean;
  openInGmailUrl: string;
};

export type NeedsReplyPage = {
  items: NeedsReplyRow[];
  nextCursor: string | null;
  toReplyCount: number;
};

export type NeedsReplyInboxOptions = {
  bucket: NeedsReplyBucket;
  cursor?: string | null;
  limit?: number;
  resolved?: boolean;
};

export class NeedsReplyApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly apiError?: ApiError;

  constructor(message: string, status: number, apiError?: ApiError) {
    super(message);
    this.name = 'NeedsReplyApiError';
    this.status = status;
    this.code = apiError?.code;
    this.apiError = apiError;
  }
}

function maybeApiError(error: unknown): ApiError | undefined {
  if (
    error &&
    typeof error === 'object' &&
    typeof (error as { code?: unknown }).code === 'string'
  ) {
    return error as ApiError;
  }
  return undefined;
}

function throwApiError(
  result: { error?: unknown; response: Response },
  fallbackMessage: string,
): never {
  const apiError = maybeApiError(result.error);
  throw new NeedsReplyApiError(fallbackMessage, result.response.status, apiError);
}

function normalizeDraftStatus(value: string | undefined): DraftStatus {
  if (value === 'DRAFT_READY' || value === 'DRAFT_SENT') return value;
  return 'NO_DRAFT';
}

function gmailUrlForThread(gmailThreadId: string): string {
  return `https://mail.google.com/mail/u/0/#all/${encodeURIComponent(gmailThreadId)}`;
}

function normalizeRow(row: NeedsReplyRowResponse): NeedsReplyRow | null {
  if (!row.gmailThreadId) return null;

  return {
    gmailThreadId: row.gmailThreadId,
    subject: row.subject ?? '',
    otherParty: row.otherParty ?? '',
    lastActivityAt: row.lastActivityAt ?? new Date(0).toISOString(),
    draftStatus: normalizeDraftStatus(row.draftStatus),
    resolved: Boolean(row.resolved),
    openInGmailUrl: row.openInGmailUrl ?? gmailUrlForThread(row.gmailThreadId),
  };
}

function normalizePage(response: NeedsReplyListResponse): NeedsReplyPage {
  return {
    items: (response.items ?? [])
      .map(normalizeRow)
      .filter((row): row is NeedsReplyRow => row !== null),
    nextCursor: response.nextCursor ?? null,
    toReplyCount: response.toReplyCount ?? 0,
  };
}

export function isDraftGenerationInFlight(error: unknown): boolean {
  return (
    error instanceof NeedsReplyApiError &&
    (error.status === 409 || error.code === ErrorCode.DraftGenerationInFlight)
  );
}

export async function getNeedsReplyInbox({
  bucket,
  cursor,
  limit = 50,
  resolved = false,
}: NeedsReplyInboxOptions): Promise<NeedsReplyPage> {
  const result = await api.GET('/api/threads', {
    params: {
      query: {
        bucket: toServerBucket(bucket),
        cursor: cursor ?? undefined,
        limit,
        resolved,
      },
    },
  });

  if (result.error || !result.response.ok || result.data === undefined) {
    throwApiError(result, `/api/threads failed: ${result.response.status}`);
  }

  const page = normalizePage(result.data);
  // Synthetic 'drafted' bucket: server returns to-reply rows; we keep only those with a draft.
  if (bucket === 'drafted') {
    return { ...page, items: page.items.filter((row) => row.draftStatus !== 'NO_DRAFT') };
  }
  return page;
}

export async function getToReplyCount(): Promise<number> {
  const result = await api.GET('/api/threads/to-reply-count', {});

  if (result.error || !result.response.ok || result.data === undefined) {
    throwApiError(result, `/api/threads/to-reply-count failed: ${result.response.status}`);
  }

  return result.data.toReplyCount ?? 0;
}

export async function generateDraft(gmailThreadId: string): Promise<ThreadDraftResponse> {
  const result = await api.POST('/api/threads/{gmailThreadId}/draft', {
    params: { path: { gmailThreadId } },
  });

  if (result.error || !result.response.ok || result.data === undefined) {
    throwApiError(result, `/api/threads/${gmailThreadId}/draft failed: ${result.response.status}`);
  }

  return result.data;
}

export async function markResolved(gmailThreadId: string): Promise<void> {
  const result = await api.POST('/api/threads/{gmailThreadId}/resolve', {
    params: { path: { gmailThreadId } },
  });

  if (result.error || !result.response.ok) {
    throwApiError(
      result,
      `/api/threads/${gmailThreadId}/resolve failed: ${result.response.status}`,
    );
  }
}
