'use client';

import { useState } from 'react';
import { Send } from 'lucide-react';

import type { FeedbackType } from '@/features/support/api/support-api';
import { submitFeedback } from '@/features/support/api/support-api';

const TYPE_OPTIONS: { value: FeedbackType; label: string }[] = [
  { value: 'BUG_REPORT', label: 'Bug Report' },
  { value: 'FEATURE_REQUEST', label: 'Feature Request' },
  { value: 'GENERAL', label: 'General Inquiry' },
];

export function ContactForm() {
  const [type, setType] = useState<FeedbackType>('GENERAL');
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [contactEmail, setContactEmail] = useState('');
  const [status, setStatus] = useState<'idle' | 'pending' | 'success' | 'error'>('idle');

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (status === 'pending') return;
    setStatus('pending');
    try {
      await submitFeedback({
        type,
        subject: subject.trim(),
        message: message.trim(),
        contactEmail: contactEmail.trim(),
      });
      setStatus('success');
      setSubject('');
      setMessage('');
    } catch {
      setStatus('error');
    }
  }

  if (status === 'success') {
    return (
      <div className="rounded-[24px] border border-(--line-strong) bg-(--bg-elevated) p-10 text-center">
        <div className="mb-4 text-4xl">✓</div>
        <h3 className="mb-2 text-xl font-bold text-(--ink)">Message received</h3>
        <p className="text-[15px] text-(--text-muted)">
          Thanks for reaching out. We read every message and will get back to you soon.
        </p>
        <button
          type="button"
          onClick={() => setStatus('idle')}
          className="mt-6 text-sm text-(--text-muted) underline underline-offset-4"
        >
          Send another message
        </button>
      </div>
    );
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="space-y-5 rounded-[24px] border border-(--line-strong) bg-(--bg-elevated) p-8 shadow-sm"
    >
      <div className="grid grid-cols-2 gap-3">
        {TYPE_OPTIONS.map((option) => (
          <button
            key={option.value}
            type="button"
            onClick={() => setType(option.value)}
            className={[
              'rounded-xl border px-4 py-2.5 text-sm font-medium transition-colors',
              type === option.value
                ? 'border-(--primary) bg-(--primary)/10 text-(--primary)'
                : 'border-(--line-strong) bg-(--bg) text-(--text-muted) hover:border-(--ink-2)',
              option.value === 'GENERAL' ? 'col-span-2' : '',
            ]
              .filter(Boolean)
              .join(' ')}
          >
            {option.label}
          </button>
        ))}
      </div>

      <div className="space-y-1">
        <label htmlFor="lp-subject" className="block text-sm font-medium text-(--ink)">
          Subject
        </label>
        <input
          id="lp-subject"
          type="text"
          value={subject}
          onChange={(e) => setSubject(e.target.value)}
          placeholder="Brief summary of your message"
          maxLength={200}
          required
          className="w-full rounded-xl border border-(--line-strong) bg-(--bg) px-4 py-2.5 text-sm text-(--ink) transition-colors outline-none placeholder:text-(--text-muted) focus:border-(--primary)"
        />
      </div>

      <div className="space-y-1">
        <label htmlFor="lp-message" className="block text-sm font-medium text-(--ink)">
          Message
        </label>
        <textarea
          id="lp-message"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          placeholder="Tell us what's on your mind…"
          rows={5}
          maxLength={5000}
          required
          className="w-full resize-none rounded-xl border border-(--line-strong) bg-(--bg) px-4 py-2.5 text-sm text-(--ink) transition-colors outline-none placeholder:text-(--text-muted) focus:border-(--primary)"
        />
      </div>

      <div className="space-y-1">
        <label htmlFor="lp-email" className="block text-sm font-medium text-(--ink)">
          Your email
        </label>
        <input
          id="lp-email"
          type="email"
          value={contactEmail}
          onChange={(e) => setContactEmail(e.target.value)}
          placeholder="you@example.com"
          maxLength={320}
          required
          className="w-full rounded-xl border border-(--line-strong) bg-(--bg) px-4 py-2.5 text-sm text-(--ink) transition-colors outline-none placeholder:text-(--text-muted) focus:border-(--primary)"
        />
      </div>

      {status === 'error' && (
        <p className="text-sm text-red-500">
          Something went wrong. Please try again or email us directly.
        </p>
      )}

      <button
        type="submit"
        disabled={status === 'pending'}
        className="flex w-full items-center justify-center gap-2 rounded-xl bg-(--primary) px-6 py-3 text-sm font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-60"
      >
        <Send className="size-4" />
        {status === 'pending' ? 'Sending…' : 'Send message'}
      </button>
    </form>
  );
}
