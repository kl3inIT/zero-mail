'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  listChats,
  loadChat,
  softDeleteChat,
  type ChatHistoryListResponse,
} from '@/features/chat/api/chat-api';
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

// Optimistic delete: chat row vanishes from history-sidebar immediately;
// restored on error.
export function useSoftDeleteChat() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (chatId: string) => softDeleteChat(chatId),
    onMutate: async (chatId) => {
      await queryClient.cancelQueries({ queryKey: chatKeys.list() });
      const previous = queryClient.getQueryData<ChatHistoryListResponse>(chatKeys.list());
      if (previous) {
        queryClient.setQueryData<ChatHistoryListResponse>(chatKeys.list(), {
          ...previous,
          chats: previous.chats.filter((chat) => chat.id !== chatId),
        });
      }
      return { previous };
    },
    onError: (_error, _chatId, context) => {
      if (context?.previous) {
        queryClient.setQueryData(chatKeys.list(), context.previous);
      }
    },
    onSettled: (_data, _error, chatId) => {
      queryClient.invalidateQueries({ queryKey: chatKeys.list() });
      queryClient.invalidateQueries({ queryKey: chatKeys.detail(chatId) });
    },
  });
}
