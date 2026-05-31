'use client';

import { useMemo } from 'react';
import { useTranslations } from 'next-intl';
import {
  AlertCircle,
  Archive,
  CheckCircle2,
  FileText,
  Forward,
  MailOpen,
  Newspaper,
  OctagonAlert,
  Reply,
  Send,
  Star,
  Tags,
  type LucideIcon,
} from 'lucide-react';

import type { RuleCatalogActionDescriptorResponse } from '@/features/rules/api/rule-catalog-api';

const ACTION_ICONS: Record<string, LucideIcon> = {
  label: Tags,
  archive: Archive,
  save_draft: FileText,
  mark_read: MailOpen,
  star: Star,
  add_to_digest: Newspaper,
  mark_spam: OctagonAlert,
  send_reply: Reply,
  forward_email: Forward,
  send_email: Send,
};

type Props = {
  actions: RuleCatalogActionDescriptorResponse[];
  isLoadingActions?: boolean;
  isActionsError?: boolean;
};

export function AvailableActionsPanel({
  actions,
  isLoadingActions = false,
  isActionsError = false,
}: Props) {
  const t = useTranslations();
  const sortedActions = useMemo(
    () => actions.toSorted((left, right) => left.displayOrder - right.displayOrder),
    [actions],
  );

  return (
    <aside className="space-y-2 xl:sticky xl:top-0 xl:self-start">
      <h3 className="text-sm font-semibold">{t('rules.actions.title')}</h3>
      {isLoadingActions ? (
        <div className="grid gap-1.5">
          {[0, 1, 2, 3].map((index) => (
            <div key={index} className="bg-muted/40 h-9 animate-pulse rounded-md" />
          ))}
        </div>
      ) : isActionsError ? (
        <div className="text-warning flex items-start gap-2 text-sm">
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{t('rules.actions.error')}</span>
        </div>
      ) : sortedActions.length === 0 ? (
        <p className="text-muted-foreground text-sm">{t('rules.actions.empty')}</p>
      ) : (
        <div className="grid gap-1.5">
          {sortedActions.map((action) => (
            <ActionItem key={action.actionKey} action={action} />
          ))}
        </div>
      )}
    </aside>
  );
}

function ActionItem({ action }: { action: RuleCatalogActionDescriptorResponse }) {
  const Icon = ACTION_ICONS[action.actionKey] ?? CheckCircle2;

  return (
    <div className="hover:bg-muted/40 flex items-center gap-2 rounded-md p-2">
      <Icon className="text-muted-foreground size-4 shrink-0" aria-hidden="true" />
      <span className="text-foreground truncate text-sm font-medium">{action.label}</span>
    </div>
  );
}
