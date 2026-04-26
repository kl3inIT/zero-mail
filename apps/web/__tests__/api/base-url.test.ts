import { afterEach, describe, expect, it, vi } from 'vitest';

import { getApiBase, getApiUrl } from '@/lib/api/base-url';

describe('API base URL normalization', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('defaults to backend dev server when NEXT_PUBLIC_API_BASE is absent', () => {
    vi.stubEnv('NEXT_PUBLIC_API_BASE', '');

    expect(getApiBase()).toBe('http://localhost:8080');
    expect(getApiUrl('/oauth2/authorization/google')).toBe(
      'http://localhost:8080/oauth2/authorization/google',
    );
  });

  it('adds http:// when env contains host:port without a scheme', () => {
    vi.stubEnv('NEXT_PUBLIC_API_BASE', 'localhost:8080/');

    expect(getApiBase()).toBe('http://localhost:8080');
    expect(getApiUrl('/tenant/connect-gmail')).toBe('http://localhost:8080/tenant/connect-gmail');
  });

  it('preserves explicit http or https origins', () => {
    vi.stubEnv('NEXT_PUBLIC_API_BASE', 'https://api.example.test/');

    expect(getApiBase()).toBe('https://api.example.test');
    expect(getApiUrl('/me')).toBe('https://api.example.test/me');
  });
});
