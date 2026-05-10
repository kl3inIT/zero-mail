import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import { ErrorCode } from '@/lib/api/error-codes';

/**
 * Contract test guarding REVIEW IN-05.
 *
 * The frontend ErrorCode object mirrors backend ErrorCodes.java. If a code
 * is added on either side without the other, this test fails so the drift
 * is caught at CI time rather than on a missing-key fallback at runtime.
 *
 * The check is **subset semantics**: every dotted code value in the FE
 * ErrorCode object must appear as a constant in ErrorCodes.java. The
 * reverse is not required - the backend is the source of truth and the
 * frontend mirrors only the codes that feature flows actually switch on.
 * This way, adding a new backend code without immediately mirroring it
 * is allowed; mirroring a non-existent code is not.
 */
describe('frontend ErrorCode -> backend ErrorCodes.java parity', () => {
  const errorCodesJavaPath = resolve(
    __dirname,
    '../../../../backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java',
  );
  const errorCodesJavaSource = readFileSync(errorCodesJavaPath, 'utf-8');

  // Extract every quoted dotted code from `public static final String NAME = "value";`
  const backendCodeMatches = Array.from(
    errorCodesJavaSource.matchAll(
      /public\s+static\s+final\s+String\s+\w+\s*=\s*"([^"]+)"/g,
    ),
  );
  const backendCodes = new Set(backendCodeMatches.map((match) => match[1]));

  it('extracts at least one backend code (sanity check on regex parsing)', () => {
    expect(backendCodes.size).toBeGreaterThan(0);
  });

  for (const [name, value] of Object.entries(ErrorCode)) {
    it(`backend ErrorCodes.java contains FE code ${name} (${value})`, () => {
      expect(
        backendCodes,
        `frontend ErrorCode.${name} = "${value}" but no matching constant in backend ErrorCodes.java. ` +
          `Either add the constant to backend/api/.../ErrorCodes.java or remove the FE entry.`,
      ).toContain(value);
    });
  }
});
