package com.zeromail.api.error;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Allow-list filter for values that may appear in a {@code ProblemDetail.params} Map
 * (and {@code FieldErrorDto.params}). The positive allow-list is the primary mitigation
 * for threat T-1.1.02-01 (Information Disclosure via params).
 *
 * <p>Accepted shapes:
 * <ul>
 *   <li>{@link Number} — counts, sizes, indexes.</li>
 *   <li>{@link Boolean} — flags.</li>
 *   <li>{@link String} matching the regex {@code [a-zA-Z0-9_.\-]{1,64}} — short
 *       identifiers / dotted dictionary keys / enum names / locale codes such as
 *       {@code "vi"} / {@code "en"}.</li>
 * </ul>
 *
 * <p>Everything else — HTML, refresh-token shapes (containing {@code /}), SQL state
 * codes (rejected by length and underscore-only formats are accepted, but the
 * underlying contract is "never pass SQL state into params" — defense in depth lives
 * at call sites; the regex is the second line), exception class names embedded in
 * messages, prompt-injection text, raw user input, long strings, etc. — is dropped.
 *
 * <p>The output uses {@link LinkedHashMap} to preserve declaration order for stable
 * ICU MessageFormat placeholder rendering on the frontend. Keys are {@code arg0},
 * {@code arg1}, ... and the index counter advances for every input regardless of
 * whether the value was accepted or rejected, so a rejected value at slot {@code index=0}
 * does NOT cause a downstream {@code arg1} value to silently move into {@code arg0}.
 *
 * <p>Cite: RESEARCH.md Common Pitfall #5 (allow-list rationale).
 */
public final class AllowedParamScalars {

    private static final Pattern ALLOWED_STRING = Pattern.compile("[a-zA-Z0-9_.\\-]{1,64}");

    /**
     * Filter a Bean Validation argument array (typically from {@code FieldError#getArguments()})
     * down to scalars that are safe to render through ICU MessageFormat on the frontend.
     *
     * @param arguments the raw argument array; {@code null} is treated as empty.
     * @return an order-preserving Map of {@code arg{i}} → accepted value. Rejected entries
     *         are dropped, but their index slot is consumed (no key shifting).
     */
    public static Map<String, Object> filter(Object[] arguments) {
        if (arguments == null) {
            return Map.of();
        }
        Map<String, Object> allowedParameters = new LinkedHashMap<>();
        int argumentIndex = 0;
        for (Object argument : arguments) {
            if (argument instanceof Number || argument instanceof Boolean) {
                allowedParameters.put("arg" + argumentIndex, argument);
            } else if (argument instanceof String stringValue && ALLOWED_STRING.matcher(stringValue).matches()) {
                allowedParameters.put("arg" + argumentIndex, stringValue);
            }
            // else: silently drop. Index counter still advances below.
            argumentIndex++;
        }
        return allowedParameters;
    }

    private AllowedParamScalars() {}
}
