'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { ErrorState } from '@/components/states/ErrorState';
import { LoadingState } from '@/components/states/LoadingState';
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
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Switch } from '@/components/ui/switch';
import { useShadowMode } from '@/features/triage/hooks/useShadowMode';

export function ShadowModeCard() {
  const t = useTranslations();
  const shadowMode = useShadowMode();
  const [confirmOffOpen, setConfirmOffOpen] = useState(false);

  if (shadowMode.state.isPending) {
    return <LoadingState variant="cards" count={1} />;
  }

  if (shadowMode.state.isError) {
    return (
      <ErrorState
        heading={t('triage.shadow.error.title')}
        body={t('triage.shadow.error.body')}
        retryLabel={t('triage.shadow.error.retry')}
        onRetry={() => void shadowMode.state.refetch()}
      />
    );
  }

  return (
    <Card>
      <CardHeader className="grid-cols-[1fr_auto]">
        <div className="space-y-1">
          <CardTitle>{t('triage.shadow.title')}</CardTitle>
          <CardDescription>{t('triage.shadow.body')}</CardDescription>
        </div>
        {shadowMode.enabled ? (
          <Badge className="bg-sky-500/10 text-sky-700 dark:text-sky-300" variant="outline">
            {t('triage.shadow.badgeOn')}
          </Badge>
        ) : null}
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center justify-between gap-4 rounded-lg border p-3">
          <div className="space-y-1">
            <p className="text-foreground text-sm font-medium">{t('triage.shadow.toggleLabel')}</p>
            <p className="text-muted-foreground text-sm leading-6">
              {shadowMode.enabled ? t('triage.shadow.onBody') : t('triage.shadow.offBody')}
            </p>
          </div>
          <Switch
            checked={shadowMode.enabled}
            aria-label={t('triage.shadow.toggleLabel')}
            disabled={shadowMode.mutation.isPending}
            onCheckedChange={(enabled) => {
              if (enabled) {
                shadowMode.mutation.mutate(true);
                return;
              }
              setConfirmOffOpen(true);
            }}
            data-testid="shadow-mode-switch"
          />
        </div>
      </CardContent>
      <AlertDialog open={confirmOffOpen} onOpenChange={setConfirmOffOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('triage.shadow.confirm.title')}</AlertDialogTitle>
            <AlertDialogDescription>{t('triage.shadow.confirm.body')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('triage.shadow.confirm.cancel')}</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                setConfirmOffOpen(false);
                shadowMode.mutation.mutate(false);
              }}
            >
              {t('triage.shadow.confirm.action')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}
