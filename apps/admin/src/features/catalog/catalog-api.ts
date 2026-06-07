import { api } from '@/lib/api/admin-client';
import type { components } from '@/lib/api/admin-schema';

export const catalogFeatures = [
  'CHAT',
  'TRIAGE',
  'DRAFT',
  'RULE_AUTHORING',
  'RULE_PREVIEW_SEMANTIC',
  'TRIAGE_SEMANTIC',
  'DRIFT_CHECK',
] as const;

export type CatalogProvider = string;
export type CatalogFeature = (typeof catalogFeatures)[number];
export type CatalogListResponse = components['schemas']['CatalogListResponse'];
export type CatalogFeatureResponse = components['schemas']['FeatureCatalogResponse'];
export type CatalogModel = components['schemas']['CatalogModelResponse'];
export type CatalogSyncFetchResponse = components['schemas']['CatalogSyncFetchResponse'];
export type CatalogSyncDiffResponse = components['schemas']['CatalogSyncDiffResponse'];
export type CatalogModelCreateRequest = components['schemas']['CatalogModelCreateRequest'];
export type CatalogModelDisableRequest = components['schemas']['CatalogModelDisableRequest'];
export type CatalogModelVerificationResponse =
  components['schemas']['CatalogModelVerificationResponse'];

export type CreateCatalogModelInput = {
  provider: CatalogProvider;
  modelId: string;
  displayName: string;
  costPer1kInput?: number;
  costPer1kOutput?: number;
  recommended?: boolean;
};

export type DisableCatalogModelInput = {
  modelId: string;
  reason: string;
  confirmedPinned: boolean;
  pinnedCountAcknowledged: number;
};

export type EnableCatalogModelInput = {
  modelId: string;
};

export type SetCatalogDefaultInput = {
  provider: CatalogProvider;
  feature: CatalogFeature;
  modelId: string;
  reason: string;
};

function errorFor(operation: string): Error {
  return new Error(`Không thể ${operation}.`);
}

export function providerLabel(provider: CatalogProvider): string {
  return {
    OPENAI: 'OpenAI',
    ANTHROPIC: 'Anthropic',
    GOOGLE: 'Google',
    DEEPSEEK: 'DeepSeek',
  }[provider] ?? provider;
}

export function featureLabel(feature: CatalogFeature): string {
  return {
    CHAT: 'Chat',
    TRIAGE: 'Chọn hành động AI',
    DRAFT: 'Soạn nội dung',
    RULE_AUTHORING: 'Tạo quy tắc',
    RULE_PREVIEW_SEMANTIC: 'Test quy tắc',
    TRIAGE_SEMANTIC: 'Chạy quy tắc',
    DRIFT_CHECK: 'Kiểm tra chất lượng',
  }[feature];
}

export async function fetchCatalogProvider(
  provider: CatalogProvider,
): Promise<CatalogListResponse> {
  const { data, error } = await api.GET('/api/admin/catalog/{provider}', {
    params: {
      path: { provider },
    },
  });
  if (error || !data) {
    throw errorFor('tải danh mục');
  }
  return data;
}

export async function startCatalogSync(
  provider: CatalogProvider,
): Promise<CatalogSyncFetchResponse> {
  const { data, error } = await api.POST('/api/admin/catalog/{provider}/sync/fetch', {
    params: {
      path: { provider },
    },
  });
  if (error || !data) {
    throw errorFor('bắt đầu đồng bộ danh mục');
  }
  return data;
}

export async function fetchCatalogSyncDiff(jobId: string): Promise<CatalogSyncDiffResponse> {
  const { data, error } = await api.GET('/api/admin/catalog/sync/{jobId}/diff', {
    params: {
      path: { jobId },
    },
  });
  if (error || !data) {
    throw errorFor('tải bản so sánh đồng bộ danh mục');
  }
  return data;
}

export async function confirmCatalogSync(input: { jobId: string; reason?: string }): Promise<void> {
  const { error } = await api.POST('/api/admin/catalog/sync/{jobId}/confirm', {
    params: {
      path: { jobId: input.jobId },
    },
    body: input.reason ? { reason: input.reason } : {},
  });
  if (error) {
    throw errorFor('xác nhận đồng bộ danh mục');
  }
}

export async function cancelCatalogSync(jobId: string): Promise<void> {
  const { error } = await api.POST('/api/admin/catalog/sync/{jobId}/cancel', {
    params: {
      path: { jobId },
    },
  });
  if (error) {
    throw errorFor('hủy đồng bộ danh mục');
  }
}

export type CreateCatalogModelOutcome = 'created' | 'already-exists';

export async function createCatalogModel(
  input: CreateCatalogModelInput,
): Promise<CreateCatalogModelOutcome> {
  const request: CatalogModelCreateRequest = {
    modelId: input.modelId,
    displayName: input.displayName,
    costPer1kInput: input.costPer1kInput,
    costPer1kOutput: input.costPer1kOutput,
    recommended: input.recommended,
  };
  const { error, response } = await api.POST('/api/admin/catalog/{provider}/models', {
    params: {
      path: { provider: input.provider },
    },
    body: request,
  });
  if (!error) return 'created';
  // 409 = the row was inserted by a previous attempt whose verify probe failed
  // and the user is retrying. Treat as success so the caller can re-run verify.
  if (response?.status === 409) return 'already-exists';
  throw errorFor('tạo mô hình danh mục');
}

export async function disableCatalogModel(input: DisableCatalogModelInput): Promise<void> {
  const request: CatalogModelDisableRequest = {
    modelId: input.modelId,
    reason: input.reason,
    confirmedPinned: input.confirmedPinned,
    pinnedCountAcknowledged: input.pinnedCountAcknowledged,
  };
  const { error } = await api.POST('/api/admin/catalog/models/disable', {
    body: request,
  });
  if (error) {
    throw errorFor('vô hiệu mô hình danh mục');
  }
}

export async function enableCatalogModel(input: EnableCatalogModelInput): Promise<void> {
  // TODO: regenerate admin-schema once backend is running; use typed api.POST when path is in schema
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const { error } = await (api as any).POST('/api/admin/catalog/models/enable', {
    body: { modelId: input.modelId },
  });
  if (error) {
    throw errorFor('kích hoạt model');
  }
}

export async function verifyCatalogModel(
  modelId: string,
): Promise<CatalogModelVerificationResponse> {
  const { data, error } = await api.POST('/api/admin/catalog/models/verify', {
    body: { modelId },
  });
  if (error || !data) {
    throw errorFor('xác thực model');
  }
  return data;
}

export async function setCatalogDefault(input: SetCatalogDefaultInput): Promise<void> {
  const { error } = await api.PUT('/api/admin/catalog/{provider}/{feature}/default', {
    params: {
      path: {
        provider: input.provider,
        feature: input.feature,
      },
    },
    body: {
      modelId: input.modelId,
      reason: input.reason,
    },
  });
  if (error) {
    throw errorFor('đặt mô hình mặc định');
  }
}
