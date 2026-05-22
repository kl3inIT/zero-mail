import { existsSync, readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it, expect } from 'vitest';

const APP_DIR = resolve(__dirname, '../../app');

describe('Phase 1.3 — Route group architecture', () => {
  it('app/(public)/, app/(auth)/, app/(protected)/ exist as directories', () => {
    expect(existsSync(resolve(APP_DIR, '(public)'))).toBe(true);
    expect(existsSync(resolve(APP_DIR, '(auth)'))).toBe(true);
    expect(existsSync(resolve(APP_DIR, '(protected)'))).toBe(true);
    expect(statSync(resolve(APP_DIR, '(public)')).isDirectory()).toBe(true);
    expect(statSync(resolve(APP_DIR, '(auth)')).isDirectory()).toBe(true);
    expect(statSync(resolve(APP_DIR, '(protected)')).isDirectory()).toBe(true);
  });

  it('app/[locale]/ mirror tree is absent (locale is cookie data, not route structure)', () => {
    expect(existsSync(resolve(APP_DIR, '[locale]'))).toBe(false);
  });

  it('public landing replaces flat app/page.tsx', () => {
    expect(existsSync(resolve(APP_DIR, '(public)/page.tsx'))).toBe(true);
    expect(existsSync(resolve(APP_DIR, 'page.tsx'))).toBe(false);
  });

  it('flat route pages are moved into route groups', () => {
    expect(existsSync(resolve(APP_DIR, '(auth)/login/page.tsx'))).toBe(true);
    expect(existsSync(resolve(APP_DIR, '(protected)/onboarding/page.tsx'))).toBe(true);
    expect(existsSync(resolve(APP_DIR, '(protected)/(app)/rules/page.tsx'))).toBe(true);
    expect(existsSync(resolve(APP_DIR, '(protected)/(app)/settings/page.tsx'))).toBe(true);
    expect(existsSync(resolve(APP_DIR, 'login/page.tsx'))).toBe(false);
    expect(existsSync(resolve(APP_DIR, 'onboarding/page.tsx'))).toBe(false);
    expect(existsSync(resolve(APP_DIR, 'settings/page.tsx'))).toBe(false);
    expect(existsSync(resolve(APP_DIR, '(protected)/rules/page.tsx'))).toBe(false);
    expect(existsSync(resolve(APP_DIR, '(protected)/settings/page.tsx'))).toBe(false);
  });

  it('every route group has its own layout.tsx', () => {
    expect(existsSync(resolve(APP_DIR, '(public)/layout.tsx'))).toBe(true);
    expect(existsSync(resolve(APP_DIR, '(auth)/layout.tsx'))).toBe(true);
    expect(existsSync(resolve(APP_DIR, '(protected)/layout.tsx'))).toBe(true);
    expect(existsSync(resolve(APP_DIR, '(protected)/(app)/layout.tsx'))).toBe(true);
    expect(existsSync(resolve(APP_DIR, '(protected)/onboarding/layout.tsx'))).toBe(true);
  });

  it('root app/layout.tsx is preserved', () => {
    expect(existsSync(resolve(APP_DIR, 'layout.tsx'))).toBe(true);
  });

  it('proxy does not use next-intl routing middleware rewrite', () => {
    const proxy = readFileSync(resolve(APP_DIR, '../proxy.ts'), 'utf8');
    expect(proxy).not.toMatch(/next-intl\/middleware/);
    expect(proxy).not.toMatch(/createIntlMiddleware/);
    expect(proxy).toMatch(/NextResponse\.next\(/);
  });
});
