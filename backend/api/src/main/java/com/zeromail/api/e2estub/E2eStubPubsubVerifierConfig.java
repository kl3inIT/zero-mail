package com.zeromail.api.e2estub;

import com.google.auth.oauth2.TokenVerifier;
import com.zeromail.api.security.PubSubTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("e2e-stub")
@ConditionalOnProperty(name = "zero-mail.e2e-stub.enabled", havingValue = "true")
public class E2eStubPubsubVerifierConfig {

    @Bean
    @Primary
    public PubSubTokenVerifier e2eStubTokenVerifier(
            @Value("${zero-mail.api.gmail.pubsub.sa-principal-email}") String serviceAccountEmail) {
        return idToken -> verifiedEmail(idToken, serviceAccountEmail);
    }

    private static String verifiedEmail(String idToken, String serviceAccountEmail)
            throws TokenVerifier.VerificationException {
        if (idToken == null || idToken.isBlank()) {
            throw new TokenVerifier.VerificationException("e2e-stub token must not be blank");
        }
        return serviceAccountEmail;
    }
}
