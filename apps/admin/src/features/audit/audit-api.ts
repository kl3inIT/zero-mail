import { api } from '@/lib/api/admin-client';
import { getAdminApiUrl } from '@/lib/api/admin-base-url';
import type { components } from '@/lib/api/admin-schema';

export type AuditPage = components['schemas']['AdminAuditPageResponse'];
export type AuditRow = components['schemas']['AdminAuditEventResponse'];

export type AuditFilters = {
  actorEmail?: string;
  action?: string;
  targetKind?: string;
  targetId?: string;
  from?: string;
  to?: string;
  cursor?: string;
  limit?: number;
};

export async function fetchAuditPage(filters: AuditFilters): Promise<AuditPage> {
  const { data, error } = await api.GET('/api/admin/audit/events', {
    params: {
      query: filters,
    },
  });
  if (error || !data) {
    throw new Error('Unable to load admin audit events.');
  }
  return data;
}

export function auditCsvUrl(filters: AuditFilters): string {
  const searchParameters = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      searchParameters.set(key, String(value));
    }
  });
  return `${getAdminApiUrl('/api/admin/audit/events/csv')}?${searchParameters.toString()}`;
}
