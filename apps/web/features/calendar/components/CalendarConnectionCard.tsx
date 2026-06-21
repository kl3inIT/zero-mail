'use client';

import { MoreHorizontalIcon } from 'lucide-react';
import { useFormatter, useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { CalendarList } from '@/features/calendar/components/CalendarList';
import { useDisconnectCalendarConnection } from '@/features/calendar/hooks/use-disconnect-calendar-connection';
import type { CalendarConnection } from '@/features/calendar/types';

interface CalendarConnectionCardProps {
  mailboxId: string;
  connection: CalendarConnection;
}

/**
 * One Card per connected Google Calendar account (D-07). Mirrors Inbox Zero's
 * CalendarConnectionCard.tsx composition (header with status + dropdown
 * overflow menu containing Disconnect, content with collapsible sub-calendar
 * list) using raw shadcn primitives + token classes.
 */
export function CalendarConnectionCard({ mailboxId, connection }: CalendarConnectionCardProps) {
  const t = useTranslations();
  const format = useFormatter();
  const disconnect = useDisconnectCalendarConnection(mailboxId);

  const displayName = connection.googleProfileName ?? connection.googleEmail;
  const isConnected = connection.status === 'CONNECTED';

  return (
    <Card data-testid={`calendar-connection-card-${connection.id}`}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <span className="truncate">{displayName}</span>
          <Badge
            variant={isConnected ? 'secondary' : 'destructive'}
            data-testid={`calendar-status-badge-${connection.id}`}
          >
            {isConnected
              ? t('calendar.card.statusConnected')
              : t('calendar.card.statusDisconnected')}
          </Badge>
        </CardTitle>
        {connection.connectedAt ? (
          <CardDescription>
            {t('calendar.card.connectedSince', {
              date: format.dateTime(new Date(connection.connectedAt), {
                year: 'numeric',
                month: 'short',
                day: '2-digit',
              }),
            })}
          </CardDescription>
        ) : null}
        <CardAction>
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  aria-label={t('calendar.card.menuLabel')}
                  data-testid={`calendar-card-menu-${connection.id}`}
                />
              }
            >
              <MoreHorizontalIcon className="size-4" aria-hidden="true" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                variant="destructive"
                disabled={disconnect.isPending || !isConnected}
                onClick={() => disconnect.mutate(connection.id)}
                data-testid={`calendar-disconnect-${connection.id}`}
              >
                {t('calendar.card.disconnect')}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </CardAction>
      </CardHeader>
      <CardContent>
        <CalendarList mailboxId={mailboxId} calendars={connection.calendars} />
      </CardContent>
    </Card>
  );
}
