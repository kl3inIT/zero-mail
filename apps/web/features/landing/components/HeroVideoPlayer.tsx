'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';

import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';

/**
 * Click-to-play YouTube facade for the landing hero.
 *
 * Performance: we render ONLY a static thumbnail + a play button on load — no
 * YouTube iframe, so none of YouTube's ~hundreds of KB of JS/network hits the
 * initial render (keeps LCP/INP clean). The iframe is mounted lazily inside the
 * Dialog the first time the user clicks play. youtube-nocookie + a click gate
 * means no YouTube cookies until the user opts in.
 */
export function HeroVideoPlayer({ videoId }: { videoId: string }) {
  const t = useTranslations('landing');
  // maxresdefault is sharp but not guaranteed to exist; fall back to hqdefault.
  const [useMaxRes, setUseMaxRes] = useState(true);
  const thumbnailUrl = `https://i.ytimg.com/vi/${videoId}/${
    useMaxRes ? 'maxresdefault' : 'hqdefault'
  }.jpg`;

  return (
    <Dialog>
      <DialogTrigger
        render={
          <button
            type="button"
            aria-label={t('playDemo')}
            className="group absolute inset-0 h-full w-full cursor-pointer"
          />
        }
      >
        {/* eslint-disable-next-line @next/next/no-img-element -- remote YouTube thumbnail; next/image remotePatterns intentionally not configured for a single facade image */}
        <img
          src={thumbnailUrl}
          alt={t('videoThumbnailAlt')}
          onError={() => setUseMaxRes(false)}
          loading="lazy"
          className="h-full w-full object-cover"
        />
        <span className="absolute inset-0 flex items-center justify-center bg-(--ink)/10 transition group-hover:bg-(--ink)/20">
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-(--ink)/85 shadow-lg transition group-hover:scale-105">
            <svg
              viewBox="0 0 24 24"
              aria-hidden="true"
              className="ml-1 h-7 w-7 fill-(--bg-elevated)"
            >
              <path d="M8 5v14l11-7z" />
            </svg>
          </span>
        </span>
      </DialogTrigger>
      <DialogContent className="max-w-4xl">
        <DialogTitle className="sr-only">{t('videoTitle')}</DialogTitle>
        <div className="relative aspect-video w-full">
          <iframe
            src={`https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1&rel=0`}
            title={t('videoTitle')}
            className="h-full w-full rounded-lg"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            allowFullScreen
          />
        </div>
      </DialogContent>
    </Dialog>
  );
}
