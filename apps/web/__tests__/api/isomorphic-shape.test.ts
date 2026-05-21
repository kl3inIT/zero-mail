import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it, expect } from 'vitest';

const APP_WEB = resolve(__dirname, '../..');
const ACCOUNT_API_FILE = resolve(APP_WEB, 'features/account/api/account-api.ts');
const QUERY_KEYS_FILE = resolve(APP_WEB, 'features/account/query-keys.ts');
const HOOK_FILE = resolve(APP_WEB, 'features/account/hooks/useCurrentUser.ts');
const PROXY_FILE = resolve(APP_WEB, 'proxy.ts');
const LAYOUT_FILE = resolve(APP_WEB, 'app/layout.tsx');

describe('Account current-user API shape', () => {
  it('features/account/api/account-api.ts exposes the isomorphic current-user API', () => {
    expect(existsSync(ACCOUNT_API_FILE)).toBe(true);
    const src = readFileSync(ACCOUNT_API_FILE, 'utf8');
    expect(src).toMatch(/export\s+async\s+function\s+fetchCurrentUser/);
    expect(src).toMatch(/fetcher\??:/);
    expect(src).toMatch(/signal\??:/);
    expect(src).toMatch(/headers\??:/);
    // Cached wrapper export (primitive-keyed)
    expect(src).toMatch(/export\s+const\s+getCurrentUserCached\s*=\s*cache\s*\(/);
    // Backwards-compat alias for client/TanStack callers
    expect(src).toMatch(/export\s+const\s+getCurrentUser\s*=\s*fetchCurrentUser/);
  });

  it('features/account/query-keys.ts exports accountQueryKeys factory', () => {
    expect(existsSync(QUERY_KEYS_FILE)).toBe(true);
    const src = readFileSync(QUERY_KEYS_FILE, 'utf8');
    expect(src).toMatch(/export\s+const\s+accountQueryKeys/);
    expect(src).toMatch(/me\s*:\s*\(\s*\)\s*=>/);
  });

  it('features/account/hooks/useCurrentUser.ts wraps getCurrentUser in useQuery', () => {
    expect(existsSync(HOOK_FILE)).toBe(true);
    const src = readFileSync(HOOK_FILE, 'utf8');
    expect(src).toMatch(/useQuery/);
    expect(src).toMatch(/getCurrentUser/);
    expect(src).toMatch(/accountQueryKeys\.me\(\)/);
  });

  it('proxy.ts no longer has inline fetch(`${apiBase}/me`); imports getCurrentUser', () => {
    const src = readFileSync(PROXY_FILE, 'utf8');
    expect(src).not.toMatch(/fetch\(`\$\{apiBase\}\/me`/);
    expect(src).toMatch(/getCurrentUser\s*\(/);
    expect(src).toMatch(/from\s+['"]@\/features\/account\/api\/account-api['"]/);
  });

  it('app/layout.tsx no longer has inline fetch(`${apiBase}/me`); imports getCurrentUserCached', () => {
    const src = readFileSync(LAYOUT_FILE, 'utf8');
    expect(src).not.toMatch(/fetch\(`\$\{apiBase\}\/me`/);
    expect(src).toMatch(/getCurrentUserCached\s*\(/);
    expect(src).toMatch(/from\s+['"]@\/features\/account\/api\/account-api['"]/);
  });

  it('proxy.ts avoids next-intl middleware rewrite and no longer needs the cast bridge', () => {
    const src = readFileSync(PROXY_FILE, 'utf8');
    expect(src).not.toMatch(/next-intl\/middleware/);
    expect(src).not.toMatch(/createIntlMiddleware/);
    expect(src).not.toMatch(/as\s+unknown\s+as/);
    expect(src).toMatch(/NextResponse\.next\(/);
  });
});
