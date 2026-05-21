import { api } from '@/lib/api/admin-client';
import type { components } from '@/lib/api/admin-schema';

export type MasterKeyRow = components['schemas']['MasterKeyMaskedResponse'];
export type MasterKeyListResponse = components['schemas']['MasterKeyListResponse'];
export type EditSessionResponse = components['schemas']['MasterKeyEditSessionResponse'];
export type TestConnectionRequest = components['schemas']['TestConnectionRequest'];
export type TestConnectionResponse = components['schemas']['TestConnectionResponse'];
export type SetFeatureDefaultRequest = components['schemas']['SetFeatureDefaultRequest'];

// MasterKeyMaskedResponse uses loose `string` for provider/keyFormat (no enum on
// Response DTO). Derive the strict unions from the Request DTOs so callers get
// the same narrowed type the backend will enforce.
export type KeyFormat = components['schemas']['TestConnectionRequest']['keyFormat'];
export type LlmProvider = SetFeatureDefaultRequest['provider'];
export type MasterKeyFeature = SetFeatureDefaultRequest['feature'];
export type TestConnectionResult = TestConnectionResponse['result'];

export async function fetchMasterKeys(): Promise<MasterKeyListResponse> {
  const { data, error } = await api.GET('/api/admin/master-keys/');
  if (error || !data) {
    throw new Error('Không thể tải danh sách master key.');
  }
  return data;
}

export async function fetchMasterKey(provider: string): Promise<MasterKeyRow> {
  const { data, error } = await api.GET('/api/admin/master-keys/{provider}', {
    params: { path: { provider: provider as LlmProvider } },
  });
  if (error || !data) {
    throw new Error('Không thể tải master key.');
  }
  return data;
}

export async function mintEditSession(provider: string): Promise<EditSessionResponse> {
  const { data, error } = await api.POST('/api/admin/master-keys/{provider}/edit-session', {
    params: { path: { provider: provider as LlmProvider } },
  });
  if (error || !data) {
    throw new Error('Không thể mở phiên chỉnh sửa master key.');
  }
  return data;
}

export async function testMasterKeyConnection(input: {
  provider: string;
  plaintextKey: string;
  keyFormat: KeyFormat;
  baseUrl?: string | null;
  editSessionToken: string;
}): Promise<TestConnectionResponse> {
  const { data, error } = await api.POST('/api/admin/master-keys/{provider}/test-connection', {
    params: { path: { provider: input.provider as LlmProvider } },
    body: {
      plaintextKey: input.plaintextKey,
      keyFormat: input.keyFormat,
      baseUrl: input.baseUrl ?? undefined,
      editSessionToken: input.editSessionToken,
    },
  });
  if (error || !data) {
    throw new Error('Không thể kiểm tra kết nối master key.');
  }
  return data;
}

export async function saveMasterKey(input: {
  provider: string;
  plaintextKey: string;
  keyFormat: KeyFormat;
  baseUrl?: string | null;
  editSessionToken: string;
  reason: string;
}): Promise<void> {
  const { error } = await api.PUT('/api/admin/master-keys/{provider}', {
    params: { path: { provider: input.provider as LlmProvider } },
    body: {
      plaintextKey: input.plaintextKey,
      keyFormat: input.keyFormat,
      baseUrl: input.baseUrl ?? undefined,
      editSessionToken: input.editSessionToken,
      reason: input.reason,
    },
  });
  if (error) {
    throw new Error('Không thể lưu master key.');
  }
}

export async function rotateMasterKey(input: {
  provider: string;
  plaintextKey: string;
  keyFormat: KeyFormat;
  baseUrl?: string | null;
  editSessionToken: string;
  reason: string;
}) {
  const { data, error } = await api.POST('/api/admin/master-keys/{provider}/rotate', {
    params: { path: { provider: input.provider as LlmProvider } },
    body: {
      plaintextKey: input.plaintextKey,
      keyFormat: input.keyFormat,
      baseUrl: input.baseUrl ?? undefined,
      editSessionToken: input.editSessionToken,
      reason: input.reason,
    },
  });
  if (error || !data) {
    throw new Error('Không thể xoay master key.');
  }
  return data;
}

export async function setFeatureDefault(input: SetFeatureDefaultRequest): Promise<void> {
  const { error } = await api.PUT('/api/admin/master-keys/feature-default', {
    body: input,
  });
  if (error) {
    throw new Error('Không thể đặt provider mặc định cho feature.');
  }
}
