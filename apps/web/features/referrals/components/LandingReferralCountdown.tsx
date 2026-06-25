'use client';

import { useEffect, useState } from 'react';

interface CountdownLabels {
  days: string;
  hours: string;
  minutes: string;
  seconds: string;
}

interface CountdownParts {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
}

function remaining(endsAtMs: number, nowMs: number): CountdownParts {
  const totalSeconds = Math.max(0, Math.floor((endsAtMs - nowMs) / 1000));
  return {
    days: Math.floor(totalSeconds / 86_400),
    hours: Math.floor((totalSeconds % 86_400) / 3_600),
    minutes: Math.floor((totalSeconds % 3_600) / 60),
    seconds: totalSeconds % 60,
  };
}

function pad(value: number): string {
  return value.toString().padStart(2, '0');
}

/**
 * Live ticking countdown for the landing referral band. The numeric cells carry
 * suppressHydrationWarning because the server-rendered value (computed at request time) and the
 * first client value (computed at hydration) differ by the network/render delay — expected, not a
 * real mismatch. After mount a 1s interval keeps it current.
 */
export function LandingReferralCountdown({
  endsAt,
  labels,
}: {
  endsAt: string;
  labels: CountdownLabels;
}) {
  const endsAtMs = new Date(endsAt).getTime();
  const [nowMs, setNowMs] = useState(() => Date.now());

  useEffect(() => {
    const intervalId = window.setInterval(() => setNowMs(Date.now()), 1000);
    return () => window.clearInterval(intervalId);
  }, []);

  if (!Number.isFinite(endsAtMs)) return null;
  const parts = remaining(endsAtMs, nowMs);

  const cells: { value: number; label: string }[] = [
    { value: parts.days, label: labels.days },
    { value: parts.hours, label: labels.hours },
    { value: parts.minutes, label: labels.minutes },
    { value: parts.seconds, label: labels.seconds },
  ];

  return (
    <div className="grid grid-cols-4 gap-2 sm:gap-3">
      {cells.map((cell) => (
        <div
          key={cell.label}
          className="flex flex-col items-center justify-center rounded-2xl border border-(--line) bg-(--accent-soft)/40 px-1 py-4 sm:py-5"
        >
          <span
            suppressHydrationWarning
            className="text-3xl font-extrabold tracking-tight text-(--accent) tabular-nums sm:text-4xl"
          >
            {pad(cell.value)}
          </span>
          <span className="mt-1 text-[11px] font-medium tracking-wide text-(--text-muted) uppercase">
            {cell.label}
          </span>
        </div>
      ))}
    </div>
  );
}
