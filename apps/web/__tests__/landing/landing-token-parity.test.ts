// Locks the token parity contract (Phase 1.6 REQ-1.6-1, updated for purple pivot):
//  - Every supplemental token has --color-{name}: var(--{name}) in @theme inline
//  - --accent is #EAE8F7 (light) — petclinic purple accent-soft surface
//  - --accent is #2F2A4A in .dark
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const SUPPLEMENTAL_TOKENS = [
  'accent-soft',
  'ink',
  'ink-2',
  'text-faint',
  'green',
  'green-soft',
  'amber',
  'amber-soft',
  'red',
  'red-soft',
  'blue',
  'blue-soft',
  'violet',
  'violet-soft',
];

describe('globals.css token parity', () => {
  const css = readFileSync(resolve(__dirname, '../../app/globals.css'), 'utf8');

  it.each(SUPPLEMENTAL_TOKENS)(
    'supplemental token --%s has matching --color-%s in @theme inline',
    (token) => {
      expect(css).toMatch(new RegExp(`--${token}:\\s*#[0-9A-Fa-f]{3,8}`));
      expect(css).toMatch(new RegExp(`--color-${token}:\\s*var\\(--${token}\\)`));
    },
  );

  it('--accent is #EAE8F7 in :root (purple accent-soft surface)', () => {
    const root = css.match(/:root\s*\{[\s\S]*?\}/)?.[0] ?? '';
    expect(root).toMatch(/--accent:\s*#EAE8F7/);
  });

  it('--accent is #2F2A4A in .dark', () => {
    const dark = css.match(/\.dark\s*\{[\s\S]*?\}/)?.[0] ?? '';
    expect(dark).toMatch(/--accent:\s*#2F2A4A/);
  });
});
