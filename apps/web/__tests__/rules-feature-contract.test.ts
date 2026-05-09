import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { toRuleCompileResult } from '@/features/rules/api/rules-api';

const WEB_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');

const plannedRulesFiles = [
  'app/(protected)/rules/page.tsx',
  'features/rules/api/rules-api.ts',
  'features/rules/hooks/use-rules.ts',
  'features/rules/query-keys.ts',
  'features/rules/components/RulesWorkspace.tsx',
  'features/rules/messages.ts',
] as const;

const requiredRulesCopy = [
  'Rules',
  'Write rules in plain language, preview what would match, then enable only the rules you trust.',
  'Compile rule',
  'Save disabled rule',
  'Preview rule',
  'Enable rule',
  'Answer clarification',
  'No Gmail changes were made.',
] as const;

describe('rules feature source contract', () => {
  it('declares the future feature-owned files that Plan 03-08 must land together', () => {
    expect(plannedRulesFiles).toContain('features/rules/api/rules-api.ts');
    expect(plannedRulesFiles).toContain('features/rules/components/RulesWorkspace.tsx');
    expect(requiredRulesCopy).toContain('No Gmail changes were made.');
  });

  it('keeps raw fetch calls to /api/rules out of app and feature code', () => {
    const offenders = allSourceFiles(['app', 'features', 'lib'])
      .filter((sourceFile) => sourceFile !== 'features/rules/api/rules-api.ts')
      .filter((sourceFile) => {
        const sourceText = readFileSync(join(WEB_ROOT, sourceFile), 'utf8');
        return /fetch\(\s*['"`]\/api\/rules/.test(sourceText);
      });

    expect(offenders).toEqual([]);
  });

  it('requires generated OpenAPI client usage when the rules API module appears', () => {
    const rulesApiPath = join(WEB_ROOT, 'features/rules/api/rules-api.ts');
    if (!existsSync(rulesApiPath)) {
      expect(plannedRulesFiles).toContain('features/rules/api/rules-api.ts');
      return;
    }

    const sourceText = readFileSync(rulesApiPath, 'utf8');
    expect(sourceText).toContain('@/lib/api/client');
    expect(sourceText).toContain("components['schemas']");
    expect(sourceText).not.toMatch(/fetch\(\s*['"`]\/api\/rules/);
  });

  it('requires i18n-visible rules copy once feature messages land', () => {
    const rulesMessagesPath = join(WEB_ROOT, 'features/rules/messages.ts');
    if (!existsSync(rulesMessagesPath)) {
      expect(requiredRulesCopy).toContain('No Gmail changes were made.');
      return;
    }

    const sourceText = readFileSync(rulesMessagesPath, 'utf8');
    for (const copy of requiredRulesCopy) {
      expect(sourceText).toContain(copy);
    }
  });

  it('keeps compile clarification as a typed result instead of a ProblemDetail error', () => {
    expect(
      toRuleCompileResult({
        status: 'clarificationRequired',
        clarification: {
          language: 'en',
          question: 'Which newsletters should Zero Mail archive?',
        },
      }),
    ).toMatchObject({
      status: 'clarificationRequired',
      clarification: {
        question: 'Which newsletters should Zero Mail archive?',
      },
    });

    expect(
      toRuleCompileResult({
        status: 'invalid',
        invalid: { reason: 'unknown_matcher' },
      }),
    ).toMatchObject({
      status: 'invalid',
      invalid: { reason: 'unknown_matcher' },
    });
  });

  it('requires the protected route and owned feature files once the route lands', () => {
    const rulesRoutePath = join(WEB_ROOT, 'app/(protected)/rules/page.tsx');
    if (!existsSync(rulesRoutePath)) {
      expect(plannedRulesFiles).toContain('app/(protected)/rules/page.tsx');
      return;
    }

    for (const plannedRulesFile of plannedRulesFiles) {
      expect(existsSync(join(WEB_ROOT, plannedRulesFile)), plannedRulesFile).toBe(true);
    }
  });
});

function allSourceFiles(topLevelDirectories: string[]): string[] {
  return topLevelDirectories.flatMap((topLevelDirectory) =>
    collectSourceFiles(join(WEB_ROOT, topLevelDirectory)),
  );
}

function collectSourceFiles(directoryPath: string): string[] {
  if (!existsSync(directoryPath)) return [];

  return readdirSync(directoryPath).flatMap((entryName) => {
    const absolutePath = join(directoryPath, entryName);
    const relativePath = relative(WEB_ROOT, absolutePath).replaceAll('\\', '/');
    if (relativePath.includes('/node_modules/') || relativePath.includes('/components/ui/')) {
      return [];
    }
    const entryStats = statSync(absolutePath);
    if (entryStats.isDirectory()) return collectSourceFiles(absolutePath);
    if (!/\.(ts|tsx)$/.test(entryName)) return [];
    return [relativePath];
  });
}
