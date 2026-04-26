import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it, expect } from 'vitest';

const APP_WEB = resolve(__dirname, '../..');
const ME_FILE = resolve(APP_WEB, 'features/account/api/me.ts');
const KEYS_FILE = resolve(APP_WEB, 'features/account/api/keys.ts');
const HOOK_FILE = resolve(APP_WEB, 'features/account/hooks/useCurrentUser.ts');
const PROXY_FILE = resolve(APP_WEB, 'proxy.ts');
const LAYOUT_FILE = resolve(APP_WEB, 'app/layout.tsx');

describe('Phase 1.3 — Isomorphic /me API shape (D-B1, D-B4)', () => {
  it('features/account/api/me.ts exists with isomorphic getCurrentUser signature', () => {
    expect(existsSync(ME_FILE)).toBe(true);
    const src = readFileSync(ME_FILE, 'utf8');
    expect(src).toMatch(/export\s+async\s+function\s+getCurrentUser/);
    expect(src).toMatch(/fetcher\??:/);
    expect(src).toMatch(/signal\??:/);
    expect(src).toMatch(/headers\??:/);
  });

  it('features/account/api/keys.ts exports accountKeys factory (D-B3)', () => {
    expect(existsSync(KEYS_FILE)).toBe(true);
    const src = readFileSync(KEYS_FILE, 'utf8');
    expect(src).toMatch(/export\s+const\s+accountKeys/);
    expect(src).toMatch(/me\s*:\s*\(\s*\)\s*=>/);
  });

  it('features/account/hooks/useCurrentUser.ts wraps getCurrentUser in useQuery', () => {
    expect(existsSync(HOOK_FILE)).toBe(true);
    const src = readFileSync(HOOK_FILE, 'utf8');
    expect(src).toMatch(/useQuery/);
    expect(src).toMatch(/getCurrentUser/);
    expect(src).toMatch(/accountKeys\.me\(\)/);
  });

  it('proxy.ts no longer has inline fetch(`${apiBase}/me`); imports getCurrentUser', () => {
    const src = readFileSync(PROXY_FILE, 'utf8');
    expect(src).not.toMatch(/fetch\(`\$\{apiBase\}\/me`/);
    expect(src).toMatch(/getCurrentUser\s*\(/);
    expect(src).toMatch(/from\s+['"]@\/features\/account\/api\/me['"]/);
  });

  it('app/layout.tsx no longer has inline fetch(`${apiBase}/me`); imports getCurrentUser', () => {
    const src = readFileSync(LAYOUT_FILE, 'utf8');
    expect(src).not.toMatch(/fetch\(`\$\{apiBase\}\/me`/);
    expect(src).toMatch(/getCurrentUser\s*\(/);
    expect(src).toMatch(/from\s+['"]@\/features\/account\/api\/me['"]/);
  });

  it('proxy.ts avoids next-intl middleware rewrite and no longer needs the cast bridge', () => {
    const src = readFileSync(PROXY_FILE, 'utf8');
    expect(src).not.toMatch(/next-intl\/middleware/);
    expect(src).not.toMatch(/createIntlMiddleware/);
    expect(src).not.toMatch(/as\s+unknown\s+as/);
    expect(src).toMatch(/NextResponse\.next\(\)/);
  });
});
