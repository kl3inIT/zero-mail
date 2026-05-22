import { api } from '@/lib/api/admin-client';
import type { components } from '@/lib/api/admin-schema';

export type FeatureDefaultMatrix = components['schemas']['FeatureDefaultMatrixResponse'];
export type FeatureDefaultBinding = NonNullable<FeatureDefaultMatrix['bindings']>[number];
export type SetFeatureDefaultTier = components['schemas']['SetFeatureDefaultTierRequest'];
export type RoutingTier = SetFeatureDefaultTier['tier'];
export type RoutingFeature = SetFeatureDefaultTier['feature'];

export async function fetchFeatureDefaults(): Promise<FeatureDefaultMatrix> {
  const { data, error } = await api.GET('/api/admin/catalog/feature-defaults');
  if (error || !data) {
    throw new Error('Không thể tải ma trận routing.');
  }
  return data;
}

export async function assignFeatureDefaultTier(input: SetFeatureDefaultTier): Promise<void> {
  const { error } = await api.PUT('/api/admin/catalog/feature-defaults', { body: input });
  if (error) {
    throw new Error('Không thể gán mặc định cho tier.');
  }
}
