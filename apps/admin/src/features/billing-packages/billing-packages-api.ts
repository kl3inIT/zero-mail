import { api } from '@/lib/api/admin-client';
import type { components } from '@/lib/api/admin-schema';

export type AdminBillingPackageResponse = components['schemas']['AdminBillingPackageResponse'];
export type AdminBillingPlanResponse = components['schemas']['AdminBillingPlanResponse'];
export type AdminBillingFeaturePermissionResponse =
  components['schemas']['AdminBillingFeaturePermissionResponse'];
export type AdminBillingPlanPermissionResponse =
  components['schemas']['AdminBillingPlanPermissionResponse'];
export type AdminBillingPaymentResponse = components['schemas']['AdminBillingPaymentResponse'];
export type AdminBillingFeaturePermissionUpdateRequest =
  components['schemas']['AdminBillingFeaturePermissionUpdateRequest'];
export type AdminBillingFeatureCreditCostUpdateRequest =
  components['schemas']['AdminBillingFeatureCreditCostUpdateRequest'];

export type UpdateBillingFeaturePermissionInput = {
  featureCode: string;
  planCode: string;
  enabled: boolean;
};

export type UpdateBillingFeatureCreditCostInput = {
  featureCode: string;
  fixedCreditCost: number;
};

export async function fetchBillingPackages(): Promise<AdminBillingPackageResponse> {
  const { data, error } = await api.GET('/api/admin/billing-packages');
  if (error || !data) {
    throw new Error('Không thể tải cấu hình gói dịch vụ.');
  }
  return data;
}

export async function updateBillingFeaturePermission(
  input: UpdateBillingFeaturePermissionInput,
): Promise<void> {
  const request: AdminBillingFeaturePermissionUpdateRequest = {
    enabled: input.enabled,
  };
  const { error } = await api.PATCH(
    '/api/admin/billing-packages/features/{featureCode}/plans/{planCode}/enabled',
    {
      params: {
        path: {
          featureCode: input.featureCode,
          planCode: input.planCode,
        },
      },
      body: request,
    },
  );
  if (error) {
    throw new Error('Không thể cập nhật quyền gói dịch vụ.');
  }
}

export async function updateBillingFeatureCreditCost(
  input: UpdateBillingFeatureCreditCostInput,
): Promise<void> {
  const request: AdminBillingFeatureCreditCostUpdateRequest = {
    fixedCreditCost: input.fixedCreditCost,
  };
  const { error } = await api.PATCH(
    '/api/admin/billing-packages/features/{featureCode}/credit-cost',
    {
      params: {
        path: {
          featureCode: input.featureCode,
        },
      },
      body: request,
    },
  );
  if (error) {
    throw new Error('Không thể cập nhật giá credit chức năng.');
  }
}
