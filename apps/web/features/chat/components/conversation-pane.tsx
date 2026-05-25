'use client';

import type { UIMessage } from 'ai';
import { ArrowUp, Sparkles } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useMemo, useRef, useState } from 'react';

import {
  Conversation,
  ConversationContent,
  ConversationEmptyState,
  ConversationScrollButton,
} from '@/components/ai/conversation';
import { Loader } from '@/components/ai/loader';
import { Message, MessageContent } from '@/components/ai/message';
import {
  PromptInput,
  type PromptInputMessage,
  PromptInputSubmit,
  PromptInputTextarea,
} from '@/components/ai/prompt-input';
import { Response } from '@/components/ai/response';
import { Suggestion, Suggestions } from '@/components/ai/suggestion';
import { Tool, ToolContent, ToolHeader, ToolInput, ToolOutput } from '@/components/ai/tool';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import {
  historyDetailToUIMessages,
  type ChatHistoryDetailResponse,
} from '@/features/chat/api/chat-api';
import { useChat } from '@/features/chat/hooks/use-chat';
import { useChatDetail } from '@/features/chat/hooks/use-chat-history';
import { PreviewCard } from './preview-card/preview-card';
import {
  isBodySlotToolName,
  isWriteReversibleToolName,
  parseMaybeJsonObject,
  type PreviewCardAction,
} from './preview-card/preview-card-state';

type ToolLikePart = {
  type: string;
  toolCallId?: string;
  state?: string;
  input?: unknown;
  output?: unknown;
  confirmation?: unknown;
  errorText?: string;
};

function toolNameFromPart(part: ToolLikePart): string {
  return part.type.startsWith('tool-') ? part.type.slice('tool-'.length) : part.type;
}

function isPersistedMessage(message: UIMessage): boolean {
  return Boolean((message.metadata as { persisted?: boolean } | undefined)?.persisted);
}

function toPreviewAction({
  chatId,
  message,
  part,
  persistenceConfirmed,
}: {
  chatId: string;
  message: UIMessage;
  part: ToolLikePart;
  persistenceConfirmed: boolean;
}): PreviewCardAction | null {
  const toolName = toolNameFromPart(part);
  if (!isBodySlotToolName(toolName)) return null;

  return {
    kind: toolName,
    chatId,
    messageId: message.id,
    toolCallId: part.toolCallId ?? `${message.id}-${toolName}`,
    state: part.state,
    input: parseMaybeJsonObject(part.input),
    output: parseMaybeJsonObject(part.output),
    confirmation: parseMaybeJsonObject(part.confirmation),
    persistenceConfirmed,
  };
}

function ChatMessageParts({
  chatId,
  message,
  persistenceAckCount,
}: {
  chatId: string;
  message: UIMessage;
  persistenceAckCount: number;
}) {
  return (
    <>
      {message.parts.map((part, index) => {
        if (part.type === 'text') {
          return <Response key={`${message.id}-${index}`}>{part.text}</Response>;
        }

        if (part.type === 'reasoning') {
          return null;
        }

        if (part.type.startsWith('tool-')) {
          const toolPart = part as ToolLikePart;
          const toolName = toolNameFromPart(toolPart);
          const persistenceConfirmed = isPersistedMessage(message) || persistenceAckCount > 0;
          const previewAction = toPreviewAction({
            chatId,
            message,
            part: toolPart,
            persistenceConfirmed,
          });

          if (previewAction) {
            return (
              <PreviewCard
                key={`${message.id}-${toolPart.toolCallId ?? index}`}
                action={previewAction}
              />
            );
          }

          return (
            <Tool key={`${message.id}-${toolPart.toolCallId ?? index}`} defaultOpen>
              <ToolHeader
                type={toolPart.type as never}
                state={(toolPart.state ?? 'input-available') as never}
                title={toolName}
              />
              <ToolContent>
                <ToolInput input={parseMaybeJsonObject(toolPart.input)} />
                <ToolOutput
                  output={parseMaybeJsonObject(toolPart.output)}
                  errorText={toolPart.errorText}
                />
                {isWriteReversibleToolName(toolName) && (
                  <div className="flex justify-end gap-2">
                    <Button type="button" size="sm" variant="outline">
                      Confirm
                    </Button>
                  </div>
                )}
              </ToolContent>
            </Tool>
          );
        }

        return null;
      })}
    </>
  );
}

function useInitialMessages(detail?: ChatHistoryDetailResponse): UIMessage[] {
  return useMemo(() => historyDetailToUIMessages(detail), [detail]);
}

