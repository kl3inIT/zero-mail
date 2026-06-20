import { api } from '@/lib/api/admin-client';
import type { components } from '@/lib/api/admin-schema';

export type AdminUserSummary = components['schemas']['AdminUserSummaryResponse'];
export type GrantAdminResponse = components['schemas']['GrantAdminResponse'];

export async function fetchAdmins(): Promise<AdminUserSummary[]> {
  const { data, error } = await api.GET('/api/admin/admins');
  if (error || !data) {
    throw new Error('Không thể tải danh sách quản trị viên.');
  }
  return data;
}

export async function grantAdmin(email: string): Promise<GrantAdminResponse> {
  const { data, error } = await api.POST('/api/admin/grant-admin', {
    body: { email },
  });
  if (error || !data) {
    throw new Error('Không thể cấp quyền quản trị.');
  }
  return data;
}

export async function revokeAdmin(adminUserId: string, reason: string): Promise<void> {
  const { error } = await api.POST('/api/admin/admins/{adminUserId}/revoke', {
    params: {
      path: { adminUserId },
    },
    body: { reason },
  });
  if (error) {
    throw new Error('Không thể thu hồi quyền quản trị.');
  }
}

export async function deleteAdmin(adminUserId: string, reason: string): Promise<void> {
  const { error } = await api.DELETE('/api/admin/admins/{adminUserId}', {
    params: {
      path: { adminUserId },
    },
    body: { reason },
  });
  if (error) {
    throw new Error('Không thể xóa admin.');
  }
}
