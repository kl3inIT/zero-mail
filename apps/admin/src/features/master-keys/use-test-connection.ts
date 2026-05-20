import { useMutation } from '@tanstack/react-query';

import { testMasterKeyConnection } from './master-keys-api';

export function useTestConnection() {
  return useMutation({
    mutationFn: testMasterKeyConnection,
  });
}
