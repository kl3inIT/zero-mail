import { describe, expect, it } from 'vitest';

import { billingKeys } from './query-keys';

describe('billingKeys', () => {
  it('builds stable query keys for billing resources', () => {
    expect(billingKeys.packages()).toEqual(['billing', 'packages']);
    expect(billingKeys.ledger()).toEqual(['billing', 'ledger']);
    expect(billingKeys.topupIntent('ZM-123')).toEqual(['billing', 'topup-intent', 'ZM-123']);
  });
});
