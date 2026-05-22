import type { AuditFilters } from './audit-api';

export const auditQueryKeys = {
  all: ['admin-audit'] as const,
  page: (filters: AuditFilters) => [...auditQueryKeys.all, 'page', filters] as const,
};
