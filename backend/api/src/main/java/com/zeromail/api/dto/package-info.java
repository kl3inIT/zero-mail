/**
 * HTTP wire DTOs grouped by domain ({@code account/}, {@code gmail/}, {@code onboarding/}). Layout
 * mirrors {@code core.<domain>.*} per Phase 1.2.1 D-D1.
 *
 * <p><b>Naming convention (D-D2):</b> {@code *Request} for input DTOs, {@code *Response} for output
 * DTOs.
 *
 * <p><b>Exception (D-D3):</b> {@code com.zeromail.api.error.*} ({@code ApiError}, {@code
 * ErrorCodes}, {@code FieldErrorDto}, {@code AllowedParamScalars}) is cross-cutting Phase 1.1
 * infrastructure, NOT domain-specific. Those types stay in {@code com.zeromail.api.error} and are
 * NOT reorganized into this layout.
 *
 * <p><b>Upgrade trigger (D-D1):</b> when any single domain exceeds 10 DTOs, split into {@code
 * request/} + {@code response/} sub-folders.
 */
package com.zeromail.api.dto;
