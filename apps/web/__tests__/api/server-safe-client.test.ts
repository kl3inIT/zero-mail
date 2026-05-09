import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it, expect } from 'vitest';

const APP_WEB = resolve(__dirname, '../..');
const CLIENT_FILE = resolve(APP_WEB, 'lib/api/client.ts');
const ERRORS_FILE = resolve(APP_WEB, 'lib/api/errors.ts');

describe('API client server-safety contract', () => {
  it('lib/api/client.ts has NO "use client" directive at top of file', () => {
    expect(existsSync(CLIENT_FILE)).toBe(true);
    const src = readFileSync(CLIENT_FILE, 'utf8');
    // Look only at the first ~3 lines (directive must be the first statement)
    const head = src.split('\n').slice(0, 3).join('\n');
    expect(head).not.toMatch(/['"]use client['"]/);
  });

  it('lib/api/client.ts does not re-export client-localized error hooks', () => {
    const src = readFileSync(CLIENT_FILE, 'utf8');
    expect(src).not.toMatch(/from\s+['"]\.\/errors['"]/);
    expect(src).not.toMatch(/from\s+['"]@\/lib\/api\/errors['"]/);
    expect(src).not.toMatch(/useLocalizedApiError/);
    expect(src).not.toMatch(/useLocalizedFieldError/);
  });

  it('lib/api/errors.ts STILL has "use client" and exports the localized hooks', () => {
    expect(existsSync(ERRORS_FILE)).toBe(true);
    const src = readFileSync(ERRORS_FILE, 'utf8');
    const head = src.split('\n').slice(0, 3).join('\n');
    expect(head).toMatch(/['"]use client['"]/);
    expect(src).toMatch(/export\s+function\s+useLocalizedApiError/);
    expect(src).toMatch(/export\s+function\s+useLocalizedFieldError/);
  });
});
