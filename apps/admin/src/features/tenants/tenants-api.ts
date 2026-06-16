import {api} from '@/lib/api/admin-client';
import type {components, paths} from '@/lib/api/admin-schema';

export type TenantListResponse = components['schemas']['TenantListResponse'];
export type TenantListRow = components['schemas']['TenantListRowResponse'];
export type TenantDetailResponse = components['schemas']['TenantDetailResponse'];
export type TenantHealthResponse = components['schemas']['TenantHealthResponse'];
export type TenantBillingResponse = components['schemas']['TenantBillingResponse'];
export type TenantSpendResponse = components['schemas']['TenantSpendResponse'];
export type TenantActivityResponse = components['schemas']['TenantActivityResponse'];
export type TenantDeletionPreviewResponse = components['schemas']['TenantDeletionPreviewResponse'];
export type TenantActionRequest = components['schemas']['TenantActionRequest'];
export type TenantStatusFilter = TenantListRow['status'] | 'ALL';
export type TenantDetailTab = 'overview' | 'activity' | 'email' | 'settings' | 'billing';

type TenantListQuery = NonNullable<paths['/api/admin/tenants']['get']['parameters']['query']>;

export type TenantListFilters = {
  status?: TenantStatusFilter;
  email?: string;
  from?: string;
  to?: string;
  cursor?: string;
  limit?: number;
};

export type TenantActionInput = {
  tenantId: string;
  reason: string;
};

export type TenantDeleteInput = TenantActionInput & {
  confirmEmail: string;
};

function errorFor(operation: string): Error {
  return new Error(`Không thể ${operation}.`);
}

function toTenantListQuery(filters: TenantListFilters): TenantListQuery {
  const query: TenantListQuery = {};
  if (filters.status && filters.status !== 'ALL') {
    query.status = filters.status;
  }
  if (filters.email) {
    query.email = filters.email;
  }
  if (filters.from) {
    query.from = `${filters.from}T00:00:00Z`;
  }
  if (filters.to) {
    query.to = `${filters.to}T23:59:59Z`;
  }
  if (filters.cursor) {
    query.cursor = filters.cursor;
  }
  if (filters.limit) {
    query.limit = filters.limit;
  }
  return query;
}

export async function fetchTenantList(filters: TenantListFilters): Promise<TenantListResponse> {
  const {data, error} = await api.GET('/api/admin/tenants', {
    params: {
      query: toTenantListQuery(filters),
    },
  });
  if (error || !data) {
    throw errorFor('tải danh sách khách hàng');
  }
  return data;
}

export async function fetchTenantOverview(tenantId: string): Promise<TenantDetailResponse> {
  const {data, error} = await api.GET('/api/admin/tenants/{tenantId}/overview', {
    params: {
      path: {tenantId},
    },
  });
  if (error || !data) {
    throw errorFor('tải tổng quan khách hàng');
  }
  return data;
}

export async function fetchTenantHealth(tenantId: string): Promise<TenantHealthResponse> {
  const {data, error} = await api.GET('/api/admin/tenants/{tenantId}/health', {
    params: {
      path: {tenantId},
    },
  });
  if (error || !data) {
    throw errorFor('tải tình trạng khách hàng');
  }
  return data;
}

export async function fetchTenantBilling(tenantId: string): Promise<TenantBillingResponse> {
  const {data, error} = await api.GET('/api/admin/tenants/{tenantId}/billing', {
    params: {
      path: {tenantId},
    },
  });
  if (error || !data) {
    throw errorFor('tải thông tin thanh toán khách hàng');
  }
  return data;
}

export async function fetchTenantSpend(tenantId: string): Promise<TenantSpendResponse> {
  const {data, error} = await api.GET('/api/admin/tenants/{tenantId}/spend', {
    params: {
      path: {tenantId},
    },
  });
  if (error || !data) {
    throw errorFor('tải chi phí khách hàng');
  }
  return data;
}

export async function fetchTenantActivity(tenantId: string): Promise<TenantActivityResponse> {
  const {data, error} = await api.GET('/api/admin/tenants/{tenantId}/activity', {
    params: {
      path: {tenantId},
    },
  });
  if (error || !data) {
    throw errorFor('tải hoạt động khách hàng');
  }
  return data;
}

export async function fetchTenantDeletionPreview(tenantId: string): Promise<TenantDeletionPreviewResponse> {
  const {data, error} = await api.GET('/api/admin/tenants/{tenantId}/deletion-preview', {
    params: {
      path: {tenantId},
    },
  });
  if (error || !data) {
    throw errorFor('tải bản xem trước xóa khách hàng');
  }
  return data;
}

export async function pauseTenant(input: TenantActionInput): Promise<void> {
  const {error} = await api.POST('/api/admin/tenants/{tenantId}/pause', {
    params: {
      path: {tenantId: input.tenantId},
    },
    body: {reason: input.reason},
  });
  if (error) {
    throw errorFor('tạm dừng khách hàng');
  }
}

export async function disconnectTenant(input: TenantActionInput): Promise<void> {
  const {error} = await api.POST('/api/admin/tenants/{tenantId}/disconnect', {
    params: {
      path: {tenantId: input.tenantId},
    },
    body: {reason: input.reason},
  });
  if (error) {
    throw errorFor('ngắt kết nối khách hàng');
  }
}

export async function deleteTenant(input: TenantDeleteInput): Promise<void> {
  const request: TenantActionRequest = {
    reason: input.reason,
    confirmEmail: input.confirmEmail,
  };
  const {error} = await api.POST('/api/admin/tenants/{tenantId}/delete', {
    params: {
      path: {tenantId: input.tenantId},
    },
    body: request,
  });
  if (error) {
    throw errorFor('xóa khách hàng');
  }
}
