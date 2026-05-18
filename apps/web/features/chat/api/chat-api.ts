import type { UIMessage } from 'ai';

import { getApiUrl } from '@/lib/api/base-url';
import { xsrfHeader } from '@/lib/api/client';

export type ChatRole = 'USER' | 'ASSISTANT' | 'SYSTEM';

export type ChatPart = {
  type: string;
  partId?: string | null;
  text?: string | null;
  completedAt?: string | null;
  toolCallId?: string | null;
  toolName?: string | null;
  state?: string | null;
  input?: Record<string, unknown> | null;
  output?: Record<string, unknown> | null;
  confirmation?: Record<string, unknown> | null;
  truncated?: boolean | null;
  errorMessage?: string | null;
};

export type ChatMessageParts = {
  schemaVersion?: number | string;
  parts?: ChatPart[];
};

export type ChatHistoryMessage = {
  id: string;
  role: ChatRole;
  parts: ChatMessageParts;
  createdAt: string;
};

export type ChatHistorySummary = {
  id: string;
  title: string;
  updatedAt: string;
  messageCount: number;
};

export type ChatHistoryListResponse = {
  chats: ChatHistorySummary[];
  pageSize: number;
  pageOffset: number;
};

export type ChatHistoryDetailResponse = {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messages: ChatHistoryMessage[];
};

export type ConfirmActionRequest = {
  toolCallId: string;
  contentOverride?: Record<string, unknown>;
  vipAcknowledged: boolean;
};

export type ConfirmActionResponse = {
  state: string;
};

function jsonHeaders(): HeadersInit {
  return { 'Content-Type': 'application/json', ...xsrfHeader() };
}

function unsafeHeaders(): HeadersInit {
  return { ...xsrfHeader() };
}

async function parseError(response: Response, fallbackMessage: string): Promise<unknown> {
  try {
    const body = (await response.json()) as unknown;
    return body && typeof body === 'object' ? body : new Error(fallbackMessage);
  } catch {
    return new Error(fallbackMessage);
  }
}

async function readJson<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (!response.ok) {
    throw await parseError(response, fallbackMessage);
  }
  return (await response.json()) as T;
}

export async function listChats(pageSize = 50, pageOffset = 0): Promise<ChatHistoryListResponse> {
  const params = new URLSearchParams({
    pageSize: String(pageSize),
    pageOffset: String(pageOffset),
  });
  const response = await fetch(
    getApiUrl(`/api/chat/history?${params.toString()}` as `/${string}`),
    {
      method: 'GET',
      credentials: 'include',
    },
  );
  return readJson(response, `/api/chat/history failed: ${response.status}`);
}

export async function loadChat(chatId: string): Promise<ChatHistoryDetailResponse> {
  const response = await fetch(getApiUrl(`/api/chat/${chatId}`), {
    method: 'GET',
    credentials: 'include',
  });
  return readJson(response, `/api/chat/${chatId} failed: ${response.status}`);
}

export async function softDeleteChat(chatId: string): Promise<void> {
  const response = await fetch(getApiUrl(`/api/chat/${chatId}`), {
    method: 'DELETE',
    credentials: 'include',
    headers: unsafeHeaders(),
  });
  if (!response.ok) {
    throw await parseError(response, `/api/chat/${chatId} delete failed: ${response.status}`);
  }
}

export async function confirmAction(
  chatId: string,
  body: ConfirmActionRequest,
): Promise<ConfirmActionResponse> {
  const response = await fetch(getApiUrl(`/api/chat/${chatId}/confirm`), {
    method: 'POST',
    credentials: 'include',
    headers: jsonHeaders(),
    body: JSON.stringify(body),
  });
  return readJson(response, `/api/chat/${chatId}/confirm failed: ${response.status}`);
}

export async function cancelAction(
  chatId: string,
  body: ConfirmActionRequest,
): Promise<ConfirmActionResponse> {
  const response = await fetch(getApiUrl(`/api/chat/${chatId}/cancel`), {
    method: 'POST',
    credentials: 'include',
    headers: jsonHeaders(),
    body: JSON.stringify(body),
  });
  return readJson(response, `/api/chat/${chatId}/cancel failed: ${response.status}`);
}

export function historyMessageToUIMessage(message: ChatHistoryMessage): UIMessage {
  const role = message.role.toLowerCase() as UIMessage['role'];
  const parts = (message.parts.parts ?? []).map((part) => {
    if (part.type === 'assistant-text') {
      return { type: 'text', text: part.text ?? '' };
    }
    if (part.type === 'text') {
      return { type: 'text', text: part.text ?? '' };
    }
    if (part.type === 'data-error') {
      return {
        type: 'data-error',
        data: {
          errorMessage: part.errorMessage ?? '',
          toolCallId: part.toolCallId,
          toolName: part.toolName,
        },
      };
    }
    if (part.type.startsWith('tool-')) {
      return {
        type: part.type,
        toolCallId: part.toolCallId ?? part.partId ?? part.type,
        state: part.state ?? 'input-available',
        input: part.input ?? {},
        output: part.output ?? undefined,
        confirmation: part.confirmation ?? undefined,
      };
    }
    return { type: 'text', text: '' };
  });

  return {
    id: message.id,
    role,
    parts,
    metadata: { persisted: true, createdAt: message.createdAt },
  } as UIMessage;
}

export function historyDetailToUIMessages(detail?: ChatHistoryDetailResponse): UIMessage[] {
  return detail?.messages.map(historyMessageToUIMessage) ?? [];
}
