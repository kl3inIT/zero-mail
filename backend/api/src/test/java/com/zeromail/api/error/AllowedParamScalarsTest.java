package com.zeromail.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Asserts the {@link AllowedParamScalars#filter} allow-list invariants. The filter is the primary
 * mitigation for threat T-1.1.02-01 (Information Disclosure via params): every {@code params} Map
 * written by {@code GlobalExceptionHandler} flows through this filter, so anything the filter
 * accepts is what reaches the wire.
 *
 * <p>Sentinel values use the obvious-marker convention ({@code <<SENTINEL_*>>}); never
 * realistic-looking email or token data.
 *
 * <p>Original Wave 0 RED stub lived under {@code backend/core/src/test/.../i18n/}. Plan 02
 * deviation Rule 3: relocated to {@code backend/api/src/test/.../error/} because the unit under
 * test lives in the {@code backend:api} module, and {@code backend:core} cannot see {@code
 * backend:api} classes (api depends on core, not the reverse).
 */
class AllowedParamScalarsTest {

    @Test
    @DisplayName("filter accepts numbers, booleans, dotted keys, locale codes")
    void filter_acceptsNumbersBooleansAllowListedStrings() {
        Object[] input = {42, true, "vi", "error.validation.field.email.invalid"};

        Map<String, Object> out = AllowedParamScalars.filter(input);

        assertThat(out)
                .hasSize(4)
                .containsExactly(
                        entry("arg0", 42),
                        entry("arg1", true),
                        entry("arg2", "vi"),
                        entry("arg3", "error.validation.field.email.invalid"));
    }

    @Test
    @DisplayName("filter rejects HTML / script payloads")
    void filter_rejectsRawHtmlScripts() {
        Object[] input = {"<<SENTINEL_HTML <script>>>"};

        assertThat(AllowedParamScalars.filter(input)).isEmpty();
    }

    @Test
    @DisplayName("filter rejects refresh-token-shaped strings (contain '/')")
    void filter_rejectsRefreshTokenShapes() {
        // Real refresh tokens look like "1//04abcDEF...". Use an obvious sentinel.
        Object[] input = {"<<SENTINEL/TOKEN>>"};

        assertThat(AllowedParamScalars.filter(input)).isEmpty();
    }

    @Test
    @DisplayName("filter rejects long strings (> 64 chars)")
    void filter_rejectsLongStrings() {
        String tooLong = "x".repeat(65);

        assertThat(AllowedParamScalars.filter(new Object[] {tooLong})).isEmpty();
    }

    @Test
    @DisplayName("filter accepts identifiers shaped like SQL constraint names")
    void filter_acceptsConstraintNameLikeIdentifier() {
        // "users_email_key" matches [a-zA-Z0-9_.\-]{1,64}, so the allow-list accepts it.
        // Defense in depth lives at call sites: never pass SQL state / SQLSTATE codes /
        // exception class names INTO the params Map. The regex is the second line of
        // defense, not the first.
        assertThat(AllowedParamScalars.filter(new Object[] {"users_email_key"}))
                .containsEntry("arg0", "users_email_key")
                .hasSize(1);
    }

    @Test
    @DisplayName("filter rejects null array → empty Map")
    void filter_handlesNullArray() {
        assertThat(AllowedParamScalars.filter(null)).isEmpty();
    }

    @Test
    @DisplayName("filter advances index counter even for rejected entries (no key shifting)")
    void filter_preservesIndexCounterEvenForRejected() {
        // Per PATTERNS.md skeleton: when a rejected value sits at slot 0, the accepted
        // value at slot 1 must NOT silently move into "arg0" — it stays at "arg1".
        Object[] input = {"<bad>", 42};

        Map<String, Object> out = AllowedParamScalars.filter(input);

        assertThat(out).hasSize(1).containsEntry("arg1", 42).doesNotContainKey("arg0");
    }
}
