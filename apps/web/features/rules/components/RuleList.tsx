'use client';

import { useState, type ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { Edit3, Loader2, Trash2 } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { EmptyState } from '@/components/states/EmptyState';
import { LoadingState } from '@/components/states/LoadingState';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import type { RuleResponse } from '@/features/rules/api/rules-api';
import { summarizeActionIntents, summarizeMatcherAst } from '@/features/rules/lib/rule-structure';
import { createRuleStructureCopy } from '@/features/rules/lib/rule-structure-copy';
import { cn } from '@/lib/utils';

type Props = {
  rules: RuleResponse[];
  selectedRuleId: string | null;
  isLoading: boolean;
  pendingRuleId: string | null;
  onSelectRule: (rule: RuleResponse) => void;
  onEditRule: (rule: RuleResponse) => void;
  onToggleEnabled: (rule: RuleResponse) => void;
  onDeleteRule: (rule: RuleResponse) => void;
  action?: ReactNode;
};

export function RuleList({
  rules,
  selectedRuleId,
  isLoading,
  pendingRuleId,
  onSelectRule,
  onEditRule,
  onToggleEnabled,
  onDeleteRule,
  action,
}: Props) {
  const t = useTranslations();
  const [rulePendingDelete, setRulePendingDelete] = useState<RuleResponse | null>(null);

  return (
    <section className="bg-background overflow-hidden rounded-lg border">
      <div className="flex flex-col gap-3 border-b px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-base font-semibold tracking-tight">{t('rules.list.title')}</h2>
          <p className="text-muted-foreground text-xs">{t('rules.list.subtitle')}</p>
        </div>
        {action && <div className="flex-shrink-0">{action}</div>}
      </div>

      {isLoading ? (
        <div className="space-y-4 p-4">
          <LoadingState count={3} />
        </div>
      ) : rules.length === 0 ? (
        <EmptyState
          heading={t('rules.list.empty.heading')}
          body={t('rules.list.empty.body')}
          className="min-h-40 px-4 py-10"
        />
      ) : (
        <>
          <div className="hidden md:block">
            <Table className="table-fixed">
              <TableHeader>
                <TableRow className="bg-muted/20 hover:bg-muted/20 text-muted-foreground text-xs font-semibold">
                  <TableHead className="w-[92px] px-4 py-3">
                    {t('rules.list.column.enabled')}
                  </TableHead>
                  <TableHead className="w-[240px] px-4 py-3">
                    {t('rules.list.column.name')}
                  </TableHead>
                  <TableHead className="px-4 py-3">{t('rules.list.when')}</TableHead>
                  <TableHead className="px-4 py-3">{t('rules.list.then')}</TableHead>
                  <TableHead className="w-[88px] px-2 py-3">
                    <span className="sr-only">{t('rules.list.actions')}</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rules.map((rule, index) => (
                  <RuleTableRow
                    key={rule.ruleId ?? `rule-${index}`}
                    rule={rule}
                    selected={selectedRuleId === rule.ruleId}
                    pending={pendingRuleId === rule.ruleId}
                    onSelectRule={onSelectRule}
                    onEditRule={onEditRule}
                    onToggleEnabled={onToggleEnabled}
                    onDeleteRule={() => setRulePendingDelete(rule)}
                  />
                ))}
              </TableBody>
            </Table>
          </div>

          <div className="divide-y md:hidden">
            {rules.map((rule, index) => (
              <RuleMobileCard
                key={rule.ruleId ?? `rule-mobile-${index}`}
                rule={rule}
                selected={selectedRuleId === rule.ruleId}
                pending={pendingRuleId === rule.ruleId}
                onSelectRule={onSelectRule}
                onEditRule={onEditRule}
                onToggleEnabled={onToggleEnabled}
                onDeleteRule={() => setRulePendingDelete(rule)}
              />
            ))}
          </div>
        </>
      )}

      <Dialog
        open={Boolean(rulePendingDelete)}
        onOpenChange={(open) => !open && setRulePendingDelete(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('rules.delete.title')}</DialogTitle>
            <DialogDescription>{t('rules.delete.body')}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose render={<Button type="button" variant="outline" />}>
              {t('rules.delete.dismiss')}
            </DialogClose>
            <DialogClose
              render={<Button type="button" variant="destructive" />}
              onClick={() => {
                if (rulePendingDelete) onDeleteRule(rulePendingDelete);
                setRulePendingDelete(null);
              }}
            >
              {t('rules.delete.confirm')}
            </DialogClose>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  );
}

function RuleTableRow({
  rule,
  selected,
  pending,
  onSelectRule,
  onEditRule,
  onToggleEnabled,
  onDeleteRule,
}: RuleRowProps) {
  const t = useTranslations();
  const structureCopy = createRuleStructureCopy(t as unknown as (key: string) => string);
  const whenItems = summarizeMatcherAst(
    rule.matcherAst,
    rule.sourceText || t('rules.list.noWhen'),
    structureCopy,
  );
  const thenItems = summarizeActionIntents(
    rule.actionIntents,
    t('rules.list.noThen'),
    structureCopy,
  );

  return (
    <TableRow
      className={cn(
        'cursor-pointer',
        selected && 'bg-[#E7F0EF] hover:bg-[#E7F0EF]',
        !rule.enabled && 'text-foreground/80',
      )}
      onClick={() => onSelectRule(rule)}
    >
      <TableCell className="px-4 py-4" onClick={(event) => event.stopPropagation()}>
        <div className="flex items-center gap-2">
          {pending ? (
            <Loader2 className="text-muted-foreground size-4 animate-spin" aria-hidden="true" />
          ) : (
            <Switch
              checked={Boolean(rule.enabled)}
              aria-label={
                rule.enabled ? t('rules.preview.disableCta') : t('rules.preview.enableCta')
              }
              onCheckedChange={() => onToggleEnabled(rule)}
            />
          )}
        </div>
      </TableCell>
      <TableCell className="px-4 py-4 align-top whitespace-normal">
        <div className="min-w-0">
          <p className="truncate font-semibold">{rule.displayName ?? t('rules.composer.title')}</p>
          {rule.templateKey && (
            <div className="mt-1 flex flex-wrap gap-1">
              <Badge variant="outline" className="h-5 rounded-sm px-1.5 text-[10px]">
                {rule.customized ? t('rules.list.customizedBadge') : t('rules.list.templateBadge')}
              </Badge>
              {rule.templateVersion && (
                <Badge variant="outline" className="h-5 rounded-sm px-1.5 text-[10px]">
                  {`${rule.templateKey} · v${rule.templateVersion}`}
                </Badge>
              )}
            </div>
          )}
        </div>
      </TableCell>
      <TableCell className="px-4 py-4 align-top whitespace-normal">
        <SummaryChips items={whenItems} />
      </TableCell>
      <TableCell className="px-4 py-4 align-top whitespace-normal">
        <SummaryChips items={thenItems} action />
      </TableCell>
      <TableCell className="px-2 py-3 align-top" onClick={(event) => event.stopPropagation()}>
        <RuleRowActions
          rule={rule}
          pending={pending}
          onEditRule={onEditRule}
          onDeleteRule={onDeleteRule}
        />
      </TableCell>
    </TableRow>
  );
}

function RuleMobileCard(props: RuleRowProps) {
  const { rule, selected, pending, onSelectRule, onEditRule, onToggleEnabled, onDeleteRule } =
    props;
  const t = useTranslations();
  const structureCopy = createRuleStructureCopy(t as unknown as (key: string) => string);
  const whenItems = summarizeMatcherAst(
    rule.matcherAst,
    rule.sourceText || t('rules.list.noWhen'),
    structureCopy,
  );
  const thenItems = summarizeActionIntents(
    rule.actionIntents,
    t('rules.list.noThen'),
    structureCopy,
  );

  return (
    <article
      className={cn('cursor-pointer p-4 transition-colors', selected && 'bg-[#E7F0EF]')}
      onClick={() => onSelectRule(rule)}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-semibold">{rule.displayName ?? t('rules.composer.title')}</p>
        </div>
        <div className="flex items-center gap-2" onClick={(event) => event.stopPropagation()}>
          {pending ? (
            <Loader2 className="text-muted-foreground size-4 animate-spin" aria-hidden="true" />
          ) : (
            <Switch
              checked={Boolean(rule.enabled)}
              aria-label={
                rule.enabled ? t('rules.preview.disableCta') : t('rules.preview.enableCta')
              }
              onCheckedChange={() => onToggleEnabled(rule)}
            />
          )}
          <RuleRowActions
            rule={rule}
            pending={pending}
            onEditRule={onEditRule}
            onDeleteRule={onDeleteRule}
          />
        </div>
      </div>
      <div className="mt-3 space-y-2">
        <MobileSummaryLine label={t('rules.list.when')} items={whenItems} />
        <MobileSummaryLine label={t('rules.list.then')} items={thenItems} action />
      </div>
    </article>
  );
}

function SummaryChips({ items, action = false }: { items: string[]; action?: boolean }) {
  return (
    <div className="flex min-w-0 flex-wrap gap-1.5">
      {items.slice(0, 3).map((item) => (
        <span
          key={`${action ? 'action' : 'matcher'}-${item}`}
          className={cn(
            'max-w-full truncate rounded-sm px-2 py-1 text-xs font-medium',
            action
              ? 'bg-amber-500/10 text-amber-700 dark:text-amber-300'
              : 'bg-muted text-muted-foreground',
          )}
        >
          {item}
        </span>
      ))}
    </div>
  );
}

function MobileSummaryLine({
  label,
  items,
  action = false,
}: {
  label: string;
  items: string[];
  action?: boolean;
}) {
  return (
    <div className="grid grid-cols-[52px_1fr] gap-2 text-xs">
      <span className="text-muted-foreground font-semibold uppercase">{label}</span>
      <SummaryChips items={items} action={action} />
    </div>
  );
}

function RuleRowActions({
  rule,
  pending,
  onEditRule,
  onDeleteRule,
}: {
  rule: RuleResponse;
  pending: boolean;
  onEditRule: (rule: RuleResponse) => void;
  onDeleteRule: () => void;
}) {
  const t = useTranslations();

  return (
    <div className="flex items-center gap-0.5">
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="size-8 rounded-md"
        aria-label={t('rules.list.edit')}
        disabled={pending}
        onClick={() => onEditRule(rule)}
      >
        <Edit3 className="size-4" aria-hidden="true" />
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="text-destructive hover:text-destructive size-8 rounded-md"
        aria-label={t('rules.list.delete')}
        disabled={pending}
        onClick={onDeleteRule}
      >
        <Trash2 className="size-4" aria-hidden="true" />
      </Button>
    </div>
  );
}

type RuleRowProps = {
  rule: RuleResponse;
  selected: boolean;
  pending: boolean;
  onSelectRule: (rule: RuleResponse) => void;
  onEditRule: (rule: RuleResponse) => void;
  onToggleEnabled: (rule: RuleResponse) => void;
  onDeleteRule: () => void;
};
