'use client';

import { useMemo } from 'react';
import { useTranslations } from 'next-intl';
import {
  AlertCircle,
  Archive,
  CheckCircle2,
  FileText,
  Forward,
  Loader2,
  MailOpen,
  Newspaper,
  OctagonAlert,
  Reply,
  Send,
  Star,
  Tags,
  type LucideIcon,
} from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import type { RuleCatalogActionDescriptorResponse } from '@/features/rules/api/rule-catalog-api';

const OUTBOUND_ACTION_KEYS = new Set(['send_reply', 'forward_email', 'send_email']);

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
  autoSendRulesEnabled: boolean;
  isLoadingActions?: boolean;
  isActionsError?: boolean;
  isLoadingAutomationSetting?: boolean;
};

export function AvailableActionsPanel({
  actions,
  autoSendRulesEnabled,
  isLoadingActions = false,
  isActionsError = false,
  isLoadingAutomationSetting = false,
}: Props) {
  const t = useTranslations();
  const sortedActions = useMemo(
    () => [...actions].sort((left, right) => left.displayOrder - right.displayOrder),
    [actions],
  );

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base">
          <CheckCircle2 className="text-primary size-4" aria-hidden="true" />
          {t('rules.actions.title')}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {isLoadingActions ? (
          <div className="grid min-h-28 gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {[0, 1, 2].map((index) => (
              <div key={index} className="bg-muted/40 h-20 animate-pulse rounded-md border" />
            ))}
          </div>
        ) : isActionsError ? (
          <div className="border-warning/40 bg-warning-soft/50 text-warning flex items-start gap-2 rounded-md border p-3 text-sm">
            <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <span>{t('rules.actions.error')}</span>
          </div>
        ) : sortedActions.length === 0 ? (
          <div className="text-muted-foreground bg-muted/20 rounded-md border border-dashed p-3 text-sm">
            {t('rules.actions.empty')}
          </div>
        ) : (
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {sortedActions.map((action) => (
              <ActionItem
                key={action.actionKey}
                action={action}
                autoSendRulesEnabled={autoSendRulesEnabled}
                isLoadingAutomationSetting={isLoadingAutomationSetting}
              />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function ActionItem({
  action,
  autoSendRulesEnabled,
  isLoadingAutomationSetting,
}: {
  action: RuleCatalogActionDescriptorResponse;
  autoSendRulesEnabled: boolean;
  isLoadingAutomationSetting: boolean;
}) {
  const t = useTranslations();
  const Icon = ACTION_ICONS[action.actionKey] ?? CheckCircle2;
  const isOutbound = OUTBOUND_ACTION_KEYS.has(action.actionKey);
  const isAvailable = action.availabilityStatus === 'AVAILABLE';

  return (
    <div className="bg-background min-h-28 rounded-md border p-3">
      <div className="flex items-start gap-2">
        <Icon className="text-muted-foreground mt-0.5 size-4 shrink-0" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <p className="text-foreground text-sm font-semibold">{action.label}</p>
            <RiskBadge riskLevel={action.riskLevel} />
          </div>
          <p className="text-muted-foreground mt-1 text-xs leading-5">{action.description}</p>
        </div>
      </div>

      <div className="mt-3 flex flex-wrap gap-1.5">
        {isAvailable ? (
          <Badge variant="outline" className="rounded-sm text-[11px]">
            {t('rules.actions.available')}
          </Badge>
        ) : (
          <Badge
            variant="outline"
            className="border-warning/40 text-warning rounded-sm text-[11px]"
          >
            {t('rules.actions.unavailableReason', { status: action.availabilityStatus })}
          </Badge>
        )}

        {isOutbound && (
          <Badge
            variant={autoSendRulesEnabled ? 'secondary' : 'outline'}
            className="rounded-sm text-[11px]"
          >
            {isLoadingAutomationSetting ? (
              <span className="inline-flex items-center gap-1">
                <Loader2 className="size-3 animate-spin" aria-hidden="true" />
                {t('rules.actions.autoSendChecking')}
              </span>
            ) : autoSendRulesEnabled ? (
              t('rules.actions.willAutoSend')
            ) : (
              t('rules.actions.saveDraftInstead')
            )}
          </Badge>
        )}
      </div>
    </div>
  );
}

function RiskBadge({ riskLevel }: { riskLevel: string }) {
  const t = useTranslations();
  const normalizedRisk = riskLevel.toUpperCase();
  const label =
    normalizedRisk === 'HIGH'
      ? t('rules.actions.risk.high')
      : normalizedRisk === 'MEDIUM'
        ? t('rules.actions.risk.medium')
        : t('rules.actions.risk.low');

  return (
    <Badge variant="outline" className="rounded-sm text-[11px]">
      {label}
    </Badge>
  );
}