export function ConversationPane({
  chatId,
  historyChatId,
  initialPrompt,
  onChatStarted,
}: {
  chatId: string;
  historyChatId: string | null;
  initialPrompt?: string;
  onChatStarted: () => void;
}) {
  const t = useTranslations('chat');
  const detail = useChatDetail(historyChatId);
  const initialMessages = useInitialMessages(detail.data);
  const chat = useChat({ chatId, initialMessages });
  const [input, setInput] = useState(initialPrompt ?? '');
  const [stopped, setStopped] = useState(false);
  const chatStartedNotifiedRef = useRef(false);
  const suggestions = [
    t('suggestion.rules'),
    t('suggestion.search'),
    t('suggestion.draft'),
    t('suggestion.memory'),
  ];

  useEffect(() => {
    if (historyChatId || chatStartedNotifiedRef.current || chat.persistenceAckCount === 0) {
      return;
    }
    chatStartedNotifiedRef.current = true;
    onChatStarted();
  }, [chat.persistenceAckCount, historyChatId, onChatStarted]);

  async function handleSubmit(message: PromptInputMessage) {
    const text = message.text.trim();
    if (!text) return;
    setStopped(false);
    await chat.sendMessage({ text });
    setInput('');
  }

  function handleStop() {
    void chat.stop();
    setStopped(true);
  }

  if (detail.isLoading && historyChatId && chat.messages.length === 0) {
    return (
      <div className="flex h-full flex-col gap-4 p-6">
        <Skeleton className="h-16 w-2/3 rounded-lg" />
        <Skeleton className="ml-auto h-20 w-1/2 rounded-lg" />
        <Skeleton className="h-24 w-3/4 rounded-lg" />
      </div>
    );
  }

  const messages = chat.messages.length > 0 ? chat.messages : initialMessages;
  const isBusy = chat.status === 'submitted' || chat.status === 'streaming';

  return (
    <section className="bg-background flex min-h-0 flex-1 flex-col" data-testid="chat-pane">
      <Conversation className="min-h-0 flex-1">
        <ConversationContent className="mx-auto w-full max-w-[760px] gap-4 px-4 py-6">
          {messages.length === 0 ? (
            <ConversationEmptyState
              icon={
                <span className="bg-accent-soft text-accent inline-flex rounded-lg p-2">
                  <Sparkles className="size-8" />
                </span>
              }
              title={t('empty.title')}
              description={t('empty.body')}
            >
              <div className="mx-auto grid max-w-[520px] gap-4 text-center">
                <span className="bg-accent-soft text-accent mx-auto inline-flex rounded-lg p-2">
                  <Sparkles className="size-8" />
                </span>
                <div className="space-y-1">
                  <h2 className="text-[22px] font-semibold">{t('empty.title')}</h2>
                  <p className="text-muted-foreground text-sm leading-relaxed">{t('empty.body')}</p>
                </div>
                <Suggestions className="mx-auto justify-center">
                  {suggestions.map((suggestion) => (
                    <Suggestion
                      key={suggestion}
                      suggestion={suggestion}
                      onClick={(value) => setInput(value)}
                    />
                  ))}
                </Suggestions>
              </div>
            </ConversationEmptyState>
          ) : (
            messages.map((message) => (
              <Message from={message.role} key={message.id}>
                <MessageContent>
                  <ChatMessageParts
                    chatId={chatId}
                    message={message}
                    persistenceAckCount={chat.persistenceAckCount}
                  />
                </MessageContent>
              </Message>
            ))
          )}
          {chat.status === 'submitted' && <Loader className="px-1 py-2" />}
          {stopped && (
            <p className="text-text-faint text-[12.5px] italic">{t('stream.cancelled')}</p>
          )}
        </ConversationContent>
        <ConversationScrollButton />
      </Conversation>
      <div className="border-border bg-card border-t px-4 py-3">
        <PromptInput
          onSubmit={handleSubmit}
          className="border-input bg-background mx-auto flex max-w-[760px] items-end gap-2 rounded-lg border p-2"
        >
          <PromptInputTextarea
            value={input}
            placeholder={t('prompt.placeholder')}
            onChange={(event) => setInput(event.currentTarget.value)}
            className="min-h-11"
          />
          <PromptInputSubmit
            aria-label={isBusy ? t('prompt.stop') : t('prompt.send')}
            status={chat.status}
            onStop={handleStop}
            disabled={!input.trim() && !isBusy}
            className="bg-primary text-primary-foreground size-10 rounded-md"
          >
            {!isBusy && <ArrowUp className="size-4" />}
          </PromptInputSubmit>
        </PromptInput>
      </div>
    </section>
  );
}
