'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import {
  cancelAction,
  confirmAction,
  type ConfirmActionRequest,
} from '@/features/chat/api/chat-api';
import { chatKeys } from '@/features/chat/query-keys';

type ConfirmVariables = {
  chatId: string;
  body: ConfirmActionRequest;
};

export function useConfirmAction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ chatId, body }: ConfirmVariables) => confirmAction(chatId, body),
    onSuccess: async (_response, variables) => {
      await queryClient.invalidateQueries({ queryKey: chatKeys.detail(variables.chatId) });
      await queryClient.invalidateQueries({ queryKey: chatKeys.list() });
    },
  });
}

export function useCancelAction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ chatId, body }: ConfirmVariables) => cancelAction(chatId, body),
    onSuccess: async (_response, variables) => {
      await queryClient.invalidateQueries({ queryKey: chatKeys.detail(variables.chatId) });
      await queryClient.invalidateQueries({ queryKey: chatKeys.list() });
    },
  });
}
