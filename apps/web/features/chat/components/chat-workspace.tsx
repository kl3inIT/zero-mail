'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useState } from 'react';

import { ConversationPane } from './conversation-pane';
import { HistorySidebar } from './history-sidebar';

function createChatId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;
}

export function ChatWorkspace() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryChatId = searchParams.get('chat');
  const initialPrompt = searchParams.get('prompt') ?? '';
  const [newChatId, setNewChatId] = useState(() => createChatId());
  const activeChatId = queryChatId ?? newChatId;
  const paneKey = `${activeChatId}:${initialPrompt}`;

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
