'use client';

import { useState } from 'react';
import { Bug, Lightbulb, MessageCircle } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import type { CurrentUser } from '@/features/account/api/account-api';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
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

type SupportClientProps = {
  initialUser?: CurrentUser;
};

export function SupportClient({ initialUser }: SupportClientProps) {
  const t = useTranslations('support');
  const { data: currentUser } = useCurrentUser(initialUser);
  const submitMutation = useSubmitFeedback();

  const [feedbackType, setFeedbackType] = useState<FeedbackType>('BUG_REPORT');
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [contactEmail, setContactEmail] = useState(currentUser?.email ?? '');
  const [succeeded, setSucceeded] = useState(false);

  const messagePlaceholder =
    feedbackType === 'BUG_REPORT'
      ? t('placeholders.bug')
      : feedbackType === 'FEATURE_REQUEST'
        ? t('placeholders.feature')
        : t('placeholders.general');

  const isValid = message.trim().length > 0 && contactEmail.trim().length > 0;

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

  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto p-3 sm:p-6">
        <div className="mx-auto w-full max-w-2xl space-y-8">
          <header className="space-y-1">
            <h1 className="text-foreground text-2xl font-semibold tracking-normal">
              {t('pageTitle')}
            </h1>
            <p className="text-muted-foreground text-sm">{t('pageDescription')}</p>
          </header>

          {succeeded ? (
            <div className="border-border bg-muted/30 flex flex-col items-center gap-3 rounded-xl border p-10 text-center">
              <div className="bg-primary/10 flex size-12 items-center justify-center rounded-full">
                <MessageCircle className="text-primary size-6" />
              </div>
              <p className="text-foreground font-semibold">{t('successTitle')}</p>
              <p className="text-muted-foreground text-sm">{t('successBody')}</p>
              <Button
                variant="outline"
                size="sm"
                className="mt-1"
                onClick={() => {
                  setSucceeded(false);
                  submitMutation.reset();
                }}
              >
                {t('successReset')}
              </Button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-6">
              {/* Type selector */}
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
                          'flex flex-1 items-center justify-center gap-2 rounded-lg border px-4 py-2.5 text-sm font-medium transition-colors',
                          isActive
                            ? 'border-primary bg-primary/10 text-primary'
                            : 'border-border text-muted-foreground hover:bg-accent hover:text-accent-foreground',
                        )}
                      >
                        <Icon className="size-4 shrink-0" aria-hidden="true" />
                        {t(option.labelKey)}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Subject — optional */}
              <div className="space-y-1.5">
                <Label htmlFor="support-subject" className="text-sm font-medium">
                  {t('subjectLabel')}{' '}
                  <span className="text-muted-foreground font-normal">{t('subjectOptional')}</span>
                </Label>
                <Input
                  id="support-subject"
                  value={subject}
                  onChange={(e) => setSubject(e.target.value)}
                  maxLength={200}
                />
              </div>

              {/* Message */}
              <div className="space-y-1.5">
                <Label htmlFor="support-message" className="text-sm font-medium">
                  {t('messageLabel')}
                </Label>
                <Textarea
                  id="support-message"
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder={messagePlaceholder}
                  rows={6}
                  maxLength={5000}
                  required
                  className="resize-none"
                />
              </div>

              {/* Email */}
              <div className="space-y-1.5">
                <Label htmlFor="support-email" className="text-sm font-medium">
                  {t('emailLabel')}
                </Label>
                <Input
                  id="support-email"
                  type="email"
                  value={contactEmail}
                  onChange={(e) => setContactEmail(e.target.value)}
                  placeholder="you@example.com"
                  maxLength={320}
                  required
                />
                <p className="text-muted-foreground text-xs">{t('emailHint')}</p>
              </div>

              {/* Submit row */}
              <div className="flex items-center justify-between gap-4 pt-1">
                <p className="text-muted-foreground text-xs">{t('trust')}</p>
                <Button type="submit" disabled={!isValid || submitMutation.isPending}>
                  {submitMutation.isPending ? t('submitting') : t('submit')}
                </Button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
