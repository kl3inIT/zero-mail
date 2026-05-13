'use client';

import { useEffect, useRef, useState } from 'react';
import { Loader2, PenLine, RefreshCw } from 'lucide-react';
import { useTranslations } from 'next-intl';

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import {
  isDraftGenerationInFlight,
  type DraftStatus,
} from '@/features/needs-reply/api/needs-reply-api';
import { useGenerateDraft } from '@/features/needs-reply/hooks/useGenerateDraft';

type GenerateDraftButtonProps = {
  gmailThreadId: string;
  draftStatus?: DraftStatus;
  compact?: boolean;
  onDraftReady?: () => void;
};

const COOLDOWN_MS = 4_000;

export function GenerateDraftButton({
  gmailThreadId,
  draftStatus = 'NO_DRAFT',
  compact = false,
  onDraftReady,
}: GenerateDraftButtonProps) {
  const t = useTranslations();
  const mutation = useGenerateDraft();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [showInFlightNotice, setShowInFlightNotice] = useState(false);
  const [coolingDown, setCoolingDown] = useState(false);
  const cooldownTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isRegenerate = draftStatus !== 'NO_DRAFT';
  const pending = mutation.isPending;
  const disabled = pending || coolingDown;
  const label = pending
    ? isRegenerate
      ? t('needsReply.action.regenerating')
      : t('needsReply.action.generating')
    : isRegenerate
      ? t('needsReply.action.regenerateDraft')
      : t('needsReply.action.draftReply');
  const Icon = pending ? Loader2 : isRegenerate ? RefreshCw : PenLine;

  useEffect(() => {
    return () => {
      if (cooldownTimeout.current) clearTimeout(cooldownTimeout.current);
    };
  }, []);

  function startCooldown(): void {
    setCoolingDown(true);
    setShowInFlightNotice(true);
    if (cooldownTimeout.current) clearTimeout(cooldownTimeout.current);
    cooldownTimeout.current = setTimeout(() => setCoolingDown(false), COOLDOWN_MS);
  }

  function runGeneration(): void {
    mutation.mutate(gmailThreadId, {
      onSuccess: () => {
        setShowInFlightNotice(false);
        setConfirmOpen(false);
        onDraftReady?.();
      },
      onError: (error) => {
        if (isDraftGenerationInFlight(error)) {
          startCooldown();
        }
      },
    });
  }

  return (
    <div className="grid gap-1.5">
      <Button
        type="button"
        variant="outline"
        size={compact ? 'icon-lg' : 'sm'}
        aria-label={compact ? label : undefined}
        disabled={disabled}
        onClick={() => {
          if (isRegenerate) {
            setConfirmOpen(true);
            return;
          }
          runGeneration();
        }}
      >
        <Icon className={pending ? 'size-4 animate-spin' : 'size-4'} aria-hidden="true" />
        {compact ? <span className="sr-only">{label}</span> : <span>{label}</span>}
      </Button>

      {showInFlightNotice ? (
        <Alert variant="warning" className="max-w-64 py-1.5 text-xs">
          <AlertDescription className="text-xs">
            {t('needsReply.notice.draftInFlight')}
          </AlertDescription>
        </Alert>
      ) : null}

      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('needsReply.dialog.replaceTitle')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('needsReply.dialog.replaceDescription')}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('needsReply.dialog.cancel')}</AlertDialogCancel>
            <AlertDialogAction variant="destructive" disabled={pending} onClick={runGeneration}>
              {t('needsReply.dialog.replace')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
