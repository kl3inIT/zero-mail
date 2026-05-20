import { useMutation } from '@tanstack/react-query';

import { mintEditSession } from './master-keys-api';

export function useEditSession(provider: string) {
  return useMutation({
    mutationFn: () => mintEditSession(provider),
  });
}
