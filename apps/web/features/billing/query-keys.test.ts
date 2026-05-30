import { describe, expect, it } from 'vitest';

import { billingKeys } from './query-keys';

describe('billingKeys', () => {
  it('builds stable query keys for billing resources', () => {
    expect(billingKeys.all).toEqual(['billing']);
    expect(billingKeys.balance()).toEqual(['billing', 'balance']);
    expect(billingKeys.ledger()).toEqual(['billing', 'ledger', { limit: 10 }]);
    expect(billingKeys.ledger(25)).toEqual(['billing', 'ledger', { limit: 25 }]);
  });
});
