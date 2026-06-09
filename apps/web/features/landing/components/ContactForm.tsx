'use client';

import { useState } from 'react';
import { CheckCircle, Send } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { submitFeedback } from '@/features/support/api/support-api';

export function ContactForm() {
  const t = useTranslations('landingContact');
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [status, setStatus] = useState<'idle' | 'pending' | 'success' | 'error'>('idle');

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (status === 'pending') return;
    setStatus('pending');
    const trimmedMessage = message.trim();
    try {
      await submitFeedback({
        type: 'GENERAL',
        subject: trimmedMessage.slice(0, 60),
        message: trimmedMessage,
        contactEmail: email.trim(),
      });
      setStatus('success');
    } catch {
      setStatus('error');
    }
  }

  if (status === 'success') {
    return (
      <div className="rounded-[24px] border border-(--line-strong) bg-(--bg-elevated) p-12 text-center">
        <CheckCircle className="mx-auto mb-4 size-10 text-(--primary)" strokeWidth={1.5} />
        <h3 className="mb-2 text-xl font-bold text-(--ink)">{t('successHeading')}</h3>
        <p className="text-[15px] text-(--text-muted)">{t('successBody')}</p>
        <button
          type="button"
          onClick={() => {
            setStatus('idle');
            setMessage('');
          }}
          className="mt-6 text-sm text-(--text-muted) underline underline-offset-4"
        >
          {t('successReset')}
        </button>
      </div>
    );
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="space-y-5 rounded-[24px] border border-(--line-strong) bg-(--bg-elevated) p-8 shadow-sm"
    >
      <div className="space-y-1">
        <label htmlFor="lp-email" className="block text-sm font-medium text-(--ink)">
          {t('emailLabel')}
        </label>
        <input
          id="lp-email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder={t('emailPlaceholder')}
          maxLength={320}
          required
          className="w-full rounded-xl border border-(--line-strong) bg-(--bg) px-4 py-2.5 text-sm text-(--ink) transition-colors outline-none placeholder:text-(--text-muted) focus:border-(--primary)"
        />
      </div>

      <div className="space-y-1">
        <label htmlFor="lp-message" className="block text-sm font-medium text-(--ink)">
          {t('messageLabel')}
        </label>
        <textarea
          id="lp-message"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          placeholder={t('messagePlaceholder')}
          rows={5}
          maxLength={5000}
          required
          className="w-full resize-none rounded-xl border border-(--line-strong) bg-(--bg) px-4 py-2.5 text-sm text-(--ink) transition-colors outline-none placeholder:text-(--text-muted) focus:border-(--primary)"
        />
      </div>

      {status === 'error' && <p className="text-sm text-red-500">{t('errorMessage')}</p>}

      <div className="flex items-center justify-between gap-4">
        <p className="text-xs text-(--text-muted)">{t('trust')}</p>
        <button
          type="submit"
          disabled={status === 'pending'}
          className="flex shrink-0 items-center gap-2 rounded-xl bg-(--primary) px-5 py-2.5 text-sm font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-60"
        >
          <Send className="size-4" />
          {status === 'pending' ? t('submitting') : t('submit')}
        </button>
      </div>
    </form>
  );
}
