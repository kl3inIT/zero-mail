import { ImageResponse } from 'next/og';

import { SITE_NAME } from '@/lib/site';

/**
 * Dynamic 1200×630 Open Graph / Twitter card image (file convention — Next
 * auto-wires it into `<meta property="og:image">` and, with no `twitter-image`
 * present, the Twitter card too). Replaces the old square 512×512 icon that made
 * social shares render a tiny thumbnail. Standalone asset, so brand hex values
 * are inlined here (the "no hardcoded hex" rule governs token-driven UI
 * components, not a generated image). Text is ASCII-only — the ImageResponse
 * default font does not reliably render Vietnamese diacritics.
 */
export const runtime = 'nodejs';
export const alt = `${SITE_NAME} — AI inbox cleanup for Gmail`;
export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';

export default function OpenGraphImage() {
  return new ImageResponse(
    <div
      style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        padding: '80px',
        background: '#191724',
        backgroundImage: 'radial-gradient(circle at 80% 15%, #2F2A4A 0%, #191724 55%)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
        <div
          style={{
            width: '64px',
            height: '64px',
            borderRadius: '16px',
            background: '#6C5CE7',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '40px',
            fontWeight: 700,
            color: '#191724',
          }}
        >
          0
        </div>
        <span style={{ fontSize: '34px', fontWeight: 600, color: '#E5E2EE' }}>{SITE_NAME}</span>
      </div>

      <div
        style={{
          display: 'flex',
          fontSize: '76px',
          fontWeight: 700,
          color: '#E5E2EE',
          lineHeight: 1.1,
          marginTop: '48px',
          maxWidth: '900px',
        }}
      >
        AI inbox cleanup for Gmail
      </div>

      <div style={{ display: 'flex', fontSize: '38px', color: '#A59CF0', marginTop: '24px' }}>
        Auto-triage, label &amp; draft replies — reach inbox zero.
      </div>
    </div>,
    size,
  );
}
