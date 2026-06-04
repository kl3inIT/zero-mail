import { getApiUrl } from '@/lib/api/base-url';
import { xsrfHeader } from '@/lib/api/client';

// TODO: switch to the generated OpenAPI client after :backend:api:generateOpenApiDocs
// runs under the project JDK 25 locally — same pattern as inbox-api.ts.
export type ComposerDraftMode = 'reply' | 'reply_all' | 'forward';

export type ComposerDraftSnapshot = {
  draftId: string;
  gmailThreadId: string;
  toAddresses: string[];
  ccAddresses: string[];
  bccAddresses: string[];
  subject: string;
  body: string;
};

export type ComposerDraftUpsertInput = {
  gmailThreadId: string;
  sourceGmailMessageId?: string | null;
  rfc822MessageId?: string | null;
  priorReferences?: string | null;
  mode: ComposerDraftMode;
  toAddresses: string;
  ccAddresses: string;
  bccAddresses: string;
  subject: string;
  body: string;
};

export class ComposerDraftApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ComposerDraftApiError';
    this.status = status;
  }
}

export async function getComposerDraft(
  gmailThreadId: string,
): Promise<ComposerDraftSnapshot | null> {
  const searchParams = new URLSearchParams({ gmailThreadId });
  const response = await fetch(getApiUrl(`/api/composer/drafts?${searchParams.toString()}`), {
    credentials: 'include',
  });
  if (response.status === 204) return null;
  if (!response.ok) {
    throw new ComposerDraftApiError(
      `/api/composer/drafts lookup failed: ${response.status}`,
      response.status,
    );
  }
  return (await response.json()) as ComposerDraftSnapshot;
}

export async function upsertComposerDraft(
  body: ComposerDraftUpsertInput,
): Promise<ComposerDraftSnapshot> {
  const response = await fetch(getApiUrl('/api/composer/drafts'), {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...xsrfHeader(),
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new ComposerDraftApiError(
      `/api/composer/drafts upsert failed: ${response.status}`,
      response.status,
    );
  }
  return (await response.json()) as ComposerDraftSnapshot;
}

export async function deleteComposerDraft(draftId: string): Promise<void> {
  const response = await fetch(getApiUrl(`/api/composer/drafts/${encodeURIComponent(draftId)}`), {
    method: 'DELETE',
    credentials: 'include',
    headers: xsrfHeader(),
  });
  if (!response.ok && response.status !== 404) {
    throw new ComposerDraftApiError(
      `/api/composer/drafts/${draftId} delete failed: ${response.status}`,
      response.status,
    );
  }
}
