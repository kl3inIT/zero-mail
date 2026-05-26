'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';

import { ConversationPane } from './conversation-pane';
import { HistorySidebar } from './history-sidebar';

function createChatId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;
}

export function ChatWorkspace() {
  const t = useTranslations('chat.page');
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryChatId = searchParams.get('chat');
  const initialPrompt = searchParams.get('prompt') ?? '';
  const [newChatId, setNewChatId] = useState(() => createChatId());
  const activeChatId = queryChatId ?? newChatId;
  const paneKey = useMemo(() => `${activeChatId}:${initialPrompt}`, [activeChatId, initialPrompt]);

  function handleSelectChat(chatId: string) {
    router.push(`/chat?chat=${chatId}`);
  }

  function handleNewChat() {
    setNewChatId(createChatId());
    router.push('/chat');
  }

  function handleChatStarted() {
    if (!queryChatId) {
      router.replace(`/chat?chat=${activeChatId}`);
    }
  }

  return (
    <div className="flex h-full min-h-0 overflow-hidden">
      <HistorySidebar
        activeChatId={queryChatId}
        onSelectChat={handleSelectChat}
        onNewChat={handleNewChat}
      />
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="border-border border-b px-4 py-3">
          <h1 className="text-[17px] font-semibold">{t('title')}</h1>
          <p className="text-muted-foreground text-sm">{t('subtitle')}</p>
        </div>
        <ConversationPane
          key={paneKey}
          chatId={activeChatId}
          historyChatId={queryChatId}
          initialPrompt={initialPrompt}
          onChatStarted={handleChatStarted}
        />
      </div>
    </div>
  );
}
