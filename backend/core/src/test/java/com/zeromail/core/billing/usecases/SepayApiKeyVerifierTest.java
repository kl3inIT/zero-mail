package com.zeromail.core.billing.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SepayApiKeyVerifierTest {

    @Test
    void null_authorization_header_rejected() {
        SepayApiKeyVerifier verifier = new SepayApiKeyVerifier("expected-key");

        assertThat(verifier.verify(null)).isFalse();
    }

    @Test
    void wrong_prefix_rejected() {
        SepayApiKeyVerifier verifier = new SepayApiKeyVerifier("expected-key");

        assertThat(verifier.verify("Bearer expected-key")).isFalse();
    }

    @Test
    void wrong_key_rejected() {
        SepayApiKeyVerifier verifier = new SepayApiKeyVerifier("expected-key");

        assertThat(verifier.verify("Apikey wrong-key")).isFalse();
    }

    @Test
    void correct_key_accepted() {
        SepayApiKeyVerifier verifier = new SepayApiKeyVerifier("expected-key");

        assertThat(verifier.verify("Apikey expected-key")).isTrue();
    }
}
