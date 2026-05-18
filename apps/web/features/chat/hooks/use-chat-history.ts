'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { listChats, loadChat, softDeleteChat } from '@/features/chat/api/chat-api';
import { chatKeys } from '@/features/chat/query-keys';

export function useChatHistory() {
  return useQuery({ queryKey: chatKeys.list(), queryFn: () => listChats() });
}

export function useChatDetail(chatId: string | null | undefined) {
  return useQuery({
    queryKey: chatKeys.detail(chatId ?? 'missing-chat'),
    queryFn: () => loadChat(chatId ?? ''),
    enabled: Boolean(chatId),
  });
}

export function useSoftDeleteChat() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (chatId: string) => softDeleteChat(chatId),
    onSuccess: async (_data, chatId) => {
      await queryClient.invalidateQueries({ queryKey: chatKeys.list() });
      await queryClient.invalidateQueries({ queryKey: chatKeys.detail(chatId) });
    },
  });
}
