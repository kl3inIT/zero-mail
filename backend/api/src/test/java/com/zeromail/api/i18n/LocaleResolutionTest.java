package com.zeromail.api.i18n;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Wave 0 RED stub — Plan 04 turns this GREEN.
 *
 * Asserts the /me + PATCH /me/language locale-resolution contract:
 *  - GET /me returns the persisted preferred_language
 *  - PATCH /me/language with { "language": "vi" | "en" } persists to users.preferred_language
 *    and rejects values outside the allow-list with error.validation.field.preferredLanguage.invalid
 *
 * All sentinels in fixtures must be obvious (e.g. "<<SENTINEL_LOCALE>>", "zz"). Never
 * realistic email content or refresh-token-shaped strings.
 */
@Disabled("Wave 0 RED stub — implemented by Plan 04")
class LocaleResolutionTest {

    @Test
    void patch_me_language_persists_preferredLanguage_to_db() {
        // Plan 04:
        //  1. authenticated PATCH /me/language { "language": "en" } -> 200
        //  2. SELECT preferred_language FROM users WHERE id = <session user> -> "en"
        //  3. cross-tenant safety: a second tenant's row is unchanged (T-1.1.01-03).
    }

    @Test
    void get_me_returns_preferred_language() {
        // Plan 04:
        //  1. seed user with preferred_language = "vi"
        //  2. GET /me -> 200, $.preferredLanguage == "vi"
        //  Today MeController hard-codes null at the 5th arg; Plan 04 swaps to
        //  user.preferredLanguage().
    }

    @Test
    void patch_me_language_rejects_value_outside_allow_list() {
        // Plan 04: PATCH /me/language { "language": "zz" } -> 400
        //   $.code == "error.validation"
        //   $.fieldErrors[0].field == "language"
        //   $.fieldErrors[0].code == "error.validation.field.preferredLanguage.invalid"
    }
}
