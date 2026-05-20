'use client';

import { useTranslations } from 'next-intl';

import { Checkbox } from '@/components/ui/checkbox';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import type { UnsubscribeCandidateResponse } from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import { MethodBadge } from '@/features/cleanup/unsubscribe-campaign/components/MethodBadge';
import { RiskBadge } from '@/features/cleanup/unsubscribe-campaign/components/RiskBadge';
import { cn } from '@/lib/utils';

type CandidateRow = UnsubscribeCandidateResponse & { riskBadge?: string };

function deriveRiskBadge(candidate: CandidateRow): string {
  if (candidate.riskBadge) return candidate.riskBadge;
  if (candidate.suppressed) return 'SUPPRESSED_BLOCKED';
  if (candidate.unsubscribeMethod === 'NONE') return 'NO_HEADER_DISABLED';
  return 'SAFE';
}

export function CandidateListTable({
  candidates,
  selectedEmails,
  onToggleEmail,
}: {
  candidates: CandidateRow[];
  selectedEmails: Set<string>;
  onToggleEmail: (senderEmail: string) => void;
}) {
  const t = useTranslations();

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-10" aria-label="select" />
          <TableHead>{t('cleanup.unsubscribe.list.col.sender')}</TableHead>
          <TableHead>{t('cleanup.unsubscribe.list.col.domain')}</TableHead>
          <TableHead className="tabular-nums">{t('cleanup.unsubscribe.list.col.count')}</TableHead>
          <TableHead>{t('cleanup.unsubscribe.list.col.method')}</TableHead>
          <TableHead>{t('cleanup.unsubscribe.list.col.risk')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {candidates.map((candidate) => {
          const senderEmail = candidate.senderEmail ?? '';
          const method = candidate.unsubscribeMethod ?? 'NONE';
          const isDisabled = method === 'NONE' || candidate.suppressed === true;
          const isChecked = selectedEmails.has(senderEmail);
          const risk = deriveRiskBadge(candidate);
          const showTooltip = risk === 'NO_HEADER_DISABLED';

          const rowContent = (
            <TableRow
              key={senderEmail}
              className={cn(isDisabled && 'opacity-60')}
              data-state={isChecked ? 'selected' : undefined}
            >
              <TableCell>
                <Checkbox
                  aria-label={senderEmail}
                  checked={isChecked}
                  disabled={isDisabled}
                  onCheckedChange={() => {
                    if (!isDisabled) onToggleEmail(senderEmail);
                  }}
                />
              </TableCell>
              <TableCell className="font-medium">{senderEmail}</TableCell>
              <TableCell className="text-muted-foreground">
                {candidate.senderDomain ?? ''}
              </TableCell>
              <TableCell className="tabular-nums">{candidate.messageCount ?? 0}</TableCell>
              <TableCell>
                <MethodBadge method={method} />
              </TableCell>
              <TableCell>
                <RiskBadge risk={risk} />
              </TableCell>
            </TableRow>
          );

          if (!showTooltip) return rowContent;

          return (
            <Tooltip key={senderEmail}>
              <TooltipTrigger render={rowContent} />
              <TooltipContent>{t('cleanup.unsubscribe.risk.noHeaderTooltip')}</TooltipContent>
            </Tooltip>
          );
        })}
      </TableBody>
    </Table>
  );
}
