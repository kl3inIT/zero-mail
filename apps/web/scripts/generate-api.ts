import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';

const SPEC_URL = process.env.API_SPEC_URL;
const SPEC_PATH = process.env.API_SPEC_PATH ?? 'openapi/openapi.json';
const OUT = 'lib/api/schema.d.ts';

async function resolveSpecInput(): Promise<string> {
  if (SPEC_URL) {
    const res = await fetch(SPEC_URL);
    if (!res.ok) throw new Error(`spec fetch ${res.status}`);
    const spec = await res.text();
    mkdirSync('openapi', { recursive: true });
    writeFileSync('openapi/spec.json', spec);
    return 'openapi/spec.json';
  }

  if (!existsSync(SPEC_PATH)) {
    throw new Error(
      `spec file not found: ${SPEC_PATH}. Run ./gradlew :backend:api:generateOpenApiDocs ` +
        'or set API_SPEC_URL.',
    );
  }

  return SPEC_PATH;
}

async function main(): Promise<void> {
  const specInput = await resolveSpecInput();
  const pnpmExecPath = process.env.npm_execpath;
  const command = pnpmExecPath ? process.execPath : 'pnpm';
  const args = pnpmExecPath
    ? [pnpmExecPath, 'exec', 'openapi-typescript', specInput, '-o', OUT]
    : ['exec', 'openapi-typescript', specInput, '-o', OUT];

  execFileSync(command, args, {
    stdio: 'inherit',
  });
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
