'use client';

import { useEffect, useRef, useState } from 'react';

/**
 * Optional banner image for the landing referral band. Self-hides if the image fails to load — the
 * campaign row may advertise {@code bannerImageAvailable} while the stored file is absent in a given
 * environment (banners live on the API host's disk, not in git), so a broken-image box must never
 * reach the marketing page. When hidden, the section's countdown/text still stand on their own.
 *
 * Two detection paths are needed: {@code onError} catches a failure that happens after hydration,
 * and the mount effect catches an image that already errored during SSR hydration (the browser
 * finished the failed request before React attached the handler, so onError never re-fires).
 */
export function LandingReferralBanner({ src, alt }: { src: string; alt: string }) {
  const [failed, setFailed] = useState(false);
  const imageRef = useRef<HTMLImageElement>(null);

  useEffect(() => {
    const image = imageRef.current;
    if (image && image.complete && image.naturalWidth === 0) {
      setFailed(true);
    }
  }, []);

  if (failed) return null;

  return (
    <div className="overflow-hidden rounded-2xl border border-(--line) shadow-sm">
      {/* eslint-disable-next-line @next/next/no-img-element -- Banner served by backend API at runtime. */}
      <img
        ref={imageRef}
        src={src}
        alt={alt}
        onError={() => setFailed(true)}
        className="block aspect-[16/5] w-full object-cover sm:aspect-[21/6]"
      />
    </div>
  );
}
