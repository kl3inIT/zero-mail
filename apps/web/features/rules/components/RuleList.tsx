'use client';

import { useState, type ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { ArrowDown, ArrowUp, Edit3, Loader2, MoreHorizontal, Trash2 } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Switch } from '@/components/ui/switch';
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import type { RuleResponse } from '@/features/rules/api/rules-api';
import { summarizeActionIntents, summarizeMatcherAst } from '@/features/rules/lib/rule-structure';
import { createRuleStructureCopy } from '@/features/rules/lib/rule-structure-copy';
import { cn } from '@/lib/utils';

type Props = {
  rules: RuleResponse[];
  selectedRuleId: string | null;
  selectedForTestIds: ReadonlySet<string>;
  isLoading: boolean;
  pendingRuleId: string | null;
  canEnableRule: (rule: RuleResponse) => boolean;
  onSelectRule: (rule: RuleResponse) => void;
  onMoveRule: (rule: RuleResponse, direction: 'up' | 'down') => void;
  onEditRule: (rule: RuleResponse) => void;
  onToggleEnabled: (rule: RuleResponse) => void;
  onDeleteRule: (rule: RuleResponse) => void;
  onToggleRuleForTest: (rule: RuleResponse) => void;
  onToggleAllRulesForTest: (selectAll: boolean) => void;
  action?: ReactNode;
};

