import { getAdminApiUrl } from '@/lib/api/admin-base-url';

export type KeyFormat = 'OPENAI_FORMAT' | 'ANTHROPIC_FORMAT' | 'GOOGLE_FORMAT';
export type LlmProvider = 'OPENAI' | 'ANTHROPIC' | 'GOOGLE' | 'DEEPSEEK' | 'OPENROUTER' | 'ROUTER_9R';
export type MasterKeyFeature = 'CHAT' | 'TRIAGE' | 'DRAFT';
export type TestConnectionResult = 'OK' | 'INVALID_KEY' | 'RATE_LIMITED' | 'NETWORK_ERROR' | 'TIMEOUT';

export type MasterKeyRow = {
  provider: LlmProvider;
  displayName: string;
  maskedKey: string | null;
  keyFormat: KeyFormat | null;
  kekVersion: number | null;
  providerSecretVersion: number;
  lastRotatedAt: string | null;
  dependentsCount: number;
  rotationRecommended: boolean;
  baseUrl: string | null;
  featureDefaultProviderChat: boolean;
  featureDefaultProviderTriage: boolean;
  featureDefaultProviderDraft: boolean;
};

export type MasterKeyListResponse = {
  rows: MasterKeyRow[];
};

export type EditSessionResponse = {
  token: string;
  expiresAt: string;
};

async function adminFetch<T>(path: `/${string}`, init?: RequestInit): Promise<T> {
  // TODO: switch to typed openapi-fetch after admin-schema.d.ts is regenerated with 8B paths.
  const response = await fetch(getAdminApiUrl(path), {
    credentials: 'include',
    headers: { 'content-type': 'application/json', ...(init?.headers ?? {}) },
    ...init,
  });
  if (!response.ok) {
    throw new Error(`Yêu cầu quản trị thất bại: ${response.status}`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export async function fetchMasterKeys(): Promise<MasterKeyListResponse> {
  return adminFetch<MasterKeyListResponse>('/api/admin/master-keys/');
}

export async function fetchMasterKey(provider: string): Promise<MasterKeyRow> {
  return adminFetch<MasterKeyRow>(`/api/admin/master-keys/${provider}`);
}

export async function mintEditSession(provider: string): Promise<EditSessionResponse> {
  return adminFetch<EditSessionResponse>(`/api/admin/master-keys/${provider}/edit-session`, {
    method: 'POST',
  });
}

export async function testMasterKeyConnection(input: {
  provider: string;
  plaintextKey: string;
  keyFormat: KeyFormat;
  baseUrl?: string | null;
  editSessionToken: string;
}): Promise<{ result: TestConnectionResult }> {
  return adminFetch<{ result: TestConnectionResult }>(`/api/admin/master-keys/${input.provider}/test-connection`, {
    method: 'POST',
    body: JSON.stringify({
      plaintextKey: input.plaintextKey,
      keyFormat: input.keyFormat,
      baseUrl: input.baseUrl,
      editSessionToken: input.editSessionToken,
    }),
  });
}

export async function saveMasterKey(input: {
  provider: string;
  plaintextKey: string;
  keyFormat: KeyFormat;
  baseUrl?: string | null;
  editSessionToken: string;
  reason: string;
}): Promise<void> {
  await adminFetch<void>(`/api/admin/master-keys/${input.provider}`, {
    method: 'PUT',
    body: JSON.stringify({
      plaintextKey: input.plaintextKey,
      keyFormat: input.keyFormat,
      baseUrl: input.baseUrl,
      editSessionToken: input.editSessionToken,
      reason: input.reason,
    }),
  });
}

export async function rotateMasterKey(input: {
  provider: string;
  plaintextKey: string;
  keyFormat: KeyFormat;
  baseUrl?: string | null;
  editSessionToken: string;
  reason: string;
}): Promise<{ result: 'OK' | 'TEST_FAILED'; testResult?: TestConnectionResult }> {
  return adminFetch<{ result: 'OK' | 'TEST_FAILED'; testResult?: TestConnectionResult }>(
    `/api/admin/master-keys/${input.provider}/rotate`,
    {
      method: 'POST',
      body: JSON.stringify({
        plaintextKey: input.plaintextKey,
        keyFormat: input.keyFormat,
        baseUrl: input.baseUrl,
        editSessionToken: input.editSessionToken,
        reason: input.reason,
      }),
    },
  );
}

export async function setFeatureDefault(input: {
  feature: MasterKeyFeature;
  provider: LlmProvider;
  reason: string;
}): Promise<void> {
  await adminFetch<void>('/api/admin/master-keys/feature-default', {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}
