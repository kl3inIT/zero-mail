import { PauseIcon, PlayIcon } from 'lucide-react';
import { useEffect, useState } from 'react';

import { Button } from '@/components/ui/button';

export type AutoRefreshIndicatorProps = {
  lastUpdatedAt: Date | null;
  intervalMs: number;
  paused: boolean;
  onPauseToggle: () => void;
};

/**
 * 6px pulsing accent dot + monospace `Updated Ns ago` + pause toggle. Shared between `/queue`
 * (Phase 8E) and `/spend` (Phase 8F).
 *
 * Accessibility: the "Updated Ns ago" line is `aria-live="polite"`. When `prefers-reduced-motion:
 * reduce` is set, the pulse animation is disabled but the dot remains visible.
 */
export function AutoRefreshIndicator({
  lastUpdatedAt,
  intervalMs,
  paused,
  onPauseToggle,
}: AutoRefreshIndicatorProps) {
  const elapsedSeconds = useElapsedSeconds(lastUpdatedAt);
  const reducedMotion = usePrefersReducedMotion();
  const intervalSeconds = Math.round(intervalMs / 1000);

  return (
    <div className="flex items-center gap-3 text-xs">
      <span
        aria-hidden
        data-paused={paused}
        data-reduced-motion={reducedMotion}
        className={
          paused
            ? 'inline-block size-[6px] rounded-full bg-muted-foreground/40'
            : reducedMotion
              ? 'inline-block size-[6px] rounded-full bg-primary'
              : 'animate-pulse inline-block size-[6px] rounded-full bg-primary'
        }
      />
      <span
        aria-live="polite"
        className="text-muted-foreground tabular-nums"
      >
        {lastUpdatedAt
          ? paused
            ? 'Đã tạm dừng'
            : `Cập nhật cách đây ${formatElapsed(elapsedSeconds)}`
          : 'Đang chờ…'}
      </span>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={onPauseToggle}
        aria-label={paused ? 'Tiếp tục tự làm mới' : 'Tạm dừng tự làm mới'}
        title={
          paused
            ? `Tiếp tục tự làm mới (mỗi ${intervalSeconds}s)`
            : `Tạm dừng tự làm mới (đang chạy mỗi ${intervalSeconds}s)`
        }
        className="h-7 gap-1 px-2"
      >
        {paused ? <PlayIcon className="size-3.5" /> : <PauseIcon className="size-3.5" />}
        <span className="text-xs">{paused ? 'Tiếp tục' : 'Tạm dừng'}</span>
      </Button>
    </div>
  );
}

function formatElapsed(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remaining = seconds % 60;
  return `${minutes}m ${remaining}s`;
}

function useElapsedSeconds(timestamp: Date | null): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const tickId = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(tickId);
  }, []);
  if (!timestamp) return 0;
  return Math.max(0, Math.floor((now - timestamp.getTime()) / 1000));
}

function usePrefersReducedMotion(): boolean {
  // Initialize from the media query directly so the effect body never calls
  // setReduced synchronously (react-hooks set-state-in-effect rule). The
  // effect only wires the subscription; updates flow through the change event.
  const [reduced, setReduced] = useState(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  });
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    const handler = (event: MediaQueryListEvent) => setReduced(event.matches);
    mediaQuery.addEventListener('change', handler);
    return () => mediaQuery.removeEventListener('change', handler);
  }, []);
  return reduced;
}
