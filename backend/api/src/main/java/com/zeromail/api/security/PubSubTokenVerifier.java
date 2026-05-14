package com.zeromail.api.security;

import com.google.auth.oauth2.TokenVerifier;

@FunctionalInterface
public interface PubSubTokenVerifier {

    String verifyEmail(String idToken) throws TokenVerifier.VerificationException;
}
