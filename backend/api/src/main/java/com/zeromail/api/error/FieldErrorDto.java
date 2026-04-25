package com.zeromail.api.error;

import java.util.Map;

/**
 * Per-field validation error record carried in {@code ProblemDetail.fieldErrors[]}.
 *
 * <p>Shape (CONTEXT.md decision D-C2): {@code { field, code, params }}.
 * <ul>
 *   <li>{@code field} — dotted path (e.g., {@code email}, {@code templates[0].key}).</li>
 *   <li>{@code code} — dotted hierarchical key inside the {@code errors.field.*} namespace.</li>
 *   <li>{@code params} — allow-listed scalars only (numbers, booleans, dotted keys, short
 *       resource identifiers). Filtered through {@link AllowedParamScalars#filter(Object[])}.
 *       Never raw user content, Gmail body fragments, SQL constraint names, or exception
 *       class names.</li>
 * </ul>
 *
 * <p><b>Reject from JHipster {@code FieldErrorVM}:</b> we deliberately omit any
 * server-built localized-prose string field and the {@code objectName} field.
 * Server-side prose violates Phase 1.1's "frontend owns user-visible localization
 * from stable codes" invariant (CONTEXT.md §E reject). The frontend localizes the
 * dotted {@code code} via the {@code errors.*} dictionary; nothing else is needed.
 */
public record FieldErrorDto(String field, String code, Map<String, Object> params) {}