export function RuleList({
  rules,
  selectedRuleId,
  selectedForTestIds,
  isLoading,
  pendingRuleId,
  canEnableRule,
  onSelectRule,
  onMoveRule,
  onEditRule,
  onToggleEnabled,
  onDeleteRule,
  onToggleRuleForTest,
  onToggleAllRulesForTest,
  action,
}: Props) {
  const t = useTranslations();
  const [rulePendingDelete, setRulePendingDelete] = useState<RuleResponse | null>(null);
  const eligibleRuleIds = rules
    .map((rule) => rule.ruleId)
    .filter((id): id is string => Boolean(id));
  const selectedCount = eligibleRuleIds.filter((id) => selectedForTestIds.has(id)).length;
  const allSelected = eligibleRuleIds.length > 0 && selectedCount === eligibleRuleIds.length;
  const headerIndeterminate = selectedCount > 0 && !allSelected;

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
            <table className="w-full table-fixed border-collapse text-sm">
              <thead>
                <tr className="bg-muted/20 text-muted-foreground border-b text-left text-xs font-semibold">
                  <th className="w-[44px] px-3 py-3">
                    <Checkbox
                      aria-label={t('rules.list.column.selectAll')}
                      checked={allSelected}
                      indeterminate={headerIndeterminate}
                      onCheckedChange={(nextChecked) =>
                        onToggleAllRulesForTest(nextChecked === true)
                      }
                      data-testid="rule-list-select-all"
                    />
                  </th>
                  <th className="w-[92px] px-4 py-3">{t('rules.list.column.enabled')}</th>
                  <th className="w-[240px] px-4 py-3">{t('rules.list.column.name')}</th>
                  <th className="px-4 py-3">{t('rules.list.when')}</th>
                  <th className="px-4 py-3">{t('rules.list.then')}</th>
                  <th className="w-[56px] px-2 py-3">
                    <span className="sr-only">{t('rules.list.actions')}</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {rules.map((rule, index) => (
                  <RuleTableRow
                    key={rule.ruleId ?? `rule-${index}`}
                    rule={rule}
                    index={index}
                    total={rules.length}
                    selected={selectedRuleId === rule.ruleId}
                    selectedForTest={rule.ruleId ? selectedForTestIds.has(rule.ruleId) : false}
                    pending={pendingRuleId === rule.ruleId}
                    canEnable={canEnableRule(rule)}
                    onSelectRule={onSelectRule}
                    onMoveRule={onMoveRule}
                    onEditRule={onEditRule}
                    onToggleEnabled={onToggleEnabled}
                    onToggleSelectForTest={onToggleRuleForTest}
                    onDeleteRule={() => setRulePendingDelete(rule)}
                  />
                ))}
              </tbody>
            </table>
          </div>

          <div className="divide-y md:hidden">
            {rules.map((rule, index) => (
              <RuleMobileCard
                key={rule.ruleId ?? `rule-mobile-${index}`}
                rule={rule}
                index={index}
                total={rules.length}
                selected={selectedRuleId === rule.ruleId}
                selectedForTest={rule.ruleId ? selectedForTestIds.has(rule.ruleId) : false}
                pending={pendingRuleId === rule.ruleId}
                canEnable={canEnableRule(rule)}
                onSelectRule={onSelectRule}
                onMoveRule={onMoveRule}
                onEditRule={onEditRule}
                onToggleEnabled={onToggleEnabled}
                onToggleSelectForTest={onToggleRuleForTest}
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
  index,
  total,
  selected,
  selectedForTest,
  pending,
  canEnable,
  onSelectRule,
  onMoveRule,
  onEditRule,
  onToggleEnabled,
  onToggleSelectForTest,
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
    <tr
      className={cn(
        'hover:bg-muted/30 cursor-pointer transition-colors',
        selected && 'bg-[#E7F0EF] hover:bg-[#E7F0EF]',
        !rule.enabled && 'text-foreground/80',
      )}
      onClick={() => onSelectRule(rule)}
    >
      <td className="px-3 py-4" onClick={(event) => event.stopPropagation()}>
        <Checkbox
          aria-label={t('rules.list.column.selectRow', {
            name: rule.displayName ?? t('rules.composer.title'),
          })}
          checked={selectedForTest}
          disabled={!rule.ruleId}
          onCheckedChange={() => onToggleSelectForTest(rule)}
          data-testid={rule.ruleId ? `rule-list-select-${rule.ruleId}` : undefined}
        />
      </td>
      <td className="px-4 py-4" onClick={(event) => event.stopPropagation()}>
        <div className="flex items-center gap-2">
          {pending ? (
            <Loader2 className="text-muted-foreground size-4 animate-spin" aria-hidden="true" />
          ) : (
            <Switch
              checked={Boolean(rule.enabled)}
              disabled={!rule.enabled && !canEnable}
              aria-label={
                rule.enabled ? t('rules.preview.disableCta') : t('rules.preview.enableCta')
              }
              onCheckedChange={() => onToggleEnabled(rule)}
            />
          )}
        </div>
      </td>
      <td className="px-4 py-4 align-top">
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
      </td>
      <td className="px-4 py-4 align-top">
        <SummaryChips items={whenItems} />
      </td>
      <td className="px-4 py-4 align-top">
        <SummaryChips items={thenItems} action />
      </td>
      <td className="px-2 py-3 align-top" onClick={(event) => event.stopPropagation()}>
        <RuleMenu
          index={index}
          total={total}
          pending={pending}
          rule={rule}
          onMoveRule={onMoveRule}
          onEditRule={onEditRule}
          onDeleteRule={onDeleteRule}
        />
      </td>
    </tr>
  );
}

function RuleMobileCard(props: RuleRowProps) {
  const {
    rule,
    index,
    total,
    selected,
    selectedForTest,
    pending,
    canEnable,
    onSelectRule,
    onMoveRule,
    onEditRule,
    onToggleEnabled,
    onToggleSelectForTest,
    onDeleteRule,
  } = props;
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
        <div className="flex min-w-0 items-center gap-3">
          <span onClick={(event) => event.stopPropagation()}>
            <Checkbox
              aria-label={t('rules.list.column.selectRow', {
                name: rule.displayName ?? t('rules.composer.title'),
              })}
              checked={selectedForTest}
              disabled={!rule.ruleId}
              onCheckedChange={() => onToggleSelectForTest(rule)}
            />
          </span>
          <p className="truncate font-semibold">{rule.displayName ?? t('rules.composer.title')}</p>
        </div>
        <div className="flex items-center gap-2" onClick={(event) => event.stopPropagation()}>
          {pending ? (
            <Loader2 className="text-muted-foreground size-4 animate-spin" aria-hidden="true" />
          ) : (
            <Switch
              checked={Boolean(rule.enabled)}
              disabled={!rule.enabled && !canEnable}
              aria-label={
                rule.enabled ? t('rules.preview.disableCta') : t('rules.preview.enableCta')
              }
              onCheckedChange={() => onToggleEnabled(rule)}
            />
          )}
          <RuleMenu
            index={index}
            total={total}
            pending={pending}
            rule={rule}
            onMoveRule={onMoveRule}
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

function RuleMenu({
  rule,
  index,
  total,
  pending,
  onMoveRule,
  onEditRule,
  onDeleteRule,
}: {
  rule: RuleResponse;
  index: number;
  total: number;
  pending: boolean;
  onMoveRule: (rule: RuleResponse, direction: 'up' | 'down') => void;
  onEditRule: (rule: RuleResponse) => void;
  onDeleteRule: () => void;
}) {
  const t = useTranslations();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="size-8 rounded-md"
            aria-label={t('rules.list.actions')}
            disabled={pending}
          />
        }
      >
        <MoreHorizontal className="size-4" aria-hidden="true" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-44">
        <DropdownMenuItem disabled={index === 0 || pending} onClick={() => onMoveRule(rule, 'up')}>
          <ArrowUp className="size-4" aria-hidden="true" />
          {t('rules.list.moveUp')}
        </DropdownMenuItem>
        <DropdownMenuItem
          disabled={index >= total - 1 || pending}
          onClick={() => onMoveRule(rule, 'down')}
        >
          <ArrowDown className="size-4" aria-hidden="true" />
          {t('rules.list.moveDown')}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem disabled={pending} onClick={() => onEditRule(rule)}>
          <Edit3 className="size-4" aria-hidden="true" />
          {t('rules.list.edit')}
        </DropdownMenuItem>
        <DropdownMenuItem variant="destructive" disabled={pending} onClick={onDeleteRule}>
          <Trash2 className="size-4" aria-hidden="true" />
          {t('rules.list.delete')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

type RuleRowProps = {
  rule: RuleResponse;
  index: number;
  total: number;
  selected: boolean;
  selectedForTest: boolean;
  pending: boolean;
  canEnable: boolean;
  onSelectRule: (rule: RuleResponse) => void;
  onMoveRule: (rule: RuleResponse, direction: 'up' | 'down') => void;
  onEditRule: (rule: RuleResponse) => void;
  onToggleEnabled: (rule: RuleResponse) => void;
  onToggleSelectForTest: (rule: RuleResponse) => void;
  onDeleteRule: () => void;
};
