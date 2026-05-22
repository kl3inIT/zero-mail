import { useMutation } from '@tanstack/react-query';

import { testProviderKey } from './master-keys-api';

export function useTestProviderKey() {
  return useMutation({
    mutationFn: testProviderKey,
    meta: {
      errorMessage: 'Không thể test key.',
    },
  });
}
