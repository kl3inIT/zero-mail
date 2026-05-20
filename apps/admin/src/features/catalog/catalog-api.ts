import { api } from '@/lib/api/admin-client';
import type { components } from '@/lib/api/admin-schema';

export const catalogProviders = [
  'OPENAI',
  'ANTHROPIC',
  'GOOGLE',
  'DEEPSEEK',
  'OPENROUTER',
  'ROUTER_9R',
] as const;
export const catalogFeatures = ['CHAT', 'TRIAGE', 'DRAFT'] as const;

export type CatalogProvider = (typeof catalogProviders)[number];
export type CatalogFeature = (typeof catalogFeatures)[number];
export type CatalogListResponse = components['schemas']['CatalogListResponse'];
export type CatalogFeatureResponse = components['schemas']['FeatureCatalogResponse'];
export type CatalogModel = components['schemas']['CatalogModelResponse'];
export type CatalogSyncFetchResponse = components['schemas']['CatalogSyncFetchResponse'];
export type CatalogSyncDiffResponse = components['schemas']['CatalogSyncDiffResponse'];
export type CatalogModelCreateRequest = components['schemas']['CatalogModelCreateRequest'];
export type CatalogModelDisableRequest = components['schemas']['CatalogModelDisableRequest'];

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

export type SetCatalogDefaultInput = {
  provider: CatalogProvider;
  feature: CatalogFeature;
  modelId: string;
  reason: string;
};

function errorFor(operation: string): Error {
  return new Error(`Unable to ${operation}.`);
}

export function providerLabel(provider: CatalogProvider): string {
  return {
    OPENAI: 'OpenAI',
    ANTHROPIC: 'Anthropic',
    GOOGLE: 'Google',
    DEEPSEEK: 'DeepSeek',
    OPENROUTER: 'OpenRouter',
    ROUTER_9R: '9Router',
  }[provider];
}

export function featureLabel(feature: CatalogFeature): string {
  return {
    CHAT: 'Chat',
    TRIAGE: 'Triage',
    DRAFT: 'Draft',
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
    throw errorFor('load catalog');
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
    throw errorFor('start catalog sync');
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
    throw errorFor('load catalog sync diff');
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
    throw errorFor('confirm catalog sync');
  }
}

export async function cancelCatalogSync(jobId: string): Promise<void> {
  const { error } = await api.POST('/api/admin/catalog/sync/{jobId}/cancel', {
    params: {
      path: { jobId },
    },
  });
  if (error) {
    throw errorFor('cancel catalog sync');
  }
}

export async function createCatalogModel(input: CreateCatalogModelInput): Promise<void> {
  const request: CatalogModelCreateRequest = {
    modelId: input.modelId,
    displayName: input.displayName,
    costPer1kInput: input.costPer1kInput,
    costPer1kOutput: input.costPer1kOutput,
    recommended: input.recommended,
  };
  const { error } = await api.POST('/api/admin/catalog/{provider}/models', {
    params: {
      path: { provider: input.provider },
    },
    body: request,
  });
  if (error) {
    throw errorFor('create catalog model');
  }
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
    throw errorFor('disable catalog model');
  }
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
    throw errorFor('set catalog default');
  }
}
