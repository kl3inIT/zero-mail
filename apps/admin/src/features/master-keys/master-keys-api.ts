import { api } from '@/lib/api/admin-client';
import type { components } from '@/lib/api/admin-schema';

export type MasterKeyRow = components['schemas']['MasterKeyMaskedResponse'];
export type MasterKeyListResponse = components['schemas']['MasterKeyListResponse'];
export type EditSessionResponse = components['schemas']['MasterKeyEditSessionResponse'];
export type TestConnectionRequest = components['schemas']['TestConnectionRequest'];
export type TestConnectionResponse = components['schemas']['TestConnectionResponse'];
export type SetFeatureDefaultRequest = components['schemas']['SetFeatureDefaultRequest'];
export type ProviderKey = components['schemas']['ProviderKey'];
export type ProviderKeyList = components['schemas']['ProviderKeyList'];
export type ProviderKeyStatus = ProviderKey['status'];
export type AddProviderKeyRequest = components['schemas']['AddProviderKeyRequest'];
export type AddProviderKeyResponse = components['schemas']['AddProviderKeyResponse'];
export type CreateProviderRequest = components['schemas']['CreateProviderRequest'];
export type CreateProviderResponse = components['schemas']['CreateProviderResponse'];
export type ReorderProviderKeysRequest = components['schemas']['ReorderProviderKeysRequest'];
export type UpdateProviderKeyRequest = components['schemas']['UpdateProviderKeyRequest'];

// MasterKeyMaskedResponse uses loose `string` for provider/keyFormat (no enum on
// Response DTO). Derive the strict unions from the Request DTOs so callers get
// the same narrowed type the backend will enforce.
export type KeyFormat = components['schemas']['TestConnectionRequest']['keyFormat'];
export type LlmProvider = string;
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
}): Promise<void> {
  const { error } = await api.PUT('/api/admin/master-keys/{provider}', {
    params: { path: { provider: input.provider as LlmProvider } },
    body: {
      plaintextKey: input.plaintextKey,
      keyFormat: input.keyFormat,
      baseUrl: input.baseUrl ?? undefined,
      editSessionToken: input.editSessionToken,
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
}) {
  const { data, error } = await api.POST('/api/admin/master-keys/{provider}/rotate', {
    params: { path: { provider: input.provider as LlmProvider } },
    body: {
      plaintextKey: input.plaintextKey,
      keyFormat: input.keyFormat,
      baseUrl: input.baseUrl ?? undefined,
      editSessionToken: input.editSessionToken,
    },
  });
  if (error || !data) {
    throw new Error('Không thể xoay master key.');
  }
  return data;
}

export async function fetchProviderKeys(provider: string): Promise<ProviderKeyList> {
  const { data, error } = await api.GET('/api/admin/master-keys/{provider}/keys', {
    params: { path: { provider: provider as LlmProvider } },
  });
  if (error || !data) {
    throw new Error('Không thể tải danh sách key của provider.');
  }
  return data;
}

export async function addProviderKey(input: {
  provider: string;
  plaintextKey: string;
  keyFormat: KeyFormat;
  baseUrl?: string | null;
  label?: string | null;
  editSessionToken: string;
}): Promise<AddProviderKeyResponse> {
  const { data, error } = await api.POST('/api/admin/master-keys/{provider}/keys', {
    params: { path: { provider: input.provider as LlmProvider } },
    body: {
      plaintextKey: input.plaintextKey,
      keyFormat: input.keyFormat,
      baseUrl: input.baseUrl ?? undefined,
      label: input.label ?? undefined,
      editSessionToken: input.editSessionToken,
    },
  });
  if (error || !data) {
    throw new Error('Không thể thêm key mới.');
  }
  return data;
}

export async function createProvider(input: {
  providerId: string;
  displayName: string;
  compatibleType: KeyFormat;
  defaultBaseUrl: string;
  plaintextKey: string;
  label?: string | null;
  editSessionToken: string;
}): Promise<CreateProviderResponse> {
  const { data, error } = await api.POST('/api/admin/master-keys/providers', {
    body: {
      providerId: input.providerId,
      displayName: input.displayName,
      compatibleType: input.compatibleType,
      defaultBaseUrl: input.defaultBaseUrl,
      plaintextKey: input.plaintextKey,
      label: input.label ?? undefined,
      editSessionToken: input.editSessionToken,
    },
  });
  if (error || !data) {
    throw new Error('Không thể tạo provider.');
  }
  return data;
}

export async function reorderProviderKeys(input: {
  provider: string;
  orderedKeyIds: string[];
}): Promise<void> {
  const { error } = await api.PUT('/api/admin/master-keys/{provider}/keys/reorder', {
    params: { path: { provider: input.provider as LlmProvider } },
    body: { orderedKeyIds: input.orderedKeyIds },
  });
  if (error) {
    throw new Error('Không thể đổi thứ tự key.');
  }
}

export async function testProviderKey(input: {
  provider: string;
  keyId: string;
}): Promise<TestConnectionResponse> {
  const { data, error } = await api.POST(
    '/api/admin/master-keys/{provider}/keys/{keyId}/test',
    {
      params: {
        path: { provider: input.provider as LlmProvider, keyId: input.keyId },
      },
    },
  );
  if (error || !data) {
    throw new Error('Không thể test key.');
  }
  return data;
}

export async function updateProviderKey(input: {
  provider: string;
  keyId: string;
  label?: string | null;
  baseUrl?: string | null;
}): Promise<void> {
  const { error } = await api.PATCH('/api/admin/master-keys/{provider}/keys/{keyId}', {
    params: {
      path: { provider: input.provider as LlmProvider, keyId: input.keyId },
    },
    body: {
      label: input.label ?? undefined,
      baseUrl: input.baseUrl ?? undefined,
    },
  });
  if (error) {
    throw new Error('Không thể cập nhật key.');
  }
}

export async function revokeProviderKey(input: {
  provider: string;
  keyId: string;
}): Promise<void> {
  const { error } = await api.DELETE('/api/admin/master-keys/{provider}/keys/{keyId}', {
    params: {
      path: { provider: input.provider as LlmProvider, keyId: input.keyId },
    },
  });
  if (error) {
    throw new Error('Không thể xoá key.');
  }
}

export async function deleteProvider(provider: string): Promise<void> {
  const { error } = await api.DELETE('/api/admin/master-keys/{provider}', {
    params: {
      path: { provider: provider as LlmProvider },
    },
  });
  if (error) {
    throw new Error('Không thể xoá provider.');
  }
}

export async function setFeatureDefault(input: SetFeatureDefaultRequest): Promise<void> {
  const { error } = await api.PUT('/api/admin/master-keys/feature-default', {
    body: input,
  });
  if (error) {
    throw new Error('Không thể đặt provider mặc định cho feature.');
  }
}
