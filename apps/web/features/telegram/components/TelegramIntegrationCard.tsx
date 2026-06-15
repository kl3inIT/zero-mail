'use client';

import { useEffect, useRef } from 'react';
import { CheckCircle2, Copy, ExternalLink, MessageCircle, RefreshCw, Unplug } from 'lucide-react';
import { useTranslations } from 'next-intl';
import QRCode from 'react-qr-code';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { SettingCard } from '@/features/ai/components/SettingCard';
import {
  useCreateTelegramPairing,
  useDisconnectTelegram,
  useTelegramStatus,
} from '@/features/telegram/hooks/useTelegramIntegration';
import { cn } from '@/lib/utils';

function formatTelegramDate(value: string | null | undefined): string | null {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function botUsernameFromDeeplink(deeplink: string | null | undefined): string | null {
  if (!deeplink) return null;

  try {
    const url = new URL(deeplink);
    if (url.hostname !== 't.me') return null;
    const username = url.pathname.replace(/^\/+/, '').trim();
    return username ? `@${username}` : null;
  } catch {
    return null;
  }
}

export function TelegramIntegrationCard() {
  const t = useTranslations();
  const status = useTelegramStatus();
  const { refetch } = status;
  const pairing = useCreateTelegramPairing();
  const disconnect = useDisconnectTelegram();
  const wasWaitingForConnection = useRef(false);

  const data = status.data;
  const connected = data?.connected ?? false;
  const configured = data?.configured ?? false;
  const showPairing = Boolean(pairing.data && configured && !connected);
  const linkedAt = formatTelegramDate(data?.linkedAt);
  const pairingDeeplink = pairing.data?.deeplink;
  const pairingBotUsername = botUsernameFromDeeplink(pairingDeeplink);

  useEffect(() => {
    if (!pairing.data || connected) return;
    wasWaitingForConnection.current = true;
    const interval = window.setInterval(() => {
      void refetch();
    }, 3000);
    return () => window.clearInterval(interval);
  }, [connected, pairing.data, refetch]);

  useEffect(() => {
    if (!connected || !wasWaitingForConnection.current) return;
    wasWaitingForConnection.current = false;
    toast.success(t('settings.telegramConnection.connectedToast'));
  }, [connected, t]);

  const copyCode = async () => {
    if (!pairing.data?.code) return;
    await navigator.clipboard.writeText(pairing.data.code);
    toast.success(t('settings.telegramConnection.codeCopied'));
  };

  return (
    <SettingCard className="py-3" contentClassName="py-0">
      <div className="space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0 space-y-1">
            <p className="text-foreground text-sm font-medium">
              {connected
                ? data?.telegramUsername
                  ? `@${data.telegramUsername}`
                  : t('settings.telegramConnection.connectedAccount')
                : configured
                  ? t('settings.telegramConnection.ready')
                  : t('settings.telegramConnection.notConfigured')}
            </p>
            {linkedAt ? (
              <p className="text-muted-foreground text-xs">
                {t('settings.telegramConnection.linkedAt', { date: linkedAt })}
              </p>
            ) : null}
          </div>
          <Badge
            variant={connected ? 'default' : configured ? 'secondary' : 'destructive'}
            className={cn('self-start', connected && 'bg-green text-white')}
          >
            {connected
              ? t('settings.telegramConnection.connected')
              : configured
                ? t('settings.telegramConnection.notConnected')
                : t('settings.telegramConnection.unavailable')}
          </Badge>
        </div>

        {showPairing ? (
          <div className="border-border bg-muted/30 space-y-3 rounded-lg border p-3">
            <div className="space-y-1">
              <p className="text-foreground text-sm font-medium">
                {t('settings.telegramConnection.pairingTitle')}
              </p>
              <p className="text-muted-foreground text-xs">
                {t('settings.telegramConnection.pairingBody')}
              </p>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
              {pairingDeeplink ? (
                <div className="border-border bg-background text-foreground flex size-36 shrink-0 items-center justify-center rounded-md border p-2">
                  <QRCode
                    aria-label={t('settings.telegramConnection.qrLabel')}
                    bgColor="transparent"
                    fgColor="currentColor"
                    size={120}
                    value={pairingDeeplink}
                    viewBox="0 0 256 256"
                  />
                </div>
              ) : null}
              <div className="min-w-0 space-y-1 text-sm">
                {pairingBotUsername ? (
                  <p className="text-foreground font-medium">
                    {t('settings.telegramConnection.botLabel')}: {pairingBotUsername}
                  </p>
                ) : null}
                <p className="text-muted-foreground text-xs">
                  {t('settings.telegramConnection.qrHelp')}
                </p>
              </div>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row">
              <Input readOnly value={pairing.data?.code ?? ''} className="font-mono text-xs" />
              <Button type="button" variant="outline" onClick={copyCode} className="shrink-0">
                <Copy className="size-4" aria-hidden="true" />
                {t('settings.telegramConnection.copyCode')}
              </Button>
            </div>
            <div className="flex flex-wrap gap-2">
              <a
                className={buttonVariants()}
                href={pairingDeeplink}
                target="_blank"
                rel="noreferrer"
              >
                <ExternalLink className="size-4" aria-hidden="true" />
                {t('settings.telegramConnection.openTelegram')}
              </a>
              <Button type="button" variant="outline" onClick={() => void refetch()}>
                <RefreshCw className="size-4" aria-hidden="true" />
                {t('settings.telegramConnection.refreshStatus')}
              </Button>
            </div>
          </div>
        ) : null}

        <div className="flex flex-wrap gap-2">
          {!connected && !showPairing ? (
            <Button
              type="button"
              disabled={!configured || pairing.isPending || status.isPending}
              onClick={() => pairing.mutate()}
            >
              <MessageCircle className="size-4" aria-hidden="true" />
              {pairing.isPending
                ? t('settings.telegramConnection.creatingPairing')
                : t('settings.telegramConnection.connectCta')}
            </Button>
          ) : null}
          {connected ? (
            <Button
              type="button"
              variant="outline"
              disabled={disconnect.isPending}
              onClick={() => disconnect.mutate()}
            >
              <Unplug className="size-4" aria-hidden="true" />
              {t('settings.telegramConnection.disconnectCta')}
            </Button>
          ) : null}
          {connected ? (
            <Button type="button" variant="ghost" onClick={() => void refetch()}>
              <RefreshCw className="size-4" aria-hidden="true" />
              {t('settings.telegramConnection.refreshStatus')}
            </Button>
          ) : null}
        </div>

        {status.isError || pairing.isError || disconnect.isError ? (
          <p className="text-destructive text-sm">{t('settings.telegramConnection.error')}</p>
        ) : null}
        {connected ? (
          <p className="text-muted-foreground flex items-center gap-1.5 text-xs">
            <CheckCircle2 className="size-3.5" aria-hidden="true" />
            {t('settings.telegramConnection.readyForChat')}
          </p>
        ) : null}
      </div>
    </SettingCard>
  );
}
