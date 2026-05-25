'use client';

import { useTranslations } from 'next-intl';

import { Alert, AlertTitle } from '@/components/ui/alert';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Skeleton } from '@/components/ui/skeleton';
import { SuppressionAddForm } from '@/features/cleanup/suppression/components/SuppressionAddForm';
import { SuppressionTable } from '@/features/cleanup/suppression/components/SuppressionTable';
import { useSuppressionList } from '@/features/cleanup/suppression/hooks/useSuppressionList';

export function SuppressionDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (next: boolean) => void;
}) {
  const t = useTranslations();
  const listQuery = useSuppressionList({ enabled: open });
  const entries = listQuery.data ?? [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[calc(100vh-2rem)] max-w-2xl overflow-hidden sm:max-w-2xl">
        <DialogHeader className="pr-8">
          <DialogTitle>{t('cleanup.suppression.title')}</DialogTitle>
          <DialogDescription>{t('cleanup.suppression.lead')}</DialogDescription>
        </DialogHeader>

        <SuppressionAddForm />

        {listQuery.isPending && (
          <div className="flex flex-col gap-2">
            {Array.from({ length: 4 }).map((_, skeletonIndex) => (
              <Skeleton key={skeletonIndex} className="h-10 w-full" />
            ))}
          </div>
        )}

        {listQuery.isError && (
          <Alert variant="destructive">
            <AlertTitle>{t('cleanup.suppression.error')}</AlertTitle>
          </Alert>
        )}

        {!listQuery.isPending && !listQuery.isError && entries.length === 0 && (
          <div className="border-foreground/10 flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed px-4 py-8 text-center">
            <h2 className="text-foreground text-base font-medium">
              {t('cleanup.suppression.empty.title')}
            </h2>
            <p className="text-muted-foreground max-w-md text-sm">
              {t('cleanup.suppression.empty.body')}
            </p>
          </div>
        )}

        {!listQuery.isPending && !listQuery.isError && entries.length > 0 && (
          <ScrollArea className="max-h-[min(48vh,360px)] rounded-lg border">
            <SuppressionTable entries={entries} />
          </ScrollArea>
        )}
      </DialogContent>
    </Dialog>
  );
}
