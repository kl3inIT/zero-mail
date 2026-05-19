import type { UIMessage } from 'ai';

import { api, xsrfHeader } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type ChatHistoryListResponse = components['schemas']['ChatHistoryListResponseDto'];
export type ChatHistorySummary = ChatHistoryListResponse['chats'][number];
export type ChatHistoryDetailResponse = components['schemas']['ChatHistoryDetailResponseDto'];
export type ChatHistoryMessage = ChatHistoryDetailResponse['messages'][number];
export type ChatRole = ChatHistoryMessage['role'];
export type ChatMessageParts = components['schemas']['ChatMessagePartsDto'];
export type ChatPart = components['schemas']['ChatPartDto'];
export type ChatStreamRequest = components['schemas']['ChatStreamRequestDto'];
export type ConfirmActionRequest = components['schemas']['ConfirmActionRequestDto'];
export type ConfirmActionResponse = components['schemas']['ConfirmActionResponseDto'];

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

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export async function listChats(pageSize = 50, pageOffset = 0): Promise<ChatHistoryListResponse> {
  const result = await api.GET('/api/chat/history', {
    params: { query: { pageSize, pageOffset } },
  });
  return unwrap(result, `/api/chat/history failed: ${result.response.status}`);
}

export async function loadChat(chatId: string): Promise<ChatHistoryDetailResponse> {
  const result = await api.GET('/api/chat/{chatId}', {
    params: { path: { chatId } },
  });
  return unwrap(result, `/api/chat/${chatId} failed: ${result.response.status}`);
}

export async function softDeleteChat(chatId: string): Promise<void> {
  const result = await api.DELETE('/api/chat/{chatId}', {
    params: { path: { chatId } },
    headers: unsafeHeaders(),
  });
  if (result.error || !result.response.ok) {
    throw (
      result.error ??
      (await parseError(
        result.response,
        `/api/chat/${chatId} delete failed: ${result.response.status}`,
      ))
    );
  }
}

export async function confirmAction(
  chatId: string,
  body: ConfirmActionRequest,
): Promise<ConfirmActionResponse> {
  const result = await api.POST('/api/chat/{chatId}/confirm', {
    params: { path: { chatId } },
    headers: jsonHeaders(),
    body,
  });
  return unwrap(result, `/api/chat/${chatId}/confirm failed: ${result.response.status}`);
}

export async function cancelAction(
  chatId: string,
  body: ConfirmActionRequest,
): Promise<ConfirmActionResponse> {
  const result = await api.POST('/api/chat/{chatId}/cancel', {
    params: { path: { chatId } },
    headers: jsonHeaders(),
    body,
  });
  return unwrap(result, `/api/chat/${chatId}/cancel failed: ${result.response.status}`);
}

function toolUiType(part: ChatPart): string | null {
  const type = part.type;
  if (type.startsWith('tool-')) return type;
  return part.toolName ? `tool-${part.toolName}` : null;
}

function uiRole(role: ChatRole): UIMessage['role'] {
  const loweredRole = role.toLowerCase();
  if (loweredRole === 'user' || loweredRole === 'assistant' || loweredRole === 'system') {
    return loweredRole;
  }
  return 'assistant';
}

export function historyMessageToUIMessage(message: ChatHistoryMessage): UIMessage {
  const role = uiRole(message.role);
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
    const toolType = toolUiType(part);
    if (toolType) {
      return {
        type: toolType,
        toolCallId: part.toolCallId ?? part.partId ?? toolType,
        state: part.state ?? 'input-available',
        input: part.input ?? {},
        output: part.output,
        confirmation: part.confirmation,
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
