import { getApiUrl } from '@/lib/api/base-url';
import { adaptFetchForOpenApi, api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type MailboxSummary = components['schemas']['MailboxSummaryResponse'];
export type ActiveMailbox = components['schemas']['ActiveMailboxResponse'];

export interface MailboxRequestOptions {
  fetcher?: typeof fetch;
  signal?: AbortSignal;
  headers?: HeadersInit;
}

export async function listMailboxes(
  options: MailboxRequestOptions = {},
): Promise<MailboxSummary[]> {
  const { fetcher, signal, headers } = options;
  const { data, error, response } = await api.GET('/api/gmail/mailboxes', {
    cache: fetcher || headers ? 'no-store' : undefined,
    fetch: adaptFetchForOpenApi(fetcher ?? (headers ? fetch : undefined)),
    headers,
    signal,
  });
  if (error || !response.ok || data === undefined) {
    throw error ?? new Error(`/api/gmail/mailboxes failed: ${response.status}`);
  }
  return data;
}

export async function getActiveMailbox(
  options: MailboxRequestOptions = {},
): Promise<ActiveMailbox> {
  const { fetcher, signal, headers } = options;
  const { data, error, response } = await api.GET('/api/gmail/active-mailbox', {
    cache: fetcher || headers ? 'no-store' : undefined,
    fetch: adaptFetchForOpenApi(fetcher ?? (headers ? fetch : undefined)),
    headers,
    signal,
  });
  if (error || !response.ok || data === undefined) {
    throw error ?? new Error(`/api/gmail/active-mailbox failed: ${response.status}`);
  }
  return data;
}

export async function setActiveMailbox(gmailConnectionId: string): Promise<ActiveMailbox> {
  const { data, error, response } = await api.PUT('/api/gmail/active-mailbox/{gmailConnectionId}', {
    params: { path: { gmailConnectionId } },
  });
  if (error || !response.ok || data === undefined) {
    throw (
      error ??
      new Error(`/api/gmail/active-mailbox/${gmailConnectionId} failed: ${response.status}`)
    );
  }
  return data;
}

export function getConnectMailboxUrl(): string {
  return getApiUrl('/api/gmail/mailboxes/connect');
}

export function getReconnectMailboxUrl(gmailConnectionId: string): string {
  return getApiUrl(`/api/gmail/mailboxes/${gmailConnectionId}/reconnect`);
}
