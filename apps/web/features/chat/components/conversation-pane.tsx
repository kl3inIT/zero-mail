'use client';

import type { UIMessage } from 'ai';
import { ArrowUp, Loader2, Square } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';

import {
  Conversation,
  ConversationContent,
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
import { Tool, ToolContent, ToolHeader, ToolInput, ToolOutput } from '@/components/ai/tool';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import {
  historyDetailToUIMessages,
  type ChatHistoryDetailResponse,
} from '@/features/chat/api/chat-api';
import { useChat } from '@/features/chat/hooks/use-chat';
import { useChatDetail } from '@/features/chat/hooks/use-chat-history';
import { PreviewCard } from './preview-card/preview-card';
import {
  isBodySlotToolName,
  parseMaybeJsonObject,
  type PreviewCardAction,
} from './preview-card/preview-card-state';
import { StreamingTextResponse } from './streaming-text-response';
import { hasToolResultRenderer, ToolResult } from './tool-results';

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
          return (
            <StreamingTextResponse
              key={`${message.id}-${index}`}
              text={part.text}
              revealIncrementally={message.role === 'assistant' && !isPersistedMessage(message)}
              isStreaming={part.state === 'streaming'}
            />
          );
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

          if (hasToolResultRenderer(toolName)) {
            return (
              <div key={`${message.id}-${toolPart.toolCallId ?? index}`} className="my-1">
                <ToolResult
                  toolName={toolName}
                  input={parseMaybeJsonObject(toolPart.input)}
                  output={parseMaybeJsonObject(toolPart.output)}
                />
              </div>
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

// Best-effort display name from the signed-in email's local part (no name field
// on MeResponse). Returns undefined when the local part doesn't start with a
// letter so we fall back to an unpersonalized greeting.
function deriveFirstName(email?: string): string | undefined {
  if (!email) return undefined;
  const localPart = email.split('@')[0] ?? '';
  const firstToken = (localPart.split(/[._+\-]/)[0] ?? '').replace(/\d+$/, '');
  if (!/^[a-zA-Z]/.test(firstToken)) return undefined;
  return firstToken.charAt(0).toUpperCase() + firstToken.slice(1).toLowerCase();
}

function greetingKeyForHour(
  hour: number,
): 'greeting.night' | 'greeting.morning' | 'greeting.afternoon' | 'greeting.evening' {
  if (hour < 5) return 'greeting.night';
  if (hour < 12) return 'greeting.morning';
  if (hour < 18) return 'greeting.afternoon';
  return 'greeting.evening';
}

// useSyncExternalStore with a server snapshot of `false` lets us render a
// time-based greeting on the client without an SSR hydration mismatch, and
// without a setState-in-effect (banned by react-hooks lint).
const subscribeNoop = () => () => {};

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
  const currentUser = useCurrentUser();
  const [input, setInput] = useState(initialPrompt ?? '');
  const [stopped, setStopped] = useState(false);
  const chatStartedNotifiedRef = useRef(false);
  const suggestions = [
    t('suggestion.rules'),
    t('suggestion.search'),
    t('suggestion.draft'),
    t('suggestion.memory'),
  ];

  // Time-based greeting is resolved on the client only (see subscribeNoop).
  const isClient = useSyncExternalStore(
    subscribeNoop,
    () => true,
    () => false,
  );
  const firstName = deriveFirstName(currentUser.data?.email);
  const greeting = isClient
    ? t(greetingKeyForHour(new Date().getHours()), {
        name: firstName ? `, ${firstName}` : '',
      })
    : null;

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
    setInput('');
    try {
      await chat.sendMessage({ text });
    } catch (sendFailure) {
      setInput(text);
      throw sendFailure;
    }
  }

  function handleStop() {
    void chat.stop();
    setStopped(true);
  }

  function sendSuggestion(suggestion: string) {
    const text = suggestion.trim();
    if (!text) return;
    setStopped(false);
    setInput('');
    void chat.sendMessage({ text });
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
  const isEmpty = messages.length === 0;
  // Show the "assistant is thinking" loader from the moment the user sends until the assistant
  // produces its first visible part (text or a tool call). This bridges the gap that 'submitted'
  // alone misses — e.g. a createRule turn spends seconds compiling before the preview card lands.
  const lastMessage = messages.at(-1);
  const awaitingAssistant =
    isBusy &&
    !(
      lastMessage?.role === 'assistant' &&
      lastMessage.parts.some((part) => part.type === 'text' || part.type.startsWith('tool-'))
    );

  const inputArea = (
    <PromptInput
      onSubmit={handleSubmit}
      className="[&_[data-slot=input-group]]:bg-background [&_[data-slot=input-group]]:items-end [&_[data-slot=input-group]]:gap-2 [&_[data-slot=input-group]]:rounded-2xl [&_[data-slot=input-group]]:p-1.5 [&_[data-slot=input-group]]:shadow-sm"
    >
      <PromptInputTextarea
        value={input}
        placeholder={t('prompt.placeholder')}
        onChange={(event) => setInput(event.currentTarget.value)}
        className="min-h-12"
      />
      <PromptInputSubmit
        aria-label={isBusy ? t('prompt.stop') : t('prompt.send')}
        status={chat.status}
        onStop={handleStop}
        disabled={isBusy ? false : !input.trim()}
        onClick={(event) => {
          if (isBusy) {
            event.preventDefault();
            handleStop();
          }
        }}
        className="bg-primary text-primary-foreground hover:bg-primary/90 disabled:bg-muted disabled:text-muted-foreground size-9 rounded-full disabled:cursor-not-allowed"
      >
        {chat.status === 'submitted' ? (
          <Loader2 className="size-4 animate-spin" />
        ) : chat.status === 'streaming' ? (
          <Square className="size-4 fill-current" />
        ) : (
          <ArrowUp className="size-4" />
        )}
      </PromptInputSubmit>
    </PromptInput>
  );

  // First visit: ChatGPT/Inbox-Zero style — time-based greeting with the
  // composer centered in the viewport and suggestion pills beneath it. Once a
  // conversation exists the composer drops to a sticky bottom bar.
  if (isEmpty) {
    return (
      <section className="bg-background flex min-h-0 flex-1 flex-col" data-testid="chat-pane">
        <div className="flex flex-1 flex-col items-center justify-center px-4">
          <div className="w-full max-w-[760px]">
            <h1 className="mb-6 text-center text-2xl font-light tracking-tight sm:text-3xl">
              {greeting ?? t('empty.title')}
            </h1>
            {inputArea}
            <div className="mt-3 flex flex-wrap justify-center gap-2">
              {suggestions.map((suggestion) => (
                <Button
                  key={suggestion}
                  type="button"
                  variant="outline"
                  size="sm"
                  className="rounded-full"
                  onClick={() => sendSuggestion(suggestion)}
                >
                  {suggestion}
                </Button>
              ))}
            </div>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="bg-background flex min-h-0 flex-1 flex-col" data-testid="chat-pane">
      <Conversation className="min-h-0 flex-1">
        <ConversationContent className="mx-auto w-full max-w-[760px] gap-4 px-4 py-6">
          {messages.map((message) => {
            const hasToolPart = message.parts.some((part) => part.type.startsWith('tool-'));
            return (
              <Message from={message.role} key={message.id}>
                <MessageContent className={hasToolPart ? 'w-full max-w-full' : undefined}>
                  <ChatMessageParts
                    chatId={chatId}
                    message={message}
                    persistenceAckCount={chat.persistenceAckCount}
                  />
                </MessageContent>
              </Message>
            );
          })}
          {awaitingAssistant && <Loader className="px-1 py-2" />}
          {stopped && (
            <p className="text-text-faint text-[12.5px] italic">{t('stream.cancelled')}</p>
          )}
        </ConversationContent>
        <ConversationScrollButton />
      </Conversation>
      <div className="border-border bg-card border-t px-4 py-3">
        <div className="mx-auto max-w-[760px]">{inputArea}</div>
      </div>
    </section>
  );
}
