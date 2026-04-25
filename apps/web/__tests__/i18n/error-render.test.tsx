import { describe, it } from 'vitest';

/**
 * Wave 0 RED stub — Plan 06 turns this GREEN.
 *
 * Locks the FE error-localization display contract (CONTEXT.md decision D-D5):
 *   - default locale vi -> renders Vietnamese localized string for known error codes
 *   - locale switch to en -> renders English localized string for the same code
 *   - unknown error code -> renders localized errors.unknown fallback
 *   - never renders raw ProblemDetail title/detail, exception class names, or SQL
 *     constraint names (security_threat_model #6 — missing-key sentinel rendering)
 *
 * Plan 06 will mock openapi-fetch failure responses and render a route component
 * inside next-intl's NextIntlClientProvider; assertions use @testing-library/react
 * + the matchers loaded by __tests__/setup.ts.
 */
describe('error code -> localized message rendering (Wave 0 stub — implemented by Plan 06)', () => {
  it.skip('renders Vietnamese for error.auth.unauthorized when locale=vi', () => {});

  it.skip('renders English for error.auth.unauthorized when locale=en', () => {});

  it.skip('renders localized errors.unknown fallback for unknown code', () => {});

  it.skip('never renders raw title/detail/exception names/SQL constraint names', () => {});
});
