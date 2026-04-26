'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { api, xsrfHeader } from '@/lib/api/client';
import type { ApiError } from '@/lib/api/errors';
import { accountKeys } from '@/features/account/api/keys';

export function useUpdateLanguage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (language: 'vi' | 'en') => {
      const res = await api.PATCH('/me/language', {
        body: { language },
        headers: { 'Content-Type': 'application/json', ...xsrfHeader() },
      });
      if (res.error) throw res.error as ApiError;
      return res.data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: accountKeys.me() }),
  });
}
