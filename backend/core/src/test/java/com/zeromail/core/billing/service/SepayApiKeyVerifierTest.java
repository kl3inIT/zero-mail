package com.zeromail.core.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class SepayApiKeyVerifierTest {

    @Test
    @Disabled("Wave 0 RED scaffold - production class lands in Plan 03")
    void null_authorization_header_rejected() {
        SepayApiKeyVerifier verifier = new SepayApiKeyVerifier("expected-key");

        assertThat(verifier.verify(null)).isFalse();
    }

    @Test
    @Disabled("Wave 0 RED scaffold - production class lands in Plan 03")
    void wrong_prefix_rejected() {
        SepayApiKeyVerifier verifier = new SepayApiKeyVerifier("expected-key");

        assertThat(verifier.verify("Bearer expected-key")).isFalse();
    }

    @Test
    @Disabled("Wave 0 RED scaffold - production class lands in Plan 03")
    void wrong_key_rejected() {
        SepayApiKeyVerifier verifier = new SepayApiKeyVerifier("expected-key");

        assertThat(verifier.verify("Apikey wrong-key")).isFalse();
    }

    @Test
    @Disabled("Wave 0 RED scaffold - production class lands in Plan 03")
    void correct_key_accepted() {
        SepayApiKeyVerifier verifier = new SepayApiKeyVerifier("expected-key");

        assertThat(verifier.verify("Apikey expected-key")).isTrue();
    }
}
