import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { writeMergedMessages } from '@/scripts/merge-feature-i18n';

const WEB_ROOT = resolve(__dirname, '..');
const tempDirs: string[] = [];

function makeTempMessagesDir(): string {
  const root = mkdtempSync(join(tmpdir(), 'zeromail-i18n-'));
  const messagesDir = join(root, 'messages');
  mkdirSync(messagesDir, { recursive: true });
  tempDirs.push(root);
  return messagesDir;
}

function readJson(path: string): Record<string, unknown> {
  return JSON.parse(readFileSync(path, 'utf8')) as Record<string, unknown>;
}

describe('merge-feature-i18n erase protection', () => {
  afterEach(() => {
    while (tempDirs.length > 0) {
      rmSync(tempDirs.pop()!, { recursive: true, force: true });
    }
  });

  it('preserves legacy non-feature keys while adding feature-sourced llm keys', async () => {
    const messagesDir = makeTempMessagesDir();
    const viLegacy = {
      auth: { login: { title: 'Đăng nhập cũ' } },
      errors: { unknown: 'Lỗi cũ' },
    };
    const enLegacy = {
      auth: { login: { title: 'Legacy sign in' } },
      errors: { unknown: 'Legacy error' },
    };
    writeFileSync(join(messagesDir, 'vi.json'), `${JSON.stringify(viLegacy, null, 2)}\n`, 'utf8');
    writeFileSync(join(messagesDir, 'en.json'), `${JSON.stringify(enLegacy, null, 2)}\n`, 'utf8');

    await writeMergedMessages({
      featuresDir: resolve(WEB_ROOT, 'features'),
      messagesDir,
    });

    const vi = readJson(join(messagesDir, 'vi.json'));
    const en = readJson(join(messagesDir, 'en.json'));

    expect(vi).toMatchObject(viLegacy);
    expect(en).toMatchObject(enLegacy);
    expect(vi).toHaveProperty('llm.byok.title', 'Khóa API cho nhà cung cấp AI');
    expect(en).toHaveProperty('llm.byok.title', 'AI provider key');
    expect(vi).toHaveProperty('_generated');
    expect(en).toHaveProperty('_generated');
  });
});
