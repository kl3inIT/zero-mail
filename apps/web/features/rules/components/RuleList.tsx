'use client';

import { useTranslations } from 'next-intl';
import type { ReactNode } from 'react';
import {
  ArrowDown,
  ArrowUp,
  Edit3,
  GripVertical,
  Loader2,
  Power,
  PowerOff,
  Trash2,
} from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
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
  DialogTrigger,
} from '@/components/ui/dialog';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import type { RuleResponse } from '@/features/rules/api/rules-api';
import { cn } from '@/lib/utils';

type Props = {
  rules: RuleResponse[];
  selectedRuleId: string | null;
  isLoading: boolean;
  pendingRuleId: string | null;
  canEnableRule: (rule: RuleResponse) => boolean;
  onSelectRule: (rule: RuleResponse) => void;
  onMoveRule: (rule: RuleResponse, direction: 'up' | 'down') => void;
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
  canEnableRule,
  onSelectRule,
  onMoveRule,
  onEditRule,
  onToggleEnabled,
  onDeleteRule,
  action,
}: Props) {
  const t = useTranslations();

  return (
    <section className="bg-background overflow-hidden rounded-xl border">
      <div className="bg-muted/20 border-b p-4">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-base font-semibold tracking-tight">{t('rules.list.title')}</h2>
          {action && <div className="flex-shrink-0">{action}</div>}
        </div>
      </div>

      {isLoading ? (
        <div className="space-y-4 p-4">
          <LoadingState count={3} />
        </div>
      ) : rules.length === 0 ? (
        <EmptyState
          heading={t('rules.list.empty.heading')}
          body={t('rules.list.empty.body')}
          className="min-h-32 px-4 py-8"
        />
      ) : (
        <TooltipProvider>
          <ol className="divide-border divide-y">
            {rules.map((rule, index) => {
              const ruleId = rule.ruleId ?? `rule-${index}`;
              const selected = selectedRuleId === rule.ruleId;
              const pending = pendingRuleId === rule.ruleId;
              const previewReady = rule.lastPreviewedEntityVersion === rule.entityVersion;
              const canMoveUp = index > 0;
              const canMoveDown = index < rules.length - 1;

              return (
                <li
                  key={ruleId}
                  className={cn(
                    'group relative flex cursor-pointer items-center gap-2 border-l-4 px-4 py-3 transition-all',
                    selected
                      ? 'border-l-[#0a3d3a] bg-[#E7F0EF] hover:bg-[#E7F0EF]/80'
                      : 'hover:bg-muted/50 border-l-transparent bg-transparent',
                  )}
                  onClick={() => onSelectRule(rule)}
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-4">
                      <div className="min-w-0">
                        <h3
                          className={cn(
                            'truncate text-sm leading-tight font-bold',
                            selected ? 'text-[#0a3d3a]' : 'text-foreground',
                          )}
                        >
                          {rule.displayName ?? t('rules.composer.title')}
                        </h3>
                        <p
                          className={cn(
                            'mt-0.5 truncate text-xs leading-tight italic',
                            selected ? 'text-[#0a3d3a]/70' : 'text-muted-foreground',
                          )}
                        >
                          {rule.sourceText}
                        </p>
                      </div>

                      <div
                        className="flex items-center gap-0.5 opacity-0 transition-all duration-200 group-hover:opacity-100"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <IconAction
                          label={t('rules.list.moveUp')}
                          disabled={!canMoveUp || pending}
                          onClick={() => onMoveRule(rule, 'up')}
                        >
                          <ArrowUp className="size-3.5" />
                        </IconAction>
                        <IconAction
                          label={t('rules.list.moveDown')}
                          disabled={!canMoveDown || pending}
                          onClick={() => onMoveRule(rule, 'down')}
                        >
                          <ArrowDown className="size-3.5" />
                        </IconAction>
                        <div className="bg-border/60 mx-1 h-3.5 w-px" />
                        <IconAction
                          label={t('rules.list.edit')}
                          disabled={pending}
                          onClick={() => onEditRule(rule)}
                        >
                          <Edit3 className="size-3.5" />
                        </IconAction>
                        <IconAction
                          label={
                            rule.enabled
                              ? t('rules.preview.disableCta')
                              : t('rules.preview.enableCta')
                          }
                          disabled={pending || (!rule.enabled && !canEnableRule(rule))}
                          onClick={() => onToggleEnabled(rule)}
                        >
                          {pending ? (
                            <Loader2 className="size-3.5 animate-spin" />
                          ) : rule.enabled ? (
                            <PowerOff className="size-3.5" />
                          ) : (
                            <Power className="size-3.5" />
                          )}
                        </IconAction>
                        <Dialog>
                          <Tooltip>
                            <TooltipTrigger
                              render={
                                <DialogTrigger
                                  render={
                                    <Button
                                      type="button"
                                      variant="ghost"
                                      size="icon"
                                      className="text-destructive hover:bg-destructive/10 size-7 rounded-full"
                                      disabled={pending}
                                    />
                                  }
                                />
                              }
                            >
                              <Trash2 className="size-3.5" />
                            </TooltipTrigger>
                            <TooltipContent>{t('rules.list.delete')}</TooltipContent>
                          </Tooltip>
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
                                onClick={() => onDeleteRule(rule)}
                              >
                                {t('rules.delete.confirm')}
                              </DialogClose>
                            </DialogFooter>
                          </DialogContent>
                        </Dialog>
                      </div>
                    </div>

                    <div className="mt-1.5 flex items-center gap-2">
                      <div
                        className={cn(
                          'size-1.5 rounded-full',
                          rule.enabled ? 'bg-[var(--green)]' : 'bg-muted-foreground/30',
                        )}
                      />
                      <span
                        className={cn(
                          'text-[10px] font-medium',
                          rule.enabled ? 'text-[var(--green)]' : 'text-muted-foreground',
                        )}
                      >
                        {rule.enabled ? t('rules.list.enabled') : t('rules.list.disabled')}
                      </span>

                      {rule.templateKey && (
                        <Badge
                          variant="secondary"
                          className="bg-muted h-4 rounded-sm px-1.5 py-0 text-[9px] font-medium"
                        >
                          {rule.customized
                            ? t('rules.list.customizedBadge')
                            : t('rules.list.templateBadge')}
                        </Badge>
                      )}
                      {previewReady && (
                        <Badge
                          variant="outline"
                          className="h-4 rounded-sm border-[var(--green)]/30 bg-[var(--green-soft)] px-1.5 py-0 text-[9px] font-medium text-[var(--green)]"
                        >
                          {t('rules.list.previewReady')}
                        </Badge>
                      )}
                    </div>
                  </div>
                </li>
              );
            })}
          </ol>
        </TooltipProvider>
      )}
    </section>
  );
}

function IconAction({
  label,
  disabled,
  children,
  onClick,
}: {
  label: string;
  disabled?: boolean;
  children: ReactNode;
  onClick: () => void;
}) {
  return (
    <Tooltip>
      <TooltipTrigger
        render={
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="size-7 rounded-full"
            aria-label={label}
            disabled={disabled}
            onClick={onClick}
          />
        }
      >
        {children}
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  );
}
