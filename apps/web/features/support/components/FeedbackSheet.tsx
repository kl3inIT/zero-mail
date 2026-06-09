'use client';

import { useState } from 'react';
import { Bug, CheckCircle2, Lightbulb, MessageCircle } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { Textarea } from '@/components/ui/textarea';
import type { FeedbackType } from '@/features/support/api/support-api';
import { useSubmitFeedback } from '@/features/support/hooks/use-submit-feedback';
import { cn } from '@/lib/utils';

type TypeOption = {
  value: FeedbackType;
  labelKey: 'typeBug' | 'typeFeature' | 'typeGeneral';
  icon: typeof Bug;
};

const TYPE_OPTIONS: TypeOption[] = [
  { value: 'BUG_REPORT', labelKey: 'typeBug', icon: Bug },
  { value: 'FEATURE_REQUEST', labelKey: 'typeFeature', icon: Lightbulb },
  { value: 'GENERAL', labelKey: 'typeGeneral', icon: MessageCircle },
];

type FeedbackSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialEmail?: string;
};

export function FeedbackSheet({ open, onOpenChange, initialEmail = '' }: FeedbackSheetProps) {
  const t = useTranslations('support');
  const submitMutation = useSubmitFeedback();

  const [feedbackType, setFeedbackType] = useState<FeedbackType>('BUG_REPORT');
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [contactEmail, setContactEmail] = useState(initialEmail);
  const [succeeded, setSucceeded] = useState(false);

  function handleOpenChange(nextOpen: boolean) {
    onOpenChange(nextOpen);
    if (!nextOpen) {
      setTimeout(() => {
        setFeedbackType('BUG_REPORT');
        setSubject('');
        setMessage('');
        setContactEmail(initialEmail);
        setSucceeded(false);
        submitMutation.reset();
      }, 250);
    }
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (submitMutation.isPending) return;
    const trimmedMessage = message.trim();
    const derivedSubject = subject.trim() || trimmedMessage.slice(0, 60);
    submitMutation.mutate(
      {
        type: feedbackType,
        subject: derivedSubject,
        message: trimmedMessage,
        contactEmail: contactEmail.trim(),
      },
      {
        onSuccess: () => {
          setSucceeded(true);
          setSubject('');
          setMessage('');
        },
      },
    );
  }

  const messagePlaceholder =
    feedbackType === 'BUG_REPORT'
      ? t('placeholders.bug')
      : feedbackType === 'FEATURE_REQUEST'
        ? t('placeholders.feature')
        : t('placeholders.general');
  const isValid = message.trim().length > 0 && contactEmail.trim().length > 0;

  return (
    <Sheet open={open} onOpenChange={handleOpenChange}>
      <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-[420px]">
        <SheetHeader className="border-b p-5 pb-4">
          <SheetTitle>{t('sheetTitle')}</SheetTitle>
          <SheetDescription>{t('sheetDescription')}</SheetDescription>
        </SheetHeader>

        {succeeded ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
            <CheckCircle2 className="text-primary size-10" strokeWidth={1.5} />
            <p className="text-foreground font-semibold">{t('successTitle')}</p>
            <p className="text-muted-foreground text-sm">{t('successBody')}</p>
            <Button
              variant="outline"
              size="sm"
              className="mt-2"
              onClick={() => setSucceeded(false)}
            >
              {t('successReset')}
            </Button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-1 flex-col overflow-hidden">
            <div className="flex flex-1 flex-col gap-5 overflow-y-auto p-5">
              {/* Type pills */}
              <div className="space-y-2">
                <span className="text-foreground text-sm font-medium">{t('typeLabel')}</span>
                <div className="flex gap-2">
                  {TYPE_OPTIONS.map((option) => {
                    const Icon = option.icon;
                    const isActive = feedbackType === option.value;
                    return (
                      <button
                        key={option.value}
                        type="button"
                        onClick={() => setFeedbackType(option.value)}
                        className={cn(
                          'flex flex-1 items-center justify-center gap-1.5 rounded-lg border px-3 py-2 text-xs font-medium transition-colors',
                          isActive
                            ? 'border-primary bg-primary/10 text-primary'
                            : 'border-border text-muted-foreground hover:bg-accent hover:text-accent-foreground',
                        )}
                      >
                        <Icon className="size-3.5 shrink-0" aria-hidden="true" />
                        {t(option.labelKey)}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Subject — optional */}
              <div className="space-y-1.5">
                <Label htmlFor="fb-subject" className="text-sm font-medium">
                  {t('subjectLabel')}{' '}
                  <span className="text-muted-foreground font-normal">{t('subjectOptional')}</span>
                </Label>
                <Input
                  id="fb-subject"
                  value={subject}
                  onChange={(e) => setSubject(e.target.value)}
                  maxLength={200}
                />
              </div>

              {/* Message */}
              <div className="space-y-1.5">
                <Label htmlFor="fb-message" className="text-sm font-medium">
                  {t('messageLabel')}
                </Label>
                <Textarea
                  id="fb-message"
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder={messagePlaceholder}
                  rows={5}
                  maxLength={5000}
                  required
                  className="resize-none"
                />
              </div>

              {/* Email */}
              <div className="space-y-1.5">
                <Label htmlFor="fb-email" className="text-sm font-medium">
                  {t('emailLabel')}
                </Label>
                <Input
                  id="fb-email"
                  type="email"
                  value={contactEmail}
                  onChange={(e) => setContactEmail(e.target.value)}
                  placeholder="you@example.com"
                  maxLength={320}
                  required
                />
                <p className="text-muted-foreground text-xs">{t('emailHint')}</p>
              </div>
            </div>

            {/* Sticky footer */}
            <div className="border-t p-5 pt-4">
              <p className="text-muted-foreground mb-3 text-xs">{t('trust')}</p>
              <Button
                type="submit"
                disabled={!isValid || submitMutation.isPending}
                className="w-full"
              >
                {submitMutation.isPending ? t('submitting') : t('submit')}
              </Button>
            </div>
          </form>
        )}
      </SheetContent>
    </Sheet>
  );
}
