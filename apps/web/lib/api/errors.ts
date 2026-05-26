'use client';

/**
 * Frontend error-localization helpers (Plan 06 — REQ-4 + threat_model T-1.1.06-01/02).
 *
 * Two contracts these helpers enforce:
 *   1. Switch on `code` ONLY. Server-controlled `title` / `detail` / exception class
 *      names / SQL state must NEVER reach the rendered DOM. The hooks accept the
 *      whole ApiError object only so that the call site cannot accidentally pull a
 *      raw English diagnostic out of the server-only "detail" property.
 *   2. Missing-key sentinel rendering. When an error code has no matching bundle
 *      key, dev renders `[MISSING:<code>]` (loud QA signal, blocks Plan 07's CI
 *      sweep) and prod renders the localized `errors.unknown` fallback. Silent
 *      fallback to the raw key (next-intl's default behavior) is forbidden by
 *      the threat model.
 *
 * Code → bundle key mapping:
 *   Backend ErrorCodes constants are dotted with the `error.` prefix
 *   (e.g. `error.auth.unauthorized`, `error.validation.field.language.Pattern`).
 *   The FE bundle namespaces under `errors.*`. When this hook is used inside a
 *   `useTranslations('errors')` namespace, we look up keys WITHOUT the `errors.`
 *   prefix. The server's `error.` prefix is stripped before lookup so callers can
 *   pass either shape (`error.auth.unauthorized` or `auth.unauthorized`).
 */

import { useTranslations, useMessages } from 'next-intl';

import type { components } from './schema';

export type ApiError = components['schemas']['ApiError'];
export type FieldError = components['schemas']['FieldErrorDto'];

/** Strip the legacy `error.` prefix from server-provided codes. */
function normalizeCode(code: string): string {
  return code.startsWith('error.') ? code.slice('error.'.length) : code;
}

/**
 * Map a normalized server code to the bundle key that actually exists in
 * `i18n/messages/<locale>.json`. Most codes are 1:1 (e.g. `auth.unauthorized` ->
 * `errors.auth.unauthorized`), but a handful of top-level codes resolve to an
 * object in the bundle (e.g. `errors.validation` is a namespace, not a leaf
 * string). For those, we redirect to the generic localized leaf so the hook
 * never falls through to the [MISSING:] sentinel for a contractually-known
 * server code. (WR-01.)
 */
function bundleKeyForCode(normalized: string): string {
  // Top-level validation: backend emits `error.validation` for body-level
  // MethodArgumentNotValid + ConstraintViolation failures (see ErrorCodes.VALIDATION
  // and GlobalExceptionHandler). The FE bundle nests `errors.validation.*`, so the
  // string leaf is at `errors.validation.generic`.
  if (normalized === 'validation') return 'validation.generic';
  return phaseNineErrorCodeMap[normalized] ?? normalized;
}

const phaseNineErrorCodeMap: Record<string, string> = {
  'ai.byok.base_url_host_private': 'ai.byok.base_url_host_private',
  'ai.byok.base_url_host_unresolvable': 'ai.byok.base_url_host_unresolvable',
  'ai.byok.base_url_not_https': 'ai.byok.base_url_not_https',
  'ai.byok.base_url_not_supported_for_provider': 'ai.byok.base_url_not_supported_for_provider',
  'ai.byok.base_url_port_not_allowed': 'ai.byok.base_url_port_not_allowed',
  'ai.byok.model_not_in_last_test': 'ai.byok.model_not_in_last_test',
  'ai.byok.no_model_picked': 'ai.byok.no_model_picked',
  'ai.byok.no_row': 'ai.byok.no_row',
  'ai.byok.provider_not_allowed': 'ai.byok.provider_not_allowed',
  'ai.byok.rate_limit_unavailable': 'ai.byok.rate_limit_unavailable',
  'ai.byok.rate_limited': 'ai.byok.rate_limited',
  'ai.byok.test_connection.rate_limited': 'ai.byok.test_connection.rate_limited',
  'ai.test_connection.rate_limited': 'ai.test_connection.rate_limited',
  'behavior.draft_confidence.invalid': 'behavior.draft_confidence.invalid',
  'knowledge.not_found': 'knowledge.not_found',
  'knowledge.title.duplicate': 'knowledge.title.duplicate',
  'safety_net.not_found': 'safety_net.not_found',
  'safety_net.observation_not_deletable': 'safety_net.observation_not_deletable',
  'safety_net.pattern_invalid': 'safety_net.pattern_invalid',
  'voice.generate.failed': 'voice.generate.failed',
  'voice.generate.gmail_read_failed': 'voice.generate.gmail_read_failed',
  'voice.generate.rate_limited': 'voice.generate.rate_limited',
  'voice.personal_instructions.too_long': 'voice.personal_instructions.too_long',
  'voice.tone_preset.invalid': 'voice.tone_preset.invalid',
  'voice.writing_style.too_long': 'voice.writing_style.too_long',
  'voice.writing_style.too_short': 'voice.writing_style.too_short',
};

/** Walk a dotted path and confirm the leaf is a string. */
function hasNestedKey(messages: unknown, dottedKey: string): boolean {
  let cur: unknown = messages;
  for (const part of dottedKey.split('.')) {
    if (cur == null || typeof cur !== 'object' || !(part in (cur as Record<string, unknown>))) {
      return false;
    }
    cur = (cur as Record<string, unknown>)[part];
  }
  return typeof cur === 'string';
}

function isProduction(): boolean {
  return process.env.NODE_ENV === 'production';
}

/**
 * React hook returning a function that converts an ApiError to a localized
 * user-facing message. Switches on err.code only; never reads title/detail.
 */
export function useLocalizedApiError() {
  const t = useTranslations('errors');
  const messages = useMessages() as Record<string, unknown>;
  const errorsBundle = (messages as { errors?: unknown }).errors;

  return (err: ApiError | undefined): string => {
    if (!err?.code) return t('unknown');
    const code = bundleKeyForCode(normalizeCode(err.code));

    // Missing-key sentinel rendering (threat_model T-1.1.06-02).
    if (!hasNestedKey(errorsBundle, code)) {
      return isProduction() ? t('unknown') : `[MISSING:${code}]`;
    }

    // ICU placeholders are auto-escaped by next-intl; backend's
    // AllowedParamScalars filters params at the wire boundary (Plan 02).
    try {
      // next-intl's t() typing is strict against the bundle shape; cast through
      // never because the runtime shape is validated by hasNestedKey above.
      return t(code as never, (err.params ?? {}) as never);
    } catch {
      return isProduction() ? t('unknown') : `[MISSING:${code}]`;
    }
  };
}

/**
 * React hook returning a function that converts a FieldError to a localized
 * field-level message. Same code-only invariant as useLocalizedApiError.
 */
export function useLocalizedFieldError() {
  const t = useTranslations('errors');
  const messages = useMessages() as Record<string, unknown>;
  const errorsBundle = (messages as { errors?: unknown }).errors;

  return (fe: FieldError | undefined): string => {
    if (!fe?.code) return t('fieldFallback');
    const code = normalizeCode(fe.code);

    if (!hasNestedKey(errorsBundle, code)) {
      return isProduction() ? t('fieldFallback') : `[MISSING:${code}]`;
    }

    try {
      return t(code as never, (fe.params ?? {}) as never);
    } catch {
      return isProduction() ? t('fieldFallback') : `[MISSING:${code}]`;
    }
  };
}
