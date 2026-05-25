import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const featureRoot = join(process.cwd(), 'src/features/rule-catalog');

function readFeatureFile(fileName: string): string {
  return readFileSync(join(featureRoot, fileName), 'utf8');
}

describe('rule catalog feature contract', () => {
  it('uses generated admin schema types and openapi-fetch client for catalog endpoints', () => {
    const apiSource = readFeatureFile('rule-catalog-api.ts');

    expect(apiSource).toContain("from '@/lib/api/admin-client'");
    expect(apiSource).toContain("from '@/lib/api/admin-schema'");
    expect(apiSource).toContain("api.GET('/api/admin/rule-catalog/personas'");
    expect(apiSource).toContain("api.GET('/api/admin/rule-catalog/actions'");
    expect(apiSource).not.toContain('fetch(');
  });

  it('declares rule catalog query keys and invalidates them after mutations', () => {
    const queryKeysSource = readFeatureFile('query-keys.ts');
    const savePersonaSource = readFeatureFile('use-save-persona.ts');
    const saveExampleSource = readFeatureFile('use-save-example.ts');
    const saveActionSource = readFeatureFile('use-save-action-descriptor.ts');
    const reorderSource = readFeatureFile('use-reorder-rule-catalog.ts');

    expect(queryKeysSource).toContain("all: ['rule-catalog']");
    for (const source of [
      savePersonaSource,
      saveExampleSource,
      saveActionSource,
      reorderSource,
    ]) {
      expect(source).toContain('invalidateQueries');
      expect(source).toContain('ruleCatalogQueryKeys.all');
    }
  });
});
