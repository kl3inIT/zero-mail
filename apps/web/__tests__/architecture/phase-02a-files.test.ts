import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

const WEB_ROOT = resolve(__dirname, '../..');

const EXPECTED_FILES = [
  'features/triage/components/PauseBanner.tsx',
  'features/triage/hooks/useToggleTriagePause.ts',
  'features/triage/api/triagePause.ts',
  'features/triage/api/keys.ts',
] as const;

function readJson(path: string): unknown {
  return JSON.parse(readFileSync(path, 'utf-8')) as unknown;
}

function getDeep(root: unknown, key: string): unknown {
  return key
    .split('.')
    .reduce<unknown>((node, part) => (node as Record<string, unknown> | undefined)?.[part], root);
}

describe('Phase 02A: required files exist', () => {
  EXPECTED_FILES.forEach((relPath) => {
    it(`${relPath} exists`, () => {
      expect(existsSync(resolve(WEB_ROOT, relPath))).toBe(true);
    });
  });
});

describe('Phase 02A: i18n key parity', () => {
  it('vi.json and en.json contain settings.triage.pause keys', () => {
    const viPath = resolve(WEB_ROOT, 'i18n/messages/vi.json');
    const enPath = resolve(WEB_ROOT, 'i18n/messages/en.json');
    expect(existsSync(viPath)).toBe(true);
    expect(existsSync(enPath)).toBe(true);

    const vi = readJson(viPath);
    const en = readJson(enPath);
    const requiredKeys = [
      'settings.triage.pause.title',
      'settings.triage.pause.toggleLabel',
      'settings.triage.pause.banner.heading',
      'settings.triage.pause.banner.unpause',
    ];

    for (const key of requiredKeys) {
      expect(getDeep(vi, key), `vi.json missing: ${key}`).toBeTruthy();
      expect(getDeep(en, key), `en.json missing: ${key}`).toBeTruthy();
    }
  });
});
