// Locks the token parity contract (Phase 1.6 REQ-1.6-1):
//  - Every supplemental token has --color-{name}: var(--{name}) in @theme inline
//  - --accent is #0E5E5A in :root
//  - --accent is #6FB3A8 in .dark
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

  it('--accent is #0E5E5A in :root', () => {
    const root = css.match(/:root\s*\{[\s\S]*?\}/)?.[0] ?? '';
    expect(root).toMatch(/--accent:\s*#0E5E5A/);
  });

  it('--accent is #6FB3A8 in .dark', () => {
    const dark = css.match(/\.dark\s*\{[\s\S]*?\}/)?.[0] ?? '';
    expect(dark).toMatch(/--accent:\s*#6FB3A8/);
  });
});
