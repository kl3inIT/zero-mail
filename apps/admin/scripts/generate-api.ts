import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';

const SPEC_URL = process.env.ADMIN_API_SPEC_URL ?? 'http://localhost:8080/v3/api-docs/admin';
const SPEC_PATH = process.env.ADMIN_API_SPEC_PATH;
const OUT = 'src/lib/api/admin-schema.d.ts';

async function resolveSpecInput(): Promise<string> {
  if (SPEC_PATH) {
    if (!existsSync(SPEC_PATH)) {
      throw new Error(`admin spec file not found: ${SPEC_PATH}`);
    }
    return SPEC_PATH;
  }

  const response = await fetch(SPEC_URL);
  if (!response.ok) {
    throw new Error(`admin spec fetch failed: ${response.status}`);
  }
  const spec = await response.text();
  mkdirSync('openapi', { recursive: true });
  writeFileSync('openapi/admin-spec.json', spec);
  return 'openapi/admin-spec.json';
}

async function main(): Promise<void> {
  const specInput = await resolveSpecInput();
  const pnpmExecPath = process.env.npm_execpath;
  const command = pnpmExecPath ? process.execPath : 'pnpm';
  const args = pnpmExecPath
    ? [pnpmExecPath, 'exec', 'openapi-typescript', specInput, '-o', OUT]
    : ['exec', 'openapi-typescript', specInput, '-o', OUT];

  execFileSync(command, args, { stdio: 'inherit' });
}

main().catch((error: unknown) => {
  console.error(error);
  process.exit(1);
});
