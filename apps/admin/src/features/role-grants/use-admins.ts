import { useQuery } from '@tanstack/react-query';

import { fetchAdmins } from './role-grants-api';
import { roleGrantQueryKeys } from './query-keys';

export function useAdmins() {
  return useQuery({
    queryKey: roleGrantQueryKeys.admins,
    queryFn: fetchAdmins,
  });
}
