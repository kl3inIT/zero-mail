package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProdSessionCookieSecureGuardTest {

    @Test
    void prod_profile_with_secure_false_throws() {
        MockEnvironment prodEnvironment = new MockEnvironment();
        prodEnvironment.setActiveProfiles("prod");
        ProdSessionCookieSecureGuard guard =
                new ProdSessionCookieSecureGuard(prodEnvironment, false);

        assertThatThrownBy(guard::verifyProdCookieSecure)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server.servlet.session.cookie.secure=true")
                .hasMessageContaining("V3.4.1");
    }

    @Test
    void prod_profile_with_secure_true_passes() {
        MockEnvironment prodEnvironment = new MockEnvironment();
        prodEnvironment.setActiveProfiles("prod");
        ProdSessionCookieSecureGuard guard =
                new ProdSessionCookieSecureGuard(prodEnvironment, true);

        assertThatCode(guard::verifyProdCookieSecure).doesNotThrowAnyException();
    }

    @Test
    void dev_profile_with_secure_false_passes() {
        MockEnvironment devEnvironment = new MockEnvironment();
        devEnvironment.setActiveProfiles("dev");
        ProdSessionCookieSecureGuard guard =
                new ProdSessionCookieSecureGuard(devEnvironment, false);

        assertThatCode(guard::verifyProdCookieSecure).doesNotThrowAnyException();
    }

    @Test
    void mixed_profiles_containing_prod_with_secure_false_throws() {
        MockEnvironment mixedEnvironment = new MockEnvironment();
        mixedEnvironment.setActiveProfiles("prod", "local");
        ProdSessionCookieSecureGuard guard =
                new ProdSessionCookieSecureGuard(mixedEnvironment, false);

        assertThatThrownBy(guard::verifyProdCookieSecure).isInstanceOf(IllegalStateException.class);
    }
}
