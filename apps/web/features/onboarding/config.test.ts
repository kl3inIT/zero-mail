import { describe, expect, it } from 'vitest';

import {
  BETA_ONBOARDING_ENABLED,
  ONBOARDING_BYPASS_ROUTE,
  shouldShowBetaOnboarding,
} from './config';

describe('onboarding config', () => {
  it('keeps beta onboarding disabled and routes users to the bypass route', () => {
    expect(BETA_ONBOARDING_ENABLED).toBe(false);
    expect(ONBOARDING_BYPASS_ROUTE).toBe('/chat');
    expect(shouldShowBetaOnboarding('GMAIL_CONNECT')).toBe(false);
    expect(shouldShowBetaOnboarding('COMPLETE')).toBe(false);
    expect(shouldShowBetaOnboarding(null)).toBe(false);
  });
});
