import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, it, expect } from 'vitest';

/**
 * Locks the vi/en dictionary parity contract:
 *  - every leaf key in i18n/messages/vi.json exists in i18n/messages/en.json
 *    (and vice versa)
 *  - the "errors" namespace mirrors the backend ErrorCodes 1:1 (Plan 02 — 8 dotted codes)
 *  - the locked validation banner key path `errors.validation.generic` exists in both
 *  - bundles use ICU `{var}` placeholders only, never Mustache `{{ var }}`
 *
 * Plan 07 will reuse the same flatten/diff logic in apps/web/scripts/check-i18n.ts
 * so test + CI stay in lockstep (CONTEXT.md decision D-D1 — single source of truth).
 */

type JsonObject = { [key: string]: unknown };

function loadBundle(file: string): JsonObject {
  const path = resolve(__dirname, '..', '..', 'i18n/messages', file);
  return JSON.parse(readFileSync(path, 'utf8')) as JsonObject;
}

function flatten(obj: JsonObject, prefix = ''): string[] {
  return Object.entries(obj).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      return flatten(value as JsonObject, path);
    }
    return [path];
  });
}

function getDeep(obj: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>((acc, key) => {
    if (acc !== null && typeof acc === 'object' && key in (acc as JsonObject)) {
      return (acc as JsonObject)[key];
    }
    return undefined;
  }, obj);
}

const vi = loadBundle('vi.json');
const en = loadBundle('en.json');

describe('vi/en key parity (Plan 05 GREEN)', () => {
  it('every leaf key in feature message bundles exists in the other locale', () => {
    const viKeys = flatten(vi).sort();
    const enKeys = flatten(en).sort();
    const viOnly = viKeys.filter((k) => !enKeys.includes(k));
    const enOnly = enKeys.filter((k) => !viKeys.includes(k));

    expect({ viOnly, enOnly }).toEqual({ viOnly: [], enOnly: [] });
    // sanity: bundles are non-trivial
    expect(viKeys.length).toBeGreaterThan(20);
  });

  it('locked validation banner key errors.validation.generic exists in both bundles', () => {
    expect(typeof getDeep(vi, 'errors.validation.generic')).toBe('string');
    expect(typeof getDeep(en, 'errors.validation.generic')).toBe('string');
  });

  it('errors namespace mirrors backend ErrorCodes 1:1 (Plan 02 — 8 dotted codes)', () => {
    // ErrorCodes constants → messages key paths (drop the "error." prefix when looking
    // up in the dictionary because next-intl namespaces under `errors.*`):
    const requiredErrorKeys = [
      'errors.auth.unauthorized', // ErrorCodes.AUTH_UNAUTHORIZED        = error.auth.unauthorized
      'errors.auth.forbidden', // ErrorCodes.AUTH_FORBIDDEN           = error.auth.forbidden
      'errors.auth.currentUserNotFound', // ErrorCodes.AUTH_CURRENT_USER_NOT_FOUND
      'errors.validation.generic', // ErrorCodes.VALIDATION (top-level)
      'errors.dataIntegrity', // ErrorCodes.DATA_INTEGRITY
      'errors.conflict', // ErrorCodes.CONFLICT
      'errors.badRequest', // ErrorCodes.BAD_REQUEST
      'errors.gmail.disconnected', // ErrorCodes.GMAIL_DISCONNECTED
    ];
    for (const key of requiredErrorKeys) {
      expect(typeof getDeep(vi, key), `vi.${key}`).toBe('string');
      expect(typeof getDeep(en, key), `en.${key}`).toBe('string');
    }
  });

  it('errors.validation.field includes the language.Pattern and preferredLanguage.invalid keys Plan 02 emits', () => {
    expect(typeof getDeep(vi, 'errors.validation.field.language.Pattern')).toBe('string');
    expect(typeof getDeep(en, 'errors.validation.field.language.Pattern')).toBe('string');
    expect(typeof getDeep(vi, 'errors.validation.field.preferredLanguage.invalid')).toBe('string');
    expect(typeof getDeep(en, 'errors.validation.field.preferredLanguage.invalid')).toBe('string');
  });

  it('bundles do not use Mustache {{ var }} placeholders (ICU `{var}` only)', () => {
    const viRaw = readFileSync(resolve(__dirname, '..', '..', 'i18n/messages', 'vi.json'), 'utf8');
    const enRaw = readFileSync(resolve(__dirname, '..', '..', 'i18n/messages', 'en.json'), 'utf8');
    expect(viRaw).not.toMatch(/\{\{/);
    expect(enRaw).not.toMatch(/\{\{/);
  });

  it('bundles do not contain the rejected `validation_` shape from CONTEXT.md', () => {
    const viRaw = readFileSync(resolve(__dirname, '..', '..', 'i18n/messages', 'vi.json'), 'utf8');
    const enRaw = readFileSync(resolve(__dirname, '..', '..', 'i18n/messages', 'en.json'), 'utf8');
    expect(viRaw).not.toMatch(/validation_/);
    expect(enRaw).not.toMatch(/validation_/);
  });

  it('privacy page keys exist in both locales', () => {
    const requiredPrivacyKeys = [
      'privacy.page.title',
      'privacy.page.intro',
      'privacy.neverStore.heading',
      'privacy.neverStore.body',
      'privacy.capabilities.heading',
      'privacy.capabilities.body',
      'privacy.byok.heading',
      'privacy.byok.body',
      'privacy.publicPolicyIntro',
      'privacy.publicPolicyLink',
      'privacy.settingsLink.body',
      'privacy.settingsLink.cta',
    ];

    for (const key of requiredPrivacyKeys) {
      expect(typeof getDeep(vi, key), `vi.${key}`).toBe('string');
      expect(typeof getDeep(en, key), `en.${key}`).toBe('string');
    }
  });
});
