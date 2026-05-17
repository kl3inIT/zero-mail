/**
 * Frontend mirror of backend `ErrorCodes.java` constants.
 *
 * Why this exists (REVIEW IN-05):
 *   Switching on bare string literals (`'error.billing.insufficient'` etc.)
 *   in feature code lets a typo silently fall through to the unknown-error
 *   fallback path with no test failure. Centralize the dotted codes here
 *   so a typo is a TypeScript error and so a contract test can fail CI
 *   when this file drifts from `backend/api/.../ErrorCodes.java`.
 *
 * Contract (CONTEXT.md D-C3):
 *   - Backend emits these dotted codes from `GlobalExceptionHandler` /
 *     `ErrorCodes.java` in every error `ApiError.code`.
 *   - Frontend switches on `code` ONLY (never `title` / `detail` / class name).
 *   - Each code maps 1:1 to an `errors.*` translation key after the
 *     leading `error.` prefix is stripped (see `lib/api/errors.ts`).
 *
 * To add a new code:
 *   1. Add the constant to `backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java`.
 *   2. Add the matching property here.
 *   3. Add the matching translation key under `errors.*` in `i18n/messages/{vi,en}.json`
 *      (typically owned by the feature's `messages.ts`).
 *   4. The contract test in `apps/web/__tests__/error-codes-parity.test.ts`
 *      will fail until both sides agree.
 */

export const ErrorCode = {
  // auth
  AuthUnauthorized: 'error.auth.unauthorized',
  AuthForbidden: 'error.auth.forbidden',
  AuthCurrentUserNotFound: 'error.auth.currentUserNotFound',
  AuthConsentDenied: 'error.auth.consent_denied',
  AuthGmailScopeRequired: 'error.auth.gmail_scope_required',
  // generic
  Validation: 'error.validation',
  DataIntegrity: 'error.dataIntegrity',
  Conflict: 'error.conflict',
  BadRequest: 'error.badRequest',
  // gmail
  GmailDisconnected: 'error.gmail.disconnected',
  // billing
  BillingInsufficient: 'error.billing.insufficient',
  BillingLedgerInvalidState: 'error.billing.ledger.invalidState',
  BillingSepayReferenceInvalid: 'error.billing.sepay.reference_invalid',
  BillingSepayAuthInvalid: 'error.billing.sepay.auth_invalid',
  // llm
  LlmSafetyViolation: 'error.llm.safety_violation',
  LlmSanitizationFailed: 'error.llm.sanitization_failed',
  LlmByokInvalid: 'error.llm.byok.invalid',
  LlmByokValidateFailed: 'error.llm.byok.validate_failed',
  // draft/thread
  DraftGenerationInFlight: 'error.draft.generation.in_flight',
  DraftGenerationUnavailable: 'error.draft.generation.unavailable',
  DraftGenerationFailed: 'error.draft.generation.failed',
  InvalidCursor: 'error.pagination.invalid_cursor',
  // rules
  RulesCompileInvalid: 'error.rules.compile.invalid',
  RulesCompileClarificationRequired: 'error.rules.compile.clarification_required',
  RulesNotFound: 'error.rules.not_found',
  RulesPreviewRequired: 'error.rules.preview.required',
  RulesPreviewInvalidSampleSize: 'error.rules.preview.invalid_sample_size',
  RulesReorderInvalid: 'error.rules.reorder.invalid',
  RulesVersionMismatch: 'error.rules.version_mismatch',
  RulesUnsafeAction: 'error.rules.unsafe_action',
  RulesDuplicate: 'error.rules.duplicate',
  RulesGmailUnavailable: 'error.rules.gmail.unavailable',
} as const;

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode];
