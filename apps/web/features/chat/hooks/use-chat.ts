'use client';

import { useChat as useVercelChat } from '@ai-sdk/react';
import { DefaultChatTransport, type UIMessage } from 'ai';
import { useMemo, useState } from 'react';

import { getApiUrl } from '@/lib/api/base-url';
import { xsrfHeader } from '@/lib/api/client';
import type { ChatStreamRequest } from '@/features/chat/api/chat-api';

type UseChatOptions = {
  chatId: string;
  initialMessages: UIMessage[];
};

type PersistenceDataPart = {
  type?: string;
  data?: {
    chatMessageId?: string;
    state?: string;
  };
  chatMessageId?: string;
  state?: string;
};

function lastUserText(messages: UIMessage[]): string {
  const lastUserMessage = [...messages].reverse().find((message) => message.role === 'user');
  if (!lastUserMessage) return '';
  return lastUserMessage.parts
    .filter(
      (part): part is Extract<(typeof lastUserMessage.parts)[number], { type: 'text' }> =>
        part.type === 'text',
    )
    .map((part) => part.text)
    .join('');
}

function isPersistencePart(dataPart: unknown): dataPart is PersistenceDataPart {
  return (
    Boolean(dataPart) &&
    typeof dataPart === 'object' &&
    ((dataPart as PersistenceDataPart).type === 'data-persistence' ||
      (dataPart as PersistenceDataPart).type === 'persistence' ||
      typeof (dataPart as PersistenceDataPart).chatMessageId === 'string' ||
      typeof (dataPart as PersistenceDataPart).data?.chatMessageId === 'string')
  );
}

function xsrfHeaderRecord(): Record<string, string> {
  const headers = xsrfHeader();
  if (headers instanceof Headers) {
    return Object.fromEntries(headers.entries());
  }
  if (Array.isArray(headers)) {
    return Object.fromEntries(headers);
  }
  return headers;
}

export function useChat({ chatId, initialMessages }: UseChatOptions) {
  const [persistenceAckCount, setPersistenceAckCount] = useState(0);
  const transport = useMemo(
    () =>
      new DefaultChatTransport({
        api: getApiUrl('/api/chat'),
        credentials: 'include',
        headers: xsrfHeaderRecord(),
        prepareSendMessagesRequest: ({ id, messages }) => ({
          api: getApiUrl('/api/chat'),
          credentials: 'include',
          headers: xsrfHeaderRecord(),
          body: {
            chatId: id,
            userText: lastUserText(messages),
          } satisfies ChatStreamRequest,
        }),
      }),
    [],
  );

  const chat = useVercelChat({
    id: chatId,
    messages: initialMessages,
    experimental_throttle: 100,
    transport,
    onData: (dataPart) => {
      if (isPersistencePart(dataPart)) {
        setPersistenceAckCount((current) => current + 1);
      }
    },
  });

  return {
    ...chat,
    persistenceAckCount,
  };
}
