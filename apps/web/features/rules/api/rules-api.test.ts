import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

const CLIENT_FILE = resolve(__dirname, '../../../lib/api/client.ts');

describe('rules api XSRF wiring', () => {
  it('XSRF is injected by fetch-layer middleware, not per-mutation', () => {
    const clientSrc = readFileSync(CLIENT_FILE, 'utf8');

    // Middleware exists and gates on mutating methods.
    expect(clientSrc).toMatch(/xsrfMiddleware/);
    expect(clientSrc).toMatch(/X-XSRF-TOKEN/);
    expect(clientSrc).toMatch(/MUTATING_METHODS/);
    // Middleware is actually registered on the client.
    expect(clientSrc).toMatch(/api\.use\(xsrfMiddleware\)/);
  });

  it('rules-api.ts does not duplicate XSRF wiring at the callsite', () => {
    const rulesApi = readFileSync(resolve(__dirname, './rules-api.ts'), 'utf8');
    // Old pattern: per-endpoint jsonHeaders()/unsafeHeaders() spreading xsrfHeader().
    expect(rulesApi).not.toMatch(/xsrfHeader/);
    expect(rulesApi).not.toMatch(/jsonHeaders|unsafeHeaders/);
  });
});
