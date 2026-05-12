package com.zeromail.api.error;

import java.util.List;
import java.util.Map;

/**
 * Marker record describing the {@code application/problem+json} wire shape every Phase 1 error
 * response actually serializes. Used by the springdoc {@code OpenApiCustomizer} (Plan 03) to
 * register a single {@code ApiError} schema component, so the generated {@code openapi-typescript}
 * client (Plan 06) gets a typed {@code ApiError} interface to switch on.
 *
 * <p>This class is NOT instantiated at runtime — the actual response body is a {@code
 * ProblemDetail} with extension members written via {@code setProperty(...)}. Field order here
 * matches the Jackson 3 serialization order so the OpenAPI schema and the live wire shape stay
 * aligned.
 *
 * <p>Wire example (the {@code message} field is a transitional alias of {@code code} kept for one
 * phase per CONTEXT.md decision D-C1):
 *
 * <pre>
 * {
 *   "type": "about:blank",
 *   "title": "Validation failed",
 *   "status": 400,
 *   "detail": "One or more fields failed validation.",
 *   "code": "error.validation",
 *   "params": {},
 *   "fieldErrors": [
 *     { "field": "language",
 *       "code": "error.validation.field.language.Pattern",
 *       "params": {} }
 *   ],
 *   "message": "error.validation"
 * }
 * </pre>
 */
public record ApiError(
        String type,
        String title,
        int status,
        String detail,
        String code,
        Map<String, Object> params,
        List<FieldErrorDto> fieldErrors,
        String message) {}
