import { useQuery } from '@tanstack/react-query';

import { fetchAuditPage, type AuditFilters } from './audit-api';
import { auditQueryKeys } from './query-keys';

export function useAuditPage(filters: AuditFilters) {
  return useQuery({
    queryKey: auditQueryKeys.page(filters),
    queryFn: () => fetchAuditPage(filters),
  });
}
