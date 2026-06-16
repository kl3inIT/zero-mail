'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  createTelegramPairing,
  disconnectTelegram,
  getTelegramStatus,
} from '@/features/telegram/api/telegram-api';
import { telegramQueryKeys } from '@/features/telegram/query-keys';

export function useTelegramStatus() {
  return useQuery({
    queryKey: telegramQueryKeys.status(),
    queryFn: ({ signal }) => getTelegramStatus(signal),
  });
}

export function useCreateTelegramPairing() {
  return useMutation({
    mutationFn: createTelegramPairing,
  });
}

export function useDisconnectTelegram() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: disconnectTelegram,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: telegramQueryKeys.all });
    },
  });
}
