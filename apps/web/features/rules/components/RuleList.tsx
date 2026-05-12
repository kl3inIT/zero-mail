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
}: Props) {
  const t = useTranslations();

  return (
    <section className="bg-card rounded-xl border p-3">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold">{t('rules.list.title')}</h2>
          <p className="text-muted-foreground text-sm">{t('rules.page.safetyNote')}</p>
        </div>
      </div>

      {isLoading ? (
        <LoadingState count={2} />
      ) : rules.length === 0 ? (
        <EmptyState
          heading={t('rules.list.empty.heading')}
          body={t('rules.list.empty.body')}
          className="min-h-32 px-4 py-8"
        />
      ) : (
        <TooltipProvider>
          <ol className="space-y-2">
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
                    'grid min-h-20 grid-cols-[auto_1fr] gap-2 rounded-lg border p-2 transition-colors',
                    selected ? 'border-primary bg-accent-soft/60' : 'bg-background',
                  )}
                >
                  <button
                    type="button"
                    className="text-muted-foreground hover:bg-muted flex min-h-11 min-w-11 items-center justify-center rounded-md"
                    aria-label={rule.displayName ?? t('rules.list.title')}
                    onClick={() => onSelectRule(rule)}
                  >
                    <GripVertical className="size-4" aria-hidden="true" />
                  </button>

                  <div className="min-w-0 space-y-2">
                    <button
                      type="button"
                      className="block w-full min-w-0 text-left"
                      onClick={() => onSelectRule(rule)}
                    >
                      <span className="block truncate text-sm font-medium">
                        {rule.displayName ?? t('rules.composer.title')}
                      </span>
                      <span className="text-muted-foreground block truncate text-xs">
                        {rule.sourceText}
                      </span>
                    </button>

                    <div className="flex flex-wrap gap-1.5">
                      <Badge
                        variant={rule.enabled ? 'default' : 'outline'}
                        className={rule.enabled ? 'bg-green text-white' : ''}
                      >
                        {rule.enabled ? t('rules.list.enabled') : t('rules.list.disabled')}
                      </Badge>
                      {rule.templateKey && (
                        <Badge variant="outline">
                          {rule.customized
                            ? t('rules.list.customizedBadge')
                            : t('rules.list.templateBadge')}
                        </Badge>
                      )}
                      <Badge
                        variant="outline"
                        className={previewReady ? 'border-green/30 text-green' : ''}
                      >
                        {previewReady
                          ? t('rules.list.previewReady')
                          : t('rules.list.previewRequired')}
                      </Badge>
                    </div>

                    <div className="flex flex-wrap gap-1">
                      <IconAction
                        label={t('rules.list.moveUp')}
                        disabled={!canMoveUp || pending}
                        onClick={() => onMoveRule(rule, 'up')}
                      >
                        <ArrowUp className="size-4" aria-hidden="true" />
                      </IconAction>
                      <IconAction
                        label={t('rules.list.moveDown')}
                        disabled={!canMoveDown || pending}
                        onClick={() => onMoveRule(rule, 'down')}
                      >
                        <ArrowDown className="size-4" aria-hidden="true" />
                      </IconAction>
                      <IconAction
                        label={t('rules.list.edit')}
                        disabled={pending}
                        onClick={() => onEditRule(rule)}
                      >
                        <Edit3 className="size-4" aria-hidden="true" />
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
                          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                        ) : rule.enabled ? (
                          <PowerOff className="size-4" aria-hidden="true" />
                        ) : (
                          <Power className="size-4" aria-hidden="true" />
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
                                    variant="destructive"
                                    size="icon-lg"
                                    aria-label={t('rules.list.delete')}
                                    disabled={pending}
                                  />
                                }
                              />
                            }
                          >
                            <Trash2 className="size-4" aria-hidden="true" />
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
            variant="outline"
            size="icon-lg"
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
