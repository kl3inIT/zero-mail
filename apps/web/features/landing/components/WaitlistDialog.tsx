'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { CheckCircle2Icon, Loader2Icon, LockIcon, SparklesIcon, XIcon } from 'lucide-react';

type WaitlistDialogCopy = {
  title: string;
  description: string;
  emailPlaceholder: string;
  button: string;
};

function clearWaitlistHash() {
  if (window.location.hash === '#waitlist') {
    window.history.replaceState(
      window.history.state,
      document.title,
      window.location.pathname + window.location.search,
    );
  }
}

export default function WaitlistDialog({ copy }: { copy: WaitlistDialogCopy }) {
  const [isOpen, setIsOpen] = useState(false);
  const [email, setEmail] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const closeTimeoutRef = useRef<number | null>(null);
  const submitTimeoutRef = useRef<number | null>(null);

  const clearDialogTimeouts = useCallback(() => {
    if (closeTimeoutRef.current !== null) {
      window.clearTimeout(closeTimeoutRef.current);
      closeTimeoutRef.current = null;
    }
    if (submitTimeoutRef.current !== null) {
      window.clearTimeout(submitTimeoutRef.current);
      submitTimeoutRef.current = null;
    }
  }, []);

  useEffect(() => {
    const syncFromHash = () => {
      setIsOpen(window.location.hash === '#waitlist');
    };
    const openFromWaitlistLink = (event: MouseEvent) => {
      const target = event.target;
      if (!(target instanceof Element)) return;

      const trigger = target.closest<HTMLAnchorElement>('a[href="#waitlist"]');
      if (!trigger) return;

      event.preventDefault();
      if (window.location.hash !== '#waitlist') {
        window.location.hash = 'waitlist';
      }
      setIsOpen(true);
    };

    syncFromHash();
    document.addEventListener('click', openFromWaitlistLink);
    window.addEventListener('hashchange', syncFromHash);
    window.addEventListener('popstate', syncFromHash);
    return () => {
      document.removeEventListener('click', openFromWaitlistLink);
      window.removeEventListener('hashchange', syncFromHash);
      window.removeEventListener('popstate', syncFromHash);
      clearDialogTimeouts();
    };
  }, [clearDialogTimeouts]);

  useEffect(() => {
    document.body.style.overflow = isOpen ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  function closeDialog() {
    setIsOpen(false);
    clearWaitlistHash();
    clearDialogTimeouts();
    closeTimeoutRef.current = window.setTimeout(() => {
      setEmail('');
      setIsSuccess(false);
      setIsSubmitting(false);
      closeTimeoutRef.current = null;
    }, 150);
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!email || isSubmitting) return;

    setIsSubmitting(true);
    if (submitTimeoutRef.current !== null) {
      window.clearTimeout(submitTimeoutRef.current);
    }
    submitTimeoutRef.current = window.setTimeout(() => {
      setIsSubmitting(false);
      setIsSuccess(true);
      submitTimeoutRef.current = null;
    }, 350);
  }

  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="waitlist-title"
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/45 px-4 py-8 backdrop-blur-sm"
    >
      <button type="button" aria-label="Đóng" className="absolute inset-0" onClick={closeDialog} />
      <div className="relative z-10 w-full max-w-[420px] overflow-hidden rounded-[24px] border border-(--line-strong) bg-(--bg-elevated) p-6 shadow-2xl">
        <button
          type="button"
          aria-label="Đóng"
          onClick={closeDialog}
          className="absolute top-4 right-4 z-20 grid size-8 place-items-center rounded-full text-(--text-muted) hover:bg-(--bg-subtle) hover:text-(--ink)"
        >
          <XIcon className="size-4" aria-hidden="true" />
        </button>

        <div className="pointer-events-none absolute -top-12 -left-12 size-28 rounded-full bg-(--accent-soft) opacity-40 blur-3xl" />

        <div className="relative z-10 flex flex-col items-center text-center">
          <div className="mb-4 flex size-12 items-center justify-center rounded-2xl border border-(--accent-hover) bg-(--accent-soft) text-(--accent) shadow-sm">
            <SparklesIcon className="size-5" aria-hidden="true" />
          </div>
          <h2
            id="waitlist-title"
            className="text-2xl leading-tight font-extrabold tracking-tight text-(--ink)"
          >
            {isSuccess ? 'Đăng ký thành công!' : copy.title}
          </h2>
          <p className="mt-2 max-w-sm text-[14px] leading-relaxed text-(--text-muted)">
            {isSuccess ? `Cảm ơn bạn! Chúng tôi đã ghi nhận ${email}.` : copy.description}
          </p>
        </div>

        {isSuccess ? (
          <div className="relative z-10 mt-6 space-y-4 text-center">
            <div className="mx-auto flex size-12 items-center justify-center rounded-full border border-(--green) bg-(--green-soft) text-(--green)">
              <CheckCircle2Icon className="size-6" aria-hidden="true" />
            </div>
            <button
              type="button"
              onClick={closeDialog}
              className="flex h-11 w-full items-center justify-center rounded-xl border border-(--line-strong) bg-(--bg) text-sm font-semibold text-(--ink) hover:bg-(--bg-subtle)"
            >
              Đóng lại
            </button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="relative z-10 mt-6 space-y-4">
            <input
              type="email"
              name="email"
              placeholder={copy.emailPlaceholder}
              value={email}
              onChange={(event) => setEmail(event.currentTarget.value)}
              required
              className="h-12 w-full rounded-xl border border-(--line-strong) bg-(--bg) px-4 text-base transition-all outline-none placeholder:text-(--text-faint) focus:border-(--accent) focus:ring-2 focus:ring-(--accent-soft)"
            />
            <button
              type="submit"
              disabled={!email || isSubmitting}
              className="flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-(--ink) text-[15px] font-bold text-(--bg) shadow-md transition-all hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isSubmitting && <Loader2Icon className="size-4 animate-spin" aria-hidden="true" />}
              {isSubmitting ? 'Đang đăng ký...' : copy.button}
            </button>
            <div className="mt-4 flex items-center justify-center gap-2 text-[11px] text-(--text-faint)">
              <LockIcon className="size-3 text-(--green)" aria-hidden="true" />
              <span>Chúng tôi không lưu email của bạn vĩnh viễn và không spam.</span>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
