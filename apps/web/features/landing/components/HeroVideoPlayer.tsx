'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';

/**
 * Click-to-play YouTube facade for the landing hero.
 *
 * Performance: we render ONLY a static thumbnail + a play button on load — no
 * YouTube iframe, so none of YouTube's ~hundreds of KB of JS/network hits the
 * initial render (keeps LCP/INP clean). The iframe is mounted lazily IN PLACE
 * the first time the user clicks play, filling the full hero card (matches
 * Inbox Zero's large inline demo rather than a small modal). youtube-nocookie +
 * a click gate means no YouTube cookies until the user opts in.
 *
 * The parent container (Hero.tsx `data-slot="hero-video"`) is
 * `relative aspect-video w-full overflow-hidden`, so both the facade button and
 * the iframe absolutely fill it.
 */
export function HeroVideoPlayer({ videoId }: { videoId: string }) {
  const t = useTranslations('landing');
  const [isPlaying, setIsPlaying] = useState(false);
  // maxresdefault is sharp but not guaranteed to exist; fall back to hqdefault.
  const [useMaxRes, setUseMaxRes] = useState(true);
  const thumbnailUrl = `https://i.ytimg.com/vi/${videoId}/${
    useMaxRes ? 'maxresdefault' : 'hqdefault'
  }.jpg`;

  if (isPlaying) {
    return (
      <iframe
        src={`https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1&rel=0`}
        title={t('videoTitle')}
        className="absolute inset-0 h-full w-full"
        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
        allowFullScreen
      />
    );
  }

  return (
    <button
      type="button"
      onClick={() => setIsPlaying(true)}
      aria-label={t('playDemo')}
      className="group absolute inset-0 h-full w-full cursor-pointer"
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
            className="h-7 w-7 fill-(--bg-elevated)"
          >
            <path d="M8 5v14l11-7z" />
          </svg>
        </span>
      </span>
    </button>
  );
}
