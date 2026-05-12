import { test } from '@playwright/test';

// Phase 05A Plan 02 owns chrome balance; Plan 04 owns the billing page balance.
test.describe.configure({ mode: 'serial' });

test.skip('credit balance is visible in chrome and billing surfaces', async () => {});
