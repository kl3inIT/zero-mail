'use client';

import { MessageSquarePlus, PanelRightOpen } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useRouter, useSearchParams } from 'next/navigation';
import { useState } from 'react';

import { Button } from '@/components/ui/button';

import { ConversationPane } from './conversation-pane';
import { HistorySidebar } from './history-sidebar';

function createChatId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;
}

export function ChatWorkspace() {
  const t = useTranslations('chat.history');
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryChatId = searchParams.get('chat');
  const initialPrompt = searchParams.get('prompt') ?? '';
  const [newChatId, setNewChatId] = useState(() => createChatId());
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
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
      <div className="flex min-w-0 flex-1 flex-col">
        {sidebarCollapsed && (
          <div className="flex items-center justify-end gap-1 px-2 pt-2 md:px-3">
            <Button
              size="icon-sm"
              type="button"
              variant="ghost"
              onClick={() => setSidebarCollapsed(false)}
              aria-label={t('title')}
            >
              <PanelRightOpen className="size-4" />
            </Button>
            <Button
              size="icon-sm"
              type="button"
              variant="ghost"
              onClick={handleNewChat}
              aria-label={t('new')}
            >
              <MessageSquarePlus className="size-4" />
            </Button>
          </div>
        )}
        <ConversationPane
          key={paneKey}
          chatId={activeChatId}
          historyChatId={queryChatId}
          initialPrompt={initialPrompt}
          onChatStarted={handleChatStarted}
        />
      </div>
      {!sidebarCollapsed && (
        <HistorySidebar
          activeChatId={queryChatId}
          onSelectChat={handleSelectChat}
          onNewChat={handleNewChat}
          onCollapse={() => setSidebarCollapsed(true)}
        />
      )}
    </div>
  );
}
