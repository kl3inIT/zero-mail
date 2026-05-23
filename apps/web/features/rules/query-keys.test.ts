import { describe, expect, it } from 'vitest';

import { rulesKeys } from './query-keys';

describe('rulesKeys', () => {
  it('builds stable query keys for rule resources', () => {
    expect(rulesKeys.list()).toEqual(['rules', 'list']);
    expect(rulesKeys.detail('rule-123')).toEqual(['rules', 'detail', 'rule-123']);
    expect(rulesKeys.templates()).toEqual(['rules', 'templates']);
  });
});
