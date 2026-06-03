'use client';

import { useState } from 'react';
import {
  ArchiveIcon,
  ArchiveRestoreIcon,
  Loader2Icon,
  MailXIcon,
  ThumbsDownIcon,
  ThumbsUpIcon,
  TrashIcon,
  XIcon,
} from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { cn } from '@/lib/utils';

const CAMPAIGN_SENDER_CAP = 25;

export function SelectionToolbar({
  selectedCount,
  totalCount,
  selectedMailCount,
  unsubscribeLabel,
  allSelectedApproved,
  onClear,
  onUnsubscribeOrBlock,
  onAutoArchive,
  onApproveToggle,
  onArchive,
  onDelete,
  isExecuting,
}: {
  selectedCount: number;
  totalCount: number;
  selectedMailCount?: number;
  unsubscribeLabel: string;
  allSelectedApproved: boolean;
  onClear: () => void;
  onUnsubscribeOrBlock: () => void;
  onAutoArchive: () => void;
  onApproveToggle: () => void;
  onArchive: () => void;
  onDelete: () => void;
  isExecuting?: boolean;
}) {
  const t = useTranslations();
  const [archiveDialogOpen, setArchiveDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const overCap = selectedCount > CAMPAIGN_SENDER_CAP;
  const actionDisabled = selectedCount === 0 || overCap || Boolean(isExecuting);

  return (
    <>
      <div className="border-border bg-card flex flex-col gap-3 rounded-lg border px-3 py-2 shadow-sm md:flex-row md:items-center md:justify-between">
        <div aria-live="polite" className="flex items-center gap-3">
          <Button type="button" variant="ghost" size="icon-sm" disabled={selectedCount === 0} onClick={onClear}>
            <XIcon className="size-4" aria-hidden="true" />
            <span className="sr-only">{t('cleanup.unsubscribe.list.clear')}</span>
          </Button>
          <div className="flex flex-col gap-0.5">
            <span
              className={cn(
                'text-sm font-medium tabular-nums',
                overCap ? 'text-destructive font-semibold' : 'text-foreground',
              )}
            >
              {overCap
                ? t('cleanup.unsubscribe.list.counterOver', { count: selectedCount })
                : t('cleanup.unsubscribe.list.counter', { count: selectedCount, total: totalCount })}
            </span>
            {selectedMailCount !== undefined && selectedCount > 0 && (
              <span className="text-muted-foreground text-xs">
                {t('cleanup.unsubscribe.list.selectedMail', { count: selectedMailCount })}
              </span>
            )}
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-1.5 md:justify-end">
          <ToolbarButton
            icon={MailXIcon}
            label={unsubscribeLabel}
            disabled={actionDisabled}
            loading={isExecuting}
            onClick={onUnsubscribeOrBlock}
          />
          <ToolbarButton
            icon={ArchiveRestoreIcon}
            label={t('cleanup.unsubscribe.list.action.autoArchive')}
            disabled={actionDisabled}
            onClick={onAutoArchive}
          />
          <ToolbarButton
            icon={allSelectedApproved ? ThumbsDownIcon : ThumbsUpIcon}
            label={
              allSelectedApproved
                ? t('cleanup.unsubscribe.list.action.unapprove')
                : t('cleanup.unsubscribe.list.action.approve')
            }
            disabled={actionDisabled}
            onClick={onApproveToggle}
          />
          <ToolbarButton
            icon={ArchiveIcon}
            label={t('cleanup.unsubscribe.list.action.archive')}
            disabled={actionDisabled}
            onClick={() => setArchiveDialogOpen(true)}
          />
          <ToolbarButton
            icon={TrashIcon}
            label={t('cleanup.unsubscribe.list.action.delete')}
            disabled={actionDisabled}
            danger
            onClick={() => setDeleteDialogOpen(true)}
          />
        </div>
      </div>

      <Dialog open={archiveDialogOpen} onOpenChange={setArchiveDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {t('cleanup.unsubscribe.confirm.archiveTitle', { count: selectedCount })}
            </DialogTitle>
            <DialogDescription>
              {t('cleanup.unsubscribe.confirm.archiveBody', { count: selectedCount })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setArchiveDialogOpen(false)}>
              {t('cleanup.unsubscribe.confirm.cancel')}
            </Button>
            <Button
              type="button"
              onClick={() => {
                onArchive();
                setArchiveDialogOpen(false);
              }}
            >
              <ArchiveIcon className="size-4" aria-hidden="true" />
              {t('cleanup.unsubscribe.list.action.archive')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {t('cleanup.unsubscribe.confirm.deleteTitle', { count: selectedCount })}
            </DialogTitle>
            <DialogDescription>
              {t('cleanup.unsubscribe.confirm.deleteBody', { count: selectedCount })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setDeleteDialogOpen(false)}>
              {t('cleanup.unsubscribe.confirm.cancel')}
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={() => {
                onDelete();
                setDeleteDialogOpen(false);
              }}
            >
              <TrashIcon className="size-4" aria-hidden="true" />
              {t('cleanup.unsubscribe.list.action.delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

function ToolbarButton({
  icon: Icon,
  label,
  onClick,
  disabled,
  loading,
  danger,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  onClick: () => void;
  disabled?: boolean;
  loading?: boolean;
  danger?: boolean;
}) {
  return (
    <Button
      type="button"
      variant={danger ? 'destructive' : 'ghost'}
      size="sm"
      disabled={disabled}
      onClick={onClick}
      className="h-8"
      title={label}
    >
      {loading ? (
        <Loader2Icon className="size-4 animate-spin" aria-hidden="true" />
      ) : (
        <Icon className="size-4" aria-hidden="true" />
      )}
      <span className="hidden sm:inline">{label}</span>
    </Button>
  );
}
