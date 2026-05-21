import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

const WEB_ROOT = resolve(__dirname, '..');
const BYOK_FORM_PATH = resolve(WEB_ROOT, 'features/llm/components/ByokForm.tsx');
const BYOK_HOOK_PATH = resolve(WEB_ROOT, 'features/llm/hooks/use-byok.ts');
const BYOK_QUERY_KEYS_PATH = resolve(WEB_ROOT, 'features/llm/query-keys.ts');
const BYOK_FEATURE_ROOT = resolve(WEB_ROOT, 'features/llm');

function readSource(path: string): string {
  return readFileSync(path, 'utf8');
}

function featureSources(root: string): string[] {
  return readdirSync(root).flatMap((entry) => {
    const path = join(root, entry);
    const stats = statSync(path);
    if (stats.isDirectory()) return featureSources(path);
    return stats.isFile() && /\.(ts|tsx)$/.test(entry) ? [readSource(path)] : [];
  });
}

describe('BYOK key handling invariants', () => {
  it('does not keep the raw apiKey in React state', () => {
    const source = readSource(BYOK_FORM_PATH);

    expect(source).not.toMatch(/\[\s*apiKey\s*,\s*setApiKey\s*\]/);
    expect(source).not.toMatch(/useState\s*<\s*string\s*>\s*\([^)]*apiKey/i);
    expect(source).toMatch(/useRef<HTMLFormElement>/);
  });

  it('resets the form DOM after a successful save', () => {
    expect(readSource(BYOK_FORM_PATH)).toContain('formRef.current?.reset()');
  });

  it('does not write the raw key to browser storage, cookies, or URLs in the BYOK feature', () => {
    const combinedSources = featureSources(BYOK_FEATURE_ROOT).join('\n');

    expect(combinedSources).not.toMatch(
      /localStorage\.setItem|sessionStorage\.setItem|document\.cookie\s*=/,
    );
    expect(combinedSources).not.toMatch(
      /URLSearchParams\([^)]*apiKey|searchParams\.[a-zA-Z]+\([^)]*apiKey/,
    );
  });

  it('renders apiKey as an uncontrolled password input', () => {
    const source = readSource(BYOK_FORM_PATH);

    expect(source).toMatch(/name="apiKey"/);
    expect(source).toMatch(/type="password"/);
    expect(source).toMatch(/autoComplete="off"/);
    expect(source).not.toMatch(/value=\{[^}]*apiKey/i);
  });

  it('keeps TanStack query keys free of provider, endpoint, and apiKey material', () => {
    // Key factory lives in query-keys.ts (per CLAUDE.md convention #8); the hook
    // file consumes it. Verify both: the factory shape AND that no queryKey
    // anywhere in the feature mentions sensitive material.
    expect(readSource(BYOK_QUERY_KEYS_PATH)).toContain("all: ['byok'] as const");
    expect(readSource(BYOK_HOOK_PATH)).not.toMatch(/queryKey:[\s\S]*(apiKey|endpoint|provider)/);
  });
});
