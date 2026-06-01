'use client';

import { MessageSquarePlus, MoreHorizontal, Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Skeleton } from '@/components/ui/skeleton';
import { useChatHistory, useSoftDeleteChat } from '@/features/chat/hooks/use-chat-history';
import { cn } from '@/lib/utils';

const timeFormatter = new Intl.DateTimeFormat(undefined, {
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

function formatTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return timeFormatter.format(date);
}

export function HistorySidebar({
  activeChatId,
  onSelectChat,
  onNewChat,
}: {
  activeChatId: string | null;
  onSelectChat: (chatId: string) => void;
  onNewChat: () => void;
}) {
  const t = useTranslations('chat.history');
  const history = useChatHistory();
  const deleteChat = useSoftDeleteChat();

  return (
    <aside className="bg-secondary/50 border-border hidden w-[280px] shrink-0 flex-col border-r md:flex">
      <div className="border-border flex items-center justify-between gap-2 border-b p-4">
        <h2 className="text-[17px] font-semibold">{t('title')}</h2>
        <Button
          size="icon-sm"
          type="button"
          variant="ghost"
          onClick={onNewChat}
          aria-label={t('new')}
        >
          <MessageSquarePlus className="size-4" />
        </Button>
      </div>
      <div className="flex-1 overflow-auto p-2">
        {history.isLoading && (
          <div className="space-y-2 p-2">
            {Array.from({ length: 5 }).map((_, index) => (
              <Skeleton key={index} className="h-12 w-full rounded-md" />
            ))}
          </div>
        )}
        {!history.isLoading && history.data?.chats.length === 0 && (
          <p className="text-muted-foreground p-4 text-sm">{t('empty')}</p>
        )}
        <div className="space-y-1">
          {history.data?.chats.map((chat) => {
            const active = chat.id === activeChatId;
            return (
              <div
                key={chat.id}
                data-testid={`chat-history-row-${chat.id}`}
                className={cn(
                  'group relative flex items-center gap-2 rounded-md p-2',
                  active ? 'bg-card shadow-xs' : 'hover:bg-card/70',
                )}
              >
                {active && (
                  <span className="bg-primary absolute top-2 bottom-2 left-0 w-[3px] rounded-full" />
                )}
                <button
                  type="button"
                  className="min-w-0 flex-1 text-left"
                  onClick={() => onSelectChat(chat.id)}
                >
                  <span className="text-foreground block truncate text-sm font-medium">
                    {chat.title || t('new')}
                  </span>
                  <span className="text-muted-foreground mt-0.5 block truncate text-xs">
                    {formatTime(chat.updatedAt)} · {chat.messageCount}
                  </span>
                </button>
                <DropdownMenu>
                  <DropdownMenuTrigger
                    render={
                      <Button
                        size="icon-xs"
                        type="button"
                        variant="ghost"
                        aria-label={t('actions')}
                      />
                    }
                  >
                    <MoreHorizontal className="size-4" />
                  </DropdownMenuTrigger>
                  <DropdownMenuContent>
                    <DropdownMenuItem
                      variant="destructive"
                      disabled={deleteChat.isPending}
                      onClick={() => deleteChat.mutate(chat.id)}
                      data-testid="chat-delete"
                    >
                      <Trash2 className="size-4" />
                      {t('delete')}
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>
            );
          })}
        </div>
      </div>
    </aside>
  );
}
