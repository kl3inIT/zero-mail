package com.zeromail.core.i18n;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Wave 0 RED stub — Plan 02 (or wherever AllowedParamScalars utility lands) turns this GREEN.
 *
 * Asserts the allow-list filter that gates which values may appear in a ProblemDetail's
 * params Map. The filter exists to enforce CONTEXT.md decision D-C2: params values are
 * scalars only — numbers, booleans, enum strings, dotted translation keys, short
 * resource identifiers — never raw user content, refresh-token shapes, SQL constraint
 * names, or exception class names (RESEARCH.md Pitfall 5: regex {@code [a-zA-Z0-9_.\-]{1,64}}).
 *
 * Sentinels in this stub use obvious markers — never realistic-looking email or token data.
 */
@Disabled("Wave 0 RED stub — implemented by Plan 02")
class AllowedParamScalarsTest {

    @Test
    void filter_acceptsNumbersBooleansAllowListedStrings() {
        // Plan 02:
        //   var input = new Object[] { 42, true, "vi", "error.validation.field.email.invalid" };
        //   var out = AllowedParamScalars.filter(input);
        //   assertThat(out).hasSize(4);
    }

    @Test
    void filter_rejectsRawHtmlScripts() {
        // Plan 02:
        //   var input = new Object[] { "<<SENTINEL_HTML <script>>>" };
        //   assertThat(AllowedParamScalars.filter(input)).isEmpty();
    }

    @Test
    void filter_rejectsRefreshTokenShapes() {
        // Plan 02: a string containing '/' must be rejected — refresh tokens have shape
        //   "1//04abcDEF...". Use an obvious sentinel like "<<SENTINEL_TOKEN/AB>>".
        //   assertThat(AllowedParamScalars.filter(new Object[] { "<<SENTINEL/TOKEN>>" })).isEmpty();
    }

    @Test
    void filter_rejectsLongStrings() {
        // Plan 02:
        //   var tooLong = "x".repeat(65);
        //   assertThat(AllowedParamScalars.filter(new Object[] { tooLong })).isEmpty();
    }

    @Test
    void filter_acceptsConstraintNameLikeIdentifier() {
        // Plan 02: "users_email_key" matches [a-zA-Z0-9_.\-]{1,64} so the allow-list
        // accepts it. Defense in depth lives at the call site — never pass SQL state /
        // SQLSTATE codes / exception class names into the params Map.
        //   assertThat(AllowedParamScalars.filter(new Object[] { "users_email_key" })).hasSize(1);
    }
}
